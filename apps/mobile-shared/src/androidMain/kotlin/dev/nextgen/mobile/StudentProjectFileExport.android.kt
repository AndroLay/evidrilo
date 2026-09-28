package dev.nextgen.mobile

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.nextgen.mobile.compose.BuildConfig
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual val isStudentProjectFileExportAvailable: Boolean = true
internal actual val studentProjectAppVersion: String = BuildConfig.EVIDRILO_APP_VERSION
internal actual val isStudentProjectFileImportAvailable: Boolean = true
internal actual val isStudentProjectAttachmentPickerAvailable: Boolean = true

@Composable
internal actual fun rememberStudentProjectFileExporter(
    onResult: (StudentProjectFileExportResult) -> Unit,
): (StudentProjectExportArtifact) -> Unit {
    val context = LocalContext.current.applicationContext
    val currentResult = rememberUpdatedState(onResult)
    val pendingArtifact = remember { mutableStateOf<StudentProjectExportArtifact?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val artifact = pendingArtifact.value
        pendingArtifact.value = null
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            currentResult.value(StudentProjectFileExportResult.Cancelled)
        } else if (artifact == null) {
            currentResult.value(StudentProjectFileExportResult.Failed("The export request expired before the file could be written. Your project snapshot remains saved locally."))
        } else {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = when {
                            artifact.binaryContent != null -> requireNotNull(artifact.binaryContent)
                            artifact.mimeType == "application/pdf" -> {
                                require(artifact.content.encodeToByteArray().size <= MAX_TEXT_EXPORT_BYTES)
                                renderProjectReportPdf(artifact.content)
                            }
                            artifact.mimeType == DOCX_MIME_TYPE -> {
                                require(artifact.content.encodeToByteArray().size <= MAX_TEXT_EXPORT_BYTES)
                                studentProjectDocxArchive(artifact.content)
                            }
                            else -> artifact.content.encodeToByteArray()
                        }
                        val sizeLimit = when {
                            artifact.mimeType == "application/pdf" || artifact.mimeType == DOCX_MIME_TYPE -> MAX_RICH_REPORT_EXPORT_BYTES
                            artifact.binaryContent == null -> MAX_TEXT_EXPORT_BYTES
                            else -> MAX_PROJECT_ARCHIVE_BYTES
                        }
                        require(bytes.size <= sizeLimit)
                        val output = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("The selected destination is not writable")
                        output.use { stream ->
                            stream.write(bytes)
                            stream.flush()
                        }
                    }.fold(
                        onSuccess = { StudentProjectFileExportResult.Saved(artifact.fileName) },
                        onFailure = { StudentProjectFileExportResult.Failed("The file could not be generated or written completely. Your project snapshot remains saved locally; check the destination for a partial file before retrying.") },
                    )
                }
                currentResult.value(outcome)
            }
        }
    }

    return { artifact ->
        if (pendingArtifact.value == null) {
            pendingArtifact.value = artifact
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = artifact.mimeType
                putExtra(Intent.EXTRA_TITLE, artifact.fileName)
            }
            runCatching { launcher.launch(intent) }.onFailure {
                pendingArtifact.value = null
                currentResult.value(StudentProjectFileExportResult.Failed("The system file picker could not be opened. Your project snapshot remains saved locally."))
            }
        }
    }
}

private const val MAX_TEXT_EXPORT_BYTES = 2 * 1024 * 1024
private const val MAX_RICH_REPORT_EXPORT_BYTES = 8 * 1024 * 1024
private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

