package dev.nextgen.mobile.domain.askready

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskReadyFeedbackEngineTest {
    private val engine = AskReadyFeedbackEngine()

    @Test
    fun completeRequestPassesAllSixObservableCriteria() {
        val report = engine.evaluate(
            AskReadyDraft(
                specificObject = "Lab 3 sorting function",
                context = "I am stuck on the failing test in the sorting function.",
                attempt = "I compared the loop bounds and ran the provided test case.",
                concreteAsk = "Could you point out which invariant I should check next?",
                nextStep = "A short hint by email would help before office hours.",
                channel = AskReadyChannel.INSTRUCTOR,
            ),
        )

        assertEquals(AskReadyFeedbackState.PASS, report.overallState)
        assertEquals(6, report.items.size)
        assertTrue(report.items.all { it.state == AskReadyFeedbackState.PASS })
    }

    @Test
    fun missingContextIsDifferentFromAWeakButPresentField() {
        val report = engine.evaluate(
            AskReadyDraft(
                specificObject = "",
                context = "lab",
                attempt = "I tried something.",
                concreteAsk = "Can you help?",
                nextStep = "A hint would help.",
                channel = AskReadyChannel.PEER,
            ),
        )

        assertEquals(
            AskReadyFeedbackState.NOT_ENOUGH_CONTEXT,
            report.itemFor(AskReadyCriterion.SPECIFIC_OBJECT).state,
        )
        assertEquals(
            AskReadyFeedbackState.NEEDS_WORK,
            report.itemFor(AskReadyCriterion.CONCRETE_ASK).state,
        )
        assertEquals(AskReadyFeedbackState.NEEDS_WORK, report.overallState)
    }

    @Test
    fun privacyCriterionFlagsStudentIdentifiersWithoutJudgingAcademicContent() {
        val report = engine.evaluate(
            AskReadyDraft(
                specificObject = "Essay draft",
                context = "My student ID is 123456 and I need help with paragraph two.",
                attempt = "I rewrote the topic sentence.",
                concreteAsk = "Could you suggest what evidence I should look for?",
                nextStep = "A short reply is enough.",
                channel = AskReadyChannel.TEACHING_ASSISTANT,
            ),
        )

        assertEquals(
            AskReadyFeedbackState.NEEDS_WORK,
            report.itemFor(AskReadyCriterion.PRIVACY_HYGIENE).state,
        )
        assertEquals(AskReadyFeedbackState.NEEDS_WORK, report.overallState)
        assertTrue(
            report.itemFor(AskReadyCriterion.PRIVACY_HYGIENE).message
                .contains("private", ignoreCase = true),
        )
    }

    @Test
    fun feedbackNeverClaimsThatARecipientWillApproveTheRequest() {
        val report = engine.evaluate(
            AskReadyDraft(
                specificObject = "Assignment 2",
                context = "I do not understand the final step.",
                attempt = "I checked the lecture example and tried a smaller case.",
                concreteAsk = "Could you clarify the final step?",
                nextStep = "A hint before tomorrow's class would help.",
                channel = AskReadyChannel.INSTRUCTOR,
            ),
        )

        assertTrue(report.items.none { it.message.contains("approve", ignoreCase = true) })
        assertTrue(report.items.none { it.message.contains("guarantee", ignoreCase = true) })
    }
}
