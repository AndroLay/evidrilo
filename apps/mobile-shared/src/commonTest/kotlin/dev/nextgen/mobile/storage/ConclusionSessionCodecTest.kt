package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConclusionSessionCodecTest {
    @Test
    fun roundTripPreservesDraftAndSessionPhase() {
        val draft = ConclusionDraft(
            caseId = ConclusionCases.M0_T2.id,
            relation = ConclusionRelation.OBSERVED_DIFFERENCE,
            evidenceRefs = listOf("OBS-WARM-01", "OBS-COLD-01"),
            claimText = "Warm water dissolved faster; this note includes a | separator.",
            scope = ConclusionScope.LIMITED_COMPARISON,
            limitationRefs = listOf("LIMIT-TRIAL-01"),
            limitationNote = "One trial per condition\nlimits generalization.",
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "Repeat the trials to check whether the pattern persists.",
        )
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.REVISION,
            initialDraft = draft,
            currentDraft = draft.copy(claimText = draft.claimText + " Revised."),
        )

        assertEquals(snapshot, ConclusionSessionCodec.decode(ConclusionSessionCodec.encode(snapshot)))
    }

    @Test
    fun malformedStorageDoesNotBecomeAUserDraft() {
        assertNull(ConclusionSessionCodec.decode("not-a-valid-session"))
        assertNull(ConclusionSessionCodec.decode("SUMMARY|missing-draft"))
    }

    @Test
    fun an_interrupted_write_is_rejected_instead_of_restoring_a_partial_draft() {
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.DRAFTING,
            initialDraft = ConclusionDraft(
                caseId = ConclusionCases.M0_T2.id,
                claimText = "The warm sample dissolved first.",
            ),
            currentDraft = ConclusionDraft(
                caseId = ConclusionCases.M0_T2.id,
                claimText = "The warm sample dissolved first.",
            ),
        )
        val encoded = ConclusionSessionCodec.encode(snapshot)

        assertNull(ConclusionSessionCodec.decode(encoded.dropLast(1)))
        assertNull(ConclusionSessionCodec.decode(encoded.substring(0, encoded.length / 2)))
    }

    @Test
    fun the_existing_v1_record_shape_remains_readable_for_migration() {
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.DRAFTING,
            initialDraft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id),
            currentDraft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id),
        )

        assertEquals(snapshot, ConclusionSessionCodec.decode(ConclusionSessionCodec.encode(snapshot)))
    }

    @Test
    fun semanticallyCorruptStorageFailsClosed() {
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.DRAFTING,
            initialDraft = ConclusionDraft(
                caseId = ConclusionCases.M0_T2.id,
                relation = ConclusionRelation.OBSERVED_DIFFERENCE,
            ),
            currentDraft = ConclusionDraft(
                caseId = ConclusionCases.M0_T2.id,
                relation = ConclusionRelation.OBSERVED_DIFFERENCE,
            ),
        )
        val encoded = ConclusionSessionCodec.encode(snapshot)
        val firstRecordDelimiter = encoded.indexOf('|')
        val malformedListEscape = encoded.substring(0, firstRecordDelimiter) +
            "\\\\" + encoded.substring(firstRecordDelimiter)

        assertNull(
            ConclusionSessionCodec.decode(
                encoded.replace("OBSERVED_DIFFERENCE", "UNKNOWN_RELATION"),
            ),
        )
        assertNull(ConclusionSessionCodec.decode(malformedListEscape))
        assertNull(
            ConclusionSessionCodec.decode(encoded + "x".repeat(8_192)),
        )
    }

    @Test
    fun challengePhasesRoundTripWithTheSameSnapshotShape() {
        val base = ConclusionDraft(
            caseId = ConclusionCases.M0_T2.id,
            relation = ConclusionRelation.OBSERVED_DIFFERENCE,
            evidenceRefs = listOf("OBS-WARM-01", "OBS-ROOM-01"),
            claimText = "The warm sample dissolved before the room-temperature sample.",
            scope = ConclusionScope.LIMITED_COMPARISON,
            limitationRefs = listOf("LIMIT-TRIAL-01"),
            limitationNote = "One trial limits this comparison.",
            implication = ConclusionImplication.REPEAT_TRIALS,
            implicationReason = "Repeat the trials to check the pattern.",
        )
        val challenge = base.copy(
            caseId = ConclusionCases.EVIDENCE_CHANGE.id,
            claimText = "In this round, the warm sample dissolved before the room-temperature sample.",
        )

        ConclusionSessionPhase.entries
            .filter { it.name.startsWith("EVIDENCE_CHANGE_") }
            .forEach { phase ->
                val snapshot = ConclusionSessionSnapshot(phase, base, challenge)
                assertEquals(snapshot, ConclusionSessionCodec.decode(ConclusionSessionCodec.encode(snapshot)))
            }
    }

    @Test
    fun phaseAndCaseMismatchFailsClosed() {
        val mainDraft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id)
        val challengeDraft = ConclusionDraft(caseId = ConclusionCases.EVIDENCE_CHANGE.id)

        assertNull(
            ConclusionSessionCodec.decode(
                ConclusionSessionCodec.encode(
                    ConclusionSessionSnapshot(
                        phase = ConclusionSessionPhase.DRAFTING,
                        initialDraft = mainDraft,
                        currentDraft = challengeDraft,
                    ),
                ),
            ),
        )
        assertNull(
            ConclusionSessionCodec.decode(
                ConclusionSessionCodec.encode(
                    ConclusionSessionSnapshot(
                        phase = ConclusionSessionPhase.EVIDENCE_CHANGE_FEEDBACK,
                        initialDraft = challengeDraft,
                        currentDraft = challengeDraft,
                    ),
                ),
            ),
        )
        assertNull(
            ConclusionSessionCodec.decode(
                ConclusionSessionCodec.encode(
                    ConclusionSessionSnapshot(
                        phase = ConclusionSessionPhase.SUMMARY,
                        initialDraft = mainDraft,
                        currentDraft = challengeDraft,
                    ),
                ),
            ),
        )
    }

    @Test
    fun storageEncodingRejectsRecordsThatWouldBeUnrestorable() {
        val valid = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.DRAFTING,
            initialDraft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id),
            currentDraft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id),
        )
        val oversized = valid.copy(
            currentDraft = valid.currentDraft.copy(claimText = "x".repeat(8_193)),
        )

        assertEquals(ConclusionSessionCodec.encode(valid), ConclusionSessionCodec.encodeForStorage(valid))
        assertEquals(null, ConclusionSessionCodec.encodeForStorage(oversized))
    }

    @Test
    fun storageEncodingRejectsSemanticallyInvalidSnapshotsBeforeAWrite() {
        val invalid = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.DRAFTING,
            initialDraft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id),
            currentDraft = ConclusionDraft(caseId = ConclusionCases.EVIDENCE_CHANGE.id),
        )

        assertEquals(null, ConclusionSessionCodec.encodeForStorage(invalid))
    }
}
