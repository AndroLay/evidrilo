package dev.nextgen.mobile.billing

enum class BillingUiState {
    LOADING,
    LOCKED,
    OFFER_AVAILABLE,
    UNLOCKED,
    UNAVAILABLE,
    CANCELLED,
    FAILED,
    PENDING,
    UNKNOWN,
}

data class BillingPresentation(
    val state: BillingUiState,
    val message: String,
    val offer: BillingOffer? = null,
    val offers: List<BillingOffer> = offer?.let { listOf(it) } ?: emptyList(),
    val selectedProductId: String? = offer?.productId,
    val isBusy: Boolean = false,
) {
    val selectedOffer: BillingOffer?
        get() = offers.firstOrNull {
            it.productId == selectedProductId && isApprovedBillingProductId(it.productId)
        }
            ?: offer?.takeIf { isApprovedBillingProductId(it.productId) }
            ?: offers.firstOrNull { isApprovedBillingProductId(it.productId) }

    val canPurchase: Boolean
        get() = !isBusy && selectedOffer != null && (
            state == BillingUiState.OFFER_AVAILABLE ||
                state == BillingUiState.CANCELLED ||
                state == BillingUiState.FAILED
            )

    val canRestore: Boolean
        get() = state != BillingUiState.UNLOCKED && !isBusy

    fun selectOffer(productId: String): BillingPresentation =
        offers.firstOrNull {
            it.productId == productId && isApprovedBillingProductId(it.productId)
        }
            ?.let { copy(offer = it, selectedProductId = it.productId) }
            ?: this

    companion object {
        fun loading(message: String = "Checking premium access…"): BillingPresentation =
            BillingPresentation(
                state = BillingUiState.LOADING,
                message = message,
                isBusy = true,
            )

        fun fromOutcome(
            outcome: BillingOutcome,
            offer: BillingOffer? = (outcome as? BillingOutcome.OfferAvailable)?.offer,
            offers: List<BillingOffer> = offer?.let { listOf(it) } ?: emptyList(),
            selectedProductId: String? = offer?.productId,
        ): BillingPresentation = when (outcome) {
            is BillingOutcome.OfferAvailable -> {
                val availableOffers = approvedBillingOffers(
                    outcome.offers.ifEmpty { listOf(outcome.offer) },
                )
                if (availableOffers.isEmpty()) {
                    BillingPresentation(
                        state = BillingUiState.UNAVAILABLE,
                        message = "Premium offers are unavailable right now.",
                    )
                } else {
                    val selectedOffer = availableOffers.firstOrNull { it.productId == selectedProductId }
                        ?: availableOffers.firstOrNull { it.productId == outcome.offer.productId }
                        ?: availableOffers.first()
                    BillingPresentation(
                        state = BillingUiState.OFFER_AVAILABLE,
                        message = "Premium evidence cases are available.",
                        offer = selectedOffer,
                        offers = availableOffers,
                        selectedProductId = selectedOffer.productId,
                    )
                }
            }

            is BillingOutcome.Access -> BillingPresentation(
                state = if (outcome.value == PremiumAccess.UNLOCKED) {
                    BillingUiState.UNLOCKED
                } else {
                    BillingUiState.LOCKED
                },
                message = if (outcome.value == PremiumAccess.UNLOCKED) {
                    "Premium evidence cases unlocked."
                } else {
                    "Premium access is not active on this customer."
                },
                offer = approvedBillingOffers(offers).firstOrNull { it.productId == selectedProductId }
                    ?: approvedBillingOffers(listOfNotNull(offer)).firstOrNull(),
                offers = approvedBillingOffers(offers),
                selectedProductId = selectedProductId?.takeIf(::isApprovedBillingProductId),
            )

            is BillingOutcome.Failure -> BillingPresentation(
                state = if (outcome.operation == BillingOperation.LOAD_OFFER) {
                    BillingUiState.UNAVAILABLE
                } else {
                    BillingUiState.FAILED
                },
                message = outcome.message.ifBlank { "Premium access is unavailable right now." },
                offer = approvedBillingOffers(listOfNotNull(offer)).firstOrNull(),
                offers = approvedBillingOffers(offers),
                selectedProductId = selectedProductId?.takeIf(::isApprovedBillingProductId),
            )

            BillingOutcome.Cancelled -> BillingPresentation(
                state = BillingUiState.CANCELLED,
                message = "Purchase cancelled; the free workflow remains available.",
                offer = approvedBillingOffers(listOfNotNull(offer)).firstOrNull(),
                offers = approvedBillingOffers(offers),
                selectedProductId = selectedProductId?.takeIf(::isApprovedBillingProductId),
            )

            is BillingOutcome.Pending -> BillingPresentation(
                state = BillingUiState.PENDING,
                message = outcome.message.ifBlank { "Purchase is still being reconciled." },
                offer = approvedBillingOffers(listOfNotNull(offer)).firstOrNull(),
                offers = approvedBillingOffers(offers),
                selectedProductId = selectedProductId?.takeIf(::isApprovedBillingProductId),
            )

            is BillingOutcome.Unknown -> {
                val approvedOffers = approvedBillingOffers(offers.ifEmpty { listOfNotNull(offer) })
                val selectedOffer = approvedOffers.firstOrNull { it.productId == selectedProductId }
                    ?: approvedOffers.firstOrNull()
                BillingPresentation(
                    state = BillingUiState.UNKNOWN,
                    message = outcome.message.ifBlank { "Purchase state needs reconciliation." },
                    offer = selectedOffer,
                    offers = approvedOffers,
                    selectedProductId = selectedOffer?.productId,
                )
            }
        }
    }
}
