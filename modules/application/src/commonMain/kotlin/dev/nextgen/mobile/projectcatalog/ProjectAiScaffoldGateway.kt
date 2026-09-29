package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.domain.project.ProjectAiFieldSuggestion
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldOperation
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldProposal
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldRules
import dev.nextgen.mobile.network.RemoteFailureKind
import dev.nextgen.mobile.network.RemoteFailureState
import dev.nextgen.mobile.network.RemoteOperationKind
import dev.nextgen.mobile.network.resolveRemoteFailure
import dev.nextgen.mobile.security.SecureSessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

data class ProjectAiScaffoldRequest(
    val templateId: String,
    val templateVersion: Int,
    val baseProjectRevision: Int?,
    val assignmentBrief: String,
    val researchQuestion: String?,
    val studentQuestion: String?,
    val currentFields: Map<String, String>,
    val constraints: List<String>,
    val locale: String,
    val optedIn: Boolean,
    val projectDataConsent: Boolean,
    val projectDataConsentVersion: String,
    val projectId: String? = null,
    val operation: ProjectAiScaffoldOperation = if (projectId == null) {
        ProjectAiScaffoldOperation.CREATE_PROJECT
    } else {
        ProjectAiScaffoldOperation.ASSIST_PROJECT
    },
)

sealed interface ProjectAiScaffoldGatewayResult {
    data class Preview(
        val proposal: ProjectAiScaffoldProposal,
        val requestId: String,
        val creditCost: Int,
    ) : ProjectAiScaffoldGatewayResult

    data class Deferred(val reason: ProjectAiScaffoldDeferredReason) : ProjectAiScaffoldGatewayResult

    data class Unavailable(val code: String) : ProjectAiScaffoldGatewayResult

    data class Rejected(val code: String) : ProjectAiScaffoldGatewayResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean,
        val sameIntentReplayAllowed: Boolean,
        val idempotencyKey: String?,
    ) : ProjectAiScaffoldGatewayResult
}

enum class ProjectAiScaffoldDeferredReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

