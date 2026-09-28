package dev.nextgen.mobile.platform

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

class PlatformProjectionGatewayTest {
    @Test
    fun progress_and_entitlements_parse_from_authenticated_api_contracts() {
        val transport = QueueProjectionTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.progress-summary","version":"1","calculationVersion":"progress.v1","attemptsObserved":4,"completedAttempts":3,"revisionsObserved":2,"passCount":2,"actionRequiredCount":1,"abstentionCount":0,"coverage":0.75,"requestId":"req-progress-1"}
                """.trimIndent(),
            ),
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.entitlements","version":"1","entitlements":[{"entitlement":"evidrilo_pro","status":"active","updatedAt":"2026-09-21T05:00:00Z"}],"requestId":"req-entitle-1"}
                """.trimIndent(),
            ),
        )
        val gateway = gateway(transport)

        val progress = assertIs<PlatformProjectionResult.Found<PlatformProgressSummary>>(
            runSuspendTest { gateway.getProgress() },
        ).value
        val entitlements = assertIs<PlatformProjectionResult.Found<PlatformEntitlements>>(
            runSuspendTest { gateway.getEntitlements() },
        ).value

        assertEquals(4, progress.attemptsObserved)
        assertEquals(0.75, progress.coverage)
        assertEquals("active", entitlements.items.single().status)
        assertEquals("evidrilo_pro", entitlements.items.single().entitlement)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("/v1/progress/me", transport.requests[0].path)
        assertEquals("/v1/billing/entitlements", transport.requests[1].path)
        assertEquals("Bearer access-token", transport.requests[0].headers["Authorization"])
    }

    @Test
    fun projection_gateway_defers_before_network_for_missing_or_expired_session() {
        val transport = QueueProjectionTransport(AccountHttpResponse(200, "{}"))
        val missing = runSuspendTest {
            gateway(transport, session = null).getProgress()
        }
        assertEquals(ProjectionDeferralReason.AUTH_REQUIRED, assertIs<PlatformProjectionResult.Deferred>(missing).reason)
        assertTrue(transport.requests.isEmpty())

        val expired = runSuspendTest {
            gateway(transport, session = verifiedSession(expiresAt = 100)).getEntitlements()
        }
        assertEquals(ProjectionDeferralReason.SESSION_EXPIRED, assertIs<PlatformProjectionResult.Deferred>(expired).reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun projection_gateway_fails_closed_for_malformed_success_and_transient_statuses() {
        val malformed = runSuspendTest {
            gateway(QueueProjectionTransport(AccountHttpResponse(200, "{}"))).getProgress()
        }
        assertEquals("INVALID_PROGRESS_RESPONSE", assertIs<PlatformProjectionResult.Failed>(malformed).code)
        assertFalse(assertIs<PlatformProjectionResult.Failed>(malformed).retryable)

        val transient = runSuspendTest {
            gateway(QueueProjectionTransport(AccountHttpResponse(503, "{}"))).getEntitlements()
        }
        assertEquals("PROJECTION_UNAVAILABLE", assertIs<PlatformProjectionResult.Failed>(transient).code)
        assertTrue(assertIs<PlatformProjectionResult.Failed>(transient).retryable)
    }

    @Test
    fun projection_gateway_rejects_unsafe_progress_values() {
        val result = runSuspendTest {
            gateway(
                QueueProjectionTransport(
                    AccountHttpResponse(
                        200,
                        """
                        {"schema":"evidrilo.progress-summary","version":"1","calculationVersion":"progress.v1","attemptsObserved":1,"completedAttempts":2,"revisionsObserved":0,"passCount":0,"actionRequiredCount":0,"abstentionCount":0,"coverage":1.5,"requestId":"req-progress-2"}
                        """.trimIndent(),
                    ),
                ),
            ).getProgress()
        }
        assertEquals("INVALID_PROGRESS_RESPONSE", assertIs<PlatformProjectionResult.Failed>(result).code)
    }

    @Test
    fun projection_transport_failure_does_not_call_a_reachable_device_offline() {
        val offline = runSuspendTest {
            gateway(FailingProjectionTransport(DeviceConnectivity.OFFLINE)).getProgress()
        }
        val online = runSuspendTest {
            gateway(FailingProjectionTransport(DeviceConnectivity.ONLINE)).getProgress()
        }

        assertEquals("PROJECTION_OFFLINE", assertIs<PlatformProjectionResult.Failed>(offline).code)
        assertEquals("PROJECTION_UNAVAILABLE", assertIs<PlatformProjectionResult.Failed>(online).code)
    }

    private fun gateway(
        transport: AccountHttpTransport,
        session: StoredAccountSession? = verifiedSession(),
    ): PlatformProjectionGateway = PlatformProjectionGateway(
        configuration = PlatformClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryProjectionSecureStore(session),
        nowEpochSeconds = { 100 },
    )

    private fun verifiedSession(expiresAt: Long = 200): StoredAccountSession = StoredAccountSession(
        AccountSummary("123e4567-e89b-42d3-a456-426614174002", true),
        SecureSessionMaterial("access-token", expiresAt),
    )
}

private data class ProjectionRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
)

private class QueueProjectionTransport(
    private vararg val responses: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<ProjectionRequest>()
    private var index = 0

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += ProjectionRequest(method, url.substringAfter("api.example.test"), headers)
        return responses[index++]
    }
}

private class FailingProjectionTransport(
    override val deviceConnectivity: DeviceConnectivity,
) : AccountHttpTransport {
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse = error("Synthetic transport failure")
}

private class MemoryProjectionSecureStore(
    private var value: StoredAccountSession?,
) : SecureSessionStore {
    override fun read(): StoredAccountSession? = value
    override fun write(session: StoredAccountSession) { value = session }
    override fun clear() { value = null }
}
