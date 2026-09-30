package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.ai.AiAssistPurpose
import dev.nextgen.mobile.ai.AiConversationGatewayResult
import dev.nextgen.mobile.ai.AiConversationHistoryMessage
import dev.nextgen.mobile.ai.AiConversationProposal
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED
import dev.nextgen.mobile.ai.AiCredits
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionFeedbackItem

/**
 * UI state for optional, bounded AI assistance. The deterministic evaluator
 * remains the source of truth; this surface can only explain its result.
 */
internal sealed interface EvidriloAiAssistUiState {
    data object SignInRequired : EvidriloAiAssistUiState

    data object Loading : EvidriloAiAssistUiState

    data class Ready(
        val credits: AiCredits,
        val conversationTurnsUsed: Int? = null,
    ) : EvidriloAiAssistUiState

    data class Answer(
        val text: String,
        val remainingCredits: Int?,
        val groundedAnchorIds: List<String> = emptyList(),
        val requestId: String? = null,
        val turnsUsed: Int? = null,
        val proposal: AiConversationProposal? = null,
        val creditCost: Int? = null,
    ) : EvidriloAiAssistUiState

    data class Unavailable(
        val message: String,
        val retryable: Boolean,
        val turnsUsed: Int? = null,
        val requestId: String? = null,
        val creditCost: Int? = null,
        val remainingCredits: Int? = null,
    ) : EvidriloAiAssistUiState
}

internal sealed interface EvidriloAiConversationClearState {
    data object Idle : EvidriloAiConversationClearState

    data class Clearing(
        val sessionId: String?,
        val accountId: String?,
    ) : EvidriloAiConversationClearState

    data class Failed(
        val sessionId: String,
        val accountId: String,
        val retryable: Boolean,
    ) : EvidriloAiConversationClearState
}

internal fun aiConversationClearStateAfterGatewayResult(
    sessionId: String,
    accountId: String,
    result: AiConversationGatewayResult,
): EvidriloAiConversationClearState = when (result) {
    is AiConversationGatewayResult.Cleared ->
        if (result.sessionId == sessionId) {
            EvidriloAiConversationClearState.Idle
        } else {
            EvidriloAiConversationClearState.Failed(sessionId, accountId, retryable = false)
        }
    is AiConversationGatewayResult.Failed -> EvidriloAiConversationClearState.Failed(
        sessionId = sessionId,
        accountId = accountId,
        retryable = result.error.retryable || result.error.outcomeUnknown,
    )
    is AiConversationGatewayResult.Deferred,
    is AiConversationGatewayResult.Fallback,
    is AiConversationGatewayResult.SessionStarted,
    is AiConversationGatewayResult.TurnReceived ->
        EvidriloAiConversationClearState.Failed(sessionId, accountId, retryable = false)
}

/** Keeps stale replies out of the current transcript while preserving server-reported billing facts. */
internal fun staleAiConversationRecoveryState(
    result: AiConversationGatewayResult,
    remainingCredits: Int?,
): EvidriloAiAssistUiState.Unavailable? = when (result) {
    is AiConversationGatewayResult.TurnReceived -> EvidriloAiAssistUiState.Unavailable(
        message = "An earlier AI reply belongs to a previous workspace state. It was not added to this chat, and no draft change was applied.",
        retryable = false,
        turnsUsed = result.value.turnsUsed,
        requestId = result.value.requestId,
        creditCost = result.value.creditCost,
        remainingCredits = remainingCredits,
    )
    is AiConversationGatewayResult.Fallback -> EvidriloAiAssistUiState.Unavailable(
        message = "An earlier AI turn became unavailable after the workspace changed. No draft change was applied.",
        retryable = false,
        turnsUsed = result.turnsUsed,
        requestId = result.requestId,
        creditCost = result.creditCost,
        remainingCredits = remainingCredits,
    )
    is AiConversationGatewayResult.Failed -> if (result.error.outcomeUnknown) {
        EvidriloAiAssistUiState.Unavailable(
            message = "An earlier AI turn may have been processed before the workspace changed. Its reply was not added; check the refreshed credit balance before retrying.",
            retryable = false,
            remainingCredits = remainingCredits,
        )
    } else {
        null
    }
    is AiConversationGatewayResult.SessionStarted,
    is AiConversationGatewayResult.Cleared,
    is AiConversationGatewayResult.Deferred,
    -> null
}

