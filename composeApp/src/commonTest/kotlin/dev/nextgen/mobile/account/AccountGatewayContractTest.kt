package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AccountGatewayContractTest {
    @Test
    fun unconfiguredGatewayNeverReportsSignedIn() {
        val gateway = UnconfiguredAccountGateway()

        assertIs<AccountGatewayResult.NotConfigured>(runSuspendTest { gateway.restore() })
        assertIs<AccountGatewayResult.NotConfigured>(
            runSuspendTest { gateway.signIn(identifier = "unused", secret = "unused") },
        )
        assertIs<AccountGatewayResult.NotConfigured>(runSuspendTest { gateway.signOut() })
    }

    @Test
    fun gatewayStatesRemainExplicitAndDoNotPretendToBeSignedIn() {
        assertEquals("OFFLINE", AccountGatewayResult.Offline.code)
        assertEquals("INVALID_CREDENTIALS", AccountGatewayResult.InvalidCredentials.code)
        assertEquals("EXPIRED", AccountGatewayResult.Expired.code)
        assertEquals("NO_SESSION", AccountGatewayResult.NoSession.code)
        assertEquals("OAUTH_STARTED", AccountGatewayResult.OAuthStarted.code)
        assertEquals("EMAIL_CONFIRMATION_REQUIRED", AccountGatewayResult.EmailConfirmationRequired.code)
        assertIs<AccountGatewayResult.Verified>(
            AccountGatewayResult.Verified(testSession()),
        )
    }

    @Test
    fun unconfigured_gateway_keeps_all_credential_operations_fail_closed() {
        val gateway = UnconfiguredAccountGateway()

        assertIs<AccountGatewayResult.NotConfigured>(runSuspendTest { gateway.signUp("unused", "unused") })
        assertIs<AccountGatewayResult.NotConfigured>(runSuspendTest { gateway.requestPasswordReset("unused") })
        assertIs<AccountGatewayResult.NotConfigured>(runSuspendTest { gateway.startGoogleSignIn() })
        assertIs<AccountGatewayResult.NotConfigured>(
            runSuspendTest { gateway.completeRedirect("evidrilo://auth/callback?code=unused") },
        )
        assertIs<AccountGatewayResult.NotConfigured>(runSuspendTest { gateway.updatePassword("unused") })
        assertIs<AccountGatewayResult.NotConfigured>(runSuspendTest { gateway.deleteAccount() })
    }

    @Test
    fun unverifiedSessionIsStillRejectedByTheSessionController() {
        val controller = AccountSessionController(NoopSecureSessionStoreForTest()) { 100L }

        val result = controller.acceptGatewayResult(
            AccountGatewayResult.Verified(
                testSession(emailVerified = false),
            ),
        )

        assertIs<AccountSession.Unavailable>(result)
        assertEquals(AccountUnavailableReason.INVALID_CREDENTIALS, result.reason)
    }

    private fun testSession(emailVerified: Boolean = true) =
        dev.nextgen.mobile.security.StoredAccountSession(
            account = AccountSummary("account-123", emailVerified),
            material = dev.nextgen.mobile.security.SecureSessionMaterial("synthetic-value", 200L),
        )
}

private class NoopSecureSessionStoreForTest : dev.nextgen.mobile.security.SecureSessionStore {
    override fun read(): dev.nextgen.mobile.security.StoredAccountSession? = null

    override fun write(session: dev.nextgen.mobile.security.StoredAccountSession) = Unit

    override fun clear() = Unit
}
