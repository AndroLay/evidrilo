package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun brandMarkUsesTheApprovedSharedLogoResource() {
        assertEquals(Res.drawable.evidrilo_logo, evidriloLogoDrawable)
    }

    @Test
    fun loadingScreenUsesTheApprovedFullBlueLogoAsset() {
        assertEquals(Res.drawable.evidrilo_loading_logo, evidriloLoadingLogoDrawable)
    }
}
