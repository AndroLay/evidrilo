package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectTemplateDetailEligibilityTest {
    @Test
    fun legacy_published_detail_remains_readable_but_is_not_offered_as_a_new_project() {
        assertFalse(isProjectTemplateSelectable(template(examples = listOf(
            ProjectTemplateExample("legacy-example", "Historical example", reviewed = true),
        ))))
    }

    @Test
    fun template_with_reviewed_normal_and_edge_examples_can_start_a_project() {
        assertTrue(isProjectTemplateSelectable(template(examples = listOf(
            ProjectTemplateExample(
                "normal-example",
                "Nominal scenario",
                reviewed = true,
                kind = ProjectTemplateExampleKind.NORMAL,
            ),
            ProjectTemplateExample(
                "edge-example",
                "Edge scenario",
                reviewed = true,
                kind = ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
            ),
        ))))
    }

    private fun template(examples: List<ProjectTemplateExample>) = ProjectTemplateDefinition(
        id = "literature-synthesis",
        version = 1,
        family = ProjectTemplateFamily.LITERATURE_REVIEW,
        title = "Literature synthesis",
        summary = "Organize a bounded synthesis.",
        intendedOutput = "A traceable synthesis.",
        inputFields = listOf(
            ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
        ),
        steps = listOf(ProjectTemplateStep("scope", "Define scope", listOf("question"))),
        methodSpecificLimitations = listOf("Source quality needs human judgment."),
        provenanceRequirements = listOf("Record source identifiers."),
        accessibilityExpectations = listOf("Provide text alternatives."),
        examples = examples,
        publication = ProjectTemplatePublication.PUBLISHED,
    )
}
