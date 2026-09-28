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

    @Test
    fun account_service_unavailable_and_invalid_response_remain_distinct_from_offline() {
        val controller = AccountSessionController(MemorySecureSessionStore()) { 100L }

        val unavailable = controller.acceptGatewayResult(AccountGatewayResult.ServiceUnavailable)
        val invalid = controller.acceptGatewayResult(AccountGatewayResult.InvalidResponse)

        assertEquals(
            AccountSession.Unavailable(AccountUnavailableReason.SERVICE_UNAVAILABLE),
            unavailable,
        )
        assertEquals(
            AccountSession.Unavailable(AccountUnavailableReason.INVALID_RESPONSE),
            invalid,
        )

        val unknown = controller.acceptGatewayResult(
            AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.DELETE_ACCOUNT),
        )
        val pending = assertIs<AccountSession.Unavailable>(unknown)
        assertEquals(AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN, pending.reason)
        assertEquals(AccountMutationOperation.DELETE_ACCOUNT, pending.pendingOperationOutcome)
        assertTrue(unknown.toPresentation().body.contains("Account deletion may have completed"))
    }

    @Test
    fun unknown_mutation_outcome_is_visible_without_discarding_verified_session() {
        val store = MemorySecureSessionStore()
        val controller = AccountSessionController(store) { 100L }
        val session = sampleStoredSession()
        controller.acceptVerifiedSession(session)

        val result = controller.acceptGatewayResult(
            AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.DELETE_ACCOUNT),
        )
        val signedIn = assertIs<AccountSession.SignedIn>(result)

        assertEquals(
            session.account,
            signedIn.account,
        )
        assertEquals(session, store.value)
        assertEquals("Request outcome not confirmed", signedIn.toPresentation().title)
        assertTrue(signedIn.toPresentation().body.contains("Account deletion may have completed"))
        assertEquals("Account request not confirmed", signedIn.toSettingsSubtitle())
    }

    @Test
    fun unknown_password_update_is_attached_to_the_recovery_state() {
        val controller = AccountSessionController(MemorySecureSessionStore()) { 100L }
        val session = sampleStoredSession()
        controller.acceptGatewayResult(AccountGatewayResult.PasswordRecoveryReady(session))

        val result = controller.acceptGatewayResult(
            AccountGatewayResult.OperationOutcomeUnknown(AccountMutationOperation.PASSWORD_UPDATE),
        )
        val recovery = assertIs<AccountSession.PasswordRecovery>(result)

        assertEquals(
            AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN,
            recovery.errorReason,
        )
        assertEquals(AccountMutationOperation.PASSWORD_UPDATE, recovery.pendingOperationOutcome)
        assertTrue(recovery.toPresentation().body.contains("password change may have completed"))
    }

    @Test
    fun export_failure_does_not_demote_a_valid_signed_in_session() {
        val controller = AccountSessionController(MemorySecureSessionStore()) { 100L }
        val session = sampleStoredSession()
        controller.acceptVerifiedSession(session)

        assertEquals(
            AccountSession.SignedIn(session.account),
            controller.acceptGatewayResult(
                AccountGatewayResult.ExportFailed(AccountUnavailableReason.OFFLINE),
            ),
        )
    }

    @Test
    fun google_link_states_preserve_the_existing_account_and_only_accept_the_same_verified_identity() {
        val store = MemorySecureSessionStore()
        val controller = AccountSessionController(store) { 100L }
        val original = sampleStoredSession().copy(
            account = sampleStoredSession().account.copy(googleLinked = false),
        )
        controller.acceptVerifiedSession(original)

        assertEquals(
            AccountSession.SignedIn(original.account, GoogleIdentityLinkOutcome.STARTED),
            controller.acceptGatewayResult(
                AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.STARTED),
            ),
        )
        assertEquals(original, store.value)

        val linked = original.copy(account = original.account.copy(googleLinked = true))
        assertEquals(
            AccountSession.SignedIn(linked.account, GoogleIdentityLinkOutcome.LINKED),
            controller.acceptGatewayResult(
                AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.LINKED, linked),
            ),
        )
        assertEquals(linked, store.value)

        assertEquals(
            AccountSession.SignedIn(linked.account, GoogleIdentityLinkOutcome.CONFLICT),
            controller.acceptGatewayResult(
                AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.CONFLICT),
            ),
        )
        assertEquals(linked, store.value)
    }

    @Test
    fun rejected_or_cancelled_oauth_callback_does_not_discard_an_existing_signed_in_account() {
        val session = sampleStoredSession()
        val controller = AccountSessionController(MemorySecureSessionStore(session)) { 100L }
        controller.restore()

        assertEquals(
            AccountSession.SignedIn(session.account),
            controller.acceptGatewayResult(AccountGatewayResult.InvalidRedirect),
        )
        assertEquals(
            AccountSession.SignedIn(session.account),
            controller.acceptGatewayResult(AccountGatewayResult.OAuthCancelled),
        )
        assertEquals(
            AccountSession.SignedIn(session.account),
            controller.acceptGatewayResult(AccountGatewayResult.Offline),
        )
    }

    @Test
    fun identity_link_result_cannot_switch_the_signed_in_account() {
        val current = sampleStoredSession()
        val other = sampleStoredSession().copy(
            account = AccountSummary("223e4567-e89b-42d3-a456-426614174000", true, googleLinked = true),
        )
        val store = MemorySecureSessionStore(current)
        val controller = AccountSessionController(store) { 100L }
        controller.restore()

        assertEquals(
            AccountSession.SignedIn(current.account, GoogleIdentityLinkOutcome.FAILED),
            controller.acceptGatewayResult(
                AccountGatewayResult.GoogleIdentityLink(GoogleIdentityLinkOutcome.LINKED, other),
            ),
        )
        assertEquals(current, store.value)
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
