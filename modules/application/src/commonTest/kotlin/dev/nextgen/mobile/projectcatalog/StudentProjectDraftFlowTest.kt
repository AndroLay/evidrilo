package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectStarterTemplateCatalog
import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import dev.nextgen.mobile.domain.project.ProjectAiFieldSuggestion
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldProposal
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectDeadlineChange
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectClaimReviewStatus
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelationType
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.domain.project.StudentProjectRevisionActor
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.SourceSelectionStatus
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportSession
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadHandle
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentStore
import dev.nextgen.mobile.storage.StudentProjectAttachmentWriteHandle
import dev.nextgen.mobile.storage.StudentProjectAttachmentWriterResult
import dev.nextgen.mobile.storage.StudentProjectDraftStore
import dev.nextgen.mobile.storage.STUDENT_PROJECT_ATTACHMENT_MAX_IO_CHUNK_BYTES
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.storage.UnavailableStudentProjectAttachmentStore
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudentProjectDraftFlowTest {
    @Test
    fun `start saves a valid published template snapshot for offline resume`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val template = publishedTemplate()

        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(template, "My inquiry"),
        ).value
        val resumed = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.resume(started.id),
        ).value

        assertEquals(template, resumed.templateSnapshot)
        assertEquals(1, resumed.revision)
        assertEquals("My inquiry", resumed.title)
    }

    @Test
    fun `all five built in starters create local editable project drafts`() {
        ProjectStarterTemplateCatalog.templates.forEachIndexed { index, template ->
            val store = InMemoryDraftStore()
            val flow = StudentProjectDraftFlow(store, { "starter-project-${index + 1}" }, { 100 })

            val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
                flow.start(template, template.title),
            ).value

            assertEquals(template, started.templateSnapshot)
            assertEquals(emptyMap(), started.fieldValues)
            assertEquals(emptyList(), started.sources)
            assertEquals(emptyList(), started.evidenceItems)
            assertEquals(emptyList(), started.findings)
            assertEquals(emptyList(), started.claims)
            assertEquals(listOf(started), store.drafts)
        }
    }

    @Test
    fun `listing resolves interrupted file removal against current and revision attachment references`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        val flow = StudentProjectDraftFlow(
            store = projectStore,
            idGenerator = { "project-with-file-history" },
            clock = { 100 },
            attachmentStore = attachmentStore,
        )
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Project with attachment history"),
        ).value
        val currentAttachment = StudentProjectAttachmentRef(
            id = "current-attachment",
            fileName = "current.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            sha256 = "0".repeat(64),
        )
        val historicalAttachment = StudentProjectAttachmentRef(
            id = "historical-attachment",
            fileName = "historical.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            sha256 = "1".repeat(64),
        )
        projectStore.drafts[0] = project.copy(
            attachments = listOf(currentAttachment),
            revisionSnapshots = project.revisionSnapshots.map {
                it.copy(attachments = listOf(historicalAttachment))
            },
        )

        assertIs<StudentProjectDraftFlowResult.Value<List<StudentProjectDraft>>>(flow.list())

        assertEquals(
            mapOf(project.id to setOf(currentAttachment.id, historicalAttachment.id)),
            attachmentStore.recoveredAttachmentReferences,
        )
    }

    @Test
    fun `start refuses unreviewed draft templates`() {
        val store = InMemoryDraftStore()
        val result = flow(store).start(
            publishedTemplate().copy(
                publication = ProjectTemplatePublication.DRAFT,
                examples = listOf(ProjectTemplateExample("example-1", "Candidate only.", false)),
            ),
            "My inquiry",
        )

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("TEMPLATE_NOT_READY"),
            result,
        )
        assertEquals(emptyList(), store.drafts)
    }

    @Test
    fun `free tier refuses a sixth active project without changing stored work`() {
        val store = InMemoryDraftStore()
        val ids = ArrayDeque((1..6).map { "project-$it" })
        val flow = StudentProjectDraftFlow(store, { ids.removeFirst() }, { 100 })

        repeat(5) { index ->
            assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
                flow.start(publishedTemplate(), "Project ${index + 1}"),
            )
        }

        val sixth = flow.start(publishedTemplate(), "Project 6")

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED"), sixth)
        assertEquals(5, store.drafts.size)
        assertEquals(5, store.saveCount)
    }

    @Test
    fun `manual project starts blank without a template or invented research content`() {
        val store = InMemoryDraftStore()

        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow(store).startManual("My first research project"),
        ).value

        assertEquals(null, project.templateSnapshot)
        assertEquals(emptyMap(), project.fieldValues)
        assertEquals(emptyList(), project.sources)
        assertEquals(emptyList(), project.themes)
        assertEquals(emptySet(), project.claimEvidenceSourceIds)
        assertEquals(StudentProjectStatus.DRAFT, project.status)
        assertEquals("My first research project", project.title)
    }

    @Test
    fun `manual project saves source extraction theme and claim links without evaluating them`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My synthesis"),
        ).value
        val source = StudentProjectSourceRecord(
            id = "source-1",
            title = "Student-entered source",
            citationText = "Author (2024). Title.",
            selectionStatus = SourceSelectionStatus.SELECTED,
            reportedFindings = "A reported result, not independently checked.",
        )
        val theme = StudentProjectSynthesisTheme(
            id = "theme-1",
            title = "Reported difference",
            synthesis = "Student-written comparison.",
            sourceIds = setOf(source.id),
        )

        val saved = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                project.id,
                project.title,
                mapOf(ManualLiteratureSynthesisFields.RESEARCH_QUESTION to "What differs?"),
                sources = listOf(source),
                themes = listOf(theme),
                claimEvidenceSourceIds = setOf(source.id),
            ),
        ).value

        assertEquals(listOf(source), saved.sources)
        assertEquals(listOf(theme), saved.themes)
        assertEquals(setOf(source.id), saved.claimEvidenceSourceIds)
        assertEquals(saved, assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(flow.resume(project.id)).value)
        assertTrue(StudentProjectDraftRules.structureReport(saved).hasOpenStructureIssues)
    }

    @Test
    fun `manual project rejects dangling evidence links and preserves saved work`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My synthesis"),
        ).value
        val source = StudentProjectSourceRecord(id = "source-1", title = "Source", citationText = "Citation")
        val saved = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(project.id, project.title, emptyMap(), sources = listOf(source)),
        ).value

        val rejected = flow.update(project.id, project.title, emptyMap(), claimEvidenceSourceIds = setOf("missing"))

        assertEquals(StudentProjectDraftFlowResult.Rejected("INVALID_CLAIM_EVIDENCE_LINK"), rejected)
        assertEquals(saved, store.drafts.single())
    }

    @Test
    fun `manual project persists student evidence findings and typed relationships`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My synthesis"),
        ).value
        val source = StudentProjectSourceRecord(id = "source-1", title = "Study", citationText = "Citation")
        val evidence = StudentProjectEvidenceItem(
            id = "evidence-1", sourceId = source.id, excerpt = "Observed association", studentChecked = true,
        )
        val finding = StudentProjectFindingRecord(id = "finding-1", statement = "A bounded synthesis")
        val relation = StudentProjectEvidenceRelation(
            StudentProjectEvidenceTargetType.FINDING,
            finding.id,
            evidence.id,
            StudentProjectEvidenceRelationType.PROVIDES_CONTEXT,
        )

        val saved = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = project.id,
                title = project.title,
                fieldValues = emptyMap(),
                sources = listOf(source),
                evidenceItems = listOf(evidence),
                findings = listOf(finding),
                evidenceRelations = listOf(relation),
            ),
        ).value

        assertEquals(listOf(evidence), saved.evidenceItems)
        assertEquals(listOf(finding), saved.findings)
        assertEquals(listOf(relation), saved.evidenceRelations)
        assertEquals(saved, assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(flow.resume(project.id)).value)
    }

    @Test
    fun `source and evidence removal checkpoint atomically and failed removal preserves the saved project`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My synthesis"),
        ).value
        val sourceA = StudentProjectSourceRecord(id = "source-a", title = "Study A")
        val sourceB = StudentProjectSourceRecord(id = "source-b", title = "Study B")
        val evidenceA = StudentProjectEvidenceItem(id = "evidence-a", sourceId = sourceA.id, excerpt = "A result")
        val evidenceB = StudentProjectEvidenceItem(id = "evidence-b", sourceId = sourceB.id, excerpt = "B result")
        val claimA = StudentProjectClaimRecord(
            id = "claim-a",
            statement = "Claim based on A",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val claimB = StudentProjectClaimRecord(
            id = "claim-b",
            statement = "Claim based on B",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val populated = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = started.id,
                title = started.title,
                fieldValues = emptyMap(),
                sources = listOf(sourceA, sourceB),
                evidenceItems = listOf(evidenceA, evidenceB),
                claims = listOf(claimA, claimB),
                evidenceRelations = listOf(
                    StudentProjectEvidenceRelation(
                        StudentProjectEvidenceTargetType.CLAIM,
                        claimA.id,
                        evidenceA.id,
                        StudentProjectEvidenceRelationType.SUPPORTS,
                    ),
                    StudentProjectEvidenceRelation(
                        StudentProjectEvidenceTargetType.CLAIM,
                        claimB.id,
                        evidenceB.id,
                        StudentProjectEvidenceRelationType.SUPPORTS,
                    ),
                ),
            ),
        ).value

        val withoutSourceA = StudentProjectDraftRules.removeSource(populated, sourceA.id)
        val afterSourceRemoval = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = withoutSourceA.id,
                title = withoutSourceA.title,
                fieldValues = withoutSourceA.fieldValues,
                sources = withoutSourceA.sources,
                themes = withoutSourceA.themes,
                claimEvidenceSourceIds = withoutSourceA.claimEvidenceSourceIds,
                evidenceItems = withoutSourceA.evidenceItems,
                findings = withoutSourceA.findings,
                evidenceRelations = withoutSourceA.evidenceRelations,
                claims = withoutSourceA.claims,
            ),
        ).value

        assertEquals(populated.revision + 1, afterSourceRemoval.revision)
        assertEquals(
            listOf(sourceA, sourceB),
            afterSourceRemoval.revisionSnapshots[afterSourceRemoval.revisionSnapshots.lastIndex - 1].sources,
        )
        assertEquals(listOf(sourceB), afterSourceRemoval.sources)
        assertEquals(listOf(evidenceB), afterSourceRemoval.evidenceItems)
        assertEquals(StudentProjectClaimReviewStatus.NEEDS_REVISION, afterSourceRemoval.claims.first().reviewStatus)
        assertEquals(StudentProjectClaimReviewStatus.READY_FOR_REVIEW, afterSourceRemoval.claims.last().reviewStatus)

        val withoutEvidenceB = StudentProjectDraftRules.removeEvidenceNote(afterSourceRemoval, evidenceB.id)
        store.saveResult = LocalStorageWriteResult.FAILED
        assertEquals(
            StudentProjectDraftFlowResult.StorageFailed,
            flow.update(
                projectId = withoutEvidenceB.id,
                title = withoutEvidenceB.title,
                fieldValues = withoutEvidenceB.fieldValues,
                sources = withoutEvidenceB.sources,
                themes = withoutEvidenceB.themes,
                claimEvidenceSourceIds = withoutEvidenceB.claimEvidenceSourceIds,
                evidenceItems = withoutEvidenceB.evidenceItems,
                findings = withoutEvidenceB.findings,
                evidenceRelations = withoutEvidenceB.evidenceRelations,
                claims = withoutEvidenceB.claims,
            ),
        )
        assertEquals(afterSourceRemoval, store.drafts.single())

        store.saveResult = LocalStorageWriteResult.SAVED
        val afterEvidenceRemoval = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = withoutEvidenceB.id,
                title = withoutEvidenceB.title,
                fieldValues = withoutEvidenceB.fieldValues,
                sources = withoutEvidenceB.sources,
                themes = withoutEvidenceB.themes,
                claimEvidenceSourceIds = withoutEvidenceB.claimEvidenceSourceIds,
                evidenceItems = withoutEvidenceB.evidenceItems,
                findings = withoutEvidenceB.findings,
                evidenceRelations = withoutEvidenceB.evidenceRelations,
                claims = withoutEvidenceB.claims,
            ),
        ).value

        assertEquals(afterSourceRemoval.revision + 1, afterEvidenceRemoval.revision)
        assertEquals(
            listOf(evidenceB),
            afterEvidenceRemoval.revisionSnapshots[afterEvidenceRemoval.revisionSnapshots.lastIndex - 1].evidenceItems,
        )
        assertTrue(afterEvidenceRemoval.evidenceItems.isEmpty())
        assertEquals(StudentProjectClaimReviewStatus.NEEDS_REVISION, afterEvidenceRemoval.claims.last().reviewStatus)
        assertEquals(afterEvidenceRemoval, store.drafts.single())
    }

    @Test
    fun `claim list autosaves through revisions and restores with its evidence links`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My synthesis"),
        ).value
        val source = StudentProjectSourceRecord(id = "source-1", title = "Study", citationText = "Citation")
        val evidence = StudentProjectEvidenceItem(id = "evidence-1", sourceId = source.id, excerpt = "Student note")
        val claimOne = StudentProjectClaimRecord(
            id = "claim-one",
            statement = "A scoped statement.",
            scopeNote = "Within this sample.",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val claimRelation = StudentProjectEvidenceRelation(
            StudentProjectEvidenceTargetType.CLAIM,
            claimOne.id,
            evidence.id,
            StudentProjectEvidenceRelationType.SUPPORTS,
            "Student-entered rationale.",
        )
        val finding = StudentProjectFindingRecord("finding-one", statement = "A source pattern")
        val limitation = StudentProjectLimitationActionRecord(
            id = "limit-one",
            boundary = "Only one context is represented.",
            reason = "The other source does not report the same group.",
            nextAction = "Check a source from another context.",
            affectedFindingIds = setOf(finding.id),
            affectedClaimIds = setOf(claimOne.id),
        )

        val firstSave = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                project.id,
                project.title,
                emptyMap(),
                sources = listOf(source),
                evidenceItems = listOf(evidence),
                findings = listOf(finding),
                evidenceRelations = listOf(claimRelation),
                claims = listOf(claimOne),
                limitationActions = listOf(limitation),
            ),
        ).value
        val claimTwo = StudentProjectClaimRecord("claim-two", "A separate statement.", "A separate scope.")
        val secondSave = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                project.id,
                project.title,
                emptyMap(),
                sources = listOf(source),
                evidenceItems = listOf(evidence),
                evidenceRelations = listOf(claimRelation) + claimRelation.copy(targetId = claimTwo.id),
                claims = listOf(claimOne, claimTwo),
                limitationActions = listOf(limitation),
            ),
        ).value

        val restored = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.restoreRevision(project.id, firstSave.revision),
        ).value

        assertEquals(1, firstSave.claims.size)
        assertEquals(listOf(claimOne), firstSave.claims)
        assertEquals(listOf(claimOne, claimTwo), secondSave.claims)
        assertEquals(listOf(claimOne), restored.claims)
        assertEquals(listOf(claimRelation), restored.evidenceRelations)
        assertEquals(listOf(finding), restored.findings)
        assertEquals(listOf(limitation), firstSave.limitationActions)
        assertEquals(listOf(limitation), restored.limitationActions)
        assertEquals(listOf(claimOne), restored.revisionSnapshots.last().claims)
        assertEquals(listOf(limitation), restored.revisionSnapshots.last().limitationActions)
    }

    @Test
    fun `manual project rejects evidence links to missing records without replacing saved work`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My synthesis"),
        ).value

        val rejected = flow.update(
            projectId = project.id,
            title = project.title,
            fieldValues = emptyMap(),
            evidenceRelations = listOf(StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.FINDING,
                "missing-finding",
                "missing-evidence",
                StudentProjectEvidenceRelationType.SUPPORTS,
            )),
        )

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_EVIDENCE_RELATION_INVALID"), rejected)
        assertEquals(project, store.drafts.single())
    }

    @Test
    fun `verified pro entitlement allows up to fifty local active projects`() {
        val store = InMemoryDraftStore()
        val ids = ArrayDeque((1..51).map { "pro-project-$it" })
        val flow = StudentProjectDraftFlow(store, { ids.removeFirst() }, { 100 }, hasVerifiedProEntitlement = { true })

        repeat(50) { index ->
            assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
                flow.startManual("Project ${index + 1}"),
            )
        }

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED"),
            flow.startManual("Project 51"),
        )
        assertEquals(50, store.drafts.size)
    }

    @Test
    fun `unknown entitlement uses free cap without removing existing work`() {
        val store = InMemoryDraftStore()
        val ids = ArrayDeque((1..6).map { "unknown-project-$it" })
        val flow = StudentProjectDraftFlow(store, { ids.removeFirst() }, { 100 })

        repeat(5) { index -> assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(flow.startManual("Project ${index + 1}")) }
        val existing = store.drafts.first()

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED"),
            flow.startManual("Project 6"),
        )
        assertEquals(5, store.drafts.size)
        assertEquals(existing, assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(flow.resume(existing.id)).value)
    }

    @Test
    fun `completed and archived projects consume total quota while existing work can reactivate`() {
        val store = InMemoryDraftStore()
        val ids = ArrayDeque((1..6).map { "archive-project-$it" })
        val flow = StudentProjectDraftFlow(store, { ids.removeFirst() }, { 100 })
        val projects = (1..5).map { index ->
            assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
                flow.startManual("Project $index"),
            ).value
        }
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.archive(projects.first().id))
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.markCompleted(projects.last().id))
        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED"), flow.startManual("Project 6"))
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.reactivate(projects.first().id))
        assertEquals(StudentProjectStatus.ACTIVE, store.drafts.single { it.id == projects.first().id }.status)
        assertEquals(StudentProjectStatus.COMPLETED, store.drafts.single { it.id == projects.last().id }.status)
        assertEquals(5, store.drafts.size)
    }

    @Test
    fun `legacy trash occupies a total slot and restoration does not add a project`() {
        val store = InMemoryDraftStore()
        val ids = ArrayDeque((1..6).map { "trash-project-$it" })
        val flow = StudentProjectDraftFlow(store, { ids.removeFirst() }, { 100 })
        val projects = (1..5).map { index ->
            assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
                flow.startManual("Project $index"),
            ).value
        }
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.moveToTrash(projects.first().id))
        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED"), flow.startManual("Project 6"))
        assertEquals(StudentProjectStatus.TRASHED, store.drafts.single { it.id == projects.first().id }.status)
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.restoreFromTrash(projects.first().id))
        assertEquals(StudentProjectStatus.DRAFT, store.drafts.single { it.id == projects.first().id }.status)
        assertEquals(5, store.drafts.size)
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.permanentlyDelete(projects.first().id))
        assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(flow.startManual("Project 6"))
        assertEquals(5, store.drafts.size)
    }

    @Test
    fun `trash expires after thirty days and purge failure preserves every project`() {
        val store = InMemoryDraftStore()
        val attachments = InMemoryAttachmentStore()
        var now = 100L
        val flow = StudentProjectDraftFlow(store, { "expiring-project" }, { now }, attachmentStore = attachments)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Expiring project"),
        ).value
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.delete(project.id))
        attachments.blobs += project.id
        now = StudentProjectDraftRules.TRASH_RETENTION_MILLIS + 100
        store.saveResult = LocalStorageWriteResult.FAILED

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, flow.list())
        assertEquals(1, store.drafts.size)
        assertEquals(StudentProjectStatus.TRASHED, store.drafts.single().status)
        assertEquals(setOf(project.id), attachments.blobs)
        assertTrue(attachments.pending.isEmpty())
    }

    @Test
    fun `expired trash is purged transactionally during project listing`() {
        val store = InMemoryDraftStore()
        val attachments = InMemoryAttachmentStore()
        var now = 100L
        val flow = StudentProjectDraftFlow(store, { "expiring-project" }, { now }, attachmentStore = attachments)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Expiring project"),
        ).value
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.delete(project.id))
        attachments.blobs += project.id
        now = StudentProjectDraftRules.TRASH_RETENTION_MILLIS + 100
        attachments.failNextCleanup = true

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, flow.list())
        assertEquals(0, store.drafts.size)
        assertEquals(setOf(project.id), attachments.blobs)
        assertEquals("COMMITTED", attachments.pending[project.id])
        assertIs<StudentProjectAttachmentReadResult.Failed>(attachments.openRead(project.id, "attachment-one"))

        assertEquals(emptyList(), assertIs<StudentProjectDraftFlowResult.Value<List<StudentProjectDraft>>>(flow.list()).value)
        assertTrue(attachments.blobs.isEmpty())
        assertTrue(attachments.pending.isEmpty())
    }

    @Test
    fun `permanent deletion preserves blobs when metadata removal fails`() {
        val store = InMemoryDraftStore()
        val attachments = InMemoryAttachmentStore()
        val flow = StudentProjectDraftFlow(store, { "delete-project" }, { 100 }, attachmentStore = attachments)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Project with an attachment"),
        ).value
        attachments.blobs += project.id
        store.saveResult = LocalStorageWriteResult.FAILED

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, flow.permanentlyDelete(project.id))
        assertEquals(listOf(project), store.drafts)
        assertEquals(setOf(project.id), attachments.blobs)
        assertTrue(attachments.pending.isEmpty())
    }

    @Test
    fun `permanent deletion reports incomplete cleanup and catalog load retries it`() {
        val store = InMemoryDraftStore()
        val attachments = InMemoryAttachmentStore()
        val flow = StudentProjectDraftFlow(store, { "delete-project" }, { 100 }, attachmentStore = attachments)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Project with an attachment"),
        ).value
        attachments.blobs += project.id
        attachments.failNextCleanup = true

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, flow.permanentlyDelete(project.id))
        assertTrue(store.drafts.isEmpty())
        assertEquals(setOf(project.id), attachments.blobs)
        assertEquals("COMMITTED", attachments.pending[project.id])
        assertIs<StudentProjectAttachmentReadResult.Failed>(attachments.openRead(project.id, "attachment-one"))

        assertEquals(emptyList(), assertIs<StudentProjectDraftFlowResult.Value<List<StudentProjectDraft>>>(flow.list()).value)
        assertTrue(attachments.blobs.isEmpty())
        assertTrue(attachments.pending.isEmpty())
    }

    @Test
    fun `unsupported attachment storage keeps metadata-only local project CRUD available`() {
        val store = InMemoryDraftStore()
        var now = 100L
        val flow = StudentProjectDraftFlow(
            store = store,
            idGenerator = { "metadata-only-project" },
            clock = { now },
            attachmentStore = UnavailableStudentProjectAttachmentStore(),
        )
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Local project"),
        ).value
        assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(project.id, "Edited local project", emptyMap()),
        )
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.archive(project.id))
        assertEquals(
            StudentProjectStatus.ARCHIVED,
            assertIs<StudentProjectDraftFlowResult.Value<List<StudentProjectDraft>>>(flow.list()).value.single().status,
        )

        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.permanentlyDelete(project.id))
        assertTrue(store.drafts.isEmpty())

        val expired = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Metadata-only trash"),
        ).value
        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.delete(expired.id))
        now = StudentProjectDraftRules.TRASH_RETENTION_MILLIS + 100
        assertEquals(
            emptyList(),
            assertIs<StudentProjectDraftFlowResult.Value<List<StudentProjectDraft>>>(flow.list()).value,
        )
        assertTrue(store.drafts.isEmpty())
    }

    @Test
    fun `unsupported attachment storage refuses deletion while any attachment refs remain`() {
        val store = InMemoryDraftStore()
        val flow = StudentProjectDraftFlow(
            store = store,
            idGenerator = { "attachment-project" },
            clock = { 100 },
            attachmentStore = UnavailableStudentProjectAttachmentStore(),
        )
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Project with an attachment"),
        ).value
        val reference = StudentProjectAttachmentRef(
            id = "attachment-one",
            fileName = "evidence.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            sha256 = "0".repeat(64),
        )
        val attachmentBearing = project.copy(
            revisionSnapshots = project.revisionSnapshots.map { it.copy(attachments = listOf(reference)) },
        )
        store.drafts[0] = attachmentBearing

        assertEquals(
            StudentProjectDraftFlowResult.StorageUnavailable,
            flow.permanentlyDelete(project.id),
        )
        assertEquals(listOf(attachmentBearing), store.drafts)
    }

    @Test
    fun `AI project is saved once only after a selected reviewed scaffold is confirmed`() {
        val store = InMemoryDraftStore()
        val template = publishedTemplate()
        val proposal = ProjectAiScaffoldProposal(
            templateId = template.id,
            templateVersion = template.version,
            baseProjectRevision = null,
            promptVersion = "project-scaffold.v1",
            guidanceText = "Review this question against your assignment instructions.",
            fieldSuggestions = listOf(
                ProjectAiFieldSuggestion("question", "How does the outcome vary under the tested conditions?"),
            ),
            clarificationQuestions = listOf("Which outcome will you measure?"),
            recommendedNextPrompts = listOf("What evidence would answer my question?"),
        )

        val unconfirmed = flow(store).startWithAiScaffold(
            template = template,
            title = "My investigation",
            proposal = proposal,
            selectedFieldIds = emptySet(),
        )

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("PROJECT_AI_NO_FIELDS_SELECTED"),
            unconfirmed,
        )
        assertEquals(emptyList(), store.drafts)

        val confirmed = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow(store).startWithAiScaffold(
                template = template,
                title = "My investigation",
                proposal = proposal,
                selectedFieldIds = setOf("question"),
                editedValues = mapOf("question" to "My edited and confirmed research question?"),
            ),
        ).value

        assertEquals(1, store.saveCount)
        assertEquals("My edited and confirmed research question?", confirmed.fieldValues["question"])
        assertEquals(listOf(confirmed), store.drafts)
    }

    @Test
    fun `stale AI scaffold is rejected without saving a project`() {
        val store = InMemoryDraftStore()
        val template = publishedTemplate()
        val proposal = ProjectAiScaffoldProposal(
            templateId = template.id,
            templateVersion = template.version - 1,
            baseProjectRevision = null,
            promptVersion = "project-scaffold.v1",
            guidanceText = "Review the proposed question against your assignment.",
            fieldSuggestions = listOf(ProjectAiFieldSuggestion("question", "A proposed question?")),
            clarificationQuestions = emptyList(),
            recommendedNextPrompts = emptyList(),
        )

        val result = flow(store).startWithAiScaffold(
            template = template,
            title = "My investigation",
            proposal = proposal,
            selectedFieldIds = setOf("question"),
        )

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_AI_TEMPLATE_MISMATCH"), result)
        assertEquals(0, store.saveCount)
        assertEquals(emptyList(), store.drafts)
    }

    @Test
    fun `in-project AI suggestions apply only after confirmation against the current revision`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val template = publishedTemplate()
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(template, "My inquiry"),
        ).value
        val proposal = ProjectAiScaffoldProposal(
            templateId = template.id,
            templateVersion = template.version,
            baseProjectRevision = project.revision,
            promptVersion = "project-scaffold.v1",
            guidanceText = "Review the proposed question against your current project.",
            fieldSuggestions = listOf(ProjectAiFieldSuggestion("question", "A bounded suggested question?")),
            clarificationQuestions = emptyList(),
            recommendedNextPrompts = emptyList(),
            projectId = project.id,
        )

        val applied = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.applyAiScaffoldToProject(
                projectId = project.id,
                proposal = proposal,
                selectedFieldIds = setOf("question"),
                editedValues = mapOf("question" to "My reviewed project question?"),
            ),
        ).value

        assertEquals(2, applied.revision)
        assertEquals("My reviewed project question?", applied.fieldValues["question"])
        assertEquals(applied, store.drafts.single())
    }

    @Test
    fun `stale in-project AI suggestion is rejected without changing the saved draft`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val template = publishedTemplate()
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(template, "My inquiry"),
        ).value
        val proposal = ProjectAiScaffoldProposal(
            templateId = template.id,
            templateVersion = template.version,
            baseProjectRevision = project.revision - 1,
            promptVersion = "project-scaffold.v1",
            guidanceText = "Review this suggestion against your current project.",
            fieldSuggestions = listOf(ProjectAiFieldSuggestion("question", "A stale question?")),
            clarificationQuestions = emptyList(),
            recommendedNextPrompts = emptyList(),
            projectId = project.id,
        )

        val rejected = flow.applyAiScaffoldToProject(
            projectId = project.id,
            proposal = proposal,
            selectedFieldIds = setOf("question"),
        )

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_AI_STALE_REVISION"), rejected)
        assertEquals(listOf(project), store.drafts)
    }

    @Test
    fun `stage AI applies only confirmed allowlisted values and records assistance provenance`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val template = projectAiTemplate()
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(template, "My inquiry"),
        ).value
        val saved = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(started.id, started.title, mapOf("question" to "What changes?", "method" to "Compare observations.")),
        ).value

        val applied = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.applyAiStageAssistToProject(
                projectId = saved.id,
                expectedTemplateId = template.id,
                expectedTemplateVersion = template.version,
                stageId = "frame",
                operationId = "suggest_revision",
                expectedBaseRevision = saved.revision,
                expectedBeforeValues = mapOf("question" to "What changes?"),
                confirmedValues = mapOf("question" to "How does the measured condition relate to the observed outcome?"),
            ),
        ).value

        assertEquals(saved.revision + 1, applied.revision)
        assertEquals("How does the measured condition relate to the observed outcome?", applied.fieldValues["question"])
        assertEquals("Compare observations.", applied.fieldValues["method"])
        assertEquals(StudentProjectRevisionActor.CONFIRMED_ASSISTANCE, applied.revisionSnapshots.last().actor)
        assertTrue(applied.revisionSnapshots.last().changeSummary.contains("AI"))
    }

    @Test
    fun `stage AI rejects stale and non-allowlisted changes without writing`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val template = projectAiTemplate()
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(template, "My inquiry"),
        ).value
        val saved = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(started.id, started.title, mapOf("question" to "Old question", "method" to "Old method")),
        ).value
        val newer = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(saved.id, saved.title, mapOf("question" to "New question", "method" to "Old method")),
        ).value
        val writesBeforeRejectedApplies = store.saveCount

        val stale = flow.applyAiStageAssistToProject(
            projectId = newer.id,
            expectedTemplateId = template.id,
            expectedTemplateVersion = template.version,
            stageId = "frame",
            operationId = "suggest_revision",
            expectedBaseRevision = saved.revision,
            expectedBeforeValues = mapOf("question" to "Old question"),
            confirmedValues = mapOf("question" to "A stale suggestion."),
        )
        val unlisted = flow.applyAiStageAssistToProject(
            projectId = newer.id,
            expectedTemplateId = template.id,
            expectedTemplateVersion = template.version,
            stageId = "frame",
            operationId = "suggest_revision",
            expectedBaseRevision = newer.revision,
            expectedBeforeValues = mapOf("method" to "Old method"),
            confirmedValues = mapOf("method" to "An unsupported change."),
        )

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_AI_STALE_REVISION"), stale)
        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_AI_FIELD_NOT_ALLOWED"), unlisted)
        assertEquals(writesBeforeRejectedApplies, store.saveCount)
        assertEquals(newer, store.drafts.single())
    }

    @Test
    fun `in-project AI preview cannot be applied to another project`() {
        val store = InMemoryDraftStore()
        val ids = mutableListOf("project-source", "project-other")
        val flow = StudentProjectDraftFlow(store, { ids.removeAt(0) }, { 100 })
        val template = publishedTemplate()
        val sourceProject = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(template, "Source project"),
        ).value
        val otherProject = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(template, "Other project"),
        ).value
        val proposal = ProjectAiScaffoldProposal(
            templateId = template.id,
            templateVersion = template.version,
            baseProjectRevision = sourceProject.revision,
            promptVersion = "project-scaffold.v1",
            guidanceText = "Review this suggestion against the selected project.",
            fieldSuggestions = listOf(ProjectAiFieldSuggestion("question", "A project-specific question?")),
            clarificationQuestions = emptyList(),
            recommendedNextPrompts = emptyList(),
            projectId = sourceProject.id,
        )

        val result = flow.applyAiScaffoldToProject(
            projectId = otherProject.id,
            proposal = proposal,
            selectedFieldIds = setOf("question"),
        )

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_AI_PROJECT_MISMATCH"), result)
        assertEquals(listOf(sourceProject, otherProject), store.drafts)
    }

    @Test
    fun `update keeps template identity and increments local revision`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(publishedTemplate(), "My inquiry"),
        ).value

        val updated = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(started.id, "Updated inquiry", mapOf("question" to "Why?")),
        ).value

        assertEquals(started.templateSnapshot, updated.templateSnapshot)
        assertEquals(started.createdAtEpochMillis, updated.createdAtEpochMillis)
        assertEquals(2, updated.revision)
        assertEquals(listOf(1, 2), updated.revisionSnapshots.map { it.revision })
        assertEquals("Why?", updated.fieldValues["question"])
    }

    @Test
    fun `optional deadline saves clears and restores as part of project revision history`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value

        val dated = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = started.id,
                title = started.title,
                fieldValues = started.fieldValues,
                deadlineChange = StudentProjectDeadlineChange.Set("2026-10-15"),
            ),
        ).value
        val cleared = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = dated.id,
                title = dated.title,
                fieldValues = dated.fieldValues,
                deadlineChange = StudentProjectDeadlineChange.Set(null),
            ),
        ).value

        assertEquals("2026-10-15", dated.deadlineDate)
        assertEquals("2026-10-15", dated.revisionSnapshots.last().deadlineDate)
        assertEquals(null, cleared.deadlineDate)
        assertEquals(dated.revision + 1, cleared.revision)

        val restored = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.restoreRevision(started.id, dated.revision),
        ).value
        assertEquals("2026-10-15", restored.deadlineDate)
        assertEquals("2026-10-15", restored.revisionSnapshots.last().deadlineDate)
    }

    @Test
    fun `autosave preserves content without creating a revision checkpoint`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value

        val autosaved = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = started.id,
                title = started.title,
                fieldValues = mapOf(ManualLiteratureSynthesisFields.RESEARCH_QUESTION to "Can this be saved?"),
                checkpoint = false,
            ),
        ).value

        assertEquals(started.revision, autosaved.revision)
        assertEquals(started.revisionSnapshots, autosaved.revisionSnapshots)
        assertTrue(StudentProjectDraftRules.needsRevisionCheckpoint(autosaved))

        val checkpoint = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                projectId = started.id,
                title = autosaved.title,
                fieldValues = autosaved.fieldValues,
            ),
        ).value
        assertEquals(started.revision + 1, checkpoint.revision)
        assertEquals(listOf(1, 2), checkpoint.revisionSnapshots.map { it.revision })
        assertEquals(StudentProjectRevisionActor.STUDENT, checkpoint.revisionSnapshots.last().actor)
    }

    @Test
    fun `restoring a prior revision creates a new checkpoint and preserves later history`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value
        val second = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                started.id,
                started.title,
                mapOf(ManualLiteratureSynthesisFields.RESEARCH_QUESTION to "First saved question?"),
            ),
        ).value
        val third = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.update(
                started.id,
                started.title,
                mapOf(ManualLiteratureSynthesisFields.RESEARCH_QUESTION to "Later question?"),
            ),
        ).value

        val restored = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.restoreRevision(started.id, second.revision),
        ).value

        assertEquals(4, restored.revision)
        assertEquals("First saved question?", restored.fieldValues[ManualLiteratureSynthesisFields.RESEARCH_QUESTION])
        assertEquals(listOf(1, 2, 3, 4), restored.revisionSnapshots.map { it.revision })
        assertTrue(restored.revisionSnapshots.last().changeSummary.contains("Restored revision 2"))
        assertEquals("Later question?", third.fieldValues[ManualLiteratureSynthesisFields.RESEARCH_QUESTION])
        assertEquals(
            "Later question?",
            restored.revisionSnapshots.single { it.revision == 3 }.fieldValues[ManualLiteratureSynthesisFields.RESEARCH_QUESTION],
        )
    }

    @Test
    fun `archive import adds an unseen validated project in one store replacement`() {
        val store = InMemoryDraftStore()
        val imported = manualProject("imported-project")

        val result = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectImportReceipt>>(
            flow(store).importValidatedProject(imported),
        ).value

        assertEquals(imported, result.project)
        assertEquals(false, result.importedAsCopy)
        assertEquals(false, result.archivedToRespectCapacity)
        assertEquals(listOf(imported), store.drafts)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `archive codec preview can be atomically imported into the local project store`() {
        val original = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject("f3cd82c5-b6ba-4f25-80e3-ae743ce4071b"),
        )
        val archive = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(original, appVersion = "0.1.0"),
        )
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(archive.bytes))
        val store = InMemoryDraftStore()

        val imported = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectImportReceipt>>(
            flow(store).importValidatedProject(preview.project),
        ).value

        assertEquals(original, imported.project)
        assertEquals(listOf(original), store.drafts)
    }

    @Test
    fun `archive import reports an ID conflict and never overwrites existing work`() {
        val original = manualProject("shared-project-id")
        val store = InMemoryDraftStore().apply { drafts += original }
        val imported = original.copy(title = "Different archived project")

        val result = flow(store).importValidatedProject(imported)

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_ID_CONFLICT"), result)
        assertEquals(listOf(original), store.drafts)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `archive import as copy remaps project and linked record IDs including revision history`() {
        val original = researchProject("shared-project-id")
        val store = InMemoryDraftStore().apply { drafts += original.copy(title = "Existing work") }
        val generated = ArrayDeque(listOf("copy-project", "copy-source", "copy-theme", "copy-evidence", "copy-finding", "copy-claim", "copy-limit"))
        val flow = StudentProjectDraftFlow(store, { generated.removeFirst() }, { 200 })

        val copied = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectImportReceipt>>(
            flow.importValidatedProject(original, importAsCopy = true),
        ).value.project

        assertEquals(2, store.drafts.size)
        assertEquals("copy-project", copied.id)
        assertEquals("copy-source", copied.sources.single().id)
        assertEquals(setOf("copy-source"), copied.themes.single().sourceIds)
        assertEquals(setOf("copy-source"), copied.claimEvidenceSourceIds)
        assertEquals("copy-evidence", copied.evidenceItems.single().id)
        assertEquals("copy-source", copied.evidenceItems.single().sourceId)
        assertEquals("copy-finding", copied.findings.single().id)
        assertEquals("copy-claim", copied.claims.single().id)
        assertEquals("copy-limit", copied.limitationActions.single().id)
        assertEquals(setOf("copy-finding"), copied.limitationActions.single().affectedFindingIds)
        assertEquals(setOf("copy-claim"), copied.limitationActions.single().affectedClaimIds)
        assertEquals("copy-finding", copied.evidenceRelations.single { it.targetType == StudentProjectEvidenceTargetType.FINDING }.targetId)
        assertEquals("copy-claim", copied.evidenceRelations.single { it.targetType == StudentProjectEvidenceTargetType.CLAIM }.targetId)
        assertEquals("copy-evidence", copied.evidenceRelations.first().evidenceId)
        assertEquals("copy-source", copied.revisionSnapshots.single().sources.single().id)
        assertEquals("copy-finding", copied.revisionSnapshots.single().findings.single().id)
        assertEquals("copy-claim", copied.revisionSnapshots.single().claims.single().id)
        assertEquals("copy-limit", copied.revisionSnapshots.single().limitationActions.single().id)
        assertEquals(setOf("copy-finding"), copied.revisionSnapshots.single().limitationActions.single().affectedFindingIds)
        assertEquals(setOf("copy-claim"), copied.revisionSnapshots.single().limitationActions.single().affectedClaimIds)
        assertEquals("shared-project-id", store.drafts.first().id)
    }

    @Test
    fun `archive import as copy remaps and commits attachment data with project metadata`() {
        val projectId = "f3cd82c5-b6ba-4f25-80e3-ae743ce4071b"
        val copyProjectId = "f3cd82c5-b6ba-4f25-80e3-ae743ce4071c"
        val copyAttachmentId = "f3cd82c5-b6ba-4f25-80e3-ae743ce4071d"
        val bytes = "%PDF-1.4\nEvidence source\n%%EOF".encodeToByteArray()
        val reference = StudentProjectAttachmentRef(
            id = "attachment-a",
            fileName = "source.pdf",
            mimeType = "application/pdf",
            sizeBytes = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
        val original = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject(projectId).copy(attachments = listOf(reference)),
        )
        val archive = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(
                original,
                appVersion = "1.0.0",
                attachmentStore = InMemoryAttachmentStore(reference, bytes),
            ),
        )
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(archive.bytes))
        val projectStore = InMemoryDraftStore().apply { drafts += original.copy(title = "Existing project") }
        val attachmentStore = InMemoryAttachmentStore()
        val generated = ArrayDeque(listOf(copyProjectId, copyAttachmentId))

        val result = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectImportReceipt>>(
            StudentProjectDraftFlow(projectStore, { generated.removeFirst() }, { 200 })
                .importValidatedArchive(preview, archive.bytes, attachmentStore, importAsCopy = true),
        ).value

        assertEquals(copyProjectId, result.project.id)
        assertEquals(copyAttachmentId, result.project.attachments.single().id)
        assertEquals(copyAttachmentId, result.project.revisionSnapshots.single().attachments.single().id)
        assertEquals(copyProjectId, attachmentStore.importProjectId)
        assertEquals(bytes.toList(), attachmentStore.committed[copyAttachmentId]?.toList())
        assertTrue(attachmentStore.completed)
        assertEquals(false, attachmentStore.rolledBack)
        assertEquals(2, projectStore.drafts.size)
        assertEquals(projectId, projectStore.drafts.first().id)
    }

    @Test
    fun `failed local project write rolls back published attachment bytes`() {
        val projectId = "f3cd82c5-b6ba-4f25-80e3-ae743ce4071b"
        val bytes = "%PDF-1.4\nEvidence source\n%%EOF".encodeToByteArray()
        val reference = StudentProjectAttachmentRef(
            id = "attachment-a",
            fileName = "source.pdf",
            mimeType = "application/pdf",
            sizeBytes = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
        val original = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject(projectId).copy(attachments = listOf(reference)),
        )
        val archive = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(
                original,
                appVersion = "1.0.0",
                attachmentStore = InMemoryAttachmentStore(reference, bytes),
            ),
        )
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(archive.bytes))
        val projectStore = InMemoryDraftStore().apply { saveResult = LocalStorageWriteResult.FAILED }
        val attachmentStore = InMemoryAttachmentStore()

        val result = StudentProjectDraftFlow(projectStore, { "unused-id" }, { 200 })
            .importValidatedArchive(preview, archive.bytes, attachmentStore)

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, result)
        assertTrue(attachmentStore.rolledBack)
        assertTrue(attachmentStore.committed.isEmpty())
        assertTrue(projectStore.drafts.isEmpty())
    }

    @Test
    fun `selected attachment is staged privately and saved as a project revision`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        val generatedIds = ArrayDeque(listOf("project-one", "attachment-one"))
        val flow = StudentProjectDraftFlow(
            projectStore,
            { generatedIds.removeFirst() },
            { 200 },
            attachmentStore = attachmentStore,
        )
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value
        val bytes = "%PDF-1.4\nStudent-selected notes\n%%EOF".encodeToByteArray()

        val receipt = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectAttachmentAddReceipt>>(
            flow.addAttachment(project.id, "notes.pdf", bytes),
        ).value

        assertEquals(project.id, attachmentStore.importProjectId)
        assertEquals(bytes.toList(), attachmentStore.committed.getValue("attachment-one").toList())
        assertTrue(attachmentStore.completed)
        assertEquals(false, attachmentStore.rolledBack)
        assertEquals(listOf("attachment-one"), receipt.project.attachments.map { it.id })
        assertEquals(receipt.project.attachments, receipt.project.revisionSnapshots.last().attachments)
        assertEquals(2, receipt.project.revision)
        assertEquals(false, receipt.cleanupPending)
        assertEquals(receipt.project, projectStore.drafts.single())
    }

    @Test
    fun `removing current attachment keeps its bytes available to the earlier revision`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        val generatedIds = ArrayDeque(listOf("project-with-file-history", "attachment-kept-in-history"))
        val flow = StudentProjectDraftFlow(
            projectStore,
            { generatedIds.removeFirst() },
            { 300 },
            attachmentStore = attachmentStore,
        )
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Project with file history"),
        ).value
        val bytes = "%PDF-1.4\nKeep this in the earlier revision\n%%EOF".encodeToByteArray()
        val attached = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectAttachmentAddReceipt>>(
            flow.addAttachment(project.id, "source.pdf", bytes),
        ).value.project
        val attachment = attached.attachments.single()

        val removal = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectAttachmentRemoveReceipt>>(
            flow.removeAttachment(project.id, attachment.id),
        ).value
        val revisedProject = removal.project

        assertTrue(revisedProject.attachments.isEmpty())
        assertTrue(revisedProject.revision > attached.revision)
        assertEquals(listOf(attachment), revisedProject.revisionSnapshots.single { it.revision == attached.revision }.attachments)
        assertTrue(revisedProject.revisionSnapshots.last().attachments.isEmpty())
        assertEquals(true, removal.preservedInRevisionHistory)
        assertEquals(false, removal.cleanupPending)
        assertTrue(attachmentStore.committed.getValue(attachment.id).contentEquals(bytes))
        assertEquals(null, attachmentStore.pendingAttachmentDeletions[attachment.id])
    }

    @Test
    fun `restoring an earlier revision restores its retained attachment`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        val generatedIds = ArrayDeque(listOf("project-with-restored-file", "attachment-restored-from-history"))
        val flow = StudentProjectDraftFlow(
            projectStore,
            { generatedIds.removeFirst() },
            { 400 },
            attachmentStore = attachmentStore,
        )
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Project with file history"),
        ).value
        val bytes = "%PDF-1.4\nRestore this source with its saved revision\n%%EOF".encodeToByteArray()
        val attached = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectAttachmentAddReceipt>>(
            flow.addAttachment(project.id, "source.pdf", bytes),
        ).value.project
        val attachment = attached.attachments.single()
        val removed = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectAttachmentRemoveReceipt>>(
            flow.removeAttachment(project.id, attachment.id),
        ).value.project

        assertTrue(removed.attachments.isEmpty())
        assertTrue(attachmentStore.committed.getValue(attachment.id).contentEquals(bytes))

        val restored = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.restoreRevision(project.id, attached.revision),
        ).value

        assertEquals(listOf(attachment), restored.attachments)
        assertEquals(listOf(attachment), restored.revisionSnapshots.last().attachments)
        assertEquals(listOf(attachment), projectStore.drafts.single().attachments)
        assertTrue(attachmentStore.committed.getValue(attachment.id).contentEquals(bytes))
    }

    @Test
    fun `failed metadata save cancels attachment removal and leaves current file attached`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        val generatedIds = ArrayDeque(listOf("project-with-file-history", "attachment-kept-in-history"))
        val flow = StudentProjectDraftFlow(
            projectStore,
            { generatedIds.removeFirst() },
            { 300 },
            attachmentStore = attachmentStore,
        )
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("Project with file history"),
        ).value
        val bytes = "%PDF-1.4\nKeep this in the current project\n%%EOF".encodeToByteArray()
        val attached = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectAttachmentAddReceipt>>(
            flow.addAttachment(project.id, "source.pdf", bytes),
        ).value.project
        val attachment = attached.attachments.single()
        projectStore.saveResult = LocalStorageWriteResult.FAILED

        val result = flow.removeAttachment(project.id, attachment.id)

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, result)
        assertEquals(listOf(attachment), projectStore.drafts.single().attachments)
        assertEquals(listOf(attachment), projectStore.drafts.single().revisionSnapshots.last().attachments)
        assertTrue(attachmentStore.committed.getValue(attachment.id).contentEquals(bytes))
        assertEquals(null, attachmentStore.pendingAttachmentDeletions[attachment.id])
    }

    @Test
    fun `failed project metadata write rolls back selected attachment bytes`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        var id = 0
        val flow = StudentProjectDraftFlow(projectStore, { "local-${++id}" }, { 200 }, attachmentStore = attachmentStore)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value
        projectStore.saveResult = LocalStorageWriteResult.FAILED

        val result = flow.addAttachment(
            project.id,
            "notes.pdf",
            "%PDF-1.4\nStudent-selected notes\n%%EOF".encodeToByteArray(),
        )

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, result)
        assertTrue(attachmentStore.rolledBack)
        assertTrue(attachmentStore.committed.isEmpty())
        assertEquals(listOf(project), projectStore.drafts)
    }

    @Test
    fun `cancellation during attachment staging rolls back bytes and leaves project revision unchanged`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore().apply { cancelOnWriteNumber = 2 }
        val flow = StudentProjectDraftFlow(projectStore, { "local-id" }, { 200 }, attachmentStore = attachmentStore)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value
        val bytes = ByteArray(STUDENT_PROJECT_ATTACHMENT_MAX_IO_CHUNK_BYTES + 1) { 'x'.code.toByte() }

        assertFailsWith<CancellationException> {
            flow.addAttachment(project.id, "notes.txt", bytes)
        }

        assertTrue(attachmentStore.rolledBack)
        assertTrue(attachmentStore.committed.isEmpty())
        assertEquals(listOf(project), projectStore.drafts)
    }

    @Test
    fun `invalid selected attachment is rejected before storage starts`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        val flow = StudentProjectDraftFlow(projectStore, { "local-id" }, { 200 }, attachmentStore = attachmentStore)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value

        val result = flow.addAttachment(project.id, "notes.pdf", "not a PDF".encodeToByteArray())

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("PROJECT_ATTACHMENT_CONTENT_INVALID"),
            result,
        )
        assertEquals(null, attachmentStore.importProjectId)
        assertEquals(listOf(project), projectStore.drafts)
    }

    @Test
    fun `NUL bytes in selected plain text attachment are rejected without crashing the flow`() {
        val projectStore = InMemoryDraftStore()
        val attachmentStore = InMemoryAttachmentStore()
        val flow = StudentProjectDraftFlow(projectStore, { "local-id" }, { 200 }, attachmentStore = attachmentStore)
        val project = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.startManual("My project"),
        ).value

        val result = flow.addAttachment(project.id, "notes.txt", byteArrayOf(0))

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("PROJECT_ATTACHMENT_CONTENT_INVALID"),
            result,
        )
        assertEquals(null, attachmentStore.importProjectId)
        assertEquals(listOf(project), projectStore.drafts)
    }

    @Test
    fun `cancelled archive import rolls back staged files and leaves local projects unchanged`() {
        val projectId = "f3cd82c5-b6ba-4f25-80e3-ae743ce4071b"
        val bytes = "%PDF-1.4\nEvidence source\n%%EOF".encodeToByteArray()
        val reference = StudentProjectAttachmentRef(
            id = "attachment-a",
            fileName = "source.pdf",
            mimeType = "application/pdf",
            sizeBytes = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
        val incoming = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject(projectId).copy(attachments = listOf(reference)),
        )
        val archive = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(
                incoming,
                appVersion = "1.0.0",
                attachmentStore = InMemoryAttachmentStore(reference, bytes),
            ),
        )
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(archive.bytes))
        val existing = manualProject("existing-project")
        val projectStore = InMemoryDraftStore().apply { drafts += existing }
        val attachmentStore = InMemoryAttachmentStore()

        assertFailsWith<CancellationException> {
            StudentProjectDraftFlow(projectStore, { "unused-id" }, { 200 })
                .importValidatedArchive(
                    preview,
                    archive.bytes,
                    attachmentStore,
                    ensureActive = {
                        if (attachmentStore.importProjectId != null) throw CancellationException("cancel archive import")
                    },
                )
        }

        assertEquals(listOf(existing), projectStore.drafts)
        assertTrue(attachmentStore.rolledBack)
        assertTrue(attachmentStore.committed.isEmpty())
    }

    @Test
    fun `archive import cannot bypass the free total project cap`() {
        val store = InMemoryDraftStore()
        val generated = ArrayDeque((1..5).map { "existing-$it" })
        val flow = StudentProjectDraftFlow(store, { generated.removeFirst() }, { 100 })
        repeat(5) { index ->
            assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
                flow.startManual("Existing ${index + 1}"),
            )
        }
        val incoming = manualProject("incoming-project")

        val blocked = flow.importValidatedProject(incoming)
        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED"), blocked)
        assertEquals(5, store.drafts.size)

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED"),
            flow.importValidatedProject(incoming, archiveWhenAtCapacity = true),
        )
        assertEquals(5, store.drafts.size)
        assertTrue(store.drafts.none { it.id == incoming.id })
    }

    @Test
    fun `restoring a newer archive revision appends a new local checkpoint`() {
        val original = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject("shared-project-id").copy(title = "Original local state"),
        )
        val store = InMemoryDraftStore().apply { drafts += original }
        val imported = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject("shared-project-id").copy(
                title = "Newer archive state",
                revision = 3,
                updatedAtEpochMillis = 150,
            ),
        )
        val flow = StudentProjectDraftFlow(store, { "unused-id" }, { 200 })

        val restored = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectImportReceipt>>(
            flow.restoreImportedProjectAsRevision(imported),
        ).value

        assertEquals("shared-project-id", restored.project.id)
        assertEquals("Newer archive state", restored.project.title)
        assertEquals(2, restored.project.revision)
        assertEquals(listOf(1, 2), restored.project.revisionSnapshots.map { it.revision })
        assertEquals("Original local state", restored.project.revisionSnapshots.first().title)
        assertTrue(restored.project.revisionSnapshots.last().changeSummary.contains("archive revision 3"))
        assertTrue(restored.restoredAsNewRevision)
        assertEquals(restored.project, store.drafts.single())
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `restoring an equal or older archive revision is rejected without changes`() {
        val original = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject("shared-project-id").copy(revision = 2),
        )
        val store = InMemoryDraftStore().apply { drafts += original }
        val imported = manualProject("shared-project-id").copy(revision = 2)

        val result = flow(store).restoreImportedProjectAsRevision(imported)

        assertEquals(StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_NO_NEW_REVISION"), result)
        assertEquals(listOf(original), store.drafts)
        assertEquals(0, store.saveCount)
    }

    private fun manualProject(id: String): StudentProjectDraft = assertIs<
        dev.nextgen.mobile.domain.project.StudentProjectDraftCreateResult.Created
    >(
        StudentProjectDraftRules.createManual(id, "Imported project", 100),
    ).draft

    private fun researchProject(id: String): StudentProjectDraft {
        val source = StudentProjectSourceRecord("source-a", title = "Student-entered source")
        val project = manualProject(id).copy(
            sources = listOf(source),
            themes = listOf(StudentProjectSynthesisTheme("theme-a", sourceIds = setOf(source.id))),
            claimEvidenceSourceIds = setOf(source.id),
            evidenceItems = listOf(StudentProjectEvidenceItem("evidence-a", source.id, excerpt = "Recorded excerpt")),
            findings = listOf(StudentProjectFindingRecord("finding-a", statement = "A bounded finding")),
            claims = listOf(StudentProjectClaimRecord(
                "claim-a",
                statement = "A bounded claim",
                scopeNote = "In the described context.",
                reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
            )),
            limitationActions = listOf(StudentProjectLimitationActionRecord(
                id = "limit-a",
                boundary = "One study covers a different population.",
                reason = "The finding is limited to the populations actually described.",
                nextAction = "Compare population-specific findings.",
                affectedFindingIds = setOf("finding-a"),
                affectedClaimIds = setOf("claim-a"),
            )),
            evidenceRelations = listOf(
                StudentProjectEvidenceRelation(
                    targetType = StudentProjectEvidenceTargetType.FINDING,
                    targetId = "finding-a",
                    evidenceId = "evidence-a",
                    relation = StudentProjectEvidenceRelationType.SUPPORTS,
                ),
                StudentProjectEvidenceRelation(
                    targetType = StudentProjectEvidenceTargetType.CLAIM,
                    targetId = "claim-a",
                    evidenceId = "evidence-a",
                    relation = StudentProjectEvidenceRelationType.PROVIDES_CONTEXT,
                ),
            ),
        )
        return StudentProjectDraftRules.initializeRevisionHistory(project)
    }

    private class InMemoryAttachmentStore(
        private val sourceReference: StudentProjectAttachmentRef? = null,
        private val sourceBytes: ByteArray? = null,
    ) : StudentProjectAttachmentStore {
        var cancelOnWriteNumber: Int? = null
        var importProjectId: String? = null
        var completed = false
        var rolledBack = false
        val committed = mutableMapOf<String, ByteArray>()
        val blobs = mutableSetOf<String>()
        val pending = mutableMapOf<String, String>()
        val pendingAttachmentDeletions = mutableMapOf<String, String>()
        var failNextCleanup = false
        var recoveredAttachmentReferences: Map<String, Set<String>>? = null

        override fun recoverPendingAttachmentDeletions(
            existingAttachmentIdsByProject: Map<String, Set<String>>,
        ): LocalStorageWriteResult {
            recoveredAttachmentReferences = existingAttachmentIdsByProject
            var changed = false
            pendingAttachmentDeletions.toMap().forEach { (attachmentId, _) ->
                val referenced = existingAttachmentIdsByProject.values.any { attachmentId in it }
                if (referenced) {
                    pendingAttachmentDeletions.remove(attachmentId)
                } else {
                    committed.remove(attachmentId)
                    pendingAttachmentDeletions.remove(attachmentId)
                }
                changed = true
            }
            return if (changed) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.CLEARED
        }

        override fun prepareAttachmentDeletion(
            projectId: String,
            attachmentId: String,
        ): LocalStorageWriteResult {
            if (attachmentId !in committed) return LocalStorageWriteResult.FAILED
            pendingAttachmentDeletions[attachmentId] = "PREPARED"
            return LocalStorageWriteResult.SAVED
        }

        override fun cancelAttachmentDeletion(
            projectId: String,
            attachmentId: String,
        ): LocalStorageWriteResult {
            pendingAttachmentDeletions.remove(attachmentId)
            return LocalStorageWriteResult.CLEARED
        }

        override fun completeAttachmentDeletion(
            projectId: String,
            attachmentId: String,
        ): LocalStorageWriteResult {
            pendingAttachmentDeletions.remove(attachmentId)
            return if (committed.remove(attachmentId) != null) {
                LocalStorageWriteResult.SAVED
            } else {
                LocalStorageWriteResult.CLEARED
            }
        }

        override fun openRead(projectId: String, attachmentId: String): StudentProjectAttachmentReadResult {
            if (projectId in pending) {
                return StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_DELETION_PENDING")
            }
            val bytes = sourceBytes?.takeIf { sourceReference?.id == attachmentId }
                ?: return StudentProjectAttachmentReadResult.Missing
            return StudentProjectAttachmentReadResult.Opened(object : StudentProjectAttachmentReadHandle {
                private var cursor = 0
                override val sizeBytes = bytes.size.toLong()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (cursor >= bytes.size) return -1
                    val count = minOf(length, bytes.size - cursor)
                    bytes.copyInto(buffer, offset, cursor, cursor + count)
                    cursor += count
                    return count
                }
                override fun close() = Unit
            })
        }

        override fun beginImport(projectId: String): StudentProjectAttachmentImportResult {
            importProjectId = projectId
            return StudentProjectAttachmentImportResult.Started(object : StudentProjectAttachmentImportSession {
                val pending = mutableMapOf<String, ByteArrayOutputStream>()
                override fun openWriter(reference: StudentProjectAttachmentRef): StudentProjectAttachmentWriterResult {
                    val output = ByteArrayOutputStream()
                    pending[reference.id] = output
                    return StudentProjectAttachmentWriterResult.Opened(object : StudentProjectAttachmentWriteHandle {
                        private var writeCount = 0
                        override fun write(buffer: ByteArray, offset: Int, length: Int) {
                            writeCount += 1
                            if (writeCount == cancelOnWriteNumber) throw CancellationException("cancel attachment import")
                            output.write(buffer, offset, length)
                        }
                        override fun finish(): LocalStorageWriteResult = LocalStorageWriteResult.SAVED
                        override fun abort() { pending.remove(reference.id) }
                    })
                }
                override fun publish(): LocalStorageWriteResult {
                    pending.forEach { (id, output) -> committed[id] = output.toByteArray() }
                    return LocalStorageWriteResult.SAVED
                }
                override fun complete(): LocalStorageWriteResult {
                    completed = true
                    return LocalStorageWriteResult.SAVED
                }
                override fun rollback(): LocalStorageWriteResult {
                    rolledBack = true
                    pending.keys.forEach(committed::remove)
                    pending.clear()
                    return LocalStorageWriteResult.CLEARED
                }
            })
        }

        override fun prepareProjectDeletion(
            projectId: String,
            hasAttachments: Boolean,
        ): LocalStorageWriteResult {
            pending.putIfAbsent(projectId, "PREPARED")
            return LocalStorageWriteResult.SAVED
        }

        override fun cancelProjectDeletion(projectId: String): LocalStorageWriteResult {
            if (pending[projectId] == "COMMITTED") return LocalStorageWriteResult.FAILED
            pending.remove(projectId)
            return LocalStorageWriteResult.CLEARED
        }

        override fun completeProjectDeletion(projectId: String): LocalStorageWriteResult {
            if (failNextCleanup) {
                failNextCleanup = false
                pending[projectId] = "COMMITTED"
                return LocalStorageWriteResult.FAILED
            }
            blobs.remove(projectId)
            if (importProjectId == projectId) committed.clear()
            pending.remove(projectId)
            return LocalStorageWriteResult.CLEARED
        }

        override fun recoverPendingProjectDeletions(
            existingProjectIds: Set<String>,
        ): LocalStorageWriteResult {
            var result = LocalStorageWriteResult.CLEARED
            pending.toMap().forEach { (projectId, state) ->
                val recovered = if (state == "COMMITTED" || projectId !in existingProjectIds) {
                    completeProjectDeletion(projectId)
                } else {
                    cancelProjectDeletion(projectId)
                }
                if (recovered == LocalStorageWriteResult.FAILED || recovered == LocalStorageWriteResult.UNAVAILABLE) {
                    result = recovered
                }
            }
            return result
        }
    }

    @Test
    fun `update rejects unknown fields without changing stored draft`() {
        val store = InMemoryDraftStore()
        val flow = flow(store)
        val started = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(publishedTemplate(), "My inquiry"),
        ).value

        val result = flow.update(started.id, "My inquiry", mapOf("arbitrary" to "text"))

        assertEquals(StudentProjectDraftFlowResult.Rejected("UNKNOWN_PROJECT_FIELD"), result)
        assertEquals(started, store.drafts.single())
    }

    @Test
    fun `delete removes only the selected project`() {
        val store = InMemoryDraftStore()
        val ids = ArrayDeque(listOf("project-a", "project-b"))
        val flow = StudentProjectDraftFlow(store, { ids.removeFirst() }, { 100 })
        val first = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(publishedTemplate(), "First"),
        ).value
        val second = assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(
            flow.start(publishedTemplate(), "Second"),
        ).value

        assertIs<StudentProjectDraftFlowResult.Value<Unit>>(flow.delete(first.id))
        assertEquals(2, store.drafts.size)
        assertEquals(StudentProjectStatus.TRASHED, store.drafts.first { it.id == first.id }.status)
        assertIs<StudentProjectDraftFlowResult.Value<StudentProjectDraft>>(flow.resume(first.id))
    }

    @Test
    fun `storage failures are not reported as saved`() {
        val store = InMemoryDraftStore().apply { saveResult = LocalStorageWriteResult.FAILED }

        val result = flow(store).start(publishedTemplate(), "My inquiry")

        assertEquals(StudentProjectDraftFlowResult.StorageFailed, result)
        assertEquals(emptyList(), store.drafts)
    }

    @Test
    fun `project storage limits are explicit and do not overwrite earlier work`() {
        val store = InMemoryDraftStore().apply { saveResult = LocalStorageWriteResult.LIMIT_REACHED }
        val result = flow(store).start(publishedTemplate(), "My inquiry")

        assertEquals(
            StudentProjectDraftFlowResult.Rejected("PROJECT_DRAFT_STORAGE_LIMIT_REACHED"),
            result,
        )
        assertEquals(emptyList(), store.drafts)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun `required-field progress is structure only`() {
        val draft = StudentProjectDraftRules.create(
            id = "project-a",
            template = publishedTemplate(),
            title = "My inquiry",
            createdAtEpochMillis = 100,
        ) as dev.nextgen.mobile.domain.project.StudentProjectDraftCreateResult.Created

        assertEquals(0, StudentProjectDraftRules.requiredFieldProgress(
            requireNotNull(draft.draft.templateSnapshot),
            draft.draft.fieldValues,
        ).filledRequired)
        assertEquals(1, StudentProjectDraftRules.requiredFieldProgress(
            requireNotNull(draft.draft.templateSnapshot),
            mapOf("question" to "A careful question"),
        ).filledRequired)
    }

    private fun flow(
        store: InMemoryDraftStore,
        attachments: StudentProjectAttachmentStore = InMemoryAttachmentStore(),
    ) = StudentProjectDraftFlow(store, { "project-a" }, { 100 }, attachmentStore = attachments)

    private fun projectAiTemplate() = publishedTemplate().copy(
        steps = listOf(
            ProjectTemplateStep(
                "frame",
                "Frame the project",
                listOf("question", "method"),
                aiOperations = listOf(
                    dev.nextgen.mobile.domain.project.ProjectTemplateAiOperationCapability(
                        "suggest_revision",
                        inputFieldIds = listOf("question"),
                        outputFieldIds = listOf("question"),
                    ),
                ),
            ),
        ),
    )

    private fun publishedTemplate() = ProjectTemplateDefinition(
        id = "reviewed-template",
        version = 2,
        family = ProjectTemplateFamily.EXPERIMENTAL_LABORATORY,
        title = "A reviewed experimental starter",
        summary = "A bounded starter with explicit limits.",
        intendedOutput = "A traceable project report.",
        inputFields = listOf(
            ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
            ProjectTemplateInputField("method", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "Method", true),
        ),
        steps = listOf(ProjectTemplateStep("frame", "Frame the project", listOf("question", "method"))),
        methodSpecificLimitations = listOf("Do not infer causation from a simple difference."),
        provenanceRequirements = listOf("Record source and measurement context."),
        accessibilityExpectations = listOf("Use text labels and non-color cues."),
        examples = listOf(
            ProjectTemplateExample("example-normal", "Reviewed nominal example.", true, ProjectTemplateExampleKind.NORMAL),
            ProjectTemplateExample(
                "example-edge",
                "Reviewed edge or conflicting example.",
                true,
                ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
            ),
        ),
        publication = ProjectTemplatePublication.PUBLISHED,
    )

    private class InMemoryDraftStore : StudentProjectDraftStore {
        val drafts = mutableListOf<StudentProjectDraft>()
        var saveResult = LocalStorageWriteResult.SAVED
        var saveCount = 0

        override fun load(): LocalStorageReadResult<List<StudentProjectDraft>> =
            LocalStorageReadResult.Success(drafts.toList())

        override fun replaceAll(drafts: List<StudentProjectDraft>): LocalStorageWriteResult {
            if (saveResult != LocalStorageWriteResult.SAVED) return saveResult
            saveCount += 1
            this.drafts.clear()
            this.drafts.addAll(drafts)
            return if (drafts.isEmpty()) LocalStorageWriteResult.CLEARED else LocalStorageWriteResult.SAVED
        }

        override fun save(draft: StudentProjectDraft): LocalStorageWriteResult {
            if (saveResult != LocalStorageWriteResult.SAVED) return saveResult
            saveCount += 1
            drafts.removeAll { it.id == draft.id }
            drafts += draft
            return LocalStorageWriteResult.SAVED
        }

        override fun delete(projectId: String): LocalStorageWriteResult {
            if (saveResult != LocalStorageWriteResult.SAVED) return saveResult
            drafts.removeAll { it.id == projectId }
            return LocalStorageWriteResult.CLEARED
        }
    }

}
