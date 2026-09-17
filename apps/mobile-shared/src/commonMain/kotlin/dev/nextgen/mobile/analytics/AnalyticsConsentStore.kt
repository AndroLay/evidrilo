package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

internal interface AnalyticsConsentStore {
    fun load(): LocalStorageReadResult<AnalyticsConsent>

    fun save(consent: AnalyticsConsent): LocalStorageWriteResult
}

internal class NoopAnalyticsConsentStore : AnalyticsConsentStore {
    override fun load(): LocalStorageReadResult<AnalyticsConsent> = LocalStorageReadResult.Unavailable

    override fun save(consent: AnalyticsConsent): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE
}
