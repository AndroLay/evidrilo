package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import dev.nextgen.mobile.domain.practice.*

@Composable
internal fun CourseLessonScreen(
    session: PracticeLessonSession, storageLabel: String, problem: String?,
    onEvent: (PracticeCourseEvent) -> Unit, onContinue: () -> Unit, onLeave: () -> Unit,
    onHelp: () -> Unit, onRestart: () -> Unit, onOpenProjects: () -> Unit,
    onNextCase: () -> Unit, onRetrySave: () -> Unit,
    busy: Boolean = false,
) {
    val stage = session.stage
    val id = session.lesson
    val draft = session.draft
    var revisionPart by rememberSaveable(id.name, stage.name) { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    LaunchedEffect(stage, revisionPart) { scroll.scrollTo(0) }
    val revisionProblem = when (revisionPart) {
        0 -> PracticeCourseReducer.revisionEvidenceProblem(session)
        1 -> if (draft.claim.trim().length !in 20..320) "Write your revised claim in 20–320 characters." else null
        2 -> if (draft.scope == null) "Choose the scope." else null
        3 -> if (draft.limitation == null || draft.limitationNote.trim().length !in 10..240) "Explain the selected limit in 10–240 characters." else null
        else -> problem
    }
    val currentProblem = if (stage == PracticeLessonStage.REVISION) revisionProblem else problem
    fun edit(value: PracticeLessonDraft) { onEvent(PracticeCourseEvent.Edit(value)) }
    val title = when (stage) {
        PracticeLessonStage.MISSION -> PracticeCourseContent.title(id)
        PracticeLessonStage.PREDICTION -> "What might matter?"
        PracticeLessonStage.INSPECT -> if (id == PracticeLessonId.STUDIES) "Look closely at the sources." else "Look at the submissions."
        PracticeLessonStage.ORGANIZE -> "Build your comparison."
        PracticeLessonStage.CLAIM -> "Say what the records support."
        PracticeLessonStage.SCOPE -> "How far can it reach?"
        PracticeLessonStage.LIMITS -> "Keep the uncertainty visible."
        PracticeLessonStage.ACTION -> "Choose a useful next move."
        PracticeLessonStage.FEEDBACK -> "Follow the evidence."
        PracticeLessonStage.CHANGE -> if (id == PracticeLessonId.STUDIES) "A fourth card enters." else "One row was counted twice."
        PracticeLessonStage.REVISION -> listOf("Review the changed evidence.", "Revise in your own words.", "Reconsider the scope.", "Revisit the limitation.", "Review your next move.")[revisionPart]
        PracticeLessonStage.FINAL_REVIEW -> "Compare your reasoning."
        PracticeLessonStage.COMPLETE -> "Carry the move forward."
    }
    val prompt = when (stage) {
        PracticeLessonStage.MISSION -> PracticeCourseContent.skill(id)
        PracticeLessonStage.PREDICTION -> "This is a prediction, not a scored answer. You can skip it."
        PracticeLessonStage.INSPECT -> "Tap records to connect them to your claim. Open details when you need them."
        PracticeLessonStage.ORGANIZE -> "Separate a similar recall direction, a different finding, and a different outcome."
        PracticeLessonStage.CLAIM -> "Write one short sentence using the evidence you selected."
        PracticeLessonStage.SCOPE -> "Declare the reach of your own sentence."
        PracticeLessonStage.LIMITS -> "Choose a supplied limit and explain what remains uncertain."
        PracticeLessonStage.ACTION -> "Connect your next action to the limitation you selected."
        PracticeLessonStage.FEEDBACK -> "These checks review your selected links and decisions."
        PracticeLessonStage.CHANGE -> "Commit to a decision before reading its consequence."
        PracticeLessonStage.REVISION -> "Your initial response stays intact. You can submit this revision once."
        PracticeLessonStage.FINAL_REVIEW -> "Both versions and their evidence remain visible."
        PracticeLessonStage.COMPLETE -> "Use the reasoning pattern in your own work, with your own evidence."
    }
    val buttonLabel = when (stage) {
        PracticeLessonStage.MISSION -> "Start investigating"
        PracticeLessonStage.PREDICTION -> "Inspect the evidence"
        PracticeLessonStage.ACTION -> "Review my reasoning"
        PracticeLessonStage.FEEDBACK -> "See the evidence change"
        PracticeLessonStage.CHANGE -> "Review this decision"
        PracticeLessonStage.REVISION -> if (revisionPart == 4) "Review my one revision" else "Continue"
        PracticeLessonStage.FINAL_REVIEW -> "Finish this attempt"
        PracticeLessonStage.COMPLETE -> "Explore practice"
        else -> "Continue"
    }
    PracticeFrame(onLeave, onHelp, when {
        stage == PracticeLessonStage.REVISION -> "One revision · synthetic case"
        id == PracticeLessonId.STUDIES -> "Source comparison · synthetic case"
        id == PracticeLessonId.SURVEY -> "Survey investigation · synthetic case"
        else -> "Tablet investigation · synthetic case"
    }, footer = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(currentProblem ?: storageLabel, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            if (storageLabel.startsWith("Not saved")) TextButton(onRetrySave) { Text("Retry saving") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stage == PracticeLessonStage.REVISION && revisionPart > 0 ||
                    session.initial == null && stage !in setOf(PracticeLessonStage.MISSION, PracticeLessonStage.COMPLETE)) {
                    EvidriloBackGesture("Previous practice decision", {
                        if (busy) return@EvidriloBackGesture
                        focus.clearFocus()
                        if (stage == PracticeLessonStage.REVISION) revisionPart-- else onEvent(PracticeCourseEvent.Back)
                    })
                }
                Box(Modifier.weight(1f)) {
                    EvidriloPrimaryButton(buttonLabel, {
                        focus.clearFocus()
                        if (stage == PracticeLessonStage.REVISION && revisionPart < 4) revisionPart++ else onContinue()
                    }, enabled = !busy && currentProblem == null && (stage != PracticeLessonStage.CHANGE || session.changeChoice != null),
                        trailingIcon = EvidriloIconName.ARROW_FORWARD)
                }
            }
        }
    }) {
        CourseProgress(session, revisionPart)
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PracticeHeading(title, prompt)
            when (stage) {
                PracticeLessonStage.MISSION -> {
                    EvidriloWorkflowScene(courseIcon(id), listOf("Inspect the supplied records", "Build a bounded claim", "Reconsider when evidence changes"), id)
                    PracticeMessage("Your mission", PracticeCourseContent.mission(id), courseIcon(id))
                    Text("Synthetic practice material · work at your own pace", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Cobalt)
                    Text("You'll keep your first response, inspect one evidence change, and revise once. No case material is added to Projects.", style = MaterialTheme.typography.bodyMedium)
                    if (id == PracticeLessonId.TABLET) CourseWorkedExample()
                    TextButton(onRestart) { Text("Start a fresh attempt") }
                }
                PracticeLessonStage.PREDICTION -> {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PracticeCourseContent.predictions(id).forEach { prediction ->
                            PracticeChoice(prediction, "Choose what you expect, or continue without choosing.", session.prediction == prediction, {
                                onEvent(PracticeCourseEvent.Predict(if (session.prediction == prediction) null else prediction))
                            })
                        }
                    }
                }
                PracticeLessonStage.INSPECT -> CourseEvidence(id, draft, false, ::edit)
                PracticeLessonStage.ORGANIZE -> CourseStudyBoard(draft, ::edit)
                PracticeLessonStage.CLAIM -> {
                    CourseSelectedLinks(id, draft, false)
                    PracticeTextInput(draft.claim, "Your claim", "What do the selected records support?", 320, 20, { edit(draft.copy(claim = it)) }, 4)
                    Text("The sentence is yours. The checks cannot assess every meaning in free text.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                PracticeLessonStage.SCOPE -> {
                    PracticeClaim("Your current claim", draft.claim)
                    CourseScope(draft, ::edit)
                }
                PracticeLessonStage.LIMITS -> CourseLimits(id, draft, ::edit)
                PracticeLessonStage.ACTION -> CourseActions(id, draft, ::edit)
                PracticeLessonStage.FEEDBACK -> {
                    PracticeClaim("Your initial claim · retained", requireNotNull(session.initial).claim)
                    CourseFeedback(id, requireNotNull(session.initial), false)
                    Text("Next, inspect new evidence before making your one revision.", style = MaterialTheme.typography.bodyMedium)
                }
                PracticeLessonStage.CHANGE -> {
                    if (id == PracticeLessonId.STUDIES) {
                        CourseStudyCard(PracticeCourseContent.fourthStudy, false, null)
                        Text("A and B still exist. C still measures preference. This new card changes the set you need to consider.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        CourseSurveyChart(false)
                        PracticeMessage("Duplicate found", "R07 repeats respondent C's R03 submission. These anonymous IDs are fictional.", EvidriloIconName.FILE)
                        PracticeCourseContent.responses.filter { it.id in setOf("R03", "R07") }.forEach { response ->
                            Text(response.id + " · respondent " + response.respondent + " · quiet space", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PracticeCourseContent.changeChoices(id).forEach { choice ->
                            PracticeChoice(PracticeCourseContent.changeText(choice), when (choice) {
                                PracticeChangeChoice.RECONSIDER -> "Compare the new finding with earlier records."
                                PracticeChangeChoice.DISCARD_EARLIER -> "Set aside the earlier recall findings."
                                PracticeChangeChoice.UNIVERSALIZE -> "Extend the result beyond these studies."
                                PracticeChangeChoice.REMOVE_DUPLICATE -> "Count respondent C once."
                                PracticeChangeChoice.KEEP_DUPLICATE -> "Count one participant twice."
                                PracticeChangeChoice.REMOVE_BOTH -> "Exclude respondent C entirely."
                            }, session.changeChoice == choice,
                                { onEvent(PracticeCourseEvent.ChooseChange(choice)) })
                        }
                    }
                    if (session.changeReviewed)
                        PracticeMessage("Reconsider this decision", PracticeCourseEvaluator.changeExplanation(id, session.changeChoice), EvidriloIconName.INFO)
                }
                PracticeLessonStage.REVISION -> when (revisionPart) {
                    0 -> {
                        PracticeMessage("What changed", PracticeCourseEvaluator.changeExplanation(id, session.changeChoice), EvidriloIconName.HISTORY)
                        PracticeChoice("Use this changed evidence set", "The first response keeps its original evidence version.", draft.evidenceVersion == 2,
                            { onEvent(PracticeCourseEvent.UseChangedEvidence) }, checkbox = true)
                        CourseEvidence(id, draft, true, ::edit)
                    }
                    1 -> {
                        PracticeClaim("Your initial claim · retained", requireNotNull(session.initial).claim)
                        CourseSelectedLinks(id, draft, true)
                        PracticeTextInput(draft.claim, "Your one revision", "Reconsider your own wording…", 320, 20, { edit(draft.copy(claim = it)) }, 4)
                    }
                    2 -> CourseScope(draft, ::edit)
                    3 -> CourseLimits(id, draft, ::edit)
                    else -> CourseActions(id, draft, ::edit)
                }
                PracticeLessonStage.FINAL_REVIEW, PracticeLessonStage.COMPLETE -> {
                    CourseComparison(session)
                    CourseFeedback(id, requireNotNull(session.revised), true)
                    if (stage == PracticeLessonStage.COMPLETE) {
                        GetStartedPaperBurst(session.lesson.ordinal, Modifier.fillMaxWidth().height(70.dp))
                        PracticeMessage("Use this in Projects",
                            if (id == PracticeLessonId.STUDIES) "Add your own sources and notes, compare outcomes and methods, then link a synthesis claim to the material that supports it."
                            else "Keep the denominator and corrected records alongside your own findings. When data changes, inspect the claims that rely on it.",
                            EvidriloIconName.LINK)
                        EvidriloSecondaryButton("Use this move in my project", onOpenProjects)
                        if (id == PracticeLessonId.STUDIES) EvidriloSecondaryButton("Try the corrected-survey case", onNextCase)
                        Text("No synthetic facts or practice wording will be copied. Completion records your attempt; it does not certify mastery.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                        TextButton(onRestart) { Text("Start a new attempt") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseScope(draft: PracticeLessonDraft, edit: (PracticeLessonDraft) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            PracticeClaimScope.SUPPLIED_RECORDS to ("These supplied records" to "Keep the claim within these contexts and measures."),
            PracticeClaimScope.ALL_STUDENTS to ("All students" to "Extend the claim to people outside this material."),
            PracticeClaimScope.GENERAL_CAUSE to ("A general causal rule" to "Declare that a cause applies beyond these records."),
        ).forEach { (scope, copy) -> PracticeChoice(copy.first, copy.second, draft.scope == scope, { edit(draft.copy(scope = scope)) }) }
    }
}

@Composable
private fun CourseLimits(id: PracticeLessonId, draft: PracticeLessonDraft, edit: (PracticeLessonDraft) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PracticeCourseContent.limitations(id).forEach { limitation ->
            PracticeChoice(when (limitation) {
                PracticeLimitation.SMALL_SAMPLE -> "Small samples"
                PracticeLimitation.DIFFERENT_MEASURES -> "Different methods and outcomes"
                PracticeLimitation.SELF_SELECTED -> "Self-selected participation"
            }, PracticeCourseContent.limitationText(limitation), draft.limitation == limitation,
                { edit(draft.copy(limitation = limitation)) })
        }
    }
    PracticeTextInput(draft.limitationNote, "Why does this limit matter?", "What remains uncertain?", 240, 10, { edit(draft.copy(limitationNote = it)) })
}

@Composable
private fun CourseActions(id: PracticeLessonId, draft: PracticeLessonDraft, edit: (PracticeLessonDraft) -> Unit) {
    val choices = if (id == PracticeLessonId.STUDIES) PracticeNextAction.entries else listOf(PracticeNextAction.BROADER_SAMPLE, PracticeNextAction.CLAIM_PROVEN)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.forEach { action -> PracticeChoice(PracticeCourseContent.actionText(action), when (action) {
            PracticeNextAction.BROADER_SAMPLE -> "Respond to sampling limits without promising a universal result."
            PracticeNextAction.SAME_MEASURE -> "Compare like outcomes and follow-up conditions."
            PracticeNextAction.CLAIM_PROVEN -> "End the inquiry without addressing its limits."
        }, draft.action == action, { edit(draft.copy(action = action)) }) }
    }
    PracticeTextInput(draft.actionReason, "Why this next action?", "Connect it to your selected limitation…", 240, 10, { edit(draft.copy(actionReason = it)) })
}

@Composable
private fun CourseFeedback(id: PracticeLessonId, draft: PracticeLessonDraft, changed: Boolean) {
    val checks = PracticeCourseEvaluator.evaluate(id, draft, changed)
    val firstConcern = checks.firstOrNull { it.status != ConclusionStatus.PASS }
    var details by remember(id, changed) { mutableStateOf(false) }
    PracticeMessage(
        if (firstConcern == null) "Your selected decisions fit these checks" else firstConcern.title,
        firstConcern?.explanation ?: "Your evidence links, declared scope, and next action fit this synthetic case's checks. Your sentence still needs your own review.",
        if (firstConcern == null) EvidriloIconName.CHECK else EvidriloIconName.INFO)
    firstConcern?.let {
        Text("Next move: " + it.nextMove, style = MaterialTheme.typography.bodyMedium)
        Text("Linked to: " + it.anchors.joinToString(), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
    TextButton({ details = !details }) { Text(if (details) "Hide the case checks" else "Inspect the case checks") }
    AnimatedVisibility(details, enter = fadeIn(), exit = fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            checks.forEach { check ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(check.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (check.status == ConclusionStatus.PASS) "Fits this check" else "Needs review", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
                    Text(check.explanation, style = MaterialTheme.typography.bodyMedium)
                    Text(check.anchors.joinToString(), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    HorizontalDivider(color = EvidriloColors.Separator)
                }
            }
        }
    }
    Text("Checks assess supplied links and choices, not the scientific truth of every word, real-world effectiveness, or academic quality.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
}

@Composable
private fun CourseComparison(session: PracticeLessonSession) {
    val original = requireNotNull(session.initial)
    val revised = requireNotNull(session.revised)
    PracticeClaim("Before the evidence change · retained", original.claim)
    PracticeClaim("Your one revision", revised.claim)
    PracticeMessage("Evidence changed", PracticeCourseEvaluator.changeExplanation(session.lesson, session.changeChoice), EvidriloIconName.HISTORY)
    Text(if (original.claim == revised.claim) "You kept the wording. Inspect whether the changed evidence still supports it."
        else "You changed the wording. The first sentence remains above.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    var details by remember(session.lesson) { mutableStateOf(false) }
    TextButton({ details = !details }) { Text(if (details) "Hide the retained records" else "Compare evidence and reasoning records") }
    AnimatedVisibility(details, enter = fadeIn(), exit = fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CourseSelectedLinks(session.lesson, original, false)
            CourseRetainedDecisions("Before · retained", original)
            HorizontalDivider(color = EvidriloColors.Separator)
            CourseSelectedLinks(session.lesson, revised, true)
            CourseRetainedDecisions("After · one revision", revised)
        }
    }
}

@Composable
private fun CourseRetainedDecisions(label: String, draft: PracticeLessonDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Cobalt)
        Text("Scope: " + when (draft.scope) {
            PracticeClaimScope.SUPPLIED_RECORDS -> "These supplied records"
            PracticeClaimScope.ALL_STUDENTS -> "All students"
            PracticeClaimScope.GENERAL_CAUSE -> "A general causal rule"
            null -> "No scope selected"
        }, style = MaterialTheme.typography.bodyMedium)
        if (draft.groups.isNotEmpty()) Text("Comparison board: " + draft.groups.entries.sortedBy { it.key }.joinToString {
            it.key + " → " + when (it.value) {
                PracticeEvidenceGroup.SIMILAR -> "similar finding"
                PracticeEvidenceGroup.DIFFERENT -> "different finding"
                PracticeEvidenceGroup.CONTEXT -> "context only"
            }
        }, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        Text("Limit: " + (draft.limitation?.let(PracticeCourseContent::limitationText) ?: "No limit selected"),
            style = MaterialTheme.typography.bodyMedium)
        RawText(draft.limitationNote, style = MaterialTheme.typography.bodyMedium)
        Text("Next action: " + (draft.action?.let(PracticeCourseContent::actionText) ?: "No action selected"),
            style = MaterialTheme.typography.bodyMedium)
        RawText(draft.actionReason, style = MaterialTheme.typography.bodyMedium)
        Text("Evidence set " + draft.evidenceVersion, style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
    }
}

@Composable
private fun CourseWorkedExample() {
    var show by remember { mutableStateOf(false) }
    TextButton({ show = !show }) { Text(if (show) "Hide the small example" else "Show a small worked example") }
    if (show) PracticeMessage("Another synthetic comparison",
        "If sample X records 12 s and sample Y 18 s, you can describe the shorter recorded time in X. Those two records alone do not establish what caused the difference.",
        EvidriloIconName.LINK)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseHelpSheet(session: PracticeLessonSession?, onDismiss: () -> Unit) {
    var level by remember { mutableIntStateOf(0) }
    val changeRevealed = session?.stage in setOf(PracticeLessonStage.CHANGE, PracticeLessonStage.REVISION,
        PracticeLessonStage.FINAL_REVIEW, PracticeLessonStage.COMPLETE)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = EvidriloColors.Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("A nudge, when you need it.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(when (session?.lesson) {
                PracticeLessonId.STUDIES -> when (level) {
                    0 -> "Ask what each card measured before comparing its result."
                    1 -> if (changeRevealed) "A and B measure recall at different times. C measures preference. D adds another recall finding."
                        else "A and B measure recall at different times. C measures preference."
                    else -> "A preference result is not a recall contradiction. A synthesis can name an observed pattern while keeping different outcomes and contexts visible."
                }
                PracticeLessonId.SURVEY -> when (level) {
                    0 -> "Check the count and denominator, then ask who the sample includes."
                    1 -> if (changeRevealed) "R07 repeats R03. Remove only the duplicate submission, so respondent C is counted once."
                        else "The eight submissions contain five quiet-space choices and three group-space choices. Keep the denominator beside the count."
                    else -> if (changeRevealed) "Five of eight rows becomes four of seven unique respondents. The direction remains a slight majority, but counts and strength must be reconsidered; the sample is still self-selected."
                        else "A majority in these supplied submissions is not a claim about every student. The sample was small and self-selected."
                }
                PracticeLessonId.TABLET -> "Choose measured observations, write your own bounded claim, and connect the next action to a limitation."
                null -> "These three cases practice observations, source comparison, and evidence correction. Open any case, pause, and return. They do not need AI or a network connection."
            }, style = MaterialTheme.typography.bodyLarge)
            if (session != null && session.lesson != PracticeLessonId.TABLET) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ level = (level + 1).coerceAtMost(2) }, enabled = level < 2) { Text("Show a fuller hint") }
                    TextButton({ level = 0 }) { Text("Try it myself") }
                }
            }
            Text("Authored local guidance for synthetic material. This is not a response from an AI provider.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            EvidriloSecondaryButton("Done", onDismiss)
        }
    }
}
