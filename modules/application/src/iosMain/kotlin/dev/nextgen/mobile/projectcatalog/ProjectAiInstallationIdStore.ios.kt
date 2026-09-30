package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.analytics.newAnalyticsEventId
import platform.Foundation.NSUserDefaults

private const val INSTALLATION_ID_KEY = "evidrilo.project_ai.installation_id.v1"
private val installationIdPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
)

actual fun createProjectAiInstallationIdStore(): ProjectAiInstallationIdStore = IosProjectAiInstallationIdStore()

private class IosProjectAiInstallationIdStore : ProjectAiInstallationIdStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getOrCreate(): String? = runCatching {
        defaults.stringForKey(INSTALLATION_ID_KEY)
            ?.takeIf(installationIdPattern::matches)
            ?: newAnalyticsEventId().also { generated ->
                defaults.setObject(generated, forKey = INSTALLATION_ID_KEY)
                check(defaults.stringForKey(INSTALLATION_ID_KEY) == generated)
            }
    }.getOrNull()
}
