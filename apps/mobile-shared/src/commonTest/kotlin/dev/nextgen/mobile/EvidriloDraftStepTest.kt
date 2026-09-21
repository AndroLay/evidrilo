package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluator
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloDraftStepTest {
    @Test
    fun freshEvidenceChangeDraftStartsWithEvidenceSelection() {
        val baseCase = ConclusionCases.M0_T2
        val baseDraft = ConclusionDraft(caseId = baseCase.id)
        val baseEvaluation = ConclusionEvaluator(baseCase).evaluate(baseDraft)
        val challengeDraft = ConclusionDraft(caseId = ConclusionCases.EVIDENCE_CHANGE.id)
        val state = ConclusionState.EvidenceChangeDrafting(
            baseDraft = baseDraft,
            baseEvaluation = baseEvaluation,
            draft = challengeDraft,
        )

        assertEquals(EvidriloDraftStep.EVIDENCE, initialDraftStepFor(state))
    }

    @Test
    fun revisionStartsAtTheClaimForFocusedEditing() {
        val case = ConclusionCases.M0_T2
        val draft = ConclusionDraft(caseId = case.id)
        val evaluation = ConclusionEvaluator(case).evaluate(draft)
        val state = ConclusionState.Revision(
            initialDraft = draft,
            draft = draft,
            initialEvaluation = evaluation,
        )

        assertEquals(EvidriloDraftStep.CLAIM, initialDraftStepFor(state))
    }
}
