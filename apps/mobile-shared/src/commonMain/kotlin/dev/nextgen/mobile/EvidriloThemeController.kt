package dev.nextgen.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Session-scoped holder for the user's theme choice. Kept deliberately small:
 * the app reads [mode] to drive [EvidriloTheme], and Settings updates it. The
 * default follows the device (SYSTEM). Durable persistence across launches is a
 * separate storage concern and can be layered on without changing this API.
 */
internal class EvidriloThemeController {
    var mode: EvidriloThemeMode by mutableStateOf(EvidriloThemeMode.SYSTEM)
        private set

    fun select(next: EvidriloThemeMode) {
        mode = next
    }
}

internal fun evidriloThemeModeLabel(mode: EvidriloThemeMode): String = when (mode) {
    EvidriloThemeMode.SYSTEM -> "Match device"
    EvidriloThemeMode.LIGHT -> "Light"
    EvidriloThemeMode.DARK -> "Dark"
}

internal fun evidriloNextThemeMode(mode: EvidriloThemeMode): EvidriloThemeMode = when (mode) {
    EvidriloThemeMode.SYSTEM -> EvidriloThemeMode.LIGHT
    EvidriloThemeMode.LIGHT -> EvidriloThemeMode.DARK
    EvidriloThemeMode.DARK -> EvidriloThemeMode.SYSTEM
}
