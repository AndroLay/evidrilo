package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PremiumPaywallModelTest {
    private val monthly = BillingOffer("monthly", "Monthly", "$1.00")
    private val yearly = BillingOffer("yearly", "Yearly", "$10.00")
    private val lifetime = BillingOffer("lifetime", "Lifetime", "$99.00")

    @Test
    fun offer_state_exposes_only_monthly_and_yearly_packages() {
        val model = premiumPaywallModel(
            BillingPresentation.fromOutcome(
                BillingOutcome.OfferAvailable(
                    offer = yearly,
                    offers = listOf(lifetime, monthly, yearly),
                ),
            ),
        )

        assertEquals(PremiumPaywallState.OFFERS_AVAILABLE, model.state)
        assertEquals(listOf(monthly, yearly), model.offers)
        assertTrue(model.canPurchase)
        assertTrue(model.canRestore)
        assertFalse(model.offers.any { it.productId == "lifetime" })
    }

    @Test
    fun loading_state_is_busy_and_does_not_offer_purchase_or_restore() {
        val model = premiumPaywallModel(BillingPresentation.loading())

        assertEquals(PremiumPaywallState.LOADING, model.state)
        assertTrue(model.isBusy)
        assertFalse(model.canPurchase)
        assertFalse(model.canRestore)
    }

    @Test
    fun unavailable_state_is_an_honest_empty_state_with_retry() {
        val model = premiumPaywallModel(
            BillingPresentation.fromOutcome(
                BillingOutcome.Failure(BillingOperation.LOAD_OFFER, "Plans are not configured."),
            ),
        )

        assertEquals(PremiumPaywallState.EMPTY, model.state)
        assertTrue(model.offers.isEmpty())
        assertTrue(model.showRetry)
        assertFalse(model.canPurchase)
    }

    @Test
    fun purchase_failure_is_recoverable_without_granting_access() {
        val model = premiumPaywallModel(
            BillingPresentation.fromOutcome(
                BillingOutcome.Failure(BillingOperation.PURCHASE, "Purchase failed."),
                offer = monthly,
                offers = listOf(monthly, yearly),
            ),
        )

        assertEquals(PremiumPaywallState.ERROR, model.state)
        assertEquals(listOf(monthly, yearly), model.offers)
        assertTrue(model.showRetry)
        assertTrue(model.canPurchase)
    }

    @Test
    fun entitlement_state_controls_access_without_local_purchase_flags() {
        val locked = premiumPaywallModel(
            BillingPresentation.fromOutcome(BillingOutcome.Access(PremiumAccess.LOCKED)),
        )
        val unlocked = premiumPaywallModel(
            BillingPresentation.fromOutcome(BillingOutcome.Access(PremiumAccess.UNLOCKED)),
        )

        assertEquals(PremiumPaywallState.LOCKED, locked.state)
        assertTrue(locked.canRestore)
        assertEquals(PremiumPaywallState.UNLOCKED, unlocked.state)
        assertFalse(unlocked.canPurchase)
        assertFalse(unlocked.canRestore)
    }
}
