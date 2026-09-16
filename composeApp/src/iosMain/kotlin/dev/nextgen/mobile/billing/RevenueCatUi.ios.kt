package dev.nextgen.mobile.billing

import androidx.compose.runtime.Composable
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.ui.revenuecatui.CustomerCenter
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions
import platform.Foundation.NSBundle

internal actual fun createRevenueCatUiAvailability(): RevenueCatUiAvailability =
    revenueCatUiAvailability(
        sdkKey = NSBundle.mainBundle.objectForInfoDictionaryKey("REVENUECAT_PUBLIC_SDK_KEY") as? String,
        configuration = BillingConfiguration.from(
            entitlementId = NSBundle.mainBundle.objectForInfoDictionaryKey("REVENUECAT_ENTITLEMENT_ID") as? String,
            productIds = NSBundle.mainBundle.objectForInfoDictionaryKey("REVENUECAT_PRODUCT_IDS") as? String,
        ),
        platformSupported = Purchases.isConfigured,
    )

@Composable
internal actual fun RevenueCatManagedPaywall(onDismiss: () -> Unit) {
    Paywall(
        PaywallOptions(dismissRequest = onDismiss) {
            shouldDisplayDismissButton = true
        },
    )
}

@Composable
internal actual fun RevenueCatCustomerCenter(onDismiss: () -> Unit) {
    CustomerCenter(onDismiss = onDismiss)
}
