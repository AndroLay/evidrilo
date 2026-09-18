package dev.nextgen.mobile.account

enum class AccountPresentationAction {
    NONE,
    SIGN_OUT,
}

data class AccountPresentation(
    val title: String,
    val body: String,
    val actionLabel: String?,
    val action: AccountPresentationAction,
    val isBusy: Boolean,
)

fun AccountSession.toPresentation(): AccountPresentation = when (this) {
    AccountSession.SignedOut -> AccountPresentation(
        title = "Account is optional",
        body = "The local workflow works without an account. Sign in when you want a managed account for future cross-device features.",
        actionLabel = null,
        action = AccountPresentationAction.NONE,
        isBusy = false,
    )

    AccountSession.SigningIn -> AccountPresentation(
        title = "Preparing account sign-in",
        body = "The account request is being completed securely. Your local workflow remains available while this is in progress.",
        actionLabel = null,
        action = AccountPresentationAction.NONE,
        isBusy = true,
    )

    AccountSession.AwaitingOAuthCallback -> AccountPresentation(
        title = "Finish Google sign-in",
        body = "Complete the sign-in in your browser, then return to Evidrilo. Your local workflow remains available.",
        actionLabel = null,
        action = AccountPresentationAction.NONE,
        isBusy = true,
    )

    is AccountSession.SignedIn -> AccountPresentation(
        title = "Account connected",
        body = "This device has a verified account session. Cloud sync is not connected in this build; the local workflow remains available.",
        actionLabel = "Sign out",
        action = AccountPresentationAction.SIGN_OUT,
        isBusy = false,
    )

    AccountSession.Expired -> AccountPresentation(
        title = "Session expired",
        body = "The stored account session was removed. The local workflow still works; sign in again when you are ready.",
        actionLabel = null,
        action = AccountPresentationAction.NONE,
        isBusy = false,
    )

    is AccountSession.PasswordRecovery -> {
        val recoveryError = errorReason
        AccountPresentation(
            title = recoveryError?.let(::unavailableTitle) ?: "Choose a new password",
            body = recoveryError?.let { "${unavailableBody(it)} You can try again." }
                ?: "Your recovery link was verified. Choose a new password to finish recovering this account.",
            actionLabel = null,
            action = AccountPresentationAction.NONE,
            isBusy = false,
        )
    }

    is AccountSession.Unavailable -> AccountPresentation(
        title = unavailableTitle(reason),
        body = unavailableBody(reason),
        actionLabel = null,
        action = AccountPresentationAction.NONE,
        isBusy = false,
    )
}

fun AccountSession.toSettingsSubtitle(): String = when (this) {
    AccountSession.SignedOut -> "Optional sign-in"
    AccountSession.SigningIn -> "Signing in securely"
    AccountSession.AwaitingOAuthCallback -> "Finish Google sign-in"
    is AccountSession.SignedIn -> "Verified session"
    AccountSession.Expired -> "Session expired"
    is AccountSession.PasswordRecovery -> "Finish password recovery"
    is AccountSession.Unavailable -> toPresentation().title
}

private fun unavailableTitle(reason: AccountUnavailableReason): String = when (reason) {
    AccountUnavailableReason.SECURE_STORAGE -> "Secure storage unavailable"
    AccountUnavailableReason.OFFLINE -> "Offline"
    AccountUnavailableReason.INVALID_CREDENTIALS -> "Credentials not verified"
    AccountUnavailableReason.INVALID_INPUT -> "Check the account details"
    AccountUnavailableReason.RATE_LIMITED -> "Too many attempts"
    AccountUnavailableReason.EMAIL_CONFIRMATION_REQUIRED -> "Confirm your email"
    AccountUnavailableReason.PASSWORD_RESET_REQUESTED -> "Check your email"
    AccountUnavailableReason.OAUTH_CANCELLED -> "Google sign-in cancelled"
    AccountUnavailableReason.INVALID_REDIRECT -> "Sign-in link was not accepted"
    AccountUnavailableReason.NOT_CONFIGURED -> "Account not configured"
    AccountUnavailableReason.OWNER_TRANSFER_REQUIRED -> "Transfer ownership first"
}

private fun unavailableBody(reason: AccountUnavailableReason): String = when (reason) {
    AccountUnavailableReason.SECURE_STORAGE -> "The account session could not be accessed safely. The local workflow remains available; no token was shown or copied."
    AccountUnavailableReason.OFFLINE -> "The account service is unavailable offline. The local workflow remains available and no cloud sync is claimed."
    AccountUnavailableReason.INVALID_CREDENTIALS -> "The credentials could not be verified. The local workflow remains available and no account data was disclosed."
    AccountUnavailableReason.INVALID_INPUT -> "The account details need attention. The local workflow remains available and no credentials were stored."
    AccountUnavailableReason.RATE_LIMITED -> "Please wait before trying again. The local workflow remains available and no credentials were stored."
    AccountUnavailableReason.EMAIL_CONFIRMATION_REQUIRED -> "Confirm the email address from the message we sent, then sign in. The local workflow remains available."
    AccountUnavailableReason.PASSWORD_RESET_REQUESTED -> "If that address can receive mail, a reset message was requested. The local workflow remains available."
    AccountUnavailableReason.OAUTH_CANCELLED -> "Google sign-in was cancelled. The local workflow remains available."
    AccountUnavailableReason.INVALID_REDIRECT -> "The sign-in callback could not be verified. The local workflow remains available and no session was stored."
    AccountUnavailableReason.NOT_CONFIGURED -> "The managed account adapter is not connected in this build. The local workflow remains available without an account."
    AccountUnavailableReason.OWNER_TRANSFER_REQUIRED -> "This account is the only active owner of an organization. Transfer ownership before deleting it; the local workflow remains available."
}
