package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelationType
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFieldDefinition
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.domain.project.SourceSelectionStatus

data class StudentProjectExportArtifact(
    val fileName: String,
    val mimeType: String,
    val content: String,
    val binaryContent: ByteArray? = null,
)

/** Renders local project snapshots only; it does not assess academic quality or source credibility. */
object StudentProjectExportFormatter {
    private const val SPREADSHEET_SAFETY_NOTE =
        "Formula-like values are prefixed with an apostrophe for spreadsheet safety; spreadsheet behavior varies, so review exported cells before use."
    private val spreadsheetFormulaStarts = setOf('=', '+', '-', '@', '＝', '＋', '－', '＠')

    fun markdown(project: StudentProjectDraft): StudentProjectExportArtifact = StudentProjectExportArtifact(
        fileName = fileName(project, "md"),
        mimeType = "text/markdown",
        content = buildString {
            appendLine("# ${project.title.asMarkdownText()}")
            appendLine()
            appendLine("> Snapshot revision: ${project.revision}")
            appendLine("> Student-entered content; sources and claims have not been independently verified by Evidrilo.")
            appendLine()
            appendLine("## Project record")
            appendLine()
            appendLine("- Project ID: `${project.id}`")
            appendLine("- Status: ${project.status.displayLabel()}")
            appendLine("- Created (UTC epoch milliseconds): ${project.createdAtEpochMillis}")
            appendLine("- Last saved (UTC epoch milliseconds): ${project.updatedAtEpochMillis}")
            val templateDescription = project.templateSnapshot?.let { "${it.title} · version ${it.version}" }
                ?: "Manual project; no reviewed catalog template applied"
            appendLine("- Template: ${templateDescription.asMarkdownText()}")
            appendLine()
            appendLine("## Revision checkpoints")
            appendLine()
            if (project.revisionSnapshots.isEmpty()) {
                appendLine("No immutable revision checkpoints are available in this older project record.")
            } else {
                project.revisionSnapshots.forEach { snapshot ->
                    appendLine("- Revision ${snapshot.revision} · ${snapshot.changeSummary.asMarkdownText()} · ${snapshot.actor.name.lowercase().replace('_', ' ')} · saved ${snapshot.savedAtEpochMillis} UTC epoch milliseconds")
                }
            }
            appendLine()
            appendLine("## Project sections")
            appendLine()
            project.fieldDefinitions().filterNot { field ->
                field.id == ManualLiteratureSynthesisFields.CLAIM &&
                    StudentProjectDraftRules.effectiveClaims(project).isNotEmpty()
            }.forEach { field ->
                appendLine("### ${field.label.asMarkdownText()}")
                appendLine()
                appendLine(project.fieldValues[field.id].orEmpty().presentOrNotEntered().asMarkdownText())
                appendLine()
            }
            appendLine("## Sources and extraction notes")
            appendLine()
            if (project.sources.isEmpty()) {
                appendLine("No sources have been entered.")
                appendLine()
            } else {
                project.sources.forEach { source ->
                    appendLine("### ${source.title.ifBlank { "Untitled source · ${source.id}" }.asMarkdownText()}")
                    appendLine()
                    appendLine("- Source ID: `${source.id}`")
                    appendLine("- Authors/organization: ${source.authors.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Year: ${source.year.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Type: ${source.sourceType.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- DOI/URL as entered: ${source.doiOrUrl.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Access date as entered: ${source.accessedOn.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Citation text: ${source.citationText.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Student selection status: ${source.selectionStatus.displayLabel()}")
                    if (source.selectionStatus == SourceSelectionStatus.EXCLUDED) {
                        appendLine("- Student-entered exclusion reason: ${source.exclusionReason.presentOrNotEntered().asMarkdownText()}")
                    }
                    appendLine("- Aim/purpose reported in source: ${source.reportedAim.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Context/population reported in source: ${source.reportedContext.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Method reported in source: ${source.reportedMethod.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Findings reported in source: ${source.reportedFindings.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Limitations reported in source: ${source.reportedLimitations.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Student notes: ${source.studentNotes.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Student marked source as checked: ${if (source.studentChecked) "Yes" else "No"}; this is not independent verification by Evidrilo.")
                    appendLine()
                }
            }
            appendLine("## Evidence notes")
            appendLine()
            if (project.evidenceItems.isEmpty()) {
                appendLine("No evidence notes have been entered.")
                appendLine()
            } else {
                val sourceNames = project.sources.associate { it.id to it.title.ifBlank { it.id } }
                project.evidenceItems.forEach { evidence ->
                    appendLine("### Evidence item ID: `${evidence.id}`")
                    appendLine()
                    appendLine("- Source: ${(sourceNames[evidence.sourceId] ?: "Unavailable source · ${evidence.sourceId}").asMarkdownText()} (`${evidence.sourceId}`)")
                    appendLine("- Location as entered: ${evidence.locator.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Student checked note against source: ${if (evidence.studentChecked) "Yes" else "No"}")
                    appendLine("- Excerpt or paraphrase entered by student: ${evidence.excerpt.presentOrNotEntered().asMarkdownText()}")
                    val links = project.evidenceRelations.filter { it.evidenceId == evidence.id }
                    appendLine("- User-selected relationships: ${links.joinToString { "${it.targetType.name.lowercase()} ${it.targetId}: ${it.relation.displayLabel()}" }.ifBlank { "None" }}")
                    appendLine()
                }
            }
            appendLine("## Findings")
            appendLine()
            if (project.findings.isEmpty()) {
                appendLine("No separate findings have been entered.")
                appendLine()
            } else {
                project.findings.forEach { finding ->
                    appendLine("### Finding ID: `${finding.id}`")
                    appendLine()
                    appendLine("- Student-authored statement: ${finding.statement.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Scope/context: ${finding.scopeNote.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Uncertainty/variation: ${finding.uncertaintyNote.presentOrNotEntered().asMarkdownText()}")
                    project.evidenceRelations.filter {
                        it.targetType == StudentProjectEvidenceTargetType.FINDING && it.targetId == finding.id
                    }.forEach { relation ->
                        appendLine("- Evidence `${relation.evidenceId}` ${relation.relation.displayLabel().asMarkdownText()}: ${relation.rationale.presentOrNotEntered().asMarkdownText()}")
                    }
                    appendLine()
                }
            }
            appendLine("## Synthesis themes")
            appendLine()
            if (project.themes.isEmpty()) {
                appendLine("No synthesis themes have been entered.")
                appendLine()
            } else {
                project.themes.forEach { theme ->
                    appendLine("### ${theme.title.ifBlank { "Untitled theme · ${theme.id}" }.asMarkdownText()}")
                    appendLine()
                    appendLine("- Theme ID: `${theme.id}`")
                    appendLine("- Student synthesis: ${theme.synthesis.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Linked source IDs: ${theme.sourceIds.sorted().joinToString().ifBlank { "None" }}")
                    appendLine("- Differences/variation noted by student: ${theme.conflictOrVariation.presentOrNotEntered().asMarkdownText()}")
                    appendLine()
                }
            }
            appendLine("## Claims and evidence relationships")
            appendLine()
            appendLine("- Sources associated with this project (student-entered links, not a support verdict): ${project.claimEvidenceSourceIds.sorted().joinToString().ifBlank { "None" }}")
            val claims = StudentProjectDraftRules.effectiveClaims(project)
            if (claims.isEmpty()) {
                appendLine("No claims have been recorded.")
            } else {
                claims.forEach { claim ->
                    appendLine("### Claim ID: `${claim.id}`")
                    appendLine()
                    appendLine("- Student-authored statement: ${claim.statement.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Scope: ${claim.scopeNote.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Claim-specific limitations: ${claim.limitationsNote.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Student review state: ${claim.reviewStatus.name.lowercase().replace('_', ' ')}; this is not an Evidrilo assessment.")
                    project.evidenceRelations.filter {
                        it.targetType == StudentProjectEvidenceTargetType.CLAIM && it.targetId == claim.id
                    }.forEach { relation ->
                        appendLine("- Evidence `${relation.evidenceId}` ${relation.relation.displayLabel().asMarkdownText()}: ${relation.rationale.presentOrNotEntered().asMarkdownText()}")
                    }
                    appendLine()
                }
            }
            appendLine("## Limitations and next actions")
            appendLine()
            appendLine("Each link is a student-recorded relationship; not an Evidrilo verdict.")
            appendLine()
            if (project.limitationActions.isEmpty()) {
                appendLine("No structured limitation/action records have been entered.")
                appendLine()
            } else {
                val findingNames = project.findings.associate { finding ->
                    finding.id to finding.statement.ifBlank { "Unnamed finding" }
                }
                val claimNames = claims.associate { claim ->
                    claim.id to claim.statement.ifBlank { "Unnamed claim" }
                }
                project.limitationActions.forEach { item ->
                    appendLine("### Limitation/action ID: `${item.id}`")
                    appendLine()
                    appendLine("- Boundary: ${item.boundary.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Reason: ${item.reason.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Next action: ${item.nextAction.presentOrNotEntered().asMarkdownText()}")
                    appendLine("- Affected findings: ${item.affectedFindingIds.sorted().joinToString { id ->
                        "${(findingNames[id] ?: "Unavailable finding").asMarkdownText()} (`$id`)"
                    }.ifBlank { "Project-wide / no finding linked" }}")
                    appendLine("- Affected claims: ${item.affectedClaimIds.sorted().joinToString { id ->
                        "${(claimNames[id] ?: "Unavailable claim").asMarkdownText()} (`$id`)"
                    }.ifBlank { "Project-wide / no claim linked" }}")
                    appendLine()
                }
            }
            val structure = StudentProjectDraftRules.structureReport(project)
            appendLine("- Required sections present: ${if (structure.missingRequiredFieldIds.isEmpty()) "All currently defined required sections are filled" else "Some currently defined required sections are blank"}")
            if (structure.missingRequiredFieldIds.isNotEmpty()) {
                val labels = project.fieldDefinitions().associateBy(StudentProjectFieldDefinition::id)
                structure.missingRequiredFieldIds.forEach { id ->
                    appendLine("  - Missing: ${labels[id]?.label ?: id}")
                }
            }
            appendLine()
            appendLine("This structure check reports presence and recorded links only. It is not a grade and does not establish source quality, method quality, claim support, or truth.")
        },
    )

    fun sourceMatrixCsv(project: StudentProjectDraft): StudentProjectExportArtifact {
        val headers = listOf(
            "source_id", "title", "authors_or_organization", "year", "source_type", "doi_or_url_as_entered",
            "accessed_on_as_entered", "citation_text_as_entered", "student_selection_status", "student_exclusion_reason",
            "reported_aim", "reported_context_or_population", "reported_method", "reported_findings",
            "reported_limitations", "student_notes", "student_marked_checked", "provenance_notice",
            "spreadsheet_safety_note",
        )
        val rows = project.sources.map { source ->
            listOf(
                source.id,
                source.title,
                source.authors,
                source.year,
                source.sourceType,
                source.doiOrUrl,
                source.accessedOn,
                source.citationText,
                source.selectionStatus.displayLabel(),
                source.exclusionReason,
                source.reportedAim,
                source.reportedContext,
                source.reportedMethod,
                source.reportedFindings,
                source.reportedLimitations,
                source.studentNotes,
                if (source.studentChecked) "yes" else "no",
                "Student-entered; not independently verified by Evidrilo",
                SPREADSHEET_SAFETY_NOTE,
            )
        }
        return csvArtifact(project, "sources.csv", headers, rows)
    }

    fun synthesisThemesCsv(project: StudentProjectDraft): StudentProjectExportArtifact {
        val sourceNames = project.sources.associate { it.id to it.title.ifBlank { it.id } }
        val headers = listOf(
            "theme_id", "title", "student_synthesis", "source_ids", "source_titles",
            "student_noted_differences_or_variation", "spreadsheet_safety_note",
        )
        val rows = project.themes.map { theme ->
            listOf(
                theme.id,
                theme.title,
                theme.synthesis,
                theme.sourceIds.sorted().joinToString(";"),
                theme.sourceIds.sorted().mapNotNull(sourceNames::get).joinToString(";"),
                theme.conflictOrVariation,
                SPREADSHEET_SAFETY_NOTE,
            )
        }
        return csvArtifact(project, "synthesis-themes.csv", headers, rows)
    }

    fun evidenceItemsCsv(project: StudentProjectDraft): StudentProjectExportArtifact {
        val sourceNames = project.sources.associate { it.id to it.title.ifBlank { it.id } }
        val headers = listOf(
            "evidence_id", "source_id", "source_title", "excerpt_or_paraphrase_as_entered", "location_as_entered",
            "student_checked", "provenance_notice", "spreadsheet_safety_note",
        )
        val rows = project.evidenceItems.map { evidence ->
            listOf(
                evidence.id,
                evidence.sourceId,
                sourceNames[evidence.sourceId].orEmpty(),
                evidence.excerpt,
                evidence.locator,
                if (evidence.studentChecked) "yes" else "no",
                "Student-entered; not independently verified by Evidrilo",
                SPREADSHEET_SAFETY_NOTE,
            )
        }
        return csvArtifact(project, "evidence-items.csv", headers, rows)
    }

    fun findingsCsv(project: StudentProjectDraft): StudentProjectExportArtifact {
        val headers = listOf(
            "finding_id", "student_authored_statement", "scope_or_context", "uncertainty_or_variation",
            "provenance_notice", "spreadsheet_safety_note",
        )
        val rows = project.findings.map { finding ->
            listOf(
                finding.id,
                finding.statement,
                finding.scopeNote,
                finding.uncertaintyNote,
                "Student-authored; not independently validated by Evidrilo",
                SPREADSHEET_SAFETY_NOTE,
            )
        }
        return csvArtifact(project, "findings.csv", headers, rows)
    }

    fun evidenceRelationsCsv(project: StudentProjectDraft): StudentProjectExportArtifact {
        val headers = listOf(
            "target_type", "target_id", "evidence_id", "student_selected_relationship", "student_rationale",
            "relationship_notice", "spreadsheet_safety_note",
        )
        val rows = project.evidenceRelations.map { relation ->
            listOf(
                relation.targetType.name,
                relation.targetId,
                relation.evidenceId,
                relation.relation.displayLabel(),
                relation.rationale,
                "User-selected working relationship; not an evaluator judgment",
                SPREADSHEET_SAFETY_NOTE,
            )
        }
        return csvArtifact(project, "evidence-relations.csv", headers, rows)
    }

    private fun csvArtifact(
        project: StudentProjectDraft,
        suffix: String,
        headers: List<String>,
        rows: List<List<String>>,
    ): StudentProjectExportArtifact = StudentProjectExportArtifact(
        fileName = fileName(project, suffix),
        mimeType = "text/csv",
        content = (listOf(headers) + rows)
            .joinToString(separator = "\r\n", postfix = "\r\n") { row -> row.joinToString(",") { it.asCsvCell() } },
    )

    private fun StudentProjectDraft.fieldDefinitions(): List<StudentProjectFieldDefinition> = templateSnapshot?.inputFields?.map {
        StudentProjectFieldDefinition(it.id, it.label, it.label, it.required)
    } ?: ManualLiteratureSynthesisFields.all

    private fun fileName(project: StudentProjectDraft, suffix: String): String {
        val slug = project.title.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
            .joinToString("")
            .split('-')
            .filter(String::isNotEmpty)
            .joinToString("-")
            .take(60)
            .trim('-')
            .ifBlank { "project" }
        return "$slug.$suffix"
    }

    private fun String.presentOrNotEntered(): String = if (isBlank()) "[Not entered]" else this

    private fun String.asMarkdownText(): String = buildString {
        for (character in replace("\r\n", "\n").replace('\r', '\n')) {
            when (character) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '\n' -> append('\n')
                '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '~' -> {
                    append('\\').append(character)
                }
                else -> append(character)
            }
        }
    }

    private fun String.asCsvCell(): String {
        val firstRaw = firstOrNull()
        val firstMeaningful = firstOrNull { !it.isWhitespace() }
        val dangerousControlStart = firstRaw == '\t' || firstRaw == '\r' || firstRaw == '\n'
        val safe = if (dangerousControlStart || firstMeaningful in spreadsheetFormulaStarts) "'$this" else this
        val normalized = safe.replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\r\n")
        return "\"${normalized.replace("\"", "\"\"")}\""
    }

    private fun StudentProjectStatus.displayLabel(): String = when (this) {
        StudentProjectStatus.DRAFT -> "Draft"
        StudentProjectStatus.ACTIVE -> "Active"
        StudentProjectStatus.COMPLETED -> "Completed"
        StudentProjectStatus.ARCHIVED -> "Archived"
        StudentProjectStatus.TRASHED -> "In Trash"
    }

    private fun SourceSelectionStatus.displayLabel(): String = when (this) {
        SourceSelectionStatus.TO_REVIEW -> "To review"
        SourceSelectionStatus.SELECTED -> "Selected by student"
        SourceSelectionStatus.EXCLUDED -> "Excluded by student"
    }

    private fun StudentProjectEvidenceRelationType.displayLabel(): String = when (this) {
        StudentProjectEvidenceRelationType.SUPPORTS -> "Supports"
        StudentProjectEvidenceRelationType.CONTRADICTS -> "Contradicts"
        StudentProjectEvidenceRelationType.PROVIDES_CONTEXT -> "Provides context"
    }
}
