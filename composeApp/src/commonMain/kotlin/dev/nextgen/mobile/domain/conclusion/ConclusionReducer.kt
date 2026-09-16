package dev.nextgen.mobile.domain.conclusion

sealed interface ConclusionState {
    data object Intro : ConclusionState

    data class Drafting(
        val draft: ConclusionDraft,
        val validationMessage: String? = null,
    ) : ConclusionState

    data class Feedback(
        val initialDraft: ConclusionDraft,
        val draft: ConclusionDraft,
        val evaluation: ConclusionEvaluation,
        val canRevise: Boolean = true,
    ) : ConclusionState

    data class Revision(
        val initialDraft: ConclusionDraft,
        val draft: ConclusionDraft,
        val initialEvaluation: ConclusionEvaluation,
        val validationMessage: String? = null,
    ) : ConclusionState

    data class Summary(
        val initialDraft: ConclusionDraft,
        val revisedDraft: ConclusionDraft,
        val initialEvaluation: ConclusionEvaluation,
        val finalEvaluation: ConclusionEvaluation,
    ) : ConclusionState

    data class EvidenceChangeDrafting(
        val baseDraft: ConclusionDraft,
        val baseEvaluation: ConclusionEvaluation,
        val draft: ConclusionDraft,
        val validationMessage: String? = null,
    ) : ConclusionState

    data class EvidenceChangeFeedback(
        val baseDraft: ConclusionDraft,
        val baseEvaluation: ConclusionEvaluation,
        val draft: ConclusionDraft,
        val evaluation: ConclusionEvaluation,
    ) : ConclusionState

    data class EvidenceChangeSummary(
        val baseDraft: ConclusionDraft,
        val baseEvaluation: ConclusionEvaluation,
        val challengeDraft: ConclusionDraft,
        val challengeEvaluation: ConclusionEvaluation,
    ) : ConclusionState

    data class Incomplete(
        val draft: ConclusionDraft,
        val feedback: ConclusionFeedbackItem,
    ) : ConclusionState
}

sealed interface ConclusionEvent {
    data object Begin : ConclusionEvent

    data class UpdateDraft(val draft: ConclusionDraft) : ConclusionEvent

    data object Submit : ConclusionEvent

    data object BeginRevision : ConclusionEvent

    data object BeginEvidenceChange : ConclusionEvent

    data class UpdateEvidenceChangeDraft(val draft: ConclusionDraft) : ConclusionEvent

    data object SubmitEvidenceChange : ConclusionEvent

    data object FinishEvidenceChange : ConclusionEvent

    data object Reset : ConclusionEvent
}

class ConclusionReducer(
    private val case: ConclusionCase = ConclusionCases.M0_T2,
    private val evidenceChangeCase: ConclusionCase = ConclusionCases.EVIDENCE_CHANGE,
    private val evaluator: ConclusionEvaluator = ConclusionEvaluator(case),
    private val evidenceChangeEvaluator: ConclusionEvaluator = ConclusionEvaluator(evidenceChangeCase),
) {
    fun evaluate(draft: ConclusionDraft): ConclusionEvaluation = evaluator.evaluate(draft)

    fun evaluateEvidenceChange(draft: ConclusionDraft): ConclusionEvaluation =
        evidenceChangeEvaluator.evaluate(draft)

    fun reduce(
        state: ConclusionState,
        event: ConclusionEvent,
    ): ConclusionState = when (event) {
        ConclusionEvent.Begin ->
            if (state is ConclusionState.Intro) {
                ConclusionState.Drafting(ConclusionDraft(caseId = case.id))
            } else {
                state
            }

        is ConclusionEvent.UpdateDraft -> when (state) {
            is ConclusionState.Drafting -> state.copy(
                draft = event.draft,
                validationMessage = null,
            )

            is ConclusionState.Revision -> state.copy(
                draft = event.draft,
                validationMessage = null,
            )

            is ConclusionState.Incomplete -> ConclusionState.Drafting(event.draft)
            else -> state
        }

        is ConclusionEvent.UpdateEvidenceChangeDraft -> when (state) {
            is ConclusionState.EvidenceChangeDrafting -> state.copy(
                draft = event.draft,
                validationMessage = null,
            )

            else -> state
        }

        ConclusionEvent.Submit -> when (state) {
            is ConclusionState.Drafting -> submitInitial(state.draft)
            is ConclusionState.Revision -> submitRevision(state)
            else -> state
        }

        ConclusionEvent.BeginRevision ->
            if (state is ConclusionState.Feedback && state.canRevise) {
                ConclusionState.Revision(
                    initialDraft = state.initialDraft,
                    draft = state.draft,
                    initialEvaluation = state.evaluation,
                )
            } else {
                state
            }

        ConclusionEvent.BeginEvidenceChange ->
            if (state is ConclusionState.Summary) {
                ConclusionState.EvidenceChangeDrafting(
                    baseDraft = state.revisedDraft,
                    baseEvaluation = state.finalEvaluation,
                    draft = ConclusionDraft(caseId = evidenceChangeCase.id),
                )
            } else {
                state
            }

        ConclusionEvent.SubmitEvidenceChange -> when (state) {
            is ConclusionState.EvidenceChangeDrafting -> submitEvidenceChange(state)
            else -> state
        }

        ConclusionEvent.FinishEvidenceChange -> when (state) {
            is ConclusionState.EvidenceChangeFeedback -> ConclusionState.EvidenceChangeSummary(
                baseDraft = state.baseDraft,
                baseEvaluation = state.baseEvaluation,
                challengeDraft = state.draft,
                challengeEvaluation = state.evaluation,
            )

            else -> state
        }

        ConclusionEvent.Reset -> ConclusionState.Intro
    }

    private fun submitInitial(draft: ConclusionDraft): ConclusionState {
        val evaluation = evaluator.evaluate(draft)
        val primary = evaluation.primaryFeedback
        return if (primary?.status == ConclusionStatus.INCOMPLETE) {
            ConclusionState.Incomplete(draft = draft, feedback = primary)
        } else {
            ConclusionState.Feedback(
                initialDraft = draft,
                draft = draft,
                evaluation = evaluation,
            )
        }
    }

    private fun submitRevision(state: ConclusionState.Revision): ConclusionState {
        val evaluation = evaluator.evaluate(state.draft)
        val primary = evaluation.primaryFeedback
        return if (primary?.status == ConclusionStatus.INCOMPLETE) {
            state.copy(validationMessage = primary.message)
        } else {
            ConclusionState.Summary(
                initialDraft = state.initialDraft,
                revisedDraft = state.draft,
                initialEvaluation = state.initialEvaluation,
                finalEvaluation = evaluation,
            )
        }
    }

    private fun submitEvidenceChange(
        state: ConclusionState.EvidenceChangeDrafting,
    ): ConclusionState {
        val evaluation = evidenceChangeEvaluator.evaluate(state.draft)
        val primary = evaluation.primaryFeedback
        return if (primary?.status == ConclusionStatus.INCOMPLETE) {
            state.copy(validationMessage = primary.message)
        } else {
            ConclusionState.EvidenceChangeFeedback(
                baseDraft = state.baseDraft,
                baseEvaluation = state.baseEvaluation,
                draft = state.draft,
                evaluation = evaluation,
            )
        }
    }
}
