package dev.nextgen.mobile.storage

import platform.Foundation.NSUserDefaults

private const val COMPLETED_KEY = "evidrilo.onboarding.completed.v1"

internal actual fun createOnboardingStore(): OnboardingStore = IosOnboardingStore()

private class IosOnboardingStore : OnboardingStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<Boolean> = runCatching {
        LocalStorageReadResult.Success(defaults.boolForKey(COMPLETED_KEY))
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun complete(): LocalStorageWriteResult = runCatching {
        defaults.setBool(true, forKey = COMPLETED_KEY)
        LocalStorageWriteResult.SAVED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
