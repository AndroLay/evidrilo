package dev.nextgen.mobile.domain.project

enum class ProjectTemplateFamily(
    val id: String,
    val displayName: String,
) {
    EXPERIMENTAL_LABORATORY("experimental_laboratory", "Experimental and laboratory work"),
    OBSERVATIONAL_SURVEY("observational_survey", "Observational and survey studies"),
    LITERATURE_REVIEW("literature_review", "Literature reviews"),
    QUALITATIVE_INTERVIEW_FIELD_STUDY(
        "qualitative_interview_field_study",
        "Qualitative interviews and field studies",
    ),
    DESIGN_ENGINEERING("design_engineering", "Design and engineering projects"),
}

enum class ProjectTemplatePublication {
    DRAFT,
    PUBLISHED,
    RETIRED,
}

enum class ProjectTemplateInputKind {
    ASSIGNMENT_BRIEF,
    RESEARCH_QUESTION,
    HYPOTHESIS,
    SOURCE,
    DATA,
    ANALYSIS,
    CLAIM,
    LIMITATION,
    NEXT_ACTION,
}

data class ProjectTemplateInputField(
    val id: String,
    val kind: ProjectTemplateInputKind,
    val label: String,
    val required: Boolean,
)

data class ProjectTemplateStep(
    val id: String,
    val title: String,
    val inputFieldIds: List<String>,
    val aiOperations: List<ProjectTemplateAiOperationCapability> = emptyList(),
)

enum class ProjectAiStageOperation(val id: String) {
    EXPLAIN_TEMPLATE_STEP("explain_template_step"),
    ORGANIZE_SELECTED_MATERIAL("organize_selected_material"),
    SUMMARIZE_SELECTED_MATERIAL("summarize_selected_material"),
    SUGGEST_ANALYSIS("suggest_analysis"),
    CHECK_EVIDENCE_LINKS("check_evidence_links"),
    SUGGEST_REVISION("suggest_revision"),
    PREPARE_OUTPUT_SECTION("prepare_output_section"),
}

data class ProjectTemplateAiOperationCapability(
    val id: String,
    val inputFieldIds: List<String>,
    val outputFieldIds: List<String>,
)

data class ProjectTemplateExample(
    val id: String,
    val summary: String,
    val reviewed: Boolean,
    val kind: ProjectTemplateExampleKind = ProjectTemplateExampleKind.UNSPECIFIED,
)

enum class ProjectTemplateExampleKind {
    UNSPECIFIED,
    /** A nominal scenario path; it does not imply a correct answer or favorable finding. */
    NORMAL,
    EDGE_OR_CONFLICTING,
}

data class ProjectTemplateDefinition(
    val id: String,
    val version: Int,
    val family: ProjectTemplateFamily,
    val title: String,
    val summary: String,
    val intendedOutput: String,
    val inputFields: List<ProjectTemplateInputField>,
    val steps: List<ProjectTemplateStep>,
    val methodSpecificLimitations: List<String>,
    val provenanceRequirements: List<String>,
    val accessibilityExpectations: List<String>,
    val examples: List<ProjectTemplateExample>,
    val publication: ProjectTemplatePublication,
)

data class ProjectTemplateCatalogSnapshot(
    val schemaVersion: Int,
    val templates: List<ProjectTemplateDefinition>,
)

data class ProjectTemplateFamilyOffering(
    val family: ProjectTemplateFamily,
    val selectableTemplateCount: Int,
)

data class ProjectTemplateCatalogIssue(
    val code: String,
    val templateId: String? = null,
)

data class ProjectTemplateCatalogValidation(
    val issues: List<ProjectTemplateCatalogIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()
}

sealed interface TemplateSelectionResult {
    data class Selected(val template: ProjectTemplateDefinition) : TemplateSelectionResult

    data class Unavailable(val code: String) : TemplateSelectionResult
}

/**
 * Pure catalog rules shared by bundled content, the application layer, and UI.
 * A family is browseable even when it has no released template; only complete,
 * reviewed, published templates can be selected for a student project.
 */
object ProjectTemplateCatalog {
    val families: List<ProjectTemplateFamily> = ProjectTemplateFamily.values().toList()

    fun familyOfferings(snapshot: ProjectTemplateCatalogSnapshot): List<ProjectTemplateFamilyOffering> =
        families.map { family ->
            ProjectTemplateFamilyOffering(
                family = family,
                selectableTemplateCount = snapshot.templates.count { template ->
                    template.family == family &&
                        select(snapshot, template.id, template.version) is TemplateSelectionResult.Selected
                },
            )
        }

