package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldProposal
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldDeferredReason
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldDecision
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentDeferralReason
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentGatewayResult
import dev.nextgen.mobile.projectcatalog.PROJECT_AI_CONSENT_POLICY_VERSION

internal sealed interface ProjectAiScaffoldUiState {
    data object Idle : ProjectAiScaffoldUiState
    data object Loading : ProjectAiScaffoldUiState
    data class Preview(
        val proposal: ProjectAiScaffoldProposal,
        val requestId: String,
        val creditCost: Int,
    ) : ProjectAiScaffoldUiState
    data class Settling(
        val requestId: String,
        val decision: ProjectAiScaffoldDecision,
        val creditCost: Int,
        val projectAlreadyApplied: Boolean,
    ) : ProjectAiScaffoldUiState
    data class SettlementFailed(
        val requestId: String,
        val decision: ProjectAiScaffoldDecision,
        val creditCost: Int,
        val projectAlreadyApplied: Boolean,
        val message: String,
        val canRetry: Boolean,
    ) : ProjectAiScaffoldUiState
    data class Unavailable(val message: String, val retryable: Boolean = false) : ProjectAiScaffoldUiState
}

internal sealed interface ProjectAiConsentUiState {
    data object Unknown : ProjectAiConsentUiState
    data object Checking : ProjectAiConsentUiState
    data object Granted : ProjectAiConsentUiState
    data object NotGranted : ProjectAiConsentUiState
    data class Unavailable(val message: String) : ProjectAiConsentUiState
}

/** UI safeguard: a pending AI proposal belongs only to the account that requested it. */
internal fun projectAiSessionMatchesOwner(ownerAccountId: String?, currentAccountId: String?): Boolean =
    ownerAccountId != null && currentAccountId != null && ownerAccountId == currentAccountId

internal fun ProjectAiConsentGatewayResult.toProjectAiConsentUiState(): ProjectAiConsentUiState = when (this) {
    is ProjectAiConsentGatewayResult.State -> when {
        !value.granted -> ProjectAiConsentUiState.NotGranted
        value.policyVersion != PROJECT_AI_CONSENT_POLICY_VERSION -> ProjectAiConsentUiState.Unavailable(
            "The saved consent policy is out of date. Grant consent again before sending project context.",
        )
        else -> ProjectAiConsentUiState.Granted
    }
    is ProjectAiConsentGatewayResult.Deferred -> ProjectAiConsentUiState.Unavailable(
        when (reason) {
            ProjectAiConsentDeferralReason.NOT_CONFIGURED -> "Project AI consent is not configured in this build."
            ProjectAiConsentDeferralReason.AUTH_REQUIRED -> if (TEMPORARY_GUEST_MODE_ENABLED) {
                "Project AI is temporarily unavailable in local guest mode. Your project stays on this device; no context was sent."
            } else {
                "Sign in with a verified account to manage project AI consent."
            }
            ProjectAiConsentDeferralReason.SESSION_EXPIRED -> if (TEMPORARY_GUEST_MODE_ENABLED) {
                "Project AI is temporarily unavailable in local guest mode. Your project stays on this device; no context was sent."
            } else {
                "Your session expired. Sign in again to manage project AI consent."
            }
            ProjectAiConsentDeferralReason.SECURE_STORAGE -> "Secure session storage is unavailable; consent could not be checked."
        },
    )
    ProjectAiConsentGatewayResult.Unauthorized -> ProjectAiConsentUiState.Unavailable(
        if (TEMPORARY_GUEST_MODE_ENABLED) {
            "Project AI is temporarily unavailable in local guest mode. Your project stays on this device; no context was sent."
        } else {
            "Your session is no longer authorized. Sign in again before managing project AI consent."
        },
    )
    ProjectAiConsentGatewayResult.Forbidden -> ProjectAiConsentUiState.Unavailable(
        "This account cannot manage project AI consent. No project context was sent.",
    )
    ProjectAiConsentGatewayResult.PolicyStale -> ProjectAiConsentUiState.Unavailable(
        "The project AI consent policy changed. Review and grant consent again.",
    )
    is ProjectAiConsentGatewayResult.Failed -> ProjectAiConsentUiState.Unavailable(
        when (code) {
            "PROJECT_AI_CONSENT_OFFLINE" -> "You are offline. Project AI consent could not be checked; project work stays local."
            else -> "Project AI consent could not be verified (${code}). No project context was sent."
        },
    )
}

