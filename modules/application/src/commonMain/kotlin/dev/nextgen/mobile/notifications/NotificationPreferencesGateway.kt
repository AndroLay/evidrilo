package dev.nextgen.mobile.notifications

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.account.isAllowedApiBaseUrl
import dev.nextgen.mobile.network.DeviceConnectivity
import dev.nextgen.mobile.network.RemoteFailureKind
import dev.nextgen.mobile.network.RemoteFailureState
import dev.nextgen.mobile.network.RemoteOperationKind
import dev.nextgen.mobile.network.resolveRemoteFailure
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionStoreFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

private const val MAX_NOTIFICATION_RESPONSE_BYTES = 32 * 1024

data class NotificationClientConfiguration(
    val apiBaseUrl: String,
) {
    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = isAllowedApiBaseUrl(normalizedApiBaseUrl)
}

data class RemoteNotificationPreferences(
    val preferences: NotificationPreferences,
    val revision: Long,
    val updatedAt: String?,
)

enum class NotificationPreferencesDeferralReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

sealed interface NotificationPreferencesGatewayResult {
    data class Found(val value: RemoteNotificationPreferences) : NotificationPreferencesGatewayResult

    data class Saved(
        val value: RemoteNotificationPreferences,
        val outcome: String,
    ) : NotificationPreferencesGatewayResult

    data class Deferred(val reason: NotificationPreferencesDeferralReason) : NotificationPreferencesGatewayResult

    data object Conflict : NotificationPreferencesGatewayResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean = false,
        val reconciliationRequired: Boolean = false,
    ) : NotificationPreferencesGatewayResult
}

/**
 * Optional authenticated mirror for the local notification scheduler.
 * Local preferences remain authoritative for guest/offline use; this gateway
 * only runs after a verified session exists and never requests notification
 * permission on its own.
 */
