package dev.nextgen.mobile.storage

internal interface OnboardingStore {
    fun load(): LocalStorageReadResult<Boolean>

    fun complete(): LocalStorageWriteResult
}

internal class NoopOnboardingStore : OnboardingStore {
    override fun load(): LocalStorageReadResult<Boolean> = LocalStorageReadResult.Unavailable

    override fun complete(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}
