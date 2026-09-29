package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.network.RemoteFailureKind
import dev.nextgen.mobile.network.RemoteFailureState
import dev.nextgen.mobile.network.RemoteOperationKind
import dev.nextgen.mobile.network.resolveRemoteFailure
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException

enum class ProjectAiScaffoldDecision(val wireValue: String) {
    APPLY("apply"),
    DISMISS("dismiss"),
}

sealed interface ProjectAiScaffoldSettlementResult {
    data class Settled(
        val requestId: String,
        val decision: ProjectAiScaffoldDecision,
        val creditCost: Int,
    ) : ProjectAiScaffoldSettlementResult

    data class Deferred(val reason: ProjectAiScaffoldDeferredReason) : ProjectAiScaffoldSettlementResult

    data class Unavailable(val code: String) : ProjectAiScaffoldSettlementResult

    data class Rejected(val code: String) : ProjectAiScaffoldSettlementResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean,
        val sameIntentReplayAllowed: Boolean,
        val idempotencyKey: String?,
    ) : ProjectAiScaffoldSettlementResult
}

/** Account-scoped, idempotent finalization of a reserved project-AI operation. */
class ProjectAiScaffoldSettlementGateway(
    private val configuration: AiClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun settle(
        requestId: String,
        decision: ProjectAiScaffoldDecision,
        expectedCreditCost: Int,
    ): ProjectAiScaffoldSettlementResult {
        if (!requestIdPattern.matches(requestId) || expectedCreditCost !in 1..PROJECT_AI_MAX_CREDITS_PER_REQUEST) {
            return ProjectAiScaffoldSettlementResult.Rejected("INVALID_PROJECT_AI_SETTLEMENT")
        }
        val session = when (val result = readSession()) {
            is SessionResult.Ready -> result.value
            is SessionResult.Deferred -> return ProjectAiScaffoldSettlementResult.Deferred(result.reason)
        }
        val body = buildJsonObject {
            put("schema", SETTLEMENT_SCHEMA)
            put("version", "1")
            put("requestId", requestId)
            put("decision", decision.wireValue)
        }.toString()
        if (body.encodeToByteArray().size > MAX_REQUEST_BYTES) {
            return ProjectAiScaffoldSettlementResult.Rejected("INVALID_PROJECT_AI_SETTLEMENT")
        }

        val response = try {
            transport.request(
                method = "POST",
                url = "${configuration.normalizedApiBaseUrl}/v1/project-ai/scaffold/settlement",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.accessToken}",
                    "Content-Type" to "application/json",
                    "Idempotency-Key" to requestId,
                ),
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return failed(requestId, RemoteFailureKind.TRANSPORT)
        }

        if (response.body.encodeToByteArray().size > MAX_RESPONSE_BYTES) {
            return ProjectAiScaffoldSettlementResult.Rejected("INVALID_PROJECT_AI_SETTLEMENT_RESPONSE")
        }
        if (response.statusCode == 401) {
            return ProjectAiScaffoldSettlementResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
        }
        if (response.statusCode == 403) {
            return ProjectAiScaffoldSettlementResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
        }
        if (response.statusCode == 503) {
            return ProjectAiScaffoldSettlementResult.Unavailable(
                errorCode(response) ?: "PROJECT_AI_SETTLEMENT_NOT_READY",
            )
        }
        if (response.statusCode in 408..429 || response.statusCode in 500..599) {
            return failed(requestId, RemoteFailureKind.TRANSIENT_HTTP)
        }
        if (response.statusCode !in 200..299) {
            return ProjectAiScaffoldSettlementResult.Rejected(
                errorCode(response) ?: "PROJECT_AI_SETTLEMENT_REJECTED",
            )
        }

        val settlement = parseResponse(response)
            ?: return ProjectAiScaffoldSettlementResult.Rejected("INVALID_PROJECT_AI_SETTLEMENT_RESPONSE")
        val expectedStatus = when (decision) {
            ProjectAiScaffoldDecision.APPLY -> "applied"
            ProjectAiScaffoldDecision.DISMISS -> "dismissed"
        }
        if (settlement.requestId != requestId
            || settlement.status != expectedStatus
            || settlement.creditCost != expectedCreditCost
        ) {
            return ProjectAiScaffoldSettlementResult.Rejected("INVALID_PROJECT_AI_SETTLEMENT_RESPONSE")
        }
        return ProjectAiScaffoldSettlementResult.Settled(requestId, decision, settlement.creditCost)
    }

    private fun readSession(): SessionResult {
        if (!configuration.isConfigured) {
            return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.NOT_CONFIGURED)
        }
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.SECURE_STORAGE)
        } ?: return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) {
            return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.SESSION_EXPIRED)
        }
        return SessionResult.Ready(session.material)
    }

    private fun parseResponse(response: AccountHttpResponse): ParsedSettlement? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == RESPONSE_KEYS)
        require(root.string("schema") == SETTLEMENT_SCHEMA)
        require(root.string("version") == "1")
        val status = root.string("status")?.also { require(it == "applied" || it == "dismissed") }
            ?: error("status required")
        val requestId = root.string("requestId")?.also { require(requestIdPattern.matches(it)) }
            ?: error("request id required")
        val creditCost = root.int("creditCost").also { require(it in 1..PROJECT_AI_MAX_CREDITS_PER_REQUEST) }
        ParsedSettlement(status, requestId, creditCost)
    }.getOrNull()

    private fun errorCode(response: AccountHttpResponse): String? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        root.string("code")?.takeIf(reasonCodePattern::matches)
    }.getOrNull()

    private fun failed(
        requestId: String,
        failure: RemoteFailureKind,
    ): ProjectAiScaffoldSettlementResult.Failed {
        val resolution = resolveRemoteFailure(
            connectivity = transport.deviceConnectivity,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            failure = failure,
            requestWasDispatched = true,
            idempotencyKey = requestId,
        )
        val code = when (resolution.state) {
            RemoteFailureState.OFFLINE -> "PROJECT_AI_OFFLINE"
            RemoteFailureState.OUTCOME_UNKNOWN -> "PROJECT_AI_OUTCOME_UNKNOWN"
            RemoteFailureState.SERVICE_UNAVAILABLE -> "PROJECT_AI_SETTLEMENT_UNAVAILABLE"
            RemoteFailureState.REJECTED -> "PROJECT_AI_SETTLEMENT_REJECTED"
            RemoteFailureState.CANCELLED -> "PROJECT_AI_SETTLEMENT_CANCELLED"
        }
        return ProjectAiScaffoldSettlementResult.Failed(
            code = code,
            retryable = resolution.retryAllowed,
            outcomeUnknown = resolution.state == RemoteFailureState.OUTCOME_UNKNOWN,
            sameIntentReplayAllowed = resolution.sameIntentReplayAllowed,
            idempotencyKey = requestId.takeIf { resolution.sameIntentReplayAllowed },
        )
    }

    private data class ParsedSettlement(
        val status: String,
        val requestId: String,
        val creditCost: Int,
    )

    private sealed interface SessionResult {
        data class Ready(val value: SecureSessionMaterial) : SessionResult
        data class Deferred(val reason: ProjectAiScaffoldDeferredReason) : SessionResult
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(name: String): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: error("$name required")

    private companion object {
        const val SETTLEMENT_SCHEMA = "evidrilo.project-ai-scaffold-settlement"
        const val MAX_REQUEST_BYTES = 4 * 1024
        const val MAX_RESPONSE_BYTES = 16 * 1024
        val RESPONSE_KEYS = setOf("schema", "version", "status", "requestId", "creditCost")
        val requestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
        val reasonCodePattern = Regex("^[A-Z0-9_]{3,64}$")
    }
}
