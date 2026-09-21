package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionCheck
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluator
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionFeedbackItem
import dev.nextgen.mobile.domain.conclusion.ConclusionField
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus

/**
 * A UI-safe projection of one bundled case into the Evidence Graph vocabulary.
 * It deliberately contains only facts supplied by the active case and the
 * learner's structured selections; it does not infer relationships or add
 * backend state.
 */
data class EvidriloWorkspaceTrace(
    val workspaceId: String,
    val workspaceTitle: String,
    val workspaceDescription: String,
    val requirement: EvidriloTraceFact?,
    val relation: ConclusionRelation?,
    val evidence: List<EvidriloEvidenceLink>,
    val limitations: List<EvidriloTraceFact>,
    val boundary: EvidriloTraceFact?,
) {
    val selectedEvidence: List<EvidriloEvidenceLink>
        get() = evidence.filter { it.selected }
}

data class EvidriloTraceFact(
    val factId: String,
    val text: String,
)

data class EvidriloEvidenceLink(
    val factId: String,
    val text: String,
    val displayLabel: String?,
    val displayValue: String?,
    val selected: Boolean,
)

enum class EvidriloEvidenceLensSelection {
    SELECTED,
    AVAILABLE,
    UNAVAILABLE,
}

data class EvidriloEvidenceLensEntry(
    val factId: String,
    val text: String,
    val displayLabel: String?,
    val displayValue: String?,
    val selection: EvidriloEvidenceLensSelection,
)

/** A read-only lens over supplied observations; it never infers support. */
data class EvidriloEvidenceLens(
    val workspaceId: String,
    val requirementId: String?,
    val entries: List<EvidriloEvidenceLensEntry>,
) {
    val selectedCount: Int
        get() = entries.count { it.selection == EvidriloEvidenceLensSelection.SELECTED }

    val observationCount: Int
        get() = entries.count { it.selection != EvidriloEvidenceLensSelection.UNAVAILABLE }

    val unavailableCount: Int
        get() = entries.count { it.selection == EvidriloEvidenceLensSelection.UNAVAILABLE }
}

/** The deterministic Claim Boundary projection shown alongside verification. */
data class EvidriloClaimBoundary(
    val claimText: String,
    val scope: ConclusionScope?,
    val limitationRefs: List<String>,
    val status: ConclusionStatus,
    val feedback: ConclusionFeedbackItem?,
    val boundary: EvidriloTraceFact?,
)

/** A deterministic view of every failing verification check, not only the first feedback item. */
data class EvidriloVerificationDetail(
    val check: ConclusionCheck,
    val status: ConclusionStatus,
    val field: ConclusionField,
    val code: String?,
    val anchorIds: List<String>,
    val message: String,
    val why: String,
    val nextAction: String,
    val isPrimary: Boolean,
)

enum class EvidriloActionOrigin {
    NONE,
    LEARNER_SELECTED,
    SUGGESTED_BY_VERIFICATION,
}

enum class EvidriloActionAnchorState {
    NOT_APPLICABLE,
    ANCHORED,
    MISSING,
}

/** Explains whether the next action is authored, suggested, and still anchored. */
data class EvidriloActionTrace(
    val implication: ConclusionImplication?,
    val origin: EvidriloActionOrigin,
    val anchor: EvidriloTraceFact?,
    val anchorState: EvidriloActionAnchorState,
    val reason: String,
)

enum class EvidriloActionTraceState {
    NOT_SELECTED,
    NEW,
    UNCHANGED,
    CHANGED,
    STALE,
}

/** Field-level changes between two learner-authored drafts. */
data class EvidriloEvidenceDelta(
    val beforeEvidenceIds: List<String>,
    val afterEvidenceIds: List<String>,
    val retainedEvidenceIds: List<String>,
    val addedEvidenceIds: List<String>,
    val removedEvidenceIds: List<String>,
    val claimChanged: Boolean,
    val scopeChanged: Boolean,
    val addedLimitationIds: List<String>,
    val removedLimitationIds: List<String>,
    val limitationNoteChanged: Boolean,
    val implicationChanged: Boolean,
    val beforeAssessment: ConclusionStatus,
    val afterAssessment: ConclusionStatus,
    val evidenceRelationshipChanged: Boolean,
    val claimBoundaryChanged: Boolean,
    val gapChanged: Boolean,
    val afterActionState: EvidriloActionTraceState,
)

fun workspaceTraceFor(
    case: ConclusionCase,
    draft: ConclusionDraft,
): EvidriloWorkspaceTrace = EvidriloWorkspaceTrace(
    workspaceId = case.id,
    workspaceTitle = case.title,
    workspaceDescription = case.description,
    requirement = case.facts.firstOrNull { it.type == ConclusionFactType.AIM }?.toTraceFact(),
    relation = draft.relation,
    evidence = case.factsOfType(ConclusionFactType.OBSERVATION).map { fact ->
        EvidriloEvidenceLink(
            factId = fact.id,
            text = fact.text,
            displayLabel = fact.displayLabel,
            displayValue = fact.displayValue,
            selected = fact.id in draft.evidenceRefs,
        )
    },
    limitations = case.factsOfType(ConclusionFactType.LIMITATION).map(ConclusionFact::toTraceFact),
    boundary = case.facts.firstOrNull { it.type == ConclusionFactType.BOUNDARY }?.toTraceFact(),
)

