package dev.nextgen.mobile.domain.conclusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConclusionReducerTest {
    private val reducer = ConclusionReducer()

    @Test
    fun beginCreatesAnEmptyDraftingState() {
        val state = reducer.reduce(ConclusionState.Intro, ConclusionEvent.Begin)

        assertTrue(state is ConclusionState.Drafting)
        assertEquals(ConclusionCases.M0_T2.id, state.draft.caseId)
    }

    @Test
    fun premiumReducerKeepsThePremiumCaseWhenSubmittingAValidDraft() {
        val premiumCase = ConclusionCases.premium.last()
        val reducer = ConclusionReducer(case = premiumCase)
        val observationIds = premiumCase.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }
        val limitationId = premiumCase.requiredLimitationId(ConclusionImplication.REPEAT_TRIALS)
        val draft = ConclusionDraft(
            caseId = premiumCase.id,
            relation = ConclusionRelation.LIMITED_OBSERVATION,
            evidenceRefs = observationIds.take(2),
            claimText = "In this observation, the first condition finished before the second condition.",
            scope = ConclusionScope.THIS_OBSERVATION,
            limitationRefs = listOfNotNull(limitationId),
            limitationNote = "One trial limits how far this comparison can be generalized.",
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "Repeating trials addresses the single-trial limitation.",
        )

        var state: ConclusionState = reducer.reduce(ConclusionState.Intro, ConclusionEvent.Begin)
        state = reducer.reduce(state, ConclusionEvent.UpdateDraft(draft))
        state = reducer.reduce(state, ConclusionEvent.Submit)

        assertTrue(state is ConclusionState.Feedback)
        assertEquals(premiumCase.id, state.draft.caseId)
        assertEquals(null, state.evaluation.primaryFeedback)
    }

    @Test
    fun completeSubmissionCapturesTheInitialDraftAndFeedback() {
        val draft = validDraft()
        var state: ConclusionState = ConclusionState.Intro
        state = reducer.reduce(state, ConclusionEvent.Begin)
        state = reducer.reduce(state, ConclusionEvent.UpdateDraft(draft))
        state = reducer.reduce(state, ConclusionEvent.Submit)

        assertTrue(state is ConclusionState.Feedback)
        assertEquals(draft, state.draft)
        assertEquals(draft, state.initialDraft)
        assertNotNull(state.evaluation)
        assertEquals(null, state.evaluation.primaryFeedback)
        assertTrue(state.canRevise)
    }

    @Test
    fun adv14RevisionPreservationAcceptsExactlyOneRevision() {
        val initial = validDraft()
        val revised = initial.copy(
            scope = ConclusionScope.THIS_OBSERVATION,
            limitationRefs = listOf("LIMIT-TRIAL-01"),
            limitationNote = "One trial limits this observation.",
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "Repeat trials address the single-trial limitation.",
        )
        var state: ConclusionState = ConclusionState.Feedback(
            initialDraft = initial,
            draft = initial,
            evaluation = reducer.evaluate(initial),
        )

        state = reducer.reduce(state, ConclusionEvent.BeginRevision)
        state = reducer.reduce(state, ConclusionEvent.UpdateDraft(revised))
        state = reducer.reduce(state, ConclusionEvent.Submit)

        assertTrue(state is ConclusionState.Summary)
        assertEquals(initial, state.initialDraft)
        assertEquals(revised, state.revisedDraft)
        assertEquals(null, state.finalEvaluation.primaryFeedback)
        assertEquals(state, reducer.reduce(state, ConclusionEvent.BeginRevision))
    }

    @Test
    fun emptySubmissionReturnsAnIncompleteStateWithoutQualityJudgement() {
        var state: ConclusionState = ConclusionState.Intro
        state = reducer.reduce(state, ConclusionEvent.Begin)
        state = reducer.reduce(state, ConclusionEvent.Submit)

        assertTrue(state is ConclusionState.Incomplete)
        assertEquals(ConclusionStatus.INCOMPLETE, state.feedback.status)
        assertEquals(ConclusionPriority.P0, state.feedback.priority)
        assertTrue(state.feedback.message.contains("relation", ignoreCase = true))
    }

    @Test
    fun unsupportedInputRemainsAnAbstentionFeedbackNotAnIncompleteState() {
        val draft = validDraft().copy(evidenceRefs = listOf("OBS-WARM-01", "EXTERNAL-99"))
        var state: ConclusionState = ConclusionState.Intro
        state = reducer.reduce(state, ConclusionEvent.Begin)
        state = reducer.reduce(state, ConclusionEvent.UpdateDraft(draft))
        state = reducer.reduce(state, ConclusionEvent.Submit)

        assertTrue(state is ConclusionState.Feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, state.evaluation.primaryFeedback?.status)
        assertEquals(draft, state.initialDraft)
    }

    @Test
    fun incompleteStateCanRecoverToDraftingWithAnUpdatedDraft() {
        var state: ConclusionState = ConclusionState.Intro
        state = reducer.reduce(state, ConclusionEvent.Begin)
        state = reducer.reduce(state, ConclusionEvent.Submit)
        val draft = validDraft()

        state = reducer.reduce(state, ConclusionEvent.UpdateDraft(draft))

        assertEquals(ConclusionState.Drafting(draft), state)
    }

    @Test
    fun feedbackCannotBeRevisedTwiceOrSubmittedAgain() {
        val initial = validDraft()
        val feedback = ConclusionState.Feedback(
            initialDraft = initial,
            draft = initial,
            evaluation = reducer.evaluate(initial),
        )
        val revision = reducer.reduce(feedback, ConclusionEvent.BeginRevision)
        val duplicateRevision = reducer.reduce(revision, ConclusionEvent.BeginRevision)

        assertEquals(revision, duplicateRevision)
        assertEquals(feedback, reducer.reduce(feedback, ConclusionEvent.Submit))
    }

    @Test
    fun completedBaseSummaryStartsAFreshEvidenceChangeDraft() {
        val base = validDraft()
        val summary = ConclusionState.Summary(
            initialDraft = base,
            revisedDraft = base.copy(scope = ConclusionScope.THIS_OBSERVATION),
            initialEvaluation = reducer.evaluate(base),
            finalEvaluation = reducer.evaluate(base.copy(scope = ConclusionScope.THIS_OBSERVATION)),
        )

        val challenge = reducer.reduce(summary, ConclusionEvent.BeginEvidenceChange)

        assertTrue(challenge is ConclusionState.EvidenceChangeDrafting)
        assertEquals(base.copy(scope = ConclusionScope.THIS_OBSERVATION), challenge.baseDraft)
        assertEquals(ConclusionCases.EVIDENCE_CHANGE.id, challenge.draft.caseId)
        assertTrue(challenge.draft.evidenceRefs.isEmpty())
    }

    @Test
    fun evidenceChangeCannotCarryBaseDraftOrBeRevisedTwice() {
        val base = validDraft()
        val revisedBase = base.copy(scope = ConclusionScope.THIS_OBSERVATION)
        val challengeDraft = validChallengeDraft()
        val summary = ConclusionState.Summary(
            initialDraft = base,
            revisedDraft = revisedBase,
            initialEvaluation = reducer.evaluate(base),
            finalEvaluation = reducer.evaluate(revisedBase),
        )

        var state = reducer.reduce(summary, ConclusionEvent.BeginEvidenceChange)
        state = reducer.reduce(state, ConclusionEvent.UpdateEvidenceChangeDraft(challengeDraft))
        state = reducer.reduce(state, ConclusionEvent.SubmitEvidenceChange)

        assertTrue(state is ConclusionState.EvidenceChangeFeedback)
        assertEquals(revisedBase, state.baseDraft)
        assertEquals(challengeDraft, state.draft)
        assertEquals(null, state.evaluation.primaryFeedback)

        state = reducer.reduce(state, ConclusionEvent.FinishEvidenceChange)
        assertTrue(state is ConclusionState.EvidenceChangeSummary)
        assertEquals(revisedBase, state.baseDraft)
        assertEquals(challengeDraft, state.challengeDraft)
        assertEquals(state, reducer.reduce(state, ConclusionEvent.BeginEvidenceChange))
        assertEquals(state, reducer.reduce(state, ConclusionEvent.SubmitEvidenceChange))
    }

    @Test
    fun incompleteEvidenceChangeRemainsInChallengeEditorAndCanRecover() {
        val base = validDraft()
        val summary = ConclusionState.Summary(
            initialDraft = base,
            revisedDraft = base,
            initialEvaluation = reducer.evaluate(base),
            finalEvaluation = reducer.evaluate(base),
        )

        var state = reducer.reduce(summary, ConclusionEvent.BeginEvidenceChange)
        state = reducer.reduce(state, ConclusionEvent.SubmitEvidenceChange)

        assertTrue(state is ConclusionState.EvidenceChangeDrafting)
        val incompleteState = state as ConclusionState.EvidenceChangeDrafting
        assertTrue(incompleteState.validationMessage != null)

        state = reducer.reduce(incompleteState, ConclusionEvent.UpdateEvidenceChangeDraft(validChallengeDraft()))
        assertEquals(null, (state as ConclusionState.EvidenceChangeDrafting).validationMessage)
    }

    @Test
    fun challengeResetReturnsToIntroWithoutMutatingBaseSummary() {
        val base = validDraft()
        val summary = ConclusionState.Summary(
            initialDraft = base,
            revisedDraft = base,
            initialEvaluation = reducer.evaluate(base),
            finalEvaluation = reducer.evaluate(base),
        )
        val challenge = reducer.reduce(summary, ConclusionEvent.BeginEvidenceChange)

        assertEquals(ConclusionState.Intro, reducer.reduce(challenge, ConclusionEvent.Reset))
        assertEquals(base, summary.revisedDraft)
    }

    @Test
    fun resetAlwaysReturnsToIntroAndDoesNotChangeBillingState() {
        val state = ConclusionState.Summary(
            initialDraft = validDraft(),
            revisedDraft = validDraft().copy(scope = ConclusionScope.THIS_OBSERVATION),
            initialEvaluation = reducer.evaluate(validDraft()),
            finalEvaluation = reducer.evaluate(validDraft()),
        )

        assertEquals(ConclusionState.Intro, reducer.reduce(state, ConclusionEvent.Reset))
    }

    @Test
    fun free_core_can_replay_the_same_case_after_reset_without_a_run_quota() {
        val revised = validDraft().copy(
            scope = ConclusionScope.THIS_OBSERVATION,
            limitationRefs = listOf("LIMIT-TRIAL-01"),
            limitationNote = "One trial limits this observation.",
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "Repeat trials address the single-trial limitation.",
        )

        repeat(3) {
            var state: ConclusionState = reducer.reduce(ConclusionState.Intro, ConclusionEvent.Begin)
            state = reducer.reduce(state, ConclusionEvent.UpdateDraft(validDraft()))
            state = reducer.reduce(state, ConclusionEvent.Submit)
            assertTrue(state is ConclusionState.Feedback)

            state = reducer.reduce(state, ConclusionEvent.BeginRevision)
            state = reducer.reduce(state, ConclusionEvent.UpdateDraft(revised))
            state = reducer.reduce(state, ConclusionEvent.Submit)
            assertTrue(state is ConclusionState.Summary)

            state = reducer.reduce(state, ConclusionEvent.Reset)
            assertEquals(ConclusionState.Intro, state)
        }
    }

    private fun validDraft() = ConclusionDraft(
        caseId = ConclusionCases.M0_T2.id,
        relation = ConclusionRelation.OBSERVED_DIFFERENCE,
        evidenceRefs = listOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
        claimText = "In this observation, the warm sample dissolved faster than the room-temperature and cold samples.",
        scope = ConclusionScope.LIMITED_COMPARISON,
        limitationRefs = listOf("LIMIT-TRIAL-01", "LIMIT-STIR-01"),
        limitationNote = "One trial per condition and unmeasured stirring limit this comparison.",
        implication = ConclusionImplication.CONTROL_STIRRING,
        implicationReason = "Controlling stirring addresses the unmeasured stirring limitation.",
    )

    private fun validChallengeDraft() = ConclusionDraft(
        caseId = ConclusionCases.EVIDENCE_CHANGE.id,
        relation = ConclusionRelation.LIMITED_OBSERVATION,
        evidenceRefs = listOf("OBS-WARM-01", "OBS-ROOM-01"),
        claimText = "In this round, the warm sample dissolved before the room-temperature sample.",
        scope = ConclusionScope.LIMITED_COMPARISON,
        limitationRefs = listOf("LIMIT-TRIAL-01", "LIMIT-STIR-01"),
        limitationNote = "One trial and unmeasured stirring limit this comparison.",
        implication = ConclusionImplication.REPEAT_TRIALS,
        implicationReason = "Repeat the trials to check whether the changed evidence persists.",
    )
}
