package dev.nextgen.mobile.surfaces

import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.storage.ConclusionSessionPhase
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloSurfacePresentationTest {
    @Test
    fun historyIsEmptyUntilAnEvidenceChangeComparisonIsCompleted() {
        assertEquals(HistorySurfaceAvailability.EMPTY, historySurfaceAvailability(null))
        assertEquals(
            HistorySurfaceAvailability.EMPTY,
            historySurfaceAvailability(snapshot(ConclusionSessionPhase.DRAFTING)),
        )
        assertEquals(
            HistorySurfaceAvailability.AVAILABLE,
            historySurfaceAvailability(snapshot(ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY)),
        )
    }

    @Test
    fun cloudDisclosureExplainsTheOptionalLocalFirstBoundary() {
        val disclosure = cloudSyncDisclosure()
        assertEquals(
            true,
            disclosure.contains("optional") &&
                disclosure.contains("verified account") &&
                disclosure.contains("draft text"),
        )
    }

    @Test
    fun disclosureSemanticsDescribeBothActionAndCurrentState() {
        assertEquals("Expand Reading and motion", disclosureActionLabel("Reading and motion", expanded = false))
        assertEquals("Collapsed", disclosureStateDescription(expanded = false))
        assertEquals("Collapse Reading and motion", disclosureActionLabel("Reading and motion", expanded = true))
        assertEquals("Expanded", disclosureStateDescription(expanded = true))
    }

    private fun snapshot(phase: ConclusionSessionPhase): ConclusionSessionSnapshot =
        ConclusionSessionSnapshot(
            phase = phase,
            initialDraft = ConclusionDraft(caseId = "case.base"),
            currentDraft = ConclusionDraft(caseId = "case.current"),
        )
}
