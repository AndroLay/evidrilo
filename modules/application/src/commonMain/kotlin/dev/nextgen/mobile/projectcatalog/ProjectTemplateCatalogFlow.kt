package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.ProjectTemplateCatalog
import dev.nextgen.mobile.domain.project.ProjectTemplateCatalogSnapshot
import dev.nextgen.mobile.domain.project.ProjectTemplateCatalogValidation
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.TemplateSelectionResult

/** A family row for a local, student-facing catalog browse screen. */
data class ProjectTemplateFamilyCard(
    val family: ProjectTemplateFamily,
    val selectableTemplateCount: Int,
)

data class ProjectTemplateCatalogBrowse(
    val schemaVersion: Int,
    val validation: ProjectTemplateCatalogValidation,
    val families: List<ProjectTemplateFamilyCard>,
)

sealed interface ProjectTemplateFamilyDetail {
    data class Ready(
        val family: ProjectTemplateFamily,
        val templates: List<ProjectTemplateDefinition>,
    ) : ProjectTemplateFamilyDetail

    /** Empty families are informative, but must not be presented as selectable templates. */
    data class NoReadyTemplates(
        val family: ProjectTemplateFamily,
    ) : ProjectTemplateFamilyDetail
}

/**
 * Offline catalog use-cases. All readiness and exact-version decisions remain
 * owned by the domain catalog so the UI cannot accidentally bypass its gates.
 */
object ProjectTemplateCatalogFlow {
    fun browse(snapshot: ProjectTemplateCatalogSnapshot): ProjectTemplateCatalogBrowse =
        ProjectTemplateCatalogBrowse(
            schemaVersion = snapshot.schemaVersion,
            validation = ProjectTemplateCatalog.validate(snapshot),
            families = ProjectTemplateCatalog.familyOfferings(snapshot).map { offering ->
                ProjectTemplateFamilyCard(
                    family = offering.family,
                    selectableTemplateCount = offering.selectableTemplateCount,
                )
            },
        )

    fun inspect(
        snapshot: ProjectTemplateCatalogSnapshot,
        family: ProjectTemplateFamily,
    ): ProjectTemplateFamilyDetail {
        val readyTemplates = snapshot.templates
            .asSequence()
            .filter { it.family == family }
            .mapNotNull { candidate ->
                when (val selection = ProjectTemplateCatalog.select(snapshot, candidate.id, candidate.version)) {
                    is TemplateSelectionResult.Selected -> selection.template
                    is TemplateSelectionResult.Unavailable -> null
                }
            }
            .toList()

        return if (readyTemplates.isEmpty()) {
            ProjectTemplateFamilyDetail.NoReadyTemplates(family)
        } else {
            ProjectTemplateFamilyDetail.Ready(family, readyTemplates)
        }
    }

    fun choose(
        snapshot: ProjectTemplateCatalogSnapshot,
        templateId: String,
        expectedVersion: Int,
    ): TemplateSelectionResult = ProjectTemplateCatalog.select(snapshot, templateId, expectedVersion)
}
