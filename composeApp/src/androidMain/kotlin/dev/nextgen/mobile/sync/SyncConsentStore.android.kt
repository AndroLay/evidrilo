package dev.nextgen.mobile.sync

import android.content.Context
import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

private const val PREFERENCES_NAME = "evidrilo_sync_consent_v1"
private const val CONSENT_KEY = "granted"

object AndroidSyncConsentStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createStore(): SyncConsentStore =
        applicationContext?.let(::AndroidSyncConsentStore) ?: NoopSyncConsentStore()
}

internal actual fun createSyncConsentStore(): SyncConsentStore = AndroidSyncConsentStorage.createStore()

private class AndroidSyncConsentStore(
    context: Context,
) : SyncConsentStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<SyncConsent> = runCatching {
        LocalStorageReadResult.Success(
            if (preferences.getBoolean(CONSENT_KEY, false)) SyncConsent.GRANTED else SyncConsent.NOT_GRANTED,
        )
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(consent: SyncConsent): LocalStorageWriteResult = runCatching {
        if (preferences.edit().putBoolean(CONSENT_KEY, consent == SyncConsent.GRANTED).commit()) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
