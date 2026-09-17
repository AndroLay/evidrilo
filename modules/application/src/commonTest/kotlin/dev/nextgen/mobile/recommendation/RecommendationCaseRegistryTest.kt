package dev.nextgen.mobile.recommendation

import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecommendationCaseRegistryTest {
    @Test
    fun resolves_only_the_exact_first_release_remote_case_version() {
        val target = RecommendationCaseRegistry().resolve(recommended("M0_T2:1"))

        requireNotNull(target)
        assertEquals("M0_T2:1", target.remoteCaseVersionId)
        assertEquals(ConclusionCases.M0_T2, target.localCase)
    }

    @Test
    fun null_case_abstain_and_related_ids_never_resolve() {
        assertNull(RecommendationCaseRegistry().resolve(recommended(null)))
        assertNull(RecommendationCaseRegistry().resolve(abstain()))
        assertNull(RecommendationCaseRegistry().resolve(recommended("M0_T2:10")))
        assertNull(RecommendationCaseRegistry().resolve(recommended("evidrilo-m0-t2-v1")))
        assertNull(RecommendationCaseRegistry().resolve(recommended("M0_T2")))
    }

    private fun recommended(caseVersionId: String?) = RecommendationPayload(
        status = RecommendationStatus.RECOMMENDED,
        calculationVersion = "recommendation.v1",
        caseVersionId = caseVersionId,
        objective = "Practice the next evidence comparison.",
        reason = RecommendationReason.START_HERE,
        evidenceReferences = listOf("attempt-001"),
        requestId = "req-rec-001",
    )

    private fun abstain() = RecommendationPayload(
        status = RecommendationStatus.ABSTAIN,
        calculationVersion = "recommendation.v1",
        caseVersionId = null,
        objective = null,
        reason = RecommendationReason.NO_ELIGIBLE_CASE,
        evidenceReferences = emptyList(),
        requestId = "req-rec-002",
    )
}
