package dev.nextgen.mobile.storage

import android.content.Context
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRules
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID

private const val STORE_DIRECTORY = "evidrilo_project_attachments_v1"
private const val PROJECTS_DIRECTORY = "projects"
private const val STAGING_DIRECTORY = "staging"
private const val ATTACHMENTS_DIRECTORY = "attachments"
private const val DELETIONS_DIRECTORY = "deletions"
private const val ATTACHMENT_DELETIONS_DIRECTORY = "attachment-deletions"
private const val BLOB_SUFFIX = ".blob"
private const val STAGING_SUFFIX = ".part"
private const val PREPARED_DELETION_SUFFIX = ".prepared"
private const val COMMITTED_DELETION_SUFFIX = ".committed"
private const val PREPARED_ATTACHMENT_DELETION_SUFFIX = ".attachment-prepared"
private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,96}")
private val attachmentMutationLock = Any()
private enum class AttachmentImportState { OPEN, PUBLISHED, COMPLETED, ROLLED_BACK }
private enum class ProjectDeletionState { NONE, PREPARED, COMMITTED, INVALID }
private enum class AttachmentDeletionState { NONE, PREPARED, INVALID }

/** Initializes the app-private root before [createStudentProjectAttachmentStore] is called. */
object AndroidStudentProjectAttachmentStorage {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun createStore(): StudentProjectAttachmentStore = applicationContext
        ?.let { AndroidStudentProjectAttachmentStore(it.filesDir) }
        ?: UnavailableStudentProjectAttachmentStore()
}

actual fun createStudentProjectAttachmentStore(): StudentProjectAttachmentStore =
    AndroidStudentProjectAttachmentStorage.createStore()

/**
 * Attachment bytes live below Android's private files directory. Archive entry paths and display
 * file names are never used as filesystem paths; only validated project/attachment IDs are used.
 */