/** Authenticated, bounded transport for reviewed-template project scaffolding. */
class ProjectAiScaffoldGateway(
    private val configuration: AiClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun generatePreview(
        request: ProjectAiScaffoldRequest,
        idempotencyKey: String,
    ): ProjectAiScaffoldGatewayResult {
        if (!request.optedIn) return ProjectAiScaffoldGatewayResult.Rejected("AI_OPT_IN_REQUIRED")
        if (!request.projectDataConsent
            || request.projectDataConsentVersion != ProjectAiScaffoldRules.PROJECT_DATA_CONSENT_VERSION
        ) {
            return ProjectAiScaffoldGatewayResult.Rejected("PROJECT_AI_DATA_CONSENT_REQUIRED")
        }
        if (!isValidRequest(request)
            || !idempotencyKeyPattern.matches(idempotencyKey)
        ) {
            return ProjectAiScaffoldGatewayResult.Rejected("INVALID_PROJECT_AI_REQUEST")
        }

        val session = when (val result = readSession()) {
            is SessionResult.Ready -> result.value
            is SessionResult.Deferred -> return ProjectAiScaffoldGatewayResult.Deferred(result.reason)
        }
        val body = requestBody(request)
        if (body.encodeToByteArray().size > MAX_REQUEST_BYTES) {
            return ProjectAiScaffoldGatewayResult.Rejected("INVALID_PROJECT_AI_REQUEST")
        }

        val response = try {
            transport.request(
                method = "POST",
                url = "${configuration.normalizedApiBaseUrl}/v1/project-ai/scaffold",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.accessToken}",
                    "Content-Type" to "application/json",
                    "Idempotency-Key" to idempotencyKey,
                ),
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return failed(idempotencyKey, RemoteFailureKind.TRANSPORT)
        }

        if (response.body.encodeToByteArray().size > MAX_RESPONSE_BYTES) {
            return ProjectAiScaffoldGatewayResult.Rejected("INVALID_PROJECT_AI_RESPONSE")
        }
        if (response.statusCode == 401) {
            return ProjectAiScaffoldGatewayResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
        }
        if (response.statusCode == 403) {
            val code = errorCode(response)
            return if (code == "PROJECT_AI_CONSENT_REQUIRED" || code == "PROJECT_AI_CONSENT_POLICY_STALE") {
                ProjectAiScaffoldGatewayResult.Rejected(code)
            } else {
                ProjectAiScaffoldGatewayResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
            }
        }
        if (response.statusCode == 503) {
            return ProjectAiScaffoldGatewayResult.Unavailable(errorCode(response) ?: "PROJECT_AI_UNAVAILABLE")
        }
        if (response.statusCode in 408..429 || response.statusCode in 500..599) {
            return failed(idempotencyKey, RemoteFailureKind.TRANSIENT_HTTP)
        }
        if (response.statusCode !in 200..299) {
            return ProjectAiScaffoldGatewayResult.Rejected(errorCode(response) ?: "PROJECT_AI_REQUEST_REJECTED")
        }

        val parsed = parsePreview(response)
            ?: return ProjectAiScaffoldGatewayResult.Rejected("INVALID_PROJECT_AI_RESPONSE")
        if (parsed.proposal.templateId != request.templateId
            || parsed.proposal.templateVersion != request.templateVersion
            || parsed.operation != request.operation
            || parsed.projectId != request.projectId
            || parsed.proposal.baseProjectRevision != request.baseProjectRevision
        ) {
            return ProjectAiScaffoldGatewayResult.Rejected("PROJECT_AI_RESPONSE_CONTEXT_MISMATCH")
        }
        return ProjectAiScaffoldGatewayResult.Preview(parsed.proposal, response.requestId(), parsed.creditCost)
    }

    private fun readSession(): SessionResult {
        if (!configuration.isConfigured) return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.NOT_CONFIGURED)
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.SECURE_STORAGE)
        } ?: return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.AUTH_REQUIRED)
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return SessionResult.Deferred(ProjectAiScaffoldDeferredReason.SESSION_EXPIRED)
        }
        return SessionResult.Ready(session.material)
    }

    private fun requestBody(request: ProjectAiScaffoldRequest): String = buildJsonObject {
        put("schema", REQUEST_SCHEMA)
        put("version", "1")
        put("operation", request.operation.wireValue())
        put("projectId", request.projectId)
        put("templateId", request.templateId)
        put("templateVersion", request.templateVersion)
        request.baseProjectRevision?.let { put("baseProjectRevision", it) } ?: put("baseProjectRevision", JsonNull)
        put("assignmentBrief", request.assignmentBrief)
        request.researchQuestion?.let { put("researchQuestion", it) }
        request.studentQuestion?.let { put("studentQuestion", it) }
        put("currentFields", buildJsonObject {
            request.currentFields.entries.sortedBy { it.key }.forEach { entry ->
                put(entry.key, entry.value)
            }
        })
        put("constraints", buildJsonArray {
            request.constraints.forEach { add(JsonPrimitive(it)) }
        })
        put("locale", request.locale)
        put("optedIn", true)
        put("projectDataConsent", true)
        put("projectDataConsentVersion", request.projectDataConsentVersion)
    }.toString()

    private fun isValidRequest(request: ProjectAiScaffoldRequest): Boolean =
        templateIdPattern.matches(request.templateId)
            && when (request.operation) {
                ProjectAiScaffoldOperation.CREATE_PROJECT -> request.projectId == null && request.baseProjectRevision == null
                ProjectAiScaffoldOperation.ASSIST_PROJECT ->
                    request.projectId?.let(projectIdPattern::matches) == true && request.baseProjectRevision?.let { it > 0 } == true
            }
            && request.templateVersion > 0
            && request.assignmentBrief.isNotBlank()
            && request.assignmentBrief.length <= 8_000
            && request.researchQuestion?.length?.let { it <= 2_000 } != false
            && request.studentQuestion?.let { it.isNotBlank() && it.length <= 2_000 && '\u0000' !in it } != false
            && request.currentFields.size <= 32
            && request.currentFields.all { (id, value) ->
                fieldIdPattern.matches(id) && value.length <= 8_000 && '\u0000' !in value
            }
            && request.constraints.size <= 8
            && request.constraints.all { it.isNotBlank() && it.length <= 400 && '\u0000' !in it }
            && localePattern.matches(request.locale)

    private data class ParsedPreview(
        val proposal: ProjectAiScaffoldProposal,
        val creditCost: Int,
        val operation: ProjectAiScaffoldOperation,
        val projectId: String?,
    )

    private fun parsePreview(response: AccountHttpResponse): ParsedPreview? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == RESPONSE_KEYS)
        require(root.string("schema") == "evidrilo.project-ai-scaffold")
        require(root.string("version") == "1")
        require(root.string("status") == "preview")
        require(root["reasonCode"] == JsonNull)
        val operation = when (root.string("operation")) {
            "create_project" -> ProjectAiScaffoldOperation.CREATE_PROJECT
            "assist_project" -> ProjectAiScaffoldOperation.ASSIST_PROJECT
            else -> error("operation invalid")
        }
        val projectId = when (val value = root["projectId"]) {
            JsonNull -> null
            is JsonPrimitive -> value.content.also { require(projectIdPattern.matches(it)) }
            else -> error("project id invalid")
        }
        val creditCost = root.int("creditCost").also { require(it in 1..PROJECT_AI_MAX_CREDITS_PER_REQUEST) }
        val baseProjectRevision = when (val value = root["baseProjectRevision"]) {
            JsonNull -> null
            is JsonPrimitive -> value.content.toIntOrNull()?.also { require(it > 0) }
                ?: error("base project revision invalid")
            else -> error("base project revision invalid")
        }
        val scaffold = root["scaffold"] as? JsonObject ?: error("scaffold required")
        require(scaffold.keys == SCAFFOLD_KEYS)
        val templateId = scaffold.string("templateId")?.also { require(templateIdPattern.matches(it)) }
            ?: error("template id required")
        val templateVersion = scaffold.int("templateVersion").also { require(it > 0) }
        val promptVersion = scaffold.string("promptVersion")?.also { require(it == ProjectAiScaffoldRules.PROMPT_VERSION) }
            ?: error("prompt version required")
        val guidanceText = scaffold.string("guidanceText")
            ?.also { require(it.isNotBlank() && it.length <= ProjectAiScaffoldRules.MAX_GUIDANCE_CHARS) }
            ?: error("guidance text required")
        val suggestions = scaffold["fieldSuggestions"]?.jsonArray?.map { element ->
            val suggestion = element as? JsonObject ?: error("suggestion required")
            require(suggestion.keys == SUGGESTION_KEYS)
            val fieldId = suggestion.string("fieldId")?.also { require(fieldIdPattern.matches(it)) }
                ?: error("field id required")
            val value = suggestion.string("suggestedValue")?.also { require(it.isNotBlank() && it.length <= 8_000) }
                ?: error("suggested value required")
            ProjectAiFieldSuggestion(fieldId, value)
        } ?: error("suggestions required")
        require(suggestions.size <= ProjectAiScaffoldRules.MAX_SUGGESTIONS)
        require(suggestions.map(ProjectAiFieldSuggestion::fieldId).distinct().size == suggestions.size)
        val questions: List<String> = scaffold["clarificationQuestions"]?.jsonArray?.map { element ->
            (element as? JsonPrimitive)?.contentOrNull
                ?.also { require(it.isNotBlank() && it.length <= ProjectAiScaffoldRules.MAX_QUESTION_CHARS) }
                ?: error("clarification question required")
        } ?: error("clarifications required")
        require(questions.size <= ProjectAiScaffoldRules.MAX_QUESTIONS)
        val nextPrompts = scaffold["recommendedNextPrompts"]?.jsonArray?.map { element ->
            (element as? JsonPrimitive)?.contentOrNull
                ?.also {
                    require(it.isNotBlank() && it.length <= ProjectAiScaffoldRules.MAX_NEXT_PROMPT_CHARS)
                }
                ?: error("next prompt required")
        } ?: error("next prompts required")
        require(nextPrompts.size <= ProjectAiScaffoldRules.MAX_NEXT_PROMPTS)
        ParsedPreview(
            proposal = ProjectAiScaffoldProposal(
                templateId = templateId,
                templateVersion = templateVersion,
                baseProjectRevision = baseProjectRevision,
                promptVersion = promptVersion,
                guidanceText = guidanceText,
                fieldSuggestions = suggestions,
                clarificationQuestions = questions,
                recommendedNextPrompts = nextPrompts,
                projectId = projectId,
            ),
            creditCost = creditCost,
            operation = operation,
            projectId = projectId,
        )
    }.getOrNull()

    private fun errorCode(response: AccountHttpResponse): String? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        root.string("code")?.takeIf(reasonCodePattern::matches)
    }.getOrNull()

    private fun failed(
        idempotencyKey: String,
        failure: RemoteFailureKind,
    ): ProjectAiScaffoldGatewayResult.Failed {
        val resolution = resolveRemoteFailure(
            connectivity = transport.deviceConnectivity,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            failure = failure,
            requestWasDispatched = true,
            idempotencyKey = idempotencyKey,
        )
        val code = when (resolution.state) {
            RemoteFailureState.OFFLINE -> "PROJECT_AI_OFFLINE"
            RemoteFailureState.OUTCOME_UNKNOWN -> "PROJECT_AI_OUTCOME_UNKNOWN"
            RemoteFailureState.SERVICE_UNAVAILABLE -> "PROJECT_AI_UNAVAILABLE"
            RemoteFailureState.REJECTED -> "PROJECT_AI_REQUEST_REJECTED"
            RemoteFailureState.CANCELLED -> "PROJECT_AI_CANCELLED"
        }
        return ProjectAiScaffoldGatewayResult.Failed(
            code = code,
            retryable = resolution.retryAllowed,
            outcomeUnknown = resolution.state == RemoteFailureState.OUTCOME_UNKNOWN,
            sameIntentReplayAllowed = resolution.sameIntentReplayAllowed,
            idempotencyKey = idempotencyKey.takeIf { resolution.sameIntentReplayAllowed },
        )
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.optionalString(name: String): String? = when (val value = this[name]) {
        null -> null
        is JsonPrimitive -> value.contentOrNull
        else -> error("$name must be a string or null")
    }

    private fun JsonObject.int(name: String): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: error("$name required")

    private fun AccountHttpResponse.requestId(): String = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("object required")
        root.string("requestId")?.also { require(requestIdPattern.matches(it)) }
            ?: error("request id required")
    }.getOrThrow()

    private sealed interface SessionResult {
        data class Ready(val value: dev.nextgen.mobile.security.SecureSessionMaterial) : SessionResult
        data class Deferred(val reason: ProjectAiScaffoldDeferredReason) : SessionResult
    }

    private companion object {
        const val REQUEST_SCHEMA = "evidrilo.project-ai-scaffold"
        const val MAX_REQUEST_BYTES = 16 * 1024
        const val MAX_RESPONSE_BYTES = 64 * 1024
        val RESPONSE_KEYS = setOf(
            "schema", "version", "status", "scaffold", "operation", "projectId", "baseProjectRevision", "reasonCode", "requestId",
            "creditCost",
        )
        val SCAFFOLD_KEYS = setOf(
            "templateId", "templateVersion", "promptVersion", "guidanceText", "fieldSuggestions",
            "clarificationQuestions", "recommendedNextPrompts",
        )
        val SUGGESTION_KEYS = setOf("fieldId", "suggestedValue")
        val templateIdPattern = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
        val fieldIdPattern = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
        val localePattern = Regex("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")
        val reasonCodePattern = Regex("^[A-Z0-9_]{3,64}$")
        val requestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
        val idempotencyKeyPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
        val projectIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}

private fun ProjectAiScaffoldOperation.wireValue(): String = when (this) {
    ProjectAiScaffoldOperation.CREATE_PROJECT -> "create_project"
    ProjectAiScaffoldOperation.ASSIST_PROJECT -> "assist_project"
}
