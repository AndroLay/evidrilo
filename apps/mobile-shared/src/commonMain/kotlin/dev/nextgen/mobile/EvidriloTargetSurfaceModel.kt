package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionState

internal data class TargetWorkspaceMetrics(
    val requirementCount: Int,
    val evidenceCount: Int,
    val gapCount: Int,
    val actionCount: Int,
    val coveragePercent: Int,
)

internal fun targetWorkspaceMetrics(
    case: ConclusionCase,
    draft: ConclusionDraft,
): TargetWorkspaceMetrics {
    val observationIds = case.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }
    val hasEvidence = draft.evidenceRefs.any { it in observationIds }
    val hasClaim = draft.claimText.trim().length >= 20
    val hasScope = draft.scope != null
    val hasLimit = draft.limitationRefs.isNotEmpty() || draft.limitationNote.isNotBlank()
    val hasAction = draft.implication != null && draft.implicationReason.isNotBlank()
    val completedSignals = listOf(hasEvidence, hasClaim, hasScope, hasLimit, hasAction).count { it }

    return TargetWorkspaceMetrics(
        requirementCount = 1,
        evidenceCount = case.facts.size,
        gapCount = if (completedSignals == 5) 0 else 1,
        actionCount = 1,
        coveragePercent = completedSignals * 20,
    )
}

internal fun targetDraftFor(
    state: ConclusionState,
    case: ConclusionCase,
): ConclusionDraft = when (state) {
    ConclusionState.Intro -> ConclusionDraft(caseId = case.id)
    is ConclusionState.Drafting -> state.draft
    is ConclusionState.Feedback -> state.draft
    is ConclusionState.Revision -> state.draft
    is ConclusionState.Summary -> state.revisedDraft
    is ConclusionState.EvidenceChangeDrafting -> state.draft
    is ConclusionState.EvidenceChangeFeedback -> state.draft
    is ConclusionState.EvidenceChangeSummary -> state.challengeDraft
    is ConclusionState.Incomplete -> state.draft
}
