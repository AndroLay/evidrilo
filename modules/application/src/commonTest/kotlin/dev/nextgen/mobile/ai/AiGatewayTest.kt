package dev.nextgen.mobile.ai

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

class AiGatewayTest {
    @Test
    fun accumulated_monthly_credits_above_the_first_allowance_are_usable() {
        val transport = QueueAiTransport(AccountHttpResponse(200, """
            {"schema":"evidrilo.ai-credits","version":"1","consentRecorded":true,"available":418,
            "grants":[
              {"grantKind":"free_once","grantKey":"once","granted":20,"reserved":0,"consumed":2,"available":18,"expiresAt":null},
              {"grantKind":"subscription_month","grantKey":"period-1","granted":200,"reserved":0,"consumed":0,"available":200,"expiresAt":null},
              {"grantKind":"subscription_month","grantKey":"period-2","granted":200,"reserved":0,"consumed":0,"available":200,"expiresAt":null}
            ],"requestId":"req-ai-accumulated"}
        """.trimIndent()))
        val result = assertIs<AiGatewayResult.CreditsFound>(runSuspendTest { gateway(transport).getCredits() })
        assertEquals(418, result.value.available)
        assertEquals(3, result.value.grants.size)
    }
    @Test
    fun credits_parse_and_assist_sends_bounded_opted_in_request() {
        val transport = QueueAiTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.ai-credits","version":"1","consentRecorded":true,"available":19,"grants":[{"grantKind":"free_once","grantKey":"free_once","granted":20,"reserved":1,"consumed":0,"available":19,"expiresAt":null}],"requestId":"req-ai-001"}
                """.trimIndent(),
            ),
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.ai-assist-result","version":"1","status":"success","text":"The comparison is limited to the supplied observations.","reasonCode":null,"promptVersion":"assist.v1","groundedAnchorIds":["OBS-01"],"requestId":"req-ai-002"}
                """.trimIndent(),
            ),
        )
        val gateway = gateway(transport)

        val credits = assertIs<AiGatewayResult.CreditsFound>(runSuspendTest { gateway.getCredits() }).value
        val result = assertIs<AiGatewayResult.AssistFound>(
            runSuspendTest {
                gateway.assist(
                    AiAssistRequest(
                        purpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
                        input = "Feedback: connect the claim to OBS-01 and OBS-02.",
                        locale = "en-US",
                        optedIn = true,
                        context = AiAssistContext(
                            caseVersionId = "M0_T2:1",
                            feedbackCode = "MISSING_EVIDENCE",
                            feedbackStatus = "ACTION_REQUIRED",
                            anchorIds = listOf("OBS-01"),
                            limitationIds = listOf("LIMIT-01"),
                            claimText = "The claim stays bounded.",
                            claimScope = "LIMITED_COMPARISON",
                            nextAction = "State the limitation.",
                        ),
                    ),
                    idempotencyKey = "ai-request-001",
                )
            },
        ).value

        assertEquals(19, credits.available)
        assertEquals("The comparison is limited to the supplied observations.", result.text)
        assertEquals("GET", transport.requests[0].method)
        assertEquals("/v1/ai/credits", transport.requests[0].path)
        assertEquals("POST", transport.requests[1].method)
        assertEquals("/v1/ai/assist", transport.requests[1].path)
        assertEquals("ai-request-001", transport.requests[1].headers["Idempotency-Key"])
        assertTrue(transport.requests[1].body.contains("explain_feedback"))
        assertTrue(transport.requests[1].body.contains("\"optedIn\":true"))
        assertTrue(transport.requests[1].body.contains("M0_T2:1"))
        assertTrue(transport.requests[1].body.contains("MISSING_EVIDENCE"))
    }

    @Test
    fun `credits parser accepts the full free and active pro allowances`() {
        val transport = QueueAiTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.ai-credits","version":"1","consentRecorded":true,"available":220,"grants":[{"grantKind":"free_once","grantKey":"free_once","granted":20,"reserved":0,"consumed":0,"available":20,"expiresAt":null},{"grantKind":"subscription_month","grantKey":"2026-09","granted":200,"reserved":0,"consumed":0,"available":200,"expiresAt":"2026-10-01T00:00:00Z"}],"requestId":"req-ai-full-allowance"}
                """.trimIndent(),
            ),
        )

        val credits = assertIs<AiGatewayResult.CreditsFound>(runSuspendTest { gateway(transport).getCredits() }).value

        assertEquals(220, credits.available)
        assertEquals(listOf(20, 200), credits.grants.map(AiCreditGrant::granted))
    }

    @Test
    fun `assist requests and parses the negotiated server calculated charge`() {
        val transport = QueueAiTransport(
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.ai-assist-result","version":"2","status":"success","text":"A grounded explanation.","reasonCode":null,"promptVersion":"assist.v1","groundedAnchorIds":["OBS-01"],"requestId":"req-ai-cost-001","creditCost":4}""",
            ),
        )

        val result = runSuspendTest {
            gateway(transport).assist(
                AiAssistRequest(AiAssistPurpose.EXPLAIN_FEEDBACK, "Explain this feedback.", "en-US", optedIn = true),
                "ai-request-cost-001",
            )
        }

        val assist = assertIs<AiGatewayResult.AssistFound>(result).value
        assertEquals(4, assist.creditCost)
        assertEquals("application/vnd.evidrilo.ai-assist-result.v2+json", transport.requests.single().headers["Accept"])
    }

    @Test
    fun ai_gateway_requires_verified_session_and_explicit_consent() {
        val transport = QueueAiTransport(AccountHttpResponse(200, "{}"))
        val noSession = runSuspendTest { gateway(transport, session = null).getCredits() }
        assertEquals(AiDeferralReason.AUTH_REQUIRED, assertIs<AiGatewayResult.Deferred>(noSession).reason)
        assertTrue(transport.requests.isEmpty())

        val notOptedIn = runSuspendTest {
            gateway(transport).assist(
                AiAssistRequest(AiAssistPurpose.EXPLAIN_FEEDBACK, "feedback", "en-US", optedIn = false),
                "ai-request-002",
            )
        }
        assertEquals("AI_OPT_IN_REQUIRED", assertIs<AiGatewayResult.Fallback>(notOptedIn).reasonCode)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun ai_gateway_maps_provider_disabled_and_malformed_responses_without_inventing_text() {
        val fallback = runSuspendTest {
            gateway(
                QueueAiTransport(
                    AccountHttpResponse(
                        200,
                        """{"schema":"evidrilo.ai-assist-result","version":"1","status":"fallback","text":null,"reasonCode":"AI_PROVIDER_UNAVAILABLE","promptVersion":"assist.v1","groundedAnchorIds":[],"requestId":"req-ai-003"}""",
                    ),
                ),
            ).assist(
                AiAssistRequest(AiAssistPurpose.EXPLAIN_FEEDBACK, "feedback", "en-US", optedIn = true),
                "ai-request-003",
            )
        }
        val fallbackValue = assertIs<AiGatewayResult.Fallback>(fallback)
        assertEquals("AI_PROVIDER_UNAVAILABLE", fallbackValue.reasonCode)
        assertFalse(fallbackValue.text != null)

        val malformed = runSuspendTest {
            gateway(QueueAiTransport(AccountHttpResponse(200, "{}"))).getCredits()
        }
        assertEquals("INVALID_AI_CREDITS_RESPONSE", assertIs<AiGatewayResult.Failed>(malformed).code)
    }

    @Test
    fun ai_transport_failure_is_not_misreported_as_device_offline_when_online() {
        val online = runSuspendTest {
            gateway(FailingAiTransport(DeviceConnectivity.ONLINE)).getCredits()
        }
        val offline = runSuspendTest {
            gateway(FailingAiTransport(DeviceConnectivity.OFFLINE)).getCredits()
        }

        assertEquals("AI_UNAVAILABLE", assertIs<AiGatewayResult.Failed>(online).code)
        assertEquals("AI_OFFLINE", assertIs<AiGatewayResult.Failed>(offline).code)
    }

    @Test
    fun lost_ai_mutation_response_is_outcome_unknown_and_retains_only_same_intent_key() {
        val result = runSuspendTest {
            gateway(FailingAiTransport(DeviceConnectivity.ONLINE)).assist(
                AiAssistRequest(AiAssistPurpose.EXPLAIN_FEEDBACK, "feedback", "en-US", optedIn = true),
                "ai-request-same-intent",
            )
        }
        val failure = assertIs<AiGatewayResult.Failed>(result)

        assertEquals("AI_OUTCOME_UNKNOWN", failure.code)
        assertFalse(failure.retryable)
        assertTrue(failure.outcomeUnknown)
        assertTrue(failure.reconciliationRequired)
        assertTrue(failure.sameIntentReplayAllowed)
        assertEquals("ai-request-same-intent", failure.idempotencyKey)
    }

    @Test
    fun conversation_start_posts_the_explicitly_opted_in_case_context() {
        val transport = QueueAiTransport(
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.ai-conversation-session","version":"1","status":"active","sessionId":"123e4567-e89b-42d3-a456-426614174111","caseVersionId":"M0_T2:1","contextFingerprint":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","turnLimit":5,"turnsUsed":0,"expiresAt":"2026-09-28T12:00:00Z","requestId":"req-ai-010"}""",
            ),
        )

        val result = runSuspendTest {
            gateway(transport).startConversation(
                context = conversationContext(),
                learnerLimitation = "Stirring was not controlled.",
                locale = "en-US",
                optedIn = true,
                idempotencyKey = "ai-conversation-start-001",
            )
        }

        val session = assertIs<AiConversationGatewayResult.SessionStarted>(result).value
        assertEquals("123e4567-e89b-42d3-a456-426614174111", session.sessionId)
        assertEquals("M0_T2:1", session.caseVersionId)
        assertEquals(5, session.turnLimit)
        assertEquals(0, session.turnsUsed)
        assertEquals("POST", transport.requests.single().method)
        assertEquals("/v1/ai/conversations", transport.requests.single().path)
        assertEquals("ai-conversation-start-001", transport.requests.single().headers["Idempotency-Key"])
        assertTrue(transport.requests.single().body.contains("\"optedIn\":true"))
        assertTrue(transport.requests.single().body.contains("\"caseVersionId\":\"M0_T2:1\""))
        assertTrue(transport.requests.single().body.contains("Stirring was not controlled."))
    }

    @Test
    fun conversation_turn_sends_only_bounded_history_and_keeps_proposal_unapplied() {
        val transport = QueueAiTransport(
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.ai-conversation-turn","version":"1","status":"success","kind":"draft_proposal","text":"Consider narrowing the claim to this comparison.","groundedAnchorIds":["OBS-01"],"proposal":{"field":"claim_text","beforeValue":"The tablet dissolved faster because of heat.","suggestedValue":"The observations show a shorter dissolve time in the warm-water trial.","anchorIds":["OBS-01"]},"autoApplied":false,"turnsUsed":2,"turnsRemaining":3,"requestId":"req-ai-011"}""",
            ),
        )

        val result = runSuspendTest {
            gateway(transport).sendConversationTurn(
                sessionId = "123e4567-e89b-42d3-a456-426614174111",
                purpose = AiAssistPurpose.LANGUAGE_ALTERNATIVE,
                input = "Can you help me narrow this claim?",
                locale = "en-US",
                optedIn = true,
                context = conversationContext(),
                learnerLimitation = "Stirring was not controlled.",
                history = listOf(
                    AiConversationHistoryMessage("user", "Why is my causal claim too broad?"),
                    AiConversationHistoryMessage("assistant", "The comparison does not isolate temperature.", listOf("OBS-01")),
                ),
                idempotencyKey = "ai-conversation-turn-002",
            )
        }

        val turn = assertIs<AiConversationGatewayResult.TurnReceived>(result).value
        assertEquals("draft_proposal", turn.kind)
        assertEquals("claim_text", turn.proposal?.field)
        assertEquals(3, turn.turnsRemaining)
        assertFalse(turn.autoApplied)
        val request = transport.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/v1/ai/conversations/123e4567-e89b-42d3-a456-426614174111/turns", request.path)
        assertEquals("ai-conversation-turn-002", request.headers["Idempotency-Key"])
        assertTrue(request.body.contains("Can you help me narrow this claim?"))
        assertTrue(request.body.contains("\"role\":\"assistant\""))
        assertTrue(request.body.contains("\"groundedAnchorIds\":[\"OBS-01\"]"))
    }

    @Test
    fun `conversation turn exposes settled server cost and fallback cost`() {
        val transport = QueueAiTransport(
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.ai-conversation-turn","version":"2","status":"success","kind":"explanation","text":"The observation supports only a bounded comparison.","groundedAnchorIds":["OBS-01"],"autoApplied":false,"turnsUsed":1,"turnsRemaining":4,"requestId":"req-ai-cost-002","creditCost":4}""",
            ),
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.ai-conversation-turn","version":"2","status":"fallback","reasonCode":"AI_CONVERSATION_SESSION_EXPIRED","groundedAnchorIds":[],"autoApplied":false,"turnsUsed":0,"turnsRemaining":5,"requestId":"req-ai-cost-003","creditCost":2}""",
            ),
        )
        val gateway = gateway(transport)

        val success = runSuspendTest {
            gateway.sendConversationTurn(
                sessionId = "123e4567-e89b-42d3-a456-426614174111",
                purpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
                input = "Explain this result.",
                locale = "en-US",
                optedIn = true,
                context = conversationContext(),
                learnerLimitation = null,
                history = emptyList(),
                idempotencyKey = "ai-conversation-cost-001",
            )
        }
        val received = assertIs<AiConversationGatewayResult.TurnReceived>(success).value

        assertEquals(4, received.creditCost)
        assertEquals(
            "application/vnd.evidrilo.ai-conversation-turn.v2+json",
            transport.requests.first().headers["Accept"],
        )

        val fallback = runSuspendTest {
            gateway.sendConversationTurn(
                sessionId = "123e4567-e89b-42d3-a456-426614174111",
                purpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
                input = "Explain this result.",
                locale = "en-US",
                optedIn = true,
                context = conversationContext(),
                learnerLimitation = null,
                history = emptyList(),
                idempotencyKey = "ai-conversation-cost-002",
            )
        }

        assertEquals(2, assertIs<AiConversationGatewayResult.Fallback>(fallback).creditCost)
    }

    @Test
    fun conversation_turn_rejects_unknown_anchors_and_never_accepts_auto_applied_output() {
        val unknownAnchor = """{"schema":"evidrilo.ai-conversation-turn","version":"1","status":"success","kind":"explanation","text":"Unsupported answer.","groundedAnchorIds":["FOREIGN-01"],"autoApplied":false,"turnsUsed":1,"turnsRemaining":4,"requestId":"req-ai-012"}"""
        val autoApplied = """{"schema":"evidrilo.ai-conversation-turn","version":"1","status":"success","kind":"explanation","text":"Unsupported automatic change.","groundedAnchorIds":["OBS-01"],"autoApplied":true,"turnsUsed":1,"turnsRemaining":4,"requestId":"req-ai-013"}"""

        listOf(unknownAnchor, autoApplied).forEach { responseBody ->
            val result = runSuspendTest {
                gateway(QueueAiTransport(AccountHttpResponse(200, responseBody))).sendConversationTurn(
                    sessionId = "123e4567-e89b-42d3-a456-426614174111",
                    purpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
                    input = "Explain this result.",
                    locale = "en-US",
                    optedIn = true,
                    context = conversationContext(),
                    learnerLimitation = null,
                    history = emptyList(),
                    idempotencyKey = "ai-conversation-invalid-001",
                )
            }

            assertEquals(
                "INVALID_AI_CONVERSATION_TURN_RESPONSE",
                assertIs<AiConversationGatewayResult.Failed>(result).error.code,
            )
        }
    }

    @Test
    fun conversation_proposal_allows_an_explicit_null_before_value_and_remains_unapplied() {
        val response = """{"schema":"evidrilo.ai-conversation-turn","version":"1","status":"success","kind":"draft_proposal","text":"Try a bounded observation statement.","groundedAnchorIds":["OBS-01"],"proposal":{"field":"claim_text","beforeValue":null,"suggestedValue":"The warm-water trial dissolved the tablet in less time.","anchorIds":["OBS-01"]},"autoApplied":false,"turnsUsed":1,"turnsRemaining":4,"requestId":"req-ai-015"}"""
        val context = conversationContext().copy(claimText = null)

        val result = runSuspendTest {
            gateway(QueueAiTransport(AccountHttpResponse(200, response))).sendConversationTurn(
                sessionId = "123e4567-e89b-42d3-a456-426614174111",
                purpose = AiAssistPurpose.LANGUAGE_ALTERNATIVE,
                input = "Help me write a first claim.",
                locale = "en-US",
                optedIn = true,
                context = context,
                learnerLimitation = null,
                history = emptyList(),
                idempotencyKey = "ai-conversation-turn-003",
            )
        }

        val turn = assertIs<AiConversationGatewayResult.TurnReceived>(result).value
        assertEquals(null, turn.proposal?.beforeValue)
        assertFalse(turn.autoApplied)
    }

    @Test
    fun conversation_clear_treats_an_already_missing_session_as_cleared_locally() {
        val transport = QueueAiTransport(AccountHttpResponse(404, ""))

        val result = runSuspendTest {
            gateway(transport).clearConversation("123e4567-e89b-42d3-a456-426614174111")
        }

        assertEquals("123e4567-e89b-42d3-a456-426614174111", assertIs<AiConversationGatewayResult.Cleared>(result).sessionId)
    }

    @Test
    fun conversation_history_is_bounded_and_rejects_unanchored_assistant_messages_locally() {
        val tooMany = List(5) { AiConversationHistoryMessage("user", "Question $it") }
        val fabricated = listOf(AiConversationHistoryMessage("assistant", "Fabricated fact.", listOf("OTHER-01")))
        val first = runSuspendTest {
            gateway(QueueAiTransport()).sendConversationTurn(
                "123e4567-e89b-42d3-a456-426614174111",
                AiAssistPurpose.EXPLAIN_FEEDBACK,
                "Explain the feedback.",
                "en-US",
                true,
                conversationContext(),
                null,
                tooMany,
                "ai-conversation-turn-004",
            )
        }
        val second = runSuspendTest {
            gateway(QueueAiTransport()).sendConversationTurn(
                "123e4567-e89b-42d3-a456-426614174111",
                AiAssistPurpose.EXPLAIN_FEEDBACK,
                "Explain the feedback.",
                "en-US",
                true,
                conversationContext(),
                null,
                fabricated,
                "ai-conversation-turn-005",
            )
        }

        assertEquals("INVALID_AI_CONVERSATION_REQUEST", assertIs<AiConversationGatewayResult.Fallback>(first).reasonCode)
        assertEquals("INVALID_AI_CONVERSATION_REQUEST", assertIs<AiConversationGatewayResult.Fallback>(second).reasonCode)
    }

    @Test
    fun clearing_a_conversation_uses_its_session_id_and_does_not_clear_draft_state() {
        val transport = QueueAiTransport(
            AccountHttpResponse(
                200,
                """{"schema":"evidrilo.ai-conversation-clear","version":"1","status":"cleared","sessionId":"123e4567-e89b-42d3-a456-426614174111","requestId":"req-ai-014"}""",
            ),
        )

        val result = runSuspendTest {
            gateway(transport).clearConversation(sessionId = "123e4567-e89b-42d3-a456-426614174111")
        }

        assertIs<AiConversationGatewayResult.Cleared>(result)
        assertEquals("DELETE", transport.requests.single().method)
        assertEquals("/v1/ai/conversations/123e4567-e89b-42d3-a456-426614174111", transport.requests.single().path)
        assertFalse("Idempotency-Key" in transport.requests.single().headers)
        assertTrue(transport.requests.single().body.isEmpty())
    }

    @Test
    fun conversation_calls_require_explicit_consent_and_reject_malformed_session_ids_locally() {
        val transport = QueueAiTransport(AccountHttpResponse(200, "{}"))
        val notOptedIn = runSuspendTest {
            gateway(transport).startConversation(
                context = conversationContext(),
                learnerLimitation = null,
                locale = "en-US",
                optedIn = false,
                idempotencyKey = "ai-conversation-start-002",
            )
        }
        val malformedSession = runSuspendTest {
            gateway(transport).clearConversation("not-a-guid")
        }

        assertEquals("AI_OPT_IN_REQUIRED", assertIs<AiConversationGatewayResult.Fallback>(notOptedIn).reasonCode)
        assertEquals("INVALID_AI_CONVERSATION_REQUEST", assertIs<AiConversationGatewayResult.Fallback>(malformedSession).reasonCode)
        assertTrue(transport.requests.isEmpty())
    }

    private fun conversationContext() = AiAssistContext(
        caseVersionId = "M0_T2:1",
        feedbackCode = "MISSING_EVIDENCE",
        feedbackStatus = "ACTION_REQUIRED",
        anchorIds = listOf("OBS-01"),
        limitationIds = listOf("LIMIT-01"),
        claimText = "The tablet dissolved faster because of heat.",
        claimScope = "GENERAL_CAUSAL_CLAIM",
        nextAction = "Control stirring in a repeat trial.",
    )

    private fun gateway(
        transport: AccountHttpTransport,
        session: StoredAccountSession? = verifiedSession(),
    ): AiGateway = AiGateway(
        configuration = AiClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryAiSecureStore(session),
        nowEpochSeconds = { 100 },
    )

    private fun verifiedSession(): StoredAccountSession = StoredAccountSession(
        AccountSummary("123e4567-e89b-42d3-a456-426614174002", true),
        SecureSessionMaterial("access-token", 200),
    )
}

private data class AiRequestRecord(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private class QueueAiTransport(
    private vararg val responses: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<AiRequestRecord>()
    private var index = 0

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += AiRequestRecord(method, url.substringAfter("api.example.test"), headers, body)
        return responses[index++]
    }
}

private class FailingAiTransport(
    override val deviceConnectivity: DeviceConnectivity,
) : AccountHttpTransport {
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse = error("Synthetic transport failure")
}

private class MemoryAiSecureStore(
    private var value: StoredAccountSession?,
) : SecureSessionStore {
    override fun read(): StoredAccountSession? = value
    override fun write(session: StoredAccountSession) { value = session }
    override fun clear() { value = null }
}
