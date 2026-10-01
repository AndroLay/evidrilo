package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.*
import dev.nextgen.mobile.projectcatalog.*

/** A bounded saved local snapshot. Original files and inferred facts are never included. */
internal fun projectChatContext(draft: StudentProjectDraft): ProjectAiChatContext {
    val template = draft.templateSnapshot
    val fields = if (template == null) {
        ManualLiteratureSynthesisFields.all.filter { it.id in setOf(
            ManualLiteratureSynthesisFields.RESEARCH_QUESTION, ManualLiteratureSynthesisFields.AIM,
            ManualLiteratureSynthesisFields.SCOPE, ManualLiteratureSynthesisFields.LIMITATIONS,
            ManualLiteratureSynthesisFields.NEXT_ACTION,
        ) }.map { ProjectAiChatField(it.id, it.label, draft.fieldValues[it.id].orEmpty().take(1000)) }
    } else template.inputFields.filter { it.kind in setOf(
        ProjectTemplateInputKind.RESEARCH_QUESTION, ProjectTemplateInputKind.HYPOTHESIS,
        ProjectTemplateInputKind.ANALYSIS, ProjectTemplateInputKind.LIMITATION, ProjectTemplateInputKind.NEXT_ACTION,
    ) }.take(16).map { field -> ProjectAiChatField(field.id, field.label.take(160), draft.fieldValues[field.id].orEmpty().take(1000)) }
    val notes = buildString {
        appendLine("Student-selected saved notes. Original files are not included.")
        draft.fieldValues.entries.filter { it.value.isNotBlank() }.take(10).forEach { (id, value) ->
            appendLine("${draft.templateSnapshot?.inputFields?.firstOrNull { it.id == id }?.label ?: id}: ${value.take(250)}")
        }
        draft.sources.take(4).forEach { appendLine("Source ${it.id}: ${it.title.take(120)}; ${it.citationText.take(100)}") }
        draft.evidenceItems.take(6).forEach { appendLine("Note ${it.id} from ${it.sourceId}: ${it.excerpt.take(200)}") }
        StudentProjectDraftRules.effectiveClaims(draft).take(4).forEach { appendLine("Claim ${it.id}: ${it.statement.take(180)}; limits: ${it.limitationsNote.take(100)}") }
        draft.evidenceRelations.take(4).forEach { appendLine("Recorded relation: ${it.evidenceId} -> ${it.targetId}; ${it.relation}; ${it.rationale.take(80)}") }
    }.take(6000)
    return ProjectAiChatContext(draft.id, draft.revision, draft.title.ifBlank { "Untitled project" }.take(160), notes, fields)
}

internal fun validProjectChatEdits(context: ProjectAiChatContext, draft: StudentProjectDraft,
    edits: List<ProjectAiChatEdit>): Boolean =
    context.projectId == draft.id && context.revision == draft.revision &&
        edits.isNotEmpty() && edits.size <= 6 && edits.map { it.fieldId }.distinct().size == edits.size &&
        edits.all { edit -> context.fields.any { it.id == edit.fieldId } &&
            projectChatContext(draft).fields.any { it.id == edit.fieldId } &&
            edit.value.isNotBlank() && edit.value.length <= 8000 && '\u0000' !in edit.value }
