package dev.nextgen.mobile.storage

import platform.Foundation.NSUserDefaults

private const val SNAPSHOT_KEY = "evidrilo.conclusion.snapshot.v1"

actual fun createConclusionSessionStore(): ConclusionSessionStore =
    IosConclusionSessionStore()

private class IosConclusionSessionStore : ConclusionSessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<ConclusionSessionSnapshot> = runCatching {
        val encoded = defaults.stringForKey(SNAPSHOT_KEY)
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
        defaults.setObject(encoded, forKey = SNAPSHOT_KEY)
        if (defaults.stringForKey(SNAPSHOT_KEY) == encoded) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }

    override fun clear(): LocalStorageWriteResult = runCatching {
        defaults.removeObjectForKey(SNAPSHOT_KEY)
        if (defaults.stringForKey(SNAPSHOT_KEY) == null) {
            LocalStorageWriteResult.CLEARED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
