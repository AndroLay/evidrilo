package dev.nextgen.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields

internal fun projectSectionBookmarkKey(draft: StudentProjectDraft): String =
    "${draft.id}|${draft.templateSnapshot?.id ?: "manual"}|${draft.templateSnapshot?.version ?: 0}"

internal data class ProjectWorkSuggestion(val sectionId: String, val title: String, val detail: String)

/** Local structure guidance only; a recorded response or link is never a quality verdict. */
internal fun projectWorkSuggestions(draft: StudentProjectDraft): List<ProjectWorkSuggestion> {
    val sections = studentProjectEditorSections(draft)
    val report = StudentProjectDraftRules.structureReport(draft)
    val result = mutableListOf<ProjectWorkSuggestion>()
    fun add(kind: StudentProjectEditorSectionKind, title: String, detail: String) {
        val section = sections.firstOrNull { it.kind == kind } ?: return
        result += ProjectWorkSuggestion(section.navigationId, title, detail)
    }
    sections.forEach { section ->
        val missing = section.fieldIds.count { it in report.missingRequiredFieldIds }
        if (missing > 0) result += ProjectWorkSuggestion(section.navigationId, section.title,
            "$missing required ${if (missing == 1) "response is" else "responses are"} still empty. Start with one sentence.")
    }
    if (draft.sources.isEmpty()) add(StudentProjectEditorSectionKind.SOURCES_AND_FILES,
        "Record your first source", "Add material you actually used. Keep its title and citation with it.")
    else if (report.incompleteSourceIds.isNotEmpty()) add(StudentProjectEditorSectionKind.SOURCES_AND_FILES,
        "Complete your source details", "Add a title and citation to the source records that need them.")
    if (draft.sources.isNotEmpty() && draft.evidenceItems.isEmpty()) add(StudentProjectEditorSectionKind.EVIDENCE_NOTES,
        "Take a note from your material", "Record an excerpt or paraphrase and keep it connected to its source.")
    else if (report.incompleteEvidenceIds.isNotEmpty() || report.unlinkedEvidenceIds.isNotEmpty())
        add(StudentProjectEditorSectionKind.EVIDENCE_NOTES, "Review your evidence notes", "Add the missing note text or recorded connection.")
    if (report.unlinkedFindingIds.isNotEmpty() || report.unlinkedThemeIds.isNotEmpty())
        add(StudentProjectEditorSectionKind.FINDINGS_AND_SYNTHESIS, "Connect your findings", "Show which recorded material each finding or comparison uses.")
    if (report.unlinkedClaimIds.isNotEmpty() || report.invalidClaimEvidenceIds.isNotEmpty())
        add(StudentProjectEditorSectionKind.CLAIMS, "Review your claim links", "Connect each statement to the material you want it to use.")
    return result.distinctBy { it.sectionId }
}

