package dev.nextgen.mobile.storage

/** Device-only navigation metadata. Never part of a project, revision, or export. */
interface ProjectSectionBookmarkStore {
    fun read(key: String): LocalStorageReadResult<String>
    fun write(key: String, sectionId: String): LocalStorageWriteResult
    fun remove(key: String): LocalStorageWriteResult
}

expect fun createProjectSectionBookmarkStore(): ProjectSectionBookmarkStore

class UnavailableProjectSectionBookmarkStore : ProjectSectionBookmarkStore {
    override fun read(key: String): LocalStorageReadResult<String> = LocalStorageReadResult.Failed
    override fun write(key: String, sectionId: String): LocalStorageWriteResult = LocalStorageWriteResult.FAILED
    override fun remove(key: String): LocalStorageWriteResult = LocalStorageWriteResult.FAILED
}
