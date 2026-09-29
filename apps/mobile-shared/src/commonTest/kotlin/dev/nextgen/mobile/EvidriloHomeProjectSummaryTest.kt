package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloHomeProjectSummaryTest {
    @Test
    fun project_list_shortcut_only_appears_when_a_visible_list_can_be_opened() {
        val empty = homeProjectSummary(emptyList(), activeLimit = 3)
        val withProjects = homeProjectSummary(
            listOf(project("archived", StudentProjectStatus.ARCHIVED)),
            activeLimit = 3,
        )

        assertFalse(shouldShowHomeProjectListAction(empty, isLoading = false, loadError = null))
        assertFalse(shouldShowHomeProjectListAction(withProjects, isLoading = true, loadError = null))
        assertFalse(shouldShowHomeProjectListAction(withProjects, isLoading = false, loadError = "offline"))
        assertTrue(shouldShowHomeProjectListAction(withProjects, isLoading = false, loadError = null))
    }

    @Test
    fun every_project_family_has_a_distinct_field_specific_catalog_marker() {
        val designs = ProjectTemplateFamily.values().map(::projectTemplateFamilyCardDesign)

        assertEquals(
            listOf(
                ProjectTemplateFamilyCardDesign(EvidriloIconName.LIGHTNING, "Change and measure"),
                ProjectTemplateFamilyCardDesign(EvidriloIconName.LIST, "Observe patterns"),
                ProjectTemplateFamilyCardDesign(EvidriloIconName.BOOK, "Compare sources"),
                ProjectTemplateFamilyCardDesign(EvidriloIconName.CHAT_BUBBLE, "Hear perspectives"),
                ProjectTemplateFamilyCardDesign(EvidriloIconName.CHECKLIST, "Build and test"),
            ),
            designs,
        )
    }

    @Test
    fun summary_counts_active_slots_separately_from_completed_archived_and_trashed_projects() {
        val summary = homeProjectSummary(
            projects = listOf(
                project("draft", StudentProjectStatus.DRAFT),
                project("active", StudentProjectStatus.ACTIVE),
                project("complete", StudentProjectStatus.COMPLETED),
                project("archived", StudentProjectStatus.ARCHIVED),
                project("trash", StudentProjectStatus.TRASHED),
            ),
            activeLimit = 5,
        )

        assertEquals(4, summary.totalCount)
        assertEquals(2, summary.activeCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.archivedCount)
        assertEquals(3, summary.remainingActiveSlots)
    }

    @Test
    fun summary_uses_the_current_installation_limit_without_counting_trash() {
        val summary = homeProjectSummary(
            projects = listOf(project("trashed", StudentProjectStatus.TRASHED)),
            activeLimit = 50,
        )

        assertEquals(0, summary.totalCount)
        assertEquals(0, summary.activeCount)
        assertEquals(50, summary.remainingActiveSlots)
    }

    private fun project(id: String, status: StudentProjectStatus) = StudentProjectDraft(
        id = id,
        templateSnapshot = null,
        title = id,
        fieldValues = emptyMap(),
        revision = 0,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
        status = status,
    )
}
