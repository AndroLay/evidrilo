package dev.nextgen.mobile

import androidx.compose.ui.graphics.Color
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCheck
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionReducer
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import dev.nextgen.mobile.navigation.EvidriloDestination
import dev.nextgen.mobile.navigation.EvidriloNavigationState
import dev.nextgen.mobile.storage.ConclusionSessionPhase
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import androidx.compose.ui.unit.dp

class EvidriloTargetSurfaceModelTest {
    @Test
    fun selecting_a_root_tab_replaces_the_old_root_without_accumulating_tabs() {
        val selected = EvidriloNavigationState(
            stack = listOf(
                EvidriloDestination.HOME,
                EvidriloDestination.WORKSPACE,
                EvidriloDestination.EVIDENCE,
            ),
        ).selectRoot(EvidriloDestination.ACTION)

        assertEquals(
            listOf(EvidriloDestination.HOME, EvidriloDestination.ACTION),
            selected.stack,
        )
    }

    @Test
    fun selecting_the_current_root_tab_does_not_change_the_stack() {
        val current = EvidriloNavigationState(
            stack = listOf(EvidriloDestination.HOME, EvidriloDestination.SOURCES),
        )

        assertEquals(current, current.selectRoot(EvidriloDestination.SOURCES))
    }

    @Test
    fun target_page_state_has_truthful_labels_for_non_content_states() {
        assertEquals("Loading", TargetPageState.LOADING.targetDisplayLabel())
        assertEquals("Not assessed", TargetPageState.NOT_ASSESSED.targetDisplayLabel())
        assertEquals("Nothing here yet", TargetPageState.EMPTY.targetDisplayLabel())
        assertEquals("Offline", TargetPageState.OFFLINE.targetDisplayLabel())
        assertEquals("Unavailable", TargetPageState.UNAVAILABLE.targetDisplayLabel())
        assertEquals("Something went wrong", TargetPageState.ERROR.targetDisplayLabel())
        assertEquals("Action unavailable", TargetPageState.DISABLED.targetDisplayLabel())
        assertEquals("Recovery needed", TargetPageState.RECOVERY.targetDisplayLabel())
    }

    @Test
    fun target_visual_palette_keeps_the_reference_blue_atmosphere() {
        assertEquals(Color(0xFFEFF6FF), EvidriloColors.Atmosphere)
        assertEquals(Color(0xFFBFD7FF), EvidriloColors.PatternBlue)
        assertEquals(Color(0xFF2E7BFF), EvidriloColors.PatternCobalt)
    }

    @Test
    fun target_visual_layout_keeps_the_reference_scale_and_spacing() {
        assertEquals(50.dp, EvidriloTargetLayout.BrandLogoSize)
        assertEquals(52.dp, EvidriloTargetLayout.SourcesLogoSize)
        assertEquals(158.dp, EvidriloTargetLayout.SourcesGraphicHeight)
        assertEquals(4.dp, EvidriloTargetLayout.NavigationVisualOffset)
        assertEquals(28.dp, EvidriloTargetLayout.ContentTopPadding)
    }

    @Test
    fun target_shell_uses_the_five_current_navigation_labels() {
        assertEquals("Home", targetSectionLabel(EvidriloTargetSection.HOME))
        assertEquals("Sources", targetSectionLabel(EvidriloTargetSection.SOURCES))
        assertEquals("Evidence", targetSectionLabel(EvidriloTargetSection.EVIDENCE))
        assertEquals("Action", targetSectionLabel(EvidriloTargetSection.ACTION))
        assertEquals("Profile", targetSectionLabel(EvidriloTargetSection.PROFILE))
    }

    @Test
    fun target_root_navigation_matches_the_reference_composition() {
        assertEquals(
            listOf(
                EvidriloTargetSection.HOME,
                EvidriloTargetSection.SOURCES,
                EvidriloTargetSection.EVIDENCE,
                EvidriloTargetSection.ACTION,
            ),
            targetNavigationSections(EvidriloTargetSection.HOME),
        )
        assertEquals(
            listOf(
                EvidriloTargetSection.HOME,
                EvidriloTargetSection.SOURCES,
                EvidriloTargetSection.EVIDENCE,
                EvidriloTargetSection.ACTION,
                EvidriloTargetSection.PROFILE,
            ),
            targetNavigationSections(EvidriloTargetSection.PROFILE),
        )
    }

