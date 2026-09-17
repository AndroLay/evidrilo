package dev.nextgen.mobile.audio

import dev.nextgen.mobile.evidriloOnboardingPresentation
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import kotlin.test.Test
import kotlin.test.assertTrue

class AudioNarrationCopyTest {
    @Test
    fun case_narration_contains_the_visible_case_copy_in_stable_order() {
        val case = ConclusionCases.EVIDENCE_CHANGE

        val narration = AudioNarrationCopy.case(case)

        assertTrue(narration.indexOf(case.title) >= 0)
        assertTrue(narration.indexOf(case.description) > narration.indexOf(case.title))
        case.changeNotice?.let { notice ->
            assertTrue(narration.indexOf(notice) > narration.indexOf(case.description))
        }
        case.facts.forEach { fact ->
            assertTrue(narration.contains(fact.text))
        }
    }

    @Test
    fun onboarding_narration_contains_every_benefit() {
        val presentation = evidriloOnboardingPresentation(
            completed = false,
            hasSavedPractice = false,
        )

        val narration = AudioNarrationCopy.onboarding(presentation)

        assertTrue(narration.contains(presentation.title))
        assertTrue(narration.contains(presentation.body))
        presentation.freeBenefits.forEach { benefit ->
            assertTrue(narration.contains(benefit))
        }
    }

    @Test
    fun fixed_screen_narration_is_non_empty_and_does_not_depend_on_learner_text() {
        listOf(
            AudioNarrationCopy.guide(),
            AudioNarrationCopy.feedback(),
            AudioNarrationCopy.revision(),
            AudioNarrationCopy.challenge(),
            AudioNarrationCopy.support(),
        ).forEach { narration ->
            assertTrue(narration.isNotBlank())
        }
    }
}
