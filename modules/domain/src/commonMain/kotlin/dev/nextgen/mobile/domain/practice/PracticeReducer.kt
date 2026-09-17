package dev.nextgen.mobile.domain.practice

import dev.nextgen.mobile.domain.feedback.DeadlineFeedbackEngine
import dev.nextgen.mobile.domain.feedback.FeedbackReport
import dev.nextgen.mobile.domain.model.DeadlineDraft

sealed interface PracticeState {
    data object Start : PracticeState

    data class Drafting(
        val draft: DeadlineDraft,
        val validationMessage: String? = null,
    ) : PracticeState

    data class Feedback(
        val draft: DeadlineDraft,
        val report: FeedbackReport,
    ) : PracticeState

    data class Revision(
        val draft: DeadlineDraft,
        val lastReport: FeedbackReport,
    ) : PracticeState

    data class PremiumLocked(
        val entitlement: String,
        val notice: String? = null,
        val returnTo: PracticeState? = null,
        val offerLabel: String? = null,
    ) : PracticeState {
        val canPurchase: Boolean
            get() = offerLabel != null

        val offerStatusLabel: String
            get() = offerLabel ?: if (notice == null) {
                "Loading the guided rehearsal offer…"
            } else {
                "Guided rehearsal offer unavailable."
            }
    }

    data class PremiumUnlocked(
        val entitlement: String,
        val sourceDraft: DeadlineDraft,
        val contingencyPlan: String = "",
        val result: GuidedRehearsalResult? = null,
    ) : PracticeState

    data class Error(
        val message: String,
        val recoverTo: PracticeState,
    ) : PracticeState
}

sealed interface PracticeEvent {
    data object BeginDraft : PracticeEvent

    data class UpdateDraft(val draft: DeadlineDraft) : PracticeEvent

    data object SubmitDraft : PracticeEvent

    data object BeginRevision : PracticeEvent

    data class RequirePremium(val entitlement: String) : PracticeEvent

    data object PurchaseSucceeded : PracticeEvent

    data object PurchaseCancelled : PracticeEvent

    data class PurchaseFailed(val message: String) : PracticeEvent

    data class PremiumOfferLoaded(val label: String) : PracticeEvent

    data class UpdateContingencyPlan(val plan: String) : PracticeEvent

    data object SubmitContingencyPlan : PracticeEvent

    data object DismissError : PracticeEvent

    data object ResetPractice : PracticeEvent

    data object LeavePremium : PracticeEvent
}

class PracticeReducer(
    private val feedbackEngine: DeadlineFeedbackEngine = DeadlineFeedbackEngine(),
    private val guidedRehearsalEngine: GuidedRehearsalEngine = GuidedRehearsalEngine(),
) {
    fun evaluate(draft: DeadlineDraft): FeedbackReport = feedbackEngine.evaluate(draft)

    fun reduce(state: PracticeState, event: PracticeEvent): PracticeState = when (event) {
        PracticeEvent.BeginDraft ->
            if (state is PracticeState.Start) {
                PracticeState.Drafting(DeadlineDraft())
            } else {
                state
            }

        is PracticeEvent.UpdateDraft -> when (state) {
            is PracticeState.Drafting -> state.copy(
                draft = event.draft,
                validationMessage = null,
            )

            is PracticeState.Revision -> state.copy(draft = event.draft)
            else -> state
        }

        PracticeEvent.SubmitDraft -> when (state) {
            is PracticeState.Drafting -> submit(state.draft, state)
            is PracticeState.Revision -> submit(state.draft, state)
            else -> state
        }

        PracticeEvent.BeginRevision ->
            if (state is PracticeState.Feedback) {
                PracticeState.Revision(state.draft, state.report)
            } else {
                state
            }

        is PracticeEvent.RequirePremium ->
            PracticeState.PremiumLocked(
                entitlement = event.entitlement,
                returnTo = state.takeIf { it is PracticeState.Feedback },
            )

        PracticeEvent.PurchaseSucceeded ->
            if (state is PracticeState.PremiumLocked) {
                PracticeState.PremiumUnlocked(
                    entitlement = state.entitlement,
                    sourceDraft = (state.returnTo as? PracticeState.Feedback)?.draft
                        ?: DeadlineDraft(),
                )
            } else {
                state
            }

        PracticeEvent.PurchaseCancelled ->
            if (state is PracticeState.PremiumLocked) {
                state.copy(notice = "Purchase cancelled; the free flow remains available.")
            } else {
                state
            }

        is PracticeEvent.PurchaseFailed ->
            if (state is PracticeState.PremiumLocked) {
                state.copy(notice = event.message.ifBlank { "Purchase failed; try again later." })
            } else {
                state
            }

        is PracticeEvent.PremiumOfferLoaded ->
            if (state is PracticeState.PremiumLocked) {
                state.copy(offerLabel = event.label)
            } else {
                state
            }

        is PracticeEvent.UpdateContingencyPlan ->
            if (state is PracticeState.PremiumUnlocked) {
                state.copy(contingencyPlan = event.plan, result = null)
            } else {
                state
            }

        PracticeEvent.SubmitContingencyPlan ->
            if (state is PracticeState.PremiumUnlocked) {
                state.copy(result = guidedRehearsalEngine.evaluate(state.contingencyPlan))
            } else {
                state
            }

        PracticeEvent.DismissError ->
            if (state is PracticeState.Error) state.recoverTo else state

        PracticeEvent.ResetPractice -> PracticeState.Start

        PracticeEvent.LeavePremium ->
            if (state is PracticeState.PremiumLocked) {
                state.returnTo ?: PracticeState.Start
            } else {
                state
            }
    }

    private fun submit(draft: DeadlineDraft, recoverTo: PracticeState): PracticeState =
        if (draft.hasAnyContent()) {
            PracticeState.Feedback(draft, evaluate(draft))
        } else {
            PracticeState.Error(
                message = "Complete the required fields before reviewing.",
                recoverTo = when (recoverTo) {
                    is PracticeState.Drafting -> recoverTo.copy(
                        validationMessage = "Complete the required fields before reviewing.",
                    )

                    else -> recoverTo
                },
            )
        }
}

private fun DeadlineDraft.hasAnyContent(): Boolean =
    listOf(
        assignment,
        completedWork,
        requestedNextStep,
        completionPlan,
        policyAcknowledgment,
    ).any { it.isNotBlank() }
