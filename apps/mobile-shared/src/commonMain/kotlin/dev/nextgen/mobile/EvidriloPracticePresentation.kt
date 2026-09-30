package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope

/** Presentation-only checkpoints. Submission and evaluation stay in ConclusionReducer. */
internal enum class EvidriloPracticeTask(val label: String, val title: String, val prompt: String) {
    EVIDENCE("Evidence", "Start with the evidence.", "Tap the observations you want to use in your comparison."),
    RELATION("Connection", "What are you saying?", "Choose the relationship your conclusion will describe."),
    CLAIM("Your claim", "Put it in your own words.", "Write a short claim that follows from your selected evidence."),
    SCOPE("Scope", "How far does it go?", "Choose how broadly your claim applies."),
    LIMITS("Limits", "Give the claim its limits.", "Choose a supplied limitation and explain why it matters."),
    ACTION("Next action", "Turn a limit into a next move.", "Choose what to do next, and explain your reason."),
}

// These input lengths mirror the existing bounded evaluator, not a new rubric.
internal const val PRACTICE_CLAIM_MAX = 320
internal const val PRACTICE_NOTE_MAX = 240

internal fun practiceStartingTask(initialStep: EvidriloDraftStep, draft: ConclusionDraft): EvidriloPracticeTask =
    when (initialStep) {
        EvidriloDraftStep.CLAIM -> EvidriloPracticeTask.CLAIM
        EvidriloDraftStep.LIMITS -> when {
            draft.limitationRefs.isEmpty() || draft.limitationNote.trim().length !in 10..PRACTICE_NOTE_MAX ->
                EvidriloPracticeTask.LIMITS
            else -> EvidriloPracticeTask.ACTION
        }
        EvidriloDraftStep.EVIDENCE -> when {
            draft.evidenceRefs.isEmpty() -> EvidriloPracticeTask.EVIDENCE
            draft.relation == null -> EvidriloPracticeTask.RELATION
            draft.claimText.trim().length !in 20..PRACTICE_CLAIM_MAX -> EvidriloPracticeTask.CLAIM
            draft.scope == null -> EvidriloPracticeTask.SCOPE
            draft.limitationRefs.isEmpty() || draft.limitationNote.trim().length !in 10..PRACTICE_NOTE_MAX ->
                EvidriloPracticeTask.LIMITS
            draft.implication == null || draft.implicationReason.trim().length !in 10..PRACTICE_NOTE_MAX ->
                EvidriloPracticeTask.ACTION
            else -> EvidriloPracticeTask.EVIDENCE
        }
    }

internal fun practiceInputPrompt(task: EvidriloPracticeTask, draft: ConclusionDraft): String? = when (task) {
    EvidriloPracticeTask.EVIDENCE -> when {
        draft.evidenceRefs.isEmpty() -> "Select at least one observation to continue."
        draft.evidenceRefs.size > 3 -> "Keep no more than three evidence links."
        else -> null
    }
    EvidriloPracticeTask.RELATION -> if (draft.relation == null || draft.relation == ConclusionRelation.UNSUPPORTED)
        "Choose a relationship to continue." else null
    EvidriloPracticeTask.CLAIM -> when {
        draft.claimText.trim().length < 20 -> "Write at least 20 characters in your own words."
        draft.claimText.trim().length > PRACTICE_CLAIM_MAX -> "Keep the claim within 320 characters."
        else -> null
    }
    EvidriloPracticeTask.SCOPE -> if (draft.scope == null || draft.scope == ConclusionScope.UNSUPPORTED)
        "Choose the scope of your claim." else null
    EvidriloPracticeTask.LIMITS -> when {
        draft.limitationRefs.isEmpty() -> "Select at least one supplied limitation."
        draft.limitationRefs.size > 2 -> "Keep no more than two limitation links."
        draft.limitationNote.trim().length !in 10..PRACTICE_NOTE_MAX -> "Explain the limit in 10–240 characters."
        else -> null
    }
    EvidriloPracticeTask.ACTION -> when {
        draft.implication == null || draft.implication == ConclusionImplication.UNSUPPORTED -> "Choose a next action."
        draft.implicationReason.trim().length !in 10..PRACTICE_NOTE_MAX -> "Explain your reason in 10–240 characters."
        else -> null
    }
}

internal fun ConclusionRelation.practiceLabel(): String = when (this) {
    ConclusionRelation.OBSERVED_DIFFERENCE -> "Compare an observed difference"
    ConclusionRelation.LIMITED_OBSERVATION -> "Describe a limited observation"
    ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE -> "Say the case is insufficient"
    ConclusionRelation.UNSUPPORTED -> "Unsupported relationship"
}

internal fun ConclusionRelation.practiceDescription(): String = when (this) {
    ConclusionRelation.OBSERVED_DIFFERENCE -> "Describe a difference between selected records."
    ConclusionRelation.LIMITED_OBSERVATION -> "Keep the wording close to a specific observation."
    ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE -> "Explain why these records do not support the conclusion."
    ConclusionRelation.UNSUPPORTED -> "Choose a supported relationship."
}

internal fun ConclusionScope.practiceLabel(): String = when (this) {
    ConclusionScope.THIS_OBSERVATION -> "These observations only"
    ConclusionScope.LIMITED_COMPARISON -> "A limited comparison"
    ConclusionScope.GENERAL_CAUSAL_CLAIM -> "A general causal claim"
    ConclusionScope.UNSUPPORTED -> "Unsupported scope"
}

internal fun ConclusionScope.practiceDescription(): String = when (this) {
    ConclusionScope.THIS_OBSERVATION -> "Describe what was recorded in this case."
    ConclusionScope.LIMITED_COMPARISON -> "Compare the supplied conditions, with their limits."
    ConclusionScope.GENERAL_CAUSAL_CLAIM -> "Claim that a cause applies beyond these records."
    ConclusionScope.UNSUPPORTED -> "Choose a supported scope."
}

internal fun ConclusionImplication.practiceLabel(): String = when (this) {
    ConclusionImplication.REPEAT_TRIALS -> "Repeat the trials"
    ConclusionImplication.CONTROL_STIRRING -> "Measure and control stirring"
    ConclusionImplication.LIMIT_CLAIM -> "Narrow the claim"
    ConclusionImplication.NOT_APPLICABLE -> "No follow-up applies"
    ConclusionImplication.UNSUPPORTED -> "Unsupported action"
}

internal fun practiceActionDescription(action: ConclusionImplication, case: ConclusionCase): String =
    case.requiredLimitationId(action)?.let(case::fact)?.text ?: when (action) {
        ConclusionImplication.LIMIT_CLAIM -> "Bring the wording back within the available evidence."
        ConclusionImplication.NOT_APPLICABLE -> "Explain why no next action is applicable."
        else -> "Give a reason tied to a supplied limitation."
    }
