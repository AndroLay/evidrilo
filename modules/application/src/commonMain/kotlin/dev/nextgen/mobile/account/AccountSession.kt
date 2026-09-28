package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession

enum class AccountUnavailableReason {
    SECURE_STORAGE,
    OFFLINE,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE,
    OPERATION_OUTCOME_UNKNOWN,
    INVALID_CREDENTIALS,
    INVALID_INPUT,
    RATE_LIMITED,
    EXPORT_TOO_LARGE,
    EMAIL_CONFIRMATION_REQUIRED,
    PASSWORD_RESET_REQUESTED,
    OAUTH_CANCELLED,
    INVALID_REDIRECT,
    NOT_CONFIGURED,
    OWNER_TRANSFER_REQUIRED,
}

sealed interface AccountSession {
    data object SignedOut : AccountSession

    data object SigningIn : AccountSession

    data object AwaitingOAuthCallback : AccountSession

    data class SignedIn(
        val account: AccountSummary,
        val googleLinkOutcome: GoogleIdentityLinkOutcome? = null,
        val pendingOperationOutcome: AccountMutationOperation? = null,
    ) : AccountSession

    data object Expired : AccountSession

    data class PasswordRecovery(
        val account: AccountSummary,
        val errorReason: AccountUnavailableReason? = null,
        val pendingOperationOutcome: AccountMutationOperation? = null,
    ) : AccountSession

    data class Unavailable(
        val reason: AccountUnavailableReason,
        val pendingOperationOutcome: AccountMutationOperation? = null,
    ) : AccountSession
}

