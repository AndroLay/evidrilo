package dev.nextgen.mobile.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AudioCoordinatorTest {
    @Test
    fun narration_uses_a_matching_bundled_clip_and_starts_playback() {
        val engine = RecordingAudioEngine()
        val catalog = AudioCatalog(
            narration = mapOf(
                AudioNarrationId.ONBOARDING to listOf(
                    AudioClipSpec(
                        id = AudioNarrationId.ONBOARDING,
                        sourceText = "Start with the evidence.",
                        resourceName = "audio/narration/onboarding.m4a",
                    ),
                ),
            ),
        )
        val coordinator = AudioCoordinator(engine = engine, catalog = catalog)

        coordinator.playNarration(
            id = AudioNarrationId.ONBOARDING,
            sourceText = "Start with the evidence.",
        )

        assertEquals(
            listOf("asset:audio/narration/onboarding.m4a"),
            engine.calls,
        )
        assertIs<AudioPlaybackState.Loading>(coordinator.state)
    }

    @Test
    fun narration_uses_offline_speech_when_the_source_text_does_not_match() {
        val engine = RecordingAudioEngine()
        val catalog = AudioCatalog(
            narration = mapOf(
                AudioNarrationId.ONBOARDING to listOf(
                    AudioClipSpec(
                        id = AudioNarrationId.ONBOARDING,
                        sourceText = "Start with the evidence.",
                        resourceName = "audio/narration/onboarding.m4a",
                    ),
                ),
            ),
        )
        val coordinator = AudioCoordinator(engine = engine, catalog = catalog)

        coordinator.playNarration(AudioNarrationId.ONBOARDING, "Changed copy.")

        assertEquals(listOf("speech:Changed copy."), engine.calls)
    }

    @Test
    fun disabled_narration_does_not_start_a_request() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(
            engine = engine,
            initialSettings = AudioSettings(narrationEnabled = false),
        )

        coordinator.speakDynamic("Keep this text visible.")

        assertTrue(engine.calls.isEmpty())
        assertEquals(AudioPlaybackState.Idle, coordinator.state)
    }

    @Test
    fun replacing_audio_stops_the_old_request_and_ignores_its_stale_callback() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(engine = engine)

        coordinator.speakDynamic("First sentence.")
        val staleCallback = engine.callbacks.first()
        coordinator.speakDynamic("Second sentence.")

        staleCallback(AudioEngineEvent.Started)
        staleCallback(AudioEngineEvent.Interrupted)

        assertEquals(
            listOf("speech:First sentence.", "stop:1", "speech:Second sentence."),
            engine.calls,
        )
        assertIs<AudioPlaybackState.Loading>(coordinator.state)
    }

    @Test
    fun replay_starts_the_last_request_with_a_new_generation() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(
            engine = engine,
            catalog = AudioCatalog(
                narration = mapOf(
                    AudioNarrationId.ONBOARDING to listOf(
                        AudioClipSpec(
                            AudioNarrationId.ONBOARDING,
                            "Start with the evidence.",
                            "audio/narration/onboarding.m4a",
                        ),
                    ),
                ),
            ),
        )

        coordinator.playNarration(AudioNarrationId.ONBOARDING, "Start with the evidence.")
        engine.emit(0, AudioEngineEvent.Completed)
        coordinator.replay()

        assertEquals(
            listOf(
                "asset:audio/narration/onboarding.m4a",
                "asset:audio/narration/onboarding.m4a",
            ),
            engine.calls,
        )
        assertIs<AudioPlaybackState.Loading>(coordinator.state)
    }

    @Test
    fun stop_returns_to_idle_and_late_completion_cannot_resurrect_audio() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(engine = engine)

        coordinator.speakDynamic("Stop me.")
        val callback = engine.callbacks.first()
        coordinator.stop()
        callback(AudioEngineEvent.Completed)

        assertEquals(AudioPlaybackState.Idle, coordinator.state)
        assertEquals(listOf("speech:Stop me.", "stop:1"), engine.calls)
    }

    @Test
    fun missing_effect_is_unavailable_without_calling_the_engine() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(engine = engine)

        coordinator.playEffect(AudioEffectId.SUCCESS)

        assertEquals(AudioPlaybackState.Unavailable, coordinator.state)
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun engine_unavailable_is_exposed_without_unlocking_any_other_state() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(engine = engine)

        coordinator.speakDynamic("No offline voice.")
        engine.emit(0, AudioEngineEvent.Unavailable)

        assertEquals(AudioPlaybackState.Unavailable, coordinator.state)
    }

    @Test
    fun completion_and_interruption_are_bound_to_the_active_request() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(engine = engine)

        coordinator.speakDynamic("Play this offline.")
        engine.emit(0, AudioEngineEvent.Started)
        assertIs<AudioPlaybackState.Playing>(coordinator.state)
        engine.emit(0, AudioEngineEvent.Completed)
        assertEquals(AudioPlaybackState.Completed, coordinator.state)

        coordinator.speakDynamic("Interrupt this offline.")
        engine.emit(1, AudioEngineEvent.Started)
        engine.emit(1, AudioEngineEvent.Interrupted)
        assertEquals(AudioPlaybackState.Idle, coordinator.state)
    }

    @Test
    fun pause_resume_and_failure_events_update_only_the_current_request() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(engine = engine)

        coordinator.speakDynamic("Pause this offline.")
        engine.emit(0, AudioEngineEvent.Started)
        coordinator.pauseOrResume()
        engine.emit(0, AudioEngineEvent.Paused)
        assertIs<AudioPlaybackState.Paused>(coordinator.state)
        coordinator.pauseOrResume()
        engine.emit(0, AudioEngineEvent.Resumed)
        assertIs<AudioPlaybackState.Playing>(coordinator.state)

        coordinator.speakDynamic("Fail this offline.")
        engine.emit(1, AudioEngineEvent.Failed("Audio output failed."))
        assertEquals(
            AudioPlaybackState.Failed("Audio output failed."),
            coordinator.state,
        )
    }

    @Test
    fun network_required_offline_voice_is_exposed_as_unavailable() {
        val engine = RecordingAudioEngine(offlineSpeechUnavailable = true)
        val coordinator = AudioCoordinator(engine = engine)

        coordinator.speakDynamic("Keep the visible text.")

        assertEquals(AudioPlaybackState.Unavailable, coordinator.state)
    }

    @Test
    fun disabling_effects_stops_an_active_effect_and_blocks_its_replay() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(
            engine = engine,
            catalog = AudioCatalog(
                effects = mapOf(
                    AudioEffectId.SUCCESS to AudioEffectSpec(
                        AudioEffectId.SUCCESS,
                        "audio/effects/success.wav",
                    ),
                ),
            ),
        )

        coordinator.playEffect(AudioEffectId.SUCCESS)
        engine.emit(0, AudioEngineEvent.Started)
        coordinator.updateSettings(AudioSettings(effectsEnabled = false))
        coordinator.replay()

        assertEquals(
            listOf("effect:audio/effects/success.wav", "stop:1"),
            engine.calls,
        )
        assertEquals(AudioPlaybackState.Idle, coordinator.state)
    }

    @Test
    fun completed_effect_returns_to_idle_instead_of_exposing_narration_replay() {
        val engine = RecordingAudioEngine()
        val coordinator = AudioCoordinator(
            engine = engine,
            catalog = AudioCatalog(
                effects = mapOf(
                    AudioEffectId.SUCCESS to AudioEffectSpec(
                        AudioEffectId.SUCCESS,
                        "audio/effects/success.wav",
                    ),
                ),
            ),
        )

        coordinator.playEffect(AudioEffectId.SUCCESS)
        engine.emit(0, AudioEngineEvent.Started)
        engine.emit(0, AudioEngineEvent.Completed)

        assertEquals(AudioPlaybackState.Idle, coordinator.state)
    }
}

private class RecordingAudioEngine(
    private val offlineSpeechUnavailable: Boolean = false,
) : AudioEngine {
    val calls = mutableListOf<String>()
    val callbacks = mutableListOf<(AudioEngineEvent) -> Unit>()

    override fun playAsset(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        calls += "asset:$resourceName"
        callbacks += onEvent
    }

    override fun speakOffline(
        text: String,
        localeTag: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        calls += "speech:$text"
        callbacks += onEvent
        if (offlineSpeechUnavailable) {
            onEvent(AudioEngineEvent.Unavailable)
        }
    }

    override fun playEffect(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        calls += "effect:$resourceName"
        callbacks += onEvent
    }

    override fun pause(requestId: Long) = Unit

    override fun resume(requestId: Long) = Unit

    override fun stop(requestId: Long) {
        calls += "stop:$requestId"
    }

    override fun close() = Unit

    fun emit(index: Int, event: AudioEngineEvent) {
        callbacks[index](event)
    }
}
