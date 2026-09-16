package dev.nextgen.mobile.recommendation

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.analytics.AnalyticsEvent
import dev.nextgen.mobile.analytics.AnalyticsEventName
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecommendationControllerTest {
    @Test
    fun hidden_gates_do_not_touch_the_network() {
        val transport = ControllerTransport(AccountHttpResponse(200, recommendedJson()))
        val states = mutableListOf<RecommendationUiState>()
        val controller = controller(transport, states)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = false)
            controller.load(lifecycleKey(), AnalyticsConsent.NOT_GRANTED, canDisplay = true)
        }

        assertTrue(transport.requests.isEmpty())
        assertIs<RecommendationUiState.Hidden>(states.last())
    }

    @Test
    fun one_lifecycle_key_fetches_once_and_exposes_a_supported_card() {
        val transport = ControllerTransport(
            AccountHttpResponse(200, recommendedJson()),
            interactionResponse("accepted"),
        )
        val states = mutableListOf<RecommendationUiState>()
        val analytics = mutableListOf<AnalyticsEvent>()
        val controller = controller(transport, states, analytics)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }

        val available = assertIs<RecommendationUiState.Available>(states.last())
        assertEquals(ConclusionCases.M0_T2, available.target.localCase)
        assertEquals("M0_T2:1", available.target.remoteCaseVersionId)
        assertEquals(2, transport.requests.size)
        assertEquals(listOf(AnalyticsEventName.RECOMMENDATION_SHOWN), analytics.map { it.name })
    }

    @Test
    fun abstain_unknown_case_and_deferred_sessions_fail_closed() {
        val abstainStates = mutableListOf<RecommendationUiState>()
        runSuspendTest {
            controller(
                ControllerTransport(AccountHttpResponse(200, abstainJson())),
                abstainStates,
            ).load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }
        assertIs<RecommendationUiState.Abstained>(abstainStates.last())

        val unknownStates = mutableListOf<RecommendationUiState>()
        runSuspendTest {
            controller(
                ControllerTransport(AccountHttpResponse(200, recommendedJson("unknown:1"))),
                unknownStates,
            ).load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }
        val unsupported = assertIs<RecommendationUiState.Unsupported>(unknownStates.last())
        assertEquals("unknown:1", unsupported.caseVersionId)

        val expiredStates = mutableListOf<RecommendationUiState>()
        runSuspendTest {
            controller(
                ControllerTransport(AccountHttpResponse(200, recommendedJson())),
                expiredStates,
                session = session(expiry = 100),
            ).load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }
        assertIs<RecommendationUiState.Expired>(expiredStates.last())

        val authStates = mutableListOf<RecommendationUiState>()
        runSuspendTest {
            controller(
                ControllerTransport(AccountHttpResponse(200, recommendedJson())),
                authStates,
                session = null,
            ).load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }
        assertIs<RecommendationUiState.Hidden>(authStates.last())

        val configStates = mutableListOf<RecommendationUiState>()
        runSuspendTest {
            controller(
                ControllerTransport(AccountHttpResponse(200, recommendedJson())),
                configStates,
                apiBaseUrl = "http://api.example.test",
            ).load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }
        assertEquals("NOT_CONFIGURED", assertIs<RecommendationUiState.Unavailable>(configStates.last()).code)
    }

    @Test
    fun transient_failure_is_retryable_unavailable_and_malformed_success_is_rejected() {
        val unavailableStates = mutableListOf<RecommendationUiState>()
        runSuspendTest {
            controller(
                ControllerTransport(AccountHttpResponse(503, "{}")),
                unavailableStates,
            ).load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }
        assertEquals("RECOMMENDATION_UNAVAILABLE", assertIs<RecommendationUiState.Unavailable>(unavailableStates.last()).code)

        val rejectedStates = mutableListOf<RecommendationUiState>()
        runSuspendTest {
            controller(
                ControllerTransport(AccountHttpResponse(200, "{}")),
                rejectedStates,
            ).load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }
        assertIs<RecommendationUiState.Rejected>(rejectedStates.last())
    }

    @Test
    fun invalidate_suppresses_a_result_that_returns_after_the_key_is_invalidated() {
        val states = mutableListOf<RecommendationUiState>()
        val transport = ControllerTransport(AccountHttpResponse(200, recommendedJson()))
        lateinit var controller: RecommendationController
        transport.onRequest = { request ->
            if (request.method == "GET") controller.invalidate()
        }
        controller = controller(transport, states)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }

        assertIs<RecommendationUiState.Hidden>(states.last())
        assertTrue(states.none { it is RecommendationUiState.Available })
    }

    @Test
    fun cancelled_load_can_be_retried_with_the_same_lifecycle_key() {
        val transport = CancelThenRespondTransport()
        val states = mutableListOf<RecommendationUiState>()
        val controller = controller(transport, states)

        var requestCompleted = false
        val requestJob = CoroutineScope(Dispatchers.Unconfined + Job()).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
            } catch (_: CancellationException) {
                // The screen owner cancelled the in-flight request.
            }
        }
        requestJob.invokeOnCompletion { requestCompleted = true }

        assertEquals(1, transport.requestCount)
        requestJob.cancel()
        assertTrue(transport.firstRequestCancelled)
        assertTrue(requestCompleted)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }

        assertEquals(3, transport.requestCount)
        assertIs<RecommendationUiState.Available>(states.last())
    }

    @Test
    fun cancelled_accept_restores_the_card_and_can_be_retried() {
        val transport = CancelOnAcceptedInteractionTransport()
        val states = mutableListOf<RecommendationUiState>()
        val launches = mutableListOf<ConclusionCase>()
        val controller = controller(transport, states)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }

        var requestCompleted = false
        val requestJob = CoroutineScope(Dispatchers.Unconfined + Job()).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                controller.accept { launches += it }
            } catch (_: CancellationException) {
                // The screen owner cancelled the in-flight interaction.
            }
        }
        requestJob.invokeOnCompletion { requestCompleted = true }

        requestJob.cancel()

        assertTrue(transport.acceptedRequestCancelled)
        assertTrue(requestCompleted)
        assertIs<RecommendationUiState.Available>(states.last())

        runSuspendTest { controller.accept { launches += it } }

        assertEquals(listOf(ConclusionCases.M0_T2), launches)
        assertIs<RecommendationUiState.Hidden>(states.last())
    }

    @Test
    fun cancelled_dismiss_restores_the_card_and_can_be_retried() {
        val transport = CancelOnDismissInteractionTransport()
        val states = mutableListOf<RecommendationUiState>()
        val controller = controller(transport, states)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }

        var requestCompleted = false
        val requestJob = CoroutineScope(Dispatchers.Unconfined + Job()).launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                controller.dismiss()
            } catch (_: CancellationException) {
                // The screen owner cancelled the in-flight interaction.
            }
        }
        requestJob.invokeOnCompletion { requestCompleted = true }

        requestJob.cancel()

        assertTrue(transport.dismissRequestCancelled)
        assertTrue(requestCompleted)
        assertIs<RecommendationUiState.Available>(states.last())

        runSuspendTest { controller.dismiss() }

        assertIs<RecommendationUiState.Hidden>(states.last())
    }

    @Test
    fun accept_launches_the_bundled_case_and_emits_one_typed_event() {
        val transport = ControllerTransport(
            AccountHttpResponse(200, recommendedJson()),
            interactionResponse("accepted"),
            interactionResponse("accepted"),
        )
        val states = mutableListOf<RecommendationUiState>()
        val analytics = mutableListOf<AnalyticsEvent>()
        val launches = mutableListOf<ConclusionCase>()
        val controller = controller(transport, states, analytics)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
            controller.accept { launches += it }
            controller.accept { launches += it }
        }

        assertEquals(listOf(ConclusionCases.M0_T2), launches)
        assertIs<RecommendationUiState.Hidden>(states.last())
        assertEquals(
            listOf(AnalyticsEventName.RECOMMENDATION_SHOWN, AnalyticsEventName.RECOMMENDATION_ACCEPTED),
            analytics.map { it.name },
        )
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun each_interaction_type_uses_a_distinct_idempotency_key() {
        val shownId = "123e4567-e89b-42d3-a456-426614174011"
        val acceptedId = "123e4567-e89b-42d3-a456-426614174012"
        val transport = ControllerTransport(
            AccountHttpResponse(200, recommendedJson()),
            interactionResponse("accepted"),
            interactionResponse("accepted"),
        )
        val ids = listOf(shownId, acceptedId).iterator()
        val states = mutableListOf<RecommendationUiState>()
        val controller = controller(
            transport = transport,
            states = states,
            newInteractionId = { ids.next() },
        )

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
            controller.accept { }
        }

        val shownRequest = transport.requests[1]
        val acceptedRequest = transport.requests[2]
        assertEquals(shownId, shownRequest.headers["Idempotency-Key"])
        assertEquals(acceptedId, acceptedRequest.headers["Idempotency-Key"])
        assertTrue(shownRequest.body.contains("\"interaction\":\"shown\""))
        assertTrue(acceptedRequest.body.contains("\"interaction\":\"accepted\""))
    }

    @Test
    fun dismiss_hides_immediately_and_emits_one_typed_event() {
        val transport = ControllerTransport(
            AccountHttpResponse(200, recommendedJson()),
            interactionResponse("accepted"),
            interactionResponse("accepted"),
        )
        val states = mutableListOf<RecommendationUiState>()
        val analytics = mutableListOf<AnalyticsEvent>()
        val controller = controller(transport, states, analytics)

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
            controller.dismiss()
            controller.dismiss()
        }

        assertIs<RecommendationUiState.Hidden>(states.last())
        assertEquals(
            listOf(AnalyticsEventName.RECOMMENDATION_SHOWN, AnalyticsEventName.RECOMMENDATION_DISMISSED),
            analytics.map { it.name },
        )
        assertFalse(states.any { it is RecommendationUiState.ActionInProgress })
        assertEquals(3, transport.requests.size)
    }

    @Test
    fun accept_is_ignored_until_the_shown_interaction_finishes() {
        val transport = DeferredShownInteractionTransport()
        val states = mutableListOf<RecommendationUiState>()
        val launches = mutableListOf<ConclusionCase>()
        val controller = controller(transport, states)
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        val loadJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }

        assertTrue(transport.shownInteractionStarted)
        runSuspendTest { controller.accept { launches += it } }
        assertTrue(launches.isEmpty())
        assertEquals(2, transport.requests.size)

        transport.completeShownInteraction()
        runSuspendTest { loadJob.join() }
        runSuspendTest { controller.accept { launches += it } }

        assertEquals(listOf(ConclusionCases.M0_T2), launches)
        assertEquals(3, transport.requests.size)
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun invalidation_after_card_publish_does_not_record_a_stale_shown_interaction() {
        val transport = ControllerTransport(AccountHttpResponse(200, recommendedJson()))
        val states = mutableListOf<RecommendationUiState>()
        lateinit var controller: RecommendationController
        controller = controller(
            transport = transport,
            states = states,
            onStateChanged = { next ->
                if (next is RecommendationUiState.Available) controller.invalidate()
            },
        )

        runSuspendTest {
            controller.load(lifecycleKey(), AnalyticsConsent.GRANTED, canDisplay = true)
        }

        assertIs<RecommendationUiState.Hidden>(states.last())
        assertEquals(listOf("GET"), transport.requests.map { it.method })
    }

    private fun controller(
        transport: AccountHttpTransport,
        states: MutableList<RecommendationUiState>,
        analytics: MutableList<AnalyticsEvent> = mutableListOf(),
        session: StoredAccountSession? = session(expiry = 200),
        apiBaseUrl: String = "https://api.example.test",
        newInteractionId: () -> String = { "123e4567-e89b-42d3-a456-426614174010" },
        onStateChanged: ((RecommendationUiState) -> Unit)? = null,
    ): RecommendationController = RecommendationController(
        gateway = RecommendationGateway(
            configuration = RecommendationClientConfiguration(apiBaseUrl),
            transport = transport,
            secureSessionStore = ControllerSecureStore(session),
            nowEpochSeconds = { 100 },
        ),
        registry = RecommendationCaseRegistry(),
        emitAnalytics = { analytics += it },
        onStateChanged = { next ->
            states += next
            onStateChanged?.invoke(next)
        },
        newInteractionId = newInteractionId,
        wait = { },
    )

    private fun lifecycleKey() = RecommendationLifecycleKey(
        accountId = "123e4567-e89b-42d3-a456-426614174002",
        sessionGeneration = 1,
        consentGeneration = 1,
    )
}

