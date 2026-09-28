package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.projectcatalog.StudentProjectExportArtifact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StudentProjectExportReviewTest {
    @Test
    fun `text export review presents the exact content and a clear privacy warning`() {
        val project = project()
        val artifact = StudentProjectExportArtifact(
            fileName = "research-report.pdf",
            mimeType = "application/pdf",
            content = "# Student report\nStudent-entered notes remain unverified.",
        )

        val review = studentProjectExportReview(project, artifact)

        assertEquals("research-report.pdf", review.fileName)
        assertEquals("PDF report", review.formatLabel)
        assertEquals(artifact.content, review.contentPreview)
        assertEquals(emptyList(), review.packageSummary)
        assertTrue(review.privacyNotice.contains("does not automatically detect or redact"))
        assertTrue(review.cancelNotice.contains("does not undo a local save"))
    }

    @Test
    fun `archive review shows project and attachment contents without exposing archive bytes`() {
        val project = project().copy(
            sources = listOf(StudentProjectSourceRecord(id = "source-a", title = "Entered source")),
            attachments = listOf(
                StudentProjectAttachmentRef(
                    id = "attachment-a",
                    fileName = "interview-notes.docx",
                    mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    sizeBytes = 42,
                    sha256 = "a".repeat(64),
                ),
            ),
        )
        val archiveBytes = byteArrayOf(0, 1, 2, 3, 4)
        val artifact = StudentProjectExportArtifact(
            fileName = "student-project.evproj",
            mimeType = "application/vnd.evidrilo.project+zip",
            content = "",
            binaryContent = archiveBytes,
        )

        val review = studentProjectExportReview(project, artifact)

        assertEquals("Project archive (.evproj)", review.formatLabel)
        assertNull(review.contentPreview)
        assertEquals(
            listOf(
                "Project: My research project",
                "Saved revision: 3",
                "Sources: 1",
                "Evidence notes: 0",
                "Findings: 0",
                "Claims: 0",
                "Revision checkpoints: 0",
                "Attachments: 1",
                "Attachment file: interview-notes.docx · 42 bytes",
            ),
            review.packageSummary,
        )
        assertTrue(review.privacyNotice.contains("AI conversations, account credentials, and credit records are excluded"))
        assertTrue(review.artifact.binaryContent.contentEquals(archiveBytes))
    }

    @Test
    fun `canceling review closes it without yielding an artifact to the file exporter`() {
        val review = studentProjectExportReview(
            project(),
            StudentProjectExportArtifact("report.md", "text/markdown", "# Report"),
        )
        val reviewing = reduceStudentProjectExportReview(
            StudentProjectExportReviewState.Hidden,
            StudentProjectExportReviewEvent.Request(review),
        ).state

        val canceled = reduceStudentProjectExportReview(reviewing, StudentProjectExportReviewEvent.Cancel)

        assertEquals(StudentProjectExportReviewState.Hidden, canceled.state)
        assertNull(canceled.confirmedArtifact)
    }

    @Test
    fun `confirming review yields the exact reviewed artifact once`() {
        val artifact = StudentProjectExportArtifact("report.md", "text/markdown", "# Report")
        val review = studentProjectExportReview(project(), artifact)
        val reviewing = reduceStudentProjectExportReview(
            StudentProjectExportReviewState.Hidden,
            StudentProjectExportReviewEvent.Request(review),
        ).state

        val confirmed = reduceStudentProjectExportReview(reviewing, StudentProjectExportReviewEvent.Confirm)
        val duplicateConfirm = reduceStudentProjectExportReview(confirmed.state, StudentProjectExportReviewEvent.Confirm)

        assertEquals(StudentProjectExportReviewState.Hidden, confirmed.state)
        assertSame(artifact, confirmed.confirmedArtifact)
        assertNull(duplicateConfirm.confirmedArtifact)
    }

    @Test
    fun `confirming without an open review cannot start a file export`() {
        val transition = reduceStudentProjectExportReview(
            StudentProjectExportReviewState.Hidden,
            StudentProjectExportReviewEvent.Confirm,
        )

        assertEquals(StudentProjectExportReviewState.Hidden, transition.state)
        assertNull(transition.confirmedArtifact)
    }

    private fun project() = StudentProjectDraft(
        id = "project-a",
        templateSnapshot = null,
        title = "My research project",
        fieldValues = emptyMap(),
        revision = 3,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2,
    )
}
