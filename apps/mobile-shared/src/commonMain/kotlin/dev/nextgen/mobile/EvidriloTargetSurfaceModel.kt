package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionReducer
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.navigation.EvidriloDestination
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.ConclusionSessionPhase

internal enum class TargetEvidenceStatus {
    NOT_ASSESSED,
    PARTIALLY_SUPPORTED,
    SUPPORTED,
}

internal data class TargetEvidenceDelta(
    val addedEvidenceIds: List<String>,
    val removedEvidenceIds: List<String>,
    val claimChanged: Boolean,
    val scopeChanged: Boolean,
    val limitationsChanged: Boolean,
    val actionChanged: Boolean,
) {
    val hasChanges: Boolean
        get() = addedEvidenceIds.isNotEmpty() ||
            removedEvidenceIds.isNotEmpty() ||
            claimChanged ||
            scopeChanged ||
            limitationsChanged ||
            actionChanged
}

internal data class TargetWorkspaceMetrics(
    val requirementCount: Int,
    val evidenceCount: Int,
    val gapCount: Int,
    val actionCount: Int,
    val coveragePercent: Int,
)

/**
 * The target shell's user-visible journey. Practice remains a reducer-owned
 * stateful workflow; the surrounding destinations are only navigation
 * surfaces over the current local projection.
 */
internal fun targetJourneyDestinations(): List<EvidriloDestination> = listOf(
    EvidriloDestination.HOME,
    EvidriloDestination.SOURCES,
    EvidriloDestination.WORKSPACE,
    EvidriloDestination.EVIDENCE,
    EvidriloDestination.CLAIM_TRACE,
    EvidriloDestination.ACTION,
    EvidriloDestination.VERIFY_CLAIM,
    EvidriloDestination.PRACTICE,
    EvidriloDestination.EVIDENCE_DELTA,
    EvidriloDestination.HISTORY,
)

internal fun targetEvaluationFor(state: ConclusionState): ConclusionEvaluation? = when (state) {
    ConclusionState.Intro,
    is ConclusionState.Drafting,
    is ConclusionState.Revision,
    -> null
    is ConclusionState.Incomplete -> null
    is ConclusionState.Feedback -> state.evaluation
    is ConclusionState.Summary -> state.finalEvaluation
    is ConclusionState.EvidenceChangeDrafting -> null
    is ConclusionState.EvidenceChangeFeedback -> state.evaluation
    is ConclusionState.EvidenceChangeSummary -> state.challengeEvaluation
}

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
        evidenceCount = observationIds.size,
        gapCount = if (completedSignals == 5) 0 else 1,
        actionCount = 1,
        coveragePercent = completedSignals * 20,
    )
}

internal fun targetEvidenceStatus(
    case: ConclusionCase,
    draft: ConclusionDraft,
): TargetEvidenceStatus {
    val observations = case.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }.toSet()
    val selected = draft.evidenceRefs.toSet().intersect(observations)
    val evaluation = ConclusionReducer(case = case).evaluate(draft)
    return when {
        selected.isEmpty() -> TargetEvidenceStatus.NOT_ASSESSED
        evaluation.primaryFeedback?.status == ConclusionStatus.PASS -> TargetEvidenceStatus.SUPPORTED
        else -> TargetEvidenceStatus.PARTIALLY_SUPPORTED
    }
}

internal fun targetEvidenceDelta(
    before: ConclusionDraft,
    after: ConclusionDraft,
): TargetEvidenceDelta = TargetEvidenceDelta(
    addedEvidenceIds = after.evidenceRefs.filterNot { it in before.evidenceRefs },
    removedEvidenceIds = before.evidenceRefs.filterNot { it in after.evidenceRefs },
    claimChanged = before.claimText != after.claimText,
    scopeChanged = before.scope != after.scope,
    limitationsChanged = before.limitationRefs != after.limitationRefs ||
        before.limitationNote != after.limitationNote,
    actionChanged = before.implication != after.implication ||
        before.implicationReason != after.implicationReason,
)

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

internal fun targetHistorySubtitle(history: ConclusionSessionSnapshot?): String = when {
    history == null -> "No comparison saved yet"
    history.phase == ConclusionSessionPhase.REVISION -> "One revision is ready to review"
    else -> "Review your latest before/after comparison"
}
