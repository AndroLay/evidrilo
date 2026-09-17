package dev.nextgen.mobile.sync

actual fun createSyncQueueStore(): SyncQueueStore = NoopSyncQueueStore()
