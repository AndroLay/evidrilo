package dev.nextgen.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionCheckResult
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import dev.nextgen.mobile.navigation.EvidriloSystemBackHandler
import kotlin.math.ceil

/** Native UI only: all draft writes and submissions go through the existing callbacks. */
@Composable
internal fun EvidriloPracticeDraftScreen(
    case: ConclusionCase,
    title: String,
    draft: ConclusionDraft,
    validationMessage: String?,
    onDraftChange: (ConclusionDraft) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)?,
    initialStep: EvidriloDraftStep,
    onSelectionSound: () -> Unit,
    audioControls: @Composable () -> Unit = {},
) {
    var taskName by rememberSaveable(case.id, initialStep.name) {
        mutableStateOf(practiceStartingTask(initialStep, draft).name)
    }
    val task = EvidriloPracticeTask.entries.firstOrNull { it.name == taskName } ?: EvidriloPracticeTask.EVIDENCE
    val inputPrompt = practiceInputPrompt(task, draft)
    var showHelp by remember(case.id) { mutableStateOf(false) }
    var confirmReset by remember(case.id) { mutableStateOf(false) }
    var showEvidence by remember(case.id, task) { mutableStateOf(false) }
    var scopeCueMessage by remember(case.id, task) { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    LaunchedEffect(task) { scroll.scrollTo(0) }
    fun previousTask() {
        focusManager.clearFocus()
        taskName = EvidriloPracticeTask.entries[(task.ordinal - 1).coerceAtLeast(0)].name
    }
    EvidriloSystemBackHandler(enabled = task.ordinal > 0 && !showHelp && !confirmReset) { previousTask() }

    PracticeFrame(
        onBack = onBack,
        onHelp = { showHelp = true },
        context = if (case.changeNotice != null) "Evidence-change challenge" else if (title.contains("Revise", ignoreCase = true)) "Your one revision" else "Guided investigation",
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    inputPrompt ?: if (task == EvidriloPracticeTask.ACTION) "Feedback checks the supplied case, not academic quality." else "Your draft stays with this practice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (task.ordinal > 0) {
                        EvidriloBackGesture("Previous practice task", ::previousTask)
                    }
                    Box(Modifier.weight(1f)) {
                        EvidriloPrimaryButton(
                            label = if (task == EvidriloPracticeTask.ACTION) "Review my conclusion" else "Continue",
                            enabled = inputPrompt == null,
                            trailingIcon = if (task == EvidriloPracticeTask.ACTION) EvidriloIconName.CHECKLIST else EvidriloIconName.ARROW_FORWARD,
                            onClick = {
                                focusManager.clearFocus()
                                if (task == EvidriloPracticeTask.ACTION) onSubmit()
                                else taskName = EvidriloPracticeTask.entries[task.ordinal + 1].name
                            },
                        )
                    }
                }
            }
        },
    ) {
        PracticeProgress(task)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scroll).padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PracticeHeading(task.title, task.prompt)
            validationMessage?.let {
                PracticeMessage("A required input needs attention", it, EvidriloIconName.INFO)
            }
            case.changeNotice?.takeIf { task == EvidriloPracticeTask.EVIDENCE }?.let { notice ->
                PracticeMessage("The evidence has changed", notice, EvidriloIconName.HISTORY)
            }
            AnimatedContent(
                targetState = task,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                label = "practiceTask",
            ) { currentTask ->
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    when (currentTask) {
                        EvidriloPracticeTask.EVIDENCE -> {
                            PracticeQuestion(case)
                            PracticeObservations(
                                case = case,
                                selectedIds = draft.evidenceRefs,
                                onToggle = { fact ->
                                    onSelectionSound()
                                    onDraftChange(draft.copy(evidenceRefs = draft.evidenceRefs.practiceToggle(fact.id)))
                                },
                            )
                            PracticeUnknownLinks(case, draft.evidenceRefs, "Evidence") { id ->
                                onDraftChange(draft.copy(evidenceRefs = draft.evidenceRefs.filterNot { it == id }))
                            }
                            PracticeEvidenceThread(case, draft.evidenceRefs)
                        }
                        EvidriloPracticeTask.RELATION -> {
                            PracticeEvidenceDisclosure(case, draft.evidenceRefs, showEvidence) { showEvidence = !showEvidence }
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ConclusionRelation.entries.filter { it != ConclusionRelation.UNSUPPORTED }.forEach { relation ->
                                    PracticeChoice(
                                        title = relation.practiceLabel(),
                                        description = relation.practiceDescription(),
                                        selected = draft.relation == relation,
                                        onClick = { onSelectionSound(); onDraftChange(draft.copy(relation = relation)) },
                                    )
                                }
                            }
                            Text("Choose your interpretation. The case checks come after your complete draft.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                        }
                        EvidriloPracticeTask.CLAIM -> {
                            PracticeEvidenceDisclosure(case, draft.evidenceRefs, showEvidence) { showEvidence = !showEvidence }
                            PracticeTextInput(
                                value = draft.claimText,
                                label = "Your claim",
                                placeholder = "What do your selected observations support?",
                                maxLength = PRACTICE_CLAIM_MAX,
                                minLength = 20,
                                onValueChange = { onDraftChange(draft.copy(claimText = it)) },
                                minLines = 4,
                            )
                            TextButton(onClick = {
                                val cue = "In these observations, "
                                when {
                                    draft.claimText.startsWith(cue, ignoreCase = true) -> scopeCueMessage = "You already have this scope cue. You can edit it in your sentence."
                                    draft.claimText.length + cue.length > PRACTICE_CLAIM_MAX -> scopeCueMessage = "Shorten the draft before adding this cue. Your wording has been kept."
                                    else -> { onDraftChange(draft.copy(claimText = cue + draft.claimText)); scopeCueMessage = null }
                                }
                            }) {
                                EvidriloIcon(EvidriloIconName.PLUS, tint = EvidriloColors.Cobalt, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Add an editable scope cue")
                            }
                            scopeCueMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                            Text("Keep the sentence yours. Use the selected records; avoid adding facts the case does not supply.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                        }
                        EvidriloPracticeTask.SCOPE -> {
                            PracticeClaim("Your current claim", draft.claimText)
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ConclusionScope.entries.filter { it != ConclusionScope.UNSUPPORTED }.forEach { scope ->
                                    PracticeChoice(
                                        title = scope.practiceLabel(),
                                        description = scope.practiceDescription(),
                                        selected = draft.scope == scope,
                                        onClick = { onSelectionSound(); onDraftChange(draft.copy(scope = scope)) },
                                    )
                                }
                            }
                        }
                        EvidriloPracticeTask.LIMITS -> {
                            case.factsOfType(ConclusionFactType.LIMITATION).forEach { fact ->
                                PracticeChoice(
                                    title = fact.text,
                                    description = "Supplied limitation",
                                    selected = fact.id in draft.limitationRefs,
                                    checkbox = true,
                                    enabled = fact.id in draft.limitationRefs || draft.limitationRefs.size < 2,
                                    onClick = { onSelectionSound(); onDraftChange(draft.copy(limitationRefs = draft.limitationRefs.practiceToggle(fact.id))) },
                                )
                            }
                            PracticeUnknownLinks(case, draft.limitationRefs, "Limitation") { id ->
                                onDraftChange(draft.copy(limitationRefs = draft.limitationRefs.filterNot { it == id }))
                            }
                            PracticeTextInput(
                                value = draft.limitationNote,
                                label = "Why does this limit matter?",
                                placeholder = "Explain what the evidence leaves uncertain…",
                                maxLength = PRACTICE_NOTE_MAX,
                                minLength = 10,
                                onValueChange = { onDraftChange(draft.copy(limitationNote = it)) },
                            )
                        }
                        EvidriloPracticeTask.ACTION -> {
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ConclusionImplication.entries.filter { it != ConclusionImplication.UNSUPPORTED }.forEach { action ->
                                    PracticeChoice(
                                        title = action.practiceLabel(),
                                        description = practiceActionDescription(action, case),
                                        selected = draft.implication == action,
                                        onClick = { onSelectionSound(); onDraftChange(draft.copy(implication = action)) },
                                    )
                                }
                            }
                            PracticeTextInput(
                                value = draft.implicationReason,
                                label = "Why this next action?",
                                placeholder = "Connect the action to a limitation you selected…",
                                maxLength = PRACTICE_NOTE_MAX,
                                minLength = 10,
                                onValueChange = { onDraftChange(draft.copy(implicationReason = it)) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showHelp) PracticeHelpSheet(case, task, onDismiss = { showHelp = false }, onRestart = { showHelp = false; confirmReset = true }, audioControls = audioControls)
    if (confirmReset) PracticeResetDialog(onDismiss = { confirmReset = false }, onReset = { confirmReset = false; onReset() })
}

private fun List<String>.practiceToggle(id: String): List<String> =
    if (id in this) filterNot { it == id } else this + id

@Composable
internal fun PracticeFrame(
    onBack: (() -> Unit)?,
    onHelp: () -> Unit,
    context: String,
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(EvidriloColors.Canvas), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onBack?.let { EvidriloBackGesture("Leave practice", it) }
                Column(Modifier.weight(1f)) {
                    Text("Practice", style = MaterialTheme.typography.titleMedium)
                    Text(context, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                EvidriloIconButton(EvidriloIconName.QUESTION, "Open practice hints and supplied facts", onHelp)
            }
            Column(Modifier.weight(1f).padding(horizontal = 20.dp), content = content)
            HorizontalDivider(color = EvidriloColors.Separator)
            Box(Modifier.fillMaxWidth().background(EvidriloColors.Card).padding(horizontal = 20.dp, vertical = 12.dp)) { footer() }
        }
    }
}

@Composable
private fun PracticeProgress(task: EvidriloPracticeTask) {
    val progress by animateFloatAsState((task.ordinal + 1f) / EvidriloPracticeTask.entries.size, tween(220, easing = FastOutSlowInEasing), label = "practiceProgress")
    val color = EvidriloColors.Cobalt
    val track = EvidriloColors.Separator
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(7.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo((task.ordinal + 1f) / EvidriloPracticeTask.entries.size, 0f..1f)
            contentDescription = "Practice progress: ${task.label}"
        }) {
            drawLine(track, Offset(3.dp.toPx(), size.height / 2), Offset(size.width - 3.dp.toPx(), size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round)
            drawLine(color, Offset(3.dp.toPx(), size.height / 2), Offset(3.dp.toPx() + (size.width - 6.dp.toPx()) * progress, size.height / 2), strokeWidth = size.height, cap = StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Evidence", "Claim", "Next action").forEachIndexed { index, label ->
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (task.ordinal / 2 == index) EvidriloColors.Cobalt else EvidriloColors.Slate)
            }
        }
    }
}

@Composable
internal fun PracticeHeading(title: String, prompt: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Text(prompt, style = MaterialTheme.typography.bodyLarge, color = EvidriloColors.Slate)
    }
}

@Composable
private fun PracticeQuestion(case: ConclusionCase) {
    Column(
        Modifier.fillMaxWidth().background(EvidriloColors.Tint, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(case.factsOfType(ConclusionFactType.AIM).firstOrNull()?.text ?: case.description, style = MaterialTheme.typography.titleMedium)
        Text("Synthetic practice data", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Cobalt)
    }
}

@Composable
private fun PracticeObservations(case: ConclusionCase, selectedIds: List<String>, onToggle: ((ConclusionFact) -> Unit)?) {
    val observations = case.factsOfType(ConclusionFactType.OBSERVATION)
    val numericValues = observations.map { fact -> fact.displayValue?.let { Regex("^(\\d+(?:\\.\\d+)?)\\s*s$").matchEntire(it.trim())?.groupValues?.get(1)?.toFloatOrNull() } }
    val comparable = observations.isNotEmpty() && numericValues.all { it != null && it >= 0f }
    // Keep the M0 and evidence-change comparison on the same 0–100 s scale.
    // Longer supplied records use labeled 100-second increments.
    val maximum = ceil((numericValues.filterNotNull().maxOrNull() ?: 1f) / 100f).coerceAtLeast(1f) * 100f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        observations.forEachIndexed { index, fact ->
            val selected = fact.id in selectedIds
            val choiceColors = evidriloChoiceColors(selected)
            val enabled = onToggle == null || selected || selectedIds.size < 3
            val interaction = if (onToggle != null) Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(fact) }) else Modifier
            Column(
                modifier = Modifier.fillMaxWidth().then(interaction).semantics(mergeDescendants = true) {
                    stateDescription = if (onToggle == null) "Available observation" else if (selected) "Connected to your claim" else if (enabled) "Not selected" else "Three evidence links already selected"
                }.background(choiceColors.container, RoundedCornerShape(16.dp)).border(1.5.dp, choiceColors.border, RoundedCornerShape(16.dp)).padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(fact.displayLabel ?: "Observation", style = MaterialTheme.typography.titleMedium)
                        Text(fact.text, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    }
                    fact.displayValue?.let { value -> Text(value, style = MaterialTheme.typography.headlineSmall, color = EvidriloColors.Cobalt) }
                    if (onToggle != null) PracticeSelectionMark(selected, checkbox = true)
                }
                if (comparable) {
                    val fill by animateFloatAsState(numericValues[index]!! / maximum, tween(180), label = "observationBar")
                    Box(Modifier.fillMaxWidth().height(7.dp).background(EvidriloColors.Separator, CircleShape)) {
                        Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(7.dp).background(if (selected || onToggle == null) EvidriloColors.Cobalt else EvidriloColors.PatternBlue, CircleShape))
                    }
                }
            }
        }
        if (case.changeNotice != null) {
            PracticeMessage("Unavailable in this round", "The withdrawn observation is not part of this active evidence set.", EvidriloIconName.INFO)
        }
        if (comparable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recorded time · seconds", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                Text("0–${maximum.toInt()} s", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
        }
    }
}

