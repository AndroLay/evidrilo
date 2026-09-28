package dev.nextgen.mobile

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.ai.AiAssistPurpose
import dev.nextgen.mobile.ai.AiConversationHistoryMessage
import dev.nextgen.mobile.ai.AiConversationProposal
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionFeedbackItem
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Clock

internal fun evidriloAssistantContextKey(
    case: ConclusionCase,
    draft: ConclusionDraft,
    feedback: ConclusionFeedbackItem?,
): String = listOf(
    case.id,
    case.remoteCaseVersionId.orEmpty(),
    draft.hashCode().toString(),
    feedback?.code.orEmpty(),
    feedback?.status?.name.orEmpty(),
    feedback?.anchorIds?.joinToString(",").orEmpty(),
).joinToString("|")

/** A movable, case-scoped entry point; the local guide never calls the AI service. */
@Composable
internal fun EvidriloFloatingAssistant(
    visible: Boolean,
    dockNearTop: Boolean = false,
    case: ConclusionCase,
    draft: ConclusionDraft,
    feedback: ConclusionFeedbackItem?,
    accountId: String?,
    aiState: EvidriloAiAssistUiState,
    aiClearState: EvidriloAiConversationClearState,
    onRequestAi: (AiAssistPurpose, String, List<AiConversationHistoryMessage>) -> Unit,
    onClearAiConversation: () -> Unit,
    onRetryClearAiConversation: (String) -> Unit,
    canApplyAiProposal: (AiConversationProposal) -> Boolean,
    onApplyAiProposal: (AiConversationProposal) -> Boolean,
    onRetryAi: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenVerify: () -> Unit,
) {
    var opened by remember { mutableStateOf(false) }
    var aiMode by remember { mutableStateOf(false) }
    var bubblePosition by remember { mutableStateOf<Offset?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val contextKey = "${accountId.orEmpty()}|${evidriloAssistantContextKey(case, draft, feedback)}"
    val canAskAi = feedback != null && feedback.anchorIds.isNotEmpty() && case.remoteCaseVersionId != null

    LaunchedEffect(visible, contextKey) {
        if (!visible) opened = false
        if (!canAskAi) aiMode = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bubbleSize = with(density) { 52.dp.roundToPx() }
        val margin = with(density) { 16.dp.roundToPx() }.toFloat()
        val lowerClearance = with(density) { 96.dp.roundToPx() }
        val minY = with(density) { 24.dp.roundToPx() }.toFloat()
        val maxX = (constraints.maxWidth - bubbleSize - margin).coerceAtLeast(margin)
        val maxY = (constraints.maxHeight - bubbleSize - lowerClearance).coerceAtLeast(minY.toInt()).toFloat()
        val defaultY = if (dockNearTop) {
            with(density) { 120.dp.roundToPx() }.toFloat().coerceIn(minY, maxY)
        } else {
            maxY
        }
        val defaultPosition = Offset(maxX, defaultY)
        val currentPosition = bubblePosition ?: defaultPosition
        val clampedPosition = Offset(
            currentPosition.x.coerceIn(margin, maxX),
            currentPosition.y.coerceIn(minY, maxY),
        )
        val animatedPosition by animateIntOffsetAsState(
            targetValue = IntOffset(clampedPosition.x.roundToInt(), clampedPosition.y.roundToInt()),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "assistantBubblePosition",
        )

        if (visible) {
            Surface(
                modifier = Modifier
                    .offset { if (dragging) IntOffset(clampedPosition.x.roundToInt(), clampedPosition.y.roundToInt()) else animatedPosition }
                    .size(52.dp)
                    .pointerInput(maxX, maxY, dockNearTop) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val position = bubblePosition ?: defaultPosition
                                bubblePosition = position.copy(x = if (position.x < maxX / 2f) margin else maxX)
                            },
                            onDragCancel = { dragging = false },
                        ) { change, amount ->
                            change.consume()
                            val position = bubblePosition ?: defaultPosition
                            bubblePosition = Offset(
                                (position.x + amount.x).coerceIn(margin, maxX),
                                (position.y + amount.y).coerceIn(minY, maxY),
                            )
                        }
                    }
                    .semantics {
                        contentDescription = "Open Evidrilo chat guide. Drag to move."
                        role = Role.Button
                        customActions = listOf(
                            CustomAccessibilityAction("Move assistant to left edge") {
                                bubblePosition = clampedPosition.copy(x = margin)
                                true
                            },
                            CustomAccessibilityAction("Move assistant to right edge") {
                                bubblePosition = clampedPosition.copy(x = maxX)
                                true
                            },
                        )
                    }
                    .clickable { opened = true; aiMode = false },
                shape = CircleShape,
                color = EvidriloColors.Cobalt,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.linearGradient(listOf(EvidriloColors.Cobalt, EvidriloColors.CobaltBright)),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    EvidriloIcon(
                        name = EvidriloIconName.CHAT_BUBBLE,
                        tint = EvidriloColors.White,
                        modifier = Modifier.size(27.dp),
                    )
                }
            }
        }
    }

    if (visible && canAskAi) {
        val selectedFeedback = requireNotNull(feedback)
        EvidriloAiAssistCard(
            state = aiState,
            clearState = aiClearState,
            contextKey = contextKey,
            contextPreview = "${case.title} · ${selectedFeedback.code}\nEvidence: ${selectedFeedback.anchorIds.joinToString()}",
            feedback = selectedFeedback,
            opened = opened && aiMode,
            onDismiss = { opened = false; aiMode = false },
            onRequest = onRequestAi,
            onClearConversation = onClearAiConversation,
            onRetryClearConversation = onRetryClearAiConversation,
            canApplyProposal = canApplyAiProposal,
            onApplyProposal = onApplyAiProposal,
            onRetry = onRetryAi,
            onOpenAccount = onOpenAccount,
        )
    }
    if (visible && opened && !aiMode) {
        EvidriloLocalChatSheet(
            case = case,
            draft = draft,
            feedback = feedback,
            contextKey = contextKey,
            canAskAi = canAskAi,
            onAskAi = { aiMode = true },
            onOpenEvidence = { opened = false; onOpenEvidence() },
            onOpenPractice = { opened = false; onOpenPractice() },
            onOpenVerify = { opened = false; onOpenVerify() },
            onDismiss = { opened = false },
        )
    }
}

