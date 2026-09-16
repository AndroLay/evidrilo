package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BillingContractTest {
    @Test
    fun activeEntitlementUnlocksGuidedRehearsal() {
        assertEquals(
            PremiumAccess.UNLOCKED,
            premiumAccessFromEntitlement(isActive = true),
        )
    }

    @Test
    fun missingEntitlementStaysLocked() {
        assertEquals(
            PremiumAccess.LOCKED,
            premiumAccessFromEntitlement(isActive = null),
        )
    }

    @Test
    fun inactiveEntitlementStaysLocked() {
        assertEquals(
            PremiumAccess.LOCKED,
            premiumAccessFromEntitlement(isActive = false),
        )
    }

    @Test
    fun pendingAndUnknownTransactionsRemainExplicit() {
        val pending = BillingOutcome.Pending(
            operation = BillingOperation.PURCHASE,
            message = "Purchase is still being reconciled.",
        )
        val unknown = BillingOutcome.Unknown(
            operation = BillingOperation.PURCHASE,
            message = "Purchase state needs reconciliation.",
        )

        assertIs<BillingOutcome.Pending>(pending)
        assertIs<BillingOutcome.Unknown>(unknown)
        assertEquals(BillingOperation.PURCHASE, pending.operation)
        assertEquals(BillingOperation.PURCHASE, unknown.operation)
    }
}
