package dev.nextgen.mobile.storage

import android.content.Context

private const val PREFERENCES_NAME = "evidrilo_onboarding_v1"
private const val COMPLETED_KEY = "completed"

object AndroidOnboardingStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createStore(): OnboardingStore =
        applicationContext?.let(::AndroidOnboardingStore) ?: NoopOnboardingStore()
}

internal actual fun createOnboardingStore(): OnboardingStore =
    AndroidOnboardingStorage.createStore()

private class AndroidOnboardingStore(
    context: Context,
) : OnboardingStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<Boolean> = runCatching {
        LocalStorageReadResult.Success(preferences.getBoolean(COMPLETED_KEY, false))
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun complete(): LocalStorageWriteResult = runCatching {
        if (preferences.edit().putBoolean(COMPLETED_KEY, true).commit()) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
