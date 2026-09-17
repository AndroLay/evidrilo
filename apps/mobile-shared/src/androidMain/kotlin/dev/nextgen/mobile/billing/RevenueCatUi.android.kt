package dev.nextgen.mobile.billing

import androidx.compose.runtime.Composable
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.ui.revenuecatui.CustomerCenter
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions
import dev.nextgen.mobile.compose.BuildConfig

internal actual fun createRevenueCatUiAvailability(): RevenueCatUiAvailability =
    revenueCatUiAvailability(
        sdkKey = BuildConfig.REVENUECAT_PUBLIC_SDK_KEY,
        configuration = BillingConfiguration.from(
            entitlementId = BuildConfig.REVENUECAT_ENTITLEMENT_ID,
            productIds = BuildConfig.REVENUECAT_PRODUCT_IDS,
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
