package dev.nextgen.mobile.storage

import platform.Foundation.NSUserDefaults
import dev.nextgen.mobile.domain.onboarding.GetStartedStatus

private const val STATUS_KEY = "evidrilo.onboarding.status.v2"
private const val LEGACY_COMPLETED_KEY = "evidrilo.onboarding.completed.v1"

actual fun createOnboardingStore(): OnboardingStore = IosOnboardingStore()

private class IosOnboardingStore : OnboardingStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<GetStartedStatus> = runCatching {
        val status = GetStartedStatus.fromStorage(
            value = defaults.stringForKey(STATUS_KEY),
            legacyCompleted = defaults.boolForKey(LEGACY_COMPLETED_KEY),
        )
        if (status == null) LocalStorageReadResult.Failed else LocalStorageReadResult.Success(status)
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun skip(): LocalStorageWriteResult = persist(GetStartedStatus.SKIPPED)

    override fun complete(): LocalStorageWriteResult = persist(GetStartedStatus.COMPLETED)

    private fun persist(status: GetStartedStatus): LocalStorageWriteResult = runCatching {
        defaults.setObject(status.storageValue, forKey = STATUS_KEY)
        val saved = defaults.stringForKey(STATUS_KEY) == status.storageValue
        if (saved) defaults.removeObjectForKey(LEGACY_COMPLETED_KEY)
        if (saved) LocalStorageWriteResult.SAVED else LocalStorageWriteResult.FAILED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
