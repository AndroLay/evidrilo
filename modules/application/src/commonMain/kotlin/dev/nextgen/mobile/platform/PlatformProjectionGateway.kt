package dev.nextgen.mobile.platform

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.account.isAllowedApiBaseUrl
import dev.nextgen.mobile.network.DeviceConnectivity
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionStoreFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

private const val MAX_PROJECTION_RESPONSE_BYTES = 64 * 1024

data class PlatformClientConfiguration(
    val apiBaseUrl: String,
) {
    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() {
            val normalized = normalizedApiBaseUrl
            return isAllowedApiBaseUrl(normalized)
        }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val index = indexOfFirst { it in delimiters }
        return if (index == -1) this else substring(0, index)
    }
}

enum class ProjectionDeferralReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

sealed interface PlatformProjectionResult<out T> {
    data class Found<T>(val value: T) : PlatformProjectionResult<T>

    data class Deferred(val reason: ProjectionDeferralReason) : PlatformProjectionResult<Nothing>

    data class Failed(
        val code: String,
        val retryable: Boolean,
    ) : PlatformProjectionResult<Nothing>
}

data class PlatformProgressSummary(
    val calculationVersion: String,
    val attemptsObserved: Long,
    val completedAttempts: Long,
    val revisionsObserved: Long,
    val passCount: Long,
    val actionRequiredCount: Long,
    val abstentionCount: Long,
    val coverage: Double,
    val requestId: String,
)

data class PlatformEntitlement(
    val entitlement: String,
    val status: String,
    val updatedAt: String,
)

data class PlatformEntitlements(
    val items: List<PlatformEntitlement>,
    val requestId: String,
)

class PlatformProjectionGateway(
    private val configuration: PlatformClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun getProgress(): PlatformProjectionResult<PlatformProgressSummary> =
        execute(
            path = "/v1/progress/me",
            invalidCode = "INVALID_PROGRESS_RESPONSE",
            parse = ::parseProgress,
        )

    suspend fun getEntitlements(): PlatformProjectionResult<PlatformEntitlements> =
        execute(
            path = "/v1/billing/entitlements",
            invalidCode = "INVALID_ENTITLEMENTS_RESPONSE",
            parse = ::parseEntitlements,
        )

    private suspend fun <T> execute(
        path: String,
        invalidCode: String,
        parse: (AccountHttpResponse) -> T?,
    ): PlatformProjectionResult<T> {
        if (!configuration.isConfigured) {
            return PlatformProjectionResult.Deferred(ProjectionDeferralReason.NOT_CONFIGURED)
        }
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return PlatformProjectionResult.Deferred(ProjectionDeferralReason.SECURE_STORAGE)
        } ?: return PlatformProjectionResult.Deferred(ProjectionDeferralReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) {
            return PlatformProjectionResult.Deferred(ProjectionDeferralReason.AUTH_REQUIRED)
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return PlatformProjectionResult.Deferred(ProjectionDeferralReason.SESSION_EXPIRED)
        }

        val response = try {
            transport.request(
                method = "GET",
                url = "${configuration.normalizedApiBaseUrl}$path",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.material.accessToken}",
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            val code = if (transport.deviceConnectivity == DeviceConnectivity.OFFLINE) {
                "PROJECTION_OFFLINE"
            } else {
                "PROJECTION_UNAVAILABLE"
            }
            return PlatformProjectionResult.Failed(code, retryable = true)
        }

        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                PlatformProjectionResult.Deferred(ProjectionDeferralReason.AUTH_REQUIRED)

            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 ->
                PlatformProjectionResult.Failed("PROJECTION_UNAVAILABLE", retryable = true)

            response.statusCode !in 200..299 ->
                PlatformProjectionResult.Failed("PROJECTION_REQUEST_REJECTED", retryable = false)

            response.body.encodeToByteArray().size > MAX_PROJECTION_RESPONSE_BYTES ->
                PlatformProjectionResult.Failed(invalidCode, retryable = false)

            else -> parse(response)?.let { value -> PlatformProjectionResult.Found(value) }
                ?: PlatformProjectionResult.Failed(invalidCode, retryable = false)
        }
    }

    private fun parseProgress(response: AccountHttpResponse): PlatformProgressSummary? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == progressKeys)
        require(root.string("schema") == "evidrilo.progress-summary")
        require(root.string("version") == "1")
        val calculationVersion = root.string("calculationVersion")
            ?.also { require(identifierPattern.matches(it)) }
            ?: error("calculation version required")
        val attempts = root.nonNegativeLong("attemptsObserved")
        val completed = root.nonNegativeLong("completedAttempts")
        val revisions = root.nonNegativeLong("revisionsObserved")
        val pass = root.nonNegativeLong("passCount")
        val actionRequired = root.nonNegativeLong("actionRequiredCount")
        val abstention = root.nonNegativeLong("abstentionCount")
        val coverage = (root["coverage"] as? JsonPrimitive)?.doubleOrNull ?: error("coverage required")
        require(completed <= attempts)
        require(pass + actionRequired + abstention <= completed)
        require(coverage in 0.0..1.0)
        val requestId = root.requestId()
        PlatformProgressSummary(
            calculationVersion,
            attempts,
            completed,
            revisions,
            pass,
            actionRequired,
            abstention,
            coverage,
            requestId,
        )
    }.getOrNull()

    private fun parseEntitlements(response: AccountHttpResponse): PlatformEntitlements? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == entitlementsKeys)
        require(root.string("schema") == "evidrilo.entitlements")
        require(root.string("version") == "1")
        val items = root["entitlements"]?.jsonArray?.map { element ->
            val item = element as? JsonObject ?: error("entitlement object required")
            require(item.keys == entitlementKeys)
            val entitlement = item.string("entitlement")
                ?.also { require(it.length in 1..128) }
                ?: error("entitlement required")
            val status = item.string("status")
                ?.also { require(it in setOf("active", "expired", "revoked")) }
                ?: error("status required")
            val updatedAt = item.string("updatedAt")
                ?.also { require(dateTimePattern.matches(it)) }
                ?: error("updated at required")
            PlatformEntitlement(entitlement, status, updatedAt)
        } ?: error("entitlements required")
        PlatformEntitlements(items, root.requestId())
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.nonNegativeLong(name: String): Long =
        (this[name] as? JsonPrimitive)?.longOrNull?.also { require(it >= 0) }
            ?: error("$name required")

    private fun JsonObject.requestId(): String =
        string("requestId")?.also { require(requestIdPattern.matches(it)) }
            ?: error("request id required")

    private companion object {
        val progressKeys = setOf(
            "schema", "version", "calculationVersion", "attemptsObserved", "completedAttempts",
            "revisionsObserved", "passCount", "actionRequiredCount", "abstentionCount", "coverage", "requestId",
        )
        val entitlementsKeys = setOf("schema", "version", "entitlements", "requestId")
        val entitlementKeys = setOf("entitlement", "status", "updatedAt")
        val identifierPattern = Regex("^[a-z0-9._-]{1,64}$")
        val requestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
        val dateTimePattern = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z$")
    }
}

fun createPlatformProjectionGateway(): PlatformProjectionGateway {
    val accountConfiguration = createAccountClientConfiguration()
    return PlatformProjectionGateway(
        configuration = PlatformClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
        transport = createAccountHttpTransport(),
        secureSessionStore = SecureSessionStoreFactory.create(),
        nowEpochSeconds = { Clock.System.now().epochSeconds },
    )
}
