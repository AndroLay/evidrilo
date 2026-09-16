package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BillingRaceAndRecoveryTest {
    private val reducer = PremiumPracticeReducer()
    private val offer = BillingOffer("monthly", "Evidrilo Premium", "$1.00")

    @Test
    fun billing_request_from_previous_account_is_rejected_after_identity_invalidation() {
        val gate = BillingRequestGate()
        val previousAccountRequest = gate.begin("account-a")

        gate.invalidate()

        assertFalse(gate.isCurrent(previousAccountRequest, "account-a"))
        assertFalse(gate.isCurrent(previousAccountRequest, "account-b"))
        assertFalse(gate.isCurrent(previousAccountRequest, null))
    }

    @Test
    fun anonymous_billing_request_does_not_match_a_signed_in_account() {
        val gate = BillingRequestGate()
        val anonymousRequest = gate.begin(null)

        assertTrue(gate.isCurrent(anonymousRequest, null))
        assertFalse(gate.isCurrent(anonymousRequest, "account-a"))
    }

    @Test
    fun newer_billing_request_invalidates_an_older_request_for_the_same_account() {
        val gate = BillingRequestGate()
        val firstRequest = gate.begin("account-a")
        val secondRequest = gate.begin("account-a")

        assertFalse(gate.isCurrent(firstRequest, "account-a"))
        assertTrue(gate.isCurrent(secondRequest, "account-a"))
    }

    @Test
    fun lateAccessResultAfterBackCannotReopenPremium() {
        var state: PremiumPracticeState = reducer.reduce(
            PremiumPracticeState.Hidden,
            PremiumPracticeEvent.Open,
        )
        state = reducer.reduce(state, PremiumPracticeEvent.Back)

        val late = reducer.reduce(
            state,
            PremiumPracticeEvent.BillingResult(
                BillingOutcome.Access(PremiumAccess.UNLOCKED),
            ),
        )

        assertEquals(PremiumPracticeState.Hidden, late)
    }

    @Test
    fun lateOfferResultAfterCloseCannotRepopulateTheCatalog() {
        var state: PremiumPracticeState = reducer.reduce(
            PremiumPracticeState.Hidden,
            PremiumPracticeEvent.Open,
        )
        state = reducer.reduce(state, PremiumPracticeEvent.Close)

        val late = reducer.reduce(
            state,
            PremiumPracticeEvent.BillingResult(BillingOutcome.OfferAvailable(offer)),
        )

        assertEquals(PremiumPracticeState.Hidden, late)
    }

    @Test
    fun missingOfferingIsUnavailableAndCannotPurchase() {
        val state = reducer.reduce(
            PremiumPracticeState.Locked(BillingPresentation.loading()),
            PremiumPracticeEvent.BillingResult(
                BillingOutcome.Failure(
                    operation = BillingOperation.LOAD_OFFER,
                    message = "No configured offering.",
                ),
            ),
        )

        val locked = assertIs<PremiumPracticeState.Locked>(state)
        assertEquals(BillingUiState.UNAVAILABLE, locked.billing.state)
        assertFalse(locked.billing.canPurchase)
    }

    @Test
    fun pendingUnknownCancelledAndFailedPurchaseNeverUnlockPremium() {
        val outcomes = listOf(
            BillingOutcome.Pending(BillingOperation.PURCHASE, "pending"),
            BillingOutcome.Unknown(BillingOperation.PURCHASE, "unknown"),
            BillingOutcome.Cancelled,
            BillingOutcome.Failure(BillingOperation.PURCHASE, "failed"),
        )

        outcomes.forEach { outcome ->
            val state = reducer.reduce(
                PremiumPracticeState.Locked(
                    BillingPresentation.fromOutcome(BillingOutcome.OfferAvailable(offer)),
                ),
                PremiumPracticeEvent.BillingResult(outcome),
            )

            assertIs<PremiumPracticeState.Locked>(state)
        }
    }

    @Test
    fun revokedAccessReturnsAnAlreadyUnlockedCatalogToLocked() {
        val catalog = PremiumPracticeState.Catalog(
            billing = BillingPresentation.fromOutcome(
                BillingOutcome.Access(PremiumAccess.UNLOCKED),
            ),
        )

        val state = reducer.reduce(
            catalog,
            PremiumPracticeEvent.BillingResult(BillingOutcome.Access(PremiumAccess.LOCKED)),
        )

        val locked = assertIs<PremiumPracticeState.Locked>(state)
        assertEquals(BillingUiState.LOCKED, locked.billing.state)
    }

    @Test
    fun busyPresentationBlocksBothPurchaseAndRestore() {
        val busy = BillingPresentation.fromOutcome(
            BillingOutcome.OfferAvailable(offer),
        ).copy(isBusy = true)

        assertFalse(busy.canPurchase)
        assertFalse(busy.canRestore)
    }
}