internal fun aiChatCanSendDuringConversationClear(
    clearState: EvidriloAiConversationClearState,
): Boolean = clearState !is EvidriloAiConversationClearState.Clearing

internal fun aiChatCanRequest(credits: AiCredits): Boolean =
    credits.available > 0 || !credits.consentRecorded

internal fun aiChatCanClear(hasTranscript: Boolean, requestInFlight: Boolean): Boolean =
    hasTranscript && !requestInFlight

/** Applies only a fresh, allowlisted learner-field proposal; evaluator-owned fields are never writable. */
internal fun applyGroundedAiProposal(
    draft: ConclusionDraft,
    proposal: AiConversationProposal,
): ConclusionDraft? {
    val currentValue = when (proposal.field) {
        "claim_text" -> draft.claimText.takeIf { it.isNotBlank() }
        "claim_scope" -> draft.scope?.name
        "learner_limitation" -> draft.limitationNote.takeIf { it.isNotBlank() }
        else -> return null
    }
    if (proposal.beforeValue != currentValue || proposal.suggestedValue.isBlank()) return null

    return when (proposal.field) {
        "claim_text" -> proposal.suggestedValue.takeIf { it.length <= 320 }
            ?.let { draft.copy(claimText = it) }
        "claim_scope" -> ConclusionScope.entries
            .firstOrNull { it.name == proposal.suggestedValue && it != ConclusionScope.UNSUPPORTED }
            ?.let { draft.copy(scope = it) }
        "learner_limitation" -> proposal.suggestedValue.takeIf { it.length <= 300 }
            ?.let { draft.copy(limitationNote = it) }
        else -> null
    }?.takeIf { it != draft }
}

internal fun aiProposalSupportsInlineEdit(proposal: AiConversationProposal): Boolean =
    proposal.field in setOf("claim_text", "claim_scope", "learner_limitation")

/** Edits only the proposed value; its field, before-value, and grounded anchors remain immutable. */
internal fun editGroundedAiProposal(
    proposal: AiConversationProposal,
    editedValue: String,
): AiConversationProposal? {
    if (!aiProposalSupportsInlineEdit(proposal)) return null
    val value = editedValue.trim()
    if (value.isBlank()) return null

    val validValue = when (proposal.field) {
        "claim_text" -> value.takeIf { it.length <= 320 }
        "learner_limitation" -> value.takeIf { it.length <= 300 }
        "claim_scope" -> value.takeIf { candidate ->
            ConclusionScope.entries.any { it != ConclusionScope.UNSUPPORTED && it.name == candidate }
        }
        else -> null
    } ?: return null

    return proposal.copy(suggestedValue = validValue)
}

/**
 * A single visible turn in the bounded, session-local AI transcript. This is a
 * presentation-only conversation: every assistant turn still comes from the
 * grounded gateway, and no raw transcript is persisted server-side.
 */
internal sealed interface EvidriloAiChatMessage {
    data class User(val text: String) : EvidriloAiChatMessage

    data class Assistant(
        val text: String,
        val purpose: AiAssistPurpose,
        val groundedAnchorIds: List<String>,
        val remainingCredits: Int?,
        val requestId: String,
        val proposal: AiConversationProposal?,
        val creditCost: Int? = null,
    ) : EvidriloAiChatMessage

