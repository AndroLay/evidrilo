package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.storage.ConclusionSessionPhase
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloTargetSurfaceModelTest {
    @Test
    fun workspace_metrics_are_derived_from_supplied_case_and_current_draft() {
        val case = ConclusionCases.M0_T2
        val draft = ConclusionDraft(caseId = case.id)

        val metrics = targetWorkspaceMetrics(case, draft)

        assertEquals(3, metrics.evidenceCount)
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
    fun evidence_status_distinguishes_empty_partial_and_supported_claims() {
        val case = ConclusionCases.M0_T2

        assertEquals(
            TargetEvidenceStatus.NOT_ASSESSED,
            targetEvidenceStatus(case, ConclusionDraft(caseId = case.id)),
        )
        assertEquals(
            TargetEvidenceStatus.PARTIALLY_SUPPORTED,
            targetEvidenceStatus(
                case,
                ConclusionDraft(
                    caseId = case.id,
                    evidenceRefs = listOf("OBS-WARM-01"),
                ),
            ),
        )
        assertEquals(
            TargetEvidenceStatus.SUPPORTED,
            targetEvidenceStatus(
                case,
                completeTargetDraft(case),
            ),
        )
    }

    @Test
    fun evidence_delta_only_reports_learner_authored_changes() {
        val before = completeTargetDraft(ConclusionCases.M0_T2)
        val after = before.copy(
            evidenceRefs = before.evidenceRefs.dropLast(1),
            claimText = "The supplied observations show a limited comparison.",
        )

        val delta = targetEvidenceDelta(before, after)

        assertEquals(listOf("OBS-COLD-01"), delta.removedEvidenceIds)
        assertEquals(emptyList(), delta.addedEvidenceIds)
        assertEquals(true, delta.claimChanged)
        assertEquals(true, delta.hasChanges)
    }

    @Test
    fun revision_history_subtitle_exposes_the_single_revision_boundary() {
        val draft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id)
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.REVISION,
            initialDraft = draft,
            currentDraft = draft,
        )

        assertEquals("One revision is ready to review", targetHistorySubtitle(snapshot))
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

    @Test
    fun history_summary_distinguishes_empty_from_saved_comparison() {
        assertEquals("No comparison saved yet", targetHistorySubtitle(null))

        val draft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id)
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.SUMMARY,
            initialDraft = draft,
            currentDraft = draft,
        )

        assertEquals(
            "Review your latest before/after comparison",
            targetHistorySubtitle(snapshot),
        )
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
