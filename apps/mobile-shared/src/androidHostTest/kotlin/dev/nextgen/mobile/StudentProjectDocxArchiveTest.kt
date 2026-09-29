package dev.nextgen.mobile

import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudentProjectDocxArchiveTest {
    @Test
    fun `Android report exporter writes a complete well formed OOXML package`() {
        val bytes = renderProjectReportDocx("# Student report\nEvidence: A & B <reviewed> 😀")
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                entries[entry.name] = archive.readBytes().decodeToString()
                archive.closeEntry()
            }
        }

        val requiredParts = setOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "word/_rels/document.xml.rels",
            "word/document.xml",
            "word/styles.xml",
        )
        assertTrue(entries.keys.containsAll(requiredParts))
        assertTrue(entries.getValue("word/document.xml").contains("A &amp; B &lt;reviewed&gt; 😀"))
        assertXmlRoot(entries.getValue("[Content_Types].xml"), "Types")
        assertXmlRoot(entries.getValue("_rels/.rels"), "Relationships")
        assertXmlRoot(entries.getValue("word/_rels/document.xml.rels"), "Relationships")
        assertXmlRoot(entries.getValue("word/document.xml"), "document")
        assertXmlRoot(entries.getValue("word/styles.xml"), "styles")
        assertNotNull(entries["word/document.xml"])
    }

    private fun assertXmlRoot(xml: String, expectedLocalName: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        assertEquals(
            expectedLocalName,
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement.localName,
        )
    }
}
