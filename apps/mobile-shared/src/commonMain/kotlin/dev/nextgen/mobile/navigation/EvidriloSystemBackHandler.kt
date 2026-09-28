package dev.nextgen.mobile.navigation

import androidx.compose.runtime.Composable

@Composable
internal expect fun EvidriloSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