@Composable
private fun PracticeSelectionMark(selected: Boolean, checkbox: Boolean) {
    Box(
        Modifier.size(26.dp).background(if (selected) EvidriloColors.Cobalt else EvidriloColors.Card, if (checkbox) RoundedCornerShape(8.dp) else CircleShape)
            .border(1.5.dp, if (selected) EvidriloColors.Cobalt else EvidriloColors.Outline, if (checkbox) RoundedCornerShape(8.dp) else CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) EvidriloIcon(EvidriloIconName.CHECK, tint = EvidriloColors.White, modifier = Modifier.size(17.dp))
    }
}

@Composable
internal fun PracticeChoice(title: String, description: String, selected: Boolean, onClick: () -> Unit, checkbox: Boolean = false, enabled: Boolean = true) {
    val colors = evidriloChoiceColors(selected)
    val selectionReveal by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "Evidence selected")
    val interaction = if (checkbox) Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() })
        else Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).graphicsLayer { translationX = selectionReveal * 3.dp.toPx() }.then(interaction).semantics(mergeDescendants = true) {
            stateDescription = if (selected) "Selected" else "Not selected"
        }.background(colors.container, RoundedCornerShape(16.dp)).border(1.5.dp, colors.border, RoundedCornerShape(16.dp)).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PracticeSelectionMark(selected, checkbox)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
    }
}

