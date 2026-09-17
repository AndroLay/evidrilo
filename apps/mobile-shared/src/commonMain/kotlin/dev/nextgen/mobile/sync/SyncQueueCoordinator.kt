package dev.nextgen.mobile.sync

import dev.nextgen.mobile.storage.LocalStorageStatus
import dev.nextgen.mobile.storage.LocalStorageWriteResult

internal enum class SyncQueueMutation {
    ENQUEUED,
    DUPLICATE,
    CONFLICT,
    INVALID,
    QUEUE_FULL,
    ACCOUNT_MISMATCH,
    STORE_UNAVAILABLE,
}

internal sealed interface SyncQueueFlushResult {
    data object Empty : SyncQueueFlushResult

    data class Completed(
        val response: SyncPushResponse,
        val pendingCount: Int,
        val conflictCommandIds: List<String>,
    ) : SyncQueueFlushResult

    data class Deferred(
        val result: SyncGatewayResult,
        val pendingCount: Int,
    ) : SyncQueueFlushResult

    data class Failed(
        val code: String,
        val pendingCount: Int,
    ) : SyncQueueFlushResult
}

internal sealed interface SyncQueuePullResult {
    data class Completed(val response: SyncPullResponse) : SyncQueuePullResult

    data class Deferred(val result: SyncGatewayResult) : SyncQueuePullResult

    data class Failed(val code: String) : SyncQueuePullResult
}

internal data class SyncQueueSyncResult(
    val pull: SyncQueuePullResult,
    val flush: SyncQueueFlushResult,
)

internal data class SyncConsentClearOutcome(
    val pendingCount: Int,
    val message: String,
)

internal fun syncConsentClearOutcome(
    result: LocalStorageWriteResult,
    pendingCountAfterReload: Int,
): SyncConsentClearOutcome = if (result == LocalStorageWriteResult.CLEARED) {
    SyncConsentClearOutcome(
        pendingCount = 0,
        message = "Cloud sync is off; pending progress metadata was cleared locally.",
    )
} else {
    SyncConsentClearOutcome(
        pendingCount = pendingCountAfterReload.coerceAtLeast(0),
        message = "Cloud sync is off; pending progress could not be cleared locally; no progress was sent.",
    )
}

/**
 * Owns the local queue and cursor. Conflicting commands stay local so the
 * product can later offer a human-reviewed resolution instead of overwriting
 * a learner snapshot silently.
 */
