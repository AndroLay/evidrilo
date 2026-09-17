package dev.nextgen.mobile.audio

import dev.nextgen.mobile.storage.LocalStorageWriteResult

internal interface AudioSettingsStore {
    fun load(): AudioSettings

    fun save(settings: AudioSettings): LocalStorageWriteResult
}

internal class NoopAudioSettingsStore : AudioSettingsStore {
    override fun load(): AudioSettings = AudioSettings()

    override fun save(settings: AudioSettings): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE
}

internal class UnavailableAudioEngine : AudioEngine {
    override fun playAsset(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        onEvent(AudioEngineEvent.Unavailable)
    }

    override fun speakOffline(
        text: String,
        localeTag: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        onEvent(AudioEngineEvent.Unavailable)
    }

    override fun playEffect(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        onEvent(AudioEngineEvent.Unavailable)
    }

    override fun pause(requestId: Long) = Unit

    override fun resume(requestId: Long) = Unit

    override fun stop(requestId: Long) = Unit

    override fun close() = Unit
}

internal expect fun createAudioSettingsStore(): AudioSettingsStore

internal expect fun createPlatformAudioEngine(): AudioEngine
