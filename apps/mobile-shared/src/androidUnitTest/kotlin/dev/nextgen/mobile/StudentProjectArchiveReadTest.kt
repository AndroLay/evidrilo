package dev.nextgen.mobile

import java.io.InputStream
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StudentProjectArchiveReadTest {
    @Test
    fun `reader accepts the compressed size limit with bounded reads and monotonic progress`() {
        val input = GeneratedInputStream(MAX_PROJECT_ARCHIVE_BYTES.toLong())
        val progress = mutableListOf<StudentProjectFileImportProgress>()

        val bytes = readStudentProjectArchive(
            input = input,
            declaredSizeBytes = MAX_PROJECT_ARCHIVE_BYTES.toLong(),
            ensureActive = {},
            onProgress = progress::add,
        )

        assertEquals(MAX_PROJECT_ARCHIVE_BYTES, bytes.size)
        assertTrue(input.closed)
        assertTrue(input.maxRequestedRead <= PROJECT_ARCHIVE_READ_CHUNK_BYTES)
        assertEquals(0L, progress.first().bytesRead)
        assertEquals(MAX_PROJECT_ARCHIVE_BYTES.toLong(), progress.last().bytesRead)
        assertTrue(progress.zipWithNext().all { (before, after) -> after.bytesRead >= before.bytesRead })
        assertTrue(progress.size <= 202, "Progress reports should be coalesced instead of emitted for every read chunk")
    }

    @Test
    fun `reader rejects data beyond the compressed size limit`() {
        val input = GeneratedInputStream(MAX_PROJECT_ARCHIVE_BYTES + 1L)

        assertFailsWith<IllegalArgumentException> {
            readStudentProjectArchive(
                input = input,
                declaredSizeBytes = null,
                ensureActive = {},
                onProgress = {},
            )
        }

        assertEquals(MAX_PROJECT_ARCHIVE_BYTES + 1L, input.bytesRead)
        assertTrue(input.closed)
    }

    @Test
    fun `reader rejects an empty archive even when the provider omits size`() {
        val input = GeneratedInputStream(0L)

        assertFailsWith<IllegalArgumentException> {
            readStudentProjectArchive(
                input = input,
                declaredSizeBytes = null,
                ensureActive = {},
                onProgress = {},
            )
        }

        assertTrue(input.closed)
    }

    @Test
    fun `reader stops on cancellation and closes the selected stream`() {
        val input = GeneratedInputStream(64 * 1024L)
        val progress = mutableListOf<StudentProjectFileImportProgress>()

        assertFailsWith<CancellationException> {
            readStudentProjectArchive(
                input = input,
                declaredSizeBytes = 64 * 1024L,
                ensureActive = {
                    if (input.bytesRead >= PROJECT_ARCHIVE_READ_CHUNK_BYTES) {
                        throw CancellationException("cancel import")
                    }
                },
                onProgress = progress::add,
            )
        }

        assertEquals(PROJECT_ARCHIVE_READ_CHUNK_BYTES.toLong(), input.bytesRead)
        assertTrue(input.closed)
        assertEquals(listOf(0L), progress.map(StudentProjectFileImportProgress::bytesRead))
    }

    private class GeneratedInputStream(size: Long) : InputStream() {
        private var remaining = size
        var bytesRead = 0L
            private set
        var maxRequestedRead = 0
            private set
        var closed = false
            private set

        override fun read(): Int {
            if (remaining == 0L) return -1
            remaining--
            bytesRead++
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            maxRequestedRead = maxOf(maxRequestedRead, length)
            if (remaining == 0L) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            buffer.fill(0, offset, offset + count)
            remaining -= count
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
