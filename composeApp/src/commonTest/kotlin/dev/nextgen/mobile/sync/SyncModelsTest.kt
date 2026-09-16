package dev.nextgen.mobile.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncModelsTest {
    @Test
    fun validRedactedPushEnvelopeIsAccepted() {
        val result = validEnvelope().validate()

        assertTrue(result.isValid)
        assertEquals(null, result.errorCode)
        assertFalse(validEnvelope().toString().contains("claimText"))
    }

    @Test
    fun emptyIdempotencyKeyIsRejected() {
        val result = validEnvelope().copy(idempotencyKey = "").validate()

        assertFalse(result.isValid)
        assertEquals("INVALID_IDEMPOTENCY_KEY", result.errorCode)
    }

    @Test
    fun cursorAndCommandBatchAreBounded() {
        val negativeCursor = validEnvelope().copy(cursor = -1).validate()
        val oversizedCursor = validEnvelope().copy(cursor = MAX_SYNC_CURSOR + 1).validate()
        val oversizedBatch = validEnvelope().copy(
            commands = List(MAX_SYNC_COMMANDS + 1) { validCommand(it) },
        ).validate()

        assertEquals("INVALID_CURSOR", negativeCursor.errorCode)
        assertEquals("INVALID_CURSOR", oversizedCursor.errorCode)
        assertEquals("INVALID_COMMAND_BATCH", oversizedBatch.errorCode)
    }

    @Test
    fun syncRequiresExplicitConsent() {
        val result = validEnvelope().copy(consent = SyncConsent.NOT_GRANTED).validate()

        assertFalse(result.isValid)
        assertEquals("CONSENT_REQUIRED", result.errorCode)
    }

    @Test
    fun duplicate_command_ids_are_rejected_before_a_push_is_built() {
        val command = validCommand(0)
        val result = validEnvelope().copy(commands = listOf(command, command)).validate()

        assertFalse(result.isValid)
        assertEquals("INVALID_COMMAND_BATCH", result.errorCode)
    }

    @Test
    fun commandShapeContainsDigestOnlyAndRejectsMalformedDigest() {
        val result = validEnvelope().copy(
            commands = listOf(validCommand(0).copy(snapshotDigest = "not-a-digest")),
        ).validate()

        assertFalse(result.isValid)
        assertEquals("INVALID_SNAPSHOT_DIGEST", result.errorCode)
        assertFalse(result.message.orEmpty().contains("not-a-digest"))
    }

    private fun validEnvelope() = SyncPushEnvelope(
        cursor = 40,
        consent = SyncConsent.GRANTED,
        idempotencyKey = "push_20260911_0001",
        commands = listOf(validCommand(0)),
    )

    private fun validCommand(index: Int) = SyncCommandIntent(
        commandId = "123e4567-e89b-42d3-a456-42661417400${index % 10}",
        attemptId = "223e4567-e89b-42d3-a456-42661417400${index % 10}",
        caseVersionId = "M0_T2:1",
        revisionNumber = 0,
        snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    )
}