    fun validate(snapshot: ProjectTemplateCatalogSnapshot): ProjectTemplateCatalogValidation {
        val issues = buildList {
            if (snapshot.schemaVersion < MIN_SCHEMA_VERSION) {
                add(ProjectTemplateCatalogIssue("INVALID_CATALOG_SCHEMA_VERSION"))
            }

            val templateIds = snapshot.templates.map(ProjectTemplateDefinition::id)
            if (templateIds.size != templateIds.toSet().size) {
                add(ProjectTemplateCatalogIssue("DUPLICATE_TEMPLATE_ID"))
            }

            snapshot.templates
                .asSequence()
                .filter { it.publication == ProjectTemplatePublication.PUBLISHED }
                .forEach { template -> addAll(issuesForPublishedTemplate(template)) }
        }
        return ProjectTemplateCatalogValidation(issues)
    }

    fun select(
        snapshot: ProjectTemplateCatalogSnapshot,
        templateId: String,
        expectedVersion: Int,
    ): TemplateSelectionResult {
        if (snapshot.schemaVersion < MIN_SCHEMA_VERSION) {
            return TemplateSelectionResult.Unavailable("INVALID_CATALOG_SCHEMA_VERSION")
        }

        val matches = snapshot.templates.filter { it.id == templateId }
        if (matches.isEmpty()) return TemplateSelectionResult.Unavailable("TEMPLATE_NOT_FOUND")
        if (matches.size != 1) return TemplateSelectionResult.Unavailable("DUPLICATE_TEMPLATE_ID")

        val template = matches.single()
        if (template.version != expectedVersion) {
            return TemplateSelectionResult.Unavailable("TEMPLATE_VERSION_MISMATCH")
        }
        if (template.publication != ProjectTemplatePublication.PUBLISHED) {
            return TemplateSelectionResult.Unavailable("TEMPLATE_NOT_READY")
        }
        if (issuesForPublishedTemplate(template).isNotEmpty()) {
            return TemplateSelectionResult.Unavailable("TEMPLATE_NOT_READY")
        }
        return TemplateSelectionResult.Selected(template)
    }

    /**
     * Validates readable published detail and snapshots. Historical
     * unclassified examples stay readable for existing work, but are not
     * eligible as the basis of a newly created project.
     */
    fun validateReadablePublishedTemplate(template: ProjectTemplateDefinition): List<String> {
        if (template.publication != ProjectTemplatePublication.PUBLISHED) {
            return listOf("TEMPLATE_NOT_READY")
        }
        val isLegacyUnclassified = template.examples.isNotEmpty() &&
            template.examples.all { it.kind == ProjectTemplateExampleKind.UNSPECIFIED }
        return issuesForPublishedTemplate(template, allowLegacyUnclassified = isLegacyUnclassified)
            .map(ProjectTemplateCatalogIssue::code)
    }

