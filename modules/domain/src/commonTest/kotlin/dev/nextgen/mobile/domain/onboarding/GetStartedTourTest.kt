package dev.nextgen.mobile.domain.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetStartedTourTest {
    @Test
    fun walkthrough_moves_from_student_work_to_organization_to_review_in_three_steps() {
        var state = GetStartedTourState()

        assertEquals("WELCOME", state.step.name)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals("ORGANIZE", state.step.name)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals("REVIEW", state.step.name)
        assertTrue(state.canContinue)
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
    fun walkthrough_is_field_neutral_and_completes_after_three_sections() {
        var state = GetStartedTourState()
        assertEquals(GetStartedTourStep.WELCOME, state.step)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals(GetStartedTourStep.ORGANIZE, state.step)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)
        assertEquals(GetStartedTourStep.REVIEW, state.step)
        assertTrue(state.canContinue)
        state = state.reduce(GetStartedTourEvent.Next)

        assertTrue(state.isComplete)
        assertEquals(GetStartedTourStep.REVIEW, state.step)
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
