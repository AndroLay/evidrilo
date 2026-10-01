package dev.nextgen.mobile
import androidx.compose.runtime.*
import java.util.prefs.Preferences
@Composable internal actual fun rememberLanguageSettingsStore(): LanguageSettingsStore = remember { object : LanguageSettingsStore {
    private val preferences = runCatching { Preferences.userRoot().node("dev.nextgen.mobile/workspace") }.getOrNull()
    override fun load(): String? = runCatching { preferences?.get("language", null) }.getOrNull()
    override fun save(tag: String): Boolean = runCatching { val p=preferences ?: return false; p.put("language",tag); p.flush(); true }.getOrDefault(false)
} }
