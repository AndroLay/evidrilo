package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
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
        EvidriloBackGesture(label = backLabel, onClick = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How can we help?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Help with your account, data, and purchases.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )

        EvidriloExplanation("Restore a purchase", "Open Premium and choose Restore purchase. The store account holds the completed transaction.")
        EvidriloExplanation("Manage a subscription", if (customerCenterAvailable) "Use Customer Center. The store controls billing and cancellation." else "Use Apple Account subscriptions on iOS or Google Play subscriptions on Android.")
        if (customerCenterAvailable) EvidriloSecondaryButton("Manage subscription", onOpenCustomerCenter)
        EvidriloExplanation("Request a refund", "Contact the store that processed your purchase. Include the order or transaction reference. Never send passwords or payment credentials to support.")
        EvidriloExplanation("Privacy and deletion", "Local projects stay on this device. Request server-account deletion from Account; local workflow and history are cleared separately.")
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
