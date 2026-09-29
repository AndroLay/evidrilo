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

const val SYSTEM_NOTIFICATION_PERMISSION_OFF_MESSAGE =
    "System notification permission is off. Open system settings to enable reminders."
const val NOTIFICATION_PERMISSION_REQUEST_DENIED_MESSAGE =
    "Permission was not granted. Open system settings if you want reminders."

data class NotificationPermissionUiState(
    val permission: NotificationPermissionState = NotificationPermissionState.UNKNOWN,
    val statusMessage: String? = null,
) {
    fun withPermission(refreshedPermission: NotificationPermissionState): NotificationPermissionUiState = copy(
        permission = refreshedPermission,
        statusMessage = if (
            refreshedPermission == NotificationPermissionState.GRANTED &&
            statusMessage in STALE_PERMISSION_DENIAL_MESSAGES
        ) {
            null
        } else {
            statusMessage
        },
    )

    private companion object {
        val STALE_PERMISSION_DENIAL_MESSAGES = setOf(
            SYSTEM_NOTIFICATION_PERMISSION_OFF_MESSAGE,
            NOTIFICATION_PERMISSION_REQUEST_DENIED_MESSAGE,
        )
    }
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

/** Minimal local state needed to restore the exact opted-in reminders after a device restart. */
data class NotificationScheduleSnapshot(
    val preferences: NotificationPreferences,
    val categories: List<LocalNotificationCategory>,
    /** Calendar.DAY_OF_WEEK anchor (1..7) for weekly reminders; absent for daily reminders. */
    val weeklyDayOfWeek: Int? = null,
) {
    val isValid: Boolean
        get() = preferences.enabled && preferences.isValid && categories.isNotEmpty() &&
            (if (preferences.cadence == NotificationCadence.WEEKLY) {
                weeklyDayOfWeek in 1..7
            } else {
                weeklyDayOfWeek == null
            }) &&
            categories.distinct().size == categories.size && categories.all { category ->
                when (category) {
                    LocalNotificationCategory.CONTINUE_UNFINISHED -> preferences.continueUnfinishedEnabled
                    LocalNotificationCategory.REVIEW_COMPLETED -> preferences.reviewCompletedEnabled
                }
            }
}

object NotificationScheduleSnapshotCodec {
    private const val VERSION = "v1"

    fun encode(snapshot: NotificationScheduleSnapshot): String? {
        if (!snapshot.isValid) return null
        return listOf(
            VERSION,
            NotificationPreferencesCodec.encode(snapshot.preferences),
            snapshot.categories.joinToString(",") { it.id },
            snapshot.weeklyDayOfWeek?.toString() ?: "-",
        ).joinToString("~")
    }

    fun decode(value: String?): NotificationScheduleSnapshot? = runCatching {
        requireNotNull(value)
        val fields = value.split('~')
        require(fields.size == 4 && fields[0] == VERSION)
        val preferences = requireNotNull(NotificationPreferencesCodec.decode(fields[1]))
        val categories = fields[2].split(',').map { id ->
            LocalNotificationCategory.entries.first { it.id == id }
        }
        val weeklyDayOfWeek = fields[3].takeUnless { it == "-" }?.toInt()
        NotificationScheduleSnapshot(preferences, categories, weeklyDayOfWeek)
            .also { require(it.isValid) }
    }.getOrNull()
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
