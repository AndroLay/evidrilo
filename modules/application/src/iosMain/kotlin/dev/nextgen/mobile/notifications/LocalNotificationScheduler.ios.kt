package dev.nextgen.mobile.notifications

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitWeekday
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNCalendarNotificationTrigger
import kotlin.coroutines.resume

private const val CONTINUE_IDENTIFIER = "evidrilo.local.continue_unfinished"
private const val REVIEW_IDENTIFIER = "evidrilo.local.review_completed"

actual fun createLocalNotificationScheduler(): LocalNotificationScheduler =
    IosLocalNotificationScheduler()

private class IosLocalNotificationScheduler : LocalNotificationScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun permissionState(): NotificationPermissionState =
        suspendCancellableCoroutine { continuation ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                val state = when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized,
                    UNAuthorizationStatusProvisional,
                    -> NotificationPermissionState.GRANTED
                    UNAuthorizationStatusDenied -> NotificationPermissionState.DENIED
                    UNAuthorizationStatusNotDetermined -> NotificationPermissionState.UNKNOWN
                    else -> NotificationPermissionState.UNAVAILABLE
                }
                if (continuation.isActive) continuation.resume(state)
            }
        }

    override suspend fun requestPermission(): NotificationPermissionState {
        val current = permissionState()
        if (current == NotificationPermissionState.GRANTED ||
            current == NotificationPermissionState.DENIED
        ) {
            return current
        }
        return suspendCancellableCoroutine { continuation ->
            center.requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or
                    UNAuthorizationOptionSound or
                    UNAuthorizationOptionBadge,
            ) { granted, _ ->
                if (continuation.isActive) {
                    continuation.resume(
                        if (granted) NotificationPermissionState.GRANTED
                        else NotificationPermissionState.DENIED,
                    )
                }
            }
        }
    }

    override suspend fun schedule(
        preferences: NotificationPreferences,
        hasUnfinishedCase: Boolean,
        hasCompletedCase: Boolean,
    ): LocalNotificationScheduleResult {
        if (!preferences.isValid) {
            return LocalNotificationScheduleResult.Failed("The reminder time is invalid.")
        }
        if (permissionState() != NotificationPermissionState.GRANTED) {
            return LocalNotificationScheduleResult.PermissionDenied
        }
        val categories = preferences.activeCategories(hasUnfinishedCase, hasCompletedCase)
        cancelAll()
        if (categories.isEmpty()) return LocalNotificationScheduleResult.Scheduled(emptyList())
        val scheduled = categories.map { category ->
            addRequest(preferences, category)
        }
        return if (scheduled.all { it }) {
            LocalNotificationScheduleResult.Scheduled(categories)
        } else {
            LocalNotificationScheduleResult.Failed("One or more reminders could not be scheduled.")
        }
    }

    override fun cancelAll() {
        center.removePendingNotificationRequestsWithIdentifiers(
            listOf(CONTINUE_IDENTIFIER, REVIEW_IDENTIFIER),
        )
    }

    override fun openSystemSettings() {
        UIApplication.sharedApplication.openURL(
            NSURL(string = UIApplicationOpenSettingsURLString),
            options = emptyMap<Any?, Any?>(),
            completionHandler = null,
        )
    }

    private suspend fun addRequest(
        preferences: NotificationPreferences,
        category: LocalNotificationCategory,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val content = UNMutableNotificationContent()
        content.setTitle(
            when (category) {
                LocalNotificationCategory.CONTINUE_UNFINISHED -> "Continue your Evidrilo case"
                LocalNotificationCategory.REVIEW_COMPLETED -> "Review your Evidrilo changes"
            },
        )
        content.setBody(
            when (category) {
                LocalNotificationCategory.CONTINUE_UNFINISHED ->
                    "Your evidence workspace is ready when you are."
                LocalNotificationCategory.REVIEW_COMPLETED ->
                    "Revisit the evidence and see what changed in your conclusion."
            },
        )
        content.setSound(UNNotificationSound.defaultSound())
        val components = NSDateComponents().apply {
            hour = preferences.hour.toLong()
            minute = preferences.minute.toLong()
            if (preferences.cadence == NotificationCadence.WEEKLY) {
                weekday = NSCalendar.currentCalendar.component(
                    NSCalendarUnitWeekday,
                    fromDate = NSDate(),
                )
            }
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            components,
            repeats = true,
        )
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = when (category) {
                LocalNotificationCategory.CONTINUE_UNFINISHED -> CONTINUE_IDENTIFIER
                LocalNotificationCategory.REVIEW_COMPLETED -> REVIEW_IDENTIFIER
            },
            content = content,
            trigger = trigger,
        )
        center.addNotificationRequest(request) { error ->
            if (continuation.isActive) continuation.resume(error == null)
        }
    }
}