private enum class GuideTopic(val label: String) {
    NEXT_STEP("What should I do next?"),
    OBSERVATIONS("Show the observations"),
    FEEDBACK("Explain the feedback"),
    ANCHORS("Which evidence is linked?"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidriloLocalChatSheet(
    case: ConclusionCase,
    draft: ConclusionDraft,
    feedback: ConclusionFeedbackItem?,
    contextKey: String,
    canAskAi: Boolean,
    onAskAi: () -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    val store = remember { createChatSessionStore() }
    val initialHistory = remember { store.load() }
    var sessions by remember { mutableStateOf(initialHistory.getOrElse { emptyList() }) }
    var selectedId by remember(contextKey) {
        mutableStateOf(sessions.firstOrNull { it.contextKey == contextKey }?.id)
    }
    var showHistory by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var composerFocused by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(if (initialHistory.isFailure) "Local chat history could not be loaded. Your saved chats were not changed." else "") }
    var pendingDeletionId by remember { mutableStateOf<String?>(null) }
    val pendingFiles = remember { mutableStateListOf<ChatAttachment>() }
    val selectedSession = sessions.firstOrNull { it.id == selectedId }
    val readOnly = selectedSession != null && selectedSession.contextKey != contextKey
    val messages = selectedSession?.messages.orEmpty()
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(messages.size, selectedId, showHistory, composerFocused) {
        if (!showHistory) scroll.animateScrollTo(scroll.maxValue)
    }
    DisposableEffect(Unit) {
        onDispose { pendingFiles.forEach { store.deleteAttachment(it.id) } }
    }

    val persist: (List<ChatSession>) -> Boolean = { next ->
        if (initialHistory.isFailure || !store.save(next)) {
            notice = "Could not save this chat on your device. Try again after checking free storage."
            false
        } else {
            sessions = next
            notice = ""
            true
        }
    }
    val startNewChat = {
        pendingFiles.forEach { store.deleteAttachment(it.id) }
        pendingFiles.clear()
        selectedId = null
        question = ""
        showHistory = false
        notice = if (initialHistory.isFailure) "Local chat history could not be loaded. Your saved chats were not changed." else ""
    }
    val sendMessage: (String, GuideTopic?) -> Unit = { raw, topic ->
        val text = raw.trim()
        if (!readOnly && (text.isNotEmpty() || pendingFiles.isNotEmpty())) {
            when {
                selectedSession == null && sessions.size >= 50 -> notice = "Chat history is full. Delete an old chat to start another."
                messages.size >= 100 -> notice = "This chat is full. Start a new chat to continue."
                else -> {
                    val files = pendingFiles.toList()
                    val answer = if (topic != null) guideAnswer(topic, case, draft, feedback)
                        else guideAnswerForText(text, case, draft, feedback)
                    val fileNote = if (files.isNotEmpty()) {
                        "\n\nYour file is saved locally with this chat. I have not read or evaluated it, and it is not part of the case evidence."
                    } else ""
                    val now = Clock.System.now().toEpochMilliseconds()
                    val id = selectedSession?.id ?: "${now}_${Random.nextLong().toString(16)}"
                    val updated = ChatSession(
                        id = id,
                        contextKey = contextKey,
                        caseTitle = case.title,
                        title = selectedSession?.title ?: text.ifBlank { files.first().name }.take(48),
                        updatedAtMillis = now,
                        messages = messages + ChatMessage(true, text, files) + ChatMessage(false, answer + fileNote),
                    )
                    val next = (sessions.filterNot { it.id == id } + updated).sortedByDescending { it.updatedAtMillis }
                    if (persist(next)) {
                        selectedId = id
                        question = ""
                        pendingFiles.clear()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    }
                }
            }
        }
    }
    val pickFile = rememberChatFilePicker { result ->
        when (result) {
            is ChatFilePickResult.Picked -> {
                if (readOnly || pendingFiles.size >= 3) {
                    store.deleteAttachment(result.attachment.id)
                    notice = "You can attach up to three files to one message."
                } else {
                    pendingFiles.add(result.attachment)
                    notice = ""
                }
            }
            ChatFilePickResult.TooLarge -> notice = "This file is over 10 MB. Choose a smaller file."
            is ChatFilePickResult.Failed -> notice = result.reason
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(shape = CircleShape, color = EvidriloColors.PaleBlue) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        EvidriloIcon(EvidriloIconName.CHAT_BUBBLE, tint = EvidriloColors.Cobalt, modifier = Modifier.size(23.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(if (showHistory) "Chat history" else "Evidrilo chat", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Ink)
                    Text("Local guide · ${case.title}", maxLines = 1, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                EvidriloIconButton(EvidriloIconName.HISTORY, "Chat history", onClick = { showHistory = !showHistory })
                EvidriloIconButton(EvidriloIconName.PLUS, "New chat", onClick = startNewChat)
                EvidriloIconButton(EvidriloIconName.CHEVRON_DOWN, "Close chat", onClick = onDismiss)
            }

            if (showHistory) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved on this device", style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Ink)
                    if (sessions.isEmpty()) {
                        Text("No chats yet. Start a conversation about this case and it will appear here.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    }
                    sessions.forEach { session ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                pendingFiles.forEach { store.deleteAttachment(it.id) }
                                pendingFiles.clear()
                                question = ""
                                selectedId = session.id
                                showHistory = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (session.id == selectedId) EvidriloColors.PaleBlue else EvidriloColors.Surface,
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(session.title, style = MaterialTheme.typography.titleSmall, color = EvidriloColors.Ink, maxLines = 1)
                                    Text(
                                        if (session.contextKey == contextKey) "Current case state · ${session.messages.size / 2} questions"
                                        else "Earlier case state · read only",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EvidriloColors.Slate,
                                    )
                                }
                                TextButton(onClick = { pendingDeletionId = session.id }) { Text("Delete") }
                            }
                        }
                    }
                }
            } else {
                if (readOnly) {
                    Text("This chat is from an earlier case state. You can read it here; start a new chat for the current facts.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (messages.isEmpty() && !composerFocused) {
                        Text("What would you like to understand?", style = MaterialTheme.typography.headlineSmall, color = EvidriloColors.Ink)
                        Text("Ask about the supplied observations, your hypothesis, or the next step. Answers stay within this case.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    } else {
                        messages.forEach { message ->
                            if (message.fromLearner) GuideLearnerBubble(message.text, message.attachments)
                            else GuideAssistantBubble(message.text)
                        }
                    }
                }

                if (!readOnly && !composerFocused) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val topics = if (feedback == null) listOf(GuideTopic.NEXT_STEP, GuideTopic.OBSERVATIONS)
                            else listOf(GuideTopic.FEEDBACK, GuideTopic.ANCHORS, GuideTopic.NEXT_STEP)
                        topics.forEach { topic ->
                            Surface(
                                modifier = Modifier.heightIn(min = 44.dp).clickable(enabled = initialHistory.isSuccess) { sendMessage(topic.label, topic) },
                                shape = CircleShape,
                                color = EvidriloColors.PaleBlue,
                            ) {
                                Text(topic.label, modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp), style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
                            }
                        }
                    }
                }

                if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Error)
                if (pendingFiles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pendingFiles.forEach { file ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                EvidriloIcon(EvidriloIconName.FILE, tint = EvidriloColors.Cobalt, modifier = Modifier.size(18.dp))
                                Text("${file.name} · ${formatChatFileSize(file.sizeBytes)}", modifier = Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Ink, maxLines = 1)
                                TextButton(onClick = { pendingFiles.remove(file); store.deleteAttachment(file.id) }) { Text("Remove") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        modifier = Modifier.size(48.dp)
                            .semantics { contentDescription = "Add file, maximum 10 MB"; role = Role.Button }
                            .clickable(enabled = !readOnly && initialHistory.isSuccess && pendingFiles.size < 3, onClick = pickFile),
                        shape = CircleShape,
                        color = EvidriloColors.PaleBlue,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            EvidriloIcon(EvidriloIconName.PLUS, tint = EvidriloColors.Cobalt, modifier = Modifier.size(23.dp))
                        }
                    }
                    OutlinedTextField(
                        value = question,
                        onValueChange = { if (it.length <= 500) question = it },
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp).onFocusChanged { composerFocused = it.isFocused },
                        placeholder = { Text(if (readOnly) "Start a new chat to continue" else "Ask about this case") },
                        enabled = !readOnly && initialHistory.isSuccess,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage(question, null) }),
                        shape = RoundedCornerShape(18.dp),
                    )
                    Surface(
                        modifier = Modifier.size(48.dp)
                            .semantics { contentDescription = "Send message"; role = Role.Button }
                            .clickable(enabled = !readOnly && initialHistory.isSuccess && (question.isNotBlank() || pendingFiles.isNotEmpty()), onClick = { sendMessage(question, null) }),
                        shape = CircleShape,
                        color = if (!readOnly && (question.isNotBlank() || pendingFiles.isNotEmpty())) EvidriloColors.Cobalt else EvidriloColors.Surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = if (!readOnly && (question.isNotBlank() || pendingFiles.isNotEmpty())) EvidriloColors.White else EvidriloColors.Slate, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                if (!composerFocused) {
                    Text("Files up to 10 MB stay on this device. The local guide does not analyze them.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    if (canAskAi) {
                        TextButton(onClick = onAskAi) { Text("Open bounded AI help for this feedback") }
                    } else if (feedback != null) {
                        TextButton(onClick = onOpenVerify) { Text("Open verification") }
                    } else if (draft.evidenceRefs.isEmpty()) {
                        TextButton(onClick = onOpenEvidence) { Text("Explore evidence") }
                    } else {
                        TextButton(onClick = onOpenPractice) { Text("Continue this case") }
                    }
                }
            }
        }
    }
    val toDelete = sessions.firstOrNull { it.id == pendingDeletionId }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeletionId = null },
            title = { Text("Delete this chat?") },
            text = { Text("Its messages and locally stored files will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    if (persist(sessions.filterNot { it.id == toDelete.id })) {
                        toDelete.messages.flatMap { it.attachments }.forEach { store.deleteAttachment(it.id) }
                        if (selectedId == toDelete.id) selectedId = null
                    }
                    pendingDeletionId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeletionId = null }) { Text("Cancel") } },
        )
    }
}

