package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadHandle
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentStore
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudentProjectAttachmentPreviewReaderTest {
    @Test
    fun `preview reads the exact private attachment and closes its handle`() {
        val bytes = "hello".encodeToByteArray()
        val handle = MemoryReadHandle(bytes)
        val store = MemoryAttachmentStore(StudentProjectAttachmentReadResult.Opened(handle))

        val result = readStudentProjectAttachmentForPreview(store, "project-1", validReference())

        val loaded = assertIs<StudentProjectAttachmentPreviewRead.Loaded>(result)
        assertContentEquals(bytes, loaded.bytes)
        assertTrue(handle.closed)
    }

    @Test
    fun `preview rejects attachment bytes whose checksum differs from saved metadata`() {
        val handle = MemoryReadHandle("jello".encodeToByteArray())
        val store = MemoryAttachmentStore(StudentProjectAttachmentReadResult.Opened(handle))

        val result = readStudentProjectAttachmentForPreview(store, "project-1", validReference())

        assertEquals(
            StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_CHECKSUM_MISMATCH"),
            result,
        )
        assertTrue(handle.closed)
    }

    @Test
    fun `preview rejects a stored size mismatch without reading the file`() {
        val handle = MemoryReadHandle("hello".encodeToByteArray(), reportedSize = 4)
        val store = MemoryAttachmentStore(StudentProjectAttachmentReadResult.Opened(handle))

        val result = readStudentProjectAttachmentForPreview(store, "project-1", validReference())

        assertEquals(StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_SIZE_MISMATCH"), result)
        assertTrue(handle.closed)
        assertEquals(0, handle.bytesRead)
    }

    @Test
    fun `preview preserves missing and unavailable storage as explicit failures`() {
        val missing = readStudentProjectAttachmentForPreview(
            MemoryAttachmentStore(StudentProjectAttachmentReadResult.Missing),
            "project-1",
            validReference(),
        )
        val unavailable = readStudentProjectAttachmentForPreview(
            MemoryAttachmentStore(StudentProjectAttachmentReadResult.Unavailable),
            "project-1",
            validReference(),
        )

        assertEquals(StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_MISSING"), missing)
        assertEquals(StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_STORE_UNAVAILABLE"), unavailable)
    }

    @Test
    fun `preview stops and closes the handle when the caller cancels`() {
        val handle = MemoryReadHandle("hello".encodeToByteArray())
        val store = MemoryAttachmentStore(StudentProjectAttachmentReadResult.Opened(handle))

        assertFailsWith<CancellationException> {
            readStudentProjectAttachmentForPreview(
                store,
                "project-1",
                validReference(),
                ensureActive = { throw CancellationException("preview cancelled") },
            )
        }

        assertTrue(handle.closed)
        assertEquals(0, handle.bytesRead)
    }

    private fun validReference() = StudentProjectAttachmentRef(
        id = "attachment-1",
        fileName = "source.txt",
        mimeType = "text/plain",
        sizeBytes = 5,
        sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
    )

    private class MemoryAttachmentStore(
        private val readResult: StudentProjectAttachmentReadResult,
    ) : StudentProjectAttachmentStore {
        override fun openRead(projectId: String, attachmentId: String) = readResult
        override fun beginImport(projectId: String) = StudentProjectAttachmentImportResult.Unavailable
        override fun prepareProjectDeletion(projectId: String, hasAttachments: Boolean) = LocalStorageWriteResult.UNAVAILABLE
        override fun cancelProjectDeletion(projectId: String) = LocalStorageWriteResult.UNAVAILABLE
        override fun completeProjectDeletion(projectId: String) = LocalStorageWriteResult.UNAVAILABLE
        override fun recoverPendingProjectDeletions(existingProjectIds: Set<String>) = LocalStorageWriteResult.UNAVAILABLE
        override fun recoverPendingAttachmentDeletions(
            existingAttachmentIdsByProject: Map<String, Set<String>>,
        ) = LocalStorageWriteResult.UNAVAILABLE
    }

    private class MemoryReadHandle(
        private val bytes: ByteArray,
        private val reportedSize: Long = bytes.size.toLong(),
    ) : StudentProjectAttachmentReadHandle {
        private var offset = 0
        var bytesRead = 0
            private set
        var closed = false
            private set

        override val sizeBytes: Long get() = reportedSize

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (this.offset == bytes.size) return -1
            val count = minOf(length, bytes.size - this.offset)
            bytes.copyInto(buffer, offset, this.offset, this.offset + count)
            this.offset += count
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
