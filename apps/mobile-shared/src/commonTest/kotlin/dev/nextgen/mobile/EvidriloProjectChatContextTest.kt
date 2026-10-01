package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.*
import dev.nextgen.mobile.projectcatalog.ProjectAiChatEdit
import kotlin.test.*

class EvidriloProjectChatContextTest {
    @Test fun manual_projects_can_receive_reviewed_question_edits() {
        val manual = draft().copy(templateSnapshot = null)
        val context = projectChatContext(manual)
        assertTrue(context.fields.any { it.id == ManualLiteratureSynthesisFields.RESEARCH_QUESTION })
        assertFalse(context.fields.any { it.id == ManualLiteratureSynthesisFields.CLAIM })
        assertTrue(validProjectChatEdits(context, manual, listOf(
            ProjectAiChatEdit("research_question", "How does shade relate to campus heat exposure?", "Clarify scope"),
        )))
    }
    private fun draft() = StudentProjectDraft("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        ProjectStarterTemplateCatalog.templates.first(), "Campus shade",
        mapOf("research_question" to "How does shade affect heat exposure?", "measurement_notes" to "No measurements yet"),
        2, 1, 1)

    @Test fun saved_snapshot_excludes_original_data_from_editable_fields() {
        val context = projectChatContext(draft())
        assertTrue(context.fields.any { it.id == "research_question" })
        assertFalse(context.fields.any { it.id == "measurement_notes" })
        assertTrue(context.notes.contains("No measurements yet"))
    }
    @Test fun changed_revision_cannot_accept_edits() {
        val original = draft()
        val context = projectChatContext(original)
        val edits = listOf(ProjectAiChatEdit("research_question", "How does walkway shade affect perceived heat?", "Focus the question"))
        assertTrue(validProjectChatEdits(context, original, edits))
        assertFalse(validProjectChatEdits(context, original.copy(revision = 3), edits))
        assertFalse(validProjectChatEdits(context, original.copy(id = "other"), edits))
    }
    @Test fun unrelated_fields_and_duplicate_edits_are_rejected() {
        val current = draft(); val context = projectChatContext(current)
        val edit = ProjectAiChatEdit("measurement_notes", "Fabricated result", "Wrong field")
        assertFalse(validProjectChatEdits(context, current, listOf(edit)))
        val allowed = edit.copy(fieldId = "research_question")
        assertFalse(validProjectChatEdits(context, current, listOf(allowed, allowed)))
    }
}
