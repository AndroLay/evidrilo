package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.audio.AudioPlaybackState
import dev.nextgen.mobile.audio.EvidriloAudioListenControl

@Composable
internal fun EvidriloOnboardingScreen(
    presentation: EvidriloOnboardingPresentation,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    EvidriloContentColumn {
        EvidriloLogoMark(contentDescription = "Evidrilo")
        EvidriloEyebrow("A SHORT START")
        Text(presentation.title, style = MaterialTheme.typography.headlineLarge)
        Text(presentation.body, style = MaterialTheme.typography.bodyLarge)
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            presentation.freeBenefits.forEachIndexed { index, benefit ->
                EvidriloTintPanel {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                    Text(benefit, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        EvidriloPrimaryButton(
            label = presentation.primaryLabel,
            onClick = onStart,
        )
        EvidriloSecondaryButton(
            label = presentation.secondaryLabel,
            onClick = onSkip,
        )
    }
}
