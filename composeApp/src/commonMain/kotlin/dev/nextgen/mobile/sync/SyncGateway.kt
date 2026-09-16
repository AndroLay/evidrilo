package dev.nextgen.mobile.sync

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionStoreFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

internal data class SyncClientConfiguration(
    val apiBaseUrl: String,
) {
    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() {
            val normalized = normalizedApiBaseUrl
            if (!normalized.startsWith("https://", ignoreCase = true)) return false
            val authority = normalized
                .substringAfter("//", "")
                .substringBeforeAny('/', '?', '#')
            return authority.isNotBlank() &&
                !authority.contains('@') &&
                !authority.any(Char::isWhitespace) &&
                (authority.contains('.') || authority == "localhost")
        }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val index = indexOfFirst { it in delimiters }
        return if (index == -1) this else substring(0, index)
    }
}

internal data class SyncPushResponse(
    val results: List<SyncCommandResult>,
    val nextCursor: Long,
    val requestId: String,
)

internal data class SyncCommandResult(
    val commandId: String,
    val outcome: String,
    val serverSequence: Long?,
    val reasonCode: String?,
)

internal data class SyncPullResponse(
    val cursor: Long,
    val nextCursor: Long,
    val hasMore: Boolean,
    val changes: List<SyncChange>,
    val requestId: String,
)

internal data class SyncChange(
    val serverSequence: Long,
    val attemptId: String,
    val caseVersionId: String,
    val commandType: SyncCommandType,
    val revisionNumber: Int,
    val snapshotDigest: String,
)

internal enum class SyncDeferralReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

internal sealed interface SyncGatewayResult {
    data class PushCompleted(val response: SyncPushResponse) : SyncGatewayResult

    data class PullCompleted(val response: SyncPullResponse) : SyncGatewayResult

    data class Deferred(val reason: SyncDeferralReason) : SyncGatewayResult

