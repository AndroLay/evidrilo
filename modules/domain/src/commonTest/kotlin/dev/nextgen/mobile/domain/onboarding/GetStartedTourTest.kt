package dev.nextgen.mobile.domain.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetStartedTourTest {
    @Test
    fun introduction_visits_each_current_product_section_in_order() {
        val expected = listOf("WELCOME", "ORGANIZE", "WORKSPACE", "REVIEW", "GRAPH", "PRACTICE", "ASSISTANCE", "PORTABILITY", "READY")
        var state = GetStartedTourState()
        for (section in expected) {
            assertEquals(section, state.step.name)
            assertTrue(state.canContinue)
            assertFalse(state.isComplete)
            state = state.reduce(GetStartedTourEvent.Next)
        }
        assertTrue(state.isComplete)
        assertFalse(state.canContinue)
        assertEquals(GetStartedTourStep.READY, state.step)
        assertEquals(state, state.reduce(GetStartedTourEvent.Next))
    }

    @Test
    fun onboarding_status_migrates_legacy_completion_and_keeps_skip_distinct() {
        assertEquals(GetStartedStatus.NOT_STARTED, GetStartedStatus.fromStorage(null, legacyCompleted = false))
        assertEquals(GetStartedStatus.COMPLETED, GetStartedStatus.fromStorage(null, legacyCompleted = true))
        assertEquals(GetStartedStatus.SKIPPED, GetStartedStatus.fromStorage("skipped", legacyCompleted = false))
        assertEquals(GetStartedStatus.COMPLETED, GetStartedStatus.fromStorage("completed", legacyCompleted = false))
        assertNull(GetStartedStatus.fromStorage("future-value", legacyCompleted = false))
    }

    @Test
    fun introduction_is_field_neutral_and_completion_does_not_reopen_the_tour() {
        var state = GetStartedTourState()
        repeat(8) { state = state.reduce(GetStartedTourEvent.Next) }
        assertEquals(GetStartedTourStep.READY, state.step)
        assertFalse(state.isComplete)
        state = state.reduce(GetStartedTourEvent.Next)
        assertTrue(state.isComplete)
        assertEquals(state, state.reduce(GetStartedTourEvent.Back))
        assertFalse(GetStartedTourState().isComplete)
    }

    @Test
    fun back_navigation_returns_to_the_previous_walkthrough_section() {
        val state = GetStartedTourState()
            .reduce(GetStartedTourEvent.Next)
            .reduce(GetStartedTourEvent.Next)

        assertEquals(GetStartedTourStep.WORKSPACE, state.step)
        assertEquals(GetStartedTourStep.ORGANIZE, state.reduce(GetStartedTourEvent.Back).step)
        assertEquals(GetStartedTourStep.WELCOME, GetStartedTourState().reduce(GetStartedTourEvent.Back).step)
    }
}
