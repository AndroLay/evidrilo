package dev.nextgen.mobile

import dev.nextgen.mobile.domain.onboarding.GetStartedTourStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvidriloOnboardingPreviewTest {
    @Test
    fun choosing_a_blank_start_changes_only_the_temporary_first_view_preview() {
        val initial = GetStartedTemporaryPreview()
        val selected = initial.copy(startingPoint = GetStartedStartingPoint.BLANK)

        assertEquals("Start blank", getStartedPreviewFor(GetStartedTourStep.WELCOME, selected).title)
        assertEquals(
            getStartedPreviewFor(GetStartedTourStep.ORGANIZE, initial),
            getStartedPreviewFor(GetStartedTourStep.ORGANIZE, selected),
        )
        assertEquals(
            getStartedPreviewFor(GetStartedTourStep.REVIEW, initial),
            getStartedPreviewFor(GetStartedTourStep.REVIEW, selected),
        )
    }

    @Test
    fun selecting_first_material_and_review_focus_updates_the_matching_preview() {
        val preview = GetStartedTemporaryPreview(
            firstMaterial = GetStartedFirstMaterial.OBSERVATIONS,
            reviewFocus = GetStartedReviewFocus.LIMITATIONS,
        )

        assertEquals("Observations or notes", getStartedPreviewFor(GetStartedTourStep.ORGANIZE, preview).title)
        assertEquals("Notice limitations", getStartedPreviewFor(GetStartedTourStep.REVIEW, preview).title)
    }

    @Test
    fun preview_never_presents_sample_research_as_saved_student_content() {
        val allPreviews = listOf(
            GetStartedTourStep.WELCOME,
            GetStartedTourStep.ORGANIZE,
            GetStartedTourStep.REVIEW,
        ).map { getStartedPreviewFor(it, GetStartedTemporaryPreview()) }

        assertTrue(allPreviews.all { it.disclaimer == "Temporary preview · nothing is saved" })
        assertTrue(allPreviews.none { it.title.contains("OBS-") || it.title.contains("result", ignoreCase = true) })
    }
}
