package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCheck
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionReducer
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.navigation.EvidriloDestination
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.ConclusionSessionPhase

internal enum class TargetEvidenceStatus {
    NOT_ASSESSED,
    PARTIALLY_SUPPORTED,
    SUPPORTED,
    UNAVAILABLE,
    CANNOT_ASSESS,
}

internal enum class TargetPageState {
    CONTENT,
    NOT_ASSESSED,
    LOADING,
    EMPTY,
    OFFLINE,
    UNAVAILABLE,
    ERROR,
    DISABLED,
    RECOVERY,
}

internal object EvidriloSourcesCopy {
    const val intro = "The bundled example is available offline. For your own work, open My Projects and add materials there."
    const val assignmentBriefSubtitle = "Requirement supplied with this case"
    const val rubricSubtitle = "Bounded checks for this case"
    const val sourcesSubtitle = "Bundled observations and stated limits"
    const val importBoundary = "Project attachments stay source material until you choose and record a finding."
}

internal object EvidriloTargetWorkspaceCopy {
    const val caseHeading = "Case workspace"
}

internal fun TargetPageState.targetDisplayLabel(): String = when (this) {
    TargetPageState.CONTENT -> "Ready"
    TargetPageState.NOT_ASSESSED -> "Not assessed"
    TargetPageState.LOADING -> "Loading"
    TargetPageState.EMPTY -> "Nothing here yet"
    TargetPageState.OFFLINE -> "Offline"
    TargetPageState.UNAVAILABLE -> "Unavailable"
    TargetPageState.ERROR -> "Something went wrong"
    TargetPageState.DISABLED -> "Action unavailable"
    TargetPageState.RECOVERY -> "Recovery needed"
}

internal fun ConclusionScope.targetDisplayLabel(): String = when (this) {
    ConclusionScope.THIS_OBSERVATION -> "Only this observation"
    ConclusionScope.LIMITED_COMPARISON -> "This limited comparison"
    ConclusionScope.GENERAL_CAUSAL_CLAIM -> "A general causal claim"
    ConclusionScope.UNSUPPORTED -> "Unsupported scope"
}

internal data class TargetHistorySummary(
    val evidenceAdded: Int,
    val evidenceRemoved: Int,
    val actionsChanged: Int,
    val hasComparison: Boolean,
) {
    internal companion object {
        val EMPTY = TargetHistorySummary(
            evidenceAdded = 0,
            evidenceRemoved = 0,
            actionsChanged = 0,
            hasComparison = false,
        )
    }
}

internal fun targetSectionLabel(section: EvidriloTargetSection): String = when (section) {
    EvidriloTargetSection.HOME -> "Home"
    EvidriloTargetSection.SOURCES -> "Sources"
    EvidriloTargetSection.EVIDENCE -> "Evidence"
    EvidriloTargetSection.ACTION -> "Action"
    EvidriloTargetSection.PROFILE -> "Profile"
}

internal fun targetNavigationSections(selected: EvidriloTargetSection): List<EvidriloTargetSection> =
    if (selected == EvidriloTargetSection.PROFILE) {
        emptyList()
    } else {
        listOf(
            EvidriloTargetSection.HOME,
            EvidriloTargetSection.SOURCES,
            EvidriloTargetSection.EVIDENCE,
            EvidriloTargetSection.ACTION,
        )
    }

internal fun shouldShowTargetBottomNavigation(selected: EvidriloTargetSection): Boolean =
    selected != EvidriloTargetSection.PROFILE

internal fun TargetEvidenceStatus.targetDisplayLabel(): String = when (this) {
    TargetEvidenceStatus.NOT_ASSESSED -> "Not assessed"
    TargetEvidenceStatus.PARTIALLY_SUPPORTED -> "Partially supported"
    TargetEvidenceStatus.SUPPORTED -> "Supported"
    TargetEvidenceStatus.UNAVAILABLE -> "Unavailable"
    TargetEvidenceStatus.CANNOT_ASSESS -> "Cannot assess"
}

internal fun targetHistorySummary(
    history: ConclusionSessionSnapshot?,
): TargetHistorySummary {
    if (history == null) return TargetHistorySummary.EMPTY

    val before = history.initialDraft.evidenceRefs.distinct().toSet()
    val after = history.currentDraft.evidenceRefs.distinct().toSet()
    return TargetHistorySummary(
        evidenceAdded = after.count { it !in before },
        evidenceRemoved = before.count { it !in after },
        actionsChanged = if (
            history.initialDraft.implication != history.currentDraft.implication ||
            history.initialDraft.implicationReason != history.currentDraft.implicationReason
        ) 1 else 0,
        hasComparison = true,
    )
}

internal fun targetHistoryEvidenceLabel(summary: TargetHistorySummary): String =
    if (summary.evidenceAdded == 0 && summary.evidenceRemoved == 0) {
        "Evidence anchors unchanged"
    } else {
        "Evidence anchors changed"
    }

internal fun targetHistoryResultLabel(snapshot: ConclusionSessionSnapshot): String =
    when (snapshot.phase) {
        ConclusionSessionPhase.SUMMARY -> "Claim revision saved"
        ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY -> "Changed-evidence response saved"
        else -> "Local comparison saved"
    }

