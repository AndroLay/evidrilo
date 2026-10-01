package dev.nextgen.mobile
import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
@Composable internal actual fun rememberLanguageSettingsStore(): LanguageSettingsStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { object : LanguageSettingsStore {
        private val preferences = context.getSharedPreferences("evidrilo_workspace_preferences_v1", Context.MODE_PRIVATE)
        override fun load(): String? = runCatching { preferences.getString("language", null) }.getOrNull()
        override fun save(tag: String): Boolean = runCatching { preferences.edit().putString("language", tag).commit() }.getOrDefault(false)
    } }
}
