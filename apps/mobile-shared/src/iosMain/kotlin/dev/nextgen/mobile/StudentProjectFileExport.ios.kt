@file:OptIn(kotlinx.cinterop.BetaInteropApi::class)

package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSData
import platform.Foundation.NSBundle
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorReadingWithoutChanges
import platform.Foundation.NSInputStream
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSUUID
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsBeginPDFContextToFile
import platform.UIKit.UIGraphicsBeginPDFPageWithInfo
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.drawAtPoint
import platform.UIKit.sizeWithAttributes
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

internal actual val isStudentProjectFileExportAvailable: Boolean = true
internal actual val studentProjectAppVersion: String =
    NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String ?: "unknown"
internal actual val isStudentProjectFileImportAvailable: Boolean = true
// Keep the attachment action hidden until iosMain has a private persistent attachment store.
internal actual val isStudentProjectAttachmentPickerAvailable: Boolean = false

private const val MAX_TEXT_EXPORT_BYTES = 2 * 1024 * 1024
private const val MAX_RICH_REPORT_EXPORT_BYTES = 8 * 1024 * 1024
private const val PROJECT_ARCHIVE_MIME_TYPE = "application/vnd.evidrilo.project+zip"
private const val PROJECT_ARCHIVE_ERROR =
    "The selected file could not be read as a project archive. The original file and current projects were left unchanged."
private const val ATTACHMENT_ERROR =
    "The file could not be read safely. Choose a supported file under 20 MB; your project was not changed."

