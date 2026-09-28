package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StudentProjectDocumentTextTest {
    @Test
    fun `plain text preview removes a UTF-8 byte-order mark and keeps student text unchanged`() {
        val result = extractStudentProjectDocumentText(
            fileName = "notes.txt",
            mimeType = "text/plain",
            bytes = "\uFEFFResearch note: café\nSecond line".encodeToByteArray(),
        )

        val extracted = assertIs<StudentProjectDocumentTextResult.Extracted>(result)
        assertEquals("Research note: café\nSecond line", extracted.text)
        assertEquals(false, extracted.isTruncated)
    }

    @Test
    fun `plain text preview truncates long content at a safe character boundary`() {
        val content = "a".repeat(STUDENT_PROJECT_DOCUMENT_PREVIEW_MAX_CHARS - 1) + "😀tail"
        val result = extractStudentProjectDocumentText(
            fileName = "notes.md",
            mimeType = "text/markdown",
            bytes = content.encodeToByteArray(),
        )

        val extracted = assertIs<StudentProjectDocumentTextResult.Extracted>(result)
        assertEquals(STUDENT_PROJECT_DOCUMENT_PREVIEW_MAX_CHARS - 1, extracted.text.length)
        assertEquals(true, extracted.isTruncated)
    }

    @Test
    fun `plain text preview rejects invalid UTF-8 and unsupported file formats`() {
        val invalidUtf8 = extractStudentProjectDocumentText(
            fileName = "notes.txt",
            mimeType = "text/plain",
            bytes = byteArrayOf(0xC3.toByte(), 0x28),
        )
        val unsupported = extractStudentProjectDocumentText(
            fileName = "paper.pdf",
            mimeType = "application/pdf",
            bytes = "%PDF".encodeToByteArray(),
        )

        assertEquals(StudentProjectDocumentTextResult.InvalidDocument, invalidUtf8)
        assertEquals(StudentProjectDocumentTextResult.UnsupportedFormat, unsupported)
    }
}
