package dev.nextgen.mobile

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudentProjectDocxContentTest {
    @Test
    fun `generated WordprocessingML is well formed and excludes invalid XML controls`() {
        val xml = studentProjectDocxDocumentXml("# Summary\nA & B <checked> 😀 \u0001")
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

        assertEquals("document", document.documentElement.localName)
        assertTrue(xml.contains("A &amp; B &lt;checked&gt; 😀"))
        assertFalse(xml.contains('\u0001'))
    }
}
