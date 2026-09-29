package dev.nextgen.mobile.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvidriloNavigationStateTest {
    @Test
    fun startsAtHome() {
        assertEquals(EvidriloDestination.HOME, EvidriloNavigationState().current)
        assertEquals(listOf(EvidriloDestination.HOME), EvidriloNavigationState().stack)
    }

    @Test
    fun openingTheCurrentDestinationDoesNotDuplicateTheStack() {
        val state = EvidriloNavigationState().open(EvidriloDestination.HISTORY)

        assertEquals(state, state.open(EvidriloDestination.HISTORY))
        assertEquals(
            listOf(EvidriloDestination.HOME, EvidriloDestination.HISTORY),
            state.stack,
        )
    }

    @Test
    fun backReturnsToThePreviousSurfaceAndNeverLeavesAnEmptyStack() {
        val state = EvidriloNavigationState()
            .open(EvidriloDestination.SETTINGS)
            .open(EvidriloDestination.ABOUT)

        assertEquals(EvidriloDestination.SETTINGS, state.back().current)
        assertEquals(EvidriloDestination.HOME, state.back().back().current)
        assertEquals(EvidriloDestination.HOME, state.back().back().back().current)
    }

    @Test
    fun settingsSubpagesArePublicAndBackReturnsToTheirEntrySurface() {
        val destinations = listOf(
            EvidriloDestination.WORKSPACE_PREFERENCES,
            EvidriloDestination.NOTIFICATIONS,
            EvidriloDestination.PRIVACY_DATA,
        )

        destinations.forEach { destination ->
            assertFalse(destination.requiresAuthenticatedFreeAccess())
            val fromSettings = EvidriloNavigationState()
                .open(EvidriloDestination.SETTINGS)
                .open(destination)
            assertEquals(EvidriloDestination.SETTINGS, fromSettings.back().current)

            val fromProfile = EvidriloNavigationState()
                .selectRoot(EvidriloDestination.PROFILE)
                .open(destination)
            assertEquals(EvidriloDestination.PROFILE, fromProfile.back().current)
        }
    }

    @Test
    fun premiumStillRequiresAnAccountWhileLocalFreeProjectRoutesDoNot() {
        assertFalse(EvidriloDestination.PROJECTS.requiresAuthenticatedFreeAccess())
        assertFalse(EvidriloDestination.WORKSPACE_PREFERENCES.requiresAuthenticatedFreeAccess())
        assertTrue(EvidriloDestination.PREMIUM.requiresAuthenticatedFreeAccess())

        var authenticationWasRequested = false
        var actionRan = false
        val accepted = runWithAuthenticatedAccess(
            destination = EvidriloDestination.PREMIUM,
            authenticated = false,
            onAuthenticationRequired = { authenticationWasRequested = true },
            action = { actionRan = true },
        )

        assertFalse(accepted)
        assertTrue(authenticationWasRequested)
        assertFalse(actionRan)
    }

    @Test
    fun systemBackIsEnabledOnlyWhileThereIsAPreviousDestination() {
        val home = EvidriloNavigationState()
        val catalog = home.open(EvidriloDestination.PROJECT_CATALOG)
        val detail = catalog.open(EvidriloDestination.PROJECT_FAMILY_DETAIL)

        assertFalse(home.canHandleSystemBack)
        assertTrue(catalog.canHandleSystemBack)
        assertTrue(detail.canHandleSystemBack)
        assertEquals(catalog, detail.back())
        assertEquals(home, detail.back().back())
        assertFalse(detail.back().back().canHandleSystemBack)
    }

    @Test
    fun resetToHomeDiscardsTransientSurfaceHistory() {
        val state = EvidriloNavigationState()
            .open(EvidriloDestination.PREMIUM)
            .open(EvidriloDestination.ACCOUNT)

        assertEquals(EvidriloNavigationState(), state.resetToHome())
    }

    @Test
    fun accountGateReturnsToTheSafePublicOriginWithoutRetainingProtectedRoutes() {
        val fromGuide = EvidriloNavigationState()
            .open(EvidriloDestination.GUIDE)
            .openAccountGate()

        assertEquals(
            listOf(EvidriloDestination.HOME, EvidriloDestination.GUIDE, EvidriloDestination.ACCOUNT),
            fromGuide.stack,
        )
        assertEquals(EvidriloDestination.GUIDE, fromGuide.back().current)

        val fromProtectedLearning = EvidriloNavigationState()
            .open(EvidriloDestination.PREMIUM)
            .openAccountGate()

        assertEquals(
            listOf(EvidriloDestination.GUIDE, EvidriloDestination.ACCOUNT),
            fromProtectedLearning.stack,
        )
        assertFalse(fromProtectedLearning.back().current.requiresAuthenticatedFreeAccess())
    }

    @Test
    fun accountGatePreservesThePublicPartOfAnInformationalRouteStack() {
        val state = EvidriloNavigationState()
            .open(EvidriloDestination.GUIDE)
            .open(EvidriloDestination.SUPPORT)
            .openAccountGate()

        assertEquals(
            listOf(
                EvidriloDestination.HOME,
                EvidriloDestination.GUIDE,
                EvidriloDestination.SUPPORT,
                EvidriloDestination.ACCOUNT,
            ),
            state.stack,
        )
        assertEquals(EvidriloDestination.SUPPORT, state.back().current)
    }

    @Test
    fun profileSignInGateKeepsProfileAsTheRequestedReturnDestination() {
        val state = EvidriloNavigationState()
            .selectRoot(EvidriloDestination.PROFILE)
            .openAccountGate()

        assertEquals(
            listOf(EvidriloDestination.HOME, EvidriloDestination.PROFILE, EvidriloDestination.ACCOUNT),
            state.stack,
        )
        assertEquals(EvidriloDestination.PROFILE, state.back().current)

        val afterSuccessfulSignIn = EvidriloNavigationState.afterSuccessfulAccountGate(EvidriloDestination.PROFILE)
        assertEquals(listOf(EvidriloDestination.HOME, EvidriloDestination.PROFILE), afterSuccessfulSignIn.stack)
    }

    @Test
    fun profileProjectSettingsAndSupportShortcutsReturnToProfile() {
        val profile = EvidriloNavigationState().selectRoot(EvidriloDestination.PROFILE)

        listOf(
            EvidriloDestination.PROJECTS,
            EvidriloDestination.SETTINGS,
            EvidriloDestination.SUPPORT,
        ).forEach { destination ->
            val opened = profile.open(destination)
            assertEquals(destination, opened.current)
            assertEquals(EvidriloDestination.PROFILE, opened.back().current)
        }
    }

    @Test
    fun guideIsAReachableUtilitySurface() {
        val state = EvidriloNavigationState().open(EvidriloDestination.GUIDE)

        assertEquals(EvidriloDestination.GUIDE, state.current)
        assertEquals(EvidriloDestination.HOME, state.back().current)
    }

    @Test
    fun projectCatalogFamilyAndTemplateDetailsAreStackedRoutesWithWorkingBackNavigation() {
        val catalog = EvidriloDestination.values()
            .singleOrNull { it.name == "PROJECT_CATALOG" }
        assertNotNull(catalog, "Home must be able to open the project catalog.")

        val familyDetail = EvidriloDestination.values()
            .singleOrNull { it.name == "PROJECT_FAMILY_DETAIL" }
        assertNotNull(familyDetail, "A catalog family card must open its detail page.")
        val templateDetail = EvidriloDestination.PROJECT_TEMPLATE_DETAIL

        val detailState = EvidriloNavigationState()
            .open(catalog)
            .open(familyDetail)
            .open(templateDetail)

        assertEquals(templateDetail, detailState.current)
        assertEquals(familyDetail, detailState.back().current)
        assertEquals(catalog, detailState.back().back().current)
        assertEquals(EvidriloDestination.HOME, detailState.back().back().back().current)
    }

    @Test
    fun localProjectListAndEditorAreReachableAndReturnToTheirOrigin() {
        val projectList = EvidriloDestination.PROJECTS
        val projectEditor = EvidriloDestination.PROJECT_EDITOR
        val fromCatalog = EvidriloNavigationState()
            .open(EvidriloDestination.PROJECT_CATALOG)
            .open(projectList)
            .open(projectEditor)

        assertEquals(projectEditor, fromCatalog.current)
        assertEquals(projectList, fromCatalog.back().current)
        assertEquals(EvidriloDestination.PROJECT_CATALOG, fromCatalog.back().back().current)

        val fromTemplate = EvidriloNavigationState()
            .open(EvidriloDestination.PROJECT_CATALOG)
            .open(EvidriloDestination.PROJECT_FAMILY_DETAIL)
            .open(EvidriloDestination.PROJECT_TEMPLATE_DETAIL)
            .open(projectEditor)
        assertEquals(EvidriloDestination.PROJECT_TEMPLATE_DETAIL, fromTemplate.back().current)
    }

    @Test
    fun evidence_journey_keeps_stacked_trace_and_verification_surfaces_reachable() {
        val state = EvidriloNavigationState()
            .open(EvidriloDestination.SOURCES)
            .open(EvidriloDestination.WORKSPACE)
            .open(EvidriloDestination.EVIDENCE)
            .open(EvidriloDestination.EVIDENCE_LENS)
            .open(EvidriloDestination.CLAIM_TRACE)
            .open(EvidriloDestination.CLAIM_BOUNDARY)
            .open(EvidriloDestination.ACTION)
            .open(EvidriloDestination.VERIFY_CLAIM)
            .open(EvidriloDestination.EVIDENCE_DELTA)

        assertEquals(EvidriloDestination.EVIDENCE_DELTA, state.current)
        assertEquals(EvidriloDestination.VERIFY_CLAIM, state.back().current)
        assertEquals(EvidriloDestination.ACTION, state.back().back().current)
        assertEquals(EvidriloDestination.CLAIM_BOUNDARY, state.back().back().back().current)
        assertEquals(EvidriloDestination.CLAIM_TRACE, state.back().back().back().back().current)
    }
}
