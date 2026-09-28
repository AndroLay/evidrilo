package dev.nextgen.mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

private const val WORDPROCESSINGML_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val DOCX_DOCUMENT_XML_LIMIT = 4 * 1024 * 1024
private const val DOCX_EXPANDED_CONTENT_LIMIT = 40L * 1024 * 1024
private const val DOCX_ENTRY_COUNT_LIMIT = 200

internal actual val studentProjectDocxPreviewSupported: Boolean = true

internal actual fun extractStudentProjectDocxText(
    bytes: ByteArray,
    ensureActive: () -> Unit,
): StudentProjectDocumentTextResult = try {
    ensureActive()
    when (val mainPart = readDocxMainPart(bytes, ensureActive)) {
        is DocxMainPart.Ready -> parseWordDocument(mainPart.xml, ensureActive)
        DocxMainPart.Missing -> StudentProjectDocumentTextResult.InvalidDocument
        DocxMainPart.TooLarge -> StudentProjectDocumentTextResult.TooLarge
        DocxMainPart.Invalid -> StudentProjectDocumentTextResult.InvalidDocument
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: DocumentLimitExceeded) {
    StudentProjectDocumentTextResult.TooLarge
} catch (_: Exception) {
    StudentProjectDocumentTextResult.InvalidDocument
}

private sealed interface DocxMainPart {
    data class Ready(val xml: ByteArray) : DocxMainPart
    data object Missing : DocxMainPart
    data object TooLarge : DocxMainPart
    data object Invalid : DocxMainPart
}

private class DocumentLimitExceeded : Exception()

private fun readDocxMainPart(bytes: ByteArray, ensureActive: () -> Unit): DocxMainPart {
    var mainDocumentXml: ByteArray? = null
    val entryNames = mutableSetOf<String>()
    var entryCount = 0
    var expandedBytes = 0L

    ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
        while (true) {
            ensureActive()
            val entry = archive.nextEntry ?: break
            entryCount++
            if (entryCount > DOCX_ENTRY_COUNT_LIMIT) return DocxMainPart.TooLarge
            if (entry.name.isBlank() || entry.name.length > 512 || !entryNames.add(entry.name)) {
                return DocxMainPart.Invalid
            }
            if (entry.size > DOCX_EXPANDED_CONTENT_LIMIT) return DocxMainPart.TooLarge

            val isMainPart = entry.name == "word/document.xml" && !entry.isDirectory
            if (isMainPart && mainDocumentXml != null) return DocxMainPart.Invalid
            val captured = if (isMainPart) ByteArrayOutputStream() else null
            val buffer = ByteArray(8 * 1024)
            while (true) {
                ensureActive()
                val count = archive.read(buffer)
                if (count < 0) break
                expandedBytes += count
                if (expandedBytes > DOCX_EXPANDED_CONTENT_LIMIT) return DocxMainPart.TooLarge
                if (isMainPart && captured!!.size().toLong() + count > DOCX_DOCUMENT_XML_LIMIT) {
                    return DocxMainPart.TooLarge
                }
                captured?.write(buffer, 0, count)
            }
            if (isMainPart) mainDocumentXml = captured!!.toByteArray()
            archive.closeEntry()
        }
    }

    return mainDocumentXml?.let(DocxMainPart::Ready) ?: DocxMainPart.Missing
}

private fun parseWordDocument(xml: ByteArray, ensureActive: () -> Unit): StudentProjectDocumentTextResult {
    ensureActive()
    if (xml.isEmpty()) return StudentProjectDocumentTextResult.InvalidDocument

    val factory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }
    val handler = WordDocumentTextHandler(ensureActive)
    val reader = factory.newSAXParser().xmlReader
    reader.contentHandler = handler
    reader.errorHandler = handler
    reader.entityResolver = org.xml.sax.EntityResolver { _, _ ->
        throw SAXException("External entities are not allowed in a document preview.")
    }
    reader.parse(InputSource(ByteArrayInputStream(xml)))

    if (!handler.documentSeen || !handler.bodySeen) return StudentProjectDocumentTextResult.InvalidDocument
    val text = handler.text().trimEnd()
    if (text.isBlank()) return StudentProjectDocumentTextResult.EmptyDocument
    return StudentProjectDocumentTextResult.Extracted(text, handler.isTruncated)
}

private class WordDocumentTextHandler(
    private val ensureActive: () -> Unit,
) : DefaultHandler() {
    private val content = StringBuilder()
    private val cellCounts = ArrayDeque<Int>()
    private var ignoredRevisionDepth = 0
    private var inBody = false
    private var inText = false
    var documentSeen = false
        private set
    var bodySeen = false
        private set
    var isTruncated = false
        private set

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        ensureActive()
        if (ignoredRevisionDepth > 0) {
            ignoredRevisionDepth++
            return
        }
        val name = localName.ifBlank { qName.substringAfter(':') }
        if (uri != WORDPROCESSINGML_NAMESPACE) return

        if (!documentSeen) {
            documentSeen = name == "document"
            return
        }
        if (!inBody) {
            if (name == "body") {
                inBody = true
                bodySeen = true
            }
            return
        }
        if (name == "del" || name == "moveFrom") {
            ignoredRevisionDepth = 1
            return
        }

        when (name) {
            "tr" -> cellCounts.addLast(0)
            "tc" -> {
                if (cellCounts.isNotEmpty()) {
                    val count = cellCounts.removeLast()
                    if (count > 0) {
                        while (content.isNotEmpty() && content.last() == '\n') content.setLength(content.length - 1)
                        appendLimited("\t")
                    }
                    cellCounts.addLast(count + 1)
                }
            }
            "p" -> if (content.isNotEmpty() && content.last() != '\n' && content.last() != '\t') appendLimited("\n")
            "t" -> inText = true
            "tab" -> appendLimited("\t")
            "br", "cr" -> appendLimited("\n")
        }
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        if (ignoredRevisionDepth > 0) {
            ignoredRevisionDepth--
            return
        }
        val name = localName.ifBlank { qName.substringAfter(':') }
        if (uri != WORDPROCESSINGML_NAMESPACE) return
        if (!inBody) return

        when (name) {
            "t" -> inText = false
            "p" -> if (content.isNotEmpty() && content.last() != '\n') appendLimited("\n")
            "tr" -> {
                if (content.isNotEmpty() && content.last() != '\n') appendLimited("\n")
                if (cellCounts.isNotEmpty()) cellCounts.removeLast()
            }
            "body" -> inBody = false
        }
    }

    override fun characters(characters: CharArray, start: Int, length: Int) {
        ensureActive()
        if (inBody && inText && ignoredRevisionDepth == 0) {
            appendLimited(String(characters, start, length))
        }
    }

    fun text(): String {
        if (isTruncated && content.isNotEmpty() && content.last().isHighSurrogate()) {
            content.setLength(content.length - 1)
        }
        return content.toString()
    }

    private fun appendLimited(value: String) {
        val remaining = STUDENT_PROJECT_DOCUMENT_PREVIEW_MAX_CHARS - content.length
        if (remaining <= 0) {
            if (value.isNotEmpty()) isTruncated = true
            return
        }
        if (value.length <= remaining) {
            content.append(value)
        } else {
            content.append(value, 0, remaining)
            isTruncated = true
        }
    }

    override fun error(exception: SAXParseException) = throw exception
    override fun fatalError(exception: SAXParseException) = throw exception
}
