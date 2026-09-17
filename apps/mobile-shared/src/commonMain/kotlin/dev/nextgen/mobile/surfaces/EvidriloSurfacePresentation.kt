package dev.nextgen.mobile.surfaces

import dev.nextgen.mobile.storage.ConclusionSessionPhase
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot

internal enum class HistorySurfaceAvailability {
    EMPTY,
    AVAILABLE,
}

internal fun historySurfaceAvailability(snapshot: ConclusionSessionSnapshot?): HistorySurfaceAvailability =
    if (snapshot?.phase == ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY) {
        HistorySurfaceAvailability.AVAILABLE
    } else {
        HistorySurfaceAvailability.EMPTY
    }

internal fun cloudSyncDisclosure(): String =
    "Cloud progress sync is optional, requires a verified account and explicit consent, and keeps learner draft text on this device."

internal fun disclosureActionLabel(title: String, expanded: Boolean): String =
    if (expanded) "Collapse $title" else "Expand $title"

internal fun disclosureStateDescription(expanded: Boolean): String =
    if (expanded) "Expanded" else "Collapsed"
