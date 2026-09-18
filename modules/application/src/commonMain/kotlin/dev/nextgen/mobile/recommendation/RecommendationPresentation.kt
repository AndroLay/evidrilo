package dev.nextgen.mobile.recommendation

data class RecommendationCardPresentation(
    val title: String,
    val objective: String,
    val explanation: String,
    val evidenceSummary: String,
    val acceptLabel: String,
    val dismissLabel: String,
    val contentDescription: String,
)

fun RecommendationPayload.toCardPresentation(): RecommendationCardPresentation? {
    if (status != RecommendationStatus.RECOMMENDED || caseVersionId == null) return null
    val safeObjective = objective
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val explanation = when (reason) {
        RecommendationReason.START_HERE -> "A short evidence review is ready to begin."
        RecommendationReason.PRACTICE_ACTION_REQUIRED -> "A focused review can help turn your latest feedback into a next step."
        RecommendationReason.NEXT_PRACTICE -> "A bounded follow-up review is ready when you are."
        RecommendationReason.NO_ELIGIBLE_CASE,
        RecommendationReason.INSUFFICIENT_PROJECTION,
        -> return null
    }
    val evidenceSummary = "Evidence anchors: ${evidenceReferences.size.coerceIn(0, 128)}"
    val title = "Suggested next review"
    return RecommendationCardPresentation(
        title = title,
        objective = safeObjective,
        explanation = explanation,
        evidenceSummary = evidenceSummary,
        acceptLabel = "Start suggested review",
        dismissLabel = "Not now",
        contentDescription = "$title. Focus: $safeObjective. $explanation $evidenceSummary.",
    )
}
