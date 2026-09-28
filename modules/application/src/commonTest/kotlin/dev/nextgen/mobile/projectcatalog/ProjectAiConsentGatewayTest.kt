package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.AccountSummary
import dev.nextgen.mobile.account.runSuspendTest
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.network.DeviceConnectivity
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.StoredAccountSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectAiConsentGatewayTest {
    @Test
    fun `GET returns an ungranted state with verified session and empty body`() {
        val transport = QueueConsentTransport(AccountHttpResponse(200, ungrantedConsent))

        val result = runSuspendTest { gateway(transport).refresh() }

        assertEquals(ProjectAiConsentGatewayResult.State(ungrantedState), result)
        assertEquals("GET", transport.requests.single().method)
        assertEquals("https://api.example.test/v1/project-ai/consent", transport.requests.single().url)
        assertEquals("", transport.requests.single().body)
        assertEquals("Bearer access-token", transport.requests.single().headers["Authorization"])
        assertEquals("application/json", transport.requests.single().headers["Accept"])
        assertFalse("Content-Type" in transport.requests.single().headers)
    }

    @Test
    fun `PUT requires explicit confirmation and sends only the consent policy contract`() {
        val transport = QueueConsentTransport(AccountHttpResponse(200, grantedConsent))
        val gateway = gateway(transport)

        val denied = runSuspendTest { gateway.grantAfterExplicitUserAction(explicitlyConfirmed = false) }
        assertEquals(
            ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_CONFIRMATION_REQUIRED", retryable = false),
            denied,
        )
        assertTrue(transport.requests.isEmpty())

        val granted = runSuspendTest { gateway.grantAfterExplicitUserAction(explicitlyConfirmed = true) }

        assertEquals(ProjectAiConsentGatewayResult.State(grantedState), granted)
        val request = transport.requests.single()
        assertEquals("PUT", request.method)
        assertEquals(
            """{"schema":"evidrilo.project-ai-consent","version":"1","policyVersion":"project-ai-data.v1"}""",
            request.body,
        )
        assertEquals("application/json", request.headers["Content-Type"])
    }

    @Test
    fun `DELETE revokes consent without a request body`() {
        val transport = QueueConsentTransport(AccountHttpResponse(200, revokedConsent))

        val result = runSuspendTest { gateway(transport).revoke() }

        assertEquals(ProjectAiConsentGatewayResult.State(revokedState), result)
        assertEquals("DELETE", transport.requests.single().method)
        assertEquals("", transport.requests.single().body)
        assertFalse("Content-Type" in transport.requests.single().headers)
    }

    @Test
    fun `session guards prevent requests without configuration verified account and live secure session`() {
        val notConfiguredTransport = QueueConsentTransport(AccountHttpResponse(200, ungrantedConsent))
        val missingSessionTransport = QueueConsentTransport(AccountHttpResponse(200, ungrantedConsent))
        val unverifiedTransport = QueueConsentTransport(AccountHttpResponse(200, ungrantedConsent))
        val expiredSessionTransport = QueueConsentTransport(AccountHttpResponse(200, ungrantedConsent))
        val secureStorageTransport = QueueConsentTransport(AccountHttpResponse(200, ungrantedConsent))
        val cases = listOf(
            Triple(
                gateway(notConfiguredTransport, configured = false),
                ProjectAiConsentDeferralReason.NOT_CONFIGURED,
                notConfiguredTransport,
            ),
            Triple(
                gateway(missingSessionTransport, session = null),
                ProjectAiConsentDeferralReason.AUTH_REQUIRED,
                missingSessionTransport,
            ),
            Triple(
                gateway(unverifiedTransport, verified = false),
                ProjectAiConsentDeferralReason.AUTH_REQUIRED,
                unverifiedTransport,
            ),
            Triple(
                gateway(expiredSessionTransport, expiresAt = 100),
                ProjectAiConsentDeferralReason.SESSION_EXPIRED,
                expiredSessionTransport,
            ),
            Triple(
                gateway(secureStorageTransport, secureReadFails = true),
                ProjectAiConsentDeferralReason.SECURE_STORAGE,
                secureStorageTransport,
            ),
        )

        cases.forEach { (gateway, reason, transport) ->
            val result = runSuspendTest { gateway.refresh() }
            assertEquals(ProjectAiConsentGatewayResult.Deferred(reason), result)
            assertTrue(transport.requests.isEmpty())
        }
    }

    @Test
    fun `malformed and contract-mismatched responses are rejected`() {
        val invalidBodies = listOf(
            "not-json",
            ungrantedConsent.replace("evidrilo.project-ai-consent", "wrong-schema"),
            ungrantedConsent.replace("\"version\":\"1\"", "\"version\":\"2\""),
            ungrantedConsent.replace("\"granted\":false", "\"granted\":\"false\""),
            ungrantedConsent.replace("\"policyVersion\":\"project-ai-data.v1\"", "\"policyVersion\":\"wrong\""),
            ungrantedConsent.replace("\"grantedAt\":null,", ""),
            ungrantedConsent.replace("\"revokedAt\":null,", ""),
            ungrantedConsent.replace("\"grantedAt\":null", "\"grantedAt\":\"not-a-timestamp\""),
            ungrantedConsent.replace("\"generation\":0", "\"generation\":-1"),
            ungrantedConsent.replace("\"generation\":0", "\"generation\":\"0\""),
            ungrantedConsent.replace("\"generation\":0", "\"generation\":0,\"extra\":true"),
        )

        invalidBodies.forEach { body ->
            val transport = QueueConsentTransport(AccountHttpResponse(200, body))
            val result = runSuspendTest { gateway(transport).refresh() }

            assertEquals(
                ProjectAiConsentGatewayResult.Failed("INVALID_PROJECT_AI_CONSENT_RESPONSE", retryable = false),
                result,
            )
        }
    }

    @Test
    fun `oversized response is rejected before parsing`() {
        val transport = QueueConsentTransport(
            AccountHttpResponse(200, " ".repeat(MAX_PROJECT_AI_CONSENT_RESPONSE_BYTES + 1)),
        )

        val result = runSuspendTest { gateway(transport).refresh() }

        assertEquals(
            ProjectAiConsentGatewayResult.Failed("INVALID_PROJECT_AI_CONSENT_RESPONSE", retryable = false),
            result,
        )
    }

    @Test
    fun `authorization status is preserved even when the error body exceeds the response bound`() {
        val result = runSuspendTest {
            gateway(
                QueueConsentTransport(
                    AccountHttpResponse(401, " ".repeat(MAX_PROJECT_AI_CONSENT_RESPONSE_BYTES + 1)),
                ),
            ).refresh()
        }

        assertEquals(ProjectAiConsentGatewayResult.Unauthorized, result)
    }

    @Test
    fun `stale policy conflict is distinguished from other request failures`() {
        val result = runSuspendTest {
            gateway(QueueConsentTransport(
                AccountHttpResponse(409, """{"code":"PROJECT_AI_CONSENT_POLICY_STALE"}"""),
            )).grantAfterExplicitUserAction(explicitlyConfirmed = true)
        }

        assertEquals(ProjectAiConsentGatewayResult.PolicyStale, result)
    }

    @Test
    fun `unauthorized and forbidden responses remain distinct`() {
        val unauthorized = runSuspendTest {
            gateway(QueueConsentTransport(AccountHttpResponse(401, "{}"))).refresh()
        }
        val forbidden = runSuspendTest {
            gateway(QueueConsentTransport(AccountHttpResponse(403, "{}"))).refresh()
        }

        assertEquals(ProjectAiConsentGatewayResult.Unauthorized, unauthorized)
        assertEquals(ProjectAiConsentGatewayResult.Forbidden, forbidden)
    }

    @Test
    fun `transient HTTP and transport failures are reported without granting locally`() {
        val readFailure = runSuspendTest {
            gateway(QueueConsentTransport(AccountHttpResponse(503, "{}"))).refresh()
        }
        val serverFailure = runSuspendTest {
            gateway(QueueConsentTransport(AccountHttpResponse(503, """{"code":"DATABASE_NOT_CONFIGURED"}""")))
                .grantAfterExplicitUserAction(explicitlyConfirmed = true)
        }
        val lostResponseTransport = QueueConsentTransport(failure = IllegalStateException("offline"))
        val lostResponse = runSuspendTest {
            gateway(lostResponseTransport).grantAfterExplicitUserAction(explicitlyConfirmed = true)
        }

        assertEquals(
            ProjectAiConsentGatewayResult.Failed(
                code = "DATABASE_NOT_CONFIGURED",
                retryable = true,
                outcomeUnknown = true,
            ),
            serverFailure,
        )
        assertEquals(
            ProjectAiConsentGatewayResult.Failed(
                code = "PROJECT_AI_CONSENT_SERVER_ERROR",
                retryable = true,
                outcomeUnknown = false,
            ),
            readFailure,
        )
        val transportFailure = assertIs<ProjectAiConsentGatewayResult.Failed>(lostResponse)
        assertTrue(transportFailure.retryable)
        assertTrue(transportFailure.outcomeUnknown)
        assertEquals("PROJECT_AI_CONSENT_UNAVAILABLE", transportFailure.code)
        assertEquals("PUT", lostResponseTransport.requests.single().method)
    }

    private fun gateway(
        transport: QueueConsentTransport,
        configured: Boolean = true,
        session: StoredAccountSession? = validSession(),
        verified: Boolean = true,
        expiresAt: Long = 500,
        secureReadFails: Boolean = false,
    ) = ProjectAiConsentGateway(
        configuration = AiClientConfiguration(if (configured) "https://api.example.test" else ""),
        transport = transport,
        secureSessionStore = MemoryConsentSessionStore(
            session = session?.copy(
                account = session.account.copy(emailVerified = verified),
                material = SecureSessionMaterial("access-token", expiresAtEpochSeconds = expiresAt),
            ),
            readFails = secureReadFails,
        ),
        nowEpochSeconds = { 100 },
    )

    private fun validSession() = StoredAccountSession(
        account = AccountSummary("student-account", emailVerified = true),
        material = SecureSessionMaterial("access-token", expiresAtEpochSeconds = 500),
    )

    private class QueueConsentTransport(
        private val response: AccountHttpResponse? = null,
        private val failure: Exception? = null,
    ) : AccountHttpTransport {
        override val deviceConnectivity = DeviceConnectivity.ONLINE
        val requests = mutableListOf<CapturedConsentRequest>()

        override suspend fun request(
            method: String,
            url: String,
            headers: Map<String, String>,
            body: String,
        ): AccountHttpResponse {
            requests += CapturedConsentRequest(method, url, headers, body)
            failure?.let { throw it }
            return requireNotNull(response)
        }
    }

    private data class CapturedConsentRequest(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val body: String,
    )

    private class MemoryConsentSessionStore(
        private var session: StoredAccountSession?,
        private val readFails: Boolean = false,
    ) : SecureSessionStore {
        override fun read(): StoredAccountSession? {
            if (readFails) error("secure storage unavailable")
            return session
        }

        override fun write(session: StoredAccountSession) {
            this.session = session
        }

        override fun clear() {
            session = null
        }
    }

    private companion object {
        const val ungrantedConsent = """{"schema":"evidrilo.project-ai-consent","version":"1","granted":false,"policyVersion":"project-ai-data.v1","grantedAt":null,"revokedAt":null,"generation":0}"""
        const val grantedConsent = """{"schema":"evidrilo.project-ai-consent","version":"1","granted":true,"policyVersion":"project-ai-data.v1","grantedAt":"2026-09-27T01:00:00+00:00","revokedAt":null,"generation":1}"""
        const val revokedConsent = """{"schema":"evidrilo.project-ai-consent","version":"1","granted":false,"policyVersion":"project-ai-data.v1","grantedAt":"2026-09-27T01:00:00+00:00","revokedAt":"2026-09-27T02:00:00+00:00","generation":2}"""

        val ungrantedState = ProjectAiConsentState(
            granted = false,
            policyVersion = "project-ai-data.v1",
            grantedAt = null,
            revokedAt = null,
            generation = 0,
        )
        val grantedState = ProjectAiConsentState(
            granted = true,
            policyVersion = "project-ai-data.v1",
            grantedAt = "2026-09-27T01:00:00+00:00",
            revokedAt = null,
            generation = 1,
        )
        val revokedState = ProjectAiConsentState(
            granted = false,
            policyVersion = "project-ai-data.v1",
            grantedAt = "2026-09-27T01:00:00+00:00",
            revokedAt = "2026-09-27T02:00:00+00:00",
            generation = 2,
        )
    }
}
