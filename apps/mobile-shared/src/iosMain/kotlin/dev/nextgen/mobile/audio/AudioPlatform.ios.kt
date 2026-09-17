package dev.nextgen.mobile.audio

import dev.nextgen.mobile.resources.Res
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesisVoiceQualityDefault
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSURL
import platform.darwin.NSObject

private const val NARRATION_ENABLED_KEY = "evidrilo.audio.narration.enabled.v1"
private const val EFFECTS_ENABLED_KEY = "evidrilo.audio.effects.enabled.v1"

@OptIn(ExperimentalForeignApi::class)
internal actual fun createPlatformAudioEngine(): AudioEngine = IosAudioEngine()

internal actual fun createAudioSettingsStore(): AudioSettingsStore = IosAudioSettingsStore()

private class IosAudioSettingsStore : AudioSettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): AudioSettings = runCatching {
        AudioSettings(
            narrationEnabled = defaults.objectForKey(NARRATION_ENABLED_KEY) as? Boolean ?: true,
            effectsEnabled = defaults.objectForKey(EFFECTS_ENABLED_KEY) as? Boolean ?: true,
        )
    }.getOrElse { AudioSettings() }

    override fun save(settings: AudioSettings): LocalStorageWriteResult = runCatching {
        defaults.setBool(settings.narrationEnabled, forKey = NARRATION_ENABLED_KEY)
        defaults.setBool(settings.effectsEnabled, forKey = EFFECTS_ENABLED_KEY)
        LocalStorageWriteResult.SAVED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}

@OptIn(ExperimentalForeignApi::class)
private class IosAudioEngine : AudioEngine {
    private val speechSynthesizer = AVSpeechSynthesizer()
    private val playerDelegate = IosAudioPlayerDelegate { player, successfully ->
        handleAudioPlayerDidFinish(player, successfully)
    }
    private val speechDelegate = IosSpeechDelegate(
        onStart = { handleSpeechStarted() },
        onFinish = { handleSpeechFinished() },
        onCancel = { handleSpeechCancelled() },
        onPause = { handleSpeechPaused() },
        onContinue = { handleSpeechContinued() },
    )
    private var activeRequestId: Long? = null
    private var activeCallback: ((AudioEngineEvent) -> Unit)? = null
    private var activePlayer: AVAudioPlayer? = null
    private var activePlayerRequestId: Long? = null
    private var activeSpeechRequestId: Long? = null
    private var speechChunks: List<String> = emptyList()
    private var speechChunkIndex = 0
    private var activeSpeechVoice: AVSpeechSynthesisVoice? = null
    private var closed = false

    init {
        speechSynthesizer.delegate = speechDelegate
    }

    override fun playAsset(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        if (!begin(requestId, onEvent)) return
        playBundled(resourceName, requestId)
    }

    override fun speakOffline(
        text: String,
        localeTag: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        if (!begin(requestId, onEvent)) return
        val requestedLanguage = localeTag.substringBefore('-')
        val availableVoices = AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .filter { it.quality == AVSpeechSynthesisVoiceQualityDefault }
        val voice = availableVoices
            .firstOrNull { it.language == localeTag }
            ?: availableVoices.firstOrNull {
                it.language.substringBefore('-') == requestedLanguage
            }
        if (voice == null) {
            finish(requestId, AudioEngineEvent.Unavailable)
            return
        }

        activeSpeechRequestId = requestId
        speechChunks = splitOfflineSpeech(text)
        speechChunkIndex = 0
        activeSpeechVoice = voice
        if (speechChunks.isEmpty() || !speakCurrentChunk()) {
            if (isActive(requestId)) {
                finish(requestId, AudioEngineEvent.Unavailable)
            }
        }
    }

    override fun playEffect(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        if (!begin(requestId, onEvent)) return
        playBundled(resourceName, requestId)
    }

