package dev.nextgen.mobile.navigation

import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED

internal enum class EvidriloDestination {
    HOME,
    PROJECT_CATALOG,
    PROJECT_FAMILY_DETAIL,
    PROJECT_TEMPLATE_DETAIL,
    PROJECTS,
    PROJECT_EDITOR,
    SOURCES,
    WORKSPACE,
    EVIDENCE,
    EVIDENCE_LENS,
    CLAIM_TRACE,
    CLAIM_BOUNDARY,
    VERIFY_CLAIM,
    ACTION,
    EVIDENCE_DELTA,
    PROFILE,
    PRACTICE,
    PREMIUM,
    GUIDE,
    HISTORY,
    ACCOUNT,
    SETTINGS,
    WORKSPACE_PREFERENCES,
    NOTIFICATIONS,
    PRIVACY_DATA,
    SUPPORT,
    ABOUT,
}

/** Account-bound actions stay gated while local work, account entry, and settings remain guest-reachable. */
internal fun EvidriloDestination.requiresAuthenticatedFreeAccess(): Boolean {
    if (TEMPORARY_GUEST_MODE_ENABLED && this in localGuestDestinations) return false
    return when (this) {
        EvidriloDestination.ACCOUNT,
        EvidriloDestination.GUIDE,
        EvidriloDestination.SUPPORT,
        EvidriloDestination.ABOUT,
        EvidriloDestination.HOME,
        EvidriloDestination.PROJECT_CATALOG,
        EvidriloDestination.PROJECT_FAMILY_DETAIL,
        EvidriloDestination.PROJECT_TEMPLATE_DETAIL,
        EvidriloDestination.PROJECTS,
        EvidriloDestination.PROJECT_EDITOR,
        EvidriloDestination.PROFILE,
        EvidriloDestination.SETTINGS,
        EvidriloDestination.WORKSPACE_PREFERENCES,
        EvidriloDestination.NOTIFICATIONS,
        EvidriloDestination.PRIVACY_DATA,
        -> false
        else -> true
    }
}

private val localGuestDestinations = setOf(
    EvidriloDestination.SOURCES,
    EvidriloDestination.WORKSPACE,
    EvidriloDestination.EVIDENCE,
    EvidriloDestination.EVIDENCE_LENS,
    EvidriloDestination.CLAIM_TRACE,
    EvidriloDestination.CLAIM_BOUNDARY,
    EvidriloDestination.VERIFY_CLAIM,
    EvidriloDestination.ACTION,
    EvidriloDestination.EVIDENCE_DELTA,
    EvidriloDestination.PRACTICE,
    EvidriloDestination.HISTORY,
)

/** Execute a protected mutation only after the account gate has been satisfied. */
internal fun runWithAuthenticatedAccess(
    destination: EvidriloDestination,
    authenticated: Boolean,
    onAuthenticationRequired: (EvidriloDestination) -> Unit,
    action: () -> Unit,
): Boolean {
    if (destination.requiresAuthenticatedFreeAccess() && !authenticated) {
        onAuthenticationRequired(destination)
        return false
    }
    action()
    return true
}
