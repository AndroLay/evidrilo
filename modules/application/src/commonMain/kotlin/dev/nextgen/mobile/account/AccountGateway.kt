package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.StoredAccountSession

/**
 * Provider-neutral result boundary for the managed auth adapter.
 *
 * A client-provided account id is never accepted here as proof of identity.
 * Only a provider-verified session may be handed to AccountSessionController,
 * which performs a second local verification before secure persistence.
 */
sealed interface AccountGatewayResult {
    val code: String

    data object NoSession : AccountGatewayResult {
        override val code: String = "NO_SESSION"
    }

    data object NotConfigured : AccountGatewayResult {
        override val code: String = "NOT_CONFIGURED"
    }

    data object Offline : AccountGatewayResult {
        override val code: String = "OFFLINE"
    }

    data object ServiceUnavailable : AccountGatewayResult {
        override val code: String = "SERVICE_UNAVAILABLE"
    }

    data object InvalidResponse : AccountGatewayResult {
        override val code: String = "INVALID_PROVIDER_RESPONSE"
    }

    data class OperationOutcomeUnknown(
        val operation: AccountMutationOperation,
    ) : AccountGatewayResult {
        override val code: String = "ACCOUNT_OPERATION_OUTCOME_UNKNOWN"
    }

    data object InvalidCredentials : AccountGatewayResult {
        override val code: String = "INVALID_CREDENTIALS"
    }

    data object InvalidInput : AccountGatewayResult {
        override val code: String = "INVALID_INPUT"
    }

    data object RateLimited : AccountGatewayResult {
        override val code: String = "RATE_LIMITED"
    }

    data object EmailConfirmationRequired : AccountGatewayResult {
        override val code: String = "EMAIL_CONFIRMATION_REQUIRED"
    }

    data object PasswordResetRequested : AccountGatewayResult {
        override val code: String = "PASSWORD_RESET_REQUESTED"
    }

    data object SecureStorageUnavailable : AccountGatewayResult {
        override val code: String = "SECURE_STORAGE_UNAVAILABLE"
    }

    data object OAuthStarted : AccountGatewayResult {
        override val code: String = "OAUTH_STARTED"
    }

    data class GoogleIdentityLink(
        val outcome: GoogleIdentityLinkOutcome,
        val session: StoredAccountSession? = null,
    ) : AccountGatewayResult {
        override val code: String = "GOOGLE_IDENTITY_LINK_${outcome.name}"
    }

    data class AppleIdentityLink(
        val outcome: IdentityLinkOutcome,
        val session: StoredAccountSession? = null,
    ) : AccountGatewayResult {
        override val code: String = "APPLE_IDENTITY_LINK_${outcome.name}"
    }

    data object OAuthCancelled : AccountGatewayResult {
        override val code: String = "OAUTH_CANCELLED"
    }

    data object InvalidRedirect : AccountGatewayResult {
        override val code: String = "INVALID_REDIRECT"
    }

    data object SignedOut : AccountGatewayResult {
        override val code: String = "SIGNED_OUT"
    }

    data object AccountDeleted : AccountGatewayResult {
        override val code: String = "ACCOUNT_DELETED"
    }

    data object OwnerTransferRequired : AccountGatewayResult {
        override val code: String = "OWNER_TRANSFER_REQUIRED"
    }

    data class ExportReady(val json: String) : AccountGatewayResult {
        override val code: String = "EXPORT_READY"
    }

    /** A read-only export failure must not demote an otherwise valid session. */
    data class ExportFailed(val reason: AccountUnavailableReason) : AccountGatewayResult {
        override val code: String = "EXPORT_FAILED_${reason.name}"
    }

    data object Expired : AccountGatewayResult {
        override val code: String = "EXPIRED"
    }

    data class Verified(val session: StoredAccountSession) : AccountGatewayResult {
        override val code: String = "VERIFIED_SESSION"
    }

    data class PasswordRecoveryReady(val session: StoredAccountSession) : AccountGatewayResult {
        override val code: String = "PASSWORD_RECOVERY_READY"
    }
}

enum class AccountMutationOperation {
    SIGN_UP,
    PASSWORD_RESET,
    PASSWORD_UPDATE,
    DELETE_ACCOUNT,
}

