package dev.nextgen.mobile.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioPresentationTest {
    @Test
    fun idle_exposes_a_visible_listen_action() {
        val presentation = audioListenPresentation(AudioPlaybackState.Idle)

        assertEquals("Listen", presentation.label)
        assertEquals("Listen to this content", presentation.contentDescription)
        assertEquals("Audio ready", presentation.stateDescription)
    }

    @Test
    fun active_states_expose_pause_and_resume_semantics() {
        assertEquals(
            "Pause",
            audioListenPresentation(AudioPlaybackState.Playing(1, AudioChannel.NARRATION)).label,
        )
        assertEquals(
            "Resume",
            audioListenPresentation(AudioPlaybackState.Paused(1, AudioChannel.NARRATION)).label,
        )
    }

    @Test
    fun completed_audio_exposes_replay_semantics() {
        val presentation = audioListenPresentation(AudioPlaybackState.Completed)

        assertEquals("Replay", presentation.label)
        assertTrue(presentation.contentDescription.contains("Replay"))
    }

    @Test
    fun unavailable_audio_keeps_the_visible_text_as_the_fallback() {
        val presentation = audioListenPresentation(AudioPlaybackState.Unavailable)

        assertTrue(presentation.contentDescription.contains("visible text"))
        assertTrue(presentation.stateDescription.contains("visible text"))
    }

    @Test
    fun effect_playback_does_not_take_over_the_narration_control() {
        val presentation = audioListenPresentation(
            AudioPlaybackState.Playing(1, AudioChannel.EFFECT),
        )

        assertEquals("Listen", presentation.label)
        assertEquals("Audio ready", presentation.stateDescription)
    }
}
