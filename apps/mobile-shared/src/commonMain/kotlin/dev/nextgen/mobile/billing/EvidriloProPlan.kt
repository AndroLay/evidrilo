package dev.nextgen.mobile.billing

/** Approved product references (D-100). These prices never authorize a checkout. */
internal enum class EvidriloProPlan(
    val productId: String,
    val label: String,
    val referencePrice: String,
    val period: String,
    val renewalPeriod: String,
) {
    MONTHLY("monthly", "Monthly", "USD 1.99", "per month", "month"),
    ANNUALLY("yearly", "Annually", "USD 19.99", "per year", "year");

    companion object {
        fun forProduct(productId: String?): EvidriloProPlan? = entries.firstOrNull { it.productId == productId }
    }
}

/** Preserve an explicit period and fail closed if the provider does not offer it. */
internal fun BillingOutcome.withRequestedProPlan(productId: String?): BillingOutcome {
    if (this !is BillingOutcome.OfferAvailable || productId == null) return this
    val available = approvedBillingOffers(offers.ifEmpty { listOf(offer) })
    val requested = available.firstOrNull { it.productId == productId }
        ?: return BillingOutcome.Failure(
            BillingOperation.LOAD_OFFER,
            "The ${EvidriloProPlan.forProduct(productId)?.label ?: "selected"} plan is unavailable right now. Try again or return to the comparison to choose another plan.",
        )
    return copy(offer = requested, offers = available)
}
