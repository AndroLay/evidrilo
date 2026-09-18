package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionFeedbackItem
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

/** The deterministic Claim Boundary projection shown alongside verification. */
data class EvidriloClaimBoundary(
    val claimText: String,
    val scope: ConclusionScope?,
    val limitationRefs: List<String>,
    val status: ConclusionStatus,
    val feedback: ConclusionFeedbackItem?,
    val boundary: EvidriloTraceFact?,
)

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
    val implicationChanged: Boolean,
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

fun evidenceDeltaFor(
    before: ConclusionDraft,
    after: ConclusionDraft,
): EvidriloEvidenceDelta {
    val beforeEvidence = before.evidenceRefs.distinct()
    val afterEvidence = after.evidenceRefs.distinct()
    val beforeLimitations = before.limitationRefs.distinct()
    val afterLimitations = after.limitationRefs.distinct()
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
        implicationChanged = before.implication != after.implication,
    )
}

private fun ConclusionFact.toTraceFact(): EvidriloTraceFact = EvidriloTraceFact(
    factId = id,
    text = text,
)
