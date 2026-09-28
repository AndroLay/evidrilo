package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.onboarding.GetStartedStatus

interface OnboardingStore {
    fun load(): LocalStorageReadResult<GetStartedStatus>

    fun skip(): LocalStorageWriteResult

    fun complete(): LocalStorageWriteResult
}

/** Keep UI state truthful: a requested tour status is persisted only after a successful write. */
fun onboardingStatusAfterWrite(
    current: GetStartedStatus,
    requested: GetStartedStatus,
    result: LocalStorageWriteResult,
): GetStartedStatus = if (result == LocalStorageWriteResult.SAVED) requested else current

class NoopOnboardingStore : OnboardingStore {
    override fun load(): LocalStorageReadResult<GetStartedStatus> = LocalStorageReadResult.Unavailable

    override fun skip(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE

    override fun complete(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}