class NotificationPreferencesGateway(
    private val configuration: NotificationClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    private val mutex = Mutex()
    private var knownAccountId: String? = null
    private var knownRevision: Long? = null
    private var pendingPreferencesAfterUnknownOutcome: NotificationPreferences? = null

    suspend fun refresh(): NotificationPreferencesGatewayResult = mutex.withLock {
        execute(
            method = "GET",
            path = "/v1/notifications/preferences",
            body = "",
            parse = ::parseGetResponse,
        ) { value ->
            knownRevision = value.revision
            NotificationPreferencesGatewayResult.Found(value)
        }
    }

    suspend fun save(preferences: NotificationPreferences): NotificationPreferencesGatewayResult =
        mutex.withLock {
            if (!preferences.isValid) {
                return@withLock NotificationPreferencesGatewayResult.Failed(
                    code = "INVALID_NOTIFICATION_PREFERENCES",
                    retryable = false,
                )
            }

            pendingPreferencesAfterUnknownOutcome?.let { pendingIntent ->
                when (val reconciliation = refreshUnlocked()) {
                    is NotificationPreferencesGatewayResult.Found -> {
                        if (reconciliation.value.preferences == pendingIntent) {
                            pendingPreferencesAfterUnknownOutcome = null
                            if (preferences == pendingIntent) {
                                return@withLock NotificationPreferencesGatewayResult.Saved(
                                    value = reconciliation.value,
                                    outcome = "reconciled",
                                )
                            }
                        }
                    }
                    is NotificationPreferencesGatewayResult.Failed -> return@withLock reconciliation.copy(
                        code = "NOTIFICATION_PREFERENCES_OUTCOME_UNKNOWN",
                        retryable = false,
                        outcomeUnknown = true,
                        reconciliationRequired = true,
                    )
                    else -> return@withLock reconciliation
                }
                pendingPreferencesAfterUnknownOutcome = null
            }

            if (knownRevision == null) {
                when (val refreshed = refreshUnlocked()) {
                    is NotificationPreferencesGatewayResult.Found -> Unit
                    is NotificationPreferencesGatewayResult.Deferred,
                    is NotificationPreferencesGatewayResult.Failed,
                    NotificationPreferencesGatewayResult.Conflict,
                    is NotificationPreferencesGatewayResult.Saved,
                    -> return@withLock refreshed
                }
            }

            val expectedRevision = knownRevision
                ?: return@withLock NotificationPreferencesGatewayResult.Conflict

            val requestBody = buildJsonObject {
                put("schema", "evidrilo.notification-preferences-update")
                put("version", "1")
                put("enabled", preferences.enabled)
                put("continueUnfinishedEnabled", preferences.continueUnfinishedEnabled)
                put("reviewCompletedEnabled", preferences.reviewCompletedEnabled)
                put("cadence", preferences.cadence.id)
                put("localHour", preferences.hour)
                put("localMinute", preferences.minute)
                put("expectedRevision", expectedRevision)
            }.toString()

            execute(
                method = "PUT",
                path = "/v1/notifications/preferences",
                body = requestBody,
                parse = ::parsePutResponse,
                mutationIntent = preferences,
            ) { value ->
                knownRevision = value.first.revision
                pendingPreferencesAfterUnknownOutcome = null
                NotificationPreferencesGatewayResult.Saved(value.first, value.second)
            }
        }

    private suspend fun refreshUnlocked(): NotificationPreferencesGatewayResult = execute(
        method = "GET",
        path = "/v1/notifications/preferences",
        body = "",
        parse = ::parseGetResponse,
    ) { value ->
        knownRevision = value.revision
        NotificationPreferencesGatewayResult.Found(value)
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String,
        parse: (AccountHttpResponse) -> T?,
        mutationIntent: NotificationPreferences? = null,
        onSuccess: (T) -> NotificationPreferencesGatewayResult,
    ): NotificationPreferencesGatewayResult {
        if (!configuration.isConfigured) {
            return NotificationPreferencesGatewayResult.Deferred(
                NotificationPreferencesDeferralReason.NOT_CONFIGURED,
            )
        }
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return NotificationPreferencesGatewayResult.Deferred(
                NotificationPreferencesDeferralReason.SECURE_STORAGE,
            )
        } ?: return NotificationPreferencesGatewayResult.Deferred(
            NotificationPreferencesDeferralReason.AUTH_REQUIRED,
        )
        if (!session.account.emailVerified) {
            return NotificationPreferencesGatewayResult.Deferred(
                NotificationPreferencesDeferralReason.AUTH_REQUIRED,
            )
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return NotificationPreferencesGatewayResult.Deferred(
                NotificationPreferencesDeferralReason.SESSION_EXPIRED,
            )
        }
        if (knownAccountId != session.account.accountId) {
            knownAccountId = session.account.accountId
            knownRevision = null
        }

        val response = try {
            transport.request(
                method = method,
                url = "${configuration.normalizedApiBaseUrl}$path",
                headers = buildMap {
                    put("Accept", "application/json")
                    put("Authorization", "Bearer ${session.material.accessToken}")
                    if (body.isNotEmpty()) put("Content-Type", "application/json")
                },
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (mutationIntent != null) {
                knownRevision = null
                pendingPreferencesAfterUnknownOutcome = mutationIntent
                return NotificationPreferencesGatewayResult.Failed(
                    code = "NOTIFICATION_PREFERENCES_OUTCOME_UNKNOWN",
                    retryable = false,
                    outcomeUnknown = true,
                    reconciliationRequired = true,
                )
            }
            val resolution = resolveRemoteFailure(
                connectivity = transport.deviceConnectivity,
                operation = RemoteOperationKind.READ_ONLY,
                failure = RemoteFailureKind.TRANSPORT,
                requestWasDispatched = true,
            )
            return NotificationPreferencesGatewayResult.Failed(
                code = if (resolution.state == RemoteFailureState.OFFLINE) {
                    "NOTIFICATION_PREFERENCES_OFFLINE"
                } else {
                    "NOTIFICATION_PREFERENCES_UNAVAILABLE"
                },
                retryable = resolution.retryAllowed,
                outcomeUnknown = pendingPreferencesAfterUnknownOutcome != null,
                reconciliationRequired = pendingPreferencesAfterUnknownOutcome != null,
            )
        }

        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                NotificationPreferencesGatewayResult.Deferred(
                    NotificationPreferencesDeferralReason.AUTH_REQUIRED,
                )
            response.statusCode == 409 -> {
                knownRevision = null
                NotificationPreferencesGatewayResult.Conflict
            }
            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 -> if (mutationIntent != null) {
                knownRevision = null
                pendingPreferencesAfterUnknownOutcome = mutationIntent
                NotificationPreferencesGatewayResult.Failed(
                    code = "NOTIFICATION_PREFERENCES_OUTCOME_UNKNOWN",
                    retryable = false,
                    outcomeUnknown = true,
                    reconciliationRequired = true,
                )
            } else {
                NotificationPreferencesGatewayResult.Failed(
                    code = "NOTIFICATION_PREFERENCES_UNAVAILABLE",
                    retryable = true,
                )
            }
            response.statusCode !in 200..299 ->
                NotificationPreferencesGatewayResult.Failed(
                    code = "NOTIFICATION_PREFERENCES_REQUEST_REJECTED",
                    retryable = false,
                )
            response.body.encodeToByteArray().size > MAX_NOTIFICATION_RESPONSE_BYTES ->
                NotificationPreferencesGatewayResult.Failed(
                    code = "INVALID_NOTIFICATION_PREFERENCES_RESPONSE",
                    retryable = false,
                )
            else -> parse(response)?.let(onSuccess)
                ?: NotificationPreferencesGatewayResult.Failed(
                    code = "INVALID_NOTIFICATION_PREFERENCES_RESPONSE",
                    retryable = false,
                )
        }
    }

    private fun parseGetResponse(response: AccountHttpResponse): RemoteNotificationPreferences? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == getResponseKeys)
        require(root.string("schema") == "evidrilo.notification-preferences")
        require(root.string("version") == "1")
        parsePreferences(root).also {
            require(it.revision >= 0)
            require(it.updatedAt == null || dateTimePattern.matches(it.updatedAt))
        }
    }.getOrNull()

    private fun parsePutResponse(response: AccountHttpResponse): Pair<RemoteNotificationPreferences, String>? =
        runCatching {
            val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
            require(root.keys == putResponseKeys)
            require(root.string("schema") == "evidrilo.notification-preferences-update-result")
            require(root.string("version") == "1")
            val outcome = root.string("outcome")?.also {
                require(it == "accepted" || it == "unchanged")
            } ?: error("outcome required")
            val preferences = parsePreferences(root)
            require(preferences.revision > 0)
            require(preferences.updatedAt != null && dateTimePattern.matches(preferences.updatedAt))
            preferences to outcome
        }.getOrNull()

    private fun parsePreferences(root: JsonObject): RemoteNotificationPreferences {
        val preferences = NotificationPreferences(
            enabled = root.boolean("enabled"),
            continueUnfinishedEnabled = root.boolean("continueUnfinishedEnabled"),
            reviewCompletedEnabled = root.boolean("reviewCompletedEnabled"),
            cadence = NotificationCadence.entries.first {
                it.id == root.string("cadence")
            },
            hour = root.int("localHour"),
            minute = root.int("localMinute"),
        )
        require(preferences.isValid)
        return RemoteNotificationPreferences(
            preferences = preferences,
            revision = root.long("revision"),
            updatedAt = root.string("updatedAt"),
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(name: String): Boolean =
        (this[name] as? JsonPrimitive)?.booleanOrNull ?: error("$name required")

    private fun JsonObject.int(name: String): Int =
        (this[name] as? JsonPrimitive)?.intOrNull ?: error("$name required")

    private fun JsonObject.long(name: String): Long =
        (this[name] as? JsonPrimitive)?.longOrNull?.also { require(it >= 0) }
            ?: error("$name required")

    private companion object {
        val getResponseKeys = setOf(
            "schema", "version", "enabled", "continueUnfinishedEnabled",
            "reviewCompletedEnabled", "cadence", "localHour", "localMinute",
            "revision", "updatedAt", "requestId",
        )
        val putResponseKeys = setOf(
            "schema", "version", "outcome", "enabled", "continueUnfinishedEnabled",
            "reviewCompletedEnabled", "cadence", "localHour", "localMinute",
            "revision", "updatedAt", "requestId",
        )
        val dateTimePattern = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z$")
    }
}

fun createPlatformNotificationPreferencesGateway(): NotificationPreferencesGateway {
    val accountConfiguration = createAccountClientConfiguration()
    return NotificationPreferencesGateway(
        configuration = NotificationClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
        transport = createAccountHttpTransport(),
        secureSessionStore = SecureSessionStoreFactory.create(),
        nowEpochSeconds = { Clock.System.now().epochSeconds },
    )
}
