package dev.nextgen.mobile.account

interface AccountAuthPlatform {
    /** Opens a system-managed browser or provider hand-off surface. */
    fun openExternalUrl(url: String): Boolean
}

expect fun createAccountAuthPlatform(): AccountAuthPlatform

expect fun createAccountHttpTransport(): AccountHttpTransport

expect fun createAccountClientConfiguration(): AccountClientConfiguration

private object AccountAuthRedirectBus {
    private val listeners = mutableListOf<(String) -> Unit>()
    private var pendingUrl: String? = null

    fun subscribe(listener: (String) -> Unit): () -> Unit {
        listeners += listener
        pendingUrl?.let { url ->
            pendingUrl = null
            listener(url)
        }
        var active = true
        return {
            if (active) {
                active = false
                listeners -= listener
            }
        }
    }

    fun publish(url: String) {
        if (url.isBlank() || !isBoundedAuthRedirectUrl(url)) return
        if (listeners.isEmpty()) {
            pendingUrl = url
        } else {
            listeners.toList().forEach { listener -> listener(url) }
        }
    }
}

fun subscribeAccountAuthRedirect(listener: (String) -> Unit): () -> Unit =
    AccountAuthRedirectBus.subscribe(listener)

/** Called by the native host after receiving evidrilo://auth/callback. */
fun submitAccountAuthRedirect(url: String) {
    AccountAuthRedirectBus.publish(url)
}
