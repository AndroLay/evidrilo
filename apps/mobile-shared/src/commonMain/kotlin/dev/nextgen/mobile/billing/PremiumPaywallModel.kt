package dev.nextgen.mobile.billing

internal enum class PremiumPaywallState {
    LOADING,
    LOCKED,
    OFFERS_AVAILABLE,
    EMPTY,
    ERROR,
    PENDING,
    CANCELLED,
    UNKNOWN,
    UNLOCKED,
}

internal data class PremiumPaywallModel(
    val state: PremiumPaywallState,
    val title: String,
    val message: String,
    val offers: List<BillingOffer>,
    val selectedProductId: String?,
    val canPurchase: Boolean,
    val canRestore: Boolean,
    val isBusy: Boolean,
    val showRetry: Boolean,
)

internal fun premiumPaywallModel(
    billing: BillingPresentation,
): PremiumPaywallModel {
    val offers = approvedBillingOffers(billing.offers)
    val selectedProductId = billing.selectedProductId
        ?.takeIf { productId -> offers.any { it.productId == productId } }
        ?: billing.selectedOffer?.productId
    val state = when (billing.state) {
        BillingUiState.LOADING -> PremiumPaywallState.LOADING
        BillingUiState.LOCKED -> PremiumPaywallState.LOCKED
        BillingUiState.OFFER_AVAILABLE -> PremiumPaywallState.OFFERS_AVAILABLE
        BillingUiState.UNLOCKED -> PremiumPaywallState.UNLOCKED
        BillingUiState.UNAVAILABLE -> PremiumPaywallState.EMPTY
        BillingUiState.CANCELLED -> PremiumPaywallState.CANCELLED
        BillingUiState.FAILED -> PremiumPaywallState.ERROR
        BillingUiState.PENDING -> PremiumPaywallState.PENDING
        BillingUiState.UNKNOWN -> PremiumPaywallState.UNKNOWN
    }
    return PremiumPaywallModel(
        state = state,
        title = when (state) {
            PremiumPaywallState.LOADING -> "Checking premium access"
            PremiumPaywallState.LOCKED -> "Unlock two evidence cases"
            PremiumPaywallState.OFFERS_AVAILABLE -> "Choose monthly or yearly access"
            PremiumPaywallState.EMPTY -> "Premium plans are unavailable"
            PremiumPaywallState.ERROR -> "Purchase needs attention"
            PremiumPaywallState.PENDING -> "Purchase is being reconciled"
            PremiumPaywallState.CANCELLED -> "Purchase cancelled"
            PremiumPaywallState.UNKNOWN -> "Purchase state needs reconciliation"
            PremiumPaywallState.UNLOCKED -> "Premium evidence cases are unlocked"
        },
        message = billing.message.ifBlank {
            "The free evidence workflow remains available offline without an account or subscription."
        },
        offers = offers,
        selectedProductId = selectedProductId,
        canPurchase = billing.canPurchase && state in setOf(
            PremiumPaywallState.OFFERS_AVAILABLE,
            PremiumPaywallState.CANCELLED,
            PremiumPaywallState.ERROR,
        ),
        canRestore = billing.canRestore && state != PremiumPaywallState.LOADING,
        isBusy = billing.isBusy || state == PremiumPaywallState.LOADING,
        showRetry = state in setOf(
            PremiumPaywallState.EMPTY,
            PremiumPaywallState.ERROR,
            PremiumPaywallState.UNKNOWN,
        ),
    )
}
