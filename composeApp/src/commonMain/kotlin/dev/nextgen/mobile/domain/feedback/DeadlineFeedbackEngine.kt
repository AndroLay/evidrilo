package dev.nextgen.mobile.domain.feedback

import dev.nextgen.mobile.domain.model.DeadlineDraft

enum class FeedbackState {
    PASS,
    NEEDS_WORK,
    NOT_ENOUGH_CONTEXT,
}

enum class FeedbackCriterion {
    SPECIFIC_TASK,
    CLEAR_REQUEST,
    REALISTIC_PLAN,
    RESPECTFUL_BOUNDARY,
}

data class FeedbackItem(
    val criterion: FeedbackCriterion,
    val state: FeedbackState,
    val message: String,
)

data class FeedbackReport(
    val items: List<FeedbackItem>,
    val overallState: FeedbackState,
) {
    fun itemFor(criterion: FeedbackCriterion): FeedbackItem =
        items.first { it.criterion == criterion }
}

/**
 * Evaluates only observable writing criteria.
 *
 * The engine intentionally avoids keyword heuristics, sentiment scoring, and
 * approval prediction. It gives a positive result only when the relevant
 * field contains enough concrete information for the user to review it.
 */
class DeadlineFeedbackEngine {
    fun evaluate(draft: DeadlineDraft): FeedbackReport {
        val items = listOf(
            evaluateSpecificTask(draft),
            evaluateClearRequest(draft),
            evaluateRealisticPlan(draft),
            evaluateRespectfulBoundary(draft),
        )

        return FeedbackReport(
            items = items,
            overallState = if (items.all { it.state == FeedbackState.PASS }) {
                FeedbackState.PASS
            } else {
                FeedbackState.NEEDS_WORK
            },
        )
    }

    private fun evaluateSpecificTask(draft: DeadlineDraft): FeedbackItem =
        if (hasMeaningfulText(draft.assignment, minimumLength = 3) &&
            hasMeaningfulText(draft.completedWork, minimumLength = 3)
        ) {
            FeedbackItem(
                criterion = FeedbackCriterion.SPECIFIC_TASK,
                state = FeedbackState.PASS,
                message = "The task and completed work are concrete enough to review.",
            )
        } else {
            FeedbackItem(
                criterion = FeedbackCriterion.SPECIFIC_TASK,
                state = FeedbackState.NEEDS_WORK,
                message = "Name the task and what you have already completed.",
            )
        }

    private fun evaluateClearRequest(draft: DeadlineDraft): FeedbackItem =
        if (hasMeaningfulText(draft.requestedNextStep, minimumLength = 5)) {
            FeedbackItem(
                criterion = FeedbackCriterion.CLEAR_REQUEST,
                state = FeedbackState.PASS,
                message = "The requested next step is stated clearly.",
            )
        } else {
            FeedbackItem(
                criterion = FeedbackCriterion.CLEAR_REQUEST,
                state = FeedbackState.NEEDS_WORK,
                message = "State the exact next step or date you are asking for.",
            )
        }

    private fun evaluateRealisticPlan(draft: DeadlineDraft): FeedbackItem =
        if (hasMeaningfulText(draft.completionPlan, minimumLength = 8)) {
            FeedbackItem(
                criterion = FeedbackCriterion.REALISTIC_PLAN,
                state = FeedbackState.PASS,
                message = "The completion plan gives a concrete next action.",
            )
        } else {
            FeedbackItem(
                criterion = FeedbackCriterion.REALISTIC_PLAN,
                state = FeedbackState.NEEDS_WORK,
                message = "Add the next actions and a realistic time boundary.",
            )
        }

    private fun evaluateRespectfulBoundary(draft: DeadlineDraft): FeedbackItem =
        if (hasMeaningfulText(draft.policyAcknowledgment, minimumLength = 8)) {
            FeedbackItem(
                criterion = FeedbackCriterion.RESPECTFUL_BOUNDARY,
                state = FeedbackState.PASS,
                message = "The draft acknowledges the recipient's policy or decision.",
            )
        } else {
            FeedbackItem(
                criterion = FeedbackCriterion.RESPECTFUL_BOUNDARY,
                state = FeedbackState.NEEDS_WORK,
                message = "Acknowledge the relevant policy or that the recipient may decide.",
            )
        }

    private fun hasMeaningfulText(value: String, minimumLength: Int): Boolean =
        value.trim().length >= minimumLength
}