private fun guideAnswer(
    topic: GuideTopic,
    case: ConclusionCase,
    draft: ConclusionDraft,
    feedback: ConclusionFeedbackItem?,
): String = when (topic) {
    GuideTopic.NEXT_STEP -> when {
        feedback != null -> feedback.nextAction
        draft.evidenceRefs.isEmpty() -> "Start with the Evidence screen. Compare the supplied observations, then select the facts that directly support your claim."
        draft.claimText.trim().length < 20 -> "You have selected evidence. Write a claim that stays within those observations, then set its scope and limitation."
        else -> "Review your claim, its scope and limitation, and one useful next action. Then run the deterministic verification."
    }
    GuideTopic.OBSERVATIONS -> case.factsOfType(ConclusionFactType.OBSERVATION)
        .take(4)
        .joinToString("\n") { fact -> "${fact.displayLabel ?: fact.id}: ${fact.displayValue ?: fact.text}" }
        .ifBlank { "This case has no supplied observations to show." }
    GuideTopic.FEEDBACK -> feedback?.let { "${it.message}\n\nWhy: ${it.why}\n\nNext action: ${it.nextAction}" }
        ?: "Run verification to get feedback for this claim."
    GuideTopic.ANCHORS -> feedback?.anchorIds
        ?.map { id -> case.fact(id)?.let { fact -> "[$id] ${fact.text}" } ?: "[$id] Linked in verification" }
        ?.joinToString("\n")
        ?.ifBlank { "This feedback has no linked evidence anchors." }
        ?: "Run verification to see linked evidence anchors."
}