    data class AssistantError(
        val text: String,
        val creditCost: Int? = null,
        val remainingCredits: Int? = null,
    ) : EvidriloAiChatMessage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EvidriloAiAssistCard(
    state: EvidriloAiAssistUiState,
    clearState: EvidriloAiConversationClearState,
    contextKey: String,
    contextPreview: String,
    feedback: ConclusionFeedbackItem,
    opened: Boolean,
    onDismiss: () -> Unit,
    onRequest: (AiAssistPurpose, String, List<AiConversationHistoryMessage>) -> Unit,
    onClearConversation: () -> Unit,
    onRetryClearConversation: (String) -> Unit,
    canApplyProposal: (AiConversationProposal) -> Boolean,
    onApplyProposal: (AiConversationProposal) -> Boolean,
    onRetry: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val transcript = remember(contextKey) { mutableStateListOf<EvidriloAiChatMessage>() }
    var requestsUsed by remember(contextKey) { mutableIntStateOf(0) }
    var requestInFlight by remember(contextKey) { mutableStateOf(false) }
    var lastAnswerKey by remember(contextKey) { mutableStateOf<String?>(null) }
    var lastErrorKey by remember(contextKey) { mutableStateOf<String?>(null) }
    var lastRequestedPurpose by remember(contextKey) { mutableStateOf<AiAssistPurpose?>(null) }
    var dismissedProposalKeys by remember(contextKey) { mutableStateOf(emptySet<String>()) }

    // Append assistant turns as grounded answers or bounded errors arrive.
    LaunchedEffect(state, contextKey) {
        when (val current = state) {
            is EvidriloAiAssistUiState.Answer -> {
                val key = current.requestId ?: (current.text + "·" + (current.remainingCredits ?: -1))
                if (key != lastAnswerKey) {
                    transcript.add(
                        EvidriloAiChatMessage.Assistant(
                            text = current.text,
                            purpose = lastRequestedPurpose ?: AiAssistPurpose.EXPLAIN_FEEDBACK,
                            groundedAnchorIds = current.groundedAnchorIds,
                            remainingCredits = current.remainingCredits,
                            requestId = key,
                            proposal = current.proposal,
                            creditCost = current.creditCost,
                        ),
                    )
                    lastAnswerKey = key
                }
                current.turnsUsed?.let { requestsUsed = it }
            }

            is EvidriloAiAssistUiState.Unavailable -> {
                val key = current.requestId ?: current.message
                if (key != lastErrorKey) {
                    transcript.add(
                        EvidriloAiChatMessage.AssistantError(
                            current.message,
                            current.creditCost,
                            current.remainingCredits,
                        ),
                    )
                    lastErrorKey = key
                }
                current.turnsUsed?.let { requestsUsed = it }
            }

            is EvidriloAiAssistUiState.Ready -> current.conversationTurnsUsed?.let { requestsUsed = it }

            else -> Unit
        }
        if (state !is EvidriloAiAssistUiState.Loading) requestInFlight = false
    }

    if (opened) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EvidriloAiChatSheet(
                state = state,
                clearState = clearState,
                contextPreview = contextPreview,
                contextKey = contextKey,
                feedback = feedback,
                transcript = transcript,
                requestsUsed = requestsUsed,
                requestInFlight = requestInFlight,
                dismissedProposalKeys = dismissedProposalKeys,
                canApplyProposal = canApplyProposal,
                onApplyProposal = { proposal ->
                    if (onApplyProposal(proposal)) onDismiss()
                },
                onDismissProposal = { requestId ->
                    dismissedProposalKeys = dismissedProposalKeys + requestId
                },
                onRequest = { purpose, prompt ->
                    if (!requestInFlight) {
                        requestInFlight = true
                        lastRequestedPurpose = purpose
                        val history = transcript.mapNotNull { message ->
                            when (message) {
                                is EvidriloAiChatMessage.User -> AiConversationHistoryMessage("user", message.text)
                                is EvidriloAiChatMessage.Assistant -> AiConversationHistoryMessage(
                                    "assistant",
                                    message.text,
                                    message.groundedAnchorIds,
                                )
                                is EvidriloAiChatMessage.AssistantError -> null
                            }
                        }.takeLast(4)
                        onRequest(purpose, prompt, history)
                        transcript.add(EvidriloAiChatMessage.User(prompt))
                        requestsUsed += 1
                    }
                },
                onRetry = onRetry,
                onRetryClearConversation = onRetryClearConversation,
                onOpenAccount = {
                    onDismiss()
                    onOpenAccount()
                },
                onClearChat = {
                    onClearConversation()
                    transcript.clear()
                    requestsUsed = 0
                    lastRequestedPurpose = null
                    lastAnswerKey = null
                    lastErrorKey = null
                    dismissedProposalKeys = emptySet()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun EvidriloAiChatSheet(
    state: EvidriloAiAssistUiState,
    clearState: EvidriloAiConversationClearState,
    contextPreview: String,
    contextKey: String,
    feedback: ConclusionFeedbackItem,
    transcript: List<EvidriloAiChatMessage>,
    requestsUsed: Int,
    requestInFlight: Boolean,
    dismissedProposalKeys: Set<String>,
    canApplyProposal: (AiConversationProposal) -> Boolean,
    onApplyProposal: (AiConversationProposal) -> Unit,
    onDismissProposal: (String) -> Unit,
    onRequest: (AiAssistPurpose, String) -> Unit,
    onRetry: () -> Unit,
    onRetryClearConversation: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    var question by remember(contextKey) { mutableStateOf("") }
    var consented by remember(contextKey) { mutableStateOf(false) }
    val hasCredits = when (state) {
        is EvidriloAiAssistUiState.Ready -> aiChatCanRequest(state.credits)
        is EvidriloAiAssistUiState.Answer -> (state.remainingCredits ?: 0) > 0
        else -> false
    }
    val canSend = hasCredits && consented && requestsUsed < 5 && !requestInFlight &&
        aiChatCanSendDuringConversationClear(clearState)
    val submitPrompt: (AiAssistPurpose, String) -> Unit = { purpose, prompt ->
        if (canSend && prompt.isNotBlank()) {
            onRequest(purpose, prompt)
            consented = false
        }
    }
    val sendQuestion = {
        val prompt = question.trim()
        if (canSend && prompt.isNotEmpty()) {
            submitPrompt(AiAssistPurpose.EXPLAIN_FEEDBACK, prompt)
            question = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.86f)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EvidriloAiChatHeader(
            hasTranscript = transcript.isNotEmpty(),
            requestInFlight = requestInFlight,
            onClearChat = onClearChat,
            onDismiss = onDismiss,
        )
        when (val currentClearState = clearState) {
            EvidriloAiConversationClearState.Idle -> Unit
            is EvidriloAiConversationClearState.Clearing -> Text(
                if (currentClearState.sessionId == null) {
                    "Finishing the conversation reset…"
                } else {
                    "Clearing this conversation's server session metadata…"
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
            is EvidriloAiConversationClearState.Failed -> Surface(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(16.dp),
                color = EvidriloColors.Atmosphere,
                border = BorderStroke(2.dp, EvidriloColors.PaleBlue),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "This conversation was cleared on this device, but the server did not confirm clearing its session metadata. It may remain until the session expires. Your project and deterministic feedback are unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidriloColors.Ink,
                    )
                    if (currentClearState.retryable) {
                        TextButton(
                            onClick = { onRetryClearConversation(currentClearState.sessionId) },
                        ) { Text("Retry server clear") }
                    }
                }
            }
        }
        if (transcript.isNotEmpty() && requestInFlight) {
            Text(
                "The current response is still processing. Clear becomes available when it finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = EvidriloColors.PaleBlue,
        ) {
            Text(
                contextPreview,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Ink,
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (transcript.isEmpty()) {
                EvidriloAiAssistantBubble(
                    text = "I can explain this verification feedback or ask a reflection question. Every answer stays linked to this case.",
                )
                EvidriloAiPromptRow(
                    heading = "Suggested prompts",
                    actions = evidriloAiChatQuickActions(),
                    enabled = canSend,
                    onRequest = { action -> submitPrompt(action.purpose, action.prompt) },
                )
            } else {
                transcript.forEach { message ->
                    EvidriloAiChatBubble(message)
                    if (message is EvidriloAiChatMessage.Assistant &&
                        message.proposal != null &&
                        message.requestId !in dismissedProposalKeys
                    ) {
                        EvidriloAiProposalPreview(
                            proposal = message.proposal,
                            canApplyProposal = canApplyProposal,
                            onApplyProposal = onApplyProposal,
                            onDismiss = { onDismissProposal(message.requestId) },
                        )
                    }
                }
                val latestAssistant = transcript.lastOrNull() as? EvidriloAiChatMessage.Assistant
                if (latestAssistant != null && !requestInFlight && requestsUsed < 5) {
                    val nextPrompts = evidriloAiNextPromptRecommendations(
                        context = EvidriloAiPromptRecommendationContext(
                            status = feedback.status,
                            field = feedback.field,
                            allowedAnchorIds = feedback.anchorIds,
                            groundedAnchorIds = latestAssistant.groundedAnchorIds,
                        ),
                        previousPurpose = latestAssistant.purpose,
                    )
                    if (nextPrompts.isNotEmpty()) {
                        EvidriloAiPromptRow(
                            heading = "Suggested next",
                            actions = nextPrompts,
                            enabled = canSend,
                            onRequest = { action -> submitPrompt(action.purpose, action.prompt) },
                        )
                    }
                }
            }
            when (state) {
                EvidriloAiAssistUiState.SignInRequired -> {
                    if (TEMPORARY_GUEST_MODE_ENABLED) {
                        EvidriloAiAssistantBubble(
                            "AI help is temporarily unavailable in local guest mode. The case guide and your local work remain available; nothing was sent.",
                        )
                    } else {
                        EvidriloAiAssistantBubble("Sign in with a verified account to request AI help. The case guide still works offline.")
                        EvidriloSecondaryButton(label = "Open account", onClick = onOpenAccount)
                    }
                }
                EvidriloAiAssistUiState.Loading -> EvidriloAiTypingBubble()
                is EvidriloAiAssistUiState.Ready -> AiCreditSummary(credits = state.credits)
                is EvidriloAiAssistUiState.Answer -> Unit
                is EvidriloAiAssistUiState.Unavailable -> {
                    if (state.retryable) EvidriloSecondaryButton(label = "Try again", onClick = onRetry)
                }
            }
            EvidriloAiBoundaryBanner()
        }
        Row(
            modifier = Modifier.fillMaxWidth().toggleable(value = consented, onValueChange = { consented = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = consented, onCheckedChange = null)
            Text(
                "Share the shown feedback, my claim, and selected evidence for this request.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Ink,
            )
        }
        if (requestsUsed >= 5) {
            Text("Five requests reached for this review. Revise and verify again to start a new context.", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Slate)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = question,
                onValueChange = { if (it.length <= 280) question = it },
                modifier = Modifier.weight(1f),
                label = { Text("Ask about this feedback") },
                maxLines = 3,
                enabled = canSend,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendQuestion() }),
            )
            TextButton(
                onClick = sendQuestion,
                enabled = canSend && question.isNotBlank(),
            ) { Text("Send") }
        }
        Text(
            "Credits follow verified provider token usage. A valid AI response is charged; failed or rejected requests release the reservation. The deterministic check remains authoritative.",
            style = MaterialTheme.typography.labelSmall,
            color = EvidriloColors.Slate,
        )
    }
}

@Composable
private fun EvidriloAiChatHeader(
    hasTranscript: Boolean,
    requestInFlight: Boolean,
    onClearChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(EvidriloColors.PaleBlue),
            contentAlignment = Alignment.Center,
        ) {
            EvidriloIcon(
                name = EvidriloIconName.SPARK,
                tint = EvidriloColors.Cobalt,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Evidrilo AI", style = MaterialTheme.typography.headlineSmall)
            Text(
                "A bounded assistant for this evidence review.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
        }
        if (aiChatCanClear(hasTranscript, requestInFlight)) {
            EvidriloIconButton(
                icon = EvidriloIconName.HISTORY,
                contentDescription = "Clear this conversation",
                onClick = onClearChat,
            )
        }
        EvidriloIconButton(
            icon = EvidriloIconName.CHEVRON_DOWN,
            contentDescription = "Close Evidrilo AI assistant",
            onClick = onDismiss,
        )
    }
}

@Composable
private fun EvidriloAiBoundaryBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = EvidriloColors.Atmosphere,
        border = BorderStroke(2.dp, EvidriloColors.PaleBlue),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            EvidriloIcon(
                name = EvidriloIconName.INFO,
                tint = EvidriloColors.Cobalt,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "AI can explain feedback and offer a draft suggestion. It cannot change verification status or invent evidence; a suggestion stays unapplied until you choose it and verify again.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Ink,
            )
        }
    }
}

@Composable
private fun EvidriloAiChatBubble(message: EvidriloAiChatMessage) {
    when (message) {
        is EvidriloAiChatMessage.User -> EvidriloAiUserBubble(message.text)
        is EvidriloAiChatMessage.Assistant -> {
            EvidriloAiAssistantBubble(
                text = message.text,
                groundedAnchorIds = message.groundedAnchorIds,
                remainingCredits = message.remainingCredits,
                creditCost = message.creditCost,
            )
        }
        is EvidriloAiChatMessage.AssistantError -> EvidriloAiAssistantBubble(
            text = message.text,
            error = true,
            creditCost = message.creditCost,
            remainingCredits = message.remainingCredits,
        )
    }
}

@Composable
private fun EvidriloAiProposalPreview(
    proposal: AiConversationProposal,
    canApplyProposal: (AiConversationProposal) -> Boolean,
    onApplyProposal: (AiConversationProposal) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember(proposal) { mutableStateOf(false) }
    var editedValue by remember(proposal) { mutableStateOf(proposal.suggestedValue) }
    val fieldLabel = when (proposal.field) {
        "claim_text" -> "Claim"
        "claim_scope" -> "Claim scope"
        "learner_limitation" -> "Limitation note"
        "next_action" -> "Next action"
        else -> "Draft field"
    }
    val editable = aiProposalSupportsInlineEdit(proposal)
    val editedProposal = if (editing) editGroundedAiProposal(proposal, editedValue) else proposal
    val canApply = editedProposal != null && canApplyProposal(editedProposal)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = EvidriloColors.Atmosphere,
        border = BorderStroke(1.dp, EvidriloColors.PaleBlue),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Draft suggestion · $fieldLabel", style = MaterialTheme.typography.titleSmall, color = EvidriloColors.Ink)
            Text("Current: ${proposal.beforeValue ?: "Empty"}", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            if (editing && proposal.field == "claim_scope") {
                Text("Choose how far the claim should reach", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                listOf(
                    ConclusionScope.THIS_OBSERVATION,
                    ConclusionScope.LIMITED_COMPARISON,
                    ConclusionScope.GENERAL_CAUSAL_CLAIM,
                ).forEach { scope ->
                    FilterChip(
                        selected = editedValue == scope.name,
                        onClick = { editedValue = scope.name },
                        label = {
                            Text(
                                when (scope) {
                                    ConclusionScope.THIS_OBSERVATION -> "This observation only"
                                    ConclusionScope.LIMITED_COMPARISON -> "Limited comparison"
                                    ConclusionScope.GENERAL_CAUSAL_CLAIM -> "Cause-and-effect claim"
                                    ConclusionScope.UNSUPPORTED -> "Unsupported"
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (editing) {
                val maxLength = if (proposal.field == "claim_text") 320 else 300
                OutlinedTextField(
                    value = editedValue,
                    onValueChange = { candidate ->
                        if (candidate.length <= maxLength) editedValue = candidate
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Edit suggested ${fieldLabel.lowercase()}") },
                    supportingText = { Text("${editedValue.length}/$maxLength characters") },
                    minLines = 2,
                    maxLines = 4,
                )
                Text(
                    "Your edit stays in this proposal until you apply it. The original evidence links do not verify your changed wording; submit the draft for a fresh check.",
                    style = MaterialTheme.typography.labelSmall,
                    color = EvidriloColors.Slate,
                )
            } else {
                Text("Suggested: ${proposal.suggestedValue}", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Ink)
            }
            Text(
                if (canApply) {
                    "Not applied. Review it, then submit your draft for a fresh deterministic check."
                } else {
                    "This field or value cannot be applied to the current case draft. No draft changes were made."
                },
                style = MaterialTheme.typography.labelSmall,
                color = EvidriloColors.Slate,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editable) {
                    TextButton(
                        onClick = {
                            val wasEditing = editing
                            editing = !wasEditing
                            if (wasEditing) editedValue = proposal.suggestedValue
                        },
                    ) {
                        Text(if (editing) "Cancel edit" else "Edit suggestion")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            TextButton(
                onClick = { editedProposal?.let(onApplyProposal) },
                enabled = canApply,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (editing) "Apply edited proposal" else "Apply to draft")
            }
        }
    }
}

@Composable
private fun EvidriloAiUserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp),
            color = EvidriloColors.PrimaryAction,
            contentColor = EvidriloColors.White,
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.White,
            )
        }
    }
}

@Composable
private fun EvidriloAiAssistantBubble(
    text: String,
    groundedAnchorIds: List<String> = emptyList(),
    remainingCredits: Int? = null,
    creditCost: Int? = null,
    error: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(if (error) EvidriloColors.ErrorSurface else EvidriloColors.PaleBlue),
            contentAlignment = Alignment.Center,
        ) {
            EvidriloIcon(
                name = if (error) EvidriloIconName.ALERT else EvidriloIconName.SPARK,
                tint = if (error) EvidriloColors.Error else EvidriloColors.Cobalt,
                modifier = Modifier.size(18.dp),
            )
        }
        Surface(
            modifier = Modifier
                .padding(start = 10.dp)
                .widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = if (error) EvidriloColors.ErrorSurface else EvidriloColors.PaleBlue,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error) EvidriloColors.Error else EvidriloColors.Ink,
                )
                if (groundedAnchorIds.isNotEmpty()) {
                    Text(
                        "Grounded in: ${groundedAnchorIds.joinToString()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EvidriloColors.Slate,
                    )
                }
                remainingCredits?.let { remaining ->
                    Text(
                        "$remaining AI credits remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = EvidriloColors.Slate,
                    )
                }
                creditCost?.let { cost ->
                    Text(
                        aiCreditUsageLabel(cost, error),
                        style = MaterialTheme.typography.labelSmall,
                        color = EvidriloColors.Slate,
                    )
                }
            }
        }
    }
}

