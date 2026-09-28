package dev.nextgen.mobile.domain.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectAiScaffoldRulesTest {
    @Test
    fun `validated suggestions remain a preview until selected and applied`() {
        val template = publishedTemplate()
        val proposal = proposal(template)
        val current = mapOf("question" to "My own question")

        val preview = ProjectAiScaffoldRules.applySelected(
            template = template,
            proposal = proposal,
            currentValues = current,
            expectedBaseRevision = null,
            selectedFieldIds = emptySet(),
            explicitlyReplacedFieldIds = emptySet(),
        )
        val applied = ProjectAiScaffoldRules.applySelected(
            template = template,
            proposal = proposal,
            currentValues = current,
            expectedBaseRevision = null,
            selectedFieldIds = setOf("hypothesis"),
            explicitlyReplacedFieldIds = emptySet(),
        )

        assertEquals(ProjectAiScaffoldApplyResult.NoChanges, preview)
        assertEquals(current, mapOf("question" to "My own question"))
        assertEquals(
            ProjectAiScaffoldApplyResult.Applied(mapOf(
                "question" to "My own question",
                "hypothesis" to "I expect the measured outcome to differ; this needs testing.",
            )),
            applied,
        )
    }

    @Test
    fun `AI cannot invent sources data analysis claims or limitations`() {
        val template = publishedTemplate()

        listOf("source", "data", "analysis", "claim", "limitation").forEach { fieldId ->
            val result = ProjectAiScaffoldRules.validate(
                template,
                proposal(template, listOf(ProjectAiFieldSuggestion(fieldId, "Generated text"))),
                expectedBaseRevision = null,
            )

            assertEquals("PROJECT_AI_FIELD_NOT_ALLOWED", result, fieldId)
        }
    }

    @Test
    fun `unknown template version and stale project revision are rejected`() {
        val template = publishedTemplate()

        assertEquals(
            "PROJECT_AI_TEMPLATE_MISMATCH",
            ProjectAiScaffoldRules.validate(
                template,
                proposal(template).copy(templateVersion = template.version + 1),
                expectedBaseRevision = null,
            ),
        )
        assertEquals(
            "PROJECT_AI_STALE_REVISION",
            ProjectAiScaffoldRules.validate(
                template,
                proposal(template, baseRevision = 4),
                expectedBaseRevision = 5,
            ),
        )
    }

    @Test
    fun `in-project proposal is bound to the exact local project identity`() {
        val template = publishedTemplate()
        val projectId = "11111111-1111-4111-8111-111111111111"
        val proposal = proposal(template, baseRevision = 4, projectId = projectId)

        assertEquals(
            null,
            ProjectAiScaffoldRules.validate(
                template,
                proposal,
                expectedBaseRevision = 4,
                expectedProjectId = projectId,
            ),
        )
        assertEquals(
            "PROJECT_AI_PROJECT_MISMATCH",
            ProjectAiScaffoldRules.validate(
                template,
                proposal,
                expectedBaseRevision = 4,
                expectedProjectId = "22222222-2222-4222-8222-222222222222",
            ),
        )
    }

    @Test
    fun `replacing learner text requires explicit field confirmation`() {
        val template = publishedTemplate()
        val result = ProjectAiScaffoldRules.applySelected(
            template = template,
            proposal = proposal(template),
            currentValues = mapOf("hypothesis" to "My current prediction"),
            expectedBaseRevision = null,
            selectedFieldIds = setOf("hypothesis"),
            explicitlyReplacedFieldIds = emptySet(),
        )

        assertIs<ProjectAiScaffoldApplyResult.Rejected>(result)
        assertEquals("PROJECT_AI_REPLACEMENT_CONFIRMATION_REQUIRED", result.code)
    }

    @Test
    fun `clarification-only response is valid and does not fill missing facts`() {
        val template = publishedTemplate()
        val clarification = proposal(
            template,
            suggestions = emptyList(),
            clarificationQuestions = listOf("What outcome will you measure?")
        )

        assertEquals(
            null,
            ProjectAiScaffoldRules.validate(template, clarification, expectedBaseRevision = null),
        )
        assertEquals(
            ProjectAiScaffoldApplyResult.NoChanges,
            ProjectAiScaffoldRules.applySelected(
                template,
                clarification,
                emptyMap(),
                expectedBaseRevision = null,
                selectedFieldIds = emptySet(),
                explicitlyReplacedFieldIds = emptySet(),
            ),
        )
    }

    @Test
    fun `guidance and next prompts are bounded typed output`() {
        val template = publishedTemplate()
        val valid = proposal(template)

        assertEquals(
            "PROJECT_AI_INVALID_RESPONSE",
            ProjectAiScaffoldRules.validate(
                template,
                valid.copy(guidanceText = " "),
                expectedBaseRevision = null,
            ),
        )
        assertEquals(
            "PROJECT_AI_INVALID_RESPONSE",
            ProjectAiScaffoldRules.validate(
                template,
                valid.copy(recommendedNextPrompts = listOf("x".repeat(ProjectAiScaffoldRules.MAX_NEXT_PROMPT_CHARS + 1))),
                expectedBaseRevision = null,
            ),
        )
    }

    @Test
    fun `student edits are the values saved instead of the raw AI suggestion`() {
        val template = publishedTemplate()
        val result = ProjectAiScaffoldRules.applySelected(
            template = template,
            proposal = proposal(template),
            currentValues = emptyMap(),
            expectedBaseRevision = null,
            selectedFieldIds = setOf("hypothesis"),
            explicitlyReplacedFieldIds = emptySet(),
            editedValues = mapOf("hypothesis" to "My revised prediction, which I will test."),
        )

        assertEquals(
            ProjectAiScaffoldApplyResult.Applied(
                mapOf("hypothesis" to "My revised prediction, which I will test."),
            ),
            result,
        )
    }

    private fun proposal(
        template: ProjectTemplateDefinition,
        suggestions: List<ProjectAiFieldSuggestion> = listOf(
            ProjectAiFieldSuggestion("hypothesis", "I expect the measured outcome to differ; this needs testing."),
        ),
        baseRevision: Int? = null,
        projectId: String? = null,
        clarificationQuestions: List<String> = emptyList(),
    ) = ProjectAiScaffoldProposal(
        templateId = template.id,
        templateVersion = template.version,
        baseProjectRevision = baseRevision,
        promptVersion = "project-scaffold.v1",
        guidanceText = "Check the suggested framing against your assignment instructions.",
        fieldSuggestions = suggestions,
        clarificationQuestions = clarificationQuestions,
        recommendedNextPrompts = listOf("What evidence would I need to answer this question?"),
        projectId = projectId,
    )

    private fun publishedTemplate() = ProjectTemplateDefinition(
        id = "reviewed-template",
        version = 3,
        family = ProjectTemplateFamily.EXPERIMENTAL_LABORATORY,
        title = "Experimental project",
        summary = "A bounded template.",
        intendedOutput = "A student-owned research plan.",
        inputFields = listOf(
            ProjectTemplateInputField("assignment", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "Assignment", true),
            ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
            ProjectTemplateInputField("hypothesis", ProjectTemplateInputKind.HYPOTHESIS, "Hypothesis", false),
            ProjectTemplateInputField("source", ProjectTemplateInputKind.SOURCE, "Source", false),
            ProjectTemplateInputField("data", ProjectTemplateInputKind.DATA, "Data", false),
            ProjectTemplateInputField("analysis", ProjectTemplateInputKind.ANALYSIS, "Analysis", false),
            ProjectTemplateInputField("claim", ProjectTemplateInputKind.CLAIM, "Claim", false),
            ProjectTemplateInputField("limitation", ProjectTemplateInputKind.LIMITATION, "Limitation", false),
            ProjectTemplateInputField("next", ProjectTemplateInputKind.NEXT_ACTION, "Next action", false),
        ),
        steps = listOf(ProjectTemplateStep("frame", "Frame the question", listOf("assignment", "question"))),
        methodSpecificLimitations = listOf("Do not infer causation from a simple difference."),
        provenanceRequirements = listOf("Record source and measurement context."),
        accessibilityExpectations = listOf("Use text labels."),
        examples = listOf(
            ProjectTemplateExample("example-normal", "Reviewed nominal example.", true, ProjectTemplateExampleKind.NORMAL),
            ProjectTemplateExample(
                "example-edge",
                "Reviewed edge or conflicting example.",
                true,
                ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
            ),
        ),
        publication = ProjectTemplatePublication.PUBLISHED,
    )
}
