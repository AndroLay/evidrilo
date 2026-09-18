package dev.nextgen.mobile.audio

import dev.nextgen.mobile.EvidriloOnboardingPresentation
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.evidriloGuideTopics
import dev.nextgen.mobile.guideTopicCopy

/**
 * Stable, learner-safe narration copy for screens that do not contain draft
 * text. Keeping this copy in one place makes a future reviewed audio catalog
 * auditable against the visible UI copy.
 */
internal object AudioNarrationCopy {
    fun onboarding(presentation: EvidriloOnboardingPresentation): String = joinCopy(
        presentation.title,
        presentation.body,
        *presentation.freeBenefits.toTypedArray(),
    )

    fun guide(): String = joinCopy(
        "Evidence guide.",
        "A short way to move from an observation to a claim you can explain.",
        *evidriloGuideTopics.flatMap { topic ->
            val copy = guideTopicCopy(topic)
            listOf(copy.title, copy.subtitle, copy.body)
        }.toTypedArray(),
    )

    fun case(case: ConclusionCase): String = joinCopy(
        case.title,
        case.description,
        case.changeNotice,
        *case.facts.map { fact -> fact.text }.toTypedArray(),
    )

    fun feedback(): String = joinCopy(
        "See what the case supports.",
        "These checks are bounded to the supplied facts.",
        "They are not a grade or a claim that the real-world experiment is scientifically complete.",
        "Review the feedback and its fact anchors before making your one revision.",
    )

    fun revision(): String = joinCopy(
        "Revise once with the feedback.",
        "Use the feedback reason to make one learner-authored revision.",
        "The original draft remains available for comparison.",
    )

    fun challenge(): String = joinCopy(
        "Evidence-change challenge.",
        "The cold-water observation is unavailable in this round.",
        "Re-evaluate the conclusion using only the observations still supplied.",
        "There is no second revision in this round.",
    )

    fun support(): String = joinCopy(
        "Support and billing.",
        "Restore a purchase from Premium; the store account is the source of truth for a completed transaction.",
        "Manage or cancel a subscription through the relevant app store or Customer Center when available.",
        "Refund requests must be made through the store that processed the purchase.",
        "Free drafts remain local, and account deletion is separate from clearing local workflow history.",
    )

    private fun joinCopy(vararg sections: String?): String = sections
        .filterNotNull()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" ")
}
