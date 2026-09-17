package dev.nextgen.mobile.storage

import platform.Foundation.NSUserDefaults

private const val HISTORY_KEY = "evidrilo.conclusion.history.v1"

internal actual fun createConclusionHistoryStore(): ConclusionHistoryStore =
    IosConclusionHistoryStore()

private class IosConclusionHistoryStore : ConclusionHistoryStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<ConclusionSessionSnapshot> = runCatching {
        val encoded = defaults.stringForKey(HISTORY_KEY)
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
        defaults.setObject(encoded, forKey = HISTORY_KEY)
        if (defaults.stringForKey(HISTORY_KEY) == encoded) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }

    override fun clear(): LocalStorageWriteResult = runCatching {
        defaults.removeObjectForKey(HISTORY_KEY)
        if (defaults.stringForKey(HISTORY_KEY) == null) {
            LocalStorageWriteResult.CLEARED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
