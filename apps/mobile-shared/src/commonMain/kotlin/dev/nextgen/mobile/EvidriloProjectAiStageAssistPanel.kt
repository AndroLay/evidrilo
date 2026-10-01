package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistOutcome
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistPreview
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistRequest
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistSelectedEvidence

internal data class ProjectAiStageAssistContextIdentity(
    val accountId: String? = null,
    val projectId: String,
    val templateId: String,
    val templateVersion: Int,
    val projectRevision: Int,
    val stageId: String,
    val operationId: String,
) {
    fun sameStage(other: ProjectAiStageAssistContextIdentity): Boolean =
        accountId == other.accountId && projectId == other.projectId && templateId == other.templateId &&
            templateVersion == other.templateVersion && stageId == other.stageId && operationId == other.operationId
}

internal fun canApplyProjectAiStageAssistPreview(
    previewContext: ProjectAiStageAssistContextIdentity,
    currentContext: ProjectAiStageAssistContextIdentity,
    isDirty: Boolean,
    hasSelectedProposals: Boolean,
): Boolean = !isDirty && previewContext == currentContext && hasSelectedProposals

internal fun canReviewProjectAiStageAssistPreview(
    previewContext: ProjectAiStageAssistContextIdentity,
    currentContext: ProjectAiStageAssistContextIdentity,
    isDirty: Boolean,
): Boolean = !isDirty && previewContext == currentContext

internal fun canDismissProjectAiStageAssistPreview(
    previewContext: ProjectAiStageAssistContextIdentity,
    currentContext: ProjectAiStageAssistContextIdentity,
): Boolean = previewContext.sameStage(currentContext)

internal data class ProjectAiStageAssistSession(
    val accountId: String,
    val projectId: String,
    val templateId: String,
    val templateVersion: Int,
    val projectRevision: Int,
    val bindingGeneration: Long,
    val stageId: String,
    val operationId: String,
    val selectedFieldValues: Map<String, String>,
    val selectedEvidence: List<ProjectAiStageAssistSelectedEvidence>,
    val preview: ProjectAiStageAssistPreview,
) {
    val contextIdentity: ProjectAiStageAssistContextIdentity
        get() = ProjectAiStageAssistContextIdentity(
            accountId = accountId,
            projectId = projectId,
            templateId = templateId,
            templateVersion = templateVersion,
            projectRevision = projectRevision,
            stageId = stageId,
            operationId = operationId,
        )
}

internal sealed interface ProjectAiStageAssistUiState {
    data object Idle : ProjectAiStageAssistUiState
    data class Requesting(val context: ProjectAiStageAssistContextIdentity, val stageTitle: String) : ProjectAiStageAssistUiState
    data class OutcomeUnknown(
        val accountId: String,
        val context: ProjectAiStageAssistContextIdentity,
        val stageTitle: String,
        val request: ProjectAiStageAssistRequest,
        val idempotencyKey: String?,
        val consentGeneration: Long,
    ) : ProjectAiStageAssistUiState
    data class Preview(val session: ProjectAiStageAssistSession) : ProjectAiStageAssistUiState
    data class Settling(val message: String, val context: ProjectAiStageAssistContextIdentity? = null) : ProjectAiStageAssistUiState
    data class SettlementFailed(
        val session: ProjectAiStageAssistSession,
        val outcome: ProjectAiStageAssistOutcome,
        val resultProjectRevision: Int?,
        val resultBindingGeneration: Long?,
        val message: String,
        val canRetry: Boolean,
    ) : ProjectAiStageAssistUiState
    data class Unavailable(val message: String, val context: ProjectAiStageAssistContextIdentity? = null) : ProjectAiStageAssistUiState
    data class Rejected(val message: String, val context: ProjectAiStageAssistContextIdentity? = null) : ProjectAiStageAssistUiState
}

