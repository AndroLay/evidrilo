package dev.nextgen.mobile.surfaces

import dev.nextgen.mobile.storage.ConclusionSessionPhase
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot

enum class HistorySurfaceAvailability {
    EMPTY,
    AVAILABLE,
}

fun historySurfaceAvailability(snapshot: ConclusionSessionSnapshot?): HistorySurfaceAvailability =
    if (snapshot?.phase == ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY) {
        HistorySurfaceAvailability.AVAILABLE
    } else {
        HistorySurfaceAvailability.EMPTY
    }

fun cloudSyncDisclosure(): String =
    "Cloud progress sync is optional, requires a verified account and explicit consent, and keeps learner draft text on this device."

fun disclosureActionLabel(title: String, expanded: Boolean): String =
    if (expanded) "Collapse $title" else "Expand $title"

fun disclosureStateDescription(expanded: Boolean): String =
    if (expanded) "Expanded" else "Collapsed"
