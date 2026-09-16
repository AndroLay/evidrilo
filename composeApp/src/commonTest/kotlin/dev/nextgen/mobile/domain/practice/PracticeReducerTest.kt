package dev.nextgen.mobile.domain.practice

import dev.nextgen.mobile.domain.feedback.FeedbackCriterion
import dev.nextgen.mobile.domain.feedback.FeedbackState
import dev.nextgen.mobile.domain.model.DeadlineDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeReducerTest {
    private val reducer = PracticeReducer()

    @Test
    fun completeDraftMovesFromStartToFeedback() {
        val draft = completeDraft()

        var state: PracticeState = PracticeState.Start
        state = reducer.reduce(state, PracticeEvent.BeginDraft)
        state = reducer.reduce(state, PracticeEvent.UpdateDraft(draft))
        state = reducer.reduce(state, PracticeEvent.SubmitDraft)

        assertTrue(state is PracticeState.Feedback)
        assertEquals(FeedbackState.PASS, state.report.overallState)
    }

    @Test
    fun emptyDraftReturnsRecoverableValidationError() {
        val drafting = PracticeState.Drafting(DeadlineDraft())

        val state = reducer.reduce(drafting, PracticeEvent.SubmitDraft)

        assertTrue(state is PracticeState.Error)
        assertEquals("Complete the required fields before reviewing.", state.message)
        assertTrue(state.recoverTo is PracticeState.Drafting)
    }

    @Test
    fun revisionReevaluatesOnlyTheChangedCriterion() {
        val first = PracticeState.Feedback(
            draft = completeDraft(),
            report = reducer.evaluate(completeDraft()),
        )
        val revision = reducer.reduce(first, PracticeEvent.BeginRevision)
        val changedDraft = completeDraft().copy(requestedNextStep = "")
        val changed = reducer.reduce(revision, PracticeEvent.UpdateDraft(changedDraft))
        val finalState = reducer.reduce(changed, PracticeEvent.SubmitDraft)

        assertTrue(finalState is PracticeState.Feedback)
        val report = finalState.report
        assertEquals(FeedbackState.NEEDS_WORK, report.itemFor(FeedbackCriterion.CLEAR_REQUEST).state)
        assertEquals(FeedbackState.PASS, report.itemFor(FeedbackCriterion.SPECIFIC_TASK).state)
        assertEquals(FeedbackState.PASS, report.itemFor(FeedbackCriterion.REALISTIC_PLAN).state)
        assertEquals(FeedbackState.PASS, report.itemFor(FeedbackCriterion.RESPECTFUL_BOUNDARY).state)
    }

    @Test
    fun premiumPurchaseStatesRemainExplicit() {
        val locked = reducer.reduce(
            PracticeState.Feedback(completeDraft(), reducer.evaluate(completeDraft())),
            PracticeEvent.RequirePremium("guided_rehearsal"),
        )

        assertTrue(locked is PracticeState.PremiumLocked)

        val cancelled = reducer.reduce(locked, PracticeEvent.PurchaseCancelled)
        assertTrue(cancelled is PracticeState.PremiumLocked)
        assertEquals("Purchase cancelled; the free flow remains available.", cancelled.notice)

        val failed = reducer.reduce(
            cancelled,
            PracticeEvent.PurchaseFailed("Store unavailable"),
        )
        assertTrue(failed is PracticeState.PremiumLocked)
        assertEquals("Store unavailable", failed.notice)

        val unlocked = reducer.reduce(failed, PracticeEvent.PurchaseSucceeded)
        assertTrue(unlocked is PracticeState.PremiumUnlocked)
        assertEquals("guided_rehearsal", unlocked.entitlement)
    }

    @Test
    fun successfulPremiumPurchaseOpensGuidedRehearsalForTheOriginalDraft() {
        val feedback = PracticeState.Feedback(completeDraft(), reducer.evaluate(completeDraft()))
        val locked = reducer.reduce(
            feedback,
            PracticeEvent.RequirePremium("guided_rehearsal"),
        )

        val unlocked = reducer.reduce(locked, PracticeEvent.PurchaseSucceeded)

        assertTrue(unlocked is PracticeState.PremiumUnlocked)
        assertEquals(completeDraft(), unlocked.sourceDraft)
        assertEquals("", unlocked.contingencyPlan)
        assertEquals(null, unlocked.result)
    }

    @Test
    fun guidedRehearsalProducesAnObservableResult() {
        var state: PracticeState = PracticeState.PremiumUnlocked(
            entitlement = "guided_rehearsal",
            sourceDraft = completeDraft(),
        )

        state = reducer.reduce(
            state,
            PracticeEvent.UpdateContingencyPlan(
                "Ask whether I can submit the finished sections first and confirm the next step.",
            ),
        )
        state = reducer.reduce(state, PracticeEvent.SubmitContingencyPlan)

        assertTrue(state is PracticeState.PremiumUnlocked)
        assertEquals(FeedbackState.PASS, state.result?.state)
    }

    @Test
    fun guidedRehearsalKeepsAnIncompletePlanVisibleAsNeedsWork() {
        var state: PracticeState = PracticeState.PremiumUnlocked(
            entitlement = "guided_rehearsal",
            sourceDraft = completeDraft(),
        )

        state = reducer.reduce(state, PracticeEvent.UpdateContingencyPlan("ask later"))
        state = reducer.reduce(state, PracticeEvent.SubmitContingencyPlan)

        assertTrue(state is PracticeState.PremiumUnlocked)
        assertEquals(FeedbackState.NEEDS_WORK, state.result?.state)
    }

    @Test
    fun leavingPremiumReturnsToTheFeedbackState() {
        val feedback = PracticeState.Feedback(completeDraft(), reducer.evaluate(completeDraft()))
        val locked = reducer.reduce(feedback, PracticeEvent.RequirePremium("guided_rehearsal"))

        val returned = reducer.reduce(locked, PracticeEvent.LeavePremium)

        assertEquals(feedback, returned)
    }

    @Test
    fun premiumOfferCanBeShownWithoutUnlockingAccess() {
        val locked = reducer.reduce(
            PracticeState.Feedback(completeDraft(), reducer.evaluate(completeDraft())),
            PracticeEvent.RequirePremium("guided_rehearsal"),
        )

        val withOffer = reducer.reduce(
            locked,
            PracticeEvent.PremiumOfferLoaded("Guided rehearsal · $2.99/month"),
        )

        assertTrue(withOffer is PracticeState.PremiumLocked)
        assertEquals("Guided rehearsal · $2.99/month", withOffer.offerLabel)
    }

    @Test
    fun premiumPurchaseIsUnavailableUntilAnOfferIsLoaded() {
        val locked = reducer.reduce(
            PracticeState.Feedback(completeDraft(), reducer.evaluate(completeDraft())),
            PracticeEvent.RequirePremium("guided_rehearsal"),
        )

        assertTrue(locked is PracticeState.PremiumLocked)
        assertFalse(locked.canPurchase)

        val withOffer = reducer.reduce(
            locked,
            PracticeEvent.PremiumOfferLoaded("Guided rehearsal · $2.99/month"),
        )

        assertTrue(withOffer is PracticeState.PremiumLocked)
        assertTrue(withOffer.canPurchase)
    }

    @Test
    fun failedOfferDoesNotKeepShowingLoadingCopy() {
        val locked = reducer.reduce(
            PracticeState.Feedback(completeDraft(), reducer.evaluate(completeDraft())),
            PracticeEvent.RequirePremium("guided_rehearsal"),
        )

        val failed = reducer.reduce(
            locked,
            PracticeEvent.PurchaseFailed("Billing is not configured on this build."),
        )

        assertTrue(failed is PracticeState.PremiumLocked)
        assertEquals("Guided rehearsal offer unavailable.", failed.offerStatusLabel)
    }

    @Test
    fun resetReturnsToStartAndDropsTheCurrentDraft() {
        val feedback = PracticeState.Feedback(completeDraft(), reducer.evaluate(completeDraft()))

        val reset = reducer.reduce(feedback, PracticeEvent.ResetPractice)

        assertEquals(PracticeState.Start, reset)
    }

    private fun completeDraft() = DeadlineDraft(
        assignment = "Research methods report",
        completedWork = "Outline and sources are ready",
        requestedNextStep = "Submit by Monday at 17:00",
        completionPlan = "Finish analysis Saturday and proofread Sunday",
        policyAcknowledgment = "I understand the course policy and your decision is final",
    )
}
