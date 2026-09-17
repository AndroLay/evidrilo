package dev.nextgen.mobile.analytics

import android.content.Context
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

private const val PREFERENCES_NAME = "evidrilo_analytics_consent_v1"
private const val CONSENT_KEY = "granted"

object AndroidAnalyticsConsentStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun createStore(): AnalyticsConsentStore =
        applicationContext?.let(::AndroidAnalyticsConsentStore) ?: NoopAnalyticsConsentStore()
}

actual fun createAnalyticsConsentStore(): AnalyticsConsentStore =
    AndroidAnalyticsConsentStorage.createStore()

private class AndroidAnalyticsConsentStore(
    context: Context,
) : AnalyticsConsentStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<AnalyticsConsent> = runCatching {
        LocalStorageReadResult.Success(
            if (preferences.getBoolean(CONSENT_KEY, false)) {
                AnalyticsConsent.GRANTED
            } else {
                AnalyticsConsent.NOT_GRANTED
            },
        )
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(consent: AnalyticsConsent): LocalStorageWriteResult = runCatching {
        if (preferences.edit().putBoolean(CONSENT_KEY, consent == AnalyticsConsent.GRANTED).commit()) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
