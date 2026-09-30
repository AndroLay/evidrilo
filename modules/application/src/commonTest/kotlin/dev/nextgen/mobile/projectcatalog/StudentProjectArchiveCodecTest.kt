package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftCreateResult
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.ProjectTemplateAiOperationCapability
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectStarterTemplateCatalog
import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectClaimReviewStatus
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportSession
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadHandle
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentStore
import dev.nextgen.mobile.storage.StudentProjectAttachmentWriteHandle
import dev.nextgen.mobile.storage.StudentProjectAttachmentWriterResult
import dev.nextgen.mobile.account.sha256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.kmpzip.io.ByteArrayInputStream
import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipInputStream
import no.synth.kmpzip.zip.ZipOutputStream
import no.synth.kmpzip.zip.ZipConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.coroutines.cancellation.CancellationException

class StudentProjectArchiveCodecTest {
    @Test
    fun `streaming SHA and CRC checksums match canonical fixture values across chunks`() {
        val bytes = "abc".encodeToByteArray()
        val sha = StudentProjectSha256Accumulator().apply {
            update(bytes, 0, 1)
            update(bytes, 1, 2)
        }.digest().toLowerHex()
        val crc = StudentProjectCrc32Accumulator().apply {
            update(bytes, 0, 1)
            update(bytes, 1, 2)
        }.value()

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha)
        assertEquals(0x352441c2L, crc)
        listOf(0, 1, 55, 56, 63, 64, 65, 1_024).forEach { size ->
            val sample = ByteArray(size) { ((it * 37) and 0xff).toByte() }
            val incremental = StudentProjectSha256Accumulator().apply {
                var offset = 0
                while (offset < sample.size) {
                    val length = minOf(17, sample.size - offset)
                    update(sample, offset, length)
                    offset += length
                }
            }.digest().toLowerHex()
            assertEquals(sha256(sample).toLowerHex(), incremental, "size=$size")
        }
    }

    @Test
    fun `archive is a zip and imports a project without changing its local snapshot`() {
        val project = manualProject()
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )

        assertEquals("${project.id}.evproj", encoded.fileName)
        assertEquals("application/vnd.evidrilo.project+zip", encoded.mimeType)
        assertTrue(encoded.bytes.size >= 4)
        assertEquals(0x50, encoded.bytes[0].toInt() and 0xff)
        assertEquals(0x4b, encoded.bytes[1].toInt() and 0xff)
        val preview = StudentProjectArchiveCodec.preview(encoded.bytes)
        assertTrue(preview is StudentProjectArchiveReadResult.Preview, "archive preview rejected: $preview")
        assertEquals(project, (preview as StudentProjectArchiveReadResult.Preview).project)
    }

    @Test
    fun `archive round trips declared stage AI capabilities in the immutable template snapshot`() {
        val template = ProjectTemplateDefinition(
            id = "review-outline",
            version = 3,
            family = ProjectTemplateFamily.LITERATURE_REVIEW,
            title = "Review outline",
            summary = "A bounded source-comparison structure.",
            intendedOutput = "A traceable outline.",
            inputFields = listOf(
                ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
            ),
            steps = listOf(ProjectTemplateStep(
                "scope",
                "Set the review scope",
                listOf("question"),
                listOf(ProjectTemplateAiOperationCapability(
                    "summarize_selected_material",
                    listOf("question"),
                    listOf("question"),
                )),
            )),
            methodSpecificLimitations = listOf("This outline does not establish complete literature coverage."),
            provenanceRequirements = listOf("Record the source origin and locator."),
            accessibilityExpectations = listOf("Keep labels explicit and readable."),
            examples = listOf(
                ProjectTemplateExample("normal", "Synthetic nominal example.", true, ProjectTemplateExampleKind.NORMAL),
                ProjectTemplateExample("edge", "Synthetic edge example.", true, ProjectTemplateExampleKind.EDGE_OR_CONFLICTING),
            ),
            publication = ProjectTemplatePublication.PUBLISHED,
        )
        val project = assertIs<StudentProjectDraftCreateResult.Created>(StudentProjectDraftRules.create(
            id = "f3cd82c5-b6ba-4f25-80e3-ae743ce4071b",
            template = template,
            title = "Review project",
            createdAtEpochMillis = 1_758_960_000_000,
            fieldValues = mapOf("question" to "How do the selected sources compare?"),
        )).draft
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )

        val restored = assertIs<StudentProjectArchiveReadResult.Preview>(
            StudentProjectArchiveCodec.preview(encoded.bytes),
        ).project

        assertEquals(project, restored)
        assertEquals(
            template.steps.single().aiOperations,
            restored.templateSnapshot?.steps?.single()?.aiOperations,
        )
    }

    @Test
    fun `manifest and canonical entries record portable UTC metadata and integrity hashes`() {
        val project = manualProject()
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes)
        val manifest = Json.parseToJsonElement(entries.getValue("manifest.json").decodeToString()).jsonObject
        val projectJson = Json.parseToJsonElement(entries.getValue("project.json").decodeToString()).jsonObject

        assertEquals("evidrilo.project-archive-manifest", manifest.getValue("schema").jsonPrimitive.content)
        assertEquals("7", manifest.getValue("version").jsonPrimitive.content)
        assertEquals(project.id, manifest.getValue("projectId").jsonPrimitive.content)
        assertTrue(manifest.getValue("createdAtUtc").jsonPrimitive.content.endsWith("Z"))
        assertTrue(manifest.getValue("updatedAtUtc").jsonPrimitive.content.endsWith("Z"))
        assertEquals("evidrilo.student-project", projectJson.getValue("schema").jsonPrimitive.content)
        assertEquals("7", projectJson.getValue("version").jsonPrimitive.content)
        assertTrue("createdAtUtc" in projectJson.getValue("project").jsonObject)
        assertTrue("updatedAtUtc" in projectJson.getValue("project").jsonObject)
        val manifestEntries = manifest.getValue("entries").jsonArray
        assertTrue(manifestEntries.isNotEmpty())
        val entryPaths = manifestEntries.map { it.jsonObject.getValue("path").jsonPrimitive.content }
        assertEquals(entryPaths.sorted(), entryPaths)
    }

    @Test
    fun `archive preserves selected revision snapshots as individual entries`() {
        val base = manualProject()
        val project = StudentProjectDraftRules.initializeRevisionHistory(base)
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )

        val entries = unzip(encoded.bytes)
        assertTrue("revisions/revision-0001.json" in entries)
        assertEquals(
            project,
            assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(encoded.bytes)).project,
        )
    }

    @Test
    fun `archive round trips a local starter without relabeling it as reviewed`() {
        val template = ProjectStarterTemplateCatalog.forFamily(ProjectTemplateFamily.QUALITATIVE_INTERVIEW_FIELD_STUDY)
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.create("11111111-1111-4111-8111-111111111111", template, template.title, 200),
        ).draft

        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )
        val restored = assertIs<StudentProjectArchiveReadResult.Preview>(
            StudentProjectArchiveCodec.preview(encoded.bytes),
        ).project

        assertEquals(project, restored)
        assertEquals(ProjectTemplatePublication.BUILT_IN_STARTER, restored.templateSnapshot?.publication)
    }

    @Test
    fun `archive roundtrips claims and review status in project and revision payloads`() {
        val claims = listOf(
            StudentProjectClaimRecord(
                id = "claim-primary",
                statement = "The bounded primary claim.",
                scopeNote = "For the selected sample.",
                limitationsNote = "The sample is small.",
                reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
            ),
            StudentProjectClaimRecord(
                id = "claim-secondary",
                statement = "A second claim.",
                reviewStatus = StudentProjectClaimReviewStatus.NEEDS_REVISION,
            ),
        )
        val project = StudentProjectDraftRules.initializeRevisionHistory(manualProject().copy(
            claims = claims,
            deadlineDate = "2026-10-15",
        ))
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes)
        val projectPayload = Json.parseToJsonElement(entries.getValue("project.json").decodeToString())
            .jsonObject.getValue("project").jsonObject
        val revisionPayload = Json.parseToJsonElement(entries.getValue("revisions/revision-0001.json").decodeToString())
            .jsonObject

        assertEquals(2, projectPayload.getValue("claims").jsonArray.size)
        assertEquals("2026-10-15", projectPayload.getValue("deadlineDate").jsonPrimitive.content)
        assertEquals("2026-10-15", revisionPayload.getValue("deadlineDate").jsonPrimitive.content)
        assertEquals(
            "ready_for_review",
            projectPayload.getValue("claims").jsonArray.first().jsonObject.getValue("reviewStatus").jsonPrimitive.content,
        )
        assertEquals(
            "needs_revision",
            revisionPayload.getValue("claims").jsonArray.last().jsonObject.getValue("reviewStatus").jsonPrimitive.content,
        )
        assertEquals(
            project,
            assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(encoded.bytes)).project,
        )
    }

    @Test
    fun `archive version five remains readable after current archive advances`() {
        val project = StudentProjectDraftRules.initializeRevisionHistory(manualProject())
        val legacy = legacyArchive(project, "5")

        val restored = assertIs<StudentProjectArchiveReadResult.Preview>(
            StudentProjectArchiveCodec.preview(legacy),
        ).project

        assertEquals(project, restored)
    }

    @Test
    fun `archive roundtrips limitation action links in project and immutable revision payloads`() {
        val finding = StudentProjectFindingRecord("finding-a", statement = "A reported pattern")
        val claim = StudentProjectClaimRecord("claim-a", statement = "A bounded claim")
        val limitation = StudentProjectLimitationActionRecord(
            id = "limit-a",
            boundary = "One population is represented.",
            reason = "The other source studies a different group.",
            nextAction = "Compare findings by population.",
            affectedFindingIds = setOf(finding.id),
            affectedClaimIds = setOf(claim.id),
        )
        val project = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject().copy(
                findings = listOf(finding),
                claims = listOf(claim),
                limitationActions = listOf(limitation),
            ),
        )
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes)
        val current = Json.parseToJsonElement(entries.getValue("project.json").decodeToString())
            .jsonObject.getValue("project").jsonObject
        val revision = Json.parseToJsonElement(entries.getValue("revisions/revision-0001.json").decodeToString())
            .jsonObject

        assertEquals("limit-a", current.getValue("limitationActions").jsonArray.single().jsonObject
            .getValue("id").jsonPrimitive.content)
        assertEquals("finding-a", revision.getValue("limitationActions").jsonArray.single().jsonObject
            .getValue("affectedFindingIds").jsonArray.single().jsonPrimitive.content)
        assertEquals("claim-a", revision.getValue("limitationActions").jsonArray.single().jsonObject
            .getValue("affectedClaimIds").jsonArray.single().jsonPrimitive.content)
        assertEquals(project, assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(encoded.bytes)).project)
    }

    @Test
    fun `archive v1 without attachment metadata remains readable after v4 upgrade`() {
        val project = manualProject()
        val legacyArchive = legacyV1Archive(project)

        assertEquals(
            project,
            assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(legacyArchive)).project,
        )
    }

    @Test
    fun `archive v1 migration restores the legacy primary claim in project and revision`() {
        val legacyProject = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject().copy(
                fieldValues = mapOf(ManualLiteratureSynthesisFields.CLAIM to "Claim from an older archive."),
            ),
        )
        val expected = StudentProjectClaimRecord(
            id = StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID,
            statement = "Claim from an older archive.",
        )

        val restored = assertIs<StudentProjectArchiveReadResult.Preview>(
            StudentProjectArchiveCodec.preview(legacyV1Archive(legacyProject)),
        ).project

        assertEquals(listOf(expected), restored.claims)
        assertEquals(listOf(expected), restored.revisionSnapshots.single().claims)
    }

    @Test
    fun `archive v2 migration restores the legacy primary claim in project and revision`() {
        val legacyProject = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject().copy(
                fieldValues = mapOf(ManualLiteratureSynthesisFields.CLAIM to "Claim from a v2 archive."),
            ),
        )
        val expected = StudentProjectClaimRecord(
            id = StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID,
            statement = "Claim from a v2 archive.",
        )

        val restored = assertIs<StudentProjectArchiveReadResult.Preview>(
            StudentProjectArchiveCodec.preview(legacyV2Archive(legacyProject)),
        ).project

        assertEquals(listOf(expected), restored.claims)
        assertEquals(listOf(expected), restored.revisionSnapshots.single().claims)
    }

    @Test
    fun `archive v3 remains readable and gains an empty limitation action list`() {
        val legacy = StudentProjectDraftRules.initializeRevisionHistory(
            manualProject().copy(claims = listOf(StudentProjectClaimRecord("claim-v3", statement = "Legacy claim"))),
        )

        val restored = assertIs<StudentProjectArchiveReadResult.Preview>(
            StudentProjectArchiveCodec.preview(legacyV3Archive(legacy)),
        ).project

        assertEquals(legacy.claims, restored.claims)
        assertEquals(emptyList(), restored.limitationActions)
        assertEquals(emptyList(), restored.revisionSnapshots.single().limitationActions)
    }

    @Test
    fun `import rejects path traversal before any archive content can be accepted`() {
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(manualProject(), appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes).toMutableMap()
        entries["../outside.json"] = "{}".encodeToByteArray()

        val rejected = assertIs<StudentProjectArchiveReadResult.Rejected>(
            StudentProjectArchiveCodec.preview(zip(entries)),
        )
        assertEquals("UNSAFE_PROJECT_ARCHIVE_ENTRY", rejected.code)
    }

    @Test
    fun `import rejects a modified payload whose manifest checksum no longer matches`() {
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(manualProject(), appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes).toMutableMap()
        entries["project.json"] = entries.getValue("project.json").copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }

        val rejected = assertIs<StudentProjectArchiveReadResult.Rejected>(
            StudentProjectArchiveCodec.preview(zip(entries)),
        )
        assertEquals("PROJECT_ARCHIVE_INTEGRITY_MISMATCH", rejected.code)
    }

    @Test
    fun `attachment archive roundtrips metadata and streams one file through the store`() {
        val source = pdfBytes()
        val reference = pdfReference(source)
        val project = manualProject().copy(attachments = listOf(reference))
        val sourceStore = MemoryAttachmentStore(mapOf(reference.id to source))
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev", attachmentStore = sourceStore),
        )

        assertTrue(sourceStore.reads.maxReadSize <= 8 * 1024)
        val unzipped = unzip(encoded.bytes)
        assertTrue("attachments/${reference.id}.pdf" in unzipped)
        assertEquals(source.toList(), unzipped.getValue("attachments/${reference.id}.pdf").toList())
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(encoded.bytes))
        assertEquals(listOf(reference), preview.project.attachments)
        assertEquals(reference.sha256, preview.manifest.attachments.single().sha256)
        assertEquals("source notes.pdf", preview.manifest.attachments.single().fileName)
    }

    @Test
    fun `attachment preview is read only and accepted bytes can be staged before project write`() {
        val source = pdfBytes()
        val reference = pdfReference(source)
        val project = manualProject().copy(attachments = listOf(reference))
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(
                project,
                appVersion = "1.0.0-dev",
                attachmentStore = MemoryAttachmentStore(mapOf(reference.id to source)),
            ),
        )
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(encoded.bytes))
        val destination = MemoryAttachmentStore()

        assertTrue(destination.staged.isEmpty())
        val staged = assertIs<StudentProjectArchiveStagingResult.Staged>(
            StudentProjectArchiveCodec.stageAttachments(encoded.bytes, preview, destination),
        )
        assertTrue(destination.staged.isEmpty())
        assertEquals(LocalStorageWriteResult.SAVED, staged.session.publish())
        assertEquals(source.toList(), destination.staged.getValue(reference.id).toList())
        assertEquals(LocalStorageWriteResult.SAVED, staged.session.complete())
    }

    @Test
    fun `attachment staging rejects bytes changed after preview`() {
        val source = pdfBytes()
        val reference = pdfReference(source)
        val project = manualProject().copy(attachments = listOf(reference))
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(
                project,
                appVersion = "1.0.0-dev",
                attachmentStore = MemoryAttachmentStore(mapOf(reference.id to source)),
            ),
        )
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(encoded.bytes))
        val changed = encoded.bytes.copyOf().also { it[it.lastIndex / 2] = (it[it.lastIndex / 2].toInt() xor 1).toByte() }
        val destination = MemoryAttachmentStore()

        assertIs<StudentProjectArchiveStagingResult.Rejected>(
            StudentProjectArchiveCodec.stageAttachments(changed, preview, destination),
        )
        assertTrue(destination.staged.isEmpty())
        assertEquals(0, destination.rollbacks)
    }

    @Test
    fun `preview propagates cancellation instead of reporting an invalid archive`() {
        val cancellation = assertFailsWith<CancellationException> {
            StudentProjectArchiveCodec.preview(byteArrayOf(1, 2, 3)) {
                throw CancellationException("cancel preview")
            }
        }

        assertEquals("cancel preview", cancellation.message)
    }

    @Test
    fun `cancelled attachment staging rolls back private files and propagates cancellation`() {
        val source = pdfBytes()
        val reference = pdfReference(source)
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(
                manualProject().copy(attachments = listOf(reference)),
                appVersion = "1.0.0-dev",
                attachmentStore = MemoryAttachmentStore(mapOf(reference.id to source)),
            ),
        )
        val preview = assertIs<StudentProjectArchiveReadResult.Preview>(StudentProjectArchiveCodec.preview(encoded.bytes))
        val destination = MemoryAttachmentStore()

        assertFailsWith<CancellationException> {
            StudentProjectArchiveCodec.stageAttachments(
                bytes = encoded.bytes,
                preview = preview,
                attachmentStore = destination,
                ensureActive = {
                    if (destination.importSessions > 0) throw CancellationException("cancel staging")
                },
            )
        }

        assertEquals(1, destination.rollbacks)
        assertTrue(destination.staged.isEmpty())
    }

    @Test
    fun `attachment MIME extension mismatch is rejected before archive encoding`() {
        val bytes = pdfBytes()
        val badReference = pdfReference(bytes).copy(mimeType = "image/png")
        val result = StudentProjectArchiveCodec.encode(
            manualProject().copy(attachments = listOf(badReference)),
            appVersion = "1.0.0-dev",
            attachmentStore = MemoryAttachmentStore(mapOf(badReference.id to bytes)),
        )

        assertEquals("PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH", assertIs<StudentProjectArchiveEncodingResult.Rejected>(result).code)
    }

    @Test
    fun `selected attachment reference accepts only supported names and matching content`() {
        val bytes = pdfBytes()

        val accepted = assertIs<StudentProjectAttachmentReferenceResult.Ready>(
            StudentProjectArchiveCodec.prepareSelectedAttachmentReference(
                id = "attachment-1",
                fileName = "source notes.PDF",
                bytes = bytes,
            ),
        ).reference

        assertEquals("source notes.PDF", accepted.fileName)
        assertEquals("application/pdf", accepted.mimeType)
        assertEquals(bytes.size.toLong(), accepted.sizeBytes)
        assertEquals(pdfReference(bytes).sha256, accepted.sha256)
        assertEquals(
            "PROJECT_ATTACHMENT_CONTENT_INVALID",
            assertIs<StudentProjectAttachmentReferenceResult.Rejected>(
                StudentProjectArchiveCodec.prepareSelectedAttachmentReference(
                    id = "attachment-2",
                    fileName = "mislabelled.pdf",
                    bytes = "not a PDF".encodeToByteArray(),
                ),
            ).code,
        )
    }

    @Test
    fun `selected attachment rejects unsupported extension and unsafe display name`() {
        val bytes = pdfBytes()

        assertEquals(
            "PROJECT_ATTACHMENT_TYPE_UNSUPPORTED",
            assertIs<StudentProjectAttachmentReferenceResult.Rejected>(
                StudentProjectArchiveCodec.prepareSelectedAttachmentReference("attachment-1", "source.exe", bytes),
            ).code,
        )
        assertEquals(
            "PROJECT_ATTACHMENT_REFERENCE_INVALID",
            assertIs<StudentProjectAttachmentReferenceResult.Rejected>(
                StudentProjectArchiveCodec.prepareSelectedAttachmentReference("attachment-1", "../source.pdf", bytes),
            ).code,
        )
    }

    @Test
    fun `attachment content signature must agree with its allowed extension`() {
        val bytes = "not a PDF".encodeToByteArray()
        val reference = pdfReference(bytes)
        val result = StudentProjectArchiveCodec.encode(
            manualProject().copy(attachments = listOf(reference)),
            appVersion = "1.0.0-dev",
            attachmentStore = MemoryAttachmentStore(mapOf(reference.id to bytes)),
        )

        assertEquals("PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH", assertIs<StudentProjectArchiveEncodingResult.Rejected>(result).code)
    }

    @Test
    fun `docx attachment is structurally checked and macro-marked packages fail closed`() {
        val safeDocx = docxBytes("<Types></Types>")
        val docxPreflight = StudentProjectArchiveZipPreflight.inspect(
            bytes = safeDocx,
            maxEntries = 100,
            maxExpandedBytes = 20L * 1024 * 1024,
            maxCompressionRatio = 100,
            entryLimit = { path -> if (path.endsWith('/')) 1 else 2 * 1024 * 1024 },
            allowDirectories = true,
        )
        assertIs<StudentProjectZipPreflightResult.Valid>(
            docxPreflight,
            (docxPreflight as? StudentProjectZipPreflightResult.Rejected)?.code,
        )
        val safeReference = docxReference(safeDocx)
        val safeArchive = StudentProjectArchiveCodec.encode(
            manualProject().copy(attachments = listOf(safeReference)),
            appVersion = "1.0.0-dev",
            attachmentStore = MemoryAttachmentStore(mapOf(safeReference.id to safeDocx)),
        )
        assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            safeArchive,
            (safeArchive as? StudentProjectArchiveEncodingResult.Rejected)?.code,
        )

        val macroDocx = docxBytes("<Types>macroEnabled</Types>")
        val macroReference = docxReference(macroDocx)
        val rejected = StudentProjectArchiveCodec.encode(
            manualProject().copy(attachments = listOf(macroReference)),
            appVersion = "1.0.0-dev",
            attachmentStore = MemoryAttachmentStore(mapOf(macroReference.id to macroDocx)),
        )
        assertEquals("PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH", assertIs<StudentProjectArchiveEncodingResult.Rejected>(rejected).code)
    }

    @Test
    fun `import rejects encrypted zip entries before reading their content`() {
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(manualProject(), appVersion = "1.0.0-dev"),
        )
        val encrypted = mutateCentralEntry(encoded.bytes, "project.json") { bytes, central, local ->
            bytes.writeU16(central + 8, bytes.readU16(central + 8) or 1)
            bytes.writeU16(local + 6, bytes.readU16(local + 6) or 1)
        }

        assertEquals(
            "PROJECT_ARCHIVE_ENCRYPTED",
            assertIs<StudentProjectArchiveReadResult.Rejected>(StudentProjectArchiveCodec.preview(encrypted)).code,
        )
    }

    @Test
    fun `import rejects unix symbolic links`() {
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(manualProject(), appVersion = "1.0.0-dev"),
        )
        val symlink = mutateCentralEntry(encoded.bytes, "project.json") { bytes, central, _ ->
            bytes.writeU16(central + 4, (3 shl 8) or 20)
            bytes.writeU32(central + 38, ((0xa000 or 0x1ff).toLong()) shl 16)
        }

        assertEquals(
            "PROJECT_ARCHIVE_FILE_TYPE_UNSUPPORTED",
            assertIs<StudentProjectArchiveReadResult.Rejected>(StudentProjectArchiveCodec.preview(symlink)).code,
        )
    }

    @Test
    fun `import rejects case-colliding entry names`() {
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(manualProject(), appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes).toMutableMap()
        entries["PROJECT.JSON"] = entries.getValue("project.json")

        assertEquals(
            "PROJECT_ARCHIVE_DUPLICATE_ENTRY",
            assertIs<StudentProjectArchiveReadResult.Rejected>(StudentProjectArchiveCodec.preview(zip(entries))).code,
        )
    }

    @Test
    fun `import rejects a high-ratio archive expansion before allocating its payload`() {
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(manualProject(), appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes).toMutableMap()
        entries["revisions/revision-0002.json"] = ByteArray(100_000) { 'x'.code.toByte() }

        assertEquals(
            "PROJECT_ARCHIVE_COMPRESSION_RATIO_EXCEEDED",
            assertIs<StudentProjectArchiveReadResult.Rejected>(StudentProjectArchiveCodec.preview(zip(entries))).code,
        )
    }

    private fun manualProject(): StudentProjectDraft = assertIs<StudentProjectDraftCreateResult.Created>(
        StudentProjectDraftRules.createManual(
            id = "f3cd82c5-b6ba-4f25-80e3-ae743ce4071b",
            title = "Literature synthesis project",
            createdAtEpochMillis = 1_758_960_000_000,
        ),
    ).draft

    private fun pdfBytes(): ByteArray = "%PDF-1.4\nEvidence note\n%%EOF\n".encodeToByteArray()

    private fun pdfReference(bytes: ByteArray) = StudentProjectAttachmentRef(
        id = "source_01",
        fileName = "source notes.pdf",
        mimeType = "application/pdf",
        sizeBytes = bytes.size.toLong(),
        sha256 = sha256(bytes).toHex(),
    )

    private fun docxBytes(contentTypes: String): ByteArray = zip(mapOf(
        "[Content_Types].xml" to contentTypes.encodeToByteArray(),
        "_rels/.rels" to "<Relationships/>".encodeToByteArray(),
        "word/document.xml" to "<document/>".encodeToByteArray(),
        "_rels/" to byteArrayOf(),
        "word/" to byteArrayOf(),
    ))

    private fun docxReference(bytes: ByteArray) = StudentProjectAttachmentRef(
        id = "word_01",
        fileName = "study.docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        sizeBytes = bytes.size.toLong(),
        sha256 = sha256(bytes).toLowerHex(),
    )

    private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private class MemoryAttachmentStore(
        private val existing: Map<String, ByteArray> = emptyMap(),
    ) : StudentProjectAttachmentStore {
        val staged = mutableMapOf<String, ByteArray>()
        val reads = ReadStats()
        var rollbacks = 0
        var importSessions = 0

        override fun openRead(projectId: String, attachmentId: String): StudentProjectAttachmentReadResult =
            existing[attachmentId]?.let { StudentProjectAttachmentReadResult.Opened(MemoryReadHandle(it, reads)) }
                ?: StudentProjectAttachmentReadResult.Missing

        override fun beginImport(projectId: String): StudentProjectAttachmentImportResult {
            importSessions += 1
            return StudentProjectAttachmentImportResult.Started(object : StudentProjectAttachmentImportSession {
                private val pending = mutableMapOf<String, ByteArrayOutputStream>()

                override fun openWriter(reference: StudentProjectAttachmentRef): StudentProjectAttachmentWriterResult {
                    val output = ByteArrayOutputStream()
                    pending[reference.id] = output
                    return StudentProjectAttachmentWriterResult.Opened(object : StudentProjectAttachmentWriteHandle {
                        override fun write(buffer: ByteArray, offset: Int, length: Int) = output.write(buffer, offset, length)
                        override fun finish(): LocalStorageWriteResult = LocalStorageWriteResult.SAVED
                        override fun abort() { pending.remove(reference.id) }
                    })
                }

                override fun publish(): LocalStorageWriteResult {
                    pending.forEach { (id, bytes) -> staged[id] = bytes.toByteArray() }
                    return LocalStorageWriteResult.SAVED
                }

                override fun complete(): LocalStorageWriteResult = LocalStorageWriteResult.SAVED
                override fun rollback(): LocalStorageWriteResult {
                    rollbacks += 1
                    pending.clear()
                    staged.clear()
                    return LocalStorageWriteResult.SAVED
                }
            })
        }

        override fun prepareProjectDeletion(
            projectId: String,
            hasAttachments: Boolean,
        ): LocalStorageWriteResult = LocalStorageWriteResult.SAVED

        override fun cancelProjectDeletion(projectId: String): LocalStorageWriteResult =
            LocalStorageWriteResult.CLEARED

        override fun completeProjectDeletion(projectId: String): LocalStorageWriteResult =
            LocalStorageWriteResult.CLEARED

        override fun recoverPendingProjectDeletions(existingProjectIds: Set<String>): LocalStorageWriteResult =
            LocalStorageWriteResult.CLEARED

        override fun recoverPendingAttachmentDeletions(
            existingAttachmentIdsByProject: Map<String, Set<String>>,
        ): LocalStorageWriteResult = LocalStorageWriteResult.CLEARED
    }

    private class ReadStats(var maxReadSize: Int = 0)

    private class MemoryReadHandle(
        private val bytes: ByteArray,
        private val stats: ReadStats,
    ) : StudentProjectAttachmentReadHandle {
        private var offset = 0
        override val sizeBytes: Long get() = bytes.size.toLong()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            stats.maxReadSize = maxOf(stats.maxReadSize, length)
            if (this.offset >= bytes.size) return -1
            val count = minOf(length, bytes.size - this.offset)
            bytes.copyInto(buffer, offset, this.offset, this.offset + count)
            this.offset += count
            return count
        }
        override fun close() = Unit
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                put(entry.name, stream.readBytes())
            }
        }
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { stream ->
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                if (name.endsWith('/')) {
                    entry.method = ZipConstants.STORED
                    entry.size = 0
                    entry.compressedSize = 0
                    entry.crc = StudentProjectArchiveZipPreflight.crc32(bytes)
                }
                stream.putNextEntry(entry)
                stream.write(bytes)
                stream.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun legacyV1Archive(project: StudentProjectDraft): ByteArray = legacyArchive(project, "1")

    private fun legacyV2Archive(project: StudentProjectDraft): ByteArray = legacyArchive(project, "2")

    private fun legacyV3Archive(project: StudentProjectDraft): ByteArray = legacyArchive(project, "3")

    private fun legacyArchive(project: StudentProjectDraft, version: String): ByteArray {
        val encoded = assertIs<StudentProjectArchiveEncodingResult.Encoded>(
            StudentProjectArchiveCodec.encode(project, appVersion = "1.0.0-dev"),
        )
        val entries = unzip(encoded.bytes).toMutableMap()
        val projectDocument = Json.parseToJsonElement(entries.getValue("project.json").decodeToString()).jsonObject
        val archivedProject = projectDocument.getValue("project").jsonObject.toMutableMap().apply {
            if (version == "1" || version == "2") remove("claims")
            if (version in setOf("1", "2", "3")) remove("limitationActions")
            if (version == "1") remove("attachments")
            remove("deadlineDate")
            val revisions = getValue("revisionSnapshots").jsonArray
            put("revisionSnapshots", JsonArray(revisions.map { revision ->
                JsonObject(revision.jsonObject.toMutableMap().apply {
                    if (version == "1" || version == "2") remove("claims")
                    if (version in setOf("1", "2", "3")) remove("limitationActions")
                    if (version == "1") remove("attachments")
                    remove("deadlineDate")
                })
            }))
        }
        entries["project.json"] = JsonObject(mapOf(
            "schema" to JsonPrimitive("evidrilo.student-project"),
            "version" to JsonPrimitive(version),
            "project" to JsonObject(archivedProject),
        )).toString().encodeToByteArray()
        entries.keys.filter { it.startsWith("revisions/") }.forEach { path ->
            val revision = Json.parseToJsonElement(entries.getValue(path).decodeToString()).jsonObject.toMutableMap()
            if (version == "1" || version == "2") revision.remove("claims")
            if (version in setOf("1", "2", "3")) revision.remove("limitationActions")
            if (version == "1") revision.remove("attachments")
            revision.remove("deadlineDate")
            entries[path] = JsonObject(revision).toString().encodeToByteArray()
        }

        val manifest = Json.parseToJsonElement(entries.getValue("manifest.json").decodeToString()).jsonObject.toMutableMap()
        manifest["version"] = JsonPrimitive(version)
        manifest["entries"] = JsonArray(manifest.getValue("entries").jsonArray.map { element ->
            val item = element.jsonObject.toMutableMap()
            val path = item.getValue("path").jsonPrimitive.content
            if (path in entries) {
                val content = entries.getValue(path)
                item["sizeBytes"] = JsonPrimitive(content.size)
                item["sha256"] = JsonPrimitive(sha256(content).toLowerHex())
            }
            JsonObject(item)
        })
        entries["manifest.json"] = JsonObject(manifest).toString().encodeToByteArray()
        return zip(entries)
    }

    private fun mutateCentralEntry(
        source: ByteArray,
        name: String,
        update: (bytes: ByteArray, centralOffset: Int, localOffset: Int) -> Unit,
    ): ByteArray {
        val bytes = source.copyOf()
        val eocd = (bytes.size - 22 downTo (bytes.size - 22 - 65_535).coerceAtLeast(0))
            .first { bytes.readU32(it) == 0x06054b50L }
        var offset = bytes.readU32(eocd + 16).toInt()
        repeat(bytes.readU16(eocd + 10)) {
            val nameLength = bytes.readU16(offset + 28)
            val extraLength = bytes.readU16(offset + 30)
            val commentLength = bytes.readU16(offset + 32)
            val entryName = bytes.copyOfRange(offset + 46, offset + 46 + nameLength).decodeToString()
            val localOffset = bytes.readU32(offset + 42).toInt()
            if (entryName == name) {
                update(bytes, offset, localOffset)
                return bytes
            }
            offset += 46 + nameLength + extraLength + commentLength
        }
        error("ZIP entry not found: $name")
    }

    private fun ByteArray.readU16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.readU32(offset: Int): Long =
        (this[offset].toLong() and 0xff) or
            ((this[offset + 1].toLong() and 0xff) shl 8) or
            ((this[offset + 2].toLong() and 0xff) shl 16) or
            ((this[offset + 3].toLong() and 0xff) shl 24)

    private fun ByteArray.writeU16(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.writeU32(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
