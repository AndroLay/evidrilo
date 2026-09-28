package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloLoadingScreenTest {
    @Test
    fun app_bootstrap_loading_uses_the_brand_and_does_not_offer_back_navigation() {
        val presentation = evidriloLoadingPresentation(EvidriloLoadingMode.APP_BOOTSTRAP)

        assertEquals("Evidrilo", presentation.title)
        assertEquals("Preparing your evidence workspace…", presentation.message)
        assertEquals("Loading Evidrilo", presentation.accessibilityLabel)
        assertFalse(presentation.canGoBack)
    }

    @Test
    fun network_page_loading_explains_the_wait_and_allows_safe_back_navigation() {
        val presentation = evidriloLoadingPresentation(EvidriloLoadingMode.NETWORK_PAGE)

        assertEquals("Connecting securely", presentation.title)
        assertEquals("This page needs a network connection. Your local work stays safe.", presentation.message)
        assertEquals("Loading network page", presentation.accessibilityLabel)
        assertTrue(presentation.canGoBack)
    }

    @Test
    fun logo_loading_motion_loops_across_the_configured_scale_range() {
        assertEquals(0.92f, EvidriloLoadingLogoScaleMin)
        assertEquals(1.08f, EvidriloLoadingLogoScaleMax)
        assertTrue(EvidriloLoadingLogoScaleMin < 1f)
        assertTrue(EvidriloLoadingLogoScaleMax > 1f)
    }
}
