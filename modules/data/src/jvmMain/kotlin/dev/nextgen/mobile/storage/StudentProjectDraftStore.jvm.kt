package dev.nextgen.mobile.storage

actual fun createStudentProjectDraftStore(): StudentProjectDraftStore =
    NoopStudentProjectDraftStore()
