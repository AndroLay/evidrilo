package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.StudentProjectStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloStudentProjectCardActionTest {
    @Test
    fun active_projects_keep_state_changes_as_secondary_menu_actions() {
        assertEquals(StudentProjectCardAction.CONTINUE, studentProjectPrimaryAction(StudentProjectStatus.ACTIVE))
        assertEquals(StudentProjectCardAction.CONTINUE, studentProjectPrimaryAction(StudentProjectStatus.DRAFT))
        assertEquals(
            listOf(
                StudentProjectCardAction.MARK_COMPLETE,
                StudentProjectCardAction.EXPORT,
                StudentProjectCardAction.DELETE_PERMANENTLY,
            ),
            studentProjectSecondaryActions(StudentProjectStatus.ACTIVE),
        )
        assertEquals(
            studentProjectSecondaryActions(StudentProjectStatus.ACTIVE),
            studentProjectSecondaryActions(StudentProjectStatus.DRAFT),
        )
    }

    @Test
    fun legacy_archived_and_completed_projects_can_be_restored_exported_or_removed() {
        assertEquals(StudentProjectCardAction.OPEN, studentProjectPrimaryAction(StudentProjectStatus.ARCHIVED))
        assertEquals(StudentProjectCardAction.OPEN, studentProjectPrimaryAction(StudentProjectStatus.COMPLETED))
        val expected = listOf(
            StudentProjectCardAction.RESTORE_ACTIVE,
            StudentProjectCardAction.EXPORT,
            StudentProjectCardAction.DELETE_PERMANENTLY,
        )

        assertEquals(expected, studentProjectSecondaryActions(StudentProjectStatus.ARCHIVED))
        assertEquals(expected, studentProjectSecondaryActions(StudentProjectStatus.COMPLETED))
    }

    @Test
    fun legacy_trashed_projects_can_restore_export_or_permanently_delete() {
        assertEquals(StudentProjectCardAction.RESTORE_ACTIVE, studentProjectPrimaryAction(StudentProjectStatus.TRASHED))
        assertEquals(
            listOf(
                StudentProjectCardAction.RESTORE_ACTIVE,
                StudentProjectCardAction.EXPORT,
                StudentProjectCardAction.DELETE_PERMANENTLY,
            ),
            studentProjectSecondaryActions(StudentProjectStatus.TRASHED),
        )
    }
}
