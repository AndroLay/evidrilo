package dev.nextgen.mobile.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun resetToHomeDiscardsTransientSurfaceHistory() {
        val state = EvidriloNavigationState()
            .open(EvidriloDestination.PREMIUM)
            .open(EvidriloDestination.ACCOUNT)

        assertEquals(EvidriloNavigationState(), state.resetToHome())
    }

    @Test
    fun guideIsAReachableUtilitySurface() {
        val state = EvidriloNavigationState().open(EvidriloDestination.GUIDE)

        assertEquals(EvidriloDestination.GUIDE, state.current)
        assertEquals(EvidriloDestination.HOME, state.back().current)
    }
}