private fun recommendedJson(caseVersionId: String = "M0_T2:1"): String = """
    {
      "schema":"evidrilo.recommendation",
      "version":"1",
      "status":"recommended",
      "calculationVersion":"recommendation.v1",
      "caseVersionId":"$caseVersionId",
      "objective":"Practice the next evidence comparison.",
      "reasonCode":"PRACTICE_ACTION_REQUIRED",
      "evidenceReferences":["attempt-001"],
      "requestId":"req-rec-001"
    }
""".trimIndent()

private fun abstainJson(): String = """
    {
      "schema":"evidrilo.recommendation",
      "version":"1",
      "status":"abstain",
      "calculationVersion":"recommendation.v1",
      "caseVersionId":null,
      "objective":null,
      "reasonCode":"NO_ELIGIBLE_CASE",
      "evidenceReferences":[],
      "requestId":"req-rec-002"
    }
""".trimIndent()

private fun interactionResponse(outcome: String): AccountHttpResponse = AccountHttpResponse(
    200,
    """
    {"schema":"evidrilo.recommendation-interaction-result","version":"1","outcome":"$outcome","requestId":"req-int-001"}
    """.trimIndent(),
)

private fun session(expiry: Long) = StoredAccountSession(
    AccountSummary("123e4567-e89b-42d3-a456-426614174002", true),
    SecureSessionMaterial("access-token", expiry),
)

