package dev.nextgen.mobile

data class EvidriloFreeLearningPolicy(
    val caseCount: Int,
    val localReplayUnlimited: Boolean,
    val completeFeedback: Boolean,
    val revisionsPerSession: Int,
    val evidenceChallenge: Boolean,
    val beforeAfterComparison: Boolean,
)

/**
 * Product boundary for the free core. This is deliberately explicit so a
 * future quota, paywall, or remote-config change cannot silently make the
 * learning path uncomfortable.
 */
val EVIDRILO_FREE_LEARNING_POLICY = EvidriloFreeLearningPolicy(
    caseCount = 1,
    localReplayUnlimited = true,
    completeFeedback = true,
    revisionsPerSession = 1,
    evidenceChallenge = true,
    beforeAfterComparison = true,
)

data class EvidriloOnboardingPresentation(
    val isVisible: Boolean,
    val title: String,
    val body: String,
    val primaryLabel: String,
    val secondaryLabel: String,
    val freeBenefits: List<String>,
)

fun evidriloOnboardingPresentation(
    completed: Boolean,
    hasSavedPractice: Boolean,
    forceShow: Boolean = false,
): EvidriloOnboardingPresentation = EvidriloOnboardingPresentation(
    isVisible = forceShow || (!completed && !hasSavedPractice),
    title = "Learn from the evidence, one bounded case at a time.",
    body = "Evidrilo helps you connect observations to a careful conclusion, see what the evidence supports, and revise once with useful feedback.",
    primaryLabel = "Try the free case",
    secondaryLabel = "Skip introduction",
    freeBenefits = listOf(
        "One complete case with unlimited local replay and reset.",
        "Complete feedback, an evidence-change challenge, and a before/after comparison.",
        "One focused revision per session; no login is required to begin.",
    ),
)
