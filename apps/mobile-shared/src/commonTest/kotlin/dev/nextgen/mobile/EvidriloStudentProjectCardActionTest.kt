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
                StudentProjectCardAction.ARCHIVE,
                StudentProjectCardAction.MOVE_TO_TRASH,
            ),
            studentProjectSecondaryActions(StudentProjectStatus.ACTIVE),
        )
        assertEquals(
            studentProjectSecondaryActions(StudentProjectStatus.ACTIVE),
            studentProjectSecondaryActions(StudentProjectStatus.DRAFT),
        )
    }

    @Test
    fun archived_and_completed_projects_can_be_restored_or_trashed() {
        assertEquals(StudentProjectCardAction.OPEN, studentProjectPrimaryAction(StudentProjectStatus.ARCHIVED))
        assertEquals(StudentProjectCardAction.OPEN, studentProjectPrimaryAction(StudentProjectStatus.COMPLETED))
        val expected = listOf(
            StudentProjectCardAction.RESTORE_ACTIVE,
            StudentProjectCardAction.MOVE_TO_TRASH,
        )

        assertEquals(expected, studentProjectSecondaryActions(StudentProjectStatus.ARCHIVED))
        assertEquals(expected, studentProjectSecondaryActions(StudentProjectStatus.COMPLETED))
    }

    @Test
    fun trashed_projects_keep_both_restore_choices_and_confirmed_permanent_delete() {
        assertEquals(StudentProjectCardAction.RESTORE_ACTIVE, studentProjectPrimaryAction(StudentProjectStatus.TRASHED))
        assertEquals(
            listOf(
                StudentProjectCardAction.RESTORE_ARCHIVED,
                StudentProjectCardAction.DELETE_PERMANENTLY,
            ),
            studentProjectSecondaryActions(StudentProjectStatus.TRASHED),
        )
    }
}
