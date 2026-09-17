package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.StoredAccountSession
import kotlinx.serialization.json.Json
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
        ) ?: return AccountGatewayResult.Offline
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
        ) ?: return AccountGatewayResult.Offline
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
        if (response.statusCode !in 200..299) return response.toFailure(AccountGatewayResult.InvalidCredentials)
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
        ) ?: return AccountGatewayResult.Offline
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
        return when {
            response.statusCode in 200..299 -> AccountGatewayResult.PasswordResetRequested
            response.statusCode in 400..499 -> AccountGatewayResult.PasswordResetRequested
            else -> AccountGatewayResult.Offline
        }
    }

    override suspend fun startGoogleSignIn(): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
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

    override suspend fun completeRedirect(url: String): AccountGatewayResult {
        if (!configuration.isConfigured) return AccountGatewayResult.NotConfigured
        return when (val redirect = parseAuthRedirect(url, configuration.redirectUrl)) {
            AuthRedirect.Invalid -> AccountGatewayResult.InvalidRedirect
            is AuthRedirect.Code -> completeCodeRedirect(redirect)
            is AuthRedirect.Tokens -> completeTokenRedirect(redirect)
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
        ) ?: return AccountGatewayResult.Offline
        if (response.statusCode == 401) {
            recoverySession = null
            return AccountGatewayResult.Expired
        }
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
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
        ) ?: return AccountGatewayResult.Offline
        return when {
            response.statusCode == 401 -> AccountGatewayResult.Expired
            response.statusCode == 403 -> AccountGatewayResult.InvalidCredentials
            response.statusCode == 429 -> AccountGatewayResult.RateLimited
            response.statusCode == 409 -> AccountGatewayResult.OwnerTransferRequired
            response.statusCode in 200..299 -> try {
                secureSessionStore.clear()
                recoverySession = null
                AccountGatewayResult.AccountDeleted
            } catch (_: Exception) {
                AccountGatewayResult.SecureStorageUnavailable
            }
            response.statusCode in 400..499 -> AccountGatewayResult.InvalidInput
            else -> AccountGatewayResult.Offline
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
        ) ?: return AccountGatewayResult.Offline
        return response.toSessionResult()
    }

    private suspend fun completeTokenRedirect(redirect: AuthRedirect.Tokens): AccountGatewayResult {
        if (!redirect.type.equals("recovery", ignoreCase = true)) {
            // OAuth uses the PKCE authorization-code path. Accepting arbitrary
            // access-token fragments would widen the custom-scheme interception
            // surface, so fragments are reserved for Supabase recovery links.
            return AccountGatewayResult.InvalidRedirect
        }
        val session = loadRecoverySession(redirect) ?: return AccountGatewayResult.Offline
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
        ) ?: return AccountGatewayResult.Offline
        if (response.statusCode == 401 || response.statusCode == 400) {
            return clearAfterInvalidStoredSession(AccountGatewayResult.Expired)
        }
        if (response.statusCode == 429) return AccountGatewayResult.RateLimited
        val session = response.parseSession(refreshToken)
            ?: return response.toFailure(AccountGatewayResult.Offline)
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
        if (statusCode !in 200..299) return toFailure(AccountGatewayResult.Offline)
        val session = parseSession() ?: return AccountGatewayResult.Offline
        return session.toVerifiedResult()
    }

    private fun AccountHttpResponse.toFailure(default: AccountGatewayResult): AccountGatewayResult = when {
        statusCode == 429 -> AccountGatewayResult.RateLimited
        statusCode in 400..499 -> default
        else -> AccountGatewayResult.Offline
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
        return AccountSummary(accountId, emailVerified)
    }

    private fun JsonElement.asJsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun parseObject(body: String): Map<String, JsonElement>? =
        runCatching { authJson.parseToJsonElement(body) as? JsonObject }.getOrNull()

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
