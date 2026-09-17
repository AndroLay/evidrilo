package dev.nextgen.mobile.domain.conclusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PremiumCasesTest {
    @Test
    fun premiumCatalogContainsTwoDistinctEvidenceLinkedCases() {
        val cases = ConclusionCases.premium

        assertEquals(2, cases.size)
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
        assertTrue(cases.all { it.factsOfType(ConclusionFactType.OBSERVATION).size >= 2 })
        assertTrue(cases.all { it.factsOfType(ConclusionFactType.LIMITATION).isNotEmpty() })
    }

    @Test
    fun premiumEvaluatorUsesTheSelectedCaseAnchors() {
        ConclusionCases.premium.forEach { premiumCase ->
            val observationIds = premiumCase.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }
            val repeatTrialsId = premiumCase.requiredLimitationId(ConclusionImplication.REPEAT_TRIALS)
            val draft = ConclusionDraft(
                caseId = premiumCase.id,
                relation = ConclusionRelation.LIMITED_OBSERVATION,
                evidenceRefs = observationIds.take(2),
                claimText = "In this observation, the first condition finished before the second condition.",
                scope = ConclusionScope.THIS_OBSERVATION,
                limitationRefs = listOfNotNull(repeatTrialsId),
                limitationNote = "One trial limits how far this comparison can be generalized.",
                implication = ConclusionImplication.REPEAT_TRIALS,
                implicationReason = "Repeating trials addresses the single-trial limitation.",
            )

            val evaluation = ConclusionEvaluator(premiumCase).evaluate(draft)

            assertNull(evaluation.primaryFeedback)
            val evidenceCheck = evaluation.checks.first { it.check == ConclusionCheck.EVIDENCE_ANCHORING }
            assertEquals(observationIds.take(2).toSet(), evidenceCheck.anchorIds.toSet())
            assertNotNull(premiumCase.boundaryFactId())
        }
    }

    @Test
    fun reducerBeginsWithTheSelectedPremiumCaseId() {
        val premiumCase = ConclusionCases.premium.first()
        val reducer = ConclusionReducer(case = premiumCase)

        val state = reducer.reduce(ConclusionState.Intro, ConclusionEvent.Begin)

        assertTrue(state is ConclusionState.Drafting)
        assertEquals(premiumCase.id, state.draft.caseId)
    }
}
