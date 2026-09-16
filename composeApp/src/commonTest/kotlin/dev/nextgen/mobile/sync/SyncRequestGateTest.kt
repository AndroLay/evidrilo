package dev.nextgen.mobile.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRequestGateTest {
    @Test
    fun stale_account_or_consent_results_cannot_update_the_current_sync_surface() {
        val gate = SyncRequestGate()
        val token = gate.begin(ACCOUNT_ID, SyncConsent.GRANTED)

        assertTrue(gate.isCurrent(token, ACCOUNT_ID, SyncConsent.GRANTED))
        assertFalse(gate.isCurrent(token, OTHER_ACCOUNT_ID, SyncConsent.GRANTED))
        assertFalse(gate.isCurrent(token, ACCOUNT_ID, SyncConsent.NOT_GRANTED))

        gate.invalidate()
        assertFalse(gate.isCurrent(token, ACCOUNT_ID, SyncConsent.GRANTED))
    }

    private companion object {
        const val ACCOUNT_ID = "123e4567-e89b-42d3-a456-426614174002"
        const val OTHER_ACCOUNT_ID = "123e4567-e89b-42d3-a456-426614174003"
    }
}
