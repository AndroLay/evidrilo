package dev.nextgen.mobile.domain.conclusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConclusionEvaluatorTest {
    private val evaluator = ConclusionEvaluator()

    @Test
    fun completeBoundedObservationPassesAllFourChecks() {
        val result = evaluator.evaluate(validDraft())

        assertNull(result.primaryFeedback)
        assertEquals(
            setOf(
                ConclusionCheck.GOAL_CONNECTEDNESS,
                ConclusionCheck.EVIDENCE_ANCHORING,
                ConclusionCheck.SCOPE_UNCERTAINTY,
                ConclusionCheck.ACTIONABLE_IMPLICATION,
            ),
            result.checks.map { it.check }.toSet(),
        )
        assertTrue(result.checks.all { it.status == ConclusionStatus.PASS })
        assertEquals(
            setOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
            result.checks
                .first { it.check == ConclusionCheck.EVIDENCE_ANCHORING }
                .anchorIds
                .toSet(),
        )
    }

    @Test
    fun emptyInputReportsIncompleteRequiredFieldBeforeQualityJudgement() {
        val feedback = evaluator.evaluate(ConclusionDraft()).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.INCOMPLETE, feedback.status)
        assertEquals(ConclusionPriority.P0, feedback.priority)
        assertEquals(ConclusionField.RELATION, feedback.field)
        assertTrue(feedback.message.contains("relation", ignoreCase = true))
    }

    @Test
    fun missingComparatorRequestsMoreEvidenceAtP2() {
        val feedback = evaluator.evaluate(
            validDraft().copy(evidenceRefs = listOf("OBS-WARM-01")),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.ACTION_REQUIRED, feedback.status)
        assertEquals(ConclusionPriority.P2, feedback.priority)
        assertEquals(ConclusionField.EVIDENCE_REFS, feedback.field)
        assertEquals(listOf("OBS-WARM-01"), feedback.anchorIds)
    }

    @Test
    fun missingEvidenceIsIncompleteBeforeComparatorQualityIsJudged() {
        val feedback = evaluator.evaluate(
            validDraft().copy(evidenceRefs = emptyList()),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.INCOMPLETE, feedback.status)
        assertEquals(ConclusionPriority.P0, feedback.priority)
        assertEquals(ConclusionField.EVIDENCE_REFS, feedback.field)
    }

    @Test
    fun causalOverclaimWinsOverMissingComparatorBecauseItIsP1() {
        val draft = validDraft().copy(
            evidenceRefs = listOf("OBS-WARM-01"),
            scope = ConclusionScope.GENERAL_CAUSAL_CLAIM,
        )

        val feedback = evaluator.evaluate(draft).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.ACTION_REQUIRED, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.SCOPE, feedback.field)
        assertTrue("BOUND-01" in feedback.anchorIds)
    }

    @Test
    fun scopeFeedbackUsesTheSelectedCaseBoundaryAndLimitations() {
        val selectedCase = ConclusionCases.premium.first()
        val evaluator = ConclusionEvaluator(selectedCase)
        val boundaryId = selectedCase.boundaryFactId()
        val limitationIds = selectedCase.factsOfType(ConclusionFactType.LIMITATION).map { it.id }
        val draft = ConclusionDraft(
            caseId = selectedCase.id,
            relation = ConclusionRelation.OBSERVED_DIFFERENCE,
            evidenceRefs = selectedCase.factsOfType(ConclusionFactType.OBSERVATION).take(2).map { it.id },
            claimText = "The first condition finished before the second condition in this trial.",
            scope = ConclusionScope.GENERAL_CAUSAL_CLAIM,
            limitationRefs = limitationIds,
            limitationNote = "The single trial limits this comparison.",
            implication = ConclusionImplication.LIMIT_CLAIM,
            implicationReason = "Keep the claim within the supplied case.",
        )

        val feedback = evaluator.evaluate(draft).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.ACTION_REQUIRED, feedback.status)
        assertTrue(boundaryId in feedback.anchorIds)
        assertTrue(limitationIds.all { it in feedback.anchorIds })
    }

    @Test
    fun externalFactIdAbstainsInsteadOfTreatingItAsEvidence() {
        val feedback = evaluator.evaluate(
            validDraft().copy(evidenceRefs = listOf("OBS-WARM-01", "EXTERNAL-99")),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.EVIDENCE_REFS, feedback.field)
        assertTrue(feedback.message.contains("cannot assess", ignoreCase = true))
    }

    @Test
    fun unsupportedNumberAbstainsInsteadOfCorrectingTheLearner() {
        val feedback = evaluator.evaluate(
            validDraft().copy(claimText = "The warm sample dissolved in 20 seconds in this observation."),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionField.CLAIM_TEXT, feedback.field)
        assertTrue(feedback.why.contains("20", ignoreCase = false))
    }

    @Test
    fun unsafeNegationAbstainsRatherThanGuessingFreeTextMeaning() {
        val feedback = evaluator.evaluate(
            validDraft().copy(claimText = "The warm sample did not dissolve faster than the cold sample."),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionField.CLAIM_TEXT, feedback.field)
    }

    @Test
    fun unsupportedGoalAbstainsInsteadOfChangingTheCaseAim() {
        val feedback = evaluator.evaluate(
            validDraft().copy(claimText = "The tablet mass changed more than the water temperature."),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionField.CLAIM_TEXT, feedback.field)
        assertTrue(feedback.anchorIds.contains("AIM-01"))
    }

    @Test
    fun unsupportedGoalTermsDoNotMatchInsideUnrelatedWords() {
        val result = evaluator.evaluate(
            validDraft().copy(
                claimText = "The phase comparison records the warm sample before the room-temperature and cold samples.",
            ),
        )

        assertNull(result.primaryFeedback)
    }

    @Test
    fun genericImplicationDoesNotPassWhenItIsNotAnchoredToTheSelectedLimitation() {
        val feedback = evaluator.evaluate(
            validDraft().copy(
                limitationRefs = listOf("LIMIT-STIR-01"),
                implication = ConclusionImplication.REPEAT_TRIALS,
                implicationReason = "Collect more data.",
            ),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.ACTION_REQUIRED, feedback.status)
        assertEquals(ConclusionPriority.P3, feedback.priority)
        assertEquals(ConclusionField.IMPLICATION, feedback.field)
    }

    @Test
    fun limitedObservationCanPassWithoutMakingACausalClaim() {
        val result = evaluator.evaluate(
            validDraft().copy(
                relation = ConclusionRelation.LIMITED_OBSERVATION,
                evidenceRefs = listOf("OBS-WARM-01", "OBS-COLD-01"),
                claimText = "In this observation, the warm sample dissolved faster than the cold sample.",
                scope = ConclusionScope.THIS_OBSERVATION,
                limitationRefs = listOf("LIMIT-TRIAL-01"),
                limitationNote = "One trial limits how far this observation can be generalized.",
                implication = ConclusionImplication.REPEAT_TRIALS,
                implicationReason = "Repeating trials addresses the single-trial limitation.",
            ),
        )

        assertNull(result.primaryFeedback)
        assertTrue(result.checks.all { it.status == ConclusionStatus.PASS })
    }

    @Test
    fun contextHypothesisCannotBeUsedAsObservationEvidence() {
        val feedback = evaluator.evaluate(
            validDraft().copy(evidenceRefs = listOf("HYP-01", "OBS-WARM-01")),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionField.EVIDENCE_REFS, feedback.field)
        assertEquals(listOf("HYP-01"), feedback.anchorIds)
    }

    @Test
    fun safeParaphrasePassesBecauseStructuredFactsCarryTheMeaning() {
        val result = evaluator.evaluate(
            validDraft().copy(
                relation = ConclusionRelation.LIMITED_OBSERVATION,
                evidenceRefs = listOf("OBS-WARM-01", "OBS-COLD-01"),
                claimText = "In this trial, the warm sample completed dissolution before the cold sample.",
                scope = ConclusionScope.THIS_OBSERVATION,
                limitationRefs = listOf("LIMIT-TRIAL-01"),
                limitationNote = "One trial limits how far this observation can be generalized.",
                implication = ConclusionImplication.REPEAT_TRIALS,
                implicationReason = "Repeating trials addresses the single-trial limitation.",
            ),
        )

        assertNull(result.primaryFeedback)
        assertTrue(result.checks.all { it.status == ConclusionStatus.PASS })
    }

    @Test
    fun externalCauseAbstainsInsteadOfInventingAnExplanation() {
        val feedback = evaluator.evaluate(
            validDraft().copy(
                claimText = "The warm sample dissolved faster because the tablet was more soluble.",
            ),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionField.CLAIM_TEXT, feedback.field)
        assertEquals("UNSUPPORTED_CAUSE", feedback.code)
    }

    @Test
    fun causalExternalCauseStillSurfacesTheScopeBoundaryFirst() {
        val feedback = evaluator.evaluate(
            validDraft().copy(
                claimText = "Warm water caused faster dissolution because the tablet was more soluble.",
                scope = ConclusionScope.GENERAL_CAUSAL_CLAIM,
            ),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.ACTION_REQUIRED, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.SCOPE, feedback.field)
        assertEquals("OVERCLAIM_SCOPE", feedback.code)
        assertTrue("BOUND-01" in feedback.anchorIds)
        assertTrue("LIMIT-TRIAL-01" in feedback.anchorIds)
        assertTrue("LIMIT-STIR-01" in feedback.anchorIds)
    }

    @Test
    fun explicitNotApplicableRequiresAndAcceptsAReason() {
        val result = evaluator.evaluate(
            validDraft().copy(
                implication = ConclusionImplication.NOT_APPLICABLE,
                implicationReason = "This exercise is limited to reporting the supplied observation.",
            ),
        )

        assertNull(result.primaryFeedback)
        assertEquals(
            ConclusionStatus.PASS,
            result.checks.first { it.check == ConclusionCheck.ACTIONABLE_IMPLICATION }.status,
        )
    }

    @Test
    fun unsupportedEnumAbstainsInsteadOfGuessingAnAction() {
        val feedback = evaluator.evaluate(
            validDraft().copy(implication = ConclusionImplication.UNSUPPORTED),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.IMPLICATION, feedback.field)
        assertEquals("UNSUPPORTED_INPUT", feedback.code)
    }

    @Test
    fun unsupportedRelationIsRejectedBeforeOtherRequiredFields() {
        val feedback = evaluator.evaluate(
            ConclusionDraft(relation = ConclusionRelation.UNSUPPORTED),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.RELATION, feedback.field)
    }

    @Test
    fun relationConflictAbstainsInsteadOfChoosingBetweenStructuredAndFreeTextMeaning() {
        val feedback = evaluator.evaluate(
            validDraft().copy(
                relation = ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE,
                claimText = "The warm sample dissolved faster than the cold sample in this case.",
            ),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.CLAIM_TEXT, feedback.field)
        assertEquals("RELATION_CONFLICT", feedback.code)
    }

    @Test
    fun cannotConcludeRelationCanPassWithoutAcausalClaim() {
        val result = evaluator.evaluate(
            validDraft().copy(
                relation = ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE,
                claimText = "This case cannot establish a general effect beyond the supplied observation.",
                scope = ConclusionScope.THIS_OBSERVATION,
                implication = ConclusionImplication.LIMIT_CLAIM,
                implicationReason = "Limiting the claim respects the supplied case boundary.",
            ),
        )

        assertNull(result.primaryFeedback)
        assertTrue(result.checks.all { it.status == ConclusionStatus.PASS })
    }

    @Test
    fun missingLimitationIsIncompleteRatherThanAQualityFailure() {
        val feedback = evaluator.evaluate(
            validDraft().copy(limitationRefs = emptyList()),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.INCOMPLETE, feedback.status)
        assertEquals(ConclusionPriority.P0, feedback.priority)
        assertEquals(ConclusionField.LIMITATION_REFS, feedback.field)
    }

    @Test
    fun unsupportedCaseAbstainsBeforeReadingItsFields() {
        val feedback = evaluator.evaluate(
            validDraft().copy(caseId = "other-case"),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.CASE_ID, feedback.field)
    }

    @Test
    fun moreThanThreeEvidenceRefsIsUnsupportedInput() {
        val feedback = evaluator.evaluate(
            validDraft().copy(
                evidenceRefs = listOf(
                    "OBS-WARM-01",
                    "OBS-ROOM-01",
                    "OBS-COLD-01",
                    "OBS-WARM-01",
                ),
            ),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.EVIDENCE_REFS, feedback.field)
    }

    @Test
    fun moreThanTwoLimitationRefsIsUnsupportedInput() {
        val feedback = evaluator.evaluate(
            validDraft().copy(
                limitationRefs = listOf("LIMIT-TRIAL-01", "LIMIT-STIR-01", "LIMIT-TRIAL-01"),
            ),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.CANNOT_ASSESS, feedback.status)
        assertEquals(ConclusionPriority.P1, feedback.priority)
        assertEquals(ConclusionField.LIMITATION_REFS, feedback.field)
    }

    @Test
    fun whitespaceDoesNotSatisfyRequiredTextLength() {
        val feedback = evaluator.evaluate(
            validDraft().copy(claimText = "                    "),
        ).primaryFeedback

        assertNotNull(feedback)
        assertEquals(ConclusionStatus.INCOMPLETE, feedback.status)
        assertEquals(ConclusionPriority.P0, feedback.priority)
        assertEquals(ConclusionField.CLAIM_TEXT, feedback.field)
    }

    private fun validDraft() = ConclusionDraft(
        caseId = ConclusionCases.M0_T2.id,
        relation = ConclusionRelation.OBSERVED_DIFFERENCE,
        evidenceRefs = listOf("OBS-WARM-01", "OBS-ROOM-01", "OBS-COLD-01"),
        claimText = "In this observation, the warm sample dissolved faster than the room-temperature and cold samples.",
        scope = ConclusionScope.LIMITED_COMPARISON,
        limitationRefs = listOf("LIMIT-TRIAL-01", "LIMIT-STIR-01"),
        limitationNote = "One trial per condition and unmeasured stirring limit what this comparison can establish.",
        implication = ConclusionImplication.CONTROL_STIRRING,
        implicationReason = "Controlling stirring addresses the unmeasured stirring limitation.",
    )
}
