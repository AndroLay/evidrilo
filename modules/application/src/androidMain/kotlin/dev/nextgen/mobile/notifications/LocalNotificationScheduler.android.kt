package dev.nextgen.mobile.notifications

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.lang.ref.WeakReference
import java.util.Calendar
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val CHANNEL_ID = "evidrilo_local_reminders_v1"
private const val REQUEST_NOTIFICATIONS = 4100
private const val CONTINUE_NOTIFICATION_ID = 4101
private const val REVIEW_NOTIFICATION_ID = 4102
private const val CATEGORY_EXTRA = "evidrilo_notification_category"
private const val CONTINUE_CATEGORY = "continue_unfinished"
private const val REVIEW_CATEGORY = "review_completed"

object AndroidLocalNotificationPlatform {
    private var applicationContext: Context? = null
    private var activityReference: WeakReference<Activity>? = null
    private var permissionContinuation: CancellableContinuation<NotificationPermissionState>? = null

    fun initialize(activity: Activity) {
        applicationContext = activity.applicationContext
        activityReference = WeakReference(activity)
    }

    fun onActivityAvailable(activity: Activity) {
        activityReference = WeakReference(activity)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
    ) {
        if (requestCode != REQUEST_NOTIFICATIONS) return
        val continuation = permissionContinuation ?: return
        permissionContinuation = null
        val state = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            NotificationPermissionState.GRANTED
        } else {
            NotificationPermissionState.DENIED
        }
        if (continuation.isActive) continuation.resume(state)
    }

    private fun context(): Context? = applicationContext

    private fun activity(): Activity? = activityReference?.get()

    private fun registerPermissionContinuation(
        continuation: CancellableContinuation<NotificationPermissionState>,
    ) {
        permissionContinuation?.cancel()
        permissionContinuation = continuation
    }

    private fun clearPermissionContinuation(
        continuation: CancellableContinuation<NotificationPermissionState>,
    ) {
        if (permissionContinuation === continuation) permissionContinuation = null
    }

    fun requestPermission(
        continuation: CancellableContinuation<NotificationPermissionState>,
    ): Boolean {
        val activity = activity() ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        registerPermissionContinuation(continuation)
        activity.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS,
        )
        return true
    }

    fun cancelPermissionRequest(
        continuation: CancellableContinuation<NotificationPermissionState>,
    ) {
        clearPermissionContinuation(continuation)
    }

    fun notificationContext(): Context? = context()

    fun currentActivity(): Activity? = activity()
}

actual fun createLocalNotificationScheduler(): LocalNotificationScheduler =
    AndroidLocalNotificationPlatform.notificationContext()?.let(::AndroidLocalNotificationScheduler)
        ?: NoopLocalNotificationScheduler()