internal fun aiCreditUsageLabel(creditCost: Int, error: Boolean): String = when {
    error && creditCost == 0 -> "No AI credits were charged"
    error -> "$creditCost AI ${creditCostLabel(creditCost)} ${if (creditCost == 1) "was" else "were"} charged before this turn became unavailable"
    else -> "$creditCost AI ${creditCostLabel(creditCost)} used"
}

private fun creditCostLabel(creditCost: Int): String = if (creditCost == 1) "credit" else "credits"

@Composable
private fun EvidriloAiTypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(EvidriloColors.PaleBlue),
            contentAlignment = Alignment.Center,
        ) {
            EvidriloIcon(
                name = EvidriloIconName.SPARK,
                tint = EvidriloColors.Cobalt,
                modifier = Modifier.size(18.dp),
            )
        }
        Surface(
            modifier = Modifier.padding(start = 10.dp),
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            color = EvidriloColors.PaleBlue,
        ) {
            Text(
                "Evidrilo AI is thinking…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
        }
    }
}

@Composable
private fun EvidriloAiPromptRow(
    heading: String,
    actions: List<EvidriloAiChatQuickAction>,
    enabled: Boolean,
    onRequest: (EvidriloAiChatQuickAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            heading,
            style = MaterialTheme.typography.labelMedium,
            color = EvidriloColors.Slate,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                EvidriloAiPromptChip(
                    action = action,
                    enabled = enabled,
                    onClick = { onRequest(action) },
                )
            }
        }
    }
}

