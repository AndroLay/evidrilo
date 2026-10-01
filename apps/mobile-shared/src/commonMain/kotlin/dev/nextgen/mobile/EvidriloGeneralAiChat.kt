package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nextgen.mobile.EvidriloUiText as Text
import dev.nextgen.mobile.projectcatalog.*
import dev.nextgen.mobile.analytics.newAnalyticsEventId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class ProjectAiEntryProject(val id: String, val title: String, val context: ProjectAiChatContext? = null)

internal fun projectAiEntryEmptyStateMessage(): String =
    "No project with a supported AI stage is available yet. Your saved projects remain available in Home."

internal fun projectAiEntryProjectOption(
    projectId: String,
    title: String,
    isActive: Boolean,
    templatePublished: Boolean,
    hasDeclaredAiOperation: Boolean,
): ProjectAiEntryProject? =
    if (projectId.isNotBlank() && isActive && templatePublished && hasDeclaredAiOperation) {
        ProjectAiEntryProject(projectId, title)
    } else {
        null
    }

internal enum class ProjectAiEntrySurface { CLOSED, MODE_PICKER, PROJECT_PICKER, GENERAL_CHAT, ACTIVITY }

internal fun shouldRefreshAiCreditBalance(surface: ProjectAiEntrySurface): Boolean =
    surface == ProjectAiEntrySurface.MODE_PICKER ||
        surface == ProjectAiEntrySurface.PROJECT_PICKER ||
        surface == ProjectAiEntrySurface.GENERAL_CHAT

internal enum class ProjectAiEntryEvent { OPEN, CHOOSE_PROJECT, CHOOSE_GENERAL, CHOOSE_ACTIVITY, BACK, DISMISS }

internal data class AiShortcutPosition(val xPx: Float, val yPx: Float)

internal data class AiShortcutBounds(
    val minXPx: Float,
    val minYPx: Float,
    val maxXPx: Float,
    val maxYPx: Float,
)

internal fun initialAiShortcutPosition(
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    shortcutSizePx: Float,
    endInsetPx: Float,
    bottomInsetPx: Float,
    bounds: AiShortcutBounds,
): AiShortcutPosition = clampAiShortcutPosition(
    AiShortcutPosition(
        xPx = viewportWidthPx - shortcutSizePx - endInsetPx,
        yPx = viewportHeightPx - shortcutSizePx - bottomInsetPx,
    ),
    bounds,
)

internal fun dragAiShortcutPosition(
    current: AiShortcutPosition,
    deltaXPx: Float,
    deltaYPx: Float,
    bounds: AiShortcutBounds,
): AiShortcutPosition = clampAiShortcutPosition(
    AiShortcutPosition(current.xPx + deltaXPx, current.yPx + deltaYPx),
    bounds,
)

internal fun clampAiShortcutPosition(
    position: AiShortcutPosition,
    bounds: AiShortcutBounds,
): AiShortcutPosition {
    val maxX = bounds.maxXPx.coerceAtLeast(bounds.minXPx)
    val maxY = bounds.maxYPx.coerceAtLeast(bounds.minYPx)
    return AiShortcutPosition(
        xPx = position.xPx.coerceIn(bounds.minXPx, maxX),
        yPx = position.yPx.coerceIn(bounds.minYPx, maxY),
    )
}

internal fun reduceProjectAiEntrySurface(
    current: ProjectAiEntrySurface,
    event: ProjectAiEntryEvent,
): ProjectAiEntrySurface = when (event) {
    ProjectAiEntryEvent.OPEN -> ProjectAiEntrySurface.MODE_PICKER
    ProjectAiEntryEvent.CHOOSE_PROJECT -> if (current in setOf(
            ProjectAiEntrySurface.MODE_PICKER,
            ProjectAiEntrySurface.GENERAL_CHAT,
        )
    ) {
        ProjectAiEntrySurface.PROJECT_PICKER
    } else current
    ProjectAiEntryEvent.CHOOSE_GENERAL -> if (current in setOf(
            ProjectAiEntrySurface.MODE_PICKER,
            ProjectAiEntrySurface.PROJECT_PICKER,
        )
    ) {
        ProjectAiEntrySurface.GENERAL_CHAT
    } else current
    ProjectAiEntryEvent.CHOOSE_ACTIVITY -> if (current == ProjectAiEntrySurface.GENERAL_CHAT) {
        ProjectAiEntrySurface.ACTIVITY
    } else current
    ProjectAiEntryEvent.BACK -> when (current) {
        ProjectAiEntrySurface.PROJECT_PICKER, ProjectAiEntrySurface.GENERAL_CHAT -> ProjectAiEntrySurface.MODE_PICKER
        ProjectAiEntrySurface.ACTIVITY -> ProjectAiEntrySurface.GENERAL_CHAT
        else -> current
    }
    ProjectAiEntryEvent.DISMISS -> ProjectAiEntrySurface.CLOSED
}

