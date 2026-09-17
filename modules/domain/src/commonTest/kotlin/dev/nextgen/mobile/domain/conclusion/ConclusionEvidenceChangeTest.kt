package dev.nextgen.mobile.domain.conclusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConclusionEvidenceChangeTest {
    @Test
    fun evidenceChangeCaseOmitsColdObservationAndExplainsTheChange() {
        val challenge = ConclusionCases.EVIDENCE_CHANGE

        assertEquals("evidrilo-m0-t2-evidence-change-v1", challenge.id)
        assertFalse(challenge.facts.any { it.id == "OBS-COLD-01" })
        assertEquals(
            "Evidence change: the cold-water observation is unavailable in this round.",
            challenge.changeNotice,
        )
        assertEquals(
            setOf("OBS-WARM-01", "OBS-ROOM-01"),
            challenge.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }.toSet(),
        )
    }

    @Test
    fun challengeEvaluatorRejectsRemovedObservationAndLiteral() {
        val evaluator = ConclusionEvaluator(ConclusionCases.EVIDENCE_CHANGE)
        val draft = challengeDraft().copy(
            evidenceRefs = listOf("OBS-WARM-01", "OBS-COLD-01"),
        )

        val staleReference = evaluator.evaluate(draft).primaryFeedback
        assertEquals(ConclusionStatus.CANNOT_ASSESS, staleReference?.status)
        assertEquals("UNSUPPORTED_INPUT", staleReference?.code)

        val staleLiteral = evaluator.evaluate(
            challengeDraft().copy(claimText = "The warm sample dissolved in 92 seconds in this round."),
        ).primaryFeedback
        assertEquals(ConclusionStatus.CANNOT_ASSESS, staleLiteral?.status)
        assertEquals("UNSUPPORTED_LITERAL", staleLiteral?.code)
    }

    @Test
    fun challengeEvaluatorAcceptsOnlyActiveObservationLiterals() {
        val evaluator = ConclusionEvaluator(ConclusionCases.EVIDENCE_CHANGE)
        val result = evaluator.evaluate(challengeDraft())

        assertTrue(result.checks.isNotEmpty())
        assertEquals(null, result.primaryFeedback)
    }

    private fun challengeDraft() = ConclusionDraft(
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
