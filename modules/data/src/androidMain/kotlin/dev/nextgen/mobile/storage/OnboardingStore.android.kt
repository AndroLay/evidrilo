package dev.nextgen.mobile.storage

import android.content.Context
import dev.nextgen.mobile.domain.onboarding.GetStartedStatus

private const val PREFERENCES_NAME = "evidrilo_onboarding_v1"
private const val STATUS_KEY = "get_started_status_v2"
private const val LEGACY_COMPLETED_KEY = "completed"

object AndroidOnboardingStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun createStore(): OnboardingStore =
        applicationContext?.let(::AndroidOnboardingStore) ?: NoopOnboardingStore()
}

actual fun createOnboardingStore(): OnboardingStore =
    AndroidOnboardingStorage.createStore()

private class AndroidOnboardingStore(
    context: Context,
) : OnboardingStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<GetStartedStatus> = runCatching {
        val status = GetStartedStatus.fromStorage(
            value = preferences.getString(STATUS_KEY, null),
            legacyCompleted = preferences.getBoolean(LEGACY_COMPLETED_KEY, false),
        )
        if (status == null) LocalStorageReadResult.Failed else LocalStorageReadResult.Success(status)
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun skip(): LocalStorageWriteResult = persist(GetStartedStatus.SKIPPED)

    override fun complete(): LocalStorageWriteResult = persist(GetStartedStatus.COMPLETED)

    private fun persist(status: GetStartedStatus): LocalStorageWriteResult = runCatching {
        if (preferences.edit()
                .putString(STATUS_KEY, status.storageValue)
                .remove(LEGACY_COMPLETED_KEY)
                .commit()
        ) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