internal class AndroidStudentProjectAttachmentStore(
    private val privateFilesDirectory: File,
) : StudentProjectAttachmentStore {
    override fun openRead(
        projectId: String,
        attachmentId: String,
    ): StudentProjectAttachmentReadResult = synchronized(attachmentMutationLock) {
        runCatching {
            if (!isSafeId(projectId) || !isSafeId(attachmentId)) {
                return@runCatching StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_ID_INVALID")
            }
            when (projectDeletionState(projectId)) {
                ProjectDeletionState.PREPARED,
                ProjectDeletionState.COMMITTED,
                -> return@runCatching StudentProjectAttachmentReadResult.Failed(
                    "PROJECT_ATTACHMENT_DELETION_PENDING",
                )
                ProjectDeletionState.INVALID ->
                    return@runCatching StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_STORAGE_INVALID")
                ProjectDeletionState.NONE -> Unit
            }
            when (attachmentDeletionState(projectId, attachmentId)) {
                AttachmentDeletionState.PREPARED -> return@runCatching StudentProjectAttachmentReadResult.Failed(
                    "PROJECT_ATTACHMENT_DELETION_PENDING",
                )
                AttachmentDeletionState.INVALID -> return@runCatching StudentProjectAttachmentReadResult.Failed(
                    "PROJECT_ATTACHMENT_STORAGE_INVALID",
                )
                AttachmentDeletionState.NONE -> Unit
            }
            when (val directory = attachmentDirectory(projectId, create = false)) {
                DirectoryLookup.Missing -> StudentProjectAttachmentReadResult.Missing
                DirectoryLookup.Failed -> StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_STORAGE_INVALID")
                is DirectoryLookup.Ready -> {
                    val candidate = File(directory.value, "$attachmentId$BLOB_SUFFIX")
                    when {
                        Files.isSymbolicLink(candidate.toPath()) ->
                            StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_STORAGE_INVALID")

                        !candidate.exists() -> StudentProjectAttachmentReadResult.Missing
                        !isSafeChild(directory.value, candidate) ||
                            !Files.isRegularFile(candidate.toPath(), LinkOption.NOFOLLOW_LINKS) ->
                            StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_STORAGE_INVALID")

                        candidate.length() !in 1..StudentProjectAttachmentRules.MAX_ATTACHMENT_BYTES ->
                            StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_SIZE_INVALID")

                        else -> StudentProjectAttachmentReadResult.Opened(
                            AndroidStudentProjectAttachmentReadHandle(
                                FileInputStream(candidate),
                                candidate.length(),
                            ),
                        )
                    }
                }
            }
        }.getOrElse {
            StudentProjectAttachmentReadResult.Failed("PROJECT_ATTACHMENT_READ_FAILED")
        }
    }

    override fun beginImport(projectId: String): StudentProjectAttachmentImportResult = runCatching {
        if (!isSafeId(projectId)) {
            return@runCatching StudentProjectAttachmentImportResult.Failed("PROJECT_ATTACHMENT_PROJECT_ID_INVALID")
        }
        synchronized(attachmentMutationLock) {
            when (projectDeletionState(projectId)) {
                ProjectDeletionState.PREPARED,
                ProjectDeletionState.COMMITTED,
                -> return@synchronized StudentProjectAttachmentImportResult.Failed(
                    "PROJECT_ATTACHMENT_DELETION_PENDING",
                )
                ProjectDeletionState.INVALID ->
                    return@synchronized StudentProjectAttachmentImportResult.Failed("PROJECT_ATTACHMENT_STORAGE_INVALID")
                ProjectDeletionState.NONE -> Unit
            }
            val root = rootDirectory(create = true)
                ?: return@synchronized StudentProjectAttachmentImportResult.Failed("PROJECT_ATTACHMENT_STORAGE_UNAVAILABLE")
            val staging = directory(root, STAGING_DIRECTORY, create = true)
                ?: return@synchronized StudentProjectAttachmentImportResult.Failed("PROJECT_ATTACHMENT_STORAGE_UNAVAILABLE")
            val projectStaging = directory(staging, projectId, create = true)
                ?: return@synchronized StudentProjectAttachmentImportResult.Failed("PROJECT_ATTACHMENT_STORAGE_UNAVAILABLE")
            repeat(4) {
                val transaction = File(projectStaging, UUID.randomUUID().toString())
                if (transaction.mkdir() && isSafeChild(projectStaging, transaction)) {
                    return@synchronized StudentProjectAttachmentImportResult.Started(
                        AndroidStudentProjectAttachmentImportSession(this, projectId, transaction),
                    )
                }
            }
            StudentProjectAttachmentImportResult.Failed("PROJECT_ATTACHMENT_STAGE_UNAVAILABLE")
        }
    }.getOrElse {
        StudentProjectAttachmentImportResult.Failed("PROJECT_ATTACHMENT_STAGE_FAILED")
    }

    override fun prepareProjectDeletion(
        projectId: String,
        hasAttachments: Boolean,
    ): LocalStorageWriteResult =
        synchronized(attachmentMutationLock) {
            if (!isSafeId(projectId)) return@synchronized LocalStorageWriteResult.FAILED
            when (projectDeletionState(projectId)) {
                ProjectDeletionState.PREPARED,
                ProjectDeletionState.COMMITTED,
                -> return@synchronized LocalStorageWriteResult.SAVED
                ProjectDeletionState.INVALID -> return@synchronized LocalStorageWriteResult.FAILED
                ProjectDeletionState.NONE -> Unit
            }
            val root = rootDirectory(create = true) ?: return@synchronized LocalStorageWriteResult.FAILED
            val markerDirectory = directory(root, DELETIONS_DIRECTORY, create = true)
                ?: return@synchronized LocalStorageWriteResult.FAILED
            val marker = File(markerDirectory, "$projectId$PREPARED_DELETION_SUFFIX")
            if (!isSafeChild(markerDirectory, marker)) return@synchronized LocalStorageWriteResult.FAILED
            if (marker.exists()) {
                if (!isValidMarker(marker)) return@synchronized LocalStorageWriteResult.FAILED
                LocalStorageWriteResult.SAVED
            } else if (writeMarker(marker)) {
                LocalStorageWriteResult.SAVED
            } else {
                LocalStorageWriteResult.FAILED
            }
        }

    override fun cancelProjectDeletion(projectId: String): LocalStorageWriteResult =
        synchronized(attachmentMutationLock) {
            if (!isSafeId(projectId)) return@synchronized LocalStorageWriteResult.FAILED
            when (projectDeletionState(projectId)) {
                ProjectDeletionState.NONE -> return@synchronized LocalStorageWriteResult.CLEARED
                ProjectDeletionState.INVALID,
                ProjectDeletionState.COMMITTED,
                -> return@synchronized LocalStorageWriteResult.FAILED
                ProjectDeletionState.PREPARED -> Unit
            }
            val marker = deletionMarker(projectId, PREPARED_DELETION_SUFFIX, createDirectory = false)
                ?: return@synchronized LocalStorageWriteResult.FAILED
            if (!marker.exists()) return@synchronized LocalStorageWriteResult.CLEARED
            if (!isValidMarker(marker) || !marker.delete()) LocalStorageWriteResult.FAILED
            else LocalStorageWriteResult.CLEARED
        }

    override fun completeProjectDeletion(projectId: String): LocalStorageWriteResult =
        synchronized(attachmentMutationLock) { completeProjectDeletionLocked(projectId) }

    override fun recoverPendingProjectDeletions(
        existingProjectIds: Set<String>,
    ): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
        if (existingProjectIds.any { !isSafeId(it) }) return@synchronized LocalStorageWriteResult.FAILED
        val markerDirectory = existingMarkerDirectory() ?: return@synchronized markerDirectoryStatus()
        val entries = markerDirectory.listFiles() ?: return@synchronized LocalStorageWriteResult.FAILED
        val pending = mutableMapOf<String, MutableSet<String>>()
        for (entry in entries) {
            if (Files.isSymbolicLink(entry.toPath()) ||
                !Files.isRegularFile(entry.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                !isSafeChild(markerDirectory, entry)
            ) return@synchronized LocalStorageWriteResult.FAILED
            val suffix = when {
                entry.name.endsWith(PREPARED_DELETION_SUFFIX) -> PREPARED_DELETION_SUFFIX
                entry.name.endsWith(COMMITTED_DELETION_SUFFIX) -> COMMITTED_DELETION_SUFFIX
                else -> return@synchronized LocalStorageWriteResult.FAILED
            }
            val id = entry.name.removeSuffix(suffix)
            if (!isSafeId(id)) return@synchronized LocalStorageWriteResult.FAILED
            pending.getOrPut(id) { mutableSetOf() } += suffix
        }

        var changed = false
        var failed = false
        pending.forEach { (id, states) ->
            val result = if (COMMITTED_DELETION_SUFFIX in states || id !in existingProjectIds) {
                completeProjectDeletionLocked(id)
            } else {
                cancelProjectDeletion(id)
            }
            if (result == LocalStorageWriteResult.FAILED || result == LocalStorageWriteResult.UNAVAILABLE) {
                failed = true
            } else {
                changed = true
            }
        }
        when {
            failed -> LocalStorageWriteResult.FAILED
            changed -> LocalStorageWriteResult.SAVED
            else -> LocalStorageWriteResult.CLEARED
        }
    }

    override fun prepareAttachmentDeletion(
        projectId: String,
        attachmentId: String,
    ): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
        if (!isSafeId(projectId) || !isSafeId(attachmentId)) return@synchronized LocalStorageWriteResult.FAILED
        if (projectDeletionState(projectId) != ProjectDeletionState.NONE) {
            return@synchronized LocalStorageWriteResult.FAILED
        }
        when (attachmentDeletionState(projectId, attachmentId)) {
            AttachmentDeletionState.PREPARED -> return@synchronized LocalStorageWriteResult.SAVED
            AttachmentDeletionState.INVALID -> return@synchronized LocalStorageWriteResult.FAILED
            AttachmentDeletionState.NONE -> Unit
        }
        val marker = attachmentDeletionMarker(projectId, attachmentId, createDirectories = true)
            ?: return@synchronized LocalStorageWriteResult.FAILED
        if (writeMarker(marker)) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }

    override fun cancelAttachmentDeletion(
        projectId: String,
        attachmentId: String,
    ): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
        if (!isSafeId(projectId) || !isSafeId(attachmentId)) return@synchronized LocalStorageWriteResult.FAILED
        when (attachmentDeletionState(projectId, attachmentId)) {
            AttachmentDeletionState.NONE -> return@synchronized LocalStorageWriteResult.CLEARED
            AttachmentDeletionState.INVALID -> return@synchronized LocalStorageWriteResult.FAILED
            AttachmentDeletionState.PREPARED -> Unit
        }
        val marker = attachmentDeletionMarker(projectId, attachmentId, createDirectories = false)
            ?: return@synchronized LocalStorageWriteResult.FAILED
        if (!isValidMarker(marker) || !marker.delete()) LocalStorageWriteResult.FAILED
        else LocalStorageWriteResult.CLEARED
    }

    override fun completeAttachmentDeletion(
        projectId: String,
        attachmentId: String,
    ): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
        if (!isSafeId(projectId) || !isSafeId(attachmentId)) return@synchronized LocalStorageWriteResult.FAILED
        if (projectDeletionState(projectId) != ProjectDeletionState.NONE) {
            return@synchronized LocalStorageWriteResult.FAILED
        }
        when (attachmentDeletionState(projectId, attachmentId)) {
            AttachmentDeletionState.INVALID -> return@synchronized LocalStorageWriteResult.FAILED
            AttachmentDeletionState.NONE -> return@synchronized if (attachmentBlobExists(projectId, attachmentId) == false) {
                LocalStorageWriteResult.CLEARED
            } else {
                LocalStorageWriteResult.FAILED
            }
            AttachmentDeletionState.PREPARED -> Unit
        }
        if (!deleteAttachmentBlob(projectId, attachmentId)) return@synchronized LocalStorageWriteResult.FAILED
        val marker = attachmentDeletionMarker(projectId, attachmentId, createDirectories = false)
            ?: return@synchronized LocalStorageWriteResult.FAILED
        if (!isValidMarker(marker) || !marker.delete()) LocalStorageWriteResult.FAILED
        else LocalStorageWriteResult.SAVED
    }

    override fun recoverPendingAttachmentDeletions(
        existingAttachmentIdsByProject: Map<String, Set<String>>,
    ): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
        if (existingAttachmentIdsByProject.any { (projectId, ids) ->
                !isSafeId(projectId) || ids.any { !isSafeId(it) }
            }
        ) return@synchronized LocalStorageWriteResult.FAILED
        val attachmentDeletionRoot = when (val lookup = attachmentDeletionRootLookup(create = false)) {
            DirectoryLookup.Missing -> return@synchronized LocalStorageWriteResult.CLEARED
            DirectoryLookup.Failed -> return@synchronized LocalStorageWriteResult.FAILED
            is DirectoryLookup.Ready -> lookup.value
        }
        val projectDirectories = attachmentDeletionRoot.listFiles()
            ?: return@synchronized LocalStorageWriteResult.FAILED
        var changed = false
        var failed = false
        for (projectDirectory in projectDirectories) {
            if (Files.isSymbolicLink(projectDirectory.toPath()) ||
                !Files.isDirectory(projectDirectory.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                !isSafeChild(attachmentDeletionRoot, projectDirectory) ||
                !isSafeId(projectDirectory.name)
            ) return@synchronized LocalStorageWriteResult.FAILED
            val markers = projectDirectory.listFiles() ?: return@synchronized LocalStorageWriteResult.FAILED
            for (marker in markers) {
                if (Files.isSymbolicLink(marker.toPath()) ||
                    !Files.isRegularFile(marker.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                    !isSafeChild(projectDirectory, marker) ||
                    !marker.name.endsWith(PREPARED_ATTACHMENT_DELETION_SUFFIX)
                ) return@synchronized LocalStorageWriteResult.FAILED
                val attachmentId = marker.name.removeSuffix(PREPARED_ATTACHMENT_DELETION_SUFFIX)
                if (!isSafeId(attachmentId) || !isValidMarker(marker)) {
                    return@synchronized LocalStorageWriteResult.FAILED
                }
                val referenced = attachmentId in existingAttachmentIdsByProject[projectDirectory.name].orEmpty()
                val result = if (referenced) {
                    cancelAttachmentDeletion(projectDirectory.name, attachmentId)
                } else {
                    completeAttachmentDeletion(projectDirectory.name, attachmentId)
                }
                if (result == LocalStorageWriteResult.FAILED || result == LocalStorageWriteResult.UNAVAILABLE) {
                    failed = true
                } else {
                    changed = true
                }
            }
        }
        when {
            failed -> LocalStorageWriteResult.FAILED
            changed -> LocalStorageWriteResult.SAVED
            else -> LocalStorageWriteResult.CLEARED
        }
    }

    private fun completeProjectDeletionLocked(projectId: String): LocalStorageWriteResult {
        if (!isSafeId(projectId)) return LocalStorageWriteResult.FAILED
        when (projectDeletionState(projectId)) {
            ProjectDeletionState.NONE -> return when (hasProjectData(projectId)) {
                false -> LocalStorageWriteResult.CLEARED
                true, null -> LocalStorageWriteResult.FAILED
            }
            ProjectDeletionState.INVALID -> return LocalStorageWriteResult.FAILED
            ProjectDeletionState.PREPARED,
            ProjectDeletionState.COMMITTED,
            -> Unit
        }

        val markerDirectory = existingMarkerDirectory() ?: return LocalStorageWriteResult.FAILED
        val committedMarker = File(markerDirectory, "$projectId$COMMITTED_DELETION_SUFFIX")
        if (!committedMarker.exists() && !writeMarker(committedMarker)) return LocalStorageWriteResult.FAILED
        if (!isValidMarker(committedMarker)) return LocalStorageWriteResult.FAILED

        if (!deleteProjectDataDirectory(projectId, PROJECTS_DIRECTORY) ||
            !deleteProjectDataDirectory(projectId, STAGING_DIRECTORY)
        ) return LocalStorageWriteResult.FAILED

        val preparedMarker = File(markerDirectory, "$projectId$PREPARED_DELETION_SUFFIX")
        if (preparedMarker.exists() && (!isValidMarker(preparedMarker) || !preparedMarker.delete())) {
            return LocalStorageWriteResult.FAILED
        }
        if (!committedMarker.delete()) return LocalStorageWriteResult.FAILED
        return LocalStorageWriteResult.SAVED
    }

    private fun projectDeletionState(projectId: String): ProjectDeletionState {
        if (!isSafeId(projectId)) return ProjectDeletionState.INVALID
        val markerDirectory = when (val lookup = markerDirectoryLookup()) {
            DirectoryLookup.Missing -> return ProjectDeletionState.NONE
            DirectoryLookup.Failed -> return ProjectDeletionState.INVALID
            is DirectoryLookup.Ready -> lookup.value
        }
        val prepared = File(markerDirectory, "$projectId$PREPARED_DELETION_SUFFIX")
        val committed = File(markerDirectory, "$projectId$COMMITTED_DELETION_SUFFIX")
        for (marker in listOf(prepared, committed)) {
            if (Files.isSymbolicLink(marker.toPath())) return ProjectDeletionState.INVALID
            if (marker.exists() && !isValidMarker(marker)) return ProjectDeletionState.INVALID
        }
        return when {
            committed.exists() -> ProjectDeletionState.COMMITTED
            prepared.exists() -> ProjectDeletionState.PREPARED
            else -> ProjectDeletionState.NONE
        }
    }

    private fun attachmentDeletionState(projectId: String, attachmentId: String): AttachmentDeletionState {
        if (!isSafeId(projectId) || !isSafeId(attachmentId)) return AttachmentDeletionState.INVALID
        val marker = attachmentDeletionMarker(projectId, attachmentId, createDirectories = false)
            ?: return when (attachmentDeletionMarkerDirectoryLookup(projectId)) {
                DirectoryLookup.Missing -> AttachmentDeletionState.NONE
                DirectoryLookup.Failed -> AttachmentDeletionState.INVALID
                is DirectoryLookup.Ready -> AttachmentDeletionState.INVALID
            }
        if (!marker.exists() && !Files.isSymbolicLink(marker.toPath())) return AttachmentDeletionState.NONE
        return if (isValidMarker(marker)) AttachmentDeletionState.PREPARED else AttachmentDeletionState.INVALID
    }

    private fun attachmentDeletionMarker(
        projectId: String,
        attachmentId: String,
        createDirectories: Boolean,
    ): File? {
        if (!isSafeId(projectId) || !isSafeId(attachmentId)) return null
        val markerDirectory = when (val lookup = attachmentDeletionMarkerDirectoryLookup(projectId, createDirectories)) {
            DirectoryLookup.Missing,
            DirectoryLookup.Failed,
            -> return null
            is DirectoryLookup.Ready -> lookup.value
        }
        return File(markerDirectory, "$attachmentId$PREPARED_ATTACHMENT_DELETION_SUFFIX")
            .takeIf { isSafeChild(markerDirectory, it) }
    }

    private fun attachmentDeletionMarkerDirectoryLookup(
        projectId: String,
        create: Boolean = false,
    ): DirectoryLookup {
        if (!isSafeId(projectId)) return DirectoryLookup.Failed
        val attachmentDeletionRoot = when (val lookup = attachmentDeletionRootLookup(create)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> lookup.value
        }
        return lookupDirectory(attachmentDeletionRoot, projectId, create)
    }

    private fun attachmentDeletionRootLookup(create: Boolean): DirectoryLookup {
        val base = privateFilesDirectory.canonicalFile
        if (!base.exists()) return if (create) DirectoryLookup.Failed else DirectoryLookup.Missing
        if (!base.isDirectory) return DirectoryLookup.Failed
        val root = when (val lookup = lookupDirectory(base, STORE_DIRECTORY, create)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> lookup.value
        }
        val deletionRoot = when (val lookup = lookupDirectory(root, DELETIONS_DIRECTORY, create)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> lookup.value
        }
        return lookupDirectory(deletionRoot, ATTACHMENT_DELETIONS_DIRECTORY, create)
    }

    private fun attachmentBlobExists(projectId: String, attachmentId: String): Boolean? =
        when (val lookup = attachmentDirectory(projectId, create = false)) {
            DirectoryLookup.Missing -> false
            DirectoryLookup.Failed -> null
            is DirectoryLookup.Ready -> {
                val blob = File(lookup.value, "$attachmentId$BLOB_SUFFIX")
                when {
                    Files.isSymbolicLink(blob.toPath()) -> null
                    !blob.exists() -> false
                    !isSafeChild(lookup.value, blob) ||
                        !Files.isRegularFile(blob.toPath(), LinkOption.NOFOLLOW_LINKS) -> null
                    else -> true
                }
            }
        }

    private fun deleteAttachmentBlob(projectId: String, attachmentId: String): Boolean =
        when (val lookup = attachmentDirectory(projectId, create = false)) {
            DirectoryLookup.Missing -> true
            DirectoryLookup.Failed -> false
            is DirectoryLookup.Ready -> {
                val blob = File(lookup.value, "$attachmentId$BLOB_SUFFIX")
                when {
                    Files.isSymbolicLink(blob.toPath()) -> false
                    !blob.exists() -> true
                    !isSafeChild(lookup.value, blob) ||
                        !Files.isRegularFile(blob.toPath(), LinkOption.NOFOLLOW_LINKS) -> false
                    else -> blob.delete()
                }
            }
        }

    private fun markerDirectoryLookup(): DirectoryLookup {
        val base = privateFilesDirectory.canonicalFile
        if (!base.exists()) return DirectoryLookup.Missing
        if (!base.isDirectory) return DirectoryLookup.Failed
        val root = when (val lookup = lookupDirectory(base, STORE_DIRECTORY, create = false)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> lookup.value
        }
        return lookupDirectory(root, DELETIONS_DIRECTORY, create = false)
    }

    private fun existingMarkerDirectory(): File? =
        (markerDirectoryLookup() as? DirectoryLookup.Ready)?.value

    private fun markerDirectoryStatus(): LocalStorageWriteResult = when (markerDirectoryLookup()) {
        DirectoryLookup.Missing -> LocalStorageWriteResult.CLEARED
        DirectoryLookup.Failed,
        is DirectoryLookup.Ready,
        -> LocalStorageWriteResult.FAILED
    }

    private fun deletionMarker(
        projectId: String,
        suffix: String,
        createDirectory: Boolean,
    ): File? {
        if (!isSafeId(projectId)) return null
        val root = rootDirectory(create = createDirectory) ?: return null
        val markerDirectory = directory(root, DELETIONS_DIRECTORY, create = createDirectory) ?: return null
        return File(markerDirectory, "$projectId$suffix").takeIf { isSafeChild(markerDirectory, it) }
    }

    private fun writeMarker(marker: File): Boolean = runCatching {
        val parent = marker.parentFile ?: return@runCatching false
        if (!parent.isDirectory || !isSafeChild(parent, marker)) return@runCatching false
        if (marker.exists()) return@runCatching isValidMarker(marker)
        if (!marker.createNewFile()) return@runCatching marker.exists() && isValidMarker(marker)
        FileOutputStream(marker, false).use { output ->
            output.write(1)
            output.fd.sync()
        }
        isValidMarker(marker)
    }.getOrDefault(false)

    private fun isValidMarker(marker: File): Boolean = runCatching {
        val parent = marker.parentFile ?: return@runCatching false
        !Files.isSymbolicLink(marker.toPath()) &&
            Files.isRegularFile(marker.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            isSafeChild(parent, marker)
    }.getOrDefault(false)

    private fun projectDataDirectoryLookup(projectId: String, containerName: String): DirectoryLookup {
        if (!isSafeId(projectId)) return DirectoryLookup.Failed
        val base = privateFilesDirectory.canonicalFile
        if (!base.exists()) return DirectoryLookup.Missing
        if (!base.isDirectory) return DirectoryLookup.Failed
        val root = when (val lookup = lookupDirectory(base, STORE_DIRECTORY, create = false)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> lookup.value
        }
        val container = when (val lookup = lookupDirectory(root, containerName, create = false)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> lookup.value
        }
        return lookupDirectory(container, projectId, create = false)
    }

    private fun deleteProjectDataDirectory(projectId: String, containerName: String): Boolean =
        when (val lookup = projectDataDirectoryLookup(projectId, containerName)) {
            DirectoryLookup.Missing -> true
            DirectoryLookup.Failed -> false
            is DirectoryLookup.Ready -> deleteTreeNoFollow(lookup.value)
        }

    private fun hasProjectData(projectId: String): Boolean? {
        for (containerName in listOf(PROJECTS_DIRECTORY, STAGING_DIRECTORY)) {
            when (projectDataDirectoryLookup(projectId, containerName)) {
                DirectoryLookup.Missing -> Unit
                DirectoryLookup.Failed -> return null
                is DirectoryLookup.Ready -> return true
            }
        }
        return false
    }

    private fun rootDirectory(create: Boolean): File? = directory(
        parent = privateFilesDirectory.canonicalFile,
        childName = STORE_DIRECTORY,
        create = create,
    )

    private fun attachmentDirectory(projectId: String, create: Boolean): DirectoryLookup {
        val base = privateFilesDirectory.canonicalFile
        if (!base.isDirectory) return DirectoryLookup.Failed
        var parent = when (val root = lookupDirectory(base, STORE_DIRECTORY, create)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> root.value
        }
        parent = when (val projects = lookupDirectory(parent, PROJECTS_DIRECTORY, create)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> projects.value
        }
        parent = when (val project = lookupDirectory(parent, projectId, create)) {
            DirectoryLookup.Missing -> return DirectoryLookup.Missing
            DirectoryLookup.Failed -> return DirectoryLookup.Failed
            is DirectoryLookup.Ready -> project.value
        }
        return lookupDirectory(parent, ATTACHMENTS_DIRECTORY, create)
    }

    private fun lookupDirectory(parent: File, childName: String, create: Boolean): DirectoryLookup {
        if (!isSafeIdOrKnownDirectory(childName) || !parent.isDirectory) return DirectoryLookup.Failed
        val candidate = File(parent, childName)
        if (Files.isSymbolicLink(candidate.toPath())) return DirectoryLookup.Failed
        if (!candidate.exists()) {
            if (!create) return DirectoryLookup.Missing
            if (!candidate.mkdir() && !candidate.isDirectory) return DirectoryLookup.Failed
        }
        if (!candidate.isDirectory || !isSafeChild(parent, candidate)) return DirectoryLookup.Failed
        return DirectoryLookup.Ready(candidate.canonicalFile)
    }

    private fun directory(parent: File, childName: String, create: Boolean): File? =
        (lookupDirectory(parent, childName, create) as? DirectoryLookup.Ready)?.value

    private fun listExistingAttachments(projectId: String): List<File>? {
        val directory = when (val result = attachmentDirectory(projectId, create = false)) {
            DirectoryLookup.Missing -> return emptyList()
            DirectoryLookup.Failed -> return null
            is DirectoryLookup.Ready -> result.value
        }
        val children = directory.listFiles() ?: return if (directory.exists()) null else emptyList()
        val ids = mutableSetOf<String>()
        val files = mutableListOf<File>()
        children.forEach { child ->
            if (Files.isSymbolicLink(child.toPath()) ||
                !Files.isRegularFile(child.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                !isSafeChild(directory, child) || !child.name.endsWith(BLOB_SUFFIX)
            ) return null
            val id = child.name.removeSuffix(BLOB_SUFFIX)
            if (!isSafeId(id) || !ids.add(id.lowercase())) return null
            if (child.length() !in 1..StudentProjectAttachmentRules.MAX_ATTACHMENT_BYTES) return null
            files += child
        }
        return files
    }

    private fun finalAttachmentDirectory(projectId: String): File? =
        (attachmentDirectory(projectId, create = true) as? DirectoryLookup.Ready)?.value

    private fun isSafeChild(parent: File, child: File): Boolean = runCatching {
        !Files.isSymbolicLink(child.toPath()) && child.canonicalFile.parentFile == parent.canonicalFile
    }.getOrDefault(false)

    private fun deleteTreeNoFollow(target: File): Boolean = runCatching {
        if (Files.isSymbolicLink(target.toPath())) {
            false
        } else if (!target.exists() || !target.isDirectory) {
            !target.exists() || target.delete()
        } else {
            val children = target.listFiles() ?: return@runCatching false
            children.all(::deleteTreeNoFollow) && target.delete()
        }
    }.getOrDefault(false)

    private sealed interface DirectoryLookup {
        data class Ready(val value: File) : DirectoryLookup
        data object Missing : DirectoryLookup
        data object Failed : DirectoryLookup
    }

    private fun isSafeIdOrKnownDirectory(value: String): Boolean =
        isSafeId(value) || value in setOf(
            STORE_DIRECTORY,
            PROJECTS_DIRECTORY,
            STAGING_DIRECTORY,
            ATTACHMENTS_DIRECTORY,
            DELETIONS_DIRECTORY,
        )

    private fun isSafeId(value: String): Boolean = SAFE_ID.matches(value)

    private data class StagedBlob(
        val reference: StudentProjectAttachmentRef,
        val stagedFile: File,
    )

    private inner class AndroidStudentProjectAttachmentImportSession(
        private val store: AndroidStudentProjectAttachmentStore,
        private val projectId: String,
        private val transactionDirectory: File,
    ) : StudentProjectAttachmentImportSession {
        private var state = AttachmentImportState.OPEN
        private var failed = false
        private var activeWriters = 0
        private var reservedBytes = 0L
        private val staged = mutableListOf<StagedBlob>()
        private val published = mutableListOf<File>()
        private val activeIds = mutableSetOf<String>()

        override fun openWriter(reference: StudentProjectAttachmentRef): StudentProjectAttachmentWriterResult =
            synchronized(attachmentMutationLock) {
                try {
                    if (state != AttachmentImportState.OPEN || failed ||
                        StudentProjectAttachmentRules.validate(reference).isNotEmpty()
                    ) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_REFERENCE_INVALID",
                        )
                    }
                    if (store.projectDeletionState(projectId) != ProjectDeletionState.NONE) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_DELETION_PENDING",
                        )
                    }
                    if (store.attachmentDeletionState(projectId, reference.id) != AttachmentDeletionState.NONE) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_DELETION_PENDING",
                        )
                    }
                    val normalizedId = reference.id.lowercase()
                    if (staged.any { it.reference.id.lowercase() == normalizedId } || normalizedId in activeIds) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_DUPLICATE_ID",
                        )
                    }
                    if (staged.size + activeWriters >= StudentProjectAttachmentRules.MAX_ATTACHMENTS) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_LIMIT_REACHED",
                        )
                    }
                    if (reservedBytes > STUDENT_PROJECT_ATTACHMENT_MAX_IMPORT_BYTES - reference.sizeBytes) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_IMPORT_SIZE_LIMIT",
                        )
                    }
                    val existing = store.listExistingAttachments(projectId)
                        ?: return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_STORAGE_INVALID",
                        )
                    if (existing.size + staged.size + activeWriters >= StudentProjectAttachmentRules.MAX_ATTACHMENTS) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_LIMIT_REACHED",
                        )
                    }
                    val file = File(transactionDirectory, "${reference.id}$STAGING_SUFFIX")
                    if (!isSafeChild(transactionDirectory, file) || !file.createNewFile()) {
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_STAGE_FAILED",
                        )
                    }
                    val output = runCatching { FileOutputStream(file, false) }.getOrElse {
                        file.delete()
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_STAGE_FAILED",
                        )
                    }
                    val writer = runCatching {
                        AndroidStudentProjectAttachmentWriteHandle(
                            output = output,
                            stagedFile = file,
                            reference = reference,
                            onClosed = { completedFile ->
                                synchronized(attachmentMutationLock) {
                                    activeWriters = (activeWriters - 1).coerceAtLeast(0)
                                    activeIds -= normalizedId
                                    if (completedFile == null) {
                                        failed = true
                                    } else {
                                        staged += StagedBlob(reference, completedFile)
                                    }
                                }
                            },
                        )
                    }.getOrElse {
                        runCatching { output.close() }
                        file.delete()
                        return@synchronized StudentProjectAttachmentWriterResult.Failed(
                            "PROJECT_ATTACHMENT_STAGE_FAILED",
                        )
                    }
                    activeWriters += 1
                    reservedBytes += reference.sizeBytes
                    activeIds += normalizedId
                    StudentProjectAttachmentWriterResult.Opened(writer)
                } catch (_: Exception) {
                    StudentProjectAttachmentWriterResult.Failed("PROJECT_ATTACHMENT_STAGE_FAILED")
                }
        }

        override fun publish(): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
            if (state != AttachmentImportState.OPEN || failed || activeWriters != 0) {
                return@synchronized LocalStorageWriteResult.FAILED
            }
            if (store.projectDeletionState(projectId) != ProjectDeletionState.NONE) {
                return@synchronized LocalStorageWriteResult.FAILED
            }
            return try {
                val existing = store.listExistingAttachments(projectId)
                    ?: return@synchronized LocalStorageWriteResult.FAILED
                val existingIds = existing.map { it.name.removeSuffix(BLOB_SUFFIX).lowercase() }.toMutableSet()
                if (staged.any { !existingIds.add(it.reference.id.lowercase()) } ||
                    existingIds.size > StudentProjectAttachmentRules.MAX_ATTACHMENTS
                ) {
                    return@synchronized LocalStorageWriteResult.FAILED
                }
                val destinationDirectory = store.finalAttachmentDirectory(projectId)
                    ?: return@synchronized LocalStorageWriteResult.FAILED
                state = AttachmentImportState.PUBLISHED
                staged.forEach { blob ->
                    val destination = File(destinationDirectory, "${blob.reference.id}$BLOB_SUFFIX")
                    if (!store.isSafeChild(destinationDirectory, destination)) {
                        throw IOException("Unsafe attachment target")
                    }
                    Files.move(blob.stagedFile.toPath(), destination.toPath())
                    published += destination
                }
                LocalStorageWriteResult.SAVED
            } catch (_: Exception) {
                if (state == AttachmentImportState.PUBLISHED) rollbackPublishedLocked()
                LocalStorageWriteResult.FAILED
            }
        }

        override fun complete(): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
            when (state) {
                AttachmentImportState.COMPLETED -> Unit
                AttachmentImportState.PUBLISHED -> state = AttachmentImportState.COMPLETED
                AttachmentImportState.OPEN,
                AttachmentImportState.ROLLED_BACK,
                -> return@synchronized LocalStorageWriteResult.FAILED
            }
            if (store.deleteTreeNoFollow(transactionDirectory)) {
                LocalStorageWriteResult.SAVED
            } else {
                LocalStorageWriteResult.FAILED
            }
        }

        override fun rollback(): LocalStorageWriteResult = synchronized(attachmentMutationLock) {
            when (state) {
                AttachmentImportState.COMPLETED -> LocalStorageWriteResult.FAILED
                AttachmentImportState.ROLLED_BACK -> LocalStorageWriteResult.CLEARED
                AttachmentImportState.OPEN -> {
                    if (activeWriters != 0) return@synchronized LocalStorageWriteResult.FAILED
                    if (store.deleteTreeNoFollow(transactionDirectory)) {
                        state = AttachmentImportState.ROLLED_BACK
                        LocalStorageWriteResult.CLEARED
                    } else {
                        LocalStorageWriteResult.FAILED
                    }
                }
                AttachmentImportState.PUBLISHED -> if (rollbackPublishedLocked()) {
                    LocalStorageWriteResult.CLEARED
                } else {
                    LocalStorageWriteResult.FAILED
                }
            }
        }

        private fun rollbackPublishedLocked(): Boolean = runCatching {
            val removed = published.all { file ->
                if (!file.exists()) true
                else if (Files.isSymbolicLink(file.toPath())) file.delete()
                else file.isFile && file.delete()
            }
            if (!removed) return@runCatching false
            published.clear()
            if (!store.deleteTreeNoFollow(transactionDirectory)) return@runCatching false
            state = AttachmentImportState.ROLLED_BACK
            true
        }.getOrDefault(false)
    }
}

