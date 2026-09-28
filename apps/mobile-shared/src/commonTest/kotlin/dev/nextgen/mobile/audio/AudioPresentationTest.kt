package dev.nextgen.mobile.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioPresentationTest {
    @Test
    fun current_student_surfaces_do_not_offer_narration_controls() {
        val states = listOf(
            AudioPlaybackState.Idle,
            AudioPlaybackState.Loading(1, AudioChannel.NARRATION),
            AudioPlaybackState.Playing(1, AudioChannel.NARRATION),
            AudioPlaybackState.Paused(1, AudioChannel.NARRATION),
            AudioPlaybackState.Completed,
            AudioPlaybackState.Unavailable,
            AudioPlaybackState.Failed("Playback failed"),
        )

        states.forEach { state ->
            assertNull(currentAudioListenPresentation(state), "Narration control should stay hidden for $state")
        }
    }

    @Test
    fun idle_exposes_a_visible_listen_action() {
        val presentation = audioListenStatePresentation(AudioPlaybackState.Idle)

        assertEquals("Listen", presentation.label)
        assertEquals("Listen to this content", presentation.contentDescription)
        assertEquals("Audio ready", presentation.stateDescription)
    }

    @Test
    fun active_states_expose_pause_and_resume_semantics() {
        assertEquals(
            "Pause",
            audioListenStatePresentation(AudioPlaybackState.Playing(1, AudioChannel.NARRATION)).label,
        )
        assertEquals(
            "Resume",
            audioListenStatePresentation(AudioPlaybackState.Paused(1, AudioChannel.NARRATION)).label,
        )
    }

    @Test
    fun completed_audio_exposes_replay_semantics() {
        val presentation = audioListenStatePresentation(AudioPlaybackState.Completed)

        assertEquals("Replay", presentation.label)
        assertTrue(presentation.contentDescription.contains("Replay"))
    }

    @Test
    fun unavailable_audio_keeps_the_visible_text_as_the_fallback() {
        val presentation = audioListenStatePresentation(AudioPlaybackState.Unavailable)

        assertTrue(presentation.contentDescription.contains("visible text"))
        assertTrue(presentation.stateDescription.contains("visible text"))
    }

    @Test
    fun effect_playback_does_not_take_over_the_narration_control() {
        val presentation = audioListenStatePresentation(
            AudioPlaybackState.Playing(1, AudioChannel.EFFECT),
        )

        assertEquals("Listen", presentation.label)
        assertEquals("Audio ready", presentation.stateDescription)
    }
}