private class AndroidLocalNotificationScheduler(
    private val context: Context,
) : LocalNotificationScheduler {
    override suspend fun permissionState(): NotificationPermissionState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (notificationsEnabledBySystem()) {
                NotificationPermissionState.GRANTED
            } else {
                NotificationPermissionState.DENIED
            }
        }
        return when {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED && notificationsEnabledBySystem() ->
                NotificationPermissionState.GRANTED
            AndroidLocalNotificationPlatform.currentActivity()?.let { activity ->
                activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            } == true -> NotificationPermissionState.DENIED
            else -> NotificationPermissionState.UNKNOWN
        }
    }

    override suspend fun requestPermission(): NotificationPermissionState {
        val current = permissionState()
        if (current == NotificationPermissionState.GRANTED ||
            current == NotificationPermissionState.DENIED
        ) {
            return current
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationPermissionState.GRANTED
        }
        return suspendCancellableCoroutine { continuation ->
            val started = AndroidLocalNotificationPlatform.requestPermission(continuation)
            if (!started) {
                continuation.resume(NotificationPermissionState.UNAVAILABLE)
            } else {
                continuation.invokeOnCancellation {
                    AndroidLocalNotificationPlatform.cancelPermissionRequest(continuation)
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
        return when (permissionState()) {
            NotificationPermissionState.GRANTED -> {
                val categories = preferences.activeCategories(hasUnfinishedCase, hasCompletedCase)
                cancelAll()
                if (categories.isEmpty()) {
                    LocalNotificationScheduleResult.Scheduled(emptyList())
                } else {
                    runCatching {
                        createChannel()
                        categories.forEach { category -> scheduleCategory(preferences, category) }
                        LocalNotificationScheduleResult.Scheduled(categories)
                    }.getOrElse { error ->
                        LocalNotificationScheduleResult.Failed(
                            error.message ?: "The reminder could not be scheduled.",
                        )
                    }
                }
            }
            NotificationPermissionState.DENIED -> LocalNotificationScheduleResult.PermissionDenied
            NotificationPermissionState.UNAVAILABLE,
            NotificationPermissionState.UNKNOWN,
            -> LocalNotificationScheduleResult.Unavailable
        }
    }

    override fun cancelAll() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        listOf(
            CONTINUE_NOTIFICATION_ID to CONTINUE_CATEGORY,
            REVIEW_NOTIFICATION_ID to REVIEW_CATEGORY,
        ).forEach { (requestCode, category) ->
            alarmManager.cancel(pendingIntent(requestCode, category))
        }
    }

    override fun openSystemSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Evidrilo reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Optional reminders for unfinished and completed Evidrilo cases."
            },
        )
    }

    private fun notificationsEnabledBySystem(): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false

    private fun scheduleCategory(
        preferences: NotificationPreferences,
        category: LocalNotificationCategory,
    ) {
        val requestCode = when (category) {
            LocalNotificationCategory.CONTINUE_UNFINISHED -> CONTINUE_NOTIFICATION_ID
            LocalNotificationCategory.REVIEW_COMPLETED -> REVIEW_NOTIFICATION_ID
        }
        val categoryId = when (category) {
            LocalNotificationCategory.CONTINUE_UNFINISHED -> CONTINUE_CATEGORY
            LocalNotificationCategory.REVIEW_COMPLETED -> REVIEW_CATEGORY
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
            ?: error("AlarmManager unavailable")
        val intervalMillis = when (preferences.cadence) {
            NotificationCadence.DAILY -> AlarmManager.INTERVAL_DAY
            NotificationCadence.WEEKLY -> AlarmManager.INTERVAL_DAY * 7L
        }
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(preferences),
            intervalMillis,
            pendingIntent(requestCode, categoryId),
        )
    }

    private fun nextTriggerMillis(preferences: NotificationPreferences): Long {
        val now = Calendar.getInstance()
        val trigger = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, preferences.hour)
            set(Calendar.MINUTE, preferences.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (trigger.timeInMillis <= now.timeInMillis) {
            trigger.add(
                Calendar.DAY_OF_YEAR,
                if (preferences.cadence == NotificationCadence.WEEKLY) 7 else 1,
            )
        }
        return trigger.timeInMillis
    }

    private fun pendingIntent(requestCode: Int, category: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, EvidriloLocalNotificationReceiver::class.java).apply {
                putExtra(CATEGORY_EXTRA, category)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

class EvidriloLocalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val category = intent.getStringExtra(CATEGORY_EXTRA) ?: return
        val (title, body) = when (category) {
            CONTINUE_CATEGORY -> "Continue your Evidrilo case" to
                "Your evidence workspace is ready when you are."
            REVIEW_CATEGORY -> "Review your Evidrilo changes" to
                "Revisit the evidence and see what changed in your conclusion."
            else -> return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                4200,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
        contentIntent?.let(builder::setContentIntent)
        manager.notify(
            when (category) {
                CONTINUE_CATEGORY -> CONTINUE_NOTIFICATION_ID
                else -> REVIEW_NOTIFICATION_ID
            },
            builder.build(),
        )
    }
}
