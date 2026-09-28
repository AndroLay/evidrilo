package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudentProjectFileSelectionRulesTest {
    @Test
    fun `project archive selection accepts only evproj names within the existing cap`() {
        assertTrue(
            StudentProjectFileSelectionRules.acceptsProjectArchive(
                fileName = "student-project.EVPROJ",
                sizeBytes = 50L * 1024 * 1024,
            ),
        )
        assertTrue(StudentProjectFileSelectionRules.acceptsProjectArchive(fileName = null, sizeBytes = null))
        assertFalse(StudentProjectFileSelectionRules.acceptsProjectArchive("student-project.zip", 24))
        assertFalse(StudentProjectFileSelectionRules.acceptsProjectArchive("student-project.evproj", 0))
        assertFalse(
            StudentProjectFileSelectionRules.acceptsProjectArchive(
                "student-project.evproj",
                50L * 1024 * 1024 + 1,
            ),
        )
    }

    @Test
    fun `attachment selection requires a name and enforces the existing byte limit`() {
        assertTrue(StudentProjectFileSelectionRules.acceptsAttachment("source.pdf", 1))
        assertTrue(StudentProjectFileSelectionRules.acceptsAttachment("source.pdf", null))
        assertFalse(StudentProjectFileSelectionRules.acceptsAttachment("source.exe", 1))
        assertFalse(StudentProjectFileSelectionRules.acceptsAttachment(" ", 1))
        assertFalse(StudentProjectFileSelectionRules.acceptsAttachment("source.pdf", 0))
        assertFalse(
            StudentProjectFileSelectionRules.acceptsAttachment(
                "source.pdf",
                20L * 1024 * 1024 + 1,
            ),
        )
    }

    @Test
    fun `bounded reader returns only a complete declared file and reports progress`() {
        val progress = mutableListOf<Long>()

        val bytes = readBoundedStudentProjectFileBytes(
            expectedSizeBytes = 3,
            maximumSizeBytes = 4,
            ensureActive = {},
            readInto = byteSource(byteArrayOf(7, 8, 9)),
            onProgress = progress::add,
        )

        assertContentEquals(byteArrayOf(7, 8, 9), bytes)
        assertEquals(listOf(0L, 3L), progress)
    }

    @Test
    fun `bounded reader rejects a source longer than its declared size`() {
        assertFailsWith<IllegalArgumentException> {
            readBoundedStudentProjectFileBytes(
                expectedSizeBytes = 2,
                maximumSizeBytes = 4,
                ensureActive = {},
                readInto = byteSource(byteArrayOf(1, 2, 3)),
                onProgress = {},
            )
        }
    }

    @Test
    fun `bounded reader rejects a truncated source`() {
        assertFailsWith<IllegalArgumentException> {
            readBoundedStudentProjectFileBytes(
                expectedSizeBytes = 3,
                maximumSizeBytes = 4,
                ensureActive = {},
                readInto = byteSource(byteArrayOf(1, 2)),
                onProgress = {},
            )
        }
    }

    @Test
    fun `picker gate admits only one in-flight picker or file read`() {
        val gate = StudentProjectFilePickerGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        assertTrue(gate.beginReadAfterSelection())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `picker cancellation before selection discards a later selected file`() {
        val gate = StudentProjectFilePickerGate()

        assertTrue(gate.tryAcquire())
        gate.cancelBeforeRead()

        assertFalse(gate.beginReadAfterSelection())
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `temporary export is cleaned unless ownership is handed to the system picker`() {
        val cleaned = mutableListOf<String>()
        val cancelledExport = StudentProjectTemporaryFileHandoff<String> { cleaned += it }
        cancelledExport.takeOwnership("cancelled-export.tmp")
        cancelledExport.cleanup()
        assertEquals(listOf("cancelled-export.tmp"), cleaned)

        val completedExport = StudentProjectTemporaryFileHandoff<String> { cleaned += it }
        completedExport.takeOwnership("shared-export.tmp")
        assertEquals("shared-export.tmp", completedExport.handoff())
        completedExport.cleanup()
        assertEquals(listOf("cancelled-export.tmp"), cleaned)
    }

    private fun byteSource(source: ByteArray): (ByteArray, Int) -> Int {
        var offset = 0
        return { target, maximumLength ->
            val count = minOf(maximumLength, source.size - offset)
            if (count > 0) source.copyInto(target, 0, offset, offset + count)
            offset += count
            count
        }
    }
}
