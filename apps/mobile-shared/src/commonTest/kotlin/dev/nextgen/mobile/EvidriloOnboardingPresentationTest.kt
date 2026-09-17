package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloOnboardingPresentationTest {
    @Test
    fun first_run_explains_the_free_core_before_premium() {
        val presentation = evidriloOnboardingPresentation(
            completed = false,
            hasSavedPractice = false,
        )

        assertTrue(presentation.isVisible)
        assertEquals("Learn from the evidence, one bounded case at a time.", presentation.title)
        assertEquals("Try the free case", presentation.primaryLabel)
        assertEquals("Skip introduction", presentation.secondaryLabel)
        assertTrue(presentation.freeBenefits.any { it.contains("unlimited", ignoreCase = true) })
        assertTrue(presentation.freeBenefits.any { it.contains("feedback", ignoreCase = true) })
    }

    @Test
    fun onboarding_is_not_shown_after_completion_or_when_saved_practice_exists() {
        assertFalse(
            evidriloOnboardingPresentation(completed = true, hasSavedPractice = false).isVisible,
        )
        assertFalse(
            evidriloOnboardingPresentation(completed = false, hasSavedPractice = true).isVisible,
        )
    }

    @Test
    fun guide_can_reopen_onboarding_without_erasing_saved_practice() {
        val presentation = evidriloOnboardingPresentation(
            completed = true,
            hasSavedPractice = true,
            forceShow = true,
        )

        assertTrue(presentation.isVisible)
    }

    @Test
    fun free_learning_policy_keeps_the_core_comfortable_without_a_usage_cap() {
        val policy = EVIDRILO_FREE_LEARNING_POLICY

        assertEquals(1, policy.caseCount)
        assertTrue(policy.localReplayUnlimited)
        assertTrue(policy.completeFeedback)
        assertEquals(1, policy.revisionsPerSession)
        assertTrue(policy.evidenceChallenge)
        assertTrue(policy.beforeAfterComparison)
    }
}
