package dev.nextgen.mobile.security

internal actual fun createSecureSessionStore(): SecureSessionStore = NoopSecureSessionStore()
