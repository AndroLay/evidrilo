package dev.nextgen.mobile.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

internal actual fun createRevenueCatUiAvailability(): RevenueCatUiAvailability =
    RevenueCatUiAvailability(canPresent = false)

@Composable
internal actual fun RevenueCatManagedPaywall(onDismiss: () -> Unit) {
    ManagedUiUnavailable(onDismiss)
}

@Composable
internal actual fun RevenueCatCustomerCenter(onDismiss: () -> Unit) {
    ManagedUiUnavailable(onDismiss)
}

@Composable
private fun ManagedUiUnavailable(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Managed RevenueCat billing is available on Android and iOS builds.")
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }
}
