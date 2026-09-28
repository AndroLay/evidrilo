package dev.nextgen.mobile.ios

import dev.nextgen.mobile.account.submitAccountAuthRedirect as submitAccountAuthRedirectToApplication

/** Exposes the deep-link callback through the ComposeApp framework for the Swift host. */
fun submitAccountAuthRedirect(url: String) {
    submitAccountAuthRedirectToApplication(url)
}
