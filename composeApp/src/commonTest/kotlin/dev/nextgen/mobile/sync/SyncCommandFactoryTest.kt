package dev.nextgen.mobile.sync

import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncCommandFactoryTest {
    @Test
    fun bundled_local_case_uses_the_canonical_backend_version_id() {
        val command = syncCommandFor(
            commandType = SyncCommandType.ATTEMPT_STARTED,
            attemptId = "123e4567-e89b-42d3-a456-426614174001",
            draft = ConclusionDraft(caseId = ConclusionCases.M0_T2.id),
            revisionNumber = 0,
            commandId = "123e4567-e89b-42d3-a456-426614174000",
            clientOccurredAt = "2026-09-13T10:00:00Z",
        )

        assertEquals("M0_T2:1", command.caseVersionId)
    }

    @Test
    fun command_factory_hashes_local_draft_but_never_places_it_in_the_command() {
        val draft = ConclusionDraft(
            caseId = "M0_T2:1",
            claimText = "A bounded learner-authored claim",
            limitationNote = "One trial only",
        )

        val command = syncCommandFor(
            commandType = SyncCommandType.ATTEMPT_SUBMITTED,
            attemptId = "123e4567-e89b-42d3-a456-426614174001",
            draft = draft,
            revisionNumber = 0,
            commandId = "123e4567-e89b-42d3-a456-426614174000",
            clientOccurredAt = "2026-09-13T10:00:00Z",
        )

        assertTrue(command.validate().isValid)
        assertEquals(64, command.snapshotDigest.length)
        assertFalse(command.toString().contains(draft.claimText))
        assertFalse(command.toString().contains(draft.limitationNote))
    }

    @Test
    fun changing_a_local_draft_changes_its_digest_without_changing_case_identity() {
        val first = ConclusionDraft(caseId = "M0_T2:1", claimText = "First")
        val second = first.copy(claimText = "Second")

        assertEquals(first.caseId, second.caseId)
        assertTrue(snapshotDigestFor(first) != snapshotDigestFor(second))
    }
}
