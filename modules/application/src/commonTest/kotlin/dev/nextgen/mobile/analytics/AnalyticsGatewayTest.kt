package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import dev.nextgen.mobile.network.DeviceConnectivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnalyticsGatewayTest {
    @Test
    fun guest_only_mode_never_sends_analytics_even_if_old_consent_exists() {
        assertFalse(analyticsTransmissionAllowed(AnalyticsConsent.GRANTED, guestOnlyMode = true))
        assertTrue(analyticsTransmissionAllowed(AnalyticsConsent.GRANTED, guestOnlyMode = false))
        assertFalse(analyticsTransmissionAllowed(AnalyticsConsent.NOT_GRANTED, guestOnlyMode = false))
    }

    @Test
    fun event_requires_explicit_consent_and_sends_only_minimal_properties() {
        val transport = FakeAnalyticsTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.analytics-event-result","version":"1","outcome":"accepted","requestId":"req-analytics-001"}
                """.trimIndent(),
            ),
        )
        val gateway = gateway(transport)

        val result = runSuspendTest {
            gateway.send(validEvent(), AnalyticsConsent.GRANTED)
        }

        val sent = assertIs<AnalyticsGatewayResult.Sent>(result)
        assertEquals("accepted", sent.outcome)
        assertEquals("Bearer access-token", transport.requests.single().headers["Authorization"])
        assertEquals(validEvent().clientEventId, transport.requests.single().headers["Idempotency-Key"])
        assertTrue(transport.requests.single().body.contains("attempt_completed"))
        assertFalse(transport.requests.single().body.contains("claimText"))
        assertFalse(transport.requests.single().body.contains("rawDraftText"))

        val deniedTransport = FakeAnalyticsTransport(AccountHttpResponse(200, "{}"))
        val denied = runSuspendTest {
            gateway(deniedTransport).send(validEvent(), AnalyticsConsent.NOT_GRANTED)
        }
        assertEquals("CONSENT_REQUIRED", assertIs<AnalyticsGatewayResult.Failed>(denied).code)
        assertTrue(deniedTransport.requests.isEmpty())
    }

    @Test
    fun event_validation_rejects_missing_properties_before_network_access() {
        val transport = FakeAnalyticsTransport(AccountHttpResponse(200, "{}"))
        val gateway = gateway(transport)

        val invalid = runSuspendTest {
            gateway.send(
                AnalyticsEvent(
                    clientEventId = validEvent().clientEventId,
                    name = AnalyticsEventName.ATTEMPT_COMPLETED,
                    occurredAt = "2026-09-13T10:00:00Z",
                    properties = AnalyticsEventProperties(outcome = "PASS"),
                ),
                AnalyticsConsent.GRANTED,
            )
        }

        assertEquals("INVALID_ANALYTICS_EVENT", assertIs<AnalyticsGatewayResult.Failed>(invalid).code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun event_rejects_an_unverified_session_before_network_access() {
        val transport = FakeAnalyticsTransport(AccountHttpResponse(200, "{}"))
        val result = runSuspendTest {
            gateway(transport, verified = false).send(validEvent(), AnalyticsConsent.GRANTED)
        }

        val deferred = assertIs<AnalyticsGatewayResult.Deferred>(result)
        assertEquals(AnalyticsDeferralReason.AUTH_REQUIRED, deferred.reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun malformed_event_timestamp_is_rejected_before_network_access() {
        val transport = FakeAnalyticsTransport(AccountHttpResponse(200, "{}"))
        val event = validEvent().copy(occurredAt = "2026-09-13T10:00:00Z|raw-draft")

        val result = runSuspendTest { gateway(transport).send(event, AnalyticsConsent.GRANTED) }

        assertEquals("INVALID_ANALYTICS_EVENT", assertIs<AnalyticsGatewayResult.Failed>(result).code)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun transient_failures_are_retryable_but_malformed_success_is_not_accepted() {
        val transient = runSuspendTest {
            gateway(FakeAnalyticsTransport(AccountHttpResponse(503, "{}")))
                .send(validEvent(), AnalyticsConsent.GRANTED)
        }
        assertTrue(assertIs<AnalyticsGatewayResult.Failed>(transient).retryable)

        val malformed = runSuspendTest {
            gateway(FakeAnalyticsTransport(AccountHttpResponse(200, "{}")))
                .send(validEvent(), AnalyticsConsent.GRANTED)
        }
        assertFalse(assertIs<AnalyticsGatewayResult.Failed>(malformed).retryable)
        assertEquals("INVALID_ANALYTICS_RESPONSE", assertIs<AnalyticsGatewayResult.Failed>(malformed).code)
    }

    @Test
    fun lost_analytics_response_is_unknown_and_retries_with_the_same_event_identity() {
        val transport = FailingAnalyticsTransport()
        val event = validEvent()
        val result = runSuspendTest {
            gateway(transport).sendWithRetry(event, AnalyticsConsent.GRANTED) { }
        }
        val failure = assertIs<AnalyticsGatewayResult.Failed>(result)

        assertEquals("ANALYTICS_OUTCOME_UNKNOWN", failure.code)
        assertTrue(failure.outcomeUnknown)
        assertTrue(failure.reconciliationRequired)
        assertTrue(failure.sameIntentReplayAllowed)
        assertEquals(event.clientEventId, failure.idempotencyKey)
        assertEquals(3, transport.requests.size)
        assertTrue(transport.requests.all { it["Idempotency-Key"] == event.clientEventId })
    }

    private fun validEvent() = AnalyticsEvent(
        clientEventId = "123e4567-e89b-42d3-a456-426614174000",
        name = AnalyticsEventName.ATTEMPT_COMPLETED,
        occurredAt = "2026-09-13T10:00:00Z",
        properties = AnalyticsEventProperties(
            attemptId = "123e4567-e89b-42d3-a456-426614174001",
            caseVersionId = "M0_T2:1",
            outcome = "PASS",
        ),
    )

    private fun gateway(
        transport: AccountHttpTransport,
        verified: Boolean = true,
    ): AnalyticsGateway = AnalyticsGateway(
        configuration = AnalyticsClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryAnalyticsSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174002", verified),
                SecureSessionMaterial("access-token", 200),
            ),
        ),
        nowEpochSeconds = { 100 },
    )
}

private data class AnalyticsRequest(
    val headers: Map<String, String>,
    val body: String,
)

private class FakeAnalyticsTransport(
    private val response: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<AnalyticsRequest>()

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += AnalyticsRequest(headers, body)
        return response
    }
}

private class FailingAnalyticsTransport : AccountHttpTransport {
    override val deviceConnectivity = DeviceConnectivity.ONLINE
    val requests = mutableListOf<Map<String, String>>()

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += headers
        error("response lost")
    }
}

private class MemoryAnalyticsSecureStore(
    private var value: StoredAccountSession?,
) : SecureSessionStore {
    override fun read(): StoredAccountSession? = value

    override fun write(session: StoredAccountSession) {
        value = session
    }

    override fun clear() {
        value = null
    }
}
