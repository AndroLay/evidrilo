package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldRules
import dev.nextgen.mobile.network.DeviceConnectivity
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

const val MAX_PROJECT_AI_CONSENT_RESPONSE_BYTES: Int = 16 * 1024
const val PROJECT_AI_CONSENT_POLICY_VERSION: String = ProjectAiScaffoldRules.PROJECT_DATA_CONSENT_VERSION

data class ProjectAiConsentState(
    val granted: Boolean,
    val policyVersion: String,
    val grantedAt: String?,
    val revokedAt: String?,
    val generation: Long,
)

enum class ProjectAiConsentDeferralReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

sealed interface ProjectAiConsentGatewayResult {
    data class State(val value: ProjectAiConsentState) : ProjectAiConsentGatewayResult

    data class Deferred(val reason: ProjectAiConsentDeferralReason) : ProjectAiConsentGatewayResult

    data object Unauthorized : ProjectAiConsentGatewayResult

    data object Forbidden : ProjectAiConsentGatewayResult

    data object PolicyStale : ProjectAiConsentGatewayResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean = false,
    ) : ProjectAiConsentGatewayResult
}

/** Authenticated account consent state; it neither caches nor grants consent locally. */
class ProjectAiConsentGateway(
    private val configuration: AiClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun refresh(): ProjectAiConsentGatewayResult = execute(method = "GET", body = "")

    suspend fun grantAfterExplicitUserAction(
        explicitlyConfirmed: Boolean,
    ): ProjectAiConsentGatewayResult {
        if (!explicitlyConfirmed) {
            return ProjectAiConsentGatewayResult.Failed(
                code = "PROJECT_AI_CONSENT_CONFIRMATION_REQUIRED",
                retryable = false,
            )
        }
        val body = buildJsonObject {
            put("schema", CONSENT_SCHEMA)
            put("version", CONSENT_VERSION)
            put("policyVersion", PROJECT_AI_CONSENT_POLICY_VERSION)
        }.toString()
        return execute(method = "PUT", body = body)
    }

    suspend fun revoke(): ProjectAiConsentGatewayResult = execute(method = "DELETE", body = "")

    private suspend fun execute(
        method: String,
        body: String,
    ): ProjectAiConsentGatewayResult {
        if (!configuration.isConfigured) {
            return ProjectAiConsentGatewayResult.Deferred(ProjectAiConsentDeferralReason.NOT_CONFIGURED)
        }
        val session = when (val result = readSession()) {
            is SessionResult.Ready -> result.value
            is SessionResult.Deferred -> return ProjectAiConsentGatewayResult.Deferred(result.reason)
        }
        val response = try {
            transport.request(
                method = method,
                url = "${configuration.normalizedApiBaseUrl}/v1/project-ai/consent",
                headers = buildMap {
                    put("Accept", "application/json")
                    put("Authorization", "Bearer ${session.accessToken}")
                    if (method == "PUT") put("Content-Type", "application/json")
                },
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return transportFailure(method)
        }

        if (response.statusCode == 401) return ProjectAiConsentGatewayResult.Unauthorized
        if (response.statusCode == 403) return ProjectAiConsentGatewayResult.Forbidden
        if (response.body.encodeToByteArray().size > MAX_PROJECT_AI_CONSENT_RESPONSE_BYTES) {
            val successMayHaveCommitted = method != "GET" && response.statusCode in 200..299
            if (response.statusCode in TRANSIENT_HTTP_CODES) {
                val outcomeUnknown = method != "GET" &&
                    (response.statusCode == 408 || response.statusCode in 500..599)
                return ProjectAiConsentGatewayResult.Failed(
                    code = "PROJECT_AI_CONSENT_SERVER_ERROR",
                    retryable = true,
                    outcomeUnknown = outcomeUnknown,
                )
            }
            return invalidResponse(
                outcomeUnknown = successMayHaveCommitted,
                retryable = successMayHaveCommitted,
            )
        }
        return when (response.statusCode) {
            409 -> if (errorCode(response) == POLICY_STALE_CODE) {
                ProjectAiConsentGatewayResult.PolicyStale
            } else {
                ProjectAiConsentGatewayResult.Failed(
                    code = errorCode(response) ?: "PROJECT_AI_CONSENT_CONFLICT",
                    retryable = false,
                )
            }
            in TRANSIENT_HTTP_CODES -> {
                val outcomeUnknown = method != "GET" &&
                    (response.statusCode == 408 || response.statusCode in 500..599)
                ProjectAiConsentGatewayResult.Failed(
                    code = errorCode(response) ?: "PROJECT_AI_CONSENT_SERVER_ERROR",
                    retryable = true,
                    outcomeUnknown = outcomeUnknown,
                )
            }
            in 200..299 -> parseState(response.body)?.let(ProjectAiConsentGatewayResult::State)
                ?: invalidResponse(outcomeUnknown = method != "GET", retryable = method != "GET")
            else -> ProjectAiConsentGatewayResult.Failed(
                code = errorCode(response) ?: "PROJECT_AI_CONSENT_REQUEST_REJECTED",
                retryable = false,
            )
        }
    }

    private fun readSession(): SessionResult {
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return SessionResult.Deferred(ProjectAiConsentDeferralReason.SECURE_STORAGE)
        } ?: return SessionResult.Deferred(ProjectAiConsentDeferralReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) {
            return SessionResult.Deferred(ProjectAiConsentDeferralReason.AUTH_REQUIRED)
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return SessionResult.Deferred(ProjectAiConsentDeferralReason.SESSION_EXPIRED)
        }
        return SessionResult.Ready(session.material)
    }

    private fun parseState(body: String): ProjectAiConsentState? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("object required")
        require(root.keys == RESPONSE_KEYS)
        require(root.requiredString("schema") == CONSENT_SCHEMA)
        require(root.requiredString("version") == CONSENT_VERSION)
        val grantedPrimitive = root["granted"] as? JsonPrimitive
            ?: error("granted is required")
        require(!grantedPrimitive.isString)
        val granted = grantedPrimitive.booleanOrNull
            ?: error("granted is required")
        val policyVersion = root.requiredString("policyVersion")
            .also { require(it.length <= 80 && policyVersionPattern.matches(it)) }
        val grantedAt = root.requiredNullableTimestamp("grantedAt")
        val revokedAt = root.requiredNullableTimestamp("revokedAt")
        val generationPrimitive = root["generation"] as? JsonPrimitive
            ?: error("generation is required")
        require(!generationPrimitive.isString)
        val generation = generationPrimitive.longOrNull
            ?.takeIf { it >= 0 }
            ?: error("generation must be a non-negative integer")
        ProjectAiConsentState(
            granted = granted,
            policyVersion = policyVersion,
            grantedAt = grantedAt,
            revokedAt = revokedAt,
            generation = generation,
        )
    }.getOrNull()

    private fun JsonObject.requiredString(name: String): String {
        val value = this[name] as? JsonPrimitive ?: error("$name is required")
        require(value.isString)
        return value.content
    }

    private fun JsonObject.requiredNullableTimestamp(name: String): String? = when (val value = this[name]) {
        JsonNull -> null
        is JsonPrimitive -> {
            require(value.isString)
            value.content.also { timestamp ->
                require(timestamp.length in 1..64)
                Instant.parse(timestamp)
            }
        }
        else -> error("$name must be a required nullable timestamp")
    }

    private fun errorCode(response: AccountHttpResponse): String? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        val value = root["code"] as? JsonPrimitive ?: return@runCatching null
        value.contentOrNull?.takeIf(reasonCodePattern::matches)
    }.getOrNull()

    private fun transportFailure(method: String): ProjectAiConsentGatewayResult.Failed {
        return ProjectAiConsentGatewayResult.Failed(
            code = if (transport.deviceConnectivity == DeviceConnectivity.OFFLINE) {
                "PROJECT_AI_CONSENT_OFFLINE"
            } else {
                "PROJECT_AI_CONSENT_UNAVAILABLE"
            },
            retryable = true,
            outcomeUnknown = method != "GET",
        )
    }

    private fun invalidResponse(
        outcomeUnknown: Boolean = false,
        retryable: Boolean = false,
    ) = ProjectAiConsentGatewayResult.Failed(
        code = "INVALID_PROJECT_AI_CONSENT_RESPONSE",
        retryable = retryable,
        outcomeUnknown = outcomeUnknown,
    )

    private sealed interface SessionResult {
        data class Ready(val value: SecureSessionMaterial) : SessionResult

        data class Deferred(val reason: ProjectAiConsentDeferralReason) : SessionResult
    }

    private companion object {
        const val CONSENT_SCHEMA = "evidrilo.project-ai-consent"
        const val CONSENT_VERSION = "1"
        const val POLICY_STALE_CODE = "PROJECT_AI_CONSENT_POLICY_STALE"
        val RESPONSE_KEYS = setOf(
            "schema",
            "version",
            "granted",
            "policyVersion",
            "grantedAt",
            "revokedAt",
            "generation",
        )
        val TRANSIENT_HTTP_CODES = setOf(408, 425, 429) + (500..599)
        val policyVersionPattern = Regex("^project-ai-data\\.v[0-9]+$")
        val reasonCodePattern = Regex("^[A-Z0-9_]{3,64}$")
    }
}
