package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluator
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloEvidenceGraphPresentationTest {
    private val case = ConclusionCases.M0_T2
    private val evaluator = ConclusionEvaluator(case)

    @Test
    fun workspace_trace_projects_requirement_and_selected_evidence_relationship() {
        val draft = validDraft()

        val trace = workspaceTraceFor(case, draft)

        assertEquals(case.id, trace.workspaceId)
        assertEquals(case.title, trace.workspaceTitle)
        assertEquals("AIM-01", trace.requirement?.factId)
        assertEquals(case.fact("AIM-01")?.text, trace.requirement?.text)
        assertEquals(ConclusionRelation.OBSERVED_DIFFERENCE, trace.relation)
        assertEquals(
            listOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
            trace.evidence.filter { it.selected }.map { it.factId },
        )
        assertEquals(
            listOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
            trace.evidence.map { it.factId },
        )
        assertEquals("BOUND-01", trace.boundary?.factId)
    }

    @Test
    fun workspace_trace_uses_only_active_case_evidence_after_the_change() {
        val trace = workspaceTraceFor(
            ConclusionCases.EVIDENCE_CHANGE,
            validChallengeDraft(),
        )

        assertEquals(
            listOf("OBS-WARM-01", "OBS-ROOM-01"),
            trace.evidence.map { it.factId },
        )
        assertFalse(trace.evidence.any { it.factId == "OBS-COLD-01" })
        assertEquals(
            listOf("OBS-WARM-01", "OBS-ROOM-01"),
            trace.evidence.filter { it.selected }.map { it.factId },
        )
    }

    @Test
    fun claim_boundary_reflects_deterministic_status_and_case_boundary() {
        val draft = validDraft()
        val evaluation = evaluator.evaluate(draft)

        val boundary = claimBoundaryFor(case, draft, evaluation)

        assertEquals(ConclusionStatus.PASS, boundary.status)
        assertEquals("BOUND-01", boundary.boundary?.factId)
        assertEquals(draft.scope, boundary.scope)
        assertEquals(draft.limitationRefs, boundary.limitationRefs)
        assertEquals(null, boundary.feedback)
        assertTrue(boundary.claimText.isNotBlank())
    }

    @Test
    fun claim_boundary_preserves_primary_feedback_without_regrading_it() {
        val draft = validDraft().copy(scope = ConclusionScope.GENERAL_CAUSAL_CLAIM)
        val evaluation = evaluator.evaluate(draft)

        val boundary = claimBoundaryFor(case, draft, evaluation)

        assertEquals(ConclusionStatus.ACTION_REQUIRED, boundary.status)
        assertEquals(evaluation.primaryFeedback, boundary.feedback)
        assertEquals("OVERCLAIM_SCOPE", boundary.feedback?.code)
    }

    @Test
    fun evidence_delta_names_removed_observations_and_changed_learner_fields() {
        val before = validDraft()
        val after = before.copy(
            evidenceRefs = listOf("OBS-WARM-01", "OBS-ROOM-01"),
            claimText = before.claimText + " Revised.",
            scope = ConclusionScope.THIS_OBSERVATION,
            limitationRefs = listOf("LIMIT-TRIAL-01"),
            implication = ConclusionImplication.REPEAT_TRIALS,
        )

        val delta = evidenceDeltaFor(before, after)

        assertEquals(listOf("OBS-WARM-01", "OBS-ROOM-01"), delta.retainedEvidenceIds)
        assertEquals(emptyList(), delta.addedEvidenceIds)
        assertEquals(listOf("OBS-COLD-01"), delta.removedEvidenceIds)
        assertTrue(delta.claimChanged)
        assertTrue(delta.scopeChanged)
        assertEquals(emptyList(), delta.addedLimitationIds)
        assertEquals(listOf("LIMIT-STIR-01"), delta.removedLimitationIds)
        assertTrue(delta.implicationChanged)
    }

    private fun validDraft() = ConclusionDraft(
        caseId = case.id,
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
        implicationReason = "Repeat the trials to check whether the pattern persists.",
    )
}
