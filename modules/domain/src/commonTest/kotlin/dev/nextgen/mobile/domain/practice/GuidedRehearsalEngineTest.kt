package dev.nextgen.mobile.domain.practice

import dev.nextgen.mobile.domain.feedback.FeedbackState
import kotlin.test.Test
import kotlin.test.assertEquals

class GuidedRehearsalEngineTest {
    private val engine = GuidedRehearsalEngine()

    @Test
    fun aConcreteFallbackStepPasses() {
        val result = engine.evaluate(
            "Ask whether I can submit the finished sections first and confirm the next step.",
        )

        assertEquals(FeedbackState.PASS, result.state)
    }

    @Test
    fun aVagueFallbackStepNeedsWork() {
        val result = engine.evaluate("ask later")

        assertEquals(FeedbackState.NEEDS_WORK, result.state)
    }
}