internal class SyncQueueCoordinator(
    private val store: SyncQueueStore,
    private val gateway: SyncGateway,
) {
    /**
     * Invalidates any request that is currently waiting on the network before
     * removing the local queue. This prevents an older response from restoring
     * progress after sign-out, account switch, or consent revocation.
     */
    private var stateGeneration: Long = 0

    fun clear(): LocalStorageWriteResult {
        stateGeneration = nextGeneration(stateGeneration)
        return store.clear()
    }

    fun enqueue(accountId: String, command: SyncCommandIntent): SyncQueueMutation {
        if (!isValidSyncUuid(accountId) || !command.validate().isValid) {
            return SyncQueueMutation.INVALID
        }
        val loaded = store.load()
        val current = loaded.value ?: return SyncQueueMutation.STORE_UNAVAILABLE
        if (current.accountId != null && current.accountId != accountId) {
            return SyncQueueMutation.ACCOUNT_MISMATCH
        }
        val existing = current.pending.firstOrNull { it.commandId == command.commandId }
        if (existing != null) {
            return if (existing == command) SyncQueueMutation.DUPLICATE else SyncQueueMutation.CONFLICT
        }
        if (current.pending.size >= MAX_SYNC_COMMANDS) return SyncQueueMutation.QUEUE_FULL
        return when (saveSnapshot(current.copy(accountId = accountId, pending = current.pending + command))) {
            dev.nextgen.mobile.storage.LocalStorageWriteResult.SAVED -> SyncQueueMutation.ENQUEUED
            else -> SyncQueueMutation.STORE_UNAVAILABLE
        }
    }

    suspend fun flush(
        accountId: String,
        consent: SyncConsent,
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): SyncQueueFlushResult {
        val loaded = loadForAccount(accountId)
            ?: return SyncQueueFlushResult.Failed("LOCAL_QUEUE_UNAVAILABLE", 0)
        val current = loaded.requestSnapshot
        if (current.pending.isEmpty()) return SyncQueueFlushResult.Empty

        val result = gateway.pushWithRetry(
            envelope = SyncPushEnvelope(
                cursor = current.cursor,
                consent = consent,
                idempotencyKey = idempotencyKeyFor(current),
                commands = current.pending,
            ),
            wait = wait,
        )
        return when (result) {
            is SyncGatewayResult.PushCompleted -> {
                if (result.response.nextCursor < current.cursor) {
                    return SyncQueueFlushResult.Failed("SYNC_CURSOR_REGRESSION", current.pending.size)
                }
                val requestedCommandIds = current.pending.mapTo(mutableSetOf()) { it.commandId }
                val responseCommandIds = result.response.results.map { it.commandId }
                if (responseCommandIds.size != responseCommandIds.toSet().size ||
                    responseCommandIds.toSet() != requestedCommandIds ||
                    result.response.results.any { resultItem ->
                        resultItem.serverSequence != null &&
                            resultItem.serverSequence > result.response.nextCursor
                    }
                ) {
                    return SyncQueueFlushResult.Failed("SYNC_RESPONSE_MISMATCH", current.pending.size)
                }
                val resolvedIds = result.response.results
                    .filter { it.outcome == "accepted" || it.outcome == "duplicate" }
                    .mapTo(mutableSetOf()) { it.commandId }
                val remaining = current.pending.filterNot { it.commandId in resolvedIds }
                val conflicts = result.response.results
                    .filter { it.outcome == "conflict" || it.outcome == "rejected" }
                    .map { it.commandId }
                if (!canCommit(loaded)) {
                    return SyncQueueFlushResult.Failed("SYNC_STATE_CHANGED", remaining.size)
                }
                val saved = saveSnapshot(
                    current.copy(
                        cursor = maxOf(current.cursor, result.response.nextCursor),
                        pending = remaining,
                    ),
                )
                if (saved != dev.nextgen.mobile.storage.LocalStorageWriteResult.SAVED) {
                    SyncQueueFlushResult.Failed("LOCAL_QUEUE_SAVE_FAILED", remaining.size)
                } else {
                    SyncQueueFlushResult.Completed(result.response, remaining.size, conflicts)
                }
            }

            is SyncGatewayResult.PullCompleted ->
                SyncQueueFlushResult.Failed("UNEXPECTED_SYNC_RESPONSE", current.pending.size)

            is SyncGatewayResult.Deferred,
            is SyncGatewayResult.Failed,
            -> SyncQueueFlushResult.Deferred(result, current.pending.size)
        }
    }

    suspend fun pull(
        accountId: String,
        consent: SyncConsent,
        limit: Int = MAX_SYNC_COMMANDS,
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): SyncQueuePullResult {
        val loaded = loadForAccount(accountId)
            ?: return SyncQueuePullResult.Failed("LOCAL_QUEUE_UNAVAILABLE")
        val current = loaded.requestSnapshot
        val result = gateway.pullWithRetry(consent, current.cursor, limit, wait)
        return when (result) {
            is SyncGatewayResult.PullCompleted -> {
                if (result.response.nextCursor < current.cursor) {
                    SyncQueuePullResult.Failed("SYNC_CURSOR_REGRESSION")
                } else if (!canCommit(loaded)) {
                    SyncQueuePullResult.Failed("SYNC_STATE_CHANGED")
                } else if (saveSnapshot(current.copy(cursor = result.response.nextCursor)) !=
                    dev.nextgen.mobile.storage.LocalStorageWriteResult.SAVED
                ) {
                    SyncQueuePullResult.Failed("LOCAL_QUEUE_SAVE_FAILED")
                } else {
                    SyncQueuePullResult.Completed(result.response)
                }
            }

            is SyncGatewayResult.PushCompleted ->
                SyncQueuePullResult.Failed("UNEXPECTED_SYNC_RESPONSE")

            is SyncGatewayResult.Deferred,
            is SyncGatewayResult.Failed,
            -> SyncQueuePullResult.Deferred(result)
        }
    }

    suspend fun sync(
        accountId: String,
        consent: SyncConsent,
        limit: Int = MAX_SYNC_COMMANDS,
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): SyncQueueSyncResult {
        val pull = pull(accountId, consent, limit, wait)
        val flush = if (pull is SyncQueuePullResult.Completed) {
            flush(accountId, consent, wait = wait)
        } else {
            SyncQueueFlushResult.Empty
        }
        return SyncQueueSyncResult(pull, flush)
    }

    private data class LoadedQueue(
        val storedSnapshot: SyncQueueSnapshot,
        val requestSnapshot: SyncQueueSnapshot,
        val generation: Long,
    )

    private fun loadForAccount(accountId: String): LoadedQueue? {
        if (!isValidSyncUuid(accountId)) return null
        val loaded = store.load()
        if (loaded.status in setOf(
                LocalStorageStatus.UNAVAILABLE,
                LocalStorageStatus.CORRUPT,
                LocalStorageStatus.FAILED,
            )
        ) {
            return null
        }
        val current = loaded.value ?: return null
        return if (current.accountId == null || current.accountId == accountId) {
            LoadedQueue(
                storedSnapshot = current,
                requestSnapshot = current.copy(accountId = accountId),
                generation = stateGeneration,
            )
        } else {
            null
        }
    }

    private fun canCommit(loaded: LoadedQueue): Boolean =
        loaded.generation == stateGeneration &&
            store.load().value == loaded.storedSnapshot

    private fun saveSnapshot(snapshot: SyncQueueSnapshot): LocalStorageWriteResult {
        val result = store.save(snapshot)
        if (result == LocalStorageWriteResult.SAVED) {
            stateGeneration = nextGeneration(stateGeneration)
        }
        return result
    }

    private fun nextGeneration(value: Long): Long =
        if (value == Long.MAX_VALUE) 0 else value + 1

    private fun idempotencyKeyFor(snapshot: SyncQueueSnapshot): String =
        "sync_${snapshot.cursor}_${snapshot.pending.first().commandId.replace("-", "")}".take(128)
}
