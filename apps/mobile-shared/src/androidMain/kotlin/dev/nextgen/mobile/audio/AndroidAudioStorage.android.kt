package dev.nextgen.mobile.audio

import android.content.Context
import dev.nextgen.mobile.storage.LocalStorageWriteResult

private const val PREFERENCES_NAME = "evidrilo_audio_v1"
private const val NARRATION_ENABLED_KEY = "narration_enabled"
private const val EFFECTS_ENABLED_KEY = "effects_enabled"

object AndroidAudioStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createSettingsStore(): AudioSettingsStore =
        applicationContext?.let(::AndroidAudioSettingsStore) ?: NoopAudioSettingsStore()

    internal fun createEngine(): AudioEngine =
        applicationContext?.let(::AndroidAudioEngine) ?: UnavailableAudioEngine()
}

internal actual fun createAudioSettingsStore(): AudioSettingsStore =
    AndroidAudioStorage.createSettingsStore()

private class AndroidAudioSettingsStore(
    context: Context,
) : AudioSettingsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): AudioSettings = runCatching {
        AudioSettings(
            narrationEnabled = preferences.getBoolean(NARRATION_ENABLED_KEY, true),
            effectsEnabled = preferences.getBoolean(EFFECTS_ENABLED_KEY, true),
        )
    }.getOrElse { AudioSettings() }

    override fun save(settings: AudioSettings): LocalStorageWriteResult = runCatching {
        if (
            preferences.edit()
                .putBoolean(NARRATION_ENABLED_KEY, settings.narrationEnabled)
                .putBoolean(EFFECTS_ENABLED_KEY, settings.effectsEnabled)
                .commit()
        ) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
