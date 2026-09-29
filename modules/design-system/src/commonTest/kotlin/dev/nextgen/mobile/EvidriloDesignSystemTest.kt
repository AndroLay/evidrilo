package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.pow
import dev.nextgen.mobile.design.resources.Res
import dev.nextgen.mobile.design.resources.evidriloLogoDrawable
import dev.nextgen.mobile.design.resources.evidriloLoadingLogoDrawable
import dev.nextgen.mobile.design.resources.evidrilo_logo
import dev.nextgen.mobile.design.resources.evidrilo_loading_logo

class EvidriloDesignSystemTest {
    @Test
    fun primaryButtonUsesReadableContentWhenDisabled() {
        assertEquals(EvidriloColors.White, evidriloPrimaryButtonContentColor(enabled = true))
        assertEquals(EvidriloColors.Slate, evidriloPrimaryButtonContentColor(enabled = false))
    }

    @Test
    fun choiceSurfacesPairThemeAwareContainersWithReadableContent() {
        val previousScheme = evidriloActiveColors
        try {
            listOf(EvidriloLightColors, EvidriloDarkColors).forEach { scheme ->
                evidriloActiveColors = scheme
                assertTrue(contrastRatio(scheme.white, scheme.primaryAction) >= 4.5)
                assertTrue(contrastRatio(scheme.cobalt, scheme.card) >= 4.5)
                assertTrue(contrastRatio(scheme.cobalt, scheme.tint) >= 4.5)

                val unselected = evidriloChoiceColors(selected = false)
                assertEquals(scheme.card, unselected.container)
                assertEquals(scheme.ink, unselected.content)
                assertTrue(contrastRatio(unselected.content, unselected.container) >= 4.5)

                val selected = evidriloChoiceColors(selected = true)
                assertEquals(scheme.tint, selected.container)
                assertEquals(scheme.ink, selected.content)
                assertTrue(contrastRatio(selected.content, selected.container) >= 4.5)
            }
        } finally {
            evidriloActiveColors = previousScheme
        }
    }

    private fun contrastRatio(foreground: androidx.compose.ui.graphics.Color, background: androidx.compose.ui.graphics.Color): Double {
        fun luminance(color: androidx.compose.ui.graphics.Color): Double {
            fun linear(channel: Float): Double {
                val normalized = channel.toDouble()
                return if (normalized <= 0.04045) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
        }

        val values = listOf(luminance(foreground), luminance(background)).sortedDescending()
        return (values[0] + 0.05) / (values[1] + 0.05)
    }

    @Test
    fun brandMarkUsesTheApprovedSharedLogoResource() {
        assertEquals(Res.drawable.evidrilo_logo, evidriloLogoDrawable)
    }

    @Test
    fun loadingScreenUsesTheApprovedFullBlueLogoAsset() {
        assertEquals(Res.drawable.evidrilo_loading_logo, evidriloLoadingLogoDrawable)
    }
}
