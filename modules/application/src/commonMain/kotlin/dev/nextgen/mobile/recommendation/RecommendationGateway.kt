package dev.nextgen.mobile.recommendation

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionStoreFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

sealed interface RecommendationGatewayResult {
    data class Found(val recommendation: RecommendationPayload) : RecommendationGatewayResult

    data object Abstained : RecommendationGatewayResult

    data class Deferred(val reason: RecommendationDeferralReason) : RecommendationGatewayResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
    ) : RecommendationGatewayResult
}

data class RecommendationClientConfiguration(
    val apiBaseUrl: String,
) {
    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = normalizedApiBaseUrl.startsWith("https://", ignoreCase = true) &&
            normalizedApiBaseUrl.substringAfter("//", "")
                .substringBeforeAny('/', '?', '#')
                .let { authority ->
                    authority.isNotBlank() &&
                        !authority.contains('@') &&
                        !authority.any(Char::isWhitespace) &&
                        (authority.contains('.') || authority == "localhost")
                }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val index = indexOfFirst { it in delimiters }
        return if (index == -1) this else substring(0, index)
    }
}

enum class RecommendationDeferralReason {
    CONSENT_REQUIRED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
    NOT_CONFIGURED,
}

sealed interface RecommendationInteractionResult {
    data class Sent(val outcome: String, val requestId: String) : RecommendationInteractionResult

    data class Deferred(val reason: RecommendationDeferralReason) : RecommendationInteractionResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
    ) : RecommendationInteractionResult
}

