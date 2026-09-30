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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.coroutines.cancellation.CancellationException

data class ProjectAiStageAssistSelectedEvidence(
    val id: String,
    val kind: String,
    val label: String,
    val summary: String? = null,
    val origin: String? = null,
)

data class ProjectAiStageAssistRequest(
    val installationId: String,
    /** Local project identity, bound to the authenticated owner by metadata-only registration. */
    val projectId: String,
    val templateId: String,
    val templateVersion: Int,
    val stageId: String,
    val operationId: String,
    /** Current local draft revision registered with the API before dispatch. */
    val baseProjectRevision: Int,
    val baseProjectBindingGeneration: Long,
    /** Local snapshot used only to reject a stale server proposal; values are never sent in this request. */
    val selectedFieldValues: Map<String, String>,
    val selectedEvidence: List<ProjectAiStageAssistSelectedEvidence>,
    val locale: String,
)

enum class ProjectAiStageAssistItemKind {
    EXPLANATION,
    QUESTION,
    PROPOSAL,
}

data class ProjectAiStageAssistItem(
    val id: String,
    val kind: ProjectAiStageAssistItemKind,
    val text: String?,
    val targetFieldId: String?,
    val beforeValue: String?,
    val afterValue: String?,
    val referenceIds: List<String>,
    val assumptions: List<String>,
    val uncertainties: List<String>,
    val knownLimits: List<String>,
)

data class ProjectAiStageAssistEvidenceReference(
    val id: String,
    val kind: String,
    val label: String,
)

data class ProjectAiStageAssistEvaluationPreview(
    val assessmentStatus: String,
    val supportingItems: List<ProjectAiStageAssistEvidenceReference>,
    val proposalsWithoutReferences: List<String>,
    val reportedConflicts: List<String>,
    val reportedAssumptions: List<String>,
    val reportedUncertainties: List<String>,
    val reportedKnownLimits: List<String>,
    val templateLimits: List<String>,
    val reportedOutOfScopeItems: List<String>,
)

data class ProjectAiStageAssistPreview(
    val projectId: String,
    val baseProjectRevision: Int,
    val projectBindingGeneration: Long,
    val consentGeneration: Int,
    val stageId: String,
    val operationId: String,
    val templateId: String,
    val templateVersion: Int,
    val items: List<ProjectAiStageAssistItem>,
    val reportedConflicts: List<String>,
    val reportedOutOfScopeItems: List<String>,
    val evaluationPreview: ProjectAiStageAssistEvaluationPreview,
    val requestId: String,
    val creditCost: Int,
)

sealed interface ProjectAiStageAssistResult {
    data class Preview(val value: ProjectAiStageAssistPreview) : ProjectAiStageAssistResult
    data class Deferred(val reason: ProjectAiStageAssistDeferredReason) : ProjectAiStageAssistResult
    data class Unavailable(val code: String) : ProjectAiStageAssistResult
    data class Rejected(val code: String, val creditCost: Int? = null) : ProjectAiStageAssistResult
    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean,
        val sameIntentReplayAllowed: Boolean,
        val idempotencyKey: String?,
    ) : ProjectAiStageAssistResult
}

enum class ProjectAiStageAssistDeferredReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

sealed interface ProjectAiLocalContextBindingResult {
    data class Registered(val projectRevision: Int, val bindingGeneration: Long) : ProjectAiLocalContextBindingResult
    data class Deferred(val reason: ProjectAiStageAssistDeferredReason) : ProjectAiLocalContextBindingResult
    data class Unavailable(val code: String) : ProjectAiLocalContextBindingResult
    data class Rejected(val code: String) : ProjectAiLocalContextBindingResult
}

enum class ProjectAiStageAssistOutcome(val wireValue: String) {
    APPLIED("APPLIED"),
    EDITED("EDITED"),
    DISMISSED("DISMISSED"),
    STALE("STALE"),
}

sealed interface ProjectAiStageAssistSettlementResult {
    data class Settled(
        val requestId: String,
        val outcome: ProjectAiStageAssistOutcome,
        val creditCost: Int,
    ) : ProjectAiStageAssistSettlementResult

    data class Deferred(val reason: ProjectAiStageAssistDeferredReason) : ProjectAiStageAssistSettlementResult
    data class Unavailable(val code: String) : ProjectAiStageAssistSettlementResult
    data class Rejected(val code: String) : ProjectAiStageAssistSettlementResult
    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean,
        val sameIntentReplayAllowed: Boolean,
        val idempotencyKey: String?,
    ) : ProjectAiStageAssistSettlementResult
}

