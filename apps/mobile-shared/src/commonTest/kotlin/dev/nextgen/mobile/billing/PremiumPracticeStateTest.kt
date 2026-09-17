package dev.nextgen.mobile.billing

import dev.nextgen.mobile.domain.conclusion.ConclusionEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PremiumPracticeStateTest {
    private val reducer = PremiumPracticeReducer()
    private val offer = BillingOffer("monthly", "Evidrilo Premium", "$1.00")
    private val yearly = BillingOffer("yearly", "Yearly", "$10.00")

    @Test
    fun openingPremiumStartsInLoadingState() {
        val state = reducer.reduce(PremiumPracticeState.Hidden, PremiumPracticeEvent.Open)

        assertTrue(state is PremiumPracticeState.Locked)
        assertEquals(BillingUiState.LOADING, state.billing.state)
    }

    @Test
    fun offerAndFailureStatesStayLockedWithoutUnlockingPremium() {
        var state: PremiumPracticeState = reducer.reduce(
            PremiumPracticeState.Hidden,
            PremiumPracticeEvent.Open,
        )
        state = reducer.reduce(state, PremiumPracticeEvent.BillingResult(BillingOutcome.OfferAvailable(offer)))
        assertTrue(state is PremiumPracticeState.Locked)
        assertTrue(state.billing.canPurchase)

        state = reducer.reduce(
            state,
            PremiumPracticeEvent.BillingResult(
                BillingOutcome.Failure(BillingOperation.PURCHASE, "Purchase failed."),
            ),
        )
        assertTrue(state is PremiumPracticeState.Locked)
        assertEquals(BillingUiState.FAILED, state.billing.state)
    }

    @Test
    fun locked_premium_surface_can_change_the_selected_package_before_purchase() {
        val locked = PremiumPracticeState.Locked(
            BillingPresentation.fromOutcome(
                BillingOutcome.OfferAvailable(offer, listOf(offer, yearly)),
            ),
        )

        val selected = reducer.reduce(locked, PremiumPracticeEvent.SelectOffer("yearly"))

        assertTrue(selected is PremiumPracticeState.Locked)
        assertEquals(yearly, selected.billing.selectedOffer)
    }

    @Test
    fun offer_result_after_access_retains_the_full_subscription_catalog() {
        val catalog = PremiumPracticeState.Catalog(
            billing = BillingPresentation.fromOutcome(
                BillingOutcome.Access(PremiumAccess.UNLOCKED),
            ),
        )

        val withOffers = reducer.reduce(
            catalog,
            PremiumPracticeEvent.BillingResult(
                BillingOutcome.OfferAvailable(offer, listOf(offer, yearly)),
            ),
        )
        val updatedCatalog = assertIs<PremiumPracticeState.Catalog>(withOffers)

        assertEquals(listOf(offer, yearly), updatedCatalog.billing.offers)
    }

    @Test
    fun activeEntitlementOpensExactlyTheTwoPremiumCases() {
        val state = reducer.reduce(
            PremiumPracticeState.Locked(BillingPresentation.loading()),
            PremiumPracticeEvent.BillingResult(BillingOutcome.Access(PremiumAccess.UNLOCKED)),
        )

        assertTrue(state is PremiumPracticeState.Catalog)
        assertEquals(ConclusionCases.premium, state.cases)
        assertEquals(2, state.cases.size)
        assertEquals(BillingUiState.UNLOCKED, state.billing.state)
    }

    @Test
    fun selectedCaseStartsAConclusionDraftWithItsOwnCaseId() {
        val catalog = PremiumPracticeState.Catalog(
            billing = BillingPresentation.fromOutcome(BillingOutcome.Access(PremiumAccess.UNLOCKED)),
            cases = ConclusionCases.premium,
            selectedCaseId = ConclusionCases.premium.last().id,
        )

        val state = reducer.reduce(catalog, PremiumPracticeEvent.BeginSelectedCase)

        assertTrue(state is PremiumPracticeState.Practice)
        assertTrue(state.conclusion is ConclusionState.Drafting)
        assertEquals(state.case.id, state.conclusion.draft.caseId)
    }

    @Test
    fun practiceEventsUseTheSelectedCaseReducer() {
        val catalog = PremiumPracticeState.Catalog(
            billing = BillingPresentation.fromOutcome(BillingOutcome.Access(PremiumAccess.UNLOCKED)),
            cases = ConclusionCases.premium,
            selectedCaseId = ConclusionCases.premium.first().id,
        )
        val practice = reducer.reduce(catalog, PremiumPracticeEvent.BeginSelectedCase)
        val drafting = practice as PremiumPracticeState.Practice
        val updated = reducer.reduce(
            drafting,
            PremiumPracticeEvent.PracticeEvent(ConclusionEvent.Reset),
        )

        assertTrue(updated is PremiumPracticeState.Practice)
        assertEquals(ConclusionState.Intro, updated.conclusion)
    }

    @Test
    fun lateBillingResultAfterLeavingPremiumDoesNotReopenTheSurface() {
        var state: PremiumPracticeState = reducer.reduce(
            PremiumPracticeState.Hidden,
            PremiumPracticeEvent.Open,
        )
        state = reducer.reduce(state, PremiumPracticeEvent.Back)
        state = reducer.reduce(
            state,
            PremiumPracticeEvent.BillingResult(
                BillingOutcome.Access(PremiumAccess.UNLOCKED),
            ),
        )

        assertEquals(PremiumPracticeState.Hidden, state)
    }
}