fun evidenceLensFor(
    case: ConclusionCase,
    draft: ConclusionDraft,
): EvidriloEvidenceLens {
    val observations = case.factsOfType(ConclusionFactType.OBSERVATION)
    val suppliedIds = observations.map { it.id }.toSet()
    val unavailableIds = draft.evidenceRefs
        .distinct()
        .filterNot { it in suppliedIds }

    return EvidriloEvidenceLens(
        workspaceId = case.id,
        requirementId = case.facts.firstOrNull { it.type == ConclusionFactType.AIM }?.id,
        entries = observations.map { fact ->
            EvidriloEvidenceLensEntry(
                factId = fact.id,
                text = fact.text,
                displayLabel = fact.displayLabel,
                displayValue = fact.displayValue,
                selection = if (fact.id in draft.evidenceRefs) {
                    EvidriloEvidenceLensSelection.SELECTED
                } else {
                    EvidriloEvidenceLensSelection.AVAILABLE
                },
            )
        } + unavailableIds.map { factId ->
            EvidriloEvidenceLensEntry(
                factId = factId,
                text = "This evidence reference is unavailable in the active case version.",
                displayLabel = null,
                displayValue = null,
                selection = EvidriloEvidenceLensSelection.UNAVAILABLE,
            )
        },
    )
}

fun claimBoundaryFor(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation,
): EvidriloClaimBoundary = EvidriloClaimBoundary(
    claimText = draft.claimText,
    scope = draft.scope,
    limitationRefs = draft.limitationRefs,
    status = evaluation.primaryFeedback?.status ?: ConclusionStatus.PASS,
    feedback = evaluation.primaryFeedback,
    boundary = case.facts.firstOrNull { it.type == ConclusionFactType.BOUNDARY }?.toTraceFact(),
)

fun verificationDetailsFor(
    evaluation: ConclusionEvaluation,
): List<EvidriloVerificationDetail> {
    val primary = evaluation.primaryFeedback
    val checkDetails = evaluation.checks
        .filter { it.status != ConclusionStatus.PASS }
        .map { check ->
            val matchingPrimary = primary?.takeIf { feedback ->
                feedback.field == check.field && feedback.status == check.status
            }
            EvidriloVerificationDetail(
                check = check.check,
                status = check.status,
                field = check.field,
                code = matchingPrimary?.code,
                anchorIds = check.anchorIds,
                message = matchingPrimary?.message ?: check.reason,
                why = matchingPrimary?.why ?: check.reason,
                nextAction = if (matchingPrimary != null) {
                    matchingPrimary.nextAction
                } else {
                    "Review the ${check.field.name.lowercase()} field and submit again."
                },
                isPrimary = matchingPrimary != null,
            )
        }

    if (primary == null || checkDetails.any { it.isPrimary }) return checkDetails

    return checkDetails + EvidriloVerificationDetail(
        check = primary.field.toCheck(),
        status = primary.status,
        field = primary.field,
        code = primary.code,
        anchorIds = primary.anchorIds,
        message = primary.message,
        why = primary.why,
        nextAction = primary.nextAction,
        isPrimary = true,
    )
}

fun actionTraceFor(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation?,
): EvidriloActionTrace {
    val implication = draft.implication
    if (implication == null) {
        val feedback = evaluation?.primaryFeedback?.takeIf {
            it.field == ConclusionField.IMPLICATION ||
                it.field == ConclusionField.IMPLICATION_REASON
        }
        return EvidriloActionTrace(
            implication = null,
            origin = if (feedback == null) {
                EvidriloActionOrigin.NONE
            } else {
                EvidriloActionOrigin.SUGGESTED_BY_VERIFICATION
            },
            anchor = null,
            anchorState = EvidriloActionAnchorState.NOT_APPLICABLE,
            reason = feedback?.message ?: "No next action has been selected yet.",
        )
    }

    val anchorId = case.requiredLimitationId(implication)
    val anchor = anchorId
        ?.let(case::fact)
        ?.takeIf { it.type == ConclusionFactType.LIMITATION }
        ?.toTraceFact()
    val anchorState = when {
        anchorId == null -> EvidriloActionAnchorState.NOT_APPLICABLE
        anchorId in draft.limitationRefs && anchor != null -> EvidriloActionAnchorState.ANCHORED
        else -> EvidriloActionAnchorState.MISSING
    }

    return EvidriloActionTrace(
        implication = implication,
        origin = EvidriloActionOrigin.LEARNER_SELECTED,
        anchor = anchor,
        anchorState = anchorState,
        reason = draft.implicationReason.ifBlank {
            "This action was selected by the learner and is bounded by the current case."
        },
    )
}

