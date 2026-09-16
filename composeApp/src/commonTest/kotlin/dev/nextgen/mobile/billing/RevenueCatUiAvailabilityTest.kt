package dev.nextgen.mobile.billing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevenueCatUiAvailabilityTest {
    @Test
    fun managedUiRequiresPublicKeyAndApprovedBillingConfiguration() {
        assertFalse(
            revenueCatUiAvailability(
                sdkKey = "",
                configuration = usableConfiguration(),
                platformSupported = true,
            ).canPresent,
        )
        assertFalse(
            revenueCatUiAvailability(
                sdkKey = "public_key",
                configuration = BillingConfiguration.from("", "monthly,yearly"),
                platformSupported = true,
            ).canPresent,
        )
    }

    @Test
    fun managedUiRequiresSupportedPlatform() {
        assertFalse(
            revenueCatUiAvailability(
                sdkKey = "public_key",
                configuration = usableConfiguration(),
                platformSupported = false,
            ).canPresent,
        )
    }

    @Test
    fun managedUiIsAvailableOnlyWhenEveryBoundaryIsReady() {
        assertTrue(
            revenueCatUiAvailability(
                sdkKey = " public_key ",
                configuration = usableConfiguration(),
                platformSupported = true,
            ).canPresent,
        )
    }

    private fun usableConfiguration(): BillingConfiguration =
        BillingConfiguration.from("evidrilo_pro", "monthly,yearly")
}
