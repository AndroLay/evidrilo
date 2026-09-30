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
            gateway(transport).generatePreview(request, "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        val preview = assertIs<ProjectAiStageAssistResult.Preview>(result).value
        assertEquals("stage_ai_req_0001", preview.requestId)
        assertEquals(3, preview.creditCost)
        assertEquals(4, preview.consentGeneration)
        assertEquals("NOT_ASSESSED", preview.evaluationPreview.assessmentStatus)
        assertEquals("revised question", preview.items.single().afterValue)
        assertEquals("evidence_01", preview.items.single().referenceIds.single())
        assertEquals("Bearer access-token", transport.headers["Authorization"])
        assertEquals("stage_ai_req_0001", transport.headers["Idempotency-Key"])
        assertEquals("project-ai.v1", transport.headers["X-Evidrilo-Project-AI-Consent"])
        assertEquals("/v1/project-ai/stage-assist", transport.path)
        assertEquals(true, transport.body?.contains("\"mode\":\"PROJECT\"") == true)
        assertEquals(true, transport.body?.contains("\"selectedFieldIds\":[\"research_question\"]") == true)
        assertEquals(true, transport.body?.contains("\"selectedFields\":[{\"id\":\"research_question\",\"value\":\"old question\"}]") == true)
        assertEquals(true, transport.body?.contains("\"selectedEvidenceIds\":[\"evidence_01\"]") == true)
        assertEquals(true, transport.body?.contains("\"selectedEvidence\":[{\"id\":\"evidence_01\",\"kind\":\"DATA\",\"label\":\"Collected data\",\"summary\":\"Only the selected observation\",\"origin\":\"Student project\"}]") == true)
        assertEquals(false, transport.body?.contains("Unselected private note") == true)
    }

    @Test
    fun `preview rejects output that references unselected evidence`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(
                AccountHttpResponse(200, previewJson(referenceId = "evidence_not_selected")),
            )).generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
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
                .generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("INVALID_PROJECT_AI_STAGE_ASSIST_RESPONSE"), result)
    }

    @Test
    fun `preview rejects a proposal whose before value no longer matches the local stage snapshot`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(
                AccountHttpResponse(200, previewJson(beforeValue = "stale value")),
            )).generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("PROJECT_AI_CONTEXT_STALE"), result)
    }

    @Test
    fun `preview response must bind returned identity to the requested cloud project and stage`() {
        val response = previewJson().replace("\"stageId\":\"question\"", "\"stageId\":\"other_stage\"")
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(AccountHttpResponse(200, response)))
                .generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("PROJECT_AI_CONTEXT_MISMATCH"), result)
    }

    @Test
    fun `local project context registration sends metadata only under the verified account session`() {
        val response = """
            {"schema":"evidrilo.project-ai-local-project-context","version":"1","projectId":"$PROJECT_ID","projectRevision":7,"bindingGeneration":5,"templateId":"reviewed_template","templateVersion":2,"status":"registered","requestId":"server_req_0001"}
        """.trimIndent()
        val transport = QueueStageAssistTransport(AccountHttpResponse(200, response))

        val result = runSuspendTest {
            gateway(transport).registerLocalProjectContext(
                projectId = PROJECT_ID,
                installationId = INSTALLATION_ID,
                templateId = "reviewed_template",
                templateVersion = 2,
                projectRevision = 7,
                availableEvidenceIds = listOf("evidence_01", "source_01"),
                explicitlyConfirmedForRequest = true,
            )
        }

        assertEquals(ProjectAiLocalContextBindingResult.Registered(7, 5), result)
        assertEquals("PUT", transport.method)
        assertEquals("/v1/project-ai/projects/$PROJECT_ID/local-context", transport.path)
        assertEquals("Bearer access-token", transport.headers["Authorization"])
        assertEquals("project-ai.v1", transport.headers["X-Evidrilo-Project-AI-Consent"])
        assertEquals(true, transport.body?.contains("\"availableEvidenceIds\":[\"evidence_01\",\"source_01\"]") == true)
        assertEquals(false, transport.body?.contains("question text") == true)
        assertEquals(false, transport.body?.contains("summary") == true)
    }

    @Test
    fun `local project context registration fails closed for invalid identity and consent`() {
        val invalidTransport = QueueStageAssistTransport(AccountHttpResponse(200, "{}"))
        val invalid = runSuspendTest {
            gateway(invalidTransport).registerLocalProjectContext(
                projectId = "not-a-uuid",
                installationId = INSTALLATION_ID,
                templateId = "reviewed_template",
                templateVersion = 2,
                projectRevision = 7,
                availableEvidenceIds = emptyList(),
                explicitlyConfirmedForRequest = true,
            )
        }
        val consentTransport = QueueStageAssistTransport(AccountHttpResponse(403, """{"code":"PROJECT_AI_CONSENT_REQUIRED"}"""))
        val consent = runSuspendTest {
            gateway(consentTransport).registerLocalProjectContext(
                projectId = PROJECT_ID,
                installationId = INSTALLATION_ID,
                templateId = "reviewed_template",
                templateVersion = 2,
                projectRevision = 7,
                availableEvidenceIds = emptyList(),
                explicitlyConfirmedForRequest = true,
            )
        }

        assertEquals(ProjectAiLocalContextBindingResult.Rejected("INVALID_PROJECT_AI_CONTEXT"), invalid)
        assertEquals(0, invalidTransport.calls)
        assertEquals(ProjectAiLocalContextBindingResult.Rejected("PROJECT_AI_CONSENT_REQUIRED"), consent)
    }

    @Test
    fun `unavailable response never becomes an unknown mutation outcome`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(503, """{"code":"PROJECT_AI_NOT_READY"}"""))
        val result = runSuspendTest {
            gateway(transport).generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        assertEquals(ProjectAiStageAssistResult.Unavailable("PROJECT_AI_NOT_READY"), result)
    }

    @Test
    fun `withheld preview exposes verified provider usage cost without returning content`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(
                AccountHttpResponse(
                    403,
                    """{"code":"PROJECT_AI_CONSENT_REVOKED_AFTER_PROVIDER","creditCost":2}""",
                ),
            )).generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        assertEquals(
            ProjectAiStageAssistResult.Rejected("PROJECT_AI_CONSENT_REVOKED_AFTER_PROVIDER", creditCost = 2),
            result,
        )
    }

    @Test
    fun `transient dispatch failure preserves only the same idempotency key for reconciliation`() {
        val result = runSuspendTest {
            gateway(QueueStageAssistTransport(AccountHttpResponse(500, """{"code":"TEMPORARY_ERROR"}""")))
                .generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        val failed = assertIs<ProjectAiStageAssistResult.Failed>(result)
        assertEquals(true, failed.outcomeUnknown)
        assertEquals(false, failed.retryable)
        assertEquals(true, failed.sameIntentReplayAllowed)
        assertEquals("stage_ai_req_0001", failed.idempotencyKey)
    }

    @Test
    fun `stale and invalid requests are rejected instead of treated as unknown outcomes`() {
        listOf(
            Triple(409, "PROJECT_AI_CONTEXT_STALE", 2),
            Triple(422, "INVALID_PROJECT_AI_STAGE_ASSIST", null),
        ).forEach { (statusCode, errorCode, creditCost) ->
            val result = runSuspendTest {
                gateway(
                    QueueStageAssistTransport(
                        AccountHttpResponse(
                            statusCode,
                            "{\"code\":\"$errorCode\"${creditCost?.let { ",\"creditCost\":$it" }.orEmpty()}}",
                        ),
                    ),
                ).generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
            }

            assertEquals(ProjectAiStageAssistResult.Rejected(errorCode, creditCost), result)
        }
    }

    @Test
    fun `unverified account does not dispatch selected project context`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(200, previewJson()))
        val result = runSuspendTest {
            gateway(transport, verified = false).generatePreview(validRequest(), "stage_ai_req_0001", explicitlyConfirmedForRequest = true)
        }

        assertEquals(
            ProjectAiStageAssistResult.Deferred(ProjectAiStageAssistDeferredReason.AUTH_REQUIRED),
            result,
        )
        assertEquals(0, transport.calls)
        assertNull(transport.body)
    }

    @Test
    fun `stage assistance is not dispatched without request-specific consent`() {
        val transport = QueueStageAssistTransport(AccountHttpResponse(200, previewJson()))

        val result = runSuspendTest {
            gateway(transport).generatePreview(
                validRequest(),
                "stage_ai_req_0001",
                explicitlyConfirmedForRequest = false,
            )
        }

        assertEquals(ProjectAiStageAssistResult.Rejected("PROJECT_AI_REQUEST_CONSENT_REQUIRED"), result)
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
                baseProjectBindingGeneration = 5,
                resultProjectRevision = 8,
                resultProjectBindingGeneration = 6,
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
                baseProjectBindingGeneration = 5,
                resultProjectRevision = 7,
                resultProjectBindingGeneration = 6,
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
                    baseProjectBindingGeneration = 5,
                    resultProjectRevision = 8,
                    resultProjectBindingGeneration = 6,
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
        projectId = PROJECT_ID,
        templateId = "reviewed_template",
        templateVersion = 2,
        stageId = "question",
        operationId = "refine_question",
        baseProjectRevision = 7,
        baseProjectBindingGeneration = 5,
        selectedFieldValues = mapOf("research_question" to "old question"),
        selectedEvidence = listOf(
            ProjectAiStageAssistSelectedEvidence(
                "evidence_01",
                "DATA",
                "Collected data",
                summary = "Only the selected observation",
                origin = "Student project",
            ),
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
            "projectBindingGeneration":5,
            "consentGeneration":4,
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
