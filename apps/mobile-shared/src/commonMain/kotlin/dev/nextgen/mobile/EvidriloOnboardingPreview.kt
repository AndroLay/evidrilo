package dev.nextgen.mobile

import dev.nextgen.mobile.domain.onboarding.GetStartedTourStep

internal enum class GetStartedStartingPoint(val label: String, val previewTitle: String) {
    ASSIGNMENT("Assignment or topic", "Your assignment or topic"),
    QUESTION("Research question", "Your research question"),
    BLANK("Start blank", "Start blank"),
}

internal enum class GetStartedFirstMaterial(val label: String, val previewTitle: String) {
    SOURCES("Sources", "Sources"),
    OBSERVATIONS("Observations or notes", "Observations or notes"),
    NOT_SURE("I’m not sure yet", "Choose later"),
}

internal enum class GetStartedReviewFocus(val label: String, val previewTitle: String) {
    TRACE("Trace a claim", "Trace a claim"),
    LIMITATIONS("Notice limitations", "Notice limitations"),
    CHANGES("Review what changes", "Review what changes"),
}

internal data class GetStartedTemporaryPreview(
    val startingPoint: GetStartedStartingPoint = GetStartedStartingPoint.ASSIGNMENT,
    val firstMaterial: GetStartedFirstMaterial = GetStartedFirstMaterial.SOURCES,
    val reviewFocus: GetStartedReviewFocus = GetStartedReviewFocus.TRACE,
)

internal data class GetStartedPreviewContent(
    val title: String,
    val detail: String,
    val disclaimer: String = "Temporary preview · nothing is saved",
)

internal fun getStartedPreviewFor(
    step: GetStartedTourStep,
    preview: GetStartedTemporaryPreview,
): GetStartedPreviewContent = when (step) {
    GetStartedTourStep.WELCOME -> GetStartedPreviewContent(
        title = preview.startingPoint.previewTitle,
        detail = "Next, choose what you may add first—or decide later.",
    )
    GetStartedTourStep.ORGANIZE -> GetStartedPreviewContent(
        title = preview.firstMaterial.previewTitle,
        detail = "This is a starting point, not a saved source or finding.",
    )
    GetStartedTourStep.REVIEW -> GetStartedPreviewContent(
        title = preview.reviewFocus.previewTitle,
        detail = "You stay in control of the interpretation and any next step.",
    )
    GetStartedTourStep.ASSISTANCE -> GetStartedPreviewContent(
        title = "Optional AI help",
        detail = "Review suggestions before using them. Availability depends on your account and enabled services.",
    )
    GetStartedTourStep.PORTABILITY -> GetStartedPreviewContent(
        title = "History and exports",
        detail = "Keep local revisions, export a report, or move a project using an Evidrilo archive.",
    )
    GetStartedTourStep.READY -> GetStartedPreviewContent(
        title = "Your next question",
        detail = "Open My Projects to create your own project explicitly.",
    )
}