@Composable
internal actual fun rememberStudentProjectFileExporter(
    onResult: (StudentProjectFileExportResult) -> Unit,
): (StudentProjectExportArtifact) -> Unit {
    val host = LocalUIViewController.current
    val currentResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val isBusy = remember { mutableStateOf(false) }
    val pendingUrl = remember { mutableStateOf<NSURL?>(null) }
    val pendingFileName = remember { mutableStateOf<String?>(null) }
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val fileName = pendingFileName.value
                cleanupTemporaryFile(pendingUrl.value)
                pendingUrl.value = null
                pendingFileName.value = null
                isBusy.value = false
                if (didPickDocumentsAtURLs.isEmpty() || fileName == null) {
                    currentResult.value(
                        StudentProjectFileExportResult.Failed(
                            "The export destination did not confirm a saved file. Your project snapshot remains saved locally.",
                        ),
                    )
                } else {
                    currentResult.value(StudentProjectFileExportResult.Saved(fileName))
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                cleanupTemporaryFile(pendingUrl.value)
                pendingUrl.value = null
                pendingFileName.value = null
                isBusy.value = false
                currentResult.value(StudentProjectFileExportResult.Cancelled)
            }
        }
    }

    return remember(host, delegate) {
        { artifact ->
            if (!isBusy.value) {
                isBusy.value = true
                scope.launch {
                    val temporaryFileHandoff = StudentProjectTemporaryFileHandoff<NSURL>(::cleanupTemporaryFile)
                    val prepared = try {
                        val bytes = if (artifact.mimeType == "application/pdf") {
                            withContext(Dispatchers.Main.immediate) {
                                prepareStudentProjectExportBytes(artifact)
                            }
                        } else {
                            withContext(Dispatchers.Default) {
                                prepareStudentProjectExportBytes(artifact)
                            }
                        }
                        val url = createTemporaryExportUrl(artifact.fileName)
                        temporaryFileHandoff.takeOwnership(url)
                        pendingUrl.value = url
                        withContext(Dispatchers.Default) {
                            writeTemporaryExportFile(url, bytes)
                        }
                        coroutineContext.ensureActive()
                        url
                    } catch (cancelled: CancellationException) {
                        temporaryFileHandoff.cleanup()
                        pendingUrl.value = null
                        pendingFileName.value = null
                        isBusy.value = false
                        throw cancelled
                    } catch (_: Exception) {
                        temporaryFileHandoff.cleanup()
                        pendingUrl.value = null
                        pendingFileName.value = null
                        isBusy.value = false
                        currentResult.value(
                            StudentProjectFileExportResult.Failed(
                                "The file could not be generated safely. Your project snapshot remains saved locally.",
                            ),
                        )
                        null
                    }
                    if (prepared != null) {
                        pendingFileName.value = artifact.fileName
                        runCatching {
                            val picker = UIDocumentPickerViewController(
                                forExportingURLs = listOf(prepared),
                                asCopy = true,
                            )
                            picker.setDelegate(delegate)
                            host.presentViewController(picker, animated = true) {
                                // UIKit calls this only after the presented controller appears.
                                temporaryFileHandoff.handoff()
                            }
                        }.onFailure {
                            temporaryFileHandoff.cleanup()
                            pendingUrl.value = null
                            pendingFileName.value = null
                            isBusy.value = false
                            currentResult.value(
                                StudentProjectFileExportResult.Failed(
                                    "The system file picker could not be opened. Your project snapshot remains saved locally.",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal actual fun rememberStudentProjectFileImporter(
    onProgress: (StudentProjectFileImportProgress) -> Unit,
    onResult: (StudentProjectFileImportResult) -> Unit,
): StudentProjectFileImportController {
    val host = LocalUIViewController.current
    val currentResult = rememberUpdatedState(onResult)
    val currentProgress = rememberUpdatedState(onProgress)
    val scope = rememberCoroutineScope()
    val pickerGate = remember { StudentProjectFilePickerGate() }
    val isBusy = remember { mutableStateOf(false) }
    val activeReadJob = remember { mutableStateOf<Job?>(null) }

    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val selected = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (selected == null) {
                    pickerGate.release()
                    isBusy.value = false
                    currentResult.value(StudentProjectFileImportResult.Failed(PROJECT_ARCHIVE_ERROR))
                    return
                }
                if (!pickerGate.beginReadAfterSelection()) {
                    isBusy.value = false
                    currentResult.value(StudentProjectFileImportResult.Cancelled)
                    return
                }

                activeReadJob.value = scope.launch {
                    try {
                        val selectedFile = withContext(Dispatchers.Default) {
                            readSelectedStudentProjectFile(
                                url = selected,
                                maximumBytes = StudentProjectFileSelectionRules.MAX_PROJECT_ARCHIVE_BYTES.toLong(),
                                acceptsMetadata = StudentProjectFileSelectionRules::acceptsProjectArchive,
                                requiresSecurityScope = true,
                                ensureActive = { coroutineContext.ensureActive() },
                                onProgress = { progress ->
                                    scope.launch {
                                        if (activeReadJob.value?.isActive == true) currentProgress.value(progress)
                                    }
                                },
                            )
                        }
                        currentResult.value(StudentProjectFileImportResult.Selected(selectedFile.fileName, selectedFile.bytes))
                    } catch (cancelled: CancellationException) {
                        currentResult.value(StudentProjectFileImportResult.Cancelled)
                    } catch (_: Exception) {
                        currentResult.value(StudentProjectFileImportResult.Failed(PROJECT_ARCHIVE_ERROR))
                    } finally {
                        activeReadJob.value = null
                        isBusy.value = false
                        pickerGate.release()
                    }
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                val readJob = activeReadJob.value
                if (readJob != null) {
                    readJob.cancel()
                } else {
                    pickerGate.release()
                    isBusy.value = false
                    currentResult.value(StudentProjectFileImportResult.Cancelled)
                }
            }
        }
    }

    return StudentProjectFileImportController(
        select = {
            if (pickerGate.tryAcquire()) {
                isBusy.value = true
                runCatching {
                    val picker = UIDocumentPickerViewController(
                        forOpeningContentTypes = listOf(requireNotNull(UTType.typeWithIdentifier("public.data"))),
                        asCopy = false,
                    )
                    picker.setAllowsMultipleSelection(false)
                    picker.setDelegate(delegate)
                    host.presentViewController(picker, animated = true, completion = null)
                }.onFailure {
                    pickerGate.release()
                    isBusy.value = false
                    currentResult.value(
                        StudentProjectFileImportResult.Failed(
                            "The system file picker could not be opened. Your projects were not changed.",
                        ),
                    )
                }
            }
        },
        cancel = {
            val readJob = activeReadJob.value
            if (readJob != null) readJob.cancel() else pickerGate.cancelBeforeRead()
        },
    )
}

@Composable
internal actual fun rememberStudentProjectAttachmentPicker(
    onResult: (StudentProjectAttachmentPickResult) -> Unit,
): () -> Unit {
    val host = LocalUIViewController.current
    val currentResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val isBusy = remember { mutableStateOf(false) }
    val activeReadJob = remember { mutableStateOf<Job?>(null) }
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val selected = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (selected == null) {
                    isBusy.value = false
                    currentResult.value(StudentProjectAttachmentPickResult.Failed(ATTACHMENT_ERROR))
                    return
                }

                activeReadJob.value = scope.launch {
                    try {
                        val selectedFile = withContext(Dispatchers.Default) {
                            readSelectedStudentProjectFile(
                                url = selected,
                                maximumBytes = StudentProjectAttachmentRules.MAX_ATTACHMENT_BYTES,
                                acceptsMetadata = StudentProjectFileSelectionRules::acceptsAttachment,
                                requiresSecurityScope = true,
                                ensureActive = { coroutineContext.ensureActive() },
                                onProgress = {},
                            )
                        }
                        currentResult.value(
                            StudentProjectAttachmentPickResult.Selected(
                                StudentProjectAttachmentFile(selectedFile.fileName ?: error("Missing file name"), selectedFile.bytes),
                            ),
                        )
                    } catch (cancelled: CancellationException) {
                        currentResult.value(StudentProjectAttachmentPickResult.Cancelled)
                    } catch (_: Exception) {
                        currentResult.value(StudentProjectAttachmentPickResult.Failed(ATTACHMENT_ERROR))
                    } finally {
                        activeReadJob.value = null
                        isBusy.value = false
                    }
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                activeReadJob.value?.cancel()
                activeReadJob.value = null
                isBusy.value = false
                currentResult.value(StudentProjectAttachmentPickResult.Cancelled)
            }
        }
    }

    return remember(host, delegate) {
        {
            if (!isBusy.value) {
                isBusy.value = true
                runCatching {
                    val picker = UIDocumentPickerViewController(
                        forOpeningContentTypes = listOf(requireNotNull(UTType.typeWithIdentifier("public.data"))),
                        asCopy = false,
                    )
                    picker.setAllowsMultipleSelection(false)
                    picker.setDelegate(delegate)
                    host.presentViewController(picker, animated = true, completion = null)
                }.onFailure {
                    isBusy.value = false
                    currentResult.value(
                        StudentProjectAttachmentPickResult.Failed(
                            "The system file picker could not be opened. Your project was not changed.",
                        ),
                    )
                }
            }
        }
    }
}

private data class SelectedStudentProjectFile(val fileName: String?, val bytes: ByteArray)

private fun readSelectedStudentProjectFile(
    url: NSURL,
    maximumBytes: Long,
    acceptsMetadata: (String?, Long?) -> Boolean,
    requiresSecurityScope: Boolean,
    ensureActive: () -> Unit,
    onProgress: (StudentProjectFileImportProgress) -> Unit,
): SelectedStudentProjectFile {
    require(url.isFileURL())
    val scopedAccessStarted = url.startAccessingSecurityScopedResource()
    try {
        require(!requiresSecurityScope || scopedAccessStarted)
        ensureActive()

        val coordinator = NSFileCoordinator(null)
        var selectedFile: SelectedStudentProjectFile? = null
        var readFailure: Exception? = null
        coordinator.coordinateReadingItemAtURL(
            url = url,
            options = NSFileCoordinatorReadingWithoutChanges,
            error = null,
        ) { coordinatedUrl ->
            try {
                selectedFile = readCoordinatedStudentProjectFile(
                    url = requireNotNull(coordinatedUrl),
                    maximumBytes = maximumBytes,
                    acceptsMetadata = acceptsMetadata,
                    ensureActive = ensureActive,
                    onProgress = onProgress,
                )
            } catch (failure: Exception) {
                readFailure = failure
            }
        }
        readFailure?.let { throw it }
        return requireNotNull(selectedFile) { "The selected file was not available to read" }
    } finally {
        if (scopedAccessStarted) url.stopAccessingSecurityScopedResource()
    }
}

private fun readCoordinatedStudentProjectFile(
    url: NSURL,
    maximumBytes: Long,
    acceptsMetadata: (String?, Long?) -> Boolean,
    ensureActive: () -> Unit,
    onProgress: (StudentProjectFileImportProgress) -> Unit,
): SelectedStudentProjectFile {
    val path = requireNotNull(url.path)
    val fileName = url.lastPathComponent?.takeIf(String::isNotBlank)
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        ?: error("Selected file metadata is unavailable")
    val fileSize = (attributes.get(NSFileSize) as? NSNumber)?.longLongValue
        ?: error("Selected file size is unavailable")
    require(acceptsMetadata(fileName, fileSize))
    require(fileSize in 1L..maximumBytes)
    require(fileSize <= Int.MAX_VALUE.toLong())

    val stream = NSInputStream(url)
    stream.open()
    return try {
        val bytes = readBoundedStudentProjectFileBytes(
            expectedSizeBytes = fileSize,
            maximumSizeBytes = maximumBytes,
            ensureActive = ensureActive,
            readInto = { buffer, maximumLength ->
                val bytesRead = buffer.usePinned { pinned ->
                    stream.read(pinned.addressOf(0).reinterpret(), maximumLength.toULong())
                }
                when {
                    bytesRead < 0L -> error("Selected file stream failed")
                    bytesRead > maximumLength.toLong() -> error("Selected file stream exceeded its read bound")
                    else -> bytesRead.toInt()
                }
            },
            onProgress = { bytesRead ->
                onProgress(StudentProjectFileImportProgress(bytesRead, fileSize))
            },
        )
        SelectedStudentProjectFile(fileName, bytes)
    } finally {
        stream.close()
    }
}

private fun prepareStudentProjectExportBytes(artifact: StudentProjectExportArtifact): ByteArray {
    val binaryContent = artifact.binaryContent
    if (binaryContent == null) {
        require(artifact.content.encodeToByteArray().size <= MAX_TEXT_EXPORT_BYTES)
    }
    val bytes = when {
        binaryContent != null -> binaryContent
        artifact.mimeType == "application/pdf" -> renderStudentProjectPdf(artifact.content)
        artifact.mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
            studentProjectDocxArchive(artifact.content)
        else -> artifact.content.encodeToByteArray()
    }
    val maximumBytes = when {
        artifact.fileName.endsWith(".evproj", ignoreCase = true) ->
            StudentProjectFileSelectionRules.MAX_PROJECT_ARCHIVE_BYTES
        artifact.mimeType == "application/pdf" ||
            artifact.mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
            MAX_RICH_REPORT_EXPORT_BYTES
        else -> MAX_TEXT_EXPORT_BYTES
    }
    require(bytes.isNotEmpty() && bytes.size <= maximumBytes)
    require(artifact.fileName.endsWith(".evproj", ignoreCase = true) == (binaryContent != null))
    if (binaryContent != null) require(artifact.mimeType == PROJECT_ARCHIVE_MIME_TYPE)
    return bytes
}

private fun createTemporaryExportUrl(fileName: String): NSURL {
    val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
    require(safeName.isNotBlank() && safeName != "." && safeName != "..")
    val path = "${NSTemporaryDirectory().trimEnd('/')}/${NSUUID().UUIDString}-$safeName"
    return NSURL.fileURLWithPath(path)
}

private fun writeTemporaryExportFile(url: NSURL, bytes: ByteArray) {
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    if (!data.writeToURL(url, atomically = true)) {
        error("Temporary export file could not be written completely")
    }
}

private fun renderStudentProjectPdf(markdown: String): ByteArray {
    val path = "${NSTemporaryDirectory().trimEnd('/')}/${NSUUID().UUIDString}-project-report.pdf"
    val page = CGRectMake(0.0, 0.0, 595.0, 842.0)
    check(UIGraphicsBeginPDFContextToFile(path, page, null))
    try {
        val font = UIFont.systemFontOfSize(10.5)
        val attributes: Map<Any?, Any?> = mapOf(NSFontAttributeName to font)
        val lineHeight = font.lineHeight.toFloat().coerceAtLeast(14f)
        val lines = buildList {
            add("Evidrilo · Student project snapshot")
            add("Student-entered content; sources and claims are not independently verified.")
            add("")
            markdown.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    add("")
                } else {
                    val display = when {
                        line.startsWith("> ") -> line.drop(2)
                        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") -> "• ${line.drop(2)}"
                        line.startsWith("#") -> line.dropWhile { it == '#' }.trimStart()
                        else -> line
                    }
                    addAll(
                        wrapStudentProjectPdfText(display, 499f) { text ->
                            NSString.create(string = text).sizeWithAttributes(attributes).useContents {
                                width.toFloat()
                            }
                        },
                    )
                }
            }
        }

        var y = 52f
        fun beginPage() {
            UIGraphicsBeginPDFPageWithInfo(page, null)
            y = 52f
        }
        beginPage()
        lines.forEach { line ->
            if (y + lineHeight > 790f) beginPage()
            if (line.isNotEmpty()) {
                NSString.create(string = line).drawAtPoint(
                    CGPointMake(48.0, y.toDouble()),
                    withAttributes = attributes,
                )
            }
            y += if (line.isEmpty()) lineHeight * 0.65f else lineHeight
        }
    } finally {
        UIGraphicsEndPDFContext()
    }

    val url = NSURL.fileURLWithPath(path)
    return try {
        readSelectedStudentProjectFile(
            url = url,
            maximumBytes = MAX_RICH_REPORT_EXPORT_BYTES.toLong(),
            acceptsMetadata = { _, size -> size != null && size in 1L..MAX_RICH_REPORT_EXPORT_BYTES.toLong() },
            requiresSecurityScope = false,
            ensureActive = {},
            onProgress = {},
        ).bytes
    } finally {
        cleanupTemporaryFile(url)
    }
}

private fun cleanupTemporaryFile(url: NSURL?) {
    if (url == null) return
    runCatching {
        val path = url.path ?: return
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }
}
