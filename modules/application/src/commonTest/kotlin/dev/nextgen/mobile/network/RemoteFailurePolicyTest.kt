package dev.nextgen.mobile.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteFailurePolicyTest {
    @Test
    fun only_confirmed_device_disconnect_is_classified_as_offline() {
        val offline = resolveRemoteFailure(
            connectivity = DeviceConnectivity.OFFLINE,
            operation = RemoteOperationKind.READ_ONLY,
            failure = RemoteFailureKind.TRANSPORT,
            requestWasDispatched = true,
        )
        val onlineApiFailure = resolveRemoteFailure(
            connectivity = DeviceConnectivity.ONLINE,
            operation = RemoteOperationKind.READ_ONLY,
            failure = RemoteFailureKind.TRANSPORT,
            requestWasDispatched = true,
        )
        val unknownConnectivity = resolveRemoteFailure(
            connectivity = DeviceConnectivity.UNKNOWN,
            operation = RemoteOperationKind.READ_ONLY,
            failure = RemoteFailureKind.TRANSPORT,
            requestWasDispatched = true,
        )
        val limitedConnectivity = resolveRemoteFailure(
            connectivity = DeviceConnectivity.LIMITED,
            operation = RemoteOperationKind.READ_ONLY,
            failure = RemoteFailureKind.TRANSPORT,
            requestWasDispatched = true,
        )

        assertEquals(RemoteFailureState.OFFLINE, offline.state)
        assertEquals(RemoteFailureState.SERVICE_UNAVAILABLE, onlineApiFailure.state)
        assertEquals(RemoteFailureState.SERVICE_UNAVAILABLE, unknownConnectivity.state)
        assertEquals(RemoteFailureState.SERVICE_UNAVAILABLE, limitedConnectivity.state)
        assertTrue(offline.retryAllowed)
        assertTrue(onlineApiFailure.retryAllowed)
    }

    @Test
    fun definitive_http_rejection_is_not_a_connectivity_failure_or_retry() {
        val result = resolveRemoteFailure(
            connectivity = DeviceConnectivity.OFFLINE,
            operation = RemoteOperationKind.READ_ONLY,
            failure = RemoteFailureKind.DEFINITIVE_REJECTION,
            requestWasDispatched = true,
        )

        assertEquals(RemoteFailureState.REJECTED, result.state)
        assertFalse(result.retryAllowed)
        assertFalse(result.reconciliationRequired)
    }

    @Test
    fun sent_mutation_with_lost_response_has_unknown_outcome_even_if_device_is_offline() {
        val result = resolveRemoteFailure(
            connectivity = DeviceConnectivity.OFFLINE,
            operation = RemoteOperationKind.NON_IDEMPOTENT_MUTATION,
            failure = RemoteFailureKind.TIMEOUT,
            requestWasDispatched = true,
        )

        assertEquals(RemoteFailureState.OUTCOME_UNKNOWN, result.state)
        assertFalse(result.retryAllowed)
        assertTrue(result.reconciliationRequired)
        assertFalse(result.sameIntentReplayAllowed)
    }

    @Test
    fun idempotent_mutation_can_only_replay_the_same_intent_after_reconciliation() {
        val noKey = resolveRemoteFailure(
            connectivity = DeviceConnectivity.ONLINE,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            failure = RemoteFailureKind.TRANSPORT,
            requestWasDispatched = true,
        )
        val withKey = resolveRemoteFailure(
            connectivity = DeviceConnectivity.ONLINE,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            failure = RemoteFailureKind.TRANSPORT,
            requestWasDispatched = true,
            idempotencyKey = "same-intent-01",
        )

        assertEquals(RemoteFailureState.OUTCOME_UNKNOWN, noKey.state)
        assertFalse(noKey.sameIntentReplayAllowed)
        assertTrue(withKey.reconciliationRequired)
        assertTrue(withKey.sameIntentReplayAllowed)
        assertFalse(withKey.retryAllowed)
    }

    @Test
    fun cancellation_before_dispatch_is_known_cancelled() {
        val result = resolveRemoteFailure(
            connectivity = DeviceConnectivity.UNKNOWN,
            operation = RemoteOperationKind.NON_IDEMPOTENT_MUTATION,
            failure = RemoteFailureKind.CANCELLED,
            requestWasDispatched = false,
        )

        assertEquals(RemoteFailureState.CANCELLED, result.state)
        assertFalse(result.reconciliationRequired)
        assertFalse(result.retryAllowed)
    }
}
