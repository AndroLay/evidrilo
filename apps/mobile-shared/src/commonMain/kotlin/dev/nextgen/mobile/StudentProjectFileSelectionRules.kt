package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules

/** Shared, fail-closed metadata bounds for student-selected project files. */
internal object StudentProjectFileSelectionRules {
    const val MAX_PROJECT_ARCHIVE_BYTES = 50 * 1024 * 1024

    private val supportedAttachmentExtensions = setOf(
        "pdf",
        "docx",
        "csv",
        "txt",
        "md",
        "markdown",
        "png",
        "jpg",
        "jpeg",
    )

    fun acceptsProjectArchive(fileName: String?, sizeBytes: Long?): Boolean =
        (fileName == null || fileName.endsWith(".evproj", ignoreCase = true)) &&
            (sizeBytes == null || sizeBytes in 1L..MAX_PROJECT_ARCHIVE_BYTES.toLong())

    fun acceptsAttachment(fileName: String?, sizeBytes: Long?): Boolean {
        if (fileName.isNullOrBlank()) return false
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension in supportedAttachmentExtensions &&
            (sizeBytes == null || sizeBytes in 1L..StudentProjectAttachmentRules.MAX_ATTACHMENT_BYTES)
    }
}

/** Main-thread gate shared by a picker launch and the file read it starts. */
internal class StudentProjectFilePickerGate {
    private var isAcquired = false
    private var cancelBeforeReadRequested = false

    fun tryAcquire(): Boolean {
        if (isAcquired) return false
        isAcquired = true
        cancelBeforeReadRequested = false
        return true
    }

    /** Consumes the picker result, but keeps the gate held while its file is read. */
    fun beginReadAfterSelection(): Boolean {
        if (!isAcquired) return false
        if (!cancelBeforeReadRequested) return true
        release()
        return false
    }

    /** Latches cancellation while the native picker is open and no read job exists yet. */
    fun cancelBeforeRead() {
        if (isAcquired) cancelBeforeReadRequested = true
    }

    fun release() {
        isAcquired = false
        cancelBeforeReadRequested = false
    }
}

/** Keeps a generated export file owned until the platform picker accepts responsibility. */
internal class StudentProjectTemporaryFileHandoff<T : Any>(
    private val cleanupResource: (T) -> Unit,
) {
    private var ownedResource: T? = null

    fun takeOwnership(resource: T) {
        check(ownedResource == null)
        ownedResource = resource
    }

    fun handoff(): T {
        val resource = requireNotNull(ownedResource)
        ownedResource = null
        return resource
    }

    fun cleanup() {
        val resource = ownedResource ?: return
        try {
            cleanupResource(resource)
        } finally {
            ownedResource = null
        }
    }
}

/** Reads a file from a synchronous platform stream without trusting its reported length. */
internal fun readBoundedStudentProjectFileBytes(
    expectedSizeBytes: Long,
    maximumSizeBytes: Long,
    ensureActive: () -> Unit,
    readInto: (ByteArray, Int) -> Int,
    onProgress: (Long) -> Unit,
): ByteArray {
    require(maximumSizeBytes in 1L..Int.MAX_VALUE.toLong())
    require(expectedSizeBytes in 1L..maximumSizeBytes)

    val result = ByteArray(expectedSizeBytes.toInt())
    val buffer = ByteArray(minOf(64 * 1024, result.size))
    var totalBytesRead = 0
    onProgress(0L)

    while (totalBytesRead < result.size) {
        ensureActive()
        val requestedLength = minOf(buffer.size, result.size - totalBytesRead)
        val bytesRead = readInto(buffer, requestedLength)
        require(bytesRead in 0..requestedLength)
        if (bytesRead == 0) break

        buffer.copyInto(result, totalBytesRead, 0, bytesRead)
        totalBytesRead += bytesRead
        onProgress(totalBytesRead.toLong())
    }

    ensureActive()
    val extraBytesRead = readInto(buffer, 1)
    require(extraBytesRead in 0..1)
    require(totalBytesRead == result.size && extraBytesRead == 0)
    return result
}
