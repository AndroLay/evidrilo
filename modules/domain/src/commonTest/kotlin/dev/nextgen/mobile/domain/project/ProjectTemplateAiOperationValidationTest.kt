package dev.nextgen.mobile.domain.project

import kotlin.test.Test
import kotlin.test.assertContains

class ProjectTemplateAiOperationValidationTest {
    @Test
    fun `published template rejects an unknown stage AI operation`() {
        val template = publishedTemplate(
            listOf(ProjectTemplateAiOperationCapability(
                id = "invented_everything",
                inputFieldIds = listOf("question"),
                outputFieldIds = listOf("question"),
            )),
        )

        assertContains(ProjectTemplateCatalog.validateReadablePublishedTemplate(template), "INVALID_TEMPLATE_AI_OPERATION")
    }

    @Test
    fun `stage AI operation may use only fields assigned to its step`() {
        val template = publishedTemplate(
            operations = listOf(ProjectTemplateAiOperationCapability(
                id = "summarize_selected_material",
                inputFieldIds = listOf("source"),
                outputFieldIds = listOf("question"),
            )),
            stepFieldIds = listOf("question"),
            fields = listOf(
                ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
                ProjectTemplateInputField("source", ProjectTemplateInputKind.SOURCE, "Source", false),
            ),
        )

        assertContains(
            ProjectTemplateCatalog.validateReadablePublishedTemplate(template),
            "TEMPLATE_AI_OPERATION_FIELD_NOT_IN_STEP",
        )
    }

    @Test
    fun `stage AI operation cannot generate source or data fields`() {
        val template = publishedTemplate(
            operations = listOf(ProjectTemplateAiOperationCapability(
                id = "summarize_selected_material",
                inputFieldIds = listOf("question"),
                outputFieldIds = listOf("source"),
            )),
            stepFieldIds = listOf("question", "source"),
            fields = listOf(
                ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
                ProjectTemplateInputField("source", ProjectTemplateInputKind.SOURCE, "Source", false),
            ),
        )

        assertContains(
            ProjectTemplateCatalog.validateReadablePublishedTemplate(template),
            "TEMPLATE_AI_OPERATION_OUTPUT_NOT_ALLOWED",
        )
    }

    private fun publishedTemplate(
        operations: List<ProjectTemplateAiOperationCapability>,
        stepFieldIds: List<String> = listOf("question"),
        fields: List<ProjectTemplateInputField> = listOf(
            ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
        ),
    ) = ProjectTemplateDefinition(
        id = "review-outline",
        version = 1,
        family = ProjectTemplateFamily.LITERATURE_REVIEW,
        title = "Review outline",
        summary = "Compare selected sources.",
        intendedOutput = "A bounded synthesis.",
        inputFields = fields,
        steps = listOf(ProjectTemplateStep("scope", "Set the scope", stepFieldIds, operations)),
        methodSpecificLimitations = listOf("This template does not judge source quality."),
        provenanceRequirements = listOf("Keep source identifiers with notes."),
        accessibilityExpectations = listOf("Use labeled sections."),
        examples = listOf(
            ProjectTemplateExample("normal", "Nominal structure.", true, ProjectTemplateExampleKind.NORMAL),
            ProjectTemplateExample("edge", "An edge structure.", true, ProjectTemplateExampleKind.EDGE_OR_CONFLICTING),
        ),
        publication = ProjectTemplatePublication.PUBLISHED,
    )
}
