package dev.nextgen.mobile

data class EvidriloOnboardingPresentation(
    val isVisible: Boolean,
    val title: String,
    val body: String,
    val primaryLabel: String,
    val secondaryLabel: String,
    val freeBenefits: List<String>,
)

fun evidriloOnboardingPresentation(
    forceShow: Boolean = false,
): EvidriloOnboardingPresentation = EvidriloOnboardingPresentation(
    isVisible = forceShow,
    title = "Preview reasoning from evidence.",
    body = "Evidrilo helps students connect project requirements, sources, findings, and claims. This guided preview uses synthetic examples, stays separate from real work, and does not create a project.",
    primaryLabel = "Start guided preview",
    secondaryLabel = "Skip Get Started",
    freeBenefits = listOf(
        "No reviewed template is currently selectable; the walkthrough is clearly labeled as a demo.",
        "Follow one synthetic source through a finding, a bounded claim, and a next action.",
        "Create and edit local projects without an account. Sign-in is only for account-bound services; cloud sync needs separate consent.",
    ),
)
