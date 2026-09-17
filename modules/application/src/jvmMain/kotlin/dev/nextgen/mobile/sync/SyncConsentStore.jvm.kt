package dev.nextgen.mobile.sync

actual fun createSyncConsentStore(): SyncConsentStore = NoopSyncConsentStore()
