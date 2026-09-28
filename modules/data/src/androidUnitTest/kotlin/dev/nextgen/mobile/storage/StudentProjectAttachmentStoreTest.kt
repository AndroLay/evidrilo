package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudentProjectAttachmentStoreTest {
    @Test
    fun `rollback after publish removes only the staged attachment`() = withStore { store ->
        val preservedBytes = "keep this attachment".encodeToByteArray()
        val preservedReference = reference("already-kept", preservedBytes)
        val existingSession = beginImport(store, "project-one")
        write(existingSession, preservedReference, preservedBytes)
        assertEquals(LocalStorageWriteResult.SAVED, existingSession.publish())
        assertEquals(LocalStorageWriteResult.SAVED, existingSession.complete())

        val bytes = "staged attachment".encodeToByteArray()
        val reference = reference("attachment-one", bytes)
        val session = beginImport(store, "project-one")
        write(session, reference, bytes)

        assertEquals(StudentProjectAttachmentReadResult.Missing, store.openRead("project-one", reference.id))
        assertEquals(LocalStorageWriteResult.SAVED, session.publish())
        assertIs<StudentProjectAttachmentReadResult.Opened>(store.openRead("project-one", reference.id))

        assertEquals(LocalStorageWriteResult.CLEARED, session.rollback())
        assertEquals(StudentProjectAttachmentReadResult.Missing, store.openRead("project-one", reference.id))
        assertEquals(
            preservedBytes.toList(),
            readAll(store, "project-one", preservedReference.id, 4).toList(),
        )
    }

    @Test
    fun `publishing duplicate attachment id does not overwrite the existing bytes`() = withStore { store ->
        val originalBytes = "original bytes".encodeToByteArray()
        val original = reference("stable-id", originalBytes)
        val first = beginImport(store, "project-one")
        write(first, original, originalBytes)
        assertEquals(LocalStorageWriteResult.SAVED, first.publish())
        assertEquals(LocalStorageWriteResult.SAVED, first.complete())

        val replacementBytes = "replacement bytes".encodeToByteArray()
        val replacement = reference("STABLE-ID", replacementBytes)
        val second = beginImport(store, "project-one")
        write(second, replacement, replacementBytes)

        assertEquals(LocalStorageWriteResult.FAILED, second.publish())
        assertEquals(LocalStorageWriteResult.CLEARED, second.rollback())
        assertEquals(originalBytes.toList(), readAll(store, "project-one", original.id, 3).toList())
    }

    @Test
    fun `attachment bytes stream through bounded sequential buffers`() = withStore { store ->
        val bytes = "sequential reads stay bounded".encodeToByteArray()
        val reference = reference("stream-id", bytes)
        val session = beginImport(store, "project-one")
        write(session, reference, bytes, chunkSize = 4)
        assertEquals(LocalStorageWriteResult.SAVED, session.publish())
        assertEquals(LocalStorageWriteResult.SAVED, session.complete())

        val read = assertIs<StudentProjectAttachmentReadResult.Opened>(
            store.openRead("project-one", reference.id),
        ).handle
        assertEquals(bytes.size.toLong(), read.sizeBytes)

        val buffer = ByteArray(5)
        val streamed = ArrayList<Byte>()
        var readCalls = 0
        try {
            while (true) {
                val count = read.read(buffer, 0, buffer.size)
                if (count < 0) break
                assertTrue(count in 1..buffer.size)
                readCalls += 1
                repeat(count) { index -> streamed += buffer[index] }
            }
        } finally {
            read.close()
        }

        assertTrue(readCalls > 1)
        assertEquals(bytes.toList(), streamed)
    }

    @Test
    fun `writer rejects bytes that do not match declared checksum`() = withStore { store ->
        val expected = "expected payload".encodeToByteArray()
        val wrong = expected.copyOf().also { it[0] = 'X'.code.toByte() }
        val reference = reference("checksum-id", expected)
        val session = beginImport(store, "project-one")
        val writer = assertIs<StudentProjectAttachmentWriterResult.Opened>(
            session.openWriter(reference),
        ).writer
        writer.write(wrong, 0, wrong.size)

        assertEquals(LocalStorageWriteResult.FAILED, writer.finish())
        assertEquals(LocalStorageWriteResult.FAILED, session.publish())
        assertEquals(LocalStorageWriteResult.CLEARED, session.rollback())
        assertEquals(StudentProjectAttachmentReadResult.Missing, store.openRead("project-one", reference.id))
    }

    @Test
    fun `failed staging cleanup is reported and can be retried without losing committed attachment`() =
        withStoreDirectory { store, directory ->
            val bytes = "committed attachment".encodeToByteArray()
            val reference = reference("attachment-one", bytes)
            val session = beginImport(store, "project-one")
            write(session, reference, bytes)
            assertEquals(LocalStorageWriteResult.SAVED, session.publish())

            val transactionDirectory = directory.toPath()
                .resolve("evidrilo_project_attachments_v1/staging/project-one")
                .toFile().listFiles()!!.single()
            val held = directory.toPath().resolve("held-outside-staging")
            Files.createDirectory(held)
            val blocker = transactionDirectory.toPath().resolve("unsafe-entry")
            Files.createSymbolicLink(blocker, held)

            assertEquals(LocalStorageWriteResult.FAILED, session.complete())
            assertEquals(
                bytes.toList(),
                readAll(store, "project-one", reference.id, bufferSize = 4).toList(),
            )

            Files.delete(blocker)
            assertEquals(LocalStorageWriteResult.SAVED, session.complete())
            assertTrue(!transactionDirectory.exists())
            assertTrue(Files.isDirectory(held))
        }

    @Test
    fun `path-like identifiers fail closed without creating files`() = withStore { store ->
        assertIs<StudentProjectAttachmentReadResult.Failed>(store.openRead("../outside", "attachment"))
        assertIs<StudentProjectAttachmentReadResult.Failed>(store.openRead("project-one", "../../outside"))
        assertIs<StudentProjectAttachmentImportResult.Failed>(store.beginImport("../outside"))
    }

    @Test
    fun `prepared attachment removal fences reads and can be cancelled without changing bytes`() = withStore { store ->
        val bytes = "private attachment".encodeToByteArray()
        val reference = reference("attachment-one", bytes)
        val session = beginImport(store, "project-one")
        write(session, reference, bytes)
        assertEquals(LocalStorageWriteResult.SAVED, session.publish())
        assertEquals(LocalStorageWriteResult.SAVED, session.complete())

        assertEquals(LocalStorageWriteResult.SAVED, store.prepareAttachmentDeletion("project-one", reference.id))
        assertIs<StudentProjectAttachmentReadResult.Failed>(store.openRead("project-one", reference.id))
        val conflictingImport = beginImport(store, "project-one")
        assertIs<StudentProjectAttachmentWriterResult.Failed>(conflictingImport.openWriter(reference))
        assertEquals(LocalStorageWriteResult.CLEARED, conflictingImport.rollback())
        assertEquals(LocalStorageWriteResult.CLEARED, store.cancelAttachmentDeletion("project-one", reference.id))
        assertEquals(bytes.toList(), readAll(store, "project-one", reference.id, 4).toList())
    }

    @Test
    fun `attachment deletion recovery preserves referenced bytes and deletes unreferenced bytes`() = withStore { store ->
        val preservedBytes = "keep from history".encodeToByteArray()
        val deletedBytes = "remove from every revision".encodeToByteArray()
        val preserved = reference("attachment-preserved", preservedBytes)
        val deleted = reference("attachment-deleted", deletedBytes)
        val session = beginImport(store, "project-one")
        write(session, preserved, preservedBytes)
        write(session, deleted, deletedBytes)
        assertEquals(LocalStorageWriteResult.SAVED, session.publish())
        assertEquals(LocalStorageWriteResult.SAVED, session.complete())

        assertEquals(LocalStorageWriteResult.SAVED, store.prepareAttachmentDeletion("project-one", preserved.id))
        assertEquals(LocalStorageWriteResult.SAVED, store.prepareAttachmentDeletion("project-one", deleted.id))
        assertEquals(
            LocalStorageWriteResult.SAVED,
            store.recoverPendingAttachmentDeletions(mapOf("project-one" to setOf(preserved.id))),
        )

        assertEquals(preservedBytes.toList(), readAll(store, "project-one", preserved.id, 4).toList())
        assertEquals(StudentProjectAttachmentReadResult.Missing, store.openRead("project-one", deleted.id))
        assertEquals(LocalStorageWriteResult.CLEARED, store.recoverPendingAttachmentDeletions(emptyMap()))
    }

    @Test
    fun `attachment deletion operations reject unsafe identifiers`() = withStore { store ->
        assertEquals(LocalStorageWriteResult.FAILED, store.prepareAttachmentDeletion("../outside", "attachment-one"))
        assertEquals(LocalStorageWriteResult.FAILED, store.prepareAttachmentDeletion("project-one", "../outside"))
        assertEquals(LocalStorageWriteResult.FAILED, store.cancelAttachmentDeletion("project-one", "../outside"))
        assertEquals(LocalStorageWriteResult.FAILED, store.completeAttachmentDeletion("../outside", "attachment-one"))
        assertEquals(
            LocalStorageWriteResult.FAILED,
            store.recoverPendingAttachmentDeletions(mapOf("../outside" to setOf("attachment-one"))),
        )
    }

    @Test
    fun `attachment deletion failure stays fenced and recovery never follows a symlink`() =
        withStoreDirectory { store, directory ->
            val bytes = "private attachment".encodeToByteArray()
            val reference = reference("attachment-one", bytes)
            val session = beginImport(store, "project-one")
            write(session, reference, bytes)
            assertEquals(LocalStorageWriteResult.SAVED, session.publish())
            assertEquals(LocalStorageWriteResult.SAVED, session.complete())
            assertEquals(LocalStorageWriteResult.SAVED, store.prepareAttachmentDeletion("project-one", reference.id))

            val blob = directory.toPath().resolve(
                "evidrilo_project_attachments_v1/projects/project-one/attachments/${reference.id}.blob",
            )
            Files.delete(blob)
            val heldOutside = directory.toPath().resolve("held-attachment-outside-store")
            Files.write(heldOutside, bytes)
            Files.createSymbolicLink(blob, heldOutside)

            assertEquals(LocalStorageWriteResult.FAILED, store.completeAttachmentDeletion("project-one", reference.id))
            assertIs<StudentProjectAttachmentReadResult.Failed>(store.openRead("project-one", reference.id))
            assertEquals(bytes.toList(), Files.readAllBytes(heldOutside).toList())

            Files.delete(blob)
            Files.write(blob, bytes)
            assertEquals(
                LocalStorageWriteResult.SAVED,
                store.recoverPendingAttachmentDeletions(emptyMap()),
            )
            assertEquals(StudentProjectAttachmentReadResult.Missing, store.openRead("project-one", reference.id))
            assertEquals(bytes.toList(), Files.readAllBytes(heldOutside).toList())
            assertEquals(LocalStorageWriteResult.CLEARED, store.completeAttachmentDeletion("project-one", reference.id))
        }

    @Test
    fun `prepared deletion recovers without deleting bytes while project metadata remains`() = withStoreDirectory { store, directory ->
        val bytes = "private attachment".encodeToByteArray()
        val reference = reference("attachment-one", bytes)
        val session = beginImport(store, "project-one")
        write(session, reference, bytes)
        assertEquals(LocalStorageWriteResult.SAVED, session.publish())
        assertEquals(LocalStorageWriteResult.SAVED, session.complete())

        assertEquals(LocalStorageWriteResult.SAVED, store.prepareProjectDeletion("project-one", hasAttachments = true))
        assertIs<StudentProjectAttachmentReadResult.Failed>(store.openRead("project-one", reference.id))
        assertEquals(bytes.toList(), Files.readAllBytes(
            directory.toPath().resolve("evidrilo_project_attachments_v1/projects/project-one/attachments/attachment-one.blob"),
        ).toList())
        assertIs<StudentProjectAttachmentImportResult.Failed>(store.beginImport("project-one"))

        assertEquals(
            LocalStorageWriteResult.SAVED,
            store.recoverPendingProjectDeletions(setOf("project-one")),
        )
        assertEquals(bytes.toList(), readAll(store, "project-one", reference.id, 4).toList())
    }

    @Test
    fun `committed project deletion removes blobs and absent cleanup is idempotent`() = withStore { store ->
        val bytes = "private attachment".encodeToByteArray()
        val reference = reference("attachment-one", bytes)
        val session = beginImport(store, "project-one")
        write(session, reference, bytes)
        assertEquals(LocalStorageWriteResult.SAVED, session.publish())
        assertEquals(LocalStorageWriteResult.SAVED, session.complete())

        assertEquals(LocalStorageWriteResult.SAVED, store.prepareProjectDeletion("project-one", hasAttachments = true))
        assertEquals(LocalStorageWriteResult.SAVED, store.completeProjectDeletion("project-one"))
        assertEquals(StudentProjectAttachmentReadResult.Missing, store.openRead("project-one", reference.id))
        assertEquals(LocalStorageWriteResult.CLEARED, store.completeProjectDeletion("project-one"))
        assertEquals(LocalStorageWriteResult.SAVED, store.prepareProjectDeletion("project-absent", hasAttachments = false))
        assertEquals(LocalStorageWriteResult.SAVED, store.completeProjectDeletion("project-absent"))
        assertEquals(LocalStorageWriteResult.CLEARED, store.completeProjectDeletion("project-absent"))
    }

    @Test
    fun `failed no-follow cleanup stays hidden and recovery retries after storage is repaired`() = withStoreDirectory { store, directory ->
        val bytes = "private attachment".encodeToByteArray()
        val reference = reference("attachment-one", bytes)
        val session = beginImport(store, "project-one")
        write(session, reference, bytes)
        assertEquals(LocalStorageWriteResult.SAVED, session.publish())
        assertEquals(LocalStorageWriteResult.SAVED, session.complete())
        assertEquals(LocalStorageWriteResult.SAVED, store.prepareProjectDeletion("project-one", hasAttachments = true))

        val projectDirectory = directory.toPath()
            .resolve("evidrilo_project_attachments_v1/projects/project-one")
        val heldDirectory = directory.toPath().resolve("held-project-one")
        Files.move(projectDirectory, heldDirectory)
        Files.createSymbolicLink(projectDirectory, heldDirectory)

        assertEquals(LocalStorageWriteResult.FAILED, store.completeProjectDeletion("project-one"))
        assertIs<StudentProjectAttachmentReadResult.Failed>(store.openRead("project-one", reference.id))
        assertEquals(bytes.toList(), Files.readAllBytes(heldDirectory.resolve("attachments/attachment-one.blob")).toList())

        Files.delete(projectDirectory)
        Files.move(heldDirectory, projectDirectory)
        assertEquals(
            LocalStorageWriteResult.SAVED,
            store.recoverPendingProjectDeletions(emptySet()),
        )
        assertEquals(StudentProjectAttachmentReadResult.Missing, store.openRead("project-one", reference.id))
    }

    @Test
    fun `project deletion operations reject unsafe identifiers`() = withStore { store ->
        assertEquals(LocalStorageWriteResult.FAILED, store.prepareProjectDeletion("../outside", hasAttachments = false))
        assertEquals(LocalStorageWriteResult.FAILED, store.cancelProjectDeletion("../outside"))
        assertEquals(LocalStorageWriteResult.FAILED, store.completeProjectDeletion("../outside"))
        assertEquals(LocalStorageWriteResult.FAILED, store.recoverPendingProjectDeletions(setOf("../outside")))
    }

    private fun withStore(block: (AndroidStudentProjectAttachmentStore) -> Unit) {
        withStoreDirectory { store, _ -> block(store) }
    }

    private fun withStoreDirectory(block: (AndroidStudentProjectAttachmentStore, File) -> Unit) {
        val directory = Files.createTempDirectory("evidrilo-attachments-test-").toFile()
        try {
            block(AndroidStudentProjectAttachmentStore(directory), directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun beginImport(
        store: StudentProjectAttachmentStore,
        projectId: String,
    ): StudentProjectAttachmentImportSession = assertIs<StudentProjectAttachmentImportResult.Started>(
        store.beginImport(projectId),
    ).session

    private fun write(
        session: StudentProjectAttachmentImportSession,
        reference: StudentProjectAttachmentRef,
        bytes: ByteArray,
        chunkSize: Int = bytes.size,
    ) {
        val writer = assertIs<StudentProjectAttachmentWriterResult.Opened>(
            session.openWriter(reference),
        ).writer
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(chunkSize, bytes.size - offset)
            writer.write(bytes, offset, count)
            offset += count
        }
        assertEquals(LocalStorageWriteResult.SAVED, writer.finish())
    }

    private fun readAll(
        store: StudentProjectAttachmentStore,
        projectId: String,
        attachmentId: String,
        bufferSize: Int,
    ): ByteArray {
        val handle = assertIs<StudentProjectAttachmentReadResult.Opened>(
            store.openRead(projectId, attachmentId),
        ).handle
        val buffer = ByteArray(bufferSize)
        val bytes = ArrayList<Byte>()
        try {
            while (true) {
                val count = handle.read(buffer, 0, buffer.size)
                if (count < 0) break
                repeat(count) { index -> bytes += buffer[index] }
            }
        } finally {
            handle.close()
        }
        return bytes.toByteArray()
    }

    private fun reference(id: String, bytes: ByteArray) = StudentProjectAttachmentRef(
        id = id,
        fileName = "attachment.txt",
        mimeType = "text/plain",
        sizeBytes = bytes.size.toLong(),
        sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) },
    )
}
