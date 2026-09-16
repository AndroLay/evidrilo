package dev.nextgen.mobile.domain.feedback

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackPresentationTest {
    @Test
    fun criterionLabelsAreReadableForPeopleAndAssistiveTechnology() {
        assertEquals("Specific task", FeedbackCriterion.SPECIFIC_TASK.displayLabel())
        assertEquals("Clear request", FeedbackCriterion.CLEAR_REQUEST.displayLabel())
        assertEquals("Realistic plan", FeedbackCriterion.REALISTIC_PLAN.displayLabel())
        assertEquals("Respectful boundary", FeedbackCriterion.RESPECTFUL_BOUNDARY.displayLabel())
    }

    @Test
    fun stateLabelsDoNotExposeImplementationFormatting() {
        assertEquals("Pass", FeedbackState.PASS.displayLabel())
        assertEquals("Needs work", FeedbackState.NEEDS_WORK.displayLabel())
        assertEquals("Not enough context", FeedbackState.NOT_ENOUGH_CONTEXT.displayLabel())
    }
}