internal fun completedHistorySnapshot(state: ConclusionState): ConclusionSessionSnapshot? = when (state) {
    is ConclusionState.Summary -> ConclusionSessionSnapshot(
        phase = ConclusionSessionPhase.SUMMARY,
        initialDraft = state.initialDraft,
        currentDraft = state.revisedDraft,
    )

    is ConclusionState.EvidenceChangeSummary -> ConclusionSessionSnapshot(
        phase = ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY,
        initialDraft = state.baseDraft,
        currentDraft = state.challengeDraft,
    )

    else -> null
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
    val suppliedObservationCount: Int,
    val selectedEvidenceCount: Int,
    val gapCount: Int?,
    val actionCount: Int,
    val draftCompletenessPercent: Int,
    val evidenceStatus: TargetEvidenceStatus,
) {
    val selectedEvidenceSummary: String
        get() = "$selectedEvidenceCount selected"
}

internal data class EvidriloContextNote(
    val title: String,
    val body: String,
)

/**
 * The target shell's user-visible journey. Evidence review remains a reducer-owned
 * stateful workflow; the surrounding destinations are only navigation
 * surfaces over the current local projection.
 */
internal fun targetJourneyDestinations(): List<EvidriloDestination> = listOf(
    EvidriloDestination.HOME,
    EvidriloDestination.SOURCES,
    EvidriloDestination.WORKSPACE,
    EvidriloDestination.EVIDENCE,
    EvidriloDestination.EVIDENCE_LENS,
    EvidriloDestination.CLAIM_TRACE,
    EvidriloDestination.CLAIM_BOUNDARY,
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
    val observationIds = case.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }.toSet()
    val hasEvidence = draft.evidenceRefs.any { it in observationIds }
    val selectedObservationIds = draft.evidenceRefs.toSet().intersect(observationIds)
    val hasClaim = draft.claimText.trim().length >= 20
    val hasScope = draft.scope != null
    val hasLimit = draft.limitationRefs.isNotEmpty() || draft.limitationNote.isNotBlank()
    val hasAction = draft.implication != null && draft.implicationReason.isNotBlank()
    val completedSignals = listOf(hasEvidence, hasClaim, hasScope, hasLimit, hasAction).count { it }
    val evidenceStatus = targetEvidenceStatus(case, draft)

    return TargetWorkspaceMetrics(
        requirementCount = 1,
        suppliedObservationCount = observationIds.size,
        selectedEvidenceCount = selectedObservationIds.size,
        gapCount = when (evidenceStatus) {
            TargetEvidenceStatus.SUPPORTED -> 0
            TargetEvidenceStatus.PARTIALLY_SUPPORTED -> 1
            TargetEvidenceStatus.NOT_ASSESSED,
            TargetEvidenceStatus.UNAVAILABLE,
            TargetEvidenceStatus.CANNOT_ASSESS,
            -> null
        },
        actionCount = if (hasAction) 1 else 0,
        draftCompletenessPercent = completedSignals * 20,
        evidenceStatus = evidenceStatus,
    )
}

internal fun targetEvidenceStatus(
    case: ConclusionCase,
    draft: ConclusionDraft,
): TargetEvidenceStatus {
    val availableFactIds = case.facts.map { it.id }.toSet()
    val observations = case.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }.toSet()
    val selected = draft.evidenceRefs.toSet().intersect(observations)
    if (draft.evidenceRefs.any { it !in availableFactIds }) {
        return TargetEvidenceStatus.UNAVAILABLE
    }
    val supportDraft = draft.copy(
        evidenceRefs = draft.evidenceRefs.distinct(),
        implication = ConclusionImplication.REPEAT_TRIALS,
        implicationReason = "Evaluate support independently from the learner's next action.",
    )
    val evaluation = ConclusionReducer(case = case).evaluate(supportDraft)
    when (evaluation.primaryFeedback?.status) {
        ConclusionStatus.CANNOT_ASSESS -> return TargetEvidenceStatus.CANNOT_ASSESS
        ConclusionStatus.INCOMPLETE -> return TargetEvidenceStatus.NOT_ASSESSED
        else -> Unit
    }
    if (selected.isEmpty()) return TargetEvidenceStatus.NOT_ASSESSED

    // Evidence support and claim scope do not depend on whether the next action is actionable.
    val supportChecks = evaluation.checks.filter { it.check != ConclusionCheck.ACTIONABLE_IMPLICATION }
    return when {
        supportChecks.isEmpty() -> TargetEvidenceStatus.NOT_ASSESSED
        supportChecks.any { it.status == ConclusionStatus.CANNOT_ASSESS } ->
            TargetEvidenceStatus.CANNOT_ASSESS
        supportChecks.any { it.status == ConclusionStatus.INCOMPLETE } ->
            TargetEvidenceStatus.NOT_ASSESSED
        supportChecks.all { it.status == ConclusionStatus.PASS } ->
            TargetEvidenceStatus.SUPPORTED
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

internal fun targetCaseFor(
    state: ConclusionState,
    baseCase: ConclusionCase,
    evidenceChangeCase: ConclusionCase,
): ConclusionCase = when (state) {
    is ConclusionState.EvidenceChangeDrafting,
    is ConclusionState.EvidenceChangeFeedback,
    is ConclusionState.EvidenceChangeSummary,
    -> evidenceChangeCase
    else -> baseCase
}

internal fun targetHistorySubtitle(history: ConclusionSessionSnapshot?): String = when {
    history == null -> "No comparison saved yet"
    history.phase == ConclusionSessionPhase.REVISION -> "One revision is ready to review"
    else -> "Review your latest before/after comparison"
}

internal fun evidenceChangeContextNote(case: ConclusionCase): EvidriloContextNote? =
    case.changeNotice
        ?.takeIf { it.isNotBlank() }
        ?.let { notice ->
            EvidriloContextNote(
                title = "Challenge context",
                body = notice,
            )
        }
