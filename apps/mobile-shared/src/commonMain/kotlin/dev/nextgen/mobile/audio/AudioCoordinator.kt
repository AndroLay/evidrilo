package dev.nextgen.mobile.audio

internal class AudioCoordinator(
    private val engine: AudioEngine,
    private val catalog: AudioCatalog = AudioCatalog(),
    private val localeTag: String = "en-US",
    initialSettings: AudioSettings = AudioSettings(),
    private val onStateChanged: (AudioPlaybackState) -> Unit = {},
) {
    var state: AudioPlaybackState = AudioPlaybackState.Idle
        private set(value) {
            field = value
            onStateChanged(value)
        }

    var settings: AudioSettings = initialSettings
        private set

    private var nextRequestId = 0L
    private var activeRequest: ActiveRequest? = null
    private var lastRequest: AudioRequest? = null

    fun updateSettings(settings: AudioSettings) {
        this.settings = settings
        if (
            (!settings.narrationEnabled && activeRequest?.channel == AudioChannel.NARRATION) ||
            (!settings.effectsEnabled && activeRequest?.channel == AudioChannel.EFFECT)
        ) {
            stop()
        }
    }

    fun playNarration(
        id: AudioNarrationId,
        sourceText: String,
    ) {
        if (!settings.narrationEnabled) return
        val request = AudioRequest.Narration(id, sourceText)
        lastRequest = request
        start(request)
    }

    fun speakDynamic(text: String) {
        if (!settings.narrationEnabled || text.isBlank()) return
        val request = AudioRequest.Dynamic(text)
        lastRequest = request
        start(request)
    }

    fun playEffect(id: AudioEffectId) {
        if (!settings.effectsEnabled) return
        val request = AudioRequest.Effect(id)
        lastRequest = request
        start(request)
    }

    fun pauseOrResume() {
        val active = activeRequest ?: return
        when (state) {
            is AudioPlaybackState.Playing -> engine.pause(active.requestId)
            is AudioPlaybackState.Paused -> engine.resume(active.requestId)
            else -> Unit
        }
    }

    fun replay() {
        lastRequest?.let { request ->
            val enabled = when (request.channel) {
                AudioChannel.NARRATION -> settings.narrationEnabled
                AudioChannel.EFFECT -> settings.effectsEnabled
            }
            if (enabled) start(request)
        }
    }

    fun stop() {
        activeRequest?.let { engine.stop(it.requestId) }
        activeRequest = null
        state = AudioPlaybackState.Idle
    }

    fun close() {
        stop()
        engine.close()
    }

    private fun start(request: AudioRequest) {
        activeRequest?.let { engine.stop(it.requestId) }

        val requestId = ++nextRequestId
        val active = ActiveRequest(requestId, request.channel)
        activeRequest = active
        state = AudioPlaybackState.Loading(requestId, request.channel)

        val callback: (AudioEngineEvent) -> Unit = callback@{ event ->
            if (activeRequest?.requestId != requestId) return@callback
            state = event.toPlaybackState(request.channel, requestId)
            if (event is AudioEngineEvent.Completed ||
                event is AudioEngineEvent.Interrupted ||
                event is AudioEngineEvent.Unavailable ||
                event is AudioEngineEvent.Failed
            ) {
                activeRequest = null
            }
        }

        when (request) {
            is AudioRequest.Narration -> {
                val clip = catalog.narrationFor(request.id, request.sourceText)
                if (clip != null) {
                    engine.playAsset(clip.resourceName, requestId, callback)
                } else {
                    engine.speakOffline(request.sourceText, localeTag, requestId, callback)
                }
            }

            is AudioRequest.Dynamic -> engine.speakOffline(request.text, localeTag, requestId, callback)
            is AudioRequest.Effect -> {
                val effect = catalog.effectFor(request.id)
                if (effect != null) {
                    engine.playEffect(effect.resourceName, requestId, callback)
                } else {
                    activeRequest = null
                    state = AudioPlaybackState.Unavailable
                }
            }
        }
    }

    private fun AudioEngineEvent.toPlaybackState(
        channel: AudioChannel,
        requestId: Long,
    ): AudioPlaybackState = when (this) {
        AudioEngineEvent.Started -> AudioPlaybackState.Playing(requestId, channel)
        AudioEngineEvent.Paused -> AudioPlaybackState.Paused(requestId, channel)
        AudioEngineEvent.Resumed -> AudioPlaybackState.Playing(requestId, channel)
        AudioEngineEvent.Completed -> if (channel == AudioChannel.EFFECT) {
            AudioPlaybackState.Idle
        } else {
            AudioPlaybackState.Completed
        }
        AudioEngineEvent.Interrupted -> AudioPlaybackState.Idle
        AudioEngineEvent.Unavailable -> AudioPlaybackState.Unavailable
        is AudioEngineEvent.Failed -> AudioPlaybackState.Failed(message)
    }

    private data class ActiveRequest(
        val requestId: Long,
        val channel: AudioChannel,
    )

    private sealed interface AudioRequest {
        val channel: AudioChannel

        data class Narration(
            val id: AudioNarrationId,
            val sourceText: String,
        ) : AudioRequest {
            override val channel: AudioChannel = AudioChannel.NARRATION
        }

        data class Dynamic(val text: String) : AudioRequest {
            override val channel: AudioChannel = AudioChannel.NARRATION
        }

        data class Effect(val id: AudioEffectId) : AudioRequest {
            override val channel: AudioChannel = AudioChannel.EFFECT
        }
    }
}
