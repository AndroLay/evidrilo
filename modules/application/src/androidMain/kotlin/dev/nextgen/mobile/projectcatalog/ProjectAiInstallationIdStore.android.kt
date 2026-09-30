package dev.nextgen.mobile.projectcatalog

import android.content.Context
import dev.nextgen.mobile.analytics.newAnalyticsEventId

private const val PREFERENCES_NAME = "evidrilo_project_ai_installation_v1"
private const val INSTALLATION_ID_KEY = "installation_id"
private val installationIdPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
)

object AndroidProjectAiInstallationStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun createStore(): ProjectAiInstallationIdStore =
        applicationContext?.let(::AndroidProjectAiInstallationIdStore)
            ?: UnavailableProjectAiInstallationIdStore()
}

actual fun createProjectAiInstallationIdStore(): ProjectAiInstallationIdStore =
    AndroidProjectAiInstallationStorage.createStore()

private class AndroidProjectAiInstallationIdStore(context: Context) : ProjectAiInstallationIdStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun getOrCreate(): String? = runCatching {
        preferences.getString(INSTALLATION_ID_KEY, null)
            ?.takeIf(installationIdPattern::matches)
            ?: newAnalyticsEventId().also { generated ->
                check(preferences.edit().putString(INSTALLATION_ID_KEY, generated).commit())
            }
    }.getOrNull()
}
