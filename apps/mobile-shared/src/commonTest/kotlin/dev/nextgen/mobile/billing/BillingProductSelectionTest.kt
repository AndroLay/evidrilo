package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillingProductSelectionTest {
    @Test
    fun configuredProductIdsKeepOnlyApprovedSubscriptionPackages() {
        val configuration = BillingConfiguration.from(
            entitlementId = " evidrilo_pro ",
            productIds = "monthly, yearly, monthly, lifetime, unknown",
        )

        assertEquals("evidrilo_pro", configuration.entitlementId)
        assertEquals(setOf("monthly", "yearly"), configuration.productIds)
        assertTrue(configuration.isUsable)
    }

    @Test
    fun onlyConfiguredProductsAreEligibleForThePremiumBoundary() {
        val configuration = BillingConfiguration.from(
            entitlementId = "evidrilo_pro",
            productIds = "monthly,yearly",
        )

        assertTrue(isPracticePackProduct("monthly", configuration))
        assertFalse(isPracticePackProduct("lifetime", configuration))
        assertFalse(isPracticePackProduct(" unrelated_monthly_product ", configuration))
    }

    @Test
    fun provider_product_ids_must_match_the_allowlist_exactly() {
        val configuration = BillingConfiguration.from(
            entitlementId = "evidrilo_pro",
            productIds = "monthly,yearly",
        )

        assertFalse(isPracticePackProduct(" monthly ", configuration))
        assertEquals(
            PremiumAccess.LOCKED,
            premiumAccessFromEntitlement(true, "yearly ", configuration),
        )
        assertTrue(
            eligiblePracticePackOffers(
                listOf(BillingOffer(" monthly ", "Monthly", "$1.00")),
                configuration,
            ).isEmpty(),
        )
    }

    @Test
    fun active_entitlement_unlocks_only_for_an_approved_configured_product() {
        val configuration = BillingConfiguration.from(
            entitlementId = "evidrilo_pro",
            productIds = "monthly,yearly",
        )

        assertEquals(
            PremiumAccess.UNLOCKED,
            premiumAccessFromEntitlement(true, "monthly", configuration),
        )
        assertEquals(
            PremiumAccess.UNLOCKED,
            premiumAccessFromEntitlement(true, "yearly", configuration),
        )
        assertEquals(
            PremiumAccess.LOCKED,
            premiumAccessFromEntitlement(true, "lifetime", configuration),
        )
        assertEquals(
            PremiumAccess.LOCKED,
            premiumAccessFromEntitlement(true, null, configuration),
        )
    }

    @Test
    fun eligible_offers_exclude_legacy_lifetime_even_if_provider_returns_it() {
        val configuration = BillingConfiguration.from(
            entitlementId = "evidrilo_pro",
            productIds = "monthly,yearly,lifetime",
        )
        val offers = listOf(
            BillingOffer("monthly", "Monthly", "\$1.00"),
            BillingOffer("unrelated", "Unrelated", "\$1.00"),
            BillingOffer("yearly", "Yearly", "\$10.00"),
            BillingOffer("lifetime", "Lifetime", "\$99.99"),
        )

        assertEquals(
            listOf("monthly", "yearly"),
            eligiblePracticePackOffers(offers, configuration).map { it.productId },
        )
    }

    @Test
    fun requested_package_is_selected_or_missing_request_fails_closed() {
        val configuration = BillingConfiguration.from(
            entitlementId = "evidrilo_pro",
            productIds = "monthly,yearly,lifetime",
        )
        val offers = listOf(
            BillingOffer("monthly", "Monthly", "\$1.00"),
            BillingOffer("yearly", "Yearly", "\$10.00"),
        )

        assertEquals(
            "yearly",
            selectPracticePackOffer(offers, configuration, "yearly")?.productId,
        )
        assertEquals(
            "monthly",
            selectPracticePackOffer(offers, configuration, null)?.productId,
        )
        assertEquals(
            null,
            selectPracticePackOffer(offers, configuration, "lifetime")?.productId,
        )
    }

    @Test
    fun lifetime_only_configuration_is_not_usable() {
        assertFalse(BillingConfiguration.from("evidrilo_pro", "lifetime").isUsable)
    }

    @Test
    fun non_canonical_entitlement_configuration_is_not_usable() {
        assertFalse(BillingConfiguration.from("another_entitlement", "monthly,yearly").isUsable)
    }

    @Test
    fun missingEntitlementOrProductsMakesConfigurationUnavailable() {
        assertFalse(BillingConfiguration.from("", "monthly").isUsable)
        assertFalse(BillingConfiguration.from("evidrilo_pro", "").isUsable)
        assertFalse(BillingConfiguration.from(null, null).isUsable)
    }
}
