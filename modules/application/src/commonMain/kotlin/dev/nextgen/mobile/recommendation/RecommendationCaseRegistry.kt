package dev.nextgen.mobile.recommendation

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID

data class RecommendationLaunchTarget(
    val remoteCaseVersionId: String,
    val localCase: ConclusionCase,
)

class RecommendationCaseRegistry(
    private val mappings: Map<String, ConclusionCase> = mapOf(
        EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID to ConclusionCases.M0_T2,
    ),
) {
    fun resolve(recommendation: RecommendationPayload): RecommendationLaunchTarget? {
        if (recommendation.status != RecommendationStatus.RECOMMENDED) return null
        val remoteId = recommendation.caseVersionId ?: return null
        return mappings[remoteId]
            ?.takeIf { it.remoteCaseVersionId == remoteId }
            ?.let { localCase ->
            RecommendationLaunchTarget(remoteCaseVersionId = remoteId, localCase = localCase)
        }
    }
}
