package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.billing.BillingOperation
import dev.nextgen.mobile.billing.BillingOutcome
import dev.nextgen.mobile.billing.PremiumAccess

internal fun billingAnalyticsAction(
    operation: BillingOperation,
    outcome: BillingOutcome,
): String? {
    val prefix = when (operation) {
        BillingOperation.PURCHASE -> "purchase"
        BillingOperation.RESTORE -> "restore"
        else -> return null
    }
    return when (outcome) {
        is BillingOutcome.Access ->
            if (outcome.value == PremiumAccess.UNLOCKED) "${prefix}_completed" else "${prefix}_failed"

        is BillingOutcome.Failure -> "${prefix}_failed"
        is BillingOutcome.Pending -> "${prefix}_pending"
        is BillingOutcome.Unknown -> "${prefix}_unknown"
        BillingOutcome.Cancelled -> "${prefix}_cancelled"
        is BillingOutcome.OfferAvailable -> null
    }
}

internal fun billingAnalyticsErrorCode(
    operation: BillingOperation,
    outcome: BillingOutcome,
): String? = if (outcome is BillingOutcome.Failure) {
    "BILLING_${operation.name}"
} else {
    null
}