class RecommendationGateway(
    private val configuration: RecommendationClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun next(consent: AnalyticsConsent): RecommendationGatewayResult {
        val authorization = authorizedSession(consent)
        if (authorization is AuthorizationResult.Deferred) {
            return RecommendationGatewayResult.Deferred(authorization.reason)
        }

        val session = (authorization as AuthorizationResult.Ready).session
        val response = try {
            transport.request(
                method = "GET",
                url = "${configuration.normalizedApiBaseUrl}/v1/recommendations/next",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.material.accessToken}",
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return RecommendationGatewayResult.Failed("OFFLINE", retryable = true)
        }

        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                RecommendationGatewayResult.Deferred(RecommendationDeferralReason.AUTH_REQUIRED)

            response.statusCode == 404 -> RecommendationGatewayResult.Abstained

            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 ->
                RecommendationGatewayResult.Failed("RECOMMENDATION_UNAVAILABLE", retryable = true)

            response.statusCode !in 200..299 ->
                RecommendationGatewayResult.Failed("RECOMMENDATION_REQUEST_REJECTED", retryable = false)

            else -> when (val parsed = parseRecommendation(response.body, json)) {
                is RecommendationParseResult.Valid -> when (parsed.value.status) {
                    RecommendationStatus.RECOMMENDED -> RecommendationGatewayResult.Found(parsed.value)
                    RecommendationStatus.ABSTAIN -> RecommendationGatewayResult.Abstained
                }

                is RecommendationParseResult.Rejected ->
                    RecommendationGatewayResult.Failed("INVALID_RECOMMENDATION_RESPONSE", retryable = false)
            }
        }
    }

    suspend fun nextWithRetry(
        consent: AnalyticsConsent,
        wait: suspend (Long) -> Unit = ::defaultRecommendationWait,
    ): RecommendationGatewayResult {
        var attempt = 0
        while (true) {
            val result = next(consent)
            val retryable = result is RecommendationGatewayResult.Failed && result.retryable
            if (!retryable || attempt >= 2) return result
            wait(250L * (1L shl attempt))
            attempt += 1
        }
    }

    suspend fun interact(
        recommendation: RecommendationPayload,
        interaction: RecommendationInteraction,
        clientEventId: String,
        consent: AnalyticsConsent,
    ): RecommendationInteractionResult {
        if (consent != AnalyticsConsent.GRANTED) {
            return RecommendationInteractionResult.Deferred(RecommendationDeferralReason.CONSENT_REQUIRED)
        }
        if (!isValidClientEventId(clientEventId) ||
            recommendation.status != RecommendationStatus.RECOMMENDED ||
            recommendation.caseVersionId == null ||
            !recommendationIdentifierPattern.matches(recommendation.caseVersionId)
        ) {
            return RecommendationInteractionResult.Failed("INVALID_RECOMMENDATION_INTERACTION", retryable = false)
        }

        val authorization = authorizedSession(consent)
        if (authorization is AuthorizationResult.Deferred) {
            return RecommendationInteractionResult.Deferred(authorization.reason)
        }
        val session = (authorization as AuthorizationResult.Ready).session
        val body = buildJsonObject {
            put("schema", "evidrilo.recommendation-interaction")
            put("version", "1")
            put("clientEventId", clientEventId)
            put("caseVersionId", recommendation.caseVersionId)
            put("interaction", interaction.wireName)
            put("calculationVersion", recommendation.calculationVersion)
            put("reasonCode", recommendation.reason.name)
            put("consent", "granted")
        }.toString()
        val response = try {
            transport.request(
                method = "POST",
                url = "${configuration.normalizedApiBaseUrl}/v1/recommendations/interactions",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer ${session.material.accessToken}",
                    "Idempotency-Key" to clientEventId,
                ),
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return RecommendationInteractionResult.Failed("OFFLINE", retryable = true)
        }

        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                RecommendationInteractionResult.Deferred(RecommendationDeferralReason.AUTH_REQUIRED)

            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 ->
                RecommendationInteractionResult.Failed("RECOMMENDATION_INTERACTION_UNAVAILABLE", retryable = true)

            response.statusCode !in 200..299 ->
                RecommendationInteractionResult.Failed("RECOMMENDATION_INTERACTION_REJECTED", retryable = false)

            else -> parseInteractionResponse(response.body, json)?.let { parsed ->
                RecommendationInteractionResult.Sent(parsed.outcome, parsed.requestId)
            } ?: RecommendationInteractionResult.Failed(
                "INVALID_RECOMMENDATION_INTERACTION_RESPONSE",
                retryable = false,
            )
        }
    }

    suspend fun interactWithRetry(
        recommendation: RecommendationPayload,
        interaction: RecommendationInteraction,
        clientEventId: String,
        consent: AnalyticsConsent,
        wait: suspend (Long) -> Unit = ::defaultRecommendationWait,
    ): RecommendationInteractionResult {
        var attempt = 0
        while (true) {
            val result = interact(recommendation, interaction, clientEventId, consent)
            val retryable = result is RecommendationInteractionResult.Failed && result.retryable
            if (!retryable || attempt >= 2) return result
            wait(250L * (1L shl attempt))
            attempt += 1
        }
    }

    private fun authorizedSession(consent: AnalyticsConsent): AuthorizationResult {
        if (consent != AnalyticsConsent.GRANTED) {
            return AuthorizationResult.Deferred(RecommendationDeferralReason.CONSENT_REQUIRED)
        }
        if (!configuration.isConfigured) {
            return AuthorizationResult.Deferred(RecommendationDeferralReason.NOT_CONFIGURED)
        }
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return AuthorizationResult.Deferred(RecommendationDeferralReason.SECURE_STORAGE)
        } ?: return AuthorizationResult.Deferred(RecommendationDeferralReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) {
            return AuthorizationResult.Deferred(RecommendationDeferralReason.AUTH_REQUIRED)
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return AuthorizationResult.Deferred(RecommendationDeferralReason.SESSION_EXPIRED)
        }
        return AuthorizationResult.Ready(session)
    }

    private fun parseInteractionResponse(
        body: String,
        json: Json,
    ): ParsedInteractionResponse? = runCatching {
        require(body.encodeToByteArray().size <= MAX_RECOMMENDATION_RESPONSE_BYTES)
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("object required")
        require(root.keys == interactionResponseKeys)
        require(root.string("schema") == "evidrilo.recommendation-interaction-result")
        require(root.string("version") == "1")
        val outcome = root.string("outcome")?.also { require(it == "accepted" || it == "duplicate") }
            ?: error("outcome required")
        val requestId = root.string("requestId")?.also {
            require(recommendationRequestIdPattern.matches(it))
        } ?: error("request id required")
        ParsedInteractionResponse(outcome, requestId)
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private sealed interface AuthorizationResult {
        data class Ready(val session: dev.nextgen.mobile.security.StoredAccountSession) : AuthorizationResult

        data class Deferred(val reason: RecommendationDeferralReason) : AuthorizationResult
    }

    private data class ParsedInteractionResponse(
        val outcome: String,
        val requestId: String,
    )
}

private val interactionResponseKeys = setOf("schema", "version", "outcome", "requestId")
private val recommendationIdentifierPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
private val recommendationRequestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
private val clientEventIdPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
)

private fun isValidClientEventId(value: String): Boolean = clientEventIdPattern.matches(value)

private suspend fun defaultRecommendationWait(delayMillis: Long) {
    kotlinx.coroutines.delay(delayMillis)
}

fun createPlatformRecommendationGateway(): RecommendationGateway {
    val accountConfiguration = createAccountClientConfiguration()
    return RecommendationGateway(
        configuration = RecommendationClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
        transport = createAccountHttpTransport(),
        secureSessionStore = SecureSessionStoreFactory.create(),
        nowEpochSeconds = { Clock.System.now().epochSeconds },
    )
}
