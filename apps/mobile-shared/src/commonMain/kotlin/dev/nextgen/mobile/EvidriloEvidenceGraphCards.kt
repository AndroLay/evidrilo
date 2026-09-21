package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope

@Composable
internal fun EvidriloWorkspaceTraceCard(
    case: ConclusionCase,
    draft: ConclusionDraft,
) {
    val trace = workspaceTraceFor(case, draft)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Workspace trace for ${trace.workspaceTitle}"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Tint),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("WORKSPACE TRACE", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
            Text(trace.workspaceTitle, style = MaterialTheme.typography.titleLarge)
            Text(trace.workspaceDescription, style = MaterialTheme.typography.bodyMedium)
            trace.requirement?.let { requirement ->
                TraceFactRow(label = "Requirement · ${requirement.factId}", text = requirement.text)
            } ?: TraceFactRow(
                label = "Requirement",
                text = "No requirement was supplied for this workspace.",
            )
            Text("Evidence relationship", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Relation: ${trace.relation.evidriloLabel()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                trace.selectedEvidence
                    .map { evidence -> evidence.factId }
                    .ifEmpty { listOf("No observation selected yet") }
                    .joinToString(prefix = "Anchors: "),
                style = MaterialTheme.typography.bodyMedium,
            )
            trace.boundary?.let { boundary ->
                TraceFactRow(label = "Claim boundary · ${boundary.factId}", text = boundary.text)
            }
        }
    }
}

@Composable
internal fun EvidriloEvidenceLensCard(
    case: ConclusionCase,
    draft: ConclusionDraft,
) {
    val lens = evidenceLensFor(case, draft)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Evidence lens for ${lens.workspaceId}"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("EVIDENCE LENS", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
            Text(
                "${lens.selectedCount} of ${lens.observationCount} supplied observations selected",
                style = MaterialTheme.typography.titleLarge,
            )
            if (lens.unavailableCount > 0) {
                Text(
                    "${lens.unavailableCount} selected reference is unavailable in this case version and cannot support the claim.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.Error,
                )
            }
            Text(
                "Selection is shown as a learner anchor; it is not a claim of proof by itself.",
                style = MaterialTheme.typography.bodyMedium,
            )
            lens.entries.forEach { entry ->
                TraceFactRow(
                    label = "${entry.selection.evidriloLabel()} · ${entry.factId}",
                    text = entry.text,
                )
            }
        }
    }
}