@Composable
internal fun EvidriloGeneralAiChat(
    visible: Boolean, editorMode: Boolean = false, accountKey: String?,
    initialProject: ProjectAiChatContext? = null,
    projects: List<ProjectAiEntryProject>, projectsLoading: Boolean,
    accessMessage: String?, apiConfigured: Boolean,
    aiCreditBalance: AiCreditBalancePresentation = AiCreditBalancePresentation.SignInRequired,
    consentState: ProjectAiConsentUiState, canOpenAccount: Boolean,
    onOpenAccount: () -> Unit, onRefreshConsent: () -> Unit,
    onRefreshAiCreditBalance: () -> Unit = {}, onGrantConsent: () -> Unit,
    onRevokeConsent: () -> Unit, onOpenProjects: () -> Unit, onOpenProject: (String) -> Unit,
    activityHistoryState: ProjectAiActivityHistoryUiState,
    onRefreshActivityHistory: (String?) -> Unit, onLoadMoreActivityHistory: (String?, String) -> Unit,
    onSendMessage: (String, String, List<ProjectAiChatTurn>, ProjectAiChatContext?, (ProjectAiGeneralChatResult) -> Unit) -> Unit,
    onApplyEdits: (String, ProjectAiChatContext, List<ProjectAiChatEdit>) -> ProjectAiChatContext? = { _, _, _ -> null },
    opinionIntent: ProjectOpinionIntent? = null, onConsumeOpinionIntent: () -> Unit = {},
    isOpinionCurrent: (String, Int) -> Boolean = { _, _ -> true },
) {
    var open by remember { mutableStateOf(false) }
    var input by remember(accountKey) { mutableStateOf("") }
    var sending by remember(accountKey) { mutableStateOf(false) }
    var selectedProject by remember(accountKey) { mutableStateOf<ProjectAiChatContext?>(null) }
    val entries = remember(accountKey) { mutableStateListOf<GeneralChatEntry>() }
    val listState = rememberLazyListState()
    val locale = LocalEvidriloLanguage.current.tag
    var shortcutX by rememberSaveable { mutableStateOf<Float?>(null) }
    var shortcutY by rememberSaveable { mutableStateOf<Float?>(null) }
    val shortcut = shortcutX?.let { x -> shortcutY?.let { y -> AiShortcutPosition(x, y) } }
    var draggingShortcut by remember { mutableStateOf(false) }
    val shortcutScale by animateFloatAsState(if (draggingShortcut) 1.08f else 1f)
    var menu by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var projectPicker by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var approveSend by remember(accountKey) { mutableStateOf(false) }
    var awaitingConsent by remember(accountKey) { mutableStateOf(false) }
    var excerptOpen by remember { mutableStateOf(false) }
    var proposal by remember(accountKey) { mutableStateOf<GeneralChatEntry.Answer?>(null) }
    var localNotice by remember(accountKey) { mutableStateOf<String?>(null) }
    val historyStore = remember { createChatSessionStore() }
    var sessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var historyLoaded by remember { mutableStateOf(false) }
    var sessionId by remember(accountKey) { mutableStateOf(newAnalyticsEventId()) }
    val historyPrefix = "live-ai:${accountKey.orEmpty()}:"

    LaunchedEffect(historyStore) {
        val result = withContext(Dispatchers.Default) { historyStore.load() }
        sessions = result.getOrDefault(emptyList()); historyLoaded = result.isSuccess
        if (result.isFailure) localNotice = chatCopy(locale, "History couldn't be read.", "Riwayat tidak dapat dibaca.")
    }
    LaunchedEffect(entries.size, sending, sessionId, historyLoaded, accountKey) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
        if (historyLoaded && accountKey != null && entries.isNotEmpty()) {
            val messages = entries.mapNotNull { entry -> when (entry) {
                is GeneralChatEntry.User -> ChatMessage(true, entry.text)
                is GeneralChatEntry.Answer -> ChatMessage(false, entry.value.answer)
                is GeneralChatEntry.Notice -> null
            } }.takeLast(80)
            val session = ChatSession(sessionId, historyPrefix + (selectedProject?.projectId ?: "general"),
                selectedProject?.title ?: "General Chat", messages.firstOrNull()?.text?.take(60).orEmpty(),
                kotlin.time.Clock.System.now().toEpochMilliseconds(), messages)
            val latest = withContext(Dispatchers.Default) { historyStore.load() }.getOrNull()
            if (latest == null) {
                localNotice = chatCopy(locale, "Chat history wasn't saved.", "Riwayat chat belum tersimpan.")
                return@LaunchedEffect
            }
            val updated = (latest.filterNot { it.id == sessionId } + session).sortedByDescending { it.updatedAtMillis }
            val own = updated.filter { it.contextKey.startsWith(historyPrefix) }.take(30).map { it.id }.toSet()
            val kept = updated.filter { !it.contextKey.startsWith(historyPrefix) || it.id in own }
            if (withContext(Dispatchers.Default) { historyStore.save(kept) }) sessions = kept
            else localNotice = chatCopy(locale, "Chat history wasn't saved.", "Riwayat chat belum tersimpan.")
        }
    }
    fun fresh(context: ProjectAiChatContext? = null) {
        if (sending) return
        selectedProject = context; entries.clear(); input = ""; sessionId = newAnalyticsEventId(); localNotice = null
    }
    fun send() {
        val message = input.trim()
        if (!canSendGeneralAiMessage(message, true, consentState, accessMessage, apiConfigured, sending)) return
        val context = selectedProject
        if (context != null && !isOpinionCurrent(context.projectId, context.revision)) {
            localNotice = chatCopy(locale, "Project changed. Select it again to use its latest version.", "Proyek berubah. Pilih kembali untuk memakai revisi terbaru."); return
        }
        val history = entries.mapNotNull { entry -> when (entry) {
            is GeneralChatEntry.User -> ProjectAiChatTurn("user", entry.text)
            is GeneralChatEntry.Answer -> ProjectAiChatTurn("assistant", entry.value.answer)
            is GeneralChatEntry.Notice -> null
        } }.takeLast(8).let { turns ->
            var size = 0
            turns.asReversed().takeWhile { size += it.text.length; size <= 16000 }.asReversed()
        }
        input = ""; sending = true; localNotice = null
        entries += GeneralChatEntry.User("user-${entries.size}", message)
        onSendMessage(message, locale, history, context) { result ->
            sending = false
            when (result) {
                is ProjectAiGeneralChatResult.Answer -> entries += GeneralChatEntry.Answer("answer-${entries.size}", result.value, context)
                else -> entries += GeneralChatEntry.Notice("notice-${entries.size}", generalChatFailureMessage(result, locale))
            }
            onRefreshAiCreditBalance()
        }
    }
    fun submit() {
        if (input.isBlank() || sending || accessMessage != null || !apiConfigured) return
        if (consentState == ProjectAiConsentUiState.Granted) send()
        else { approveSend = true; onRefreshConsent() }
    }
    LaunchedEffect(consentState, awaitingConsent) {
        if (awaitingConsent && consentState == ProjectAiConsentUiState.Granted) { awaitingConsent = false; send() }
        if (awaitingConsent && consentState is ProjectAiConsentUiState.Unavailable) awaitingConsent = false
    }
    LaunchedEffect(opinionIntent, sending) {
        opinionIntent?.let { intent ->
            if (!sending) {
                fresh(projects.firstOrNull { it.id == intent.projectId }?.context)
                input = chatCopy(locale, "Review this project and suggest practical next steps.", "Tinjau proyek ini dan sarankan langkah berikutnya.")
                open = true; onConsumeOpinionIntent()
            }
        }
    }
    LaunchedEffect(visible) { if (!visible) open = false }
    LaunchedEffect(open, accountKey) { if (open && accountKey != null) { onRefreshConsent(); onRefreshAiCreditBalance() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val size = with(density) { 48.dp.toPx() }; val inset = with(density) { 16.dp.toPx() }
        val bounds = AiShortcutBounds(inset, inset,
            (with(density) { maxWidth.toPx() } - size - inset).coerceAtLeast(inset),
            (with(density) { maxHeight.toPx() } - size - with(density) { 100.dp.toPx() }).coerceAtLeast(inset))
        val initial = AiShortcutPosition(bounds.maxXPx - with(density) { (if (editorMode) 0.dp else 64.dp).toPx() },
            if (editorMode) bounds.maxYPx - with(density) { 60.dp.toPx() } else with(density) { 52.dp.toPx() })
        val position = clampAiShortcutPosition(shortcut ?: initial, bounds)
        AnimatedVisibility(visible, Modifier.offset { IntOffset(position.xPx.roundToInt(), position.yPx.roundToInt()) }, enter = fadeIn() + scaleIn()) {
            FloatingActionButton({
                if (editorMode && initialProject != null && selectedProject?.projectId != initialProject.projectId) fresh(initialProject)
                open = true
            }, Modifier.size(48.dp).graphicsLayer { scaleX = shortcutScale; scaleY = shortcutScale }.pointerInput(bounds) {
                detectDragGestures(
                    onDragStart = { draggingShortcut = true },
                    onDragEnd = { draggingShortcut = false },
                    onDragCancel = { draggingShortcut = false },
                ) { change, delta ->
                    change.consume()
                    val current = shortcutX?.let { x -> shortcutY?.let { y -> AiShortcutPosition(x, y) } } ?: initial
                    val moved = dragAiShortcutPosition(current, delta.x, delta.y, bounds)
                    shortcutX = moved.xPx; shortcutY = moved.yPx
                }
            }.semantics { contentDescription = chatCopy(locale, "Open AI chat. Drag to move.", "Buka chat AI. Geser untuk memindahkan.") },
                shape = CircleShape, containerColor = EvidriloColors.Tint, contentColor = EvidriloColors.Cobalt) {
                EvidriloIcon(EvidriloIconName.CHAT_BUBBLE, tint = EvidriloColors.Cobalt, modifier = Modifier.size(23.dp))
            }
        }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = EvidriloColors.Surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ historyOpen = true }, enabled = !sending, modifier = Modifier.semantics { contentDescription = chatCopy(locale, "Chat history", "Riwayat chat") }) {
                        EvidriloIcon(EvidriloIconName.HISTORY, tint = EvidriloColors.Slate)
                    }
                    Box(Modifier.weight(1f)) {
                        TextButton({ menu = true }) {
                            Text(chatCopy(locale, "Evidrilo AI"), style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Ink)
                            EvidriloIcon(EvidriloIconName.CHEVRON_DOWN, tint = EvidriloColors.Slate, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(chatCopy(locale, "New chat", "Chat baru")) }, onClick = { menu = false; fresh() }, enabled = !sending)
                            DropdownMenuItem(text = { Text(chatCopy(locale, "Choose a project", "Pilih proyek")) }, onClick = { menu = false; projectPicker = true }, enabled = !sending)
                            DropdownMenuItem(text = { Text(chatCopy(locale, "AI privacy", "Privasi AI")) }, onClick = { menu = false; privacyOpen = true })
                        }
                    }
                    EvidriloAiCreditBadge(aiCreditBalance, onRefreshAiCreditBalance)
                    IconButton({ open = false }, Modifier.semantics { contentDescription = chatCopy(locale, "Close chat", "Tutup chat") }) {
                        EvidriloIcon(EvidriloIconName.PLUS, tint = EvidriloColors.Slate, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 45f })
                    }
                }
                selectedProject?.let { context ->
                    Surface(color = EvidriloColors.Tint, shape = RoundedCornerShape(12.dp), onClick = { excerptOpen = true }) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EvidriloIcon(EvidriloIconName.FOLDER, tint = EvidriloColors.Cobalt, modifier = Modifier.size(18.dp))
                            RawText(context.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                            Text(chatCopy(locale, "Context", "Konteks"), color = EvidriloColors.Cobalt, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (accountKey == null || accessMessage != null) {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        EvidriloIcon(EvidriloIconName.CHAT_BUBBLE, tint = EvidriloColors.Cobalt, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(20.dp))
                        Text(chatCopy(locale, "Sign in to chat with AI", "Masuk untuk menggunakan AI"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        if (canOpenAccount) EvidriloPrimaryButton(chatCopy(locale, "Sign in", "Masuk"), { open = false; onOpenAccount() })
                    }
                } else {
                    if (entries.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            EvidriloLogoMark(size = 56.dp); Spacer(Modifier.height(22.dp))
                            Text(chatCopy(locale, "What are you working on?", "Sedang mengerjakan apa?"), style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(24.dp))
                            val prompts = if (selectedProject == null) listOf(
                                chatCopy(locale, "Help me shape a research question", "Bantu rumuskan pertanyaan penelitian"),
                                chatCopy(locale, "Explain claims and evidence", "Jelaskan klaim dan bukti"), chatCopy(locale, "Plan my next step", "Rencanakan langkah berikutnya"))
                            else listOf(chatCopy(locale, "Review my project", "Tinjau proyek saya"),
                                chatCopy(locale, "Improve my research question", "Perbaiki pertanyaan penelitian saya"), chatCopy(locale, "What evidence is missing?", "Bukti apa yang masih kurang?"))
                            prompts.forEach { prompt -> TextButton({ input = prompt }, Modifier.fillMaxWidth()) { Text(prompt, color = EvidriloColors.Slate) } }
                        }
                    } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                        contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        itemsIndexed(entries, key = { _, entry -> entry.key }) { _, entry -> when (entry) {
                            is GeneralChatEntry.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Surface(shape = RoundedCornerShape(18.dp), color = EvidriloColors.Atmosphere, modifier = Modifier.widthIn(max = 300.dp)) {
                                    SelectionContainer { RawText(entry.text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge) }
                                }
                            }
                            is GeneralChatEntry.Answer -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SelectionContainer { RawText(entry.value.answer, style = MaterialTheme.typography.bodyLarge) }
                                if (entry.value.proposedEdits.isNotEmpty()) OutlinedButton({ proposal = entry }, enabled = !sending) {
                                    Text(chatCopy(locale, "Review ${entry.value.proposedEdits.size} suggested edits", "Tinjau ${entry.value.proposedEdits.size} usulan perubahan"))
                                }
                                if (entry == entries.lastOrNull()) entry.value.recommendedNextPrompts.take(2).forEach { prompt ->
                                    TextButton({ input = prompt }, Modifier.fillMaxWidth()) { RawText(prompt, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
                                }
                            }
                            is GeneralChatEntry.Notice -> Text(entry.text, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                        } }
                        if (sending) item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = EvidriloColors.Cobalt)
                            Text(chatCopy(locale, "Thinking…", "Sedang berpikir…"), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                        } }
                    }
                    localNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate, modifier = Modifier.padding(vertical = 6.dp)) }
                    if (!apiConfigured) Text(chatCopy(locale, "AI is unavailable right now.", "AI belum tersedia saat ini."), style = MaterialTheme.typography.bodySmall)
                    Surface(shape = RoundedCornerShape(24.dp), color = EvidriloColors.Atmosphere, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                            TextField(input, { if (it.length <= 4000) input = it }, Modifier.weight(1f),
                                placeholder = { Text(chatCopy(locale, "Message Evidrilo…", "Kirim pesan…")) }, minLines = 1, maxLines = 5, enabled = !sending && apiConfigured,
                                colors = TextFieldDefaults.colors(focusedContainerColor = EvidriloColors.Atmosphere, unfocusedContainerColor = EvidriloColors.Atmosphere,
                                    disabledContainerColor = EvidriloColors.Atmosphere, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }))
                            FilledIconButton({ submit() }, enabled = input.isNotBlank() && !sending && apiConfigured,
                                modifier = Modifier.padding(bottom = 4.dp).semantics { contentDescription = chatCopy(locale, "Send message", "Kirim pesan") }) {
                                EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = -90f })
                            }
                        }
                    }
                }
            }
        }
    }
    if (projectPicker) AlertDialog(onDismissRequest = { projectPicker = false }, title = { Text(chatCopy(locale, "Choose a project", "Pilih proyek")) },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            if (projectsLoading) CircularProgressIndicator(Modifier.size(24.dp))
            else if (projects.isEmpty()) Text(chatCopy(locale, "Create a project to get help with it.", "Buat proyek untuk mendapatkan bantuan."))
            projects.forEach { project -> TextButton({ fresh(project.context); projectPicker = false }, Modifier.fillMaxWidth()) {
                RawText(project.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } }
        } }, confirmButton = { TextButton({ projectPicker = false }) { Text(chatCopy(locale, "Done", "Selesai")) } })
    if (historyOpen) AlertDialog(onDismissRequest = { historyOpen = false }, title = { Text(chatCopy(locale, "Chat history", "Riwayat chat")) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            val own = sessions.filter { it.contextKey.startsWith(historyPrefix) }
            if (own.isEmpty()) Text(chatCopy(locale, "Your conversations will appear here.", "Percakapan Anda akan muncul di sini."))
            own.forEach { session -> TextButton({
                if (!sending) {
                    val id = session.contextKey.removePrefix(historyPrefix)
                    selectedProject = projects.firstOrNull { it.id == id }?.context
                    entries.clear(); entries.addAll(session.messages.mapIndexed { index, message ->
                        if (message.fromLearner) GeneralChatEntry.User("restored-$index", message.text)
                        else GeneralChatEntry.Answer("restored-$index", ProjectAiGeneralChatAnswer(message.text, emptyList(), "history", 0), selectedProject)
                    }); sessionId = session.id; input = ""; historyOpen = false
                }
            }, Modifier.fillMaxWidth()) { RawText(session.title, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
        } }, confirmButton = { TextButton({ historyOpen = false; fresh() }, enabled = !sending) { Text(chatCopy(locale, "New chat", "Chat baru")) } })
    if (approveSend || privacyOpen) AlertDialog(onDismissRequest = { approveSend = false; privacyOpen = false }, title = { Text(chatCopy(locale, "Chat with Evidrilo AI", "Chat dengan Evidrilo AI")) },
        text = { Text(chatCopy(locale, "Sending shares your message, recent conversation and selected project notes with Experiential Labs. AI uses credits and can make mistakes. You can revoke permission here anytime.",
            "Mengirim pesan membagikan pesan, percakapan terakhir, dan catatan proyek pilihan ke Experiential Labs. AI memakai kredit dan dapat keliru. Izin dapat dicabut di sini kapan saja.")) },
        confirmButton = { TextButton({ if (approveSend) { approveSend = false; awaitingConsent = true; onGrantConsent() } else privacyOpen = false }) {
            Text(if (approveSend) chatCopy(locale, "Allow & send", "Izinkan & kirim") else chatCopy(locale, "Done", "Selesai")) } },
        dismissButton = { if (privacyOpen && consentState == ProjectAiConsentUiState.Granted) TextButton({ onRevokeConsent(); privacyOpen = false }) { Text(chatCopy(locale, "Revoke permission", "Cabut izin")) }
            else TextButton({ approveSend = false; privacyOpen = false }) { Text(chatCopy(locale, "Cancel", "Batal")) } })
    if (excerptOpen) selectedProject?.let { context -> AlertDialog(onDismissRequest = { excerptOpen = false }, title = { RawText(context.title) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(chatCopy(locale, "Saved revision ${context.revision} · selected notes only", "Revisi ${context.revision} · catatan pilihan saja"), style = MaterialTheme.typography.labelMedium)
            context.fields.forEach { field -> Text(field.label, style = MaterialTheme.typography.titleSmall); RawText(field.value.ifBlank { "—" }) }; RawText(context.notes)
        } }, confirmButton = { TextButton({ excerptOpen = false }) { Text(chatCopy(locale, "Done", "Selesai")) } }) }
    proposal?.let { answer ->
        val context = answer.context
        val current = context != null && isOpinionCurrent(context.projectId, context.revision)
        AlertDialog(onDismissRequest = { proposal = null }, title = { Text(chatCopy(locale, "Review suggested edits", "Tinjau usulan perubahan")) },
            text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!current) Text(chatCopy(locale, "Project changed. These edits cannot be applied.", "Proyek berubah. Usulan ini tidak dapat diterapkan."))
                answer.value.proposedEdits.forEach { edit ->
                    Text(context?.fields?.firstOrNull { it.id == edit.fieldId }?.label ?: edit.fieldId, style = MaterialTheme.typography.titleSmall)
                    RawText(context?.fields?.firstOrNull { it.id == edit.fieldId }?.value.orEmpty(), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    RawText(edit.value, style = MaterialTheme.typography.bodyMedium); RawText(edit.reason, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
            } }, confirmButton = { TextButton({
                val updated = if (context != null && accountKey != null) onApplyEdits(accountKey, context, answer.value.proposedEdits) else null
                if (updated != null) {
                    selectedProject = updated
                    val index = entries.indexOf(answer)
                    if (index >= 0) entries[index] = answer.copy(value = answer.value.copy(proposedEdits = emptyList()))
                    localNotice = chatCopy(locale, "Edits saved.", "Perubahan tersimpan.")
                } else localNotice = chatCopy(locale, "Edits weren't saved. Reopen the project and try again.", "Perubahan belum tersimpan. Buka ulang proyek lalu coba lagi.")
                proposal = null
            }, enabled = current && !sending) { Text(chatCopy(locale, "Apply edits", "Terapkan perubahan")) } },
            dismissButton = { TextButton({ proposal = null }) { Text(chatCopy(locale, "Keep original", "Pertahankan versi awal")) } })
    }
}

