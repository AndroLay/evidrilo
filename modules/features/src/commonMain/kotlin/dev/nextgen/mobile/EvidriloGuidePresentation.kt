package dev.nextgen.mobile

enum class EvidriloGuideTopic {
    SUPPORTING_EVIDENCE,
    CLAIM_BOUNDS,
    FEEDBACK_REVISION,
}

data class EvidriloGuideTopicCopy(
    val title: String,
    val subtitle: String,
    val body: String,
)

val evidriloGuideTopics: List<EvidriloGuideTopic> = listOf(
    EvidriloGuideTopic.SUPPORTING_EVIDENCE,
    EvidriloGuideTopic.CLAIM_BOUNDS,
    EvidriloGuideTopic.FEEDBACK_REVISION,
)

fun guideTopicCopy(topic: EvidriloGuideTopic): EvidriloGuideTopicCopy = when (topic) {
    EvidriloGuideTopic.SUPPORTING_EVIDENCE -> EvidriloGuideTopicCopy(
        title = "Choose supporting evidence",
        subtitle = "Use observations, not assumptions.",
        body = "Select the supplied observation facts that directly support the relationship you want to describe. A selected fact is an anchor for your writing, not proof that the conclusion is correct.",
    )
    EvidriloGuideTopic.CLAIM_BOUNDS -> EvidriloGuideTopicCopy(
        title = "Keep the claim in bounds",
        subtitle = "Say only what the case can support.",
        body = "Choose a scope that matches the supplied observations and name the limitations that still matter. The case can support a bounded comparison without establishing a general causal effect.",
    )
    EvidriloGuideTopic.FEEDBACK_REVISION -> EvidriloGuideTopicCopy(
        title = "Read feedback, then revise",
        subtitle = "Inspect the reason. Revise once.",
        body = "Feedback points to a specific field and its fact anchors. Use that reason to make one learner-authored revision; the original draft remains available for comparison.",
    )
}
