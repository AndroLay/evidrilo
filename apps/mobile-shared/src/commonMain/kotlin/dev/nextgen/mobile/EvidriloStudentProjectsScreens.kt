package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldProposal
import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.RequiredProjectFieldProgress
import dev.nextgen.mobile.domain.project.SourceSelectionStatus
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectClaimReviewStatus
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelationType
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.domain.project.StudentProjectRevisionSnapshot
import dev.nextgen.mobile.domain.project.StudentProjectFieldDefinition
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.StudentProjectStructureReport
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.projectcatalog.StudentProjectArchiveCodec
import dev.nextgen.mobile.projectcatalog.StudentProjectArchiveEncodingResult
import dev.nextgen.mobile.projectcatalog.StudentProjectArchiveReadResult
import dev.nextgen.mobile.projectcatalog.StudentProjectAttachmentAddReceipt
import dev.nextgen.mobile.projectcatalog.StudentProjectAttachmentRemoveReceipt
import dev.nextgen.mobile.projectcatalog.StudentProjectAttachmentPreviewRead
import dev.nextgen.mobile.projectcatalog.StudentProjectDraftFlowResult
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact
import dev.nextgen.mobile.projectcatalog.StudentProjectExportFormatter
import dev.nextgen.mobile.projectcatalog.StudentProjectImportReceipt
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistSelectedEvidence
import dev.nextgen.mobile.projectcatalog.readStudentProjectAttachmentForPreview
import dev.nextgen.mobile.storage.StudentProjectAttachmentStore
import dev.nextgen.mobile.storage.createProjectSectionBookmarkStore
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface StudentProjectListUiState {
    data object Loading : StudentProjectListUiState
    data class Loaded(val projects: List<StudentProjectDraft>) : StudentProjectListUiState
    data object StorageUnavailable : StudentProjectListUiState
    data object StorageCorrupt : StudentProjectListUiState
    data object StorageFailed : StudentProjectListUiState
}

internal enum class StudentProjectCardAction(val label: String) {
    CONTINUE("Continue project"),
    OPEN("Open project"),
    EXPORT("Export project"),
    MARK_COMPLETE("Mark complete"),
    ARCHIVE("Archive project"),
    MOVE_TO_TRASH("Move to Trash"),
    RESTORE_ACTIVE("Restore to active"),
    RESTORE_ARCHIVED("Restore as archived"),
    DELETE_PERMANENTLY("Delete permanently"),
}

internal fun studentProjectPrimaryAction(status: StudentProjectStatus): StudentProjectCardAction = when (status) {
    StudentProjectStatus.DRAFT,
    StudentProjectStatus.ACTIVE,
    -> StudentProjectCardAction.CONTINUE
    StudentProjectStatus.COMPLETED,
    StudentProjectStatus.ARCHIVED,
    -> StudentProjectCardAction.OPEN
    StudentProjectStatus.TRASHED -> StudentProjectCardAction.RESTORE_ACTIVE
}

internal fun studentProjectSecondaryActions(status: StudentProjectStatus): List<StudentProjectCardAction> =
    listOf(if (status == StudentProjectStatus.DRAFT || status == StudentProjectStatus.ACTIVE)
        StudentProjectCardAction.MARK_COMPLETE else StudentProjectCardAction.RESTORE_ACTIVE,
        StudentProjectCardAction.EXPORT, StudentProjectCardAction.DELETE_PERMANENTLY)

private sealed interface StudentProjectAttachmentPreviewLoad {
    data class Ready(val result: StudentProjectDocumentTextResult) : StudentProjectAttachmentPreviewLoad
    data class Failed(val code: String) : StudentProjectAttachmentPreviewLoad
}

private data class StudentProjectAttachmentTextPreview(
    val fileName: String,
    val text: String,
    val isTruncated: Boolean,
)

internal fun StudentProjectDraftFlowResult<List<StudentProjectDraft>>.toStudentProjectListUiState(): StudentProjectListUiState =
    when (this) {
        is StudentProjectDraftFlowResult.Value -> StudentProjectListUiState.Loaded(value)
        StudentProjectDraftFlowResult.StorageUnavailable -> StudentProjectListUiState.StorageUnavailable
        StudentProjectDraftFlowResult.StorageCorrupt -> StudentProjectListUiState.StorageCorrupt
        StudentProjectDraftFlowResult.StorageFailed -> StudentProjectListUiState.StorageFailed
        is StudentProjectDraftFlowResult.Rejected,
        StudentProjectDraftFlowResult.NotFound,
        -> StudentProjectListUiState.StorageFailed
    }

internal fun StudentProjectListUiState.toHomeErrorMessage(): String? = when (this) {
    StudentProjectListUiState.Loading,
    is StudentProjectListUiState.Loaded,
    -> null
    StudentProjectListUiState.StorageUnavailable -> "Local project storage is unavailable."
    StudentProjectListUiState.StorageCorrupt -> "Saved project data could not be read safely."
    StudentProjectListUiState.StorageFailed -> "Evidrilo could not read your saved projects."
}

internal fun studentProjectDraftFlowMessage(result: StudentProjectDraftFlowResult<*>): String? = when (result) {
    is StudentProjectDraftFlowResult.Value -> null
    is StudentProjectDraftFlowResult.Rejected -> when (result.code) {
        "PROJECT_ACTIVE_LIMIT_REACHED" -> "Your project spaces are full. Export and permanently remove a project to make room. Completed projects also use a space."
        "TEMPLATE_NOT_READY" -> "This template is not ready to start. Only a published, reviewed template can create a project."
        "PROJECT_AI_TEMPLATE_REQUIRED" -> "Project assistance is unavailable until a compatible reviewed template is selected. You can continue manually."
        "PROJECT_TRASH_RETENTION_EXPIRED" -> "This project is past its 30-day recovery period. Refresh the project list to remove expired Trash items."
        "PROJECT_NOT_IN_TRASH" -> "Only projects in Trash can be restored from Trash."
        "PROJECT_RESTORE_REQUIRED" -> "Restore this project from Trash before changing its status."
        "INVALID_CLAIM_EVIDENCE_LINK" -> "A claim link points to a source that is not in this project. No changes were saved."
        "PROJECT_SOURCE_INVALID" -> "One source field exceeds the local size limit or contains unsupported content. Your edits remain on screen."
        "TOO_MANY_PROJECT_REVISION_SNAPSHOTS" -> "This project reached its 100-checkpoint history limit. Earlier saved revisions were not removed. Your current changes remain on screen; export a project archive before deciding how to continue."
        "PROJECT_DRAFT_TOO_LARGE" -> "This project exceeds the local per-project storage limit. Earlier saved work was not replaced. Shorten or split the project content, then retry; your current edits remain on screen."
        "PROJECT_DRAFT_STORAGE_LIMIT_REACHED" -> "A local project storage limit was reached. Nothing was overwritten. Export a project archive or permanently remove work you no longer need; your current edits remain on screen."
        "PROJECT_IMPORT_INVALID" -> "This project archive contains an invalid project record. No projects were changed."
        "PROJECT_IMPORT_ID_CONFLICT" -> "A different local project uses this archive ID. The existing project was not changed; import the file as a copy instead."
        "PROJECT_IMPORT_ALREADY_PRESENT" -> "This exact project is already present on this device. Nothing was changed."
        "PROJECT_IMPORT_ID_REMAP_FAILED" -> "A safe copy could not be created because new local IDs could not be generated. No projects were changed."
        "PROJECT_IMPORT_ATTACHMENT_DATA_REQUIRED" -> "This project contains attachment references without their verified archive bytes. No project data was imported."
        "PROJECT_IMPORT_ATTACHMENT_PUBLISH_FAILED" -> "Attachments could not be published safely. No project metadata was imported; retry after checking device storage."
        "PROJECT_IMPORT_ATTACHMENT_REVISION_UNSUPPORTED" -> "The archive changes this project's attachment set. Import it as a separate copy to preserve existing files."
        "PROJECT_ARCHIVE_ATTACHMENT_METADATA_MISMATCH" -> "The archive's attachment metadata did not match the project. No data was imported."
        "PROJECT_ARCHIVE_ATTACHMENT_STORE_FAILED" -> "Private attachment storage could not complete the import. No project metadata was imported."
        "PROJECT_IMPORT_NO_NEW_REVISION" -> "This archive does not contain a newer project revision. Existing work was not changed."
        "PROJECT_IMPORT_TIMESTAMP_EXHAUSTED" -> "The current project timestamp cannot safely accept another revision. Existing work was not changed."
        else -> "This project action could not be completed. No unsupported project state was created."
    }
    StudentProjectDraftFlowResult.NotFound -> "This project is no longer available in local storage. Refresh the list and try again."
    StudentProjectDraftFlowResult.StorageUnavailable -> "Local project storage is unavailable in this build."
    StudentProjectDraftFlowResult.StorageCorrupt -> "Saved project data could not be read. It was not overwritten."
    StudentProjectDraftFlowResult.StorageFailed -> "Local project storage could not complete that action. Your current edits remain on screen."
}

internal data class StudentProjectArchiveImportPayload(
    val preview: StudentProjectArchiveReadResult.Preview,
    val bytes: ByteArray,
)

internal data class ProjectSourceRemovalImpact(
    val evidenceNotes: List<StudentProjectEvidenceItem>,
    val relationships: List<ProjectRemovalRelationImpact>,
    val themes: List<StudentProjectSynthesisTheme>,
    val claimsNeedingReview: List<StudentProjectClaimRecord>,
    val removesClaimSourceLink: Boolean,
    val claimSourceLinkedClaim: StudentProjectClaimRecord?,
)

internal data class ProjectEvidenceRemovalImpact(
    val evidenceNote: StudentProjectEvidenceItem,
    val relationships: List<ProjectRemovalRelationImpact>,
    val claimsNeedingReview: List<StudentProjectClaimRecord>,
)

internal data class ProjectRemovalRelationImpact(
    val relation: StudentProjectEvidenceRelation,
    val targetLabel: String,
    val evidenceLabel: String,
)

internal fun projectSourceRemovalImpact(
    source: StudentProjectSourceRecord,
    draft: StudentProjectDraft,
): ProjectSourceRemovalImpact {
    val evidenceNotes = draft.evidenceItems.filter { it.sourceId == source.id }
    val evidenceIds = evidenceNotes.mapTo(mutableSetOf(), StudentProjectEvidenceItem::id)
    val relationships = projectRemovalRelationshipImpacts(draft, evidenceIds)
    val removesClaimSourceLink = source.id in draft.claimEvidenceSourceIds
    val claims = StudentProjectDraftRules.effectiveClaims(draft)
    val claimSourceLinkedClaim = if (removesClaimSourceLink) {
        StudentProjectDraftRules.primaryClaim(claims)
    } else {
        null
    }
    val affectedClaimIds = relationships.asSequence()
        .filter { it.relation.targetType == StudentProjectEvidenceTargetType.CLAIM }
        .map { it.relation.targetId }
        .toMutableSet()
    if (removesClaimSourceLink) {
        claimSourceLinkedClaim?.id?.let(affectedClaimIds::add)
    }
    return ProjectSourceRemovalImpact(
        evidenceNotes = evidenceNotes,
        relationships = relationships,
        themes = draft.themes.filter { source.id in it.sourceIds },
        claimsNeedingReview = claims.filter { it.id in affectedClaimIds },
        removesClaimSourceLink = removesClaimSourceLink,
        claimSourceLinkedClaim = claimSourceLinkedClaim,
    )
}

internal fun projectEvidenceRemovalImpact(
    evidence: StudentProjectEvidenceItem,
    draft: StudentProjectDraft,
): ProjectEvidenceRemovalImpact {
    val relationships = projectRemovalRelationshipImpacts(draft, setOf(evidence.id))
    val affectedClaimIds = relationships.asSequence()
        .filter { it.relation.targetType == StudentProjectEvidenceTargetType.CLAIM }
        .map { it.relation.targetId }
        .toSet()
    val claims = StudentProjectDraftRules.effectiveClaims(draft)
    return ProjectEvidenceRemovalImpact(
        evidenceNote = evidence,
        relationships = relationships,
        claimsNeedingReview = claims.filter { it.id in affectedClaimIds },
    )
}

private fun projectRemovalRelationshipImpacts(
    draft: StudentProjectDraft,
    evidenceIds: Set<String>,
): List<ProjectRemovalRelationImpact> {
    val findings = draft.findings.associateBy(StudentProjectFindingRecord::id)
    val claims = StudentProjectDraftRules.effectiveClaims(draft).associateBy(StudentProjectClaimRecord::id)
    val evidence = draft.evidenceItems.associateBy(StudentProjectEvidenceItem::id)
    return draft.evidenceRelations.asSequence()
        .filter { it.evidenceId in evidenceIds }
        .map { relation ->
            val label = when (relation.targetType) {
                StudentProjectEvidenceTargetType.FINDING -> findings[relation.targetId]?.statement
                StudentProjectEvidenceTargetType.CLAIM -> claims[relation.targetId]?.statement
            }.orEmpty().ifBlank { "Untitled ${relation.targetType.name.lowercase()}" }
            val evidenceLabel = evidence[relation.evidenceId]?.excerpt
                ?.ifBlank { "Untitled evidence note" }
                ?.removalPreview()
                ?: "unavailable evidence note"
            ProjectRemovalRelationImpact(relation, label, evidenceLabel)
        }
        .toList()
}

internal fun projectSourceRemovalWarning(
    source: StudentProjectSourceRecord,
    impact: ProjectSourceRemovalImpact,
): String = buildString {
    val sourceLabel = source.title.trim().takeIf(String::isNotEmpty)?.removalPreview() ?: "this source"
    appendLine("Removing “$sourceLabel” affects these linked project items:")
    impact.evidenceNotes.forEach { evidence ->
        appendLine("Evidence note “${evidence.excerpt.ifBlank { "Untitled evidence note" }.removalPreview()}” will be removed.")
    }
    impact.relationships.forEach { appendLine(it.toRemovalPreview()) }
    impact.themes.forEach { theme ->
        appendLine("Theme “${theme.title.ifBlank { "Untitled theme" }.removalPreview()}” loses its source link; the text remains.")
    }
    val linkedClaim = impact.claimSourceLinkedClaim
    if (linkedClaim != null) {
        appendLine("The source link is removed from “${linkedClaim.statement.removalPreview()}”.")
    } else if (impact.removesClaimSourceLink) {
        appendLine("A source link is removed from a claim that could not be identified.")
    }
    impact.claimsNeedingReview.forEach { claim ->
        appendLine("Claim “${claim.statement.removalPreview()}” will be marked Needs revision.")
    }
    if (impact.evidenceNotes.isEmpty() && impact.relationships.isEmpty() && impact.themes.isEmpty() &&
        !impact.removesClaimSourceLink
    ) {
        appendLine("No structured project item is linked to this source.")
    }
    appendLine("Findings, themes, claims, and free-text analysis/output are retained unless listed above; free-text analysis and output are not linked automatically, so review them manually.")
    append("Confirm to remove these source links and save a new revision. Cancel leaves the project unchanged.")
}

internal fun projectEvidenceRemovalWarning(impact: ProjectEvidenceRemovalImpact): String = buildString {
    appendLine(
        "Evidence note “${impact.evidenceNote.excerpt.ifBlank { "Untitled evidence note" }.removalPreview()}” will be removed from this revision.",
    )
    impact.relationships.forEach { appendLine(it.toRemovalPreview()) }
    impact.claimsNeedingReview.forEach { claim ->
        appendLine("Claim “${claim.statement.removalPreview()}” will be marked Needs revision.")
    }
    if (impact.relationships.isEmpty()) appendLine("No finding or claim currently links to this note.")
    appendLine("Findings and free-text analysis/output are retained; free-text analysis and output are not linked automatically, so review them manually.")
    append("Confirm to remove this note and save a new revision. Cancel leaves the project unchanged.")
}

private fun ProjectRemovalRelationImpact.toRemovalPreview(): String {
    val target = when (relation.targetType) {
        StudentProjectEvidenceTargetType.FINDING -> "Finding"
        StudentProjectEvidenceTargetType.CLAIM -> "Claim"
    }
    val relationLabel = relation.relation.name.lowercase().replace('_', ' ')
    return "$target “${targetLabel.removalPreview()}” loses its $relationLabel link to evidence note “$evidenceLabel”."
}

private fun String.removalPreview(): String {
    val normalized = trim().replace('\n', ' ')
    return when {
        normalized.isBlank() -> "Untitled"
        normalized.length <= 120 -> normalized
        else -> "${normalized.take(117)}…"
    }
}

private sealed interface StudentProjectArchiveImportDialogState {
    data object Hidden : StudentProjectArchiveImportDialogState
    data class Busy(val message: String) : StudentProjectArchiveImportDialogState
    data class Reading(val progress: StudentProjectFileImportProgress) : StudentProjectArchiveImportDialogState
    data class Preview(val payload: StudentProjectArchiveImportPayload) : StudentProjectArchiveImportDialogState
    data class AlreadyPresent(val project: StudentProjectDraft) : StudentProjectArchiveImportDialogState
    data class Duplicate(val payload: StudentProjectArchiveImportPayload, val existing: StudentProjectDraft) : StudentProjectArchiveImportDialogState
    data class AtCapacity(val payload: StudentProjectArchiveImportPayload, val importAsCopy: Boolean) : StudentProjectArchiveImportDialogState
    data class Error(val message: String) : StudentProjectArchiveImportDialogState
}

