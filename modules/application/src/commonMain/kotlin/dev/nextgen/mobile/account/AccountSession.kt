package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession

enum class AccountUnavailableReason {
    SECURE_STORAGE,
    OFFLINE,
    INVALID_CREDENTIALS,
    INVALID_INPUT,
    RATE_LIMITED,
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

    data class SignedIn(val account: AccountSummary) : AccountSession

    data object Expired : AccountSession

    data class PasswordRecovery(
        val account: AccountSummary,
        val errorReason: AccountUnavailableReason? = null,
    ) : AccountSession

    data class Unavailable(val reason: AccountUnavailableReason) : AccountSession
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

            AccountGatewayResult.OAuthCancelled ->
                AccountSession.Unavailable(AccountUnavailableReason.OAUTH_CANCELLED)

            AccountGatewayResult.InvalidRedirect ->
                AccountSession.Unavailable(AccountUnavailableReason.INVALID_REDIRECT)

            AccountGatewayResult.OAuthStarted -> AccountSession.AwaitingOAuthCallback

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
            ?: AccountSession.Unavailable(reason)
}
