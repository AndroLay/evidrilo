package dev.nextgen.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact

internal data class StudentProjectExportReview(
    val artifact: StudentProjectExportArtifact,
    val fileName: String,
    val formatLabel: String,
    val projectTitle: String,
    val projectRevision: Int,
    val contentPreview: String?,
    val packageSummary: List<String>,
    val privacyNotice: String,
    val cancelNotice: String,
    val previewDescription: String,
)

internal fun studentProjectExportReview(
    project: StudentProjectDraft,
    artifact: StudentProjectExportArtifact,
): StudentProjectExportReview {
    val isArchive = artifact.binaryContent != null || artifact.fileName.endsWith(".evproj", ignoreCase = true)
    val formatLabel = when {
        isArchive -> "Project archive (.evproj)"
        artifact.mimeType == "application/pdf" -> "PDF report"
        artifact.mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOCX report"
        artifact.mimeType == "text/markdown" -> "Markdown report"
        artifact.mimeType == "text/csv" -> "CSV table"
        else -> "Project file"
    }
    val packageSummary = if (isArchive) buildList {
        add("Project: ${project.title.ifBlank { "Untitled project" }}")
        add("Saved revision: ${project.revision}")
        add("Sources: ${project.sources.size}")
        add("Evidence notes: ${project.evidenceItems.size}")
        add("Findings: ${project.findings.size}")
        add("Claims: ${StudentProjectDraftRules.effectiveClaims(project).size}")
        add("Revision checkpoints: ${project.revisionSnapshots.size}")
        add("Attachments: ${project.attachments.size}")
        project.attachments.forEach { attachment ->
            add("Attachment file: ${attachment.fileName} · ${attachment.sizeBytes} bytes")
        }
    } else {
        emptyList()
    }
    val privacyNotice = buildString {
        append("Project exports may include personal or sensitive details you entered. ")
        append("Evidrilo does not automatically detect or redact them. Review the preview; ")
        append("cancel and edit the project if anything should be removed. ")
        append("AI conversations, account credentials, and credit records are excluded.")
    }
    val previewDescription = when {
        isArchive -> "Package summary. The archive preserves the saved project, selected revision history, and listed attachment files."
        artifact.mimeType == "application/pdf" || artifact.mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
            "Text preview for the ${formatLabel.lowercase()}; final page layout may differ."
        else -> "Review the text that will be saved in this file."
    }

    return StudentProjectExportReview(
        artifact = artifact,
        fileName = artifact.fileName,
        formatLabel = formatLabel,
        projectTitle = project.title.ifBlank { "Untitled project" },
        projectRevision = project.revision,
        contentPreview = artifact.content.takeUnless { isArchive },
        packageSummary = packageSummary,
        privacyNotice = privacyNotice,
        cancelNotice = "Canceling stops file creation only; it does not undo a local save or checkpoint already made to prepare this snapshot.",
        previewDescription = previewDescription,
    )
}

internal sealed interface StudentProjectExportReviewState {
    data object Hidden : StudentProjectExportReviewState
    data class Reviewing(val review: StudentProjectExportReview) : StudentProjectExportReviewState
}

internal sealed interface StudentProjectExportReviewEvent {
    data class Request(val review: StudentProjectExportReview) : StudentProjectExportReviewEvent
    data object Confirm : StudentProjectExportReviewEvent
    data object Cancel : StudentProjectExportReviewEvent
}

internal data class StudentProjectExportReviewTransition(
    val state: StudentProjectExportReviewState,
    val confirmedArtifact: StudentProjectExportArtifact? = null,
)

internal fun reduceStudentProjectExportReview(
    state: StudentProjectExportReviewState,
    event: StudentProjectExportReviewEvent,
): StudentProjectExportReviewTransition = when (event) {
    is StudentProjectExportReviewEvent.Request ->
        StudentProjectExportReviewTransition(StudentProjectExportReviewState.Reviewing(event.review))
    StudentProjectExportReviewEvent.Cancel ->
        StudentProjectExportReviewTransition(StudentProjectExportReviewState.Hidden)
    StudentProjectExportReviewEvent.Confirm -> when (state) {
        StudentProjectExportReviewState.Hidden -> StudentProjectExportReviewTransition(state)
        is StudentProjectExportReviewState.Reviewing ->
            StudentProjectExportReviewTransition(StudentProjectExportReviewState.Hidden, state.review.artifact)
    }
}

@Composable
internal fun StudentProjectExportReviewDialog(
    review: StudentProjectExportReview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Review ${review.formatLabel.lowercase()}") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(review.fileName, style = MaterialTheme.typography.titleSmall)
                Text("${review.projectTitle} · revision ${review.projectRevision}", style = MaterialTheme.typography.titleSmall)
                Text(review.privacyNotice, style = MaterialTheme.typography.bodySmall)
                Text(review.cancelNotice, style = MaterialTheme.typography.bodySmall)
                Text(review.previewDescription, style = MaterialTheme.typography.bodySmall)
                if (review.packageSummary.isNotEmpty()) {
                    review.packageSummary.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Archive checks detect file corruption; they do not verify the truth or quality of project content.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    SelectionContainer {
                        Text(review.contentPreview.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Choose save location") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
