package dev.nextgen.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.*

internal data class ProjectOpinionIntent(val projectId: String, val revision: Int, val prompt: String)

/** Bounded excerpt of a saved snapshot. No files, inferred facts or generated answers. */
internal fun projectOpinionIntent(draft: StudentProjectDraft): ProjectOpinionIntent {
    val prompt=buildString {
        appendLine("Give a formative opinion on the student's recorded project excerpt below. Do not grade it, verify truth or citations, invent sources or missing data, or write the student's conclusion. Distinguish recorded content from uncertainty. Explain possible gaps and propose up to three practical next actions. This excerpt may omit material. No original files are included.")
        appendLine("Project: ${draft.title.take(160)}; saved revision ${draft.revision}")
        appendLine("Structure: ${draft.templateSnapshot?.title ?: "Independent project"}")
        draft.fieldValues.entries.filter { it.value.isNotBlank() }.take(6).forEach { appendLine("${draft.templateSnapshot?.inputFields?.firstOrNull { field -> field.id == it.key }?.label?.take(140) ?: it.key}: ${it.value.take(160)}") }
        draft.sources.take(3).forEach { appendLine("Recorded source ${it.id}: ${it.title.take(100)}; ${it.citationText.take(100)}") }
        draft.evidenceItems.take(4).forEach { appendLine("Note ${it.id} from source ${it.sourceId}: ${it.excerpt.take(180)}") }
        StudentProjectDraftRules.effectiveClaims(draft).take(3).forEach { appendLine("Claim ${it.id}: ${it.statement.take(180)}; scope: ${it.scopeNote.take(100)}; limits: ${it.limitationsNote.take(100)}") }
        draft.evidenceRelations.take(3).forEach { appendLine("Recorded ${it.relation.name}: ${it.evidenceId} -> ${it.targetId}; ${it.rationale.take(100)}") }
        draft.limitationActions.take(2).forEach { appendLine("Boundary: ${it.boundary.take(120)}; next action: ${it.nextAction.take(120)}") }
        appendLine("Respond in the student's selected interface language.")
    }.take(4000)
    return ProjectOpinionIntent(draft.id,draft.revision,prompt)
}

