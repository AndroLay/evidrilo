package dev.nextgen.mobile.domain.conclusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConclusionCaseContractTest {
    @Test
    fun competition_cases_declare_their_canonical_backend_version() {
        assertEquals("M0_T2:1", ConclusionCases.M0_T2.remoteCaseVersionId)
        assertEquals("M0_T2:1", ConclusionCases.EVIDENCE_CHANGE.remoteCaseVersionId)
    }

    @Test
    fun bundledCasesSatisfyTheEvaluatorContract() {
        val cases = listOf(ConclusionCases.M0_T2, ConclusionCases.EVIDENCE_CHANGE) + ConclusionCases.premium

        cases.forEach { candidate ->
            val result = ConclusionCaseValidator.validate(candidate)

            assertTrue(result.isValid, "${candidate.id}: ${result.issues}")
        }
    }

    @Test
    fun invalidCaseReportsDuplicateFactsAndMissingRequiredFactTypes() {
        val duplicate = ConclusionFact(
            id = "OBS-01",
            type = ConclusionFactType.OBSERVATION,
            text = "One supplied observation.",
        )
        val result = ConclusionCaseValidator.validate(
            ConclusionCase(
                id = "broken-case-v1",
                facts = listOf(duplicate, duplicate),
            ),
        )

        assertFalse(result.isValid)
        assertEquals(
            setOf("DUPLICATE_FACT_ID", "AIM_REQUIRED", "OBSERVATIONS_REQUIRED", "LIMITATION_REQUIRED", "BOUNDARY_REQUIRED"),
            result.issues.map { it.code }.toSet(),
        )
    }

    @Test
    fun invalidImplicationAnchorCannotPassCaseValidation() {
        val result = ConclusionCaseValidator.validate(
            ConclusionCases.M0_T2.copy(
                implicationAnchors = mapOf(
                    ConclusionImplication.REPEAT_TRIALS to "OBS-WARM-01",
                ),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == "IMPLICATION_ANCHOR_NOT_LIMITATION" })
    }

    @Test
    fun evaluatorFailsClosedInsteadOfThrowingForAnInvalidCase() {
        val evaluator = ConclusionEvaluator(
            ConclusionCase(
                id = "broken-case-v1",
                facts = emptyList(),
            ),
        )

        val evaluation = evaluator.evaluate(
            ConclusionDraft(caseId = "broken-case-v1", relation = ConclusionRelation.UNSUPPORTED),
        )

        assertEquals(ConclusionStatus.CANNOT_ASSESS, evaluation.primaryFeedback?.status)
        assertEquals("INVALID_CASE", evaluation.primaryFeedback?.code)
        assertEquals(emptyList(), evaluation.primaryFeedback?.anchorIds)
    }
}
