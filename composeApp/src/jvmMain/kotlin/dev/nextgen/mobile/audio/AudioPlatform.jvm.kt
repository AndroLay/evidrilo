package dev.nextgen.mobile.audio

internal actual fun createPlatformAudioEngine(): AudioEngine = UnavailableAudioEngine()

internal actual fun createAudioSettingsStore(): AudioSettingsStore = NoopAudioSettingsStore()