internal fun requiredProjectProgressLabel(progress: RequiredProjectFieldProgress): String =
    "Required information: ${progress.filledRequired} of ${progress.totalRequired} fields filled. This is not a grade."

internal enum class StudentProjectEditorSectionKind {
    PROJECT_BASICS,
    TEMPLATE_STEP,
    TEMPLATE_ADDITIONAL_FIELDS,
    SOURCES_AND_FILES,
    EVIDENCE_NOTES,
    FINDINGS_AND_SYNTHESIS,
    CLAIMS,
    LIMITATIONS_AND_NEXT_STEPS,
    REVIEW,
}

internal data class StudentProjectEditorSection(
    val id: String,
    val title: String,
    val kind: StudentProjectEditorSectionKind,
    val fieldIds: List<String> = emptyList(),
    val templateStepId: String? = null,
) {
    // Navigation metadata has its own namespace; backend/template step IDs stay unchanged.
    val navigationId: String get() = if (templateStepId != null) "template-step:$templateStepId" else id
}

internal data class StudentProjectReviewField(
    val id: String,
    val label: String,
    val value: String,
)

internal fun studentProjectReviewFields(draft: StudentProjectDraft): List<StudentProjectReviewField> {
    val fields = draft.templateSnapshot?.inputFields?.map { it.id to it.label }
        ?: ManualLiteratureSynthesisFields.all.map { it.id to it.label }
    return fields.map { (id, label) ->
        StudentProjectReviewField(id, label, draft.fieldValues[id].orEmpty())
    }
}

internal fun studentProjectEditorSections(draft: StudentProjectDraft): List<StudentProjectEditorSection> = buildList {
    val template = draft.templateSnapshot
    add(
        StudentProjectEditorSection(
            id = "project-basics",
            title = "Project basics",
            kind = StudentProjectEditorSectionKind.PROJECT_BASICS,
            fieldIds = emptyList(),
        ),
    )

    if (template == null) {
        add(StudentProjectEditorSection("manual-question", "Frame your question", StudentProjectEditorSectionKind.TEMPLATE_STEP,
            listOf(ManualLiteratureSynthesisFields.ASSIGNMENT_BRIEF, ManualLiteratureSynthesisFields.RESEARCH_QUESTION)))
        add(StudentProjectEditorSection("manual-boundaries", "Set your boundaries", StudentProjectEditorSectionKind.TEMPLATE_STEP,
            listOf(ManualLiteratureSynthesisFields.AIM, ManualLiteratureSynthesisFields.SCOPE)))
        add(StudentProjectEditorSection("manual-material", "Plan your material", StudentProjectEditorSectionKind.TEMPLATE_STEP,
            listOf(ManualLiteratureSynthesisFields.SELECTION_METHOD, ManualLiteratureSynthesisFields.SEARCH_SCOPE)))
    }

    if (template != null) {
        val knownFieldIds = template.inputFields.mapTo(mutableSetOf(), ProjectTemplateInputField::id)
        val assignedFieldIds = mutableSetOf<String>()
        template.steps.forEach { step ->
            val fieldIds = step.inputFieldIds.filter { it in knownFieldIds }
            assignedFieldIds += fieldIds
            if (fieldIds.isNotEmpty() || step.aiOperations.isNotEmpty()) {
                add(
                    StudentProjectEditorSection(
                        id = step.id,
                        title = step.title,
                        kind = StudentProjectEditorSectionKind.TEMPLATE_STEP,
                        fieldIds = fieldIds,
                        templateStepId = step.id,
                    ),
                )
            }
        }
        val unassignedFieldIds = template.inputFields.map(ProjectTemplateInputField::id)
            .filterNot(assignedFieldIds::contains)
        if (unassignedFieldIds.isNotEmpty()) {
            add(
                StudentProjectEditorSection(
                    id = "template-additional-fields",
                    title = "Other project details",
                    kind = StudentProjectEditorSectionKind.TEMPLATE_ADDITIONAL_FIELDS,
                    fieldIds = unassignedFieldIds,
                ),
            )
        }
    }

    add(StudentProjectEditorSection("sources-and-files", "Sources and files", StudentProjectEditorSectionKind.SOURCES_AND_FILES))
    add(StudentProjectEditorSection("evidence-notes", "Evidence notes", StudentProjectEditorSectionKind.EVIDENCE_NOTES))
    add(StudentProjectEditorSection("findings-and-synthesis", "Findings and comparison", StudentProjectEditorSectionKind.FINDINGS_AND_SYNTHESIS,
        if (template == null) listOf(ManualLiteratureSynthesisFields.SYNTHESIS) else emptyList()))
    add(
        StudentProjectEditorSection(
            id = "claims-and-evidence-links",
            title = "Claims and evidence links",
            kind = StudentProjectEditorSectionKind.CLAIMS,
            fieldIds = if (template == null) {
                ManualLiteratureSynthesisFields.all.drop(7).take(2).map(StudentProjectFieldDefinition::id)
            } else {
                emptyList()
            },
        ),
    )
    add(
        StudentProjectEditorSection(
            id = "limitations-and-next-steps",
            title = "Limitations and next steps",
            kind = StudentProjectEditorSectionKind.LIMITATIONS_AND_NEXT_STEPS,
            fieldIds = if (template == null) {
                ManualLiteratureSynthesisFields.all.drop(9).map(StudentProjectFieldDefinition::id)
            } else {
                emptyList()
            },
        ),
    )
    add(StudentProjectEditorSection("review", "Review", StudentProjectEditorSectionKind.REVIEW))
}

@Composable
private fun ProjectStarterMethodNotes(template: ProjectTemplateDefinition) {
    if (template.publication != ProjectTemplatePublication.BUILT_IN_STARTER) return
    var expanded by remember(template.id, template.version) { mutableStateOf(false) }

    EvidriloTargetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Method notes", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide notes" else "View notes")
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Limits", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                template.methodSpecificLimitations.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                Text("Record provenance", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                template.provenanceRequirements.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                Text("Accessibility", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                template.accessibilityExpectations.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
            }
        }
    }
}

internal fun studentProjectEditorProgress(sectionIndex: Int, sectionCount: Int): Float =
    if (sectionCount <= 0) 0f else (sectionIndex.coerceIn(0, sectionCount - 1) + 1f) / sectionCount

internal fun studentProjectEditorProgressDescription(
    sectionIndex: Int,
    sectionCount: Int,
    title: String,
): String = "Section ${if (sectionCount <= 0) 0 else sectionIndex.coerceIn(0, sectionCount - 1) + 1} of $sectionCount: $title"

internal fun detachFindingFromLimitationActions(
    actions: List<StudentProjectLimitationActionRecord>,
    findingId: String,
): List<StudentProjectLimitationActionRecord> = actions.map { action ->
    action.copy(affectedFindingIds = action.affectedFindingIds - findingId)
}

internal fun detachClaimFromLimitationActions(
    actions: List<StudentProjectLimitationActionRecord>,
    claimId: String,
): List<StudentProjectLimitationActionRecord> = actions.map { action ->
    action.copy(affectedClaimIds = action.affectedClaimIds - claimId)
}

internal fun studentProjectImportProgressLabel(progress: StudentProjectFileImportProgress): String =
    "Reading archive: ${formatProjectImportBytes(progress.bytesRead)}" +
        (progress.totalBytes?.let { " of ${formatProjectImportBytes(it)}" } ?: "") +
        (studentProjectImportProgressFraction(progress)?.let { " (${(it * 100).roundToInt()}%)" } ?: "") +
        ". Your project data has not changed."

internal fun studentProjectImportProgressFraction(progress: StudentProjectFileImportProgress): Float? =
    progress.totalBytes
        ?.takeIf { it > 0L }
        ?.let { totalBytes ->
            (progress.bytesRead.coerceAtLeast(0L).toDouble() / totalBytes.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }

private fun formatProjectImportBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes bytes"
}

@Composable
internal fun EvidriloStudentProjectsScreen(
    state: StudentProjectListUiState,
    notice: String?,
    onRetry: () -> Unit,
    onOpenCatalog: () -> Unit,
    onResume: (StudentProjectDraft) -> Unit,
    onCreateManualProject: () -> Unit,
    onExportProject: (StudentProjectDraft) -> Unit = onResume,
    onMarkCompleted: (StudentProjectDraft) -> Boolean,
    onArchive: (StudentProjectDraft) -> Boolean,
    onMoveToTrash: (StudentProjectDraft) -> Boolean,
    onRestore: (StudentProjectDraft, Boolean) -> Boolean,
    onPermanentlyDelete: (StudentProjectDraft) -> Boolean,
    onImportProject: suspend (
        StudentProjectArchiveImportPayload,
        importAsCopy: Boolean,
        archiveWhenAtCapacity: Boolean,
        ensureActive: () -> Unit,
    ) -> StudentProjectDraftFlowResult<StudentProjectImportReceipt>,
    onRestoreArchiveRevision: (StudentProjectDraft) -> StudentProjectDraftFlowResult<StudentProjectImportReceipt>,
    activeLimit: Int,
    onOpenPremium: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
) {
    var confirmTarget by remember { mutableStateOf<Pair<StudentProjectDraft, Boolean>?>(null) }
    var archiveImportDialog by remember { mutableStateOf<StudentProjectArchiveImportDialogState>(StudentProjectArchiveImportDialogState.Hidden) }
    val archiveImportScope = rememberCoroutineScope()
    var archiveImportJob by remember { mutableStateOf<Job?>(null) }
    val selectProjectArchive = rememberStudentProjectFileImporter(
        onProgress = { progress -> archiveImportDialog = StudentProjectArchiveImportDialogState.Reading(progress) },
    ) { result ->
        when (result) {
            is StudentProjectFileImportResult.Selected -> {
                archiveImportDialog = StudentProjectArchiveImportDialogState.Busy("Validating archive contents…")
                archiveImportJob = archiveImportScope.launch {
                    try {
                        val preview = withContext(Dispatchers.Default) {
                            val importContext = currentCoroutineContext()
                            StudentProjectArchiveCodec.preview(result.bytes, ensureActive = importContext::ensureActive)
                        }
                        archiveImportDialog = when (preview) {
                            is StudentProjectArchiveReadResult.Preview -> StudentProjectArchiveImportDialogState.Preview(
                                StudentProjectArchiveImportPayload(preview, result.bytes),
                            )
                            is StudentProjectArchiveReadResult.Rejected -> StudentProjectArchiveImportDialogState.Error(
                                studentProjectArchiveImportFailure(preview.code),
                            )
                        }
                    } catch (_: CancellationException) {
                        archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden
                    } catch (_: Exception) {
                        archiveImportDialog = StudentProjectArchiveImportDialogState.Error(
                            "The project archive could not be validated. Your existing projects were not changed.",
                        )
                    }
                }
            }
            is StudentProjectFileImportResult.Failed -> archiveImportDialog = StudentProjectArchiveImportDialogState.Error(result.message)
            is StudentProjectFileImportResult.Unavailable -> archiveImportDialog = StudentProjectArchiveImportDialogState.Error(result.message)
            StudentProjectFileImportResult.Cancelled -> archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden
        }
    }

    fun cancelArchiveImport() {
        selectProjectArchive.cancel()
        archiveImportJob?.cancel()
        archiveImportJob = null
        archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden
    }

    fun commitArchiveImport(payload: StudentProjectArchiveImportPayload, asCopy: Boolean, archiveAtCapacity: Boolean) {
        archiveImportDialog = StudentProjectArchiveImportDialogState.Busy("Importing project and selected attachments…")
        archiveImportJob = archiveImportScope.launch {
            archiveImportDialog = try {
                val importContext = currentCoroutineContext()
                when (val result = onImportProject(payload, asCopy, archiveAtCapacity, importContext::ensureActive)) {
                    is StudentProjectDraftFlowResult.Value -> StudentProjectArchiveImportDialogState.Hidden
                    is StudentProjectDraftFlowResult.Rejected -> when (result.code) {
                        "PROJECT_IMPORT_ID_CONFLICT" -> {
                            val existing = (state as? StudentProjectListUiState.Loaded)?.projects
                                ?.singleOrNull { it.id == payload.preview.project.id }
                            if (existing == null) {
                                StudentProjectArchiveImportDialogState.Error("The conflicting local project could not be loaded. No projects were changed.")
                            } else {
                                StudentProjectArchiveImportDialogState.Duplicate(payload, existing)
                            }
                        }
                        "PROJECT_IMPORT_ALREADY_PRESENT" -> StudentProjectArchiveImportDialogState.AlreadyPresent(payload.preview.project)
                        "PROJECT_ACTIVE_LIMIT_REACHED" -> StudentProjectArchiveImportDialogState.AtCapacity(payload, asCopy)
                        else -> StudentProjectArchiveImportDialogState.Error(
                            studentProjectDraftFlowMessage(result) ?: "The project archive was not imported. No projects were changed.",
                        )
                    }
                    else -> StudentProjectArchiveImportDialogState.Error(
                        studentProjectDraftFlowMessage(result) ?: "The project archive was not imported. No projects were changed.",
                    )
                }
            } catch (_: CancellationException) {
                StudentProjectArchiveImportDialogState.Hidden
            } catch (_: Exception) {
                StudentProjectArchiveImportDialogState.Error("The project archive could not be imported. Existing project data was preserved.")
            }
        }
    }

    fun restoreArchiveRevision(payload: StudentProjectArchiveImportPayload) {
        archiveImportDialog = when (val result = onRestoreArchiveRevision(payload.preview.project)) {
            is StudentProjectDraftFlowResult.Value -> StudentProjectArchiveImportDialogState.Hidden
            else -> StudentProjectArchiveImportDialogState.Error(
                studentProjectDraftFlowMessage(result) ?: "The archive could not be restored as a new revision. Local work was not replaced.",
            )
        }
    }

    EvidriloTargetSurface(selected = EvidriloTargetSection.HOME, onNavigate = onNavigate) {
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
        EvidriloBackGesture(label="Home",onClick=onBack)
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
            contentPadding=PaddingValues(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item(key="heading") {
                EvidriloPageHeading(uiText("Your projects"),uiText("Make room for your ideas."),action={
                    if(isStudentProjectFileImportAvailable) EvidriloIconButton(EvidriloIconName.UPLOAD,uiText("Import project"),selectProjectArchive.select)
                })
            }
            notice?.let { item(key="notice") {EvidriloTargetCard {Text(uiText(it),style=MaterialTheme.typography.bodyMedium)}} }
            when(state) {
                StudentProjectListUiState.Loading -> item {EvidriloTargetCard {Text("Loading local projects…")}}
                is StudentProjectListUiState.Loaded -> {
                    val projects=state.projects.sortedByDescending {it.updatedAtEpochMillis}
                    item(key="capacity") {Text(uiText("${projects.size} of $activeLimit projects","${projects.size} dari $activeLimit proyek"),style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)}
                    if(projects.isEmpty()) item(key="empty") {
                        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.fillMaxWidth().height(190.dp)) {EvidriloProjectIllustration(null,Modifier.matchParentSize())}
                            Text("A place for your next question",style=MaterialTheme.typography.titleLarge)
                            Text("Choose a project structure. Bring your own ideas and evidence.",style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
                        }
                    }
                    if(projects.size>=activeLimit) item(key="full") {Text("Your project spaces are full. Export and remove a project you no longer need.",style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)}
                    items(projects,key={"project:"+it.id}) {project ->
                        StudentProjectCard(project=project,onResume={onResume(project)},onMarkCompleted={onMarkCompleted(project)},
                            onArchive={},onMoveToTrash={},onRestore={onRestore(project,false)},onRestoreArchived={},
                            onPermanentlyDelete={confirmTarget=project to true},onExport={onExportProject(project)})
                    }
                }
                StudentProjectListUiState.StorageUnavailable -> item {ProjectStorageState("Local project storage is unavailable","This platform build cannot save project drafts. Your existing project-type guidance remains available.",onRetry)}
                StudentProjectListUiState.StorageCorrupt -> item {ProjectStorageState("Saved project data could not be read","The app did not overwrite the unreadable data. Retry, or keep browsing the catalog without creating a draft.",onRetry)}
                StudentProjectListUiState.StorageFailed -> item {ProjectStorageState("Projects could not be loaded","No project state was assumed. Check local storage and retry.",onRetry)}
            }
        }
        }
        if(state is StudentProjectListUiState.Loaded) {
            Box(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)) {
                EvidriloPrimaryButton(uiText("Create a project","Buat proyek"),onOpenCatalog,
                    enabled=state.projects.size<activeLimit,trailingIcon=EvidriloIconName.PLUS)
            }
        }
        }
    }

    confirmTarget?.let { (project, permanent) ->
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(uiText(if (permanent) "Delete permanently?" else "Move project to Trash?")) },
            text = {
                Text(if (permanent) {
                    uiText("${project.title} will be permanently removed from this device. This cannot be undone.", "${project.title} akan dihapus permanen dari perangkat ini. Tindakan ini tidak dapat dibatalkan.")
                } else {
                    "${project.title} will stay recoverable in Trash for 30 days."
                })
            },
            confirmButton = {
                TextButton(onClick = {
                    val done = if (permanent) onPermanentlyDelete(project) else onMoveToTrash(project)
                    if (done) confirmTarget = null
                }) { Text(uiText(if (permanent) "Delete permanently" else "Move to Trash")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text(uiText("Cancel")) }
            },
        )
    }

    when (val importState = archiveImportDialog) {
        StudentProjectArchiveImportDialogState.Hidden -> Unit
        is StudentProjectArchiveImportDialogState.Busy -> AlertDialog(
            onDismissRequest = ::cancelArchiveImport,
            title = { Text("Working with project archive") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = "Project archive operation progress"
                            stateDescription = importState.message
                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                        },
                    )
                    Text(importState.message)
                }
            },
            confirmButton = {
                TextButton(onClick = ::cancelArchiveImport) { Text("Cancel import") }
            },
        )
        is StudentProjectArchiveImportDialogState.Reading -> AlertDialog(
            onDismissRequest = ::cancelArchiveImport,
            title = { Text("Reading project archive") },
            text = {
                val fraction = studentProjectImportProgressFraction(importState.progress)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(studentProjectImportProgressLabel(importState.progress))
                    if (fraction == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "Project archive import progress"
                                stateDescription = "Reading project archive; total size is unknown"
                                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                            },
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "Project archive import progress"
                                stateDescription = "${(fraction * 100).roundToInt()}% read"
                                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = ::cancelArchiveImport) { Text("Cancel import") }
            },
        )
        is StudentProjectArchiveImportDialogState.Preview -> AlertDialog(
            onDismissRequest = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden },
            title = { Text("Review project archive") },
            text = {
                Column {
                    val project = importState.payload.preview.project
                    RawText(project.title, style = MaterialTheme.typography.titleMedium)
                    Text("Revision ${project.revision} · ${project.sources.size} sources · ${project.findings.size} findings · ${importState.payload.preview.manifest.attachments.size} attachments")
                    Text("This is student-entered project data. Import does not verify sources, methods, or claims. Attachments are restored only to private storage on this device.")
                    Text("Existing projects will never be overwritten.")
                }
            },
            confirmButton = {
                TextButton(onClick = { commitArchiveImport(importState.payload, asCopy = false, archiveAtCapacity = false) }) {
                    Text(uiText("Import project"))
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden }) { Text(uiText("Cancel")) }
            },
        )
        is StudentProjectArchiveImportDialogState.AlreadyPresent -> AlertDialog(
            onDismissRequest = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden },
            title = { Text("Project already exists") },
            text = { Text("The same project and revision are already on this device. Nothing was imported.") },
            confirmButton = {
                TextButton(onClick = {
                    archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden
                    onResume(importState.project)
                }) { Text(uiText("Open project")) }
            },
            dismissButton = {
                TextButton(onClick = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden }) { Text(uiText("Close")) }
            },
        )
        is StudentProjectArchiveImportDialogState.Duplicate -> AlertDialog(
            onDismissRequest = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden },
            title = { Text("Review project revision conflict") },
            text = {
                Column {
                    val imported = importState.payload.preview.project
                    Text("Local revision ${importState.existing.revision} · archive revision ${imported.revision}")
                    val changedAreas = studentProjectArchiveChangedAreas(importState.existing, imported)
                    Text("Changed sections: ${changedAreas.ifEmpty { listOf("No content difference detected") }.joinToString()}")
                    Text("The local project is never overwritten. Restore creates a new local revision; importing as a copy assigns fresh IDs and remaps links and attachments.")
                    if (importState.existing.attachments != imported.attachments) {
                        Text("The attachment set differs. To avoid replacing files already used by this project, import the archive as a separate copy.")
                    }
                }
            },
            confirmButton = {
                Column {
                    val imported = importState.payload.preview.project
                    if (imported.revision > importState.existing.revision && importState.existing.attachments == imported.attachments) {
                        TextButton(onClick = { restoreArchiveRevision(importState.payload) }) {
                            Text("Restore as a new revision")
                        }
                    }
                    TextButton(onClick = { commitArchiveImport(importState.payload, asCopy = true, archiveAtCapacity = false) }) {
                        Text("Import as a copy")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden }) { Text(uiText("Cancel")) }
            },
        )
        is StudentProjectArchiveImportDialogState.AtCapacity -> AlertDialog(
            onDismissRequest = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden },
            title = { Text(uiText("Project spaces are full", "Ruang proyek penuh")) },
            text = { Text(uiText("This installation can hold $activeLimit projects, including completed projects. Export and delete a project you no longer need before importing another.",
                "Instalasi ini dapat menyimpan $activeLimit proyek, termasuk yang selesai. Ekspor lalu hapus proyek yang tidak lagi diperlukan sebelum mengimpor proyek baru.")) },
            confirmButton = {
                TextButton(onClick = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden }) { Text(uiText("Close")) }
            },
        )
        is StudentProjectArchiveImportDialogState.Error -> AlertDialog(
            onDismissRequest = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden },
            title = { Text("Project was not imported") },
            text = { Text(importState.message) },
            confirmButton = {
                TextButton(onClick = { archiveImportDialog = StudentProjectArchiveImportDialogState.Hidden }) { Text(uiText("Close")) }
            },
        )
    }
}

