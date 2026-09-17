package dev.nextgen.mobile.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AccountHttpTransportTest {
    @Test
    fun request_validation_requires_https() {
        assertFailsWith<IllegalArgumentException> {
            validateAccountHttpRequest("http://example.test/auth", "{}")
        }
    }

    @Test
    fun request_validation_bounds_the_body_before_platform_io() {
        assertFailsWith<IllegalArgumentException> {
            validateAccountHttpRequest(
                "https://example.test/auth",
                "x".repeat(MAX_ACCOUNT_HTTP_BODY_BYTES + 1),
            )
        }
    }

    @Test
    fun account_transport_does_not_follow_redirects_with_authenticated_headers() {
        assertFalse(ACCOUNT_HTTP_REDIRECTS_ALLOWED)
    }

    @Test
    fun cancellable_request_cancels_the_platform_operation_and_ignores_late_completion() {
        var complete: ((Result<Int>) -> Unit)? = null
        var cancellationCount = 0
        var observedValue: Int? = null
        val job = Job()

        CoroutineScope(job).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                observedValue = awaitCancellableRequest { callback ->
                    complete = callback
                    { cancellationCount++ }
                }
            } catch (_: CancellationException) {
                // Expected when the owner of the request leaves the screen.
            }
        }

        assertNotNull(complete)
        job.cancel()
        complete?.invoke(Result.success(7))

        assertEquals(1, cancellationCount)
        assertNull(observedValue)
    }
}
