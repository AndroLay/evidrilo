package dev.nextgen.mobile.audio

internal const val DEFAULT_OFFLINE_SPEECH_CHUNK_LENGTH = 1800

/**
 * Keeps native TTS inputs small enough for platform limits while preserving
 * every visible word. Sentence and word boundaries are preferred so chunking
 * sounds like a natural pause instead of a mid-word cut.
 */
internal fun splitOfflineSpeech(
    text: String,
    maxChunkLength: Int = DEFAULT_OFFLINE_SPEECH_CHUNK_LENGTH,
): List<String> {
    require(maxChunkLength > 0) { "maxChunkLength must be positive" }

    val normalized = text.replace(Regex("\\s+"), " ").trim()
    if (normalized.isEmpty()) return emptyList()
    if (normalized.length <= maxChunkLength) return listOf(normalized)

    val chunks = mutableListOf<String>()
    var start = 0
    while (start < normalized.length) {
        val remainingLength = normalized.length - start
        if (remainingLength <= maxChunkLength) {
            chunks += normalized.substring(start).trim()
            break
        }

        val limit = start + maxChunkLength
        val minimumBoundary = start + (maxChunkLength / 2)
        var cut = findSentenceBoundary(normalized, limit, minimumBoundary)
        if (cut == null) {
            val space = normalized.lastIndexOf(' ', limit - 1)
            cut = if (space >= minimumBoundary) space else limit
        }

        if (
            cut < normalized.length &&
            cut > start &&
            normalized[cut - 1].isHighSurrogate() &&
            normalized[cut].isLowSurrogate()
        ) {
            cut -= 1
        }

        val chunk = normalized.substring(start, cut).trim()
        if (chunk.isNotEmpty()) chunks += chunk
        start = cut
        while (start < normalized.length && normalized[start].isWhitespace()) start += 1
    }
    return chunks
}

private fun findSentenceBoundary(
    text: String,
    limit: Int,
    minimumBoundary: Int,
): Int? {
    for (index in limit - 1 downTo minimumBoundary) {
        if (text[index] == '.' || text[index] == '!' || text[index] == '?' || text[index] == ';') {
            return index + 1
        }
    }
    return null
}
