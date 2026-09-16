package dev.nextgen.mobile.domain.askready

import dev.nextgen.mobile.billing.DEFAULT_EVIDRILO_ENTITLEMENT_ID

sealed interface AskReadyPracticeState {
    data object Start : AskReadyPracticeState

    data class Drafting(
        val draft: AskReadyDraft,
        val validationMessage: String? = null,
    ) : AskReadyPracticeState

    data class Feedback(
        val draft: AskReadyDraft,
        val report: AskReadyFeedbackReport,
        val isFinal: Boolean = false,
    ) : AskReadyPracticeState

    data class PremiumLocked(
        val entitlement: String,
        val returnTo: Feedback? = null,
        val notice: String? = null,
        val offerLabel: String? = null,
    ) : AskReadyPracticeState {
        val canPurchase: Boolean
            get() = offerLabel != null

        val offerStatusLabel: String
            get() = offerLabel ?: if (notice == null) {
                "Loading the Practice Pack offer…"
            } else {
                "Practice Pack offer unavailable."
            }
    }

    data class PremiumUnlocked(
        val entitlement: String,
        val sourceDraft: AskReadyDraft,
        val returnTo: Feedback? = null,
        val selectedScenario: AskReadyScenario = AskReadyScenario.LAB_CODE,
    ) : AskReadyPracticeState

    data class Revision(
        val draft: AskReadyDraft,
        val lastReport: AskReadyFeedbackReport,
    ) : AskReadyPracticeState

    data class Error(
        val message: String,
        val recoverTo: AskReadyPracticeState,
    ) : AskReadyPracticeState
}

sealed interface AskReadyPracticeEvent {
    data object BeginDraft : AskReadyPracticeEvent

    data class UpdateDraft(val draft: AskReadyDraft) : AskReadyPracticeEvent

    data object SubmitDraft : AskReadyPracticeEvent

    data object BeginRevision : AskReadyPracticeEvent

    data object RequirePracticePack : AskReadyPracticeEvent

    data object BeginScenario : AskReadyPracticeEvent

    data class PracticePackOfferLoaded(val label: String) : AskReadyPracticeEvent

    data object PurchaseSucceeded : AskReadyPracticeEvent

    data object PurchaseCancelled : AskReadyPracticeEvent

    data class PurchaseFailed(val message: String) : AskReadyPracticeEvent

    data class SelectScenario(val scenario: AskReadyScenario) : AskReadyPracticeEvent

    data object LeavePremium : AskReadyPracticeEvent

    data object DismissError : AskReadyPracticeEvent

    data object ResetPractice : AskReadyPracticeEvent
}

class AskReadyPracticeReducer(
    private val feedbackEngine: AskReadyFeedbackEngine = AskReadyFeedbackEngine(),
) {
    fun evaluate(draft: AskReadyDraft): AskReadyFeedbackReport = feedbackEngine.evaluate(draft)

    fun reduce(
        state: AskReadyPracticeState,
        event: AskReadyPracticeEvent,
    ): AskReadyPracticeState = when (event) {
        AskReadyPracticeEvent.BeginDraft ->
            if (state is AskReadyPracticeState.Start) {
                AskReadyPracticeState.Drafting(AskReadyDraft())
            } else {
                state
            }

        is AskReadyPracticeEvent.UpdateDraft -> when (state) {
            is AskReadyPracticeState.Drafting -> state.copy(
                draft = event.draft,
                validationMessage = null,
            )

            is AskReadyPracticeState.Revision -> state.copy(draft = event.draft)
            else -> state
        }

        AskReadyPracticeEvent.SubmitDraft -> when (state) {
            is AskReadyPracticeState.Drafting -> submit(state.draft, state, isFinal = false)
            is AskReadyPracticeState.Revision -> submit(state.draft, state, isFinal = true)
            else -> state
        }

        AskReadyPracticeEvent.BeginRevision ->
            if (state is AskReadyPracticeState.Feedback && !state.isFinal) {
                AskReadyPracticeState.Revision(state.draft, state.report)
            } else {
                state
            }

        AskReadyPracticeEvent.RequirePracticePack ->
            if (state is AskReadyPracticeState.Feedback) {
                AskReadyPracticeState.PremiumLocked(
                    entitlement = DEFAULT_EVIDRILO_ENTITLEMENT_ID,
                    returnTo = state,
                )
            } else {
                state
            }

        AskReadyPracticeEvent.BeginScenario ->
            if (state is AskReadyPracticeState.PremiumUnlocked) {
                AskReadyPracticeState.Drafting(
                    AskReadyDraft(scenario = state.selectedScenario),
                )
            } else {
                state
            }

        is AskReadyPracticeEvent.PracticePackOfferLoaded ->
            if (state is AskReadyPracticeState.PremiumLocked) {
                state.copy(offerLabel = event.label)
            } else {
                state
            }

        AskReadyPracticeEvent.PurchaseSucceeded ->
            if (state is AskReadyPracticeState.PremiumLocked) {
                AskReadyPracticeState.PremiumUnlocked(
                    entitlement = state.entitlement,
                    sourceDraft = state.returnTo?.draft ?: AskReadyDraft(),
                    returnTo = state.returnTo,
                )
            } else {
                state
            }

        AskReadyPracticeEvent.PurchaseCancelled ->
            if (state is AskReadyPracticeState.PremiumLocked) {
                state.copy(notice = "Purchase cancelled; the free request remains available.")
            } else {
                state
            }

        is AskReadyPracticeEvent.PurchaseFailed ->
            if (state is AskReadyPracticeState.PremiumLocked) {
                state.copy(notice = event.message.ifBlank { "Purchase failed; try again later." })
            } else {
                state
            }

        is AskReadyPracticeEvent.SelectScenario ->
            if (state is AskReadyPracticeState.PremiumUnlocked) {
                state.copy(selectedScenario = event.scenario)
            } else {
                state
            }

        AskReadyPracticeEvent.LeavePremium ->
            if (state is AskReadyPracticeState.PremiumLocked) {
                state.returnTo ?: AskReadyPracticeState.Start
            } else if (state is AskReadyPracticeState.PremiumUnlocked) {
                state.returnTo ?: AskReadyPracticeState.Start
            } else {
                state
            }

        AskReadyPracticeEvent.DismissError ->
            if (state is AskReadyPracticeState.Error) state.recoverTo else state

        AskReadyPracticeEvent.ResetPractice -> AskReadyPracticeState.Start
    }

    private fun submit(
        draft: AskReadyDraft,
        recoverTo: AskReadyPracticeState,
        isFinal: Boolean,
    ): AskReadyPracticeState =
        if (draft.hasAnyContent()) {
            AskReadyPracticeState.Feedback(
                draft = draft,
                report = evaluate(draft),
                isFinal = isFinal,
            )
        } else {
            AskReadyPracticeState.Error(
                message = "Complete the required request fields before reviewing.",
                recoverTo = when (recoverTo) {
                    is AskReadyPracticeState.Drafting -> recoverTo.copy(
                        validationMessage = "Complete the required request fields before reviewing.",
                    )

                    else -> recoverTo
                },
            )
        }
}

private fun AskReadyDraft.hasAnyContent(): Boolean =
    listOf(specificObject, context, attempt, concreteAsk, nextStep).any { it.isNotBlank() } ||
        channel != null
