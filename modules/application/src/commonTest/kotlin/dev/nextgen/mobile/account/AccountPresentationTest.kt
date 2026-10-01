package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPresentationTest {
    @Test
    fun auth_heading_tracks_the_selected_email_form_mode() {
        assertEquals("Welcome back", accountAuthHeading(AccountAuthMode.SIGN_IN))
        assertEquals("Create your account", accountAuthHeading(AccountAuthMode.CREATE_ACCOUNT))
        assertEquals("Reset your password", accountAuthHeading(AccountAuthMode.RESET_PASSWORD))
    }

    @Test
    fun signed_out_explains_optional_account_sign_in_without_claiming_upload_or_service_access() {
        val presentation = AccountSession.SignedOut.toPresentation()

        assertEquals("Sign in or create an account", presentation.title)
        assertTrue(presentation.body.contains("Local projects stay on this device"))
        assertTrue(presentation.body.contains("does not upload projects"))
        assertTrue(presentation.body.contains("Pro requires a signed-in account"))
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
        assertTrue(presentation.body.contains("Local projects stay here"))
        assertTrue(presentation.body.contains("signing in does not upload them"))
        assertTrue(presentation.body.contains("their own service and access checks"))
        assertEquals("Sign out", presentation.actionLabel)
        assertEquals(AccountPresentationAction.SIGN_OUT, presentation.action)
        assertFalse(presentation.isBusy)
        assertFalse(presentation.body.contains("account-123"))
    }

    @Test
    fun settings_subtitle_reflects_the_account_state_without_identity_data() {
        assertEquals("Local only · Sign-in optional", AccountSession.SignedOut.toSettingsSubtitle())
        assertEquals(
            "Finish sign-in",
            AccountSession.AwaitingOAuthCallback.toSettingsSubtitle(),
        )
        assertEquals(
            "Account connected",
            AccountSession.SignedIn(AccountSummary("account-id", true)).toSettingsSubtitle(),
        )
        assertEquals(
            "Sign-in unavailable · Local projects stay here",
            AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED).toSettingsSubtitle(),
        )
    }

    @Test
    fun optional_account_sign_in_remains_available_while_local_guest_work_stays_enabled() {
        assertFalse(shouldShowAccountAuthForm(AccountSession.SignedOut, accountConfigured = false))
        assertFalse(shouldShowAccountAuthForm(AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED), accountConfigured = false))
        assertTrue(shouldShowAccountAuthForm(AccountSession.SignedOut, accountConfigured = true))
        assertTrue(
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
    fun restored_account_identity_is_shown_in_profile_while_local_guest_work_remains_enabled() {
        val signedIn = AccountSession.SignedIn(AccountSummary("account-id", true))

        assertTrue(shouldShowSignedInAccountInProfile(signedIn, accountAuthRestoreComplete = true))
        assertFalse(shouldShowSignedInAccountInProfile(signedIn, accountAuthRestoreComplete = false))
        assertFalse(shouldShowSignedInAccountInProfile(AccountSession.SignedOut, accountAuthRestoreComplete = true))
    }

    @Test
    fun unconfigured_account_state_explains_sign_in_unavailability_without_internal_terms() {
        val presentation = AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED).toPresentation()

        assertFalse(presentation.body.contains("adapter"))
        assertTrue(presentation.body.contains("Local projects remain available"))
        assertEquals("Sign-in unavailable", presentation.title)
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
