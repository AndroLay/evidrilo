package dev.nextgen.mobile.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.EvidriloBackButton
import dev.nextgen.mobile.EvidriloBrandHeader
import dev.nextgen.mobile.EvidriloColors
import dev.nextgen.mobile.EvidriloContentColumn
import dev.nextgen.mobile.EvidriloPrimaryButton
import dev.nextgen.mobile.EvidriloRecoveryNotice
import dev.nextgen.mobile.EvidriloSecondaryButton
import dev.nextgen.mobile.EvidriloTintPanel
import dev.nextgen.mobile.storage.LocalStorageNotice

@Composable
internal fun EvidriloAccountScreen(
    session: AccountSession,
    isBusy: Boolean,
    accountConfigured: Boolean,
    exportJson: String?,
    exportError: AccountUnavailableReason?,
    persistenceNotice: LocalStorageNotice? = null,
    onBack: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onGoogleLink: () -> Unit,
    onCancelGoogleLink: () -> Unit,
    onCancelOAuth: () -> Unit,
    onUpdatePassword: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onExportAccount: () -> Unit,
    onDismissExport: () -> Unit,
) {
    if (TEMPORARY_GUEST_MODE_ENABLED) {
        EvidriloContentColumn {
            EvidriloBrandHeader(onSettings = null)
            EvidriloBackButton(label = "Back", onClick = onBack)
            Text("Guest mode", style = MaterialTheme.typography.displayLarge)
            Text(
                "Sign-in is temporarily turned off. Projects, the catalog, case work, and history remain available locally. Account, Pro, and server AI features are paused; no project content is uploaded.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    var mode by remember { mutableStateOf(AccountAuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmDeletion by remember { mutableStateOf(false) }
    val validation = remember(mode, email, password, confirmation) {
        validateAccountForm(mode, email, password, confirmation)
    }
    val recoveryValidation = remember(password, confirmation) {
        validateAccountForm(AccountAuthMode.CREATE_ACCOUNT, "recovery@example.test", password, confirmation)
    }
    val presentation = if (!accountConfigured && session == AccountSession.SignedOut) {
        AccountSession.Unavailable(AccountUnavailableReason.NOT_CONFIGURED).toPresentation()
    } else {
        session.toPresentation()
    }
    val showAccountAuthForm = shouldShowAccountAuthForm(session, accountConfigured)

    EvidriloContentColumn {
        EvidriloBrandHeader(onSettings = null)
        EvidriloBackButton(label = "Back", onClick = onBack)
        Text(presentation.title, style = MaterialTheme.typography.displayLarge)
        Text(presentation.body, style = MaterialTheme.typography.bodyLarge)
        persistenceNotice?.takeIf { it.isError }?.let { notice -> EvidriloRecoveryNotice(notice) }

        when {
            session is AccountSession.SignedIn -> {
                SignedInAccountPanel(
                    account = session.account,
                    googleLinkOutcome = session.googleLinkOutcome,
                    onGoogleLink = onGoogleLink,
                    onCancelGoogleLink = onCancelGoogleLink,
                    onSignOut = onSignOut,
                    onDelete = { confirmDeletion = true },
                    onExport = onExportAccount,
                    exportError = exportError,
                    onDismissExportError = onDismissExport,
                    isBusy = isBusy,
                )
            }

            session is AccountSession.PasswordRecovery -> {
                PasswordRecoveryForm(
                    password = password,
                    confirmation = confirmation,
                    passwordVisible = passwordVisible,
                    validation = recoveryValidation,
                    isBusy = isBusy,
                    onPasswordChange = { password = it.take(128) },
                    onConfirmationChange = { confirmation = it.take(128) },
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    onSubmit = { if (recoveryValidation.isValid) onUpdatePassword(password) },
                )
            }

            session is AccountSession.AwaitingOAuthCallback -> {
                EvidriloTintPanel {
                    Text("Browser sign-in is waiting", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Finish Google sign-in in your browser, then return to Evidrilo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EvidriloSecondaryButton(
                    label = "Use email sign-in instead",
                    onClick = onCancelOAuth,
                    enabled = !isBusy,
                )
            }

            else -> {
                if (showAccountAuthForm) {
                    if (mode == AccountAuthMode.RESET_PASSWORD) {
                        ResetPasswordForm(
                            email = email,
                            emailError = validation.emailError,
                            accountConfigured = accountConfigured,
                            isBusy = isBusy,
                            onEmailChange = { email = it.take(254) },
                            onSubmit = {
                                if (accountConfigured && validation.isValid) {
                                    onResetPassword(validation.normalizedEmail)
                                }
                            },
                            onBackToSignIn = { mode = AccountAuthMode.SIGN_IN },
                        )
                    } else {
                        EmailPasswordForm(
                            mode = mode,
                            email = email,
                            password = password,
                            confirmation = confirmation,
                            passwordVisible = passwordVisible,
                            validation = validation,
                            accountConfigured = accountConfigured,
                            isBusy = isBusy,
                            onEmailChange = { email = it.take(254) },
                            onPasswordChange = { password = it.take(128) },
                            onConfirmationChange = { confirmation = it.take(128) },
                            onTogglePassword = { passwordVisible = !passwordVisible },
                            onSubmit = {
                                if (accountConfigured && validation.isValid) {
                                    if (mode == AccountAuthMode.SIGN_IN) {
                                        onSignIn(validation.normalizedEmail, password)
                                    } else {
                                        onSignUp(validation.normalizedEmail, password)
                                    }
                                }
                            },
                            onResetPassword = { mode = AccountAuthMode.RESET_PASSWORD },
                            onToggleMode = {
                                mode = if (mode == AccountAuthMode.SIGN_IN) {
                                    AccountAuthMode.CREATE_ACCOUNT
                                } else {
                                    AccountAuthMode.SIGN_IN
                                }
                                password = ""
                                confirmation = ""
                            },
                        )
                        EvidriloSecondaryButton(
                            label = "Continue with Google",
                            onClick = onGoogleSignIn,
                            enabled = !isBusy && accountConfigured,
                        )
                    }
                }
            }
        }

        EvidriloTintPanel {
            Text("Local projects do not require sign-in", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sign in for account features when available. This does not upload your projects; online sync is a separate choice. Your password is not saved by Evidrilo.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) confirmDeletion = false },
            title = { Text("Delete account data?") },
            text = {
                Text(
                    "This removes or anonymizes data stored by Evidrilo and signs you out. It does not delete local projects or your Google/email account; those must be removed separately.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeletion = false
                        onDeleteAccount()
                    },
                    enabled = !isBusy,
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletion = false }, enabled = !isBusy) { Text("Cancel") }
            },
        )
    }

    if (exportJson != null) {
        AlertDialog(
            onDismissRequest = onDismissExport,
            title = { Text("Account export") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Select and copy this JSON if you need a portable record. Local drafts are not included because they remain on this device.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(exportJson, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissExport) { Text("Close") }
            },
        )
    }
}

@Composable
private fun EmailPasswordForm(
    mode: AccountAuthMode,
    email: String,
    password: String,
    confirmation: String,
    passwordVisible: Boolean,
    validation: AccountFormValidation,
    accountConfigured: Boolean,
    isBusy: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onResetPassword: () -> Unit,
    onToggleMode: () -> Unit,
) {
    val isSignIn = mode == AccountAuthMode.SIGN_IN
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Email address") },
        singleLine = true,
        isError = validation.emailError != null,
        supportingText = validation.emailError?.let { error -> { Text(error) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    PasswordField(
        label = "Password",
        value = password,
        visible = passwordVisible,
        error = validation.passwordError,
        enabled = !isBusy,
        onValueChange = onPasswordChange,
        onToggleVisibility = onTogglePassword,
    )
    if (!isSignIn) {
        PasswordField(
            label = "Confirm password",
            value = confirmation,
            visible = passwordVisible,
            error = validation.confirmationError,
            enabled = !isBusy,
            onValueChange = onConfirmationChange,
            onToggleVisibility = onTogglePassword,
        )
    }
    if (!isSignIn) {
        Text(
            "Use the same password in both fields before creating the account.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
    }
    EvidriloPrimaryButton(
        label = if (isSignIn) "Sign in" else "Create account",
        onClick = onSubmit,
        enabled = !isBusy && accountConfigured && validation.isValid,
    )
    if (isSignIn) {
        TextButton(onClick = onResetPassword, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text("Forgot password?")
        }
    }
    TextButton(onClick = onToggleMode, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
        Text(if (isSignIn) "Create a new account" else "I already have an account")
    }
}

@Composable
private fun ResetPasswordForm(
    email: String,
    emailError: String?,
    accountConfigured: Boolean,
    isBusy: Boolean,
    onEmailChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBackToSignIn: () -> Unit,
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Email address") },
        singleLine = true,
        isError = emailError != null,
        supportingText = emailError?.let { error -> { Text(error) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    Text(
        "We will show the same confirmation whether or not the address belongs to an account.",
        style = MaterialTheme.typography.bodyMedium,
    )
    EvidriloPrimaryButton(
        label = "Send reset link",
        onClick = onSubmit,
        enabled = !isBusy && accountConfigured && emailError == null,
    )
    TextButton(onClick = onBackToSignIn, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
        Text("Back to sign in")
    }
}

@Composable
private fun PasswordRecoveryForm(
    password: String,
    confirmation: String,
    passwordVisible: Boolean,
    validation: AccountFormValidation,
    isBusy: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
) {
    PasswordField(
        label = "New password",
        value = password,
        visible = passwordVisible,
        error = validation.passwordError,
        enabled = !isBusy,
        onValueChange = onPasswordChange,
        onToggleVisibility = onTogglePassword,
    )
    PasswordField(
        label = "Confirm new password",
        value = confirmation,
        visible = passwordVisible,
        error = validation.confirmationError,
        enabled = !isBusy,
        onValueChange = onConfirmationChange,
        onToggleVisibility = onTogglePassword,
    )
    EvidriloPrimaryButton(
        label = "Save new password",
        onClick = onSubmit,
        enabled = !isBusy && validation.isValid,
    )
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    visible: Boolean,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleVisibility, enabled = enabled) {
                Text(if (visible) "Hide" else "Show")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun SignedInAccountPanel(
    account: AccountSummary,
    googleLinkOutcome: GoogleIdentityLinkOutcome?,
    onGoogleLink: () -> Unit,
    onCancelGoogleLink: () -> Unit,
    onSignOut: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    exportError: AccountUnavailableReason?,
    onDismissExportError: () -> Unit,
    isBusy: Boolean,
) {
    EvidriloTintPanel {
        Text("You are signed in", style = MaterialTheme.typography.titleMedium)
        Text(
            "This device is connected to your account. Your projects remain local unless you separately enable online sync.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    EvidriloTintPanel {
        Text("Google sign-in", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                account.googleLinked == true -> "Google is linked to this account. Your Evidrilo account and data stay under the same account ID."
                googleLinkOutcome == GoogleIdentityLinkOutcome.STARTED -> "Finish linking in your browser, then return here. This account remains active while you do that."
                googleLinkOutcome == GoogleIdentityLinkOutcome.CANCELLED -> "Google linking did not complete. Your current session remains active; if you approved Google, try again to refresh the link status."
                googleLinkOutcome == GoogleIdentityLinkOutcome.CONFLICT -> "This Google identity is already associated with an account. No accounts were merged or switched."
                googleLinkOutcome == GoogleIdentityLinkOutcome.SETUP_REQUIRED -> "Google identity linking is not enabled for this Supabase project. Your current account remains unchanged."
                googleLinkOutcome == GoogleIdentityLinkOutcome.FAILED -> "We could not verify the link result. Your existing session was preserved; try again to check the Google status."
                account.googleLinked == false -> "Google is not linked yet. Linking adds Google sign-in to this account without creating or merging accounts."
                else -> "The Google link status will be checked before linking. This will not replace your current account."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (account.googleLinked != true) {
        val linking = googleLinkOutcome == GoogleIdentityLinkOutcome.STARTED
        EvidriloSecondaryButton(
            label = if (linking) "Cancel Google linking" else "Link Google to this account",
            onClick = if (linking) onCancelGoogleLink else onGoogleLink,
            enabled = !isBusy,
        )
    }
    EvidriloSecondaryButton(label = "Export account data", onClick = onExport, enabled = !isBusy)
    exportError?.let { reason ->
        val exportPresentation = AccountSession.Unavailable(reason).toPresentation()
        EvidriloTintPanel {
            Text(exportPresentation.title, style = MaterialTheme.typography.titleMedium)
            Text(exportPresentation.body, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismissExportError, enabled = !isBusy) {
                Text("Dismiss")
            }
        }
    }
    EvidriloSecondaryButton(label = "Sign out", onClick = onSignOut, enabled = !isBusy)
    TextButton(onClick = onDelete, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
        Text("Delete account data", color = EvidriloColors.Error)
    }
}