private fun studentProjectArchiveChangedAreas(
    local: StudentProjectDraft,
    archived: StudentProjectDraft,
): List<String> = buildList {
    if (local.templateSnapshot != archived.templateSnapshot) add("method template")
    if (local.title != archived.title) add("project title")
    if (local.deadlineDate != archived.deadlineDate) add("project deadline")
    if (local.fieldValues != archived.fieldValues) add("assignment and project fields")
    if (local.sources != archived.sources) add("sources")
    if (local.themes != archived.themes) add("synthesis themes")
    if (local.claimEvidenceSourceIds != archived.claimEvidenceSourceIds) add("claim-source links")
    if (local.evidenceItems != archived.evidenceItems) add("evidence notes")
    if (local.findings != archived.findings) add("findings")
    if (local.evidenceRelations != archived.evidenceRelations) add("evidence relationships")
    if (local.attachments != archived.attachments) add("file attachments")
}

private fun studentProjectArchiveImportFailure(code: String): String = when (code) {
    "PROJECT_ARCHIVE_ATTACHMENT_STORE_UNAVAILABLE" -> "This platform cannot restore project file attachments in this build. Your original file and local projects were left unchanged."
    "PROJECT_ARCHIVE_ATTACHMENT_STAGE_FAILED", "PROJECT_IMPORT_ATTACHMENT_PUBLISH_FAILED" -> "File attachments could not be staged safely. No project data was imported; retry after checking device storage."
    "PROJECT_ARCHIVE_INTEGRITY_MISMATCH", "PROJECT_ARCHIVE_CRC_MISMATCH" -> "The archive integrity check failed. The file may be damaged or edited; no project data was imported."
    "PROJECT_ARCHIVE_COMPRESSION_RATIO_EXCEEDED", "PROJECT_ARCHIVE_EXPANSION_LIMIT" -> "The archive exceeds safe decompression limits. No project data was imported."
    "PROJECT_ARCHIVE_SCHEMA_UNSUPPORTED" -> "This archive uses an unsupported project format version. No project data was imported."
    else -> "The archive is invalid or uses unsupported content (${code}). The source file and local projects were left unchanged."
}