private fun guideAnswerForText(
    question: String,
    case: ConclusionCase,
    draft: ConclusionDraft,
    feedback: ConclusionFeedbackItem?,
): String {
    val normalized = question.lowercase()
    return when {
        normalized.isBlank() -> "I can help with the supplied case facts. Ask about the observations, your hypothesis, or the next step."
        listOf("hypothesis", "hipotesis", "dugaan").any(normalized::contains) -> {
            val observations = guideAnswer(GuideTopic.OBSERVATIONS, case, draft, feedback)
            "A useful hypothesis names a relationship you can check, then states its limits. Start with these supplied observations:\n$observations\n\nTry: ‘If [condition changes], then [observed outcome] may change within this case.’ What other condition would you need to control before making a broader claim?"
        }
        listOf("observation", "observasi", "data", "fakta", "fact").any(normalized::contains) ->
            guideAnswer(GuideTopic.OBSERVATIONS, case, draft, feedback)
        listOf("feedback", "verifikasi", "verify", "hasil").any(normalized::contains) ->
            guideAnswer(GuideTopic.FEEDBACK, case, draft, feedback)
        listOf("evidence", "anchor", "bukti", "sumber").any(normalized::contains) ->
            guideAnswer(GuideTopic.ANCHORS, case, draft, feedback)
        listOf("next", "step", "langkah", "selanjutnya", "lanjut").any(normalized::contains) ->
            guideAnswer(GuideTopic.NEXT_STEP, case, draft, feedback)
        else -> "I can guide you through this case's supplied facts and current verification. Try asking about the observations, a bounded hypothesis, linked evidence, or your next step. I cannot assess material outside this case."
    }
}

private fun formatChatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${(bytes * 10 / (1024L * 1024L)) / 10.0} MB"
    bytes >= 1024L -> "${(bytes + 1023L) / 1024L} KB"
    else -> "$bytes B"
}

@Composable
private fun GuideAssistantBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 5.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            color = EvidriloColors.PaleBlue,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Evidrilo · local guide", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Ink)
            }
        }
    }
}

@Composable
private fun GuideLearnerBubble(text: String, attachments: List<ChatAttachment>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 5.dp, bottomStart = 18.dp),
            color = EvidriloColors.Cobalt,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.White)
                attachments.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EvidriloIcon(EvidriloIconName.FILE, tint = EvidriloColors.White, modifier = Modifier.size(17.dp))
                        Text("${file.name} · ${formatChatFileSize(file.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.White, maxLines = 2)
                    }
                }
            }
        }
    }
}
