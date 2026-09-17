package dev.nextgen.mobile.recommendation

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecommendationGatewayTest {
    @Test
    fun next_requires_granted_consent_configured_https_verified_and_live_secure_session() {
        val response = AccountHttpResponse(200, recommendedJson())
        val transport = RecordingRecommendationTransport(response)
        val gateway = gateway(transport)

        val consentDenied = runSuspendTest { gateway.next(AnalyticsConsent.NOT_GRANTED) }
        assertEquals("CONSENT_REQUIRED", assertIs<RecommendationGatewayResult.Deferred>(consentDenied).reason.name)
        assertTrue(transport.requests.isEmpty())

        val noSession = runSuspendTest {
            gateway(transport, session = null).next(AnalyticsConsent.GRANTED)
        }
        assertEquals(RecommendationDeferralReason.AUTH_REQUIRED, assertIs<RecommendationGatewayResult.Deferred>(noSession).reason)
        assertTrue(transport.requests.isEmpty())

        val expired = runSuspendTest {
            gateway(transport, session = verifiedSession(expiry = 100)).next(AnalyticsConsent.GRANTED)
        }
        assertEquals(RecommendationDeferralReason.SESSION_EXPIRED, assertIs<RecommendationGatewayResult.Deferred>(expired).reason)
        assertTrue(transport.requests.isEmpty())

        val unverified = runSuspendTest {
            gateway(transport, session = verifiedSession(expiry = 200, verified = false)).next(AnalyticsConsent.GRANTED)
        }
        assertEquals(RecommendationDeferralReason.AUTH_REQUIRED, assertIs<RecommendationGatewayResult.Deferred>(unverified).reason)
        assertTrue(transport.requests.isEmpty())

        val unconfigured = runSuspendTest {
            gateway(transport, apiBaseUrl = "http://api.example.test").next(AnalyticsConsent.GRANTED)
        }
        assertEquals(RecommendationDeferralReason.NOT_CONFIGURED, assertIs<RecommendationGatewayResult.Deferred>(unconfigured).reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun next_sends_bearer_request_and_parses_supported_recommendation() {
        val transport = RecordingRecommendationTransport(AccountHttpResponse(200, recommendedJson()))
        val result = runSuspendTest { gateway(transport).next(AnalyticsConsent.GRANTED) }
        val found = assertIs<RecommendationGatewayResult.Found>(result)

        assertEquals("M0_T2:1", found.recommendation.caseVersionId)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://api.example.test/v1/recommendations/next", request.url)
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertEquals("", request.body)
    }

    @Test
    fun next_maps_statuses_and_malformed_success_fail_closed() {
        val unauthorized = runSuspendTest {
            gateway(RecordingRecommendationTransport(AccountHttpResponse(401, "{}"))).next(AnalyticsConsent.GRANTED)
        }
        assertEquals(RecommendationDeferralReason.AUTH_REQUIRED, assertIs<RecommendationGatewayResult.Deferred>(unauthorized).reason)

        val forbidden = runSuspendTest {
            gateway(RecordingRecommendationTransport(AccountHttpResponse(403, "{}"))).next(AnalyticsConsent.GRANTED)
        }
        assertEquals(RecommendationDeferralReason.AUTH_REQUIRED, assertIs<RecommendationGatewayResult.Deferred>(forbidden).reason)

        val notFound = runSuspendTest {
            gateway(RecordingRecommendationTransport(AccountHttpResponse(404, "{}"))).next(AnalyticsConsent.GRANTED)
        }
        assertIs<RecommendationGatewayResult.Abstained>(notFound)

        listOf(408, 425, 429, 500, 503).forEach { status ->
            val result = runSuspendTest {
                gateway(RecordingRecommendationTransport(AccountHttpResponse(status, "{}"))).next(AnalyticsConsent.GRANTED)
            }
            assertTrue(assertIs<RecommendationGatewayResult.Failed>(result).retryable)
        }

        val rejected = runSuspendTest {
            gateway(RecordingRecommendationTransport(AccountHttpResponse(400, "{}"))).next(AnalyticsConsent.GRANTED)
        }
        assertFalse(assertIs<RecommendationGatewayResult.Failed>(rejected).retryable)

        val malformed = runSuspendTest {
            gateway(RecordingRecommendationTransport(AccountHttpResponse(200, "{}"))).next(AnalyticsConsent.GRANTED)
        }
        assertEquals("INVALID_RECOMMENDATION_RESPONSE", assertIs<RecommendationGatewayResult.Failed>(malformed).code)
        assertFalse(assertIs<RecommendationGatewayResult.Failed>(malformed).retryable)

        val oversized = runSuspendTest {
            gateway(RecordingRecommendationTransport(AccountHttpResponse(200, "x".repeat(MAX_RECOMMENDATION_RESPONSE_BYTES + 1))))
                .next(AnalyticsConsent.GRANTED)
        }
        assertEquals("INVALID_RECOMMENDATION_RESPONSE", assertIs<RecommendationGatewayResult.Failed>(oversized).code)
    }

    @Test
    fun next_with_retry_uses_two_bounded_delays_and_stops_after_success() {
        val transport = RecordingRecommendationTransport(
            AccountHttpResponse(503, "{}"),
            AccountHttpResponse(503, "{}"),
            AccountHttpResponse(200, recommendedJson()),
        )
        val waits = mutableListOf<Long>()

        val result = runSuspendTest {
            gateway(transport).nextWithRetry(AnalyticsConsent.GRANTED) { waits += it }
        }

        assertIs<RecommendationGatewayResult.Found>(result)
        assertEquals(listOf(250L, 500L), waits)
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun interaction_sends_exact_contract_and_reuses_client_event_id() {
        val transport = RecordingRecommendationTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.recommendation-interaction-result","version":"1","outcome":"accepted","requestId":"req-int-001"}
                """.trimIndent(),
            ),
        )
        val id = "123e4567-e89b-42d3-a456-426614174010"
        val result = runSuspendTest {
            gateway(transport).interact(
                recommendation = recommendationPayload(),
                interaction = RecommendationInteraction.ACCEPTED,
                clientEventId = id,
                consent = AnalyticsConsent.GRANTED,
            )
        }
        val sent = assertIs<RecommendationInteractionResult.Sent>(result)

        assertEquals("accepted", sent.outcome)
        assertEquals("req-int-001", sent.requestId)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://api.example.test/v1/recommendations/interactions", request.url)
        assertEquals(id, request.headers["Idempotency-Key"])
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertTrue(request.body.contains("\"interaction\":\"accepted\""))
        assertTrue(request.body.contains("\"consent\":\"granted\""))
        assertFalse(request.body.contains("rawDraftText"))
        assertFalse(request.body.contains("objective"))
    }

    @Test
    fun interaction_requires_consent_and_maps_outcomes_without_retrying_authorization() {
        val deniedTransport = RecordingRecommendationTransport(AccountHttpResponse(200, "{}"))
        val denied = runSuspendTest {
            gateway(deniedTransport).interact(
                recommendationPayload(),
                RecommendationInteraction.DISMISSED,
                "123e4567-e89b-42d3-a456-426614174010",
                AnalyticsConsent.NOT_GRANTED,
            )
        }
        assertEquals(RecommendationDeferralReason.CONSENT_REQUIRED, assertIs<RecommendationInteractionResult.Deferred>(denied).reason)
        assertTrue(deniedTransport.requests.isEmpty())

        val waits = mutableListOf<Long>()
        val unauthorizedTransport = RecordingRecommendationTransport(AccountHttpResponse(401, "{}"))
        val unauthorized = runSuspendTest {
            gateway(unauthorizedTransport).interactWithRetry(
                recommendationPayload(),
                RecommendationInteraction.SHOWN,
                "123e4567-e89b-42d3-a456-426614174010",
                AnalyticsConsent.GRANTED,
            ) { waits += it }
        }
        assertEquals(RecommendationDeferralReason.AUTH_REQUIRED, assertIs<RecommendationInteractionResult.Deferred>(unauthorized).reason)
        assertTrue(waits.isEmpty())
        assertEquals(1, unauthorizedTransport.requests.size)

        val duplicateTransport = RecordingRecommendationTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.recommendation-interaction-result","version":"1","outcome":"duplicate","requestId":"req-int-002"}
                """.trimIndent(),
            ),
        )
        val duplicate = runSuspendTest {
            gateway(duplicateTransport).interact(
                recommendationPayload(),
                RecommendationInteraction.DISMISSED,
                "123e4567-e89b-42d3-a456-426614174011",
                AnalyticsConsent.GRANTED,
            )
        }
        assertEquals("duplicate", assertIs<RecommendationInteractionResult.Sent>(duplicate).outcome)
    }

    @Test
    fun interaction_retry_reuses_one_event_id_across_transient_failures() {
        val transport = RecordingRecommendationTransport(
            AccountHttpResponse(503, "{}"),
            AccountHttpResponse(503, "{}"),
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.recommendation-interaction-result","version":"1","outcome":"accepted","requestId":"req-int-003"}
                """.trimIndent(),
            ),
        )
        val waits = mutableListOf<Long>()
        val eventId = "123e4567-e89b-42d3-a456-426614174012"

        val result = runSuspendTest {
            gateway(transport).interactWithRetry(
                recommendationPayload(),
                RecommendationInteraction.ACCEPTED,
                eventId,
                AnalyticsConsent.GRANTED,
            ) { waits += it }
        }

        assertIs<RecommendationInteractionResult.Sent>(result)
        assertEquals(listOf(250L, 500L), waits)
        assertEquals(3, transport.requests.size)
        assertEquals(listOf(eventId, eventId, eventId), transport.requests.map { it.headers["Idempotency-Key"] })
        assertTrue(transport.requests.all { it.body.contains("\"clientEventId\":\"$eventId\"") })
    }

    @Test
    fun cancellation_is_propagated_for_next_and_interaction() {
        val nextTransport = RecordingRecommendationTransport { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> {
            runSuspendTest { gateway(nextTransport).next(AnalyticsConsent.GRANTED) }
        }

        val interactionTransport = RecordingRecommendationTransport { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> {
            runSuspendTest {
                gateway(interactionTransport).interact(
                    recommendationPayload(),
                    RecommendationInteraction.ACCEPTED,
                    "123e4567-e89b-42d3-a456-426614174010",
                    AnalyticsConsent.GRANTED,
                )
            }
        }
    }

    private fun gateway(
        transport: RecordingRecommendationTransport,
        apiBaseUrl: String = "https://api.example.test",
        session: StoredAccountSession? = verifiedSession(expiry = 200),
    ): RecommendationGateway = RecommendationGateway(
        configuration = RecommendationClientConfiguration(apiBaseUrl),
        transport = transport,
        secureSessionStore = MemoryRecommendationSecureStore(
            session,
        ),
        nowEpochSeconds = { 100 },
    )

    private fun recommendationPayload() = RecommendationPayload(
        status = RecommendationStatus.RECOMMENDED,
        calculationVersion = "recommendation.v1",
        caseVersionId = "M0_T2:1",
        objective = "Practice the next evidence comparison.",
        reason = RecommendationReason.PRACTICE_ACTION_REQUIRED,
        evidenceReferences = listOf("attempt-001"),
        requestId = "req-rec-001",
    )
}

private fun recommendedJson(): String = """
    {
      "schema":"evidrilo.recommendation",
      "version":"1",
      "status":"recommended",
      "calculationVersion":"recommendation.v1",
      "caseVersionId":"M0_T2:1",
      "objective":"Practice the next evidence comparison.",
      "reasonCode":"PRACTICE_ACTION_REQUIRED",
      "evidenceReferences":["attempt-001"],
      "requestId":"req-rec-001"
    }
""".trimIndent()

private fun verifiedSession(expiry: Long, verified: Boolean = true) = StoredAccountSession(
    AccountSummary("123e4567-e89b-42d3-a456-426614174002", verified),
    SecureSessionMaterial("access-token", expiry),
)

private data class RecommendationRequestRecord(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

private class RecordingRecommendationTransport : AccountHttpTransport {
    private val responses: MutableList<AccountHttpResponse>
    private val handler: (suspend () -> AccountHttpResponse)?
    val requests = mutableListOf<RecommendationRequestRecord>()

    constructor(vararg responses: AccountHttpResponse) {
        this.responses = responses.toMutableList()
        this.handler = null
    }

    constructor(handler: suspend () -> AccountHttpResponse) {
        this.responses = mutableListOf()
        this.handler = handler
    }

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += RecommendationRequestRecord(method, url, headers, body)
        if (handler != null) return handler.invoke()
        val next = responses.firstOrNull() ?: error("No fake response configured")
        if (responses.size > 1) responses.removeAt(0)
        return next
    }
}

private class MemoryRecommendationSecureStore(
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