@Composable
private fun StudentProjectCard(
    project: StudentProjectDraft, onResume: () -> Unit, onMarkCompleted: () -> Unit,
    onArchive: () -> Unit, onMoveToTrash: () -> Unit, onRestore: () -> Unit,
    onRestoreArchived: () -> Unit, onPermanentlyDelete: () -> Unit, onExport: () -> Unit,
) {
    var actionsExpanded by remember(project.id) { mutableStateOf(false) }
    val progress=StudentProjectDraftRules.requiredFieldProgress(project)
    val completed=project.status==StudentProjectStatus.COMPLETED
    val inactive=project.status==StudentProjectStatus.ARCHIVED
    val legacyRemoved=project.status==StudentProjectStatus.TRASHED
    androidx.compose.material3.Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),color=EvidriloColors.Atmosphere) {
        Box(Modifier.fillMaxWidth()) {
            EvidriloProjectIllustration(project.templateSnapshot?.family,Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    EvidriloIcon(if(completed) EvidriloIconName.CHECK else if(legacyRemoved) EvidriloIconName.ALERT else EvidriloIconName.LAYERS,
                        tint=EvidriloColors.Cobalt,modifier=Modifier.size(18.dp))
                    Text(uiText(if(completed) "Completed" else if(inactive) "Inactive" else if(legacyRemoved) "Recovery needed" else "Active"),
                        Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Cobalt)
                    Box {
                        EvidriloIconButton(EvidriloIconName.MORE,"More actions for ${project.title}",{actionsExpanded=true})
                        DropdownMenu(actionsExpanded,{actionsExpanded=false},containerColor=EvidriloColors.Card) {
                            studentProjectSecondaryActions(project.status).forEach { action ->
                                val label=when(action) { StudentProjectCardAction.MARK_COMPLETE -> uiText("Mark complete","Tandai selesai");
                                    StudentProjectCardAction.EXPORT -> uiText("Export project","Ekspor proyek");
                                    StudentProjectCardAction.DELETE_PERMANENTLY -> uiText("Delete project","Hapus proyek");
                                    else -> uiText(if(legacyRemoved) "Recover project" else "Reopen project") }
                                DropdownMenuItem(text={Text(label)},onClick={
                                    actionsExpanded=false
                                    when(action) { StudentProjectCardAction.MARK_COMPLETE -> onMarkCompleted(); StudentProjectCardAction.EXPORT -> onExport();
                                        StudentProjectCardAction.DELETE_PERMANENTLY -> onPermanentlyDelete(); else -> onRestore() }
                                })
                            }
                        }
                    }
                }
                RawText(project.title,Modifier.padding(end=88.dp),style=MaterialTheme.typography.titleLarge,maxLines=3,
                    overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(uiText(project.templateSnapshot?.family?.let(::projectFamilyShortName) ?: "Independent project"),
                    style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
                Text(uiText("${project.evidenceItems.size} notes · ${StudentProjectDraftRules.effectiveClaims(project).size} claims",
                    "${project.evidenceItems.size} catatan · ${StudentProjectDraftRules.effectiveClaims(project).size} klaim"),
                    style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
                LinearProgressIndicator(progress={if(progress.totalRequired==0) 0f else progress.filledRequired.toFloat()/progress.totalRequired},
                    modifier=Modifier.fillMaxWidth().height(5.dp),color=EvidriloColors.Cobalt,trackColor=EvidriloColors.Tint)
                Text(uiText("${progress.filledRequired}/${progress.totalRequired} required responses", "${progress.filledRequired}/${progress.totalRequired} isian wajib"),
                    style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
                EvidriloPrimaryButton(uiText(if(legacyRemoved) "Recover project" else if(completed || inactive) "Open project" else "Continue project"),
                    if(legacyRemoved) onRestore else onResume,trailingIcon=EvidriloIconName.ARROW_FORWARD)
            }
        }
    }
}

@Composable
private fun ProjectStorageState(
    title: String,
    message: String,
    onRetry: () -> Unit,
) {
    EvidriloTargetCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        EvidriloSecondaryButton(label = "Retry", onClick = onRetry)
    }
}

@Composable
internal fun EvidriloStudentProjectEditorScreen(
    draft: StudentProjectDraft,
    attachmentStore: StudentProjectAttachmentStore,
    projectAiAccountKey: String?,
    aiCreditBalance: AiCreditBalancePresentation = AiCreditBalancePresentation.SignInRequired,
    projectAiState: ProjectAiScaffoldUiState,
    projectAiConsentState: ProjectAiConsentUiState,
    notice: String?,
    isDirty: Boolean,
    showExitConfirmation: Boolean,
    saveError: String?,
    onDirtyChanged: (Boolean) -> Unit,
    onSave: (
        String,
        Map<String, String>,
        List<StudentProjectSourceRecord>,
        List<StudentProjectSynthesisTheme>,
        Set<String>,
        List<StudentProjectEvidenceItem>,
        List<StudentProjectFindingRecord>,
        List<StudentProjectEvidenceRelation>,
        List<StudentProjectClaimRecord>,
        List<StudentProjectLimitationActionRecord>,
        String?,
    ) -> StudentProjectDraft?,
    onAutosave: (
        String,
        Map<String, String>,
        List<StudentProjectSourceRecord>,
        List<StudentProjectSynthesisTheme>,
        Set<String>,
        List<StudentProjectEvidenceItem>,
        List<StudentProjectFindingRecord>,
        List<StudentProjectEvidenceRelation>,
        List<StudentProjectClaimRecord>,
        List<StudentProjectLimitationActionRecord>,
        String?,
    ) -> StudentProjectDraft?,
    onAddAttachment: suspend (String, ByteArray, () -> Unit) -> StudentProjectDraftFlowResult<StudentProjectAttachmentAddReceipt>,
    onRemoveAttachment: suspend (String) -> StudentProjectDraftFlowResult<StudentProjectAttachmentRemoveReceipt>,
    onRestoreRevision: (Int) -> StudentProjectDraft?,
    onRequestProjectAi: (String?, String, String?, Map<String, String>, Int?, Boolean) -> Unit,
    onRefreshAiCreditBalance: () -> Unit = {},
    onRefreshProjectAiConsent: () -> Unit,
    onGrantProjectAiConsent: () -> Unit,
    onRevokeProjectAiConsent: () -> Unit,
    onApplyProjectAi: (ProjectAiScaffoldProposal, Set<String>, Map<String, String>, Set<String>, String, Int) -> StudentProjectDraft?,
    onDiscardProjectAiPreview: (String, Int) -> Unit,
    onRetryProjectAiSettlement: (ProjectAiScaffoldUiState.SettlementFailed) -> Unit,
    projectAiStageAssistState: ProjectAiStageAssistUiState,
    projectAiAccountAvailable: Boolean,
    projectAiAccountMessage: String?,
    onRequestProjectAiStageAssist: (String, String, Map<String, String>, List<ProjectAiStageAssistSelectedEvidence>) -> Unit,
    onApplyProjectAiStageAssist: (ProjectAiStageAssistSession, Set<String>, Map<String, String>) -> Unit,
    onDismissProjectAiStageAssist: (ProjectAiStageAssistSession) -> Unit,
    onRetryProjectAiStageAssistSettlement: (ProjectAiStageAssistUiState.SettlementFailed) -> Unit,
    projectAiActivityHistoryState: ProjectAiActivityHistoryUiState,
    onRefreshProjectAiActivity: (String?) -> Unit,
    onLoadMoreProjectAiActivity: (String?, String) -> Unit,
    onRetryUnknownProjectAiStageAssist: (ProjectAiStageAssistUiState.OutcomeUnknown) -> Unit,
    onRequestClose: () -> Unit,
    onSaveAndLeave: () -> Unit,
    onDiscardAndLeave: () -> Unit,
    onCancelExit: () -> Unit,
    exportRequested: Boolean = false,
    onConsumeExportRequest: () -> Unit = {},
    onAskAiOpinion: (StudentProjectDraft) -> Unit = {},
) {
    var savedTitle by remember(draft.id, draft.revision) { mutableStateOf(draft.title) }
    var savedValues by remember(draft.id, draft.revision) { mutableStateOf(draft.fieldValues.toMap()) }
    var savedSources by remember(draft.id, draft.revision) { mutableStateOf(draft.sources.toList()) }
    var savedThemes by remember(draft.id, draft.revision) { mutableStateOf(draft.themes.toList()) }
    var savedClaimLinks by remember(draft.id, draft.revision) { mutableStateOf(draft.claimEvidenceSourceIds.toSet()) }
    var savedEvidenceItems by remember(draft.id, draft.revision) { mutableStateOf(draft.evidenceItems.toList()) }
    var savedFindings by remember(draft.id, draft.revision) { mutableStateOf(draft.findings.toList()) }
    var savedEvidenceRelations by remember(draft.id, draft.revision) { mutableStateOf(draft.evidenceRelations.toList()) }
    var savedClaims by remember(draft.id, draft.revision) {
        mutableStateOf(dev.nextgen.mobile.domain.project.StudentProjectDraftRules.effectiveClaims(draft))
    }
    var savedLimitationActions by remember(draft.id, draft.revision) { mutableStateOf(draft.limitationActions.toList()) }
    var savedDeadlineDate by remember(draft.id, draft.revision) { mutableStateOf(draft.deadlineDate) }
    var title by remember(draft.id) { mutableStateOf(draft.title) }
    var values by remember(draft.id) { mutableStateOf(draft.fieldValues.toMap()) }
    var sources by remember(draft.id) { mutableStateOf(draft.sources.toList()) }
    var themes by remember(draft.id) { mutableStateOf(draft.themes.toList()) }
    var claimLinks by remember(draft.id) { mutableStateOf(draft.claimEvidenceSourceIds.toSet()) }
    var evidenceItems by remember(draft.id) { mutableStateOf(draft.evidenceItems.toList()) }
    var findings by remember(draft.id) { mutableStateOf(draft.findings.toList()) }
    var evidenceRelations by remember(draft.id) { mutableStateOf(draft.evidenceRelations.toList()) }
    var claims by remember(draft.id) {
        mutableStateOf(dev.nextgen.mobile.domain.project.StudentProjectDraftRules.effectiveClaims(draft))
    }
    var limitationActions by remember(draft.id) { mutableStateOf(draft.limitationActions.toList()) }
    var deadlineDate by remember(draft.id) { mutableStateOf(draft.deadlineDate) }
    var sourceSequence by remember(draft.id) { mutableStateOf(draft.sources.size + 1) }
    var themeSequence by remember(draft.id) { mutableStateOf(draft.themes.size + 1) }
    var evidenceSequence by remember(draft.id) { mutableStateOf(draft.evidenceItems.size + 1) }
    var findingSequence by remember(draft.id) { mutableStateOf(draft.findings.size + 1) }
    var claimSequence by remember(draft.id) { mutableStateOf(claims.size + 1) }
    var limitationActionSequence by remember(draft.id) { mutableStateOf(draft.limitationActions.size + 1) }
    var localError by remember(draft.id) { mutableStateOf<String?>(null) }
    var autosaveStatus by remember(draft.id) { mutableStateOf("Saved on this device.") }
    var showExportChoices by remember(draft.id) { mutableStateOf(false) }
    var showReviewMap by remember(draft.id) { mutableStateOf(false) }
    LaunchedEffect(exportRequested,draft.id) { if(exportRequested) { showExportChoices=true; onConsumeExportRequest() } }
    var exportNotice by remember(draft.id) { mutableStateOf<String?>(null) }
    var exportReviewState by remember(draft.id) { mutableStateOf<StudentProjectExportReviewState>(StudentProjectExportReviewState.Hidden) }
    var removalReviewNotice by remember(draft.id) { mutableStateOf<String?>(null) }
    var attachmentNotice by remember(draft.id) { mutableStateOf<String?>(null) }
    var attachmentImportInProgress by remember(draft.id) { mutableStateOf(false) }
    var attachmentImportJob by remember(draft.id) { mutableStateOf<Job?>(null) }
    var attachmentRemovalTarget by remember(draft.id) { mutableStateOf<StudentProjectAttachmentRef?>(null) }
    var attachmentRemovalInProgress by remember(draft.id) { mutableStateOf(false) }
    var attachmentRemovalIssue by remember(draft.id) { mutableStateOf<String?>(null) }
    var attachmentTextReadInProgress by remember(draft.id) { mutableStateOf<String?>(null) }
    var attachmentTextReadJob by remember(draft.id) { mutableStateOf<Job?>(null) }
    var attachmentTextPreview by remember(draft.id) { mutableStateOf<StudentProjectAttachmentTextPreview?>(null) }
    val exportProjectFile = rememberStudentProjectFileExporter { result -> exportNotice = result.toNotice() }
    val archiveExportScope = rememberCoroutineScope()
    var archiveExportInProgress by remember(draft.id) { mutableStateOf(false) }
    val requiredFieldProgress = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.requiredFieldProgress(draft.copy(fieldValues = values))
    val manual = draft.templateSnapshot == null
    val manualDraft = draft.copy(
        title = title,
        fieldValues = values,
        sources = sources,
        themes = themes,
        claimEvidenceSourceIds = claimLinks,
        evidenceItems = evidenceItems,
        findings = findings,
        evidenceRelations = evidenceRelations,
        claims = claims,
        limitationActions = limitationActions,
        deadlineDate = deadlineDate,
    )
    val structure = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.structureReport(manualDraft)
    val editorSections = remember(draft.id, draft.templateSnapshot) { studentProjectEditorSections(draft) }
    val bookmarkStore = remember { createProjectSectionBookmarkStore() }
    val bookmarkKey = projectSectionBookmarkKey(draft)
    val bookmarkLoad = remember(bookmarkKey) { bookmarkStore.read(bookmarkKey) }
    var bookmarkNotice by remember(bookmarkKey) { mutableStateOf(
        if (bookmarkLoad !is LocalStorageReadResult.Success) "Your last section could not be restored. Your project is still available." else null
    ) }
    var editorSectionIndex by remember(bookmarkKey) { mutableStateOf(
        editorSections.indexOfFirst { it.navigationId == bookmarkLoad.value }.takeIf { it >= 0 } ?: 0
    ) }
    LaunchedEffect(bookmarkKey) {
        if (editorSections.none { it.navigationId == bookmarkLoad.value }) {
            bookmarkNotice = if (bookmarkStore.write(bookmarkKey, editorSections[editorSectionIndex].navigationId) == LocalStorageWriteResult.SAVED) null
                else "Your section position could not be remembered. Your project is still available."
        }
    }
    val activeEditorSectionIndex = editorSectionIndex.coerceIn(0, editorSections.lastIndex)
    val activeEditorSection = editorSections[activeEditorSectionIndex]
    val editorProgress = studentProjectEditorProgress(activeEditorSectionIndex, editorSections.size)
    val editorProgressDescription = studentProjectEditorProgressDescription(
        activeEditorSectionIndex,
        editorSections.size,
        activeEditorSection.title,
    )

    fun reportDirty(
        nextTitle: String = title,
        nextValues: Map<String, String> = values,
        nextSources: List<StudentProjectSourceRecord> = sources,
        nextThemes: List<StudentProjectSynthesisTheme> = themes,
        nextClaimLinks: Set<String> = claimLinks,
        nextEvidenceItems: List<StudentProjectEvidenceItem> = evidenceItems,
        nextFindings: List<StudentProjectFindingRecord> = findings,
        nextEvidenceRelations: List<StudentProjectEvidenceRelation> = evidenceRelations,
        nextClaims: List<StudentProjectClaimRecord> = claims,
        nextLimitationActions: List<StudentProjectLimitationActionRecord> = limitationActions,
        nextDeadlineDate: String? = deadlineDate,
    ) {
        val dirty =
            nextTitle != savedTitle || nextValues != savedValues || nextSources != savedSources ||
                nextThemes != savedThemes || nextClaimLinks != savedClaimLinks ||
                nextEvidenceItems != savedEvidenceItems || nextFindings != savedFindings ||
                nextEvidenceRelations != savedEvidenceRelations || nextClaims != savedClaims ||
                nextLimitationActions != savedLimitationActions || nextDeadlineDate != savedDeadlineDate
        onDirtyChanged(dirty)
        if (dirty) autosaveStatus = "Changes will be saved locally shortly."
    }

    fun changeClaims(nextClaims: List<StudentProjectClaimRecord>) {
        val validClaimIds = nextClaims.mapTo(mutableSetOf(), StudentProjectClaimRecord::id)
        val removedClaimIds = claims.mapTo(mutableSetOf(), StudentProjectClaimRecord::id) - validClaimIds
        claims = nextClaims
        val acceptsLegacyMirror = draft.templateSnapshot?.inputFields
            ?.any { it.id == ManualLiteratureSynthesisFields.CLAIM } != false
        val primaryStatement = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.primaryClaim(nextClaims)?.statement
        val nextValues = if (!acceptsLegacyMirror) values else if (primaryStatement == null) {
            values - ManualLiteratureSynthesisFields.CLAIM
        } else {
            values + (ManualLiteratureSynthesisFields.CLAIM to primaryStatement)
        }
        values = nextValues
        var nextLimitationActions = limitationActions
        removedClaimIds.forEach { removedId ->
            nextLimitationActions = detachClaimFromLimitationActions(nextLimitationActions, removedId)
        }
        if (nextLimitationActions != limitationActions) {
            limitationActions = nextLimitationActions
            removalReviewNotice = "Removed claim links were detached from limitation/action records. Their boundary, reason, and next-action text were retained for review."
        }
        val retainedRelations = evidenceRelations.filter { relation ->
            relation.targetType != StudentProjectEvidenceTargetType.CLAIM || relation.targetId in validClaimIds
        }
        evidenceRelations = retainedRelations
        reportDirty(
            nextValues = nextValues,
            nextEvidenceRelations = retainedRelations,
            nextClaims = nextClaims,
            nextLimitationActions = nextLimitationActions,
        )
    }

    fun changeFindings(nextFindings: List<StudentProjectFindingRecord>) {
        val validFindingIds = nextFindings.mapTo(mutableSetOf(), StudentProjectFindingRecord::id)
        val removedFindingIds = findings.mapTo(mutableSetOf(), StudentProjectFindingRecord::id) - validFindingIds
        var nextLimitationActions = limitationActions
        removedFindingIds.forEach { removedId ->
            nextLimitationActions = detachFindingFromLimitationActions(nextLimitationActions, removedId)
        }
        if (nextLimitationActions != limitationActions) {
            limitationActions = nextLimitationActions
            removalReviewNotice = "Removed finding links were detached from limitation/action records. Their boundary, reason, and next-action text were retained for review."
        }
        val nextRelations = evidenceRelations.filter { relation ->
            relation.targetType != StudentProjectEvidenceTargetType.FINDING || relation.targetId in validFindingIds
        }
        findings = nextFindings
        evidenceRelations = nextRelations
        reportDirty(
            nextFindings = nextFindings,
            nextEvidenceRelations = nextRelations,
            nextLimitationActions = nextLimitationActions,
        )
    }

    fun acceptSavedProject(saved: StudentProjectDraft) {
        title = saved.title
        values = saved.fieldValues.toMap()
        sources = saved.sources.toList()
        themes = saved.themes.toList()
        claimLinks = saved.claimEvidenceSourceIds.toSet()
        evidenceItems = saved.evidenceItems.toList()
        findings = saved.findings.toList()
        evidenceRelations = saved.evidenceRelations.toList()
        claims = saved.claims.toList()
        limitationActions = saved.limitationActions.toList()
        deadlineDate = saved.deadlineDate
        savedTitle = saved.title
        savedValues = saved.fieldValues.toMap()
        savedSources = saved.sources.toList()
        savedThemes = saved.themes.toList()
        savedClaimLinks = saved.claimEvidenceSourceIds.toSet()
        savedEvidenceItems = saved.evidenceItems.toList()
        savedFindings = saved.findings.toList()
        savedEvidenceRelations = saved.evidenceRelations.toList()
        savedClaims = saved.claims.toList()
        savedLimitationActions = saved.limitationActions.toList()
        savedDeadlineDate = saved.deadlineDate
        localError = null
        autosaveStatus = "Saved on this device."
        onDirtyChanged(false)
    }

    fun saveProjectSnapshot(snapshot: StudentProjectDraft): StudentProjectDraft? {
        val saved = onSave(
            snapshot.title,
            snapshot.fieldValues,
            snapshot.sources,
            snapshot.themes,
            snapshot.claimEvidenceSourceIds,
            snapshot.evidenceItems,
            snapshot.findings,
            snapshot.evidenceRelations,
            snapshot.claims,
            snapshot.limitationActions,
            snapshot.deadlineDate,
        )
        if (saved != null) {
            acceptSavedProject(saved)
            return saved
        }
        localError = "The local save did not complete. Your edits remain on this screen; retry before leaving."
        return null
    }

    fun saveCurrent(): StudentProjectDraft? = saveProjectSnapshot(manualDraft)

    fun saveBeforeSectionNavigation(): Boolean {
        if (!isDirty && localError == null) return true
        val saved = onAutosave(
            title,
            values,
            sources,
            themes,
            claimLinks,
            evidenceItems,
            findings,
            evidenceRelations,
            claims,
            limitationActions,
            deadlineDate,
        )
        if (saved == null) {
            localError = "Your changes could not be saved. They remain on this screen; retry before continuing."
            autosaveStatus = "Not saved — your edits are still on this screen."
            return false
        }
        acceptSavedProject(saved)
        return true
    }

    fun navigateToEditorSection(targetIndex: Int) {
        val nextIndex = targetIndex.coerceIn(0, editorSections.lastIndex)
        if (nextIndex == activeEditorSectionIndex || !saveBeforeSectionNavigation()) return
        editorSectionIndex = nextIndex
        bookmarkNotice = if (bookmarkStore.write(bookmarkKey, editorSections[nextIndex].navigationId) == LocalStorageWriteResult.SAVED) null
            else "Your work is saved, but this section position could not be remembered."
    }

    val launchAttachmentPicker = rememberStudentProjectAttachmentPicker { result ->
        when (result) {
            is StudentProjectAttachmentPickResult.Selected -> {
                val savedBeforeImport = if (isDirty || localError != null) saveCurrent() != null else true
                if (!savedBeforeImport) {
                    attachmentNotice = "Save your current edits before attaching a file. Your project was not changed."
                } else {
                    attachmentImportInProgress = true
                    attachmentNotice = null
                    attachmentImportJob = archiveExportScope.launch {
                        val importJob = currentCoroutineContext()[Job]
                        try {
                            withContext(NonCancellable) {
                                val addResult = onAddAttachment(result.file.fileName, result.file.bytes) {
                                    importJob?.ensureActive()
                                }
                                when (addResult) {
                                    is StudentProjectDraftFlowResult.Value -> {
                                        attachmentNotice = if (addResult.value.cleanupPending) {
                                            "${result.file.fileName} is attached to this device-only project. Temporary storage cleanup is still pending; no project evidence was created."
                                        } else {
                                            "${result.file.fileName} is attached privately on this device. Review supported text explicitly; no evidence or verification was created."
                                        }
                                    }
                                    is StudentProjectDraftFlowResult.Rejected -> attachmentNotice = studentProjectAttachmentFailureMessage(addResult.code)
                                    StudentProjectDraftFlowResult.NotFound -> attachmentNotice = "This project is no longer available. No file was attached."
                                    StudentProjectDraftFlowResult.StorageUnavailable -> attachmentNotice = "Private project file storage is unavailable in this build. No file was attached."
                                    StudentProjectDraftFlowResult.StorageCorrupt,
                                    StudentProjectDraftFlowResult.StorageFailed,
                                    -> attachmentNotice = "The file could not be saved with this project. Existing project data was left unchanged."
                                }
                            }
                        } catch (_: CancellationException) {
                            attachmentNotice = "File addition was canceled. The saved project revision and earlier revisions were left unchanged."
                        } finally {
                            withContext(NonCancellable) {
                                attachmentImportInProgress = false
                                attachmentImportJob = null
                            }
                        }
                    }
                }
            }
            StudentProjectAttachmentPickResult.Cancelled -> Unit
            is StudentProjectAttachmentPickResult.Failed -> attachmentNotice = result.message
            is StudentProjectAttachmentPickResult.Unavailable -> attachmentNotice = result.message
        }
    }

    fun previewAttachmentText(attachment: StudentProjectAttachmentRef) {
        if (attachmentTextReadInProgress != null) return
        attachmentTextReadInProgress = attachment.id
        attachmentNotice = null
        attachmentTextReadJob = archiveExportScope.launch {
            try {
                val loaded = withContext(Dispatchers.Default) {
                    val readContext = currentCoroutineContext()
                    when (
                        val read = readStudentProjectAttachmentForPreview(
                            store = attachmentStore,
                            projectId = draft.id,
                            reference = attachment,
                            ensureActive = readContext::ensureActive,
                        )
                    ) {
                        is StudentProjectAttachmentPreviewRead.Loaded ->
                            StudentProjectAttachmentPreviewLoad.Ready(
                                extractStudentProjectDocumentText(
                                    fileName = attachment.fileName,
                                    mimeType = attachment.mimeType,
                                    bytes = read.bytes,
                                    ensureActive = readContext::ensureActive,
                                ),
                            )
                        is StudentProjectAttachmentPreviewRead.Failed ->
                            StudentProjectAttachmentPreviewLoad.Failed(read.code)
                    }
                }
                when (loaded) {
                    is StudentProjectAttachmentPreviewLoad.Failed -> {
                        attachmentNotice = "The saved attachment could not be read or its integrity check failed. The file and project remain unchanged; try attaching the source again."
                    }
                    is StudentProjectAttachmentPreviewLoad.Ready -> when (val result = loaded.result) {
                        is StudentProjectDocumentTextResult.Extracted -> {
                            attachmentTextPreview = StudentProjectAttachmentTextPreview(
                                fileName = attachment.fileName,
                                text = result.text,
                                isTruncated = result.isTruncated,
                            )
                        }
                        StudentProjectDocumentTextResult.EmptyDocument ->
                            attachmentNotice = "No readable text was found. The source file remains attached and unchanged."
                        StudentProjectDocumentTextResult.UnsupportedFormat ->
                            attachmentNotice = "This file type cannot be previewed here. PDF and image attachments remain available for manual note-taking in a compatible viewer."
                        StudentProjectDocumentTextResult.InvalidDocument ->
                            attachmentNotice = "The document is malformed or could not be read safely. The source file remains attached and unchanged."
                        StudentProjectDocumentTextResult.TooLarge ->
                            attachmentNotice = "This document exceeds the on-device text preview limit. The source file remains attached and unchanged."
                    }
                }
            } catch (_: CancellationException) {
                attachmentNotice = "Text preview cancelled. The attachment and project were not changed."
            } finally {
                if (attachmentTextReadInProgress == attachment.id) attachmentTextReadInProgress = null
                attachmentTextReadJob = null
            }
        }
    }

    fun removeAttachmentFromCurrentProject(attachment: StudentProjectAttachmentRef) {
        if (attachmentRemovalInProgress) return
        attachmentRemovalIssue = null
        val savedBeforeRemoval = if (isDirty || localError != null) saveCurrent() != null else true
        if (!savedBeforeRemoval) {
            attachmentRemovalIssue = "Save your current edits before removing a file. The file and project were not changed."
            return
        }
        attachmentRemovalInProgress = true
        attachmentNotice = null
        archiveExportScope.launch {
            try {
                when (val result = onRemoveAttachment(attachment.id)) {
                    is StudentProjectDraftFlowResult.Value -> {
                        attachmentRemovalTarget = null
                        attachmentRemovalIssue = null
                        attachmentNotice = when {
                            result.value.cleanupPending ->
                                "Removed from the current project. Earlier revisions remain preserved; private file cleanup or recovery is still pending. Reopen the project list to retry recovery."
                            result.value.preservedInRevisionHistory ->
                                "Removed from the current project. Earlier saved revisions still retain this file."
                            else ->
                                "Removed from the current project. No earlier revision retained this file, so it was removed from private storage."
                        }
                    }
                    is StudentProjectDraftFlowResult.Rejected ->
                        attachmentRemovalIssue = studentProjectAttachmentRemovalFailureMessage(result.code)
                    StudentProjectDraftFlowResult.NotFound ->
                        attachmentRemovalIssue = "This project is no longer available. The file was not removed."
                    StudentProjectDraftFlowResult.StorageUnavailable ->
                        attachmentRemovalIssue = "Private project file storage is unavailable. The file and revision history were not changed."
                    StudentProjectDraftFlowResult.StorageCorrupt,
                    StudentProjectDraftFlowResult.StorageFailed,
                    -> attachmentRemovalIssue = "The file could not be safely removed. Existing project data and revision history were left unchanged."
                }
            } finally {
                attachmentRemovalInProgress = false
            }
        }
    }

    LaunchedEffect(
        draft.id,
        draft.revision,
        draft.updatedAtEpochMillis,
        title,
        values,
        sources,
        themes,
        claimLinks,
        evidenceItems,
        findings,
        evidenceRelations,
        claims,
        limitationActions,
        deadlineDate,
        isDirty,
    ) {
        if (!isDirty) return@LaunchedEffect
        autosaveStatus = "Saving on this device…"
        delay(PROJECT_AUTOSAVE_DEBOUNCE_MILLIS)
        val autosaved = onAutosave(
            title, values, sources, themes, claimLinks, evidenceItems, findings, evidenceRelations, claims,
            limitationActions, deadlineDate,
        )
        if (autosaved != null) {
            savedTitle = title
            savedValues = values.toMap()
            savedSources = sources.toList()
            savedThemes = themes.toList()
            savedClaimLinks = claimLinks.toSet()
            savedEvidenceItems = evidenceItems.toList()
            savedFindings = findings.toList()
            savedEvidenceRelations = evidenceRelations.toList()
            savedClaims = claims.toList()
            savedLimitationActions = limitationActions.toList()
            savedDeadlineDate = deadlineDate
            localError = null
            autosaveStatus = "Saved on this device."
            onDirtyChanged(false)
        } else {
            localError = "Automatic save failed. Your edits are still on screen; retry or create a checkpoint before leaving."
            autosaveStatus = "Not saved — your edits are still on this screen."
        }
    }

    fun projectSnapshotForExport(): StudentProjectDraft? {
        val snapshot = if (isDirty || dev.nextgen.mobile.domain.project.StudentProjectDraftRules.needsRevisionCheckpoint(manualDraft)) {
            saveCurrent()
        } else {
            draft.copy(
                title = title,
                fieldValues = values.toMap(),
                sources = sources.toList(),
                themes = themes.toList(),
                claimEvidenceSourceIds = claimLinks.toSet(),
                evidenceItems = evidenceItems.toList(),
                findings = findings.toList(),
                evidenceRelations = evidenceRelations.toList(),
                claims = claims.toList(),
                limitationActions = limitationActions.toList(),
            )
        }
        return snapshot
    }

    fun exportSnapshot(buildArtifact: (StudentProjectDraft) -> StudentProjectExportArtifact) {
        projectSnapshotForExport()?.let { snapshot ->
            val review = studentProjectExportReview(snapshot, buildArtifact(snapshot))
            exportReviewState = reduceStudentProjectExportReview(
                exportReviewState,
                StudentProjectExportReviewEvent.Request(review),
            ).state
        }
    }

    fun exportArchiveSnapshot() {
        if (archiveExportInProgress) return
        val snapshot = projectSnapshotForExport() ?: return
        archiveExportInProgress = true
        exportNotice = "Preparing a verified project archive on this device…"
        archiveExportScope.launch {
            val result = withContext(Dispatchers.Default) {
                StudentProjectArchiveCodec.encode(
                    snapshot,
                    studentProjectAppVersion,
                    attachmentStore = attachmentStore,
                )
            }
            archiveExportInProgress = false
            when (result) {
                is StudentProjectArchiveEncodingResult.Encoded -> {
                    exportNotice = null
                    val review = studentProjectExportReview(
                        snapshot,
                        StudentProjectExportArtifact(
                            fileName = result.fileName,
                            mimeType = result.mimeType,
                            content = "",
                            binaryContent = result.bytes,
                        ),
                    )
                    exportReviewState = reduceStudentProjectExportReview(
                        exportReviewState,
                        StudentProjectExportReviewEvent.Request(review),
                    ).state
                }
                is StudentProjectArchiveEncodingResult.Rejected -> {
                    exportNotice = "The project archive could not be prepared (${result.code}). No file was exported."
                }
            }
        }
    }

    EvidriloBackGesture("My projects", onRequestClose)
    EvidriloProjectEditorScaffold(
        projectTitle = title, sectionTitle = activeEditorSection.title, sectionKey = activeEditorSectionIndex,
        progress = editorProgress, progressDescription = editorProgressDescription,
        saveStatus = autosaveStatus, sections = editorSections, sectionIssue = localError ?: saveError,
        draft = manualDraft,
        onSelectSection = { index -> navigateToEditorSection(index); editorSectionIndex == index },
        nextLabel = if (activeEditorSectionIndex < editorSections.lastIndex) {
            if (editorSections[activeEditorSectionIndex + 1].kind == StudentProjectEditorSectionKind.REVIEW) "Review" else "Continue"
        } else null,
        onNext = { navigateToEditorSection(activeEditorSectionIndex + 1) },
    ) {
        key(activeEditorSectionIndex) {
            bookmarkNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
            EvidriloProjectSectionCoach(activeEditorSection, manualDraft)
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
            removalReviewNotice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Cobalt)
            }
            if (activeEditorSection.kind == StudentProjectEditorSectionKind.PROJECT_BASICS) {
                OutlinedTextField(
                value = title,
                onValueChange = { next ->
                    if (next.length <= 160) {
                        title = next
                        reportDirty(nextTitle = next)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(uiText("Project name")) },
                supportingText = { Text(uiText("A short name to find this draft later.")) },
                singleLine = true,
                isError = title.isBlank() || title.length > 160,
                )
                StudentProjectDeadlineEditor(
                    deadlineDate = deadlineDate,
                    onDeadlineDateChange = { nextDate ->
                        deadlineDate = nextDate
                        reportDirty(nextDeadlineDate = nextDate)
                    },
                )
            }

            if (manual && activeEditorSection.kind == StudentProjectEditorSectionKind.TEMPLATE_STEP) {
                    ManualLiteratureSynthesisFields.all.filter { it.id in activeEditorSection.fieldIds }.forEach { field ->
                    ProjectDraftField(
                        label = field.label,
                        prompt = field.prompt,
                        required = field.requiredForStructureCheck,
                        value = values[field.id].orEmpty(),
                        onValueChange = { next ->
                            if (next.length <= 8_000) {
                                val nextValues = values + (field.id to next)
                                values = nextValues
                                reportDirty(nextValues = nextValues)
                            }
                        },
                    )
                    }
            }
            if (activeEditorSection.kind == StudentProjectEditorSectionKind.PROJECT_BASICS) {
                Text(
                    draft.templateSnapshot?.title ?: "Blank project",
                    style = MaterialTheme.typography.labelLarge,
                    color = EvidriloColors.Slate,
                )
                EvidriloExplanation("About this project", when {
                    manual -> "Nothing is prefilled. Add your own task, sources, and notes. Progress tracks structure, not quality or truth. This project stays on this device."
                    draft.templateSnapshot?.publication == ProjectTemplatePublication.BUILT_IN_STARTER -> "This starter adds editable sections, not research content or a reviewed method. Confirm method and ethics requirements with your instructor. Structure progress is not a grade."
                    else -> "Required-field progress tracks presence, not research quality. Your draft stays on this device."
                })
                draft.templateSnapshot?.let { ProjectStarterMethodNotes(it) }
            }

            if (activeEditorSection.kind == StudentProjectEditorSectionKind.PROJECT_BASICS) {
                draft.templateSnapshot
                    ?.takeIf { it.publication == ProjectTemplatePublication.PUBLISHED }
                    ?.let { template ->
                EvidriloProjectAiScaffoldPanel(
                    template = template,
                    existingProjectId = draft.id,
                    currentFieldValues = values,
                    existingProjectRevision = draft.revision,
                    projectTitle = title,
                    projectAiAccountKey = projectAiAccountKey,
                    aiCreditBalance = aiCreditBalance,
                    state = projectAiState,
                    consentState = projectAiConsentState,
                    allowRequest = !isDirty,
                    allowApply = !isDirty,
                    onRefreshAiCreditBalance = onRefreshAiCreditBalance,
                    onRefreshConsent = onRefreshProjectAiConsent,
                    onGrantConsent = onGrantProjectAiConsent,
                    onRevokeConsent = onRevokeProjectAiConsent,
                    onRequest = onRequestProjectAi,
                    onCreateProject = { _, _, _, _, _, _ -> Unit },
                    onApplyToProject = { proposal, selected, edited, replaced, requestId, creditCost ->
                        onApplyProjectAi(proposal, selected, edited, replaced, requestId, creditCost)?.also { updated ->
                            title = updated.title
                            savedTitle = updated.title
                            values = updated.fieldValues.toMap()
                            savedValues = updated.fieldValues.toMap()
                            onDirtyChanged(false)
                        }
                    },
                    onDiscardPreview = onDiscardProjectAiPreview,
                    onRetrySettlement = onRetryProjectAiSettlement,
                )
                }
            }

            if (activeEditorSection.kind == StudentProjectEditorSectionKind.PROJECT_BASICS && projectAiAccountKey != null) {
                EvidriloProjectAiActivityHistory(
                    state = projectAiActivityHistoryState,
                    accountId = projectAiAccountKey,
                    projectId = draft.id,
                    onRefresh = { onRefreshProjectAiActivity(draft.id) },
                    onLoadMore = { cursor -> onLoadMoreProjectAiActivity(draft.id, cursor) },
                )
            }

            if (activeEditorSection.kind == StudentProjectEditorSectionKind.SOURCES_AND_FILES) {
                Text(
                    "Enter source details yourself. Evidrilo does not search the web, verify DOI/URLs, or decide whether a source is credible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                EvidriloPrimaryButton(
                    label = "Add a source",
                    onClick = {
                        val id = "source-${draft.id.take(64)}-${sourceSequence++}"
                        val nextSources = sources + StudentProjectSourceRecord(id = id)
                        sources = nextSources
                        reportDirty(nextSources = nextSources)
                    },
                )
                sources.forEach { source ->
                    key(source.id) {
                        ProjectSourceEditor(
                            source = source,
                            removalImpact = projectSourceRemovalImpact(
                                source = source,
                                draft = manualDraft,
                            ),
                            onChange = { updated ->
                                val nextSources = sources.map { if (it.id == source.id) updated else it }
                                sources = nextSources
                                reportDirty(nextSources = nextSources)
                            },
                            onAddEvidence = {
                                val id = "evidence-${draft.id.take(56)}-${evidenceSequence++}"
                                val nextEvidence = evidenceItems + StudentProjectEvidenceItem(id = id, sourceId = source.id)
                                evidenceItems = nextEvidence
                                reportDirty(nextEvidenceItems = nextEvidence)
                            },
                            onRemove = {
                                val candidate = StudentProjectDraftRules.removeSource(manualDraft, source.id)
                                val saved = saveProjectSnapshot(candidate)
                                if (saved != null) {
                                    removalReviewNotice = "Saved revision ${saved.revision}. Review the affected claims and any retained free-text analysis/output before relying on or exporting this project."
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                    }
                }
                EvidriloTargetCard {
                    Text("Project files", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Files stay private on this device. You can open a bounded local text preview for supported files, then manually record any excerpt as evidence. Preview text is not verified or sent to AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidriloColors.Slate,
                    )
                    draft.attachments.forEach { attachment ->
                        Text(
                            "${attachment.fileName} · ${formatProjectImportBytes(attachment.sizeBytes)} · ${attachment.mimeType}",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidriloColors.Slate,
                        )
                        EvidriloSecondaryButton(
                            label = "Remove from current project",
                            onClick = {
                                attachmentRemovalIssue = null
                                attachmentRemovalTarget = attachment
                            },
                            enabled = !attachmentImportInProgress &&
                                !attachmentRemovalInProgress &&
                                attachmentTextReadInProgress == null,
                        )
                        if (isStudentProjectDocumentTextPreviewSupported(attachment.fileName, attachment.mimeType)) {
                            EvidriloSecondaryButton(
                                label = if (attachmentTextReadInProgress == attachment.id) "Reading text…" else "Review text",
                                onClick = { previewAttachmentText(attachment) },
                                enabled = attachmentTextReadInProgress == null && !attachmentImportInProgress,
                            )
                        } else {
                            Text(
                                "In-app text preview is unavailable for this file type; keep it attached and record excerpts manually from a compatible viewer.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EvidriloColors.Slate,
                            )
                        }
                    }
                    when {
                        draft.attachments.size >= StudentProjectAttachmentRules.MAX_ATTACHMENTS ->
                            Text("This project reached the 50-file attachment limit.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                        isStudentProjectAttachmentPickerAvailable -> EvidriloSecondaryButton(
                            label = "Attach a file",
                            onClick = launchAttachmentPicker,
                            enabled = !attachmentImportInProgress && attachmentTextReadInProgress == null,
                        )
                        else -> Text(
                            "Selecting project files is not available in this platform build yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidriloColors.Slate,
                        )
                    }
                    attachmentNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
                }
            }
            if (activeEditorSection.kind == StudentProjectEditorSectionKind.EVIDENCE_NOTES) {
                Text(
                    "Add only excerpts or notes you recorded from a source. Each note stays linked to that source; this does not verify the source or the interpretation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                evidenceItems.forEach { evidence ->
                    val source = sources.singleOrNull { it.id == evidence.sourceId }
                    if (source != null) {
                        key(evidence.id) {
                            ProjectEvidenceItemEditor(
                                evidence = evidence,
                                sourceLabel = source.title.ifBlank { "Untitled source · ${source.id}" },
                                removalImpact = projectEvidenceRemovalImpact(evidence, manualDraft),
                                onChange = { updated ->
                                    val nextEvidence = evidenceItems.map { if (it.id == evidence.id) updated else it }
                                    evidenceItems = nextEvidence
                                    reportDirty(nextEvidenceItems = nextEvidence)
                                },
                                onRemove = {
                                    val candidate = StudentProjectDraftRules.removeEvidenceNote(manualDraft, evidence.id)
                                    val saved = saveProjectSnapshot(candidate)
                                    if (saved != null) {
                                        removalReviewNotice = "Saved revision ${saved.revision}. Review the affected claims and any retained free-text analysis/output before relying on or exporting this project."
                                        true
                                    } else {
                                        false
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (activeEditorSection.kind == StudentProjectEditorSectionKind.FINDINGS_AND_SYNTHESIS) {
                if (manual) {
                    Text("Student-written synthesis", style = MaterialTheme.typography.titleMedium)
                    ManualLiteratureSynthesisFields.all.singleOrNull { it.id == ManualLiteratureSynthesisFields.SYNTHESIS }?.let { field ->
                        ProjectDraftField(
                            label = field.label,
                            prompt = field.prompt,
                            required = field.requiredForStructureCheck,
                            value = values[field.id].orEmpty(),
                            onValueChange = { next ->
                                if (next.length <= 8_000) {
                                    val nextValues = values + (field.id to next)
                                    values = nextValues
                                    reportDirty(nextValues = nextValues)
                                }
                            },
                        )
                    }
                }
                Text(
                    "Findings are your interpretations. Connect each one to specific evidence and choose how the relationship reads to you; Evidrilo does not decide whether it is correct.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                EvidriloSecondaryButton(
                    label = "Add a finding",
                    onClick = {
                        val id = "finding-${draft.id.take(52)}-${findingSequence++}"
                        val nextFindings = findings + StudentProjectFindingRecord(id = id)
                        findings = nextFindings
                        reportDirty(nextFindings = nextFindings)
                    },
                )
                findings.forEach { finding ->
                    key(finding.id) {
                        ProjectFindingEditor(
                            finding = finding,
                            evidenceItems = evidenceItems,
                            evidenceRelations = evidenceRelations,
                            onChange = { updated ->
                                val nextFindings = findings.map { if (it.id == finding.id) updated else it }
                                findings = nextFindings
                                reportDirty(nextFindings = nextFindings)
                            },
                            onRelationsChange = { nextRelations ->
                                evidenceRelations = nextRelations
                                reportDirty(nextEvidenceRelations = nextRelations)
                            },
                            onRemove = {
                                val nextFindings = findings.filterNot { it.id == finding.id }
                                changeFindings(nextFindings)
                            },
                        )
                    }
                }
                if (themes.isNotEmpty()) {
                    Text("Earlier cross-source comparison notes", style = MaterialTheme.typography.titleMedium)
                }
                EvidriloSecondaryButton(
                    label = "Add a comparison note",
                    onClick = {
                        val id = "theme-${draft.id.take(64)}-${themeSequence++}"
                        val nextThemes = themes + StudentProjectSynthesisTheme(id = id)
                        themes = nextThemes
                        reportDirty(nextThemes = nextThemes)
                    },
                )
                themes.forEach { theme ->
                    key(theme.id) {
                        ProjectSynthesisThemeEditor(
                            theme = theme,
                            sources = sources,
                            onChange = { updated ->
                                val nextThemes = themes.map { if (it.id == theme.id) updated else it }
                                themes = nextThemes
                                reportDirty(nextThemes = nextThemes)
                            },
                            onRemove = {
                                val nextThemes = themes.filterNot { it.id == theme.id }
                                themes = nextThemes
                                reportDirty(nextThemes = nextThemes)
                            },
                        )
                    }
                }
            }
            if (!manual) {
                val template = requireNotNull(draft.templateSnapshot)
                if (activeEditorSection.kind == StudentProjectEditorSectionKind.TEMPLATE_STEP ||
                    activeEditorSection.kind == StudentProjectEditorSectionKind.TEMPLATE_ADDITIONAL_FIELDS
                ) {
                    val step = template.steps.singleOrNull { it.id == activeEditorSection.id }
                    step?.let { currentStep ->
                        template.inputFields.filter { it.id in activeEditorSection.fieldIds }.forEach { field ->
                            ProjectDraftField(
                                label = field.label,
                                prompt = field.label.takeIf { it.length <= 72 && it.isNotBlank() } ?: field.kind.editorPrompt(),
                                writingHint = field.label.takeIf { it.length > 72 } ?: field.kind.editorPrompt(),
                                placeholder = field.kind.editorHint(),
                                required = field.required,
                                value = values[field.id].orEmpty(),
                                onValueChange = { next ->
                                    if (next.length <= 8_000) {
                                        val nextValues = values + (field.id to next)
                                        values = nextValues
                                        reportDirty(nextValues = nextValues)
                                    }
                                },
                            )
                        }
                        if (currentStep.aiOperations.isNotEmpty()) {
                            val sourceById = sources.associateBy(StudentProjectSourceRecord::id)
                            val evidenceChoices = buildList {
                                sources.forEach { source ->
                                    add(
                                        ProjectAiStageAssistSelectedEvidence(
                                            id = source.id,
                                            kind = "SOURCE",
                                            label = source.title.ifBlank { "Project source" },
                                            summary = source.studentNotes.ifBlank { source.reportedFindings }.takeIf(String::isNotBlank),
                                            origin = source.doiOrUrl.ifBlank { source.citationText }.takeIf(String::isNotBlank),
                                        ),
                                    )
                                }
                                evidenceItems.forEach { item ->
                                    val source = sourceById[item.sourceId]
                                    add(
                                        ProjectAiStageAssistSelectedEvidence(
                                            id = item.id,
                                            kind = "OBSERVATION",
                                            label = source?.title?.takeIf(String::isNotBlank) ?: "Evidence note",
                                            summary = item.excerpt.takeIf(String::isNotBlank),
                                            origin = listOfNotNull(source?.doiOrUrl?.takeIf(String::isNotBlank), item.locator.takeIf(String::isNotBlank))
                                                .joinToString(" · ").takeIf(String::isNotBlank),
                                        ),
                                    )
                                }
                            }.distinctBy(ProjectAiStageAssistSelectedEvidence::id)
                            Spacer(Modifier.height(8.dp))
                            EvidriloProjectAiStageAssistPanel(
                                draft = draft,
                                accountId = projectAiAccountKey,
                                step = currentStep,
                                fields = template.inputFields,
                                values = values,
                                evidenceChoices = evidenceChoices,
                                state = projectAiStageAssistState,
                                aiCreditBalance = aiCreditBalance,
                                consentState = projectAiConsentState,
                                isDirty = isDirty,
                                accountAvailable = projectAiAccountAvailable,
                                accountMessage = projectAiAccountMessage,
                                onRefreshAiCreditBalance = onRefreshAiCreditBalance,
                                onRefreshConsent = onRefreshProjectAiConsent,
                                onGrantConsent = onGrantProjectAiConsent,
                                onRevokeConsent = onRevokeProjectAiConsent,
                                onRequest = { operationId, selectedValues, selectedEvidence ->
                                    onRequestProjectAiStageAssist(currentStep.id, operationId, selectedValues, selectedEvidence)
                                },
                                onApply = onApplyProjectAiStageAssist,
                                onDismiss = onDismissProjectAiStageAssist,
                                onRetrySettlement = onRetryProjectAiStageAssistSettlement,
                                onRetryUnknownRequest = onRetryUnknownProjectAiStageAssist,
                            )
                        }
                    }
                }
            }

            if (activeEditorSection.kind == StudentProjectEditorSectionKind.CLAIMS) {
                Text(
                    "Write a bounded claim and connect it to evidence notes you selected. These are student-recorded relationships, not an automated quality verdict.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                if (manual) {
                    ManualLiteratureSynthesisFields.byId[ManualLiteratureSynthesisFields.CLAIM_SCOPE]?.let { field ->
                        ProjectDraftField(
                            label = field.label,
                            prompt = field.prompt,
                            required = field.requiredForStructureCheck,
                            value = values[field.id].orEmpty(),
                            onValueChange = { next ->
                                if (next.length <= 8_000) {
                                    val nextValues = values + (field.id to next)
                                    values = nextValues
                                    reportDirty(nextValues = nextValues)
                                }
                            },
                        )
                    }
                }
                claims.forEach { claim ->
                    key(claim.id) {
                        ProjectClaimEditor(
                            claim = claim,
                            evidenceItems = evidenceItems,
                            evidenceRelations = evidenceRelations,
                            showScopeField = !manual,
                            onChange = { updated -> changeClaims(claims.map { if (it.id == claim.id) updated else it }) },
                            onRelationsChange = { nextRelations ->
                                evidenceRelations = nextRelations
                                reportDirty(nextEvidenceRelations = nextRelations)
                            },
                            onRemove = { changeClaims(claims.filterNot { it.id == claim.id }) },
                        )
                    }
                }
                EvidriloSecondaryButton(
                    label = "Add a claim",
                    onClick = {
                        val id = if (claims.none { it.id == dev.nextgen.mobile.domain.project.StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID }) {
                            dev.nextgen.mobile.domain.project.StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID
                        } else {
                            "claim-${draft.id.take(64)}-${claimSequence++}"
                        }
                        changeClaims(claims + StudentProjectClaimRecord(id = id))
                    },
                )
                if (manual) {
                    EvidriloTargetCard {
                        Text("Sources associated with this project", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "These project-level links do not mean a source supports a particular claim. Link evidence notes to a specific claim above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidriloColors.Slate,
                        )
                        sources.forEach { source ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = source.id in claimLinks,
                                    onCheckedChange = { checked ->
                                        val nextLinks = if (checked) claimLinks + source.id else claimLinks - source.id
                                        claimLinks = nextLinks
                                        reportDirty(nextClaimLinks = nextLinks)
                                    },
                                )
                                RawText(source.title.ifBlank { uiText("Untitled source") }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            if (activeEditorSection.kind == StudentProjectEditorSectionKind.LIMITATIONS_AND_NEXT_STEPS) {
                if (manual) {
                    ManualLiteratureSynthesisFields.all.filter {
                        it.id == ManualLiteratureSynthesisFields.LIMITATIONS || it.id == ManualLiteratureSynthesisFields.NEXT_ACTION
                    }.forEach { field ->
                        ProjectDraftField(
                            label = field.label,
                            prompt = field.prompt,
                            required = field.requiredForStructureCheck,
                            value = values[field.id].orEmpty(),
                            onValueChange = { next ->
                                if (next.length <= 8_000) {
                                    val nextValues = values + (field.id to next)
                                    values = nextValues
                                    reportDirty(nextValues = nextValues)
                                }
                            },
                        )
                    }
                }
            Text(
                "Record a boundary, why it matters, and a next action. Optional links identify which findings or claims it affects; these are your recorded relationships, not an Evidrilo verdict.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
            EvidriloSecondaryButton(
                label = "Add a limitation or next action",
                onClick = {
                    val id = "limitation-${draft.id.take(52)}-${limitationActionSequence++}"
                    val nextActions = limitationActions + StudentProjectLimitationActionRecord(id = id)
                    limitationActions = nextActions
                    reportDirty(nextLimitationActions = nextActions)
                },
                enabled = limitationActions.size < dev.nextgen.mobile.domain.project.StudentProjectDraftRules.MAX_LIMITATION_ACTIONS,
            )
            if (findings.isEmpty() && claims.isEmpty()) {
                Text(
                    "No finding or claim has been added yet. These records can remain project-wide until you choose links.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
            }
            limitationActions.forEach { action ->
                key(action.id) {
                    ProjectLimitationActionEditor(
                        action = action,
                        findings = findings,
                        claims = claims,
                        onChange = { updated ->
                            val nextActions = limitationActions.map { if (it.id == action.id) updated else it }
                            limitationActions = nextActions
                            reportDirty(nextLimitationActions = nextActions)
                        },
                        onRemove = {
                            val nextActions = limitationActions.filterNot { it.id == action.id }
                            limitationActions = nextActions
                            reportDirty(nextLimitationActions = nextActions)
                        },
                    )
                }
            }
            }

            if (activeEditorSection.kind == StudentProjectEditorSectionKind.REVIEW) {
                EvidriloProjectReview(manualDraft,isDirty,
                    onSection={sectionId -> val index=editorSections.indexOfFirst { it.navigationId==sectionId }; if(index>=0) navigateToEditorSection(index)},
                    onMap={showReviewMap=true},onExport={showExportChoices=true},
                    onAiOpinion={projectSnapshotForExport()?.let(onAskAiOpinion)})
                EvidriloProjectRecordCard("Review your material", "${manualDraft.sources.size} sources · ${manualDraft.evidenceItems.size} evidence notes · ${manualDraft.claims.size} claims") {
                StudentProjectReviewContent(
                    draft = manualDraft,
                    fields = studentProjectReviewFields(manualDraft),
                    onRestoreRevision = onRestoreRevision,
                    onRestoredRevision = { restored ->
                        title = restored.title
                        values = restored.fieldValues.toMap()
                        sources = restored.sources.toList()
                        themes = restored.themes.toList()
                        claimLinks = restored.claimEvidenceSourceIds.toSet()
                        evidenceItems = restored.evidenceItems.toList()
                        findings = restored.findings.toList()
                        evidenceRelations = restored.evidenceRelations.toList()
                        claims = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.effectiveClaims(restored)
                        limitationActions = restored.limitationActions.toList()
                        deadlineDate = restored.deadlineDate
                        savedTitle = title
                        savedValues = values.toMap()
                        savedSources = sources.toList()
                        savedThemes = themes.toList()
                        savedClaimLinks = claimLinks.toSet()
                        savedEvidenceItems = evidenceItems.toList()
                        savedFindings = findings.toList()
                        savedEvidenceRelations = evidenceRelations.toList()
                        savedClaims = claims.toList()
                        savedLimitationActions = limitationActions.toList()
                        savedDeadlineDate = deadlineDate
                        autosaveStatus = "Revision ${restored.revision} restored as a new revision."
                        localError = null
                        onDirtyChanged(false)
                    },
                    onRestoreFailed = { revision ->
                        localError = "Revision $revision could not be restored. Your current project was left unchanged."
                    },
                    onCreateCheckpoint = { saveCurrent() },
                    checkpointEnabled = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.needsRevisionCheckpoint(manualDraft),
                    checkpointLabel = if (isDirty || localError != null) "Retry save and create checkpoint" else "Create revision checkpoint",
                    isDirty = isDirty,
                    structure = structure,
                )
                }
                exportNotice?.let { message ->
                    EvidriloTargetCard {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    }
                }
            }

            val visibleError = localError ?: saveError
            visibleError?.let {
                EvidriloTargetCard {
                    Text("Draft was not saved", style = MaterialTheme.typography.titleMedium)
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                }
            }
        }
    }

    if(showReviewMap) EvidriloProjectMapDialog(manualDraft) {showReviewMap=false}
    if(showExportChoices) AlertDialog(onDismissRequest={showExportChoices=false},containerColor=EvidriloColors.Card,
        title={Text(uiText("Export project","Ekspor proyek"))},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(uiText("Choose a format. Review the file before saving or sharing.","Pilih format. Tinjau berkas sebelum menyimpan atau membagikan."))
            if(isStudentProjectFileExportAvailable) {
                EvidriloExportChooser(listOf(
                    "Markdown report" to { exportSnapshot(StudentProjectExportFormatter::markdown) },
                    "PDF report" to { exportSnapshot { project -> StudentProjectExportFormatter.markdown(project).toPdfReportInput() } },
                    "Word document" to { exportSnapshot { project -> StudentProjectExportFormatter.markdown(project).toDocxReportInput() } },
                    "Project backup (.evproj)" to { exportArchiveSnapshot() },
                    "Source matrix (CSV)" to { exportSnapshot(StudentProjectExportFormatter::sourceMatrixCsv) },
                    "Synthesis themes (CSV)" to { exportSnapshot(StudentProjectExportFormatter::synthesisThemesCsv) },
                    "Evidence notes (CSV)" to { exportSnapshot(StudentProjectExportFormatter::evidenceItemsCsv) },
                    "Findings (CSV)" to { exportSnapshot(StudentProjectExportFormatter::findingsCsv) },
                    "Evidence relationships (CSV)" to { exportSnapshot(StudentProjectExportFormatter::evidenceRelationsCsv) },
                ))
            } else Text(uiText("File export is not available in this build.","Ekspor berkas tidak tersedia pada build ini."))
        }},confirmButton={TextButton({showExportChoices=false}) {Text(uiText("Close","Tutup"))}})

    if (attachmentTextReadInProgress != null) {
        AlertDialog(
            onDismissRequest = { attachmentTextReadJob?.cancel() },
            title = { Text("Reading source text") },
            text = { Text("Checking the saved file size and checksum, then preparing a local preview. Nothing is sent online.") },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { attachmentTextReadJob?.cancel() }) { Text(uiText("Cancel")) }
            },
        )
    }

    attachmentTextPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { attachmentTextPreview = null },
            title = { RawText(preview.fileName) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Local text preview only. Review the source yourself; Evidrilo has not verified or added this text as evidence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidriloColors.Slate,
                    )
                    SelectionContainer {
                        RawText(preview.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (preview.isTruncated) {
                        Text(
                            "Preview shortened at the local safety limit. Use the original document to check the full passage and its location.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidriloColors.Slate,
                        )
                    }
                    Text(
                        "Select and copy only a passage you have checked, then record it in the project’s source and evidence fields.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidriloColors.Slate,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { attachmentTextPreview = null }) { Text("Done") }
            },
        )
    }

    (exportReviewState as? StudentProjectExportReviewState.Reviewing)?.let { current ->
        StudentProjectExportReviewDialog(
            review = current.review,
            onConfirm = {
                val transition = reduceStudentProjectExportReview(
                    exportReviewState,
                    StudentProjectExportReviewEvent.Confirm,
                )
                exportReviewState = transition.state
                transition.confirmedArtifact?.let(exportProjectFile)
            },
            onCancel = {
                exportReviewState = reduceStudentProjectExportReview(
                    exportReviewState,
                    StudentProjectExportReviewEvent.Cancel,
                ).state
            },
        )
    }

    attachmentRemovalTarget?.let { attachment ->
        AlertDialog(
            onDismissRequest = {
                if (!attachmentRemovalInProgress) {
                    attachmentRemovalTarget = null
                    attachmentRemovalIssue = null
                }
            },
            title = { Text("Remove file from this project?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${attachment.fileName} will be removed from the current project revision. Earlier saved revisions keep their copy and can still access it. This does not erase the file from those revisions.",
                    )
                    attachmentRemovalIssue?.let { issue ->
                        Text(issue, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { removeAttachmentFromCurrentProject(attachment) },
                    enabled = !attachmentRemovalInProgress,
                ) {
                    Text(if (attachmentRemovalInProgress) "Removing…" else "Remove from project")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        attachmentRemovalTarget = null
                        attachmentRemovalIssue = null
                    },
                    enabled = !attachmentRemovalInProgress,
                ) {
                    Text("Keep file")
                }
            },
        )
    }

    if (attachmentImportInProgress) {
        AlertDialog(
            onDismissRequest = {
                attachmentImportJob?.cancel(CancellationException("Student canceled file addition"))
            },
            title = { Text("Adding project file") },
            text = { Text("Saving this file privately. Canceling before the local revision is committed leaves the saved project unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        attachmentImportJob?.cancel(CancellationException("Student canceled file addition"))
                    },
                ) { Text("Cancel file addition") }
            },
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelExit,
            title = { Text("Unsaved project changes") },
            text = { Text("Save your changes before leaving, or discard them? The last saved local draft will remain available.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveCurrent() != null) {
                            onSaveAndLeave()
                        }
                    },
                    enabled = title.isNotBlank() && title.length <= 160,
                ) {
                    Text("Save and leave")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDiscardAndLeave) { Text("Discard") }
                    TextButton(onClick = onCancelExit) { Text("Stay") }
                }
            },
        )
    }
}

internal fun studentProjectAttachmentFailureMessage(code: String): String = when (code) {
    "PROJECT_ATTACHMENT_SIZE_INVALID" -> "Choose a non-empty supported file no larger than 20 MB. No file was attached."
    "PROJECT_ATTACHMENT_TYPE_UNSUPPORTED" -> "Choose a PDF, DOCX, CSV, TXT, Markdown, PNG, or JPEG file. No file was attached."
    "PROJECT_ATTACHMENT_CONTENT_INVALID" -> "The file content did not match its supported file type. No file was attached."
    "PROJECT_ATTACHMENT_REFERENCE_INVALID" -> "The file name or metadata is not safe to store. Rename it and try again."
    "PROJECT_ATTACHMENT_LIMIT_REACHED" -> "This project already has 50 attachments. No file was attached."
    "PROJECT_ATTACHMENT_ROLLBACK_FAILED" -> "Project metadata was not updated, but file cleanup could not be confirmed. Check this device's storage before retrying."
    "PROJECT_ATTACHMENT_NOT_ATTACHED" -> "This file is no longer attached to the current project. Earlier revisions, if any, were not changed."
    "PROJECT_ATTACHMENT_REMOVE_RECOVERY_FAILED" -> "The file could not be safely removed. It remains protected while storage recovery is retried."
    "PROJECT_DRAFT_STORAGE_LIMIT_REACHED" -> "Project storage is full. The file is still attached because its removal was not saved."
    else -> "The selected file could not be added. Existing project data was left unchanged."
}

internal fun studentProjectAttachmentRemovalFailureMessage(code: String): String = when (code) {
    "PROJECT_ATTACHMENT_NOT_ATTACHED",
    "PROJECT_ATTACHMENT_REMOVE_RECOVERY_FAILED",
    "PROJECT_DRAFT_STORAGE_LIMIT_REACHED",
    "PROJECT_ATTACHMENT_ROLLBACK_FAILED",
    -> studentProjectAttachmentFailureMessage(code)
    else -> "The file could not be safely removed. The current project and saved revisions were left unchanged."
}

private const val PROJECT_AUTOSAVE_DEBOUNCE_MILLIS = 900L

@Composable
private fun ProjectDraftField(
    label: String,
    prompt: String,
    placeholder: String = "Add your own notes.",
    writingHint: String? = null,
    required: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = if (label == prompt) prompt else "$label. $prompt" },
            placeholder = { Text(placeholder) },
            supportingText = {
                Text(if (required) "Required response" else "Optional response")
            },
            minLines = 2,
            maxLines = 7,
            isError = value.length > 8_000,
        )
        writingHint?.takeIf { it != prompt }?.let { EvidriloExplanation("Writing hint", it) }
    }
}

@Composable
private fun ProjectSourceEditor(
    source: StudentProjectSourceRecord,
    removalImpact: ProjectSourceRemovalImpact,
    onChange: (StudentProjectSourceRecord) -> Unit,
    onAddEvidence: () -> Unit,
    onRemove: () -> Boolean,
) {
    var showRemoveConfirmation by remember(source.id) { mutableStateOf(false) }
    var removalError by remember(source.id) { mutableStateOf(false) }
    EvidriloProjectRecordCard(title = source.title.ifBlank { "New source" }, detail = "Source · ${source.selectionStatus.label()}", initiallyExpanded = source.title.isBlank()) {

        SourceTextInput("Title", source.title, onValueChange = { onChange(source.copy(title = it)) })
        SourceTextInput("Author or organization", source.authors, onValueChange = { onChange(source.copy(authors = it)) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceTextInput("Year", source.year, modifier = Modifier.weight(1f), onValueChange = { onChange(source.copy(year = it)) })
            SourceTextInput("Source type", source.sourceType, modifier = Modifier.weight(1f), onValueChange = { onChange(source.copy(sourceType = it)) })
        }
        SourceTextInput("DOI or URL (entered by you)", source.doiOrUrl, onValueChange = { onChange(source.copy(doiOrUrl = it)) })
        SourceTextInput("Access date", source.accessedOn, onValueChange = { onChange(source.copy(accessedOn = it)) })
        SourceTextInput("Citation text", source.citationText, onValueChange = { onChange(source.copy(citationText = it)) }, multiline = true)
        Text("Selection decision", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceSelectionStatus.entries.forEach { status ->
                FilterChip(
                    selected = source.selectionStatus == status,
                    onClick = { onChange(source.copy(selectionStatus = status)) },
                    label = { Text(status.label()) },
                )
            }
        }
        if (source.selectionStatus == SourceSelectionStatus.EXCLUDED) {
            SourceTextInput("Why was it excluded?", source.exclusionReason, onValueChange = { onChange(source.copy(exclusionReason = it)) }, multiline = true)
        } else {
            Text("Extraction notes · record only what the source reports", style = MaterialTheme.typography.labelLarge)
            SourceTextInput("Aim or purpose", source.reportedAim, onValueChange = { onChange(source.copy(reportedAim = it)) }, multiline = true)
            SourceTextInput("Context or population", source.reportedContext, onValueChange = { onChange(source.copy(reportedContext = it)) }, multiline = true)
            SourceTextInput("Method", source.reportedMethod, onValueChange = { onChange(source.copy(reportedMethod = it)) }, multiline = true)
            SourceTextInput("Reported findings", source.reportedFindings, onValueChange = { onChange(source.copy(reportedFindings = it)) }, multiline = true)
            SourceTextInput("Reported limitations", source.reportedLimitations, onValueChange = { onChange(source.copy(reportedLimitations = it)) }, multiline = true)
            SourceTextInput("Your notes", source.studentNotes, onValueChange = { onChange(source.copy(studentNotes = it)) }, multiline = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = source.studentChecked, onCheckedChange = { onChange(source.copy(studentChecked = it)) })
            Text("I checked this source myself; Evidrilo has not verified it.", style = MaterialTheme.typography.bodySmall)
        }
        EvidriloSecondaryButton(label = "Add evidence note from this source", onClick = onAddEvidence)
        EvidriloSecondaryButton(label = "Remove source", onClick = { showRemoveConfirmation = true })
    }
    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false; removalError = false },
            title = { Text("Remove this source?") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(projectSourceRemovalWarning(source, removalImpact))
                    if (removalError) {
                        Text(
                            "Could not save this removal. The source and its links are unchanged; retry or keep it.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onRemove()) {
                        showRemoveConfirmation = false
                        removalError = false
                    } else {
                        removalError = true
                    }
                }) { Text("Remove source", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false; removalError = false }) { Text("Keep source") }
            },
        )
    }
}

@Composable
private fun ProjectEvidenceItemEditor(
    evidence: StudentProjectEvidenceItem,
    sourceLabel: String,
    removalImpact: ProjectEvidenceRemovalImpact,
    onChange: (StudentProjectEvidenceItem) -> Unit,
    onRemove: () -> Boolean,
) {
    var showRemoveConfirmation by remember(evidence.id) { mutableStateOf(false) }
    var removalError by remember(evidence.id) { mutableStateOf(false) }
    EvidriloProjectRecordCard(title = evidence.excerpt.ifBlank { "New evidence note" }.take(100), detail = sourceLabel, initiallyExpanded = evidence.excerpt.isBlank()) {
        RawText(uiText("Evidence from: ","Bukti dari: ")+sourceLabel, style = MaterialTheme.typography.titleSmall)
        SourceTextInput("Your excerpt or paraphrase", evidence.excerpt, multiline = true) {
            onChange(evidence.copy(excerpt = it))
        }
        SourceTextInput("Page, section, table, or location (optional)", evidence.locator) {
            onChange(evidence.copy(locator = it))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = evidence.studentChecked,
                onCheckedChange = { onChange(evidence.copy(studentChecked = it)) },
            )
            Text("I checked this note against the source.", style = MaterialTheme.typography.bodySmall)
        }
        EvidriloSecondaryButton(label = "Remove evidence note", onClick = { showRemoveConfirmation = true })
    }
    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false; removalError = false },
            title = { Text("Remove this evidence note?") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(projectEvidenceRemovalWarning(removalImpact))
                    if (removalError) {
                        Text(
                            "Could not save this removal. The evidence note and its links are unchanged; retry or keep it.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (onRemove()) {
                        showRemoveConfirmation = false
                        removalError = false
                    } else {
                        removalError = true
                    }
                }) { Text("Remove note", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false; removalError = false }) { Text("Keep note") }
            },
        )
    }
}

