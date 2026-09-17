package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import platform.Foundation.NSUserDefaults

private const val CONSENT_KEY = "evidrilo.analytics.consent.v1"

actual fun createAnalyticsConsentStore(): AnalyticsConsentStore = IosAnalyticsConsentStore()

private class IosAnalyticsConsentStore : AnalyticsConsentStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<AnalyticsConsent> = runCatching {
        LocalStorageReadResult.Success(
            if (defaults.boolForKey(CONSENT_KEY)) AnalyticsConsent.GRANTED else AnalyticsConsent.NOT_GRANTED,
        )
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(consent: AnalyticsConsent): LocalStorageWriteResult = runCatching {
        defaults.setBool(consent == AnalyticsConsent.GRANTED, forKey = CONSENT_KEY)
        LocalStorageWriteResult.SAVED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
