package dev.nextgen.mobile.storage

import platform.Foundation.NSUserDefaults

private const val PRESENTED_KEY = "evidrilo.account.offer.presented.v1"

actual fun createAccountOfferStore(): AccountOfferStore = IosAccountOfferStore()

private class IosAccountOfferStore : AccountOfferStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): LocalStorageReadResult<Boolean> = runCatching {
        LocalStorageReadResult.Success(defaults.boolForKey(PRESENTED_KEY))
    }.getOrElse { LocalStorageReadResult.Failed }

    override fun markPresented(): LocalStorageWriteResult = runCatching {
        defaults.setBool(true, forKey = PRESENTED_KEY)
        LocalStorageWriteResult.SAVED
    }.getOrElse { LocalStorageWriteResult.FAILED }
}
