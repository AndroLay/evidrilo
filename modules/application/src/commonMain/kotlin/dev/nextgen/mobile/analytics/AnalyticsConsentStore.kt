package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

interface AnalyticsConsentStore {
    fun load(): LocalStorageReadResult<AnalyticsConsent>

    fun save(consent: AnalyticsConsent): LocalStorageWriteResult
}

class NoopAnalyticsConsentStore : AnalyticsConsentStore {
    override fun load(): LocalStorageReadResult<AnalyticsConsent> = LocalStorageReadResult.Unavailable

    override fun save(consent: AnalyticsConsent): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE
}
