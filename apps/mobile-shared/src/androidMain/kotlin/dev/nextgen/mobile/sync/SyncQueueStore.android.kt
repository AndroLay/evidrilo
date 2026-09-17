package dev.nextgen.mobile.sync

import android.content.Context
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

private const val PREFERENCES_NAME = "evidrilo_sync_queue_v1"
private const val QUEUE_KEY = "queue"

object AndroidSyncQueueStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createStore(): SyncQueueStore =
        applicationContext?.let(::AndroidSyncQueueStore) ?: NoopSyncQueueStore()
}

internal actual fun createSyncQueueStore(): SyncQueueStore = AndroidSyncQueueStorage.createStore()

private class AndroidSyncQueueStore(
    context: Context,
) : SyncQueueStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<SyncQueueSnapshot> = runCatching {
        val encoded = preferences.getString(QUEUE_KEY, null)
        when {
            encoded == null -> LocalStorageReadResult.Success(SyncQueueSnapshot(0, emptyList()))
            else -> SyncQueueCodec.decode(encoded)?.let { LocalStorageReadResult.Success(it) }
                ?: LocalStorageReadResult.Corrupt
        }
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(snapshot: SyncQueueSnapshot): LocalStorageWriteResult = runCatching {
        if (snapshot.cursor !in 0..MAX_SYNC_CURSOR || snapshot.pending.size > MAX_SYNC_COMMANDS) {
            return@runCatching LocalStorageWriteResult.FAILED
        }
        if (preferences.edit().putString(QUEUE_KEY, SyncQueueCodec.encode(snapshot)).commit()) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }

    override fun clear(): LocalStorageWriteResult = runCatching {
        if (preferences.edit().remove(QUEUE_KEY).commit()) {
            LocalStorageWriteResult.CLEARED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
