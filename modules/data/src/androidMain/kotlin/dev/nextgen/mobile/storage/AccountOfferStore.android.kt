package dev.nextgen.mobile.storage

import android.content.Context

private const val PREFERENCES_NAME = "evidrilo_account_offer_v1"
private const val PRESENTED_KEY = "presented"

object AndroidAccountOfferStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun createStore(): AccountOfferStore =
        applicationContext?.let(::AndroidAccountOfferStore) ?: NoopAccountOfferStore()
}

actual fun createAccountOfferStore(): AccountOfferStore = AndroidAccountOfferStorage.createStore()

private class AndroidAccountOfferStore(context: Context) : AccountOfferStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): LocalStorageReadResult<Boolean> = runCatching {
        LocalStorageReadResult.Success(preferences.getBoolean(PRESENTED_KEY, false))
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun markPresented(): LocalStorageWriteResult = runCatching {
        if (preferences.edit().putBoolean(PRESENTED_KEY, true).commit()) {
            LocalStorageWriteResult.SAVED
        } else {
            LocalStorageWriteResult.FAILED
        }
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
