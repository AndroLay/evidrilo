package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class BillingIdentityTest {
    @Test
    fun guest_only_mode_does_not_initialize_the_store_provider() {
        var providerFactoryCalls = 0
        val providerGateway = UnavailableBillingGateway("Provider gateway")

        val selected = billingGatewayForAccessMode(guestOnlyMode = true) {
            providerFactoryCalls += 1
            providerGateway
        }

        assertEquals(0, providerFactoryCalls)
        assertIs<UnavailableBillingGateway>(selected)
    }

    @Test
    fun normal_mode_keeps_the_configured_store_provider() {
        var providerFactoryCalls = 0
        val providerGateway = UnavailableBillingGateway("Provider gateway")

        val selected = billingGatewayForAccessMode(guestOnlyMode = false) {
            providerFactoryCalls += 1
            providerGateway
        }

        assertEquals(1, providerFactoryCalls)
        assertSame(providerGateway, selected)
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
