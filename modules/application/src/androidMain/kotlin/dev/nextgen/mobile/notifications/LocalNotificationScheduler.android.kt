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
import java.util.TimeZone
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
private const val SCHEDULE_STATE_PREFERENCES = "evidrilo_notification_schedule_v1"
private const val SCHEDULE_STATE_KEY = "schedule"

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

    fun restoreScheduledReminders(context: Context) {
        AndroidLocalNotificationScheduler(context.applicationContext).restorePersistedSchedule()
    }
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
        val categories = preferences.activeCategories(hasUnfinishedCase, hasCompletedCase)
        if (categories.isEmpty()) {
            cancelAll()
            return LocalNotificationScheduleResult.Scheduled(emptyList())
        }
        return when (permissionState()) {
            NotificationPermissionState.GRANTED -> {
                cancelAll()
                runCatching {
                    createChannel()
                    val weeklyDayOfWeek = if (preferences.cadence == NotificationCadence.WEEKLY) {
                        Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                    } else {
                        null
                    }
                    val snapshot = NotificationScheduleSnapshot(
                        preferences = preferences,
                        categories = categories,
                        weeklyDayOfWeek = weeklyDayOfWeek,
                    )
                    val encoded = NotificationScheduleSnapshotCodec.encode(snapshot)
                        ?: error("The local reminder schedule is invalid.")
                    check(
                        scheduleStateStorage().edit()
                            .putString(SCHEDULE_STATE_KEY, encoded)
                            .commit(),
                    ) { "The local reminder schedule could not be saved." }
                    categories.forEach { category -> scheduleCategory(snapshot, category) }
                    LocalNotificationScheduleResult.Scheduled(categories)
                }.getOrElse { error ->
                    cancelAll()
                    LocalNotificationScheduleResult.Failed(
                        error.message ?: "The reminder could not be scheduled.",
                    )
                }
            }
            NotificationPermissionState.DENIED -> {
                cancelAll()
                LocalNotificationScheduleResult.PermissionDenied
            }
            NotificationPermissionState.UNAVAILABLE,
            NotificationPermissionState.UNKNOWN,
            -> {
                cancelAll()
                LocalNotificationScheduleResult.Unavailable
            }
        }
    }

    override fun cancelAll() {
        cancelAlarmIntents()
        scheduleStateStorage().edit().remove(SCHEDULE_STATE_KEY).commit()
    }

    fun restorePersistedSchedule() {
        val encoded = scheduleStateStorage().getString(SCHEDULE_STATE_KEY, null) ?: return
        val snapshot = NotificationScheduleSnapshotCodec.decode(encoded)
        if (snapshot == null) {
            cancelAll()
            return
        }
        runCatching {
            cancelAlarmIntents()
            createChannel()
            snapshot.categories.forEach { category -> scheduleCategory(snapshot, category) }
        }.onFailure {
            cancelAlarmIntents()
        }
    }

    fun scheduleNextOccurrence(categoryId: String) {
        val category = LocalNotificationCategory.entries.singleOrNull { it.id == categoryId } ?: return
        val snapshot = scheduleStateStorage().getString(SCHEDULE_STATE_KEY, null)
            ?.let(NotificationScheduleSnapshotCodec::decode)
            ?: return
        if (category !in snapshot.categories) return
        runCatching { scheduleCategory(snapshot, category) }
    }

    private fun cancelAlarmIntents() {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        listOf(
            CONTINUE_NOTIFICATION_ID to CONTINUE_CATEGORY,
            REVIEW_NOTIFICATION_ID to REVIEW_CATEGORY,
        ).forEach { (requestCode, category) ->
            alarmManager.cancel(pendingIntent(requestCode, category))
        }
    }

    private fun scheduleStateStorage() =
        context.getSharedPreferences(SCHEDULE_STATE_PREFERENCES, Context.MODE_PRIVATE)

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
        snapshot: NotificationScheduleSnapshot,
        category: LocalNotificationCategory,
    ) {
        val preferences = snapshot.preferences
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
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            nextNotificationTriggerMillis(
                preferences = preferences,
                nowMillis = System.currentTimeMillis(),
                weeklyDayOfWeek = snapshot.weeklyDayOfWeek,
            ),
            pendingIntent(requestCode, categoryId),
        )
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

internal fun nextNotificationTriggerMillis(
    preferences: NotificationPreferences,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    weeklyDayOfWeek: Int? = null,
): Long {
    require(preferences.isValid)
    val now = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
    val trigger = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, preferences.hour)
        set(Calendar.MINUTE, preferences.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    when (preferences.cadence) {
        NotificationCadence.DAILY -> if (trigger.timeInMillis <= now.timeInMillis) {
            trigger.add(Calendar.DAY_OF_YEAR, 1)
        }
        NotificationCadence.WEEKLY -> {
            val anchorDay = weeklyDayOfWeek ?: now.get(Calendar.DAY_OF_WEEK)
            require(anchorDay in Calendar.SUNDAY..Calendar.SATURDAY)
            val daysUntilAnchor = (anchorDay - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
            trigger.add(Calendar.DAY_OF_YEAR, daysUntilAnchor)
            if (trigger.timeInMillis <= now.timeInMillis) {
                trigger.add(Calendar.DAY_OF_YEAR, 7)
            }
        }
    }
    return trigger.timeInMillis
}

class EvidriloLocalNotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> runCatching { AndroidLocalNotificationPlatform.restoreScheduledReminders(context) }
        }
    }
}

class EvidriloLocalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val category = intent.getStringExtra(CATEGORY_EXTRA) ?: return
        val (title, body) = when (category) {
            CONTINUE_CATEGORY -> "Continue your Evidrilo case" to
                "Your evidence workspace is ready when you are."
            REVIEW_CATEGORY -> "Review your Evidrilo changes" to
                "Revisit the evidence and see what changed in your conclusion."
            else -> return
        }
        AndroidLocalNotificationScheduler(context.applicationContext).scheduleNextOccurrence(category)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
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