    data class Failed(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : SyncGatewayResult
}

/**
 * Optional cloud boundary for signed-in users. The free practice reducer does
 * not depend on this class; callers opt in only after explicit consent.
 */
internal class SyncGateway(
    private val configuration: SyncClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun push(envelope: SyncPushEnvelope): SyncGatewayResult {
        val validation = envelope.validate()
        if (!validation.isValid) {
            return SyncGatewayResult.Failed(
                code = validation.errorCode ?: "INVALID_SYNC_REQUEST",
                message = validation.message ?: "The sync request is invalid.",
                retryable = false,
            )
        }
        return executeAuthorizedRequest(
            method = "POST",
            url = "${configuration.normalizedApiBaseUrl}/v1/sync/commands",
            headers = mapOf("Idempotency-Key" to envelope.idempotencyKey),
            body = buildPushBody(envelope),
        ) { response ->
            parsePushResponse(response)?.let(SyncGatewayResult::PushCompleted)
                ?: SyncGatewayResult.Failed(
                    code = "INVALID_SYNC_RESPONSE",
                    message = "The sync response is invalid.",
                    retryable = false,
                )
        }
    }

    suspend fun pushWithRetry(
        envelope: SyncPushEnvelope,
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): SyncGatewayResult {
        var attempt = 0
        while (true) {
            val result = push(envelope)
            if (!SyncRetryPolicy.shouldRetry(result, attempt)) return result
            wait(SyncRetryPolicy.delayMillis(attempt))
            attempt += 1
        }
    }

    suspend fun pull(
        consent: SyncConsent,
        cursor: Long,
        limit: Int = MAX_SYNC_COMMANDS,
    ): SyncGatewayResult {
        if (consent != SyncConsent.GRANTED) {
            return SyncGatewayResult.Failed(
                code = "CONSENT_REQUIRED",
                message = "Sync consent is required.",
                retryable = false,
            )
        }
        if (cursor !in 0..MAX_SYNC_CURSOR || limit !in 1..MAX_SYNC_COMMANDS) {
            return SyncGatewayResult.Failed(
                code = "INVALID_SYNC_CURSOR",
                message = "The sync cursor is invalid.",
                retryable = false,
            )
        }
        return executeAuthorizedRequest(
            method = "GET",
            url = "${configuration.normalizedApiBaseUrl}/v1/sync/pull?cursor=$cursor&limit=$limit",
        ) { response ->
            parsePullResponse(
                response,
                expectedCursor = cursor,
                expectedLimit = limit,
            )?.let(SyncGatewayResult::PullCompleted)
                ?: SyncGatewayResult.Failed(
                    code = "INVALID_SYNC_RESPONSE",
                    message = "The sync response is invalid.",
                    retryable = false,
                )
        }
    }

    suspend fun pullWithRetry(
        consent: SyncConsent,
        cursor: Long,
        limit: Int = MAX_SYNC_COMMANDS,
        wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
    ): SyncGatewayResult {
        var attempt = 0
        while (true) {
            val result = pull(consent, cursor, limit)
            if (!SyncRetryPolicy.shouldRetry(result, attempt)) return result
            wait(SyncRetryPolicy.delayMillis(attempt))
            attempt += 1
        }
    }

    private suspend fun executeAuthorizedRequest(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        parse: (AccountHttpResponse) -> SyncGatewayResult,
    ): SyncGatewayResult {
        if (!configuration.isConfigured) {
            return SyncGatewayResult.Deferred(SyncDeferralReason.NOT_CONFIGURED)
        }
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return SyncGatewayResult.Deferred(SyncDeferralReason.SECURE_STORAGE)
        } ?: return SyncGatewayResult.Deferred(SyncDeferralReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) {
            return SyncGatewayResult.Deferred(SyncDeferralReason.AUTH_REQUIRED)
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return SyncGatewayResult.Deferred(SyncDeferralReason.SESSION_EXPIRED)
        }

        val requestHeaders = buildMap {
            put("Accept", "application/json")
            put("Authorization", "Bearer ${session.material.accessToken}")
            if (body.isNotEmpty()) put("Content-Type", "application/json")
            putAll(headers)
        }
        val response = try {
            transport.request(method, url, requestHeaders, body)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return SyncGatewayResult.Failed(
                code = "OFFLINE",
                message = "Sync is unavailable offline.",
                retryable = true,
            )
        }
        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                SyncGatewayResult.Deferred(SyncDeferralReason.AUTH_REQUIRED)

            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 -> SyncGatewayResult.Failed(
                code = "SYNC_UNAVAILABLE",
                message = "Sync is temporarily unavailable.",
                retryable = true,
            )

            response.statusCode !in 200..299 -> SyncGatewayResult.Failed(
                code = "SYNC_REQUEST_REJECTED",
                message = "Sync could not be completed.",
                retryable = false,
            )

            else -> parse(response)
        }
    }

    private fun buildPushBody(envelope: SyncPushEnvelope): String = buildJsonObject {
        put("schema", "evidrilo.sync-push-request")
        put("version", "1")
        putJsonArray("commands") {
            envelope.commands.forEach { command ->
                add(buildJsonObject {
                    put("schema", "evidrilo.sync-command")
                    put("version", "1")
                    put("commandId", command.commandId)
                    put("attemptId", command.attemptId)
                    put("caseVersionId", command.caseVersionId)
                    put("commandType", command.commandType.wireName)
                    put("revisionNumber", command.revisionNumber)
                    put("clientOccurredAt", command.clientOccurredAt)
                    put("snapshotDigest", command.snapshotDigest)
                })
            }
        }
    }.toString()

    private fun parsePushResponse(response: AccountHttpResponse): SyncPushResponse? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.string("schema") == "evidrilo.sync-push-result")
        require(root.string("version") == "1")
        val nextCursor = root.long("nextCursor")?.also { require(it in 0..MAX_SYNC_CURSOR) }
            ?: error("cursor required")
        val requestId = root.string("requestId")?.also { require(isValidSyncRequestId(it)) }
            ?: error("request id required")
        val results = root.array("results")?.also { require(it.size <= MAX_SYNC_COMMANDS * 2) }
            ?.map { element ->
                val item = element as? JsonObject ?: error("result object required")
                val commandId = item.string("commandId")?.also { require(isValidSyncUuid(it)) }
                    ?: error("command id required")
                val outcome = item.string("outcome")?.also {
                    require(it in setOf("accepted", "duplicate", "conflict", "rejected"))
                } ?: error("outcome required")
                val serverSequence = item.optionalLong("serverSequence")?.also {
                    require(it in 1..MAX_SYNC_CURSOR)
                }
                val reasonCode = item.string("reasonCode")?.also {
                    require(Regex("^[A-Z][A-Z0-9_]{2,63}$").matches(it))
                }
                SyncCommandResult(commandId, outcome, serverSequence, reasonCode)
            } ?: error("results required")
        SyncPushResponse(results, nextCursor, requestId)
    }.getOrNull()

    private fun parsePullResponse(
        response: AccountHttpResponse,
        expectedCursor: Long,
        expectedLimit: Int,
    ): SyncPullResponse? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.string("schema") == "evidrilo.sync-pull")
        require(root.string("version") == "1")
        val cursor = root.long("cursor")?.also { require(it in 0..MAX_SYNC_CURSOR) }
            ?: error("cursor required")
        require(cursor == expectedCursor)
        val nextCursor = root.long("nextCursor")?.also { require(it in cursor..MAX_SYNC_CURSOR) }
            ?: error("next cursor required")
        val hasMore = root.boolean("hasMore") ?: error("has more required")
        val requestId = root.string("requestId")?.also { require(isValidSyncRequestId(it)) }
            ?: error("request id required")
        val changes = root.array("changes")?.also { require(it.size <= MAX_SYNC_COMMANDS * 2) }
            ?.map { element ->
                val item = element as? JsonObject ?: error("change object required")
                val serverSequence = item.long("serverSequence")?.also {
                    require(it in 1..MAX_SYNC_CURSOR)
                } ?: error("server sequence required")
                val attemptId = item.string("attemptId")?.also { require(isValidSyncUuid(it)) }
                    ?: error("attempt id required")
                val caseVersionId = item.string("caseVersionId")?.also { require(isValidSyncCaseVersion(it)) }
                    ?: error("case version required")
                val commandType = item.string("commandType")?.let(SyncCommandType::fromWire)
                    ?: error("command type required")
                val revisionNumber = item.int("revisionNumber")?.also { require(it in 0..1) }
                    ?: error("revision required")
                val snapshotDigest = item.string("snapshotDigest")?.also { require(isValidSyncDigest(it)) }
                    ?: error("digest required")
                SyncChange(
                    serverSequence,
                    attemptId,
                    caseVersionId,
                    commandType,
                    revisionNumber,
                    snapshotDigest,
                )
            } ?: error("changes required")
        require(changes.size <= expectedLimit)
        require(!hasMore || changes.size == expectedLimit)
        require(changes.zipWithNext().all { (current, next) -> current.serverSequence < next.serverSequence })
        require(changes.all { it.serverSequence > cursor && it.serverSequence <= nextCursor })
        require(
            if (changes.isEmpty()) {
                nextCursor == cursor
            } else {
                changes.last().serverSequence == nextCursor
            },
        )
        SyncPullResponse(cursor, nextCursor, hasMore, changes, requestId)
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(name: String): Long? =
        (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.optionalLong(name: String): Long? =
        if (containsKey(name)) long(name) ?: error("invalid number") else null

    private fun JsonObject.int(name: String): Int? = long(name)?.toInt()

    private fun JsonObject.boolean(name: String): Boolean? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
}

internal fun createPlatformSyncGateway(): SyncGateway {
    val accountConfiguration = createAccountClientConfiguration()
    return SyncGateway(
        configuration = SyncClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
        transport = createAccountHttpTransport(),
        secureSessionStore = SecureSessionStoreFactory.create(),
        nowEpochSeconds = { Clock.System.now().epochSeconds },
    )
}
