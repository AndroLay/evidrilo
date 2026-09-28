package dev.nextgen.mobile

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudentProjectDocumentTextExtractionTest {
    @Test
    fun `docx preview preserves reading order and table cells while excluding tracked deletions`() {
        val document = """
            <?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>First &amp; safe</w:t></w:r></w:p>
                <w:p><w:del><w:r><w:delText>Removed version</w:delText></w:r></w:del><w:r><w:t>Current version</w:t></w:r></w:p>
                <w:tbl><w:tr>
                  <w:tc><w:p><w:r><w:t>Left cell</w:t></w:r></w:p></w:tc>
                  <w:tc><w:p><w:r><w:t>Right cell</w:t></w:r></w:p></w:tc>
                </w:tr></w:tbl>
              </w:body>
            </w:document>
        """.trimIndent()

        val result = extractStudentProjectDocumentText(
            fileName = "study.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            bytes = docx(document),
        )

        val extracted = assertIs<StudentProjectDocumentTextResult.Extracted>(result)
        assertEquals("First & safe\nCurrent version\nLeft cell\tRight cell", extracted.text)
        assertEquals(false, extracted.isTruncated)
    }

    @Test
    fun `docx preview rejects DTD and external entity payloads`() {
        val document = """
            <!DOCTYPE w:document [<!ENTITY x SYSTEM "file:///etc/passwd">]>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent()

        val result = extractStudentProjectDocumentText(
            fileName = "unsafe.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            bytes = docx(document),
        )

        assertEquals(StudentProjectDocumentTextResult.InvalidDocument, result)
    }

    @Test
    fun `docx preview rejects oversized expanded document xml`() {
        val document = """
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>${"x".repeat(4 * 1024 * 1024 + 1)}</w:t></w:r></w:p></w:body>
            </w:document>
        """.trimIndent()

        val result = extractStudentProjectDocumentText(
            fileName = "large.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            bytes = docx(document),
        )

        assertEquals(StudentProjectDocumentTextResult.TooLarge, result)
    }

    @Test
    fun `preview rejects a docx without its main document and does not parse pdf as text`() {
        val missingMainPart = extractStudentProjectDocumentText(
            fileName = "missing.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            bytes = zip("custom.xml" to "<document/>")
        )
        val pdf = extractStudentProjectDocumentText(
            fileName = "paper.pdf",
            mimeType = "application/pdf",
            bytes = "%PDF-1.7\nnot text extraction".encodeToByteArray(),
        )

        assertEquals(StudentProjectDocumentTextResult.InvalidDocument, missingMainPart)
        assertEquals(StudentProjectDocumentTextResult.UnsupportedFormat, pdf)
    }

    @Test
    fun `docx parser honors cancellation while scanning the archive`() {
        var checks = 0

        assertFailsWith<CancellationException> {
            extractStudentProjectDocxText(
                docx("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body/></w:document>"),
                ensureActive = {
                    checks++
                    if (checks == 2) throw CancellationException("preview cancelled")
                },
            )
        }

        assertTrue(checks >= 2)
    }

    private fun docx(documentXml: String): ByteArray = zip("word/document.xml" to documentXml)

    private fun zip(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { archive ->
            entries.forEach { (name, text) ->
                archive.putNextEntry(ZipEntry(name))
                archive.write(text.encodeToByteArray())
                archive.closeEntry()
            }
        }
        output.toByteArray()
    }
}
