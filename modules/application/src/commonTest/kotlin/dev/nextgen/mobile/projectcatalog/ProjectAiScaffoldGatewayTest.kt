package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldOperation
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldRules
import dev.nextgen.mobile.network.DeviceConnectivity
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProjectAiScaffoldGatewayTest {
    @Test
    fun `valid request sends only through verified secure session and parses preview`() {
        val transport = QueueProjectAiTransport(
            AccountHttpResponse(
                200,
                previewJson(creditCost = 3),
            ),
        )
        val gateway = gateway(transport)

        val result = assertIs<ProjectAiScaffoldGatewayResult.Preview>(
            runSuspendTest {
                gateway.generatePreview(validRequest(), "project-ai-request-0001")
            },
        )

        assertEquals("How does the measured outcome vary?", result.proposal.fieldSuggestions.single().suggestedValue)
        assertEquals("What evidence would answer this question?", result.proposal.recommendedNextPrompts.single())
        assertEquals("project-ai-request-0001", result.requestId)
        assertEquals(3, result.creditCost)
        assertEquals("Bearer access-token", transport.headers["Authorization"])
        assertEquals("project-ai-request-0001", transport.headers["Idempotency-Key"])
        assertEquals("/v1/project-ai/scaffold", transport.path)
        assertEquals(true, transport.body?.contains("\"operation\":\"create_project\"") == true)
        assertEquals(true, transport.body?.contains("\"projectId\":null") == true)
        assertEquals(true, transport.body?.contains("\"studentQuestion\":\"Help me define a manageable first step.\"") == true)
        assertNull(result.proposal.baseProjectRevision)
    }

    @Test
    fun `request fields are serialized in canonical key order`() {
        val transport = QueueProjectAiTransport(
            AccountHttpResponse(200, previewJson(creditCost = 3)),
        )
        runSuspendTest {
            gateway(transport).generatePreview(
                validRequest().copy(currentFields = linkedMapOf("z_field" to "last", "a_field" to "first")),
                "project-ai-request-0001",
            )
        }

        val request = Json.parseToJsonElement(requireNotNull(transport.body)).jsonObject
        val fields = request.getValue("currentFields").jsonObject
        assertEquals(listOf("a_field", "z_field"), fields.keys.toList())
    }

    @Test
    fun `preview rejects a credit cost that does not match the declared operation`() {
        val transport = QueueProjectAiTransport(
            AccountHttpResponse(200, previewJson(creditCost = 1)),
        )

        val result = runSuspendTest {
            gateway(transport).generatePreview(validRequest(), "project-ai-request-0001")
        }

        assertEquals(ProjectAiScaffoldGatewayResult.Rejected("INVALID_PROJECT_AI_RESPONSE"), result)
    }

    @Test
    fun `an in-project assist accepts exactly one credit`() {
        val projectId = "11111111-1111-4111-8111-111111111111"
        val result = runSuspendTest {
            gateway(QueueProjectAiTransport(
                AccountHttpResponse(200, previewJson(
                    creditCost = 1,
                    operation = "assist_project",
                    projectId = projectId,
                    baseProjectRevision = 7,
                )),
            )).generatePreview(
                validRequest().copy(
                    operation = ProjectAiScaffoldOperation.ASSIST_PROJECT,
                    projectId = projectId,
                    baseProjectRevision = 7,
                ),
                "project-ai-request-0001",
            )
        }

        val preview = assertIs<ProjectAiScaffoldGatewayResult.Preview>(result)
        assertEquals(1, preview.creditCost)
        assertEquals(projectId, preview.proposal.projectId)
    }

    @Test
    fun `preview for a different project identity is rejected`() {
        val requestProjectId = "11111111-1111-4111-8111-111111111111"
        val responseProjectId = "22222222-2222-4222-8222-222222222222"
        val result = runSuspendTest {
            gateway(QueueProjectAiTransport(
                AccountHttpResponse(200, previewJson(
                    creditCost = 1,
                    operation = "assist_project",
                    projectId = responseProjectId,
                    baseProjectRevision = 7,
                )),
            )).generatePreview(
                validRequest().copy(
                    operation = ProjectAiScaffoldOperation.ASSIST_PROJECT,
                    projectId = requestProjectId,
                    baseProjectRevision = 7,
                ),
                "project-ai-request-0001",
            )
        }

        assertEquals(ProjectAiScaffoldGatewayResult.Rejected("PROJECT_AI_RESPONSE_CONTEXT_MISMATCH"), result)
    }

    @Test
    fun `provider disabled response remains unavailable and is not represented as generated content`() {
        val gateway = gateway(QueueProjectAiTransport(
            AccountHttpResponse(503, """{"code":"PROJECT_AI_NOT_READY"}"""),
        ))

        val result = runSuspendTest {
            gateway.generatePreview(validRequest(), "project-ai-request-0001")
        }

        assertEquals(ProjectAiScaffoldGatewayResult.Unavailable("PROJECT_AI_NOT_READY"), result)
    }

    @Test
    fun `successful scaffold response must preserve explicit nullable contract keys`() {
        val gateway = gateway(QueueProjectAiTransport(
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.project-ai-scaffold","version":"1","status":"preview","scaffold":{},"requestId":"project-ai-request-0001"}""",
            ),
        ))

        val result = runSuspendTest {
            gateway.generatePreview(validRequest(), "project-ai-request-0001")
        }

        assertEquals(ProjectAiScaffoldGatewayResult.Rejected("INVALID_PROJECT_AI_RESPONSE"), result)
    }

    @Test
    fun `missing project data consent prevents network dispatch`() {
        val transport = QueueProjectAiTransport(AccountHttpResponse(200, "{}"))
        val gateway = gateway(transport)

        val result = runSuspendTest {
            gateway.generatePreview(validRequest().copy(projectDataConsent = false), "project-ai-request-0001")
        }

        assertEquals(
            ProjectAiScaffoldGatewayResult.Rejected("PROJECT_AI_DATA_CONSENT_REQUIRED"),
            result,
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun `server consent revocation is not misreported as authentication failure`() {
        val gateway = gateway(QueueProjectAiTransport(
            AccountHttpResponse(403, """{"code":"PROJECT_AI_CONSENT_REQUIRED"}"""),
        ))

        val result = runSuspendTest {
            gateway.generatePreview(validRequest(), "project-ai-request-0001")
        }

        assertEquals(ProjectAiScaffoldGatewayResult.Rejected("PROJECT_AI_CONSENT_REQUIRED"), result)
    }

    private fun gateway(transport: QueueProjectAiTransport) = ProjectAiScaffoldGateway(
        configuration = AiClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryProjectAiSessionStore(),
        nowEpochSeconds = { 100 },
    )

    private fun validRequest() = ProjectAiScaffoldRequest(
        templateId = "reviewed-template",
        templateVersion = 3,
        baseProjectRevision = null,
        assignmentBrief = "Compare two measurements in a bounded assignment.",
        researchQuestion = "How does the measured outcome vary?",
        studentQuestion = "Help me define a manageable first step.",
        currentFields = emptyMap(),
        constraints = listOf("Limited time"),
        locale = "en",
        optedIn = true,
        projectDataConsent = true,
        projectDataConsentVersion = ProjectAiScaffoldRules.PROJECT_DATA_CONSENT_VERSION,
    )

    private fun previewJson(
        creditCost: Int,
        operation: String = "create_project",
        projectId: String? = null,
        baseProjectRevision: Int? = null,
    ): String =
        """{"schema":"evidrilo.project-ai-scaffold","version":"1","status":"preview","scaffold":{"templateId":"reviewed-template","templateVersion":3,"promptVersion":"project-scaffold.v1","guidanceText":"Review the framing against your assignment instructions.","fieldSuggestions":[{"fieldId":"question","suggestedValue":"How does the measured outcome vary?"}],"clarificationQuestions":["Which outcome will you measure?"],"recommendedNextPrompts":["What evidence would answer this question?"]},"operation":"$operation","projectId":${projectId?.let { "\"$it\"" } ?: "null"},"baseProjectRevision":${baseProjectRevision ?: "null"},"reasonCode":null,"requestId":"project-ai-request-0001","creditCost":$creditCost}"""

    private class QueueProjectAiTransport(
        private val response: AccountHttpResponse,
    ) : AccountHttpTransport {
        var calls = 0
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
            this.path = url.substringAfter("api.example.test")
            this.headers = headers
            this.body = body
            return response
        }
    }

    private class MemoryProjectAiSessionStore : SecureSessionStore {
        private var session = StoredAccountSession(
            AccountSummary("student-account", emailVerified = true),
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