private data class ControllerRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

private class ControllerTransport(
    vararg responseValues: AccountHttpResponse,
) : AccountHttpTransport {
    private val responses = responseValues.toMutableList()
    val requests = mutableListOf<ControllerRequest>()
    var onRequest: ((ControllerRequest) -> Unit)? = null

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        val request = ControllerRequest(method, url, headers, body)
        requests += request
        onRequest?.invoke(request)
        val response = responses.firstOrNull() ?: error("No fake response configured")
        if (responses.size > 1) responses.removeAt(0)
        return response
    }
}

private class CancelThenRespondTransport : AccountHttpTransport {
    var requestCount = 0
    var firstRequestCancelled = false

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requestCount += 1
        if (requestCount == 1) {
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { firstRequestCancelled = true }
            }
        }
        return AccountHttpResponse(200, recommendedJson())
    }
}

private class CancelOnAcceptedInteractionTransport : AccountHttpTransport {
    private var interactionCount = 0
    var acceptedRequestCancelled = false

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        if (method == "GET") return AccountHttpResponse(200, recommendedJson())
        interactionCount += 1
        if (interactionCount == 1) return interactionResponse("accepted")
        if (interactionCount == 2) {
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { acceptedRequestCancelled = true }
            }
        }
        return interactionResponse("accepted")
    }
}

private class CancelOnDismissInteractionTransport : AccountHttpTransport {
    private var interactionCount = 0
    var dismissRequestCancelled = false

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        if (method == "GET") return AccountHttpResponse(200, recommendedJson())
        interactionCount += 1
        if (interactionCount == 1) return interactionResponse("accepted")
        if (interactionCount == 2) {
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { dismissRequestCancelled = true }
            }
        }
        return interactionResponse("accepted")
    }
}

private class DeferredShownInteractionTransport : AccountHttpTransport {
    val requests = mutableListOf<ControllerRequest>()
    private val shownResponse = CompletableDeferred<AccountHttpResponse>()
    var shownInteractionStarted = false
        private set
    private var postCount = 0

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += ControllerRequest(method, url, headers, body)
        if (method == "GET") return AccountHttpResponse(200, recommendedJson())
        postCount += 1
        if (postCount == 1) {
            shownInteractionStarted = true
            return shownResponse.await()
        }
        return interactionResponse("accepted")
    }

    fun completeShownInteraction() {
        shownResponse.complete(interactionResponse("accepted"))
    }
}

private class ControllerSecureStore(
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
