package dev.nextgen.mobile.storage

import platform.Foundation.NSUserDefaults

private const val PROJECTS_KEY = "evidrilo.student-projects.v1"

actual fun createStudentProjectDraftStore(): StudentProjectDraftStore =
    EncodedStudentProjectDraftStore(IosStudentProjectDraftTextStorage())

private class IosStudentProjectDraftTextStorage : StudentProjectDraftTextStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(): LocalStorageReadResult<String> = runCatching {
        LocalStorageReadResult.Success(defaults.stringForKey(PROJECTS_KEY))
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun write(encoded: String?): LocalStorageWriteResult = runCatching {
        if (encoded == null) {
            defaults.removeObjectForKey(PROJECTS_KEY)
            if (defaults.stringForKey(PROJECTS_KEY) == null) LocalStorageWriteResult.CLEARED
            else LocalStorageWriteResult.FAILED
        } else {
            defaults.setObject(encoded, forKey = PROJECTS_KEY)
            if (defaults.stringForKey(PROJECTS_KEY) == encoded) LocalStorageWriteResult.SAVED
            else LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
