package dev.nextgen.mobile.domain.practice

import dev.nextgen.mobile.domain.feedback.FeedbackState

data class GuidedRehearsalResult(
    val state: FeedbackState,
    val message: String,
)

/**
 * Checks the smallest premium follow-up: a respectful next step if the
 * requested date is not approved. It does not predict approval or judge the
 * user's reason.
 */
class GuidedRehearsalEngine {
    fun evaluate(contingencyPlan: String): GuidedRehearsalResult =
        if (contingencyPlan.trim().length >= 16) {
            GuidedRehearsalResult(
                state = FeedbackState.PASS,
                message = "The fallback step is concrete enough to rehearse without assuming approval.",
            )
        } else {
            GuidedRehearsalResult(
                state = FeedbackState.NEEDS_WORK,
                message = "Name one respectful next step if the requested date is not approved.",
            )
        }
}
