package dev.nextgen.mobile.audio

internal enum class AudioNarrationId {
    ONBOARDING,
    GUIDE,
    CASE_OBJECTIVE,
    CASE_FACT,
    FEEDBACK,
    REVISION,
    CHALLENGE,
    SUPPORT,
}

internal enum class AudioEffectId {
    SELECTION,
    SUCCESS,
    ERROR,
    CHALLENGE_REVEAL,
    PREMIUM_STATE,
}

internal enum class AudioChannel {
    NARRATION,
    EFFECT,
}

internal data class AudioSettings(
    val narrationEnabled: Boolean = true,
    val effectsEnabled: Boolean = true,
)

internal sealed interface AudioPlaybackState {
    data object Idle : AudioPlaybackState

    data class Loading(
        val requestId: Long,
        val channel: AudioChannel,
    ) : AudioPlaybackState

    data class Playing(
        val requestId: Long,
        val channel: AudioChannel,
    ) : AudioPlaybackState

    data class Paused(
        val requestId: Long,
        val channel: AudioChannel,
    ) : AudioPlaybackState

    data object Completed : AudioPlaybackState

    data object Unavailable : AudioPlaybackState

    data class Failed(val message: String) : AudioPlaybackState
}

internal sealed interface AudioEngineEvent {
    data object Started : AudioEngineEvent

    data object Paused : AudioEngineEvent

    data object Resumed : AudioEngineEvent

    data object Completed : AudioEngineEvent

    data object Interrupted : AudioEngineEvent

    data object Unavailable : AudioEngineEvent

    data class Failed(val message: String) : AudioEngineEvent
}

internal interface AudioEngine {
    fun playAsset(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    )

    fun speakOffline(
        text: String,
        localeTag: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    )

    fun playEffect(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    )

    fun pause(requestId: Long)

    fun resume(requestId: Long)

    fun stop(requestId: Long)

    fun close()
}
