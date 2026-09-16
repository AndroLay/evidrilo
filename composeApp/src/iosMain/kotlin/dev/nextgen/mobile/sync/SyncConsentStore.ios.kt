package dev.nextgen.mobile.sync

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import platform.Foundation.NSUserDefaults

private const val CONSENT_KEY = "evidrilo.sync.consent.v1"

internal actual fun createSyncConsentStore(): SyncConsentStore = IosSyncConsentStore()

private class IosSyncConsentStore : SyncConsentStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<SyncConsent> = runCatching {
        LocalStorageReadResult.Success(
            if (defaults.boolForKey(CONSENT_KEY)) SyncConsent.GRANTED else SyncConsent.NOT_GRANTED,
        )
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun save(consent: SyncConsent): LocalStorageWriteResult = runCatching {
        defaults.setBool(consent == SyncConsent.GRANTED, forKey = CONSENT_KEY)
        LocalStorageWriteResult.SAVED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