    @Test
    fun target_status_copy_does_not_turn_unknown_support_into_a_gap() {
        assertEquals("Not assessed", TargetEvidenceStatus.NOT_ASSESSED.targetDisplayLabel())
        assertEquals("Partially supported", TargetEvidenceStatus.PARTIALLY_SUPPORTED.targetDisplayLabel())
        assertEquals("Supported", TargetEvidenceStatus.SUPPORTED.targetDisplayLabel())
        assertEquals("Unavailable", TargetEvidenceStatus.UNAVAILABLE.targetDisplayLabel())
        assertEquals("Cannot assess", TargetEvidenceStatus.CANNOT_ASSESS.targetDisplayLabel())
    }

    @Test
    fun target_history_summary_counts_only_real_local_draft_changes() {
        val initial = completeTargetDraft(ConclusionCases.M0_T2)
        val current = initial.copy(
            evidenceRefs = initial.evidenceRefs.dropLast(1),
            implication = null,
            implicationReason = "",
        )
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.SUMMARY,
            initialDraft = initial,
            currentDraft = current,
        )

        assertEquals(
            TargetHistorySummary(
                evidenceAdded = 0,
                evidenceRemoved = 1,
                actionsChanged = 1,
                hasComparison = true,
            ),
            targetHistorySummary(snapshot),
        )
        assertEquals(TargetHistorySummary.EMPTY, targetHistorySummary(null))
    }

    @Test
    fun target_journey_names_the_complete_offline_evidence_loop() {
        assertEquals(
            listOf(
                EvidriloDestination.HOME,
                EvidriloDestination.SOURCES,
                EvidriloDestination.WORKSPACE,
                EvidriloDestination.EVIDENCE,
                EvidriloDestination.EVIDENCE_LENS,
                EvidriloDestination.CLAIM_TRACE,
                EvidriloDestination.CLAIM_BOUNDARY,
                EvidriloDestination.ACTION,
                EvidriloDestination.VERIFY_CLAIM,
                EvidriloDestination.PRACTICE,
                EvidriloDestination.EVIDENCE_DELTA,
                EvidriloDestination.HISTORY,
            ),
            targetJourneyDestinations(),
        )
    }

    @Test
    fun workspace_metrics_are_derived_from_supplied_case_and_current_draft() {
        val case = ConclusionCases.M0_T2
        val draft = ConclusionDraft(caseId = case.id)

        val metrics = targetWorkspaceMetrics(case, draft)

        assertEquals(3, metrics.evidenceCount)
        assertEquals(0, metrics.selectedEvidenceCount)
        assertEquals(1, metrics.requirementCount)
        assertNull(metrics.gapCount)
        assertEquals(0, metrics.actionCount)
        assertEquals(0, metrics.draftCompletenessPercent)
        assertEquals(TargetEvidenceStatus.NOT_ASSESSED, metrics.evidenceStatus)
    }

    @Test
    fun workspace_metrics_report_full_draft_completeness_when_the_draft_is_complete() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case)

        val metrics = targetWorkspaceMetrics(case, draft)

        assertEquals(0, metrics.gapCount)
        assertEquals(3, metrics.selectedEvidenceCount)
        assertEquals(1, metrics.actionCount)
        assertEquals(100, metrics.draftCompletenessPercent)
        assertEquals(TargetEvidenceStatus.SUPPORTED, metrics.evidenceStatus)
    }

    @Test
    fun workspace_metrics_do_not_claim_an_open_gap_before_the_support_checks_run() {
        val case = ConclusionCases.M0_T2
        val draft = ConclusionDraft(
            caseId = case.id,
            evidenceRefs = listOf("OBS-WARM-01"),
            claimText = "The selected observation supports a bounded comparison.",
        )

        val metrics = targetWorkspaceMetrics(case, draft)

        assertEquals(40, metrics.draftCompletenessPercent)
        assertEquals(1, metrics.selectedEvidenceCount)
        assertEquals(0, metrics.actionCount)
        assertEquals(TargetEvidenceStatus.NOT_ASSESSED, metrics.evidenceStatus)
        assertNull(metrics.gapCount)
    }

    @Test
    fun workspace_gap_count_stays_unknown_without_an_assessable_support_result() {
        val case = ConclusionCases.M0_T2
        val challenge = ConclusionCases.EVIDENCE_CHANGE
        val incompleteDraft = ConclusionDraft(
            caseId = case.id,
            evidenceRefs = listOf("OBS-WARM-01"),
        )
        val unavailableDraft = ConclusionDraft(
            caseId = challenge.id,
            evidenceRefs = listOf("OBS-COLD-01"),
        )
        val abstainedDraft = completeTargetDraft(case).copy(
            claimText = "The supplied observations prove 999 seconds in every condition.",
        )

        assertNull(targetWorkspaceMetrics(case, ConclusionDraft(caseId = case.id)).gapCount)
        assertNull(targetWorkspaceMetrics(case, incompleteDraft).gapCount)
        assertNull(targetWorkspaceMetrics(challenge, unavailableDraft).gapCount)
        assertNull(targetWorkspaceMetrics(case, abstainedDraft).gapCount)
    }

    @Test
    fun incomplete_draft_with_selected_evidence_is_not_assessed_not_partially_supported() {
        val case = ConclusionCases.M0_T2
        val draft = ConclusionDraft(
            caseId = case.id,
            evidenceRefs = listOf("OBS-WARM-01"),
        )

        assertEquals(
            ConclusionStatus.INCOMPLETE,
            ConclusionReducer(case = case).evaluate(draft).primaryFeedback?.status,
        )
        assertEquals(TargetEvidenceStatus.NOT_ASSESSED, targetEvidenceStatus(case, draft))
    }

    @Test
    fun actionability_issue_does_not_downgrade_supported_evidence_or_create_a_support_gap() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case).copy(
            limitationRefs = listOf("LIMIT-STIR-01"),
        )
        val evaluation = ConclusionReducer(case = case).evaluate(draft)

        assertEquals(
            ConclusionStatus.ACTION_REQUIRED,
            evaluation.checks.first { it.check == ConclusionCheck.ACTIONABLE_IMPLICATION }.status,
        )
        assertEquals(TargetEvidenceStatus.SUPPORTED, targetEvidenceStatus(case, draft))
        assertEquals(0, targetWorkspaceMetrics(case, draft).gapCount)
    }

    @Test
    fun incomplete_action_fields_do_not_block_independent_evidence_support_assessment() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case).copy(
            implication = null,
            implicationReason = "",
        )

        assertEquals(
            ConclusionStatus.INCOMPLETE,
            ConclusionReducer(case = case).evaluate(draft).primaryFeedback?.status,
        )
        assertEquals(TargetEvidenceStatus.SUPPORTED, targetEvidenceStatus(case, draft))
        assertEquals(0, targetWorkspaceMetrics(case, draft).gapCount)
    }

    @Test
    fun unsupported_action_enum_does_not_block_independent_evidence_support_assessment() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case).copy(
            implication = ConclusionImplication.UNSUPPORTED,
        )

        assertEquals(
            ConclusionStatus.CANNOT_ASSESS,
            ConclusionReducer(case = case).evaluate(draft).primaryFeedback?.status,
        )
        assertEquals(TargetEvidenceStatus.SUPPORTED, targetEvidenceStatus(case, draft))
        assertEquals(0, targetWorkspaceMetrics(case, draft).gapCount)
    }

    @Test
    fun duplicate_evidence_references_over_raw_limit_do_not_hide_the_partial_support_gap() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case).copy(
            evidenceRefs = List(4) { "OBS-WARM-01" },
        )

        val metrics = targetWorkspaceMetrics(case, draft)

        assertEquals(1, metrics.selectedEvidenceCount)
        assertEquals(TargetEvidenceStatus.PARTIALLY_SUPPORTED, metrics.evidenceStatus)
        assertEquals(1, metrics.gapCount)
    }

    @Test
    fun selected_observation_count_uses_distinct_fact_ids() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case).copy(
            evidenceRefs = listOf("OBS-WARM-01", "OBS-WARM-01"),
        )

        assertEquals(1, targetWorkspaceMetrics(case, draft).selectedEvidenceCount)
    }

    @Test
    fun evidence_status_distinguishes_empty_partial_and_supported_claims() {
        val case = ConclusionCases.M0_T2
        val partialDraft = completeTargetDraft(case).copy(
            evidenceRefs = listOf("OBS-WARM-01"),
        )

        assertEquals(
            TargetEvidenceStatus.NOT_ASSESSED,
            targetEvidenceStatus(case, ConclusionDraft(caseId = case.id)),
        )
        assertEquals(
            TargetEvidenceStatus.PARTIALLY_SUPPORTED,
            targetEvidenceStatus(case, partialDraft),
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
    fun evidence_status_marks_a_foreign_reference_as_unavailable() {
        val challenge = ConclusionCases.EVIDENCE_CHANGE
        val draft = ConclusionDraft(
            caseId = challenge.id,
            evidenceRefs = listOf("OBS-COLD-01"),
        )

        assertEquals(TargetEvidenceStatus.UNAVAILABLE, targetEvidenceStatus(challenge, draft))
    }

    @Test
    fun present_non_observation_fact_is_invalid_input_not_an_unavailable_reference() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case).copy(
            evidenceRefs = listOf("LIMIT-TRIAL-01"),
        )

        assertEquals(
            ConclusionStatus.CANNOT_ASSESS,
            ConclusionReducer(case = case).evaluate(draft).primaryFeedback?.status,
        )
        assertEquals(TargetEvidenceStatus.CANNOT_ASSESS, targetEvidenceStatus(case, draft))
        assertNull(targetWorkspaceMetrics(case, draft).gapCount)
    }

    @Test
    fun evidence_status_preserves_evaluator_abstention() {
        val case = ConclusionCases.M0_T2
        val draft = completeTargetDraft(case).copy(
            claimText = "The supplied observations prove 999 seconds in every condition.",
        )

        assertEquals(TargetEvidenceStatus.CANNOT_ASSESS, targetEvidenceStatus(case, draft))
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
    fun target_case_follows_the_evidence_change_state() {
        val baseCase = ConclusionCases.M0_T2
        val challengeCase = ConclusionCases.EVIDENCE_CHANGE
        val challengeState = ConclusionState.EvidenceChangeDrafting(
            baseDraft = ConclusionDraft(caseId = baseCase.id),
            baseEvaluation = ConclusionReducer(case = baseCase).evaluate(
                ConclusionDraft(caseId = baseCase.id),
            ),
            draft = ConclusionDraft(caseId = challengeCase.id),
        )

        assertEquals(
            challengeCase.id,
            targetCaseFor(challengeState, baseCase, challengeCase).id,
        )
        assertEquals(
            baseCase.id,
            targetCaseFor(ConclusionState.Intro, baseCase, challengeCase).id,
        )
    }

    @Test
    fun target_case_and_draft_share_the_active_case_across_challenge_lifecycle() {
        val baseCase = ConclusionCases.M0_T2
        val challengeCase = ConclusionCases.EVIDENCE_CHANGE
        val reducer = ConclusionReducer(case = baseCase)
        val baseDraft = completeTargetDraft(baseCase)
        val baseSummary = ConclusionState.Summary(
            initialDraft = baseDraft,
            revisedDraft = baseDraft,
            initialEvaluation = reducer.evaluate(baseDraft),
            finalEvaluation = reducer.evaluate(baseDraft),
        )

        var state: ConclusionState = reducer.reduce(
            baseSummary,
            ConclusionEvent.BeginEvidenceChange,
        )
        val challengeStates = buildList {
            add(state)
            state = reducer.reduce(
                state,
                ConclusionEvent.UpdateEvidenceChangeDraft(completeChallengeDraft(challengeCase)),
            )
            add(state)
            state = reducer.reduce(state, ConclusionEvent.SubmitEvidenceChange)
            add(state)
            state = reducer.reduce(state, ConclusionEvent.FinishEvidenceChange)
            add(state)
        }

        challengeStates.forEach { currentState ->
            val activeCase = targetCaseFor(currentState, baseCase, challengeCase)
            val activeDraft = targetDraftFor(currentState, activeCase)

            assertEquals(activeCase.id, activeDraft.caseId)
            if (activeCase.id == challengeCase.id) {
                assertFalse(activeCase.facts.any { it.id == "OBS-COLD-01" })
                assertFalse(activeDraft.evidenceRefs.contains("OBS-COLD-01"))
            }
        }
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

    @Test
    fun evidence_change_summary_context_is_neutral_and_uses_the_case_notice() {
        val context = evidenceChangeContextNote(ConclusionCases.EVIDENCE_CHANGE)

        assertEquals("Challenge context", context?.title)
        assertEquals(
            ConclusionCases.EVIDENCE_CHANGE.changeNotice,
            context?.body,
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

    private fun completeChallengeDraft(case: ConclusionCase): ConclusionDraft =
        ConclusionDraft(
            caseId = case.id,
            relation = ConclusionRelation.LIMITED_OBSERVATION,
            evidenceRefs = case.facts.filter { it.type.name == "OBSERVATION" }.map { it.id },
            claimText = "In this round, the warm sample dissolved before the room-temperature sample.",
            scope = ConclusionScope.LIMITED_COMPARISON,
            limitationRefs = case.facts.filter { it.type.name == "LIMITATION" }.map { it.id },
            limitationNote = "One trial and unmeasured stirring limit this comparison.",
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "Repeat the trials to check whether the changed evidence persists.",
        )
}
