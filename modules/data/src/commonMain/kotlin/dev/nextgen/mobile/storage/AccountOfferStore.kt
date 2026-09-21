package dev.nextgen.mobile.storage

/** Persists only whether the optional account invitation has already appeared. */
interface AccountOfferStore {
    fun load(): LocalStorageReadResult<Boolean>

    fun markPresented(): LocalStorageWriteResult
}

class NoopAccountOfferStore : AccountOfferStore {
    override fun load(): LocalStorageReadResult<Boolean> = LocalStorageReadResult.Unavailable

    override fun markPresented(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}

expect fun createAccountOfferStore(): AccountOfferStore
