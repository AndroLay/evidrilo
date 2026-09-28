package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact

internal sealed interface StudentProjectFileExportResult {
    data class Saved(val fileName: String) : StudentProjectFileExportResult
    data object Cancelled : StudentProjectFileExportResult
    data class Failed(val message: String) : StudentProjectFileExportResult
    data class Unavailable(val message: String) : StudentProjectFileExportResult
}

internal sealed interface StudentProjectFileImportResult {
    data class Selected(val fileName: String?, val bytes: ByteArray) : StudentProjectFileImportResult
    data object Cancelled : StudentProjectFileImportResult
    data class Failed(val message: String) : StudentProjectFileImportResult
    data class Unavailable(val message: String) : StudentProjectFileImportResult
}

internal data class StudentProjectAttachmentFile(
    val fileName: String,
    val bytes: ByteArray,
)

internal sealed interface StudentProjectAttachmentPickResult {
    data class Selected(val file: StudentProjectAttachmentFile) : StudentProjectAttachmentPickResult
    data object Cancelled : StudentProjectAttachmentPickResult
    data class Failed(val message: String) : StudentProjectAttachmentPickResult
    data class Unavailable(val message: String) : StudentProjectAttachmentPickResult
}

internal data class StudentProjectFileImportProgress(
    val bytesRead: Long,
    val totalBytes: Long?,
)

internal data class StudentProjectFileImportController(
    val select: () -> Unit,
    val cancel: () -> Unit,
)

internal fun StudentProjectFileExportResult.toNotice(): String = when (this) {
    is StudentProjectFileExportResult.Saved -> if (fileName.endsWith(".evproj", ignoreCase = true)) {
        "$fileName was exported. It contains project data, selected revision history, and verified attachments when available. Checksums detect corruption, not whether sources or claims are true."
    } else {
        "$fileName was exported. It contains the saved project snapshot and does not verify sources or claims."
    }
    StudentProjectFileExportResult.Cancelled -> "Export cancelled. Your project snapshot remains saved locally; the file export did not complete."
    is StudentProjectFileExportResult.Failed -> message
    is StudentProjectFileExportResult.Unavailable -> message
}

/** Keeps the structured report text as the source for the Android PDF renderer. */
internal fun StudentProjectExportArtifact.toPdfReportInput(): StudentProjectExportArtifact = copy(
    fileName = fileName.substringBeforeLast('.', fileName) + ".pdf",
    mimeType = "application/pdf",
    binaryContent = null,
)

internal fun StudentProjectExportArtifact.toDocxReportInput(): StudentProjectExportArtifact = copy(
    fileName = fileName.substringBeforeLast('.', fileName) + ".docx",
    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    binaryContent = null,
)

/** Wraps normal text at word boundaries and breaks unusually long tokens by Unicode code point. */
internal fun wrapStudentProjectPdfText(
    text: String,
    maxWidth: Float,
    measureText: (String) -> Float,
): List<String> {
    require(maxWidth > 0f)
    val output = mutableListOf<String>()

    fun splitLongToken(token: String): List<String> {
        val parts = mutableListOf<String>()
        var part = StringBuilder()
        var index = 0
        while (index < token.length) {
            val start = index
            val first = token[index]
            index += if (first.isHighSurrogate() && index + 1 < token.length && token[index + 1].isLowSurrogate()) 2 else 1
            val codePoint = token.substring(start, index)
            val candidate = part.toString() + codePoint
            if (part.isNotEmpty() && measureText(candidate) > maxWidth) {
                parts += part.toString()
                part = StringBuilder(codePoint)
            } else {
                part.append(codePoint)
            }
        }
        if (part.isNotEmpty()) parts += part.toString()
        return parts
    }

    text.split('\n').forEach { paragraph ->
        val words = paragraph.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (words.isEmpty()) {
            output += ""
            return@forEach
        }

        var line = ""
        words.forEach { word ->
            if (measureText(word) <= maxWidth) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && measureText(candidate) > maxWidth) {
                    output += line
                    line = word
                } else {
                    line = candidate
                }
            } else {
                if (line.isNotEmpty()) {
                    output += line
                    line = ""
                }
                val parts = splitLongToken(word)
                if (parts.size > 1) output += parts.dropLast(1)
                line = parts.lastOrNull().orEmpty()
            }
        }
        if (line.isNotEmpty()) output += line
    }
    return output
}

internal expect val isStudentProjectFileExportAvailable: Boolean
internal expect val studentProjectAppVersion: String
internal expect val isStudentProjectFileImportAvailable: Boolean
internal expect val isStudentProjectAttachmentPickerAvailable: Boolean

@Composable
internal expect fun rememberStudentProjectFileExporter(
    onResult: (StudentProjectFileExportResult) -> Unit,
): (StudentProjectExportArtifact) -> Unit

@Composable
internal expect fun rememberStudentProjectFileImporter(
    onProgress: (StudentProjectFileImportProgress) -> Unit,
    onResult: (StudentProjectFileImportResult) -> Unit,
): StudentProjectFileImportController

@Composable
internal expect fun rememberStudentProjectAttachmentPicker(
    onResult: (StudentProjectAttachmentPickResult) -> Unit,
): () -> Unit
