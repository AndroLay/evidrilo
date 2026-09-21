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
