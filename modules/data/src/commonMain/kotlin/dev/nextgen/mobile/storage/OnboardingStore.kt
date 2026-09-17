package dev.nextgen.mobile.storage

interface OnboardingStore {
    fun load(): LocalStorageReadResult<Boolean>

    fun complete(): LocalStorageWriteResult
}

class NoopOnboardingStore : OnboardingStore {
    override fun load(): LocalStorageReadResult<Boolean> = LocalStorageReadResult.Unavailable

    override fun complete(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}
