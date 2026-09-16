package dev.nextgen.mobile.billing

/**
 * Provisional default used only by the legacy reducer until dashboard
 * configuration is supplied. It is not evidence that this identifier exists
 * in the owner's RevenueCat project.
 */
const val DEFAULT_EVIDRILO_ENTITLEMENT_ID = "evidrilo_pro"

enum class PremiumAccess {
    LOCKED,
    UNLOCKED,
}

enum class BillingOperation {
    LOAD_OFFER,
    REFRESH_ACCESS,
    IDENTIFY_CUSTOMER,
    RESET_CUSTOMER,
    PURCHASE,
    RESTORE,
}

data class BillingOffer(
    val productId: String,
    val title: String,
    val price: String,
)

sealed interface BillingOutcome {
    data class OfferAvailable(
        val offer: BillingOffer,
        val offers: List<BillingOffer> = listOf(offer),
    ) : BillingOutcome

    data class Access(val value: PremiumAccess) : BillingOutcome

    data class Failure(
        val operation: BillingOperation,
        val message: String,
    ) : BillingOutcome

    data class Pending(
        val operation: BillingOperation,
        val message: String,
    ) : BillingOutcome

    data class Unknown(
        val operation: BillingOperation,
        val message: String,
    ) : BillingOutcome

    data object Cancelled : BillingOutcome
}

fun premiumAccessFromEntitlement(isActive: Boolean?): PremiumAccess =
    if (isActive == true) PremiumAccess.UNLOCKED else PremiumAccess.LOCKED

/**
 * A provider entitlement is usable only when its active product belongs to the
 * configured Evidrilo subscription catalog. This keeps a retired lifetime
 * transaction from unlocking the client through an otherwise active
 * entitlement.
 */
internal fun premiumAccessFromEntitlement(
    isActive: Boolean?,
    productId: String?,
    configuration: BillingConfiguration,
): PremiumAccess =
    if (isActive == true && productId != null && isPracticePackProduct(productId, configuration)) {
        PremiumAccess.UNLOCKED
    } else {
        PremiumAccess.LOCKED
    }

interface BillingGateway {
    fun loadPracticePackOffer(onResult: (BillingOutcome) -> Unit)

    fun refreshAccess(onResult: (BillingOutcome) -> Unit)

    /**
     * Associates RevenueCat purchases with the provider-verified Evidrilo account.
     * The account id is never accepted as authentication; it is only called after
     * the account session has been verified by the auth adapter.
     */
    fun identifyCustomer(appUserId: String, onResult: (BillingOutcome) -> Unit)

    /** Returns RevenueCat to its anonymous customer after the app account signs out. */
    fun resetCustomer(onResult: (BillingOutcome) -> Unit)

    fun purchasePracticePack(
        productId: String? = null,
        onResult: (BillingOutcome) -> Unit,
    )

    fun restorePurchases(onResult: (BillingOutcome) -> Unit)
}

class UnavailableBillingGateway(
    private val reason: String = "Billing is not configured on this build.",
) : BillingGateway {
    override fun loadPracticePackOffer(onResult: (BillingOutcome) -> Unit) {
        onResult(BillingOutcome.Failure(BillingOperation.LOAD_OFFER, reason))
    }

    override fun refreshAccess(onResult: (BillingOutcome) -> Unit) {
        onResult(BillingOutcome.Failure(BillingOperation.REFRESH_ACCESS, reason))
    }

    override fun identifyCustomer(
        appUserId: String,
        onResult: (BillingOutcome) -> Unit,
    ) {
        onResult(BillingOutcome.Failure(BillingOperation.IDENTIFY_CUSTOMER, reason))
    }

    override fun resetCustomer(onResult: (BillingOutcome) -> Unit) {
        onResult(BillingOutcome.Failure(BillingOperation.RESET_CUSTOMER, reason))
    }

    override fun purchasePracticePack(
        productId: String?,
        onResult: (BillingOutcome) -> Unit,
    ) {
        onResult(BillingOutcome.Failure(BillingOperation.PURCHASE, reason))
    }

    override fun restorePurchases(onResult: (BillingOutcome) -> Unit) {
        onResult(BillingOutcome.Failure(BillingOperation.RESTORE, reason))
    }
}
