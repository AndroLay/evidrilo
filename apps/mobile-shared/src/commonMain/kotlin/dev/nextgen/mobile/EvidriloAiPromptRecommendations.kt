package dev.nextgen.mobile

import dev.nextgen.mobile.ai.AiAssistPurpose
import dev.nextgen.mobile.domain.conclusion.ConclusionField
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus

internal data class EvidriloAiChatQuickAction(
    val purpose: AiAssistPurpose,
    val label: String,
    val prompt: String,
    val description: String,
)

internal data class EvidriloAiPromptRecommendationContext(
    val status: ConclusionStatus,
    val field: ConclusionField,
    val allowedAnchorIds: List<String>,
    val groundedAnchorIds: List<String>,
) {
    fun hasMatchingGrounding(): Boolean =
        allowedAnchorIds.any { it.isNotBlank() && it in groundedAnchorIds }
}

internal fun evidriloAiChatQuickActions(): List<EvidriloAiChatQuickAction> = listOf(
    EvidriloAiChatQuickAction(
        purpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
        label = "Explain this feedback",
        prompt = "Explain this verification feedback in plain language using only the supplied case anchors.",
        description = "Understand the current deterministic feedback.",
    ),
    EvidriloAiChatQuickAction(
        purpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
        label = "Why this action?",
        prompt = "Explain why the suggested next action follows from this feedback and its limitation.",
        description = "See how the next action connects to the current result.",
    ),
    EvidriloAiChatQuickAction(
        purpose = AiAssistPurpose.REFLECTION_QUESTION,
        label = "Help me reflect",
        prompt = "Ask me one grounded question that helps me review my claim against the selected evidence and limitations.",
        description = "Get one bounded question to guide your own revision.",
    ),
)

/**
 * Chooses follow-up prompts from typed evaluator state and the last assist
 * purpose. It never reads or interprets free-form model output.
 */
internal fun evidriloAiNextPromptRecommendations(
    context: EvidriloAiPromptRecommendationContext,
    previousPurpose: AiAssistPurpose,
): List<EvidriloAiChatQuickAction> {
    if (!context.hasMatchingGrounding()) return emptyList()

    val candidates = when (context.status) {
        ConclusionStatus.INCOMPLETE,
        ConclusionStatus.CANNOT_ASSESS,
        -> listOf(explanationPrompt(context), reflectionPrompt(context))

        ConclusionStatus.ACTION_REQUIRED -> when (context.field) {
            ConclusionField.CLAIM_TEXT,
            ConclusionField.SCOPE,
            ConclusionField.LIMITATION_REFS,
            ConclusionField.LIMITATION_NOTE,
            ConclusionField.IMPLICATION,
            ConclusionField.IMPLICATION_REASON,
            -> listOf(reflectionPrompt(context), languagePrompt(context))

            else -> listOf(explanationPrompt(context), reflectionPrompt(context))
        }

        ConclusionStatus.PASS -> listOf(reflectionPrompt(context), languagePrompt(context))
    }

    return candidates
        .filterNot { it.purpose == previousPurpose }
        .distinctBy { it.prompt }
        .take(2)
}

private fun explanationPrompt(
    context: EvidriloAiPromptRecommendationContext,
): EvidriloAiChatQuickAction {
    val prompt = when (context.status) {
        ConclusionStatus.INCOMPLETE ->
            "Explain which required part is still incomplete and point to the relevant case anchor."

        ConclusionStatus.CANNOT_ASSESS ->
            "Explain what information is missing before this feedback can be assessed safely. Do not fill in missing facts."

        else ->
            "Explain how the current verification feedback follows from the supplied evidence and limitations."
    }
    return EvidriloAiChatQuickAction(
        purpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
        label = if (context.status == ConclusionStatus.CANNOT_ASSESS) "Why can't this be assessed?" else "Explain this result",
        prompt = prompt,
        description = "Clarify the current evaluator result without changing it.",
    )
}

private fun reflectionPrompt(
    context: EvidriloAiPromptRecommendationContext,
): EvidriloAiChatQuickAction {
    val prompt = when (context.status) {
        ConclusionStatus.INCOMPLETE ->
            "Ask me which required part I should complete first; do not suggest rewriting around missing information."

        ConclusionStatus.CANNOT_ASSESS ->
            "Ask me one question about what information would be needed to assess this safely. Do not invent that information."

        ConclusionStatus.PASS ->
            "Ask me one question about what these observations still do not let me claim."

        ConclusionStatus.ACTION_REQUIRED -> when (context.field) {
            ConclusionField.EVIDENCE_REFS ->
                "Ask me which supplied observation supports the requirement; do not suggest new evidence."

            ConclusionField.SCOPE,
            ConclusionField.CLAIM_TEXT,
            -> "Ask me which part of my claim may go beyond the selected observations."

            ConclusionField.LIMITATION_REFS,
            ConclusionField.LIMITATION_NOTE,
            -> "Ask me how this limitation should narrow what I claim."

            ConclusionField.IMPLICATION,
            ConclusionField.IMPLICATION_REASON,
            -> "Ask me what next check could address the stated limitation."

            else -> "Ask me one question about what I should reconsider before revising."
        }
    }
    return EvidriloAiChatQuickAction(
        purpose = AiAssistPurpose.REFLECTION_QUESTION,
        label = when (context.status) {
            ConclusionStatus.INCOMPLETE -> "Complete the missing part"
            ConclusionStatus.CANNOT_ASSESS -> "Identify what's missing"
            ConclusionStatus.PASS -> "Check the claim boundary"
            ConclusionStatus.ACTION_REQUIRED -> "Reconsider the claim boundary"
        },
        prompt = prompt,
        description = "Get one question grounded in the current result.",
    )
}

private fun languagePrompt(
    context: EvidriloAiPromptRecommendationContext,
): EvidriloAiChatQuickAction {
    val (label, prompt) = when (context.field) {
        ConclusionField.LIMITATION_REFS,
        ConclusionField.LIMITATION_NOTE,
        -> "Clarify the limitation" to
            "Suggest clearer wording for my limitation note without adding facts or weakening the evidence boundary."

        ConclusionField.IMPLICATION,
        ConclusionField.IMPLICATION_REASON,
        -> "Clarify the next action" to
            "Suggest clearer wording for my next-action reason while keeping it tied to the stated limitation."

        else -> "Try narrower wording" to
            "Suggest a clearer, more carefully scoped version of my claim using only the selected evidence and limitations. Do not add facts."
    }
    return EvidriloAiChatQuickAction(
        purpose = AiAssistPurpose.LANGUAGE_ALTERNATIVE,
        label = label,
        prompt = prompt,
        description = "Review an optional wording suggestion; it will not edit your draft automatically.",
    )
}
