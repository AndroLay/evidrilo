package dev.nextgen.mobile.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloAuthenticatedAccessPolicyTest {
    @Test
    fun localProjectAndReadOnlyCatalogRoutesRemainAvailableWithoutAnAccount() {
        val localProjectRoutes = listOf(
            EvidriloDestination.HOME,
            EvidriloDestination.PROJECT_CATALOG,
            EvidriloDestination.PROJECT_FAMILY_DETAIL,
            EvidriloDestination.PROJECT_TEMPLATE_DETAIL,
            EvidriloDestination.PROJECTS,
            EvidriloDestination.PROJECT_EDITOR,
        )

        localProjectRoutes.forEach { destination ->
            assertFalse(destination.requiresAuthenticatedFreeAccess(), "$destination should remain usable without sign-in")
        }

        var mutationCount = 0
        val completed = runWithAuthenticatedAccess(
            destination = EvidriloDestination.PROJECTS,
            authenticated = false,
            onAuthenticationRequired = { error("Local project action must not request sign-in") },
            action = { mutationCount += 1 },
        )

        assertTrue(completed)
        assertEquals(1, mutationCount)
    }

    @Test
    fun local_case_workflow_and_history_are_available_to_guest_but_premium_stays_protected() {
        val localCaseRoutes = listOf(
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

        localCaseRoutes.forEach { destination ->
            assertFalse(destination.requiresAuthenticatedFreeAccess(), "$destination is local and must work in guest mode")
        }
        assertTrue(EvidriloDestination.PREMIUM.requiresAuthenticatedFreeAccess())
    }

    @Test
    fun accountAndInformationalHelpSurfacesRemainReachableBeforeSignIn() {
        val public = listOf(
            EvidriloDestination.ACCOUNT,
            EvidriloDestination.GUIDE,
            EvidriloDestination.SUPPORT,
            EvidriloDestination.ABOUT,
            EvidriloDestination.PROFILE,
            EvidriloDestination.SETTINGS,
        )

        public.forEach { destination ->
            assertFalse(destination.requiresAuthenticatedFreeAccess(), "$destination should remain available for account/help access")
        }
    }

    @Test
    fun all_unlisted_destinations_fail_closed_and_demo_does_not_unlock_product_routes() {
        val public = setOf(
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
            EvidriloDestination.PROFILE,
            EvidriloDestination.SETTINGS,
            EvidriloDestination.WORKSPACE_PREFERENCES,
            EvidriloDestination.NOTIFICATIONS,
            EvidriloDestination.PRIVACY_DATA,
        )

        EvidriloDestination.entries.filterNot(public::contains).forEach { destination ->
            assertTrue(destination.requiresAuthenticatedFreeAccess(), "$destination must remain gated while demo is active")
        }
    }

    @Test
    fun premium_action_does_not_mutate_product_state_without_an_account() {
        var mutationCount = 0
        var requestedDestination: EvidriloDestination? = null

        val completed = runWithAuthenticatedAccess(
            destination = EvidriloDestination.PREMIUM,
            authenticated = false,
            onAuthenticationRequired = { requestedDestination = it },
            action = { mutationCount += 1 },
        )

        assertFalse(completed)
        assertEquals(0, mutationCount)
        assertEquals(EvidriloDestination.PREMIUM, requestedDestination)
    }

    @Test
    fun protected_action_runs_once_after_authentication() {
        var mutationCount = 0
        var authenticationRequested = false

        val completed = runWithAuthenticatedAccess(
            destination = EvidriloDestination.PRACTICE,
            authenticated = true,
            onAuthenticationRequired = { authenticationRequested = true },
            action = { mutationCount += 1 },
        )

        assertTrue(completed)
        assertEquals(1, mutationCount)
        assertFalse(authenticationRequested)
    }
}
