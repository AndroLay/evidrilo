package dev.nextgen.mobile.storage

import android.content.Context

private const val HISTORY_KEY = "latest_comparison"

internal actual fun createConclusionHistoryStore(): ConclusionHistoryStore =
    AndroidConclusionHistoryStorage.createStore()

object AndroidConclusionHistoryStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createStore(): ConclusionHistoryStore =
        applicationContext?.let(::AndroidConclusionHistoryStore) ?: NoopConclusionHistoryStore()
}

private class AndroidConclusionHistoryStore(
    context: Context,
) : ConclusionHistoryStore {
    private val preferences = context.getSharedPreferences("evidrilo_history_v1", Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<ConclusionSessionSnapshot> = runCatching {
        val encoded = preferences.getString(HISTORY_KEY, null)
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
        if (preferences.edit()
            .putString(HISTORY_KEY, encoded)
            .commit()
        ) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }.getOrElse { LocalStorageWriteResult.FAILED }

    override fun clear(): LocalStorageWriteResult = runCatching {
        if (preferences.edit().remove(HISTORY_KEY).commit()) {
            LocalStorageWriteResult.CLEARED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
