package dev.nextgen.mobile.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class StudentProjectAttachmentStoreJvmTest {
    @Test
    fun `unsupported JVM factory fails closed for device operations and safely no-ops metadata-only deletion`() {
        val store = createStudentProjectAttachmentStore()

        assertEquals(
            StudentProjectAttachmentReadResult.Unavailable,
            store.openRead("project-one", "attachment-one"),
        )
        assertEquals(
            StudentProjectAttachmentImportResult.Unavailable,
            store.beginImport("project-one"),
        )
        assertEquals(LocalStorageWriteResult.CLEARED, store.prepareProjectDeletion("project-one", hasAttachments = false))
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, store.prepareProjectDeletion("project-one", hasAttachments = true))
        assertEquals(LocalStorageWriteResult.CLEARED, store.cancelProjectDeletion("project-one"))
        assertEquals(LocalStorageWriteResult.CLEARED, store.completeProjectDeletion("project-one"))
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, store.prepareAttachmentDeletion("project-one", "attachment-one"))
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, store.cancelAttachmentDeletion("project-one", "attachment-one"))
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, store.completeAttachmentDeletion("project-one", "attachment-one"))
        assertEquals(LocalStorageWriteResult.CLEARED, store.recoverPendingAttachmentDeletions(emptyMap()))
        assertEquals(
            LocalStorageWriteResult.FAILED,
            store.recoverPendingAttachmentDeletions(mapOf("project-one" to setOf("../outside"))),
        )
        assertEquals(
            LocalStorageWriteResult.CLEARED,
            store.recoverPendingProjectDeletions(emptySet()),
        )
        assertEquals(LocalStorageWriteResult.FAILED, store.prepareProjectDeletion("../outside", hasAttachments = false))
        assertEquals(LocalStorageWriteResult.FAILED, store.recoverPendingProjectDeletions(setOf("../outside")))
    }
}
