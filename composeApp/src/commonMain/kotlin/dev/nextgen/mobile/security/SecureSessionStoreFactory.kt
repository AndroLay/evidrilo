package dev.nextgen.mobile.security

internal object SecureSessionStoreFactory {
    fun create(): SecureSessionStore = createSecureSessionStore()
}
