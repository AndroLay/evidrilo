package dev.nextgen.mobile.storage

interface ConclusionHistoryStore {
    fun load(): LocalStorageReadResult<ConclusionSessionSnapshot>

    fun save(snapshot: ConclusionSessionSnapshot): LocalStorageWriteResult

    fun clear(): LocalStorageWriteResult
}

class NoopConclusionHistoryStore : ConclusionHistoryStore {
    override fun load(): LocalStorageReadResult<ConclusionSessionSnapshot> = LocalStorageReadResult.Unavailable

    override fun save(snapshot: ConclusionSessionSnapshot): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    override fun clear(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}
