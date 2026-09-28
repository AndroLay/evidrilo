package dev.nextgen.mobile

internal actual val studentProjectDocxPreviewSupported: Boolean = false

internal actual fun extractStudentProjectDocxText(
    bytes: ByteArray,
    ensureActive: () -> Unit,
): StudentProjectDocumentTextResult {
    ensureActive()
    return StudentProjectDocumentTextResult.UnsupportedFormat
}
