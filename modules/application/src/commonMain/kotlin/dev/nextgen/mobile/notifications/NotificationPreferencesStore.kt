package dev.nextgen.mobile.notifications

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

interface NotificationPreferencesStore {
    fun load(): LocalStorageReadResult<NotificationPreferences>

    fun save(preferences: NotificationPreferences): LocalStorageWriteResult
}

class NoopNotificationPreferencesStore : NotificationPreferencesStore {
    override fun load(): LocalStorageReadResult<NotificationPreferences> =
        LocalStorageReadResult.Unavailable

    override fun save(preferences: NotificationPreferences): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE
}

expect fun createNotificationPreferencesStore(): NotificationPreferencesStore