@Composable
private fun ProjectFindingEditor(
    finding: StudentProjectFindingRecord,
    evidenceItems: List<StudentProjectEvidenceItem>,
    evidenceRelations: List<StudentProjectEvidenceRelation>,
    onChange: (StudentProjectFindingRecord) -> Unit,
    onRelationsChange: (List<StudentProjectEvidenceRelation>) -> Unit,
    onRemove: () -> Unit,
) {
    EvidriloProjectRecordCard(title = finding.statement.ifBlank { "New finding" }.take(100), detail = "Finding · evidence links inside", initiallyExpanded = finding.statement.isBlank()) {

        SourceTextInput("Your finding or synthesis statement", finding.statement, multiline = true) {
            onChange(finding.copy(statement = it))
        }
        SourceTextInput("Scope or context", finding.scopeNote, multiline = true) {
            onChange(finding.copy(scopeNote = it))
        }
        SourceTextInput("Uncertainty or variation", finding.uncertaintyNote, multiline = true) {
            onChange(finding.copy(uncertaintyNote = it))
        }
        ProjectEvidenceRelationsEditor(
            label = "Evidence relationship to this finding",
            targetType = StudentProjectEvidenceTargetType.FINDING,
            targetId = finding.id,
            evidenceItems = evidenceItems,
            evidenceRelations = evidenceRelations,
            onChange = onRelationsChange,
        )
        EvidriloSecondaryButton(label = "Remove finding", onClick = onRemove)
    }
}

