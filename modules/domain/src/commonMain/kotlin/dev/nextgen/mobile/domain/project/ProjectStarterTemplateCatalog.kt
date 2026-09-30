package dev.nextgen.mobile.domain.project

/**
 * Five offline project starters. They create structure only: no student data,
 * findings, claims, evaluator, or human-review status is bundled here.
 * Keep released versions in this catalog so existing project snapshots remain
 * verifiable after a newer starter version is added.
 */
object ProjectStarterTemplateCatalog {
    val templates: List<ProjectTemplateDefinition> = listOf(
        ProjectTemplateDefinition(
            id = "starter.experimental-laboratory",
            version = 1,
            family = ProjectTemplateFamily.EXPERIMENTAL_LABORATORY,
            title = "Plan and record an experiment",
            summary = "Organize a planned change, a measured outcome, and the conditions around each trial.",
            intendedOutput = "A project outline for an experiment or laboratory assignment. You provide the procedure, measurements, and interpretation.",
            inputFields = listOf(
                field("assignment_brief", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "What does the assignment ask you to produce?"),
                field("research_question", ProjectTemplateInputKind.RESEARCH_QUESTION, "What condition will you change and what outcome will you measure?"),
                field("hypothesis", ProjectTemplateInputKind.HYPOTHESIS, "Optional: what result do you expect, and why?", required = false),
                field("comparison_plan", ProjectTemplateInputKind.ANALYSIS, "What will be compared, what will stay the same, and how many trials are planned?"),
                field("procedure", ProjectTemplateInputKind.ANALYSIS, "What steps and equipment will be used?"),
                field("measurement_notes", ProjectTemplateInputKind.DATA, "Which values, units, trial numbers, and measurement conditions must be recorded?"),
                field("observed_pattern", ProjectTemplateInputKind.ANALYSIS, "What pattern do the recorded measurements show?"),
                field("bounded_claim", ProjectTemplateInputKind.CLAIM, "What does this set of trials support, within the tested conditions?"),
                field("limitations", ProjectTemplateInputKind.LIMITATION, "Which uncontrolled condition or measurement limit narrows the interpretation?"),
                field("next_action", ProjectTemplateInputKind.NEXT_ACTION, "What repeat or measurement would help address that limit?"),
            ),
            steps = listOf(
                step("frame-the-test", "Frame the test", "assignment_brief", "research_question", "hypothesis"),
                step("plan-the-comparison", "Plan the comparison", "comparison_plan", "procedure"),
                step("record-measurements", "Record measurements", "measurement_notes"),
                step("interpret-the-pattern", "Interpret the pattern", "observed_pattern", "bounded_claim"),
                step("review-limits", "Review limits and next action", "limitations", "next_action"),
            ),
            methodSpecificLimitations = listOf(
                "A difference in one or a few trials does not by itself establish what caused it.",
                "Keep any conclusion within the materials, measurements, and conditions actually tested.",
                "A hypothesis is optional; include one only when it fits the assignment.",
            ),
            provenanceRequirements = listOf(
                "Record units, trial identifiers, instrument or setup details, and when measurements were taken.",
                "Keep original observations separate from later calculations and interpretations.",
            ),
            accessibilityExpectations = listOf(
                "Write units and condition names in text; do not rely on chart color or position alone.",
                "Keep procedures and measurements readable as text that can be enlarged or read by assistive technology.",
            ),
            examples = emptyList(),
            publication = ProjectTemplatePublication.BUILT_IN_STARTER,
        ),
        ProjectTemplateDefinition(
            id = "starter.observational-survey",
            version = 1,
            family = ProjectTemplateFamily.OBSERVATIONAL_SURVEY,
            title = "Observe a pattern or survey responses",
            summary = "Organize observations or responses without assigning an intervention.",
            intendedOutput = "A project outline that records who or what was observed, how information was collected, and what the data can represent.",
            inputFields = listOf(
                field("assignment_brief", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "What does the assignment ask you to produce?"),
                field("research_question", ProjectTemplateInputKind.RESEARCH_QUESTION, "What pattern, experience, or response are you studying?"),
                field("population_and_sample", ProjectTemplateInputKind.DATA, "Who or what could be included, and how will cases be selected?"),
                field("collection_plan", ProjectTemplateInputKind.ANALYSIS, "What observation protocol or survey questions will be used?"),
                field("collection_context", ProjectTemplateInputKind.DATA, "When, where, and under what conditions are observations or responses collected?"),
                field("observed_pattern", ProjectTemplateInputKind.ANALYSIS, "What response counts, measurements, or recurring patterns are present?"),
                field("bounded_claim", ProjectTemplateInputKind.CLAIM, "What can you say about this sample or setting without extending beyond it?"),
                field("sampling_limits", ProjectTemplateInputKind.LIMITATION, "Who may be missing, and how could sampling or question wording affect the pattern?"),
                field("next_action", ProjectTemplateInputKind.NEXT_ACTION, "What additional observation or response would clarify the pattern?"),
            ),
            steps = listOf(
                step("define-the-scope", "Define the question and group", "assignment_brief", "research_question", "population_and_sample"),
                step("plan-collection", "Plan observation or survey collection", "collection_plan", "collection_context"),
                step("record-responses", "Record what was observed", "observed_pattern"),
                step("describe-the-pattern", "Describe the pattern", "bounded_claim"),
                step("check-representation", "Check who the pattern represents", "sampling_limits", "next_action"),
            ),
            methodSpecificLimitations = listOf(
                "An association or response pattern does not establish cause and effect.",
                "Sampling, non-response, setting, and question wording can affect what the results represent.",
                "A survey or observation starter does not replace instructor approval, consent, or ethics requirements.",
            ),
            provenanceRequirements = listOf(
                "Record the observation or survey dates, setting, selection rules, and the exact question or protocol version.",
                "Separate missing and non-response counts from the responses that were analyzed.",
            ),
            accessibilityExpectations = listOf(
                "Use clear, neutral wording and describe response options in text.",
                "Provide a text summary for any chart or visual comparison.",
            ),
            examples = emptyList(),
            publication = ProjectTemplatePublication.BUILT_IN_STARTER,
        ),
        ProjectTemplateDefinition(
            id = "starter.literature-review",
            version = 1,
            family = ProjectTemplateFamily.LITERATURE_REVIEW,
            title = "Synthesize literature for a focused question",
            summary = "Compare selected sources and explain how their findings, methods, or limits relate.",
            intendedOutput = "A directed literature-synthesis outline. It does not claim to be a systematic or exhaustive review.",
            inputFields = listOf(
                field("assignment_brief", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "What does the assignment ask you to produce?"),
                field("research_question", ProjectTemplateInputKind.RESEARCH_QUESTION, "What focused question will the selected literature help address?"),
                field("review_scope", ProjectTemplateInputKind.ANALYSIS, "Which topic, population, time period, or source types are in scope?"),
                field("search_and_selection", ProjectTemplateInputKind.SOURCE, "Where and when did you look, and why did each source fit? Do not call the search exhaustive unless it was designed and checked as such."),
                field("comparison_dimensions", ProjectTemplateInputKind.ANALYSIS, "Which concepts, methods, outcomes, or limitations will you compare?"),
                field("synthesis", ProjectTemplateInputKind.ANALYSIS, "What themes, agreements, differences, or gaps appear across the selected sources?"),
                field("bounded_claim", ProjectTemplateInputKind.CLAIM, "What answer is supported by the sources you actually selected?"),
                field("coverage_limits", ProjectTemplateInputKind.LIMITATION, "Which sources, perspectives, or search limits may change the synthesis?"),
                field("next_action", ProjectTemplateInputKind.NEXT_ACTION, "What source check or comparison would most improve the synthesis?"),
            ),
            steps = listOf(
                step("frame-the-review", "Frame the question and scope", "assignment_brief", "research_question", "review_scope"),
                step("find-and-select", "Record where sources came from", "search_and_selection"),
                step("compare-sources", "Compare sources on shared dimensions", "comparison_dimensions", "synthesis"),
                step("write-the-synthesis", "State a source-bounded answer", "bounded_claim"),
                step("check-coverage", "Check coverage and next action", "coverage_limits", "next_action"),
            ),
            methodSpecificLimitations = listOf(
                "This starter supports a directed synthesis; it does not establish a systematic search or exhaustive coverage.",
                "A complete citation does not by itself verify a source's quality, relevance, or accuracy.",
                "Keep claims tied to the sources and passages you inspected, including disagreement and limitations.",
            ),
            provenanceRequirements = listOf(
                "Keep the source citation, retrieval location or date, and page or section locator when available.",
                "Record search terms and source-selection reasons when they matter to the assignment; never imply a database search that was not performed.",
            ),
            accessibilityExpectations = listOf(
                "Keep source notes and synthesis readable as text; use descriptive headings instead of color-only labels.",
                "Record page or section locators in text so another reader can find the cited passage.",
            ),
            examples = emptyList(),
            publication = ProjectTemplatePublication.BUILT_IN_STARTER,
        ),
        ProjectTemplateDefinition(
            id = "starter.qualitative-field-study",
            version = 1,
            family = ProjectTemplateFamily.QUALITATIVE_INTERVIEW_FIELD_STUDY,
            title = "Organize an interview or field study",
            summary = "Keep a qualitative question, collection context, anonymized notes, and interpretations connected.",
            intendedOutput = "A local organizer for an interview or field-study assignment. It does not approve participant research or ethics procedures.",
            inputFields = listOf(
                field("assignment_brief", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "What does the assignment ask you to produce?"),
                field("research_question", ProjectTemplateInputKind.RESEARCH_QUESTION, "What experience, meaning, or setting are you trying to understand?"),
                field("setting_and_context", ProjectTemplateInputKind.DATA, "What non-identifying setting and context are relevant?"),
                field("ethics_and_permission", ProjectTemplateInputKind.LIMITATION, "What instructor, consent, privacy, or ethics requirements must be met before collection? Do not enter names or contact details."),
                field("collection_protocol", ProjectTemplateInputKind.ANALYSIS, "What interview prompts or observation protocol will you use?"),
                field("deidentified_notes", ProjectTemplateInputKind.DATA, "Which de-identified notes or excerpts are relevant to the question?"),
                field("theme_and_exception", ProjectTemplateInputKind.ANALYSIS, "What tentative themes appear, and what notes do not fit them?"),
                field("bounded_interpretation", ProjectTemplateInputKind.CLAIM, "What interpretation is supported by these contextualized notes?"),
                field("interpretation_limits", ProjectTemplateInputKind.LIMITATION, "Which context, participant perspectives, or collection choices limit this interpretation?"),
                field("next_action", ProjectTemplateInputKind.NEXT_ACTION, "What additional context or review would help before drawing a broader conclusion?"),
            ),
            steps = listOf(
                step("frame-the-inquiry", "Frame the inquiry and setting", "assignment_brief", "research_question", "setting_and_context"),
                step("check-permission", "Check permission and privacy first", "ethics_and_permission"),
                step("plan-collection", "Plan interviews or field observations", "collection_protocol"),
                step("organize-notes", "Organize de-identified notes", "deidentified_notes"),
                step("interpret-with-context", "Interpret themes and exceptions", "theme_and_exception", "bounded_interpretation"),
                step("review-boundaries", "Review boundaries and next action", "interpretation_limits", "next_action"),
            ),
            methodSpecificLimitations = listOf(
                "Do not collect participant information until the instructor and applicable consent or ethics requirements are satisfied.",
                "Do not store names, contact details, or direct identifiers in project notes; use an approved privacy-safe approach.",
                "A theme is an interpretation. Keep context, exceptions, and the underlying note visible.",
            ),
            provenanceRequirements = listOf(
                "Record the protocol version and a non-identifying collection context; use approved participant codes rather than identities.",
                "Keep each excerpt linked to its note or location without copying confidential material into an unapproved service.",
            ),
            accessibilityExpectations = listOf(
                "Keep prompts and notes available as selectable text; provide text transcripts for any audio relied on in analysis.",
                "Use clear labels for speaker or field-note roles without using personal identifiers.",
            ),
            examples = emptyList(),
            publication = ProjectTemplatePublication.BUILT_IN_STARTER,
        ),
        ProjectTemplateDefinition(
            id = "starter.design-engineering",
            version = 1,
            family = ProjectTemplateFamily.DESIGN_ENGINEERING,
            title = "Design, build, and test a solution",
            summary = "Connect a defined need and requirements to design choices and observed test results.",
            intendedOutput = "A project outline that compares a prototype or design against stated criteria and explains remaining trade-offs.",
            inputFields = listOf(
                field("assignment_brief", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "What does the assignment ask you to produce?"),
                field("need_and_context", ProjectTemplateInputKind.RESEARCH_QUESTION, "What need or technical problem is in scope, and for whom? Avoid unnecessary personal data."),
                field("requirements", ProjectTemplateInputKind.ANALYSIS, "What requirements or success criteria must the solution meet?"),
                field("constraints", ProjectTemplateInputKind.LIMITATION, "What resources, safety, time, accessibility, or technical limits apply?"),
                field("design_decisions", ProjectTemplateInputKind.ANALYSIS, "Which design choices were made, and what alternatives were considered?"),
                field("prototype_version", ProjectTemplateInputKind.DATA, "What prototype or implementation version was tested?"),
                field("test_plan", ProjectTemplateInputKind.ANALYSIS, "What test conditions and criteria will be used?"),
                field("observed_results", ProjectTemplateInputKind.DATA, "What happened during each test, including conditions and failures?"),
                field("criteria_comparison", ProjectTemplateInputKind.ANALYSIS, "How did observed results compare with each stated criterion?"),
                field("bounded_claim", ProjectTemplateInputKind.CLAIM, "What does the tested version demonstrate within these conditions?"),
                field("remaining_limits", ProjectTemplateInputKind.LIMITATION, "What remains untested or could affect performance or user suitability?"),
                field("next_action", ProjectTemplateInputKind.NEXT_ACTION, "What test or design change would reduce the most important remaining limit?"),
            ),
            steps = listOf(
                step("define-the-need", "Define the need and context", "assignment_brief", "need_and_context"),
                step("set-criteria", "Set requirements and constraints", "requirements", "constraints"),
                step("record-design", "Record design and prototype", "design_decisions", "prototype_version"),
                step("test-the-prototype", "Test against stated criteria", "test_plan", "observed_results", "criteria_comparison"),
                step("review-tradeoffs", "Review trade-offs and next action", "bounded_claim", "remaining_limits", "next_action"),
            ),
            methodSpecificLimitations = listOf(
                "Passing a stated test does not by itself establish broad usability, safety, reliability, or real-world impact.",
                "Separate observed test results from design expectations and interpretations.",
                "User feedback or safety claims may require appropriate consent, specialist review, and testing beyond this organizer.",
            ),
            provenanceRequirements = listOf(
                "Record the requirement source, prototype or design version, test conditions, and the criteria used.",
                "Keep unsuccessful tests and deviations alongside successful results; do not report only the best run.",
            ),
            accessibilityExpectations = listOf(
                "State accessibility-related requirements explicitly and report which were actually tested.",
                "Pair diagrams or measurements with text labels and units that can be read without relying on color alone.",
            ),
            examples = emptyList(),
            publication = ProjectTemplatePublication.BUILT_IN_STARTER,
        ),
    )

    fun forFamily(family: ProjectTemplateFamily): ProjectTemplateDefinition =
        templates.single { it.family == family }

    fun find(id: String, version: Int): ProjectTemplateDefinition? =
        templates.singleOrNull { it.id == id && it.version == version }

    fun isCanonical(template: ProjectTemplateDefinition): Boolean =
        find(template.id, template.version) == template

    private fun field(
        id: String,
        kind: ProjectTemplateInputKind,
        label: String,
        required: Boolean = true,
    ) = ProjectTemplateInputField(id = id, kind = kind, label = label, required = required)

    private fun step(id: String, title: String, vararg fieldIds: String) =
        ProjectTemplateStep(id = id, title = title, inputFieldIds = fieldIds.toList())
}
