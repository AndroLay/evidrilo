package dev.nextgen.mobile.storage

import platform.Foundation.NSUserDefaults

actual fun createProjectSectionBookmarkStore(): ProjectSectionBookmarkStore = IosProjectSectionBookmarks()

private class IosProjectSectionBookmarks : ProjectSectionBookmarkStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun storageKey(key: String) = "evidrilo.project-section.v1.$key"
    override fun read(key: String): LocalStorageReadResult<String> = runCatching {
        LocalStorageReadResult.Success(defaults.stringForKey(storageKey(key)))
    }.getOrElse { LocalStorageReadResult.Failed }
    override fun remove(key: String): LocalStorageWriteResult = runCatching {
        defaults.removeObjectForKey(storageKey(key))
        if (defaults.stringForKey(storageKey(key)) == null) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }.getOrElse { LocalStorageWriteResult.FAILED }
    override fun write(key: String, sectionId: String): LocalStorageWriteResult = runCatching {
        defaults.setObject(sectionId, forKey = storageKey(key))
        if (defaults.stringForKey(storageKey(key)) == sectionId) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
