package dev.nextgen.mobile.account

enum class AccountPresentationAction {
    NONE,
    SIGN_OUT,
}

/** Temporary client-only release switch. It does not change or bypass API authorization. */
const val TEMPORARY_GUEST_MODE_ENABLED = true

data class AccountPresentation(
    val title: String,
    val body: String,
    val actionLabel: String?,
    val action: AccountPresentationAction,
    val isBusy: Boolean,
)

fun AccountSession.toPresentation(): AccountPresentation {
    if (TEMPORARY_GUEST_MODE_ENABLED &&
        (this == AccountSession.SignedOut ||
            (this is AccountSession.Unavailable && reason == AccountUnavailableReason.NOT_CONFIGURED))
    ) {
        return AccountPresentation(
            title = "Guest mode",
            body = "Projects and case work stay on this device; the catalog and history stay local too. Account, Pro, and server AI features are temporarily unavailable. No project content is uploaded.",
            actionLabel = null,
            action = AccountPresentationAction.NONE,
            isBusy = false,
        )
    }

    return when (this) {
        AccountSession.SignedOut -> AccountPresentation(
            title = "Sign in for account features",
            body = "Create and use local projects without an account. Sign in for account features when available. This does not upload projects; online sync is a separate choice.",
            actionLabel = null,
            action = AccountPresentationAction.NONE,
            isBusy = false,
        )

        AccountSession.SigningIn -> AccountPresentation(
            title = "Preparing account sign-in",
            body = "Sign-in is in progress. Account features will be ready when it finishes; local projects remain available.",
            actionLabel = null,
            action = AccountPresentationAction.NONE,
            isBusy = true,
        )

        AccountSession.AwaitingOAuthCallback -> AccountPresentation(
            title = "Finish Google sign-in",
            body = "Finish Google sign-in in your browser, then return to Evidrilo. Local projects remain available.",
            actionLabel = null,
            action = AccountPresentationAction.NONE,
            isBusy = true,
        )

        is AccountSession.SignedIn -> {
            val pendingOperation = pendingOperationOutcome
            AccountPresentation(
                title = pendingOperation?.let { unavailableTitle(AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN) }
                    ?: "Account connected",
                body = pendingOperation?.let(::unknownMutationBody)
                    ?: "You are signed in on this device. Online sync stays off unless you choose it separately.",
                actionLabel = "Sign out",
                action = AccountPresentationAction.SIGN_OUT,
                isBusy = false,
            )
        }

        AccountSession.Expired -> AccountPresentation(
            title = "Sign-in expired",
            body = "Sign-in expired and was removed from this device. Your local project data is still here; sign in again to use account features.",
            actionLabel = null,
            action = AccountPresentationAction.NONE,
            isBusy = false,
        )

        is AccountSession.PasswordRecovery -> {
            val recoveryError = errorReason
            val pendingOperation = pendingOperationOutcome
            AccountPresentation(
                title = pendingOperation?.let { unavailableTitle(AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN) }
                    ?: recoveryError?.let(::unavailableTitle)
                    ?: "Choose a new password",
                body = pendingOperation?.let(::unknownMutationBody)
                    ?: recoveryError?.let { "${unavailableBody(it)} You can try again." }
                    ?: "Your recovery link was verified. Choose a new password to finish recovering this account.",
                actionLabel = null,
                action = AccountPresentationAction.NONE,
                isBusy = false,
            )
        }

        is AccountSession.Unavailable -> AccountPresentation(
            title = unavailableTitle(reason),
            body = buildString {
                append(pendingOperationOutcome?.let(::unknownMutationBody) ?: unavailableBody(reason))
                append(" Account features remain unavailable until sign-in works again; local projects remain usable.")
            },
            actionLabel = null,
            action = AccountPresentationAction.NONE,
            isBusy = false,
        )
    }
}

fun AccountSession.toSettingsSubtitle(): String = when (this) {
    AccountSession.SignedOut -> if (TEMPORARY_GUEST_MODE_ENABLED) {
        "Guest mode · Local only"
    } else {
        "Sign in or create account"
    }
    AccountSession.SigningIn -> "Signing in securely"
    AccountSession.AwaitingOAuthCallback -> "Finish Google sign-in"
    is AccountSession.SignedIn ->
        if (pendingOperationOutcome != null) "Account request not confirmed" else "Account connected"
    AccountSession.Expired -> "Sign-in expired"
    is AccountSession.PasswordRecovery -> "Finish password recovery"
    is AccountSession.Unavailable -> when (reason) {
        AccountUnavailableReason.NOT_CONFIGURED -> if (TEMPORARY_GUEST_MODE_ENABLED) {
            "Guest mode · Local only"
        } else {
            "Sign in or create account"
        }
        else -> toPresentation().title
    }
}

