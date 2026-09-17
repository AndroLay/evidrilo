package dev.nextgen.mobile.sync

internal actual fun createSyncQueueStore(): SyncQueueStore = NoopSyncQueueStore()
