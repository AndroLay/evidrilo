package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountHttpTransport
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

data class AnalyticsClientConfiguration(
    val apiBaseUrl: String,
) {
    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() {
            val normalized = normalizedApiBaseUrl
            if (!normalized.startsWith("https://", ignoreCase = true)) return false
            val authority = normalized
                .substringAfter("//", "")
                .substringBeforeAny('/', '?', '#')
            return authority.isNotBlank() &&
                !authority.contains('@') &&
                !authority.any(Char::isWhitespace) &&
                (authority.contains('.') || authority == "localhost")
        }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val index = indexOfFirst { it in delimiters }
        return if (index == -1) this else substring(0, index)
    }
}

enum class AnalyticsDeferralReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

sealed interface AnalyticsGatewayResult {
    data class Sent(val outcome: String, val requestId: String) : AnalyticsGatewayResult

    data class Deferred(val reason: AnalyticsDeferralReason) : AnalyticsGatewayResult

    data class Failed(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : AnalyticsGatewayResult
}

class AnalyticsGateway(
    private val configuration: AnalyticsClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun send(
        event: AnalyticsEvent,
        consent: AnalyticsConsent,
    ): AnalyticsGatewayResult {
        if (consent != AnalyticsConsent.GRANTED) {
            return AnalyticsGatewayResult.Failed(
                code = "CONSENT_REQUIRED",
                message = "Analytics consent is required.",
                retryable = false,
            )
        }
        if (!event.validate()) {
            return AnalyticsGatewayResult.Failed(
                code = "INVALID_ANALYTICS_EVENT",
                message = "The analytics event is invalid.",
                retryable = false,
            )
        }
        if (!configuration.isConfigured) {
            return AnalyticsGatewayResult.Deferred(AnalyticsDeferralReason.NOT_CONFIGURED)
        }
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return AnalyticsGatewayResult.Deferred(AnalyticsDeferralReason.SECURE_STORAGE)
        } ?: return AnalyticsGatewayResult.Deferred(AnalyticsDeferralReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) {
            return AnalyticsGatewayResult.Deferred(AnalyticsDeferralReason.AUTH_REQUIRED)
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return AnalyticsGatewayResult.Deferred(AnalyticsDeferralReason.SESSION_EXPIRED)
        }

        val body = buildEventBody(event)
        val response = try {
            transport.request(
                method = "POST",
                url = "${configuration.normalizedApiBaseUrl}/v1/analytics/events",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Content-Type" to "application/json",
                    "Authorization" to "Bearer ${session.material.accessToken}",
                    "Idempotency-Key" to event.clientEventId,
                ),
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return AnalyticsGatewayResult.Failed(
                code = "OFFLINE",
                message = "Analytics is unavailable offline.",
                retryable = true,
            )
        }
        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                AnalyticsGatewayResult.Deferred(AnalyticsDeferralReason.AUTH_REQUIRED)

            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 -> AnalyticsGatewayResult.Failed(
                code = "ANALYTICS_UNAVAILABLE",
                message = "Analytics is temporarily unavailable.",
                retryable = true,
            )

            response.statusCode !in 200..299 -> AnalyticsGatewayResult.Failed(
                code = "ANALYTICS_REQUEST_REJECTED",
                message = "Analytics could not be recorded.",
                retryable = false,
            )

            else -> parseResponse(response)?.let { parsed ->
                AnalyticsGatewayResult.Sent(parsed.outcome, parsed.requestId)
            } ?: AnalyticsGatewayResult.Failed(
                code = "INVALID_ANALYTICS_RESPONSE",
                message = "The analytics response is invalid.",
                retryable = false,
            )
        }
    }

    suspend fun sendWithRetry(
        event: AnalyticsEvent,
        consent: AnalyticsConsent,
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): AnalyticsGatewayResult {
        var attempt = 0
        while (true) {
            val result = send(event, consent)
            val retryable = result is AnalyticsGatewayResult.Failed && result.retryable
            if (!retryable || attempt >= 2) return result
            wait(250L * (1L shl attempt))
            attempt += 1
        }
    }

    private fun buildEventBody(event: AnalyticsEvent): String = buildJsonObject {
        put("schema", "evidrilo.analytics-event")
        put("version", "1")
        put("clientEventId", event.clientEventId)
        put("eventName", event.name.wireName)
        put("eventVersion", 1)
        put("occurredAt", event.occurredAt)
        put("source", "mobile")
        put("consent", "granted")
        put("properties", buildJsonObject {
            put("attemptId", event.properties.attemptId)
            put("caseVersionId", event.properties.caseVersionId)
            put("outcome", event.properties.outcome)
            put("skillId", event.properties.skillId)
            put("revisionChanged", event.properties.revisionChanged)
            put("surfaceId", event.properties.surfaceId)
            put("action", event.properties.action)
            put("productId", event.properties.productId)
            put("errorCode", event.properties.errorCode)
        })
    }.toString()

    private fun parseResponse(response: AccountHttpResponse): ParsedAnalyticsResponse? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.string("schema") == "evidrilo.analytics-event-result")
        require(root.string("version") == "1")
        val outcome = root.string("outcome")?.also { require(it == "accepted" || it == "duplicate") }
            ?: error("outcome required")
        val requestId = root.string("requestId")?.also {
            require(it.length in 8..128 && it.matches(Regex("^[A-Za-z0-9_-]+$")))
        } ?: error("request id required")
        ParsedAnalyticsResponse(outcome, requestId)
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private data class ParsedAnalyticsResponse(
        val outcome: String,
        val requestId: String,
    )
}

fun createPlatformAnalyticsGateway(): AnalyticsGateway {
    val accountConfiguration = createAccountClientConfiguration()
    return AnalyticsGateway(
        configuration = AnalyticsClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
        transport = createAccountHttpTransport(),
        secureSessionStore = SecureSessionStoreFactory.create(),
        nowEpochSeconds = { Clock.System.now().epochSeconds },
    )
}
