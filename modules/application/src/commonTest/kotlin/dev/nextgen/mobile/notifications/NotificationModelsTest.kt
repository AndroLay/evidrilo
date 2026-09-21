package dev.nextgen.mobile.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationModelsTest {
    @Test
    fun defaults_keep_local_notifications_off() {
        val preferences = NotificationPreferences()

        assertFalse(preferences.enabled)
        assertFalse(preferences.continueUnfinishedEnabled)
        assertFalse(preferences.reviewCompletedEnabled)
        assertEquals(NotificationCadence.DAILY, preferences.cadence)
        assertEquals(9, preferences.hour)
        assertEquals(0, preferences.minute)
        assertTrue(preferences.isValid)
    }

    @Test
    fun active_categories_require_master_toggle_and_matching_local_state() {
        val preferences = NotificationPreferences(
            enabled = true,
            continueUnfinishedEnabled = true,
            reviewCompletedEnabled = true,
        )

        assertEquals(
            listOf(LocalNotificationCategory.CONTINUE_UNFINISHED, LocalNotificationCategory.REVIEW_COMPLETED),
            preferences.activeCategories(hasUnfinishedCase = true, hasCompletedCase = true),
        )
        assertEquals(
            listOf(LocalNotificationCategory.CONTINUE_UNFINISHED),
            preferences.activeCategories(hasUnfinishedCase = true, hasCompletedCase = false),
        )
        assertTrue(
            preferences.copy(enabled = false)
                .activeCategories(hasUnfinishedCase = true, hasCompletedCase = true)
                .size == 0,
        )
    }

    @Test
    fun invalid_time_is_rejected_without_changing_other_preferences() {
        val preferences = NotificationPreferences(
            enabled = true,
            continueUnfinishedEnabled = true,
            cadence = NotificationCadence.WEEKLY,
            hour = 24,
            minute = 60,
        )

        assertFalse(preferences.isValid)
        assertEquals(NotificationCadence.WEEKLY, preferences.cadence)
        assertEquals(24, preferences.hour)
        assertEquals(60, preferences.minute)
    }

    @Test
    fun storage_codec_round_trips_a_valid_preference_set() {
        val original = NotificationPreferences(
            enabled = true,
            continueUnfinishedEnabled = true,
            reviewCompletedEnabled = false,
            cadence = NotificationCadence.WEEKLY,
            hour = 18,
            minute = 45,
        )

        assertEquals(original, NotificationPreferencesCodec.decode(NotificationPreferencesCodec.encode(original)))
    }

    @Test
    fun storage_codec_rejects_unknown_or_malformed_values() {
        assertEquals(null, NotificationPreferencesCodec.decode("v1|true|true|true|MONTHLY|9|0"))
        assertEquals(null, NotificationPreferencesCodec.decode("not-a-preference"))
        assertEquals(null, NotificationPreferencesCodec.decode("v1|true|true|true|DAILY|24|0"))
    }
}
