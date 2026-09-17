package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.recommendation.RecommendationUiState

internal enum class EvidriloHomeAction(
    val label: String,
) {
    START_PRACTICE("Start practice"),
    CONTINUE_PRACTICE("Continue practice"),
    REVIEW_FEEDBACK("Review feedback"),
    CONTINUE_REVISION("Continue revision"),
    REVIEW_COMPARISON("Review your comparison"),
    CONTINUE_CHALLENGE("Continue challenge"),
    REVIEW_CHALLENGE_FEEDBACK("Review challenge feedback"),
    VIEW_COMPARISON("View comparison"),
}

internal fun homeCaseForState(state: ConclusionState): ConclusionCase = when (state) {
    is ConclusionState.EvidenceChangeDrafting,
    is ConclusionState.EvidenceChangeFeedback,
    is ConclusionState.EvidenceChangeSummary,
    -> ConclusionCases.EVIDENCE_CHANGE
    ConclusionState.Intro,
    is ConclusionState.Drafting,
    is ConclusionState.Incomplete,
    is ConclusionState.Feedback,
    is ConclusionState.Revision,
    is ConclusionState.Summary,
    -> ConclusionCases.M0_T2
}

internal fun homePrimaryAction(state: ConclusionState): EvidriloHomeAction = when (state) {
    ConclusionState.Intro -> EvidriloHomeAction.START_PRACTICE
    is ConclusionState.Drafting,
    is ConclusionState.Incomplete,
    -> EvidriloHomeAction.CONTINUE_PRACTICE
    is ConclusionState.Feedback -> EvidriloHomeAction.REVIEW_FEEDBACK
    is ConclusionState.Revision -> EvidriloHomeAction.CONTINUE_REVISION
    is ConclusionState.Summary -> EvidriloHomeAction.REVIEW_COMPARISON
    is ConclusionState.EvidenceChangeDrafting -> EvidriloHomeAction.CONTINUE_CHALLENGE
    is ConclusionState.EvidenceChangeFeedback -> EvidriloHomeAction.REVIEW_CHALLENGE_FEEDBACK
    is ConclusionState.EvidenceChangeSummary -> EvidriloHomeAction.VIEW_COMPARISON
}

internal data class EvidriloHomeObservation(
    val label: String,
    val value: String,
    val factId: String,
)

internal enum class EvidriloHomeObservationLayout {
    FIT,
    HORIZONTAL_SCROLL,
}

internal fun homeObservationLayout(observationCount: Int): EvidriloHomeObservationLayout =
    if (observationCount <= 3) {
        EvidriloHomeObservationLayout.FIT
    } else {
        EvidriloHomeObservationLayout.HORIZONTAL_SCROLL
    }

internal fun homeObservationPreview(
    case: ConclusionCase = ConclusionCases.M0_T2,
): List<EvidriloHomeObservation> = case
    .factsOfType(ConclusionFactType.OBSERVATION)
    .map { fact ->
        val rawText = fact.text.trim()
        val fallbackLabel = rawText.substringBefore(':').trim().ifBlank { "Observation" }
        val fallbackValue = rawText
            .substringAfter(':', rawText)
            .trim()
            .ifBlank { "Observation unavailable" }
        EvidriloHomeObservation(
            label = fact.displayLabel?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackLabel,
            value = fact.displayValue?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackValue,
            factId = fact.id,
        )
    }

internal enum class EvidriloRecommendationSurface {
    NONE,
    LOADING,
    AVAILABLE,
    ACTION_IN_PROGRESS,
    UNAVAILABLE,
}

internal fun recommendationHomeSurface(state: RecommendationUiState): EvidriloRecommendationSurface = when (state) {
    RecommendationUiState.Hidden,
    RecommendationUiState.Abstained,
    is RecommendationUiState.Unsupported,
    RecommendationUiState.Expired,
    RecommendationUiState.Rejected,
    -> EvidriloRecommendationSurface.NONE
    RecommendationUiState.Loading -> EvidriloRecommendationSurface.LOADING
    is RecommendationUiState.Available -> EvidriloRecommendationSurface.AVAILABLE
    RecommendationUiState.ActionInProgress -> EvidriloRecommendationSurface.ACTION_IN_PROGRESS
    is RecommendationUiState.Unavailable -> EvidriloRecommendationSurface.UNAVAILABLE
}

internal fun recommendationUnavailableCopy(): String =
    "A suggested practice is temporarily unavailable."
