package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.StoredAccountSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupabaseAccountGatewayTest {
    private val configuration = AccountClientConfiguration(
        supabaseUrl = "https://example.supabase.co",
        publishableKey = "sb_publishable_synthetic",
    )

    @Test
    fun verified_password_sign_in_returns_and_securely_persists_rotated_session_material() {
        val transport = FakeAccountHttpTransport(
            AccountHttpResponse(
                200,
                """{"access_token":"access-token","refresh_token":"refresh-token","expires_in":3600,"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":"2026-09-11T00:00:00Z"}}""",
            ),
        )
        val store = MemoryAccountSecureStore()
        val gateway = gateway(transport, store)

        val result = runSuspendTest { gateway.signIn("person@example.test", "correct horse battery staple") }

        assertIs<AccountGatewayResult.Verified>(result)
        assertEquals("123e4567-e89b-42d3-a456-426614174000", result.session.account.accountId)
        assertTrue(result.session.account.emailVerified)
        assertEquals("refresh-token", result.session.material.refreshToken)
        assertEquals(result.session, store.value)
        assertTrue(transport.requests.single().body.contains("person@example.test"))
        assertTrue(transport.requests.single().headers.containsKey("apikey"))
    }

    @Test
    fun unverified_password_sign_in_never_persists_a_session() {
        val store = MemoryAccountSecureStore()
        val gateway = gateway(
            FakeAccountHttpTransport(
                AccountHttpResponse(
                    200,
                    """{"access_token":"access-token","refresh_token":"refresh-token","expires_in":3600,"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":null}}""",
                ),
            ),
            store,
        )

        assertIs<AccountGatewayResult.EmailConfirmationRequired>(
            runSuspendTest { gateway.signIn("person@example.test", "correct horse battery staple") },
        )
        assertNull(store.value)
    }

    @Test
    fun malformed_email_confirmation_type_never_becomes_a_verified_session() {
        val store = MemoryAccountSecureStore()
        val gateway = gateway(
            FakeAccountHttpTransport(
                AccountHttpResponse(
                    200,
                    """{"access_token":"access-token","refresh_token":"refresh-token","expires_in":3600,"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":true}}""",
                ),
            ),
            store,
        )

        assertIs<AccountGatewayResult.EmailConfirmationRequired>(
            runSuspendTest { gateway.signIn("person@example.test", "correct horse battery staple") },
        )
        assertNull(store.value)
    }

    @Test
    fun sign_up_and_password_reset_have_non_enumerating_outcomes() {
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, """{"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":null}}"""),
            AccountHttpResponse(200, "{}"),
        )
        val gateway = gateway(transport, MemoryAccountSecureStore())

        assertIs<AccountGatewayResult.EmailConfirmationRequired>(
            runSuspendTest { gateway.signUp("person@example.test", "correct horse battery staple") },
        )
        assertIs<AccountGatewayResult.PasswordResetRequested>(
            runSuspendTest { gateway.requestPasswordReset("person@example.test") },
        )
    }

    @Test
    fun expired_session_refreshes_and_replaces_the_stored_refresh_token() {
        val old = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
            SecureSessionMaterial("old-access", 100, "old-refresh"),
        )
        val store = MemoryAccountSecureStore(old)
        val gateway = gateway(
            FakeAccountHttpTransport(
                AccountHttpResponse(
                    200,
                    """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":"2026-09-11T00:00:00Z"}}""",
                ),
            ),
            store,
            now = 101,
        )

        val result = runSuspendTest { gateway.restore() }

        assertIs<AccountGatewayResult.Verified>(result)
        assertEquals("new-refresh", result.session.material.refreshToken)
        assertEquals("new-access", store.value?.material?.accessToken)
    }

    @Test
    fun refresh_that_no_longer_confirms_email_clears_the_stale_secure_session() {
        val old = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
            SecureSessionMaterial("old-access", 100, "old-refresh"),
        )
        val store = MemoryAccountSecureStore(old)
        val gateway = gateway(
            FakeAccountHttpTransport(
                AccountHttpResponse(
                    200,
                    """{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":null}}""",
                ),
            ),
            store,
            now = 101,
        )

        assertIs<AccountGatewayResult.EmailConfirmationRequired>(
            runSuspendTest { gateway.restore() },
        )
        assertNull(store.value)
    }

    @Test
    fun invalid_refresh_clears_the_stored_session_and_fails_explicitly() {
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("old-access", 100, "old-refresh"),
            ),
        )
        val gateway = gateway(FakeAccountHttpTransport(AccountHttpResponse(401, "{}")), store, now = 101)

        assertIs<AccountGatewayResult.Expired>(runSuspendTest { gateway.restore() })
        assertNull(store.value)
    }

    @Test
    fun google_sso_uses_pkce_state_and_rejects_a_mismatched_callback() {
        val platform = FakeAccountAuthPlatform()
        val transport = FakeAccountHttpTransport(
            AccountHttpResponse(
                200,
                """{"access_token":"oauth-access","refresh_token":"oauth-refresh","expires_in":3600,"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":"2026-09-11T00:00:00Z"}}""",
            ),
        )
        val gateway = gateway(transport, MemoryAccountSecureStore(), platform = platform)

        assertIs<AccountGatewayResult.OAuthStarted>(runSuspendTest { gateway.startGoogleSignIn() })
        val url = platform.openedUrl
        assertTrue(url.startsWith("https://example.supabase.co/auth/v1/authorize?"))
        assertTrue(url.contains("provider=google"))
        assertTrue(url.contains("code_challenge_method=S256"))
        val state = url.substringAfter("state=")

        assertIs<AccountGatewayResult.InvalidRedirect>(
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?code=auth-code&state=wrong-state")
            },
        )
        assertIs<AccountGatewayResult.Verified>(
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?code=auth-code&state=$state")
            },
        )
        assertTrue(transport.requests.last().body.contains("auth-code"))
        assertTrue(transport.requests.last().body.contains("code_verifier"))
    }

    @Test
    fun google_sso_rejects_non_recovery_token_fragments() {
        val platform = FakeAccountAuthPlatform()
        val store = MemoryAccountSecureStore()
        val gateway = gateway(
            FakeAccountHttpTransport(AccountHttpResponse(200, "{}")),
            store,
            platform = platform,
        )

        assertIs<AccountGatewayResult.OAuthStarted>(runSuspendTest { gateway.startGoogleSignIn() })
        val state = platform.openedUrl.substringAfter("state=")
        val result = runSuspendTest {
            gateway.completeRedirect(
                "evidrilo://auth/callback#access_token=oauth-access&refresh_token=oauth-refresh&expires_in=3600&state=$state&type=signin",
            )
        }

        assertIs<AccountGatewayResult.InvalidRedirect>(result)
        assertNull(store.value)
    }

    @Test
    fun malformed_provider_json_never_becomes_a_verified_session() {
        val store = MemoryAccountSecureStore()
        val gateway = gateway(
            FakeAccountHttpTransport(
                AccountHttpResponse(
                    200,
                    """{"access_token":{},"expires_in":3600,"user":{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":"2026-09-11T00:00:00Z"}}""",
                ),
            ),
            store,
        )

        assertIs<AccountGatewayResult.Offline>(
            runSuspendTest { gateway.signIn("person@example.test", "correct horse battery staple") },
        )
        assertNull(store.value)
    }

    @Test
    fun recovery_fragment_is_temporary_until_the_new_password_is_saved() {
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(
                200,
                """{"id":"123e4567-e89b-42d3-a456-426614174000","email_confirmed_at":"2026-09-11T00:00:00Z"}""",
            ),
            AccountHttpResponse(200, "{}"),
        )
        val store = MemoryAccountSecureStore()
        val gateway = gateway(transport, store)

        assertIs<AccountGatewayResult.PasswordRecoveryReady>(
            runSuspendTest {
                gateway.completeRedirect(
                    "evidrilo://auth/callback#access_token=recovery-access&refresh_token=recovery-refresh&expires_in=3600&type=recovery",
                )
            },
        )
        assertNull(store.value)
        val result = runSuspendTest { gateway.updatePassword("new secure password") }
        assertIs<AccountGatewayResult.Verified>(result)
        assertEquals("recovery-access", store.value?.material?.accessToken)
    }

    @Test
    fun local_sign_out_clears_credentials_even_when_remote_logout_is_unavailable() {
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("access", 200, "refresh"),
            ),
        )
        val gateway = gateway(FailingAccountHttpTransport(), store)

        assertIs<AccountGatewayResult.SignedOut>(runSuspendTest { gateway.signOut() })
        assertNull(store.value)
    }

    @Test
    fun account_deletion_requires_api_configuration_and_clears_after_server_acceptance() {
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("access", 200, "refresh"),
            ),
        )
        val transport = FakeAccountHttpTransport(AccountHttpResponse(200, "{}"))
        val gateway = gateway(transport, store, apiBaseUrl = "https://api.example.test")

        assertIs<AccountGatewayResult.AccountDeleted>(runSuspendTest { gateway.deleteAccount() })
        assertNull(store.value)
        assertEquals("delete-my-account", transport.requests.single().headers["X-Account-Deletion-Confirm"])
    }

    @Test
    fun account_deletion_surfaces_the_owner_transfer_boundary_without_clearing_credentials() {
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("access", 200, "refresh"),
            ),
        )
        val gateway = gateway(
            FakeAccountHttpTransport(AccountHttpResponse(409, "")),
            store,
            apiBaseUrl = "https://api.example.test",
        )

        assertIs<AccountGatewayResult.OwnerTransferRequired>(runSuspendTest { gateway.deleteAccount() })
        assertEquals("access", store.value?.material?.accessToken)
    }

    private fun gateway(
        transport: AccountHttpTransport,
        store: MemoryAccountSecureStore,
        platform: AccountAuthPlatform = FakeAccountAuthPlatform(),
        now: Long = 0,
        apiBaseUrl: String = "",
    ) = SupabaseAccountGateway(
        configuration = configuration.copy(apiBaseUrl = apiBaseUrl),
        transport = transport,
        platform = platform,
        secureSessionStore = store,
        nowEpochSeconds = { now },
    )
}

private data class RequestRecord(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

private class FakeAccountHttpTransport(
    private val response: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<RequestRecord>()

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += RequestRecord(method, url, headers, body)
        return response
    }
}

private class QueueAccountHttpTransport(
    private vararg val responses: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<RequestRecord>()
    private var index = 0

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += RequestRecord(method, url, headers, body)
        return responses.getOrElse(index++) { responses.last() }
    }
}

private class FailingAccountHttpTransport : AccountHttpTransport {
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse = error("synthetic transport outage")
}

private class FakeAccountAuthPlatform : AccountAuthPlatform {
    var openedUrl: String = ""

    override fun openExternalUrl(url: String): Boolean {
        openedUrl = url
        return true
    }
}

private class MemoryAccountSecureStore(
    var value: StoredAccountSession? = null,
) : SecureSessionStore {
    override fun read(): StoredAccountSession? = value

    override fun write(session: StoredAccountSession) {
        value = session
    }

    override fun clear() {
        value = null
    }
}
