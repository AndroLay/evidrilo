package dev.nextgen.mobile.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun EvidriloAccountBenefitPrompt(
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep your account optional") },
        text = {
            Text(
                "You can keep using Evidrilo offline without signing in. An optional account can restore your verified account identity, associate an eligible subscription, and enable consented progress metadata sync when available. Your draft stays on this device unless you explicitly enable sync.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onSignIn) {
                Text("Sign in or sign up")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe later")
            }
        },
    )
}
