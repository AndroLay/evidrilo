package dev.nextgen.mobile.domain.askready

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AskReadyPresentationTest {
    @Test
    fun labelsExposeUserLanguageInsteadOfEnumNames() {
        assertEquals("Specific object", AskReadyCriterion.SPECIFIC_OBJECT.displayLabel())
        assertEquals("Teaching assistant", AskReadyChannel.TEACHING_ASSISTANT.displayLabel())
        assertEquals("Needs work", AskReadyFeedbackState.NEEDS_WORK.displayLabel())
    }

    @Test
    fun previewContainsTheObservableRequestFieldsAndChosenChannel() {
        val preview = buildAskReadyPreview(
            AskReadyDraft(
                specificObject = "Lab 3 sorting function",
                context = "The failing test is in the sorting function.",
                attempt = "I checked the loop bounds.",
                concreteAsk = "Could you point out which invariant to check?",
                nextStep = "A short hint before office hours would help.",
                channel = AskReadyChannel.INSTRUCTOR,
            ),
        )

        assertTrue(preview.contains("Lab 3 sorting function"))
        assertTrue(preview.contains("Could you point out which invariant to check?"))
        assertTrue(preview.contains("Instructor"))
        assertTrue(preview.contains("does not predict approval", ignoreCase = true))
    }
}
