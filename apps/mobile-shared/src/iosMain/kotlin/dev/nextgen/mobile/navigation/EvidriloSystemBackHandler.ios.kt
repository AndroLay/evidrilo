package dev.nextgen.mobile.navigation

import androidx.compose.runtime.Composable

@Composable
internal actual fun EvidriloSystemBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) { RegisterEvidriloBackGesture(enabled, onBack) }

internal actual val evidriloUsesEdgeBackGesture: Boolean = true
