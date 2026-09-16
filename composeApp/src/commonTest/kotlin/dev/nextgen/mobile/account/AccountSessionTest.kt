package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSessionTest {
    @Test
    fun restore_without_session_starts_signed_out() {
        val store = MemorySecureSessionStore()
        val controller = AccountSessionController(store) { 100L }

        assertIs<AccountSession.SignedOut>(controller.restore())
        assertIs<AccountSession.SignedOut>(controller.state)
    }

    @Test
    fun sign_in_persists_record_and_exposes_only_public_account_state() {
        val store = MemorySecureSessionStore()
        val controller = AccountSessionController(store) { 100L }
        val record = sampleStoredSession(expiresAtEpochSeconds = 200L)

        val state = controller.acceptVerifiedSession(record)

        assertEquals(AccountSession.SignedIn(record.account), state)
        assertEquals(record, store.value)
        assertEquals(record.account, (controller.state as AccountSession.SignedIn).account)
    }

    @Test
    fun expired_session_is_cleared_and_never_restored_as_signed_in() {
        val store = MemorySecureSessionStore(sampleStoredSession(expiresAtEpochSeconds = 99L))
        val controller = AccountSessionController(store) { 100L }

        assertIs<AccountSession.Expired>(controller.restore())
        assertNull(store.value)
        assertIs<AccountSession.Expired>(controller.state)
    }

    @Test
    fun unverified_session_is_rejected_and_removed_on_restore() {
        val record = sampleStoredSession().copy(
            account = AccountSummary("account-123", emailVerified = false),
        )
        val store = MemorySecureSessionStore(record)
        val controller = AccountSessionController(store) { 100L }

        assertEquals(
            AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS),
            controller.restore(),
        )
        assertNull(store.value)
    }

    @Test
    fun unverified_session_is_never_persisted_by_acceptance_path() {
        val store = MemorySecureSessionStore()
        val controller = AccountSessionController(store) { 100L }
        val record = sampleStoredSession().copy(
            account = AccountSummary("account-123", emailVerified = false),
        )

        assertEquals(
            AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS),
            controller.acceptVerifiedSession(record),
        )
        assertNull(store.value)
    }

    @Test
    fun failed_secure_read_becomes_unavailable_without_deleting_local_learning_data() {
        val controller = AccountSessionController(FailingSecureSessionStore()) { 100L }

        val state = controller.restore()

        assertEquals(AccountSession.Unavailable(AccountUnavailableReason.SECURE_STORAGE), state)
    }

    @Test
    fun sign_out_clears_secure_session_and_returns_to_signed_out() {
        val store = MemorySecureSessionStore(sampleStoredSession(expiresAtEpochSeconds = 200L))
        val controller = AccountSessionController(store) { 100L }
        controller.restore()

        assertIs<AccountSession.SignedOut>(controller.signOut())
        assertNull(store.value)
    }

    @Test
    fun session_material_diagnostic_strings_are_redacted() {
        val material = SecureSessionMaterial("access-secret", 100L)
        val record = sampleStoredSession(material = material)

        assertTrue(material.toString().contains("redacted", ignoreCase = true))
        assertTrue(record.toString().contains("redacted", ignoreCase = true))
        assertTrue("access-secret" !in material.toString())
        assertTrue("refresh-token" !in record.toString())
    }

    @Test
    fun non_session_gateway_outcomes_have_explicit_safe_states() {
        val controller = AccountSessionController(MemorySecureSessionStore()) { 100L }

        assertEquals(
            AccountSession.Unavailable(AccountUnavailableReason.EMAIL_CONFIRMATION_REQUIRED),
            controller.acceptGatewayResult(AccountGatewayResult.EmailConfirmationRequired),
        )
        assertEquals(
            AccountSession.AwaitingOAuthCallback,
            controller.acceptGatewayResult(AccountGatewayResult.OAuthStarted),
        )
        assertEquals(
            AccountSession.SignedOut,
            controller.acceptGatewayResult(AccountGatewayResult.NoSession),
        )
    }

    @Test
    fun retryable_recovery_failures_keep_the_recovery_form_available() {
        val controller = AccountSessionController(MemorySecureSessionStore()) { 100L }
        val session = sampleStoredSession()

        assertEquals(
            AccountSession.PasswordRecovery(session.account),
            controller.acceptGatewayResult(AccountGatewayResult.PasswordRecoveryReady(session)),
        )
        assertEquals(
            AccountSession.PasswordRecovery(session.account, AccountUnavailableReason.OFFLINE),
            controller.acceptGatewayResult(AccountGatewayResult.Offline),
        )
    }

    private fun sampleStoredSession(
        material: SecureSessionMaterial = SecureSessionMaterial("access", 200L),
        expiresAtEpochSeconds: Long = material.expiresAtEpochSeconds,
    ): StoredAccountSession = StoredAccountSession(
        account = AccountSummary("123e4567-e89b-42d3-a456-426614174000", emailVerified = true),
        material = material.copyForTest(expiresAtEpochSeconds),
    )
}

private class MemorySecureSessionStore(
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

private class FailingSecureSessionStore : SecureSessionStore {
    override fun read(): StoredAccountSession? = error("synthetic secure-store failure")

    override fun write(session: StoredAccountSession) = Unit

    override fun clear() = Unit
}

private fun SecureSessionMaterial.copyForTest(expiresAtEpochSeconds: Long) =
    SecureSessionMaterial(accessToken, expiresAtEpochSeconds)
