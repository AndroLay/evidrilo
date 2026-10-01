package dev.nextgen.mobile.account

import kotlinx.coroutines.suspendCancellableCoroutine
import dev.nextgen.mobile.network.DeviceConnectivity
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val MAX_ACCOUNT_HTTP_BODY_BYTES: Int = 128 * 1024

// Authenticated requests must never follow redirects. A redirect can change
// the origin and widen the destination that receives sensitive headers.
const val ACCOUNT_HTTP_REDIRECTS_ALLOWED: Boolean = false

data class AccountHttpResponse(
    val statusCode: Int,
    val body: String,
)

/** Small, bounded transport boundary; implementations must not log headers or bodies. */
interface AccountHttpTransport {
    /**
     * Latest device-network observation; it does not prove that an API or
     * provider is reachable. Implementations without an OS signal stay UNKNOWN.
     */
    val deviceConnectivity: DeviceConnectivity
        get() = DeviceConnectivity.UNKNOWN

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
suspend fun <T> awaitCancellableRequest(
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

fun validateAccountHttpRequest(url: String, body: String) {
    require(isAllowedApiBaseUrl(url)) {
        "Authenticated transport requires HTTPS or an explicit local development host."
    }
    require(body.encodeToByteArray().size <= MAX_ACCOUNT_HTTP_BODY_BYTES) {
        "Auth request exceeded the bounded body limit."
    }
}

/** Provider work can take 60 seconds; preserve short waits for ordinary account requests. */
internal fun accountHttpReadTimeoutMillis(method: String, url: String): Int {
    val path = url.substringBefore('?').substringBefore('#').substringAfter("://").substringAfter('/', "")
    val waitsForProvider = method == "POST" && (
        path == "v1/ai/assist" || path == "v1/ai/conversations" ||
            (path.startsWith("v1/ai/conversations/") && path.endsWith("/turns")) ||
            path == "v1/project-ai/scaffold" || path == "v1/project-ai/stage-assist"
        )
    return if (waitsForProvider) 75_000 else 15_000
}
