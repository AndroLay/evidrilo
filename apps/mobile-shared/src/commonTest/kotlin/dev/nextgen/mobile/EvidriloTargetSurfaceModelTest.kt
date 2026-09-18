package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloTargetSurfaceModelTest {
    @Test
    fun workspace_metrics_are_derived_from_supplied_case_and_current_draft() {
        val case = ConclusionCases.M0_T2
        val draft = ConclusionDraft(caseId = case.id)

        val metrics = targetWorkspaceMetrics(case, draft)

        assertEquals(case.facts.size, metrics.evidenceCount)
        assertEquals(1, metrics.requirementCount)
        assertEquals(1, metrics.gapCount)
        assertEquals(1, metrics.actionCount)
        assertEquals(0, metrics.coveragePercent)
    }

    @Test
    fun workspace_metrics_report_full_coverage_when_the_draft_is_complete() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case)

        val metrics = targetWorkspaceMetrics(case, draft)

        assertEquals(0, metrics.gapCount)
        assertEquals(100, metrics.coveragePercent)
    }

    @Test
    fun target_draft_follows_the_latest_local_state_without_losing_case_identity() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case)

        val resolved = targetDraftFor(
            state = ConclusionState.Incomplete(
                draft = draft,
                feedback = error("missing next action"),
            ),
            case = case,
        )

        assertEquals(draft, resolved)
        assertEquals(case.id, targetDraftFor(ConclusionState.Intro, case).caseId)
    }

    private fun error(message: String) =
        dev.nextgen.mobile.domain.conclusion.ConclusionFeedbackItem(
            code = "TEST",
            status = dev.nextgen.mobile.domain.conclusion.ConclusionStatus.INCOMPLETE,
            priority = dev.nextgen.mobile.domain.conclusion.ConclusionPriority.P0,
            field = dev.nextgen.mobile.domain.conclusion.ConclusionField.CLAIM_TEXT,
            anchorIds = emptyList(),
            message = message,
            why = message,
            nextAction = message,
        )

    private fun completeTargetDraft(case: ConclusionCase): ConclusionDraft =
        ConclusionDraft(
            caseId = case.id,
            relation = ConclusionRelation.OBSERVED_DIFFERENCE,
            evidenceRefs = case.facts.filter { it.type.name == "OBSERVATION" }.map { it.id },
            claimText = "The supplied observations support a bounded conclusion.",
            scope = ConclusionScope.LIMITED_COMPARISON,
            limitationRefs = case.facts.filter { it.type.name == "LIMITATION" }.map { it.id },
            limitationNote = "The supplied case does not establish every real-world condition.",
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "The next action follows from the remaining evidence gap.",
        )
}