enum class IdentityLinkOutcome {
    STARTED,
    LINKED,
    ALREADY_LINKED,
    CANCELLED,
    CONFLICT,
    SETUP_REQUIRED,
    FAILED,
}

typealias GoogleIdentityLinkOutcome = IdentityLinkOutcome

enum class AccountOAuthProvider(
    val providerId: String,
    val displayName: String,
    val scopes: String,
    val allowedOAuthHosts: Set<String>,
) {
    GOOGLE(
        providerId = "google",
        displayName = "Google",
        scopes = "openid email profile",
        allowedOAuthHosts = setOf("accounts.google.com"),
    ),
    APPLE(
        providerId = "apple",
        displayName = "Apple",
        scopes = "name email",
        allowedOAuthHosts = setOf("appleid.apple.com"),
    ),
}

interface AccountGateway {
    suspend fun restore(): AccountGatewayResult

    /** The secret is accepted only at the adapter boundary and must not be persisted or logged. */
    suspend fun signIn(identifier: String, secret: String): AccountGatewayResult

    suspend fun signUp(identifier: String, secret: String): AccountGatewayResult

    suspend fun requestPasswordReset(identifier: String): AccountGatewayResult

    suspend fun startProviderSignIn(provider: AccountOAuthProvider): AccountGatewayResult

    /** Add a provider only to the active verified account; never switch or merge account IDs. */
    suspend fun startIdentityLink(provider: AccountOAuthProvider): AccountGatewayResult

    /** Clears only the selected pending link flow and preserves the signed-in account. */
    suspend fun cancelIdentityLink(provider: AccountOAuthProvider): AccountGatewayResult

    suspend fun startGoogleSignIn(): AccountGatewayResult =
        startProviderSignIn(AccountOAuthProvider.GOOGLE)

    suspend fun startAppleSignIn(): AccountGatewayResult =
        startProviderSignIn(AccountOAuthProvider.APPLE)

    suspend fun startGoogleIdentityLink(): AccountGatewayResult =
        startIdentityLink(AccountOAuthProvider.GOOGLE)

    suspend fun startAppleIdentityLink(): AccountGatewayResult =
        startIdentityLink(AccountOAuthProvider.APPLE)

    suspend fun cancelGoogleIdentityLink(): AccountGatewayResult =
        cancelIdentityLink(AccountOAuthProvider.GOOGLE)

    suspend fun cancelAppleIdentityLink(): AccountGatewayResult =
        cancelIdentityLink(AccountOAuthProvider.APPLE)

    suspend fun completeRedirect(url: String): AccountGatewayResult

    /** Changes a password only for the short-lived recovery session in memory. */
    suspend fun updatePassword(newPassword: String): AccountGatewayResult

    suspend fun signOut(): AccountGatewayResult

    suspend fun deleteAccount(): AccountGatewayResult

    suspend fun exportAccount(): AccountGatewayResult
}

class UnconfiguredAccountGateway : AccountGateway {
    override suspend fun restore(): AccountGatewayResult = AccountGatewayResult.NotConfigured

    @Suppress("UNUSED_PARAMETER")
    override suspend fun signIn(identifier: String, secret: String): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    @Suppress("UNUSED_PARAMETER")
    override suspend fun signUp(identifier: String, secret: String): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    @Suppress("UNUSED_PARAMETER")
    override suspend fun requestPasswordReset(identifier: String): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    override suspend fun startProviderSignIn(provider: AccountOAuthProvider): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    override suspend fun startIdentityLink(provider: AccountOAuthProvider): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    override suspend fun cancelIdentityLink(provider: AccountOAuthProvider): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    @Suppress("UNUSED_PARAMETER")
    override suspend fun completeRedirect(url: String): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    @Suppress("UNUSED_PARAMETER")
    override suspend fun updatePassword(newPassword: String): AccountGatewayResult =
        AccountGatewayResult.NotConfigured

    override suspend fun signOut(): AccountGatewayResult = AccountGatewayResult.NotConfigured

    override suspend fun deleteAccount(): AccountGatewayResult = AccountGatewayResult.NotConfigured

    override suspend fun exportAccount(): AccountGatewayResult = AccountGatewayResult.NotConfigured
}