@Composable
private fun ProjectClaimEditor(
    claim: StudentProjectClaimRecord,
    evidenceItems: List<StudentProjectEvidenceItem>,
    evidenceRelations: List<StudentProjectEvidenceRelation>,
    showScopeField: Boolean = true,
    onChange: (StudentProjectClaimRecord) -> Unit,
    onRelationsChange: (List<StudentProjectEvidenceRelation>) -> Unit,
    onRemove: () -> Unit,
) {
    EvidriloProjectRecordCard(title = claim.statement.ifBlank { "New claim" }.take(100), detail = "Claim · ${claim.reviewStatus.claimReviewLabel()}", initiallyExpanded = claim.statement.isBlank()) {

        ProjectDraftField(
            label = "Claim statement",
            prompt = "What bounded statement are you making?",
            required = true,
            value = claim.statement,
            onValueChange = { onChange(claim.copy(statement = it)) },
        )
        if (showScopeField) {
            ProjectDraftField(
                label = "Scope",
                prompt = "Where, for whom, or under what conditions does it apply?",
                required = true,
                value = claim.scopeNote,
                onValueChange = { onChange(claim.copy(scopeNote = it)) },
            )
        }
        ProjectDraftField(
            label = "Limitations for this claim",
            prompt = "What should a reader not infer from it?",
            required = false,
            value = claim.limitationsNote,
            onValueChange = { onChange(claim.copy(limitationsNote = it)) },
        )
        Text("Student review state", style = MaterialTheme.typography.labelLarge)
        Text("This is your workflow marker, not a pass/fail or academic assessment.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudentProjectClaimReviewStatus.entries.forEach { status ->
                FilterChip(
                    selected = claim.reviewStatus == status,
                    onClick = { onChange(claim.copy(reviewStatus = status)) },
                    label = { Text(status.claimReviewLabel()) },
                )
            }
        }
        ProjectEvidenceRelationsEditor(
            label = "Evidence relationship to this claim",
            targetType = StudentProjectEvidenceTargetType.CLAIM,
            targetId = claim.id,
            evidenceItems = evidenceItems,
            evidenceRelations = evidenceRelations,
            onChange = onRelationsChange,
        )
        EvidriloSecondaryButton(label = "Remove claim", onClick = onRemove)
    }
}

