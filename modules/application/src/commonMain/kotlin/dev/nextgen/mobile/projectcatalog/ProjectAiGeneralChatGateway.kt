package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.network.DeviceConnectivity
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException

const val GENERAL_CHAT_REQUEST_CONSENT_VERSION = "general-chat.v1"

data class ProjectAiGeneralChatRequest(
    val installationId: String,
    val locale: String,
    val message: String,
    val consentConfirmed: Boolean,
)

data class ProjectAiGeneralChatAnswer(
    val answer: String,
    val recommendedNextPrompts: List<String>,
    val requestId: String,
    val creditCost: Int,
)

sealed interface ProjectAiGeneralChatResult {
    data class Answer(val value: ProjectAiGeneralChatAnswer) : ProjectAiGeneralChatResult
    data class Deferred(val reason: ProjectAiGeneralChatDeferredReason) : ProjectAiGeneralChatResult
    data class Unavailable(val code: String) : ProjectAiGeneralChatResult
    data class Rejected(val code: String, val creditCost: Int? = null) : ProjectAiGeneralChatResult
    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean,
    ) : ProjectAiGeneralChatResult
}

enum class ProjectAiGeneralChatDeferredReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

/** Sends exactly one consented, standalone message; no project or transcript is accepted. */
class ProjectAiGeneralChatGateway(
    private val configuration: AiClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun sendMessage(
        request: ProjectAiGeneralChatRequest,
        idempotencyKey: String,
    ): ProjectAiGeneralChatResult {
        if (!isValidRequest(request) || !requestIdPattern.matches(idempotencyKey)) {
            return ProjectAiGeneralChatResult.Rejected("INVALID_PROJECT_AI_GENERAL_CHAT")
        }
        if (!request.consentConfirmed) {
            return ProjectAiGeneralChatResult.Rejected("GENERAL_CHAT_CONSENT_REQUIRED")
        }
        val session = when (val state = readSession()) {
            is SessionResult.Ready -> state.value
            is SessionResult.Deferred -> return ProjectAiGeneralChatResult.Deferred(state.reason)
        }
        if (transport.deviceConnectivity == DeviceConnectivity.OFFLINE) {
            return ProjectAiGeneralChatResult.Failed(
                code = "PROJECT_AI_OFFLINE",
                retryable = true,
                outcomeUnknown = false,
            )
        }
        val body = requestBody(request)
        if (body.encodeToByteArray().size > MAX_REQUEST_BYTES) {
            return ProjectAiGeneralChatResult.Rejected("INVALID_PROJECT_AI_GENERAL_CHAT")
        }
        val response = try {
            transport.request(
                method = "POST",
                url = "${configuration.normalizedApiBaseUrl}/v2/project-ai/general-chat",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.accessToken}",
                    "Content-Type" to "application/json",
                    "Idempotency-Key" to idempotencyKey,
                    "X-Evidrilo-General-Chat-Consent" to GENERAL_CHAT_REQUEST_CONSENT_VERSION,
                ),
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return failed(RemoteFailureKind.TRANSPORT)
        }

        if (response.body.encodeToByteArray().size > MAX_RESPONSE_BYTES) {
            return ProjectAiGeneralChatResult.Rejected("INVALID_PROJECT_AI_GENERAL_CHAT_RESPONSE")
        }
        when (response.statusCode) {
            401 -> return ProjectAiGeneralChatResult.Deferred(ProjectAiGeneralChatDeferredReason.AUTH_REQUIRED)
            402 -> return ProjectAiGeneralChatResult.Rejected(errorCode(response) ?: "AI_CREDITS_INSUFFICIENT")
            403 -> {
                val code = errorCode(response)
                val creditCost = errorCreditCost(response)
                return ProjectAiGeneralChatResult.Rejected(
                    code ?: "PROJECT_AI_GENERAL_CHAT_REJECTED",
                    creditCost,
                )
            }
            429 -> return ProjectAiGeneralChatResult.Unavailable(errorCode(response) ?: "AI_RATE_LIMITED")
            503 -> {
                val code = errorCode(response) ?: "PROJECT_AI_GENERAL_CHAT_NOT_READY"
                val creditCost = errorCreditCost(response)
                if (creditCost != null) return ProjectAiGeneralChatResult.Rejected(code, creditCost)
                if (code == "PROJECT_AI_USAGE_SETTLEMENT_UNKNOWN") {
                    return ProjectAiGeneralChatResult.Failed(code, retryable = false, outcomeUnknown = true)
                }
                return ProjectAiGeneralChatResult.Unavailable(code)
            }
            in 200..299 -> Unit
            in 500..599 -> return failed(RemoteFailureKind.TRANSIENT_HTTP)
            in 400..499 -> return ProjectAiGeneralChatResult.Rejected(
                errorCode(response) ?: "PROJECT_AI_GENERAL_CHAT_REJECTED",
                errorCreditCost(response),
            )
            else -> return ProjectAiGeneralChatResult.Rejected("PROJECT_AI_GENERAL_CHAT_REJECTED")
        }

        val answer = parseAnswer(response, idempotencyKey)
            ?: return ProjectAiGeneralChatResult.Rejected("INVALID_PROJECT_AI_GENERAL_CHAT_RESPONSE")
        return ProjectAiGeneralChatResult.Answer(answer)
    }

    private fun readSession(): SessionResult {
        if (!configuration.isConfigured) {
            return SessionResult.Deferred(ProjectAiGeneralChatDeferredReason.NOT_CONFIGURED)
        }
        val stored = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return SessionResult.Deferred(ProjectAiGeneralChatDeferredReason.SECURE_STORAGE)
        } ?: return SessionResult.Deferred(ProjectAiGeneralChatDeferredReason.AUTH_REQUIRED)
        if (!stored.account.emailVerified) {
            return SessionResult.Deferred(ProjectAiGeneralChatDeferredReason.AUTH_REQUIRED)
        }
        if (stored.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return SessionResult.Deferred(ProjectAiGeneralChatDeferredReason.SESSION_EXPIRED)
        }
        return SessionResult.Ready(stored.material)
    }

    private fun requestBody(request: ProjectAiGeneralChatRequest): String = buildJsonObject {
        put("schema", REQUEST_SCHEMA)
        put("version", "2")
        put("installationId", request.installationId)
        put("locale", request.locale)
        put("message", request.message)
    }.toString()

    private fun parseAnswer(
        response: AccountHttpResponse,
        idempotencyKey: String,
    ): ProjectAiGeneralChatAnswer? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        require(root.keys == RESPONSE_KEYS)
        require(root.requiredString("schema") == REQUEST_SCHEMA)
        require(root.requiredString("version") == "2")
        require(root.requiredString("mode") == "GENERAL")
        require(root.requiredString("status") == "success")
        val answer = root.requiredString("answer")
        require(answer.isNotBlank() && answer.length <= MAX_ANSWER_LENGTH && '\u0000' !in answer)
        val prompts = root.getValue("recommendedNextPrompts").jsonArray.map { element ->
            val primitive = element as? JsonPrimitive ?: error("prompt must be a string")
            require(primitive.isString)
            primitive.content.also {
                require(it.isNotBlank() && it.length <= MAX_RECOMMENDED_PROMPT_LENGTH && it.none(Char::isISOControl))
            }
        }
        require(prompts.size in MIN_RECOMMENDED_PROMPTS..MAX_RECOMMENDED_PROMPTS)
        require(prompts.distinctBy(String::lowercase).size == prompts.size)
        val requestId = root.requiredString("requestId")
        require(requestId == idempotencyKey)
        val creditCost = root.requiredInt("creditCost")
        require(creditCost in 1..MAX_CREDIT_COST)
        ProjectAiGeneralChatAnswer(answer, prompts, requestId, creditCost)
    }.getOrNull()

    private fun failed(failure: RemoteFailureKind): ProjectAiGeneralChatResult.Failed {
        val resolution = resolveRemoteFailure(
            connectivity = transport.deviceConnectivity,
            operation = RemoteOperationKind.NON_IDEMPOTENT_MUTATION,
            failure = failure,
            requestWasDispatched = true,
        )
        val code = when (resolution.state) {
            RemoteFailureState.OFFLINE -> "PROJECT_AI_OFFLINE"
            RemoteFailureState.OUTCOME_UNKNOWN -> "PROJECT_AI_OUTCOME_UNKNOWN"
            RemoteFailureState.SERVICE_UNAVAILABLE -> "PROJECT_AI_UNAVAILABLE"
            RemoteFailureState.REJECTED -> "PROJECT_AI_GENERAL_CHAT_REJECTED"
            RemoteFailureState.CANCELLED -> "PROJECT_AI_CANCELLED"
        }
        return ProjectAiGeneralChatResult.Failed(
            code = code,
            retryable = resolution.retryAllowed,
            outcomeUnknown = resolution.state == RemoteFailureState.OUTCOME_UNKNOWN,
        )
    }

    private fun errorCode(response: AccountHttpResponse): String? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        root.string("code")?.takeIf(reasonCodePattern::matches)
    }.getOrNull()

    private fun errorCreditCost(response: AccountHttpResponse): Int? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        (root["creditCost"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..MAX_CREDIT_COST }
    }.getOrNull()

    private fun isValidRequest(request: ProjectAiGeneralChatRequest): Boolean =
        uuidPattern.matches(request.installationId)
            && request.locale.length in 2..32
            && localePattern.matches(request.locale)
            && request.message.isNotBlank()
            && request.message.length <= MAX_MESSAGE_LENGTH
            && '\u0000' !in request.message

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.requiredString(name: String): String =
        string(name)?.also { require('\u0000' !in it) } ?: error("$name required")

    private fun JsonObject.requiredInt(name: String): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: error("$name required")

    private sealed interface SessionResult {
        data class Ready(val value: SecureSessionMaterial) : SessionResult
        data class Deferred(val reason: ProjectAiGeneralChatDeferredReason) : SessionResult
    }

    private companion object {
        const val REQUEST_SCHEMA = "evidrilo.project-ai-general-chat"
        const val MAX_MESSAGE_LENGTH = 4_000
        const val MAX_ANSWER_LENGTH = 8_000
        const val MIN_RECOMMENDED_PROMPTS = 1
        const val MAX_RECOMMENDED_PROMPTS = 3
        const val MAX_RECOMMENDED_PROMPT_LENGTH = 240
        const val MAX_CREDIT_COST = 200
        const val MAX_REQUEST_BYTES = 16 * 1024
        const val MAX_RESPONSE_BYTES = 48 * 1024
        val RESPONSE_KEYS = setOf(
            "schema", "version", "mode", "status", "answer", "recommendedNextPrompts", "requestId", "creditCost",
        )
        val requestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
        val reasonCodePattern = Regex("^[A-Z0-9_]{3,64}$")
        val localePattern = Regex("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")
        val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}
