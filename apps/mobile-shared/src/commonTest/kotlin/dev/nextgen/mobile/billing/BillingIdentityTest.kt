package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BillingIdentityTest {
    @Test
    fun disabled_pro_feature_does_not_initialize_the_store_provider() {
        var providerFactoryCalls = 0
        val providerGateway = UnavailableBillingGateway("Provider gateway")

        val selected = revenueCatGatewayForFeatureEnabled(featureEnabled = false) {
            providerFactoryCalls += 1
            providerGateway
        }

        assertEquals(0, providerFactoryCalls)
        assertIs<UnavailableBillingGateway>(selected)
    }

    @Test
    fun enabled_pro_feature_keeps_the_configured_store_provider_even_for_local_guest_mode() {
        var providerFactoryCalls = 0
        val providerGateway = UnavailableBillingGateway("Provider gateway")

        val selected = revenueCatGatewayForFeatureEnabled(featureEnabled = true) {
            providerFactoryCalls += 1
            providerGateway
        }

        assertEquals(1, providerFactoryCalls)
        assertSame(providerGateway, selected)
    }

    @Test
    fun provider_identity_must_match_the_active_evidrilo_account_before_pro_access() {
        assertTrue(billingIdentityMatches("account-a", "account-a"))
        assertFalse(billingIdentityMatches("account-a", "account-b"))
        assertFalse(billingIdentityMatches("account-a", null))
        assertFalse(billingIdentityMatches(null, "account-a"))
    }

    @Test
    fun unavailable_billing_fails_closed_when_identifying_customer() {
        var outcome: BillingOutcome? = null

        UnavailableBillingGateway().identifyCustomer("123e4567-e89b-42d3-a456-426614174000") {
            outcome = it
        }

        val failure = assertIs<BillingOutcome.Failure>(outcome)
        assertEquals(BillingOperation.IDENTIFY_CUSTOMER, failure.operation)
    }

    @Test
    fun unavailable_billing_fails_closed_when_resetting_customer() {
        var outcome: BillingOutcome? = null

        UnavailableBillingGateway().resetCustomer {
            outcome = it
        }

        val failure = assertIs<BillingOutcome.Failure>(outcome)
        assertEquals(BillingOperation.RESET_CUSTOMER, failure.operation)
    }
}
