package dev.nextgen.mobile.notifications

import android.content.Context
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

private const val PREFERENCES_NAME = "evidrilo_notifications_v1"
private const val PREFERENCES_KEY = "preferences"

object AndroidNotificationPreferencesStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun createStore(): NotificationPreferencesStore =
        applicationContext?.let(::AndroidNotificationPreferencesStore)
            ?: NoopNotificationPreferencesStore()
}

actual fun createNotificationPreferencesStore(): NotificationPreferencesStore =
    AndroidNotificationPreferencesStorage.createStore()

private class AndroidNotificationPreferencesStore(
    context: Context,
) : NotificationPreferencesStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<NotificationPreferences> = runCatching {
        val encoded = preferences.getString(PREFERENCES_KEY, null)
        if (encoded == null) {
            LocalStorageReadResult.Success(null)
        } else {
            NotificationPreferencesCodec.decode(encoded)?.let { LocalStorageReadResult.Success(it) }
                ?: LocalStorageReadResult.Corrupt
        }
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(preferences: NotificationPreferences): LocalStorageWriteResult = runCatching {
        if (!preferences.isValid) {
            LocalStorageWriteResult.FAILED
        } else if (this.preferences.edit()
                .putString(PREFERENCES_KEY, NotificationPreferencesCodec.encode(preferences))
                .commit()
        ) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
