package dev.nextgen.mobile

import java.io.InputStream
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StudentProjectAttachmentReadTest {
    @Test
    fun `selected file reader copies exact declared bytes through bounded reads`() {
        val input = GeneratedInputStream(40_001L)

        val bytes = readStudentProjectAttachment(
            input = input,
            declaredSizeBytes = 40_001L,
            ensureActive = {},
        )

        assertEquals(40_001, bytes.size)
        assertEquals(0x41.toByte(), bytes.first())
        assertEquals(0x41.toByte(), bytes.last())
        assertTrue(input.closed)
        assertTrue(input.maxRequestedRead <= 8 * 1024)
    }

    @Test
    fun `selected file reader rejects empty files and declared-size mismatch`() {
        val empty = GeneratedInputStream(0)
        assertFailsWith<IllegalArgumentException> {
            readStudentProjectAttachment(empty, declaredSizeBytes = null, ensureActive = {})
        }
        assertTrue(empty.closed)

        val mismatched = GeneratedInputStream(12)
        assertFailsWith<IllegalArgumentException> {
            readStudentProjectAttachment(mismatched, declaredSizeBytes = 11, ensureActive = {})
        }
        assertTrue(mismatched.closed)
    }

    @Test
    fun `selected file reader stops at the 20 MiB limit and closes the stream`() {
        val input = GeneratedInputStream(20L * 1024 * 1024 + 1)

        assertFailsWith<IllegalArgumentException> {
            readStudentProjectAttachment(input, declaredSizeBytes = null, ensureActive = {})
        }

        assertEquals(20L * 1024 * 1024 + 1, input.bytesRead)
        assertTrue(input.closed)
        assertTrue(input.maxRequestedRead <= 8 * 1024)
    }

    @Test
    fun `selected file reader closes the source when cancelled`() {
        val input = GeneratedInputStream(32 * 1024L)

        assertFailsWith<CancellationException> {
            readStudentProjectAttachment(
                input,
                declaredSizeBytes = 32 * 1024L,
                ensureActive = { if (input.bytesRead >= 8 * 1024) throw CancellationException("cancel file read") },
            )
        }

        assertEquals(8 * 1024L, input.bytesRead)
        assertTrue(input.closed)
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
            return 0x41
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            maxRequestedRead = maxOf(maxRequestedRead, length)
            if (remaining == 0L) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            buffer.fill(0x41, offset, offset + count)
            remaining -= count
            bytesRead += count
            return count
        }

        override fun close() {
            closed = true
        }
    }
}
