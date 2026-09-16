package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillingPresentationTest {
    private val offer = BillingOffer(
        productId = "monthly",
        title = "Evidrilo Practice Pack",
        price = "\$1.00",
    )
    private val yearly = BillingOffer(
        productId = "yearly",
        title = "Yearly",
        price = "\$10.00",
    )
    private val lifetime = BillingOffer(
        productId = "lifetime",
        title = "Lifetime",
        price = "\$99.99",
    )

    @Test
    fun availableOfferEnablesPurchaseAndKeepsRestoreAvailable() {
        val presentation = BillingPresentation.fromOutcome(BillingOutcome.OfferAvailable(offer))

        assertEquals(BillingUiState.OFFER_AVAILABLE, presentation.state)
        assertEquals(offer, presentation.offer)
        assertTrue(presentation.canPurchase)
        assertTrue(presentation.canRestore)
    }

    @Test
    fun availableOfferCatalog_exposes_only_approved_subscription_packages() {
        val presentation = BillingPresentation.fromOutcome(
            BillingOutcome.OfferAvailable(
                offer = yearly,
                offers = listOf(offer, yearly, lifetime),
            ),
        )

        assertEquals(listOf(offer, yearly), presentation.offers)
        assertEquals(yearly, presentation.selectedOffer)
        assertTrue(presentation.canPurchase)
    }

    @Test
    fun lifetime_only_provider_response_fails_closed_without_a_purchase_target() {
        val presentation = BillingPresentation.fromOutcome(
            BillingOutcome.OfferAvailable(
                offer = lifetime,
                offers = listOf(lifetime),
            ),
        )

        assertEquals(BillingUiState.UNAVAILABLE, presentation.state)
        assertTrue(presentation.offers.isEmpty())
        assertEquals(null, presentation.selectedOffer)
        assertFalse(presentation.canPurchase)
    }

    @Test
    fun manually_constructed_legacy_offer_cannot_become_a_purchase_target() {
        val presentation = BillingPresentation(
            state = BillingUiState.OFFER_AVAILABLE,
            message = "synthetic legacy state",
            offer = lifetime,
            offers = emptyList(),
            selectedProductId = lifetime.productId,
        )

        assertEquals(null, presentation.selectedOffer)
        assertFalse(presentation.canPurchase)
    }

    @Test
    fun unknown_transaction_state_does_not_reintroduce_a_legacy_lifetime_offer() {
        val presentation = BillingPresentation.fromOutcome(
            outcome = BillingOutcome.Unknown(BillingOperation.PURCHASE, "needs reconciliation"),
            offer = lifetime,
            offers = listOf(lifetime, offer, yearly),
            selectedProductId = "lifetime",
        )

        assertEquals(BillingUiState.UNKNOWN, presentation.state)
        assertEquals(listOf(offer, yearly), presentation.offers)
        assertEquals(offer, presentation.selectedOffer)
        assertEquals(offer.productId, presentation.selectedProductId)
        assertFalse(presentation.canPurchase)
    }

    @Test
    fun selecting_an_approved_package_changes_purchase_target_without_accepting_legacy_lifetime() {
        val presentation = BillingPresentation.fromOutcome(
            BillingOutcome.OfferAvailable(
                offer = offer,
                offers = listOf(offer, yearly, lifetime),
            ),
        )

        val selected = presentation.selectOffer("yearly")

        assertEquals(yearly, selected.selectedOffer)
        assertEquals(presentation, presentation.selectOffer("lifetime"))
        assertEquals(presentation, presentation.selectOffer("not-configured"))
    }

    @Test
    fun activeEntitlementUnlocksWithoutDependingOnOfferPresence() {
        val presentation = BillingPresentation.fromOutcome(
            BillingOutcome.Access(PremiumAccess.UNLOCKED),
            offer = null,
        )

        assertEquals(BillingUiState.UNLOCKED, presentation.state)
        assertFalse(presentation.canPurchase)
        assertFalse(presentation.canRestore)
    }

    @Test
    fun busyPresentationBlocksDuplicatePurchaseAndRestoreActions() {
        val presentation = BillingPresentation.fromOutcome(
            BillingOutcome.OfferAvailable(offer),
        ).copy(isBusy = true)

        assertFalse(presentation.canPurchase)
        assertFalse(presentation.canRestore)
    }

    @Test
    fun cancellationFailurePendingAndUnknownRemainRecoverableLockedStates() {
        val outcomes = listOf(
            BillingOutcome.Cancelled,
            BillingOutcome.Failure(BillingOperation.PURCHASE, "try again"),
            BillingOutcome.Pending(BillingOperation.PURCHASE, "pending"),
            BillingOutcome.Unknown(BillingOperation.PURCHASE, "unknown"),
        )

        val states = outcomes.map { BillingPresentation.fromOutcome(it, offer) }

        assertEquals(
            listOf(
                BillingUiState.CANCELLED,
                BillingUiState.FAILED,
                BillingUiState.PENDING,
                BillingUiState.UNKNOWN,
            ),
            states.map { it.state },
        )
        assertTrue(states.all { it.canRestore })
        assertTrue(states[0].canPurchase)
        assertTrue(states[1].canPurchase)
        assertFalse(states[2].canPurchase)
        assertFalse(states[3].canPurchase)
    }
}
