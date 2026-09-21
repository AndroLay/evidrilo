package dev.nextgen.mobile.notifications

enum class LocalNotificationCategory(
    val id: String,
    val title: String,
    val description: String,
) {
    CONTINUE_UNFINISHED(
        id = "continue_unfinished",
        title = "Continue an unfinished case",
        description = "A gentle reminder to return to work already in progress.",
    ),
    REVIEW_COMPLETED(
        id = "review_completed",
        title = "Review a completed case",
        description = "A reminder to revisit your evidence and conclusion change.",
    ),
}

enum class NotificationCadence(
    val id: String,
    val label: String,
) {
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
}

enum class NotificationPermissionState {
    UNKNOWN,
    GRANTED,
    DENIED,
    UNAVAILABLE,
}

data class NotificationPreferences(
    val enabled: Boolean = false,
    val continueUnfinishedEnabled: Boolean = false,
    val reviewCompletedEnabled: Boolean = false,
    val cadence: NotificationCadence = NotificationCadence.DAILY,
    val hour: Int = 9,
    val minute: Int = 0,
) {
    val isValid: Boolean
        get() = hour in 0..23 && minute in 0..59

    fun activeCategories(
        hasUnfinishedCase: Boolean,
        hasCompletedCase: Boolean,
    ): List<LocalNotificationCategory> {
        if (!enabled || !isValid) return emptyList()
        return buildList {
            if (continueUnfinishedEnabled && hasUnfinishedCase) {
                add(LocalNotificationCategory.CONTINUE_UNFINISHED)
            }
            if (reviewCompletedEnabled && hasCompletedCase) {
                add(LocalNotificationCategory.REVIEW_COMPLETED)
            }
        }
    }

    fun scheduleLabel(categories: List<LocalNotificationCategory>): String? {
        if (categories.isEmpty() || !isValid) return null
        val paddedHour = hour.toString().padStart(2, '0')
        val paddedMinute = minute.toString().padStart(2, '0')
        return "${cadence.label} at $paddedHour:$paddedMinute"
    }
}

object NotificationPreferencesCodec {
    private const val VERSION = "v1"

    fun encode(preferences: NotificationPreferences): String = listOf(
        VERSION,
        preferences.enabled,
        preferences.continueUnfinishedEnabled,
        preferences.reviewCompletedEnabled,
        preferences.cadence.id,
        preferences.hour,
        preferences.minute,
    ).joinToString("|")

    fun decode(value: String): NotificationPreferences? = runCatching {
        val fields = value.split('|')
        require(fields.size == 7)
        require(fields[0] == VERSION)
        val preferences = NotificationPreferences(
            enabled = fields[1].toStrictBoolean(),
            continueUnfinishedEnabled = fields[2].toStrictBoolean(),
            reviewCompletedEnabled = fields[3].toStrictBoolean(),
            cadence = NotificationCadence.entries.first { it.id == fields[4] },
            hour = fields[5].toInt(),
            minute = fields[6].toInt(),
        )
        require(preferences.isValid)
        preferences
    }.getOrNull()

    private fun String.toStrictBoolean(): Boolean = when (this) {
        "true" -> true
        "false" -> false
        else -> error("boolean required")
    }
}

sealed interface LocalNotificationScheduleResult {
    data class Scheduled(
        val categories: List<LocalNotificationCategory>,
    ) : LocalNotificationScheduleResult

    data object PermissionDenied : LocalNotificationScheduleResult

    data object Unavailable : LocalNotificationScheduleResult

    data class Failed(val message: String) : LocalNotificationScheduleResult
}

interface LocalNotificationScheduler {
    suspend fun permissionState(): NotificationPermissionState

    suspend fun requestPermission(): NotificationPermissionState

    suspend fun schedule(
        preferences: NotificationPreferences,
        hasUnfinishedCase: Boolean,
        hasCompletedCase: Boolean,
    ): LocalNotificationScheduleResult

    fun cancelAll()

    fun openSystemSettings()
}

class NoopLocalNotificationScheduler : LocalNotificationScheduler {
    override suspend fun permissionState(): NotificationPermissionState =
        NotificationPermissionState.UNAVAILABLE

    override suspend fun requestPermission(): NotificationPermissionState =
        NotificationPermissionState.UNAVAILABLE

    override suspend fun schedule(
        preferences: NotificationPreferences,
        hasUnfinishedCase: Boolean,
        hasCompletedCase: Boolean,
    ): LocalNotificationScheduleResult = LocalNotificationScheduleResult.Unavailable

    override fun cancelAll() = Unit

    override fun openSystemSettings() = Unit
}

expect fun createLocalNotificationScheduler(): LocalNotificationScheduler
