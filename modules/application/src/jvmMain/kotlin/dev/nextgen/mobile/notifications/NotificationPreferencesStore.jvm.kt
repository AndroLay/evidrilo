package dev.nextgen.mobile.notifications

actual fun createNotificationPreferencesStore(): NotificationPreferencesStore =
    NoopNotificationPreferencesStore()