@Composable
internal fun EvidriloProjectReview(draft: StudentProjectDraft, isDirty: Boolean, onSection: (String)->Unit,
    onMap: ()->Unit, onExport: ()->Unit, onAiOpinion: ()->Unit) {
    val report=StudentProjectDraftRules.structureReport(draft)
    val sections=studentProjectEditorSections(draft)
    val suggestions=projectWorkSuggestions(draft)
    val claims=StudentProjectDraftRules.effectiveClaims(draft)
    val map=projectMapData(draft)
    val scopeCount=claims.count { it.scopeNote.isNotBlank() || it.limitationsNote.isNotBlank() || draft.limitationActions.any { a -> it.id in a.affectedClaimIds && a.boundary.isNotBlank() } }
    var explanation by remember { mutableStateOf(false) }
    Text(uiText("A clearer next step", "Langkah berikutnya lebih jelas"),style=MaterialTheme.typography.titleLarge)
    Text(uiText("Local checks on recorded material. No academic score.", "Pemeriksaan lokal pada catatan Anda. Bukan nilai akademik."),style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
    if(isDirty) Text(uiText("Showing current edits. Save before export or AI opinion.", "Menampilkan perubahan saat ini. Simpan sebelum ekspor atau meminta pendapat AI."),style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
    if(suggestions.isNotEmpty()) {
        val next=suggestions.first()
        EvidriloTintPanel {
            Text(uiText("Work on this next","Kerjakan ini berikutnya"),style=MaterialTheme.typography.titleMedium)
            Text(uiText(next.title),style=MaterialTheme.typography.bodyMedium)
            EvidriloPrimaryButton(uiText("Open next task","Buka tugas berikutnya"),{onSection(next.sectionId)},trailingIcon=EvidriloIconName.ARROW_FORWARD)
        }
    }
    val progress=StudentProjectDraftRules.requiredFieldProgress(draft)
    val checks=listOf(
        Triple(StudentProjectEditorSectionKind.TEMPLATE_STEP,uiText("Assignment responses","Isian tugas"),uiText("${progress.filledRequired}/${progress.totalRequired} recorded", "${progress.filledRequired}/${progress.totalRequired} terisi")),
        Triple(StudentProjectEditorSectionKind.SOURCES_AND_FILES,uiText("Sources","Sumber"),if(draft.sources.isEmpty()) uiText("Add your first source", "Tambahkan sumber pertama") else uiText("${draft.sources.size} recorded · ${report.incompleteSourceIds.size} need details", "${draft.sources.size} tercatat · ${report.incompleteSourceIds.size} perlu detail")),
        Triple(StudentProjectEditorSectionKind.EVIDENCE_NOTES,uiText("Evidence notes","Catatan bukti"),if(draft.evidenceItems.isEmpty()) uiText("Record a note from your source", "Catat bukti dari sumber Anda") else uiText("${draft.evidenceItems.size} recorded · ${report.incompleteEvidenceIds.size} empty", "${draft.evidenceItems.size} tercatat · ${report.incompleteEvidenceIds.size} kosong")),
        Triple(StudentProjectEditorSectionKind.CLAIMS,uiText("Claim relationships","Hubungan klaim"),if(claims.isEmpty()) uiText("Draft a claim from your notes", "Susun klaim dari catatan Anda") else uiText("${claims.size} claims · ${report.unlinkedClaimIds.size} without note links", "${claims.size} klaim · ${report.unlinkedClaimIds.size} tanpa tautan catatan")),
        Triple(StudentProjectEditorSectionKind.LIMITATIONS_AND_NEXT_STEPS,uiText("Scope and limits","Cakupan dan batas"),if(claims.isEmpty()) uiText("Add a claim, then record its limits", "Tambahkan klaim lalu catat batasnya") else uiText("$scopeCount/${claims.size} claims have recorded boundaries", "$scopeCount/${claims.size} klaim memiliki batas tercatat")),
        Triple(StudentProjectEditorSectionKind.CLAIMS,uiText("Link integrity","Keutuhan tautan"),uiText("${map.invalidLinks} missing references", "${map.invalidLinks} referensi tidak ditemukan")))
    checks.forEach { (kind,title,detail) ->
        val target=sections.firstOrNull { it.kind==kind } ?: sections.first()
        EvidriloWorkspaceRow(projectSectionIcon(kind),title,detail,{onSection(target.navigationId)})
    }
    val opposing=draft.evidenceRelations.count { it.relation==StudentProjectEvidenceRelationType.CONTRADICTS }
    if(opposing>0) Text(uiText("$opposing opposing relationships recorded. Inspect their rationale before sharing.",
        "$opposing hubungan yang bertentangan tercatat. Tinjau alasannya sebelum membagikan."),style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
    EvidriloSecondaryButton(uiText("Explore project map","Jelajahi peta proyek"),onMap)
    EvidriloProjectRecordCard(uiText("Ask for an AI opinion","Minta pendapat AI"),uiText("Optional · uses AI credits","Opsional · memakai kredit AI"),initiallyExpanded=true) {
        Text(uiText("Review the saved excerpt before sending. AI gives suggestions, not a verdict; it may be wrong.",
            "Tinjau cuplikan tersimpan sebelum mengirim. AI memberi saran, bukan keputusan; jawabannya bisa keliru."),style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
        EvidriloSecondaryButton(uiText("Review context with AI","Tinjau konteks dengan AI"),onAiOpinion)
    }
    EvidriloPrimaryButton(uiText("Export project","Ekspor proyek"),onExport,trailingIcon=EvidriloIconName.ARROW_FORWARD)
    TextButton({explanation=true}) {Text(uiText("What these checks mean","Arti pemeriksaan ini"))}
    if(explanation) AlertDialog(onDismissRequest={explanation=false},containerColor=EvidriloColors.Card,
        title={Text(uiText("Explainable checks","Pemeriksaan yang dapat dijelaskan"))},text={Text(uiText(
            "Checks count required responses, nonempty records and explicit relationships. Recorded boundaries and links do not prove a claim. Missing references mean a linked record is absent. These checks cannot evaluate methods, scientific truth, citation credibility or academic quality. The same required-field rules follow your selected project structure.",
            "Pemeriksaan menghitung isian wajib, catatan yang terisi, dan hubungan eksplisit. Batas dan tautan tercatat tidak membuktikan klaim. Referensi yang hilang berarti catatan tujuannya tidak ada. Pemeriksaan ini tidak menilai metode, kebenaran ilmiah, kredibilitas sitasi, atau kualitas akademik. Aturan isian mengikuti struktur proyek yang dipilih."))},
        confirmButton={TextButton({explanation=false}){Text(uiText("Understood","Mengerti"))}})
}