internal fun ProjectAiScaffoldGatewayResult.toProjectAiScaffoldUiState(): ProjectAiScaffoldUiState = when (this) {
    is ProjectAiScaffoldGatewayResult.Preview -> ProjectAiScaffoldUiState.Preview(proposal, requestId, creditCost)
    is ProjectAiScaffoldGatewayResult.Deferred -> ProjectAiScaffoldUiState.Unavailable(
        message = when (reason) {
            ProjectAiScaffoldDeferredReason.NOT_CONFIGURED -> "Project AI is not configured in this build. Your project remains usable manually."
            ProjectAiScaffoldDeferredReason.AUTH_REQUIRED -> if (TEMPORARY_GUEST_MODE_ENABLED) {
                "Project AI is temporarily unavailable in local guest mode. Your project remains usable manually; no context was sent."
            } else {
                "Sign in with a verified account before requesting project AI."
            }
            ProjectAiScaffoldDeferredReason.SESSION_EXPIRED -> if (TEMPORARY_GUEST_MODE_ENABLED) {
                "Project AI is temporarily unavailable in local guest mode. Your project remains usable manually; no context was sent."
            } else {
                "Your session expired. Sign in again; your local project is unchanged."
            }
            ProjectAiScaffoldDeferredReason.SECURE_STORAGE -> "Secure session storage is unavailable. No AI request was sent."
        },
    )
    is ProjectAiScaffoldGatewayResult.Unavailable -> ProjectAiScaffoldUiState.Unavailable(
        message = if (code == "PROJECT_AI_NOT_READY") {
            "Project AI is not enabled yet. No AI-generated content or credits were produced; continue manually."
        } else {
            "Project AI is temporarily unavailable. Your project and manual workflow remain available."
        },
        retryable = code != "PROJECT_AI_NOT_READY",
    )
    is ProjectAiScaffoldGatewayResult.Rejected -> ProjectAiScaffoldUiState.Unavailable(
        message = when (code) {
            "PROJECT_AI_CONSENT_REQUIRED", "PROJECT_AI_CONSENT_POLICY_STALE" ->
                "Project AI consent changed or is out of date. Refresh and review consent before another request. Your project was not changed."
            else -> "The request was not accepted (${code}). Your project was not changed."
        },
    )
    is ProjectAiScaffoldGatewayResult.Failed -> ProjectAiScaffoldUiState.Unavailable(
        message = when {
            outcomeUnknown -> "The request outcome is unknown. Check before retrying; no project fields were applied."
            code == "PROJECT_AI_OFFLINE" -> "You are offline. Project work stays local; reconnect before asking AI."
            else -> "Project AI could not complete this request. Your project was not changed."
        },
        retryable = retryable && !outcomeUnknown,
    )
}