    override fun pause(requestId: Long) {
        if (!isActive(requestId)) return
        val player = activePlayer
        if (player != null) {
            player.pause()
            activeCallback?.invoke(AudioEngineEvent.Paused)
            return
        }
        if (activeSpeechRequestId == requestId && speechSynthesizer.isSpeaking()) {
            if (speechSynthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryWord)) {
                activeCallback?.invoke(AudioEngineEvent.Paused)
            }
        }
    }

    override fun resume(requestId: Long) {
        if (!isActive(requestId)) return
        val player = activePlayer
        if (player != null) {
            if (player.play()) activeCallback?.invoke(AudioEngineEvent.Resumed)
            return
        }
        if (activeSpeechRequestId == requestId && speechSynthesizer.continueSpeaking()) {
            activeCallback?.invoke(AudioEngineEvent.Resumed)
        }
    }

    override fun stop(requestId: Long) {
        if (activeRequestId == requestId) stopActive()
    }

    override fun close() {
        closed = true
        stopActive()
        speechSynthesizer.delegate = null
        playerDelegate.close()
        speechDelegate.close()
    }

    private fun handleAudioPlayerDidFinish(
        player: AVAudioPlayer,
        successfully: Boolean,
    ) {
        val requestId = activePlayerRequestId
        if (requestId == null || activePlayer !== player || !isActive(requestId)) return
        activePlayer = null
        activePlayerRequestId = null
        player.delegate = null
        finish(
            requestId,
            if (successfully) AudioEngineEvent.Completed else AudioEngineEvent.Failed("Audio playback failed."),
        )
    }

    private fun handleSpeechStarted() {
        activeSpeechRequestId?.let { requestId ->
            if (isActive(requestId)) activeCallback?.invoke(AudioEngineEvent.Started)
        }
    }

    private fun handleSpeechFinished() {
        activeSpeechRequestId?.let { requestId ->
            if (isActive(requestId)) {
                if (speechChunkIndex + 1 < speechChunks.size) {
                    speechChunkIndex += 1
                    speakCurrentChunk()
                } else {
                    finish(requestId, AudioEngineEvent.Completed)
                }
            }
        }
    }

    private fun handleSpeechCancelled() {
        activeSpeechRequestId?.let { requestId ->
            if (isActive(requestId)) {
                activeSpeechRequestId = null
                finish(requestId, AudioEngineEvent.Interrupted)
            }
        }
    }

    private fun handleSpeechPaused() {
        activeSpeechRequestId?.let { requestId ->
            if (isActive(requestId)) activeCallback?.invoke(AudioEngineEvent.Paused)
        }
    }

    private fun handleSpeechContinued() {
        activeSpeechRequestId?.let { requestId ->
            if (isActive(requestId)) activeCallback?.invoke(AudioEngineEvent.Resumed)
        }
    }

    private fun playBundled(resourceName: String, requestId: Long) {
        val url = resourceUrl(resourceName)
        if (url == null) {
            finish(requestId, AudioEngineEvent.Failed("Bundled audio is unavailable."))
            return
        }
        runCatching {
            val player = AVAudioPlayer(contentsOfURL = url, error = null)
            player.delegate = playerDelegate
            activePlayer = player
            activePlayerRequestId = requestId
            player.prepareToPlay()
            check(player.play()) { "Audio playback failed" }
        }.onFailure {
            activePlayer?.let {
                it.delegate = null
                it.stop()
            }
            activePlayer = null
            activePlayerRequestId = null
            finish(requestId, AudioEngineEvent.Failed("Audio playback failed."))
        }
    }

    private fun speakCurrentChunk(): Boolean {
        val requestId = activeSpeechRequestId ?: return false
        val voice = activeSpeechVoice ?: return false
        val chunk = speechChunks.getOrNull(speechChunkIndex)
        if (chunk.isNullOrBlank()) {
            finish(requestId, AudioEngineEvent.Unavailable)
            return false
        }

        val utterance = AVSpeechUtterance(string = chunk)
        utterance.voice = voice
        return runCatching {
            speechSynthesizer.speakUtterance(utterance)
        }.onFailure {
            finish(requestId, AudioEngineEvent.Failed("Offline speech failed."))
        }.isSuccess
    }

    private fun resourceUrl(resourceName: String): NSURL? {
        val normalizedName = normalizeResourceName(resourceName) ?: return null
        return runCatching {
            NSURL(string = Res.getUri("files/$normalizedName"))
        }.getOrNull()
            ?: NSBundle.mainBundle.pathForResource(
                "composeResources.dev.nextgen.mobile.resources/files/$normalizedName",
                ofType = null,
            )?.let(NSURL::fileURLWithPath)
    }

    private fun normalizeResourceName(resourceName: String): String? {
        val withoutPrefix = resourceName.removePrefix("files/")
        if (
            withoutPrefix.isBlank() ||
            withoutPrefix.startsWith('/') ||
            withoutPrefix.contains('\\') ||
            withoutPrefix.split('/').any { it.isBlank() || it == "." || it == ".." }
        ) return null
        return withoutPrefix.takeIf { it.startsWith("audio/") }
    }

    private fun begin(
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ): Boolean {
        if (closed) {
            onEvent(AudioEngineEvent.Unavailable)
            return false
        }
        stopActive()
        activeRequestId = requestId
        activeCallback = onEvent
        return true
    }

    private fun finish(requestId: Long, event: AudioEngineEvent) {
        if (!isActive(requestId)) return
        val callback = activeCallback
        activeRequestId = null
        activeCallback = null
        activePlayerRequestId = null
        activeSpeechRequestId = null
        speechChunks = emptyList()
        speechChunkIndex = 0
        activeSpeechVoice = null
        callback?.invoke(event)
    }

    private fun stopActive() {
        activeRequestId = null
        activeCallback = null
        activePlayerRequestId = null
        activeSpeechRequestId = null
        speechChunks = emptyList()
        speechChunkIndex = 0
        activeSpeechVoice = null
        activePlayer?.let {
            it.delegate = null
            it.stop()
        }
        activePlayer = null
        speechSynthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    private fun isActive(requestId: Long): Boolean = !closed && activeRequestId == requestId
}

@OptIn(ExperimentalForeignApi::class)
private class IosAudioPlayerDelegate(
    private var onFinish: ((AVAudioPlayer, Boolean) -> Unit)?,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(
        player: AVAudioPlayer,
        successfully: Boolean,
    ) {
        onFinish?.invoke(player, successfully)
    }

    fun close() {
        onFinish = null
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosSpeechDelegate(
    private var onStart: (() -> Unit)?,
    private var onFinish: (() -> Unit)?,
    private var onCancel: (() -> Unit)?,
    private var onPause: (() -> Unit)?,
    private var onContinue: (() -> Unit)?,
) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didStartSpeechUtterance: AVSpeechUtterance,
    ) {
        onStart?.invoke()
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didFinishSpeechUtterance: AVSpeechUtterance,
    ) {
        onFinish?.invoke()
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didCancelSpeechUtterance: AVSpeechUtterance,
    ) {
        onCancel?.invoke()
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didPauseSpeechUtterance: AVSpeechUtterance,
    ) {
        onPause?.invoke()
    }

    @ObjCSignatureOverride
    override fun speechSynthesizer(
        synthesizer: AVSpeechSynthesizer,
        didContinueSpeechUtterance: AVSpeechUtterance,
    ) {
        onContinue?.invoke()
    }

    fun close() {
        onStart = null
        onFinish = null
        onCancel = null
        onPause = null
        onContinue = null
    }
}
