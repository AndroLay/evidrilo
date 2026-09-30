package dev.nextgen.mobile.domain.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectStarterTemplateCatalogTest {
    @Test
    fun `five bundled family starters are selectable offline and create empty project scaffolds`() {
        val templates = ProjectStarterTemplateCatalog.templates
        val snapshot = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = templates)

        assertEquals(ProjectTemplateFamily.entries.toSet(), templates.map(ProjectTemplateDefinition::family).toSet())
        assertEquals(ProjectTemplateFamily.entries.size, templates.size)
        assertTrue(ProjectTemplateCatalog.validate(snapshot).isValid)

        templates.forEachIndexed { index, template ->
            assertEquals(ProjectTemplatePublication.BUILT_IN_STARTER, template.publication)
            assertTrue(template.examples.none(ProjectTemplateExample::reviewed))
            assertTrue(template.steps.isNotEmpty())
            assertTrue(template.inputFields.isNotEmpty())
            assertTrue(template.methodSpecificLimitations.isNotEmpty())
            assertTrue(template.provenanceRequirements.isNotEmpty())
            assertTrue(template.accessibilityExpectations.isNotEmpty())
            assertTrue(template.steps.all { it.aiOperations.isEmpty() })
            assertTrue(template.steps.flatMap(ProjectTemplateStep::inputFieldIds).all { fieldId ->
                template.inputFields.any { it.id == fieldId }
            })
            assertEquals(
                template.inputFields.map(ProjectTemplateInputField::id).toSet(),
                template.steps.flatMap(ProjectTemplateStep::inputFieldIds).toSet(),
                "Every student-facing field must appear in an ordered project section",
            )

            val selected = assertIs<TemplateSelectionResult.Selected>(
                ProjectTemplateCatalog.select(snapshot, template.id, template.version),
            )
            assertEquals(template, selected.template)

            val created = assertIs<StudentProjectDraftCreateResult.Created>(
                StudentProjectDraftRules.create(
                    id = "starter-project-${index + 1}",
                    template = template,
                    title = template.title,
                    createdAtEpochMillis = 1,
                ),
            ).draft
            assertEquals(template, created.templateSnapshot)
            assertTrue(created.fieldValues.isEmpty(), "A starter must not invent student answers")
            assertTrue(created.sources.isEmpty())
            assertTrue(created.evidenceItems.isEmpty())
            assertTrue(created.findings.isEmpty())
            assertTrue(created.claims.isEmpty())
        }
    }

    @Test
    fun `forged built in starter content cannot be selected or stored as a project snapshot`() {
        val canonical = ProjectStarterTemplateCatalog.templates.first()
        val edited = canonical.copy(title = "Unreviewed altered template")
        val snapshot = ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(edited))

        assertFalse(ProjectTemplateCatalog.validate(snapshot).isValid)
        assertIs<TemplateSelectionResult.Unavailable>(
            ProjectTemplateCatalog.select(snapshot, edited.id, edited.version),
        )
        assertIs<StudentProjectDraftCreateResult.Unavailable>(
            StudentProjectDraftRules.create("forged-starter", edited, edited.title, 1),
        )
    }
}
