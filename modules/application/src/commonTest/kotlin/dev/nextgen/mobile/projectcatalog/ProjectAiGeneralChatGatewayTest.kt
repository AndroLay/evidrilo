package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectAiGeneralChatGatewayTest {
    @Test
    fun `request sends one consented standalone message and validates recommended prompts`() {
        val transport = QueueGeneralChatTransport(
            AccountHttpResponse(200, successJson("general-chat-request-0001")),
        )

        val result = runSuspendTest {
            gateway(transport).sendMessage(validRequest(), "general-chat-request-0001")
        }

        val reply = assertIs<ProjectAiGeneralChatResult.Answer>(result).value
        assertEquals("A research question should name a manageable relationship.", reply.answer)
        assertEquals(listOf("What evidence could answer this question?"), reply.recommendedNextPrompts)
        assertEquals(2, reply.creditCost)
        assertEquals("POST", transport.method)
        assertEquals("/v2/project-ai/general-chat", transport.path)
        assertEquals("Bearer access-token", transport.headers["Authorization"])
        assertEquals("general-chat-request-0001", transport.headers["Idempotency-Key"])
        assertEquals("general-chat.v1", transport.headers["X-Evidrilo-General-Chat-Consent"])
        val body = Json.parseToJsonElement(requireNotNull(transport.body)).jsonObject
        assertEquals(
            setOf("schema", "version", "installationId", "locale", "message"),
            body.keys,
        )
        assertEquals(false, body.containsKey("projectId"))
        assertEquals(false, body.containsKey("history"))
    }

    @Test
    fun `unconfirmed per-request consent is rejected before network dispatch`() {
        val transport = QueueGeneralChatTransport(AccountHttpResponse(503, "{}"))

        val result = runSuspendTest {
            gateway(transport).sendMessage(validRequest().copy(consentConfirmed = false), "general-chat-request-0001")
        }

        assertEquals(ProjectAiGeneralChatResult.Rejected("GENERAL_CHAT_CONSENT_REQUIRED"), result)
        assertEquals(0, transport.calls)
    }

    @Test
    fun `unverified session does not dispatch general message`() {
        val transport = QueueGeneralChatTransport(AccountHttpResponse(200, successJson("general-chat-request-0001")))

        val result = runSuspendTest {
            gateway(transport, verified = false).sendMessage(validRequest(), "general-chat-request-0001")
        }

        assertEquals(
            ProjectAiGeneralChatResult.Deferred(ProjectAiGeneralChatDeferredReason.AUTH_REQUIRED),
            result,
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun `provider disabled remains unavailable and is never rendered as an answer`() {
        val gateway = gateway(
            QueueGeneralChatTransport(AccountHttpResponse(503, """{"code":"PROJECT_AI_GENERAL_CHAT_NOT_READY"}""")),
        )

        val result = runSuspendTest {
            gateway.sendMessage(validRequest(), "general-chat-request-0001")
        }

        assertEquals(ProjectAiGeneralChatResult.Unavailable("PROJECT_AI_GENERAL_CHAT_NOT_READY"), result)
    }

    @Test
    fun `withheld response reports actual usage cost from the API error`() {
        val transport = QueueGeneralChatTransport(
            AccountHttpResponse(
                403,
                """{"schema":"evidrilo.http-error","version":"1","code":"PROJECT_AI_CONSENT_REVOKED_AFTER_PROVIDER","message":"Response withheld.","requestId":"api-request-0001","creditCost":3}""",
            ),
        )

        val result = runSuspendTest {
            gateway(transport).sendMessage(validRequest(), "general-chat-request-0001")
        }

        assertEquals(
            ProjectAiGeneralChatResult.Rejected("PROJECT_AI_CONSENT_REVOKED_AFTER_PROVIDER", creditCost = 3),
            result,
        )
    }

    @Test
    fun `stored project AI consent rejection remains a consent rejection`() {
        val transport = QueueGeneralChatTransport(
            AccountHttpResponse(
                403,
                """{"schema":"evidrilo.http-error","version":"1","code":"PROJECT_AI_CONSENT_REQUIRED","message":"Review consent.","requestId":"api-request-0001"}""",
            ),
        )

        val result = runSuspendTest {
            gateway(transport).sendMessage(validRequest(), "general-chat-request-0001")
        }

        assertEquals(ProjectAiGeneralChatResult.Rejected("PROJECT_AI_CONSENT_REQUIRED"), result)
    }

    @Test
    fun `unrecognized forbidden response is not mislabeled as an authentication failure`() {
        val transport = QueueGeneralChatTransport(
            AccountHttpResponse(403, """{"code":"GENERAL_CHAT_ACCESS_RESTRICTED"}"""),
        )

        val result = runSuspendTest {
            gateway(transport).sendMessage(validRequest(), "general-chat-request-0001")
        }

        assertEquals(ProjectAiGeneralChatResult.Rejected("GENERAL_CHAT_ACCESS_RESTRICTED"), result)
    }

    @Test
    fun `ambiguous transport failure is not marked safe for automatic replay`() {
        val transport = QueueGeneralChatTransport(failure = IllegalStateException("offline"))

        val result = runSuspendTest {
            gateway(transport).sendMessage(validRequest(), "general-chat-request-0001")
        }

        val failed = assertIs<ProjectAiGeneralChatResult.Failed>(result)
        assertTrue(failed.outcomeUnknown)
        assertFalse(failed.retryable)
        assertEquals("PROJECT_AI_OUTCOME_UNKNOWN", failed.code)
    }

    @Test
    fun `malformed response is rejected rather than displayed as AI content`() {
        val response = successJson("different-request-key")
            .replace("\"recommendedNextPrompts\":[\"What evidence could answer this question?\"]", "\"recommendedNextPrompts\":[]")
        val transport = QueueGeneralChatTransport(AccountHttpResponse(200, response))

        val result = runSuspendTest {
            gateway(transport).sendMessage(validRequest(), "general-chat-request-0001")
        }

        assertEquals(ProjectAiGeneralChatResult.Rejected("INVALID_PROJECT_AI_GENERAL_CHAT_RESPONSE"), result)
    }

    private fun gateway(
        transport: QueueGeneralChatTransport,
        verified: Boolean = true,
    ) = ProjectAiGeneralChatGateway(
        configuration = AiClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryGeneralChatSessionStore(verified),
        nowEpochSeconds = { 100 },
    )

    private fun validRequest() = ProjectAiGeneralChatRequest(
        installationId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        locale = "en",
        message = "Help me make a research question manageable.",
        consentConfirmed = true,
    )

    private fun successJson(requestId: String): String =
        """{"schema":"evidrilo.project-ai-general-chat","version":"2","mode":"GENERAL","status":"success","answer":"A research question should name a manageable relationship.","recommendedNextPrompts":["What evidence could answer this question?"],"requestId":"$requestId","creditCost":2}"""

    private class QueueGeneralChatTransport(
        private val response: AccountHttpResponse? = null,
        private val failure: Exception? = null,
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
            failure?.let { throw it }
            return requireNotNull(response)
        }
    }

    private class MemoryGeneralChatSessionStore(verified: Boolean) : SecureSessionStore {
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
