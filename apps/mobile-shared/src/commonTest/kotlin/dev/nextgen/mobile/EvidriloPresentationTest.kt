package dev.nextgen.mobile

import dev.nextgen.mobile.domain.conclusion.ConclusionCheck
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionField
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloPresentationTest {
    @Test
    fun statusLabelsAreClearAndNotGradeLanguage() {
        assertEquals("Incomplete", ConclusionStatus.INCOMPLETE.displayLabel())
        assertEquals("Action needed", ConclusionStatus.ACTION_REQUIRED.displayLabel())
        assertEquals("Pass", ConclusionStatus.PASS.displayLabel())
        assertEquals("Cannot assess from the supplied information", ConclusionStatus.CANNOT_ASSESS.displayLabel())
    }

    @Test
    fun checkAndFieldLabelsExplainTheVisibleContract() {
        assertEquals("Goal connection", ConclusionCheck.GOAL_CONNECTEDNESS.displayLabel())
        assertEquals("Evidence anchoring", ConclusionCheck.EVIDENCE_ANCHORING.displayLabel())
        assertEquals("Scope and uncertainty", ConclusionCheck.SCOPE_UNCERTAINTY.displayLabel())
        assertEquals("Actionable implication", ConclusionCheck.ACTIONABLE_IMPLICATION.displayLabel())
        assertEquals("Claim text", ConclusionField.CLAIM_TEXT.displayLabel())
    }

    @Test
    fun factTypeLabelsAvoidEnumNames() {
        assertEquals("Aim", ConclusionFactType.AIM.displayLabel())
        assertEquals("Observation", ConclusionFactType.OBSERVATION.displayLabel())
        assertEquals("Limitation", ConclusionFactType.LIMITATION.displayLabel())
        assertEquals("Boundary", ConclusionFactType.BOUNDARY.displayLabel())
    }

    @Test
    fun evidenceReferenceLabelUsesReadableCaseLanguageInsteadOfInternalId() {
        val observation = ConclusionFact(
            id = "OBS-WARM-01",
            type = ConclusionFactType.OBSERVATION,
            text = "Warm water: 32 seconds.",
            displayLabel = "Warm",
        )
        val limitation = ConclusionFact(
            id = "LIMIT-TRIAL-01",
            type = ConclusionFactType.LIMITATION,
            text = "Each condition was measured once.",
        )

        assertEquals("Warm · Observation", observation.learnerFacingEvidenceLabel())
        assertEquals("Limitation", limitation.learnerFacingEvidenceLabel())
    }
}
