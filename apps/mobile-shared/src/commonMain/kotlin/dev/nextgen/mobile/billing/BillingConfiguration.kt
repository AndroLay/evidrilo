package dev.nextgen.mobile.billing

/**
 * Non-secret RevenueCat identifiers supplied by a local build configuration.
 *
 * The SDK key is intentionally kept outside this common value object because
 * it is owned by the platform adapters and must never be confused with a
 * server secret. Product identifiers are configuration too: the dashboard is
 * the source of truth once the owner creates the Test Store offering.
 */
data class BillingConfiguration(
    val entitlementId: String,
    val productIds: Set<String>,
) {
    val isUsable: Boolean
        get() = entitlementId == DEFAULT_EVIDRILO_ENTITLEMENT_ID
            && productIds.any(::isApprovedBillingProductId)

    companion object {
        fun from(
            entitlementId: String?,
            productIds: String?,
        ): BillingConfiguration = BillingConfiguration(
            entitlementId = entitlementId.orEmpty().trim(),
            productIds = productIds
                .orEmpty()
                .split(',')
                .asSequence()
                .map(String::trim)
                .filter { it.isNotBlank() }
                .filter(::isApprovedBillingProductId)
                .toSet(),
        )
    }
}

/**
 * The public offer policy for Evidrilo. Keep this allowlist in common code so
 * a provider response or a hand-built test fixture cannot reintroduce the
 * retired lifetime product into purchase or presentation flows.
 */
internal val APPROVED_BILLING_PRODUCT_IDS: Set<String> = setOf("monthly", "yearly")

internal fun isApprovedBillingProductId(productId: String): Boolean =
    productId in APPROVED_BILLING_PRODUCT_IDS

internal fun approvedBillingOffers(offers: Iterable<BillingOffer>): List<BillingOffer> =
    offers
        .filter { isApprovedBillingProductId(it.productId) }
        .distinctBy { it.productId }
