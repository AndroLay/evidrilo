package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountFlowTest {
    @Test
    fun form_validation_rejects_invalid_email_and_short_password_without_retaining_input() {
        val validation = validateAccountForm(
            mode = AccountAuthMode.CREATE_ACCOUNT,
            email = "not-an-email",
            password = "short",
            confirmation = "different",
        )

        assertEquals("Enter a valid email address.", validation.emailError)
        assertEquals("Use at least 8 characters.", validation.passwordError)
        assertEquals("Passwords do not match.", validation.confirmationError)
        assertFalse(validation.isValid)
    }

    @Test
    fun sign_in_does_not_require_password_confirmation_and_normalizes_email() {
        val validation = validateAccountForm(
            mode = AccountAuthMode.SIGN_IN,
            email = "  person@example.test ",
            password = "correct horse battery staple",
            confirmation = "ignored",
        )

        assertTrue(validation.isValid)
        assertEquals("person@example.test", validation.normalizedEmail)
        assertNull(validation.confirmationError)
    }

    @Test
    fun reset_mode_validates_only_the_email_field() {
        val validation = validateAccountForm(
            mode = AccountAuthMode.RESET_PASSWORD,
            email = "person@example.test",
            password = "",
            confirmation = "",
        )

        assertTrue(validation.isValid)
        assertNull(validation.passwordError)
    }
}
