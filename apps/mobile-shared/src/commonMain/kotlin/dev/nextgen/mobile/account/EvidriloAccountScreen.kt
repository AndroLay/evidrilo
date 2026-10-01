package dev.nextgen.mobile.account

import dev.nextgen.mobile.uiText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import dev.nextgen.mobile.EvidriloLogoMark
import dev.nextgen.mobile.EvidriloExplanation
import dev.nextgen.mobile.EvidriloGoogleSignInButton
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
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.nextgen.mobile.EvidriloBackGesture
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
    accountRestoreComplete: Boolean,
    accountConfigured: Boolean,
    googleConfigured: Boolean,
    appleConfigured: Boolean,
    accountBoundFeaturesEnabled: Boolean,
    exportJson: String?,
    exportError: AccountUnavailableReason?,
    persistenceNotice: LocalStorageNotice? = null,
    onBack: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onResetPassword: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onGoogleLink: () -> Unit,
    onCancelGoogleLink: () -> Unit,
    onAppleLink: () -> Unit,
    onCancelAppleLink: () -> Unit,
    onCancelOAuth: () -> Unit,
    onUpdatePassword: (String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onExportAccount: () -> Unit,
    onDismissExport: () -> Unit,
) {
    if (!accountRestoreComplete) {
        EvidriloContentColumn {
            EvidriloBackGesture(label = "Back", onClick = onBack)
            Text("Checking saved account", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Your local projects remain available. We are securely checking whether this device already has an account session.",
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
    var confirmAppleLink by remember { mutableStateOf(false) }
    LaunchedEffect(session) {
        val verifiedEmail = (session as? AccountSession.SignedIn)
            ?.account
            ?.takeIf { it.emailVerified }
            ?.email
        if (!verifiedEmail.isNullOrBlank()) email = verifiedEmail
    }
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
    val showSignedOutAuthIntro = session == AccountSession.SignedOut && showAccountAuthForm

    EvidriloContentColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EvidriloBackGesture(label = "Back", onClick = onBack)
        if (showSignedOutAuthIntro) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EvidriloLogoMark(size = 48.dp)
                Text(accountAuthHeading(mode), style = MaterialTheme.typography.headlineMedium)
            }
            if (mode != AccountAuthMode.RESET_PASSWORD) {
                Text(
                    "Sign in for AI & Pro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.Slate,
                )
            }
        } else {
            Text(presentation.title, style = MaterialTheme.typography.headlineLarge)
            Text(presentation.body, style = MaterialTheme.typography.bodyLarge)
        }
        persistenceNotice?.takeIf { it.isError }?.let { notice -> EvidriloRecoveryNotice(notice) }

        when {
            session is AccountSession.SignedIn -> {
                SignedInAccountPanel(
                    account = session.account,
                    googleLinkOutcome = session.googleLinkOutcome,
                    appleLinkOutcome = session.appleLinkOutcome,
                    googleConfigured = googleConfigured,
                    appleConfigured = appleConfigured,
                    accountBoundFeaturesEnabled = accountBoundFeaturesEnabled,
                    onGoogleLink = onGoogleLink,
                    onCancelGoogleLink = onCancelGoogleLink,
                    onAppleLink = { confirmAppleLink = true },
                    onCancelAppleLink = onCancelAppleLink,
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
                        "Finish sign-in in your browser, then return to Evidrilo.",
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
                        if (googleConfigured) {
                            EvidriloGoogleSignInButton(onGoogleSignIn, enabled = !isBusy)
                        }
                        if (appleConfigured) {
                            EvidriloSecondaryButton("Continue with Apple", onAppleSignIn, enabled = !isBusy)
                        }
                        if (googleConfigured || appleConfigured) {
                            Text("Or use email", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Slate)
                        }
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
                        if (googleConfigured || appleConfigured) {
                            EvidriloExplanation("Sign-in methods", "Have an account already? Sign in with that method, then link Google or Apple from Account. Accounts are never merged.")
                        }
                    }
                }
            }
        }

        if (showSignedOutAuthIntro && mode != AccountAuthMode.RESET_PASSWORD) {
            EvidriloExplanation("Online features", "AI needs internet, a verified account, consent, credits and an available service. Pro requires confirmed access. Signing in never uploads your projects. Local projects and Free practice work without an account.")
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Keep working locally", color = EvidriloColors.Cobalt) }
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
                TextButton(onClick = { confirmDeletion = false }, enabled = !isBusy) { Text(uiText("Cancel")) }
            },
        )
    }

    if (confirmAppleLink && session is AccountSession.SignedIn) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) confirmAppleLink = false },
            title = { Text("Link Apple to this account?") },
            text = {
                Column {
                    Text(
                        "Confirm you are signed into the Evidrilo account you want to keep.",
                    )
                    Text(
                        session.account.email?.let { "Current account email · $it" }
                            ?: "The saved session does not include an email address. Apple will be linked to the currently signed-in Evidrilo account.",
                    )
                    Text(
                        "Apple may provide a private relay address. It will not be used to choose or switch Evidrilo accounts.",
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAppleLink = false
                        onAppleLink()
                    },
                    enabled = !isBusy,
                ) { Text("Continue to Apple") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAppleLink = false }, enabled = !isBusy) {
                    Text(uiText("Cancel"))
                }
            },
        )
    }

    if (exportJson != null) {
        AlertDialog(
            onDismissRequest = onDismissExport,
            title = { Text(uiText("Account export")) },
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
                TextButton(onClick = onDismissExport) { Text(uiText("Close")) }
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
        shape = RoundedCornerShape(16.dp),
        enabled = !isBusy,
        isError = email.isNotBlank() && validation.emailError != null,
        supportingText = validation.emailError?.takeIf { email.isNotBlank() }?.let { error -> { Text(error) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
    )
    PasswordField(
        label = "Password",
        value = password,
        visible = passwordVisible,
        error = validation.passwordError?.takeIf { password.isNotEmpty() },
        enabled = !isBusy,
        onValueChange = onPasswordChange,
        onToggleVisibility = onTogglePassword,
    )
    if (!isSignIn) {
        PasswordField(
            label = "Confirm password",
            value = confirmation,
            visible = passwordVisible,
            error = validation.confirmationError?.takeIf { confirmation.isNotEmpty() },
            enabled = !isBusy,
            onValueChange = onConfirmationChange,
            onToggleVisibility = onTogglePassword,
        )
    }
    EvidriloPrimaryButton(
        label = if (isSignIn) "Sign in" else "Create account",
        onClick = onSubmit,
        enabled = !isBusy && accountConfigured && validation.isValid,
    )
    if (isSignIn) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onResetPassword, enabled = !isBusy, modifier = Modifier.weight(1f)) { Text(uiText("Forgot password?")) }
            TextButton(onClick = onToggleMode, enabled = !isBusy, modifier = Modifier.weight(1f)) { Text(uiText("Create account")) }
        }
    } else {
        TextButton(onClick = onToggleMode, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) { Text("I already have an account") }
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
        Text("Sign in instead")
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
        shape = RoundedCornerShape(16.dp),
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
    appleLinkOutcome: IdentityLinkOutcome?,
    googleConfigured: Boolean,
    appleConfigured: Boolean,
    accountBoundFeaturesEnabled: Boolean,
    onGoogleLink: () -> Unit,
    onCancelGoogleLink: () -> Unit,
    onAppleLink: () -> Unit,
    onCancelAppleLink: () -> Unit,
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
            "Your account can be used for online AI and Pro, subject to service and access checks. Projects stay on this device; signing in does not upload them.",
            style = MaterialTheme.typography.bodyMedium,
        )
        account.email?.let { email ->
            Text(
                if (account.emailVerified) "Account email · $email · verified" else "Account email · $email",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (googleConfigured) EvidriloTintPanel {
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
    if (googleConfigured && account.googleLinked != true) {
        val linking = googleLinkOutcome == GoogleIdentityLinkOutcome.STARTED
        EvidriloSecondaryButton(
            label = if (linking) "Cancel Google linking" else "Link Google to this account",
            onClick = if (linking) onCancelGoogleLink else onGoogleLink,
            enabled = !isBusy,
        )
    }
    if (appleConfigured) EvidriloTintPanel {
        Text("Apple sign-in", style = MaterialTheme.typography.titleMedium)
        Text(
            when {
                account.appleLinked == true -> "Apple is linked to this same Evidrilo account. Its relay email, if used, does not replace the verified account email above."
                appleLinkOutcome == IdentityLinkOutcome.STARTED -> "Finish linking in your browser, then return here. Your current Evidrilo account stays active."
                appleLinkOutcome == IdentityLinkOutcome.CANCELLED -> "Apple linking did not complete. Your current account remains active; you can retry when ready."
                appleLinkOutcome == IdentityLinkOutcome.CONFLICT -> "This Apple identity is already associated with an account. No accounts were merged or switched. Sign in to that account before linking."
                appleLinkOutcome == IdentityLinkOutcome.SETUP_REQUIRED -> "Apple identity linking is not enabled for this auth project. Your current account remains unchanged."
                appleLinkOutcome == IdentityLinkOutcome.FAILED -> "We could not verify the Apple link. Your existing account and email were preserved."
                account.appleLinked == false -> "Link Apple as another sign-in method for this same Evidrilo account. Linking never merges accounts."
                else -> "We will verify whether Apple is already linked before changing sign-in methods."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (appleConfigured && account.appleLinked != true) {
        val linking = appleLinkOutcome == IdentityLinkOutcome.STARTED
        EvidriloSecondaryButton(
            label = if (linking) "Cancel Apple linking" else "Link Apple to this account",
            onClick = if (linking) onCancelAppleLink else onAppleLink,
            enabled = !isBusy,
        )
    }
    if (accountBoundFeaturesEnabled) {
        EvidriloSecondaryButton(label = "Export account data", onClick = onExport, enabled = !isBusy)
    }
    exportError?.takeIf { accountBoundFeaturesEnabled }?.let { reason ->
        val exportPresentation = AccountSession.Unavailable(reason).toPresentation()
        EvidriloTintPanel {
            Text(exportPresentation.title, style = MaterialTheme.typography.titleMedium)
            Text(exportPresentation.body, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismissExportError, enabled = !isBusy) {
                Text(uiText("Dismiss"))
            }
        }
    }
    EvidriloSecondaryButton(label = "Sign out", onClick = onSignOut, enabled = !isBusy)
    if (accountBoundFeaturesEnabled) {
        TextButton(onClick = onDelete, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text("Delete account data", color = EvidriloColors.Error)
        }
    }
}
