package dev.nextgen.mobile.domain.conclusion

data class ConclusionFixtureExpectation(
    val status: ConclusionStatus? = null,
    val priority: ConclusionPriority? = null,
    val field: ConclusionField? = null,
    val code: String? = null,
)

data class ConclusionFixture(
    val id: String,
    val draft: ConclusionDraft,
    val expectation: ConclusionFixtureExpectation,
)

object ConclusionFixtures {
    val evaluatorFixtures: List<ConclusionFixture> = listOf(
        fixture("ADV-01", completeDraft()),
        fixture(
            id = "ADV-02",
            draft = completeDraft().copy(
                relation = ConclusionRelation.LIMITED_OBSERVATION,
                evidenceRefs = listOf("OBS-WARM-01", "OBS-COLD-01"),
                claimText = "In this observation, the warm sample dissolved before the cold sample.",
                scope = ConclusionScope.THIS_OBSERVATION,
                limitationRefs = listOf("LIMIT-TRIAL-01"),
                limitationNote = "One trial limits how far this observation can be generalized.",
                implication = ConclusionImplication.REPEAT_TRIALS,
                implicationReason = "Repeating trials addresses the single-trial limitation.",
            ),
        ),
        fixture(
            id = "ADV-03",
            draft = completeDraft().copy(claimText = "The tablet mass changed more than the water temperature."),
            status = ConclusionStatus.CANNOT_ASSESS,
            priority = ConclusionPriority.P1,
            field = ConclusionField.CLAIM_TEXT,
            code = "UNSUPPORTED_GOAL",
        ),
        fixture(
            id = "ADV-04",
            draft = completeDraft().copy(evidenceRefs = emptyList()),
            status = ConclusionStatus.INCOMPLETE,
            priority = ConclusionPriority.P0,
            field = ConclusionField.EVIDENCE_REFS,
            code = "MISSING_REQUIRED_FIELD",
        ),
        fixture(
            id = "ADV-05",
            draft = completeDraft().copy(scope = ConclusionScope.GENERAL_CAUSAL_CLAIM),
            status = ConclusionStatus.ACTION_REQUIRED,
            priority = ConclusionPriority.P1,
            field = ConclusionField.SCOPE,
            code = "OVERCLAIM_SCOPE",
        ),
        fixture(
            id = "ADV-06",
            draft = completeDraft().copy(implication = ConclusionImplication.UNSUPPORTED),
            status = ConclusionStatus.CANNOT_ASSESS,
            priority = ConclusionPriority.P1,
            field = ConclusionField.IMPLICATION,
            code = "UNSUPPORTED_INPUT",
        ),
        fixture(
            id = "ADV-07",
            draft = ConclusionDraft(),
            status = ConclusionStatus.INCOMPLETE,
            priority = ConclusionPriority.P0,
            field = ConclusionField.RELATION,
            code = "MISSING_REQUIRED_FIELD",
        ),
        fixture(
            id = "ADV-08",
            draft = completeDraft().copy(
                evidenceRefs = listOf("OBS-WARM-01"),
                scope = ConclusionScope.GENERAL_CAUSAL_CLAIM,
            ),
            status = ConclusionStatus.ACTION_REQUIRED,
            priority = ConclusionPriority.P1,
            field = ConclusionField.SCOPE,
            code = "OVERCLAIM_SCOPE",
        ),
        fixture(
            id = "ADV-09",
            draft = completeDraft().copy(
                claimText = "The warm sample did not dissolve faster than the cold sample.",
            ),
            status = ConclusionStatus.CANNOT_ASSESS,
            priority = ConclusionPriority.P1,
            field = ConclusionField.CLAIM_TEXT,
            code = "UNSAFE_NEGATION",
        ),
        fixture(
            id = "ADV-10",
            draft = completeDraft().copy(
                relation = ConclusionRelation.LIMITED_OBSERVATION,
                evidenceRefs = listOf("OBS-WARM-01", "OBS-COLD-01"),
                claimText = "In this trial, the warm sample completed dissolution before the cold sample.",
                scope = ConclusionScope.THIS_OBSERVATION,
                limitationRefs = listOf("LIMIT-TRIAL-01"),
                limitationNote = "One trial limits how far this observation can be generalized.",
                implication = ConclusionImplication.REPEAT_TRIALS,
                implicationReason = "Repeating trials addresses the single-trial limitation.",
            ),
        ),
        fixture(
            id = "ADV-11",
            draft = completeDraft().copy(claimText = "The warm sample dissolved in 20 seconds in this observation."),
            status = ConclusionStatus.CANNOT_ASSESS,
            priority = ConclusionPriority.P1,
            field = ConclusionField.CLAIM_TEXT,
            code = "UNSUPPORTED_LITERAL",
        ),
        fixture(
            id = "ADV-12",
            draft = completeDraft().copy(
                claimText = "Warm water caused faster dissolution because the tablet was more soluble.",
                scope = ConclusionScope.GENERAL_CAUSAL_CLAIM,
            ),
            status = ConclusionStatus.ACTION_REQUIRED,
            priority = ConclusionPriority.P1,
            field = ConclusionField.SCOPE,
            code = "OVERCLAIM_SCOPE",
        ),
        fixture(
            id = "ADV-13",
            draft = completeDraft().copy(
                implication = ConclusionImplication.NOT_APPLICABLE,
                implicationReason = "This exercise is limited to reporting the supplied observation.",
            ),
        ),
    )

    private fun fixture(
        id: String,
        draft: ConclusionDraft,
        status: ConclusionStatus? = null,
        priority: ConclusionPriority? = null,
        field: ConclusionField? = null,
        code: String? = null,
    ) = ConclusionFixture(
        id = id,
        draft = draft,
        expectation = ConclusionFixtureExpectation(status, priority, field, code),
    )

    private fun completeDraft() = ConclusionDraft(
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