fun evidenceDeltaFor(
    before: ConclusionDraft,
    after: ConclusionDraft,
): EvidriloEvidenceDelta = evidenceDeltaFor(ConclusionCases.M0_T2, ConclusionCases.M0_T2, before, after)

fun evidenceDeltaFor(
    case: ConclusionCase,
    before: ConclusionDraft,
    after: ConclusionDraft,
): EvidriloEvidenceDelta = evidenceDeltaFor(case, case, before, after)

fun evidenceDeltaFor(
    beforeCase: ConclusionCase,
    afterCase: ConclusionCase,
    before: ConclusionDraft,
    after: ConclusionDraft,
): EvidriloEvidenceDelta {
    val beforeEvidence = before.evidenceRefs.distinct()
    val afterEvidence = after.evidenceRefs.distinct()
    val beforeLimitations = before.limitationRefs.distinct()
    val afterLimitations = after.limitationRefs.distinct()
    val beforeAssessment = assessmentFor(beforeCase, before)
    val afterAssessment = assessmentFor(afterCase, after)
    val limitationNoteChanged = before.limitationNote != after.limitationNote
    val implicationChanged = before.implication != after.implication ||
        before.implicationReason != after.implicationReason
    val claimBoundaryChanged = before.claimText != after.claimText ||
        before.scope != after.scope ||
        beforeLimitations != afterLimitations ||
        limitationNoteChanged

    return EvidriloEvidenceDelta(
        beforeEvidenceIds = beforeEvidence,
        afterEvidenceIds = afterEvidence,
        retainedEvidenceIds = beforeEvidence.filter { it in afterEvidence },
        addedEvidenceIds = afterEvidence.filter { it !in beforeEvidence },
        removedEvidenceIds = beforeEvidence.filter { it !in afterEvidence },
        claimChanged = before.claimText != after.claimText,
        scopeChanged = before.scope != after.scope,
        addedLimitationIds = afterLimitations.filter { it !in beforeLimitations },
        removedLimitationIds = beforeLimitations.filter { it !in afterLimitations },
        limitationNoteChanged = limitationNoteChanged,
        implicationChanged = implicationChanged,
        beforeAssessment = beforeAssessment,
        afterAssessment = afterAssessment,
        evidenceRelationshipChanged = beforeEvidence != afterEvidence || before.relation != after.relation,
        claimBoundaryChanged = claimBoundaryChanged,
        gapChanged = (beforeAssessment != ConclusionStatus.PASS) != (afterAssessment != ConclusionStatus.PASS),
        afterActionState = actionTraceState(afterCase, before, after),
    )
}

private fun assessmentFor(case: ConclusionCase, draft: ConclusionDraft): ConclusionStatus {
    val evaluation = ConclusionEvaluator(case).evaluate(draft)
    return evaluation.primaryFeedback?.status
        ?: if (evaluation.checks.isNotEmpty() && evaluation.checks.all { it.status == ConclusionStatus.PASS }) {
            ConclusionStatus.PASS
        } else {
            ConclusionStatus.CANNOT_ASSESS
        }
}

private fun actionTraceState(
    case: ConclusionCase,
    before: ConclusionDraft,
    after: ConclusionDraft,
): EvidriloActionTraceState {
    val implication = after.implication ?: return EvidriloActionTraceState.NOT_SELECTED
    val requiredLimitation = case.requiredLimitationId(implication)
    if (requiredLimitation != null && requiredLimitation !in after.limitationRefs) {
        return EvidriloActionTraceState.STALE
    }
    if (before.implication == null) return EvidriloActionTraceState.NEW
    return if (before.implication != after.implication || before.implicationReason != after.implicationReason) {
        EvidriloActionTraceState.CHANGED
    } else {
        EvidriloActionTraceState.UNCHANGED
    }
}

private fun ConclusionField.toCheck(): ConclusionCheck = when (this) {
    ConclusionField.CASE_ID,
    ConclusionField.RELATION,
    ConclusionField.CLAIM_TEXT,
    -> ConclusionCheck.GOAL_CONNECTEDNESS
    ConclusionField.EVIDENCE_REFS -> ConclusionCheck.EVIDENCE_ANCHORING
    ConclusionField.SCOPE,
    ConclusionField.LIMITATION_REFS,
    ConclusionField.LIMITATION_NOTE,
    -> ConclusionCheck.SCOPE_UNCERTAINTY
    ConclusionField.IMPLICATION,
    ConclusionField.IMPLICATION_REASON,
    -> ConclusionCheck.ACTIONABLE_IMPLICATION
}

private fun ConclusionFact.toTraceFact(): EvidriloTraceFact = EvidriloTraceFact(
    factId = id,
    text = text,
)
