package dev.nextgen.mobile.domain.conclusion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ConclusionFixtureTest {
    private val evaluator = ConclusionEvaluator()

    @Test
    fun independentAdversarialFixturesMatchTheirExpectedOutcomes() {
        assertEquals(
            (1..13).map { "ADV-${it.toString().padStart(2, '0')}" }.toSet(),
            ConclusionFixtures.evaluatorFixtures.map { it.id }.toSet(),
        )

        ConclusionFixtures.evaluatorFixtures.forEach { fixture ->
            val feedback = evaluator.evaluate(fixture.draft).primaryFeedback
            val expected = fixture.expectation
            if (expected.status == null) {
                assertNull(feedback, fixture.id)
            } else {
                assertNotNull(feedback, fixture.id)
                assertEquals(expected.status, feedback.status, fixture.id)
                assertEquals(expected.priority, feedback.priority, fixture.id)
                assertEquals(expected.field, feedback.field, fixture.id)
                assertEquals(expected.code, feedback.code, fixture.id)
            }
        }
    }
}