@Composable
internal fun EvidriloProjectSectionCoach(section: StudentProjectEditorSection, draft: StudentProjectDraft) {
    val purpose = when (section.kind) {
        StudentProjectEditorSectionKind.PROJECT_BASICS -> "Give your question a place to grow."
        StudentProjectEditorSectionKind.TEMPLATE_STEP, StudentProjectEditorSectionKind.TEMPLATE_ADDITIONAL_FIELDS -> if (section.fieldIds.isEmpty()) "Explore this step with the help available for your project." else "Take one question at a time. Your answers stay editable."
        StudentProjectEditorSectionKind.SOURCES_AND_FILES -> "Keep the material you used close to your work."
        StudentProjectEditorSectionKind.EVIDENCE_NOTES -> "Turn a useful passage into a note you can trace."
        StudentProjectEditorSectionKind.FINDINGS_AND_SYNTHESIS -> "Describe what you noticed, then connect it to your notes."
        StudentProjectEditorSectionKind.CLAIMS -> "Say what your material supports, and show the connection."
        StudentProjectEditorSectionKind.LIMITATIONS_AND_NEXT_STEPS -> "Make room for what is uncertain and what comes next."
        StudentProjectEditorSectionKind.REVIEW -> "Follow your work from material to statement."
    }
    val filled = section.fieldIds.count {
        if (it == ManualLiteratureSynthesisFields.CLAIM) StudentProjectDraftRules.effectiveClaims(draft).any { claim -> claim.statement.isNotBlank() }
        else draft.fieldValues[it]?.isNotBlank() == true
    }
    val recorded = when (section.kind) {
        StudentProjectEditorSectionKind.SOURCES_AND_FILES -> "${draft.sources.size} sources recorded"
        StudentProjectEditorSectionKind.EVIDENCE_NOTES -> "${draft.evidenceItems.size} notes recorded"
        StudentProjectEditorSectionKind.FINDINGS_AND_SYNTHESIS -> "${draft.findings.size + draft.themes.size} findings and comparisons recorded"
        StudentProjectEditorSectionKind.CLAIMS -> "${StudentProjectDraftRules.effectiveClaims(draft).size} claims · ${draft.evidenceRelations.count { it.targetType == dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType.CLAIM }} claim evidence links"
        StudentProjectEditorSectionKind.LIMITATIONS_AND_NEXT_STEPS -> "${draft.limitationActions.size} boundary and action records"
        else -> if (section.fieldIds.isNotEmpty()) "$filled/${section.fieldIds.size} responses recorded" else null
    }
    var showPurpose by remember(section.navigationId) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EvidriloIcon(projectSectionIcon(section.kind), tint = EvidriloColors.Cobalt, modifier = Modifier.size(22.dp))
        AnimatedContent(recorded ?: "Your own project material", modifier = Modifier.weight(1f), label = "Recorded material") { count ->
            Text(count, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        IconButton({ showPurpose = true }, Modifier.semantics { contentDescription = "About this project step" }) {
            EvidriloIcon(EvidriloIconName.QUESTION, tint = EvidriloColors.Slate, modifier = Modifier.size(20.dp))
        }
    }
    if (showPurpose) AlertDialog(onDismissRequest = { showPurpose = false }, title = { Text(section.title) },
        text = { Text("$purpose Recorded responses and links are not grades or research-quality assessments.") },
        confirmButton = { TextButton({ showPurpose = false }) { Text(uiText("Got it")) } })
}

@Composable
internal fun EvidriloProjectMaterialOverview(draft: StudentProjectDraft) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple(EvidriloIconName.BOOK, "Sources", draft.sources.size),
            Triple(EvidriloIconName.FILE, "Notes", draft.evidenceItems.size),
            Triple(EvidriloIconName.LINK, "Claims", StudentProjectDraftRules.effectiveClaims(draft).size)).forEach { (icon, label, count) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EvidriloIcon(icon, tint = EvidriloColors.Cobalt)
                Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
                Text(label, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
        }
    }
}

@Composable
internal fun EvidriloProjectReviewGuidance(draft: StudentProjectDraft, onOpenSection: (String) -> Unit) {
    val suggestions = projectWorkSuggestions(draft)
    EvidriloProjectMaterialOverview(draft)
    Text(if (suggestions.isEmpty()) "Choose your next move" else "A useful next step", style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
    if (suggestions.isEmpty()) {
        Text("Inspect your material and its limits before sharing. Recorded fields and links do not establish research quality.",
            style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
    } else {
        val next = suggestions.first()
        Text(next.title, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Cobalt)
        Text(next.detail, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        EvidriloPrimaryButton("Open next task", { onOpenSection(next.sectionId) }, trailingIcon = EvidriloIconName.ARROW_FORWARD)
        if (suggestions.size > 1) EvidriloProjectRecordCard("More to work on", "${suggestions.size - 1} other sections") {
            suggestions.drop(1).forEach { item ->
                EvidriloWorkspaceRow(EvidriloIconName.CHECKLIST, item.title, item.detail, { onOpenSection(item.sectionId) })
            }
        }
        Text("These are structure prompts, not a grade or a verdict on your research.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
}
