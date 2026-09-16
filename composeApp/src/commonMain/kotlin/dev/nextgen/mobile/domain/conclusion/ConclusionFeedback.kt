package dev.nextgen.mobile.domain.conclusion

enum class ConclusionCheck {
    GOAL_CONNECTEDNESS,
    EVIDENCE_ANCHORING,
    SCOPE_UNCERTAINTY,
    ACTIONABLE_IMPLICATION,
}

enum class ConclusionStatus {
    INCOMPLETE,
    ACTION_REQUIRED,
    PASS,
    CANNOT_ASSESS,
}

enum class ConclusionPriority {
    P0,
    P1,
    P2,
    P3,
}

enum class ConclusionField {
    CASE_ID,
    RELATION,
    EVIDENCE_REFS,
    CLAIM_TEXT,
    SCOPE,
    LIMITATION_REFS,
    LIMITATION_NOTE,
    IMPLICATION,
    IMPLICATION_REASON,
}

data class ConclusionCheckResult(
    val check: ConclusionCheck,
    val status: ConclusionStatus,
    val priority: ConclusionPriority? = null,
    val field: ConclusionField,
    val anchorIds: List<String>,
    val reason: String,
)

data class ConclusionFeedbackItem(
    val code: String,
    val status: ConclusionStatus,
    val priority: ConclusionPriority,
    val field: ConclusionField,
    val anchorIds: List<String>,
    val message: String,
    val why: String,
    val nextAction: String,
)

data class ConclusionEvaluation(
    val checks: List<ConclusionCheckResult>,
    val primaryFeedback: ConclusionFeedbackItem? = null,
)