@Composable
internal fun EvidriloProjectAiStageAssistPanel(
    draft: StudentProjectDraft,
    accountId: String?,
    step: ProjectTemplateStep,
    fields: List<ProjectTemplateInputField>,
    values: Map<String, String>,
    evidenceChoices: List<ProjectAiStageAssistSelectedEvidence>,
    state: ProjectAiStageAssistUiState,
    aiCreditBalance: AiCreditBalancePresentation = AiCreditBalancePresentation.SignInRequired,
    consentState: ProjectAiConsentUiState,
    isDirty: Boolean,
    accountAvailable: Boolean,
    accountMessage: String?,
    onRefreshAiCreditBalance: () -> Unit = {},
    onRefreshConsent: () -> Unit,
    onGrantConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
    onRequest: (String, Map<String, String>, List<ProjectAiStageAssistSelectedEvidence>) -> Unit,
    onApply: (ProjectAiStageAssistSession, Set<String>, Map<String, String>) -> Unit,
    onDismiss: (ProjectAiStageAssistSession) -> Unit,
    onRetrySettlement: (ProjectAiStageAssistUiState.SettlementFailed) -> Unit,
    onRetryUnknownRequest: (ProjectAiStageAssistUiState.OutcomeUnknown) -> Unit,
) {
    if (step.aiOperations.isEmpty()) return
    val templateSnapshot = requireNotNull(draft.templateSnapshot)

    var operationId by remember(draft.id, step.id, templateSnapshot.version) {
        mutableStateOf(step.aiOperations.first().id)
    }
    val operation = step.aiOperations.singleOrNull { it.id == operationId } ?: step.aiOperations.first()
    LaunchedEffect(accountAvailable, draft.id, draft.revision, step.id, operation.id) {
        if (accountAvailable) onRefreshAiCreditBalance()
    }
    val currentContext = ProjectAiStageAssistContextIdentity(
        accountId = accountId,
        projectId = draft.id,
        templateId = templateSnapshot.id,
        templateVersion = templateSnapshot.version,
        projectRevision = draft.revision,
        stageId = step.id,
        operationId = operation.id,
    )
    val selectedInputFields = remember(step.id, operation.id, fields) {
        fields.filter { it.id in operation.inputFieldIds && it.kind !in setOf(ProjectTemplateInputKind.SOURCE, ProjectTemplateInputKind.DATA) }
    }
    val availableFields = selectedInputFields.filter { !values[it.id].isNullOrBlank() }
    val allowsSources = operation.inputFieldIds.any { id -> fields.any { it.id == id && it.kind == ProjectTemplateInputKind.SOURCE } }
    val allowsData = operation.inputFieldIds.any { id -> fields.any { it.id == id && it.kind == ProjectTemplateInputKind.DATA } }
    val availableEvidence = evidenceChoices.filter { evidence ->
        when (evidence.kind) {
            "SOURCE" -> allowsSources
            "DATA", "OBSERVATION" -> allowsData
            else -> false
        }
    }.take(32)
    var selectedFieldIds by remember(draft.id, step.id, operation.id, draft.revision) { mutableStateOf(emptySet<String>()) }
    var selectedEvidenceIds by remember(draft.id, step.id, operation.id, draft.revision) { mutableStateOf(emptySet<String>()) }
    var requestConsent by remember(draft.id, step.id, operation.id, draft.revision) { mutableStateOf(false) }
    val visibleState = when (state) {
        is ProjectAiStageAssistUiState.Requesting -> state.takeIf { it.context.sameStage(currentContext) }
        is ProjectAiStageAssistUiState.OutcomeUnknown -> state.takeIf { it.context.sameStage(currentContext) }
        is ProjectAiStageAssistUiState.Preview -> state.takeIf { it.session.contextIdentity.sameStage(currentContext) }
        is ProjectAiStageAssistUiState.Settling -> state.takeIf { it.context == null || it.context.sameStage(currentContext) }
        is ProjectAiStageAssistUiState.SettlementFailed -> state.takeIf { it.session.contextIdentity.sameStage(currentContext) }
        is ProjectAiStageAssistUiState.Unavailable -> state.takeIf { it.context == null || it.context.sameStage(currentContext) }
        is ProjectAiStageAssistUiState.Rejected -> state.takeIf { it.context == null || it.context.sameStage(currentContext) }
        ProjectAiStageAssistUiState.Idle -> state
    } ?: ProjectAiStageAssistUiState.Idle
    val session = (visibleState as? ProjectAiStageAssistUiState.Preview)?.session
    val hasUnresolvedRequest = state is ProjectAiStageAssistUiState.Requesting ||
        state is ProjectAiStageAssistUiState.OutcomeUnknown ||
        state is ProjectAiStageAssistUiState.Preview ||
        state is ProjectAiStageAssistUiState.Settling ||
        state is ProjectAiStageAssistUiState.SettlementFailed
    val blockedByUnresolvedRequest = hasUnresolvedRequest && visibleState == ProjectAiStageAssistUiState.Idle
    var selectedProposalIds by remember(session?.preview?.requestId) { mutableStateOf(emptySet<String>()) }
    var editedValues by remember(session?.preview?.requestId) {
        mutableStateOf(session?.preview?.items.orEmpty()
            .filter { it.kind.name == "PROPOSAL" }
            .associate { it.id to it.afterValue.orEmpty() })
    }
    val busy = visibleState is ProjectAiStageAssistUiState.Requesting ||
        visibleState is ProjectAiStageAssistUiState.OutcomeUnknown ||
        visibleState is ProjectAiStageAssistUiState.Settling

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("AI help for this stage", style = MaterialTheme.typography.titleMedium)
            EvidriloAiCreditBalancePanel(
                presentation = aiCreditBalance,
                onRefresh = onRefreshAiCreditBalance,
            )
            Text(
                "${draft.title} · ${step.title}",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
            Text(
                "Suggestions only—not a grade or verification. You decide what, if anything, to apply.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
            if (blockedByUnresolvedRequest) {
                Text(
                    "A previous Project AI result is still awaiting review or reconciliation. Return to its project and stage, or sign in to the account that requested it. New Project AI requests stay paused until it is resolved.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (visibleState !is ProjectAiStageAssistUiState.Preview && !busy &&
                visibleState !is ProjectAiStageAssistUiState.SettlementFailed
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    step.aiOperations.forEach { candidate ->
                        FilterChip(
                            selected = candidate.id == operation.id,
                            onClick = {
                                operationId = candidate.id
                                selectedFieldIds = emptySet()
                                selectedEvidenceIds = emptySet()
                                requestConsent = false
                            },
                            label = { RawText(candidate.id.replace('_', ' ')) },
                            enabled = !busy && !blockedByUnresolvedRequest,
                        )
                    }
                }

                Text("Choose the context to send", style = MaterialTheme.typography.labelLarge)
                availableFields.forEach { field ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = field.id in selectedFieldIds,
                            enabled = !busy && !blockedByUnresolvedRequest,
                            onCheckedChange = { checked ->
                                selectedFieldIds = if (checked) selectedFieldIds + field.id else selectedFieldIds - field.id
                            },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(field.label, style = MaterialTheme.typography.bodySmall)
                            if (field.id in selectedFieldIds) {
                                Text(values[field.id].orEmpty(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                availableEvidence.forEach { evidence ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = evidence.id in selectedEvidenceIds,
                            enabled = !busy && !blockedByUnresolvedRequest,
                            onCheckedChange = { checked ->
                                selectedEvidenceIds = if (checked) selectedEvidenceIds + evidence.id else selectedEvidenceIds - evidence.id
                            },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${evidence.label} · ${evidence.kind.lowercase()}", style = MaterialTheme.typography.bodySmall)
                            if (evidence.id in selectedEvidenceIds) {
                                evidence.summary?.let { Text("Summary: $it", style = MaterialTheme.typography.bodySmall) }
                                evidence.origin?.let { Text("Origin: $it", style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
                if (availableFields.isEmpty() && availableEvidence.isEmpty()) {
                    Text("Add a saved field or source/evidence note before asking for help in this stage.", style = MaterialTheme.typography.bodySmall)
                }

                when (consentState) {
                    ProjectAiConsentUiState.Unknown -> TextButton(onClick = onRefreshConsent) { Text("Check Project AI consent") }
                    ProjectAiConsentUiState.Checking -> Text("Checking saved consent…", style = MaterialTheme.typography.bodySmall)
                    ProjectAiConsentUiState.NotGranted -> Column {
                        Text("Project AI consent is separate and revocable. Granting it does not send project content by itself.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onGrantConsent) { Text("Allow Project AI processing") }
                    }
                    ProjectAiConsentUiState.Granted -> Column {
                        Text("Account consent is on. Only the choices below are sent for this request.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                        TextButton(onClick = onRevokeConsent) { Text("Revoke consent") }
                    }
                    is ProjectAiConsentUiState.Unavailable -> Column {
                        Text(consentState.message, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRefreshConsent) { Text("Check again") }
                    }
                }
                if (!accountAvailable) {
                    Text(accountMessage ?: "Sign in with a verified account to use Project AI. Manual project work stays available.", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = requestConsent,
                        enabled = consentState == ProjectAiConsentUiState.Granted && accountAvailable && !busy && !blockedByUnresolvedRequest,
                        onCheckedChange = { requestConsent = it },
                    )
                    Text(
                        "For this request, send only the selected fields and evidence notes, plus project/method/revision metadata for ownership and stale checks. Nothing is applied until I review it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(projectAiCreditDisclosure(creating = false), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                EvidriloPrimaryButton(
                    label = "Ask AI about this stage",
                    enabled = requestConsent && accountAvailable && consentState == ProjectAiConsentUiState.Granted &&
                        !isDirty && !busy && !blockedByUnresolvedRequest &&
                        (selectedFieldIds.isNotEmpty() || selectedEvidenceIds.isNotEmpty()),
                    onClick = {
                        val chosenFields = selectedFieldIds.associateWith { values[it].orEmpty() }
                        val chosenEvidence = availableEvidence.filter { it.id in selectedEvidenceIds }
                        requestConsent = false
                        onRequest(operation.id, chosenFields, chosenEvidence)
                    },
                )
                if (isDirty) Text("Save the current edits first; AI only uses the saved revision.", style = MaterialTheme.typography.bodySmall)
            }

            when (val current = visibleState) {
                ProjectAiStageAssistUiState.Idle -> Unit
                is ProjectAiStageAssistUiState.Requesting -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Reviewing selected context for ${current.stageTitle}…", style = MaterialTheme.typography.bodySmall)
                }
                is ProjectAiStageAssistUiState.OutcomeUnknown -> Column {
                    Text(
                        "The result of this request is unknown. Do not start a new request; retry the same request key to reconcile it without creating a second charge.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        enabled = current.idempotencyKey != null && accountAvailable && consentState == ProjectAiConsentUiState.Granted,
                        onClick = { onRetryUnknownRequest(current) },
                    ) { Text("Retry same request") }
                }
                is ProjectAiStageAssistUiState.Preview -> EvidriloReviewFocus("Review AI suggestions") {
                ProjectAiStageAssistPreviewCard(
                    session = current.session,
                    selectedProposalIds = selectedProposalIds,
                    editedValues = editedValues,
                    onProposalSelectionChanged = { selectedProposalIds = it },
                    onProposalValueChanged = { id, value -> editedValues = editedValues + (id to value) },
                    onApply = { onApply(current.session, selectedProposalIds, editedValues) },
                    onDismiss = { onDismiss(current.session) },
                    canReview = canReviewProjectAiStageAssistPreview(
                        current.session.contextIdentity,
                        currentContext,
                        isDirty,
                    ),
                    canApply = canApplyProjectAiStageAssistPreview(
                        current.session.contextIdentity,
                        currentContext,
                        isDirty,
                        selectedProposalIds.isNotEmpty(),
                    ) && !busy,
                    stale = current.session.projectRevision != draft.revision || isDirty,
                    canDismiss = canDismissProjectAiStageAssistPreview(current.session.contextIdentity, currentContext),
                )
                }
                is ProjectAiStageAssistUiState.Settling -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(current.message, style = MaterialTheme.typography.bodySmall)
                }
                is ProjectAiStageAssistUiState.SettlementFailed -> Column {
                    Text(current.message, style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = current.canRetry, onClick = { onRetrySettlement(current) }) { Text("Retry activity settlement") }
                }
                is ProjectAiStageAssistUiState.Unavailable -> Text(
                    "${current.message} Manual project work remains available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                is ProjectAiStageAssistUiState.Rejected -> Text(current.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProjectAiStageAssistPreviewCard(
    session: ProjectAiStageAssistSession,
    selectedProposalIds: Set<String>,
    editedValues: Map<String, String>,
    onProposalSelectionChanged: (Set<String>) -> Unit,
    onProposalValueChanged: (String, String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    canReview: Boolean,
    canApply: Boolean,
    stale: Boolean,
    canDismiss: Boolean,
) {
    val preview = session.preview
    val proposals = preview.items.filter { it.kind.name == "PROPOSAL" }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Review AI suggestions", style = MaterialTheme.typography.titleSmall)
        Text(
            "${preview.creditCost} shared AI credits · actual provider usage",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        Text(
            "No evaluation was run. These suggestions do not change evidence, sources, or project status.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        if (stale) {
            Text(
                "This preview is from an older or unsaved project revision. It cannot be applied; dismiss it to reconcile the already charged request.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
        }
        preview.items.filter { it.kind.name != "PROPOSAL" }.forEach { item ->
            RawText(item.text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
        proposals.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.id in selectedProposalIds,
                    enabled = canReview,
                    onCheckedChange = { checked ->
                        onProposalSelectionChanged(if (checked) selectedProposalIds + item.id else selectedProposalIds - item.id)
                    },
                )
                Text("${item.targetFieldId} · proposed change", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedTextField(
                value = editedValues[item.id].orEmpty(),
                onValueChange = { onProposalValueChanged(item.id, it.take(8_000)) },
                label = { Text("Review or edit") },
                modifier = Modifier.fillMaxWidth(),
                enabled = canReview,
                supportingText = { RawText(uiText("Current: ","Saat ini: ")+item.beforeValue.orEmpty().take(160)) },
            )
            if (item.referenceIds.isNotEmpty()) {
                Text("Selected references: ${item.referenceIds.joinToString()}", style = MaterialTheme.typography.bodySmall)
            }
            (item.uncertainties + item.knownLimits).distinct().forEach { limit ->
                RawText(uiText("Limit: ","Batasan: ")+limit, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
        }
        if (proposals.isEmpty()) {
            Text("This response contains guidance only; it has no field changes to apply.", style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            EvidriloPrimaryButton(
                label = "Apply selected",
                enabled = canApply && selectedProposalIds.isNotEmpty(),
                onClick = onApply,
            )
            TextButton(enabled = canDismiss, onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
