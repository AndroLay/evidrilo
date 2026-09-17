package dev.nextgen.mobile.sync

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

class SyncGatewayTest {
    @Test
    fun push_sends_a_bounded_redacted_contract_with_the_session_bearer() {
        val transport = FakeSyncTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.sync-push-result","version":"1","results":[{"commandId":"123e4567-e89b-42d3-a456-426614174000","outcome":"accepted","serverSequence":41}],"nextCursor":41,"requestId":"req-sync-001"}
                """.trimIndent(),
            ),
        )
        val gateway = gateway(transport)

        val result = runSuspendTest { gateway.push(validEnvelope()) }

        val completed = assertIs<SyncGatewayResult.PushCompleted>(result)
        assertEquals(41L, completed.response.nextCursor)
        assertEquals("Bearer access-token", transport.requests.single().headers["Authorization"])
        assertEquals("push_20260911_0001", transport.requests.single().headers["Idempotency-Key"])
        assertTrue(transport.requests.single().body.contains("evidrilo.sync-push-request"))
        assertTrue(transport.requests.single().body.contains("attempt_submitted"))
        assertFalse(transport.requests.single().body.contains("claimText"))
        assertFalse(transport.requests.single().body.contains("rawDraftText"))
    }

    @Test
    fun push_requires_consent_and_does_not_touch_the_network() {
        val transport = FakeSyncTransport(AccountHttpResponse(200, "{}"))
        val gateway = gateway(transport)

        val result = runSuspendTest {
            gateway.push(validEnvelope().copy(consent = SyncConsent.NOT_GRANTED))
        }

        val failure = assertIs<SyncGatewayResult.Failed>(result)
        assertEquals("CONSENT_REQUIRED", failure.code)
        assertFalse(failure.retryable)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun push_rejects_an_unverified_session_before_network_access() {
        val transport = FakeSyncTransport(AccountHttpResponse(200, "{}"))
        val result = runSuspendTest {
            gateway(transport, verified = false).push(validEnvelope())
        }

        val deferred = assertIs<SyncGatewayResult.Deferred>(result)
        assertEquals(SyncDeferralReason.AUTH_REQUIRED, deferred.reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun pull_parses_changes_and_rejects_unauthorized_sessions_without_retrying() {
        val transport = FakeSyncTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.sync-pull","version":"1","cursor":40,"nextCursor":41,"hasMore":false,"changes":[{"serverSequence":41,"attemptId":"123e4567-e89b-42d3-a456-426614174001","caseVersionId":"M0_T2:1","commandType":"attempt_submitted","revisionNumber":0,"snapshotDigest":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}],"requestId":"req-pull-001"}
                """.trimIndent(),
            ),
        )
        val gateway = gateway(transport)

        val result = runSuspendTest { gateway.pull(SyncConsent.GRANTED, cursor = 40, limit = 50) }

        val completed = assertIs<SyncGatewayResult.PullCompleted>(result)
        assertEquals(41L, completed.response.changes.single().serverSequence)
        assertEquals("https://api.example.test/v1/sync/pull?cursor=40&limit=50", transport.requests.single().url)

        val unauthorizedTransport = FakeSyncTransport(AccountHttpResponse(401, "{}"))
        val unauthorized = runSuspendTest {
            gateway(unauthorizedTransport).pull(SyncConsent.GRANTED, cursor = 40)
        }
        val deferred = assertIs<SyncGatewayResult.Deferred>(unauthorized)
        assertEquals(SyncDeferralReason.AUTH_REQUIRED, deferred.reason)
        assertEquals(1, unauthorizedTransport.requests.size)
    }

    @Test
    fun pull_accepts_has_more_when_the_requested_limit_is_smaller_than_the_maximum() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.sync-pull","version":"1","cursor":40,"nextCursor":41,"hasMore":true,"changes":[{"serverSequence":41,"attemptId":"123e4567-e89b-42d3-a456-426614174001","caseVersionId":"M0_T2:1","commandType":"attempt_submitted","revisionNumber":0,"snapshotDigest":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}],"requestId":"req-pull-page-001"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeSyncTransport(response)).pull(SyncConsent.GRANTED, cursor = 40, limit = 1)
        }

        val completed = assertIs<SyncGatewayResult.PullCompleted>(result)
        assertTrue(completed.response.hasMore)
        assertEquals(1, completed.response.changes.size)
    }

    @Test
    fun pull_rejects_a_response_that_exceeds_the_requested_limit() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.sync-pull","version":"1","cursor":40,"nextCursor":42,"hasMore":false,"changes":[{"serverSequence":41,"attemptId":"123e4567-e89b-42d3-a456-426614174001","caseVersionId":"M0_T2:1","commandType":"attempt_submitted","revisionNumber":0,"snapshotDigest":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"},{"serverSequence":42,"attemptId":"123e4567-e89b-42d3-a456-426614174002","caseVersionId":"M0_T2:1","commandType":"revision_recorded","revisionNumber":1,"snapshotDigest":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}],"requestId":"req-pull-page-002"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeSyncTransport(response)).pull(SyncConsent.GRANTED, cursor = 40, limit = 1)
        }

        assertEquals("INVALID_SYNC_RESPONSE", assertIs<SyncGatewayResult.Failed>(result).code)
    }

    @Test
    fun malformed_or_transient_responses_fail_closed_with_a_retry_signal() {
        val malformed = runSuspendTest {
            gateway(FakeSyncTransport(AccountHttpResponse(200, "{}"))).pull(SyncConsent.GRANTED, cursor = 0)
        }
        assertEquals("INVALID_SYNC_RESPONSE", assertIs<SyncGatewayResult.Failed>(malformed).code)

        val transient = runSuspendTest {
            gateway(FakeSyncTransport(AccountHttpResponse(503, "{}"))).pull(SyncConsent.GRANTED, cursor = 0)
        }
        assertTrue(assertIs<SyncGatewayResult.Failed>(transient).retryable)
    }

    @Test
    fun pull_rejects_a_response_for_a_different_requested_cursor() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.sync-pull","version":"1","cursor":39,"nextCursor":40,"hasMore":false,"changes":[],"requestId":"req-pull-002"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeSyncTransport(response)).pull(SyncConsent.GRANTED, cursor = 40)
        }

        assertEquals("INVALID_SYNC_RESPONSE", assertIs<SyncGatewayResult.Failed>(result).code)
    }

    @Test
    fun pull_rejects_non_monotonic_changes_and_a_cursor_that_skips_the_last_change() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.sync-pull","version":"1","cursor":40,"nextCursor":42,"hasMore":false,"changes":[{"serverSequence":42,"attemptId":"123e4567-e89b-42d3-a456-426614174001","caseVersionId":"M0_T2:1","commandType":"attempt_submitted","revisionNumber":0,"snapshotDigest":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"},{"serverSequence":41,"attemptId":"123e4567-e89b-42d3-a456-426614174002","caseVersionId":"M0_T2:1","commandType":"revision_recorded","revisionNumber":1,"snapshotDigest":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}],"requestId":"req-pull-003"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeSyncTransport(response)).pull(SyncConsent.GRANTED, cursor = 40)
        }

        assertEquals("INVALID_SYNC_RESPONSE", assertIs<SyncGatewayResult.Failed>(result).code)
    }

    @Test
    fun pull_rejects_a_change_that_is_not_newer_than_the_requested_cursor() {
        val response = AccountHttpResponse(
            200,
            """
            {"schema":"evidrilo.sync-pull","version":"1","cursor":40,"nextCursor":40,"hasMore":false,"changes":[{"serverSequence":40,"attemptId":"123e4567-e89b-42d3-a456-426614174001","caseVersionId":"M0_T2:1","commandType":"attempt_submitted","revisionNumber":0,"snapshotDigest":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}],"requestId":"req-pull-004"}
            """.trimIndent(),
        )

        val result = runSuspendTest {
            gateway(FakeSyncTransport(response)).pull(SyncConsent.GRANTED, cursor = 40)
        }

        assertEquals("INVALID_SYNC_RESPONSE", assertIs<SyncGatewayResult.Failed>(result).code)
    }

    private fun validEnvelope() = SyncPushEnvelope(
        cursor = 40,
        consent = SyncConsent.GRANTED,
        idempotencyKey = "push_20260911_0001",
        commands = listOf(
            SyncCommandIntent(
                commandId = "123e4567-e89b-42d3-a456-426614174000",
                attemptId = "123e4567-e89b-42d3-a456-426614174001",
                caseVersionId = "M0_T2:1",
                revisionNumber = 0,
                snapshotDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
        ),
    )

    private fun gateway(
        transport: AccountHttpTransport,
        verified: Boolean = true,
    ): SyncGateway = SyncGateway(
        configuration = SyncClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemorySecureSessionStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174002", verified),
                SecureSessionMaterial("access-token", 200),
            ),
        ),
        nowEpochSeconds = { 100 },
    )
}

private data class SyncRequestRecord(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

private class FakeSyncTransport(
    private val response: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<SyncRequestRecord>()

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += SyncRequestRecord(method, url, headers, body)
        return response
    }
}

private class MemorySecureSessionStore(
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
