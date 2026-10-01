package dev.nextgen.mobile.storage

import android.content.Context

object AndroidProjectSectionBookmarkStorage {
    private var applicationContext: Context? = null
    fun initialize(context: Context) { applicationContext = context.applicationContext }
    fun createStore(): ProjectSectionBookmarkStore = applicationContext?.let(::AndroidProjectSectionBookmarks)
        ?: UnavailableProjectSectionBookmarkStore()
}

actual fun createProjectSectionBookmarkStore(): ProjectSectionBookmarkStore = AndroidProjectSectionBookmarkStorage.createStore()

private class AndroidProjectSectionBookmarks(context: Context) : ProjectSectionBookmarkStore {
    private val preferences = context.getSharedPreferences("evidrilo_project_sections_v1", Context.MODE_PRIVATE)
    override fun read(key: String): LocalStorageReadResult<String> = runCatching {
        LocalStorageReadResult.Success(preferences.getString(key, null))
    }.getOrElse { LocalStorageReadResult.Failed }
    override fun remove(key: String): LocalStorageWriteResult = runCatching {
        if (preferences.edit().remove(key).commit()) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }.getOrElse { LocalStorageWriteResult.FAILED }
    override fun write(key: String, sectionId: String): LocalStorageWriteResult = runCatching {
        if (preferences.edit().putString(key, sectionId).commit()) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
