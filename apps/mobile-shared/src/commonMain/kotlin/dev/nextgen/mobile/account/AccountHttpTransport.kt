package dev.nextgen.mobile.account

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val MAX_ACCOUNT_HTTP_BODY_BYTES: Int = 128 * 1024

// Authenticated requests must never follow redirects. A redirect can change
// the origin and widen the destination that receives sensitive headers.
internal const val ACCOUNT_HTTP_REDIRECTS_ALLOWED: Boolean = false

internal data class AccountHttpResponse(
    val statusCode: Int,
    val body: String,
)

/** Small, bounded transport boundary; implementations must not log headers or bodies. */
internal interface AccountHttpTransport {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
    ): AccountHttpResponse
}

/**
 * Bridges callback-based platform HTTP APIs to structured concurrency.
 *
 * The returned cancellation action must abort the underlying platform request.
 * Late callbacks are deliberately ignored after the owning coroutine is gone.
 */
internal suspend fun <T> awaitCancellableRequest(
    start: ((Result<T>) -> Unit) -> (() -> Unit),
): T = suspendCancellableCoroutine { continuation ->
    var cancelRequest: (() -> Unit)? = null
    continuation.invokeOnCancellation {
        cancelRequest?.invoke()
    }
    val startedCancelRequest = start { result ->
        if (!continuation.isActive) return@start
        runCatching {
            result.fold(
                onSuccess = { value ->
                    continuation.resume(value) { _, _, _ -> }
                },
                onFailure = { exception ->
                    continuation.resumeWithException(exception)
                },
            )
        }
    }
    cancelRequest = startedCancelRequest
    if (continuation.isCancelled) startedCancelRequest()
}

internal fun validateAccountHttpRequest(url: String, body: String) {
    require(url.startsWith("https://", ignoreCase = true)) {
        "Auth transport requires HTTPS."
    }
    require(body.encodeToByteArray().size <= MAX_ACCOUNT_HTTP_BODY_BYTES) {
        "Auth request exceeded the bounded body limit."
    }
}
