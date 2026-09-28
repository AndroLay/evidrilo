package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectAiAccountBoundaryTest {
    @Test
    fun an_unapplied_preview_is_bound_to_the_account_that_requested_it() {
        assertTrue(projectAiSessionMatchesOwner("student-a", "student-a"))
        assertFalse(projectAiSessionMatchesOwner("student-a", "student-b"))
        assertFalse(projectAiSessionMatchesOwner("student-a", null))
        assertFalse(projectAiSessionMatchesOwner(null, "student-a"))
        assertFalse(projectAiSessionMatchesOwner(null, null))
    }
}
