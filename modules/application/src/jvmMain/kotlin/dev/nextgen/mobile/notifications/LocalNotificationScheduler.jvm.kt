package dev.nextgen.mobile.notifications

actual fun createLocalNotificationScheduler(): LocalNotificationScheduler =
    NoopLocalNotificationScheduler()