@Composable
internal fun EvidriloClaimBoundaryCard(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation,
) {
    val boundary = claimBoundaryFor(case, draft, evaluation)
    val statusColor = when (boundary.status) {
        dev.nextgen.mobile.domain.conclusion.ConclusionStatus.PASS -> EvidriloColors.SuccessSurface
        dev.nextgen.mobile.domain.conclusion.ConclusionStatus.ACTION_REQUIRED -> EvidriloColors.Tint
        dev.nextgen.mobile.domain.conclusion.ConclusionStatus.INCOMPLETE,
        dev.nextgen.mobile.domain.conclusion.ConclusionStatus.CANNOT_ASSESS,
        -> EvidriloColors.ErrorSurface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Claim Boundary: ${boundary.status.displayLabel()}"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("VERIFY CLAIM · CLAIM BOUNDARY", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
            Text(boundary.status.displayLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TraceFactRow(
                label = "Learner claim",
                text = boundary.claimText.ifBlank { "Not written" },
            )
            TraceFactRow(label = "Scope", text = boundary.scope.evidriloLabel())
            TraceFactRow(
                label = "Limitation anchors",
                text = boundary.limitationRefs.ifEmpty { listOf("None selected") }.joinToString(),
            )
            boundary.boundary?.let { fact ->
                TraceFactRow(label = "Case boundary · ${fact.factId}", text = fact.text)
            }
            boundary.feedback?.let { feedback ->
                TraceFactRow(label = "Verification note · ${feedback.code}", text = feedback.message)
            } ?: Text(
                "Deterministic checks found no boundary issue in the supplied information.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun EvidriloVerificationDetailCard(
    evaluation: ConclusionEvaluation,
) {
    val details = verificationDetailsFor(evaluation)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Verification detail for claim verification"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("VERIFICATION DETAIL", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
            if (details.isEmpty()) {
                Text("No blocking verification issue found in the bounded checks.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Each deterministic check passed for the supplied claim and anchors.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                details.forEach { detail ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            buildString {
                                append(detail.check.displayLabel())
                                append(" · ")
                                append(detail.status.displayLabel())
                                if (detail.isPrimary && detail.code != null) {
                                    append(" · ")
                                    append(detail.code)
                                }
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(detail.message, style = MaterialTheme.typography.bodyMedium)
                        Text("Why: ${detail.why}", style = MaterialTheme.typography.bodySmall)
                        Text("Next: ${detail.nextAction}", style = MaterialTheme.typography.bodySmall)
                        if (detail.anchorIds.isNotEmpty()) {
                            Text(
                                "Anchors: ${detail.anchorIds.joinToString()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EvidriloColors.Slate,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EvidriloEvidenceDeltaCard(
    before: ConclusionDraft,
    after: ConclusionDraft,
    title: String,
    case: ConclusionCase = ConclusionCases.M0_T2,
    beforeCase: ConclusionCase = case,
) {
    val delta = evidenceDeltaFor(beforeCase, case, before, after)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title evidence delta"
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("EVIDENCE DELTA · WHAT CHANGED?", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
            Text(title, style = MaterialTheme.typography.titleLarge)
            DeltaRow(
                label = "Evidence retained",
                value = delta.retainedEvidenceIds.ifEmpty { listOf("None") }.joinToString(),
            )
            DeltaRow(
                label = "Evidence added",
                value = delta.addedEvidenceIds.ifEmpty { listOf("None") }.joinToString(),
            )
            DeltaRow(
                label = "Evidence removed or unavailable",
                value = delta.removedEvidenceIds.ifEmpty { listOf("None") }.joinToString(),
            )
            DeltaRow(label = "Claim text changed", value = delta.claimChanged.evidriloYesNo())
            DeltaRow(label = "Scope changed", value = delta.scopeChanged.evidriloYesNo())
            DeltaRow(
                label = "Limitation anchors added",
                value = delta.addedLimitationIds.ifEmpty { listOf("None") }.joinToString(),
            )
            DeltaRow(
                label = "Limitation anchors removed",
                value = delta.removedLimitationIds.ifEmpty { listOf("None") }.joinToString(),
            )
            DeltaRow(label = "Limitation note changed", value = delta.limitationNoteChanged.evidriloYesNo())
            DeltaRow(label = "Next action changed", value = delta.implicationChanged.evidriloYesNo())
            DeltaRow(label = "Evidence assessment before", value = delta.beforeAssessment.displayLabel())
            DeltaRow(label = "Evidence assessment after", value = delta.afterAssessment.displayLabel())
            DeltaRow(label = "Evidence relationship changed", value = delta.evidenceRelationshipChanged.evidriloYesNo())
            DeltaRow(label = "Claim boundary changed", value = delta.claimBoundaryChanged.evidriloYesNo())
            DeltaRow(label = "Open gap changed", value = delta.gapChanged.evidriloYesNo())
            DeltaRow(label = "Action trace", value = delta.afterActionState.evidriloLabel())
        }
    }
}

@Composable
private fun TraceFactRow(
    label: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeltaRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun ConclusionRelation?.evidriloLabel(): String = when (this) {
    ConclusionRelation.OBSERVED_DIFFERENCE -> "Observed difference"
    ConclusionRelation.LIMITED_OBSERVATION -> "Limited observation"
    ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE -> "Cannot conclude from this case"
    ConclusionRelation.UNSUPPORTED -> "Unsupported relation"
    null -> "Not selected"
}

private fun ConclusionScope?.evidriloLabel(): String = when (this) {
    ConclusionScope.THIS_OBSERVATION -> "This observation only"
    ConclusionScope.LIMITED_COMPARISON -> "Limited comparison"
    ConclusionScope.GENERAL_CAUSAL_CLAIM -> "General causal claim"
    ConclusionScope.UNSUPPORTED -> "Unsupported scope"
    null -> "Not selected"
}

private fun EvidriloEvidenceLensSelection.evidriloLabel(): String = when (this) {
    EvidriloEvidenceLensSelection.SELECTED -> "Selected anchor"
    EvidriloEvidenceLensSelection.AVAILABLE -> "Available observation"
    EvidriloEvidenceLensSelection.UNAVAILABLE -> "Unavailable reference"
}

private fun EvidriloActionTraceState.evidriloLabel(): String = when (this) {
    EvidriloActionTraceState.NOT_SELECTED -> "Not selected"
    EvidriloActionTraceState.NEW -> "New and anchored"
    EvidriloActionTraceState.UNCHANGED -> "Unchanged and anchored"
    EvidriloActionTraceState.CHANGED -> "Changed and anchored"
    EvidriloActionTraceState.STALE -> "Stale: limitation anchor missing"
}

private fun Boolean.evidriloYesNo(): String = if (this) "Yes" else "No"
