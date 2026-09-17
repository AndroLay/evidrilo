package dev.nextgen.mobile.billing

internal fun isPracticePackProduct(
    productId: String,
    configuration: BillingConfiguration,
): Boolean = isApprovedBillingProductId(productId) && productId in configuration.productIds

internal fun eligiblePracticePackOffers(
    offers: Iterable<BillingOffer>,
    configuration: BillingConfiguration,
): List<BillingOffer> = offers
    .filter { isPracticePackProduct(it.productId, configuration) }
    .distinctBy { it.productId }

internal fun selectPracticePackOffer(
    offers: Iterable<BillingOffer>,
    configuration: BillingConfiguration,
    requestedProductId: String?,
): BillingOffer? {
    val eligible = eligiblePracticePackOffers(offers, configuration)
    val requested = requestedProductId
    return when {
        requested != null && requested.isBlank() -> null
        requested != null -> eligible.firstOrNull { it.productId == requested }
        else -> eligible.firstOrNull()
    }
}
