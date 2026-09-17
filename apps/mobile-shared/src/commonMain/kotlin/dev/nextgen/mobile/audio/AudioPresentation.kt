package dev.nextgen.mobile.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.EvidriloColors

internal data class AudioListenPresentation(
    val label: String,
    val contentDescription: String,
    val stateDescription: String,
)

internal fun audioListenPresentation(state: AudioPlaybackState): AudioListenPresentation = when (narrationState(state)) {
    AudioPlaybackState.Idle -> AudioListenPresentation(
        label = "Listen",
        contentDescription = "Listen to this content",
        stateDescription = "Audio ready",
    )

    is AudioPlaybackState.Loading -> AudioListenPresentation(
        label = "Loading audio…",
        contentDescription = "Audio is loading",
        stateDescription = "Loading",
    )

    is AudioPlaybackState.Playing -> AudioListenPresentation(
        label = "Pause",
        contentDescription = "Pause audio",
        stateDescription = "Playing",
    )

    is AudioPlaybackState.Paused -> AudioListenPresentation(
        label = "Resume",
        contentDescription = "Resume audio",
        stateDescription = "Paused",
    )

    AudioPlaybackState.Completed -> AudioListenPresentation(
        label = "Replay",
        contentDescription = "Replay audio",
        stateDescription = "Finished",
    )

    AudioPlaybackState.Unavailable -> AudioListenPresentation(
        label = "Listen",
        contentDescription = "Audio is unavailable; read the visible text",
        stateDescription = "Audio unavailable; visible text remains available",
    )

    is AudioPlaybackState.Failed -> AudioListenPresentation(
        label = "Try audio again",
        contentDescription = "Try audio again; read the visible text if it remains unavailable",
        stateDescription = "Audio failed; visible text remains available",
    )
}

private fun narrationState(state: AudioPlaybackState): AudioPlaybackState = when (state) {
    is AudioPlaybackState.Loading -> if (state.channel == AudioChannel.EFFECT) {
        AudioPlaybackState.Idle
    } else {
        state
    }

    is AudioPlaybackState.Playing -> if (state.channel == AudioChannel.EFFECT) {
        AudioPlaybackState.Idle
    } else {
        state
    }

    is AudioPlaybackState.Paused -> if (state.channel == AudioChannel.EFFECT) {
        AudioPlaybackState.Idle
    } else {
        state
    }

    else -> state
}

@Composable
internal fun EvidriloAudioListenControl(
    state: AudioPlaybackState,
    onListen: () -> Unit,
    onPauseOrResume: () -> Unit,
    onStopAudio: () -> Unit,
) {
    val narrationPlaybackState = narrationState(state)
    val presentation = audioListenPresentation(narrationPlaybackState)
    val isActive = narrationPlaybackState is AudioPlaybackState.Loading ||
        narrationPlaybackState is AudioPlaybackState.Playing ||
        narrationPlaybackState is AudioPlaybackState.Paused
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick = {
                when (narrationPlaybackState) {
                    is AudioPlaybackState.Playing,
                    is AudioPlaybackState.Paused,
                    -> onPauseOrResume()

                    else -> onListen()
                }
            },
            enabled = narrationPlaybackState !is AudioPlaybackState.Loading,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = presentation.contentDescription
                    stateDescription = presentation.stateDescription
                    role = Role.Button
                },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = EvidriloColors.Ink,
                disabledContentColor = EvidriloColors.Outline,
            ),
        ) {
            Text(presentation.label)
        }
        if (isActive) {
            OutlinedButton(
                onClick = onStopAudio,
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Stop audio"
                        stateDescription = "Audio can be stopped"
                        role = Role.Button
                    },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = EvidriloColors.Ink,
                ),
            ) {
                Text("Stop")
            }
        }
    }
}
