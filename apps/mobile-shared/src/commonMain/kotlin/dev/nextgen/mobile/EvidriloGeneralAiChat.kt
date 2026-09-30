package dev.nextgen.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatAnswer
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatDeferredReason
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatResult
import kotlin.math.roundToInt

internal data class ProjectAiEntryProject(val id: String, val title: String)

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
    visible: Boolean,
    accountKey: String?,
    projects: List<ProjectAiEntryProject>,
    projectsLoading: Boolean,
    accessMessage: String?,
    apiConfigured: Boolean,
    aiCreditBalance: AiCreditBalancePresentation = AiCreditBalancePresentation.SignInRequired,
    consentState: ProjectAiConsentUiState,
    canOpenAccount: Boolean,
    onOpenAccount: () -> Unit,
    onRefreshConsent: () -> Unit,
    onRefreshAiCreditBalance: () -> Unit = {},
    onGrantConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenProject: (String) -> Unit,
    activityHistoryState: ProjectAiActivityHistoryUiState,
    onRefreshActivityHistory: (String?) -> Unit,
    onLoadMoreActivityHistory: (String?, String) -> Unit,
    onSendMessage: (message: String, locale: String, onResult: (ProjectAiGeneralChatResult) -> Unit) -> Unit,
) {
    var entrySurface by remember { mutableStateOf(ProjectAiEntrySurface.CLOSED) }
    var input by remember(accountKey) { mutableStateOf("") }
    var consentConfirmed by remember(accountKey) { mutableStateOf(false) }
    var sending by remember(accountKey) { mutableStateOf(false) }
    var aiShortcutPosition by remember { mutableStateOf<AiShortcutPosition?>(null) }
    val entries = remember(accountKey) { mutableStateListOf<GeneralChatEntry>() }
    val listState = rememberLazyListState()
    val locale = Locale.current.toLanguageTag().take(32).ifBlank { "en" }

    fun send(rawMessage: String) {
        val message = rawMessage.trim()
        if (!canSendGeneralAiMessage(message, consentConfirmed, consentState, accessMessage, apiConfigured, sending)) return
        input = ""
        consentConfirmed = false
        sending = true
        entries += GeneralChatEntry.User("user-${entries.size}", message)
        onSendMessage(message, locale) { result ->
            sending = false
            when (result) {
                is ProjectAiGeneralChatResult.Answer ->
                    entries += GeneralChatEntry.Answer("answer-${entries.size}", result.value)
                is ProjectAiGeneralChatResult.Deferred -> entries += GeneralChatEntry.Notice(
                    "notice-${entries.size}",
                    when (result.reason) {
                        ProjectAiGeneralChatDeferredReason.NOT_CONFIGURED -> "AI service is not configured in this build."
                        ProjectAiGeneralChatDeferredReason.AUTH_REQUIRED -> "A verified account is required. Sign in again before sending a message."
                        ProjectAiGeneralChatDeferredReason.SESSION_EXPIRED -> "Your account session expired. Sign in again before sending a message."
                        ProjectAiGeneralChatDeferredReason.SECURE_STORAGE -> "Secure session storage is unavailable; the message was not sent."
                    },
                )
                is ProjectAiGeneralChatResult.Unavailable -> entries += GeneralChatEntry.Notice(
                    "notice-${entries.size}",
                    if (result.code == "PROJECT_AI_GENERAL_CHAT_NOT_READY") {
                        "General Chat is not enabled by the platform right now. Your message was not answered; your project remains available."
                    } else {
                        "The AI service is temporarily unavailable. No answer was shown. Try again later."
                    },
                )
                is ProjectAiGeneralChatResult.Rejected -> entries += GeneralChatEntry.Notice(
                    "notice-${entries.size}",
                    when {
                        result.code in setOf(
                            "PROJECT_AI_CONSENT_REQUIRED",
                            "PROJECT_AI_CONSENT_POLICY_STALE",
                            "PROJECT_AI_CONSENT_REVOKED_AFTER_PROVIDER",
                            "PROJECT_AI_CONSENT_CHECK_UNAVAILABLE_AFTER_PROVIDER",
                        ) -> {
                            onRefreshConsent()
                            if (result.creditCost != null) {
                                generalChatProviderUsageNotice(requireNotNull(result.creditCost), consentMustBeReviewed = true)
                            } else {
                                "Account-level AI data-use consent is missing or out of date. Refresh and review consent before sending again."
                            }
                        }
                        result.creditCost != null -> generalChatProviderUsageNotice(requireNotNull(result.creditCost), consentMustBeReviewed = false)
                        result.code == "AI_CREDITS_INSUFFICIENT" -> "There are not enough AI credits for this request. No answer was returned."
                        result.code == "GENERAL_CHAT_CONSENT_REQUIRED" -> "Confirm the General chat notice for this message, then try again."
                        else -> "The response could not be verified, so it was not displayed. Check your credit balance before sending again."
                    },
                )
                is ProjectAiGeneralChatResult.Failed -> entries += GeneralChatEntry.Notice(
                    "notice-${entries.size}",
                    if (result.outcomeUnknown) {
                        "We could not confirm whether the request completed. It may have used credits; check your AI credit balance before sending again."
                    } else if (result.retryable) {
                        "The device appears offline. Reconnect before sending this message."
                    } else {
                        "The request failed. No response was shown."
                    },
                )
            }
        }
    }

    LaunchedEffect(visible) {
        if (!visible) entrySurface = ProjectAiEntrySurface.CLOSED
    }
    LaunchedEffect(entrySurface, accountKey, accessMessage, apiConfigured) {
        if (shouldRefreshAiCreditBalance(entrySurface)) onRefreshAiCreditBalance()
        if (entrySurface == ProjectAiEntrySurface.GENERAL_CHAT && accessMessage == null && apiConfigured) {
            onRefreshConsent()
        }
    }
    if (entries.isNotEmpty() || sending) {
        LaunchedEffect(entries.size, sending) {
            val lastIndex = (if (sending) entries.size else entries.size - 1).coerceAtLeast(0)
            listState.animateScrollToItem(lastIndex)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val shortcutSizePx = with(density) { 54.dp.toPx() }
        val edgeInsetPx = with(density) { 16.dp.toPx() }
        val endInsetPx = with(density) { 20.dp.toPx() }
        val bottomInsetPx = with(density) { 100.dp.toPx() }
        val bounds = AiShortcutBounds(
            minXPx = edgeInsetPx,
            minYPx = edgeInsetPx,
            maxXPx = (viewportWidthPx - shortcutSizePx - edgeInsetPx).coerceAtLeast(edgeInsetPx),
            maxYPx = (viewportHeightPx - shortcutSizePx - bottomInsetPx).coerceAtLeast(edgeInsetPx),
        )
        val initialPosition = initialAiShortcutPosition(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            shortcutSizePx = shortcutSizePx,
            endInsetPx = endInsetPx,
            bottomInsetPx = bottomInsetPx,
            bounds = bounds,
        )
        val displayedPosition = clampAiShortcutPosition(aiShortcutPosition ?: initialPosition, bounds)

        LaunchedEffect(bounds) {
            aiShortcutPosition = aiShortcutPosition?.let { clampAiShortcutPosition(it, bounds) }
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.offset {
                IntOffset(displayedPosition.xPx.roundToInt(), displayedPosition.yPx.roundToInt())
            },
            enter = fadeIn() + scaleIn(),
        ) {
            FloatingActionButton(
                onClick = { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.OPEN) },
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(bounds, initialPosition) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val position = aiShortcutPosition ?: initialPosition
                            aiShortcutPosition = dragAiShortcutPosition(
                                current = position,
                                deltaXPx = dragAmount.x,
                                deltaYPx = dragAmount.y,
                                bounds = bounds,
                            )
                        }
                    }
                    .semantics {
                        contentDescription = "AI chat shortcut. Tap to open AI modes. Drag to move."
                        customActions = listOf(
                            CustomAccessibilityAction("Move AI shortcut to upper left") {
                                aiShortcutPosition = AiShortcutPosition(bounds.minXPx, bounds.minYPx)
                                true
                            },
                            CustomAccessibilityAction("Move AI shortcut to upper right") {
                                aiShortcutPosition = AiShortcutPosition(bounds.maxXPx, bounds.minYPx)
                                true
                            },
                            CustomAccessibilityAction("Move AI shortcut to lower left") {
                                aiShortcutPosition = AiShortcutPosition(bounds.minXPx, bounds.maxYPx)
                                true
                            },
                            CustomAccessibilityAction("Move AI shortcut to lower right") {
                                aiShortcutPosition = AiShortcutPosition(bounds.maxXPx, bounds.maxYPx)
                                true
                            },
                        )
                    },
                shape = androidx.compose.foundation.shape.CircleShape,
                containerColor = EvidriloColors.PrimaryAction,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                EvidriloIcon(EvidriloIconName.CHAT_BUBBLE, tint = EvidriloColors.White, modifier = Modifier.size(24.dp))
            }
        }
    }

    if (entrySurface != ProjectAiEntrySurface.CLOSED) {
        Dialog(
            onDismissRequest = { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.DISMISS) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            dev.nextgen.mobile.navigation.EvidriloBackGestureHost {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AiWorkspaceHeader(
                        surface = entrySurface,
                        onBack = { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.BACK) },
                        onClose = { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.DISMISS) },
                    )

                    if (entrySurface == ProjectAiEntrySurface.MODE_PICKER) {
                        Text(
                            "Choose how you want AI to help",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "General Chat is unlinked. Project AI works only with an eligible project and selected context.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EvidriloAiCreditBalancePanel(
                            presentation = aiCreditBalance,
                            onRefresh = onRefreshAiCreditBalance,
                        )
                        EvidriloWorkspaceRow(EvidriloIconName.CHAT_BUBBLE, "General Chat", "Ask a study question. No project is connected.",
                            { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.CHOOSE_GENERAL) })
                        EvidriloWorkspaceRow(EvidriloIconName.FOLDER, "Project AI", "Choose a supported project stage and its material.",
                            { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.CHOOSE_PROJECT) })
                    } else {
                        if (entrySurface == ProjectAiEntrySurface.GENERAL_CHAT || entrySurface == ProjectAiEntrySurface.PROJECT_PICKER) {
                            AiWorkspaceModeSelector(
                                selected = if (entrySurface == ProjectAiEntrySurface.GENERAL_CHAT) AiWorkspaceMode.GENERAL else AiWorkspaceMode.PROJECT,
                                onChooseGeneral = { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.CHOOSE_GENERAL) },
                                onChooseProject = { entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.CHOOSE_PROJECT) },
                            )
                        }

                        when (entrySurface) {
                            ProjectAiEntrySurface.PROJECT_PICKER -> {
                                EvidriloAiCreditBalancePanel(
                                    presentation = aiCreditBalance,
                                    onRefresh = onRefreshAiCreditBalance,
                                )
                                Text(
                                    "Choose an eligible project",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Only a published template with a declared AI operation appears here. The server rechecks the template and project revision; other work remains manual.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                when {
                                    projectsLoading -> Row(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    }
                                    projects.isEmpty() -> Column(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(projectAiEntryEmptyStateMessage(), style = MaterialTheme.typography.bodyMedium)
                                        TextButton(
                                            onClick = {
                                                entrySurface = ProjectAiEntrySurface.CLOSED
                                                onOpenProjects()
                                            },
                                        ) { Text("Create or open a project") }
                                    }
                                    else -> LazyColumn(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        itemsIndexed(projects, key = { _, project -> project.id }) { _, project ->
                                            TextButton(
                                                onClick = {
                                                    entrySurface = ProjectAiEntrySurface.CLOSED
                                                    onOpenProject(project.id)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(project.title.ifBlank { "Untitled project" }, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                }
                            }

                            ProjectAiEntrySurface.ACTIVITY -> {
                                EvidriloProjectAiActivityHistory(
                                    state = activityHistoryState,
                                    accountId = accountKey,
                                    projectId = null,
                                    onRefresh = { onRefreshActivityHistory(null) },
                                    onLoadMore = { cursor -> onLoadMoreActivityHistory(null, cursor) },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )
                            }

                            ProjectAiEntrySurface.GENERAL_CHAT -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            "Not linked to a project",
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            entrySurface = reduceProjectAiEntrySurface(entrySurface, ProjectAiEntryEvent.CHOOSE_ACTIVITY)
                                            onRefreshActivityHistory(null)
                                        },
                                    ) { Text("Activity") }
                                    if (entries.isNotEmpty()) {
                                        TextButton(
                                            onClick = {
                                                entries.clear()
                                                input = ""
                                                consentConfirmed = false
                                            },
                                        ) { Text("Clear") }
                                    }
                                }
                                EvidriloExplanation("What this chat sends", "Each request sends only its current message. Earlier turns are not sent or saved to account history.")
                                EvidriloAiCreditBalancePanel(
                                    presentation = aiCreditBalance,
                                    onRefresh = onRefreshAiCreditBalance,
                                )

                                if (accessMessage == null && apiConfigured) {
                                    when (val savedConsent = consentState) {
                                        ProjectAiConsentUiState.Unknown -> TextButton(onClick = onRefreshConsent) {
                                            Text("Check saved AI data-use consent")
                                        }
                                        ProjectAiConsentUiState.Checking -> Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Text("Checking account consent…", style = MaterialTheme.typography.bodySmall)
                                        }
                                        ProjectAiConsentUiState.NotGranted -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                "The platform requires account-level AI data-use consent. Granting it sends nothing; each message still needs separate approval and never includes a project.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            TextButton(onClick = onGrantConsent) { Text("Allow AI data processing") }
                                        }
                                        ProjectAiConsentUiState.Granted -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                "Account-level AI consent is active. You approve each message separately.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            TextButton(onClick = onRevokeConsent, enabled = !sending) {
                                                Text("Revoke saved AI consent")
                                            }
                                        }
                                        is ProjectAiConsentUiState.Unavailable -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(savedConsent.message, style = MaterialTheme.typography.bodySmall)
                                            TextButton(onClick = onRefreshConsent) { Text("Check consent again") }
                                        }
                                    }
                                } else if (accessMessage == null) {
                                    ChatNotice("The Evidrilo AI API is not configured in this build. No message can be sent; your project remains usable manually.")
                                }

                                if (accessMessage != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(accessMessage, style = MaterialTheme.typography.bodyMedium)
                                            if (canOpenAccount) TextButton(onClick = onOpenAccount) { Text("Open account") }
                                        }
                                    }
                                }

                                if (entries.isEmpty() && accessMessage == null) {
                                    Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                                        Text("Start with", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        listOf(
                                            "Help me turn an assignment into a focused research question.",
                                            "What should I check before saying evidence supports a claim?",
                                            "Help me choose a manageable next step for my project.",
                                        ).forEach { prompt ->
                                            TextButton(
                                                onClick = {
                                                    input = prompt
                                                    consentConfirmed = false
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { Text(prompt, modifier = Modifier.fillMaxWidth()) }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        state = listState,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        itemsIndexed(entries, key = { index, entry -> "$index-${entry.key}" }) { _, entry ->
                                            when (entry) {
                                                is GeneralChatEntry.User -> ChatMessageBubble(text = entry.text, fromUser = true)
                                                is GeneralChatEntry.Answer -> GeneralChatAnswerBubble(entry.value) { prompt ->
                                                    input = prompt
                                                    consentConfirmed = false
                                                }
                                                is GeneralChatEntry.Notice -> ChatNotice(entry.text)
                                            }
                                        }
                                        if (sending) {
                                            item(key = "general-ai-working") {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                    Text("Checking AI availability…", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }

                                if (accessMessage == null) {
                                    OutlinedTextField(
                                        value = input,
                                        onValueChange = { value -> if (value.length <= 4_000) input = value },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("Ask a general study question") },
                                        supportingText = { Text("Up to 4,000 characters · no project context") },
                                        minLines = 1,
                                        maxLines = 3,
                                        enabled = !sending && apiConfigured,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = {
                                            if (canSendGeneralAiMessage(input, consentConfirmed, consentState, accessMessage, apiConfigured, sending)) send(input)
                                        }),
                                    )
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Checkbox(
                                            checked = consentConfirmed,
                                            onCheckedChange = { checked -> consentConfirmed = checked },
                                            enabled = !sending && apiConfigured && consentState is ProjectAiConsentUiState.Granted,
                                        )
                                        Text(
                                            "Allow this message to be sent to AI. Actual verified usage (up to 200 credits) is charged; a reply is not guaranteed.",
                                            modifier = Modifier.padding(top = 11.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Button(
                                        onClick = { send(input) },
                                        enabled = canSendGeneralAiMessage(input, consentConfirmed, consentState, accessMessage, apiConfigured, sending),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    ) {
                                        if (sending) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                            )
                                        } else {
                                            Text("Send message")
                                        }
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }
            }
        }
    }

}

private sealed interface GeneralChatEntry {
    val key: String

    data class User(override val key: String, val text: String) : GeneralChatEntry
    data class Answer(override val key: String, val value: ProjectAiGeneralChatAnswer) : GeneralChatEntry
    data class Notice(override val key: String, val text: String) : GeneralChatEntry
}

private enum class AiWorkspaceMode { GENERAL, PROJECT }

@Composable
private fun AiWorkspaceHeader(
    surface: ProjectAiEntrySurface,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (surface != ProjectAiEntrySurface.MODE_PICKER) {
            EvidriloBackGesture(label = "AI workspace", onClick = onBack)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when (surface) {
                    ProjectAiEntrySurface.MODE_PICKER -> "AI workspace"
                    ProjectAiEntrySurface.PROJECT_PICKER -> "Project AI"
                    ProjectAiEntrySurface.GENERAL_CHAT -> "General Chat"
                    ProjectAiEntrySurface.ACTIVITY -> "AI activity"
                    ProjectAiEntrySurface.CLOSED -> "AI workspace"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (surface == ProjectAiEntrySurface.MODE_PICKER) {
                Text(
                    "Choose a separate chat or project workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

@Composable
private fun AiWorkspaceModeSelector(
    selected: AiWorkspaceMode?,
    onChooseGeneral: () -> Unit,
    onChooseProject: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == AiWorkspaceMode.GENERAL,
            onClick = onChooseGeneral,
            label = { Text("General Chat") },
        )
        FilterChip(
            selected = selected == AiWorkspaceMode.PROJECT,
            onClick = onChooseProject,
            label = { Text("Project AI") },
        )
    }
}

@Composable
private fun ChatMessageBubble(text: String, fromUser: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = if (fromUser) EvidriloColors.Cobalt else EvidriloColors.Tint,
            contentColor = if (fromUser) EvidriloColors.White else EvidriloColors.Ink,
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GeneralChatAnswerBubble(
    answer: ProjectAiGeneralChatAnswer,
    onUsePrompt: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChatMessageBubble(answer.answer, fromUser = false)
        Text(
            "${answer.creditCost} AI credit${if (answer.creditCost == 1) "" else "s"} · suggestions are optional",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        answer.recommendedNextPrompts.forEach { prompt ->
            TextButton(
                onClick = { onUsePrompt(prompt) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(prompt, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ChatNotice(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

internal fun canSendGeneralAiMessage(
    input: String,
    consentConfirmed: Boolean,
    consentState: ProjectAiConsentUiState,
    accessMessage: String?,
    apiConfigured: Boolean,
    sending: Boolean,
): Boolean = input.isNotBlank() && input.length <= 4_000 && consentConfirmed &&
    consentState is ProjectAiConsentUiState.Granted && accessMessage == null && apiConfigured && !sending

internal fun generalChatProviderUsageNotice(creditCost: Int, consentMustBeReviewed: Boolean): String {
    val credits = if (creditCost == 1) "1 shared AI credit" else "$creditCost shared AI credits"
    return if (consentMustBeReviewed) {
        "The reply was withheld. Verified provider usage charged $credits. AI consent is being rechecked; review consent before sending again."
    } else {
        "The reply was withheld. Verified provider usage charged $credits. Check your current balance before retrying."
    }
}
