package dev.nextgen.mobile.billing

import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure
import platform.Foundation.NSBundle

actual fun createPlatformBillingGateway(): BillingGateway =
    iosRevenueCatConfiguration().let { configuration ->
        iosRevenueCatApiKey()
            ?.takeIf { it.isNotBlank() && configuration.isUsable }
            ?.let { apiKey -> RevenueCatIosBilling(apiKey, configuration) }
            ?: UnavailableBillingGateway(
                "Billing is not configured for this iOS build. Add local RevenueCat SDK, entitlement, and product settings.",
            )
    }

private fun iosRevenueCatApiKey(): String? =
    NSBundle.mainBundle.objectForInfoDictionaryKey("REVENUECAT_PUBLIC_SDK_KEY") as? String

private fun iosRevenueCatConfiguration(): BillingConfiguration =
    BillingConfiguration.from(
        entitlementId = NSBundle.mainBundle.objectForInfoDictionaryKey("REVENUECAT_ENTITLEMENT_ID") as? String,
        productIds = NSBundle.mainBundle.objectForInfoDictionaryKey("REVENUECAT_PRODUCT_IDS") as? String,
    )

private class RevenueCatIosBilling(
    apiKey: String,
    private val configuration: BillingConfiguration,
) : BillingGateway {
    init {
        Purchases.logLevel = LogLevel.ERROR
        if (!Purchases.isConfigured) {
            Purchases.configure(apiKey)
        }
    }

    override fun loadPracticePackOffer(onResult: (BillingOutcome) -> Unit) {
        Purchases.sharedInstance.getOfferings(
            onError = { error ->
                onResult(error.toOutcome(BillingOperation.LOAD_OFFER))
            },
            onSuccess = { offerings ->
                val availablePackages = offerings.current?.availablePackages.orEmpty()
                val availableOffers = eligiblePracticePackOffers(
                    availablePackages.map { packageToOffer(it) },
                    configuration,
                )
                val selectedOffer = availableOffers.firstOrNull()
                if (selectedOffer == null) {
                    onResult(
                        BillingOutcome.Failure(
                            BillingOperation.LOAD_OFFER,
                            "No Evidrilo premium practice pack is configured.",
                        ),
                    )
                } else {
                    onResult(
                        BillingOutcome.OfferAvailable(
                            offer = selectedOffer,
                            offers = availableOffers,
                        ),
                    )
                }
            },
        )
    }

    override fun refreshAccess(onResult: (BillingOutcome) -> Unit) {
        Purchases.sharedInstance.getCustomerInfo(
            onError = { error ->
                onResult(error.toOutcome(BillingOperation.REFRESH_ACCESS))
            },
            onSuccess = { info -> onResult(info.toAccessOutcome(configuration)) },
        )
    }

    override fun identifyCustomer(
        appUserId: String,
        onResult: (BillingOutcome) -> Unit,
    ) {
        val normalizedAppUserId = appUserId.trim()
        if (normalizedAppUserId.isEmpty()) {
            onResult(
                BillingOutcome.Failure(
                    BillingOperation.IDENTIFY_CUSTOMER,
                    "The verified account billing identity is unavailable.",
                ),
            )
            return
        }

        Purchases.sharedInstance.logIn(
            normalizedAppUserId,
            onError = { error ->
                onResult(error.toOutcome(BillingOperation.IDENTIFY_CUSTOMER))
            },
            onSuccess = { info, _ -> onResult(info.toAccessOutcome(configuration)) },
        )
    }

    override fun resetCustomer(onResult: (BillingOutcome) -> Unit) {
        if (Purchases.sharedInstance.isAnonymous) {
            Purchases.sharedInstance.getCustomerInfo(
                onError = { error ->
                    onResult(error.toOutcome(BillingOperation.RESET_CUSTOMER))
                },
                onSuccess = { info -> onResult(info.toAccessOutcome(configuration)) },
            )
            return
        }

        Purchases.sharedInstance.logOut(
            onError = { error ->
                onResult(error.toOutcome(BillingOperation.RESET_CUSTOMER))
            },
            onSuccess = { info -> onResult(info.toAccessOutcome(configuration)) },
        )
    }

    override fun purchasePracticePack(
        productId: String?,
        onResult: (BillingOutcome) -> Unit,
    ) {
        Purchases.sharedInstance.getOfferings(
            onError = { error ->
                onResult(error.toOutcome(BillingOperation.PURCHASE))
            },
            onSuccess = { offerings ->
                val availablePackages = offerings.current?.availablePackages.orEmpty()
                val availableOffers = availablePackages.map { packageToOffer(it) }
                val selectedOffer = selectPracticePackOffer(
                    availableOffers,
                    configuration,
                    productId,
                )
                val packageToPurchase = selectedOffer?.let { selected ->
                    availablePackages.firstOrNull { it.storeProduct.id == selected.productId }
                }
                if (packageToPurchase == null) {
                    onResult(
                        BillingOutcome.Failure(
                            operation = BillingOperation.PURCHASE,
                            message = "No Evidrilo premium practice pack is configured.",
                        ),
                    )
                } else {
                    Purchases.sharedInstance.purchase(
                        packageToPurchase,
                        onError = { error, userCancelled ->
                            if (userCancelled == true) {
                                onResult(BillingOutcome.Cancelled)
                            } else {
                                onResult(error.toOutcome(BillingOperation.PURCHASE))
                            }
                        },
                        onSuccess = { _, info -> onResult(info.toAccessOutcome(configuration)) },
                    )
                }
            },
        )
    }

    override fun restorePurchases(onResult: (BillingOutcome) -> Unit) {
        Purchases.sharedInstance.restorePurchases(
            onError = { error ->
                onResult(error.toOutcome(BillingOperation.RESTORE))
            },
            onSuccess = { info -> onResult(info.toAccessOutcome(configuration)) },
        )
    }
}