internal fun renderProjectReportDocx(markdown: String): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { archive ->
        archive.writeDocxPart(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
                </Types>""".trimIndent(),
        )
        archive.writeDocxPart(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""".trimIndent(),
        )
        archive.writeDocxPart(
            "word/_rels/document.xml.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""".trimIndent(),
        )
        archive.writeDocxPart("word/document.xml", studentProjectDocxDocumentXml(markdown))
        archive.writeDocxPart(
            "word/styles.xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:rPr><w:sz w:val="22"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:rPr><w:b/><w:sz w:val="30"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:rPr><w:b/><w:sz w:val="24"/></w:rPr></w:style>
                  <w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:rPr><w:i/></w:rPr></w:style>
                </w:styles>""".trimIndent(),
        )
    }
    return output.toByteArray().also { require(it.size <= MAX_RICH_REPORT_EXPORT_BYTES) }
}

private fun ZipOutputStream.writeDocxPart(path: String, content: String) {
    putNextEntry(ZipEntry(path))
    write(content.encodeToByteArray())
    closeEntry()
}

private fun renderProjectReportPdf(markdown: String): ByteArray {
    val document = PdfDocument()
    val output = ByteArrayOutputStream()
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    val left = 48f
    val right = 547f
    val contentBottom = 778f

    fun newPage() {
        pageNumber += 1
        page = document.startPage(
            PdfDocument.PageInfo.Builder(595, 842, pageNumber).create(),
        )
        y = 54f
    }

    fun finishPage() {
        val current = page ?: return
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(94, 105, 121)
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        current.canvas.drawText(
            "Evidrilo · Student-entered content; not independently verified · Page $pageNumber",
            left,
            812f,
            footerPaint,
        )
        document.finishPage(current)
        page = null
    }

    fun lineHeight(paint: Paint): Float = (paint.fontMetrics.bottom - paint.fontMetrics.top + 3f).coerceAtLeast(14f)

    fun drawParagraph(text: String, fontSize: Float, bold: Boolean, indent: Float = 0f, before: Float = 0f, after: Float = 5f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(29, 41, 57)
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        y += before
        val availableWidth = right - left - indent
        val lines = wrapStudentProjectPdfText(text, availableWidth, paint::measureText)
        val leading = lineHeight(paint)
        lines.forEach { content ->
            if (page == null) newPage()
            if (y + leading > contentBottom) {
                finishPage()
                newPage()
            }
            page?.canvas?.drawText(content, left + indent, y, paint)
            y += leading
        }
        y += after
    }

    fun plainReportText(value: String): String = value
        .replace(Regex("!?(\\[([^]]+)])\\([^)]*\\)"), "$2")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)"), "$1")
        .filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }

    try {
        markdown.lineSequence().forEach { rawLine ->
            val normalized = plainReportText(rawLine).trim()
            when {
                normalized.isEmpty() -> y += 5f
                normalized.startsWith("```") || normalized == "---" -> Unit
                normalized.startsWith("#") -> {
                    val headingLevel = normalized.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    drawParagraph(
                        text = normalized.drop(headingLevel).trim(),
                        fontSize = when (headingLevel) { 1 -> 18f; 2 -> 15f; 3 -> 13f; else -> 12f },
                        bold = true,
                        before = if (headingLevel == 1) 10f else 6f,
                        after = 5f,
                    )
                }
                normalized.startsWith("- ") || normalized.startsWith("* ") ->
                    drawParagraph("• ${normalized.drop(2)}", 10.5f, bold = false, indent = 8f, after = 3f)
                normalized.startsWith("> ") ->
                    drawParagraph(normalized.drop(2), 9.5f, bold = false, indent = 12f, after = 5f)
                else -> drawParagraph(normalized, 10.5f, bold = false, after = 5f)
            }
        }
        if (page == null) newPage()
        finishPage()
        document.writeTo(output)
        return output.toByteArray()
    } finally {
        document.close()
    }
}

@Composable
internal actual fun rememberStudentProjectFileImporter(
    onProgress: (StudentProjectFileImportProgress) -> Unit,
    onResult: (StudentProjectFileImportResult) -> Unit,
): StudentProjectFileImportController {
    val context = LocalContext.current.applicationContext
    val currentResult = rememberUpdatedState(onResult)
    val currentProgress = rememberUpdatedState(onProgress)
    val scope = rememberCoroutineScope()
    val pickerGate = remember { StudentProjectFilePickerGate() }
    val activeReadJob = remember { mutableStateOf<Job?>(null) }
    val activeInputStream = remember { AtomicReference<InputStream?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            pickerGate.release()
            currentResult.value(StudentProjectFileImportResult.Cancelled)
        } else if (!pickerGate.beginReadAfterSelection()) {
            currentResult.value(StudentProjectFileImportResult.Cancelled)
        } else {
            val readJob = scope.launch {
                try {
                    val outcome = try {
                        withContext(Dispatchers.IO) {
                            val importContext = currentCoroutineContext()
                            importContext.ensureActive()
                            val metadata = context.contentResolver.query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                                null,
                                null,
                                null,
                            )?.use { cursor ->
                                if (!cursor.moveToFirst()) {
                                    null to null
                                } else {
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    val name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
                                    val size = sizeIndex.takeIf { it >= 0 }
                                        ?.let(cursor::getLong)
                                        ?.takeIf { it >= 0L }
                                    name to size
                                }
                            } ?: (null to null)
                            val (fileName, declaredSizeBytes) = metadata
                            require(StudentProjectFileSelectionRules.acceptsProjectArchive(fileName, declaredSizeBytes))
                            importContext.ensureActive()
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("The selected file cannot be opened")
                            activeInputStream.set(input)
                            val bytes = try {
                                readStudentProjectArchive(
                                    input = input,
                                    declaredSizeBytes = declaredSizeBytes,
                                    ensureActive = importContext::ensureActive,
                                    onProgress = { progress ->
                                        mainHandler.post {
                                            if (activeReadJob.value?.isActive == true) currentProgress.value(progress)
                                        }
                                    },
                                )
                            } finally {
                                activeInputStream.compareAndSet(input, null)
                            }
                            StudentProjectFileImportResult.Selected(fileName, bytes)
                        }
                    } catch (_: CancellationException) {
                        StudentProjectFileImportResult.Cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        StudentProjectFileImportResult.Failed(
                            "The selected file could not be read as a project archive. The original file and current projects were left unchanged.",
                        )
                    }
                    currentResult.value(outcome)
                } finally {
                    activeReadJob.value = null
                    pickerGate.release()
                }
            }
            activeReadJob.value = readJob
        }
    }

    return StudentProjectFileImportController(
        select = {
            if (pickerGate.tryAcquire()) {
                runCatching {
                    launcher.launch(arrayOf("application/vnd.evidrilo.project+zip", "application/zip", "application/octet-stream"))
                }.onFailure {
                    pickerGate.release()
                    currentResult.value(StudentProjectFileImportResult.Failed("The system file picker could not be opened. Your projects were not changed."))
                }
            }
        },
        cancel = {
            val readJob = activeReadJob.value
            if (readJob != null) {
                readJob.cancel()
                runCatching { activeInputStream.getAndSet(null)?.close() }
            } else {
                pickerGate.cancelBeforeRead()
            }
        },
    )
}

@Composable
internal actual fun rememberStudentProjectAttachmentPicker(
    onResult: (StudentProjectAttachmentPickResult) -> Unit,
): () -> Unit {
    val context = LocalContext.current.applicationContext
    val currentResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val pickerGate = remember { StudentProjectFilePickerGate() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            pickerGate.release()
            currentResult.value(StudentProjectAttachmentPickResult.Cancelled)
        } else {
            scope.launch {
                try {
                    val result = try {
                        withContext(Dispatchers.IO) {
                            val coroutineContext = currentCoroutineContext()
                            coroutineContext.ensureActive()
                            val metadata = context.contentResolver.query(
                                uri,
                                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                                null,
                                null,
                                null,
                            )?.use { cursor ->
                                if (!cursor.moveToFirst()) {
                                    null to null
                                } else {
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    val name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)
                                    val size = sizeIndex.takeIf { it >= 0 }
                                        ?.let(cursor::getLong)
                                        ?.takeIf { it >= 0L }
                                    name to size
                                }
                            } ?: (null to null)
                            val (fileName, declaredSizeBytes) = metadata
                            require(StudentProjectFileSelectionRules.acceptsAttachment(fileName, declaredSizeBytes))
                            val selectedFileName = requireNotNull(fileName)
                            val input = context.contentResolver.openInputStream(uri)
                                ?: error("The selected file cannot be opened")
                            val bytes = readStudentProjectAttachment(
                                input = input,
                                declaredSizeBytes = declaredSizeBytes,
                                ensureActive = coroutineContext::ensureActive,
                            )
                            StudentProjectAttachmentPickResult.Selected(StudentProjectAttachmentFile(selectedFileName, bytes))
                        }
                    } catch (_: CancellationException) {
                        StudentProjectAttachmentPickResult.Cancelled
                    } catch (_: Exception) {
                        StudentProjectAttachmentPickResult.Failed(
                            "The file could not be read safely. Choose a supported file under 20 MB; your project was not changed.",
                        )
                    }
                    currentResult.value(result)
                } finally {
                    pickerGate.release()
                }
            }
        }
    }
    return {
        if (pickerGate.tryAcquire()) {
            runCatching {
                launcher.launch(
                    arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/csv",
                        "text/plain",
                        "text/markdown",
                        "image/png",
                        "image/jpeg",
                    ),
                )
            }.onFailure {
                pickerGate.release()
                currentResult.value(
                    StudentProjectAttachmentPickResult.Failed("The system file picker could not be opened. Your project was not changed."),
                )
            }
        }
    }
}

internal fun readStudentProjectAttachment(
    input: InputStream,
    declaredSizeBytes: Long?,
    ensureActive: () -> Unit,
): ByteArray {
    input.use { stream ->
        val maxBytes = StudentProjectAttachmentRules.MAX_ATTACHMENT_BYTES
        require(declaredSizeBytes == null || declaredSizeBytes in 1..maxBytes)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var bytesRead = 0L
        while (true) {
            ensureActive()
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            bytesRead += count
            require(bytesRead <= maxBytes)
            output.write(buffer, 0, count)
        }
        require(bytesRead in 1..maxBytes)
        require(declaredSizeBytes == null || bytesRead == declaredSizeBytes)
        ensureActive()
        return output.toByteArray()
    }
}

internal const val MAX_PROJECT_ARCHIVE_BYTES = StudentProjectFileSelectionRules.MAX_PROJECT_ARCHIVE_BYTES
internal const val PROJECT_ARCHIVE_READ_CHUNK_BYTES = 8 * 1024
private const val PROJECT_ARCHIVE_PROGRESS_INTERVAL_BYTES = 256 * 1024

internal fun readStudentProjectArchive(
    input: InputStream,
    declaredSizeBytes: Long?,
    ensureActive: () -> Unit,
    onProgress: (StudentProjectFileImportProgress) -> Unit,
): ByteArray {
    input.use { stream ->
        require(declaredSizeBytes == null || declaredSizeBytes in 1L..MAX_PROJECT_ARCHIVE_BYTES.toLong())

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(PROJECT_ARCHIVE_READ_CHUNK_BYTES)
        var bytesRead = 0L
        var lastReportedBytes = -1L
        var nextProgressBytes = PROJECT_ARCHIVE_PROGRESS_INTERVAL_BYTES.toLong()

        fun reportProgress(force: Boolean = false) {
            if (force || bytesRead >= nextProgressBytes) {
                if (lastReportedBytes != bytesRead) {
                    onProgress(StudentProjectFileImportProgress(bytesRead, declaredSizeBytes))
                    lastReportedBytes = bytesRead
                }
                nextProgressBytes = bytesRead + PROJECT_ARCHIVE_PROGRESS_INTERVAL_BYTES
            }
        }

        reportProgress(force = true)
        while (true) {
            ensureActive()
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue

            bytesRead += count
            require(bytesRead <= MAX_PROJECT_ARCHIVE_BYTES.toLong())
            output.write(buffer, 0, count)
            reportProgress()
        }
        require(bytesRead > 0L)
        require(declaredSizeBytes == null || bytesRead == declaredSizeBytes)
        reportProgress(force = true)
        ensureActive()
        return output.toByteArray()
    }
}
