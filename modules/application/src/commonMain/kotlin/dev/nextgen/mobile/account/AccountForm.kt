package dev.nextgen.mobile.account

enum class AccountAuthMode {
    SIGN_IN,
    CREATE_ACCOUNT,
    RESET_PASSWORD,
}

data class AccountFormValidation(
    val normalizedEmail: String,
    val emailError: String?,
    val passwordError: String?,
    val confirmationError: String?,
) {
    val isValid: Boolean
        get() = emailError == null && passwordError == null && confirmationError == null
}

fun validateAccountForm(
    mode: AccountAuthMode,
    email: String,
    password: String,
    confirmation: String,
): AccountFormValidation {
    val normalizedEmail = email.trim()
    val emailError = if (isValidAccountEmail(normalizedEmail)) null else "Enter a valid email address."
    val passwordError = when (mode) {
        AccountAuthMode.RESET_PASSWORD -> null
        else -> if (password.length >= 8) null else "Use at least 8 characters."
    }
    val confirmationError = when (mode) {
        AccountAuthMode.CREATE_ACCOUNT -> if (password == confirmation) null else "Passwords do not match."
        AccountAuthMode.RESET_PASSWORD -> if (password == confirmation) null else "Passwords do not match."
        AccountAuthMode.SIGN_IN -> null
    }
    return AccountFormValidation(normalizedEmail, emailError, passwordError, confirmationError)
}

fun isValidAccountEmail(value: String): Boolean =
    value.length in 3..254 && value.count { it == '@' } == 1 &&
        value.substringBefore('@').isNotBlank() &&
        value.substringAfter('@').contains('.') &&
        value.none(Char::isWhitespace)
