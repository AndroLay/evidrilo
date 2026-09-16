package dev.nextgen.mobile.domain.feedback

/**
 * User-facing labels are kept separate from enum names so implementation
 * details never leak into the review screen or its accessibility tree.
 */
fun FeedbackCriterion.displayLabel(): String = when (this) {
    FeedbackCriterion.SPECIFIC_TASK -> "Specific task"
    FeedbackCriterion.CLEAR_REQUEST -> "Clear request"
    FeedbackCriterion.REALISTIC_PLAN -> "Realistic plan"
    FeedbackCriterion.RESPECTFUL_BOUNDARY -> "Respectful boundary"
}

fun FeedbackState.displayLabel(): String = when (this) {
    FeedbackState.PASS -> "Pass"
    FeedbackState.NEEDS_WORK -> "Needs work"
    FeedbackState.NOT_ENOUGH_CONTEXT -> "Not enough context"
}
