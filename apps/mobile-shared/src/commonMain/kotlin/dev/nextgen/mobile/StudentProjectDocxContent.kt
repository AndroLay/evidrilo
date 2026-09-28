package dev.nextgen.mobile

import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipOutputStream

/** Creates the document.xml part of a DOCX without treating Markdown as trusted XML. */
internal fun studentProjectDocxDocumentXml(markdown: String): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
    append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
    markdown.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line == "---" || line == "***" || line.startsWith("```")) return@forEach

        val headingLevel = line.takeWhile { it == '#' }.length
        val style: String
        val content: String
        when {
            headingLevel > 0 && line.getOrNull(headingLevel) == ' ' -> {
                style = when (headingLevel) {
                    1 -> "Title"
                    2 -> "Heading1"
                    3 -> "Heading2"
                    else -> "Heading3"
                }
                content = line.drop(headingLevel).trim()
            }
            line.startsWith("> ") -> {
                style = "Quote"
                content = line.drop(2)
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> {
                style = "Normal"
                content = "• ${line.drop(2)}"
            }
            else -> {
                style = "Normal"
                content = line
            }
        }
        append("<w:p><w:pPr><w:pStyle w:val=\"")
        append(style)
        append("\"/></w:pPr><w:r><w:t xml:space=\"preserve\">")
        append(escapeStudentProjectXmlText(stripMarkdownInline(content)))
        append("</w:t></w:r></w:p>")
    }
    append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr>")
    append("</w:body></w:document>")
}

/** Builds a minimal, portable Office Open XML package from the same report text used by PDF export. */
internal fun studentProjectDocxArchive(markdown: String): ByteArray {
    val parts = listOf(
        "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
              <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            </Types>""".trimIndent(),
        "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>""".trimIndent(),
        "word/_rels/document.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>""".trimIndent(),
        "word/document.xml" to studentProjectDocxDocumentXml(markdown),
        "word/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="22"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:rPr><w:b/><w:sz w:val="30"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:rPr><w:b/><w:sz w:val="24"/></w:rPr></w:style>
              <w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:rPr><w:i/></w:rPr></w:style>
            </w:styles>""".trimIndent(),
    )
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { archive ->
        parts.forEach { (path, content) ->
            archive.putNextEntry(ZipEntry(path))
            archive.write(content.encodeToByteArray())
            archive.closeEntry()
        }
    }
    return output.toByteArray()
}

private fun stripMarkdownInline(text: String): String = text
    .replace(Regex("!?\\[([^]]+)]\\([^)]*\\)"), "$1")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
    .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "$1")

private fun escapeStudentProjectXmlText(text: String): String = buildString {
    var index = 0
    while (index < text.length) {
        val first = text[index]
        val codePoint: Int
        val value: String
        if (first.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
            val second = text[index + 1]
            codePoint = 0x10000 + ((first.code - 0xD800) shl 10) + (second.code - 0xDC00)
            value = text.substring(index, index + 2)
            index += 2
        } else {
            codePoint = first.code
            value = first.toString()
            index += 1
        }

        if (!isValidXmlCodePoint(codePoint)) continue
        append(
            when (codePoint) {
                0x26 -> "&amp;"
                0x3C -> "&lt;"
                0x3E -> "&gt;"
                0x22 -> "&quot;"
                0x27 -> "&apos;"
                else -> value
            },
        )
    }
}

private fun isValidXmlCodePoint(codePoint: Int): Boolean =
    codePoint == 0x9
        || codePoint == 0xA
        || codePoint == 0xD
        || codePoint in 0x20..0xD7FF
        || codePoint in 0xE000..0xFFFD
        || codePoint in 0x10000..0x10FFFF
