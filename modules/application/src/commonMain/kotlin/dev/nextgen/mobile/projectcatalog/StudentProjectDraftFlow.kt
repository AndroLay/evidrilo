package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldApplyResult
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldProposal
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldRules
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftCreateResult
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectDeadlineChange
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectRevisionSnapshot
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.StudentProjectRevisionActor
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageStatus
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportResult
import dev.nextgen.mobile.storage.StudentProjectAttachmentImportSession
import dev.nextgen.mobile.storage.StudentProjectAttachmentStore
import dev.nextgen.mobile.storage.StudentProjectAttachmentWriterResult
import dev.nextgen.mobile.storage.StudentProjectDraftStore
import dev.nextgen.mobile.storage.STUDENT_PROJECT_ATTACHMENT_MAX_IO_CHUNK_BYTES
import dev.nextgen.mobile.storage.createStudentProjectAttachmentStore
import kotlin.coroutines.cancellation.CancellationException

sealed interface StudentProjectDraftFlowResult<out T> {
    data class Value<T>(val value: T) : StudentProjectDraftFlowResult<T>
    data class Rejected(val code: String) : StudentProjectDraftFlowResult<Nothing>
    data object NotFound : StudentProjectDraftFlowResult<Nothing>
    data object StorageUnavailable : StudentProjectDraftFlowResult<Nothing>
    data object StorageCorrupt : StudentProjectDraftFlowResult<Nothing>
    data object StorageFailed : StudentProjectDraftFlowResult<Nothing>
}

data class StudentProjectImportReceipt(
    val project: StudentProjectDraft,
    val importedAsCopy: Boolean,
    val archivedToRespectCapacity: Boolean,
    val restoredAsNewRevision: Boolean = false,
    val attachmentCleanupFailed: Boolean = false,
)

data class StudentProjectAttachmentAddReceipt(
    val project: StudentProjectDraft,
    /** Project data is committed; only removal of private staging files needs retry. */
    val cleanupPending: Boolean,
)

data class StudentProjectAttachmentRemoveReceipt(
    val project: StudentProjectDraft,
    val preservedInRevisionHistory: Boolean,
    /** Metadata is committed; attachment-fence recovery or unreferenced-file cleanup needs retry. */
    val cleanupPending: Boolean,
)

/**
 * Local-only catalog-to-project operations. This flow has no HTTP, account,
 * AI, analytics, or cloud-sync dependency.
 */
