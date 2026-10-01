package dev.nextgen.mobile

import androidx.compose.runtime.*

internal interface LanguageSettingsStore {
    fun load(): String?
    fun save(tag: String): Boolean
}

@Composable internal expect fun rememberLanguageSettingsStore(): LanguageSettingsStore

internal class EvidriloLanguageController(private val store: LanguageSettingsStore) {
    var language by mutableStateOf(EvidriloLanguage.entries.firstOrNull { it.tag == store.load() } ?: EvidriloLanguage.ENGLISH)
        private set
    var saveFailed by mutableStateOf(false)
        private set
    fun select(next: EvidriloLanguage) {
        if (store.save(next.tag)) { language = next; saveFailed = false } else saveFailed = true
    }
}
