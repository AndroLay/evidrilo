package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.recommendation.RecommendationCaseRegistry
import dev.nextgen.mobile.recommendation.RecommendationLifecycleKey
import dev.nextgen.mobile.recommendation.RecommendationPayload
import dev.nextgen.mobile.recommendation.RecommendationReason
import dev.nextgen.mobile.recommendation.RecommendationStatus
import dev.nextgen.mobile.recommendation.RecommendationUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EvidriloHomePresentationTest {
    private val draft = ConclusionDraft(caseId = "case.test")
    private val evaluation = ConclusionEvaluation(checks = emptyList())

    @Test
    fun recommendation_surface_is_additive_and_hides_unsafe_states() {
        val payload = RecommendationPayload(
            status = RecommendationStatus.RECOMMENDED,
            calculationVersion = "recommendation.v1",
            caseVersionId = "M0_T2:1",
            objective = "Practice the next evidence comparison.",
            reason = RecommendationReason.START_HERE,
            evidenceReferences = listOf("attempt-001"),
            requestId = "req-rec-001",
        )
        val available = RecommendationUiState.Available(
            recommendation = payload,
            target = requireNotNull(RecommendationCaseRegistry().resolve(payload)),
            interactionId = "123e4567-e89b-42d3-a456-426614174010",
        )

        assertEquals(EvidriloRecommendationSurface.AVAILABLE, recommendationHomeSurface(available))
        assertEquals(EvidriloRecommendationSurface.LOADING, recommendationHomeSurface(RecommendationUiState.Loading))
        assertEquals(
            EvidriloRecommendationSurface.ACTION_IN_PROGRESS,
            recommendationHomeSurface(RecommendationUiState.ActionInProgress),
        )
        assertEquals(
            EvidriloRecommendationSurface.UNAVAILABLE,
            recommendationHomeSurface(RecommendationUiState.Unavailable("OFFLINE")),
        )
        listOf(
            RecommendationUiState.Hidden,
            RecommendationUiState.Abstained,
            RecommendationUiState.Unsupported("unknown:1"),
            RecommendationUiState.Expired,
            RecommendationUiState.Rejected,
        ).forEach { state ->
            assertEquals(EvidriloRecommendationSurface.NONE, recommendationHomeSurface(state))
        }

        assertEquals("A suggested review is temporarily unavailable.", recommendationUnavailableCopy())
        assertFalse(recommendationUnavailableCopy().contains("OFFLINE"))
        assertEquals("Start workspace", homePrimaryAction(ConclusionState.Intro).label)
    }

    @Test
    fun mapsEachPracticeStateToAnHonestPrimaryAction() {
        assertEquals(EvidriloHomeAction.START_PRACTICE, homePrimaryAction(ConclusionState.Intro))
        assertEquals(
            EvidriloHomeAction.CONTINUE_PRACTICE,
            homePrimaryAction(ConclusionState.Drafting(draft)),
        )
        assertEquals(
            EvidriloHomeAction.REVIEW_FEEDBACK,
            homePrimaryAction(ConclusionState.Feedback(draft, draft, evaluation)),
        )
        assertEquals(
            EvidriloHomeAction.CONTINUE_REVISION,
            homePrimaryAction(
                ConclusionState.Revision(
                    initialDraft = draft,
                    draft = draft,
                    initialEvaluation = evaluation,
                ),
            ),
        )
        assertEquals(
            EvidriloHomeAction.REVIEW_COMPARISON,
            homePrimaryAction(
                ConclusionState.Summary(
                    initialDraft = draft,
                    revisedDraft = draft,
                    initialEvaluation = evaluation,
                    finalEvaluation = evaluation,
                ),
            ),
        )
        assertEquals(
            EvidriloHomeAction.CONTINUE_CHALLENGE,
            homePrimaryAction(
                ConclusionState.EvidenceChangeDrafting(
                    baseDraft = draft,
                    baseEvaluation = evaluation,
                    draft = draft,
                ),
            ),
        )
        assertEquals(
            EvidriloHomeAction.REVIEW_CHALLENGE_FEEDBACK,
            homePrimaryAction(
                ConclusionState.EvidenceChangeFeedback(
                    baseDraft = draft,
                    baseEvaluation = evaluation,
                    draft = draft,
                    evaluation = evaluation,
                ),
            ),
        )
        assertEquals(
            EvidriloHomeAction.VIEW_COMPARISON,
            homePrimaryAction(
                ConclusionState.EvidenceChangeSummary(
                    baseDraft = draft,
                    baseEvaluation = evaluation,
                    challengeDraft = draft,
                    challengeEvaluation = evaluation,
                ),
            ),
        )
    }

    @Test
    fun actionLabelsDescribeTheNextSafeAction() {
        assertEquals("Start workspace", EvidriloHomeAction.START_PRACTICE.label)
        assertEquals("Continue workspace", EvidriloHomeAction.CONTINUE_PRACTICE.label)
        assertEquals("Review feedback", EvidriloHomeAction.REVIEW_FEEDBACK.label)
        assertEquals("Continue revision", EvidriloHomeAction.CONTINUE_REVISION.label)
        assertEquals("Review your comparison", EvidriloHomeAction.REVIEW_COMPARISON.label)
        assertEquals("Continue challenge", EvidriloHomeAction.CONTINUE_CHALLENGE.label)
        assertEquals("Review challenge feedback", EvidriloHomeAction.REVIEW_CHALLENGE_FEEDBACK.label)
        assertEquals("View comparison", EvidriloHomeAction.VIEW_COMPARISON.label)
    }

    @Test
    fun observationPreviewKeepsAllSuppliedTemperatureFactsVisible() {
        assertEquals(
            listOf("Warm" to "32 s", "Room" to "58 s", "Cold" to "92 s"),
            homeObservationPreview().map { it.label to it.value },
        )
        assertEquals(
            listOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
            homeObservationPreview().map(EvidriloHomeObservation::factId),
        )
    }

    @Test
    fun evidenceChangeHomeUsesOnlyTheActiveCaseFacts() {
        val states = listOf(
            ConclusionState.EvidenceChangeDrafting(
                baseDraft = draft,
                baseEvaluation = evaluation,
                draft = draft,
            ),
            ConclusionState.EvidenceChangeFeedback(
                baseDraft = draft,
                baseEvaluation = evaluation,
                draft = draft,
                evaluation = evaluation,
            ),
            ConclusionState.EvidenceChangeSummary(
                baseDraft = draft,
                baseEvaluation = evaluation,
                challengeDraft = draft,
                challengeEvaluation = evaluation,
            ),
        )

        states.forEach { state ->
            assertEquals(ConclusionCases.EVIDENCE_CHANGE.id, homeCaseForState(state).id)
        }

        val preview = homeObservationPreview(homeCaseForState(states.first()))
        assertEquals(
            listOf("OBS-WARM-01", "OBS-ROOM-01"),
            preview.map(EvidriloHomeObservation::factId),
        )
        assertFalse(preview.any { it.factId == "OBS-COLD-01" })
    }

    @Test
    fun unannotatedObservationPreviewFallsBackToTheSuppliedFactText() {
        val syntheticCase = ConclusionCase(
            id = "synthetic-home-case",
            facts = (1..8).map { index ->
                ConclusionFact(
                    id = "OBS-$index",
                    type = ConclusionFactType.OBSERVATION,
                    text = if (index == 1) "Qualitative note" else "Condition $index: recorded value",
                )
            },
        )

        val preview = homeObservationPreview(syntheticCase)

        assertEquals(8, preview.size)
        assertEquals("Qualitative note" to "Qualitative note", preview.first().label to preview.first().value)
        assertEquals("Condition 2" to "recorded value", preview[1].label to preview[1].value)
    }

    @Test
    fun observationRowScrollsInsteadOfCompressingLargeCases() {
        assertEquals(EvidriloHomeObservationLayout.FIT, homeObservationLayout(3))
        assertEquals(EvidriloHomeObservationLayout.HORIZONTAL_SCROLL, homeObservationLayout(8))
    }
}
