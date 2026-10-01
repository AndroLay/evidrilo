package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.StoredAccountSession
import dev.nextgen.mobile.network.DeviceConnectivity
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
        appleAuthEnabled = true,
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
                sessionJson(
                    accountId = "123e4567-e89b-42d3-a456-426614174000",
                    accessToken = "oauth-access",
                    refreshToken = "oauth-refresh",
                    googleLinked = true,
                    email = "student@example.test",
                ),
            ),
        )
        val gateway = gateway(transport, MemoryAccountSecureStore(), platform = platform)

        assertIs<AccountGatewayResult.OAuthStarted>(runSuspendTest { gateway.startGoogleSignIn() })
        val url = platform.openedUrl
        assertTrue(url.startsWith("https://example.supabase.co/auth/v1/authorize?"))
        assertTrue(url.contains("provider=google"))
        assertTrue(url.contains("code_challenge_method=S256"))
        val state = url.substringAfter("state=")
        // Supabase returns redirect_to plus code, rather than echoing the
        // application's top-level state parameter.
        assertTrue(url.contains("redirect_to=${encodeAuthUrlComponent("evidrilo://auth/callback?state=$state")}"))

        assertIs<AccountGatewayResult.InvalidRedirect>(
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?code=auth-code&state=wrong-state")
            },
        )
        val result = runSuspendTest {
            gateway.completeRedirect("evidrilo://auth/callback?state=$state&code=auth-code")
        }
        assertIs<AccountGatewayResult.Verified>(result)
        assertEquals("student@example.test", result.session.account.email)
        assertTrue(transport.requests.last().body.contains("auth-code"))
        assertTrue(transport.requests.last().body.contains("code_verifier"))
    }

    @Test
    fun social_sign_in_without_a_verified_email_is_not_persisted() {
        val platform = FakeAccountAuthPlatform()
        val store = MemoryAccountSecureStore()
        val transport = FakeAccountHttpTransport(
            AccountHttpResponse(
                200,
                sessionJson(
                    accountId = "123e4567-e89b-42d3-a456-426614174000",
                    accessToken = "google-access",
                    refreshToken = "google-refresh",
                    googleLinked = true,
                ),
            ),
        )
        val gateway = gateway(transport, store, platform)
        runSuspendTest { gateway.startGoogleSignIn() }
        val state = platform.openedUrl.substringAfter("state=")

        val result = runSuspendTest {
            gateway.completeRedirect("evidrilo://auth/callback?code=google-code&state=$state")
        }

        assertEquals(AccountGatewayResult.InvalidResponse, result)
        assertNull(store.value)
    }

    @Test
    fun apple_sign_in_requests_email_and_keeps_the_verified_provider_email_on_the_same_account() {
        val accountId = "123e4567-e89b-42d3-a456-426614174000"
        val platform = FakeAccountAuthPlatform()
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(
                200,
                sessionJson(
                    accountId = accountId,
                    accessToken = "apple-access",
                    refreshToken = "apple-refresh",
                    googleLinked = false,
                    appleLinked = true,
                    email = "student@privaterelay.appleid.com",
                ),
            ),
            AccountHttpResponse(
                200,
                userJson(
                    accountId = accountId,
                    googleLinked = false,
                    appleLinked = true,
                    email = "student@privaterelay.appleid.com",
                ),
            ),
        )
        val gateway = gateway(transport, MemoryAccountSecureStore(), platform)

        assertIs<AccountGatewayResult.OAuthStarted>(runSuspendTest { gateway.startAppleSignIn() })
        val authorizeUrl = platform.openedUrl
        assertTrue(authorizeUrl.contains("provider=apple"))
        assertTrue(authorizeUrl.contains("scopes=name%20email"))
        val state = authorizeUrl.substringAfter("state=")

        val result = runSuspendTest {
            gateway.completeRedirect("evidrilo://auth/callback?code=apple-code&state=$state")
        }

        assertIs<AccountGatewayResult.Verified>(result)
        assertEquals(accountId, result.session.account.accountId)
        assertEquals("student@privaterelay.appleid.com", result.session.account.email)
        assertTrue(result.session.account.appleLinked == true)
    }

    @Test
    fun disabled_apple_provider_cannot_start_sign_in_or_link() {
        val accountId = "123e4567-e89b-42d3-a456-426614174000"
        val existing = StoredAccountSession(
            AccountSummary(accountId, true),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val platform = FakeAccountAuthPlatform()
        val transport = FakeAccountHttpTransport(AccountHttpResponse(200, "{}"))
        val gateway = SupabaseAccountGateway(
            configuration = configuration.copy(appleAuthEnabled = false),
            transport = transport,
            platform = platform,
            secureSessionStore = store,
            nowEpochSeconds = { 0L },
        )

        assertEquals(AccountGatewayResult.NotConfigured, runSuspendTest { gateway.startAppleSignIn() })
        assertEquals(
            AccountGatewayResult.AppleIdentityLink(IdentityLinkOutcome.SETUP_REQUIRED),
            runSuspendTest { gateway.startAppleIdentityLink() },
        )
        assertTrue(platform.openedUrl.isEmpty())
        assertTrue(transport.requests.isEmpty())
        assertEquals(existing, store.value)
    }

    @Test
    fun apple_identity_link_only_persists_after_the_original_account_and_apple_identity_are_verified() {
        val accountId = "123e4567-e89b-42d3-a456-426614174000"
        val existing = StoredAccountSession(
            AccountSummary(accountId, true, googleLinked = false, appleLinked = false, email = "student@example.test"),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val platform = FakeAccountAuthPlatform()
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(accountId, googleLinked = false, appleLinked = false, email = "student@example.test")),
            AccountHttpResponse(200, """{"url":"https://appleid.apple.com/auth/authorize?client_id=supabase"}"""),
            AccountHttpResponse(
                200,
                sessionJson(
                    accountId = accountId,
                    accessToken = "linked-access",
                    refreshToken = "linked-refresh",
                    googleLinked = false,
                    appleLinked = true,
                    email = "evidrilo-relay@privaterelay.appleid.com",
                ),
            ),
            AccountHttpResponse(200, userJson(accountId, googleLinked = false, appleLinked = true, email = "student@example.test")),
        )
        val gateway = gateway(transport, store, platform)

        assertIs<AccountGatewayResult.AppleIdentityLink>(runSuspendTest { gateway.startAppleIdentityLink() })
        assertEquals(existing.copy(account = existing.account.copy(appleLinked = false)), store.value)
        assertTrue(transport.requests[1].url.contains("provider=apple"))
        assertTrue(transport.requests[1].url.contains("scopes=name%20email"))
        assertEquals("https://appleid.apple.com/auth/authorize?client_id=supabase", platform.openedUrl)
        val state = stateFrom(transport.requests[1])

        val result = runSuspendTest {
            gateway.completeRedirect("evidrilo://auth/callback?code=apple-link-code&state=$state")
        }

        assertEquals(
            AccountGatewayResult.AppleIdentityLink(
                IdentityLinkOutcome.LINKED,
                existing.copy(
                    account = existing.account.copy(appleLinked = true),
                    material = SecureSessionMaterial("linked-access", 3_600, "linked-refresh"),
                ),
            ),
            result,
        )
        assertEquals(accountId, store.value?.account?.accountId)
        assertEquals(true, store.value?.account?.appleLinked)
        assertEquals("Bearer linked-access", transport.requests[3].headers["Authorization"])
    }

    @Test
    fun apple_identity_link_rejects_a_provider_code_that_cannot_be_exchanged() {
        val accountId = "123e4567-e89b-42d3-a456-426614174000"
        val existing = StoredAccountSession(
            AccountSummary(accountId, true, googleLinked = false, appleLinked = false, email = "student@example.test"),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(accountId, googleLinked = false, appleLinked = false, email = "student@example.test")),
            AccountHttpResponse(200, """{"url":"https://appleid.apple.com/auth/authorize?client_id=supabase"}"""),
            AccountHttpResponse(400, """{"error_code":"bad_oauth_callback"}"""),
        )
        val gateway = gateway(transport, store)
        runSuspendTest { gateway.startAppleIdentityLink() }
        val state = stateFrom(transport.requests[1])

        val result = runSuspendTest {
            gateway.completeRedirect("evidrilo://auth/callback?code=unredeemable-code&state=$state")
        }

        assertEquals(AccountGatewayResult.AppleIdentityLink(IdentityLinkOutcome.FAILED), result)
        assertEquals(existing, store.value)
        assertEquals(3, transport.requests.size)
        assertTrue(transport.requests[2].body.contains("code_verifier"))
    }

    @Test
    fun apple_identity_link_rejects_mismatched_state_without_exchanging_and_allows_retry() {
        val accountId = "123e4567-e89b-42d3-a456-426614174000"
        val existing = StoredAccountSession(
            AccountSummary(accountId, true, googleLinked = false, appleLinked = false, email = "student@example.test"),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val platform = FakeAccountAuthPlatform()
        val authorizeUrl = """{"url":"https://appleid.apple.com/auth/authorize?client_id=supabase"}"""
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(accountId, googleLinked = false, appleLinked = false, email = "student@example.test")),
            AccountHttpResponse(200, authorizeUrl),
            AccountHttpResponse(200, userJson(accountId, googleLinked = false, appleLinked = false, email = "student@example.test")),
            AccountHttpResponse(200, authorizeUrl),
        )
        val gateway = gateway(transport, store, platform)
        runSuspendTest { gateway.startAppleIdentityLink() }

        assertEquals(
            AccountGatewayResult.AppleIdentityLink(IdentityLinkOutcome.FAILED),
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?code=forged-code&state=wrong-state")
            },
        )
        assertEquals(2, transport.requests.size)
        assertEquals(existing, store.value)
        assertIs<AccountGatewayResult.AppleIdentityLink>(runSuspendTest { gateway.startAppleIdentityLink() })
        assertEquals(4, transport.requests.size)
    }

    @Test
    fun apple_link_provider_error_requires_matching_state_and_preserves_the_active_account() {
        val accountId = "123e4567-e89b-42d3-a456-426614174000"
        val existing = StoredAccountSession(
            AccountSummary(accountId, true, googleLinked = false, appleLinked = false, email = "student@example.test"),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(accountId, googleLinked = false, appleLinked = false, email = "student@example.test")),
            AccountHttpResponse(200, """{"url":"https://appleid.apple.com/auth/authorize?client_id=supabase"}"""),
        )
        val gateway = gateway(transport, store)
        runSuspendTest { gateway.startAppleIdentityLink() }
        val state = stateFrom(transport.requests[1])

        assertEquals(
            AccountGatewayResult.InvalidRedirect,
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?error=access_denied&state=wrong-state")
            },
        )
        assertEquals(existing, store.value)
        assertEquals(
            AccountGatewayResult.AppleIdentityLink(IdentityLinkOutcome.CANCELLED),
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?error=access_denied&state=$state")
            },
        )
        assertEquals(existing, store.value)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun oauth_error_callback_must_match_the_pending_state() {
        val platform = FakeAccountAuthPlatform()
        val gateway = gateway(
            FakeAccountHttpTransport(AccountHttpResponse(500, "{}")),
            MemoryAccountSecureStore(),
            platform = platform,
        )
        runSuspendTest { gateway.startAppleSignIn() }
        val state = platform.openedUrl.substringAfter("state=")

        assertEquals(
            AccountGatewayResult.InvalidRedirect,
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?error=access_denied&state=wrong-state")
            },
        )
        assertEquals(
            AccountGatewayResult.OAuthCancelled,
            runSuspendTest {
                gateway.completeRedirect("evidrilo://auth/callback?error=access_denied&state=$state")
            },
        )
    }

    @Test
    fun apple_identity_link_rejects_a_lookalike_authorization_host_and_preserves_the_session() {
        val existing = StoredAccountSession(
            AccountSummary(
                "123e4567-e89b-42d3-a456-426614174000",
                true,
                googleLinked = false,
                appleLinked = false,
                email = "student@example.test",
            ),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val platform = FakeAccountAuthPlatform()
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(googleLinked = false, appleLinked = false, email = "student@example.test")),
            AccountHttpResponse(200, """{"url":"https://appleid.apple.com.attacker.example/auth/authorize"}"""),
        )
        val gateway = gateway(transport, store, platform)

        val result = runSuspendTest {
            gateway.startIdentityLink(AccountOAuthProvider.APPLE)
        }

        assertEquals(AccountGatewayResult.AppleIdentityLink(IdentityLinkOutcome.FAILED), result)
        assertTrue(platform.openedUrl.isEmpty())
        assertEquals(existing, store.value)
    }

    @Test
    fun google_identity_link_uses_the_authenticated_user_route_and_keeps_the_existing_session() {
        val existing = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true, googleLinked = false),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val platform = FakeAccountAuthPlatform()
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(googleLinked = false)),
            AccountHttpResponse(200, """{"url":"https://accounts.google.com/o/oauth2/v2/auth?client_id=supabase"}"""),
        )
        val gateway = gateway(transport, store, platform)

        val result = runSuspendTest { gateway.startGoogleIdentityLink() }

        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.STARTED),
            result,
        )
        assertEquals(existing.copy(account = existing.account.copy(appleLinked = false)), store.value)
        assertEquals(2, transport.requests.size)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("https://example.supabase.co/auth/v1/user", transport.requests[0].url)
        assertEquals("GET", transport.requests[1].method)
        assertTrue(transport.requests[1].url.contains("/auth/v1/user/identities/authorize?"))
        assertTrue(transport.requests[1].url.contains("provider=google"))
        assertTrue(transport.requests[1].url.contains("scopes=openid%20email%20profile"))
        assertTrue(transport.requests[1].url.contains("code_challenge="))
        assertTrue(transport.requests[1].url.contains("state="))
        assertTrue(transport.requests[1].url.contains("skip_http_redirect=true"))
        assertEquals("Bearer existing-access", transport.requests[1].headers["Authorization"])
        assertEquals("https://accounts.google.com/o/oauth2/v2/auth?client_id=supabase", platform.openedUrl)
    }

    @Test
    fun google_identity_link_completes_only_for_the_original_account_and_verifies_google_identity() {
        val accountId = "123e4567-e89b-42d3-a456-426614174000"
        val existing = StoredAccountSession(
            AccountSummary(accountId, true, googleLinked = false),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val platform = FakeAccountAuthPlatform()
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(googleLinked = false)),
            AccountHttpResponse(200, """{"url":"https://accounts.google.com/o/oauth2/v2/auth?client_id=supabase"}"""),
            AccountHttpResponse(
                200,
                sessionJson(
                    accountId = accountId,
                    accessToken = "linked-access",
                    refreshToken = "linked-refresh",
                    googleLinked = true,
                ),
            ),
            AccountHttpResponse(200, userJson(accountId = accountId, googleLinked = true)),
        )
        val gateway = gateway(transport, store, platform)
        assertIs<AccountGatewayResult.GoogleIdentityLink>(runSuspendTest { gateway.startGoogleIdentityLink() })
        val state = stateFrom(transport.requests[1])
        val result = runSuspendTest {
            gateway.completeRedirect("evidrilo://auth/callback?code=link-code&state=$state")
        }

        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(
                GoogleIdentityLinkOutcome.LINKED,
                StoredAccountSession(
                    AccountSummary(accountId, true, googleLinked = true, appleLinked = false),
                    SecureSessionMaterial("linked-access", 3_600, "linked-refresh"),
                ),
            ),
            result,
        )
        assertEquals(accountId, store.value?.account?.accountId)
        assertEquals(true, store.value?.account?.googleLinked)
        assertEquals("POST", transport.requests[2].method)
        assertTrue(transport.requests[2].url.contains("grant_type=pkce"))
        assertTrue(transport.requests[2].body.contains("link-code"))
        assertTrue(transport.requests[2].body.contains("code_verifier"))
        assertEquals("Bearer linked-access", transport.requests[3].headers["Authorization"])
    }

    @Test
    fun google_identity_link_detects_an_existing_google_identity_without_starting_another_flow() {
        val existing = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val platform = FakeAccountAuthPlatform()
        val transport = FakeAccountHttpTransport(AccountHttpResponse(200, userJson(googleLinked = true)))
        val gateway = gateway(transport, store, platform)

        val result = runSuspendTest { gateway.startGoogleIdentityLink() }

        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(
                GoogleIdentityLinkOutcome.ALREADY_LINKED,
                existing.copy(account = existing.account.copy(googleLinked = true, appleLinked = false)),
            ),
            result,
        )
        assertEquals(true, store.value?.account?.googleLinked)
        assertTrue(platform.openedUrl.isEmpty())
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun google_identity_link_rejects_a_callback_that_changes_the_current_account() {
        val original = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true, googleLinked = false),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(original)
        val transport = QueueAccountHttpTransport(
            AccountHttpResponse(200, userJson(googleLinked = false)),
            AccountHttpResponse(200, """{"url":"https://accounts.google.com/o/oauth2/v2/auth?client_id=supabase"}"""),
            AccountHttpResponse(
                200,
                sessionJson(
                    accountId = "223e4567-e89b-42d3-a456-426614174000",
                    accessToken = "wrong-account-access",
                    refreshToken = "wrong-account-refresh",
                    googleLinked = true,
                ),
            ),
        )
        val gateway = gateway(transport, store)
        runSuspendTest { gateway.startGoogleIdentityLink() }
        val state = stateFrom(transport.requests[1])
        val result = runSuspendTest {
            gateway.completeRedirect("evidrilo://auth/callback?code=link-code&state=$state")
        }

        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.FAILED),
            result,
        )
        assertEquals(original.copy(account = original.account.copy(appleLinked = false)), store.value)
    }

    @Test
    fun google_identity_conflict_and_disabled_manual_linking_are_safe_and_do_not_sign_out() {
        val original = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val conflictGateway = gateway(
            QueueAccountHttpTransport(
                AccountHttpResponse(200, userJson(googleLinked = false)),
                AccountHttpResponse(422, """{"error_code":"identity_already_exists"}"""),
            ),
            MemoryAccountSecureStore(original),
        )
        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.CONFLICT),
            runSuspendTest { conflictGateway.startGoogleIdentityLink() },
        )

        val disabledGateway = gateway(
            QueueAccountHttpTransport(
                AccountHttpResponse(200, userJson(googleLinked = false)),
                AccountHttpResponse(404, """{"error_code":"manual_linking_disabled"}"""),
            ),
            MemoryAccountSecureStore(original),
        )
        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.SETUP_REQUIRED),
            runSuspendTest { disabledGateway.startGoogleIdentityLink() },
        )
    }

    @Test
    fun google_identity_link_rejects_non_google_authorization_urls() {
        val original = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true, googleLinked = false),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(original)
        val platform = FakeAccountAuthPlatform()
        val gateway = gateway(
            QueueAccountHttpTransport(
                AccountHttpResponse(200, userJson(googleLinked = false)),
                AccountHttpResponse(200, """{"url":"https://attacker.example/oauth"}"""),
            ),
            store,
            platform,
        )

        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.FAILED),
            runSuspendTest { gateway.startGoogleIdentityLink() },
        )
        assertEquals("", platform.openedUrl)
        assertEquals(original, store.value)
    }

    @Test
    fun cancelling_google_identity_link_clears_only_the_pending_flow() {
        val existing = StoredAccountSession(
            AccountSummary("123e4567-e89b-42d3-a456-426614174000", true, googleLinked = false),
            SecureSessionMaterial("existing-access", 3_600, "existing-refresh"),
        )
        val store = MemoryAccountSecureStore(existing)
        val gateway = gateway(
            QueueAccountHttpTransport(
                AccountHttpResponse(200, userJson(googleLinked = false)),
                AccountHttpResponse(200, """{"url":"https://accounts.google.com/o/oauth2/v2/auth?client_id=supabase"}"""),
            ),
            store,
        )
        runSuspendTest { gateway.startGoogleIdentityLink() }

        assertEquals(
            AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.CANCELLED),
            runSuspendTest { gateway.cancelGoogleIdentityLink() },
        )
        assertEquals(existing.copy(account = existing.account.copy(appleLinked = false)), store.value)
        assertEquals(
            AccountGatewayResult.InvalidRedirect,
            runSuspendTest { gateway.completeRedirect("evidrilo://auth/callback?code=late-code") },
        )
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

        assertIs<AccountGatewayResult.InvalidResponse>(
            runSuspendTest { gateway.signIn("person@example.test", "correct horse battery staple") },
        )
        assertNull(store.value)
    }

    @Test
    fun account_transport_failure_is_offline_only_when_the_platform_confirms_no_network() {
        val store = MemoryAccountSecureStore()
        val online = runSuspendTest {
            gateway(FailingAccountHttpTransport(DeviceConnectivity.ONLINE), store)
                .signIn("person@example.test", "correct horse battery staple")
        }
        val offline = runSuspendTest {
            gateway(FailingAccountHttpTransport(DeviceConnectivity.OFFLINE), store)
                .signIn("person@example.test", "correct horse battery staple")
        }

        assertIs<AccountGatewayResult.ServiceUnavailable>(online)
        assertIs<AccountGatewayResult.Offline>(offline)
    }

    @Test
    fun account_mutation_transport_loss_is_outcome_unknown_and_preserves_local_session() {
        val signUp = runSuspendTest {
            gateway(FailingAccountHttpTransport(DeviceConnectivity.ONLINE), MemoryAccountSecureStore())
                .signUp("person@example.test", "correct horse battery staple")
        }
        val signUpServerTimeout = runSuspendTest {
            gateway(
                FakeAccountHttpTransport(AccountHttpResponse(503, "{}")),
                MemoryAccountSecureStore(),
            ).signUp("person@example.test", "correct horse battery staple")
        }
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("access", 200, "refresh"),
            ),
        )
        val delete = runSuspendTest {
            gateway(
                FailingAccountHttpTransport(DeviceConnectivity.ONLINE),
                store,
                apiBaseUrl = "https://api.example.test",
            ).deleteAccount()
        }

        assertEquals(
            AccountMutationOperation.SIGN_UP,
            assertIs<AccountGatewayResult.OperationOutcomeUnknown>(signUp).operation,
        )
        assertEquals(
            AccountMutationOperation.SIGN_UP,
            assertIs<AccountGatewayResult.OperationOutcomeUnknown>(signUpServerTimeout).operation,
        )
        assertEquals(
            AccountMutationOperation.DELETE_ACCOUNT,
            assertIs<AccountGatewayResult.OperationOutcomeUnknown>(delete).operation,
        )
        assertEquals("access", store.value?.material?.accessToken)
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

    @Test
    fun account_export_requires_the_verified_session_and_returns_the_bounded_public_payload() {
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("access", 200, "refresh"),
            ),
        )
        val transport = FakeAccountHttpTransport(
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.account-export","version":"1","accountId":"123e4567-e89b-42d3-a456-426614174000","generatedAt":"2026-09-21T00:00:00.000Z","data":{"drafts":"local_only"},"requestId":"acct-export-20260921"}""",
            ),
        )
        val gateway = gateway(transport, store, apiBaseUrl = "https://api.example.test")

        val result = runSuspendTest { gateway.exportAccount() }

        assertIs<AccountGatewayResult.ExportReady>(result)
        assertTrue(result.json.contains("evidrilo.account-export"))
        assertEquals("access", transport.requests.single().headers["Authorization"]?.removePrefix("Bearer "))
        assertEquals("/v1/account/me/export", transport.requests.single().url.removePrefix("https://api.example.test"))
    }

    @Test
    fun account_export_does_not_claim_success_when_the_session_is_missing() {
        val transport = FakeAccountHttpTransport(AccountHttpResponse(200, "{}"))
        val gateway = gateway(transport, MemoryAccountSecureStore(), apiBaseUrl = "https://api.example.test")

        assertIs<AccountGatewayResult.NoSession>(runSuspendTest { gateway.exportAccount() })
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun account_export_failure_does_not_claim_that_the_export_succeeded() {
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("access", 200, "refresh"),
            ),
        )
        val gateway = gateway(
            FakeAccountHttpTransport(AccountHttpResponse(503, "{}")),
            store,
            apiBaseUrl = "https://api.example.test",
        )

        assertEquals(
            AccountGatewayResult.ExportFailed(AccountUnavailableReason.SERVICE_UNAVAILABLE),
            runSuspendTest { gateway.exportAccount() },
        )
    }

    @Test
    fun account_export_reports_when_the_server_export_exceeds_the_supported_size() {
        val store = MemoryAccountSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174000", true),
                SecureSessionMaterial("access", 200, "refresh"),
            ),
        )
        val gateway = gateway(
            FakeAccountHttpTransport(AccountHttpResponse(413, """{"code":"ACCOUNT_EXPORT_TOO_LARGE"}""")),
            store,
            apiBaseUrl = "https://api.example.test",
        )

        assertEquals(
            AccountGatewayResult.ExportFailed(AccountUnavailableReason.EXPORT_TOO_LARGE),
            runSuspendTest { gateway.exportAccount() },
        )
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

    private fun stateFrom(request: RequestRecord): String =
        request.url.substringAfter("state=").substringBefore('&')

    private fun userJson(
        accountId: String = "123e4567-e89b-42d3-a456-426614174000",
        googleLinked: Boolean,
        appleLinked: Boolean = false,
        email: String? = null,
    ): String {
        val identities = buildList {
            add("""{"provider":"email"}""")
            if (googleLinked) add("""{"provider":"google"}""")
            if (appleLinked) add("""{"provider":"apple"}""")
        }.joinToString(",")
        val emailJson = email?.let { "\"email\":\"$it\"," }.orEmpty()
        return """{"id":"$accountId",$emailJson"email_confirmed_at":"2026-09-11T00:00:00Z","identities":[$identities]}"""
    }

    private fun sessionJson(
        accountId: String,
        accessToken: String,
        refreshToken: String,
        googleLinked: Boolean,
        appleLinked: Boolean = false,
        email: String? = null,
    ): String = """{"access_token":"$accessToken","refresh_token":"$refreshToken","expires_in":3600,"user":${userJson(accountId, googleLinked, appleLinked, email)}}"""

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

private class FailingAccountHttpTransport(
    override val deviceConnectivity: DeviceConnectivity = DeviceConnectivity.UNKNOWN,
) : AccountHttpTransport {
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