/** Authenticated, selected-context-only mobile transport for D119 project-stage assistance. */
class ProjectAiStageAssistGateway(
    private val configuration: AiClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun registerLocalProjectContext(
        projectId: String,
        installationId: String,
        templateId: String,
        templateVersion: Int,
        projectRevision: Int,
        availableEvidenceIds: List<String>,
        explicitlyConfirmedForRequest: Boolean,
    ): ProjectAiLocalContextBindingResult {
        if (!explicitlyConfirmedForRequest) {
            return ProjectAiLocalContextBindingResult.Rejected("PROJECT_AI_REQUEST_CONSENT_REQUIRED")
        }
        if (!uuidPattern.matches(projectId)
            || !uuidPattern.matches(installationId)
            || !templateIdPattern.matches(templateId)
            || templateId.length > 96
            || templateVersion < 1
            || projectRevision < 1
            || availableEvidenceIds.size > MAX_REGISTERED_EVIDENCE_IDS
            || availableEvidenceIds.distinct().size != availableEvidenceIds.size
            || availableEvidenceIds.any { !evidenceIdPattern.matches(it) }
        ) return ProjectAiLocalContextBindingResult.Rejected("INVALID_PROJECT_AI_CONTEXT")

        val session = when (val state = readSession()) {
            is SessionResult.Ready -> state.value
            is SessionResult.Deferred -> return ProjectAiLocalContextBindingResult.Deferred(state.reason)
        }
        val body = buildJsonObject {
            put("schema", LOCAL_CONTEXT_SCHEMA)
            put("version", "1")
            put("installationId", installationId)
            put("templateId", templateId)
            put("templateVersion", templateVersion)
            put("projectRevision", projectRevision)
            put("availableEvidenceIds", buildJsonArray {
                availableEvidenceIds.sorted().forEach { add(JsonPrimitive(it)) }
            })
        }.toString()
        if (body.encodeToByteArray().size > MAX_LOCAL_CONTEXT_REQUEST_BYTES) {
            return ProjectAiLocalContextBindingResult.Rejected("INVALID_PROJECT_AI_CONTEXT")
        }
        val response = try {
            transport.request(
                method = "PUT",
                url = "${configuration.normalizedApiBaseUrl}/v1/project-ai/projects/$projectId/local-context",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.accessToken}",
                    PROJECT_AI_REQUEST_CONSENT_HEADER to PROJECT_AI_REQUEST_CONSENT_VERSION,
                    "Content-Type" to "application/json",
                ),
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return ProjectAiLocalContextBindingResult.Unavailable("PROJECT_AI_CONTEXT_SYNC_FAILED")
        }
        if (response.body.encodeToByteArray().size > MAX_LOCAL_CONTEXT_RESPONSE_BYTES) {
            return ProjectAiLocalContextBindingResult.Rejected("INVALID_PROJECT_AI_CONTEXT_RESPONSE")
        }
        when (response.statusCode) {
            401 -> return ProjectAiLocalContextBindingResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED)
            403 -> return ProjectAiLocalContextBindingResult.Rejected(errorCode(response) ?: "PROJECT_AI_CONSENT_REQUIRED")
            404 -> return ProjectAiLocalContextBindingResult.Rejected(errorCode(response) ?: "PROJECT_AI_TEMPLATE_NOT_READY")
            409 -> return ProjectAiLocalContextBindingResult.Rejected(errorCode(response) ?: "PROJECT_AI_CONTEXT_STALE")
            503 -> return ProjectAiLocalContextBindingResult.Unavailable(errorCode(response) ?: "PROJECT_AI_CONTEXT_UNAVAILABLE")
            in 200..299 -> Unit
            else -> return ProjectAiLocalContextBindingResult.Rejected(errorCode(response) ?: "PROJECT_AI_CONTEXT_REJECTED")
        }
        val registration = runCatching {
            val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("context response object required")
            require(root.keys == LOCAL_CONTEXT_RESPONSE_KEYS)
            require(root.requiredString("schema") == LOCAL_CONTEXT_SCHEMA)
            require(root.requiredString("version") == "1")
            require(root.requiredString("projectId") == projectId)
            require(root.requiredString("templateId") == templateId)
            require(root.requiredInt("templateVersion") == templateVersion)
            require(root.requiredString("status") in setOf("registered", "unchanged"))
            val revision = root.requiredInt("projectRevision").also { require(it == projectRevision) }
            val generation = root.requiredLong("bindingGeneration").also { require(it > 0) }
            revision to generation
        }.getOrNull() ?: return ProjectAiLocalContextBindingResult.Rejected("INVALID_PROJECT_AI_CONTEXT_RESPONSE")
        return ProjectAiLocalContextBindingResult.Registered(registration.first, registration.second)
    }

    suspend fun generatePreview(
        request: ProjectAiStageAssistRequest,
        idempotencyKey: String,
        explicitlyConfirmedForRequest: Boolean,
    ): ProjectAiStageAssistResult {
        if (!explicitlyConfirmedForRequest) {
            return ProjectAiStageAssistResult.Rejected("PROJECT_AI_REQUEST_CONSENT_REQUIRED")
        }
        if (!isValidRequest(request) || !requestIdPattern.matches(idempotencyKey)) {
            return ProjectAiStageAssistResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST")
        }
        val session = when (val state = readSession()) {
            is SessionResult.Ready -> state.value
            is SessionResult.Deferred -> return ProjectAiStageAssistResult.Deferred(state.reason)
        }
        val body = requestBody(request)
        if (body.encodeToByteArray().size > MAX_REQUEST_BYTES) {
            return ProjectAiStageAssistResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST")
        }
        val response = try {
            transport.request(
                method = "POST",
                url = "${configuration.normalizedApiBaseUrl}/v1/project-ai/stage-assist",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.accessToken}",
                    PROJECT_AI_REQUEST_CONSENT_HEADER to PROJECT_AI_REQUEST_CONSENT_VERSION,
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
            return ProjectAiStageAssistResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_RESPONSE")
        }
        if (isTransientStatus(response.statusCode)) {
            return failed(idempotencyKey, RemoteFailureKind.TRANSIENT_HTTP)
        }
        when (response.statusCode) {
            401 -> return ProjectAiStageAssistResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED)
            403 -> {
                val code = errorCode(response)
                val chargedCost = errorCreditCost(response)
                if (chargedCost != null) return ProjectAiStageAssistResult.Rejected(
                    code ?: "PROJECT_AI_RESPONSE_WITHHELD_AFTER_PROVIDER",
                    chargedCost,
                )
                return if (code == "PROJECT_AI_CONSENT_REQUIRED" || code == "PROJECT_AI_CONSENT_POLICY_STALE") {
                    ProjectAiStageAssistResult.Rejected(code)
                } else {
                    ProjectAiStageAssistResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED)
                }
            }
            503 -> return ProjectAiStageAssistResult.Unavailable(errorCode(response) ?: "PROJECT_AI_NOT_READY")
            in 200..299 -> Unit
            else -> return ProjectAiStageAssistResult.Rejected(
                errorCode(response) ?: "PROJECT_AI_STAGE_ASSIST_REJECTED",
                errorCreditCost(response),
            )
        }

        val parsed = parsePreview(response, request)
            ?: return ProjectAiStageAssistResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_RESPONSE")
        if (parsed.requestId != idempotencyKey) {
            return ProjectAiStageAssistResult.Rejected("PROJECT_AI_REQUEST_ID_MISMATCH")
        }
        if (parsed.projectId != request.projectId
            || parsed.baseProjectRevision != request.baseProjectRevision
            || parsed.projectBindingGeneration != request.baseProjectBindingGeneration
            || parsed.stageId != request.stageId
            || parsed.operationId != request.operationId
            || parsed.templateId != request.templateId
            || parsed.templateVersion != request.templateVersion
        ) {
            return ProjectAiStageAssistResult.Rejected("PROJECT_AI_CONTEXT_MISMATCH")
        }
        if (parsed.items.any { item ->
                item.kind == ProjectAiStageAssistItemKind.PROPOSAL &&
                    request.selectedFieldValues[item.targetFieldId] != item.beforeValue
            }
        ) {
            return ProjectAiStageAssistResult.Rejected("PROJECT_AI_CONTEXT_STALE")
        }
        return ProjectAiStageAssistResult.Preview(parsed)
    }

    suspend fun settle(
        requestId: String,
        installationId: String,
        outcome: ProjectAiStageAssistOutcome,
        baseProjectRevision: Int,
        baseProjectBindingGeneration: Long,
        resultProjectRevision: Int?,
        resultProjectBindingGeneration: Long?,
        expectedCreditCost: Int,
    ): ProjectAiStageAssistSettlementResult {
        if (!requestIdPattern.matches(requestId)
            || !uuidPattern.matches(installationId)
            || baseProjectRevision < 1
            || baseProjectBindingGeneration < 1
            || expectedCreditCost !in 1..PROJECT_AI_MAX_CREDITS_PER_REQUEST
            || when (outcome) {
                ProjectAiStageAssistOutcome.APPLIED,
                ProjectAiStageAssistOutcome.EDITED,
                -> resultProjectRevision?.let { it > baseProjectRevision } != true
                    || resultProjectBindingGeneration?.let { it > baseProjectBindingGeneration } != true
                ProjectAiStageAssistOutcome.DISMISSED,
                ProjectAiStageAssistOutcome.STALE,
                -> resultProjectRevision != null || resultProjectBindingGeneration != null
            }
        ) {
            return ProjectAiStageAssistSettlementResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT")
        }
        val session = when (val state = readSession()) {
            is SessionResult.Ready -> state.value
            is SessionResult.Deferred -> return ProjectAiStageAssistSettlementResult.Deferred(state.reason)
        }
        val body = buildJsonObject {
            put("schema", SETTLEMENT_SCHEMA)
            put("version", "1")
            put("installationId", installationId)
            put("requestId", requestId)
            put("outcome", outcome.wireValue)
            resultProjectRevision?.let { put("resultProjectRevision", it) }
            resultProjectBindingGeneration?.let { put("resultProjectBindingGeneration", it) }
        }.toString()
        if (body.encodeToByteArray().size > MAX_SETTLEMENT_REQUEST_BYTES) {
            return ProjectAiStageAssistSettlementResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT")
        }
        val response = try {
            transport.request(
                method = "POST",
                url = "${configuration.normalizedApiBaseUrl}/v1/project-ai/stage-assist/settlement",
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
            return failedSettlement(requestId, RemoteFailureKind.TRANSPORT)
        }
        if (response.body.encodeToByteArray().size > MAX_SETTLEMENT_RESPONSE_BYTES) {
            return ProjectAiStageAssistSettlementResult.Rejected(
                "INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT_RESPONSE",
            )
        }
        if (isTransientStatus(response.statusCode)) {
            return failedSettlement(requestId, RemoteFailureKind.TRANSIENT_HTTP)
        }
        when (response.statusCode) {
            401 -> return ProjectAiStageAssistSettlementResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED)
            403 -> return ProjectAiStageAssistSettlementResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED)
            503 -> return ProjectAiStageAssistSettlementResult.Unavailable(
                errorCode(response) ?: "PROJECT_AI_SETTLEMENT_NOT_READY",
            )
            in 200..299 -> Unit
            else -> return ProjectAiStageAssistSettlementResult.Rejected(
                errorCode(response) ?: "PROJECT_AI_SETTLEMENT_REJECTED",
            )
        }
        val parsed = parseSettlement(response)
            ?: return ProjectAiStageAssistSettlementResult.Rejected(
                "INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT_RESPONSE",
            )
        if (parsed.requestId != requestId
            || parsed.outcome != outcome
            || parsed.creditCost != expectedCreditCost
        ) {
            return ProjectAiStageAssistSettlementResult.Rejected(
                "PROJECT_AI_SETTLEMENT_CONTEXT_MISMATCH",
            )
        }
        return ProjectAiStageAssistSettlementResult.Settled(requestId, outcome, parsed.creditCost)
    }

    private fun readSession(): SessionResult {
        if (!configuration.isConfigured) {
            return SessionResult.Deferred(ProjectAiStageAssistDeferredReason.NOT_CONFIGURED)
        }
        val stored = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return SessionResult.Deferred(ProjectAiStageAssistDeferredReason.SECURE_STORAGE)
        } ?: return SessionResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED)
        if (!stored.account.emailVerified) {
            return SessionResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED)
        }
        if (stored.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return SessionResult.Deferred(ProjectAiStageAssistDeferredReason.SESSION_EXPIRED)
        }
        return SessionResult.Ready(stored.material)
    }

    private fun requestBody(request: ProjectAiStageAssistRequest): String = buildJsonObject {
        put("schema", REQUEST_SCHEMA)
        put("version", "1")
        put("mode", "PROJECT")
        put("installationId", request.installationId)
        put("locale", request.locale)
        put("projectId", request.projectId)
        put("templateId", request.templateId)
        put("templateVersion", request.templateVersion)
        put("stageId", request.stageId)
        put("operationId", request.operationId)
        put("baseProjectRevision", request.baseProjectRevision)
        put("projectBindingGeneration", request.baseProjectBindingGeneration)
        put("selectedFieldIds", buildJsonArray {
            request.selectedFieldValues.keys.sorted().forEach { add(JsonPrimitive(it)) }
        })
        put("selectedFields", buildJsonArray {
            request.selectedFieldValues.entries.sortedBy { it.key }.forEach { (id, value) ->
                add(buildJsonObject {
                    put("id", id)
                    put("value", value)
                })
            }
        })
        put("selectedEvidenceIds", buildJsonArray {
            request.selectedEvidence.map(ProjectAiStageAssistSelectedEvidence::id).sorted().forEach { add(JsonPrimitive(it)) }
        })
        put("selectedEvidence", buildJsonArray {
            request.selectedEvidence.sortedBy(ProjectAiStageAssistSelectedEvidence::id).forEach { evidence ->
                add(buildJsonObject {
                    put("id", evidence.id)
                    put("kind", evidence.kind)
                    put("label", evidence.label)
                    put("summary", evidence.summary?.let(::JsonPrimitive) ?: JsonNull)
                    put("origin", evidence.origin?.let(::JsonPrimitive) ?: JsonNull)
                })
            }
        })
    }.toString()

    private fun parsePreview(
        response: AccountHttpResponse,
        request: ProjectAiStageAssistRequest,
    ): ProjectAiStageAssistPreview? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("response object required")
        require(root.keys == RESPONSE_KEYS)
        require(root.string("schema") == REQUEST_SCHEMA && root.string("version") == "1")
        require(root.string("status") == "preview" && root.string("mode") == "PROJECT")
        val assist = root.objectValue("assist")
        require(assist.keys == ASSIST_KEYS)
        val templateId = assist.requiredString("templateId").also {
            require(it.length <= 96 && templateIdPattern.matches(it))
        }
        val templateVersion = assist.requiredInt("templateVersion").also { require(it > 0) }
        require(assist.requiredString("promptVersion") == PROMPT_VERSION)
        val items = assist.array("items").map(::parseItem)
        require(items.isNotEmpty() && items.size <= 24)
        require(items.map(ProjectAiStageAssistItem::id).distinct().size == items.size)
        val selectedEvidence = request.selectedEvidence.associateBy(ProjectAiStageAssistSelectedEvidence::id)
        require(items.all { item -> item.referenceIds.all(selectedEvidence::containsKey) })
        val selectedFields = request.selectedFieldValues
        require(items.filter { it.kind == ProjectAiStageAssistItemKind.PROPOSAL }.all { item ->
            item.targetFieldId in selectedFields
        })
        val reportedConflicts = assist.stringList("reportedConflicts", maxCount = 8, maxLength = 800)
        val reportedOutOfScopeItems = assist.stringList("reportedOutOfScopeItems", maxCount = 8, maxLength = 800)

        val evaluation = root.objectValue("evaluationPreview")
        require(evaluation.keys == EVALUATION_KEYS)
        require(evaluation.requiredString("assessmentStatus") == "NOT_ASSESSED")
        val supportingItems = evaluation.array("supportingItems").map { element ->
            val item = element as? JsonObject ?: error("supporting evidence object required")
            require(item.keys == EVIDENCE_REFERENCE_KEYS)
            val id = item.requiredString("id")
            val kind = item.requiredString("kind")
            val label = item.requiredString("label")
            val expected = selectedEvidence[id] ?: error("unselected evidence reference")
            require(kind == expected.kind && label == expected.label)
            ProjectAiStageAssistEvidenceReference(id, kind, label)
        }
        require(supportingItems.size <= 32)
        require(supportingItems.map(ProjectAiStageAssistEvidenceReference::id).distinct().size == supportingItems.size)
        val referencedEvidenceIds = items
            .filter { it.kind == ProjectAiStageAssistItemKind.PROPOSAL }
            .flatMap(ProjectAiStageAssistItem::referenceIds)
            .toSet()
        require(supportingItems.map(ProjectAiStageAssistEvidenceReference::id).toSet() == referencedEvidenceIds)
        val proposalsWithoutReferences = evaluation.stringList("proposalsWithoutReferences", 24, 80)
        val proposalIdsWithoutReferences = items
            .filter { it.kind == ProjectAiStageAssistItemKind.PROPOSAL && it.referenceIds.isEmpty() }
            .map(ProjectAiStageAssistItem::id)
            .toSet()
        require(proposalsWithoutReferences.toSet() == proposalIdsWithoutReferences)
        val checksUnavailable = evaluation.stringList("checksUnavailable", 4, 64)
        require(checksUnavailable == UNAVAILABLE_CHECKS)
        val evaluationPreview = ProjectAiStageAssistEvaluationPreview(
            assessmentStatus = "NOT_ASSESSED",
            supportingItems = supportingItems,
            proposalsWithoutReferences = proposalsWithoutReferences,
            reportedConflicts = evaluation.stringList("reportedConflicts", 8, 800),
            reportedAssumptions = evaluation.stringList("reportedAssumptions", 24, 800),
            reportedUncertainties = evaluation.stringList("reportedUncertainties", 24, 800),
            reportedKnownLimits = evaluation.stringList("reportedKnownLimits", 24, 800),
            templateLimits = evaluation.stringList("templateLimits", 24, 1200),
            reportedOutOfScopeItems = evaluation.stringList("reportedOutOfScopeItems", 8, 800),
        )
        require(evaluationPreview.reportedConflicts == reportedConflicts)
        require(evaluationPreview.reportedOutOfScopeItems == reportedOutOfScopeItems)
        ProjectAiStageAssistPreview(
            projectId = root.requiredString("projectId").also { require(uuidPattern.matches(it)) },
            baseProjectRevision = root.requiredInt("baseProjectRevision").also { require(it > 0) },
            projectBindingGeneration = root.requiredLong("projectBindingGeneration").also { require(it > 0) },
            consentGeneration = root.requiredInt("consentGeneration").also { require(it > 0) },
            stageId = root.requiredString("stageId").also {
                require(it.length <= 80 && identifierPattern.matches(it))
            },
            operationId = root.requiredString("operationId").also {
                require(it.length <= 80 && identifierPattern.matches(it))
            },
            templateId = templateId,
            templateVersion = templateVersion,
            items = items,
            reportedConflicts = reportedConflicts,
            reportedOutOfScopeItems = reportedOutOfScopeItems,
            evaluationPreview = evaluationPreview,
            requestId = root.requiredString("requestId").also {
                require(it.length <= 128 && requestIdPattern.matches(it))
            },
            creditCost = root.requiredInt("creditCost").also { require(it in 1..PROJECT_AI_MAX_CREDITS_PER_REQUEST) },
        )
    }.getOrNull()

    private fun parseItem(element: kotlinx.serialization.json.JsonElement): ProjectAiStageAssistItem {
        val item = element as? JsonObject ?: error("assist item object required")
        require(item.keys == ITEM_KEYS)
        val kind = when (item.requiredString("kind")) {
            "EXPLANATION" -> ProjectAiStageAssistItemKind.EXPLANATION
            "QUESTION" -> ProjectAiStageAssistItemKind.QUESTION
            "PROPOSAL" -> ProjectAiStageAssistItemKind.PROPOSAL
            else -> error("unknown assist item kind")
        }
        val text = item.nullableString("text")
        val targetFieldId = item.nullableString("targetFieldId")
        val beforeValue = item.nullableString("beforeValue")
        val afterValue = item.nullableString("afterValue")
        val referenceIds = item.stringList("referenceIds", 32, 64)
        val assumptions = item.stringList("assumptions", 8, 800)
        val uncertainties = item.stringList("uncertainties", 8, 800)
        val knownLimits = item.stringList("knownLimits", 8, 800)
        when (kind) {
            ProjectAiStageAssistItemKind.EXPLANATION,
            ProjectAiStageAssistItemKind.QUESTION,
            -> {
                require(!text.isNullOrBlank() && text.length <= 2_000)
                require(targetFieldId == null && beforeValue == null && afterValue == null)
                require(referenceIds.isEmpty() && assumptions.isEmpty() && uncertainties.isEmpty() && knownLimits.isEmpty())
            }
            ProjectAiStageAssistItemKind.PROPOSAL -> {
                require(text == null)
                require(targetFieldId != null && targetFieldId.length <= 80 && identifierPattern.matches(targetFieldId))
                require(beforeValue != null && beforeValue.length <= 8_000)
                require(!afterValue.isNullOrBlank() && afterValue.length <= 8_000)
            }
        }
        return ProjectAiStageAssistItem(
            id = item.requiredString("id").also {
                require(it.length <= 80 && identifierPattern.matches(it))
            },
            kind = kind,
            text = text,
            targetFieldId = targetFieldId,
            beforeValue = beforeValue,
            afterValue = afterValue,
            referenceIds = referenceIds.also { require(it.distinct().size == it.size) },
            assumptions = assumptions,
            uncertainties = uncertainties,
            knownLimits = knownLimits,
        )
    }

    private fun parseSettlement(response: AccountHttpResponse): ParsedSettlement? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("settlement object required")
        require(root.keys == SETTLEMENT_RESPONSE_KEYS)
        require(root.requiredString("schema") == SETTLEMENT_SCHEMA)
        require(root.requiredString("version") == "1")
        val outcome = ProjectAiStageAssistOutcome.entries.singleOrNull {
            it.wireValue == root.requiredString("outcome")
        } ?: error("settlement outcome invalid")
        ParsedSettlement(
            requestId = root.requiredString("requestId").also { require(requestIdPattern.matches(it)) },
            outcome = outcome,
            creditCost = root.requiredInt("creditCost").also { require(it in 1..PROJECT_AI_MAX_CREDITS_PER_REQUEST) },
        )
    }.getOrNull()

    private fun isValidRequest(request: ProjectAiStageAssistRequest): Boolean =
        uuidPattern.matches(request.installationId)
            && uuidPattern.matches(request.projectId)
            && request.templateId.length <= 96
            && templateIdPattern.matches(request.templateId)
            && request.templateVersion > 0
            && request.stageId.length <= 80
            && identifierPattern.matches(request.stageId)
            && request.operationId.length <= 80
            && identifierPattern.matches(request.operationId)
            && request.baseProjectRevision > 0
            && request.baseProjectBindingGeneration > 0
            && request.selectedFieldValues.size <= 32
            && request.selectedFieldValues.all { (id, value) ->
                id.length <= 80 && identifierPattern.matches(id) && value.length <= 8_000 && '\u0000' !in value
            }
            && request.selectedEvidence.size <= 32
            && request.selectedEvidence.map(ProjectAiStageAssistSelectedEvidence::id).distinct().size == request.selectedEvidence.size
            && request.selectedEvidence.all { evidence ->
                evidenceIdPattern.matches(evidence.id)
                    && evidence.kind in EVIDENCE_KINDS
                    && evidence.label.isNotBlank()
                    && evidence.label.length <= 160
                    && '\u0000' !in evidence.label
                    && (evidence.summary == null || evidence.summary.length <= 8_000 && '\u0000' !in evidence.summary)
                    && (evidence.origin == null || evidence.origin.length <= 500 && '\u0000' !in evidence.origin)
            }
            && request.locale.length <= 32
            && localePattern.matches(request.locale)

    private fun isTransientStatus(statusCode: Int): Boolean =
        statusCode in setOf(408, 425, 429) || (statusCode in 500..599 && statusCode != 503)

    private fun failed(
        key: String,
        failure: RemoteFailureKind,
    ): ProjectAiStageAssistResult.Failed {
        val resolution = resolveRemoteFailure(
            connectivity = transport.deviceConnectivity,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            failure = failure,
            requestWasDispatched = true,
            idempotencyKey = key,
        )
        val code = when (resolution.state) {
            RemoteFailureState.OFFLINE -> "PROJECT_AI_OFFLINE"
            RemoteFailureState.OUTCOME_UNKNOWN -> "PROJECT_AI_OUTCOME_UNKNOWN"
            RemoteFailureState.SERVICE_UNAVAILABLE -> "PROJECT_AI_UNAVAILABLE"
            RemoteFailureState.REJECTED -> "PROJECT_AI_STAGE_ASSIST_REJECTED"
            RemoteFailureState.CANCELLED -> "PROJECT_AI_CANCELLED"
        }
        return ProjectAiStageAssistResult.Failed(
            code,
            retryable = resolution.retryAllowed,
            outcomeUnknown = resolution.state == RemoteFailureState.OUTCOME_UNKNOWN,
            sameIntentReplayAllowed = resolution.sameIntentReplayAllowed,
            idempotencyKey = key.takeIf { resolution.sameIntentReplayAllowed },
        )
    }

    private fun failedSettlement(
        key: String,
        failure: RemoteFailureKind,
    ): ProjectAiStageAssistSettlementResult.Failed {
        val resolution = resolveRemoteFailure(
            connectivity = transport.deviceConnectivity,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            failure = failure,
            requestWasDispatched = true,
            idempotencyKey = key,
        )
        val code = when (resolution.state) {
            RemoteFailureState.OFFLINE -> "PROJECT_AI_SETTLEMENT_OFFLINE"
            RemoteFailureState.OUTCOME_UNKNOWN -> "PROJECT_AI_SETTLEMENT_OUTCOME_UNKNOWN"
            RemoteFailureState.SERVICE_UNAVAILABLE -> "PROJECT_AI_SETTLEMENT_UNAVAILABLE"
            RemoteFailureState.REJECTED -> "PROJECT_AI_SETTLEMENT_REJECTED"
            RemoteFailureState.CANCELLED -> "PROJECT_AI_SETTLEMENT_CANCELLED"
        }
        return ProjectAiStageAssistSettlementResult.Failed(
            code,
            retryable = resolution.retryAllowed,
            outcomeUnknown = resolution.state == RemoteFailureState.OUTCOME_UNKNOWN,
            sameIntentReplayAllowed = resolution.sameIntentReplayAllowed,
            idempotencyKey = key.takeIf { resolution.sameIntentReplayAllowed },
        )
    }

    private fun errorCode(response: AccountHttpResponse): String? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        root.string("code")?.takeIf(reasonCodePattern::matches)
    }.getOrNull()

    private fun errorCreditCost(response: AccountHttpResponse): Int? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        root.requiredInt("creditCost").takeIf { it in 1..PROJECT_AI_MAX_CREDITS_PER_REQUEST }
    }.getOrNull()

    private data class ParsedSettlement(
        val requestId: String,
        val outcome: ProjectAiStageAssistOutcome,
        val creditCost: Int,
    )

    private sealed interface SessionResult {
        data class Ready(val value: SecureSessionMaterial) : SessionResult
        data class Deferred(val reason: ProjectAiStageAssistDeferredReason) : SessionResult
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.requiredString(name: String): String =
        string(name)?.also { require('\u0000' !in it) } ?: error("$name required")

    private fun JsonObject.nullableString(name: String): String? = when (val value = this[name]) {
        JsonNull -> null
        is JsonPrimitive -> value.contentOrNull?.also { require('\u0000' !in it) }
        else -> error("$name must be a string or null")
    }

    private fun JsonObject.requiredInt(name: String): Int =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull() ?: error("$name required")

    private fun JsonObject.requiredLong(name: String): Long =
        (this[name] as? JsonPrimitive)?.content?.toLongOrNull() ?: error("$name required")

    private fun JsonObject.objectValue(name: String): JsonObject =
        this[name] as? JsonObject ?: error("$name object required")

    private fun JsonObject.array(name: String): JsonArray =
        this[name] as? JsonArray ?: error("$name array required")

    private fun JsonObject.stringList(name: String, maxCount: Int, maxLength: Int): List<String> {
        val values = getValue(name).jsonArray.map { element ->
            (element as? JsonPrimitive)?.contentOrNull
                ?.also { require(it.isNotBlank() && it.length <= maxLength && '\u0000' !in it) }
                ?: error("$name item invalid")
        }
        require(values.size <= maxCount)
        return values
    }

    private companion object {
        const val REQUEST_SCHEMA = "evidrilo.project-ai-stage-assist"
        const val SETTLEMENT_SCHEMA = "evidrilo.project-ai-stage-assist-settlement"
        const val LOCAL_CONTEXT_SCHEMA = "evidrilo.project-ai-local-project-context"
        const val PROJECT_AI_REQUEST_CONSENT_HEADER = "X-Evidrilo-Project-AI-Consent"
        const val PROJECT_AI_REQUEST_CONSENT_VERSION = "project-ai.v1"
        const val PROMPT_VERSION = "project-ai-stage-assist.v1"
        const val MAX_REQUEST_BYTES = 16 * 1024
        const val MAX_RESPONSE_BYTES = 320 * 1024
        const val MAX_SETTLEMENT_REQUEST_BYTES = 4 * 1024
        const val MAX_SETTLEMENT_RESPONSE_BYTES = 16 * 1024
        const val MAX_LOCAL_CONTEXT_REQUEST_BYTES = 64 * 1024
        const val MAX_LOCAL_CONTEXT_RESPONSE_BYTES = 4 * 1024
        const val MAX_REGISTERED_EVIDENCE_IDS = 2_048
        val RESPONSE_KEYS = setOf(
            "schema", "version", "status", "mode", "projectId", "baseProjectRevision", "projectBindingGeneration", "consentGeneration",
            "stageId", "operationId", "assist", "evaluationPreview", "requestId", "creditCost",
        )
        val ASSIST_KEYS = setOf(
            "templateId", "templateVersion", "promptVersion", "items", "reportedConflicts", "reportedOutOfScopeItems",
        )
        val ITEM_KEYS = setOf(
            "id", "kind", "text", "targetFieldId", "beforeValue", "afterValue", "referenceIds",
            "assumptions", "uncertainties", "knownLimits",
        )
        val EVALUATION_KEYS = setOf(
            "assessmentStatus", "supportingItems", "proposalsWithoutReferences", "reportedConflicts",
            "reportedAssumptions", "reportedUncertainties", "reportedKnownLimits", "templateLimits",
            "reportedOutOfScopeItems", "checksUnavailable",
        )
        val EVIDENCE_REFERENCE_KEYS = setOf("id", "kind", "label")
        val SETTLEMENT_RESPONSE_KEYS = setOf("schema", "version", "outcome", "requestId", "creditCost")
        val LOCAL_CONTEXT_RESPONSE_KEYS = setOf(
            "schema", "version", "projectId", "projectRevision", "bindingGeneration", "templateId", "templateVersion", "status", "requestId",
        )
        val EVIDENCE_KINDS = setOf("SOURCE", "DATA", "OBSERVATION")
        val UNAVAILABLE_CHECKS = listOf(
            "ACADEMIC_TRUTH", "SEMANTIC_REFERENCE_SUPPORT", "SOURCE_QUALITY", "POST_APPLY_STRUCTURE",
        )
        val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        val identifierPattern = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
        val evidenceIdPattern = Regex("^[A-Za-z0-9_-]{1,64}$")
        val templateIdPattern = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
        val localePattern = Regex("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$")
        val reasonCodePattern = Regex("^[A-Z0-9_]{3,64}$")
        val requestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
    }
}
