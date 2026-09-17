package dev.nextgen.mobile.domain.askready

/**
 * Reviews only the structure and privacy boundary of a help request.
 * It deliberately does not answer academic questions or predict a recipient's
 * response.
 */
class AskReadyFeedbackEngine {
    fun evaluate(draft: AskReadyDraft): AskReadyFeedbackReport {
        val items = listOf(
            evaluateSpecificObject(draft.specificObject),
            evaluateMinimumContext(draft.context),
            evaluateAttempt(draft.attempt),
            evaluateConcreteAsk(draft.concreteAsk),
            evaluateNextStep(draft.nextStep, draft.channel),
            evaluatePrivacy(draft),
        )

        return AskReadyFeedbackReport(
            items = items,
            overallState = if (items.all { it.state == AskReadyFeedbackState.PASS }) {
                AskReadyFeedbackState.PASS
            } else {
                AskReadyFeedbackState.NEEDS_WORK
            },
        )
    }

    private fun evaluateSpecificObject(value: String): AskReadyFeedbackItem =
        when {
            value.isBlank() -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.SPECIFIC_OBJECT,
                state = AskReadyFeedbackState.NOT_ENOUGH_CONTEXT,
                message = "Name the exact assignment, concept, or step you need help with.",
            )

            value.trim().length < MINIMUM_PRESENT_LENGTH -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.SPECIFIC_OBJECT,
                state = AskReadyFeedbackState.NEEDS_WORK,
                message = "Add enough detail to identify the exact task or concept.",
            )

            else -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.SPECIFIC_OBJECT,
                state = AskReadyFeedbackState.PASS,
                message = "The request names a specific object.",
            )
        }

    private fun evaluateMinimumContext(value: String): AskReadyFeedbackItem =
        when {
            value.isBlank() -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.MINIMUM_CONTEXT,
                state = AskReadyFeedbackState.NOT_ENOUGH_CONTEXT,
                message = "Add the minimum course or task context needed to understand the request.",
            )

            value.trim().length < MINIMUM_PRESENT_LENGTH -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.MINIMUM_CONTEXT,
                state = AskReadyFeedbackState.NEEDS_WORK,
                message = "Add useful context, but do not paste the whole assignment.",
            )

            else -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.MINIMUM_CONTEXT,
                state = AskReadyFeedbackState.PASS,
                message = "The context is sufficient without requiring the full assignment.",
            )
        }

    private fun evaluateAttempt(value: String): AskReadyFeedbackItem =
        when {
            value.isBlank() -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.ATTEMPT,
                state = AskReadyFeedbackState.NOT_ENOUGH_CONTEXT,
                message = "Describe one thing you tried and where it stopped working.",
            )

            value.trim().length < MINIMUM_PRESENT_LENGTH -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.ATTEMPT,
                state = AskReadyFeedbackState.NEEDS_WORK,
                message = "Explain what you tried and what happened.",
            )

            else -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.ATTEMPT,
                state = AskReadyFeedbackState.PASS,
                message = "The request explains an attempt and its result.",
            )
        }

    private fun evaluateConcreteAsk(value: String): AskReadyFeedbackItem {
        val normalized = value.trim().lowercase()
        val isGeneric = GENERIC_ASKS.any { normalized == it }

        return when {
            value.isBlank() -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.CONCRETE_ASK,
                state = AskReadyFeedbackState.NOT_ENOUGH_CONTEXT,
                message = "State one specific question or type of help you need.",
            )

            isGeneric || value.trim().length < MINIMUM_PRESENT_LENGTH -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.CONCRETE_ASK,
                state = AskReadyFeedbackState.NEEDS_WORK,
                message = "Turn a general request into one answerable question or type of help.",
            )

            else -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.CONCRETE_ASK,
                state = AskReadyFeedbackState.PASS,
                message = "The request asks for a concrete kind of help.",
            )
        }
    }

    private fun evaluateNextStep(
        value: String,
        channel: AskReadyChannel?,
    ): AskReadyFeedbackItem =
        when {
            value.isBlank() || channel == null -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.NEXT_STEP,
                state = AskReadyFeedbackState.NOT_ENOUGH_CONTEXT,
                message = "Choose a channel and state the next step or response you need.",
            )

            value.trim().length < MINIMUM_PRESENT_LENGTH -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.NEXT_STEP,
                state = AskReadyFeedbackState.NEEDS_WORK,
                message = "Describe the next step or response that would help.",
            )

            else -> AskReadyFeedbackItem(
                criterion = AskReadyCriterion.NEXT_STEP,
                state = AskReadyFeedbackState.PASS,
                message = "The request includes a next step and a chosen channel.",
            )
        }

    private fun evaluatePrivacy(draft: AskReadyDraft): AskReadyFeedbackItem {
        val allText = listOf(
            draft.specificObject,
            draft.context,
            draft.attempt,
            draft.concreteAsk,
            draft.nextStep,
        ).joinToString(" ")

        return if (SENSITIVE_DETAIL_PATTERNS.any { it.containsMatchIn(allText) }) {
            AskReadyFeedbackItem(
                criterion = AskReadyCriterion.PRIVACY_HYGIENE,
                state = AskReadyFeedbackState.NEEDS_WORK,
                message = "Remove unnecessary private details before sharing this request.",
            )
        } else {
            AskReadyFeedbackItem(
                criterion = AskReadyCriterion.PRIVACY_HYGIENE,
                state = AskReadyFeedbackState.PASS,
                message = "No obvious unnecessary private detail was detected.",
            )
        }
    }

    private companion object {
        const val MINIMUM_PRESENT_LENGTH = 8

        val GENERIC_ASKS = setOf(
            "can you help?",
            "can you help",
            "please help",
            "help me",
            "i need help",
        )

        val SENSITIVE_DETAIL_PATTERNS = listOf(
            Regex("(?i)\\b(student\\s*(id|number)|nim)\\s*(?:is\\s*)?[:#-]?\\s*\\d{4,}\\b"),
            Regex("(?i)\\b(password|passcode|api\\s*key|access\\s*token)\\b"),
            Regex("(?i)\\b(health|medical)\\s+(record|details|information)\\b"),
        )
    }
}
