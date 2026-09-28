package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectRevisionSnapshot
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import dev.nextgen.mobile.storage.ProjectDraftEncodingResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportSession
import dev.nextgen.mobile.storage.StudentProjectAttachmentReadResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentStore
import dev.nextgen.mobile.storage.StudentProjectAttachmentWriterResult
import dev.nextgen.mobile.storage.StudentProjectDraftStoreCodec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import no.synth.kmpzip.io.ByteArrayInputStream
import no.synth.kmpzip.io.ByteArrayOutputStream
import no.synth.kmpzip.io.OutputStream
import no.synth.kmpzip.zip.ZipConstants
import no.synth.kmpzip.zip.ZipEntry
import no.synth.kmpzip.zip.ZipInputStream
import no.synth.kmpzip.zip.ZipOutputStream

sealed interface StudentProjectArchiveEncodingResult {
    data class Encoded(val fileName: String, val mimeType: String, val bytes: ByteArray) : StudentProjectArchiveEncodingResult
    data class Rejected(val code: String) : StudentProjectArchiveEncodingResult
}

data class StudentProjectArchiveManifest(
    val projectId: String,
    val appVersion: String,
    val templateId: String?,
    val templateVersion: Int?,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val entryPaths: List<String>,
    val exportedAtUtc: String,
    val attachments: List<StudentProjectArchiveAttachmentEntry> = emptyList(),
)

