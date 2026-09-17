package dev.nextgen.mobile.billing

actual fun createPlatformBillingGateway(): BillingGateway =
    UnavailableBillingGateway("Billing is unavailable in the desktop preview.")
