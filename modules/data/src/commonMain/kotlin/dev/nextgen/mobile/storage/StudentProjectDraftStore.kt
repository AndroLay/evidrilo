package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.project.StudentProjectDraft

interface StudentProjectDraftStore {
    fun load(): LocalStorageReadResult<List<StudentProjectDraft>>

    /** Atomically replaces the local project collection when the platform store supports it. */
    fun replaceAll(drafts: List<StudentProjectDraft>): LocalStorageWriteResult

    fun save(draft: StudentProjectDraft): LocalStorageWriteResult

    fun delete(projectId: String): LocalStorageWriteResult
}

class NoopStudentProjectDraftStore : StudentProjectDraftStore {
    override fun load(): LocalStorageReadResult<List<StudentProjectDraft>> = LocalStorageReadResult.Unavailable

    override fun replaceAll(drafts: List<StudentProjectDraft>): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE

    override fun save(draft: StudentProjectDraft): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE

    override fun delete(projectId: String): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}

expect fun createStudentProjectDraftStore(): StudentProjectDraftStore

interface StudentProjectDraftTextStorage {
    fun read(): LocalStorageReadResult<String>

    /** A null value removes the persisted project-list record. */
    fun write(encoded: String?): LocalStorageWriteResult
}

class EncodedStudentProjectDraftStore(
    private val textStorage: StudentProjectDraftTextStorage,
) : StudentProjectDraftStore {
    override fun load(): LocalStorageReadResult<List<StudentProjectDraft>> = when (val result = textStorage.read()) {
        is LocalStorageReadResult.Success -> result.value?.let(StudentProjectDraftStoreCodec::decode)
            ?: LocalStorageReadResult.Success(emptyList())
        LocalStorageReadResult.Unavailable -> LocalStorageReadResult.Unavailable
        LocalStorageReadResult.Corrupt -> LocalStorageReadResult.Corrupt
        LocalStorageReadResult.Failed -> LocalStorageReadResult.Failed
    }

    override fun replaceAll(drafts: List<StudentProjectDraft>): LocalStorageWriteResult {
        val encoded = StudentProjectDraftStoreCodec.encode(drafts)
        return when (encoded) {
            is ProjectDraftEncodingResult.Encoded -> textStorage.write(encoded.value.takeIf { drafts.isNotEmpty() })
            is ProjectDraftEncodingResult.Invalid -> if (encoded.code in setOf(
                "PROJECT_DRAFT_TOO_LARGE",
                "PROJECT_DRAFT_STORAGE_TOO_LARGE",
                "TOO_MANY_PROJECT_DRAFTS",
            )) {
                LocalStorageWriteResult.LIMIT_REACHED
            } else {
                LocalStorageWriteResult.FAILED
            }
        }
    }

    override fun save(draft: StudentProjectDraft): LocalStorageWriteResult {
        val current = load()
        if (current !is LocalStorageReadResult.Success) return current.toWriteFailure()
        val next = current.value.orEmpty().filterNot { it.id == draft.id } + draft
        return replaceAll(next)
    }

    override fun delete(projectId: String): LocalStorageWriteResult {
        val current = load()
        if (current !is LocalStorageReadResult.Success) return current.toWriteFailure()
        val remaining = current.value.orEmpty().filterNot { it.id == projectId }
        if (remaining.size == current.value.orEmpty().size) return LocalStorageWriteResult.CLEARED
        return replaceAll(remaining)
    }

    private fun LocalStorageReadResult<*>.toWriteFailure(): LocalStorageWriteResult = when (status) {
        LocalStorageStatus.UNAVAILABLE -> LocalStorageWriteResult.UNAVAILABLE
        else -> LocalStorageWriteResult.FAILED
    }
}
