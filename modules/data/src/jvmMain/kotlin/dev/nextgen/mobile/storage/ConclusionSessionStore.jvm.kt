package dev.nextgen.mobile.storage

actual fun createConclusionSessionStore(): ConclusionSessionStore =
    NoopConclusionSessionStore()
