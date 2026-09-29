package dev.nextgen.mobile.notifications

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalNotificationScheduleTimeTest {
    @Test
    fun daily_occurrence_keeps_the_selected_local_hour_across_daylight_saving_change() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val now = GregorianCalendar(zone).apply {
            set(2026, Calendar.MARCH, 7, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val preferences = NotificationPreferences(
            enabled = true,
            continueUnfinishedEnabled = true,
            hour = 9,
            minute = 0,
        )

        val next = nextNotificationTriggerMillis(preferences, now, zone)
        val local = GregorianCalendar(zone).apply { timeInMillis = next }

        assertEquals(Calendar.SUNDAY, local.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, local.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, local.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, local.get(Calendar.MINUTE))
        assertEquals(22L * 60 * 60 * 1000, next - now)
    }

    @Test
    fun weekly_occurrence_keeps_its_anchor_weekday_after_reboot() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val now = GregorianCalendar(zone).apply {
            set(2026, Calendar.MARCH, 10, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val preferences = NotificationPreferences(
            enabled = true,
            continueUnfinishedEnabled = true,
            cadence = NotificationCadence.WEEKLY,
            hour = 9,
            minute = 0,
        )

        val next = nextNotificationTriggerMillis(
            preferences = preferences,
            nowMillis = now,
            timeZone = zone,
            weeklyDayOfWeek = Calendar.SUNDAY,
        )
        val local = GregorianCalendar(zone).apply { timeInMillis = next }

        assertEquals(Calendar.SUNDAY, local.get(Calendar.DAY_OF_WEEK))
        assertEquals(15, local.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, local.get(Calendar.HOUR_OF_DAY))
    }
}
