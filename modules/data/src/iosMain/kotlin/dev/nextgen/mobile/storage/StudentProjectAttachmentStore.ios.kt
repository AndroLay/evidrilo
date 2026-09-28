package dev.nextgen.mobile.storage

actual fun createStudentProjectAttachmentStore(): StudentProjectAttachmentStore =
    UnavailableStudentProjectAttachmentStore()
