package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectClaimReviewStatus
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelationType
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.domain.project.SourceSelectionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudentProjectExportFormatterTest {
    @Test
    fun `markdown export is a bounded snapshot and labels student entered content honestly`() {
        val project = manualProject().copy(
            fieldValues = mapOf(
                ManualLiteratureSynthesisFields.ASSIGNMENT_BRIEF to "Compare two approaches.",
                ManualLiteratureSynthesisFields.CLAIM to "The sources suggest a possible pattern.",
                ManualLiteratureSynthesisFields.LIMITATIONS to "Only two sources were recorded.",
            ),
            sources = listOf(
                StudentProjectSourceRecord(
                    id = "source-a",
                    title = "Student-entered source",
                    citationText = "Citation as recorded by the student.",
                    selectionStatus = SourceSelectionStatus.SELECTED,
                    studentChecked = true,
                ),
            ),
            themes = listOf(
                StudentProjectSynthesisTheme(
                    id = "theme-a",
                    title = "Possible pattern",
                    synthesis = "The two sources differ in context.",
                    sourceIds = setOf("source-a"),
                ),
            ),
            claimEvidenceSourceIds = setOf("source-a"),
        )

        val artifact = StudentProjectExportFormatter.markdown(project)

        assertEquals("my-literature-project.md", artifact.fileName)
        assertEquals("text/markdown", artifact.mimeType)
        assertTrue(artifact.content.contains("# My literature project"))
        assertTrue(artifact.content.contains("Snapshot revision: 1"))
        assertTrue(artifact.content.contains("not been independently verified by Evidrilo"))
        assertTrue(artifact.content.contains("The sources suggest a possible pattern\\."))
        assertTrue(artifact.content.contains("Possible pattern"))
        assertTrue(artifact.content.contains("not a grade and does not establish"))
        assertTrue(artifact.content.contains("source-a"))
    }

    @Test
    fun `markdown export escapes user supplied heading and html syntax`() {
        val project = manualProject().copy(
            title = "# <script>not a title</script>",
            fieldValues = mapOf(ManualLiteratureSynthesisFields.CLAIM to "## forged heading"),
        )

        val content = StudentProjectExportFormatter.markdown(project).content

        assertTrue(content.contains("\\# &lt;script&gt;not a title&lt;/script&gt;"))
        assertTrue(content.contains("\\#\\# forged heading"))
        assertTrue(!content.contains("<script>"))
    }

    @Test
    fun `markdown export preserves limitation action links without implying a verdict`() {
        val project = manualProject().copy(
            findings = listOf(StudentProjectFindingRecord("finding-a", statement = "A reported pattern")),
            claims = listOf(StudentProjectClaimRecord("claim-a", statement = "A bounded claim")),
            limitationActions = listOf(StudentProjectLimitationActionRecord(
                id = "limit-a",
                boundary = "Only one population is represented.",
                reason = "The second source studies a different group.",
                nextAction = "Compare findings by population.",
                affectedFindingIds = setOf("finding-a"),
                affectedClaimIds = setOf("claim-a"),
            )),
        )

        val content = StudentProjectExportFormatter.markdown(project).content

        assertTrue(content.contains("## Limitations and next actions"))
        assertTrue(content.contains("Only one population is represented\\."))
        assertTrue(content.contains("The second source studies a different group\\."))
        assertTrue(content.contains("Compare findings by population\\."))
        assertTrue(content.contains("finding-a"))
        assertTrue(content.contains("claim-a"))
        assertTrue(content.contains("student-recorded relationship; not an Evidrilo verdict"))
    }

    @Test
    fun `markdown export escapes template title from catalog content`() {
        val project = manualProject().copy(
            templateSnapshot = ProjectTemplateDefinition(
                id = "template-a",
                version = 1,
                family = ProjectTemplateFamily.LITERATURE_REVIEW,
                title = "# <unsafe>",
                summary = "",
                intendedOutput = "",
                inputFields = emptyList(),
                steps = emptyList(),
                methodSpecificLimitations = emptyList(),
                provenanceRequirements = emptyList(),
                accessibilityExpectations = emptyList(),
                examples = emptyList(),
                publication = ProjectTemplatePublication.PUBLISHED,
            ),
        )

        val content = StudentProjectExportFormatter.markdown(project).content

        assertTrue(content.contains("Template: \\# &lt;unsafe&gt; · version 1"))
        assertTrue(!content.contains("Template: # <unsafe>"))
    }

    @Test
    fun `source matrix csv quotes commas quotes and embedded line breaks`() {
        val project = manualProject().copy(
            sources = listOf(
                StudentProjectSourceRecord(
                    id = "source-a",
                    title = "Source, \"A\"",
                    citationText = "Line one\nLine two",
                    selectionStatus = SourceSelectionStatus.SELECTED,
                ),
            ),
        )

        val csv = StudentProjectExportFormatter.sourceMatrixCsv(project).content

        assertTrue(csv.startsWith("\"source_id\",\"title\",\"authors_or_organization\""))
        assertTrue(csv.contains("\"Source, \"\"A\"\"\""))
        assertTrue(csv.contains("\"Line one\r\nLine two\""))
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun `source matrix csv prefixes spreadsheet formula-like student values`() {
        val project = manualProject().copy(
            sources = listOf(
                StudentProjectSourceRecord(
                    id = "source-a",
                    title = "  =HYPERLINK(\"https://example.invalid\")",
                    citationText = "+1+1",
                ),
            ),
        )

        val csv = StudentProjectExportFormatter.sourceMatrixCsv(project).content

        assertTrue(csv.contains("\"'  =HYPERLINK(\"\"https://example.invalid\"\")\""))
        assertTrue(csv.contains("\"'+1+1\""))
    }

    @Test
    fun `source and theme csv prefix full width formula characters after unicode whitespace`() {
        val project = manualProject().copy(
            sources = listOf(
                StudentProjectSourceRecord(
                    id = "source-a",
                    title = "\u3000＝SUM(1,2)",
                    authors = "＋SUM(1,2)",
                    year = "－1+2",
                    sourceType = "＠HYPERLINK(\"https://example.invalid\")",
                    studentNotes = "\tordinary text",
                ),
            ),
            themes = listOf(
                StudentProjectSynthesisTheme(
                    id = "theme-a",
                    title = "＝SUM(1,2)",
                    synthesis = "＋SUM(1,2)",
                    conflictOrVariation = "－1+2",
                ),
            ),
        )

        val sourceCsv = StudentProjectExportFormatter.sourceMatrixCsv(project).content
        val themeCsv = StudentProjectExportFormatter.synthesisThemesCsv(project).content

        assertTrue(sourceCsv.contains("\"'\u3000＝SUM(1,2)\""))
        assertTrue(sourceCsv.contains("\"'＋SUM(1,2)\""))
        assertTrue(sourceCsv.contains("\"'－1+2\""))
        assertTrue(sourceCsv.contains("\"'＠HYPERLINK(\"\"https://example.invalid\"\")\""))
        assertTrue(sourceCsv.contains("\"'\tordinary text\""))
        assertTrue(themeCsv.contains("\"'＝SUM(1,2)\""))
        assertTrue(themeCsv.contains("\"'＋SUM(1,2)\""))
        assertTrue(themeCsv.contains("\"'－1+2\""))
        assertTrue(themeCsv.contains("Formula-like values are prefixed with an apostrophe"))
    }

    @Test
    fun `themes csv retains stable source links and emits header for empty project`() {
        val empty = StudentProjectExportFormatter.synthesisThemesCsv(manualProject()).content
        val withTheme = StudentProjectExportFormatter.synthesisThemesCsv(
            manualProject().copy(
                sources = listOf(StudentProjectSourceRecord(id = "source-a", title = "Paper A")),
                themes = listOf(StudentProjectSynthesisTheme(id = "theme-a", title = "Theme", sourceIds = setOf("source-a"))),
            ),
        ).content

        assertEquals("\"theme_id\",\"title\",\"student_synthesis\",\"source_ids\",\"source_titles\",\"student_noted_differences_or_variation\",\"spreadsheet_safety_note\"\r\n", empty)
        assertTrue(withTheme.contains("\"theme-a\",\"Theme\""))
        assertTrue(withTheme.contains("\"source-a\",\"Paper A\""))
    }

    @Test
    fun `markdown and csv exports preserve evidence finding identity and typed links`() {
        val project = manualProject().copy(
            sources = listOf(StudentProjectSourceRecord(id = "source-a", title = "Paper A")),
            evidenceItems = listOf(StudentProjectEvidenceItem(
                id = "evidence-a", sourceId = "source-a", excerpt = "A reported association", locator = "Table 2",
            )),
            findings = listOf(StudentProjectFindingRecord(
                id = "finding-a", statement = "The sources report an association", scopeNote = "Two studies",
            )),
            evidenceRelations = listOf(StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.FINDING,
                "finding-a",
                "evidence-a",
                StudentProjectEvidenceRelationType.CONTRADICTS,
                "The populations differ.",
            )),
        )

        val markdown = StudentProjectExportFormatter.markdown(project).content
        val evidenceCsv = StudentProjectExportFormatter.evidenceItemsCsv(project).content
        val findingsCsv = StudentProjectExportFormatter.findingsCsv(project).content
        val relationCsv = StudentProjectExportFormatter.evidenceRelationsCsv(project).content

        assertTrue(markdown.contains("Evidence item ID: `evidence-a`"))
        assertTrue(markdown.contains("Finding ID: `finding-a`"))
        assertTrue(markdown.contains("Contradicts"))
        assertTrue(evidenceCsv.contains("\"evidence-a\",\"source-a\""))
        assertTrue(findingsCsv.contains("\"finding-a\",\"The sources report an association\""))
        assertTrue(relationCsv.contains("\"FINDING\",\"finding-a\",\"evidence-a\",\"Contradicts\""))
    }

    @Test
    fun `markdown export preserves all claims and their stable evidence relationships`() {
        val project = manualProject().copy(
            claims = listOf(
                StudentProjectClaimRecord("claim-a", "First claim.", "In one context.", "Small sample.", StudentProjectClaimReviewStatus.READY_FOR_REVIEW),
                StudentProjectClaimRecord("claim-b", "Second claim.", "In another context."),
            ),
            evidenceItems = listOf(StudentProjectEvidenceItem("evidence-a", "source-a", "Finding A")),
            evidenceRelations = listOf(StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.CLAIM,
                "claim-a",
                "evidence-a",
                StudentProjectEvidenceRelationType.SUPPORTS,
                "The note is relevant to claim A.",
            )),
        )

        val markdown = StudentProjectExportFormatter.markdown(project).content

        assertTrue(markdown.contains("Claim ID: `claim-a`"))
        assertTrue(markdown.contains("Claim ID: `claim-b`"))
        assertTrue(markdown.contains("First claim"))
        assertTrue(markdown.contains("READY_FOR_REVIEW".lowercase().replace('_', ' ')))
        assertTrue(markdown.contains("Evidence `evidence-a` Supports"))
        assertTrue(markdown.contains("not an Evidrilo assessment"))
    }

    @Test
    fun `markdown export includes immutable checkpoint actor and summary`() {
        val project = StudentProjectDraftRules.initializeRevisionHistory(manualProject())

        val markdown = StudentProjectExportFormatter.markdown(project).content

        assertTrue(markdown.contains("## Revision checkpoints"))
        assertTrue(markdown.contains("Revision 1 · Project created · student"))
    }

    private fun manualProject() = StudentProjectDraft(
        id = "project-a",
        templateSnapshot = null,
        title = "My literature project",
        fieldValues = emptyMap(),
        revision = 1,
        createdAtEpochMillis = 100,
        updatedAtEpochMillis = 100,
        status = StudentProjectStatus.DRAFT,
    )
}
