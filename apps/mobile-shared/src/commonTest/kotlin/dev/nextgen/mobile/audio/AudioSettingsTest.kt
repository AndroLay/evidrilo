package dev.nextgen.mobile.audio

import dev.nextgen.mobile.storage.LocalStorageStatus
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioSettingsTest {
    @Test
    fun unavailable_store_keeps_audio_optional_and_reports_unavailable_save() {
        val store = NoopAudioSettingsStore()

        assertEquals(AudioSettings(), store.load())
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, store.save(AudioSettings(false, false)))
        assertEquals(LocalStorageStatus.UNAVAILABLE, store.save(AudioSettings()).status)
    }

    @Test
    fun settings_keep_narration_and_effects_independent() {
        val store = MemoryAudioSettingsStore()

        assertEquals(AudioSettings(), store.load())
        assertEquals(
            LocalStorageWriteResult.SAVED,
            store.save(AudioSettings(narrationEnabled = false, effectsEnabled = true)),
        )
        assertEquals(
            AudioSettings(narrationEnabled = false, effectsEnabled = true),
            store.load(),
        )
    }
}

private class MemoryAudioSettingsStore(
    private var settings: AudioSettings = AudioSettings(),
) : AudioSettingsStore {
    override fun load(): AudioSettings = settings

    override fun save(settings: AudioSettings): LocalStorageWriteResult {
        this.settings = settings
        return LocalStorageWriteResult.SAVED
    }
}
