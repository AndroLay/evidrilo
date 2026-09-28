package dev.nextgen.mobile.content

import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteCaseAdapterTest {
    @Test
    fun published_case_updates_display_text_without_changing_evaluator_identity() {
        val adapted = summary().toBundledEvaluatorCase(ConclusionCases.M0_T2)

        requireNotNull(adapted)
        assertEquals(ConclusionCases.M0_T2.id, adapted.id)
        assertEquals("M0_T2:1", adapted.remoteCaseVersionId)
        assertEquals("Remote tablet dissolution", adapted.title)
        assertEquals("Remote objective", adapted.description)
        assertEquals("Warm water: 31 seconds.", adapted.fact("OBS-WARM-01")?.text)
        assertEquals(ConclusionCases.M0_T2.implicationAnchors, adapted.implicationAnchors)
        assertEquals(ConclusionCases.M0_T2.unsupportedClaimTerms, adapted.unsupportedClaimTerms)
    }

    @Test
    fun adapter_rejects_remote_content_that_changes_a_required_fact_type_or_version() {
        val wrongType = summary().copy(
            facts = summary().facts.map { fact ->
                if (fact.id == "OBS-WARM-01") fact.copy(type = "limitation") else fact
            },
        )
        assertNull(wrongType.toBundledEvaluatorCase(ConclusionCases.M0_T2))
        assertNull(summary().copy(caseVersionId = "M0_T2:2").toBundledEvaluatorCase(ConclusionCases.M0_T2))
    }

    private fun summary() = PublishedCaseSummary(
        caseId = "M0_T2",
        caseVersionId = "M0_T2:1",
        title = "Remote tablet dissolution",
        contentHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        evaluatorVersion = "evaluator.v1",
        skillTags = listOf("evidence"),
        objective = "Remote objective",
        difficulty = 2,
        evidenceReferences = listOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
        facts = ConclusionCases.M0_T2.facts.map { fact ->
            PublishedCaseFact(
                id = fact.id,
                type = fact.type.name.lowercase(),
                text = if (fact.id == "OBS-WARM-01") "Warm water: 31 seconds." else fact.text,
            )
        },
        rules = listOf(PublishedCaseRule("RULE-1", "PASS", listOf("OBS-WARM-01"))),
        variants = listOf(PublishedCaseVariant("CHALLENGE-1", listOf("OBS-COLD-01"))),
        requestId = "req-adapter-1",
    )
}
