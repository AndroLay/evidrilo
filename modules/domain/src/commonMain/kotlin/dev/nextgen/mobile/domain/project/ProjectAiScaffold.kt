package dev.nextgen.mobile.domain.project

data class ProjectAiFieldSuggestion(
    val fieldId: String,
    val suggestedValue: String,
)

enum class ProjectAiScaffoldOperation {
    CREATE_PROJECT,
    ASSIST_PROJECT,
}

/**
 * An untrusted, version-bound proposal. Receiving one never mutates or saves a
 * project; the student must explicitly choose fields before applying it.
 */
data class ProjectAiScaffoldProposal(
    val templateId: String,
    val templateVersion: Int,
    val baseProjectRevision: Int?,
    val promptVersion: String,
    val guidanceText: String,
    val fieldSuggestions: List<ProjectAiFieldSuggestion>,
    val clarificationQuestions: List<String>,
    val recommendedNextPrompts: List<String>,
    val projectId: String? = null,
)

sealed interface ProjectAiScaffoldApplyResult {
    data class Applied(val fieldValues: Map<String, String>) : ProjectAiScaffoldApplyResult
    data class Rejected(val code: String) : ProjectAiScaffoldApplyResult
    data object NoChanges : ProjectAiScaffoldApplyResult
}

/**
 * Validates the boundary of AI-produced project scaffolds. AI may help frame
 * the student's task, question, hypothesis, or next action, but may not fill
 * source, data, analysis, claim, or limitation fields as if they were facts.
 */
object ProjectAiScaffoldRules {
    const val PROMPT_VERSION = "project-scaffold.v1"
    const val PROJECT_DATA_CONSENT_VERSION = "project-ai-data.v1"
    const val MAX_SUGGESTIONS = 32
    const val MAX_QUESTIONS = 8
    const val MAX_QUESTION_CHARS = 400
    const val MAX_GUIDANCE_CHARS = 2_500
    const val MAX_NEXT_PROMPTS = 6
    const val MAX_NEXT_PROMPT_CHARS = 240
    const val MAX_SUGGESTION_CHARS = 8_000

    fun validate(
        template: ProjectTemplateDefinition,
        proposal: ProjectAiScaffoldProposal,
        expectedBaseRevision: Int?,
        expectedProjectId: String? = null,
    ): String? {
        if (template.publication != ProjectTemplatePublication.PUBLISHED) return "TEMPLATE_NOT_READY"
        val templateSelection = ProjectTemplateCatalog.select(
            ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(template)),
            template.id,
            template.version,
        )
        if (templateSelection !is TemplateSelectionResult.Selected) return "TEMPLATE_NOT_READY"
        if (proposal.templateId != template.id || proposal.templateVersion != template.version) {
            return "PROJECT_AI_TEMPLATE_MISMATCH"
        }
        if (proposal.baseProjectRevision != expectedBaseRevision
            || expectedBaseRevision != null && expectedBaseRevision < 1
        ) {
            return "PROJECT_AI_STALE_REVISION"
        }
        if (proposal.projectId != expectedProjectId) return "PROJECT_AI_PROJECT_MISMATCH"
        if (proposal.promptVersion != PROMPT_VERSION
            || proposal.guidanceText.isBlank()
            || proposal.guidanceText.length > MAX_GUIDANCE_CHARS
            || proposal.fieldSuggestions.size > MAX_SUGGESTIONS
            || proposal.clarificationQuestions.size > MAX_QUESTIONS
            || proposal.recommendedNextPrompts.size > MAX_NEXT_PROMPTS
        ) {
            return "PROJECT_AI_INVALID_RESPONSE"
        }

        val fieldsById = template.inputFields.associateBy(ProjectTemplateInputField::id)
        val seenIds = mutableSetOf<String>()
        proposal.fieldSuggestions.forEach { suggestion ->
            val field = fieldsById[suggestion.fieldId]
                ?: return "PROJECT_AI_UNKNOWN_FIELD"
            if (!canSuggest(field.kind)) return "PROJECT_AI_FIELD_NOT_ALLOWED"
            if (!seenIds.add(suggestion.fieldId)
                || suggestion.suggestedValue.isBlank()
                || suggestion.suggestedValue.length > MAX_SUGGESTION_CHARS
                || '\u0000' in suggestion.suggestedValue
            ) {
                return "PROJECT_AI_INVALID_RESPONSE"
            }
        }
        if (proposal.clarificationQuestions.any { question ->
                question.isBlank() || question.length > MAX_QUESTION_CHARS || '\u0000' in question
            }
        ) {
            return "PROJECT_AI_INVALID_RESPONSE"
        }
        if (proposal.recommendedNextPrompts.any { prompt ->
                prompt.isBlank() || prompt.length > MAX_NEXT_PROMPT_CHARS || '\u0000' in prompt
            }
        ) {
            return "PROJECT_AI_INVALID_RESPONSE"
        }
        return null
    }

    fun applySelected(
        template: ProjectTemplateDefinition,
        proposal: ProjectAiScaffoldProposal,
        currentValues: Map<String, String>,
        expectedBaseRevision: Int?,
        selectedFieldIds: Set<String>,
        explicitlyReplacedFieldIds: Set<String>,
        expectedProjectId: String? = null,
        editedValues: Map<String, String> = emptyMap(),
    ): ProjectAiScaffoldApplyResult {
        val validation = validate(template, proposal, expectedBaseRevision, expectedProjectId)
        if (validation != null) return ProjectAiScaffoldApplyResult.Rejected(validation)

        val suggestions = proposal.fieldSuggestions.associateBy(ProjectAiFieldSuggestion::fieldId)
        if (!suggestions.keys.containsAll(selectedFieldIds)
            || !selectedFieldIds.containsAll(explicitlyReplacedFieldIds)
            || !selectedFieldIds.containsAll(editedValues.keys)
            || editedValues.values.any { value ->
                value.isBlank() || value.length > MAX_SUGGESTION_CHARS || '\u0000' in value
            }
        ) {
            return ProjectAiScaffoldApplyResult.Rejected("PROJECT_AI_SELECTION_INVALID")
        }

        val updated = currentValues.toMutableMap()
        selectedFieldIds.forEach { fieldId ->
            val suggestion = suggestions.getValue(fieldId)
            val current = currentValues[fieldId].orEmpty()
            val chosenValue = editedValues[fieldId] ?: suggestion.suggestedValue
            if (current.isNotBlank()
                && current != chosenValue
                && fieldId !in explicitlyReplacedFieldIds
            ) {
                return ProjectAiScaffoldApplyResult.Rejected(
                    "PROJECT_AI_REPLACEMENT_CONFIRMATION_REQUIRED",
                )
            }
            updated[fieldId] = chosenValue
        }

        return if (updated == currentValues) {
            ProjectAiScaffoldApplyResult.NoChanges
        } else {
            ProjectAiScaffoldApplyResult.Applied(updated.toMap())
        }
    }

    private fun canSuggest(kind: ProjectTemplateInputKind): Boolean = when (kind) {
        ProjectTemplateInputKind.ASSIGNMENT_BRIEF,
        ProjectTemplateInputKind.RESEARCH_QUESTION,
        ProjectTemplateInputKind.HYPOTHESIS,
        ProjectTemplateInputKind.NEXT_ACTION,
        -> true

        ProjectTemplateInputKind.SOURCE,
        ProjectTemplateInputKind.DATA,
        ProjectTemplateInputKind.ANALYSIS,
        ProjectTemplateInputKind.CLAIM,
        ProjectTemplateInputKind.LIMITATION,
        -> false
    }
}
