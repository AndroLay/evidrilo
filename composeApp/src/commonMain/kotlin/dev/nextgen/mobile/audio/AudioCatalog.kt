package dev.nextgen.mobile.audio

internal data class AudioClipSpec(
    val id: AudioNarrationId,
    val sourceText: String,
    val resourceName: String,
)

internal data class AudioEffectSpec(
    val id: AudioEffectId,
    val resourceName: String,
)

internal class AudioCatalog(
    private val narration: Map<AudioNarrationId, List<AudioClipSpec>> = emptyMap(),
    private val effects: Map<AudioEffectId, AudioEffectSpec> = emptyMap(),
) {
    fun narrationFor(
        id: AudioNarrationId,
        sourceText: String,
    ): AudioClipSpec? = narration[id].orEmpty().firstOrNull { it.sourceText == sourceText }

    fun effectFor(id: AudioEffectId): AudioEffectSpec? = effects[id]
}

/**
 * The interaction catalog is intentionally explicit so a renamed asset cannot
 * silently change the meaning of a UI event. Narration supports multiple
 * reviewed variants for one semantic group and remains empty until a reviewed
 * recording is available, so the current app uses the offline TTS fallback.
 */
internal val EVIDRILO_AUDIO_CATALOG = AudioCatalog(
    effects = mapOf(
        AudioEffectId.SELECTION to AudioEffectSpec(
            id = AudioEffectId.SELECTION,
            resourceName = "audio/effects/selection.wav",
        ),
        AudioEffectId.SUCCESS to AudioEffectSpec(
            id = AudioEffectId.SUCCESS,
            resourceName = "audio/effects/success.wav",
        ),
        AudioEffectId.ERROR to AudioEffectSpec(
            id = AudioEffectId.ERROR,
            resourceName = "audio/effects/error.wav",
        ),
        AudioEffectId.CHALLENGE_REVEAL to AudioEffectSpec(
            id = AudioEffectId.CHALLENGE_REVEAL,
            resourceName = "audio/effects/challenge-reveal.wav",
        ),
        AudioEffectId.PREMIUM_STATE to AudioEffectSpec(
            id = AudioEffectId.PREMIUM_STATE,
            resourceName = "audio/effects/premium-state.wav",
        ),
    ),
)