@Composable
private fun EvidriloAiPromptChip(
    action: EvidriloAiChatQuickAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = "${action.label}. ${action.description}"
                role = Role.Button
            },
        shape = RoundedCornerShape(50),
        color = EvidriloColors.Card,
        border = BorderStroke(2.dp, if (enabled) EvidriloColors.Cobalt else EvidriloColors.Separator),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EvidriloIcon(
                name = EvidriloIconName.SPARK,
                tint = if (enabled) EvidriloColors.Cobalt else EvidriloColors.Slate,
                modifier = Modifier.size(16.dp),
            )
            Text(
                action.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) EvidriloColors.Cobalt else EvidriloColors.Slate,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun aiCreditAllowanceLabel(credits: AiCredits): String =
    if (!credits.consentRecorded) {
        "Review consent to see available credits"
    } else {
        buildList {
            add("${credits.available} available")
            if (credits.grants.isEmpty()) {
                add("No active AI credit grants")
            } else {
                credits.grants.forEach { grant ->
                    val expires = grant.expiresAt?.take(10)?.let { " · expires $it" }.orEmpty()
                    val scope = when (grant.grantKind) {
                        "free_once" -> "Free · one-time"
                        "subscription_month" -> "Pro · current period"
                        else -> "AI grant"
                    }
                    add("$scope: ${grant.available} available$expires")
                }
            }
        }.joinToString("\n")
    }

@Composable
internal fun AiCreditSummary(credits: AiCredits) {
    val allowanceLines = aiCreditAllowanceLabel(credits).split('\n')
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = EvidriloColors.Surface,
        border = BorderStroke(2.dp, EvidriloColors.Separator),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EvidriloIcon(
                    name = EvidriloIconName.SPARK,
                    tint = EvidriloColors.Cobalt,
                    modifier = Modifier.size(18.dp),
                )
                Text("AI credits", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                if (credits.consentRecorded) {
                    Text(
                        allowanceLines.first(),
                        style = MaterialTheme.typography.labelLarge,
                        color = EvidriloColors.Cobalt,
                    )
                }
            }
            val detailLines = if (credits.consentRecorded) allowanceLines.drop(1) else allowanceLines
            detailLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Slate)
            }
        }
    }
}
