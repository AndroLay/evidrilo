package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectTemplateExampleScenarioLabelTest {
    @Test
    fun scenario_label_distinguishes_nominal_edge_and_legacy_unclassified_examples() {
        assertEquals(
            "Normal workflow (not a correctness label)",
            ProjectTemplateExample("normal", "Example.", false, ProjectTemplateExampleKind.NORMAL).scenarioLabel(),
        )
        assertEquals(
            "Edge or conflicting scenario",
            ProjectTemplateExample(
                "edge",
                "Example.",
                false,
                ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
            ).scenarioLabel(),
        )
        assertEquals(
            "Scenario type not recorded in this legacy template",
            ProjectTemplateExample("legacy", "Example.", true).scenarioLabel(),
        )
    }
}
