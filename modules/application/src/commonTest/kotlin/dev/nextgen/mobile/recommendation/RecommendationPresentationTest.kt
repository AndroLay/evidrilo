package dev.nextgen.mobile.recommendation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecommendationPresentationTest {
    @Test
    fun actionable_reasons_produce_friendly_copy_with_a_bounded_objective() {
        listOf(
            RecommendationReason.START_HERE,
            RecommendationReason.PRACTICE_ACTION_REQUIRED,
            RecommendationReason.NEXT_PRACTICE,
        ).forEach { reason ->
            val presentation = assertNotNull(payload(reason).toCardPresentation())
            val rendered = listOf(
                presentation.title,
                presentation.explanation,
                presentation.evidenceSummary,
                presentation.acceptLabel,
                presentation.dismissLabel,
                presentation.contentDescription,
            ).joinToString(" ")

            assertTrue(presentation.title.isNotBlank())
            assertTrue(presentation.explanation.isNotBlank())
            assertEquals("Practice the next evidence comparison.", presentation.objective)
            assertTrue(presentation.evidenceSummary == "Evidence anchors: 1")
            assertTrue(rendered.contains(presentation.objective))
            assertFalse(rendered.contains(reason.name))
            assertFalse(rendered.contains("attempt-001"))
        }
    }

    @Test
    fun non_actionable_reasons_never_render_an_available_card() {
        assertFalse(payload(RecommendationReason.NO_ELIGIBLE_CASE).toCardPresentation() != null)
        assertFalse(payload(RecommendationReason.INSUFFICIENT_PROJECTION).toCardPresentation() != null)
    }

    private fun payload(reason: RecommendationReason) = RecommendationPayload(
        status = if (reason in setOf(RecommendationReason.NO_ELIGIBLE_CASE, RecommendationReason.INSUFFICIENT_PROJECTION)) {
            RecommendationStatus.ABSTAIN
        } else {
            RecommendationStatus.RECOMMENDED
        },
        calculationVersion = "recommendation.v1",
        caseVersionId = if (reason in setOf(RecommendationReason.NO_ELIGIBLE_CASE, RecommendationReason.INSUFFICIENT_PROJECTION)) null else "M0_T2:1",
        objective = if (reason in setOf(RecommendationReason.NO_ELIGIBLE_CASE, RecommendationReason.INSUFFICIENT_PROJECTION)) null else "Practice the next evidence comparison.",
        reason = reason,
        evidenceReferences = if (reason in setOf(RecommendationReason.NO_ELIGIBLE_CASE, RecommendationReason.INSUFFICIENT_PROJECTION)) emptyList() else listOf("attempt-001"),
        requestId = "req-rec-001",
    )
}
