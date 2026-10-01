package dev.nextgen.mobile
import androidx.compose.runtime.*
import platform.Foundation.NSUserDefaults
@Composable internal actual fun rememberLanguageSettingsStore(): LanguageSettingsStore = remember { object : LanguageSettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val key = "evidrilo.workspace.language.v1"
    override fun load(): String? = runCatching { defaults.stringForKey(key) }.getOrNull()
    override fun save(tag: String): Boolean = runCatching { defaults.setObject(tag, forKey=key); defaults.stringForKey(key)==tag }.getOrDefault(false)
} }
