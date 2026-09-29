package dev.nextgen.mobile.account

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.nextgen.mobile.EvidriloContentColumn
import dev.nextgen.mobile.EvidriloPrimaryButton
import dev.nextgen.mobile.EvidriloSecondaryButton
import dev.nextgen.mobile.EvidriloRecoveryNotice
import dev.nextgen.mobile.EvidriloTintPanel
import dev.nextgen.mobile.storage.LocalStorageNotice

@Composable
internal fun EvidriloAccountRequiredGate(
    accountConfigured: Boolean,
    revenueCatProEnabled: Boolean,
    onSignIn: () -> Unit,
    onOpenLocalProjects: () -> Unit,
    onOpenSupport: () -> Unit,
    storageNotice: LocalStorageNotice? = null,
) {
    if (TEMPORARY_GUEST_MODE_ENABLED && !revenueCatProEnabled) {
        EvidriloContentColumn {
            Text("This feature is paused", style = MaterialTheme.typography.displayLarge)
            Text(
                "Projects, the catalog, case work, and history are available in local guest mode. Account, Pro, and server AI features are temporarily unavailable.",
                style = MaterialTheme.typography.bodyLarge,
            )
            storageNotice?.takeIf { it.isError }?.let { notice -> EvidriloRecoveryNotice(notice) }
            EvidriloPrimaryButton(label = "Continue to local projects", onClick = onOpenLocalProjects)
            EvidriloSecondaryButton(label = "Contact support", onClick = onOpenSupport)
        }
        return
    }

    EvidriloContentColumn {
        Text(
            if (!accountConfigured) "Sign-in unavailable"
            else if (TEMPORARY_GUEST_MODE_ENABLED) "Sign in to access Evidrilo Pro"
            else "Sign in for this feature",
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            if (accountConfigured) {
                if (TEMPORARY_GUEST_MODE_ENABLED) {
                    "Pro requires an account and a confirmed RevenueCat entitlement. Your free projects and case work remain available locally without signing in."
                } else {
                    "This feature needs an account. Your local projects remain available without signing in."
                }
            } else {
                "Account access is not available in this build. You can keep working with local projects or contact support."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        storageNotice?.takeIf { it.isError }?.let { notice -> EvidriloRecoveryNotice(notice) }
        if (accountConfigured) {
            EvidriloPrimaryButton(
                label = if (TEMPORARY_GUEST_MODE_ENABLED) "Sign in to continue" else "Sign in or create a free account",
                onClick = onSignIn,
            )
            EvidriloSecondaryButton(label = "Continue to local projects", onClick = onOpenLocalProjects)
            EvidriloTintPanel {
                Text(
                    "Signing in does not upload projects or turn on cloud sync.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            EvidriloPrimaryButton(label = "Continue to local projects", onClick = onOpenLocalProjects)
        }
        EvidriloSecondaryButton(label = "Contact support", onClick = onOpenSupport)
    }
}
