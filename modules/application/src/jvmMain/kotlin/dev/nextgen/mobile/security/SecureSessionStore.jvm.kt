package dev.nextgen.mobile.security

actual fun createSecureSessionStore(): SecureSessionStore = NoopSecureSessionStore()
