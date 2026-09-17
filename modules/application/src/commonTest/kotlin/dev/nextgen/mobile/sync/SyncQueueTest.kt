package dev.nextgen.mobile.sync

import dev.nextgen.mobile.account.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncQueueTest {
    @Test
    fun queue_codec_round_trips_only_redacted_command_intents() {
        val command = SyncCommandIntent(
            commandId = "123e4567-e89b-42d3-a456-426614174000",
            attemptId = "123e4567-e89b-42d3-a456-426614174001",
            caseVersionId = "M0_T2:1",
            revisionNumber = 1,
            snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            commandType = SyncCommandType.REVISION_RECORDED,
            clientOccurredAt = "2026-09-13T10:00:00Z",
        )
        val snapshot = SyncQueueSnapshot(cursor = 40, pending = listOf(command))

        val encoded = SyncQueueCodec.encode(snapshot)
        val decoded = SyncQueueCodec.decode(encoded)

        assertEquals(snapshot, decoded)
        assertFalse(encoded.contains("claimText"))
        assertFalse(encoded.contains("rawDraftText"))
    }

    @Test
    fun queue_codec_rejects_corrupt_or_oversized_state() {
        assertNull(SyncQueueCodec.decode("not-a-queue"))
        assertNull(
            SyncQueueCodec.decode(
                SyncQueueCodec.encode(
                    SyncQueueSnapshot(
                        cursor = 0,
                        pending = List(MAX_SYNC_COMMANDS + 1) { validCommand(it) },
                    ),
                ),
            ),
        )
    }

    @Test
    fun sync_timestamp_must_be_a_parseable_instant_before_delimited_storage() {
        val malformed = validCommand(0).copy(clientOccurredAt = "2026-09-13T10:00:00Z|raw-draft")

        assertFalse(malformed.validate().isValid)
        assertEquals("INVALID_CLIENT_OCCURRED_AT", malformed.validate().errorCode)
    }

    @Test
    fun retry_policy_is_bounded_and_does_not_retry_auth_or_conflict_results() {
        val transient = SyncGatewayResult.Failed("OFFLINE", "offline", retryable = true)
        val rejected = SyncGatewayResult.Failed("SYNC_REQUEST_REJECTED", "rejected", retryable = false)

        assertTrue(SyncRetryPolicy.shouldRetry(transient, attempt = 0))
        assertTrue(SyncRetryPolicy.shouldRetry(transient, attempt = SyncRetryPolicy.MAX_RETRIES - 1))
        assertFalse(SyncRetryPolicy.shouldRetry(transient, attempt = SyncRetryPolicy.MAX_RETRIES))
        assertFalse(SyncRetryPolicy.shouldRetry(rejected, attempt = 0))
        assertFalse(
            SyncRetryPolicy.shouldRetry(
                SyncGatewayResult.Deferred(SyncDeferralReason.AUTH_REQUIRED),
                attempt = 0,
            ),
        )
        assertEquals(250L, SyncRetryPolicy.delayMillis(attempt = 0))
        assertEquals(4_000L, SyncRetryPolicy.delayMillis(attempt = 8))
    }

    @Test
    fun coordinator_deduplicates_local_commands_and_retains_server_conflicts_for_review() {
        val first = validCommand(0)
        val second = validCommand(1)
        val store = MemorySyncQueueStore()
        val transport = QueueSyncTransport(
            """
            {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174000","outcome":"accepted","serverSequence":41},{"commandId":"123e4567-e89b-42d3-a456-426614174001","outcome":"conflict","reasonCode":"ATTEMPT_REVISION_CONFLICT"}],"nextCursor":41,"requestId":"req-sync-002"}
            """.trimIndent(),
        )
        val coordinator = coordinator(store, transport)

        assertEquals(SyncQueueMutation.ENQUEUED, coordinator.enqueue(ACCOUNT_ID, first))
        assertEquals(SyncQueueMutation.DUPLICATE, coordinator.enqueue(ACCOUNT_ID, first))
        assertEquals(SyncQueueMutation.ENQUEUED, coordinator.enqueue(ACCOUNT_ID, second))

        val result = runSuspendTest { coordinator.flush(ACCOUNT_ID, SyncConsent.GRANTED) }
        val completed = assertIs<SyncQueueFlushResult.Completed>(result)

        assertEquals(1, completed.pendingCount)
        assertEquals(listOf(second.commandId), completed.conflictCommandIds)
        assertEquals(41L, store.load().value?.cursor)
        assertEquals(listOf(second), store.load().value?.pending)
    }

    @Test
    fun coordinator_retries_transient_transport_failures_without_requiring_the_free_flow() {
        val store = MemorySyncQueueStore()
        val transport = QueueSyncTransport(
            "{}",
            """
            {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174000","outcome":"accepted","serverSequence":41}],"nextCursor":41,"requestId":"req-sync-003"}
            """.trimIndent(),
            statuses = intArrayOf(503, 200),
        )
        val coordinator = coordinator(store, transport)
        coordinator.enqueue(ACCOUNT_ID, validCommand(0))
        val delays = mutableListOf<Long>()

        val result = runSuspendTest {
            coordinator.flush(ACCOUNT_ID, SyncConsent.GRANTED) { delays += it }
        }

        assertIs<SyncQueueFlushResult.Completed>(result)
        assertEquals(listOf(250L), delays)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun coordinator_does_not_resurrect_a_cleared_queue_after_an_in_flight_flush() {
        val store = MemorySyncQueueStore()
        val transport = QueueSyncTransport(
            """
            {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174000","outcome":"accepted","serverSequence":41}],"nextCursor":41,"requestId":"req-sync-004"}
            """.trimIndent(),
        )
        val coordinator = coordinator(store, transport)
        coordinator.enqueue(ACCOUNT_ID, validCommand(0))
        transport.beforeResponse = { coordinator.clear() }

        val result = runSuspendTest {
            coordinator.flush(ACCOUNT_ID, SyncConsent.GRANTED)
        }

        val failed = assertIs<SyncQueueFlushResult.Failed>(result)
        assertEquals("SYNC_STATE_CHANGED", failed.code)
        assertNull(store.load().value?.accountId)
        assertTrue(store.load().value?.pending.isNullOrEmpty())
    }

    @Test
    fun coordinator_does_not_commit_a_pull_after_consent_clear_during_request() {
        val store = MemorySyncQueueStore()
        val transport = QueueSyncTransport(
            """
            {"schema":"evidrilo.sync-pull","version":"1","cursor":0,"nextCursor":0,"hasMore":false,"changes":[],"requestId":"req-sync-005"}
            """.trimIndent(),
        )
        val coordinator = coordinator(store, transport)
        transport.beforeResponse = { coordinator.clear() }

        val result = runSuspendTest {
            coordinator.pull(ACCOUNT_ID, SyncConsent.GRANTED)
        }

        val failed = assertIs<SyncQueuePullResult.Failed>(result)
        assertEquals("SYNC_STATE_CHANGED", failed.code)
        assertNull(store.load().value?.accountId)
        assertEquals(0L, store.load().value?.cursor)
    }

    @Test
    fun sync_does_not_push_after_a_pull_is_invalidated_by_a_failed_clear() {
        val store = MemorySyncQueueStore(
            clearResult = dev.nextgen.mobile.storage.LocalStorageWriteResult.FAILED,
        )
        val transport = QueueSyncTransport(
            """
            {"schema":"evidrilo.sync-pull","version":"1","cursor":0,"nextCursor":0,"hasMore":false,"changes":[],"requestId":"req-sync-009"}
            """.trimIndent(),
            """
            {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174000","outcome":"accepted","serverSequence":41}],"nextCursor":41,"requestId":"req-sync-010"}
            """.trimIndent(),
        )
        val coordinator = coordinator(store, transport)
        coordinator.enqueue(ACCOUNT_ID, validCommand(0))
        transport.beforeResponse = { coordinator.clear() }

        val result = runSuspendTest {
            coordinator.sync(ACCOUNT_ID, SyncConsent.GRANTED)
        }

        val pull = assertIs<SyncQueuePullResult.Failed>(result.pull)
        assertEquals("SYNC_STATE_CHANGED", pull.code)
        assertIs<SyncQueueFlushResult.Empty>(result.flush)
        assertEquals(1, transport.requests.size)
        assertEquals(1, store.load().value?.pending?.size)
    }

    @Test
    fun failed_consent_clear_keeps_the_visible_pending_count_honest() {
        val outcome = syncConsentClearOutcome(
            dev.nextgen.mobile.storage.LocalStorageWriteResult.FAILED,
            pendingCountAfterReload = 2,
        )

        assertEquals(2, outcome.pendingCount)
        assertTrue(outcome.message.contains("could not be cleared"))
    }

    @Test
    fun coordinator_keeps_a_new_local_command_when_flush_started_with_an_older_snapshot() {
        val store = MemorySyncQueueStore()
        val transport = QueueSyncTransport(
            """
            {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174000","outcome":"accepted","serverSequence":41}],"nextCursor":41,"requestId":"req-sync-006"}
            """.trimIndent(),
        )
        val coordinator = coordinator(store, transport)
        val first = validCommand(0)
        val second = validCommand(1)
        coordinator.enqueue(ACCOUNT_ID, first)
        var mutation: SyncQueueMutation? = null
        transport.beforeResponse = {
            mutation = coordinator.enqueue(ACCOUNT_ID, second)
        }

        val result = runSuspendTest {
            coordinator.flush(ACCOUNT_ID, SyncConsent.GRANTED)
        }

        assertTrue(
            result is SyncQueueFlushResult.Failed,
            "result=$result mutation=$mutation stored=${store.load().value}",
        )
        val failed = result as SyncQueueFlushResult.Failed
        assertEquals(SyncQueueMutation.ENQUEUED, mutation)
        assertEquals("SYNC_STATE_CHANGED", failed.code)
        assertEquals(listOf(first, second), store.load().value?.pending)
    }

    @Test
    fun coordinator_keeps_commands_when_push_cursor_regresses() {
        val store = MemorySyncQueueStore(
            SyncQueueSnapshot(cursor = 40, pending = emptyList()),
        )
        val transport = QueueSyncTransport(
            """
            {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174000","outcome":"accepted","serverSequence":41}],"nextCursor":39,"requestId":"req-sync-007"}
            """.trimIndent(),
        )
        val coordinator = coordinator(store, transport)
        val command = validCommand(0)
        assertEquals(SyncQueueMutation.ENQUEUED, coordinator.enqueue(ACCOUNT_ID, command))

        val result = runSuspendTest {
            coordinator.flush(ACCOUNT_ID, SyncConsent.GRANTED)
        }

        val failed = assertIs<SyncQueueFlushResult.Failed>(result)
        assertEquals("SYNC_CURSOR_REGRESSION", failed.code)
        assertEquals(40L, store.load().value?.cursor)
        assertEquals(listOf(command), store.load().value?.pending)
    }

    @Test
    fun coordinator_rejects_push_results_for_commands_not_in_the_request() {
        val store = MemorySyncQueueStore()
        val transport = QueueSyncTransport(
            """
            {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174003","outcome":"accepted","serverSequence":41}],"nextCursor":41,"requestId":"req-sync-008"}
            """.trimIndent(),
        )
        val coordinator = coordinator(store, transport)
        val command = validCommand(0)
        coordinator.enqueue(ACCOUNT_ID, command)

        val result = runSuspendTest {
            coordinator.flush(ACCOUNT_ID, SyncConsent.GRANTED)
        }

        val failed = assertIs<SyncQueueFlushResult.Failed>(result)
        assertEquals("SYNC_RESPONSE_MISMATCH", failed.code)
        assertEquals(listOf(command), store.load().value?.pending)
    }

    private fun coordinator(store: MemorySyncQueueStore, transport: QueueSyncTransport) =
        SyncQueueCoordinator(
            store = store,
            gateway = SyncGateway(
                configuration = SyncClientConfiguration("https://api.example.test"),
                transport = transport,
                secureSessionStore = QueueMemorySecureSessionStore(
                    dev.nextgen.mobile.security.StoredAccountSession(
                        dev.nextgen.mobile.account.AccountSummary(ACCOUNT_ID, true),
                        dev.nextgen.mobile.security.SecureSessionMaterial("access-token", 200),
                    ),
                ),
                nowEpochSeconds = { 100 },
            ),
        )

    private companion object {
        const val ACCOUNT_ID = "123e4567-e89b-42d3-a456-426614174002"
    }

    private fun validCommand(index: Int) = SyncCommandIntent(
        commandId = "123e4567-e89b-42d3-a456-42661417400${index % 10}",
        attemptId = "223e4567-e89b-42d3-a456-42661417400${index % 10}",
        caseVersionId = "M0_T2:1",
        revisionNumber = 0,
        snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        clientOccurredAt = "2026-09-13T10:00:00Z",
    )
}

private class MemorySyncQueueStore(
    private var snapshot: SyncQueueSnapshot = SyncQueueSnapshot(0, emptyList()),
    private val clearResult: dev.nextgen.mobile.storage.LocalStorageWriteResult =
        dev.nextgen.mobile.storage.LocalStorageWriteResult.CLEARED,
) : SyncQueueStore {
    override fun load() = dev.nextgen.mobile.storage.LocalStorageReadResult.Success(snapshot)

    override fun save(snapshot: SyncQueueSnapshot) = run {
        this.snapshot = snapshot
        dev.nextgen.mobile.storage.LocalStorageWriteResult.SAVED
    }

    override fun clear() = if (clearResult != dev.nextgen.mobile.storage.LocalStorageWriteResult.CLEARED) {
        clearResult
    } else {
        snapshot = SyncQueueSnapshot(0, emptyList())
        dev.nextgen.mobile.storage.LocalStorageWriteResult.CLEARED
    }
}

private data class QueueRequest(
    val url: String,
    val body: String,
)

private class QueueSyncTransport(
    private vararg val bodies: String,
    private val statuses: IntArray = IntArray(bodies.size) { 200 },
) : dev.nextgen.mobile.account.AccountHttpTransport {
    val requests = mutableListOf<QueueRequest>()
    var beforeResponse: (() -> Unit)? = null
    private var index = 0

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): dev.nextgen.mobile.account.AccountHttpResponse {
        requests += QueueRequest(url, body)
        val responseBody = bodies.getOrElse(index) { bodies.lastOrNull().orEmpty() }
        val status = statuses.getOrElse(index) { statuses.lastOrNull() ?: 200 }
        index += 1
        beforeResponse?.invoke()
        return dev.nextgen.mobile.account.AccountHttpResponse(status, responseBody)
    }
}

private class QueueMemorySecureSessionStore(
    private var value: dev.nextgen.mobile.security.StoredAccountSession?,
) : dev.nextgen.mobile.security.SecureSessionStore {
    override fun read(): dev.nextgen.mobile.security.StoredAccountSession? = value

    override fun write(session: dev.nextgen.mobile.security.StoredAccountSession) {
        value = session
    }

    override fun clear() {
        value = null
    }
}
