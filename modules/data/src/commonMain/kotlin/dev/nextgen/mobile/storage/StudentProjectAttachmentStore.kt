package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef

const val STUDENT_PROJECT_ATTACHMENT_MAX_IO_CHUNK_BYTES = 64 * 1024
const val STUDENT_PROJECT_ATTACHMENT_MAX_IMPORT_BYTES = 200L * 1024 * 1024
private val SAFE_PROJECT_ID = Regex("[A-Za-z0-9_-]{1,96}")

/**
 * Local binary attachment boundary. Implementations keep bytes in private file/blob storage and
 * stream one bounded attachment at a time; draft JSON contains only [StudentProjectAttachmentRef].
 */
interface StudentProjectAttachmentStore {
    /** A null handle means the project/attachment pair is not present. */
    fun openRead(projectId: String, attachmentId: String): StudentProjectAttachmentReadResult

    /** Starts a private staging transaction scoped to one project; it must not overwrite existing IDs. */
    fun beginImport(projectId: String): StudentProjectAttachmentImportResult

    /** Prepares deletion before metadata is removed. Unsupported stores may clear only when refs are absent. */
    fun prepareProjectDeletion(projectId: String, hasAttachments: Boolean): LocalStorageWriteResult

    /** Removes an uncommitted deletion fence after draft metadata could not be removed. */
    fun cancelProjectDeletion(projectId: String): LocalStorageWriteResult

    /** Commits cleanup after draft metadata is gone. Failed cleanup must remain fenced and retryable. */
    fun completeProjectDeletion(projectId: String): LocalStorageWriteResult

    /** Resolves interrupted deletion fences against the current metadata IDs and retries committed cleanup. */
    fun recoverPendingProjectDeletions(existingProjectIds: Set<String>): LocalStorageWriteResult

    /** Fences one attachment before its references are removed from current and historical project metadata. */
    fun prepareAttachmentDeletion(projectId: String, attachmentId: String): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    /** Cancels a prepared attachment removal if the metadata update did not commit. */
    fun cancelAttachmentDeletion(projectId: String, attachmentId: String): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    /** Deletes the blob after all current and revision-snapshot references have been removed. */
    fun completeAttachmentDeletion(projectId: String, attachmentId: String): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    /** Resolves interrupted attachment removals using every current and historical metadata reference. */
    fun recoverPendingAttachmentDeletions(
        existingAttachmentIdsByProject: Map<String, Set<String>>,
    ): LocalStorageWriteResult
}

/** Opens the platform's private local attachment store, or a fail-closed unavailable adapter. */
expect fun createStudentProjectAttachmentStore(): StudentProjectAttachmentStore

class UnavailableStudentProjectAttachmentStore : StudentProjectAttachmentStore {
    override fun openRead(
        projectId: String,
        attachmentId: String,
    ): StudentProjectAttachmentReadResult = StudentProjectAttachmentReadResult.Unavailable

    override fun beginImport(projectId: String): StudentProjectAttachmentImportResult =
        StudentProjectAttachmentImportResult.Unavailable

    override fun prepareProjectDeletion(
        projectId: String,
        hasAttachments: Boolean,
    ): LocalStorageWriteResult = when {
        !SAFE_PROJECT_ID.matches(projectId) -> LocalStorageWriteResult.FAILED
        hasAttachments -> LocalStorageWriteResult.UNAVAILABLE
        else -> LocalStorageWriteResult.CLEARED
    }

    override fun cancelProjectDeletion(projectId: String): LocalStorageWriteResult =
        if (SAFE_PROJECT_ID.matches(projectId)) LocalStorageWriteResult.CLEARED else LocalStorageWriteResult.FAILED

    override fun completeProjectDeletion(projectId: String): LocalStorageWriteResult =
        if (SAFE_PROJECT_ID.matches(projectId)) LocalStorageWriteResult.CLEARED else LocalStorageWriteResult.FAILED

    override fun recoverPendingProjectDeletions(existingProjectIds: Set<String>): LocalStorageWriteResult =
        if (existingProjectIds.all { SAFE_PROJECT_ID.matches(it) }) {
            LocalStorageWriteResult.CLEARED
        } else {
            LocalStorageWriteResult.FAILED
        }

    override fun prepareAttachmentDeletion(projectId: String, attachmentId: String): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    override fun cancelAttachmentDeletion(projectId: String, attachmentId: String): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    override fun completeAttachmentDeletion(projectId: String, attachmentId: String): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    override fun recoverPendingAttachmentDeletions(
        existingAttachmentIdsByProject: Map<String, Set<String>>,
    ): LocalStorageWriteResult = if (
        existingAttachmentIdsByProject.all { (projectId, attachmentIds) ->
            SAFE_PROJECT_ID.matches(projectId) && attachmentIds.all(SAFE_PROJECT_ID::matches)
        }
    ) {
        LocalStorageWriteResult.CLEARED
    } else {
        LocalStorageWriteResult.FAILED
    }
}

sealed interface StudentProjectAttachmentReadResult {
    data class Opened(val handle: StudentProjectAttachmentReadHandle) : StudentProjectAttachmentReadResult
    data object Missing : StudentProjectAttachmentReadResult
    data object Unavailable : StudentProjectAttachmentReadResult
    data class Failed(val code: String) : StudentProjectAttachmentReadResult
}

/** Bounded sequential read API; callers provide a reusable buffer rather than materializing an archive. */
interface StudentProjectAttachmentReadHandle {
    val sizeBytes: Long

    /** Returns a positive byte count, or -1 at end of file. */
    fun read(buffer: ByteArray, offset: Int, length: Int): Int

    fun close()
}

sealed interface StudentProjectAttachmentImportResult {
    data class Started(val session: StudentProjectAttachmentImportSession) : StudentProjectAttachmentImportResult
    data object Unavailable : StudentProjectAttachmentImportResult
    data class Failed(val code: String) : StudentProjectAttachmentImportResult
}

/**
 * Stages at most the archive's allowed attachments in private storage. `publish` makes staged blobs
 * visible while retaining rollback ability; the caller then persists the draft metadata. Call
 * `complete` only after that draft write succeeds, otherwise call `rollback`.
 */
interface StudentProjectAttachmentImportSession {
    fun openWriter(reference: StudentProjectAttachmentRef): StudentProjectAttachmentWriterResult

    fun publish(): LocalStorageWriteResult

    fun complete(): LocalStorageWriteResult

    fun rollback(): LocalStorageWriteResult
}

sealed interface StudentProjectAttachmentWriterResult {
    data class Opened(val writer: StudentProjectAttachmentWriteHandle) : StudentProjectAttachmentWriterResult
    data object Unavailable : StudentProjectAttachmentWriterResult
    data class Failed(val code: String) : StudentProjectAttachmentWriterResult
}

/** One-file streaming sink. `finish` closes only this staged file; session publication is separate. */
interface StudentProjectAttachmentWriteHandle {
    fun write(buffer: ByteArray, offset: Int, length: Int)

    fun finish(): LocalStorageWriteResult

    fun abort()
}
