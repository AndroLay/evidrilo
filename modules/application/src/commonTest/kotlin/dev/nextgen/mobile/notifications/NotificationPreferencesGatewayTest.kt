package dev.nextgen.mobile.notifications

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

class NotificationPreferencesGatewayTest {
    @Test
    fun refresh_parses_the_server_mirror_and_save_uses_optimistic_revision() {
        val transport = QueueNotificationTransport(
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.notification-preferences","version":"1","enabled":false,"continueUnfinishedEnabled":false,"reviewCompletedEnabled":false,"cadence":"daily","localHour":9,"localMinute":0,"revision":0,"updatedAt":null,"requestId":"req-notify-1"}
                """.trimIndent(),
            ),
            AccountHttpResponse(
                200,
                """
                {"schema":"evidrilo.notification-preferences-update-result","version":"1","outcome":"accepted","enabled":true,"continueUnfinishedEnabled":true,"reviewCompletedEnabled":false,"cadence":"weekly","localHour":18,"localMinute":30,"revision":1,"updatedAt":"2026-09-22T04:00:00Z","requestId":"req-notify-2"}
                """.trimIndent(),
            ),
        )
        val gateway = gateway(transport)
        val preferences = NotificationPreferences(
            enabled = true,
            continueUnfinishedEnabled = true,
            cadence = NotificationCadence.WEEKLY,
            hour = 18,
            minute = 30,
        )

        assertIs<NotificationPreferencesGatewayResult.Found>(
            runSuspendTest { gateway.refresh() },
        )
        val saved = assertIs<NotificationPreferencesGatewayResult.Saved>(
            runSuspendTest { gateway.save(preferences) },
        )

        assertEquals("accepted", saved.outcome)
        assertEquals(1, saved.value.revision)
        assertEquals("PUT", transport.requests[1].method)
        assertTrue(transport.requests[1].body.contains("\"expectedRevision\":0"))
        assertEquals("Bearer access-token", transport.requests[1].headers["Authorization"])
    }

    @Test
    fun save_defers_without_a_verified_session_and_does_not_touch_the_network()
    {
        val transport = QueueNotificationTransport(AccountHttpResponse(200, "{}"))
        val result = runSuspendTest {
            gateway(transport, verified = false).save(NotificationPreferences())
        }

        assertEquals(
            NotificationPreferencesDeferralReason.AUTH_REQUIRED,
            assertIs<NotificationPreferencesGatewayResult.Deferred>(result).reason,
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun gateway_fails_closed_for_conflicts_malformed_responses_and_transient_statuses()
    {
        val conflict = runSuspendTest {
            gateway(QueueNotificationTransport(AccountHttpResponse(409, "{}"))).refresh()
        }
        assertIs<NotificationPreferencesGatewayResult.Conflict>(conflict)

        val malformed = runSuspendTest {
            gateway(QueueNotificationTransport(AccountHttpResponse(200, "{}"))).refresh()
        }
        assertEquals(
            "INVALID_NOTIFICATION_PREFERENCES_RESPONSE",
            assertIs<NotificationPreferencesGatewayResult.Failed>(malformed).code,
        )

        val transient = runSuspendTest {
            gateway(QueueNotificationTransport(AccountHttpResponse(503, "{}"))).refresh()
        }
        assertTrue(assertIs<NotificationPreferencesGatewayResult.Failed>(transient).retryable)
    }

    @Test
    fun preferences_read_distinguishes_device_offline_from_service_unavailable() {
        val offline = runSuspendTest {
            gateway(FailingNotificationTransport(DeviceConnectivity.OFFLINE)).refresh()
        }
        val online = runSuspendTest {
            gateway(FailingNotificationTransport(DeviceConnectivity.ONLINE)).refresh()
        }

        assertEquals(
            "NOTIFICATION_PREFERENCES_OFFLINE",
            assertIs<NotificationPreferencesGatewayResult.Failed>(offline).code,
        )
        assertEquals(
            "NOTIFICATION_PREFERENCES_UNAVAILABLE",
            assertIs<NotificationPreferencesGatewayResult.Failed>(online).code,
        )
    }

    @Test
    fun unknown_preference_write_is_reconciled_before_any_repeat_mutation() {
        val transport = LostNotificationWriteTransport()
        val gateway = gateway(transport)
        val desired = NotificationPreferences(
            enabled = true,
            continueUnfinishedEnabled = true,
            reviewCompletedEnabled = false,
            cadence = NotificationCadence.WEEKLY,
            hour = 18,
            minute = 30,
        )

        val first = assertIs<NotificationPreferencesGatewayResult.Failed>(
            runSuspendTest { gateway.save(desired) },
        )
        assertEquals("NOTIFICATION_PREFERENCES_OUTCOME_UNKNOWN", first.code)
        assertTrue(first.outcomeUnknown)
        assertTrue(first.reconciliationRequired)
        assertEquals(listOf("GET", "PUT"), transport.methods)
        assertTrue(transport.putBodies.single().contains("\"expectedRevision\":0"))

        val reconciled = assertIs<NotificationPreferencesGatewayResult.Saved>(
            runSuspendTest { gateway.save(desired) },
        )
        assertEquals("reconciled", reconciled.outcome)
        assertEquals(desired, reconciled.value.preferences)
        assertEquals(listOf("GET", "PUT", "GET"), transport.methods)
    }

    private fun gateway(
        transport: AccountHttpTransport,
        verified: Boolean = true,
    ): NotificationPreferencesGateway = NotificationPreferencesGateway(
        configuration = NotificationClientConfiguration("https://api.example.test"),
        transport = transport,
        secureSessionStore = MemoryNotificationSessionStore(
            StoredAccountSession(
                AccountSummary("123e4567-e89b-42d3-a456-426614174002", verified),
                SecureSessionMaterial("access-token", 200),
            ),
        ),
        nowEpochSeconds = { 100 },
    )
}

private data class NotificationRequestRecord(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

private class QueueNotificationTransport(
    private vararg val responses: AccountHttpResponse,
) : AccountHttpTransport {
    val requests = mutableListOf<NotificationRequestRecord>()
    private var index = 0

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        requests += NotificationRequestRecord(
            method,
            url.substringAfter("api.example.test"),
            headers,
            body,
        )
        return responses[index++]
    }
}

private class FailingNotificationTransport(
    override val deviceConnectivity: DeviceConnectivity,
) : AccountHttpTransport {
    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse = error("Synthetic transport failure")
}

private class LostNotificationWriteTransport : AccountHttpTransport {
    val methods = mutableListOf<String>()
    val putBodies = mutableListOf<String>()
    private var writeCommitted = false

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
    ): AccountHttpResponse {
        methods += method
        if (method == "PUT") {
            putBodies += body
            writeCommitted = true
            error("Response was lost after the server applied the desired state")
        }
        val responseBody = if (writeCommitted) {
            """{"schema":"evidrilo.notification-preferences","version":"1","enabled":true,"continueUnfinishedEnabled":true,"reviewCompletedEnabled":false,"cadence":"weekly","localHour":18,"localMinute":30,"revision":1,"updatedAt":"2026-09-25T00:00:00Z","requestId":"req-notify-reconcile"}"""
        } else {
            """{"schema":"evidrilo.notification-preferences","version":"1","enabled":false,"continueUnfinishedEnabled":false,"reviewCompletedEnabled":false,"cadence":"daily","localHour":9,"localMinute":0,"revision":0,"updatedAt":null,"requestId":"req-notify-initial"}"""
        }
        return AccountHttpResponse(200, responseBody)
    }
}

private class MemoryNotificationSessionStore(
    private var value: StoredAccountSession?,
) : SecureSessionStore {
    override fun read(): StoredAccountSession? = value
    override fun write(session: StoredAccountSession) { value = session }
    override fun clear() { value = null }
}
