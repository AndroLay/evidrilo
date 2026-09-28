package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentStore
import kotlinx.coroutines.CancellationException

sealed interface StudentProjectAttachmentPreviewRead {
    data class Loaded(val bytes: ByteArray) : StudentProjectAttachmentPreviewRead
    data class Failed(val code: String) : StudentProjectAttachmentPreviewRead
}

/** Reads one already-attached file for an on-device preview, checking its saved size and digest. */
fun readStudentProjectAttachmentForPreview(
    store: StudentProjectAttachmentStore,
    projectId: String,
    reference: StudentProjectAttachmentRef,
    ensureActive: () -> Unit = {},
): StudentProjectAttachmentPreviewRead {
    if (StudentProjectAttachmentRules.validate(reference).isNotEmpty()) {
        return StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_REFERENCE_INVALID")
    }

    return when (val opened = store.openRead(projectId, reference.id)) {
        StudentProjectAttachmentReadResult.Missing ->
            StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_MISSING")
        StudentProjectAttachmentReadResult.Unavailable ->
            StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_STORE_UNAVAILABLE")
        is StudentProjectAttachmentReadResult.Failed ->
            StudentProjectAttachmentPreviewRead.Failed(opened.code)
        is StudentProjectAttachmentReadResult.Opened -> {
            val handle = opened.handle
            val result = try {
                readVerifiedBytes(handle, reference, ensureActive)
            } catch (cancelled: CancellationException) {
                runCatching(handle::close)
                throw cancelled
            } catch (_: Exception) {
                StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_READ_FAILED")
            }
            val closed = runCatching(handle::close).isSuccess
            if (!closed && result is StudentProjectAttachmentPreviewRead.Loaded) {
                StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_CLOSE_FAILED")
            } else {
                result
            }
        }
    }
}

private fun readVerifiedBytes(
    handle: dev.nextgen.mobile.storage.StudentProjectAttachmentReadHandle,
    reference: StudentProjectAttachmentRef,
    ensureActive: () -> Unit,
): StudentProjectAttachmentPreviewRead {
    if (handle.sizeBytes != reference.sizeBytes) {
        return StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_SIZE_MISMATCH")
    }

    val bytes = ByteArray(reference.sizeBytes.toInt())
    val buffer = ByteArray(64 * 1024)
    val checksum = StudentProjectSha256Accumulator()
    var offset = 0
    while (offset < bytes.size) {
        ensureActive()
        val requested = minOf(buffer.size, bytes.size - offset)
        val count = handle.read(buffer, 0, requested)
        if (count <= 0 || count > requested) {
            return StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_SIZE_MISMATCH")
        }
        buffer.copyInto(bytes, offset, 0, count)
        checksum.update(buffer, 0, count)
        offset += count
    }

    val extra = ByteArray(1)
    ensureActive()
    if (handle.read(extra, 0, 1) != -1) {
        return StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_SIZE_MISMATCH")
    }
    if (checksum.digest().toLowerHex() != reference.sha256) {
        return StudentProjectAttachmentPreviewRead.Failed("PROJECT_ATTACHMENT_CHECKSUM_MISMATCH")
    }
    return StudentProjectAttachmentPreviewRead.Loaded(bytes)
}
