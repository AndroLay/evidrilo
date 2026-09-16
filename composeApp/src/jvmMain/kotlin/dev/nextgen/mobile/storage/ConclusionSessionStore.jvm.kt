package dev.nextgen.mobile.storage

internal actual fun createConclusionSessionStore(): ConclusionSessionStore =
    NoopConclusionSessionStore()
