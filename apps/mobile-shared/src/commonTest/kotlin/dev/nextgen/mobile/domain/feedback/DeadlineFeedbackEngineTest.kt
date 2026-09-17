package dev.nextgen.mobile.domain.feedback

import dev.nextgen.mobile.domain.model.DeadlineDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeadlineFeedbackEngineTest {
    private val engine = DeadlineFeedbackEngine()

    @Test
    fun emptyDraftDoesNotReceiveFalseConfidence() {
        val report = engine.evaluate(DeadlineDraft())

        assertEquals(4, report.items.size)
        assertTrue(report.items.all { it.state != FeedbackState.PASS })
        assertEquals(FeedbackState.NEEDS_WORK, report.overallState)
    }

    @Test
    fun completeDraftPassesAllObservableCriteria() {
        val report = engine.evaluate(
            DeadlineDraft(
                assignment = "Research methods report",
                completedWork = "Outline and sources are ready",
                requestedNextStep = "Submit by Monday at 17:00",
                completionPlan = "Finish analysis Saturday and proofread Sunday",
                policyAcknowledgment = "I understand the course policy and your decision is final",
            ),
        )

        assertEquals(4, report.items.size)
        assertTrue(report.items.all { it.state == FeedbackState.PASS })
        assertEquals(FeedbackState.PASS, report.overallState)
    }

    @Test
    fun revisingOneFieldChangesOnlyItsCriterion() {
        val draft = DeadlineDraft(
            assignment = "Research methods report",
            completedWork = "Outline and sources are ready",
            requestedNextStep = "",
            completionPlan = "Finish analysis Saturday and proofread Sunday",
            policyAcknowledgment = "I understand the course policy and your decision is final",
        )

        val before = engine.evaluate(draft)
        val after = engine.evaluate(draft.copy(requestedNextStep = "Submit by Monday at 17:00"))

        assertEquals(FeedbackState.NEEDS_WORK, before.itemFor(FeedbackCriterion.CLEAR_REQUEST).state)
        assertEquals(FeedbackState.PASS, after.itemFor(FeedbackCriterion.CLEAR_REQUEST).state)
        assertEquals(
            before.itemFor(FeedbackCriterion.SPECIFIC_TASK).state,
            after.itemFor(FeedbackCriterion.SPECIFIC_TASK).state,
        )
        assertEquals(
            before.itemFor(FeedbackCriterion.REALISTIC_PLAN).state,
            after.itemFor(FeedbackCriterion.REALISTIC_PLAN).state,
        )
        assertEquals(
            before.itemFor(FeedbackCriterion.RESPECTFUL_BOUNDARY).state,
            after.itemFor(FeedbackCriterion.RESPECTFUL_BOUNDARY).state,
        )
    }
}
