package dev.nextgen.mobile.recommendation

internal enum class RecommendationStatus {
    RECOMMENDED,
    ABSTAIN,
}

internal enum class RecommendationReason {
    START_HERE,
    PRACTICE_ACTION_REQUIRED,
    NEXT_PRACTICE,
    NO_ELIGIBLE_CASE,
    INSUFFICIENT_PROJECTION,
}

internal enum class RecommendationInteraction(
    val wireName: String,
) {
    SHOWN("shown"),
    ACCEPTED("accepted"),
    DISMISSED("dismissed"),
}

internal data class RecommendationPayload(
    val status: RecommendationStatus,
    val calculationVersion: String,
    val caseVersionId: String?,
    val objective: String?,
    val reason: RecommendationReason,
    val evidenceReferences: List<String>,
    val requestId: String,
)

internal sealed interface RecommendationParseResult {
    data class Valid(val value: RecommendationPayload) : RecommendationParseResult

    data class Rejected(val code: String) : RecommendationParseResult
}
