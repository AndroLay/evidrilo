package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.ai.AiCredits

internal sealed interface AiCreditBalancePresentation {
    data object SignInRequired : AiCreditBalancePresentation
    data object Refreshing : AiCreditBalancePresentation
    data class Ready(val credits: AiCredits) : AiCreditBalancePresentation
    data class Unavailable(val message: String, val canRetry: Boolean) : AiCreditBalancePresentation
}

internal fun presentAiCreditBalance(
    accountReady: Boolean,
    refreshing: Boolean,
    credits: AiCredits?,
    failureMessage: String?,
    canRetry: Boolean,
): AiCreditBalancePresentation = when {
    !accountReady -> AiCreditBalancePresentation.SignInRequired
    refreshing -> AiCreditBalancePresentation.Refreshing
    !failureMessage.isNullOrBlank() -> AiCreditBalancePresentation.Unavailable(failureMessage, canRetry)
    credits != null -> AiCreditBalancePresentation.Ready(credits)
    else -> AiCreditBalancePresentation.Unavailable("The shared balance has not been loaded yet.", canRetry)
}

@Composable
internal fun EvidriloAiCreditBadge(presentation: AiCreditBalancePresentation, onRefresh: () -> Unit) {
    if (presentation == AiCreditBalancePresentation.SignInRequired) return
    val label = uiText("AI credits", "Kredit AI")
    Surface(onClick = onRefresh, color = EvidriloColors.Tint, shape = RoundedCornerShape(50),
        modifier = Modifier.semantics { contentDescription = label }) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EvidriloIcon(EvidriloIconName.LIGHTNING, tint = EvidriloColors.Cobalt, modifier = Modifier.size(16.dp))
            if (presentation == AiCreditBalancePresentation.Refreshing)
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = EvidriloColors.Cobalt)
            else Text((presentation as? AiCreditBalancePresentation.Ready)?.credits?.available?.toString() ?: "—",
                style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
        }
    }
}

@Composable
internal fun EvidriloAiCreditBalancePanel(
    presentation: AiCreditBalancePresentation,
    onRefresh: (() -> Unit)? = null,
) {
    when (presentation) {
        is AiCreditBalancePresentation.Ready -> {
            AiCreditSummary(credits = presentation.credits)
            if (onRefresh != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRefresh) { Text("Refresh balance") }
                }
            }
        }

        AiCreditBalancePresentation.SignInRequired -> StatusCard(
            message = "Sign in with a verified account to view your shared AI credits.",
        )

        AiCreditBalancePresentation.Refreshing -> StatusCard(
            message = "Refreshing your shared balance from Evidrilo…",
            showProgress = true,
        )

        is AiCreditBalancePresentation.Unavailable -> StatusCard(
            message = presentation.message,
            action = if (presentation.canRetry) onRefresh else null,
        )
    }
}

@Composable
private fun StatusCard(
    message: String,
    showProgress: Boolean = false,
    action: (() -> Unit)? = null,
) {
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
                Text("AI credits", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                if (showProgress) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            if (action != null) {
                TextButton(onClick = action) { Text("Try again") }
            }
        }
    }
}
