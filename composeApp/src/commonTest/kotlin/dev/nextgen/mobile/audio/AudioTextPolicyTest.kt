package dev.nextgen.mobile.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTextPolicyTest {
    @Test
    fun short_text_is_normalized_without_losing_visible_words() {
        assertEquals(
            listOf("Read this content aloud."),
            splitOfflineSpeech("  Read   this\ncontent aloud.  ", maxChunkLength = 80),
        )
    }

    @Test
    fun long_text_prefers_sentence_or_word_boundaries_and_preserves_content() {
        val source = "First observation is warm. Second observation is clear. Third observation is limited."
        val chunks = splitOfflineSpeech(source, maxChunkLength = 36)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 36 })
        assertEquals(source, chunks.joinToString(" "))
        assertTrue(chunks.first().endsWith('.'))
    }

    @Test
    fun long_unspaced_text_is_hard_bounded_without_empty_chunks() {
        val source = "x".repeat(91)
        val chunks = splitOfflineSpeech(source, maxChunkLength = 20)

        assertTrue(chunks.all { it.isNotEmpty() && it.length <= 20 })
        assertEquals(source, chunks.joinToString(separator = ""))
    }
}
