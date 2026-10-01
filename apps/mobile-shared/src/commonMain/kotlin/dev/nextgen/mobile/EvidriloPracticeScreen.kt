package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionField
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft

/**
 * Stateless presentation entry. Its host owns access, state, storage and events.
 * Used by the course host and the debug preview. It does not own persistence.
 */
@Composable
public fun EvidriloPracticeScreen(
    state: ConclusionState,
    onEvent: (ConclusionEvent) -> Unit,
    onExit: () -> Unit,
    case: ConclusionCase = ConclusionCases.M0_T2,
    onOpenProjects: (() -> Unit)? = null,
    onSelectionSound: () -> Unit = {},
    audioControls: @Composable () -> Unit = {},
    onOpenHistory: (() -> Unit)? = null,
) {
    when (state) {
        ConclusionState.Intro -> EvidriloContentColumn {
            Text("The tablet investigation", style = MaterialTheme.typography.displayLarge)
            Text(case.description, style = MaterialTheme.typography.bodyLarge)
            Text("Synthetic practice data", style = MaterialTheme.typography.bodySmall)
            EvidriloPrimaryButton("Start investigating", { onEvent(ConclusionEvent.Begin) })
        }
        is ConclusionState.Drafting -> EvidriloPracticeDraftScreen(case, "Build a bounded conclusion", state.draft, state.validationMessage,
            { onEvent(ConclusionEvent.UpdateDraft(it)) }, { onEvent(ConclusionEvent.Submit) }, { onEvent(ConclusionEvent.Reset) }, onExit, EvidriloDraftStep.EVIDENCE, onSelectionSound, audioControls)
        is ConclusionState.Incomplete -> {
            val activeCase = if (state.draft.caseId == ConclusionCases.EVIDENCE_CHANGE.id) ConclusionCases.EVIDENCE_CHANGE else case
            val step = when (state.feedback.field) {
                ConclusionField.CLAIM_TEXT, ConclusionField.SCOPE -> EvidriloDraftStep.CLAIM
                ConclusionField.LIMITATION_REFS, ConclusionField.LIMITATION_NOTE, ConclusionField.IMPLICATION, ConclusionField.IMPLICATION_REASON -> EvidriloDraftStep.LIMITS
                else -> EvidriloDraftStep.EVIDENCE
            }
            EvidriloPracticeDraftScreen(activeCase, "Complete the conclusion", state.draft, state.feedback.message,
                { onEvent(if (activeCase == case) ConclusionEvent.UpdateDraft(it) else ConclusionEvent.UpdateEvidenceChangeDraft(it)) },
                { onEvent(if (activeCase == case) ConclusionEvent.Submit else ConclusionEvent.SubmitEvidenceChange) },
                { onEvent(ConclusionEvent.Reset) }, onExit, step, onSelectionSound, audioControls)
        }
        is ConclusionState.Feedback -> EvidriloPracticeFeedbackScreen(case, state.draft, state.evaluation,
            { onEvent(ConclusionEvent.BeginRevision) }, { onEvent(ConclusionEvent.Reset) }, onExit, audioControls)
        is ConclusionState.Revision -> EvidriloPracticeDraftScreen(case, "Revise once", state.draft, state.validationMessage,
            { onEvent(ConclusionEvent.UpdateDraft(it)) }, { onEvent(ConclusionEvent.Submit) }, { onEvent(ConclusionEvent.Reset) }, onExit, EvidriloDraftStep.CLAIM, onSelectionSound, audioControls)
        is ConclusionState.Summary -> PracticeRevisionComparison(case, state,
            { onEvent(ConclusionEvent.BeginEvidenceChange) }, onExit, { onEvent(ConclusionEvent.Reset) }, onOpenHistory)
        is ConclusionState.EvidenceChangeDrafting -> EvidriloPracticeDraftScreen(ConclusionCases.EVIDENCE_CHANGE, "Rebuild after the evidence change", state.draft, state.validationMessage,
            { onEvent(ConclusionEvent.UpdateEvidenceChangeDraft(it)) }, { onEvent(ConclusionEvent.SubmitEvidenceChange) }, { onEvent(ConclusionEvent.Reset) }, onExit, EvidriloDraftStep.EVIDENCE, onSelectionSound, audioControls)
        is ConclusionState.EvidenceChangeFeedback -> EvidriloPracticeChangedFeedbackScreen(state.baseDraft, state.draft, state.evaluation,
            { onEvent(ConclusionEvent.FinishEvidenceChange) }, { onEvent(ConclusionEvent.Reset) }, onExit, audioControls)
        is ConclusionState.EvidenceChangeSummary -> EvidriloPracticeChangedSummaryScreen(state.baseDraft, state.challengeDraft, state.challengeEvaluation,
            { onEvent(ConclusionEvent.Reset) }, onExit, audioControls, onOpenProjects, onExit)
    }
}

@Composable
private fun PracticeRevisionComparison(case: ConclusionCase, state: ConclusionState.Summary, onChallenge: () -> Unit,
    onExit: () -> Unit, onReset: () -> Unit, onOpenHistory: (() -> Unit)?) {
    var showHelp by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var confirmReset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var details by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    PracticeFrame(onExit, { showHelp = true }, "Your one revision", footer = {
        EvidriloPrimaryButton("Try the evidence-change challenge", onChallenge, trailingIcon = EvidriloIconName.ARROW_FORWARD)
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            PracticeHeading("See what you reconsidered.", "Your initial claim is kept beside the one revision.")
            PracticeClaim("Your initial claim", state.initialDraft.claimText)
            PracticeClaim("After the review", state.revisedDraft.claimText)
            TextButton({ details = !details }) { Text(if (details) "Hide the retained decisions" else "Compare evidence, limits, and next action") }
            AnimatedVisibility(details) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    PracticeTabletDecisions("Before · retained", state.initialDraft, case)
                    PracticeTabletDecisions("After · one revision", state.revisedDraft, case)
                }
            }
            PracticeEvaluation(state.finalEvaluation, case)
            Text("Next, reconsider this claim when an observation is unavailable.", style = MaterialTheme.typography.bodyLarge)
            onOpenHistory?.let { EvidriloSecondaryButton("View tablet comparison history", it) }
        }
    }
    if (showHelp) PracticeHelpSheet(case, null, { showHelp = false }, { showHelp = false; confirmReset = true })
    if (confirmReset) PracticeResetDialog({ confirmReset = false }, { confirmReset = false; onReset() })
}

@Composable
private fun PracticeTabletDecisions(label: String, draft: ConclusionDraft, case: ConclusionCase) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Cobalt)
        draft.evidenceRefs.forEach { Text(it + " · " + (case.fact(it)?.text ?: "Unavailable"), style = MaterialTheme.typography.bodyMedium) }
        Text("Relation: " + (draft.relation?.practiceLabel() ?: "No relation selected"), style = MaterialTheme.typography.bodyMedium)
        Text("Scope: " + (draft.scope?.practiceLabel() ?: "No scope selected"), style = MaterialTheme.typography.bodyMedium)
        draft.limitationRefs.forEach { Text(it + " · " + (case.fact(it)?.text ?: "Unavailable"), style = MaterialTheme.typography.bodyMedium) }
        RawText(draft.limitationNote, style = MaterialTheme.typography.bodyMedium)
        Text("Next action: " + (draft.implication?.practiceLabel() ?: "No action selected"), style = MaterialTheme.typography.bodyMedium)
        RawText(draft.implicationReason, style = MaterialTheme.typography.bodyMedium)
    }
}
