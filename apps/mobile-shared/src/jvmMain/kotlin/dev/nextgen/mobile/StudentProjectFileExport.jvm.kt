package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact

internal actual val isStudentProjectFileExportAvailable: Boolean = false
internal actual val studentProjectAppVersion: String = "unknown"
internal actual val isStudentProjectFileImportAvailable: Boolean = false
internal actual val isStudentProjectAttachmentPickerAvailable: Boolean = false

@Composable
internal actual fun rememberStudentProjectFileExporter(
    onResult: (StudentProjectFileExportResult) -> Unit,
): (StudentProjectExportArtifact) -> Unit = {
    onResult(StudentProjectFileExportResult.Unavailable("Project file export is only available in the Android app build."))
}

@Composable
internal actual fun rememberStudentProjectFileImporter(
    onProgress: (StudentProjectFileImportProgress) -> Unit,
    onResult: (StudentProjectFileImportResult) -> Unit,
): StudentProjectFileImportController = StudentProjectFileImportController(
    select = { onResult(StudentProjectFileImportResult.Unavailable("Project archive import is not available in this desktop build.")) },
    cancel = {},
)

@Composable
internal actual fun rememberStudentProjectAttachmentPicker(
    onResult: (StudentProjectAttachmentPickResult) -> Unit,
): () -> Unit = {
    onResult(StudentProjectAttachmentPickResult.Unavailable("Project file attachments are available only in the Android app build."))
}
