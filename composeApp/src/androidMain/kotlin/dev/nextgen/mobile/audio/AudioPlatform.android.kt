package dev.nextgen.mobile.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

private const val COMPOSE_RESOURCE_PREFIX = "composeResources/dev.nextgen.mobile.resources/files/"
private const val AUDIO_CACHE_DIRECTORY = "evidrilo-audio-v1"
private const val MAX_CACHED_FILE_BYTES = 8L * 1024L * 1024L

internal actual fun createPlatformAudioEngine(): AudioEngine = AndroidAudioStorage.createEngine()

internal class AndroidAudioEngine(
    context: Context,
) : AudioEngine {
    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cacheExecutor = Executors.newSingleThreadExecutor()
    private val cacheDirectory = File(applicationContext.cacheDir, AUDIO_CACHE_DIRECTORY)
    private val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var activeRequestId: Long? = null
    private var activeCallback: ((AudioEngineEvent) -> Unit)? = null
    private var activePlayer: MediaPlayer? = null
    private var pendingSpeech: PendingSpeech? = null
    private var activeSpeechRequestId: Long? = null
    private var speechChunks: List<String> = emptyList()
    private var speechChunkIndex = 0
    private var speechPaused = false
    private var ttsReady = false
    private var ttsInitializationFailed = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusOwnerRequestId: Long? = null
    private var audioFocusHeld = false
    private val textToSpeech: TextToSpeech? = runCatching {
        TextToSpeech(applicationContext) { status ->
            runOnMain { handleTextToSpeechReady(status) }
        }
    }.getOrNull()

    @Volatile
    private var closed = false

    init {
        textToSpeech?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceId?.let { runOnMain { handleSpeechEvent(it, AudioEngineEvent.Started) } }
                }

                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { runOnMain { handleSpeechEvent(it, AudioEngineEvent.Completed) } }
                }

                override fun onError(utteranceId: String?) {
                    utteranceId?.let {
                        runOnMain {
                            handleSpeechEvent(it, AudioEngineEvent.Failed("Offline speech failed."))
                        }
                    }
                }
            },
        )
    }

    override fun playAsset(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        if (!begin(requestId, onEvent)) return
        cacheExecutor.execute {
            val cachedFile = runCatching { cacheAsset(resourceName) }.getOrNull()
            runOnMain {
                if (!isActive(requestId)) return@runOnMain
                if (cachedFile == null) {
                    finish(requestId, AudioEngineEvent.Failed("Bundled audio is unavailable."))
                } else {
                    preparePlayer(cachedFile, requestId, effect = false)
                }
            }
        }
    }

    override fun speakOffline(
        text: String,
        localeTag: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        runOnMain {
            if (!begin(requestId, onEvent)) return@runOnMain
            when {
                textToSpeech == null || ttsInitializationFailed ->
                    finish(requestId, AudioEngineEvent.Unavailable)

                !ttsReady -> pendingSpeech = PendingSpeech(requestId, text, localeTag)
                else -> speakWhenReady(PendingSpeech(requestId, text, localeTag))
            }
        }
    }

    override fun playEffect(
        resourceName: String,
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ) {
        if (!begin(requestId, onEvent)) return
        cacheExecutor.execute {
            val cachedFile = runCatching { cacheAsset(resourceName) }.getOrNull()
            runOnMain {
                if (!isActive(requestId)) return@runOnMain
                if (cachedFile == null) {
                    finish(requestId, AudioEngineEvent.Failed("Bundled sound effect is unavailable."))
                } else {
                    preparePlayer(cachedFile, requestId, effect = true)
                }
            }
        }
    }

    override fun pause(requestId: Long) {
        runOnMain {
            if (!isActive(requestId)) return@runOnMain
            val player = activePlayer
            if (player != null) {
                if (player.isPlaying) {
                    runCatching { player.pause() }
                        .onSuccess { activeCallback?.invoke(AudioEngineEvent.Paused) }
                }
                return@runOnMain
            }
            val speech = textToSpeech
            if (activeSpeechRequestId == requestId && speech?.isSpeaking == true) {
                speechPaused = true
                runCatching { speech.stop() }
                    .onSuccess { activeCallback?.invoke(AudioEngineEvent.Paused) }
            }
        }
    }

    override fun resume(requestId: Long) {
        runOnMain {
            if (!isActive(requestId)) return@runOnMain
            val player = activePlayer
            if (player != null) {
                runCatching { player.start() }
                    .onSuccess { activeCallback?.invoke(AudioEngineEvent.Resumed) }
                return@runOnMain
            }
            if (activeSpeechRequestId == requestId && speechPaused) {
                speechPaused = false
                if (speakCurrentChunk()) {
                    activeCallback?.invoke(AudioEngineEvent.Resumed)
                }
            }
        }
    }

    override fun stop(requestId: Long) {
        runOnMain {
            if (activeRequestId == requestId) stopActive()
        }
    }

    override fun close() {
        closed = true
        cacheExecutor.shutdownNow()
        runOnMain {
            stopActive()
            runCatching { textToSpeech?.shutdown() }
        }
    }

    private fun begin(
        requestId: Long,
        onEvent: (AudioEngineEvent) -> Unit,
    ): Boolean {
        if (closed) {
            runOnMain { onEvent(AudioEngineEvent.Unavailable) }
            return false
        }
        stopActive()
        activeRequestId = requestId
        activeCallback = onEvent
        return true
    }

    private fun preparePlayer(
        file: File,
        requestId: Long,
        effect: Boolean,
    ) {
        if (!isActive(requestId)) return
        if (!acquireAudioFocus(requestId, effect)) {
            finish(requestId, AudioEngineEvent.Unavailable)
            return
        }
        val player = MediaPlayer()
        activePlayer = player
        try {
            player.setAudioAttributes(audioAttributes(effect))
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { preparedPlayer ->
                if (!isActive(requestId) || activePlayer !== preparedPlayer) {
                    releasePlayer(preparedPlayer)
                    return@setOnPreparedListener
                }
                val callback = activeCallback
                runCatching { preparedPlayer.start() }
                    .onSuccess { callback?.invoke(AudioEngineEvent.Started) }
                    .onFailure {
                        releasePlayer(preparedPlayer)
                        finish(requestId, AudioEngineEvent.Failed("Audio playback failed."))
                    }
            }
            player.setOnCompletionListener { completedPlayer ->
                if (!isActive(requestId) || activePlayer !== completedPlayer) {
                    releasePlayer(completedPlayer)
                    return@setOnCompletionListener
                }
                releasePlayer(completedPlayer)
                finish(requestId, AudioEngineEvent.Completed)
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                if (activePlayer === failedPlayer) releasePlayer(failedPlayer)
                if (isActive(requestId)) finish(requestId, AudioEngineEvent.Failed("Audio playback failed."))
                true
            }
            player.prepareAsync()
        } catch (_: RuntimeException) {
            releasePlayer(player)
            finish(requestId, AudioEngineEvent.Failed("Audio playback failed."))
        }
    }

    private fun handleTextToSpeechReady(status: Int) {
        if (closed) return
        if (status != TextToSpeech.SUCCESS) {
            ttsInitializationFailed = true
            pendingSpeech?.let { pending ->
                pendingSpeech = null
                finish(pending.requestId, AudioEngineEvent.Unavailable)
            }
            return
        }
        ttsReady = true
        pendingSpeech?.let { pending ->
            pendingSpeech = null
            if (isActive(pending.requestId)) speakWhenReady(pending)
        }
    }

    private fun speakWhenReady(pending: PendingSpeech) {
        val speech = textToSpeech
        if (!isActive(pending.requestId) || speech == null) return
        val requestedLocale = Locale.forLanguageTag(pending.localeTag)
        val voice = speech.voices.orEmpty()
            .asSequence()
            .filterNot { it.isNetworkConnectionRequired }
            .sortedBy { if (it.locale == requestedLocale) 0 else 1 }
            .firstOrNull { it.locale.language == requestedLocale.language }
        if (voice == null) {
            finish(pending.requestId, AudioEngineEvent.Unavailable)
            return
        }
        if (!acquireAudioFocus(pending.requestId, effect = false)) {
            finish(pending.requestId, AudioEngineEvent.Unavailable)
            return
        }

        activeSpeechRequestId = pending.requestId
        speechChunks = splitOfflineSpeech(pending.text)
        speechChunkIndex = 0
        speechPaused = false
        runCatching {
            speech.voice = voice
        }.onFailure {
            finish(pending.requestId, AudioEngineEvent.Failed("Offline speech failed."))
        }
        speakCurrentChunk()
    }

    private fun handleSpeechEvent(utteranceId: String, event: AudioEngineEvent) {
        val speechUtterance = parseSpeechUtteranceId(utteranceId) ?: return
        val requestId = speechUtterance.requestId
        if (!isActive(requestId) || activeSpeechRequestId != requestId) return
        if (speechUtterance.chunkIndex != speechChunkIndex || speechPaused) return
        when (event) {
            AudioEngineEvent.Completed -> {
                if (speechChunkIndex + 1 < speechChunks.size) {
                    speechChunkIndex += 1
                    speakCurrentChunk()
                } else {
                    finish(requestId, AudioEngineEvent.Completed)
                }
            }

            is AudioEngineEvent.Failed -> finish(requestId, event)
            else -> activeCallback?.invoke(event)
        }
    }

    private fun speakCurrentChunk(): Boolean {
        val speech = textToSpeech ?: return false
        val requestId = activeSpeechRequestId ?: return false
        val chunk = speechChunks.getOrNull(speechChunkIndex)
        if (chunk.isNullOrBlank()) {
            finish(requestId, AudioEngineEvent.Unavailable)
            return false
        }

        val utteranceId = "evidrilo-audio-$requestId-$speechChunkIndex"
        val result = runCatching {
            speech.speak(chunk, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        }.getOrElse {
            finish(requestId, AudioEngineEvent.Failed("Offline speech failed."))
            return false
        }
        if (result == TextToSpeech.ERROR) {
            finish(requestId, AudioEngineEvent.Failed("Offline speech failed."))
            return false
        }
        return true
    }

    private fun parseSpeechUtteranceId(utteranceId: String): SpeechUtterance? {
        val raw = utteranceId.removePrefix("evidrilo-audio-")
        val separator = raw.lastIndexOf('-')
        if (separator <= 0 || separator == raw.lastIndex) return null
        val requestId = raw.substring(0, separator).toLongOrNull() ?: return null
        val chunkIndex = raw.substring(separator + 1).toIntOrNull() ?: return null
        return SpeechUtterance(requestId, chunkIndex)
    }

    private fun finish(requestId: Long, event: AudioEngineEvent) {
        if (!isActive(requestId)) return
        val callback = activeCallback
        val player = activePlayer
        activePlayer = null
        activeRequestId = null
        activeCallback = null
        pendingSpeech = null
        activeSpeechRequestId = null
        speechChunks = emptyList()
        speechChunkIndex = 0
        speechPaused = false
        player?.let(::releasePlayer)
        runCatching { textToSpeech?.stop() }
        releaseAudioFocus()
        callback?.invoke(event)
    }

    private fun stopActive() {
        activeRequestId = null
        activeCallback = null
        pendingSpeech = null
        activeSpeechRequestId = null
        speechChunks = emptyList()
        speechChunkIndex = 0
        speechPaused = false
        activePlayer?.let(::releasePlayer)
        activePlayer = null
        runCatching { textToSpeech?.stop() }
        releaseAudioFocus()
    }

    private fun handleAudioFocusChange(requestId: Long, change: Int) {
        if (change != AudioManager.AUDIOFOCUS_LOSS &&
            change != AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) return
        if (activeRequestId != requestId || audioFocusOwnerRequestId != requestId) return
        finish(requestId, AudioEngineEvent.Interrupted)
    }

    private fun acquireAudioFocus(requestId: Long, effect: Boolean): Boolean {
        if (audioFocusHeld) return true
        val manager = audioManager ?: return true
        val focusGain = if (effect) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        } else {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        }
        val request = runCatching {
            AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(audioAttributes(effect))
                .setOnAudioFocusChangeListener { change ->
                    runOnMain { handleAudioFocusChange(requestId, change) }
                }
                .setAcceptsDelayedFocusGain(false)
                .build()
        }.getOrNull() ?: return false
        val granted = runCatching {
            manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        if (!granted) return false
        audioFocusRequest = request
        audioFocusOwnerRequestId = requestId
        audioFocusHeld = true
        return true
    }

    private fun releaseAudioFocus() {
        if (!audioFocusHeld) return
        audioManager?.let { manager ->
            audioFocusRequest?.let { request ->
                runCatching { manager.abandonAudioFocusRequest(request) }
            }
        }
        audioFocusRequest = null
        audioFocusOwnerRequestId = null
        audioFocusHeld = false
    }

    private fun audioAttributes(effect: Boolean): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(
                if (effect) {
                    AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                } else {
                    AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
                },
            )
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun releasePlayer(player: MediaPlayer) {
        runCatching { player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
        if (activePlayer === player) activePlayer = null
    }

    private fun isActive(requestId: Long): Boolean = !closed && activeRequestId == requestId

    private fun cacheAsset(resourceName: String): File {
        val normalizedName = normalizeResourceName(resourceName)
            ?: error("Invalid audio resource")
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs() && !cacheDirectory.isDirectory) {
            error("Audio cache is unavailable")
        }
        val extension = normalizedName.substringAfterLast('.', "audio")
        val cacheKey = sha256(normalizedName)
        val target = File(cacheDirectory, "$cacheKey.$extension")
        if (target.isFile && target.length() in 1..MAX_CACHED_FILE_BYTES) return target

        val temporary = File(cacheDirectory, ".$cacheKey.tmp")
        try {
            applicationContext.assets.open(COMPOSE_RESOURCE_PREFIX + normalizedName).use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_CACHED_FILE_BYTES) error("Audio asset exceeds the cache bound")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!temporary.renameTo(target)) error("Audio cache write failed")
            return target
        } finally {
            temporary.delete()
        }
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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private data class PendingSpeech(
        val requestId: Long,
        val text: String,
        val localeTag: String,
    )

    private data class SpeechUtterance(
        val requestId: Long,
        val chunkIndex: Int,
    )
}
