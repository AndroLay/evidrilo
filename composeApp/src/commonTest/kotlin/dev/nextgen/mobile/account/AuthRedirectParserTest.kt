package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthRedirectParserTest {
    @Test
    fun parses_a_pkce_code_callback_only_on_the_registered_redirect() {
        val result = parseAuthRedirect(
            "evidrilo://auth/callback?code=auth%2Dcode&state=state-123",
            DEFAULT_ACCOUNT_AUTH_REDIRECT_URL,
        )

        assertIs<AuthRedirect.Code>(result)
        assertEquals("auth-code", result.code)
        assertEquals("state-123", result.state)
    }

    @Test
    fun rejects_wrong_origin_missing_state_and_duplicate_parameters() {
        assertIs<AuthRedirect.Invalid>(
            parseAuthRedirect(
                "https://attacker.example/callback?code=auth-code&state=state-123",
                DEFAULT_ACCOUNT_AUTH_REDIRECT_URL,
            ),
        )
        assertIs<AuthRedirect.Invalid>(
            parseAuthRedirect(
                "evidrilo://auth/callback?code=auth-code",
                DEFAULT_ACCOUNT_AUTH_REDIRECT_URL,
            ),
        )
        assertIs<AuthRedirect.Invalid>(
            parseAuthRedirect(
                "evidrilo://auth/callback?code=one&code=two&state=state-123",
                DEFAULT_ACCOUNT_AUTH_REDIRECT_URL,
            ),
        )
        assertIs<AuthRedirect.Invalid>(
            parseAuthRedirect(
                "evidrilo://auth/callback?code=%FF&state=state-123",
                DEFAULT_ACCOUNT_AUTH_REDIRECT_URL,
            ),
        )
    }

    @Test
    fun rejects_an_oversized_callback_before_parsing_parameters() {
        val oversized = "evidrilo://auth/callback?code=" + "x".repeat(8 * 1024) + "&state=state-123"

        assertIs<AuthRedirect.Invalid>(
            parseAuthRedirect(oversized, DEFAULT_ACCOUNT_AUTH_REDIRECT_URL),
        )

        var observed: String? = null
        val unsubscribe = subscribeAccountAuthRedirect { observed = it }
        try {
            submitAccountAuthRedirect(oversized)
            assertEquals(null, observed)
        } finally {
            unsubscribe()
        }
    }

    @Test
    fun parses_recovery_tokens_from_the_fragment_without_leaking_them_in_diagnostics() {
        val result = parseAuthRedirect(
            "evidrilo://auth/callback#access_token=access-token&refresh_token=refresh-token&expires_in=3600&type=recovery",
            DEFAULT_ACCOUNT_AUTH_REDIRECT_URL,
        )

        assertIs<AuthRedirect.Tokens>(result)
        assertEquals("access-token", result.accessToken)
        assertEquals("refresh-token", result.refreshToken)
        assertEquals("recovery", result.type)
        assertEquals("3600", result.expiresIn)
        assertEquals("AuthRedirect.Tokens(redacted)", result.toString())
    }
}