private sealed interface GeneralChatEntry {
    val key: String
    data class User(override val key: String, val text: String) : GeneralChatEntry
    data class Answer(override val key: String, val value: ProjectAiGeneralChatAnswer, val context: ProjectAiChatContext?) : GeneralChatEntry
    data class Notice(override val key: String, val text: String) : GeneralChatEntry
}

private fun generalChatFailureMessage(result: ProjectAiGeneralChatResult, locale: String): String = when (result) {
    is ProjectAiGeneralChatResult.Deferred -> chatCopy(locale, "Sign in again to continue.", "Masuk kembali untuk melanjutkan.")
    is ProjectAiGeneralChatResult.Rejected -> when {
        result.code == "AI_CREDITS_INSUFFICIENT" -> chatCopy(locale, "Not enough AI credits.", "Kredit AI tidak cukup.")
        result.code.contains("CONSENT") -> chatCopy(locale, "Please review AI permission in the menu.", "Tinjau izin AI melalui menu.")
        result.creditCost != null -> generalChatProviderUsageNotice(requireNotNull(result.creditCost), false)
        else -> chatCopy(locale, "AI couldn't return a valid answer. Try again.", "AI belum dapat memberikan jawaban yang valid. Coba lagi.")
    }
    is ProjectAiGeneralChatResult.Failed -> if (result.outcomeUnknown)
        chatCopy(locale, "Connection lost. This request may have used credits.", "Koneksi terputus. Permintaan ini mungkin memakai kredit.")
        else chatCopy(locale, "Check your connection and try again.", "Periksa koneksi lalu coba lagi.")
    else -> chatCopy(locale, "AI is temporarily unavailable. Try again shortly.", "AI sementara tidak tersedia. Coba lagi sebentar.")
}

internal fun canSendGeneralAiMessage(input: String, consentConfirmed: Boolean, consentState: ProjectAiConsentUiState,
    accessMessage: String?, apiConfigured: Boolean, sending: Boolean): Boolean =
    input.isNotBlank() && input.length <= 4000 && consentConfirmed && consentState == ProjectAiConsentUiState.Granted &&
        accessMessage == null && apiConfigured && !sending

internal fun generalChatProviderUsageNotice(creditCost: Int, consentMustBeReviewed: Boolean): String =
    if (consentMustBeReviewed) "Reply withheld · $creditCost credits used. Review AI permission before retrying."
    else "Reply withheld · $creditCost credits used."

private fun chatCopy(locale: String, english: String, indonesian: String = english): String =
    if (locale.startsWith("id")) indonesian else english
