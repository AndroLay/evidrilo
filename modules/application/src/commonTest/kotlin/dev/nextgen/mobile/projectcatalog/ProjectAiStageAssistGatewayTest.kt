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
import kotlin.test.assertNull

class ProjectAiStageAssistGatewayTest {
    @Test
    fun `preview sends selected project context through a verified session and parses a bounded proposal`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(200, previewJson()))
        val request = validRequest()

        val result = runSuspendTest {
            gateway(transport).generatePreview(request, "stage_ai_req_0001")
        }

        val preview = assertIs<ProjectAiStageAssistResult.Preview>(result).value
        assertEquals("stage_ai_req_0001", preview.requestId)
        assertEquals(3, preview.creditCost)
        assertEquals("NOT_ASSESSED", preview.evaluationPreview.assessmentStatus)
        assertEquals("revised question", preview.items.single().afterValue)
        assertEquals("evidence_01", preview.items.single().referenceIds.single())
        assertEquals("Bearer access-token", transport.headers["Authorization"])
        assertEquals("stage_ai_req_0001", transport.headers["Idempotency-Key"])
        assertEquals("/v1/project-ai/stage-assist", transport.path)
        assertEquals(true, transport.body?.contains("\"mode\":\"PROJECT\"") == true)
        assertEquals(true, transport.body?.contains("\"selectedFieldIds\":[\"research_question\"]") == true)
        assertEquals(true, transport.body?.contains("\"selectedEvidenceIds\":[\"evidence_01\"]") == true)
        assertEquals(false, transport.body?.contains("old question") == true)
    }

    @Test
    fun `preview rejects output that references unselected evidence`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(
                AccountHttpResponse(200, previewJson(referenceId = "evidence_not_selected")),
            )).generatePreview(validRequest(), "stage_ai_req_0001")
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_RESPONSE"), result)
    }

    @Test
    fun `preview cannot claim selected evidence supports a proposal that does not reference it`() {
        val response = previewJson()
            .replace("\"referenceIds\":[\"evidence_01\"]", "\"referenceIds\":[]")
            .replace("\"proposalsWithoutReferences\":[]", "\"proposalsWithoutReferences\":[\"proposal_01\"]")
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(AccountHttpResponse(200, response)))
                .generatePreview(validRequest(), "stage_ai_req_0001")
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_RESPONSE"), result)
    }

    @Test
    fun `preview rejects a proposal whose before value no longer matches the local stage snapshot`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(
                AccountHttpResponse(200, previewJson(beforeValue = "stale value")),
            )).generatePreview(validRequest(), "stage_ai_req_0001")
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("PROJECT_AI_CONTEXT_STALE"), result)
    }

    @Test
    fun `preview response must bind returned identity to the requested cloud project and stage`() {
        val response = previewJson().replace("\"stageId\":\"question\"", "\"stageId\":\"other_stage\"")
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(AccountHttpResponse(200, response)))
                .generatePreview(validRequest(), "stage_ai_req_0001")
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("PROJECT_AI_CONTEXT_MISMATCH"), result)
    }

    @Test
    fun `unavailable response never becomes an unknown mutation outcome`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(503, """{"code":"PROJECT_AI_NOT_READY"}"""))
        val result = runSuspendTest {
            gateway(transport).generatePreview(validRequest(), "stage_ai_req_0001")
        }

        assertEquals(ProjectAiStageAssistResult.Unavailable("PROJECT_AI_NOT_READY"), result)
    }

    @Test
    fun `transient dispatch failure preserves only the same idempotency key for reconciliation`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(AccountHttpResponse(500, """{"code":"TEMPORARY_ERROR"}""")))
                .generatePreview(validRequest(), "stage_ai_req_0001")
        }

        val failed = assertIs<ProjectAiStageAssistResult.Failed>(result)
        assertEquals(true, failed.outcomeUnknown)
        assertEquals(false, failed.retryable)
        assertEquals(true, failed.sameIntentReplayAllowed)
        assertEquals("stage_ai_req_0001", failed.idempotencyKey)
    }

    @Test
    fun `unverified account does not dispatch selected project context`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(200, previewJson()))
        val result = runSuspendTest {
            gateway(transport, verified = false).generatePreview(validRequest(), "stage_ai_req_0001")
        }

        assertEquals(
            ProjectAiStageAssistResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED),
            result,
        )
        assertEquals(0, transport.calls)
        assertNull(transport.body)
    }

    @Test
    fun `settlement reuses preview request identity and accepts only matching actual cost`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(200, settlementJson()))
        val result = runSuspendTest {
            gateway(transport).settle(
                requestId = "stage_ai_req_0001",
                installationId = INSTALLATION_ID,
                outcome = ProjectAiStageAssistOutcome.APPLIED,
                baseProjectRevision = 7,
                resultProjectRevision = 8,
                expectedCreditCost = 3,
            )
        }

        assertEquals(
            ProjectAiStageAssistSettlementResult.Settled(
                requestId = "stage_ai_req_0001",
                outcome = ProjectAiStageAssistOutcome.APPLIED,
                creditCost = 3,
            ),
            result,
        )
        assertEquals("/v1/project-ai/stage-assist/settlement", transport.path)
        assertEquals("stage_ai_req_0001", transport.headers["Idempotency-Key"])
        assertEquals(true, transport.body?.contains("\"outcome\":\"APPLIED\"") == true)
        assertEquals(true, transport.body?.contains("\"resultProjectRevision\":8") == true)
    }

    @Test
    fun `settlement rejects an applied result without a newer project revision before dispatch`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(200, settlementJson()))
        val result = runSuspendTest {
            gateway(transport).settle(
                requestId = "stage_ai_req_0001",
                installationId = INSTALLATION_ID,
                outcome = ProjectAiStageAssistOutcome.APPLIED,
                baseProjectRevision = 7,
                resultProjectRevision = 7,
                expectedCreditCost = 3,
            )
        }

        assertEquals(
            ProjectAiStageAssistSettlementResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT"),
            result,
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun `settlement rejects a credit cost that differs from the preview`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(AccountHttpResponse(200, settlementJson(creditCost = 4))))
                .settle(
                    requestId = "stage_ai_req_0001",
                    installationId = INSTALLATION_ID,
                    outcome = ProjectAiStageAssistOutcome.APPLIED,
                    baseProjectRevision = 7,
                    resultProjectRevision = 8,
                    expectedCreditCost = 3,
                )
        }

        assertEquals(
            ProjectAiStageAssistSettlementResult.Rejected("PROJECT_AI_SETTLEMENT_CONTEXT_MISMATCH"),
            result,
        )
    }

    private fun gateway(transport: QueueStageAssistTransport, verified: Boolean = true) = ProjectAiStageAssistGateway(
        configuration = AiClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryStageAssistSessionStore(verified),
        nowEpochSeconds = { 100 },
    )

    private fun validRequest() = ProjectAiStageAssistRequest(
        installationId = INSTALLATION_ID,
        cloudProjectId = PROJECT_ID,
        templateId = "reviewed_template",
        templateVersion = 2,
        stageId = "question",
        operationId = "refine_question",
        baseCloudProjectRevision = 7,
        selectedFieldValues = mapOf("research_question" to "old question"),
        selectedEvidence = listOf(
            ProjectAiStageAssistSelectedEvidence("evidence_01", "DATA", "Collected data"),
        ),
        locale = "en-US",
    )

    private fun previewJson(
        referenceId: String = "evidence_01",
        beforeValue: String = "old question",
    ): String = """
        {
          "schema":"evidrilo.project-ai-stage-assist",
          "version":"1",
          "status":"preview",
          "mode":"PROJECT",
          "projectId":"$PROJECT_ID",
          "baseProjectRevision":7,
          "stageId":"question",
          "operationId":"refine_question",
          "assist":{
            "templateId":"reviewed_template",
            "templateVersion":2,
            "promptVersion":"project-ai-stage-assist.v1",
            "items":[{
              "id":"proposal_01",
              "kind":"PROPOSAL",
              "text":null,
              "targetFieldId":"research_question",
              "beforeValue":"$beforeValue",
              "afterValue":"revised question",
              "referenceIds":["$referenceId"],
              "assumptions":[],
              "uncertainties":[],
              "knownLimits":[]
            }],
            "reportedConflicts":[],
            "reportedOutOfScopeItems":[]
          },
          "evaluationPreview":{
            "assessmentStatus":"NOT_ASSESSED",
            "supportingItems":[{"id":"evidence_01","kind":"DATA","label":"Collected data"}],
            "proposalsWithoutReferences":[],
            "reportedConflicts":[],
            "reportedAssumptions":[],
            "reportedUncertainties":[],
            "reportedKnownLimits":[],
            "templateLimits":[],
            "reportedOutOfScopeItems":[],
            "checksUnavailable":["ACADEMIC_TRUTH","SEMANTIC_REFERENCE_SUPPORT","SOURCE_QUALITY","POST_APPLY_STRUCTURE"]
          },
          "requestId":"stage_ai_req_0001",
          "creditCost":3
        }
    """.trimIndent()

    private fun settlementJson(creditCost: Int = 3): String = """
        {"schema":"evidrilo.project-ai-stage-assist-settlement","version":"1","outcome":"APPLIED","requestId":"stage_ai_req_0001","creditCost":$creditCost}
    """.trimIndent()

    private class QueueStageAssistTransport(
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

    private class MemoryStageAssistSessionStore(verified: Boolean) : SecureSessionStore {
        private var session = StoredAccountSession(
            AccountSummary("student-account", emailVerified = verified),
            SecureSessionMaterial("access-token", expiresAtEpochSeconds = 500),
        )

        override fun read(): StoredAccountSession = session
        override fun write(session: StoredAccountSession) { this.session = session }
        override fun clear() { session = MemoryStageAssistSessionStore(false).session }
    }

    private companion object {
        const val INSTALLATION_ID = "11111111-1111-4111-8111-111111111111"
        const val PROJECT_ID = "22222222-2222-4222-8222-222222222222"
    }
}
