package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPresentationTest {
    @Test
    fun signed_out_uses_clear_local_guest_copy_without_claiming_server_access() {
        val presentation = AccountSession.SignedOut.toPresentation()

        assertEquals("Guest mode", presentation.title)
        assertTrue(presentation.body.contains("Projects and case work stay on this device"))
        assertTrue(presentation.body.contains("temporarily unavailable"))
        assertTrue(presentation.body.contains("No project content is uploaded"))
        assertFalse(presentation.body.contains("Sign in"))
        assertFalse(presentation.body.contains("Sign in or create a free account to use Evidrilo's projects"))
        assertEquals(null, presentation.actionLabel)
        assertEquals(AccountPresentationAction.NONE, presentation.action)
        assertFalse(presentation.isBusy)
    }

    @Test
    fun transient_and_failure_states_are_explicit() {
        val loading = AccountSession.SigningIn.toPresentation()
        val offline = AccountSession.Unavailable(AccountUnavailableReason.OFFLINE).toPresentation()
        val unavailable = AccountSession.Unavailable(AccountUnavailableReason.SERVICE_UNAVAILABLE).toPresentation()
        val unknownOutcome = AccountSession.Unavailable(AccountUnavailableReason.OPERATION_OUTCOME_UNKNOWN).toPresentation()
        val invalid = AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS).toPresentation()
        val expired = AccountSession.Expired.toPresentation()

        assertTrue(loading.isBusy)
        assertEquals("Preparing account sign-in", loading.title)
        assertTrue(loading.body.contains("Account features will be ready when it finishes"))
        assertEquals("Offline", offline.title)
        assertEquals("Account service unavailable", unavailable.title)
        assertEquals("Request outcome not confirmed", unknownOutcome.title)
        assertEquals("Credentials not verified", invalid.title)
        assertEquals("Sign-in expired", expired.title)
        listOf(offline, unavailable, unknownOutcome, invalid).forEach {
            assertTrue(it.body.contains("Account features remain unavailable"))
            assertEquals(AccountPresentationAction.NONE, it.action)
        }
        assertTrue(expired.body.contains("Your local project data is still here"))
        assertEquals(AccountPresentationAction.NONE, expired.action)
    }

    @Test
    fun oversized_account_export_has_an_accurate_recovery_message() {
        val presentation = AccountSession.Unavailable(
            AccountUnavailableReason.EXPORT_TOO_LARGE,
        ).toPresentation()

        assertEquals("Export exceeds size limit", presentation.title)
        assertTrue(presentation.body.contains("export exceeds the current size limit"))
        assertTrue(presentation.body.contains("No account data was changed"))
    }

    @Test
    fun signed_in_exposes_only_safe_account_summary_and_real_sign_out() {
        val presentation = AccountSession.SignedIn(
            AccountSummary("account-123", emailVerified = true),
        ).toPresentation()

        assertEquals("Account connected", presentation.title)
        assertTrue(presentation.body.contains("Online sync stays off unless you choose it separately"))
        assertEquals("Sign out", presentation.actionLabel)
        assertEquals(AccountPresentationAction.SIGN_OUT, presentation.action)
        assertFalse(presentation.isBusy)
        assertFalse(presentation.body.contains("account-123"))
    }

    @Test
    fun settings_subtitle_reflects_the_account_state_without_identity_data() {
        assertEquals("Guest mode · Local only", AccountSession.SignedOut.toSettingsSubtitle())
        assertEquals(
            "Finish Google sign-in",
            AccountSession.AwaitingOAuthCallback.toSettingsSubtitle(),
        )
        assertEquals(
            "Account connected",
            AccountSession.SignedIn(AccountSummary("account-id", true)).toSettingsSubtitle(),
        )
        assertEquals(
            "Guest mode · Local only",
            AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED).toSettingsSubtitle(),
        )
    }

    @Test
    fun account_sign_in_form_is_hidden_during_temporary_guest_mode() {
        assertFalse(shouldShowAccountAuthForm(AccountSession.SignedOut, accountConfigured = false))
        assertFalse(shouldShowAccountAuthForm(AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED), accountConfigured = false))
        assertFalse(shouldShowAccountAuthForm(AccountSession.SignedOut, accountConfigured = true))
        assertFalse(
            shouldShowAccountAuthForm(
                AccountSession.Unavailable(AccountUnavailableReason.SERVICE_UNAVAILABLE),
                accountConfigured = true,
            ),
        )
        assertFalse(shouldShowAccountAuthForm(AccountSession.SignedIn(AccountSummary("account-id", true)), accountConfigured = true))
        assertFalse(shouldShowAccountAuthForm(AccountSession.AwaitingOAuthCallback, accountConfigured = true))
        assertFalse(shouldShowAccountAuthForm(AccountSession.PasswordRecovery(AccountSummary("account-id", true)), accountConfigured = true))
    }

    @Test
    fun unconfigured_account_state_uses_guest_copy_without_internal_adapter_language() {
        val presentation = AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED).toPresentation()

        assertFalse(presentation.body.contains("adapter"))
        assertTrue(presentation.body.contains("Projects and case work stay on this device"))
        assertEquals("Guest mode", presentation.title)
    }

    @Test
    fun account_status_copy_uses_student_language_not_oauth_implementation_terms() {
        val awaiting = AccountSession.AwaitingOAuthCallback.toPresentation()
        val signedIn = AccountSession.SignedIn(AccountSummary("account-id", true)).toPresentation()

        assertFalse(awaiting.body.contains("callback", ignoreCase = true))
        assertFalse(awaiting.body.contains("registered app link", ignoreCase = true))
        assertFalse(signedIn.body.contains("API", ignoreCase = true))
        assertFalse(signedIn.body.contains("token", ignoreCase = true))
    }
}
