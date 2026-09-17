package dev.nextgen.mobile.content

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PublishedCaseGatewayTest {
    @Test
    fun reader_requires_a_verified_session_and_parses_only_published_metadata() {
        val transport = FakeContentTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.case-summary","version":"1","caseId":"case-1","caseVersionId":"case-1:v1","title":"A bounded case","contentHash":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","evaluatorVersion":"evaluator.v1","skillTags":["evidence","limits"],"objective":"Connect evidence to a bounded claim","difficulty":2,"evidenceReferences":["fact-observation"],"facts":[{"id":"fact-observation","type":"observation","text":"The tablet was cold."},{"id":"fact-limitation","type":"limitation","text":"Temperature was not controlled."}],"rules":[{"id":"rule-1","outcome":"PASS","anchorIds":["fact-observation"]}],"variants":[{"id":"challenge-1","removedFactIds":["fact-observation"]}],"requestId":"req-case-001"}
                """.trimIndent(),
            ),
        )
        val gateway = gateway(transport)

        val result = runSuspendTest { gateway.get("case-1:v1") }
        val found = assertIs<PublishedCaseGatewayResult.Found>(result)

        assertEquals("case-1:v1", found.case.caseVersionId)
        assertEquals(listOf("evidence", "limits"), found.case.skillTags)
        assertEquals("Connect evidence to a bounded claim", found.case.objective)
        assertEquals(2, found.case.difficulty)
        assertEquals("observation", found.case.facts[0].type)
        assertEquals("PASS", found.case.rules[0].outcome)
        assertEquals("challenge-1", found.case.variants[0].id)
        assertEquals("Bearer access-token", transport.requests.single().headers["Authorization"])
        assertFalse(transport.requests.single().body.contains("claimText"))

        val unauthorized = runSuspendTest {
            PublishedCaseGateway(
                configuration = ContentClientConfiguration("https://api.example.test"),
                transport = transport,
                secureSessionStore = MemoryContentSecureStore(null),
                nowEpochSeconds = { 100 },
            ).get("case-1:v1")
        }
        assertEquals(
            ContentDeferralReason.AUTH_REQUIRED,
            assertIs<PublishedCaseGatewayResult.Deferred>(unauthorized).reason,
        )
    }

    @Test
    fun reader_rejects_an_unverified_session_before_network_access() {
        val transport = FakeContentTransport(AccountHttpResponse(200, "{}"))

        val result = runSuspendTest {
            gateway(transport, verified = false).get("case-1:v1")
        }

        val deferred = assertIs<PublishedCaseGatewayResult.Deferred>(result)
        assertEquals(ContentDeferralReason.AUTH_REQUIRED, deferred.reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun invalid_or_retired_response_is_not_exposed_as_content() {
        val invalid = runSuspendTest {
            gateway(FakeContentTransport(AccountHttpResponse(200, "{}"))).get("case-1:v1")
        }
        assertEquals("INVALID_CONTENT_RESPONSE", assertIs<PublishedCaseGatewayResult.Failed>(invalid).code)

        val retired = runSuspendTest {
            gateway(FakeContentTransport(AccountHttpResponse(404, "{}"))).get("case-1:v1")
        }
        assertEquals("CASE_NOT_FOUND", assertIs<PublishedCaseGatewayResult.Failed>(retired).code)
        assertFalse(assertIs<PublishedCaseGatewayResult.Failed>(retired).retryable)
    }

    @Test
    fun reader_rejects_a_response_for_a_different_case_version() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.case-summary","version":"1","caseId":"case-1","caseVersionId":"case-1:v2","title":"A bounded case","contentHash":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","evaluatorVersion":"evaluator.v1","skillTags":["evidence"],"requestId":"req-case-002"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeContentTransport(response)).get("case-1:v1")
        }

        assertEquals("INVALID_CONTENT_RESPONSE", assertIs<PublishedCaseGatewayResult.Failed>(result).code)
    }

    @Test
    fun reader_rejects_a_published_case_with_an_unknown_fact_type() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.case-summary","version":"1","caseId":"case-1","caseVersionId":"case-1:v1","title":"A bounded case","contentHash":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","evaluatorVersion":"evaluator.v1","skillTags":["evidence"],"objective":"Connect evidence to a bounded claim","difficulty":2,"evidenceReferences":["fact-1"],"facts":[{"id":"fact-1","type":"unknown","text":"Unexpected fact."}],"rules":[{"id":"rule-1","outcome":"PASS","anchorIds":["fact-1"]}],"variants":[],"requestId":"req-case-003"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeContentTransport(response)).get("case-1:v1")
        }

        assertEquals("INVALID_CONTENT_RESPONSE", assertIs<PublishedCaseGatewayResult.Failed>(result).code)
    }

    @Test
    fun reader_rejects_a_noop_challenge_variant() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.case-summary","version":"1","caseId":"case-1","caseVersionId":"case-1:v1","title":"A bounded case","contentHash":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","evaluatorVersion":"evaluator.v1","skillTags":["evidence"],"objective":"Connect evidence to a bounded claim","difficulty":2,"evidenceReferences":["fact-1"],"facts":[{"id":"fact-1","type":"observation","text":"An observation."}],"rules":[{"id":"rule-1","outcome":"PASS","anchorIds":["fact-1"]}],"variants":[{"id":"challenge-1","removedFactIds":[]}],"requestId":"req-case-005"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeContentTransport(response)).get("case-1:v1")
        }

        assertEquals("INVALID_CONTENT_RESPONSE", assertIs<PublishedCaseGatewayResult.Failed>(result).code)
    }

    @Test
    fun reader_rejects_a_published_case_without_a_challenge_variant() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.case-summary","version":"1","caseId":"case-1","caseVersionId":"case-1:v1","title":"A bounded case","contentHash":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","evaluatorVersion":"evaluator.v1","skillTags":["evidence"],"objective":"Connect evidence to a bounded claim","difficulty":2,"evidenceReferences":["fact-1"],"facts":[{"id":"fact-1","type":"observation","text":"An observation."}],"rules":[{"id":"rule-1","outcome":"PASS","anchorIds":["fact-1"]}],"variants":[],"requestId":"req-case-006"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeContentTransport(response)).get("case-1:v1")
        }

        assertEquals("INVALID_CONTENT_RESPONSE", assertIs<PublishedCaseGatewayResult.Failed>(result).code)
    }

    @Test
    fun transient_reader_failure_is_retryable_but_invalid_case_version_is_not_sent() {
        val transient = runSuspendTest {
            gateway(FakeContentTransport(AccountHttpResponse(503, "{}"))).get("case-1:v1")
        }
        assertTrue(assertIs<PublishedCaseGatewayResult.Failed>(transient).retryable)

        val transport = FakeContentTransport(AccountHttpResponse(200, "{}"))
        val invalid = runSuspendTest { gateway(transport).get("case with spaces") }
        assertEquals("INVALID_CASE_VERSION", assertIs<PublishedCaseGatewayResult.Failed>(invalid).code)
        assertTrue(transport.requests.isEmpty())
    }

    private fun gateway(
        transport: AccountHttpTransport,
        verified: Boolean = true,
    ): PublishedCaseGateway = PublishedCaseGateway(
        configuration = ContentClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryContentSecureStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174002", verified),
                SecureSessionMaterial("access-token", 200),
            ),
        ),
        nowEpochSeconds = { 100 },
    )
}

private data class ContentRequest(
    val headers: Map<String, String>,
    val body: String,
)

private class FakeContentTransport(
    private val response: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<ContentRequest>()

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += ContentRequest(headers, body)
        return response
    }
}

private class MemoryContentSecureStore(
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