private class AndroidStudentProjectAttachmentReadHandle(
    private val input: FileInputStream,
    override val sizeBytes: Long,
) : StudentProjectAttachmentReadHandle {
    private var bytesRead = 0L
    private var closed = false

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        check(!closed) { "Attachment reader is closed" }
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        require(length <= STUDENT_PROJECT_ATTACHMENT_MAX_IO_CHUNK_BYTES) { "Attachment read chunk exceeds limit" }
        if (bytesRead >= sizeBytes) return -1
        val boundedLength = minOf(length.toLong(), sizeBytes - bytesRead).toInt()
        val count = input.read(buffer, offset, boundedLength)
        if (count > 0) bytesRead += count
        return count
    }

    override fun close() {
        if (!closed) {
            closed = true
            input.close()
        }
    }
}

private class AndroidStudentProjectAttachmentWriteHandle(
    private val output: FileOutputStream,
    private val stagedFile: File,
    private val reference: StudentProjectAttachmentRef,
    private val onClosed: (File?) -> Unit,
) : StudentProjectAttachmentWriteHandle {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var bytesWritten = 0L
    private var closed = false

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        check(!closed) { "Attachment writer is closed" }
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return
        if (bytesWritten + length > reference.sizeBytes ||
            bytesWritten + length > StudentProjectAttachmentRules.MAX_ATTACHMENT_BYTES ||
            length > STUDENT_PROJECT_ATTACHMENT_MAX_IO_CHUNK_BYTES
        ) {
            abortAfterFailure()
            throw IOException("Attachment exceeds its declared size")
        }
        try {
            output.write(buffer, offset, length)
            digest.update(buffer, offset, length)
            bytesWritten += length
        } catch (failure: Exception) {
            abortAfterFailure()
            throw failure
        }
    }

    override fun finish(): LocalStorageWriteResult {
        if (closed) return LocalStorageWriteResult.FAILED
        return runCatching {
            output.fd.sync()
            output.close()
            closed = true
            val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            if (bytesWritten != reference.sizeBytes || hash != reference.sha256) {
                stagedFile.delete()
                onClosed(null)
                LocalStorageWriteResult.FAILED
            } else {
                onClosed(stagedFile)
                LocalStorageWriteResult.SAVED
            }
        }.getOrElse {
            abortAfterFailure()
            LocalStorageWriteResult.FAILED
        }
    }

    override fun abort() {
        if (!closed) abortAfterFailure()
    }

    private fun abortAfterFailure() {
        if (closed) return
        closed = true
        runCatching { output.close() }
        runCatching { stagedFile.delete() }
        onClosed(null)
    }
}
