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
    title = "Meet your student workspace.",
    body = "Discover projects, evidence links, optional AI, and exports in about 1–2 minutes. This introduction uses synthetic examples and does not create a project.",
    primaryLabel = "Meet Evidrilo",
    secondaryLabel = "Skip Get Started",
    freeBenefits = listOf(
        "Choose a project structure, connect your notes, and keep the limits of a claim in view.",
        "Explore optional AI help and local history. AI needs an eligible account and enabled services.",
        "Create and edit local projects without an account. Sign-in is only for account-bound services; cloud sync needs separate consent.",
    ),
)