class StudentProjectDraftFlow(
    private val store: StudentProjectDraftStore,
    private val idGenerator: () -> String,
    private val clock: () -> Long,
    private val hasVerifiedProEntitlement: () -> Boolean = { false },
    private val attachmentStore: StudentProjectAttachmentStore = createStudentProjectAttachmentStore(),
) {
    fun list(): StudentProjectDraftFlowResult<List<StudentProjectDraft>> {
        val loaded = store.load()
        if (loaded !is LocalStorageReadResult.Success) return loaded.toFlowFailure()
        val projects = loaded.value.orEmpty()
        val recovery = attachmentStore.recoverPendingProjectDeletions(projects.mapTo(mutableSetOf(), StudentProjectDraft::id))
        if (!recovery.isSuccessfulStorageWrite()) return recovery.toFlowFailure()
        val attachmentRecovery = recoverPendingAttachmentDeletions(projects)
        if (!attachmentRecovery.isSuccessfulStorageWrite()) return attachmentRecovery.toFlowFailure()

        val now = clock()
        val expiredTrash = projects.filter { project ->
            val trashedAt = project.trashedAtEpochMillis
            now >= StudentProjectDraftRules.TRASH_RETENTION_MILLIS &&
                project.status == StudentProjectStatus.TRASHED &&
                trashedAt != null && trashedAt <= now - StudentProjectDraftRules.TRASH_RETENTION_MILLIS
        }
        if (expiredTrash.isEmpty()) {
            return StudentProjectDraftFlowResult.Value(projects.sortedByDescending(StudentProjectDraft::updatedAtEpochMillis))
        }

        val preparedProjectIds = mutableListOf<String>()
        for (project in expiredTrash) {
            val prepared = attachmentStore.prepareProjectDeletion(
                project.id,
                project.hasAttachmentReferences(),
            )
            if (!prepared.isSuccessfulStorageWrite()) {
                val recovery = attachmentStore.recoverPendingProjectDeletions(
                    projects.mapTo(mutableSetOf(), StudentProjectDraft::id),
                )
                return (if (recovery.isSuccessfulStorageWrite()) prepared else recovery).toFlowFailure()
            }
            preparedProjectIds += project.id
        }

        val retained = projects.filterNot { it in expiredTrash }
        val metadataWrite = store.replaceAll(retained)
        if (!metadataWrite.isSuccessfulStorageWrite()) {
            val recovery = recoverPendingDeletionsAfterMetadataFailure()
            return (if (recovery == null || recovery.isSuccessfulStorageWrite()) metadataWrite else recovery)
                .toFlowFailure()
        }

        for (projectId in preparedProjectIds) {
            val cleanup = attachmentStore.completeProjectDeletion(projectId)
            if (!cleanup.isSuccessfulStorageWrite()) return cleanup.toFlowFailure()
        }
        val finalAttachmentRecovery = recoverPendingAttachmentDeletions(retained)
        if (!finalAttachmentRecovery.isSuccessfulStorageWrite()) return finalAttachmentRecovery.toFlowFailure()
        return StudentProjectDraftFlowResult.Value(
            retained.sortedByDescending(StudentProjectDraft::updatedAtEpochMillis),
        )
    }

    fun start(
        template: ProjectTemplateDefinition,
        title: String,
    ): StudentProjectDraftFlowResult<StudentProjectDraft> {
        val current = store.load()
        if (current !is LocalStorageReadResult.Success) return current.toFlowFailure()
        val existing = current.value.orEmpty()
        if (isAtActiveLimit(existing)) return StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED")
        val id = idGenerator()
        if (existing.any { it.id == id }) return StudentProjectDraftFlowResult.Rejected("PROJECT_ID_COLLISION")

        val createdAt = clock()
        val draft = when (val result = StudentProjectDraftRules.create(id, template, title, createdAt)) {
            is StudentProjectDraftCreateResult.Created -> result.draft
            is StudentProjectDraftCreateResult.Unavailable -> return StudentProjectDraftFlowResult.Rejected(result.code)
            is StudentProjectDraftCreateResult.Invalid -> {
                return StudentProjectDraftFlowResult.Rejected(result.issues.firstOrNull() ?: "INVALID_PROJECT_DRAFT")
            }
        }
        return persist(draft)
    }

    fun startManual(title: String): StudentProjectDraftFlowResult<StudentProjectDraft> {
        val current = store.load()
        if (current !is LocalStorageReadResult.Success) return current.toFlowFailure()
        val existing = current.value.orEmpty()
        if (isAtActiveLimit(existing)) return StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED")

        val id = idGenerator()
        if (existing.any { it.id == id }) return StudentProjectDraftFlowResult.Rejected("PROJECT_ID_COLLISION")
        val createdAt = clock()
        val draft = when (val result = StudentProjectDraftRules.createManual(id, title, createdAt)) {
            is StudentProjectDraftCreateResult.Created -> result.draft
            is StudentProjectDraftCreateResult.Unavailable -> return StudentProjectDraftFlowResult.Rejected(result.code)
            is StudentProjectDraftCreateResult.Invalid -> {
                return StudentProjectDraftFlowResult.Rejected(result.issues.firstOrNull() ?: "INVALID_PROJECT_DRAFT")
            }
        }
        return persist(draft)
    }

    /**
     * Persists a new project from an already reviewed AI preview. This method
     * is called only after the student confirms the selected, editable fields;
     * receiving a provider response alone never creates a stored project.
     */
    fun startWithAiScaffold(
        template: ProjectTemplateDefinition,
        title: String,
        proposal: ProjectAiScaffoldProposal,
        selectedFieldIds: Set<String>,
        editedValues: Map<String, String> = emptyMap(),
        explicitlyReplacedFieldIds: Set<String> = emptySet(),
    ): StudentProjectDraftFlowResult<StudentProjectDraft> {
        val current = store.load()
        if (current !is LocalStorageReadResult.Success) return current.toFlowFailure()
        val existing = current.value.orEmpty()
        if (isAtActiveLimit(existing)) return StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED")

        val initialValues = when (val applied = ProjectAiScaffoldRules.applySelected(
            template = template,
            proposal = proposal,
            currentValues = emptyMap(),
            expectedBaseRevision = null,
            selectedFieldIds = selectedFieldIds,
            explicitlyReplacedFieldIds = explicitlyReplacedFieldIds,
            editedValues = editedValues,
        )) {
            is ProjectAiScaffoldApplyResult.Applied -> applied.fieldValues
            is ProjectAiScaffoldApplyResult.Rejected ->
                return StudentProjectDraftFlowResult.Rejected(applied.code)
            ProjectAiScaffoldApplyResult.NoChanges ->
                return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_NO_FIELDS_SELECTED")
        }

        val id = idGenerator()
        if (existing.any { it.id == id }) return StudentProjectDraftFlowResult.Rejected("PROJECT_ID_COLLISION")
        val createdAt = clock()
        val draft = when (val result = StudentProjectDraftRules.create(
            id = id,
            template = template,
            title = title,
            createdAtEpochMillis = createdAt,
            fieldValues = initialValues,
            revisionActor = StudentProjectRevisionActor.CONFIRMED_ASSISTANCE,
        )) {
            is StudentProjectDraftCreateResult.Created -> result.draft
            is StudentProjectDraftCreateResult.Unavailable -> return StudentProjectDraftFlowResult.Rejected(result.code)
            is StudentProjectDraftCreateResult.Invalid -> {
                return StudentProjectDraftFlowResult.Rejected(result.issues.firstOrNull() ?: "INVALID_PROJECT_DRAFT")
            }
        }
        return persist(draft)
    }

    /**
     * Applies an explicitly confirmed preview to an existing local project.
     * The proposal must target the exact saved revision; stale AI output never
     * overwrites newer student work.
     */
    fun applyAiScaffoldToProject(
        projectId: String,
        proposal: ProjectAiScaffoldProposal,
        selectedFieldIds: Set<String>,
        editedValues: Map<String, String> = emptyMap(),
        explicitlyReplacedFieldIds: Set<String> = emptySet(),
    ): StudentProjectDraftFlowResult<StudentProjectDraft> {
        val current = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty().singleOrNull { it.id == projectId }
            else -> return loaded.toFlowFailure()
        } ?: return StudentProjectDraftFlowResult.NotFound

        val template = current.templateSnapshot
            ?: return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_TEMPLATE_REQUIRED")

        val updatedValues = when (val applied = ProjectAiScaffoldRules.applySelected(
            template = template,
            proposal = proposal,
            currentValues = current.fieldValues,
            expectedBaseRevision = current.revision,
            selectedFieldIds = selectedFieldIds,
            explicitlyReplacedFieldIds = explicitlyReplacedFieldIds,
            expectedProjectId = current.id,
            editedValues = editedValues,
        )) {
            is ProjectAiScaffoldApplyResult.Applied -> applied.fieldValues
            is ProjectAiScaffoldApplyResult.Rejected -> return StudentProjectDraftFlowResult.Rejected(applied.code)
            ProjectAiScaffoldApplyResult.NoChanges ->
                return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_NO_CHANGES")
        }
        return update(
            projectId = projectId,
            title = current.title,
            fieldValues = updatedValues,
            revisionActor = StudentProjectRevisionActor.CONFIRMED_ASSISTANCE,
        )
    }

    /**
     * Applies only explicitly confirmed field proposals from a stage-bound AI
     * preview. Template capability, project revision, and before-values are
     * rechecked against the persisted draft immediately before the reducer
     * writes a revision marked as confirmed assistance.
     */
    fun applyAiStageAssistToProject(
        projectId: String,
        expectedTemplateId: String,
        expectedTemplateVersion: Int,
        stageId: String,
        operationId: String,
        expectedBaseRevision: Int,
        expectedBeforeValues: Map<String, String>,
        confirmedValues: Map<String, String>,
    ): StudentProjectDraftFlowResult<StudentProjectDraft> {
        val current = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty().singleOrNull { it.id == projectId }
            else -> return loaded.toFlowFailure()
        } ?: return StudentProjectDraftFlowResult.NotFound

        if (current.revision != expectedBaseRevision) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_STALE_REVISION")
        }
        val template = current.templateSnapshot
            ?: return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_TEMPLATE_REQUIRED")
        if (template.id != expectedTemplateId || template.version != expectedTemplateVersion) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_TEMPLATE_MISMATCH")
        }
        val operation = template.steps.singleOrNull { it.id == stageId }
            ?.aiOperations?.singleOrNull { it.id == operationId }
            ?: return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_OPERATION_NOT_SUPPORTED")
        if (confirmedValues.isEmpty() || expectedBeforeValues.keys != confirmedValues.keys) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_NO_CHANGES")
        }
        val fieldsById = template.inputFields.associateBy { it.id }
        if (confirmedValues.keys.any { fieldId ->
                fieldId !in operation.outputFieldIds ||
                    fieldsById[fieldId]?.kind in setOf(
                        dev.nextgen.mobile.domain.project.ProjectTemplateInputKind.SOURCE,
                        dev.nextgen.mobile.domain.project.ProjectTemplateInputKind.DATA,
                    )
            }
        ) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_FIELD_NOT_ALLOWED")
        }
        if (expectedBeforeValues.any { (fieldId, value) ->
                current.fieldValues[fieldId].orEmpty() != value
            }
        ) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_STALE_CONTEXT")
        }
        if (confirmedValues.any { (fieldId, value) ->
                value.length > StudentProjectDraftRules.MAX_FIELD_CHARS || '\u0000' in value ||
                    fieldId !in fieldsById
            }
        ) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_INVALID_PROPOSAL")
        }
        if (confirmedValues.all { (fieldId, value) -> current.fieldValues[fieldId].orEmpty() == value }) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_AI_NO_CHANGES")
        }

        return update(
            projectId = projectId,
            title = current.title,
            fieldValues = current.fieldValues + confirmedValues,
            revisionActor = StudentProjectRevisionActor.CONFIRMED_ASSISTANCE,
            changeSummary = "Applied AI suggestions for $stageId",
        )
    }

    fun resume(projectId: String): StudentProjectDraftFlowResult<StudentProjectDraft> =
        when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty()
                .singleOrNull { it.id == projectId }
                ?.let { StudentProjectDraftFlowResult.Value(it) }
                ?: StudentProjectDraftFlowResult.NotFound
            else -> loaded.toFlowFailure()
        }

    /**
     * Atomically commits an already validated archive preview. Conflicts are
     * never overwritten: the caller must explicitly choose a re-keyed copy.
     */
    fun importValidatedProject(
        importedProject: StudentProjectDraft,
        importAsCopy: Boolean = false,
        archiveWhenAtCapacity: Boolean = false,
    ): StudentProjectDraftFlowResult<StudentProjectImportReceipt> = importProject(
        importedProject = importedProject,
        importAsCopy = importAsCopy,
        archiveWhenAtCapacity = archiveWhenAtCapacity,
        attachmentStager = null,
        ensureActive = {},
    )

    /** Validates, stages, and commits an archive's private file attachments with its project record. */
    fun importValidatedArchive(
        preview: StudentProjectArchiveReadResult.Preview,
        archiveBytes: ByteArray,
        attachmentStore: StudentProjectAttachmentStore,
        importAsCopy: Boolean = false,
        archiveWhenAtCapacity: Boolean = false,
        ensureActive: () -> Unit = {},
    ): StudentProjectDraftFlowResult<StudentProjectImportReceipt> = importProject(
        importedProject = preview.project,
        importAsCopy = importAsCopy,
        archiveWhenAtCapacity = archiveWhenAtCapacity,
        attachmentStager = { targetProject, checkActive ->
            StudentProjectArchiveCodec.stageAttachments(
                bytes = archiveBytes,
                preview = preview,
                attachmentStore = attachmentStore,
                targetProject = targetProject,
                ensureActive = checkActive,
            )
        },
        ensureActive = ensureActive,
    )

    private fun importProject(
        importedProject: StudentProjectDraft,
        importAsCopy: Boolean,
        archiveWhenAtCapacity: Boolean,
        attachmentStager: ((StudentProjectDraft, () -> Unit) -> StudentProjectArchiveStagingResult)?,
        ensureActive: () -> Unit,
    ): StudentProjectDraftFlowResult<StudentProjectImportReceipt> {
        ensureActive()
        val loaded = store.load()
        if (loaded !is LocalStorageReadResult.Success) return loaded.toFlowFailure()
        val existing = loaded.value.orEmpty()
        if (StudentProjectDraftRules.validate(importedProject).isNotEmpty()) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_INVALID")
        }
        if (importedProject.attachments.isNotEmpty() && attachmentStager == null) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_ATTACHMENT_DATA_REQUIRED")
        }

        val idCollision = existing.any { it.id == importedProject.id }
        if (idCollision && !importAsCopy) {
            val existingProject = existing.single { it.id == importedProject.id }
            return StudentProjectDraftFlowResult.Rejected(
                if (existingProject == importedProject) "PROJECT_IMPORT_ALREADY_PRESENT" else "PROJECT_IMPORT_ID_CONFLICT",
            )
        }

        var prepared = if (importAsCopy) {
            runCatching { importedProject.copyWithFreshIds(existing.map(StudentProjectDraft::id).toSet()) }
                .getOrElse { return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_ID_REMAP_FAILED") }
        } else {
            importedProject
        }
        if (existing.any { it.id == prepared.id }) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_ID_CONFLICT")
        }

        val atCapacity = isAtActiveLimit(existing)
        if (atCapacity) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED")
        }
        val archivedForCapacity = false
        if (archivedForCapacity) {
            prepared = prepared.copy(
                status = StudentProjectStatus.ARCHIVED,
                trashedAtEpochMillis = null,
                updatedAtEpochMillis = maxOf(prepared.updatedAtEpochMillis, clock()),
            )
        }
        if (StudentProjectDraftRules.validate(prepared).isNotEmpty()) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_INVALID")
        }

        val stagedResult = try {
            ensureActive()
            attachmentStager?.invoke(prepared, ensureActive)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_ARCHIVE_ATTACHMENT_STAGE_FAILED")
        }
        val attachmentSession = when (val staged = stagedResult) {
            null,
            StudentProjectArchiveStagingResult.NoAttachments,
            -> null
            is StudentProjectArchiveStagingResult.Staged -> staged.session
            is StudentProjectArchiveStagingResult.Rejected -> return StudentProjectDraftFlowResult.Rejected(staged.code)
        }
        if (attachmentSession != null) {
            val publish = try {
                ensureActive()
                attachmentSession.publish()
            } catch (cancelled: CancellationException) {
                runCatching { attachmentSession.rollback() }
                throw cancelled
            } catch (_: Exception) {
                LocalStorageWriteResult.FAILED
            }
            if (publish != LocalStorageWriteResult.SAVED) {
                runCatching { attachmentSession.rollback() }
                return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_ATTACHMENT_PUBLISH_FAILED")
            }
        }

        try {
            ensureActive()
        } catch (cancelled: CancellationException) {
            attachmentSession?.let { runCatching { it.rollback() } }
            throw cancelled
        }
        return when (val write = runCatching { store.replaceAll(existing + prepared) }
            .getOrDefault(LocalStorageWriteResult.FAILED)
        ) {
            LocalStorageWriteResult.SAVED -> {
                val attachmentCleanupFailed = attachmentSession?.let { session ->
                    runCatching { session.complete() }.getOrDefault(LocalStorageWriteResult.FAILED) !=
                        LocalStorageWriteResult.SAVED
                } ?: false
                StudentProjectDraftFlowResult.Value(StudentProjectImportReceipt(
                    project = prepared,
                    importedAsCopy = importAsCopy,
                    archivedToRespectCapacity = archivedForCapacity,
                    attachmentCleanupFailed = attachmentCleanupFailed,
                ))
            }
            LocalStorageWriteResult.CLEARED,
            LocalStorageWriteResult.FAILED,
            -> {
                attachmentSession?.let { session -> runCatching { session.rollback() } }
                StudentProjectDraftFlowResult.StorageFailed
            }
            LocalStorageWriteResult.LIMIT_REACHED -> {
                attachmentSession?.let { session -> runCatching { session.rollback() } }
                StudentProjectDraftFlowResult.Rejected("PROJECT_DRAFT_STORAGE_LIMIT_REACHED")
            }
            LocalStorageWriteResult.UNAVAILABLE -> {
                attachmentSession?.let { session -> runCatching { session.rollback() } }
                StudentProjectDraftFlowResult.StorageUnavailable
            }
        }
    }

    /** Restores an explicitly confirmed newer archive revision onto the same local ID. */
    fun restoreImportedProjectAsRevision(
        importedProject: StudentProjectDraft,
    ): StudentProjectDraftFlowResult<StudentProjectImportReceipt> {
        if (StudentProjectDraftRules.validate(importedProject).isNotEmpty()) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_INVALID")
        }
        val loaded = store.load()
        if (loaded !is LocalStorageReadResult.Success) return loaded.toFlowFailure()
        val existing = loaded.value.orEmpty()
        val current = existing.singleOrNull { it.id == importedProject.id }
            ?: return StudentProjectDraftFlowResult.NotFound
        if (current.attachments != importedProject.attachments) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_ATTACHMENT_REVISION_UNSUPPORTED")
        }
        if (importedProject.revision <= current.revision) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_NO_NEW_REVISION")
        }
        if (current.updatedAtEpochMillis == Long.MAX_VALUE) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_TIMESTAMP_EXHAUSTED")
        }

        val restoredAt = maxOf(clock(), current.updatedAtEpochMillis + 1)
        val priorHistory = current.revisionSnapshots.ifEmpty {
            listOf(
                StudentProjectDraftRules.captureRevisionSnapshot(
                    current,
                    StudentProjectRevisionActor.STUDENT,
                    "State before archive restore",
                ),
            )
        }
        val restored = current.copy(
            templateSnapshot = importedProject.templateSnapshot,
            title = importedProject.title,
            fieldValues = importedProject.fieldValues,
            revision = current.revision + 1,
            updatedAtEpochMillis = restoredAt,
            sources = importedProject.sources,
            themes = importedProject.themes,
            claimEvidenceSourceIds = importedProject.claimEvidenceSourceIds,
            evidenceItems = importedProject.evidenceItems,
            findings = importedProject.findings,
            evidenceRelations = importedProject.evidenceRelations,
            attachments = importedProject.attachments,
            claims = importedProject.claims,
            limitationActions = importedProject.limitationActions,
            revisionSnapshots = priorHistory,
        )
        val checkpoint = StudentProjectDraftRules.captureRevisionSnapshot(
            restored,
            StudentProjectRevisionActor.STUDENT,
            "Restored project archive revision ${importedProject.revision}",
        )
        val updated = restored.copy(
            revisionSnapshots = StudentProjectDraftRules.retainRevisionSnapshots(priorHistory + checkpoint),
        )
        if (StudentProjectDraftRules.validate(updated).isNotEmpty()) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_IMPORT_INVALID")
        }
        val next = existing.map { if (it.id == current.id) updated else it }
        return when (store.replaceAll(next)) {
            LocalStorageWriteResult.SAVED -> StudentProjectDraftFlowResult.Value(
                StudentProjectImportReceipt(
                    project = updated,
                    importedAsCopy = false,
                    archivedToRespectCapacity = false,
                    restoredAsNewRevision = true,
                ),
            )
            LocalStorageWriteResult.UNAVAILABLE -> StudentProjectDraftFlowResult.StorageUnavailable
            LocalStorageWriteResult.CLEARED,
            LocalStorageWriteResult.FAILED,
            -> StudentProjectDraftFlowResult.StorageFailed
            LocalStorageWriteResult.LIMIT_REACHED ->
                StudentProjectDraftFlowResult.Rejected("PROJECT_DRAFT_STORAGE_LIMIT_REACHED")
        }
    }

    /** Restores the content of an immutable checkpoint as a new revision. */
    fun restoreRevision(
        projectId: String,
        revision: Int,
    ): StudentProjectDraftFlowResult<StudentProjectDraft> {
        val current = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty().singleOrNull { it.id == projectId }
            else -> return loaded.toFlowFailure()
        } ?: return StudentProjectDraftFlowResult.NotFound
        val snapshot = current.revisionSnapshots.singleOrNull { it.revision == revision }
            ?: return StudentProjectDraftFlowResult.Rejected("PROJECT_REVISION_NOT_FOUND")

        val restoredAt = clock().coerceAtLeast(current.createdAtEpochMillis).coerceAtLeast(current.updatedAtEpochMillis)
        var restored = current.copy(
            title = snapshot.title,
            fieldValues = snapshot.fieldValues.toMap(),
            sources = snapshot.sources.toList(),
            themes = snapshot.themes.toList(),
            claimEvidenceSourceIds = snapshot.claimEvidenceSourceIds.toSet(),
            evidenceItems = snapshot.evidenceItems.toList(),
            findings = snapshot.findings.toList(),
            evidenceRelations = snapshot.evidenceRelations.toList(),
            claims = snapshot.claims.toList(),
            limitationActions = snapshot.limitationActions.toList(),
            attachments = snapshot.attachments.toList(),
            deadlineDate = snapshot.deadlineDate,
            revision = current.revision + 1,
            updatedAtEpochMillis = restoredAt,
        )
        val priorSnapshots = current.revisionSnapshots.ifEmpty {
            listOf(StudentProjectDraftRules.captureRevisionSnapshot(
                current,
                StudentProjectRevisionActor.SYSTEM,
                "Existing saved project state",
            ))
        }
        restored = restored.copy(revisionSnapshots = StudentProjectDraftRules.retainRevisionSnapshots(
            priorSnapshots + StudentProjectDraftRules.captureRevisionSnapshot(
                restored,
                StudentProjectRevisionActor.STUDENT,
                "Restored revision $revision",
            ),
        ))
        val issue = StudentProjectDraftRules.validate(restored).firstOrNull()
        if (issue != null) return StudentProjectDraftFlowResult.Rejected(issue)
        return persist(restored)
    }

    fun update(
        projectId: String,
        title: String,
        fieldValues: Map<String, String>,
        sources: List<dev.nextgen.mobile.domain.project.StudentProjectSourceRecord>? = null,
        themes: List<dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme>? = null,
        claimEvidenceSourceIds: Set<String>? = null,
        evidenceItems: List<dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem>? = null,
        findings: List<dev.nextgen.mobile.domain.project.StudentProjectFindingRecord>? = null,
        evidenceRelations: List<dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation>? = null,
        claims: List<StudentProjectClaimRecord>? = null,
        checkpoint: Boolean = true,
        revisionActor: StudentProjectRevisionActor = StudentProjectRevisionActor.STUDENT,
        changeSummary: String? = null,
        attachments: List<StudentProjectAttachmentRef>? = null,
        limitationActions: List<StudentProjectLimitationActionRecord>? = null,
        deadlineChange: StudentProjectDeadlineChange = StudentProjectDeadlineChange.Keep,
    ): StudentProjectDraftFlowResult<StudentProjectDraft> {
        val current = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty().singleOrNull { it.id == projectId }
            else -> return loaded.toFlowFailure()
        } ?: return StudentProjectDraftFlowResult.NotFound

        val updatedAt = clock().coerceAtLeast(current.createdAtEpochMillis).coerceAtLeast(current.updatedAtEpochMillis)
        val nextFieldValues = fieldValues.toMap()
        val nextClaims = claims?.toList() ?: if (
            current.claims.isNotEmpty() &&
            nextFieldValues[dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields.CLAIM] !=
            current.fieldValues[dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields.CLAIM]
        ) {
            val primary = StudentProjectDraftRules.primaryClaim(current.claims)
            if (primary == null) current.claims else current.claims.map { claim ->
                if (claim.id == primary.id) claim.copy(
                    statement = nextFieldValues[dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields.CLAIM].orEmpty(),
                ) else claim
            }
        } else {
            current.claims
        }
        val proposed = current.copy(
            title = title,
            fieldValues = nextFieldValues,
            sources = sources?.toList() ?: current.sources,
            themes = themes?.toList() ?: current.themes,
            claimEvidenceSourceIds = claimEvidenceSourceIds?.toSet() ?: current.claimEvidenceSourceIds,
            evidenceItems = evidenceItems?.toList() ?: current.evidenceItems,
            findings = findings?.toList() ?: current.findings,
            evidenceRelations = evidenceRelations?.toList() ?: current.evidenceRelations,
            claims = nextClaims,
            limitationActions = limitationActions?.toList() ?: current.limitationActions,
            attachments = attachments?.toList() ?: current.attachments,
            deadlineDate = when (deadlineChange) {
                StudentProjectDeadlineChange.Keep -> current.deadlineDate
                is StudentProjectDeadlineChange.Set -> deadlineChange.deadlineDate
            },
            updatedAtEpochMillis = updatedAt,
        )
        val shouldCheckpoint = checkpoint && StudentProjectDraftRules.needsRevisionCheckpoint(proposed)
        var updated = proposed.copy(revision = current.revision + if (shouldCheckpoint) 1 else 0)
        if (shouldCheckpoint) {
            val priorSnapshots = current.revisionSnapshots.ifEmpty {
                listOf(StudentProjectDraftRules.captureRevisionSnapshot(
                    current,
                    StudentProjectRevisionActor.SYSTEM,
                    "Existing saved project state",
                ))
            }
            val summary = changeSummary?.takeIf(String::isNotBlank) ?: revisionChangeSummary(current, proposed)
            val nextSnapshots = priorSnapshots + StudentProjectDraftRules.captureRevisionSnapshot(
                updated,
                revisionActor,
                summary.take(240).ifBlank { "Project checkpoint" },
            )
            updated = updated.copy(revisionSnapshots = StudentProjectDraftRules.retainRevisionSnapshots(nextSnapshots))
        }
        val issue = StudentProjectDraftRules.validate(updated).firstOrNull()
        if (issue != null) return StudentProjectDraftFlowResult.Rejected(issue)
        return persist(updated)
    }

    /**
     * Adds one selected file as a separately stored project attachment. The
     * attachment is not parsed into source/evidence records; the student still
     * enters and links any academic notes explicitly.
     */
    fun addAttachment(
        projectId: String,
        fileName: String,
        bytes: ByteArray,
        ensureActive: () -> Unit = {},
    ): StudentProjectDraftFlowResult<StudentProjectAttachmentAddReceipt> {
        ensureActive()
        val current = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty().singleOrNull { it.id == projectId }
            else -> return loaded.toFlowFailure()
        } ?: return StudentProjectDraftFlowResult.NotFound
        if (current.attachments.size >= StudentProjectAttachmentRules.MAX_ATTACHMENTS) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_ATTACHMENT_LIMIT_REACHED")
        }

        val usedIds = current.attachments.mapTo(mutableSetOf(), StudentProjectAttachmentRef::id)
        val reference = when (val prepared = StudentProjectArchiveCodec.prepareSelectedAttachmentReference(
            id = freshId(usedIds),
            fileName = fileName,
            bytes = bytes,
        )) {
            is StudentProjectAttachmentReferenceResult.Ready -> prepared.reference
            is StudentProjectAttachmentReferenceResult.Rejected -> return StudentProjectDraftFlowResult.Rejected(prepared.code)
        }
        ensureActive()
        val session = when (val opened = attachmentStore.beginImport(projectId)) {
            is StudentProjectAttachmentImportResult.Started -> opened.session
            StudentProjectAttachmentImportResult.Unavailable -> return StudentProjectDraftFlowResult.StorageUnavailable
            is StudentProjectAttachmentImportResult.Failed -> return StudentProjectDraftFlowResult.StorageFailed
        }
        val writer = when (val opened = session.openWriter(reference)) {
            is StudentProjectAttachmentWriterResult.Opened -> opened.writer
            StudentProjectAttachmentWriterResult.Unavailable ->
                return rollbackAttachmentSession(session, StudentProjectDraftFlowResult.StorageUnavailable)
            is StudentProjectAttachmentWriterResult.Failed ->
                return rollbackAttachmentSession(session, StudentProjectDraftFlowResult.StorageFailed)
        }

        try {
            var offset = 0
            while (offset < bytes.size) {
                ensureActive()
                val count = minOf(STUDENT_PROJECT_ATTACHMENT_MAX_IO_CHUNK_BYTES, bytes.size - offset)
                writer.write(bytes, offset, count)
                offset += count
            }
            ensureActive()
            if (writer.finish() != LocalStorageWriteResult.SAVED) {
                writer.abort()
                return rollbackAttachmentSession(session, StudentProjectDraftFlowResult.StorageFailed)
            }
            ensureActive()
            if (session.publish() != LocalStorageWriteResult.SAVED) {
                return rollbackAttachmentSession(session, StudentProjectDraftFlowResult.StorageFailed)
            }
            ensureActive()
        } catch (cancelled: CancellationException) {
            writer.abort()
            val rollback = rollbackAttachmentSession(session, StudentProjectDraftFlowResult.StorageFailed)
            if (rollback is StudentProjectDraftFlowResult.Rejected) return rollback
            throw cancelled
        } catch (_: Exception) {
            writer.abort()
            return rollbackAttachmentSession(session, StudentProjectDraftFlowResult.StorageFailed)
        }

        val updated = when (val result = update(
            projectId = projectId,
            title = current.title,
            fieldValues = current.fieldValues,
            sources = current.sources,
            themes = current.themes,
            claimEvidenceSourceIds = current.claimEvidenceSourceIds,
            evidenceItems = current.evidenceItems,
            findings = current.findings,
            evidenceRelations = current.evidenceRelations,
            claims = current.claims,
            checkpoint = true,
            changeSummary = "Added a project file attachment",
            attachments = current.attachments + reference,
        )) {
            is StudentProjectDraftFlowResult.Value -> result.value
            else -> {
                if (!session.rollback().isSuccessfulStorageWrite()) {
                    return StudentProjectDraftFlowResult.Rejected("PROJECT_ATTACHMENT_ROLLBACK_FAILED")
                }
                return when (result) {
                    is StudentProjectDraftFlowResult.Rejected -> result
                    StudentProjectDraftFlowResult.NotFound -> StudentProjectDraftFlowResult.NotFound
                    StudentProjectDraftFlowResult.StorageUnavailable -> StudentProjectDraftFlowResult.StorageUnavailable
                    StudentProjectDraftFlowResult.StorageCorrupt -> StudentProjectDraftFlowResult.StorageCorrupt
                    StudentProjectDraftFlowResult.StorageFailed -> StudentProjectDraftFlowResult.StorageFailed
                    is StudentProjectDraftFlowResult.Value -> error("Value result was handled above")
                }
            }
        }
        val cleanupPending = !session.complete().isSuccessfulStorageWrite()
        return StudentProjectDraftFlowResult.Value(StudentProjectAttachmentAddReceipt(updated, cleanupPending))
    }

    /** Removes an attachment from the current project revision without rewriting older snapshots. */
    fun removeAttachment(
        projectId: String,
        attachmentId: String,
    ): StudentProjectDraftFlowResult<StudentProjectAttachmentRemoveReceipt> {
        val current = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty().singleOrNull { it.id == projectId }
            else -> return loaded.toFlowFailure()
        } ?: return StudentProjectDraftFlowResult.NotFound
        if (current.attachments.none { it.id == attachmentId }) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_ATTACHMENT_NOT_ATTACHED")
        }

        val prepared = attachmentStore.prepareAttachmentDeletion(projectId, attachmentId)
        if (!prepared.isSuccessfulStorageWrite()) return prepared.toFlowFailure()

        val updated = when (val result = update(
            projectId = projectId,
            title = current.title,
            fieldValues = current.fieldValues,
            sources = current.sources,
            themes = current.themes,
            claimEvidenceSourceIds = current.claimEvidenceSourceIds,
            evidenceItems = current.evidenceItems,
            findings = current.findings,
            evidenceRelations = current.evidenceRelations,
            claims = current.claims,
            checkpoint = true,
            changeSummary = "Removed a file from the current project revision",
            attachments = current.attachments.filterNot { it.id == attachmentId },
            limitationActions = current.limitationActions,
        )) {
            is StudentProjectDraftFlowResult.Value -> result.value
            else -> {
                val canceled = attachmentStore.cancelAttachmentDeletion(projectId, attachmentId)
                if (!canceled.isSuccessfulStorageWrite()) {
                    val recovered = recoverPendingDeletionsAfterMetadataFailure()
                    if (recovered == null || !recovered.isSuccessfulStorageWrite()) {
                        return StudentProjectDraftFlowResult.Rejected("PROJECT_ATTACHMENT_REMOVE_RECOVERY_FAILED")
                    }
                }
                return when (result) {
                    is StudentProjectDraftFlowResult.Rejected -> result
                    StudentProjectDraftFlowResult.NotFound -> StudentProjectDraftFlowResult.NotFound
                    StudentProjectDraftFlowResult.StorageUnavailable -> StudentProjectDraftFlowResult.StorageUnavailable
                    StudentProjectDraftFlowResult.StorageCorrupt -> StudentProjectDraftFlowResult.StorageCorrupt
                    StudentProjectDraftFlowResult.StorageFailed -> StudentProjectDraftFlowResult.StorageFailed
                    is StudentProjectDraftFlowResult.Value -> error("Value result was handled above")
                }
            }
        }

        val recovery = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> recoverPendingAttachmentDeletions(loaded.value.orEmpty())
            else -> LocalStorageWriteResult.FAILED
        }
        val preservedInHistory = updated.revisionSnapshots.any { snapshot ->
            snapshot.attachments.any { it.id == attachmentId }
        }
        return StudentProjectDraftFlowResult.Value(
            StudentProjectAttachmentRemoveReceipt(
                project = updated,
                preservedInRevisionHistory = preservedInHistory,
                cleanupPending = !recovery.isSuccessfulStorageWrite(),
            ),
        )
    }

    private fun rollbackAttachmentSession(
        session: StudentProjectAttachmentImportSession,
        failure: StudentProjectDraftFlowResult<Nothing>,
    ): StudentProjectDraftFlowResult<Nothing> =
        if (session.rollback().isSuccessfulStorageWrite()) failure
        else StudentProjectDraftFlowResult.Rejected("PROJECT_ATTACHMENT_ROLLBACK_FAILED")

    fun delete(projectId: String): StudentProjectDraftFlowResult<Unit> = moveToTrash(projectId)

    fun moveToTrash(projectId: String): StudentProjectDraftFlowResult<Unit> = updateStatus(
        projectId = projectId,
        status = StudentProjectStatus.TRASHED,
    )

    fun archive(projectId: String): StudentProjectDraftFlowResult<Unit> =
        updateStatus(projectId, StudentProjectStatus.ARCHIVED)

    fun markCompleted(projectId: String): StudentProjectDraftFlowResult<Unit> =
        updateStatus(projectId, StudentProjectStatus.COMPLETED)

    fun reactivate(projectId: String): StudentProjectDraftFlowResult<Unit> =
        updateStatus(projectId, StudentProjectStatus.ACTIVE)

    fun restoreFromTrash(
        projectId: String,
        asArchivedWhenAtLimit: Boolean = false,
    ): StudentProjectDraftFlowResult<Unit> {
        val current = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty().singleOrNull { it.id == projectId }
            else -> return loaded.toFlowFailure()
        } ?: return StudentProjectDraftFlowResult.NotFound

        if (current.status != StudentProjectStatus.TRASHED) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_NOT_IN_TRASH")
        }
        val trashedAt = current.trashedAtEpochMillis ?: return StudentProjectDraftFlowResult.Rejected("PROJECT_TRASH_TIMESTAMP_REQUIRED")
        if (clock() >= StudentProjectDraftRules.TRASH_RETENTION_MILLIS &&
            trashedAt <= clock() - StudentProjectDraftRules.TRASH_RETENTION_MILLIS
        ) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_TRASH_RETENTION_EXPIRED")
        }
        val existing = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty()
            else -> return loaded.toFlowFailure()
        }
        val hasCapacity = true // Restoring an existing stored record consumes no new slot.
        if (!hasCapacity && !asArchivedWhenAtLimit) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_ACTIVE_LIMIT_REACHED")
        }
        return saveStatus(
            current.copy(
                status = if (hasCapacity) StudentProjectStatus.DRAFT else StudentProjectStatus.ARCHIVED,
                trashedAtEpochMillis = null,
                revision = current.revision + 1,
                updatedAtEpochMillis = clock().coerceAtLeast(current.updatedAtEpochMillis),
            ),
        )
    }

    fun permanentlyDelete(projectId: String): StudentProjectDraftFlowResult<Unit> {
        val projects = when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> loaded.value.orEmpty()
            else -> return loaded.toFlowFailure()
        }
        val current = projects.singleOrNull { it.id == projectId } ?: return StudentProjectDraftFlowResult.NotFound

        val prepared = attachmentStore.prepareProjectDeletion(
            current.id,
            current.hasAttachmentReferences(),
        )
        if (!prepared.isSuccessfulStorageWrite()) {
            val recovery = attachmentStore.recoverPendingProjectDeletions(
                projects.mapTo(mutableSetOf(), StudentProjectDraft::id),
            )
            return (if (recovery.isSuccessfulStorageWrite()) prepared else recovery).toFlowFailure()
        }

        val metadataDelete = store.delete(current.id)
        if (!metadataDelete.isSuccessfulStorageWrite()) {
            val recovery = recoverPendingDeletionsAfterMetadataFailure()
            return (if (recovery == null || recovery.isSuccessfulStorageWrite()) metadataDelete else recovery)
                .toFlowFailure()
        }

        return when (val cleanup = attachmentStore.completeProjectDeletion(current.id)) {
            LocalStorageWriteResult.SAVED,
            LocalStorageWriteResult.CLEARED,
            -> StudentProjectDraftFlowResult.Value(Unit)
            LocalStorageWriteResult.UNAVAILABLE -> StudentProjectDraftFlowResult.StorageUnavailable
            LocalStorageWriteResult.FAILED -> StudentProjectDraftFlowResult.StorageFailed
            LocalStorageWriteResult.LIMIT_REACHED -> StudentProjectDraftFlowResult.StorageFailed
        }
    }

    private fun updateStatus(
        projectId: String,
        status: StudentProjectStatus,
    ): StudentProjectDraftFlowResult<Unit> {
        val loaded = when (val result = store.load()) {
            is LocalStorageReadResult.Success -> result.value.orEmpty()
            else -> return result.toFlowFailure()
        }
        val current = loaded.singleOrNull { it.id == projectId } ?: return StudentProjectDraftFlowResult.NotFound
        if (current.status == StudentProjectStatus.TRASHED && status != StudentProjectStatus.TRASHED) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_RESTORE_REQUIRED")
        }
        val timestamp = clock().coerceAtLeast(current.updatedAtEpochMillis)
        val updated = current.copy(
            status = status,
            trashedAtEpochMillis = if (status == StudentProjectStatus.TRASHED) timestamp else null,
            revision = current.revision + 1,
            updatedAtEpochMillis = timestamp,
        )
        val snapshots = current.revisionSnapshots.ifEmpty {
            listOf(StudentProjectDraftRules.captureRevisionSnapshot(
                current,
                StudentProjectRevisionActor.SYSTEM,
                "Existing saved project state",
            ))
        }
        val summary = when (status) {
            StudentProjectStatus.DRAFT -> "Project restored to draft"
            StudentProjectStatus.ACTIVE -> "Project reactivated"
            StudentProjectStatus.COMPLETED -> "Project marked completed"
            StudentProjectStatus.ARCHIVED -> "Project archived"
            StudentProjectStatus.TRASHED -> "Project moved to trash"
        }
        val checkpointed = updated.copy(revisionSnapshots = StudentProjectDraftRules.retainRevisionSnapshots(
            snapshots + StudentProjectDraftRules.captureRevisionSnapshot(
                updated,
                StudentProjectRevisionActor.SYSTEM,
                summary,
            ),
        ))
        return saveStatus(checkpointed)
    }

    private fun revisionChangeSummary(
        current: StudentProjectDraft,
        proposed: StudentProjectDraft,
    ): String {
        val previous = current.revisionSnapshots.lastOrNull()
        val fields = buildList {
            if ((previous?.title ?: current.title) != proposed.title) add("project name")
            if ((previous?.fieldValues ?: current.fieldValues) != proposed.fieldValues) add("project sections")
            if ((previous?.sources ?: current.sources) != proposed.sources) add("sources")
            if ((previous?.evidenceItems ?: current.evidenceItems) != proposed.evidenceItems) add("evidence notes")
            if ((previous?.findings ?: current.findings) != proposed.findings) add("findings")
            if ((previous?.claims ?: current.claims) != proposed.claims) add("claims")
            if ((previous?.evidenceRelations ?: current.evidenceRelations) != proposed.evidenceRelations) add("evidence links")
            if ((previous?.limitationActions ?: current.limitationActions) != proposed.limitationActions) add("limitations and next actions")
            if ((previous?.themes ?: current.themes) != proposed.themes) add("comparison notes")
            if ((previous?.claimEvidenceSourceIds ?: current.claimEvidenceSourceIds) != proposed.claimEvidenceSourceIds) {
                add("claim source links")
            }
            if ((if (previous == null) current.deadlineDate else previous.deadlineDate) != proposed.deadlineDate) {
                add("project deadline")
            }
        }
        return fields.joinToString(", ").ifBlank { "Project checkpoint" }
    }

    private fun saveStatus(draft: StudentProjectDraft): StudentProjectDraftFlowResult<Unit> = when (store.save(draft)) {
        LocalStorageWriteResult.SAVED -> StudentProjectDraftFlowResult.Value(Unit)
        LocalStorageWriteResult.CLEARED -> StudentProjectDraftFlowResult.StorageFailed
        LocalStorageWriteResult.UNAVAILABLE -> StudentProjectDraftFlowResult.StorageUnavailable
        LocalStorageWriteResult.FAILED -> StudentProjectDraftFlowResult.StorageFailed
        LocalStorageWriteResult.LIMIT_REACHED ->
            StudentProjectDraftFlowResult.Rejected("PROJECT_DRAFT_STORAGE_LIMIT_REACHED")
    }

    private fun isAtActiveLimit(projects: List<StudentProjectDraft>): Boolean =
        projects.size >= StudentProjectDraftRules.projectLimit(hasVerifiedProEntitlement())

    private fun StudentProjectDraft.copyWithFreshIds(existingProjectIds: Set<String>): StudentProjectDraft {
        val usedProjectIds = existingProjectIds + id
        val copiedProjectId = freshId(usedProjectIds)
        val allSnapshots = revisionSnapshots
        val sourceIds = (sources + allSnapshots.flatMap(StudentProjectRevisionSnapshot::sources))
            .map(StudentProjectSourceRecord::id).toSet()
        val themeIds = (themes + allSnapshots.flatMap(StudentProjectRevisionSnapshot::themes))
            .map(StudentProjectSynthesisTheme::id).toSet()
        val evidenceIds = (evidenceItems + allSnapshots.flatMap(StudentProjectRevisionSnapshot::evidenceItems))
            .map(StudentProjectEvidenceItem::id).toSet()
        val findingIds = (findings + allSnapshots.flatMap(StudentProjectRevisionSnapshot::findings))
            .map(StudentProjectFindingRecord::id).toSet()
        val claimIds = (claims + allSnapshots.flatMap(StudentProjectRevisionSnapshot::claims))
            .map(StudentProjectClaimRecord::id).toSet()
        val limitationActionIds = (limitationActions + allSnapshots.flatMap(StudentProjectRevisionSnapshot::limitationActions))
            .map(StudentProjectLimitationActionRecord::id).toSet()
        val attachmentIds = (attachments + allSnapshots.flatMap(StudentProjectRevisionSnapshot::attachments))
            .map(StudentProjectAttachmentRef::id).toSet()
        val sourceIdMap = remapIds(sourceIds)
        val themeIdMap = remapIds(themeIds)
        val evidenceIdMap = remapIds(evidenceIds)
        val findingIdMap = remapIds(findingIds)
        val claimIdMap = remapIds(claimIds)
        val limitationActionIdMap = remapIds(limitationActionIds)
        val attachmentIdMap = remapIds(attachmentIds)

        fun StudentProjectSourceRecord.withFreshId() = copy(id = sourceIdMap.getValue(id))
        fun StudentProjectSynthesisTheme.withFreshIds() = copy(
            id = themeIdMap.getValue(id),
            sourceIds = sourceIds.mapTo(mutableSetOf()) { sourceIdMap.getValue(it) },
        )
        fun StudentProjectEvidenceItem.withFreshIds() = copy(
            id = evidenceIdMap.getValue(id),
            sourceId = sourceIdMap.getValue(sourceId),
        )
        fun StudentProjectFindingRecord.withFreshId() = copy(id = findingIdMap.getValue(id))
        fun StudentProjectClaimRecord.withFreshId() = copy(id = claimIdMap.getValue(id))
        fun StudentProjectLimitationActionRecord.withFreshIds() = copy(
            id = limitationActionIdMap.getValue(id),
            affectedFindingIds = affectedFindingIds.mapTo(mutableSetOf()) { findingIdMap.getValue(it) },
            affectedClaimIds = affectedClaimIds.mapTo(mutableSetOf()) { claimIdMap[it] ?: it },
        )
        fun StudentProjectAttachmentRef.withFreshId() = copy(id = attachmentIdMap.getValue(id))
        fun StudentProjectEvidenceRelation.withFreshIds() = copy(
            targetId = if (targetType == StudentProjectEvidenceTargetType.FINDING) {
                findingIdMap.getValue(targetId)
            } else if (targetType == StudentProjectEvidenceTargetType.CLAIM && targetId in claimIdMap) {
                claimIdMap.getValue(targetId)
            } else {
                targetId
            },
            evidenceId = evidenceIdMap.getValue(evidenceId),
        )
        fun StudentProjectRevisionSnapshot.withFreshIds() = copy(
            sources = sources.map { it.withFreshId() },
            themes = themes.map { it.withFreshIds() },
            claimEvidenceSourceIds = claimEvidenceSourceIds.mapTo(mutableSetOf()) { sourceIdMap.getValue(it) },
            evidenceItems = evidenceItems.map { it.withFreshIds() },
            findings = findings.map { it.withFreshId() },
            claims = claims.map { it.withFreshId() },
            limitationActions = limitationActions.map { it.withFreshIds() },
            evidenceRelations = evidenceRelations.map { it.withFreshIds() },
            attachments = attachments.map { it.withFreshId() },
        )

        return copy(
            id = copiedProjectId,
            sources = sources.map { it.withFreshId() },
            themes = themes.map { it.withFreshIds() },
            claimEvidenceSourceIds = claimEvidenceSourceIds.mapTo(mutableSetOf()) { sourceIdMap.getValue(it) },
            evidenceItems = evidenceItems.map { it.withFreshIds() },
            findings = findings.map { it.withFreshId() },
            claims = claims.map { it.withFreshId() },
            evidenceRelations = evidenceRelations.map { it.withFreshIds() },
            attachments = attachments.map { it.withFreshId() },
            limitationActions = limitationActions.map { it.withFreshIds() },
            revisionSnapshots = allSnapshots.map { it.withFreshIds() },
        )
    }

    private fun freshId(usedIds: Set<String>): String {
        repeat(16) {
            val candidate = idGenerator()
            if (candidate !in usedIds) return candidate
        }
        error("Could not generate an unused project identifier")
    }

    private fun remapIds(ids: Set<String>): Map<String, String> {
        val usedIds = ids.toMutableSet()
        return ids.associateWith { oldId ->
            val generated = freshId(usedIds)
            usedIds += generated
            generated
        }
    }

    private fun persist(draft: StudentProjectDraft): StudentProjectDraftFlowResult<StudentProjectDraft> =
        when (store.save(draft)) {
            LocalStorageWriteResult.SAVED -> StudentProjectDraftFlowResult.Value(draft)
            LocalStorageWriteResult.CLEARED -> StudentProjectDraftFlowResult.StorageFailed
            LocalStorageWriteResult.UNAVAILABLE -> StudentProjectDraftFlowResult.StorageUnavailable
            LocalStorageWriteResult.FAILED -> StudentProjectDraftFlowResult.StorageFailed
            LocalStorageWriteResult.LIMIT_REACHED ->
                StudentProjectDraftFlowResult.Rejected("PROJECT_DRAFT_STORAGE_LIMIT_REACHED")
        }

    private fun recoverPendingDeletionsAfterMetadataFailure(): LocalStorageWriteResult? =
        when (val loaded = store.load()) {
            is LocalStorageReadResult.Success -> {
                val projects = loaded.value.orEmpty()
                val projectRecovery = attachmentStore.recoverPendingProjectDeletions(
                    projects.mapTo(mutableSetOf(), StudentProjectDraft::id),
                )
                if (!projectRecovery.isSuccessfulStorageWrite()) {
                    projectRecovery
                } else {
                    recoverPendingAttachmentDeletions(projects)
                }
            }
            else -> null
        }

    private fun recoverPendingAttachmentDeletions(
        projects: List<StudentProjectDraft>,
    ): LocalStorageWriteResult = attachmentStore.recoverPendingAttachmentDeletions(
        projects.associate { project ->
            project.id to buildSet {
                project.attachments.forEach { add(it.id) }
                project.revisionSnapshots.forEach { snapshot -> snapshot.attachments.forEach { add(it.id) } }
            }
        },
    )

    private fun LocalStorageWriteResult.isSuccessfulStorageWrite(): Boolean =
        this == LocalStorageWriteResult.SAVED || this == LocalStorageWriteResult.CLEARED

    private fun LocalStorageWriteResult.toFlowFailure(): StudentProjectDraftFlowResult<Nothing> =
        if (this == LocalStorageWriteResult.UNAVAILABLE) {
            StudentProjectDraftFlowResult.StorageUnavailable
        } else {
            StudentProjectDraftFlowResult.StorageFailed
        }

    private fun StudentProjectDraft.hasAttachmentReferences(): Boolean =
        attachments.isNotEmpty() || revisionSnapshots.any { it.attachments.isNotEmpty() }

    private fun LocalStorageReadResult<*>.toFlowFailure(): StudentProjectDraftFlowResult<Nothing> = when (status) {
        LocalStorageStatus.UNAVAILABLE -> StudentProjectDraftFlowResult.StorageUnavailable
        LocalStorageStatus.CORRUPT,
        LocalStorageStatus.REPAIRED,
        -> StudentProjectDraftFlowResult.StorageCorrupt
        else -> StudentProjectDraftFlowResult.StorageFailed
    }
}
