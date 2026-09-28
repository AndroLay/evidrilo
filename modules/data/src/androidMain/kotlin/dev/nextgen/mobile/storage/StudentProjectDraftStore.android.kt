package dev.nextgen.mobile.storage

import android.content.Context

private const val PREFERENCES_NAME = "evidrilo_student_projects_v1"
private const val PROJECTS_KEY = "projects"

object AndroidStudentProjectDraftStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun createStore(): StudentProjectDraftStore = applicationContext
        ?.let { context ->
            EncodedStudentProjectDraftStore(AndroidStudentProjectDraftTextStorage(context))
        }
        ?: NoopStudentProjectDraftStore()
}

actual fun createStudentProjectDraftStore(): StudentProjectDraftStore =
    AndroidStudentProjectDraftStorage.createStore()

private class AndroidStudentProjectDraftTextStorage(
    context: Context,
) : StudentProjectDraftTextStorage {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): LocalStorageReadResult<String> = runCatching {
        LocalStorageReadResult.Success(preferences.getString(PROJECTS_KEY, null))
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun write(encoded: String?): LocalStorageWriteResult = runCatching {
        val editor = preferences.edit()
        if (encoded == null) editor.remove(PROJECTS_KEY) else editor.putString(PROJECTS_KEY, encoded)
        if (editor.commit()) {
            if (encoded == null) LocalStorageWriteResult.CLEARED else LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
