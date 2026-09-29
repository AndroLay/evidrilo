package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.StoredAccountSession
import dev.nextgen.mobile.network.DeviceConnectivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

private const val PASSWORD_MIN_LENGTH = 8
private const val PASSWORD_MAX_LENGTH = 128
private const val REFRESH_SKEW_SECONDS = 60L
private const val MAX_SESSION_LIFETIME_SECONDS = 366L * 24L * 60L * 60L

private val authJson = Json {
    ignoreUnknownKeys = true
    isLenient = false
}

class SupabaseAccountGateway(
    private val configuration: AccountClientConfiguration,
    private val transport: AccountHttpTransport,
    private val platform: AccountAuthPlatform,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
) : AccountGateway {
    private var pendingOAuthSignIn: PendingOAuthSignIn? = null
    private var pendingIdentityLink: PendingIdentityLink? = null
    private var recoverySession: StoredAccountSession? = null

    override suspend fun restore(): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        val stored = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return AccountGatewayResult.SecureStorageUnavailable
        } ?: return AccountGatewayResult.NoSession

        if (!stored.account.emailVerified) {
            return clearAfterInvalidStoredSession(AccountGatewayResult.EmailConfirmationRequired)
        }

        val now = nowEpochSeconds()
        if (stored.material.expiresAtEpochSeconds > now + REFRESH_SKEW_SECONDS) {
            return AccountGatewayResult.Verified(stored)
        }
        if (stored.material.refreshToken.isNullOrBlank()) {
            return if (stored.material.expiresAtEpochSeconds > now) {
                AccountGatewayResult.Verified(stored)
            } else {
                clearAfterInvalidStoredSession(AccountGatewayResult.Expired)
            }
        }
        return refresh(stored)
    }

    override suspend fun signIn(identifier: String, secret: String): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        val email = identifier.trim()
        if (!isValidEmail(email) || !isValidPassword(secret)) return AccountGatewayResult.InvalidInput
        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = jsonObject(
                "email" to email,
                "password" to secret,
            ),
        ) ?: return transportUnavailableResult()
        return response.toSessionResult()
    }

    override suspend fun signUp(identifier: String, secret: String): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        val email = identifier.trim()
        if (!isValidEmail(email) || !isValidPassword(secret)) return AccountGatewayResult.InvalidInput
        val response = request(
            method = "POST",
            path = "/auth/v1/signup",
            body = jsonObject(
                "email" to email,
                "password" to secret,
                "options" to buildJsonObject {
                    put("emailRedirectTo", configuration.redirectUrl)
                },
            ),
        ) ?: return AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.SIGN_UP)
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
        if (response.statusCode !in 200..299) {
            if (response.isAmbiguousMutationResponse()) {
                return AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.SIGN_UP)
            }
            return response.toFailure(AccountGatewayResult.InvalidCredentials)
        }
        val session = response.parseSession()
            ?: return AccountGatewayResult.EmailConfirmationRequired
        return session.toVerifiedResult()
    }

    override suspend fun requestPasswordReset(identifier: String): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        val email = identifier.trim()
        if (!isValidEmail(email)) return AccountGatewayResult.InvalidInput
        val response = request(
            method = "POST",
            path = "/auth/v1/recover",
            body = jsonObject(
                "email" to email,
                "redirect_to" to configuration.redirectUrl,
            ),
        ) ?: return AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.PASSWORD_RESET)
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
        return when {
            response.statusCode in 200..299 -> AccountGatewayResult.PasswordResetRequested
            response.statusCode in 400..499 -> AccountGatewayResult.PasswordResetRequested
            response.isAmbiguousMutationResponse() ->
                AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.PASSWORD_RESET)
            else -> AccountGatewayResult.ServiceUnavailable
        }
    }

    override suspend fun startProviderSignIn(provider: AccountOAuthProvider): AccountGatewayResult {
        if (!configuration.isProviderConfigured(provider)) return AccountGatewayResult.NotConfigured
        if (pendingIdentityLink != null || pendingOAuthSignIn != null) return AccountGatewayResult.InvalidInput
        val pkce = createPkcePair()
        val authorizeUrl = buildString {
            append(configuration.normalizedSupabaseUrl)
            append("/auth/v1/authorize?provider=")
            append(provider.providerId)
            append("&scopes=")
            append(encodeAuthUrlComponent(provider.scopes))
            append("&redirect_to=")
            append(encodeAuthUrlComponent(configuration.redirectUrl))
            append("&code_challenge=")
            append(encodeAuthUrlComponent(pkce.challenge))
            append("&code_challenge_method=S256&state=")
            append(encodeAuthUrlComponent(pkce.state))
        }
        pendingOAuthSignIn = PendingOAuthSignIn(provider, pkce)
        if (!platform.openExternalUrl(authorizeUrl)) {
            pendingOAuthSignIn = null
            return AccountGatewayResult.OAuthCancelled
        }
        return AccountGatewayResult.OAuthStarted
    }

    override suspend fun startIdentityLink(provider: AccountOAuthProvider): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        if (!configuration.isProviderEnabled(provider)) {
            return identityLink(provider, IdentityLinkOutcome.SETUP_REQUIRED)
        }
        if (pendingIdentityLink != null || pendingOAuthSignIn != null) {
            return identityLink(provider, IdentityLinkOutcome.FAILED)
        }

        val existing = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return AccountGatewayResult.SecureStorageUnavailable
        } ?: return AccountGatewayResult.NoSession
        if (!existing.account.emailVerified) return AccountGatewayResult.InvalidCredentials

        val currentSession = if (existing.material.expiresAtEpochSeconds <= nowEpochSeconds() + REFRESH_SKEW_SECONDS) {
            when (val refreshed = restore()) {
                is AccountGatewayResult.Verified -> refreshed.session
                AccountGatewayResult.Expired -> return refreshed
                AccountGatewayResult.NoSession -> return refreshed
                AccountGatewayResult.NotConfigured -> return refreshed
                else -> return identityLink(provider, IdentityLinkOutcome.FAILED)
            }
        } else {
            existing
        }

        val remoteUserResponse = request(
            method = "GET",
            path = "/auth/v1/user",
            bearerToken = currentSession.material.accessToken,
        ) ?: return identityLink(provider, IdentityLinkOutcome.FAILED)
        if (remoteUserResponse.statusCode == 401) return AccountGatewayResult.Expired
        if (remoteUserResponse.statusCode == 429) return identityLink(provider, IdentityLinkOutcome.FAILED)
        if (remoteUserResponse.statusCode !in 200..299) {
            return remoteUserResponse.toIdentityLinkFailure(provider)
        }
        val remoteAccount = remoteUserResponse.parseUser()
            ?: return identityLink(provider, IdentityLinkOutcome.FAILED)
        val alreadyLinked = remoteAccount.isLinked(provider)
            ?: return identityLink(provider, IdentityLinkOutcome.FAILED)
        if (remoteAccount.accountId != currentSession.account.accountId) {
            return identityLink(provider, IdentityLinkOutcome.FAILED)
        }
        val refreshedCurrentSession = currentSession.copy(account = remoteAccount)
        if (alreadyLinked) {
            return persistIdentityLinkSession(
                provider,
                refreshedCurrentSession,
                IdentityLinkOutcome.ALREADY_LINKED,
            )
        }

        val pkce = createPkcePair()
        val authorizeResponse = request(
            method = "GET",
            path = buildString {
                append("/auth/v1/user/identities/authorize?provider=")
                append(provider.providerId)
                append("&scopes=")
                append(encodeAuthUrlComponent(provider.scopes))
                append("&redirect_to=")
                append(encodeAuthUrlComponent(configuration.redirectUrl))
                append("&code_challenge=")
                append(encodeAuthUrlComponent(pkce.challenge))
                append("&code_challenge_method=s256&state=")
                append(encodeAuthUrlComponent(pkce.state))
                append("&skip_http_redirect=true")
            },
            bearerToken = currentSession.material.accessToken,
        ) ?: return identityLink(provider, IdentityLinkOutcome.FAILED)
        if (authorizeResponse.statusCode == 401) return AccountGatewayResult.Expired
        if (authorizeResponse.statusCode == 429) return identityLink(provider, IdentityLinkOutcome.FAILED)
        if (authorizeResponse.statusCode !in 200..299) {
            return authorizeResponse.toIdentityLinkFailure(provider)
        }
        val providerUrl = authorizeResponse.parseObject()
            ?.get("url")
            ?.asJsonPrimitiveOrNull()
            ?.contentOrNull
            ?.takeIf { isAllowedOAuthUrl(provider, it) }
            ?: return identityLink(provider, IdentityLinkOutcome.FAILED)

        try {
            secureSessionStore.write(refreshedCurrentSession)
        } catch (_: Exception) {
            return AccountGatewayResult.SecureStorageUnavailable
        }
        pendingIdentityLink = PendingIdentityLink(
            provider = provider,
            pkce = pkce,
            accountId = currentSession.account.accountId,
        )
        if (!platform.openExternalUrl(providerUrl)) {
            pendingIdentityLink = null
            return identityLink(provider, IdentityLinkOutcome.CANCELLED)
        }
        return identityLink(provider, IdentityLinkOutcome.STARTED)
    }

    override suspend fun cancelIdentityLink(provider: AccountOAuthProvider): AccountGatewayResult {
        if (pendingIdentityLink?.provider != provider) return identityLink(provider, IdentityLinkOutcome.FAILED)
        pendingIdentityLink = null
        return identityLink(provider, IdentityLinkOutcome.CANCELLED)
    }

    override suspend fun completeRedirect(url: String): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        return when (val redirect = parseAuthRedirect(url, configuration.redirectUrl)) {
            AuthRedirect.Invalid -> if (pendingIdentityLink != null) {
                val provider = pendingIdentityLink?.provider ?: return AccountGatewayResult.InvalidRedirect
                pendingIdentityLink = null
                identityLink(provider, IdentityLinkOutcome.FAILED)
            } else {
                pendingOAuthSignIn = null
                AccountGatewayResult.InvalidRedirect
            }
            is AuthRedirect.Code -> if (pendingIdentityLink != null) {
                completeIdentityLinkRedirect(redirect)
            } else {
                completeCodeRedirect(redirect)
            }
            is AuthRedirect.ProviderError -> completeProviderErrorRedirect(redirect)
            is AuthRedirect.Tokens -> if (pendingIdentityLink != null) {
                val provider = pendingIdentityLink?.provider ?: return AccountGatewayResult.InvalidRedirect
                pendingIdentityLink = null
                identityLink(provider, IdentityLinkOutcome.FAILED)
            } else {
                completeTokenRedirect(redirect)
            }
        }
    }

    override suspend fun updatePassword(newPassword: String): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        if (!isValidPassword(newPassword)) return AccountGatewayResult.InvalidInput
        val session = recoverySession ?: return AccountGatewayResult.Expired
        val response = request(
            method = "PUT",
            path = "/auth/v1/user",
            bearerToken = session.material.accessToken,
            body = jsonObject("password" to newPassword),
        ) ?: return AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.PASSWORD_UPDATE)
        if (response.statusCode == 401) {
            recoverySession = null
            return AccountGatewayResult.Expired
        }
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
        if (response.isAmbiguousMutationResponse()) {
            return AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.PASSWORD_UPDATE)
        }
        if (response.statusCode !in 200..299) return response.toFailure(AccountGatewayResult.InvalidInput)
        recoverySession = null
        return persistVerified(session)
    }

    override suspend fun signOut(): AccountGatewayResult {
        val stored = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            null
        }
        if (configuration.isConfigured && stored != null) {
            request(
                method = "POST",
                path = "/auth/v1/logout",
                bearerToken = stored.material.accessToken,
            )
        }
        recoverySession = null
        pendingOAuthSignIn = null
        pendingIdentityLink = null
        return try {
            secureSessionStore.clear()
            AccountGatewayResult.SignedOut
        } catch (_: Exception) {
            AccountGatewayResult.SecureStorageUnavailable
        }
    }

    override suspend fun deleteAccount(): AccountGatewayResult {
        if (!configuration.isConfigured || !configuration.apiConfigured || configuration.normalizedApiBaseUrl.isBlank()) {
            return AccountGatewayResult.NotConfigured
        }
        val stored = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return AccountGatewayResult.SecureStorageUnavailable
        } ?: return AccountGatewayResult.NoSession
        if (!stored.account.emailVerified) return AccountGatewayResult.InvalidCredentials
        val response = requestAbsolute(
            method = "DELETE",
            url = "${configuration.normalizedApiBaseUrl}/v1/account/me",
            bearerToken = stored.material.accessToken,
            headers = mapOf("X-Account-Deletion-Confirm" to "delete-my-account"),
            includeSupabaseApiKey = false,
        ) ?: return AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.DELETE_ACCOUNT)
        return when {
            response.statusCode == 401 -> AccountGatewayResult.Expired
            response.statusCode == 403 -> AccountGatewayResult.InvalidCredentials
            response.statusCode == 429 -> AccountGatewayResult.RateLimited
            response.statusCode == 409 -> AccountGatewayResult.OwnerTransferRequired
            response.isAmbiguousMutationResponse() ->
                AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.DELETE_ACCOUNT)
            response.statusCode in 200..299 -> try {
                secureSessionStore.clear()
                recoverySession = null
                AccountGatewayResult.AccountDeleted
            } catch (_: Exception) {
                AccountGatewayResult.SecureStorageUnavailable
            }
            response.statusCode in 400..499 -> AccountGatewayResult.InvalidInput
            else -> AccountGatewayResult.ServiceUnavailable
        }
    }

    override suspend fun exportAccount(): AccountGatewayResult {
        if (!configuration.isConfigured || !configuration.apiConfigured || configuration.normalizedApiBaseUrl.isBlank()) {
            return AccountGatewayResult.ExportFailed(AccountUnavailableReason.NOT_CONFIGURED)
        }
        val stored = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return AccountGatewayResult.ExportFailed(AccountUnavailableReason.SECURE_STORAGE)
        } ?: return AccountGatewayResult.NoSession
        if (!stored.account.emailVerified) return AccountGatewayResult.InvalidCredentials

        val response = requestAbsolute(
            method = "GET",
            url = "${configuration.normalizedApiBaseUrl}/v1/account/me/export",
            bearerToken = stored.material.accessToken,
            includeSupabaseApiKey = false,
        ) ?: return AccountGatewayResult.ExportFailed(transportUnavailableReason())
        return when {
            response.statusCode == 401 -> AccountGatewayResult.Expired
            response.statusCode == 403 -> AccountGatewayResult.InvalidCredentials
            response.statusCode == 429 -> AccountGatewayResult.ExportFailed(AccountUnavailableReason.RATE_LIMITED)
            response.statusCode == 413 -> AccountGatewayResult.ExportFailed(AccountUnavailableReason.EXPORT_TOO_LARGE)
            response.statusCode in 200..299 && isValidExportPayload(response.body, stored.account.accountId) ->
                AccountGatewayResult.ExportReady(response.body)
            response.statusCode in 400..499 -> AccountGatewayResult.ExportFailed(AccountUnavailableReason.INVALID_INPUT)
            else -> AccountGatewayResult.ExportFailed(AccountUnavailableReason.SERVICE_UNAVAILABLE)
        }
    }

    private suspend fun completeCodeRedirect(redirect: AuthRedirect.Code): AccountGatewayResult {
        val pending = pendingOAuthSignIn ?: return AccountGatewayResult.InvalidRedirect
        if (redirect.state != pending.pkce.state) return AccountGatewayResult.InvalidRedirect
        pendingOAuthSignIn = null
        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=pkce",
            body = jsonObject(
                "auth_code" to redirect.code,
                "code_verifier" to pending.pkce.verifier,
            ),
        ) ?: return transportUnavailableResult()
        return response.toSessionResult(expectedProvider = pending.provider)
    }

    private suspend fun completeIdentityLinkRedirect(redirect: AuthRedirect.Code): AccountGatewayResult {
        val pending = pendingIdentityLink ?: return AccountGatewayResult.InvalidRedirect
        if (redirect.state != pending.pkce.state || redirect.code.isBlank()) {
            pendingIdentityLink = null
            return identityLink(pending.provider, IdentityLinkOutcome.FAILED)
        }
        // Consume before exchange so an identical callback cannot be replayed.
        pendingIdentityLink = null
        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=pkce",
            body = jsonObject(
                "auth_code" to redirect.code,
                "code_verifier" to pending.pkce.verifier,
            ),
        ) ?: return identityLink(pending.provider, IdentityLinkOutcome.FAILED)
        if (response.statusCode !in 200..299) return response.toIdentityLinkFailure(pending.provider)
        val parsedCandidate = response.parseSession()
            ?: return identityLink(pending.provider, IdentityLinkOutcome.FAILED)
        val candidate = parsedCandidate.session
        if (!parsedCandidate.account.emailVerified ||
            parsedCandidate.account.accountId != pending.accountId ||
            parsedCandidate.account.isLinked(pending.provider) != true
        ) {
            return identityLink(pending.provider, IdentityLinkOutcome.FAILED)
        }

        val userResponse = request(
            method = "GET",
            path = "/auth/v1/user",
            bearerToken = candidate.material.accessToken,
        ) ?: return identityLink(pending.provider, IdentityLinkOutcome.FAILED)
        if (userResponse.statusCode !in 200..299) {
            return userResponse.toIdentityLinkFailure(pending.provider)
        }
        val verifiedAccount = userResponse.parseUser()
            ?.takeIf { it.accountId == pending.accountId && it.isLinked(pending.provider) == true }
            ?: return identityLink(pending.provider, IdentityLinkOutcome.FAILED)
        return persistIdentityLinkSession(
            pending.provider,
            candidate.copy(account = verifiedAccount),
            IdentityLinkOutcome.LINKED,
        )
    }

    private fun completeProviderErrorRedirect(redirect: AuthRedirect.ProviderError): AccountGatewayResult {
        val pendingLink = pendingIdentityLink
        val pendingOAuth = pendingOAuthSignIn
        val expectedState = pendingLink?.pkce?.state ?: pendingOAuth?.pkce?.state
        if (expectedState.isNullOrBlank() || redirect.state != expectedState) {
            return AccountGatewayResult.InvalidRedirect
        }
        pendingIdentityLink = null
        pendingOAuthSignIn = null
        val outcome = when (redirect.code.lowercase()) {
            "access_denied", "user_cancelled", "request_denied" -> IdentityLinkOutcome.CANCELLED
            "identity_already_exists" -> IdentityLinkOutcome.CONFLICT
            "manual_linking_disabled" -> IdentityLinkOutcome.SETUP_REQUIRED
            else -> IdentityLinkOutcome.FAILED
        }
        if (pendingLink != null) return identityLink(pendingLink.provider, outcome)
        if (pendingOAuth != null) {
            return if (outcome == IdentityLinkOutcome.CANCELLED) {
                AccountGatewayResult.OAuthCancelled
            } else {
                AccountGatewayResult.InvalidRedirect
            }
        }
        return AccountGatewayResult.InvalidRedirect
    }

    private fun AccountHttpResponse.toIdentityLinkFailure(provider: AccountOAuthProvider): AccountGatewayResult =
        when (authErrorCode()) {
            "identity_already_exists" -> identityLink(provider, IdentityLinkOutcome.CONFLICT)
            "manual_linking_disabled" -> identityLink(provider, IdentityLinkOutcome.SETUP_REQUIRED)
            else -> identityLink(provider, IdentityLinkOutcome.FAILED)
        }

    private fun AccountHttpResponse.authErrorCode(): String? =
        parseObject()?.let { body ->
            body["error_code"]?.asJsonPrimitiveOrNull()?.contentOrNull
                ?: body["code"]?.asJsonPrimitiveOrNull()?.contentOrNull
        }

    private fun identityLink(
        provider: AccountOAuthProvider,
        outcome: IdentityLinkOutcome,
        session: StoredAccountSession? = null,
    ): AccountGatewayResult = when (provider) {
        AccountOAuthProvider.GOOGLE -> AccountGatewayResult.GoogleIdentityLink(outcome, session)
        AccountOAuthProvider.APPLE -> AccountGatewayResult.AppleIdentityLink(outcome, session)
    }

    private fun persistIdentityLinkSession(
        provider: AccountOAuthProvider,
        session: StoredAccountSession,
        outcome: IdentityLinkOutcome,
    ): AccountGatewayResult = try {
        secureSessionStore.write(session)
        identityLink(provider, outcome, session)
    } catch (_: Exception) {
        AccountGatewayResult.SecureStorageUnavailable
    }

    private suspend fun completeTokenRedirect(redirect: AuthRedirect.Tokens): AccountGatewayResult {
        if (!redirect.type.equals("recovery", ignoreCase = true)) {
            // OAuth uses the PKCE authorization-code path. Accepting arbitrary
            // access-token fragments would widen the custom-scheme interception
            // surface, so fragments are reserved for Supabase recovery links.
            return AccountGatewayResult.InvalidRedirect
        }
        val session = loadRecoverySession(redirect)
            ?: return if (transport.deviceConnectivity == DeviceConnectivity.OFFLINE) {
                AccountGatewayResult.Offline
            } else {
                AccountGatewayResult.ServiceUnavailable
            }
        if (!session.account.emailVerified) return AccountGatewayResult.EmailConfirmationRequired
        recoverySession = session
        return AccountGatewayResult.PasswordRecoveryReady(session)
    }

    private suspend fun loadRecoverySession(redirect: AuthRedirect.Tokens): StoredAccountSession? {
        val expiresIn = redirect.expiresIn.toLongOrNull()
            ?.takeIf { it in 1..MAX_SESSION_LIFETIME_SECONDS } ?: return null
        val response = request(
            method = "GET",
            path = "/auth/v1/user",
            bearerToken = redirect.accessToken,
        ) ?: return null
        if (response.statusCode !in 200..299) return null
        val user = parseUser(response.body) ?: return null
        return StoredAccountSession(
            account = user,
            material = SecureSessionMaterial(
                accessToken = redirect.accessToken,
                expiresAtEpochSeconds = nowEpochSeconds() + expiresIn,
                refreshToken = redirect.refreshToken,
            ),
        )
    }

    private suspend fun refresh(stored: StoredAccountSession): AccountGatewayResult {
        val refreshToken = stored.material.refreshToken ?: return AccountGatewayResult.Expired
        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=refresh_token",
            body = jsonObject("refresh_token" to refreshToken),
        ) ?: return transportUnavailableResult()
        if (response.statusCode == 401 || response.statusCode == 400) {
            return clearAfterInvalidStoredSession(AccountGatewayResult.Expired)
        }
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
        if (response.statusCode !in 200..299) {
            return response.toFailure(AccountGatewayResult.ServiceUnavailable)
        }
        val session = response.parseSession(refreshToken) ?: return AccountGatewayResult.InvalidResponse
        if (!session.account.emailVerified) {
            // The provider is authoritative during refresh. Do not leave the
            // previously verified record available to another local boundary
            // after the provider has withdrawn email confirmation.
            return clearAfterInvalidStoredSession(AccountGatewayResult.EmailConfirmationRequired)
        }
        return persistVerified(session.session)
    }

    private fun AccountHttpResponse.toSessionResult(
        expectedProvider: AccountOAuthProvider? = null,
    ): AccountGatewayResult {
        if (statusCode == 401 || statusCode == 400) return AccountGatewayResult.InvalidCredentials
        if (statusCode == 429) return AccountGatewayResult.RateLimited
        if (statusCode !in 200..299) return toFailure(AccountGatewayResult.ServiceUnavailable)
        val session = parseSession() ?: return AccountGatewayResult.InvalidResponse
        if (expectedProvider != null) {
            if (session.account.isLinked(expectedProvider) != true || session.account.email.isNullOrBlank()) {
                return AccountGatewayResult.InvalidResponse
            }
        }
        return session.toVerifiedResult()
    }

    private fun AccountHttpResponse.toFailure(default: AccountGatewayResult): AccountGatewayResult = when {
        statusCode == 429 -> AccountGatewayResult.RateLimited
        statusCode in 400..499 -> default
        else -> AccountGatewayResult.ServiceUnavailable
    }

    private fun AccountHttpResponse.isAmbiguousMutationResponse(): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode in 500..599

    private fun transportUnavailableResult(): AccountGatewayResult =
        if (transport.deviceConnectivity == DeviceConnectivity.OFFLINE) {
            AccountGatewayResult.Offline
        } else {
            AccountGatewayResult.ServiceUnavailable
        }

    private fun transportUnavailableReason(): AccountUnavailableReason =
        if (transport.deviceConnectivity == DeviceConnectivity.OFFLINE) {
            AccountUnavailableReason.OFFLINE
        } else {
            AccountUnavailableReason.SERVICE_UNAVAILABLE
        }

    private fun ParsedSession.toVerifiedResult(): AccountGatewayResult {
        if (!account.emailVerified) {
            return AccountGatewayResult.EmailConfirmationRequired
        }
        return persistVerified(session)
    }

    private fun persistVerified(session: StoredAccountSession): AccountGatewayResult = try {
        if (!session.account.emailVerified) return AccountGatewayResult.EmailConfirmationRequired
        secureSessionStore.write(session)
        AccountGatewayResult.Verified(session)
    } catch (_: Exception) {
        AccountGatewayResult.SecureStorageUnavailable
    }

    private fun clearAfterInvalidStoredSession(result: AccountGatewayResult): AccountGatewayResult = try {
        secureSessionStore.clear()
        result
    } catch (_: Exception) {
        AccountGatewayResult.SecureStorageUnavailable
    }

    private suspend fun request(
        method: String,
        path: String,
        bearerToken: String? = null,
        body: String = "",
    ): AccountHttpResponse? = requestAbsolute(
        method = method,
        url = configuration.normalizedSupabaseUrl + path,
        bearerToken = bearerToken,
        body = body,
    )

    private suspend fun requestAbsolute(
        method: String,
        url: String,
        bearerToken: String? = null,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        includeSupabaseApiKey: Boolean = true,
    ): AccountHttpResponse? {
        val requestHeaders = buildMap {
            if (includeSupabaseApiKey) put("apikey", configuration.publishableKey)
            put("Accept", "application/json")
            if (body.isNotEmpty()) put("Content-Type", "application/json")
            if (!bearerToken.isNullOrBlank()) put("Authorization", "Bearer $bearerToken")
            putAll(headers)
        }
        return try {
            transport.request(method, url, requestHeaders, body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    private fun AccountHttpResponse.parseSession(
        fallbackRefreshToken: String? = null,
    ): ParsedSession? {
        val root = parseObject(body) ?: return null
        val accessToken = root["access_token"]?.asJsonPrimitiveOrNull()?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null
        val expiresIn = root["expires_in"]?.asJsonPrimitiveOrNull()?.longOrNull
            ?.takeIf { it in 1..MAX_SESSION_LIFETIME_SECONDS }
            ?: return null
        val refreshToken = root["refresh_token"]?.asJsonPrimitiveOrNull()?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: fallbackRefreshToken
        val user = root["user"]?.asJsonObjectOrNull() ?: return null
        val account = parseUser(user) ?: return null
        return ParsedSession(
            session = StoredAccountSession(
                account = account,
                material = SecureSessionMaterial(
                    accessToken = accessToken,
                    expiresAtEpochSeconds = nowEpochSeconds() + expiresIn,
                    refreshToken = refreshToken,
                ),
            ),
            account = account,
        )
    }

    private fun parseUser(body: String): AccountSummary? = parseObject(body)?.let(::parseUser)

    private fun parseUser(user: Map<String, JsonElement>): AccountSummary? {
        val accountId = user["id"]?.asJsonPrimitiveOrNull()?.contentOrNull?.takeIf(::isUuid) ?: return null
        val emailVerified = listOf("email_confirmed_at", "confirmed_at")
            .mapNotNull { field ->
                user[field]?.asJsonPrimitiveOrNull()
                    ?.takeIf(JsonPrimitive::isString)
                    ?.contentOrNull
            }
            .any { it.isNotBlank() }
        val identities = user["identities"] as? JsonArray
        fun linked(provider: AccountOAuthProvider): Boolean? = identities?.any { identity ->
            identity.asJsonObjectOrNull()
                ?.get("provider")
                ?.asJsonPrimitiveOrNull()
                ?.contentOrNull
                ?.equals(provider.providerId, ignoreCase = true) == true
        }
        val verifiedEmail = user["email"]
            ?.asJsonPrimitiveOrNull()
            ?.takeIf(JsonPrimitive::isString)
            ?.contentOrNull
            ?.takeIf { emailVerified && isValidEmail(it) }
        return AccountSummary(
            accountId = accountId,
            emailVerified = emailVerified,
            googleLinked = linked(AccountOAuthProvider.GOOGLE),
            appleLinked = linked(AccountOAuthProvider.APPLE),
            email = verifiedEmail,
        )
    }

    private fun JsonElement.asJsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun AccountHttpResponse.parseObject(): Map<String, JsonElement>? = parseObject(body)

    private fun AccountHttpResponse.parseUser(): AccountSummary? = parseUser(body)

    private fun isAllowedOAuthUrl(provider: AccountOAuthProvider, value: String): Boolean {
        if (value.length !in 1..MAX_AUTH_REDIRECT_URL_BYTES || value.any { it.isWhitespace() || it.code < 0x20 }) {
            return false
        }
        val authority = value.substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        return value.startsWith("https://", ignoreCase = true) &&
            provider.allowedOAuthHosts.any { authority.equals(it, ignoreCase = true) }
    }

    private fun AccountSummary.isLinked(provider: AccountOAuthProvider): Boolean? = when (provider) {
        AccountOAuthProvider.GOOGLE -> googleLinked
        AccountOAuthProvider.APPLE -> appleLinked
    }

    private fun parseObject(body: String): Map<String, JsonElement>? =
        runCatching { authJson.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun isValidExportPayload(body: String, expectedAccountId: String): Boolean {
        val root = parseObject(body) ?: return false
        return root["schema"]?.asJsonPrimitiveOrNull()?.contentOrNull == "evidrilo.account-export" &&
            root["version"]?.asJsonPrimitiveOrNull()?.contentOrNull == "1" &&
            root["accountId"]?.asJsonPrimitiveOrNull()?.contentOrNull == expectedAccountId &&
            root["generatedAt"]?.asJsonPrimitiveOrNull()?.contentOrNull?.isNotBlank() == true &&
            root["requestId"]?.asJsonPrimitiveOrNull()?.contentOrNull?.isNotBlank() == true &&
            root["data"] is JsonObject
    }

    private fun jsonObject(vararg values: Pair<String, Any>): String = buildJsonObject {
        values.forEach { (key, value) ->
            when (value) {
                is String -> put(key, value)
                is JsonElement -> put(key, value)
                else -> error("Unsupported auth request value.")
            }
        }
    }.toString()

    private fun isValidEmail(value: String): Boolean =
        value.length in 3..254 && value.count { it == '@' } == 1 &&
            value.substringBefore('@').isNotBlank() &&
            value.substringAfter('@').contains('.') &&
            value.none(Char::isWhitespace)

    private fun isValidPassword(value: String): Boolean =
        value.length in PASSWORD_MIN_LENGTH..PASSWORD_MAX_LENGTH

    private fun isUuid(value: String): Boolean =
        value.length == 36 && value.indices.all { index ->
            if (index in listOf(8, 13, 18, 23)) value[index] == '-'
            else value[index].digitToIntOrNull(16) != null
        }

    private data class ParsedSession(
        val session: StoredAccountSession,
        val account: AccountSummary,
    )

    private data class PendingOAuthSignIn(
        val provider: AccountOAuthProvider,
        val pkce: PkcePair,
    )

    private data class PendingIdentityLink(
        val provider: AccountOAuthProvider,
        val pkce: PkcePair,
        val accountId: String,
    )
}

fun createAccountGateway(): AccountGateway {
    val configuration = createAccountClientConfiguration()
    if (!configuration.isConfigured) return UnconfiguredAccountGateway()
    return SupabaseAccountGateway(
        configuration = configuration,
        transport = createAccountHttpTransport(),
        platform = createAccountAuthPlatform(),
        secureSessionStore = dev.nextgen.mobile.security.SecureSessionStoreFactory.create(),
        nowEpochSeconds = { kotlin.time.Clock.System.now().epochSeconds },
    )
}