data class StudentProjectArchiveAttachmentEntry(
    val id: String,
    val path: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

sealed interface StudentProjectArchiveReadResult {
    data class Preview(
        val project: StudentProjectDraft,
        val manifest: StudentProjectArchiveManifest,
        val warnings: List<String>,
        internal val archiveSha256: String,
    ) : StudentProjectArchiveReadResult

    data class Rejected(val code: String) : StudentProjectArchiveReadResult
}

sealed interface StudentProjectArchiveStagingResult {
    data object NoAttachments : StudentProjectArchiveStagingResult
    data class Staged(val session: StudentProjectAttachmentImportSession) : StudentProjectArchiveStagingResult
    data class Rejected(val code: String) : StudentProjectArchiveStagingResult
}

sealed interface StudentProjectAttachmentReferenceResult {
    data class Ready(val reference: StudentProjectAttachmentRef) : StudentProjectAttachmentReferenceResult
    data class Rejected(val code: String) : StudentProjectAttachmentReferenceResult
}

private class ProjectArchiveValidationFailure(val code: String) : Exception()

/**
 * Versioned `.evproj` codec. Drafts retain attachment metadata only. Binary
 * attachments move through a separate, bounded storage port.
 */
object StudentProjectArchiveCodec {
    private const val MANIFEST_SCHEMA = "evidrilo.project-archive-manifest"
    private const val PROJECT_SCHEMA = "evidrilo.student-project"
    private const val ARCHIVE_VERSION = "5"
    private const val PREVIOUS_ARCHIVE_VERSION = "4"
    private const val EARLIER_ARCHIVE_VERSION = "3"
    private const val OLDER_ARCHIVE_VERSION = "2"
    private const val LEGACY_ARCHIVE_VERSION = "1"
    private const val PROJECT_PAYLOAD_VERSION = "9"
    private const val PREVIOUS_PROJECT_PAYLOAD_VERSION = "8"
    private const val EARLIER_PROJECT_PAYLOAD_VERSION = "7"
    private const val OLDER_PROJECT_PAYLOAD_VERSION = "6"
    private const val LEGACY_PROJECT_PAYLOAD_VERSION = "5"
    private const val MIME_TYPE = "application/vnd.evidrilo.project+zip"
    private const val JSON_MIME_TYPE = "application/json"
    private const val MAX_COMPRESSED_BYTES = 50 * 1024 * 1024
    private const val MAX_EXPANDED_BYTES = 200 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 512 * 1024
    private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
    private const val MAX_ENTRIES = 100
    private const val MAX_MANIFEST_BYTES = 64 * 1024
    private const val MAX_TEMPLATE_BYTES = 128 * 1024
    private const val PROJECT_PATH = "project.json"
    private const val TEMPLATE_PATH = "template-snapshot.json"
    private const val MANIFEST_PATH = "manifest.json"
    private val json = Json { isLenient = false }
    private val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private val appVersionPattern = Regex("[A-Za-z0-9][A-Za-z0-9.+_-]{0,63}")
    private val revisionPathPattern = Regex("revisions/revision-([0-9]{4})\\.json")
    private val attachmentPathPattern = Regex("attachments/([A-Za-z0-9_-]{1,96})\\.(pdf|docx|csv|txt|md|png|jpe?g)", RegexOption.IGNORE_CASE)
    private val manifestKeys = setOf(
        "schema", "version", "appVersion", "exporterVersion", "projectId", "templateId", "templateVersion",
        "createdAtUtc", "updatedAtUtc", "exportedAtUtc", "revisionRange", "attachments", "entries",
    )
    private val manifestEntryKeys = setOf("path", "sizeBytes", "mimeType", "sha256")
    private val manifestAttachmentKeys = setOf("id", "path", "fileName", "mimeType", "sizeBytes", "sha256")
    private val revisionRangeKeys = setOf("firstRevision", "lastRevision")
    private val projectFileKeys = setOf("schema", "version", "project")
    private val entryPathPattern = Regex("(?:manifest|project|template-snapshot)\\.json|revisions/revision-[0-9]{4}\\.json")

    /** Validates selected file metadata and content before bytes enter private project storage. */
    fun prepareSelectedAttachmentReference(
        id: String,
        fileName: String,
        bytes: ByteArray,
    ): StudentProjectAttachmentReferenceResult {
        if (bytes.size !in 1..MAX_ATTACHMENT_BYTES) {
            return StudentProjectAttachmentReferenceResult.Rejected("PROJECT_ATTACHMENT_SIZE_INVALID")
        }
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val format = attachmentFormat(extension)
            ?: return StudentProjectAttachmentReferenceResult.Rejected("PROJECT_ATTACHMENT_TYPE_UNSUPPORTED")
        val reference = StudentProjectAttachmentRef(
            id = id,
            fileName = fileName,
            mimeType = format.mimeType,
            sizeBytes = bytes.size.toLong(),
            sha256 = bytes.sha256Hex(),
        )
        if (StudentProjectAttachmentRules.validate(reference).isNotEmpty()) {
            return StudentProjectAttachmentReferenceResult.Rejected("PROJECT_ATTACHMENT_REFERENCE_INVALID")
        }
        val scanner = AttachmentContentScanner(
            format = format,
            docxBytes = bytes.takeIf { format.kind == AttachmentFormat.Kind.DOCX },
        )
        scanner.update(bytes, 0, bytes.size)
        if (scanner.finish() != null) {
            return StudentProjectAttachmentReferenceResult.Rejected("PROJECT_ATTACHMENT_CONTENT_INVALID")
        }
        return StudentProjectAttachmentReferenceResult.Ready(reference)
    }

    fun encode(
        project: StudentProjectDraft,
        appVersion: String,
        exportedAtUtc: String = Clock.System.now().toString(),
        attachmentStore: StudentProjectAttachmentStore? = null,
    ): StudentProjectArchiveEncodingResult {
        if (!uuidPattern.matches(project.id)) return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ID_NOT_UUID")
        if (!appVersionPattern.matches(appVersion)) return StudentProjectArchiveEncodingResult.Rejected("INVALID_APP_VERSION")
        if (!isCanonicalUtc(exportedAtUtc)) return StudentProjectArchiveEncodingResult.Rejected("INVALID_ARCHIVE_TIMESTAMP")
        StudentProjectDraftRules.validate(project).firstOrNull()?.let {
            return StudentProjectArchiveEncodingResult.Rejected(it)
        }

        val attachmentEntries = mutableListOf<StudentProjectArchiveAttachmentEntry>()
        val attachmentPaths = mutableSetOf<String>()
        val attachmentIds = mutableSetOf<String>()
        project.attachments.forEach { reference ->
            val path = attachmentPath(reference)
                ?: return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH")
            if (!attachmentIds.add(reference.id.lowercase()) || !attachmentPaths.add(path.lowercase())) {
                return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_COLLISION")
            }
            attachmentEntries += StudentProjectArchiveAttachmentEntry(
                id = reference.id,
                path = path,
                fileName = reference.fileName,
                mimeType = reference.mimeType,
                sizeBytes = reference.sizeBytes,
                sha256 = reference.sha256,
            )
        }
        if (attachmentEntries.isNotEmpty() && attachmentStore == null) {
            return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_STORE_UNAVAILABLE")
        }

        val localDraft = when (val encoded = StudentProjectDraftStoreCodec.encode(listOf(project))) {
            is ProjectDraftEncodingResult.Invalid -> return StudentProjectArchiveEncodingResult.Rejected(encoded.code)
            is ProjectDraftEncodingResult.Encoded -> runCatching {
                json.parseToJsonElement(encoded.value).jsonObject
                    .getValue("projects").jsonArray.single().jsonObject
            }.getOrElse { return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_ENCODE_FAILED") }
        }
        val archiveProject = localDraft.toArchiveProject()
        val projectElement = buildJsonObject {
            put("schema", PROJECT_SCHEMA)
            put("version", ARCHIVE_VERSION)
            put("project", archiveProject)
        }
        val templateElement = archiveProject["templateSnapshot"] ?: JsonNull
        val payloadEntries = linkedMapOf<String, ByteArray>()
        payloadEntries[PROJECT_PATH] = projectElement.toString().encodeToByteArray()
        payloadEntries[TEMPLATE_PATH] = templateElement.toString().encodeToByteArray()
        val snapshots = archiveProject["revisionSnapshots"]?.jsonArray ?: JsonArray(emptyList())
        snapshots.forEach { snapshotElement ->
            val snapshot = snapshotElement.jsonObject
            val revision = snapshot.requiredInt("revision")
            val entryName = revisionPath(revision)
                ?: return StudentProjectArchiveEncodingResult.Rejected("INVALID_PROJECT_REVISION")
            payloadEntries[entryName] = snapshot.toString().encodeToByteArray()
        }

        val metadataSize = payloadEntries.values.sumOf(ByteArray::size).toLong()
        val attachmentSize = attachmentEntries.sumOf(StudentProjectArchiveAttachmentEntry::sizeBytes)
        if (payloadEntries.size + attachmentEntries.size + 1 > MAX_ENTRIES ||
            metadataSize + attachmentSize > MAX_EXPANDED_BYTES
        ) {
            return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_TOO_LARGE")
        }
        if (payloadEntries.any { (name, bytes) -> bytes.size > entryLimit(name) }) {
            return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_ENTRY_TOO_LARGE")
        }

        val manifest = buildManifest(project, appVersion, exportedAtUtc, payloadEntries, attachmentEntries)
        val manifestBytes = manifest.toString().encodeToByteArray()
        if (manifestBytes.size > MAX_MANIFEST_BYTES) {
            return StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_MANIFEST_TOO_LARGE")
        }
        val allEntries = linkedMapOf(MANIFEST_PATH to manifestBytes).apply { putAll(payloadEntries) }
        val output = ByteArrayOutputStream()
        return try {
            ZipOutputStream(CappedArchiveOutputStream(output, MAX_COMPRESSED_BYTES)).use { zip ->
                allEntries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                attachmentEntries.forEach { entry ->
                    when (val opened = checkNotNull(attachmentStore).openRead(project.id, entry.id)) {
                        is StudentProjectAttachmentReadResult.Opened -> writeAttachmentToArchive(zip, entry, opened.handle)
                        StudentProjectAttachmentReadResult.Missing -> throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_MISSING")
                        StudentProjectAttachmentReadResult.Unavailable -> throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_STORE_UNAVAILABLE")
                        is StudentProjectAttachmentReadResult.Failed -> throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_STORE_FAILED")
                    }
                }
            }
            val bytes = output.toByteArray()
            if (bytes.size > MAX_COMPRESSED_BYTES) {
                StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_TOO_LARGE")
            } else {
                when (val checked = StudentProjectArchiveZipPreflight.inspect(
                    bytes = bytes,
                    maxEntries = MAX_ENTRIES,
                    maxExpandedBytes = MAX_EXPANDED_BYTES.toLong(),
                    maxCompressionRatio = 100,
                    entryLimit = ::entryLimit,
                )) {
                    is StudentProjectZipPreflightResult.Rejected -> return StudentProjectArchiveEncodingResult.Rejected(checked.code)
                    is StudentProjectZipPreflightResult.Valid -> Unit
                }
                StudentProjectArchiveEncodingResult.Encoded(
                    fileName = "${project.id}.evproj",
                    mimeType = MIME_TYPE,
                    bytes = bytes,
                )
            }
        } catch (failure: ProjectArchiveValidationFailure) {
            StudentProjectArchiveEncodingResult.Rejected(failure.code)
        } catch (_: Exception) {
            StudentProjectArchiveEncodingResult.Rejected("PROJECT_ARCHIVE_ENCODE_FAILED")
        }
    }

    /** Validates and previews a project archive without writing to a project store. */
    fun preview(
        bytes: ByteArray,
        ensureActive: () -> Unit = {},
    ): StudentProjectArchiveReadResult {
        ensureActive()
        if (bytes.isEmpty() || bytes.size > MAX_COMPRESSED_BYTES) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_SIZE_INVALID")
        }
        val preflight = when (val result = StudentProjectArchiveZipPreflight.inspect(
            bytes = bytes,
            maxEntries = MAX_ENTRIES,
            maxExpandedBytes = MAX_EXPANDED_BYTES.toLong(),
            maxCompressionRatio = 100,
            entryLimit = ::entryLimit,
            ensureActive = ensureActive,
        )) {
            is StudentProjectZipPreflightResult.Valid -> result.entries
            is StudentProjectZipPreflightResult.Rejected -> return StudentProjectArchiveReadResult.Rejected(result.code)
        }

        val entries = linkedMapOf<String, ByteArray>()
        val attachmentScans = linkedMapOf<String, StreamedAttachmentInfo>()
        val seenNames = mutableSetOf<String>()
        var expandedBytes = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var count = 0
                while (true) {
                    ensureActive()
                    val entry = zip.nextEntry ?: break
                    count += 1
                    if (count > MAX_ENTRIES) return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_TOO_MANY_ENTRIES")
                    if (entry.isDirectory || !isLexicallySafeEntryPath(entry.name)) {
                        return StudentProjectArchiveReadResult.Rejected("UNSAFE_PROJECT_ARCHIVE_ENTRY")
                    }
                    val normalizedName = entry.name.lowercase()
                    if (!seenNames.add(normalizedName)) {
                        return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_DUPLICATE_ENTRY")
                    }
                    if (!isSafeEntryPath(entry.name)) {
                        return StudentProjectArchiveReadResult.Rejected("UNSAFE_PROJECT_ARCHIVE_ENTRY")
                    }
                    val expected = preflight[entry.name]
                        ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_DIRECTORY_INVALID")
                    if (entry.method != expected.method || entry.size >= 0 && entry.size.toLong() != expected.expandedSize ||
                        entry.compressedSize >= 0 && entry.compressedSize != expected.compressedSize
                    ) {
                        return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_LOCAL_HEADER_MISMATCH")
                    }
                    if (entry.method != ZipConstants.STORED && entry.method != ZipConstants.DEFLATED) {
                        return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_COMPRESSION_UNSUPPORTED")
                    }
                    val limit = entryLimit(entry.name)
                    if (entry.size > limit || entry.size > MAX_EXPANDED_BYTES) {
                        return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_ENTRY_TOO_LARGE")
                    }
                    val format = attachmentFormatForPath(entry.name)
                    val attachment = format != null
                    val docxBytes = if (format?.extension == "docx") ByteArray(expected.expandedSize.toInt()) else null
                    val content = if (attachment) null else ByteArray(expected.expandedSize.toInt())
                    val scanner = format?.let { AttachmentContentScanner(it, docxBytes, ensureActive = ensureActive) }
                    val sha = StudentProjectSha256Accumulator()
                    val crc = StudentProjectCrc32Accumulator()
                    val buffer = ByteArray(8 * 1024)
                    var entryBytes = 0L
                    while (true) {
                        ensureActive()
                        val read = zip.read(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) continue
                        entryBytes += read
                        expandedBytes += read
                        if (entryBytes > limit || expandedBytes > MAX_EXPANDED_BYTES) {
                            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_EXPANSION_LIMIT")
                        }
                        sha.update(buffer, 0, read)
                        crc.update(buffer, 0, read)
                        if (scanner != null) {
                            scanner.update(buffer, 0, read)
                            if (docxBytes != null) buffer.copyInto(docxBytes, (entryBytes - read).toInt(), 0, read)
                        } else {
                            buffer.copyInto(checkNotNull(content), (entryBytes - read).toInt(), 0, read)
                        }
                    }
                    if (entry.size >= 0 && entry.size != entryBytes) {
                        return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_SIZE_MISMATCH")
                    }
                    if (entryBytes != expected.expandedSize || crc.value() != expected.crc32) {
                        return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_CRC_MISMATCH")
                    }
                    if (scanner != null) {
                        scanner.finish()?.let { return StudentProjectArchiveReadResult.Rejected(it) }
                        attachmentScans[entry.name] = StreamedAttachmentInfo(entryBytes, sha.digest().toLowerHex())
                    } else {
                        entries[entry.name] = checkNotNull(content)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_INVALID_ZIP")
        }
        if (entries.keys + attachmentScans.keys != preflight.keys) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_DIRECTORY_MISMATCH")
        }

        val manifestBytes = entries[MANIFEST_PATH]
            ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_MANIFEST_MISSING")
        val manifest = runCatching {
            json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject
        }.getOrElse { return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_MANIFEST_INVALID") }
        val manifestSummary = try {
            validateManifest(manifest, entries, attachmentScans)
        } catch (failure: ProjectArchiveValidationFailure) {
            return StudentProjectArchiveReadResult.Rejected(failure.code)
        } ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_MANIFEST_INVALID")
        val projectBytes = entries[PROJECT_PATH]
            ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_PROJECT_MISSING")
        val projectDocument = runCatching {
            json.parseToJsonElement(projectBytes.decodeToString()).jsonObject
        }.getOrElse { return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_PROJECT_INVALID") }
        val archiveVersion = projectDocument.optionalString("version")
        if (projectDocument.keys != projectFileKeys || projectDocument.requiredString("schema") != PROJECT_SCHEMA ||
            archiveVersion !in setOf(LEGACY_ARCHIVE_VERSION, OLDER_ARCHIVE_VERSION, EARLIER_ARCHIVE_VERSION, PREVIOUS_ARCHIVE_VERSION, ARCHIVE_VERSION) ||
            archiveVersion != manifest.requiredString("version")
        ) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_SCHEMA_UNSUPPORTED")
        }
        val archivedProject = projectDocument["project"] as? JsonObject
            ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_PROJECT_INVALID")
        val templateEntry = entries[TEMPLATE_PATH]
            ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_TEMPLATE_SNAPSHOT_MISSING")
        val templateSnapshot = runCatching { json.parseToJsonElement(templateEntry.decodeToString()) }
            .getOrElse { return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_TEMPLATE_SNAPSHOT_INVALID") }
        if (templateSnapshot != (archivedProject["templateSnapshot"] ?: JsonNull)) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_TEMPLATE_MISMATCH")
        }

        val restoredPayload = runCatching { archivedProject.toLocalDraft() }
            .getOrElse { return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_PROJECT_INVALID") }
        val localPayloadVersion = when (archiveVersion) {
            LEGACY_ARCHIVE_VERSION -> LEGACY_PROJECT_PAYLOAD_VERSION
            OLDER_ARCHIVE_VERSION -> OLDER_PROJECT_PAYLOAD_VERSION
            EARLIER_ARCHIVE_VERSION -> EARLIER_PROJECT_PAYLOAD_VERSION
            PREVIOUS_ARCHIVE_VERSION -> PREVIOUS_PROJECT_PAYLOAD_VERSION
            ARCHIVE_VERSION -> PROJECT_PAYLOAD_VERSION
            else -> return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_SCHEMA_UNSUPPORTED")
        }
        val localDocument = buildJsonObject {
            put("schema", "evidrilo.local-project-drafts")
            put("version", localPayloadVersion)
            put("projects", buildJsonArray { add(restoredPayload) })
        }
        val project = when (val decoded = StudentProjectDraftStoreCodec.decode(localDocument.toString())) {
            is LocalStorageReadResult.Success -> decoded.value.orEmpty().singleOrNull()
                ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_PROJECT_INVALID")
            else -> return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_PROJECT_INVALID")
        }
        if (project.id != manifestSummary.projectId ||
            Instant.fromEpochMilliseconds(project.createdAtEpochMillis).toString() != manifestSummary.createdAtUtc ||
            Instant.fromEpochMilliseconds(project.updatedAtEpochMillis).toString() != manifestSummary.updatedAtUtc
        ) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_PROJECT_MANIFEST_MISMATCH")
        }
        if (project.templateSnapshot?.id != manifestSummary.templateId ||
            project.templateSnapshot?.version != manifestSummary.templateVersion
        ) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_TEMPLATE_MANIFEST_MISMATCH")
        }
        if (project.attachments.size != manifestSummary.attachments.size || project.attachments.any { reference ->
                manifestSummary.attachments.none { it.toReference() == reference }
            }
        ) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_METADATA_MISMATCH")
        }

        val expectedRevisionPaths = project.revisionSnapshots.mapNotNull { revisionPath(it.revision) }.toSet()
        val actualRevisionPaths = entries.keys.filter(revisionPathPattern::matches).toSet()
        if (expectedRevisionPaths != actualRevisionPaths) {
            return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_REVISION_SET_MISMATCH")
        }
        project.revisionSnapshots.forEach { snapshot ->
            val path = revisionPath(snapshot.revision) ?: return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_REVISION_INVALID")
            val archivedRevision = runCatching { json.parseToJsonElement(entries.getValue(path).decodeToString()) }
                .getOrElse { return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_REVISION_INVALID") }
            val expectedRevision = runCatching {
                val original = archivedProject["revisionSnapshots"]?.jsonArray
                    ?.single { it.jsonObject.requiredInt("revision") == snapshot.revision }
                    ?.jsonObject ?: error("revision missing")
                original
            }.getOrElse { return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_REVISION_INVALID") }
            if (archivedRevision != expectedRevision) {
                return StudentProjectArchiveReadResult.Rejected("PROJECT_ARCHIVE_REVISION_MISMATCH")
            }
        }
        ensureActive()
        return StudentProjectArchiveReadResult.Preview(project, manifestSummary, emptyList(), bytes.sha256StreamingHex(ensureActive))
    }

    /**
     * Second-pass import after the caller has shown [preview] and the user accepted it.
     * Bytes go directly to the store's private staging writer in 8 KiB chunks.
     */
    fun stageAttachments(
        bytes: ByteArray,
        preview: StudentProjectArchiveReadResult.Preview,
        attachmentStore: StudentProjectAttachmentStore,
        targetProject: StudentProjectDraft = preview.project,
        ensureActive: () -> Unit = {},
    ): StudentProjectArchiveStagingResult {
        ensureActive()
        if (bytes.isEmpty() || bytes.size > MAX_COMPRESSED_BYTES) {
            return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_SIZE_INVALID")
        }
        if (bytes.sha256StreamingHex(ensureActive) != preview.archiveSha256) {
            return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_CHANGED_AFTER_PREVIEW")
        }
        val manifestAttachments = preview.manifest.attachments
        if (StudentProjectDraftRules.validate(targetProject).isNotEmpty() ||
            !uuidPattern.matches(targetProject.id) ||
            targetProject.attachments.size != manifestAttachments.size
        ) {
            return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_METADATA_MISMATCH")
        }
        if (manifestAttachments.isEmpty()) return StudentProjectArchiveStagingResult.NoAttachments

        val sourceAttachments = preview.project.attachments
        if (sourceAttachments.size != manifestAttachments.size) {
            return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_METADATA_MISMATCH")
        }
        val targetAttachmentsByArchiveId = mutableMapOf<String, StudentProjectAttachmentRef>()
        for (metadata in manifestAttachments) {
            val sourceIndex = sourceAttachments.indexOfFirst { it.id == metadata.id }
            if (sourceIndex < 0) {
                return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_METADATA_MISMATCH")
            }
            val source = sourceAttachments[sourceIndex]
            val target = targetProject.attachments.getOrNull(sourceIndex)
                ?: return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_METADATA_MISMATCH")
            if (source.copy(id = target.id) != target ||
                metadata.path != attachmentPath(source) ||
                metadata.fileName != source.fileName || metadata.mimeType != source.mimeType ||
                metadata.sizeBytes != source.sizeBytes || metadata.sha256 != source.sha256 ||
                !targetAttachmentsByArchiveId.put(metadata.id, target).let { it == null }
            ) {
                return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_METADATA_MISMATCH")
            }
        }
        val preflight = when (val result = StudentProjectArchiveZipPreflight.inspect(
            bytes = bytes,
            maxEntries = MAX_ENTRIES,
            maxExpandedBytes = MAX_EXPANDED_BYTES.toLong(),
            maxCompressionRatio = 100,
            entryLimit = ::entryLimit,
            ensureActive = ensureActive,
        )) {
            is StudentProjectZipPreflightResult.Valid -> result.entries
            is StudentProjectZipPreflightResult.Rejected -> return StudentProjectArchiveStagingResult.Rejected(result.code)
        }
        val session = when (val started = attachmentStore.beginImport(targetProject.id)) {
            is StudentProjectAttachmentImportResult.Started -> started.session
            StudentProjectAttachmentImportResult.Unavailable -> return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_STORE_UNAVAILABLE")
            is StudentProjectAttachmentImportResult.Failed -> return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_STORE_FAILED")
        }
        val byPath = manifestAttachments.associate { metadata ->
            metadata.path to (metadata to targetAttachmentsByArchiveId.getValue(metadata.id))
        }
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    ensureActive()
                    val entry = zip.nextEntry ?: break
                    val stagedAttachment = byPath[entry.name]
                    if (stagedAttachment == null) {
                        while (true) {
                            ensureActive()
                            if (zip.read(buffer, 0, buffer.size) < 0) break
                        }
                        continue
                    }
                    val (metadata, reference) = stagedAttachment
                    val format = attachmentFormatForPath(entry.name)
                        ?: throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH")
                    val writer = when (val opened = session.openWriter(reference)) {
                        is StudentProjectAttachmentWriterResult.Opened -> opened.writer
                        StudentProjectAttachmentWriterResult.Unavailable -> throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_STORE_UNAVAILABLE")
                        is StudentProjectAttachmentWriterResult.Failed -> throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_STORE_FAILED")
                    }
                    val sha = StudentProjectSha256Accumulator()
                    val crc = StudentProjectCrc32Accumulator()
                    val scanner = AttachmentContentScanner(
                        format,
                        null,
                        inspectDocxContainer = false,
                        ensureActive = ensureActive,
                    )
                    var entryBytes = 0L
                    try {
                        while (true) {
                            ensureActive()
                            val read = zip.read(buffer, 0, buffer.size)
                            if (read < 0) break
                            if (read == 0) continue
                            entryBytes += read
                            if (entryBytes > reference.sizeBytes || entryBytes > MAX_ATTACHMENT_BYTES) {
                                throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_SIZE_INVALID")
                            }
                            sha.update(buffer, 0, read)
                            crc.update(buffer, 0, read)
                            scanner.update(buffer, 0, read)
                            writer.write(buffer, 0, read)
                        }
                        val expected = preflight[entry.name]
                            ?: throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_DIRECTORY_INVALID")
                        if (entryBytes != reference.sizeBytes || entryBytes != expected.expandedSize) {
                            throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_SIZE_INVALID")
                        }
                        if (sha.digest().toLowerHex() != reference.sha256 || crc.value() != expected.crc32) {
                            throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_INTEGRITY_MISMATCH")
                        }
                        scanner.finish()?.let { throw ProjectArchiveValidationFailure(it) }
                        if (writer.finish() != LocalStorageWriteResult.SAVED) {
                            throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_STAGE_FAILED")
                        }
                    } catch (failure: Exception) {
                        writer.abort()
                        throw failure
                    }
                }
            }
            return StudentProjectArchiveStagingResult.Staged(session)
        } catch (failure: ProjectArchiveValidationFailure) {
            session.rollback()
            return StudentProjectArchiveStagingResult.Rejected(failure.code)
        } catch (cancelled: CancellationException) {
            session.rollback()
            throw cancelled
        } catch (_: Exception) {
            session.rollback()
            return StudentProjectArchiveStagingResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_STAGE_FAILED")
        }
    }

    private fun buildManifest(
        project: StudentProjectDraft,
        appVersion: String,
        exportedAtUtc: String,
        payloadEntries: Map<String, ByteArray>,
        attachments: List<StudentProjectArchiveAttachmentEntry>,
    ): JsonObject = buildJsonObject {
        put("schema", MANIFEST_SCHEMA)
        put("version", ARCHIVE_VERSION)
        put("appVersion", appVersion)
        put("exporterVersion", "1")
        put("projectId", project.id)
        put("templateId", project.templateSnapshot?.id?.let(::JsonPrimitive) ?: JsonNull)
        put("templateVersion", project.templateSnapshot?.version?.let(::JsonPrimitive) ?: JsonNull)
        put("createdAtUtc", Instant.fromEpochMilliseconds(project.createdAtEpochMillis).toString())
        put("updatedAtUtc", Instant.fromEpochMilliseconds(project.updatedAtEpochMillis).toString())
        put("exportedAtUtc", exportedAtUtc)
        val revisions = project.revisionSnapshots.map(StudentProjectRevisionSnapshot::revision)
        put("revisionRange", buildJsonObject {
            put("firstRevision", revisions.minOrNull()?.let(::JsonPrimitive) ?: JsonNull)
            put("lastRevision", revisions.maxOrNull()?.let(::JsonPrimitive) ?: JsonNull)
        })
        put("attachments", buildJsonArray {
            attachments.sortedBy { it.path.lowercase() }.forEach { attachment ->
                add(buildJsonObject {
                    put("id", attachment.id)
                    put("path", attachment.path)
                    put("fileName", attachment.fileName)
                    put("mimeType", attachment.mimeType)
                    put("sizeBytes", attachment.sizeBytes)
                    put("sha256", attachment.sha256)
                })
            }
        })
        put("entries", buildJsonArray {
            payloadEntries.entries.sortedBy { it.key }.forEach { entry ->
                add(buildJsonObject {
                    put("path", entry.key)
                    put("sizeBytes", entry.value.size)
                    put("mimeType", JSON_MIME_TYPE)
                    put("sha256", entry.value.sha256Hex())
                })
            }
        })
    }

    private fun validateManifest(
        manifest: JsonObject,
        archiveEntries: Map<String, ByteArray>,
        attachmentScans: Map<String, StreamedAttachmentInfo>,
    ): StudentProjectArchiveManifest? = try {
        require(manifest.keys == manifestKeys)
        require(manifest.requiredString("schema") == MANIFEST_SCHEMA)
        val version = manifest.requiredString("version")
        require(version in setOf(LEGACY_ARCHIVE_VERSION, OLDER_ARCHIVE_VERSION, EARLIER_ARCHIVE_VERSION, PREVIOUS_ARCHIVE_VERSION, ARCHIVE_VERSION))
        val projectId = manifest.requiredString("projectId")
        require(uuidPattern.matches(projectId))
        val appVersion = manifest.requiredString("appVersion")
        require(appVersionPattern.matches(appVersion))
        require(manifest.requiredString("exporterVersion") == "1")
        val templateId = manifest.optionalString("templateId")
        val templateVersion = manifest.optionalInt("templateVersion")
        require((templateId == null) == (templateVersion == null))
        require(templateVersion?.let { it > 0 } != false)
        val createdAtUtc = manifest.requiredString("createdAtUtc").also { require(isCanonicalUtc(it)) }
        val updatedAtUtc = manifest.requiredString("updatedAtUtc").also { require(isCanonicalUtc(it)) }
        val exportedAtUtc = manifest.requiredString("exportedAtUtc").also { require(isCanonicalUtc(it)) }
        val revisionRange = manifest["revisionRange"]?.jsonObject ?: error("revision range required")
        require(revisionRange.keys == revisionRangeKeys)
        val firstRevision = revisionRange.optionalInt("firstRevision")
        val lastRevision = revisionRange.optionalInt("lastRevision")
        require((firstRevision == null) == (lastRevision == null))
        require(firstRevision?.let { it > 0 } != false && lastRevision?.let { it >= firstRevision!! } != false)
        val attachmentItems = manifest["attachments"]?.jsonArray ?: error("attachments required")
        require(attachmentItems.size <= StudentProjectAttachmentRules.MAX_ATTACHMENTS)
        if (version == LEGACY_ARCHIVE_VERSION) require(attachmentItems.isEmpty() && attachmentScans.isEmpty())
        val seenAttachmentIds = mutableSetOf<String>()
        val seenAttachmentPaths = mutableSetOf<String>()
        val attachments = attachmentItems.map { element ->
            val item = element.jsonObject
            require(item.keys == manifestAttachmentKeys)
            val id = item.requiredString("id")
            val path = item.requiredString("path")
            val fileName = item.requiredString("fileName")
            val mimeType = item.requiredString("mimeType")
            val sizeBytes = item.requiredLong("sizeBytes")
            val sha256 = item.requiredString("sha256")
            val reference = StudentProjectAttachmentRef(id, fileName, mimeType, sizeBytes, sha256)
            require(StudentProjectAttachmentRules.validate(reference).isEmpty())
            val expectedPath = attachmentPath(reference) ?: error("attachment MIME and extension mismatch")
            require(path == expectedPath && attachmentFormatForPath(path) != null)
            require(seenAttachmentIds.add(id.lowercase()) && seenAttachmentPaths.add(path.lowercase()))
            val scanned = attachmentScans[path] ?: error("attachment missing")
            if (scanned.sizeBytes != sizeBytes) throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_SIZE_MISMATCH")
            if (scanned.sha256 != sha256) throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_INTEGRITY_MISMATCH")
            StudentProjectArchiveAttachmentEntry(id, path, fileName, mimeType, sizeBytes, sha256)
        }
        require(attachments.map(StudentProjectArchiveAttachmentEntry::path).toSet() == attachmentScans.keys)
        val manifestEntries = manifest["entries"]?.jsonArray ?: error("entries required")
        require(manifestEntries.size + 1 == archiveEntries.size)
        val listedPaths = mutableSetOf<String>()
        manifestEntries.forEach { element ->
            val item = element.jsonObject
            require(item.keys == manifestEntryKeys)
            val path = item.requiredString("path")
            require(path != MANIFEST_PATH && isSafeEntryPath(path))
            require(listedPaths.add(path.lowercase()))
            require(path in archiveEntries)
            val content = archiveEntries.getValue(path)
            if (item.requiredInt("sizeBytes") != content.size) {
                throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_SIZE_MISMATCH")
            }
            require(item.requiredString("mimeType") == JSON_MIME_TYPE)
            if (item.requiredString("sha256") != content.sha256Hex()) {
                throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_INTEGRITY_MISMATCH")
            }
        }
        require(archiveEntries.keys - MANIFEST_PATH == manifestEntries.map { it.jsonObject.requiredString("path") }.toSet())
        val project = json.parseToJsonElement(archiveEntries.getValue(PROJECT_PATH).decodeToString()).jsonObject
            .getValue("project").jsonObject
        val parsedFirstRevision = project["revisionSnapshots"]?.jsonArray
            ?.map { it.jsonObject.requiredInt("revision") }?.minOrNull()
        val parsedLastRevision = project["revisionSnapshots"]?.jsonArray
            ?.map { it.jsonObject.requiredInt("revision") }?.maxOrNull()
        require(parsedFirstRevision == firstRevision && parsedLastRevision == lastRevision)
        StudentProjectArchiveManifest(
            projectId = projectId,
            appVersion = appVersion,
            templateId = templateId,
            templateVersion = templateVersion,
            createdAtUtc = createdAtUtc,
            updatedAtUtc = updatedAtUtc,
            entryPaths = listedPaths.toList(),
            exportedAtUtc = exportedAtUtc,
            attachments = attachments,
        )
    } catch (failure: ProjectArchiveValidationFailure) {
        throw failure
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.toArchiveProject(): JsonObject {
        val result = toMutableMap()
        val createdAt = result.remove("createdAtEpochMillis")?.jsonPrimitive?.longOrNull ?: error("created time missing")
        val updatedAt = result.remove("updatedAtEpochMillis")?.jsonPrimitive?.longOrNull ?: error("updated time missing")
        result["createdAtUtc"] = JsonPrimitive(Instant.fromEpochMilliseconds(createdAt).toString())
        result["updatedAtUtc"] = JsonPrimitive(Instant.fromEpochMilliseconds(updatedAt).toString())
        val revisions = result["revisionSnapshots"]?.jsonArray ?: error("revision snapshots missing")
        result["revisionSnapshots"] = JsonArray(revisions.map { it.jsonObject.toArchiveRevision() })
        return JsonObject(result)
    }

    private fun JsonObject.toArchiveRevision(): JsonObject {
        val result = toMutableMap()
        val savedAt = result.remove("savedAtEpochMillis")?.jsonPrimitive?.longOrNull ?: error("revision time missing")
        result["savedAtUtc"] = JsonPrimitive(Instant.fromEpochMilliseconds(savedAt).toString())
        return JsonObject(result)
    }

    private fun JsonObject.toLocalDraft(): JsonObject {
        val result = toMutableMap()
        val createdAtUtc = result.remove("createdAtUtc")?.jsonPrimitive?.content ?: error("created time missing")
        val updatedAtUtc = result.remove("updatedAtUtc")?.jsonPrimitive?.content ?: error("updated time missing")
        result["createdAtEpochMillis"] = JsonPrimitive(Instant.parse(createdAtUtc).toEpochMilliseconds())
        result["updatedAtEpochMillis"] = JsonPrimitive(Instant.parse(updatedAtUtc).toEpochMilliseconds())
        val revisions = result["revisionSnapshots"]?.jsonArray ?: error("revision snapshots missing")
        result["revisionSnapshots"] = JsonArray(revisions.map { it.jsonObject.toLocalRevision() })
        return JsonObject(result)
    }

    private fun JsonObject.toLocalRevision(): JsonObject {
        val result = toMutableMap()
        val savedAtUtc = result.remove("savedAtUtc")?.jsonPrimitive?.content ?: error("revision time missing")
        result["savedAtEpochMillis"] = JsonPrimitive(Instant.parse(savedAtUtc).toEpochMilliseconds())
        return JsonObject(result)
    }

    private data class StreamedAttachmentInfo(val sizeBytes: Long, val sha256: String)

    private class CappedArchiveOutputStream(
        private val output: ByteArrayOutputStream,
        private val maxBytes: Int,
    ) : OutputStream() {
        private var written = 0L

        override fun write(value: Int) {
            ensureCapacity(1)
            output.write(value)
            written += 1
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
            ensureCapacity(length)
            output.write(buffer, offset, length)
            written += length
        }

        private fun ensureCapacity(incoming: Int) {
            if (written + incoming > maxBytes) throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_TOO_LARGE")
        }
    }

    private data class AttachmentFormat(val extension: String, val mimeType: String, val kind: Kind) {
        enum class Kind { PDF, DOCX, TEXT, PNG, JPEG }
    }

    private fun attachmentPath(reference: StudentProjectAttachmentRef): String? {
        if (StudentProjectAttachmentRules.validate(reference).isNotEmpty()) return null
        val extension = reference.fileName.substringAfterLast('.', "").lowercase()
        val format = attachmentFormat(extension) ?: return null
        if (reference.mimeType != format.mimeType) return null
        return "attachments/${reference.id}.$extension"
    }

    private fun attachmentFormatForPath(path: String): AttachmentFormat? {
        val match = attachmentPathPattern.matchEntire(path) ?: return null
        return attachmentFormat(match.groupValues[2].lowercase())
    }

    private fun attachmentFormat(extension: String): AttachmentFormat? = when (extension) {
        "pdf" -> AttachmentFormat(extension, "application/pdf", AttachmentFormat.Kind.PDF)
        "docx" -> AttachmentFormat(
            extension,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            AttachmentFormat.Kind.DOCX,
        )
        "csv" -> AttachmentFormat(extension, "text/csv", AttachmentFormat.Kind.TEXT)
        "txt" -> AttachmentFormat(extension, "text/plain", AttachmentFormat.Kind.TEXT)
        "md" -> AttachmentFormat(extension, "text/markdown", AttachmentFormat.Kind.TEXT)
        "png" -> AttachmentFormat(extension, "image/png", AttachmentFormat.Kind.PNG)
        "jpg", "jpeg" -> AttachmentFormat(extension, "image/jpeg", AttachmentFormat.Kind.JPEG)
        else -> null
    }

    private fun StudentProjectArchiveAttachmentEntry.toReference() = StudentProjectAttachmentRef(
        id = id,
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
    )

    private fun writeAttachmentToArchive(
        zip: ZipOutputStream,
        entry: StudentProjectArchiveAttachmentEntry,
        handle: dev.nextgen.mobile.storage.StudentProjectAttachmentReadHandle,
    ) {
        val reference = entry.toReference()
        val format = attachmentFormatForPath(entry.path)
            ?: throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH")
        if (handle.sizeBytes != reference.sizeBytes || handle.sizeBytes !in 1..MAX_ATTACHMENT_BYTES.toLong()) {
            handle.close()
            throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_SIZE_INVALID")
        }
        val docxBytes = if (format.kind == AttachmentFormat.Kind.DOCX) ByteArray(handle.sizeBytes.toInt()) else null
        val scanner = AttachmentContentScanner(format, docxBytes)
        val sha = StudentProjectSha256Accumulator()
        var count = 0L
        val buffer = ByteArray(8 * 1024)
        try {
            zip.putNextEntry(ZipEntry(entry.path))
            var emptyReads = 0
            while (true) {
                val read = handle.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) {
                    emptyReads += 1
                    if (emptyReads > 8) throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_READ_STALLED")
                    continue
                }
                emptyReads = 0
                count += read
                if (count > reference.sizeBytes || count > MAX_ATTACHMENT_BYTES) {
                    throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_SIZE_INVALID")
                }
                sha.update(buffer, 0, read)
                scanner.update(buffer, 0, read)
                if (docxBytes != null) buffer.copyInto(docxBytes, (count - read).toInt(), 0, read)
                zip.write(buffer, 0, read)
            }
            if (count != reference.sizeBytes || sha.digest().toLowerHex() != reference.sha256) {
                throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_INTEGRITY_MISMATCH")
            }
            scanner.finish()?.let { throw ProjectArchiveValidationFailure(it) }
            zip.closeEntry()
        } finally {
            handle.close()
        }
    }

    private class AttachmentContentScanner(
        private val format: AttachmentFormat,
        private val docxBytes: ByteArray?,
        private val inspectDocxContainer: Boolean = true,
        private val ensureActive: () -> Unit = {},
    ) {
        private val prefix = ByteArray(8)
        private var prefixSize = 0
        private var last = 0
        private var lastBefore = 0
        private var count = 0L
        private val utf8 = if (format.kind == AttachmentFormat.Kind.TEXT) Utf8Validator() else null

        fun update(bytes: ByteArray, offset: Int, length: Int) {
            for (index in offset until offset + length) {
                val value = bytes[index].toInt() and 0xff
                if (prefixSize < prefix.size) prefix[prefixSize++] = bytes[index]
                lastBefore = last
                last = value
                count += 1
                if (utf8?.accept(value) == false) throw ProjectArchiveValidationFailure("PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH")
            }
        }

        fun finish(): String? {
            val valid = when (format.kind) {
                AttachmentFormat.Kind.PDF -> prefixSize >= 5 && prefix.copyOfRange(0, 5).decodeToString() == "%PDF-"
                AttachmentFormat.Kind.PNG -> prefixSize == 8 && prefix.contentEquals(
                    byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
                )
                AttachmentFormat.Kind.JPEG -> prefixSize >= 2 &&
                    (prefix[0].toInt() and 0xff) == 0xff && (prefix[1].toInt() and 0xff) == 0xd8 &&
                    lastBefore == 0xff && last == 0xd9
                AttachmentFormat.Kind.TEXT -> count > 0 && utf8?.complete() == true
                AttachmentFormat.Kind.DOCX -> prefixSize >= 4 && prefix[0] == 0x50.toByte() &&
                    prefix[1] == 0x4b.toByte() && prefix[2] == 0x03.toByte() && prefix[3] == 0x04.toByte() &&
                    (!inspectDocxContainer || docxBytes?.let { isStructurallySafeDocx(it, ensureActive) } == true)
            }
            return if (valid) null else "PROJECT_ARCHIVE_ATTACHMENT_TYPE_MISMATCH"
        }
    }

    private class Utf8Validator {
        private var remaining = 0
        private var codePoint = 0
        private var minimum = 0

        fun accept(value: Int): Boolean {
            if (value == 0) return false
            if (remaining == 0) {
                when {
                    value <= 0x7f -> return true
                    value in 0xc2..0xdf -> { remaining = 1; codePoint = value and 0x1f; minimum = 0x80 }
                    value in 0xe0..0xef -> { remaining = 2; codePoint = value and 0x0f; minimum = 0x800 }
                    value in 0xf0..0xf4 -> { remaining = 3; codePoint = value and 0x07; minimum = 0x10000 }
                    else -> return false
                }
                return true
            }
            if (value !in 0x80..0xbf) return false
            codePoint = (codePoint shl 6) or (value and 0x3f)
            remaining -= 1
            return remaining != 0 || codePoint >= minimum && codePoint <= 0x10ffff && codePoint !in 0xd800..0xdfff
        }

        fun complete(): Boolean = remaining == 0
    }

    private fun isStructurallySafeDocx(bytes: ByteArray, ensureActive: () -> Unit = {}): Boolean {
        val inner = when (val result = StudentProjectArchiveZipPreflight.inspect(
            bytes = bytes,
            maxEntries = MAX_ENTRIES,
            maxExpandedBytes = MAX_ATTACHMENT_BYTES.toLong(),
            maxCompressionRatio = 100,
            entryLimit = ::docxEntryLimit,
            allowDirectories = true,
            ensureActive = ensureActive,
        )) {
            is StudentProjectZipPreflightResult.Valid -> result.entries
            is StudentProjectZipPreflightResult.Rejected -> return false
        }
        val seen = mutableSetOf<String>()
        val required = setOf("[content_types].xml", "_rels/.rels", "word/document.xml")
        var expanded = 0L
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    ensureActive()
                    val entry = zip.nextEntry ?: break
                    val expected = inner[entry.name] ?: return false
                    if (entry.isDirectory != expected.isDirectory) return false
                    if (entry.isDirectory) continue
                    val normalized = entry.name.lowercase()
                    if (containsUnsafeDocxName(normalized) || !isSupportedDocxEntry(normalized)) return false
                    seen += normalized
                    val marker = AsciiMarkerScanner("macroenabled", "vbaproject", "activex", "oleobject")
                    val crc = StudentProjectCrc32Accumulator()
                    var size = 0L
                    while (true) {
                        ensureActive()
                        val read = zip.read(buffer, 0, buffer.size)
                        if (read < 0) break
                        if (read == 0) continue
                        size += read
                        expanded += read
                        if (expanded > MAX_ATTACHMENT_BYTES) return false
                        crc.update(buffer, 0, read)
                        if (entry.name.endsWith(".xml", true) || entry.name.endsWith(".rels", true)) marker.update(buffer, 0, read)
                    }
                    if (size != expected.expandedSize || crc.value() != expected.crc32 || marker.found) return false
                }
            }
            seen.containsAll(required) && inner.keys.filterNot { it.endsWith('/') }.all { it.lowercase() in seen }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun docxEntryLimit(path: String): Int {
        val normalized = path.lowercase()
        if (containsUnsafeDocxName(normalized)) return 0
        return when {
            normalized.endsWith(".xml") || normalized.endsWith(".rels") -> 2 * 1024 * 1024
            normalized.endsWith(".png") || normalized.endsWith(".jpg") || normalized.endsWith(".jpeg") ||
                normalized.endsWith(".gif") || normalized.endsWith(".emf") || normalized.endsWith(".wmf") -> MAX_ATTACHMENT_BYTES
            else -> 0
        }
    }

    private fun isSupportedDocxEntry(path: String): Boolean = docxEntryLimit(path) > 0

    private fun containsUnsafeDocxName(path: String): Boolean =
        listOf("vbaproject", "macro", "activex", "oleobject", "embedding", "customui").any(path::contains)

    private class AsciiMarkerScanner(private vararg val markers: String) {
        private var tail = ""
        var found = false
            private set

        fun update(bytes: ByteArray, offset: Int, length: Int) {
            if (found) return
            val text = buildString(tail.length + length) {
                append(tail)
                for (index in offset until offset + length) append((bytes[index].toInt() and 0xff).toChar())
            }.lowercase()
            if (markers.any(text::contains)) {
                found = true
            } else {
                tail = text.takeLast(markers.maxOfOrNull(String::length)?.minus(1)?.coerceAtLeast(0) ?: 0)
            }
        }
    }

    private fun isSafeEntryPath(path: String): Boolean =
        isLexicallySafeEntryPath(path) && (entryPathPattern.matches(path) || attachmentPathPattern.matches(path))

    private fun isLexicallySafeEntryPath(path: String): Boolean =
        path.length <= 120 && path.all { it.code in 0x20..0x7e } &&
            '\\' !in path && ':' !in path && path.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun entryLimit(path: String): Int = when {
        path == MANIFEST_PATH -> MAX_MANIFEST_BYTES
        path == PROJECT_PATH -> StudentProjectDraftRules.MAX_ENCODED_DRAFT_BYTES + 64 * 1024
        path == TEMPLATE_PATH -> MAX_TEMPLATE_BYTES
        revisionPathPattern.matches(path) -> MAX_ENTRY_BYTES
        attachmentPathPattern.matches(path) -> MAX_ATTACHMENT_BYTES
        else -> 0
    }

    private fun revisionPath(revision: Int): String? =
        if (revision in 1..9_999) "revisions/revision-${revision.toString().padStart(4, '0')}.json" else null

    private fun isCanonicalUtc(value: String): Boolean = runCatching {
        Instant.parse(value).toString() == value && value.endsWith("Z")
    }.getOrDefault(false)

    private fun ByteArray.sha256Hex(): String = StudentProjectSha256Accumulator().apply { update(this@sha256Hex) }
        .digest().toLowerHex()

    private fun ByteArray.sha256StreamingHex(ensureActive: () -> Unit = {}): String {
        val sha = StudentProjectSha256Accumulator()
        var offset = 0
        while (offset < size) {
            ensureActive()
            val count = minOf(8 * 1024, size - offset)
            sha.update(this, offset, count)
            offset += count
        }
        return sha.digest().toLowerHex()
    }

    private fun JsonObject.requiredString(name: String): String =
        getValue(name).jsonPrimitive.content.also { require(getValue(name).jsonPrimitive.isString) }

    private fun JsonObject.optionalString(name: String): String? = when (val value = getValue(name)) {
        JsonNull -> null
        is JsonPrimitive -> value.content.takeIf { value.isString } ?: error("string required")
        else -> error("string required")
    }

    private fun JsonObject.requiredInt(name: String): Int =
        getValue(name).jsonPrimitive.intOrNull ?: error("integer required")

    private fun JsonObject.requiredLong(name: String): Long =
        getValue(name).jsonPrimitive.longOrNull ?: error("long required")

    private fun JsonObject.optionalInt(name: String): Int? = when (val value = getValue(name)) {
        JsonNull -> null
        is JsonPrimitive -> value.intOrNull ?: error("integer required")
        else -> error("integer required")
    }
}
