package dev.nextgen.mobile.sync

internal data class SyncPresentation(
    val title: String,
    val subtitle: String,
    val canSyncNow: Boolean,
)

internal fun syncPresentation(
    consent: SyncConsent,
    signedIn: Boolean,
    pendingCount: Int,
    storageAvailable: Boolean,
): SyncPresentation {
    val boundedPendingCount = pendingCount.coerceAtLeast(0)
    return when {
        !storageAvailable -> SyncPresentation(
            title = "Cloud progress sync",
            subtitle = "Local sync storage is unavailable; no cloud sync is active.",
            canSyncNow = false,
        )

        !signedIn -> SyncPresentation(
            title = "Cloud progress sync",
            subtitle = "Sign in to sync minimal progress metadata. Draft text stays on this device.",
            canSyncNow = false,
        )

        consent != SyncConsent.GRANTED -> SyncPresentation(
            title = "Cloud progress sync",
            subtitle = "Off by default. Enable it to sync minimal progress metadata; draft text stays local.",
            canSyncNow = false,
        )

        boundedPendingCount > 0 -> SyncPresentation(
            title = "Cloud progress sync",
            subtitle = "$boundedPendingCount progress item(s) waiting to sync.",
            canSyncNow = true,
        )

        else -> SyncPresentation(
            title = "Cloud progress sync",
            subtitle = "Ready. Only redacted progress metadata syncs; draft text stays local.",
            canSyncNow = true,
        )
    }
}
