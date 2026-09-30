package dev.nextgen.mobile.projectcatalog

actual fun createProjectAiInstallationIdStore(): ProjectAiInstallationIdStore =
    UnavailableProjectAiInstallationIdStore()