private fun packageToOffer(
    packageToMap: com.revenuecat.purchases.kmp.models.Package,
): BillingOffer = BillingOffer(
    productId = packageToMap.storeProduct.id,
    title = packageToMap.storeProduct.title,
    price = packageToMap.storeProduct.price.formatted,
)

private fun com.revenuecat.purchases.kmp.models.CustomerInfo.toAccessOutcome(
    configuration: BillingConfiguration,
): BillingOutcome.Access {
    val entitlement = entitlements[configuration.entitlementId]
    return BillingOutcome.Access(
        premiumAccessFromEntitlement(
            isActive = entitlement?.isActive,
            productId = entitlement?.productIdentifier,
            configuration = configuration,
        ),
    )
}

private fun com.revenuecat.purchases.kmp.models.PurchasesError.toOutcome(
    operation: BillingOperation,
): BillingOutcome {
    val rawMessage = this.message
    return when {
        rawMessage.contains("pending", ignoreCase = true) ->
            BillingOutcome.Pending(operation, "The purchase is still being reconciled.")

        rawMessage.contains("unknown", ignoreCase = true) ->
            BillingOutcome.Unknown(operation, "The purchase state needs reconciliation.")

        else -> BillingOutcome.Failure(operation, operation.safeFailureMessage())
    }
}

private fun BillingOperation.safeFailureMessage(): String = when (this) {
    BillingOperation.LOAD_OFFER -> "Premium offers are unavailable right now."
    BillingOperation.REFRESH_ACCESS -> "Premium access could not be refreshed."
    BillingOperation.IDENTIFY_CUSTOMER -> "Premium access could not be linked to this account."
    BillingOperation.RESET_CUSTOMER -> "Premium customer state could not be reset."
    BillingOperation.PURCHASE -> "The purchase could not be completed."
    BillingOperation.RESTORE -> "Purchases could not be restored right now."
}
