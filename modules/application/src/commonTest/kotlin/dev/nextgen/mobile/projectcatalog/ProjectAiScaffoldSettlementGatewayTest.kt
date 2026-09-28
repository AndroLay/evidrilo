package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProjectAiScaffoldSettlementGatewayTest {
    @Test
    fun `apply settlement is account-bound idempotent and verifies cost`() {
        val transport = QueueProjectAiSettlementTransport(
            AccountHttpResponse(200, settlementJson("applied", "project-ai-request-0001", 3)),
        )
        val result = runSuspendTest {
            gateway(transport).settle(
                requestId = "project-ai-request-0001",
                decision = ProjectAiScaffoldDecision.APPLY,
                expectedCreditCost = 3,
            )
        }

        assertEquals(
            ProjectAiScaffoldSettlementResult.Settled(
                requestId = "project-ai-request-0001",
                decision = ProjectAiScaffoldDecision.APPLY,
                creditCost = 3,
            ),
            result,
        )
        assertEquals("POST", transport.method)
        assertEquals("/v1/project-ai/scaffold/settlement", transport.path)
        assertEquals("Bearer access-token", transport.headers["Authorization"])
        assertEquals("project-ai-request-0001", transport.headers["Idempotency-Key"])
        assertEquals(true, transport.body?.contains("\"decision\":\"apply\"") == true)
    }

    @Test
    fun `dismiss settlement requires matching terminal status`() {
        val gateway = gateway(
            QueueProjectAiSettlementTransport(
                AccountHttpResponse(200, settlementJson("dismissed", "project-ai-request-0001", 1)),
            ),
        )

        val result = runSuspendTest {
            gateway.settle(
                requestId = "project-ai-request-0001",
                decision = ProjectAiScaffoldDecision.DISMISS,
                expectedCreditCost = 1,
            )
        }

        assertEquals(
            ProjectAiScaffoldSettlementResult.Settled(
                "project-ai-request-0001",
                ProjectAiScaffoldDecision.DISMISS,
                1,
            ),
            result,
        )
    }

    @Test
    fun `settlement fails closed on mismatched request id or cost`() {
        val wrongId = gateway(
            QueueProjectAiSettlementTransport(
                AccountHttpResponse(200, settlementJson("applied", "different-request-0001", 3)),
            ),
        )
        val wrongCost = gateway(
            QueueProjectAiSettlementTransport(
                AccountHttpResponse(200, settlementJson("applied", "project-ai-request-0001", 1)),
            ),
        )

        val idResult = runSuspendTest {
            wrongId.settle("project-ai-request-0001", ProjectAiScaffoldDecision.APPLY, 3)
        }
        val costResult = runSuspendTest {
            wrongCost.settle("project-ai-request-0001", ProjectAiScaffoldDecision.APPLY, 3)
        }

        assertEquals(ProjectAiScaffoldSettlementResult.Rejected("INVALID_PROJECT_AI_SETTLEMENT_RESPONSE"), idResult)
        assertEquals(ProjectAiScaffoldSettlementResult.Rejected("INVALID_PROJECT_AI_SETTLEMENT_RESPONSE"), costResult)
    }

    @Test
    fun `settlement requires a verified unexpired secure session`() {
        val transport = QueueProjectAiSettlementTransport(AccountHttpResponse(200, "{}"))
        val gateway = ProjectAiScaffoldSettlementGateway(
            configuration = AiClientConfiguration("https://api.example.test"),
            transport = transport,
            secureSessionStore = MemoryProjectAiSettlementSessionStore(verified = false),
            nowEpochSeconds = { 100 },
        )

        val result = runSuspendTest {
            gateway.settle("project-ai-request-0001", ProjectAiScaffoldDecision.APPLY, 3)
        }

        assertIs<ProjectAiScaffoldSettlementResult.Deferred>(result)
        assertEquals(0, transport.calls)
    }

    private fun gateway(transport: QueueProjectAiSettlementTransport) = ProjectAiScaffoldSettlementGateway(
        configuration = AiClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryProjectAiSettlementSessionStore(verified = true),
        nowEpochSeconds = { 100 },
    )

    private fun settlementJson(status: String, requestId: String, creditCost: Int): String =
        """{"schema":"evidrilo.project-ai-scaffold-settlement","version":"1","status":"$status","requestId":"$requestId","creditCost":$creditCost}"""

    private class QueueProjectAiSettlementTransport(
        private val response: AccountHttpResponse,
    ) : AccountHttpTransport {
        var calls = 0
        var method: String? = null
        var path: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String? = null

        override suspend fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String,
        ): AccountHttpResponse {
            calls += 1
            this.method = method
            this.path = url.substringAfter("api.example.test")
            this.headers = headers
            this.body = body
            return response
        }
    }

    private class MemoryProjectAiSettlementSessionStore(verified: Boolean) : SecureSessionStore {
        private var session = StoredAccountSession(
            AccountSummary("student-account", emailVerified = verified),
            SecureSessionMaterial("access-token", expiresAtEpochSeconds = 500),
        )

        override fun read(): StoredAccountSession = session

        override fun write(session: StoredAccountSession) {
            this.session = session
        }

        override fun clear() {
            session = StoredAccountSession(
                AccountSummary("student-account", emailVerified = false),
                SecureSessionMaterial("access-token", expiresAtEpochSeconds = 500),
            )
        }
    }
}
