package dev.nextgen.mobile.domain.conclusion

enum class ConclusionRelation {
    OBSERVED_DIFFERENCE,
    LIMITED_OBSERVATION,
    CANNOT_CONCLUDE_FROM_CASE,

    /** Only used when an untrusted adapter cannot map an incoming value. */
    UNSUPPORTED,
}

enum class ConclusionScope {
    THIS_OBSERVATION,
    LIMITED_COMPARISON,
    GENERAL_CAUSAL_CLAIM,

    /** Only used when an untrusted adapter cannot map an incoming value. */
    UNSUPPORTED,
}

enum class ConclusionImplication {
    REPEAT_TRIALS,
    CONTROL_STIRRING,
    LIMIT_CLAIM,
    NOT_APPLICABLE,

    /** Only used when an untrusted adapter cannot map an incoming value. */
    UNSUPPORTED,
}

data class ConclusionDraft(
    val caseId: String = EVIDRILO_M0_T2_CASE_ID,
    val relation: ConclusionRelation? = null,
    val evidenceRefs: List<String> = emptyList(),
    val claimText: String = "",
    val scope: ConclusionScope? = null,
    val limitationRefs: List<String> = emptyList(),
    val limitationNote: String = "",
    val implication: ConclusionImplication? = null,
    val implicationReason: String = "",
)
