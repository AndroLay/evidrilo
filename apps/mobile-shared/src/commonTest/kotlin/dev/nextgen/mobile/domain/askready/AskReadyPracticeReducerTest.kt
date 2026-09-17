package dev.nextgen.mobile.domain.askready

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskReadyPracticeReducerTest {
    private val reducer = AskReadyPracticeReducer()

    @Test
    fun completeDraftMovesFromDraftingToFeedback() {
        var state: AskReadyPracticeState = AskReadyPracticeState.Start
        state = reducer.reduce(state, AskReadyPracticeEvent.BeginDraft)
        state = reducer.reduce(state, AskReadyPracticeEvent.UpdateDraft(completeDraft()))
        state = reducer.reduce(state, AskReadyPracticeEvent.SubmitDraft)

        assertTrue(state is AskReadyPracticeState.Feedback)
        assertEquals(false, state.isFinal)
        assertEquals(AskReadyFeedbackState.PASS, state.report.overallState)
    }

    @Test
    fun oneRevisionProducesFinalFeedbackWithoutLosingTheDraft() {
        var state: AskReadyPracticeState = AskReadyPracticeState.Feedback(
            draft = completeDraft(),
            report = reducer.evaluate(completeDraft()),
        )
        state = reducer.reduce(state, AskReadyPracticeEvent.BeginRevision)
        val revised = completeDraft().copy(concreteAsk = "Could you clarify the final step?")
        state = reducer.reduce(state, AskReadyPracticeEvent.UpdateDraft(revised))
        state = reducer.reduce(state, AskReadyPracticeEvent.SubmitDraft)

        assertTrue(state is AskReadyPracticeState.Feedback)
        assertEquals(true, state.isFinal)
        assertEquals(revised, state.draft)
    }

    @Test
    fun emptySubmissionReturnsAnExplicitRecoverableError() {
        var state: AskReadyPracticeState = AskReadyPracticeState.Start
        state = reducer.reduce(state, AskReadyPracticeEvent.BeginDraft)
        state = reducer.reduce(state, AskReadyPracticeEvent.SubmitDraft)

        assertTrue(state is AskReadyPracticeState.Error)
        assertTrue(state.message.contains("required", ignoreCase = true))
        assertTrue(state.recoverTo is AskReadyPracticeState.Drafting)
    }

    @Test
    fun finalFeedbackCannotStartAnotherRevision() {
        val final = AskReadyPracticeState.Feedback(
            draft = completeDraft(),
            report = reducer.evaluate(completeDraft()),
            isFinal = true,
        )

        assertEquals(final, reducer.reduce(final, AskReadyPracticeEvent.BeginRevision))
    }

    @Test
    fun practicePackUnlocksAdditionalBoundedScenarios() {
        val feedback = AskReadyPracticeState.Feedback(
            draft = completeDraft(),
            report = reducer.evaluate(completeDraft()),
        )
        var state = reducer.reduce(feedback, AskReadyPracticeEvent.RequirePracticePack)
        state = reducer.reduce(
            state,
            AskReadyPracticeEvent.PracticePackOfferLoaded("Practice Pack · one-time purchase"),
        )
        state = reducer.reduce(state, AskReadyPracticeEvent.PurchaseSucceeded)
        state = reducer.reduce(
            state,
            AskReadyPracticeEvent.SelectScenario(AskReadyScenario.LAB_CODE),
        )
        state = reducer.reduce(state, AskReadyPracticeEvent.BeginScenario)

        assertTrue(state is AskReadyPracticeState.Drafting)
        val drafting = state as AskReadyPracticeState.Drafting
        assertEquals(AskReadyScenario.LAB_CODE, drafting.draft.scenario)
    }

    @Test
    fun leavingUnlockedPracticePackReturnsToTheOriginalFeedback() {
        val feedback = AskReadyPracticeState.Feedback(
            draft = completeDraft(),
            report = reducer.evaluate(completeDraft()),
        )
        val locked = reducer.reduce(feedback, AskReadyPracticeEvent.RequirePracticePack)
        val unlocked = reducer.reduce(locked, AskReadyPracticeEvent.PurchaseSucceeded)

        val returned = reducer.reduce(unlocked, AskReadyPracticeEvent.LeavePremium)

        assertEquals(feedback, returned)
    }

    private fun completeDraft() = AskReadyDraft(
        specificObject = "Lab 3 sorting function",
        context = "I am stuck on the failing test in the sorting function.",
        attempt = "I compared the loop bounds and ran the provided test case.",
        concreteAsk = "Could you point out which invariant I should check next?",
        nextStep = "A short hint by email would help before office hours.",
        channel = AskReadyChannel.INSTRUCTOR,
    )
}
