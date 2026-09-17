package dev.nextgen.mobile.domain.model

/**
 * The smallest useful input for a responsible deadline-extension rehearsal.
 *
 * This is deliberately plain text and local-only. The model does not attempt
 * to infer whether a request is truthful or whether a recipient will approve
 * it; it only represents what the user chose to make explicit.
 */
data class DeadlineDraft(
    val assignment: String = "",
    val completedWork: String = "",
    val requestedNextStep: String = "",
    val completionPlan: String = "",
    val context: String = "",
    val policyAcknowledgment: String = "",
)
