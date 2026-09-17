package dev.nextgen.mobile.sync

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

data class SyncQueueSnapshot(
    val cursor: Long,
    val pending: List<SyncCommandIntent>,
    val accountId: String? = null,
)

interface SyncQueueStore {
    fun load(): LocalStorageReadResult<SyncQueueSnapshot>

    fun save(snapshot: SyncQueueSnapshot): LocalStorageWriteResult

    fun clear(): LocalStorageWriteResult
}

class NoopSyncQueueStore : SyncQueueStore {
    override fun load(): LocalStorageReadResult<SyncQueueSnapshot> = LocalStorageReadResult.Unavailable

    override fun save(snapshot: SyncQueueSnapshot): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    override fun clear(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}

object SyncQueueCodec {
    fun encode(snapshot: SyncQueueSnapshot): String = buildString {
        append(snapshot.accountId.orEmpty())
        append(';')
        append(snapshot.cursor)
        snapshot.pending.forEach { command ->
            append('|')
            append(command.commandId)
            append(';')
            append(command.attemptId)
            append(';')
            append(command.caseVersionId)
            append(';')
            append(command.revisionNumber)
            append(';')
            append(command.snapshotDigest)
            append(';')
            append(command.commandType.wireName)
            append(';')
            append(command.clientOccurredAt)
        }
    }

    fun decode(value: String): SyncQueueSnapshot? = runCatching {
        val records = value.split('|')
        val header = records.firstOrNull()?.split(';') ?: return null
        if (header.size != 2) return null
        val accountId = header[0].takeIf { it.isNotEmpty() }
        if (accountId != null && !isValidSyncUuid(accountId)) return null
        val cursor = header[1].toLongOrNull()?.takeIf { it in 0..MAX_SYNC_CURSOR } ?: return null
        val pending = records.drop(1).map { record ->
            val fields = record.split(';')
            if (fields.size != 7) return null
            val commandType = SyncCommandType.fromWire(fields[5]) ?: return null
            val command = SyncCommandIntent(
                commandId = fields[0],
                attemptId = fields[1],
                caseVersionId = fields[2],
                revisionNumber = fields[3].toIntOrNull() ?: return null,
                snapshotDigest = fields[4],
                commandType = commandType,
                clientOccurredAt = fields[6],
            )
            if (!command.validate().isValid) return null
            command
        }
        if (pending.size > MAX_SYNC_COMMANDS) return null
        SyncQueueSnapshot(cursor = cursor, pending = pending, accountId = accountId)
    }.getOrNull()
}

object SyncRetryPolicy {
    const val MAX_RETRIES: Int = 3
    private const val INITIAL_DELAY_MILLIS: Long = 250
    private const val MAX_DELAY_MILLIS: Long = 4_000

    fun shouldRetry(result: SyncGatewayResult, attempt: Int): Boolean =
        result is SyncGatewayResult.Failed && result.retryable && attempt in 0 until MAX_RETRIES

    fun delayMillis(attempt: Int): Long =
        (INITIAL_DELAY_MILLIS * (1L shl attempt.coerceIn(0, 4))).coerceAtMost(MAX_DELAY_MILLIS)
}
