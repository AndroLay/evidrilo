package dev.nextgen.mobile.account

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun <T> runSuspendTest(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(value: Result<T>) {
            result = value
        }
    })
    return checkNotNull(result).getOrThrow()
}
