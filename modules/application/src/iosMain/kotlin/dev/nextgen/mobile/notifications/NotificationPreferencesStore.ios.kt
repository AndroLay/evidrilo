package dev.nextgen.mobile.notifications

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import platform.Foundation.NSUserDefaults

private const val PREFERENCES_KEY = "evidrilo.notifications.preferences.v1"

actual fun createNotificationPreferencesStore(): NotificationPreferencesStore =
    IosNotificationPreferencesStore()

private class IosNotificationPreferencesStore : NotificationPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<NotificationPreferences> = runCatching {
        val encoded = defaults.stringForKey(PREFERENCES_KEY)
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
        } else {
            defaults.setObject(NotificationPreferencesCodec.encode(preferences), forKey = PREFERENCES_KEY)
            LocalStorageWriteResult.SAVED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
