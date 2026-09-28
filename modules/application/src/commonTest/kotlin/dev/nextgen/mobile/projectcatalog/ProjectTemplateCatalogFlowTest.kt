package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.ProjectTemplateCatalogSnapshot
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import dev.nextgen.mobile.domain.project.TemplateSelectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectTemplateCatalogFlowTest {
    @Test
    fun `browse shows all five families and counts only selectable templates`() {
        val catalog = ProjectTemplateCatalogSnapshot(
            schemaVersion = 1,
            templates = listOf(
                template("review-ready", ProjectTemplateFamily.LITERATURE_REVIEW),
                template(
                    "survey-draft",
                    ProjectTemplateFamily.OBSERVATIONAL_SURVEY,
                    publication = ProjectTemplatePublication.DRAFT,
                    reviewedExample = false,
                ),
            ),
        )

        val browse = ProjectTemplateCatalogFlow.browse(catalog)

        assertEquals(5, browse.families.size)
        assertEquals(1, browse.families.single { it.family == ProjectTemplateFamily.LITERATURE_REVIEW }.selectableTemplateCount)
        assertEquals(0, browse.families.single { it.family == ProjectTemplateFamily.OBSERVATIONAL_SURVEY }.selectableTemplateCount)
    }

    @Test
    fun `browse carries catalog validation instead of disguising invalid data as empty catalog`() {
        val browse = ProjectTemplateCatalogFlow.browse(
            ProjectTemplateCatalogSnapshot(schemaVersion = 0, templates = emptyList()),
        )

        assertEquals(false, browse.validation.isValid)
        assertEquals("INVALID_CATALOG_SCHEMA_VERSION", browse.validation.issues.single().code)
        assertEquals(0, browse.families.sumOf { it.selectableTemplateCount })
    }

    @Test
    fun `inspect returns no selectable placeholders for an empty family`() {
        val catalog = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = emptyList())

        val detail = ProjectTemplateCatalogFlow.inspect(catalog, ProjectTemplateFamily.DESIGN_ENGINEERING)

        assertEquals(
            ProjectTemplateFamilyDetail.NoReadyTemplates(ProjectTemplateFamily.DESIGN_ENGINEERING),
            detail,
        )
    }

    @Test
    fun `inspect exposes only published reviewed templates`() {
        val published = template("published-review", ProjectTemplateFamily.LITERATURE_REVIEW)
        val draft = template(
            "draft-review",
            ProjectTemplateFamily.LITERATURE_REVIEW,
            publication = ProjectTemplatePublication.DRAFT,
            reviewedExample = false,
        )
        val catalog = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(published, draft))

        val detail = ProjectTemplateCatalogFlow.inspect(catalog, ProjectTemplateFamily.LITERATURE_REVIEW)

        val available = assertIs<ProjectTemplateFamilyDetail.Ready>(detail)
        assertEquals(listOf("published-review"), available.templates.map(ProjectTemplateDefinition::id))
    }

    @Test
    fun `choose delegates to the domain version and publication gate`() {
        val candidate = template("survey-review", ProjectTemplateFamily.OBSERVATIONAL_SURVEY)
        val catalog = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(candidate))

        val chosen = ProjectTemplateCatalogFlow.choose(catalog, candidate.id, candidate.version)
        val stale = ProjectTemplateCatalogFlow.choose(catalog, candidate.id, candidate.version + 1)

        assertIs<TemplateSelectionResult.Selected>(chosen)
        assertEquals(TemplateSelectionResult.Unavailable("TEMPLATE_VERSION_MISMATCH"), stale)
    }

    private fun template(
        id: String,
        family: ProjectTemplateFamily,
        publication: ProjectTemplatePublication = ProjectTemplatePublication.PUBLISHED,
        reviewedExample: Boolean = true,
    ) = ProjectTemplateDefinition(
        id = id,
        version = 1,
        family = family,
        title = "Starter",
        summary = "A bounded template for structuring a student project.",
        intendedOutput = "A traceable project outline.",
        inputFields = listOf(
            ProjectTemplateInputField(
                id = "question",
                kind = ProjectTemplateInputKind.RESEARCH_QUESTION,
                label = "Research question",
                required = true,
            ),
        ),
        steps = listOf(ProjectTemplateStep("frame-question", "Frame the question", listOf("question"))),
        methodSpecificLimitations = listOf("Use only for the method described by this template."),
        provenanceRequirements = listOf("Record where sources or data came from."),
        accessibilityExpectations = listOf("Labels and instructions must remain accessible."),
        examples = listOf(
            ProjectTemplateExample("example-normal", "Synthetic nominal example.", reviewedExample, ProjectTemplateExampleKind.NORMAL),
            ProjectTemplateExample(
                "example-edge",
                "Synthetic edge or conflicting example.",
                reviewedExample,
                ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
            ),
        ),
        publication = publication,
    )
}
