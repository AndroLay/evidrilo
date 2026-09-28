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
    private var pendingPkce: PkcePair? = null
    private var pendingGoogleIdentityLink: PendingGoogleIdentityLink? = null
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

    override suspend fun startGoogleSignIn(): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        if (pendingGoogleIdentityLink != null || pendingPkce != null) return AccountGatewayResult.InvalidInput
        val pkce = createPkcePair()
        val authorizeUrl = buildString {
            append(configuration.normalizedSupabaseUrl)
            append("/auth/v1/authorize?provider=google")
            append("&redirect_to=")
            append(encodeAuthUrlComponent(configuration.redirectUrl))
            append("&code_challenge=")
            append(encodeAuthUrlComponent(pkce.challenge))
            append("&code_challenge_method=S256&state=")
            append(encodeAuthUrlComponent(pkce.state))
        }
        pendingPkce = pkce
        if (!platform.openExternalUrl(authorizeUrl)) {
            pendingPkce = null
            return AccountGatewayResult.OAuthCancelled
        }
        return AccountGatewayResult.OAuthStarted
    }

    override suspend fun startGoogleIdentityLink(): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        if (pendingGoogleIdentityLink != null || pendingPkce != null) {
            return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
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
                else -> return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
            }
        } else {
            existing
        }

        val remoteUserResponse = request(
            method = "GET",
            path = "/auth/v1/user",
            bearerToken = currentSession.material.accessToken,
        ) ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        if (remoteUserResponse.statusCode == 401) return AccountGatewayResult.Expired
        if (remoteUserResponse.statusCode == 429) return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        if (remoteUserResponse.statusCode !in 200..299) {
            return remoteUserResponse.toGoogleIdentityLinkFailure()
        }
        val remoteAccount = remoteUserResponse.parseUser()
            ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        if (remoteAccount.accountId != currentSession.account.accountId || remoteAccount.googleLinked == null) {
            return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        }
        val refreshedCurrentSession = currentSession.copy(account = remoteAccount)
        if (remoteAccount.googleLinked) {
            return persistGoogleIdentityLinkSession(
                refreshedCurrentSession,
                GoogleIdentityLinkOutcome.ALREADY_LINKED,
            )
        }

        val pkce = createPkcePair()
        val authorizeResponse = request(
            method = "GET",
            path = buildString {
                append("/auth/v1/user/identities/authorize?provider=google&scopes=openid%20email%20profile&redirect_to=")
                append(encodeAuthUrlComponent(configuration.redirectUrl))
                append("&code_challenge=")
                append(encodeAuthUrlComponent(pkce.challenge))
                append("&code_challenge_method=s256&skip_http_redirect=true")
            },
            bearerToken = currentSession.material.accessToken,
        ) ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        if (authorizeResponse.statusCode == 401) return AccountGatewayResult.Expired
        if (authorizeResponse.statusCode == 429) return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        if (authorizeResponse.statusCode !in 200..299) {
            return authorizeResponse.toGoogleIdentityLinkFailure()
        }
        val providerUrl = authorizeResponse.parseObject()
            ?.get("url")
            ?.asJsonPrimitiveOrNull()
            ?.contentOrNull
            ?.takeIf(::isGoogleOAuthUrl)
            ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)

        try {
            secureSessionStore.write(refreshedCurrentSession)
        } catch (_: Exception) {
            return AccountGatewayResult.SecureStorageUnavailable
        }
        pendingGoogleIdentityLink = PendingGoogleIdentityLink(
            pkce = pkce,
            accountId = currentSession.account.accountId,
        )
        if (!platform.openExternalUrl(providerUrl)) {
            pendingGoogleIdentityLink = null
            return googleIdentityLink(GoogleIdentityLinkOutcome.CANCELLED)
        }
        return googleIdentityLink(GoogleIdentityLinkOutcome.STARTED)
    }

    override suspend fun cancelGoogleIdentityLink(): AccountGatewayResult {
        pendingGoogleIdentityLink = null
        return googleIdentityLink(GoogleIdentityLinkOutcome.CANCELLED)
    }

    override suspend fun completeRedirect(url: String): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        return when (val redirect = parseAuthRedirect(url, configuration.redirectUrl)) {
            AuthRedirect.Invalid -> if (pendingGoogleIdentityLink != null) {
                pendingGoogleIdentityLink = null
                googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
            } else {
                AccountGatewayResult.InvalidRedirect
            }
            is AuthRedirect.Code -> if (pendingGoogleIdentityLink != null) {
                completeGoogleIdentityLinkRedirect(redirect)
            } else {
                completeCodeRedirect(redirect)
            }
            is AuthRedirect.ProviderError -> completeProviderErrorRedirect(redirect)
            is AuthRedirect.Tokens -> if (pendingGoogleIdentityLink != null) {
                googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
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
        pendingPkce = null
        pendingGoogleIdentityLink = null
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
        val pkce = pendingPkce ?: return AccountGatewayResult.InvalidRedirect
        if (redirect.state != pkce.state) return AccountGatewayResult.InvalidRedirect
        pendingPkce = null
        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=pkce",
            body = jsonObject(
                "auth_code" to redirect.code,
                "code_verifier" to pkce.verifier,
            ),
        ) ?: return transportUnavailableResult()
        return response.toSessionResult()
    }

    private suspend fun completeGoogleIdentityLinkRedirect(redirect: AuthRedirect.Code): AccountGatewayResult {
        val pending = pendingGoogleIdentityLink ?: return AccountGatewayResult.InvalidRedirect
        if (redirect.code.isBlank()) return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        // Consume before exchange so an identical callback cannot be replayed.
        pendingGoogleIdentityLink = null
        val response = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=pkce",
            body = jsonObject(
                "auth_code" to redirect.code,
                "code_verifier" to pending.pkce.verifier,
            ),
        ) ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        if (response.statusCode !in 200..299) return response.toGoogleIdentityLinkFailure()
        val parsedCandidate = response.parseSession()
            ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        val candidate = parsedCandidate.session
        if (!parsedCandidate.account.emailVerified || parsedCandidate.account.accountId != pending.accountId) {
            return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        }

        val userResponse = request(
            method = "GET",
            path = "/auth/v1/user",
            bearerToken = candidate.material.accessToken,
        ) ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        if (userResponse.statusCode !in 200..299) {
            return userResponse.toGoogleIdentityLinkFailure()
        }
        val verifiedAccount = userResponse.parseUser()
            ?.takeIf { it.accountId == pending.accountId && it.googleLinked == true }
            ?: return googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        return persistGoogleIdentityLinkSession(
            candidate.copy(account = verifiedAccount),
            GoogleIdentityLinkOutcome.LINKED,
        )
    }

    private fun completeProviderErrorRedirect(redirect: AuthRedirect.ProviderError): AccountGatewayResult {
        val linkPending = pendingGoogleIdentityLink != null
        if (linkPending) pendingGoogleIdentityLink = null else pendingPkce = null
        val outcome = when (redirect.code.lowercase()) {
            "access_denied", "user_cancelled", "request_denied" -> GoogleIdentityLinkOutcome.CANCELLED
            "identity_already_exists" -> GoogleIdentityLinkOutcome.CONFLICT
            "manual_linking_disabled" -> GoogleIdentityLinkOutcome.SETUP_REQUIRED
            else -> GoogleIdentityLinkOutcome.FAILED
        }
        return if (linkPending) googleIdentityLink(outcome) else when (redirect.code.lowercase()) {
            "access_denied", "user_cancelled", "request_denied" -> AccountGatewayResult.OAuthCancelled
            else -> AccountGatewayResult.InvalidRedirect
        }
    }

    private fun AccountHttpResponse.toGoogleIdentityLinkFailure(): AccountGatewayResult.GoogleIdentityLink =
        when (googleAuthErrorCode()) {
            "identity_already_exists" -> googleIdentityLink(GoogleIdentityLinkOutcome.CONFLICT)
            "manual_linking_disabled" -> googleIdentityLink(GoogleIdentityLinkOutcome.SETUP_REQUIRED)
            else -> googleIdentityLink(GoogleIdentityLinkOutcome.FAILED)
        }

    private fun AccountHttpResponse.googleAuthErrorCode(): String? =
        parseObject()?.let { body ->
            body["error_code"]?.asJsonPrimitiveOrNull()?.contentOrNull
                ?: body["code"]?.asJsonPrimitiveOrNull()?.contentOrNull
        }

    private fun googleIdentityLink(outcome: GoogleIdentityLinkOutcome): AccountGatewayResult.GoogleIdentityLink =
        AccountGatewayResult.GoogleIdentityLink(outcome)

    private fun persistGoogleIdentityLinkSession(
        session: StoredAccountSession,
        outcome: GoogleIdentityLinkOutcome,
    ): AccountGatewayResult = try {
        secureSessionStore.write(session)
        AccountGatewayResult.GoogleIdentityLink(outcome, session)
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

    private fun AccountHttpResponse.toSessionResult(): AccountGatewayResult {
        if (statusCode == 401 || statusCode == 400) return AccountGatewayResult.InvalidCredentials
        if (statusCode == 429) return AccountGatewayResult.RateLimited
        if (statusCode !in 200..299) return toFailure(AccountGatewayResult.ServiceUnavailable)
        val session = parseSession() ?: return AccountGatewayResult.InvalidResponse
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
        val googleLinked = identities?.any { identity ->
            identity.asJsonObjectOrNull()
                ?.get("provider")
                ?.asJsonPrimitiveOrNull()
                ?.contentOrNull
                ?.equals("google", ignoreCase = true) == true
        }
        return AccountSummary(accountId, emailVerified, googleLinked)
    }

    private fun JsonElement.asJsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun AccountHttpResponse.parseObject(): Map<String, JsonElement>? = parseObject(body)

    private fun AccountHttpResponse.parseUser(): AccountSummary? = parseUser(body)

    private fun isGoogleOAuthUrl(value: String): Boolean {
        if (value.length !in 1..MAX_AUTH_REDIRECT_URL_BYTES || value.any { it.isWhitespace() || it.code < 0x20 }) {
            return false
        }
        val authority = value.substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        return value.startsWith("https://", ignoreCase = true) &&
            authority.equals("accounts.google.com", ignoreCase = true)
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

    private data class PendingGoogleIdentityLink(
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
