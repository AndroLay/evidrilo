package dev.nextgen.mobile.domain.askready

fun AskReadyCriterion.displayLabel(): String = when (this) {
    AskReadyCriterion.SPECIFIC_OBJECT -> "Specific object"
    AskReadyCriterion.MINIMUM_CONTEXT -> "Minimum context"
    AskReadyCriterion.ATTEMPT -> "What you tried"
    AskReadyCriterion.CONCRETE_ASK -> "Concrete ask"
    AskReadyCriterion.NEXT_STEP -> "Next step"
    AskReadyCriterion.PRIVACY_HYGIENE -> "Privacy hygiene"
}

fun AskReadyFeedbackState.displayLabel(): String = when (this) {
    AskReadyFeedbackState.PASS -> "Pass"
    AskReadyFeedbackState.NEEDS_WORK -> "Needs work"
    AskReadyFeedbackState.NOT_ENOUGH_CONTEXT -> "Not enough context"
}

fun AskReadyChannel.displayLabel(): String = when (this) {
    AskReadyChannel.INSTRUCTOR -> "Instructor"
    AskReadyChannel.TEACHING_ASSISTANT -> "Teaching assistant"
    AskReadyChannel.PEER -> "Peer"
    AskReadyChannel.OFFICE_HOUR -> "Office hour"
}

fun AskReadyScenario.displayLabel(): String = when (this) {
    AskReadyScenario.ASSIGNMENT -> "Assignment"
    AskReadyScenario.LAB_CODE -> "Lab or code"
    AskReadyScenario.CONCEPT_CLARIFICATION -> "Concept clarification"
    AskReadyScenario.ACADEMIC_PROCESS -> "Academic process"
}

fun buildAskReadyPreview(draft: AskReadyDraft): String = buildString {
    appendLine("Object: ${draft.specificObject.valueOrPlaceholder()}")
    appendLine("Context: ${draft.context.valueOrPlaceholder()}")
    appendLine("Tried: ${draft.attempt.valueOrPlaceholder()}")
    appendLine("Ask: ${draft.concreteAsk.valueOrPlaceholder()}")
    appendLine("Channel: ${draft.channel?.displayLabel() ?: "Not selected"}")
    appendLine("Next step: ${draft.nextStep.valueOrPlaceholder()}")
    append("This helps you prepare a request; it does not predict approval.")
}

private fun String.valueOrPlaceholder(): String =
    trim().ifEmpty { "Not provided" }
