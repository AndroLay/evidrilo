package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BillingIdentityTest {
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
