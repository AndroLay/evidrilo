package dev.nextgen.mobile.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AudioCatalogTest {
    @Test
    fun bundled_effect_catalog_covers_every_interaction_effect() {
        AudioEffectId.entries.forEach { id ->
            val effect = EVIDRILO_AUDIO_CATALOG.effectFor(id)

            assertNotNull(effect)
            assertEquals(id, effect.id)
        }
    }

    @Test
    fun narration_catalog_selects_the_exact_source_from_multiple_variants() {
        val catalog = AudioCatalog(
            narration = mapOf(
                AudioNarrationId.CASE_OBJECTIVE to listOf(
                    AudioClipSpec(
                        id = AudioNarrationId.CASE_OBJECTIVE,
                        sourceText = "Free case copy.",
                        resourceName = "audio/narration/free-case.m4a",
                    ),
                    AudioClipSpec(
                        id = AudioNarrationId.CASE_OBJECTIVE,
                        sourceText = "Premium case copy.",
                        resourceName = "audio/narration/premium-case.m4a",
                    ),
                ),
            ),
        )

        val clip = catalog.narrationFor(
            id = AudioNarrationId.CASE_OBJECTIVE,
            sourceText = "Premium case copy.",
        )

        assertNotNull(clip)
        assertEquals("audio/narration/premium-case.m4a", clip.resourceName)
    }
}