@Composable
private fun PracticeEvidenceThread(case: ConclusionCase, selectedIds: List<String>) {
    val facts = case.factsOfType(ConclusionFactType.OBSERVATION)
    val count = facts.count { it.id in selectedIds }
    val connections = facts.map { fact ->
        val progress by animateFloatAsState(if (fact.id in selectedIds) 1f else 0f, tween(220, easing = FastOutSlowInEasing), label = "evidenceLink-${fact.id}")
        progress
    }
    val color = EvidriloColors.Cobalt
    val track = EvidriloColors.Separator
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(42.dp)) {
            val bottom = size.height - 4.dp.toPx()
            facts.forEachIndexed { index, _ ->
                val x = size.width * (index + .5f) / facts.size.coerceAtLeast(1)
                val path = Path().apply {
                    moveTo(x, 4.dp.toPx())
                    cubicTo(x, bottom * .65f, size.width / 2, bottom * .3f, size.width / 2, bottom)
                }
                drawPath(path, track, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                val progress = connections[index]
                if (progress > 0f) {
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val segment = Path()
                    measure.getSegment(0f, measure.length * progress, segment, true)
                    drawPath(segment, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                drawCircle(if (progress > 0f) color else track, 3.5.dp.toPx(), Offset(x, 4.dp.toPx()))
            }
            drawCircle(if (count > 0) color else track, 4.dp.toPx(), Offset(size.width / 2, bottom))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (count == 0) "No evidence connected yet" else "$count observation${if (count == 1) "" else "s"} connected", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            EvidriloIcon(EvidriloIconName.LINK, tint = EvidriloColors.Cobalt, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PracticeUnknownLinks(case: ConclusionCase, ids: List<String>, kind: String, onRemove: (String) -> Unit) {
    val unknown = ids.filter { case.fact(it) == null }
    if (unknown.isNotEmpty()) {
        PracticeMessage("Some links are unavailable", "$kind links from an earlier draft are not in this case. Remove them here before reviewing.", EvidriloIconName.INFO)
        unknown.distinct().forEach { id -> TextButton(onClick = { onRemove(id) }) { Text("Remove $id") } }
    }
}

@Composable
private fun PracticeEvidenceDisclosure(case: ConclusionCase, ids: List<String>, expanded: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
            EvidriloIcon(EvidriloIconName.LINK, tint = EvidriloColors.Cobalt, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "Hide my selected evidence" else "View my selected evidence")
        }
        AnimatedVisibility(expanded, enter = fadeIn(tween(140)), exit = fadeOut(tween(90))) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val facts = ids.mapNotNull(case::fact)
                if (facts.isEmpty()) Text("No available observation is connected. Go back to evidence to choose one.", style = MaterialTheme.typography.bodyMedium)
                facts.forEach { PracticeFact(it) }
            }
        }
    }
}

@Composable
internal fun PracticeTextInput(value: String, label: String, placeholder: String, maxLength: Int, minLength: Int, onValueChange: (String) -> Unit, minLines: Int = 3) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength || it.length < value.length) onValueChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(16.dp),
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        isError = value.length > maxLength,
        supportingText = { Text("${value.length}/$maxLength characters · at least $minLength") },
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
internal fun PracticeClaim(label: String, text: String) {
    Column(Modifier.fillMaxWidth().background(EvidriloColors.Card, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
        Text(text.ifBlank { "No claim written yet." }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun PracticeMessage(title: String, body: String, icon: EvidriloIconName) {
    Row(Modifier.fillMaxWidth().background(EvidriloColors.Tint, RoundedCornerShape(16.dp)).padding(17.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EvidriloIcon(icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(21.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PracticeFact(fact: ConclusionFact) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(fact.learnerFacingEvidenceLabel(), style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
        Text(fact.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PracticeHelpSheet(case: ConclusionCase, task: EvidriloPracticeTask?, onDismiss: () -> Unit, onRestart: () -> Unit, audioControls: @Composable () -> Unit = {}) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = EvidriloColors.Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("A hint, if you need it.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(when (task) {
                EvidriloPracticeTask.EVIDENCE -> "Use the observations that answer the comparison question. The supplied hypothesis is a prediction, not a measured result."
                EvidriloPracticeTask.RELATION -> "An observed difference describes the records. It does not establish what caused them."
                EvidriloPracticeTask.CLAIM -> "A cue such as “in these observations” can help you keep the claim bounded. Write the rest in your own words."
                EvidriloPracticeTask.SCOPE -> "Ask whether your wording describes these records or makes a promise about other situations."
                EvidriloPracticeTask.LIMITS -> "Explain how a supplied limitation constrains the conclusion. Keep uncertainty connected to evidence."
                EvidriloPracticeTask.ACTION -> "Choose an action that responds to a limitation you selected. A future trial does not retroactively prove this claim."
                null -> "Read the evidence records and the reason behind the feedback. These checks apply to the supplied case only."
            }, style = MaterialTheme.typography.bodyLarge)
            audioControls()
            HorizontalDivider(color = EvidriloColors.Separator)
            Text("Supplied case facts", style = MaterialTheme.typography.titleMedium)
            case.facts.forEach { fact ->
                PracticeFact(fact)
            }
            TextButton(onClick = onRestart) { Text("Start this practice over") }
            EvidriloSecondaryButton("Done", onDismiss)
        }
    }
}

@Composable
internal fun PracticeResetDialog(onDismiss: () -> Unit, onReset: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start this practice over?") },
        text = { Text("Your current practice draft will be discarded and the workflow will return to its start.") },
        confirmButton = { TextButton(onClick = onReset) { Text("Start over") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep my draft") } },
    )
}

@Composable
internal fun EvidriloPracticeFeedbackScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation,
    onRevise: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)?,
    audioControls: @Composable () -> Unit = {},
) {
    PracticeResultScreen(case, draft, evaluation, "Follow the reasoning.", "Read what the selected facts support and what still needs attention.", "Revise my conclusion", onRevise, onReset, onBack, audioControls = audioControls)
}

@Composable
internal fun EvidriloPracticeChangedFeedbackScreen(
    baseDraft: ConclusionDraft,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation,
    onFinish: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)?,
    audioControls: @Composable () -> Unit = {},
) {
    PracticeResultScreen(
        ConclusionCases.EVIDENCE_CHANGE, draft, evaluation,
        "A smaller set. A fresh review.",
        "Feedback now uses only the observations available in the challenge.",
        "See my comparison", onFinish, onReset, onBack,
        before = baseDraft,
        audioControls = audioControls,
    )
}

@Composable
private fun PracticeResultScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation,
    title: String,
    prompt: String,
    actionLabel: String,
    onAction: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)?,
    before: ConclusionDraft? = null,
    audioControls: @Composable () -> Unit = {},
) {
    var showHelp by remember(case.id) { mutableStateOf(false) }
    var confirmReset by remember(case.id) { mutableStateOf(false) }
    var expandedChecks by remember(case.id) { mutableStateOf(false) }
    var expandedComparison by remember(case.id) { mutableStateOf(false) }
    PracticeFrame(onBack, { showHelp = true }, if (case.changeNotice == null) "Case feedback" else "Challenge feedback", footer = {
        EvidriloPrimaryButton(actionLabel, onAction, trailingIcon = EvidriloIconName.ARROW_FORWARD)
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            PracticeHeading(title, prompt)
            PracticeEvaluation(evaluation, case)
            PracticeClaim("Your current conclusion", draft.claimText)
            if (draft.evidenceRefs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Evidence you selected", style = MaterialTheme.typography.titleMedium)
                    draft.evidenceRefs.forEach { id ->
                        case.fact(id)?.let { PracticeFact(it) }
                            ?: Text("A selected record is not available in this case version.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            before?.let {
                TextButton(onClick = { expandedComparison = !expandedComparison }) { Text(if (expandedComparison) "Hide the earlier claim" else "Compare with the earlier claim") }
                AnimatedVisibility(expandedComparison, enter = fadeIn(tween(140)), exit = fadeOut(tween(90))) {
                    PracticeClaim("Before the evidence changed · retained", it.claimText)
                }
            }
            TextButton(onClick = { expandedChecks = !expandedChecks }) {
                Text(if (expandedChecks) "Hide the check details" else "Inspect the case checks")
            }
            AnimatedVisibility(expandedChecks, enter = fadeIn(tween(140)), exit = fadeOut(tween(90))) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (evaluation.checks.isEmpty()) Text("No individual check results are available. Read the main feedback above.", style = MaterialTheme.typography.bodyMedium)
                    evaluation.checks.forEach { PracticeCheck(it, case) }
                }
            }
            Text("Case-bounded feedback. This is not an academic grade or proof of a real-world causal effect.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
    }
    if (showHelp) PracticeHelpSheet(case, null, { showHelp = false }, { showHelp = false; confirmReset = true }, audioControls)
    if (confirmReset) PracticeResetDialog({ confirmReset = false }, { confirmReset = false; onReset() })
}

@Composable
internal fun PracticeEvaluation(evaluation: ConclusionEvaluation, case: ConclusionCase) {
    val feedback = evaluation.primaryFeedback
    val allPass = feedback == null && evaluation.checks.isNotEmpty() && evaluation.checks.all { it.status == ConclusionStatus.PASS }
    val title = if (allPass) "These case checks pass" else feedback?.status?.displayLabel() ?: "Review the available feedback"
    Column(Modifier.fillMaxWidth().background(EvidriloColors.Tint, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            EvidriloIcon(if (allPass) EvidriloIconName.CHECK else EvidriloIconName.INFO, tint = EvidriloColors.Cobalt)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        }
        if (feedback != null) {
            Text(feedback.message, style = MaterialTheme.typography.bodyLarge)
            Text(feedback.why, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = EvidriloColors.Separator)
            Text("Your next move", style = MaterialTheme.typography.titleSmall)
            Text(feedback.nextAction, style = MaterialTheme.typography.bodyMedium)
            if (feedback.anchorIds.isNotEmpty()) {
                Text("Evidence behind this feedback", style = MaterialTheme.typography.titleSmall)
                feedback.anchorIds.forEach { id ->
                    case.fact(id)?.let { PracticeFact(it) }
                        ?: Text("A referenced record is not available in this case version.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text(if (allPass) "The supplied checks connect your evidence, scope, limitations, and next action." else "There is not enough check information to confirm a result.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PracticeCheck(check: ConclusionCheckResult, case: ConclusionCase) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(check.check.displayLabel(), style = MaterialTheme.typography.titleMedium)
        Text(check.status.displayLabel(), style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
        Text(check.reason, style = MaterialTheme.typography.bodyMedium)
        if (check.anchorIds.isNotEmpty()) {
            Text("Evidence checked", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
            check.anchorIds.forEach { id ->
                case.fact(id)?.let { PracticeFact(it) }
                    ?: Text("A referenced record is not available in this case version.", style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(color = EvidriloColors.Separator)
    }
}

@Composable
internal fun EvidriloPracticeChangedSummaryScreen(
    baseDraft: ConclusionDraft,
    challengeDraft: ConclusionDraft,
    challengeEvaluation: ConclusionEvaluation,
    onReset: () -> Unit,
    onBack: (() -> Unit)?,
    audioControls: @Composable () -> Unit = {},
    onOpenProjects: (() -> Unit)? = null,
    onFinish: (() -> Unit)? = null,
) {
    val case = ConclusionCases.EVIDENCE_CHANGE
    var showHelp by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var showActiveRecords by remember { mutableStateOf(false) }
    PracticeFrame(onBack, { showHelp = true }, "Your evidence-change comparison", footer = {
        EvidriloPrimaryButton(if (onFinish == null) "Start a new attempt" else "Explore practice", onFinish ?: onReset, trailingIcon = EvidriloIconName.ARROW_FORWARD)
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            PracticeHeading("You reconsidered the claim.", "When evidence changes, trace what still supports the conclusion.")
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                EvidriloIcon(EvidriloIconName.EVIDENCE_GRAPH, tint = EvidriloColors.Cobalt, modifier = Modifier.size(48.dp))
                Text("Evidence → claim → next action", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            PracticeClaim("Before the evidence changed", baseDraft.claimText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { EvidriloIcon(EvidriloIconName.HISTORY, tint = EvidriloColors.Cobalt) }
            PracticeClaim("With the available evidence", challengeDraft.claimText)
            case.changeNotice?.let { PracticeMessage("What changed", it, EvidriloIconName.LINK) }
            PracticeEvaluation(challengeEvaluation, case)
            TextButton(onClick = { showActiveRecords = !showActiveRecords }) { Text(if (showActiveRecords) "Hide active records" else "Inspect the active evidence") }
            AnimatedVisibility(showActiveRecords, enter = fadeIn(tween(140)), exit = fadeOut(tween(90))) { PracticeObservations(case, challengeDraft.evidenceRefs, null) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A move for your next project", style = MaterialTheme.typography.titleMedium)
                Text("Link a claim to its sources. If a source changes or becomes unavailable, review the claim before reusing it.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                onOpenProjects?.let { open -> EvidriloSecondaryButton("Open my projects", open) }
            }
            Text("This is a synthetic practice case. The comparison is not a real experiment or an academic grade.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
    }
    if (showHelp) PracticeHelpSheet(case, null, { showHelp = false }, { showHelp = false; confirmReset = true }, audioControls)
    if (confirmReset) PracticeResetDialog({ confirmReset = false }, { confirmReset = false; onReset() })
}
