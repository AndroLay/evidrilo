package dev.nextgen.mobile.network

/** Last device-network observation. It says nothing about a particular API/provider. */
enum class DeviceConnectivity {
    UNKNOWN,
    ONLINE,
    OFFLINE,
    LIMITED,
}

enum class RemoteOperationKind {
    READ_ONLY,
    IDEMPOTENT_MUTATION,
    NON_IDEMPOTENT_MUTATION,
}

enum class RemoteFailureKind {
    TRANSPORT,
    TIMEOUT,
    TRANSIENT_HTTP,
    DEFINITIVE_REJECTION,
    CANCELLED,
}

enum class RemoteFailureState {
    OFFLINE,
    SERVICE_UNAVAILABLE,
    REJECTED,
    CANCELLED,
    OUTCOME_UNKNOWN,
}

data class RemoteFailureResolution(
    val state: RemoteFailureState,
    /** Safe immediate retry. False for any mutation whose server outcome is not known. */
    val retryAllowed: Boolean,
    /** The client must read/reconcile server state before offering a retry. */
    val reconciliationRequired: Boolean,
    /** If the endpoint contract is idempotent, replay only with the same intent key. */
    val sameIntentReplayAllowed: Boolean,
)

/**
 * Converts a failed remote operation into a truthful, retry-safe state.
 * Device-offline is reported only from a positive platform observation. A
 * timeout after dispatching a mutation is outcome-unknown even if connectivity
 * is now offline: the server may already have committed it.
 */
fun resolveRemoteFailure(
    connectivity: DeviceConnectivity,
    operation: RemoteOperationKind,
    failure: RemoteFailureKind,
    requestWasDispatched: Boolean,
    idempotencyKey: String? = null,
): RemoteFailureResolution {
    if (failure == RemoteFailureKind.DEFINITIVE_REJECTION) {
        return RemoteFailureResolution(
            state = RemoteFailureState.REJECTED,
            retryAllowed = false,
            reconciliationRequired = false,
            sameIntentReplayAllowed = false,
        )
    }

    if (failure == RemoteFailureKind.CANCELLED && !requestWasDispatched) {
        return RemoteFailureResolution(
            state = RemoteFailureState.CANCELLED,
            retryAllowed = false,
            reconciliationRequired = false,
            sameIntentReplayAllowed = false,
        )
    }

    val mutationWasDispatched = requestWasDispatched && operation != RemoteOperationKind.READ_ONLY
    if (mutationWasDispatched) {
        return RemoteFailureResolution(
            state = RemoteFailureState.OUTCOME_UNKNOWN,
            retryAllowed = false,
            reconciliationRequired = true,
            sameIntentReplayAllowed = operation == RemoteOperationKind.IDEMPOTENT_MUTATION &&
                !idempotencyKey.isNullOrBlank(),
        )
    }

    if (failure == RemoteFailureKind.CANCELLED) {
        return RemoteFailureResolution(
            state = RemoteFailureState.CANCELLED,
            retryAllowed = false,
            reconciliationRequired = false,
            sameIntentReplayAllowed = false,
        )
    }

    val isOffline = connectivity == DeviceConnectivity.OFFLINE
    return RemoteFailureResolution(
        state = if (isOffline) RemoteFailureState.OFFLINE else RemoteFailureState.SERVICE_UNAVAILABLE,
        retryAllowed = true,
        reconciliationRequired = false,
        sameIntentReplayAllowed = false,
    )
}
