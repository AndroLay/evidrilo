package dev.nextgen.mobile

internal const val STUDENT_PROJECT_DOCUMENT_PREVIEW_MAX_CHARS = 12_000
private const val MAX_PLAIN_TEXT_BYTES = 4 * 1024 * 1024

internal sealed interface StudentProjectDocumentTextResult {
    data class Extracted(val text: String, val isTruncated: Boolean) : StudentProjectDocumentTextResult
    data object EmptyDocument : StudentProjectDocumentTextResult
    data object UnsupportedFormat : StudentProjectDocumentTextResult
    data object InvalidDocument : StudentProjectDocumentTextResult
    data object TooLarge : StudentProjectDocumentTextResult
}

internal fun extractStudentProjectDocumentText(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
    ensureActive: () -> Unit = {},
): StudentProjectDocumentTextResult {
    ensureActive()
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (bytes.isEmpty()) return StudentProjectDocumentTextResult.EmptyDocument
    if (bytes.size > 20 * 1024 * 1024) return StudentProjectDocumentTextResult.TooLarge

    return when (extension) {
        "docx" -> {
            if (!mimeType.isSupportedDocxMime()) StudentProjectDocumentTextResult.UnsupportedFormat
            else extractStudentProjectDocxText(bytes, ensureActive)
        }
        "txt", "md", "csv" -> extractPlainText(extension, mimeType, bytes)
        else -> StudentProjectDocumentTextResult.UnsupportedFormat
    }
}

internal expect fun extractStudentProjectDocxText(
    bytes: ByteArray,
    ensureActive: () -> Unit = {},
): StudentProjectDocumentTextResult
internal expect val studentProjectDocxPreviewSupported: Boolean

internal fun isStudentProjectDocumentTextPreviewSupported(fileName: String, mimeType: String): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "docx" -> studentProjectDocxPreviewSupported && mimeType.isSupportedDocxMime()
        "txt", "md", "csv" -> mimeType.isSupportedTextMime(extension)
        else -> false
    }
}

private fun extractPlainText(
    extension: String,
    mimeType: String,
    bytes: ByteArray,
): StudentProjectDocumentTextResult {
    if (!mimeType.isSupportedTextMime(extension)) return StudentProjectDocumentTextResult.UnsupportedFormat
    if (bytes.size > MAX_PLAIN_TEXT_BYTES) return StudentProjectDocumentTextResult.TooLarge

    val decoded = try {
        bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
    } catch (_: Exception) {
        return StudentProjectDocumentTextResult.InvalidDocument
    }
    if (decoded.isBlank()) return StudentProjectDocumentTextResult.EmptyDocument
    return decoded.toPreview()
}

private fun String.toPreview(): StudentProjectDocumentTextResult.Extracted {
    if (length <= STUDENT_PROJECT_DOCUMENT_PREVIEW_MAX_CHARS) {
        return StudentProjectDocumentTextResult.Extracted(trimEnd(), isTruncated = false)
    }
    var end = STUDENT_PROJECT_DOCUMENT_PREVIEW_MAX_CHARS
    if (this[end - 1].isHighSurrogate()) end--
    return StudentProjectDocumentTextResult.Extracted(
        text = substring(0, end).trimEnd(),
        isTruncated = true,
    )
}

private fun String.isSupportedDocxMime(): Boolean = lowercase() in setOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/octet-stream",
    "application/zip",
)

private fun String.isSupportedTextMime(extension: String): Boolean {
    val normalized = lowercase()
    if (normalized in setOf("application/octet-stream", "text/plain")) return true
    return when (extension) {
        "md" -> normalized == "text/markdown" || normalized == "text/x-markdown"
        "csv" -> normalized == "text/csv" || normalized == "application/vnd.ms-excel"
        else -> false
    }
}
