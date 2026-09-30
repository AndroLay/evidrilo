package dev.nextgen.mobile.projectcatalog

interface ProjectAiInstallationIdStore {
    /** Returns a persisted pseudonymous installation ID, or null when local storage is unavailable. */
    fun getOrCreate(): String?
}

class UnavailableProjectAiInstallationIdStore : ProjectAiInstallationIdStore {
    override fun getOrCreate(): String? = null
}

expect fun createProjectAiInstallationIdStore(): ProjectAiInstallationIdStore
