package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact
import no.synth.kmpzip.io.ByteArrayInputStream
import no.synth.kmpzip.zip.ZipInputStream

class StudentProjectFileExportTest {
    @Test
    fun `PDF report input keeps the report content but requests a distinct PDF artifact`() {
        val markdown = StudentProjectExportArtifact(
            fileName = "project-report.md",
            mimeType = "text/markdown",
            content = "# Student report\nNot independently verified.",
        )

        val pdf = markdown.toPdfReportInput()

        assertEquals("project-report.pdf", pdf.fileName)
        assertEquals("application/pdf", pdf.mimeType)
        assertEquals(markdown.content, pdf.content)
        assertNull(pdf.binaryContent)
    }

    @Test
    fun `DOCX report input keeps content but requests a distinct Word artifact`() {
        val markdown = StudentProjectExportArtifact(
            fileName = "project-report.md",
            mimeType = "text/markdown",
            content = "# Student report\nNot independently verified.",
        )

        val docx = markdown.toDocxReportInput()

        assertEquals("project-report.docx", docx.fileName)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx.mimeType)
        assertEquals(markdown.content, docx.content)
        assertNull(docx.binaryContent)
    }

    @Test
    fun `DOCX archive contains the required package parts and escaped report text`() {
        val archiveBytes = studentProjectDocxArchive("# Student report\nA & B <checked> 😀")
        val entries = buildMap {
            ZipInputStream(ByteArrayInputStream(archiveBytes)).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    put(entry.name, archive.readBytes().decodeToString())
                }
            }
        }
        assertEquals(
            setOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/_rels/document.xml.rels",
                "word/document.xml",
                "word/styles.xml",
            ),
            entries.keys,
        )
        assertTrue(entries.getValue("word/document.xml").contains("Student report"))
        assertTrue(entries.getValue("word/document.xml").contains("A &amp; B &lt;checked&gt; 😀"))
    }

    @Test
    fun `DOCX report content preserves headings bullets Unicode and escaped student text`() {
        val xml = studentProjectDocxDocumentXml(
            "# Summary\nA & B <checked> 😀\n- Next step",
        )

        assertTrue(xml.contains("<w:pStyle w:val=\"Title\"/>"))
        assertTrue(xml.contains("A &amp; B &lt;checked&gt; 😀"))
        assertTrue(xml.contains("• Next step"))
        assertTrue(!xml.contains("A & B <checked>"))
    }

    @Test
    fun `PDF report text wraps both ordinary lines and unbroken long tokens`() {
        val lines = wrapStudentProjectPdfText(
            text = "Evidence https://example.test/verylongpath",
            maxWidth = 12f,
            measureText = { it.length.toFloat() },
        )

        assertEquals(
            listOf("Evidence", "https://exam", "ple.test/ver", "ylongpath"),
            lines,
        )
        assertTrue(lines.all { it.length <= 12 })
    }

    @Test
    fun `export result gives a truthful saved notice`() {
        val notice = StudentProjectFileExportResult.Saved("student-project.md").toNotice()

        assertTrue(notice.contains("student-project.md was exported"))
        assertTrue(notice.contains("does not verify sources or claims"))
    }

    @Test
    fun `cancel and failure notices do not imply project data was lost`() {
        assertEquals(
            "Export cancelled. Your project snapshot remains saved locally; the file export did not complete.",
            StudentProjectFileExportResult.Cancelled.toNotice(),
        )
        assertTrue(
            StudentProjectFileExportResult.Failed("Could not write the file.").toNotice()
                .contains("Could not write the file"),
        )
        assertTrue(
            StudentProjectFileExportResult.Unavailable("Export is unavailable.").toNotice()
                .contains("Export is unavailable"),
        )
        assertTrue(
            StudentProjectFileExportResult.Failed("The destination may contain an incomplete export.").toNotice()
                .contains("incomplete export"),
        )
    }
}
