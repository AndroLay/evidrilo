package dev.nextgen.mobile.billing

import androidx.compose.runtime.Composable

/**
 * Describes whether RevenueCat's remotely managed UI can be presented safely.
 * A valid public key alone is not enough: the approved catalog and platform
 * support must also be present.
 */
internal data class RevenueCatUiAvailability(
    val canPresent: Boolean,
)

internal fun revenueCatUiAvailability(
    sdkKey: String?,
    configuration: BillingConfiguration,
    platformSupported: Boolean,
): RevenueCatUiAvailability = RevenueCatUiAvailability(
    canPresent = !sdkKey.isNullOrBlank() && configuration.isUsable && platformSupported,
)

internal expect fun createRevenueCatUiAvailability(): RevenueCatUiAvailability

@Composable
internal expect fun RevenueCatManagedPaywall(onDismiss: () -> Unit)

@Composable
internal expect fun RevenueCatCustomerCenter(onDismiss: () -> Unit)
