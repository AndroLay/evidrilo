package dev.nextgen.mobile.domain.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectTemplateCatalogTest {
    @Test
    fun `initial catalog taxonomy has exactly five stable families`() {
        assertEquals(
            listOf(
                "experimental_laboratory",
                "observational_survey",
                "literature_review",
                "qualitative_interview_field_study",
                "design_engineering",
            ),
            ProjectTemplateFamily.values().map(ProjectTemplateFamily::id),
        )
    }

    @Test
    fun `catalog exposes every family while only ready templates are selectable`() {
        val ready = template(id = "literature-review", family = ProjectTemplateFamily.LITERATURE_REVIEW)
        val draft = template(
            id = "design-starter",
            family = ProjectTemplateFamily.DESIGN_ENGINEERING,
            publication = ProjectTemplatePublication.DRAFT,
            reviewedExample = false,
        )
        val catalog = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(ready, draft))

        val offerings = ProjectTemplateCatalog.familyOfferings(catalog)

        assertEquals(5, offerings.size)
        assertEquals(1, offerings.single { it.family == ProjectTemplateFamily.LITERATURE_REVIEW }.selectableTemplateCount)
        assertEquals(0, offerings.single { it.family == ProjectTemplateFamily.DESIGN_ENGINEERING }.selectableTemplateCount)
        assertEquals(0, offerings.single { it.family == ProjectTemplateFamily.EXPERIMENTAL_LABORATORY }.selectableTemplateCount)
    }

    @Test
    fun `published template requires a reviewed example before selection`() {
        val candidate = template(
            id = "survey-starter",
            family = ProjectTemplateFamily.OBSERVATIONAL_SURVEY,
            publication = ProjectTemplatePublication.PUBLISHED,
            reviewedExample = false,
        )
        val catalog = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(candidate))

        val validation = ProjectTemplateCatalog.validate(catalog)
        val selection = ProjectTemplateCatalog.select(catalog, candidate.id, candidate.version)

        assertTrue(validation.issues.any { it.code == "PUBLISHED_TEMPLATE_REQUIRES_REVIEWED_NORMAL_AND_EDGE_EXAMPLES" })
        assertEquals(
            TemplateSelectionResult.Unavailable("TEMPLATE_NOT_READY"),
            selection,
        )
    }

    @Test
    fun `legacy unclassified template remains readable but cannot start a new project`() {
        val legacy = template("legacy-template", ProjectTemplateFamily.LITERATURE_REVIEW).copy(
            examples = listOf(ProjectTemplateExample("legacy-example", "Reviewed historical example.", true)),
        )

        assertEquals(ProjectTemplateExampleKind.UNSPECIFIED, legacy.examples.single().kind)
        assertTrue(ProjectTemplateCatalog.validateReadablePublishedTemplate(legacy).isEmpty())
        assertEquals(
            TemplateSelectionResult.Unavailable("TEMPLATE_NOT_READY"),
            ProjectTemplateCatalog.select(ProjectTemplateCatalogSnapshot(1, listOf(legacy)), legacy.id, legacy.version),
        )
    }

    @Test
    fun `new template missing either scenario kind is not selectable`() {
        val incomplete = template("incomplete-template", ProjectTemplateFamily.LITERATURE_REVIEW).copy(
            examples = listOf(
                ProjectTemplateExample("normal", "Reviewed normal example.", true, ProjectTemplateExampleKind.NORMAL),
                ProjectTemplateExample("unknown", "Old untyped example.", true),
            ),
        )
        val validation = ProjectTemplateCatalog.validate(ProjectTemplateCatalogSnapshot(1, listOf(incomplete)))

        assertTrue(validation.issues.any { it.code == "PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES" })
        assertEquals(
            TemplateSelectionResult.Unavailable("TEMPLATE_NOT_READY"),
            ProjectTemplateCatalog.select(ProjectTemplateCatalogSnapshot(1, listOf(incomplete)), incomplete.id, 1),
        )
    }

    @Test
    fun `selection requires the cataloged template version`() {
        val candidate = template(id = "lab-investigation", family = ProjectTemplateFamily.EXPERIMENTAL_LABORATORY)
        val catalog = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(candidate))

        val selection = ProjectTemplateCatalog.select(catalog, candidate.id, expectedVersion = candidate.version + 1)

        assertEquals(TemplateSelectionResult.Unavailable("TEMPLATE_VERSION_MISMATCH"), selection)
    }

    @Test
    fun `method-specific template can be ready without a hypothesis field`() {
        val candidate = template(
            id = "qualitative-interview",
            family = ProjectTemplateFamily.QUALITATIVE_INTERVIEW_FIELD_STUDY,
        )
        val catalog = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(candidate))

        val selection = ProjectTemplateCatalog.select(catalog, candidate.id, candidate.version)

        assertIs<TemplateSelectionResult.Selected>(selection)
        assertTrue(selection.template.inputFields.none { it.kind == ProjectTemplateInputKind.HYPOTHESIS })
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
        steps = listOf(
            ProjectTemplateStep(
                id = "frame-question",
                title = "Frame the question",
                inputFieldIds = listOf("question"),
            ),
        ),
        methodSpecificLimitations = listOf("Use only for the method described by this template."),
        provenanceRequirements = listOf("Record where sources or data came from."),
        accessibilityExpectations = listOf("Labels and instructions must remain available to assistive technology."),
        examples = listOf(
            ProjectTemplateExample("example-normal", "A synthetic normal example.", reviewedExample, ProjectTemplateExampleKind.NORMAL),
            ProjectTemplateExample(
                "example-edge", "A synthetic edge or conflicting example.", reviewedExample,
                ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
            ),
        ),
        publication = publication,
    )
}