/** Only show sign-in controls when a provider can actually accept the request. */
fun shouldShowAccountAuthForm(session: AccountSession, accountConfigured: Boolean): Boolean =
    !TEMPORARY_GUEST_MODE_ENABLED &&
        accountConfigured &&
        session !is AccountSession.SignedIn &&
        session !is AccountSession.AwaitingOAuthCallback &&
        session !is AccountSession.PasswordRecovery

private fun unavailableTitle(reason: AccountUnavailableReason): String = when (reason) {
    AccountUnavailableReason.SECURE_STORAGE -> "Secure storage unavailable"
    AccountUnavailableReason.OFFLINE -> "Offline"
    AccountUnavailableReason.SERVICE_UNAVAILABLE -> "Account service unavailable"
    AccountUnavailableReason.INVALID_RESPONSE -> "Account response could not be verified"
    AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN -> "Request outcome not confirmed"
    AccountUnavailableReason.INVALID_CREDENTIALS -> "Credentials not verified"
    AccountUnavailableReason.INVALID_INPUT -> "Check the account details"
    AccountUnavailableReason.RATE_LIMITED -> "Too many attempts"
    AccountUnavailableReason.EXPORT_TOO_LARGE -> "Export exceeds size limit"
    AccountUnavailableReason.EMAIL_CONFIRMATION_REQUIRED -> "Confirm your email"
    AccountUnavailableReason.PASSWORD_RESET_REQUESTED -> "Check your email"
    AccountUnavailableReason.OAUTH_CANCELLED -> "Google sign-in cancelled"
    AccountUnavailableReason.INVALID_REDIRECT -> "Sign-in link was not accepted"
    AccountUnavailableReason.NOT_CONFIGURED -> "Sign-in unavailable"
    AccountUnavailableReason.OWNER_TRANSFER_REQUIRED -> "Transfer ownership first"
}

private fun unavailableBody(reason: AccountUnavailableReason): String = when (reason) {
    AccountUnavailableReason.SECURE_STORAGE -> "The account session could not be accessed safely. No token was shown or copied; try signing in again."
    AccountUnavailableReason.OFFLINE -> "The account service is unavailable offline, so Evidrilo cannot verify your session. Reconnect and retry; no cloud sync was attempted."
    AccountUnavailableReason.SERVICE_UNAVAILABLE -> "The account service could not complete this request. Retry when it is available; your local project data has not been deleted."
    AccountUnavailableReason.INVALID_RESPONSE -> "The account provider returned data Evidrilo could not verify. No unverified session was stored; try signing in again."
    AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN -> "The account service may have processed this request even though its response was lost. Check the account state before repeating a change."
    AccountUnavailableReason.INVALID_CREDENTIALS -> "The credentials could not be verified. Check them and try again; no account data was disclosed."
    AccountUnavailableReason.INVALID_INPUT -> "The account details need attention. Correct them and try again; no credentials were stored."
    AccountUnavailableReason.RATE_LIMITED -> "Please wait before trying again. No credentials were stored."
    AccountUnavailableReason.EXPORT_TOO_LARGE -> "This account export exceeds the current size limit and could not be downloaded. No account data was changed."
    AccountUnavailableReason.EMAIL_CONFIRMATION_REQUIRED -> "Confirm the email address from the message we sent, then sign in."
    AccountUnavailableReason.PASSWORD_RESET_REQUESTED -> "If that address can receive mail, a reset message was requested. Sign in again after resetting your password."
    AccountUnavailableReason.OAUTH_CANCELLED -> "Google sign-in was cancelled. You can retry or choose email sign-in."
    AccountUnavailableReason.INVALID_REDIRECT -> "The sign-in callback could not be verified. No unverified session was stored; retry sign-in."
    AccountUnavailableReason.NOT_CONFIGURED -> "Sign-in is not available in this build. Local projects remain available, and no project data was changed."
    AccountUnavailableReason.OWNER_TRANSFER_REQUIRED -> "This account is the only active owner of an organization. Transfer ownership before deleting it."
}

private fun unknownMutationBody(operation: AccountMutationOperation): String = when (operation) {
    AccountMutationOperation.SIGN_UP -> "Account creation may have completed. Check for a verification email or try signing in before repeating the request."
    AccountMutationOperation.PASSWORD_RESET -> "A password reset email may have been requested. Check your inbox before requesting another."
    AccountMutationOperation.PASSWORD_UPDATE -> "Your password change may have completed. Verify which password works before submitting another change."
    AccountMutationOperation.DELETE_ACCOUNT -> "Account deletion may have completed. Check the account state before repeating the deletion; do not assume the remote account was deleted or remains active."
}