class AccountSessionController(
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
) : AccountRepository {
    override var state: AccountSession = AccountSession.SignedOut
        private set

    override fun restore(): AccountSession {
        state = try {
            val stored = secureSessionStore.read()
            when {
            stored == null -> AccountSession.SignedOut
                !stored.account.emailVerified -> {
                    secureSessionStore.clear()
                    AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS)
                }
                stored.material.expiresAtEpochSeconds <= nowEpochSeconds() -> {
                    secureSessionStore.clear()
                    AccountSession.Expired
                }

                else -> AccountSession.SignedIn(stored.account)
            }
        } catch (_: Exception) {
            AccountSession.Unavailable(AccountUnavailableReason.SECURE_STORAGE)
        }
        return state
    }

    override fun beginSignIn(): AccountSession {
        state = AccountSession.SigningIn
        return state
    }

    override fun acceptGatewayResult(result: AccountGatewayResult): AccountSession {
        state = when (result) {
            AccountGatewayResult.NoSession,
            AccountGatewayResult.SignedOut,
            AccountGatewayResult.AccountDeleted,
            -> AccountSession.SignedOut

            AccountGatewayResult.NotConfigured ->
                AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED)

            AccountGatewayResult.Offline -> recoveryFailure(AccountUnavailableReason.OFFLINE)

            AccountGatewayResult.ServiceUnavailable -> recoveryFailure(AccountUnavailableReason.SERVICE_UNAVAILABLE)

            AccountGatewayResult.InvalidResponse -> recoveryFailure(AccountUnavailableReason.INVALID_RESPONSE)

            is AccountGatewayResult.OperationOutcomeUnknown ->
                unknownOperationOutcome(result.operation)

            AccountGatewayResult.InvalidCredentials ->
                AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS)

            AccountGatewayResult.InvalidInput -> recoveryFailure(AccountUnavailableReason.INVALID_INPUT)

            AccountGatewayResult.RateLimited -> recoveryFailure(AccountUnavailableReason.RATE_LIMITED)

            AccountGatewayResult.EmailConfirmationRequired ->
                AccountSession.Unavailable(AccountUnavailableReason.EMAIL_CONFIRMATION_REQUIRED)

            AccountGatewayResult.PasswordResetRequested ->
                AccountSession.Unavailable(AccountUnavailableReason.PASSWORD_RESET_REQUESTED)

            AccountGatewayResult.SecureStorageUnavailable ->
                AccountSession.Unavailable(AccountUnavailableReason.SECURE_STORAGE)

            AccountGatewayResult.OwnerTransferRequired ->
                AccountSession.Unavailable(AccountUnavailableReason.OWNER_TRANSFER_REQUIRED)

            is AccountGatewayResult.ExportReady,
            is AccountGatewayResult.ExportFailed,
            -> state

            AccountGatewayResult.OAuthCancelled ->
                preserveSignedInOrUnavailable(AccountUnavailableReason.OAUTH_CANCELLED)

            AccountGatewayResult.InvalidRedirect ->
                preserveSignedInOrUnavailable(AccountUnavailableReason.INVALID_REDIRECT)

            AccountGatewayResult.OAuthStarted -> AccountSession.AwaitingOAuthCallback

            is AccountGatewayResult.GoogleIdentityLink -> acceptGoogleIdentityLink(result)

            AccountGatewayResult.Expired -> markExpired()

            is AccountGatewayResult.Verified ->
                acceptVerifiedSession(result.session)

            is AccountGatewayResult.PasswordRecoveryReady -> {
                if (result.session.account.emailVerified) {
                    AccountSession.PasswordRecovery(result.session.account)
                } else {
                    AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS)
                }
            }
        }
        return state
    }

    override fun acceptVerifiedSession(session: StoredAccountSession): AccountSession {
        if (!session.account.emailVerified) {
            state = AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS)
            return state
        }
        return try {
            secureSessionStore.write(session)
            state = AccountSession.SignedIn(session.account)
            state
        } catch (_: Exception) {
            state = AccountSession.Unavailable(AccountUnavailableReason.SECURE_STORAGE)
            state
        }
    }

    override fun markExpired(): AccountSession {
        state = try {
            secureSessionStore.clear()
            AccountSession.Expired
        } catch (_: Exception) {
            AccountSession.Unavailable(AccountUnavailableReason.SECURE_STORAGE)
        }
        return state
    }

    override fun signOut(): AccountSession {
        state = try {
            secureSessionStore.clear()
            AccountSession.SignedOut
        } catch (_: Exception) {
            AccountSession.Unavailable(AccountUnavailableReason.SECURE_STORAGE)
        }
        return state
    }

    private fun recoveryFailure(reason: AccountUnavailableReason): AccountSession =
        (state as? AccountSession.PasswordRecovery)?.copy(errorReason = reason)
            ?: (state as? AccountSession.SignedIn)
            ?: AccountSession.Unavailable(reason)

    private fun unknownOperationOutcome(operation: AccountMutationOperation): AccountSession = when (val current = state) {
        is AccountSession.SignedIn -> current.copy(pendingOperationOutcome = operation)
        is AccountSession.PasswordRecovery -> current.copy(
            errorReason = AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN,
            pendingOperationOutcome = operation,
        )
        else -> AccountSession.Unavailable(
            reason = AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN,
            pendingOperationOutcome = operation,
        )
    }

    private fun acceptGoogleIdentityLink(result: AccountGatewayResult.GoogleIdentityLink): AccountSession {
        if (result.outcome == GoogleIdentityLinkOutcome.LINKED ||
            result.outcome == GoogleIdentityLinkOutcome.ALREADY_LINKED
        ) {
            val session = result.session
            val currentAccount = state as? AccountSession.SignedIn
            if (session != null &&
                currentAccount != null &&
                session.account.accountId == currentAccount.account.accountId &&
                session.account.emailVerified &&
                session.account.googleLinked == true
            ) {
                val accepted = acceptVerifiedSession(session)
                if (accepted is AccountSession.SignedIn) {
                    state = accepted.copy(googleLinkOutcome = result.outcome)
                    return state
                }
            }
            return preserveSignedInGoogleLinkOutcome(GoogleIdentityLinkOutcome.FAILED)
        }
        return preserveSignedInGoogleLinkOutcome(result.outcome)
    }

    private fun preserveSignedInGoogleLinkOutcome(outcome: GoogleIdentityLinkOutcome): AccountSession {
        val signedIn = state as? AccountSession.SignedIn
            ?: return AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS)
        state = signedIn.copy(googleLinkOutcome = outcome)
        return state
    }

    private fun preserveSignedInOrUnavailable(reason: AccountUnavailableReason): AccountSession =
        (state as? AccountSession.SignedIn) ?: AccountSession.Unavailable(reason)
}
