package dev.nextgen.mobile.storage

internal actual fun createConclusionHistoryStore(): ConclusionHistoryStore =
    NoopConclusionHistoryStore()
