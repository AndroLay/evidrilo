package dev.nextgen.mobile.storage

import android.content.Context

private const val PREFERENCES_NAME = "evidrilo_session_v1"
private const val SNAPSHOT_KEY = "conclusion_snapshot"

object AndroidConclusionStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createStore(): ConclusionSessionStore =
        applicationContext?.let(::AndroidConclusionSessionStore) ?: NoopConclusionSessionStore()
}

internal actual fun createConclusionSessionStore(): ConclusionSessionStore =
    AndroidConclusionStorage.createStore()

private class AndroidConclusionSessionStore(
    context: Context,
) : ConclusionSessionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<ConclusionSessionSnapshot> = runCatching {
        val encoded = preferences.getString(SNAPSHOT_KEY, null)
        when {
            encoded == null -> LocalStorageReadResult.Success(null)
            else -> ConclusionSessionCodec.decode(encoded)?.let { snapshot ->
                LocalStorageReadResult.Success(snapshot)
            }
                ?: LocalStorageReadResult.Corrupt
        }
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(snapshot: ConclusionSessionSnapshot): LocalStorageWriteResult = runCatching {
        val encoded = ConclusionSessionCodec.encodeForStorage(snapshot)
            ?: return@runCatching LocalStorageWriteResult.FAILED
        // Drafts are persisted from Compose field events. Apply updates memory
        // immediately and moves the disk write off the UI thread; clear()
        // intentionally keeps commit() because reset must report durable success.
        preferences.edit()
            .putString(SNAPSHOT_KEY, encoded)
            .apply()
        LocalStorageWriteResult.SAVED
    }.getOrElse { LocalStorageWriteResult.FAILED }

    override fun clear(): LocalStorageWriteResult = runCatching {
        if (preferences.edit().remove(SNAPSHOT_KEY).commit()) {
            LocalStorageWriteResult.CLEARED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
