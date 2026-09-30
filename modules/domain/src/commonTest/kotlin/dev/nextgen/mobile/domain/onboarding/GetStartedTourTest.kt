package dev.nextgen.mobile.domain.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetStartedTourTest {
    @Test
    fun introduction_moves_through_all_six_product_sections() {
        var state = GetStartedTourState()

        assertEquals("WELCOME", state.step.name)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals("ORGANIZE", state.step.name)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals("REVIEW", state.step.name)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals(GetStartedTourStep.ASSISTANCE, state.step)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals(GetStartedTourStep.PORTABILITY, state.step)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals(GetStartedTourStep.READY, state.step)
        state = state.reduce(GetStartedTourEvent.Next)

        assertTrue(state.isComplete)
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
    fun introduction_is_field_neutral_and_completes_after_six_sections() {
        var state = GetStartedTourState()
        assertEquals(GetStartedTourStep.WELCOME, state.step)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals(GetStartedTourStep.ORGANIZE, state.step)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals(GetStartedTourStep.REVIEW, state.step)
        assertTrue(state.canContinue)
        repeat(3) { state = state.reduce(GetStartedTourEvent.Next) }
        assertEquals(GetStartedTourStep.READY, state.step)
        state = state.reduce(GetStartedTourEvent.Next)

        assertTrue(state.isComplete)
        assertEquals(GetStartedTourStep.READY, state.step)
        assertEquals(state, state.reduce(GetStartedTourEvent.Next))
        assertFalse(GetStartedTourState().isComplete)
    }

    @Test
    fun back_navigation_returns_to_the_previous_walkthrough_section() {
        val state = GetStartedTourState()
            .reduce(GetStartedTourEvent.Next)
            .reduce(GetStartedTourEvent.Next)

        assertEquals(GetStartedTourStep.REVIEW, state.step)
        assertEquals(GetStartedTourStep.ORGANIZE, state.reduce(GetStartedTourEvent.Back).step)
        assertEquals(GetStartedTourStep.WELCOME, GetStartedTourState().reduce(GetStartedTourEvent.Back).step)
    }
}