@Composable
private fun ProjectLimitationActionEditor(
    action: StudentProjectLimitationActionRecord,
    findings: List<StudentProjectFindingRecord>,
    claims: List<StudentProjectClaimRecord>,
    onChange: (StudentProjectLimitationActionRecord) -> Unit,
    onRemove: () -> Unit,
) {
    EvidriloProjectRecordCard(title = action.boundary.ifBlank { "New limitation or next action" }.take(100), detail = "Boundary and next action", initiallyExpanded = action.boundary.isBlank()) {
        ProjectDraftField(
            label = "Boundary or limitation",
            prompt = "What does this project not establish?",
            required = false,
            value = action.boundary,
            onValueChange = { onChange(action.copy(boundary = it.take(8_000))) },
        )
        ProjectDraftField(
            label = "Why it matters",
            prompt = "What is the reason for this boundary?",
            required = false,
            value = action.reason,
            onValueChange = { onChange(action.copy(reason = it.take(8_000))) },
        )
        ProjectDraftField(
            label = "Next action",
            prompt = "What could you check or do next?",
            required = false,
            value = action.nextAction,
            onValueChange = { onChange(action.copy(nextAction = it.take(8_000))) },
        )
        Text("Affected findings · optional links", style = MaterialTheme.typography.labelLarge)
        if (findings.isEmpty()) {
            Text("No findings are available to link yet.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        } else {
            findings.forEach { finding ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = finding.id in action.affectedFindingIds,
                        onCheckedChange = { checked ->
                            val next = if (checked) action.affectedFindingIds + finding.id else action.affectedFindingIds - finding.id
                            onChange(action.copy(affectedFindingIds = next))
                        },
                    )
                    Column {
                        RawText(finding.statement.ifBlank { uiText("Untitled finding") }, style = MaterialTheme.typography.bodyMedium)
                        Text("ID: ${finding.id}", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    }
                }
            }
        }
        Text("Affected claims · optional links", style = MaterialTheme.typography.labelLarge)
        if (claims.isEmpty()) {
            Text("No claims are available to link yet.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        } else {
            claims.forEach { claim ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = claim.id in action.affectedClaimIds,
                        onCheckedChange = { checked ->
                            val next = if (checked) action.affectedClaimIds + claim.id else action.affectedClaimIds - claim.id
                            onChange(action.copy(affectedClaimIds = next))
                        },
                    )
                    Column {
                        RawText(claim.statement.ifBlank { uiText("Untitled claim") }, style = MaterialTheme.typography.bodyMedium)
                        Text("ID: ${claim.id}", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    }
                }
            }
        }
        EvidriloSecondaryButton(label = "Remove limitation/action", onClick = onRemove)
    }
}

