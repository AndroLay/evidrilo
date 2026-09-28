package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloOnboardingPresentationTest {
    @Test
    fun fresh_install_does_not_open_the_optional_guide_automatically() {
        assertFalse(evidriloOnboardingPresentation().isVisible)
    }

    @Test
    fun explicit_guide_request_opens_the_preview() {
        assertTrue(evidriloOnboardingPresentation(forceShow = true).isVisible)
    }

    @Test
    fun guide_copy_keeps_synthetic_demo_and_accountless_local_projects_distinct() {
        val presentation = evidriloOnboardingPresentation(forceShow = true)

        assertTrue(presentation.body.contains("synthetic", ignoreCase = true))
        assertTrue(presentation.body.contains("does not create a project", ignoreCase = true))
        assertTrue(
            presentation.freeBenefits.any {
                it.contains("local projects without an account", ignoreCase = true)
            },
        )
        assertFalse(
            presentation.freeBenefits.any {
                it.contains("account is required for real projects", ignoreCase = true)
            },
        )
    }
}
