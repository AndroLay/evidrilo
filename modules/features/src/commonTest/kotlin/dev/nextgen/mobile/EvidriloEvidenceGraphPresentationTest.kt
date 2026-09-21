package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionCheck
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
    fun evidence_lens_labels_each_supplied_observation_without_inventing_support() {
        val lens = evidenceLensFor(case, validDraft())

        assertEquals(case.id, lens.workspaceId)
        assertEquals(3, lens.selectedCount)
        assertEquals(3, lens.observationCount)
        assertEquals(
            listOf(
                EvidriloEvidenceLensSelection.SELECTED,
                EvidriloEvidenceLensSelection.SELECTED,
                EvidriloEvidenceLensSelection.SELECTED,
            ),
            lens.entries.map { it.selection },
        )
        assertEquals(
            listOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
            lens.entries.map { it.factId },
        )
    }

    @Test
    fun evidence_lens_keeps_a_foreign_reference_visible_as_unavailable() {
        val challenge = ConclusionCases.EVIDENCE_CHANGE
        val lens = evidenceLensFor(
            challenge,
            validChallengeDraft().copy(evidenceRefs = listOf("OBS-WARM-01", "OBS-COLD-01")),
        )

        assertEquals(2, lens.observationCount)
        assertEquals(1, lens.selectedCount)
        assertEquals(1, lens.unavailableCount)
        assertEquals(
            listOf(
                EvidriloEvidenceLensSelection.SELECTED,
                EvidriloEvidenceLensSelection.AVAILABLE,
                EvidriloEvidenceLensSelection.UNAVAILABLE,
            ),
            lens.entries.map { it.selection },
        )
        assertEquals("OBS-COLD-01", lens.entries.last().factId)
        assertTrue(lens.entries.last().text.contains("unavailable"))
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
    fun verification_details_expose_all_non_passing_checks_and_primary_feedback() {
        val draft = validDraft().copy(
            scope = ConclusionScope.GENERAL_CAUSAL_CLAIM,
            implication = ConclusionImplication.REPEAT_TRIALS,
            limitationRefs = listOf("LIMIT-STIR-01"),
        )
        val evaluation = evaluator.evaluate(draft)

        val details = verificationDetailsFor(evaluation)

        assertTrue(details.any { it.check == ConclusionCheck.SCOPE_UNCERTAINTY })
        assertTrue(details.any { it.check == ConclusionCheck.ACTIONABLE_IMPLICATION })
        assertTrue(details.any { it.isPrimary })
        assertEquals("OVERCLAIM_SCOPE", details.first { it.isPrimary }.code)
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

    @Test
    fun evidence_delta_propagates_assessment_gap_and_action_state() {
        val before = validDraft()
        val after = before.copy(
            evidenceRefs = listOf("OBS-WARM-01"),
            limitationRefs = listOf("LIMIT-STIR-01"),
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "Repeat the trials to address the missing comparison.",
        )

        val delta = evidenceDeltaFor(case, before, after)

        assertEquals(ConclusionStatus.PASS, delta.beforeAssessment)
        assertEquals(ConclusionStatus.ACTION_REQUIRED, delta.afterAssessment)
        assertTrue(delta.evidenceRelationshipChanged)
        assertTrue(delta.gapChanged)
        assertEquals(EvidriloActionTraceState.STALE, delta.afterActionState)
    }

    @Test
    fun evidence_delta_uses_the_case_version_for_each_side_of_a_challenge() {
        val delta = evidenceDeltaFor(
            beforeCase = ConclusionCases.M0_T2,
            afterCase = ConclusionCases.EVIDENCE_CHANGE,
            before = validDraft(),
            after = validChallengeDraft(),
        )

        assertEquals(ConclusionStatus.PASS, delta.beforeAssessment)
        assertEquals(ConclusionStatus.PASS, delta.afterAssessment)
    }

    @Test
    fun action_trace_explains_a_learner_selected_action_with_its_limitation_anchor() {
        val draft = validDraft()
        val trace = actionTraceFor(case, draft, evaluator.evaluate(draft))

        assertEquals(EvidriloActionOrigin.LEARNER_SELECTED, trace.origin)
        assertEquals(ConclusionImplication.CONTROL_STIRRING, trace.implication)
        assertEquals(EvidriloActionAnchorState.ANCHORED, trace.anchorState)
        assertEquals("LIMIT-STIR-01", trace.anchor?.factId)
        assertTrue(trace.reason.contains("Controlling stirring"))
    }

    @Test
    fun action_trace_marks_a_selected_action_stale_when_its_limitation_is_not_selected() {
        val draft = validDraft().copy(limitationRefs = listOf("LIMIT-TRIAL-01"))
        val trace = actionTraceFor(case, draft, evaluator.evaluate(draft))

        assertEquals(EvidriloActionOrigin.LEARNER_SELECTED, trace.origin)
        assertEquals(EvidriloActionAnchorState.MISSING, trace.anchorState)
        assertEquals("LIMIT-STIR-01", trace.anchor?.factId)
        assertTrue(trace.reason.contains("limitation", ignoreCase = true))
    }

    @Test
    fun action_trace_exposes_a_verification_suggestion_when_no_action_is_selected() {
        val draft = validDraft().copy(implication = null, implicationReason = "")
        val evaluation = evaluator.evaluate(draft)
        val trace = actionTraceFor(case, draft, evaluation)

        assertEquals(EvidriloActionOrigin.SUGGESTED_BY_VERIFICATION, trace.origin)
        assertEquals(null, trace.implication)
        assertEquals(EvidriloActionAnchorState.NOT_APPLICABLE, trace.anchorState)
        assertEquals(null, trace.anchor)
        assertTrue(trace.reason.contains("Choose", ignoreCase = true))
    }

    @Test
    fun action_trace_is_explicitly_empty_before_verification() {
        val draft = ConclusionDraft(caseId = case.id)
        val trace = actionTraceFor(case, draft, null)

        assertEquals(EvidriloActionOrigin.NONE, trace.origin)
        assertEquals(EvidriloActionAnchorState.NOT_APPLICABLE, trace.anchorState)
        assertEquals(null, trace.anchor)
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