@Composable
private fun ProjectEvidenceRelationsEditor(
    label: String,
    targetType: StudentProjectEvidenceTargetType,
    targetId: String,
    evidenceItems: List<StudentProjectEvidenceItem>,
    evidenceRelations: List<StudentProjectEvidenceRelation>,
    onChange: (List<StudentProjectEvidenceRelation>) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(
        "Choose a relationship for each note. These are your working links, not an automatic verification.",
        style = MaterialTheme.typography.bodySmall,
        color = EvidriloColors.Slate,
    )
    if (evidenceItems.isEmpty()) {
        Text("Add a source and an evidence note first.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
    evidenceItems.forEach { evidence ->
        val current = evidenceRelations.singleOrNull {
            it.targetType == targetType && it.targetId == targetId && it.evidenceId == evidence.id
        }
        Column {
            RawText(evidence.excerpt.ifBlank { "Evidence note · ${evidence.id}" }, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudentProjectEvidenceRelationType.entries.forEach { relation ->
                    FilterChip(
                        selected = current?.relation == relation,
                        onClick = {
                            val retained = evidenceRelations.filterNot {
                                it.targetType == targetType && it.targetId == targetId && it.evidenceId == evidence.id
                            }
                            onChange(
                                if (current?.relation == relation) retained
                                else retained + StudentProjectEvidenceRelation(targetType, targetId, evidence.id, relation),
                            )
                        },
                        label = { Text(relation.projectLabel()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectRevisionSnapshotCard(
    snapshot: StudentProjectRevisionSnapshot,
    enabled: Boolean,
    onRestore: () -> Unit,
) {
    EvidriloTargetCard {
        Text("Revision ${snapshot.revision}", style = MaterialTheme.typography.titleMedium)
        RawText(snapshot.changeSummary, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Saved ${snapshot.savedAtEpochMillis} · ${snapshot.actor.name.lowercase().replace('_', ' ')}",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        EvidriloSecondaryButton(
            label = "Restore as a new revision",
            enabled = enabled,
            onClick = onRestore,
        )
    }
}

private fun StudentProjectEvidenceRelationType.projectLabel(): String = when (this) {
    StudentProjectEvidenceRelationType.SUPPORTS -> "Supports"
    StudentProjectEvidenceRelationType.CONTRADICTS -> "Contradicts"
    StudentProjectEvidenceRelationType.PROVIDES_CONTEXT -> "Context"
}

private fun StudentProjectClaimReviewStatus.claimReviewLabel(): String = when (this) {
    StudentProjectClaimReviewStatus.DRAFT -> "Draft"
    StudentProjectClaimReviewStatus.READY_FOR_REVIEW -> "Ready to review"
    StudentProjectClaimReviewStatus.NEEDS_REVISION -> "Revise"
}

@Composable
private fun ProjectSynthesisThemeEditor(
    theme: StudentProjectSynthesisTheme,
    sources: List<StudentProjectSourceRecord>,
    onChange: (StudentProjectSynthesisTheme) -> Unit,
    onRemove: () -> Unit,
) {
    EvidriloProjectRecordCard(title = theme.title.ifBlank { "New synthesis theme" }, detail = "Synthesis · ${theme.sourceIds.size} sources", initiallyExpanded = theme.title.isBlank()) {

        SourceTextInput("Theme label", theme.title, onValueChange = { onChange(theme.copy(title = it)) })
        SourceTextInput("Your synthesis", theme.synthesis, onValueChange = { onChange(theme.copy(synthesis = it)) }, multiline = true)
        Text("Sources you used for this theme", style = MaterialTheme.typography.labelLarge)
        sources.forEach { source ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = source.id in theme.sourceIds,
                    onCheckedChange = { checked ->
                        val next = if (checked) theme.sourceIds + source.id else theme.sourceIds - source.id
                        onChange(theme.copy(sourceIds = next))
                    },
                )
                RawText(source.title.ifBlank { "Untitled source · ${source.id}" }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        SourceTextInput(
            "Differences, conflicts, or variation",
            theme.conflictOrVariation,
            onValueChange = { onChange(theme.copy(conflictOrVariation = it)) },
            multiline = true,
        )
        EvidriloSecondaryButton(label = "Remove theme", onClick = onRemove)
    }
}

@Composable
private fun SourceTextInput(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.length <= 8_000) onValueChange(next) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(uiText(label)) },
        singleLine = !multiline,
        minLines = if (multiline) 2 else 1,
        maxLines = if (multiline) 6 else 1,
    )
}

@Composable
private fun ProjectReviewValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(uiText(label), style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
        RawText(value.ifBlank { uiText("Not added yet.") }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StudentProjectReviewContent(
    draft: StudentProjectDraft,
    fields: List<StudentProjectReviewField>,
    onRestoreRevision: (Int) -> StudentProjectDraft?,
    onRestoredRevision: (StudentProjectDraft) -> Unit,
    onRestoreFailed: (Int) -> Unit,
    onCreateCheckpoint: () -> Unit,
    checkpointEnabled: Boolean,
    checkpointLabel: String,
    isDirty: Boolean,
    structure: StudentProjectStructureReport,
) {
    val sourcesById = draft.sources.associateBy(StudentProjectSourceRecord::id)
    val evidenceById = draft.evidenceItems.associateBy(StudentProjectEvidenceItem::id)
    val findingsById = draft.findings.associateBy(StudentProjectFindingRecord::id)
    val claims = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.effectiveClaims(draft)
    val claimsById = claims.associateBy(StudentProjectClaimRecord::id)
    val templateSnapshot = draft.templateSnapshot

    EvidriloTargetCard {
        Text("Project overview", style = MaterialTheme.typography.titleLarge)
        ProjectReviewValue("Project name", draft.title)
        ProjectReviewValue(
            "Starting point",
            when (templateSnapshot?.publication) {
                ProjectTemplatePublication.BUILT_IN_STARTER -> "${templateSnapshot.title} · local structure-only guide"
                else -> templateSnapshot?.title ?: "Your own task"
            },
        )
        ProjectReviewValue("Project status", uiText(draft.status.name.lowercase().replace('_', ' ').replaceFirstChar {it.uppercase()}))
        ProjectReviewValue("Optional deadline", draft.deadlineDate ?: uiText("Not set"))
        ProjectReviewValue("Saved revision", draft.revision.toString())
        Text(
            "This review records what is in the local draft. It is not an academic grade or a verification of research quality.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
    }

    EvidriloTargetCard {
        Text("Project details", style = MaterialTheme.typography.titleLarge)
        fields.forEach { field -> ProjectReviewValue(field.label, field.value) }
    }

    EvidriloTargetCard {
        Text(uiText("Sources"), style = MaterialTheme.typography.titleLarge)
        if (draft.sources.isEmpty()) {
            Text("No sources have been recorded.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
    }
    draft.sources.forEach { source ->
        EvidriloTargetCard {
            RawText(source.title.ifBlank { uiText("Untitled source") }, style = MaterialTheme.typography.titleMedium)
            ProjectReviewValue("Author or organization", source.authors)
            ProjectReviewValue("Year", source.year)
            ProjectReviewValue("Source type", source.sourceType)
            ProjectReviewValue("DOI or URL", source.doiOrUrl)
            ProjectReviewValue("Access date", source.accessedOn)
            ProjectReviewValue("Citation text", source.citationText)
            ProjectReviewValue("Your selection", uiText(source.selectionStatus.label()))
            if (source.selectionStatus == SourceSelectionStatus.EXCLUDED) {
                ProjectReviewValue("Exclusion reason", source.exclusionReason)
            } else {
                ProjectReviewValue("Reported aim or purpose", source.reportedAim)
                ProjectReviewValue("Reported context or population", source.reportedContext)
                ProjectReviewValue("Reported method", source.reportedMethod)
                ProjectReviewValue("Reported findings", source.reportedFindings)
                ProjectReviewValue("Reported limitations", source.reportedLimitations)
                ProjectReviewValue("Your notes", source.studentNotes)
            }
            ProjectReviewValue("Checked by you", uiText(if (source.studentChecked) "Yes" else "No"))
        }
    }

    EvidriloTargetCard {
        Text("Project files", style = MaterialTheme.typography.titleLarge)
        if (draft.attachments.isEmpty()) {
            Text("No files are attached.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        draft.attachments.forEach { attachment ->
            ProjectReviewValue("File", attachment.fileName)
            ProjectReviewValue("Size and type", "${formatProjectImportBytes(attachment.sizeBytes)} · ${attachment.mimeType}")
        }
    }

    EvidriloTargetCard {
        Text(uiText("Evidence notes"), style = MaterialTheme.typography.titleLarge)
        Text(
            "These excerpts or notes were entered by you. Their presence does not mean Evidrilo has verified the source or interpretation.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        if (draft.evidenceItems.isEmpty()) {
            Text("No evidence notes have been recorded.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        draft.evidenceItems.forEach { evidence ->
            ProjectReviewValue("Source", sourcesById[evidence.sourceId]?.title ?: "Source no longer in this project")
            ProjectReviewValue("Excerpt or paraphrase", evidence.excerpt)
            ProjectReviewValue("Location", evidence.locator)
            ProjectReviewValue("Checked by you", uiText(if (evidence.studentChecked) "Yes" else "No"))
        }
    }

    EvidriloTargetCard {
        Text("Findings and synthesis", style = MaterialTheme.typography.titleLarge)
        if (draft.findings.isEmpty()) {
            Text("No findings have been recorded.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        draft.findings.forEach { finding ->
            ProjectReviewValue("Finding", finding.statement)
            ProjectReviewValue("Scope or context", finding.scopeNote)
            ProjectReviewValue("Uncertainty or variation", finding.uncertaintyNote)
            val relations = draft.evidenceRelations.filter {
                it.targetType == StudentProjectEvidenceTargetType.FINDING && it.targetId == finding.id
            }
            if (relations.isEmpty()) {
                ProjectReviewValue("Evidence relationships recorded by you", "No evidence link recorded")
            }
            relations.forEach { relation ->
                ProjectReviewValue(
                    "Your relationship to ${evidenceById[relation.evidenceId]?.excerpt?.removalPreview() ?: "an unavailable note"}",
                    relation.relation.name.lowercase().replace('_', ' '),
                )
                ProjectReviewValue("Your reason for this relationship", relation.rationale)
            }
        }
        if (draft.themes.isNotEmpty()) {
            Text("Cross-source comparison notes", style = MaterialTheme.typography.titleMedium)
            draft.themes.forEach { theme ->
                ProjectReviewValue("Note", theme.title)
                ProjectReviewValue("Comparison", theme.synthesis)
                ProjectReviewValue("Variation or disagreement you recorded", theme.conflictOrVariation)
                ProjectReviewValue(
                    "Sources linked by you",
                    theme.sourceIds.mapNotNull { sourcesById[it]?.title }.joinToString().ifBlank { "None" },
                )
            }
        }
    }

    EvidriloTargetCard {
        Text(uiText("Claims and evidence links"), style = MaterialTheme.typography.titleLarge)
        Text(
            "The relationships below are choices recorded by you. Evidrilo has not determined whether a claim is true or supported.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        ProjectReviewValue(
            "Sources associated with the project",
            draft.claimEvidenceSourceIds.mapNotNull { sourcesById[it]?.title }.joinToString().ifBlank { "None" },
        )
        if (claims.isEmpty()) {
            Text("No claims have been recorded.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        claims.forEach { claim ->
            ProjectReviewValue("Claim", claim.statement)
            ProjectReviewValue("Scope", claim.scopeNote)
            ProjectReviewValue("Limitations noted for this claim", claim.limitationsNote)
            ProjectReviewValue("Your workflow marker", claim.reviewStatus.claimReviewLabel())
            val relations = draft.evidenceRelations.filter {
                it.targetType == StudentProjectEvidenceTargetType.CLAIM && it.targetId == claim.id
            }
            if (relations.isEmpty()) {
                ProjectReviewValue("Evidence relationships recorded by you", "No evidence link recorded")
            }
            relations.forEach { relation ->
                ProjectReviewValue(
                    "Your relationship to ${evidenceById[relation.evidenceId]?.excerpt?.removalPreview() ?: "an unavailable note"}",
                    relation.relation.name.lowercase().replace('_', ' '),
                )
                ProjectReviewValue("Your reason for this relationship", relation.rationale)
            }
        }
    }

    EvidriloTargetCard {
        Text(uiText("Limitations and next steps"), style = MaterialTheme.typography.titleLarge)
        if (draft.limitationActions.isEmpty()) {
            Text("No structured limitation or next action has been recorded.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        draft.limitationActions.forEach { action ->
            ProjectReviewValue("Boundary or limitation", action.boundary)
            ProjectReviewValue("Why it matters", action.reason)
            ProjectReviewValue("Next action", action.nextAction)
            ProjectReviewValue(
                "Findings linked by you",
                action.affectedFindingIds.mapNotNull { findingsById[it]?.statement }.joinToString().ifBlank { "None" },
            )
            ProjectReviewValue(
                "Claims linked by you",
                action.affectedClaimIds.mapNotNull { claimsById[it]?.statement }.joinToString().ifBlank { "None" },
            )
        }
    }

    ProjectStructureCheckCard(draft, structure)

    EvidriloTargetCard {
        Text("Revision history", style = MaterialTheme.typography.titleLarge)
        Text(
            "Restoring a checkpoint creates a new revision and keeps later history. Checkpoints do not grade research quality.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        if (draft.revisionSnapshots.size >= dev.nextgen.mobile.domain.project.StudentProjectDraftRules.MAX_REVISION_SNAPSHOTS) {
            Text("Revision history is bounded; the initial and recent checkpoints are retained.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
        if (draft.revisionSnapshots.isEmpty()) {
            Text("No named checkpoints are available yet.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        draft.revisionSnapshots.asReversed().forEach { snapshot ->
            ProjectRevisionSnapshotCard(
                snapshot = snapshot,
                enabled = !isDirty,
                onRestore = {
                    val restored = onRestoreRevision(snapshot.revision)
                    if (restored == null) onRestoreFailed(snapshot.revision) else onRestoredRevision(restored)
                },
            )
        }
        EvidriloPrimaryButton(label = checkpointLabel, enabled = checkpointEnabled, onClick = onCreateCheckpoint)
        Text(
            "A checkpoint saves the current project state. It does not validate a claim or establish research quality.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
    }
}

@Composable
private fun ProjectStructureCheckCard(
    draft: StudentProjectDraft,
    report: StudentProjectStructureReport,
) {
    val fieldLabels = draft.templateSnapshot?.inputFields?.associate { it.id to it.label }
        ?: ManualLiteratureSynthesisFields.all.associate { it.id to it.label }
    val sourcesById = draft.sources.associateBy(StudentProjectSourceRecord::id)
    val evidenceById = draft.evidenceItems.associateBy(StudentProjectEvidenceItem::id)
    val findingsById = draft.findings.associateBy(StudentProjectFindingRecord::id)
    val claimsById = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.effectiveClaims(draft)
        .associateBy(StudentProjectClaimRecord::id)
    EvidriloTargetCard {
        Text("Structure check", style = MaterialTheme.typography.titleLarge)
        Text(
            if (report.hasOpenStructureIssues) "Some links or sections still need attention." else "The currently defined structure checks are clear.",
            style = MaterialTheme.typography.bodyMedium,
            color = EvidriloColors.Slate,
        )
        Text("This is not a grade and does not assess source quality, method quality, or whether a claim is true.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        report.missingRequiredFieldIds.forEach { id ->
            Text("• Missing section: ${fieldLabels[id] ?: id}", style = MaterialTheme.typography.bodySmall)
        }
        report.incompleteSourceIds.forEach { id ->
            val sourceName = sourcesById[id]?.title?.takeIf(String::isNotBlank) ?: "An untitled source"
            Text("• $sourceName needs a title and citation text", style = MaterialTheme.typography.bodySmall)
        }
        report.unlinkedThemeIds.forEach { id ->
            val themeName = draft.themes.singleOrNull { it.id == id }?.title?.takeIf(String::isNotBlank) ?: "A comparison note"
            Text("• $themeName is not linked to a source", style = MaterialTheme.typography.bodySmall)
        }
        if (report.invalidClaimEvidenceIds.isNotEmpty()) {
            Text("• One or more project-level source links point to sources no longer in this project", style = MaterialTheme.typography.bodySmall)
        }
        report.incompleteEvidenceIds.forEach { id ->
            val sourceName = evidenceById[id]?.sourceId?.let(sourcesById::get)?.title?.takeIf(String::isNotBlank) ?: "its recorded source"
            Text("• An evidence note from $sourceName has no excerpt or paraphrase yet", style = MaterialTheme.typography.bodySmall)
        }
        report.unlinkedEvidenceIds.forEach { id ->
            val sourceName = evidenceById[id]?.sourceId?.let(sourcesById::get)?.title?.takeIf(String::isNotBlank) ?: "its recorded source"
            Text("• An evidence note from $sourceName is not linked to a finding or claim", style = MaterialTheme.typography.bodySmall)
        }
        report.unlinkedFindingIds.forEach { id ->
            val findingName = findingsById[id]?.statement?.takeIf(String::isNotBlank) ?: "An untitled finding"
            Text("• $findingName has no linked evidence note", style = MaterialTheme.typography.bodySmall)
        }
        report.unlinkedClaimIds.forEach { id ->
            val claimName = claimsById[id]?.statement?.takeIf(String::isNotBlank) ?: "A claim"
            Text("• $claimName has no specific evidence-note relationship recorded yet", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "${draft.sources.size} sources · ${draft.evidenceItems.size} evidence notes · ${draft.findings.size} findings · ${dev.nextgen.mobile.domain.project.StudentProjectDraftRules.effectiveClaims(draft).size} claims · ${draft.evidenceRelations.size} links recorded by you",
            style = MaterialTheme.typography.labelMedium,
            color = EvidriloColors.Slate,
        )
    }
}

private fun SourceSelectionStatus.label(): String = when (this) {
    SourceSelectionStatus.TO_REVIEW -> "To review"
    SourceSelectionStatus.SELECTED -> "Selected"
    SourceSelectionStatus.EXCLUDED -> "Excluded"
}

private fun ProjectTemplateInputKind.editorPrompt(): String = when (this) {
    ProjectTemplateInputKind.ASSIGNMENT_BRIEF -> "What does the assignment ask?"
    ProjectTemplateInputKind.RESEARCH_QUESTION -> "What question are you investigating?"
    ProjectTemplateInputKind.HYPOTHESIS -> "What do you predict, and why?"
    ProjectTemplateInputKind.SOURCE -> "Which source and details matter?"
    ProjectTemplateInputKind.DATA -> "What data or observations do you have?"
    ProjectTemplateInputKind.ANALYSIS -> "How are you examining the information?"
    ProjectTemplateInputKind.CLAIM -> "What can you currently say?"
    ProjectTemplateInputKind.LIMITATION -> "What limits the interpretation?"
    ProjectTemplateInputKind.NEXT_ACTION -> "What should be checked or done next?"
}

private fun ProjectTemplateInputKind.editorHint(): String = when (this) {
    ProjectTemplateInputKind.ASSIGNMENT_BRIEF -> "Paste or summarize the relevant instructions."
    ProjectTemplateInputKind.RESEARCH_QUESTION -> "Keep the question focused and aligned with the task."
    ProjectTemplateInputKind.HYPOTHESIS -> "A hypothesis is a prediction to examine, not a guaranteed result."
    ProjectTemplateInputKind.SOURCE -> "Record provenance so you can find and assess the source again."
    ProjectTemplateInputKind.DATA -> "Record what was observed, including context and missing data."
    ProjectTemplateInputKind.ANALYSIS -> "Describe the method you actually used."
    ProjectTemplateInputKind.CLAIM -> "Keep the statement within the scope of the information available."
    ProjectTemplateInputKind.LIMITATION -> "Note uncertainty, method constraints, and alternative explanations."
    ProjectTemplateInputKind.NEXT_ACTION -> "Choose a practical check that fits your task and constraints."
}
