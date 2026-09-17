package dev.nextgen.mobile.sync

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import platform.Foundation.NSUserDefaults

private const val QUEUE_KEY = "evidrilo.sync.queue.v1"

internal actual fun createSyncQueueStore(): SyncQueueStore = IosSyncQueueStore()

private class IosSyncQueueStore : SyncQueueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<SyncQueueSnapshot> = runCatching {
        val encoded = defaults.stringForKey(QUEUE_KEY)
        when {
            encoded == null -> LocalStorageReadResult.Success(SyncQueueSnapshot(0, emptyList()))
            else -> SyncQueueCodec.decode(encoded)?.let { LocalStorageReadResult.Success(it) }
                ?: LocalStorageReadResult.Corrupt
        }
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(snapshot: SyncQueueSnapshot): LocalStorageWriteResult = runCatching {
        if (snapshot.cursor !in 0..MAX_SYNC_CURSOR || snapshot.pending.size > MAX_SYNC_COMMANDS) {
            LocalStorageWriteResult.FAILED
        } else {
            val encoded = SyncQueueCodec.encode(snapshot)
            defaults.setObject(encoded, forKey = QUEUE_KEY)
            if (defaults.stringForKey(QUEUE_KEY) == encoded) {
                LocalStorageWriteResult.SAVED
            } else {
                LocalStorageWriteResult.FAILED
            }
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }

    override fun clear(): LocalStorageWriteResult = runCatching {
        defaults.removeObjectForKey(QUEUE_KEY)
        if (defaults.stringForKey(QUEUE_KEY) == null) {
            LocalStorageWriteResult.CLEARED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