    private fun issuesForPublishedTemplate(
        template: ProjectTemplateDefinition,
        allowLegacyUnclassified: Boolean = false,
    ): List<ProjectTemplateCatalogIssue> = buildList {
        fun issue(code: String) = add(ProjectTemplateCatalogIssue(code, template.id))

        if (!IDENTIFIER_PATTERN.matches(template.id)) issue("INVALID_TEMPLATE_ID")
        if (template.version < MIN_TEMPLATE_VERSION) issue("INVALID_TEMPLATE_VERSION")
        if (template.title.isBlank()) issue("TEMPLATE_TITLE_REQUIRED")
        if (template.summary.isBlank()) issue("TEMPLATE_SUMMARY_REQUIRED")
        if (template.intendedOutput.isBlank()) issue("TEMPLATE_OUTPUT_REQUIRED")

        if (template.inputFields.isEmpty()) issue("TEMPLATE_INPUTS_REQUIRED")
        val inputIds = template.inputFields.map(ProjectTemplateInputField::id)
        if (inputIds.any { !IDENTIFIER_PATTERN.matches(it) }) issue("INVALID_TEMPLATE_INPUT_ID")
        if (inputIds.size != inputIds.toSet().size) issue("DUPLICATE_TEMPLATE_INPUT_ID")
        if (template.inputFields.any { it.label.isBlank() }) issue("TEMPLATE_INPUT_LABEL_REQUIRED")
        val fieldsById = template.inputFields.associateBy(ProjectTemplateInputField::id)

        if (template.steps.isEmpty()) issue("TEMPLATE_STEPS_REQUIRED")
        val stepIds = template.steps.map(ProjectTemplateStep::id)
        if (stepIds.any { !IDENTIFIER_PATTERN.matches(it) }) issue("INVALID_TEMPLATE_STEP_ID")
        if (stepIds.size != stepIds.toSet().size) issue("DUPLICATE_TEMPLATE_STEP_ID")
        if (template.steps.any { it.title.isBlank() }) issue("TEMPLATE_STEP_TITLE_REQUIRED")
        if (template.steps.any { step -> step.inputFieldIds.any { it !in inputIds } }) {
            issue("TEMPLATE_STEP_INPUT_NOT_FOUND")
        }
        template.steps.forEach { step ->
            val operationIds = mutableSetOf<String>()
            step.aiOperations.forEach { operation ->
                if (operation.id !in ProjectAiStageOperation.entries.map(ProjectAiStageOperation::id) ||
                    !operationIds.add(operation.id) ||
                    operation.inputFieldIds.size > MAX_AI_OPERATION_FIELDS ||
                    operation.outputFieldIds.size > MAX_AI_OPERATION_FIELDS ||
                    operation.inputFieldIds.size != operation.inputFieldIds.toSet().size ||
                    operation.outputFieldIds.size != operation.outputFieldIds.toSet().size ||
                    operation.id != ProjectAiStageOperation.EXPLAIN_TEMPLATE_STEP.id && operation.inputFieldIds.isEmpty()
                ) {
                    issue("INVALID_TEMPLATE_AI_OPERATION")
                }
                if (operation.inputFieldIds.any { it !in step.inputFieldIds } ||
                    operation.outputFieldIds.any { it !in step.inputFieldIds }
                ) {
                    issue("TEMPLATE_AI_OPERATION_FIELD_NOT_IN_STEP")
                }
                if (operation.outputFieldIds.any { fieldId ->
                        fieldsById[fieldId]?.kind in setOf(ProjectTemplateInputKind.SOURCE, ProjectTemplateInputKind.DATA)
                    }
                ) {
                    issue("TEMPLATE_AI_OPERATION_OUTPUT_NOT_ALLOWED")
                }
            }
            if (step.aiOperations.size > MAX_AI_OPERATIONS) issue("TOO_MANY_TEMPLATE_AI_OPERATIONS")
        }

        if (template.methodSpecificLimitations.none(String::isNotBlank)) {
            issue("TEMPLATE_METHOD_LIMITS_REQUIRED")
        }
        if (template.provenanceRequirements.none(String::isNotBlank)) {
            issue("TEMPLATE_PROVENANCE_REQUIRED")
        }
        if (template.accessibilityExpectations.none(String::isNotBlank)) {
            issue("TEMPLATE_ACCESSIBILITY_EXPECTATIONS_REQUIRED")
        }

        val exampleIds = template.examples.map(ProjectTemplateExample::id)
        if (exampleIds.size != exampleIds.toSet().size) issue("DUPLICATE_TEMPLATE_EXAMPLE_ID")
        if (template.examples.any { !IDENTIFIER_PATTERN.matches(it.id) || it.summary.isBlank() }) {
            issue("INVALID_TEMPLATE_EXAMPLE")
        }
        val hasOnlyUnclassifiedExamples = allowLegacyUnclassified && template.examples.isNotEmpty() &&
            template.examples.all { it.kind == ProjectTemplateExampleKind.UNSPECIFIED }
        if (hasOnlyUnclassifiedExamples) {
            if (template.examples.none(ProjectTemplateExample::reviewed)) {
                issue("PUBLISHED_TEMPLATE_REQUIRES_REVIEWED_EXAMPLE")
            }
        } else {
            if (template.examples.any { it.kind == ProjectTemplateExampleKind.UNSPECIFIED }) {
                issue("TEMPLATE_EXAMPLE_KINDS_MIXED")
            }
            if (template.examples.none { it.kind == ProjectTemplateExampleKind.NORMAL } ||
                template.examples.none { it.kind == ProjectTemplateExampleKind.EDGE_OR_CONFLICTING }
            ) {
                issue("PUBLISHED_TEMPLATE_REQUIRES_NORMAL_AND_EDGE_EXAMPLES")
            }
            if (template.examples.none { it.kind == ProjectTemplateExampleKind.NORMAL && it.reviewed } ||
                template.examples.none { it.kind == ProjectTemplateExampleKind.EDGE_OR_CONFLICTING && it.reviewed }
            ) {
                issue("PUBLISHED_TEMPLATE_REQUIRES_REVIEWED_NORMAL_AND_EDGE_EXAMPLES")
            }
        }
    }

    private const val MIN_SCHEMA_VERSION = 1
    private const val MIN_TEMPLATE_VERSION = 1
    private const val MAX_AI_OPERATIONS = 8
    private const val MAX_AI_OPERATION_FIELDS = 32
    private val IDENTIFIER_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
}
