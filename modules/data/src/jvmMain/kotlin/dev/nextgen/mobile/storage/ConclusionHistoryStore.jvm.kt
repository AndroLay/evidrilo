package dev.nextgen.mobile.storage

actual fun createConclusionHistoryStore(): ConclusionHistoryStore =
    NoopConclusionHistoryStore()
