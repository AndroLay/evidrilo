package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountPresentationTest {
    @Test
    fun account_offer_is_only_available_after_value_for_signed_out_users() {
        assertTrue(shouldShowAccountBenefitPrompt(
            hasMeaningfulValue = true,
            session = AccountSession.SignedOut,
            alreadyPresented = false,
        ))
        assertFalse(shouldShowAccountBenefitPrompt(
            hasMeaningfulValue = false,
            session = AccountSession.SignedOut,
            alreadyPresented = false,
        ))
        assertFalse(shouldShowAccountBenefitPrompt(
            hasMeaningfulValue = true,
            session = AccountSession.SignedOut,
            alreadyPresented = true,
        ))
    }

    @Test
    fun account_offer_never_interrupts_a_signed_in_or_transient_session() {
        assertFalse(shouldShowAccountBenefitPrompt(
            hasMeaningfulValue = true,
            session = AccountSession.SignedIn(AccountSummary("account-123", true)),
            alreadyPresented = false,
        ))
        assertFalse(shouldShowAccountBenefitPrompt(
            hasMeaningfulValue = true,
            session = AccountSession.SigningIn,
            alreadyPresented = false,
        ))
    }

    @Test
    fun signed_out_keeps_local_core_primary_without_claiming_sync() {
        val presentation = AccountSession.SignedOut.toPresentation()

        assertEquals("Account is optional", presentation.title)
        assertTrue(presentation.body.contains("local workflow"))
        assertTrue(presentation.body.contains("without an account"))
        assertNull(presentation.actionLabel)
        assertEquals(AccountPresentationAction.NONE, presentation.action)
        assertFalse(presentation.isBusy)
    }

    @Test
    fun transient_and_failure_states_are_explicit() {
        val loading = AccountSession.SigningIn.toPresentation()
        val offline = AccountSession.Unavailable(AccountUnavailableReason.OFFLINE).toPresentation()
        val invalid = AccountSession.Unavailable(AccountUnavailableReason.INVALID_CREDENTIALS).toPresentation()
        val expired = AccountSession.Expired.toPresentation()

        assertTrue(loading.isBusy)
        assertEquals("Preparing account sign-in", loading.title)
        assertEquals("Offline", offline.title)
        assertEquals("Credentials not verified", invalid.title)
        assertEquals("Session expired", expired.title)
        listOf(offline, invalid, expired).forEach {
            assertTrue(it.body.contains("local workflow"))
            assertEquals(AccountPresentationAction.NONE, it.action)
        }
    }

    @Test
    fun signed_in_exposes_only_safe_account_summary_and_real_sign_out() {
        val presentation = AccountSession.SignedIn(
            AccountSummary("account-123", emailVerified = true),
        ).toPresentation()

        assertEquals("Account connected", presentation.title)
        assertTrue(presentation.body.contains("Cloud sync is not connected"))
        assertEquals("Sign out", presentation.actionLabel)
        assertEquals(AccountPresentationAction.SIGN_OUT, presentation.action)
        assertFalse(presentation.isBusy)
        assertFalse(presentation.body.contains("account-123"))
    }

    @Test
    fun settings_subtitle_reflects_the_account_state_without_identity_data() {
        assertEquals("Optional sign-in", AccountSession.SignedOut.toSettingsSubtitle())
        assertEquals(
            "Finish Google sign-in",
            AccountSession.AwaitingOAuthCallback.toSettingsSubtitle(),
        )
        assertEquals(
            "Verified session",
            AccountSession.SignedIn(AccountSummary("account-id", true)).toSettingsSubtitle(),
        )
    }
}
