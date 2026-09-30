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

class ProjectAiActivityGatewayTest {
    @Test
    fun `lists only metadata for the requested project`() {
        val transport = ActivityTransport(AccountHttpResponse(200, response()))
        val result = runSuspendTest {
            gateway(transport).list(
                installationId = INSTALLATION_ID,
                projectId = PROJECT_ID,
                limit = 20,
            )
        }

        val loaded = assertIs<ProjectAiActivityHistoryResult.Loaded>(result)
        assertEquals(1, loaded.activities.size)
        assertEquals("research-question", loaded.activities.single().stageId)
        assertEquals("APPLIED", loaded.activities.single().outcome)
        assertEquals(7, loaded.activities.single().baseProjectRevision)
        assertEquals(8, loaded.activities.single().resultProjectRevision)
        assertNull(loaded.nextCursor)
        assertEquals("GET", transport.method)
        assertEquals("Bearer access-token", transport.headers["Authorization"])
        assertEquals("/v1/project-ai/activity?installationId=$INSTALLATION_ID&projectId=$PROJECT_ID&limit=20", transport.path)
        assertNull(transport.body)
    }

    @Test
    fun `unverified account never requests activity history`() {
        val transport = ActivityTransport(AccountHttpResponse(200, response()))
        val result = runSuspendTest {
            gateway(transport, verified = false).list(INSTALLATION_ID, PROJECT_ID)
        }

        assertEquals(
            ProjectAiActivityHistoryResult.Deferred(ProjectAiActivityHistoryDeferredReason.AUTH_REQUIRED),
            result,
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun `account history keeps general chat unlinked and supports cursor pagination`() {
        val body = response()
            .replace("\"nextCursor\":null", "\"nextCursor\":\"next+/=\"")
            .replace(
                "\"activities\":[{",
                "\"activities\":[{\"activityId\":\"$GENERAL_ACTIVITY_ID\",\"mode\":\"GENERAL\",\"projectId\":null,\"stageId\":null,\"operationId\":null,\"baseProjectRevision\":null,\"resultProjectRevision\":null,\"outcome\":\"COMPLETED\",\"createdAt\":\"2026-09-30T00:02:00Z\",\"updatedAt\":\"2026-09-30T00:02:00Z\"},{",
            )
        val transport = ActivityTransport(AccountHttpResponse(200, body))

        val result = runSuspendTest {
            gateway(transport).list(
                installationId = INSTALLATION_ID,
                projectId = null,
                limit = 20,
                cursor = "older+/=",
            )
        }

        val loaded = assertIs<ProjectAiActivityHistoryResult.Loaded>(result)
        assertEquals(listOf("GENERAL", "PROJECT"), loaded.activities.map { it.mode })
        assertNull(loaded.activities.first().projectId)
        assertEquals(PROJECT_ID, loaded.activities.last().projectId)
        assertEquals("next+/=", loaded.nextCursor)
        assertEquals(
            "/v1/project-ai/activity?installationId=$INSTALLATION_ID&limit=20&cursor=older%2B%2F%3D",
            transport.path,
        )
    }

    @Test
    fun `general chat history rejects an accidental project association`() {
        val body = response().replace("\"mode\":\"PROJECT\"", "\"mode\":\"GENERAL\"")
        val result = runSuspendTest {
            gateway(ActivityTransport(AccountHttpResponse(200, body))).list(INSTALLATION_ID, projectId = null)
        }

        assertEquals(ProjectAiActivityHistoryResult.Rejected("INVALID_PROJECT_AI_ACTIVITY_RESPONSE"), result)
    }

    @Test
    fun `rejects transcript-like or unknown response fields`() {
        val response = response().replace("\"outcome\":\"APPLIED\"", "\"outcome\":\"APPLIED\",\"prompt\":\"private text\"")
        val result = runSuspendTest {
            gateway(ActivityTransport(AccountHttpResponse(200, response))).list(INSTALLATION_ID, PROJECT_ID)
        }

        assertEquals(ProjectAiActivityHistoryResult.Rejected("INVALID_PROJECT_AI_ACTIVITY_RESPONSE"), result)
    }

    private fun gateway(transport: ActivityTransport, verified: Boolean = true) = ProjectAiActivityGateway(
        configuration = AiClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryActivitySessionStore(verified),
        nowEpochSeconds = { 100 },
    )

    private fun response() = """
        {"schema":"evidrilo.project-ai-activity","version":"1","activities":[{"activityId":"$ACTIVITY_ID","mode":"PROJECT","projectId":"$PROJECT_ID","stageId":"research-question","operationId":"refine-question","baseProjectRevision":7,"resultProjectRevision":8,"outcome":"APPLIED","createdAt":"2026-09-30T00:00:00Z","updatedAt":"2026-09-30T00:01:00Z"}],"nextCursor":null}
    """.trimIndent()

    private class ActivityTransport(private val response: AccountHttpResponse) : AccountHttpTransport {
        var calls = 0
        var method: String? = null
        var path: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String? = null

        override val deviceConnectivity = dev.nextgen.mobile.network.DeviceConnectivity.ONLINE

        override suspend fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String,
        ): AccountHttpResponse {
            calls += 1
            this.method = method
            this.path = url.removePrefix("https://api.example.test")
            this.headers = headers
            this.body = body.takeIf(String::isNotEmpty)
            return response
        }
    }

    private class MemoryActivitySessionStore(verified: Boolean) : SecureSessionStore {
        private var value: StoredAccountSession? = StoredAccountSession(
            AccountSummary("account-1", emailVerified = verified, email = "student@example.test"),
            SecureSessionMaterial(
                accessToken = "access-token",
                expiresAtEpochSeconds = 1_000,
                refreshToken = "refresh-token",
            ),
        )

        override fun read(): StoredAccountSession? = value

        override fun write(session: StoredAccountSession) {
            value = session
        }

        override fun clear() {
            value = null
        }
    }

    private companion object {
        const val INSTALLATION_ID = "4e9d21c2-6571-456b-9430-a626c2c05555"
        const val PROJECT_ID = "9aebced7-8f7a-4d2d-a1d5-727f3c3cc3cc"
        const val ACTIVITY_ID = "70e5a2c4-9de2-41af-babe-658fd934492c"
        const val GENERAL_ACTIVITY_ID = "99e5a2c4-9de2-41af-babe-658fd934492c"
    }
}
