package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.audio.AudioPlaybackState
import dev.nextgen.mobile.audio.EvidriloAudioListenControl

@Composable
internal fun EvidriloSupportScreen(
    onBack: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenAccount: () -> Unit,
    customerCenterAvailable: Boolean = false,
    onOpenCustomerCenter: () -> Unit = {},
    backLabel: String = "Settings",
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    var contactLaunchFailed by remember { mutableStateOf(false) }

    EvidriloContentColumn {
        EvidriloBackButton(label = backLabel, onClick = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Support and billing", style = MaterialTheme.typography.displayMedium)
            Text(
                "Find the right next step without losing your local workflow.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )

        EvidriloTintPanel {
            Text("Restore a purchase", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open Premium and choose Restore purchase. The store account, not Evidrilo, is the source of truth for a completed transaction.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        EvidriloTintPanel {
            Text("Manage or cancel a subscription", style = MaterialTheme.typography.titleMedium)
            Text(
                if (customerCenterAvailable) {
                    "Open Customer Center for self-service subscription management. The store remains the source of truth for billing and cancellation."
                } else {
                    "Use Apple Account subscriptions on iOS or Google Play subscriptions on Android. Evidrilo does not claim a provider-neutral management link on this build."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (customerCenterAvailable) {
                EvidriloSecondaryButton(
                    label = "Manage subscription",
                    onClick = onOpenCustomerCenter,
                )
            }
        }
        EvidriloTintPanel {
            Text("Refund guidance", style = MaterialTheme.typography.titleMedium)
            Text(
                "Refund requests must be made through the store that processed the purchase. Include the store order or transaction reference; never send a password or payment credential to support.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        EvidriloTintPanel {
            Text("Privacy and deletion", style = MaterialTheme.typography.titleMedium)
            Text(
                "Free drafts remain local. A signed-in user can request server-account deletion from Account; local workflow and history are cleared separately so the action is explicit.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        EvidriloTintPanel {
            Text("Contact", style = MaterialTheme.typography.titleMedium)
            Text(
                "For help, email the Evidrilo team. Do not include passwords or payment credentials.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer {
                Text("andrlay30@gmail.com", style = MaterialTheme.typography.bodyMedium)
            }
            if (contactLaunchFailed) {
                Text(
                    "No email app opened. Select and copy the address above.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloSecondaryButton(
                label = "Email support",
                onClick = {
                    contactLaunchFailed = try {
                        uriHandler.openUri("mailto:andrlay30@gmail.com?subject=Evidrilo%20Support")
                        false
                    } catch (_: Exception) {
                        true
                    }
                },
            )
        }

        EvidriloPrimaryButton(
            label = "Open Premium plans",
            onClick = onOpenPremium,
        )
        EvidriloSecondaryButton(
            label = "Open Account and privacy controls",
            onClick = onOpenAccount,
        )
    }
}
