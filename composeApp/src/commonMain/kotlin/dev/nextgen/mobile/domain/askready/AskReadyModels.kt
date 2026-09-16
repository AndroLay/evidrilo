package dev.nextgen.mobile.domain.askready

/**
 * The smallest local-only input needed to rehearse an academic help request.
 * It never stores or infers the correctness of the underlying academic work.
 */
data class AskReadyDraft(
    val specificObject: String = "",
    val context: String = "",
    val attempt: String = "",
    val concreteAsk: String = "",
    val nextStep: String = "",
    val channel: AskReadyChannel? = null,
    val scenario: AskReadyScenario = AskReadyScenario.ASSIGNMENT,
)

enum class AskReadyScenario {
    ASSIGNMENT,
    LAB_CODE,
    CONCEPT_CLARIFICATION,
    ACADEMIC_PROCESS,
}

enum class AskReadyChannel {
    INSTRUCTOR,
    TEACHING_ASSISTANT,
    PEER,
    OFFICE_HOUR,
}

enum class AskReadyFeedbackState {
    PASS,
    NEEDS_WORK,
    NOT_ENOUGH_CONTEXT,
}

enum class AskReadyCriterion {
    SPECIFIC_OBJECT,
    MINIMUM_CONTEXT,
    ATTEMPT,
    CONCRETE_ASK,
    NEXT_STEP,
    PRIVACY_HYGIENE,
}

data class AskReadyFeedbackItem(
    val criterion: AskReadyCriterion,
    val state: AskReadyFeedbackState,
    val message: String,
)

data class AskReadyFeedbackReport(
    val items: List<AskReadyFeedbackItem>,
    val overallState: AskReadyFeedbackState,
) {
    fun itemFor(criterion: AskReadyCriterion): AskReadyFeedbackItem =
        items.first { it.criterion == criterion }
}
