package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EvidriloGuidePresentationTest {
    @Test
    fun guideContainsTheThreeCoreTopicsInProcessOrder() {
        assertEquals(
            listOf(
                EvidriloGuideTopic.SUPPORTING_EVIDENCE,
                EvidriloGuideTopic.CLAIM_BOUNDS,
                EvidriloGuideTopic.FEEDBACK_REVISION,
            ),
            evidriloGuideTopics,
        )
        assertEquals("Choose supporting evidence", guideTopicCopy(EvidriloGuideTopic.SUPPORTING_EVIDENCE).title)
        assertEquals("Keep the claim in bounds", guideTopicCopy(EvidriloGuideTopic.CLAIM_BOUNDS).title)
        assertEquals("Read feedback, then revise", guideTopicCopy(EvidriloGuideTopic.FEEDBACK_REVISION).title)
    }

    @Test
    fun guideCopyDoesNotPretendThatReadingIsCompletedProgress() {
        evidriloGuideTopics
            .map(::guideTopicCopy)
            .forEach { copy ->
                assertFalse(copy.body.contains("score", ignoreCase = true))
                assertFalse(copy.body.contains("mastery", ignoreCase = true))
                assertFalse(copy.body.contains("completed", ignoreCase = true))
            }
    }
}
