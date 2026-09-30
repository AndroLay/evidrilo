package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.ai.AiClientConfiguration
import dev.nextgen.mobile.security.SecureSessionMaterial
import dev.nextgen.mobile.security.SecureSessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant

data class ProjectAiActivityHistoryEntry(
    val activityId: String,
    val mode: String,
    val projectId: String?,
    val stageId: String?,
    val operationId: String?,
    val baseProjectRevision: Int?,
    val resultProjectRevision: Int?,
    val outcome: String,
    val createdAt: String,
    val updatedAt: String,
)

sealed interface ProjectAiActivityHistoryResult {
    data class Loaded(
        val activities: List<ProjectAiActivityHistoryEntry>,
        val nextCursor: String?,
    ) : ProjectAiActivityHistoryResult

    data class Deferred(val reason: ProjectAiActivityHistoryDeferredReason) : ProjectAiActivityHistoryResult
    data class Unavailable(val code: String) : ProjectAiActivityHistoryResult
    data class Rejected(val code: String) : ProjectAiActivityHistoryResult
}

enum class ProjectAiActivityHistoryDeferredReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

/** Fetches account-scoped activity metadata only; prompts, responses, and project text are never returned. */
class ProjectAiActivityGateway(
    private val configuration: AiClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun list(
        installationId: String,
        projectId: String?,
        limit: Int = DEFAULT_PAGE_SIZE,
        cursor: String? = null,
    ): ProjectAiActivityHistoryResult {
        if (!configuration.isConfigured) return ProjectAiActivityHistoryResult.Deferred(
            ProjectAiActivityHistoryDeferredReason.NOT_CONFIGURED,
        )
        if (!uuidPattern.matches(installationId) || projectId != null && !uuidPattern.matches(projectId) ||
            limit !in 1..MAX_PAGE_SIZE || cursor != null && (cursor.isBlank() || cursor.length > MAX_CURSOR_LENGTH)
        ) return ProjectAiActivityHistoryResult.Rejected("INVALID_PROJECT_AI_ACTIVITY_QUERY")

        val session = when (val result = readSession()) {
            is SessionResult.Ready -> result.value
            is SessionResult.Deferred -> return ProjectAiActivityHistoryResult.Deferred(result.reason)
        }
        val url = buildString {
            append(configuration.normalizedApiBaseUrl)
            append("/v1/project-ai/activity?installationId=")
            append(installationId)
            projectId?.let { append("&projectId="); append(it) }
            append("&limit=")
            append(limit)
            cursor?.let { append("&cursor="); append(encodeQueryComponent(it)) }
        }
        val response = try {
            transport.request(
                method = "GET",
                url = url,
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.accessToken}",
                ),
                body = "",
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return ProjectAiActivityHistoryResult.Unavailable("PROJECT_AI_ACTIVITY_UNAVAILABLE")
        }
        if (response.body.encodeToByteArray().size > MAX_RESPONSE_BYTES) {
            return ProjectAiActivityHistoryResult.Rejected("INVALID_PROJECT_AI_ACTIVITY_RESPONSE")
        }
        return when (response.statusCode) {
            401, 403 -> ProjectAiActivityHistoryResult.Deferred(ProjectAiActivityHistoryDeferredReason.AUTH_REQUIRED)
            408, 425, 429, in 500..599 -> ProjectAiActivityHistoryResult.Unavailable(
                errorCode(response) ?: "PROJECT_AI_ACTIVITY_UNAVAILABLE",
            )
            in 200..299 -> parse(response.body, projectId)
                ?: ProjectAiActivityHistoryResult.Rejected("INVALID_PROJECT_AI_ACTIVITY_RESPONSE")
            else -> ProjectAiActivityHistoryResult.Rejected(
                errorCode(response) ?: "PROJECT_AI_ACTIVITY_REJECTED",
            )
        }
    }

    private fun parse(body: String, expectedProjectId: String?): ProjectAiActivityHistoryResult.Loaded? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("response object required")
        require(root.keys == RESPONSE_KEYS)
        require(root.requiredString("schema") == ACTIVITY_SCHEMA)
        require(root.requiredString("version") == "1")
        val activityArray = root["activities"] as? kotlinx.serialization.json.JsonArray ?: error("activities required")
        require(activityArray.size <= MAX_PAGE_SIZE)
        val activities = activityArray.map { element ->
            val item = element as? JsonObject ?: error("activity object required")
            require(item.keys == ACTIVITY_KEYS)
            val activityId = item.requiredString("activityId").also { require(uuidPattern.matches(it)) }
            val mode = item.requiredString("mode").also { require(it in MODES) }
            val activityProjectId = item.requiredNullableString("projectId")?.also { require(uuidPattern.matches(it)) }
            if (expectedProjectId != null) require(activityProjectId == expectedProjectId)
            require((mode == "GENERAL") == (activityProjectId == null))
            val stageId = item.requiredNullableIdentifier("stageId")
            val operationId = item.requiredNullableIdentifier("operationId")
            val baseRevision = item.requiredNullableInt("baseProjectRevision")
            val resultRevision = item.requiredNullableInt("resultProjectRevision")
            val outcome = item.requiredString("outcome").also { require(it in OUTCOMES) }
            val createdAt = item.requiredTimestamp("createdAt")
            val updatedAt = item.requiredTimestamp("updatedAt")
            ProjectAiActivityHistoryEntry(
                activityId,
                mode,
                activityProjectId,
                stageId,
                operationId,
                baseRevision,
                resultRevision,
                outcome,
                createdAt,
                updatedAt,
            )
        }
        require(activities.map(ProjectAiActivityHistoryEntry::activityId).distinct().size == activities.size)
        val cursor = root.requiredNullableString("nextCursor")?.also {
            require(it.isNotBlank() && it.length <= MAX_CURSOR_LENGTH)
        }
        ProjectAiActivityHistoryResult.Loaded(activities, cursor)
    }.getOrNull()

    private fun readSession(): SessionResult {
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return SessionResult.Deferred(ProjectAiActivityHistoryDeferredReason.SECURE_STORAGE)
        } ?: return SessionResult.Deferred(ProjectAiActivityHistoryDeferredReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) return SessionResult.Deferred(ProjectAiActivityHistoryDeferredReason.AUTH_REQUIRED)
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return SessionResult.Deferred(ProjectAiActivityHistoryDeferredReason.SESSION_EXPIRED)
        }
        return SessionResult.Ready(session.material)
    }

    private fun errorCode(response: AccountHttpResponse): String? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: return@runCatching null
        root.requiredString("code").takeIf(reasonCodePattern::matches)
    }.getOrNull()

    private fun encodeQueryComponent(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val valueByte = byte.toInt() and 0xff
            val char = valueByte.toChar()
            if (valueByte in 'A'.code..'Z'.code
                || valueByte in 'a'.code..'z'.code
                || valueByte in '0'.code..'9'.code
                || char in "-_.~") append(char)
            else {
                append('%')
                append(HEX[valueByte ushr 4])
                append(HEX[valueByte and 0x0f])
            }
        }
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = this[name] as? JsonPrimitive ?: error("$name required")
        require(value.isString)
        return value.content
    }

    private fun JsonObject.requiredNullableString(name: String): String? = when (val value = this[name]) {
        JsonNull -> null
        is JsonPrimitive -> value.contentOrNull ?: error("$name must be a string")
        else -> error("$name required")
    }

    private fun JsonObject.requiredNullableIdentifier(name: String): String? = requiredNullableString(name)?.also {
        require(identifierPattern.matches(it))
    }

    private fun JsonObject.requiredNullableInt(name: String): Int? = when (val value = this[name]) {
        JsonNull -> null
        is JsonPrimitive -> value.longOrNull?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
            ?: error("$name must be a positive integer")
        else -> error("$name required")
    }

    private fun JsonObject.requiredTimestamp(name: String): String = requiredString(name).also {
        require(it.length <= 64)
        Instant.parse(it)
    }

    private sealed interface SessionResult {
        data class Ready(val value: SecureSessionMaterial) : SessionResult
        data class Deferred(val reason: ProjectAiActivityHistoryDeferredReason) : SessionResult
    }

    private companion object {
        const val ACTIVITY_SCHEMA = "evidrilo.project-ai-activity"
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val MAX_CURSOR_LENGTH = 512
        const val HEX = "0123456789ABCDEF"
        val RESPONSE_KEYS = setOf("schema", "version", "activities", "nextCursor")
        val ACTIVITY_KEYS = setOf(
            "activityId", "mode", "projectId", "stageId", "operationId", "baseProjectRevision",
            "resultProjectRevision", "outcome", "createdAt", "updatedAt",
        )
        val MODES = setOf("PROJECT", "GENERAL")
        val OUTCOMES = setOf("PENDING", "APPLIED", "EDITED", "DISMISSED", "STALE", "FAILED", "COMPLETED")
        val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
        val identifierPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
        val reasonCodePattern = Regex("^[A-Z][A-Z0-9_]{0,79}$")
    }
}