@Composable
internal fun EvidriloProjectAiScaffoldPanel(
    template: ProjectTemplateDefinition,
    existingProjectId: String?,
    currentFieldValues: Map<String, String>,
    existingProjectRevision: Int?,
    projectTitle: String,
    projectAiAccountKey: String?,
    state: ProjectAiScaffoldUiState,
    consentState: ProjectAiConsentUiState,
    allowRequest: Boolean = true,
    onRefreshConsent: () -> Unit,
    onGrantConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
    onRequest: (
        projectId: String?,
        assignmentBrief: String,
        studentQuestion: String?,
        currentFields: Map<String, String>,
        baseRevision: Int?,
        projectDataConsent: Boolean,
    ) -> Unit,
    onCreateProject: (title: String, proposal: ProjectAiScaffoldProposal, selectedFieldIds: Set<String>, editedValues: Map<String, String>, requestId: String, creditCost: Int) -> Unit,
    onApplyToProject: (proposal: ProjectAiScaffoldProposal, selectedFieldIds: Set<String>, editedValues: Map<String, String>, replacedFieldIds: Set<String>, requestId: String, creditCost: Int) -> StudentProjectDraft?,
    onDiscardPreview: (requestId: String, creditCost: Int) -> Unit,
    onRetrySettlement: (ProjectAiScaffoldUiState.SettlementFailed) -> Unit,
) {
    val accountSignedIn = projectAiAccountKey != null
    LaunchedEffect(template.id, existingProjectRevision, projectAiAccountKey) {
        if (accountSignedIn) onRefreshConsent()
    }
    val creating = existingProjectRevision == null
    val assignmentField = template.inputFields.firstOrNull { it.kind == ProjectTemplateInputKind.ASSIGNMENT_BRIEF }
    val questionField = template.inputFields.firstOrNull { it.kind == ProjectTemplateInputKind.RESEARCH_QUESTION }
    var title by remember(template.id, projectAiAccountKey) { mutableStateOf(projectTitle.ifBlank { "My project" }) }
    var newAssignmentBrief by remember(template.id, projectAiAccountKey) { mutableStateOf("") }
    var studentQuestion by remember(template.id, existingProjectRevision, projectAiAccountKey) { mutableStateOf("") }
    var dataConsent by remember(template.id, existingProjectRevision, projectAiAccountKey) { mutableStateOf(false) }
    var selectedContextFieldIds by remember(template.id, existingProjectRevision, projectAiAccountKey) { mutableStateOf(emptySet<String>()) }
    val assignmentBrief = if (creating) newAssignmentBrief else currentFieldValues[assignmentField?.id].orEmpty()
    val hasConsent = accountSignedIn && canRequestProjectAi(
        dataConsent = dataConsent,
        assignmentBrief = assignmentBrief,
    ) && consentState is ProjectAiConsentUiState.Granted
    val additionalContextFields = currentFieldValues.entries
        .filter { (fieldId, value) -> fieldId != assignmentField?.id && value.isNotBlank() }
        .sortedBy { it.key }

    EvidriloTargetCard {
        Text(
            if (creating) "Create with project AI" else "Ask about this project",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "AI can help frame your assignment and suggest editable project fields. It does not verify research quality, supply evidence, or replace your decision.",
            style = MaterialTheme.typography.bodyMedium,
            color = EvidriloColors.Slate,
        )
        if (!accountSignedIn) {
            Text(
                "Project AI requires an account and separate consent, and is disabled in this build. You can create and edit local projects without signing in.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
        } else {
        if (creating) {
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 160) title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Project name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = newAssignmentBrief,
                onValueChange = { if (it.length <= 8_000) newAssignmentBrief = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What does your assignment ask you to do?") },
                supportingText = { Text("Use your own instructions; do not include names or sensitive participant details.") },
                minLines = 3,
            )
        } else {
            Text(
                if (!allowRequest) {
                    "Save your current project edits before asking AI so the request is bound to the saved revision."
                } else if (assignmentBrief.isBlank()) {
                    "Add your assignment brief in the project fields below before asking AI."
                } else {
                    "AI context comes from your current assignment brief and the fields you have entered."
                },
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
        }
        OutlinedTextField(
            value = studentQuestion,
            onValueChange = { if (it.length <= 2_000) studentQuestion = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What would you like help with? (optional)") },
            supportingText = { Text("For example: help me make the question more specific, or suggest a manageable next step.") },
            minLines = 2,
        )
        if (additionalContextFields.isNotEmpty()) {
            Text(
                "Choose saved project fields to include (none selected by default)",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
            additionalContextFields.forEach { (fieldId, _) ->
                val selected = fieldId in selectedContextFieldIds
                val label = template.inputFields.firstOrNull { it.id == fieldId }?.label ?: fieldId
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { checked ->
                            selectedContextFieldIds = if (checked) {
                                selectedContextFieldIds + fieldId
                            } else {
                                selectedContextFieldIds - fieldId
                            }
                        },
                    )
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        when (val savedConsent = consentState) {
            ProjectAiConsentUiState.Unknown -> TextButton(onClick = onRefreshConsent) {
                Text("Check saved account consent")
            }
            ProjectAiConsentUiState.Checking -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("Checking saved account consent…", style = MaterialTheme.typography.bodySmall)
            }
            ProjectAiConsentUiState.NotGranted -> Column {
                Text(
                    "Project AI needs a separate, revocable account-level consent before project context can be sent. Granting it does not send project data by itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                TextButton(onClick = onGrantConsent) { Text("Allow project AI data processing") }
            }
            ProjectAiConsentUiState.Granted -> Column {
                Text(
                    "Revocable account consent is saved. It does not include project files or send data by itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                TextButton(onClick = onRevokeConsent) { Text("Revoke saved AI consent") }
            }
            is ProjectAiConsentUiState.Unavailable -> Column {
                Text(savedConsent.message, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                TextButton(onClick = onRefreshConsent) { Text("Check again") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = dataConsent, onCheckedChange = { dataConsent = it })
            Text(
                "For this request only, send the assignment brief, optional question, and selected saved fields to Evidrilo's AI endpoint. Source files are excluded. Nothing is applied or saved from a response until I confirm.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            projectAiCreditDisclosure(creating),
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        Text(
            "Provider status: disabled in this build. A request cannot generate suggestions or consume AI credits yet; your manual project workflow remains available. Revoking consent prevents future requests; it cannot recall data already sent.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        when (state) {
            ProjectAiScaffoldUiState.Idle -> Unit
            ProjectAiScaffoldUiState.Loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("Checking project AI availability…", style = MaterialTheme.typography.bodyMedium)
            }
            is ProjectAiScaffoldUiState.Unavailable -> Text(
                state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
            is ProjectAiScaffoldUiState.Preview -> ProjectAiScaffoldPreview(
                template = template,
                currentFieldValues = currentFieldValues,
                proposal = state.proposal,
                requestId = state.requestId,
                creditCost = state.creditCost,
                creating = creating,
                projectTitle = title,
                onCreateProject = onCreateProject,
                onApplyToProject = onApplyToProject,
                onDiscardPreview = onDiscardPreview,
                onUseNextPrompt = { studentQuestion = it },
            )
            is ProjectAiScaffoldUiState.Settling -> Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text(
                    if (state.decision == ProjectAiScaffoldDecision.APPLY) {
                        "Confirming the ${state.creditCost}-credit apply…"
                    } else {
                        "Releasing the reserved credits…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is ProjectAiScaffoldUiState.SettlementFailed -> Column {
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                if (state.canRetry) {
                    TextButton(onClick = { onRetrySettlement(state) }) { Text("Retry credit confirmation") }
                }
            }
        }
        EvidriloPrimaryButton(
            label = "Request AI suggestions",
            enabled = hasConsent && allowRequest && state !is ProjectAiScaffoldUiState.Loading &&
                state !is ProjectAiScaffoldUiState.Preview && state !is ProjectAiScaffoldUiState.Settling &&
                state !is ProjectAiScaffoldUiState.SettlementFailed && consentState !is ProjectAiConsentUiState.Checking,
            onClick = {
                onRequest(
                    existingProjectId,
                    assignmentBrief.trim(),
                    studentQuestion.trim().takeIf(String::isNotEmpty),
                    selectProjectAiContextFields(
                        currentFields = currentFieldValues,
                        selectedFieldIds = selectedContextFieldIds,
                        assignmentBriefFieldId = assignmentField?.id,
                    ),
                    existingProjectRevision,
                    dataConsent,
                )
            },
        )
        }
    }
}

internal fun canRequestProjectAi(dataConsent: Boolean, assignmentBrief: String): Boolean =
    dataConsent && assignmentBrief.isNotBlank()

internal fun projectAiCreditDisclosure(creating: Boolean): String = if (creating) {
    "Project scaffold: when AI is enabled, 3 AI credits are charged only if you apply a suggestion; dismissal or failure releases the reservation."
} else {
    "In-project assistance: when AI is enabled, 1 AI credit is charged only if you apply a suggestion; dismissal or failure releases the reservation."
}

internal fun selectProjectAiContextFields(
    currentFields: Map<String, String>,
    selectedFieldIds: Set<String>,
    assignmentBriefFieldId: String?,
): Map<String, String> = currentFields.filter { (fieldId, value) ->
    fieldId in selectedFieldIds && fieldId != assignmentBriefFieldId && value.isNotBlank()
}

@Composable
private fun ProjectAiScaffoldPreview(
    template: ProjectTemplateDefinition,
    currentFieldValues: Map<String, String>,
    proposal: ProjectAiScaffoldProposal,
    requestId: String,
    creditCost: Int,
    creating: Boolean,
    projectTitle: String,
    onCreateProject: (String, ProjectAiScaffoldProposal, Set<String>, Map<String, String>, String, Int) -> Unit,
    onApplyToProject: (ProjectAiScaffoldProposal, Set<String>, Map<String, String>, Set<String>, String, Int) -> StudentProjectDraft?,
    onDiscardPreview: (String, Int) -> Unit,
    onUseNextPrompt: (String) -> Unit,
) {
    var selectedIds by remember(requestId) { mutableStateOf(emptySet<String>()) }
    var editedValues by remember(requestId) {
        mutableStateOf(proposal.fieldSuggestions.associate { it.fieldId to it.suggestedValue })
    }
    var replacedIds by remember(requestId) { mutableStateOf(emptySet<String>()) }
    var applyMessage by remember(requestId) { mutableStateOf<String?>(null) }
    val fieldsById = template.inputFields.associateBy { it.id }

    EvidriloTargetCard {
        Text("AI draft · review before using", style = MaterialTheme.typography.titleMedium)
        Text(
            "$creditCost AI credit${if (creditCost == 1) "" else "s"} reserved · charged only when you apply a suggestion; dismissing releases it.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        Text(proposal.guidanceText, style = MaterialTheme.typography.bodyMedium)
        Text(
            "This is a suggestion, not verified academic guidance. Check it against your assignment and instructor's requirements.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        TextButton(onClick = { onDiscardPreview(requestId, creditCost) }) {
            Text("Discard AI suggestion")
        }
        proposal.clarificationQuestions.forEach { Text("Check: $it", style = MaterialTheme.typography.bodyMedium) }
        proposal.fieldSuggestions.forEach { suggestion ->
            val field = fieldsById[suggestion.fieldId] ?: return@forEach
            val selected = suggestion.fieldId in selectedIds
            val existingValue = currentFieldValues[suggestion.fieldId].orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { checked ->
                        selectedIds = if (checked) selectedIds + suggestion.fieldId else selectedIds - suggestion.fieldId
                    },
                )
                Text("Use suggestion for ${field.label}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = editedValues[suggestion.fieldId].orEmpty(),
                onValueChange = { next ->
                    if (next.length <= 8_000) editedValues = editedValues + (suggestion.fieldId to next)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Editable suggestion") },
                minLines = 2,
            )
            if (selected && existingValue.isNotBlank() && existingValue != editedValues[suggestion.fieldId]) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = suggestion.fieldId in replacedIds,
                        onCheckedChange = { checked ->
                            replacedIds = if (checked) replacedIds + suggestion.fieldId else replacedIds - suggestion.fieldId
                        },
                    )
                    Text("Confirm replacing the existing ${field.label}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (proposal.recommendedNextPrompts.isNotEmpty()) {
            Text("Suggested next questions", style = MaterialTheme.typography.titleSmall)
            proposal.recommendedNextPrompts.forEach { prompt ->
                TextButton(onClick = { onUseNextPrompt(prompt) }) { Text(prompt) }
            }
        }
        applyMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
        val selectionReady = selectedIds.isNotEmpty() && selectedIds.all { fieldId ->
            val previous = currentFieldValues[fieldId].orEmpty()
            previous.isBlank() || previous == editedValues[fieldId] || fieldId in replacedIds
        }
        if (creating) {
            EvidriloPrimaryButton(
                label = "Create project from selected suggestions",
                enabled = selectionReady && projectTitle.isNotBlank(),
                onClick = {
                    onCreateProject(
                        projectTitle,
                        proposal,
                        selectedIds,
                        editedValues.filterKeys(selectedIds::contains),
                        requestId,
                        creditCost,
                    )
                },
            )
        } else {
            EvidriloPrimaryButton(
                label = "Apply selected suggestions",
                enabled = selectionReady,
                onClick = {
                    val updated = onApplyToProject(
                        proposal,
                        selectedIds,
                        editedValues.filterKeys(selectedIds::contains),
                        replacedIds.intersect(selectedIds),
                        requestId,
                        creditCost,
                    )
                    applyMessage = if (updated != null) "Selected suggestions were saved locally; AI-credit confirmation is in progress." else
                        "Suggestions were not applied. Your project remains unchanged."
                },
            )
        }
    }
}
