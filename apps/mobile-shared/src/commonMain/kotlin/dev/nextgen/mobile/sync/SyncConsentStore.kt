package dev.nextgen.mobile.sync

import dev.nextgen.mobile.storage.LocalStorageReadResult
import dev.nextgen.mobile.storage.LocalStorageWriteResult

internal interface SyncConsentStore {
    fun load(): LocalStorageReadResult<SyncConsent>

    fun save(consent: SyncConsent): LocalStorageWriteResult
}

internal class NoopSyncConsentStore : SyncConsentStore {
    override fun load(): LocalStorageReadResult<SyncConsent> = LocalStorageReadResult.Unavailable

    override fun save(consent: SyncConsent): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE
}
