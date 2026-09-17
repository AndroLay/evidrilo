package dev.nextgen.mobile.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPresentationTest {
    @Test
    fun sync_stays_local_first_until_account_and_explicit_consent_are_present() {
        val signedOut = syncPresentation(
            consent = SyncConsent.NOT_GRANTED,
            signedIn = false,
            pendingCount = 0,
            storageAvailable = true,
        )
        assertFalse(signedOut.canSyncNow)
        assertTrue(signedOut.subtitle.contains("sign in", ignoreCase = true))

        val ready = syncPresentation(
            consent = SyncConsent.GRANTED,
            signedIn = true,
            pendingCount = 0,
            storageAvailable = true,
        )
        assertTrue(ready.canSyncNow)
        assertTrue(ready.subtitle.contains("draft", ignoreCase = true))

        val pending = syncPresentation(
            consent = SyncConsent.GRANTED,
            signedIn = true,
            pendingCount = 2,
            storageAvailable = true,
        )
        assertTrue(pending.subtitle.contains("2"))
    }

    @Test
    fun unavailable_storage_is_never_presented_as_successful_cloud_sync() {
        val presentation = syncPresentation(
            consent = SyncConsent.GRANTED,
            signedIn = true,
            pendingCount = 0,
            storageAvailable = false,
        )

        assertFalse(presentation.canSyncNow)
        assertTrue(presentation.subtitle.contains("unavailable", ignoreCase = true))
    }
}
