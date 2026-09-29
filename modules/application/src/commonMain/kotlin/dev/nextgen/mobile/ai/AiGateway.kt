package dev.nextgen.mobile.ai

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.account.isAllowedApiBaseUrl
import dev.nextgen.mobile.network.RemoteFailureKind
import dev.nextgen.mobile.network.RemoteFailureState
import dev.nextgen.mobile.network.RemoteOperationKind
import dev.nextgen.mobile.network.resolveRemoteFailure
import dev.nextgen.mobile.security.SecureSessionStore
import dev.nextgen.mobile.security.SecureSessionStoreFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

private const val MAX_AI_RESPONSE_BYTES = 64 * 1024
private const val MAX_AI_INPUT_BYTES = 16 * 1024
private const val AI_CONVERSATION_TURN_LIMIT = 5
private const val AI_CONVERSATION_MAX_HISTORY_MESSAGES = 4
private const val AI_CONVERSATION_MAX_HISTORY_CHARS = 6_000
private const val AI_CONVERSATION_MAX_MESSAGE_CHARS = 2_000

enum class AiAssistPurpose(val wireName: String) {
    EXPLAIN_FEEDBACK("explain_feedback"),
    REFLECTION_QUESTION("reflection_question"),
    LANGUAGE_ALTERNATIVE("language_alternative"),
}

data class AiAssistRequest(
    val purpose: AiAssistPurpose,
    val input: String,
    val locale: String,
    val optedIn: Boolean,
    val context: AiAssistContext? = null,
)

data class AiAssistContext(
    val caseVersionId: String,
    val feedbackCode: String,
    val feedbackStatus: String,
    val anchorIds: List<String>,
    val limitationIds: List<String>,
    val claimText: String?,
    val claimScope: String?,
    val nextAction: String?,
)

data class AiCreditGrant(
    val grantKind: String,
    val grantKey: String,
    val granted: Int,
    val reserved: Int,
    val consumed: Int,
    val available: Int,
    val expiresAt: String?,
)

data class AiCredits(
    val consentRecorded: Boolean,
    val available: Int,
    val grants: List<AiCreditGrant>,
    val requestId: String,
)

data class AiAssistResult(
    val status: String,
    val text: String?,
    val reasonCode: String?,
    val promptVersion: String,
    val groundedAnchorIds: List<String>,
    val requestId: String,
    val creditCost: Int? = null,
)

data class AiConversationHistoryMessage(
    val role: String,
    val text: String,
    val groundedAnchorIds: List<String> = emptyList(),
)

data class AiConversationSessionInfo(
    val sessionId: String,
    val caseVersionId: String,
    val contextFingerprint: String,
    val turnLimit: Int,
    val turnsUsed: Int,
    val expiresAt: String,
    val requestId: String,
)

data class AiConversationProposal(
    val field: String,
    val beforeValue: String?,
    val suggestedValue: String,
    val anchorIds: List<String>,
)

data class AiConversationTurn(
    val status: String,
    val kind: String?,
    val text: String?,
    val reasonCode: String?,
    val groundedAnchorIds: List<String>,
    val proposal: AiConversationProposal?,
    val turnsUsed: Int,
    val turnsRemaining: Int,
    val requestId: String,
    val autoApplied: Boolean,
    val creditCost: Int? = null,
)

sealed interface AiConversationGatewayResult {
    data class SessionStarted(val value: AiConversationSessionInfo) : AiConversationGatewayResult

    data class TurnReceived(val value: AiConversationTurn) : AiConversationGatewayResult

    data class Cleared(val sessionId: String) : AiConversationGatewayResult

    data class Fallback(
        val reasonCode: String,
        val turnsUsed: Int? = null,
        val turnsRemaining: Int? = null,
        val requestId: String? = null,
        val creditCost: Int? = null,
    ) : AiConversationGatewayResult

    data class Deferred(val reason: AiDeferralReason) : AiConversationGatewayResult

    data class Failed(val error: AiGatewayResult.Failed) : AiConversationGatewayResult
}

enum class AiDeferralReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

sealed interface AiGatewayResult {
    data class CreditsFound(val value: AiCredits) : AiGatewayResult

    data class AssistFound(val value: AiAssistResult) : AiGatewayResult

    data class Deferred(val reason: AiDeferralReason) : AiGatewayResult

    data class Fallback(
        val reasonCode: String,
        val text: String? = null,
        val requestId: String? = null,
        val creditCost: Int? = null,
    ) : AiGatewayResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
        val outcomeUnknown: Boolean = false,
        val reconciliationRequired: Boolean = false,
        val sameIntentReplayAllowed: Boolean = false,
        val idempotencyKey: String? = null,
    ) : AiGatewayResult
}

class AiGateway(
    private val configuration: AiClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun getCredits(): AiGatewayResult {
        val response = authorizedRequest("/v1/ai/credits", method = "GET")
        return when (response) {
            is AuthorizedAiRequest.Deferred -> AiGatewayResult.Deferred(response.reason)
            is AuthorizedAiRequest.Failed -> response.result
            is AuthorizedAiRequest.Ready -> mapResponse(
                response.response,
                invalidCode = "INVALID_AI_CREDITS_RESPONSE",
                parse = ::parseCredits,
            ) { AiGatewayResult.CreditsFound(it) }
        }
    }

    suspend fun assist(
        request: AiAssistRequest,
        idempotencyKey: String,
    ): AiGatewayResult {
        if (!request.optedIn) return AiGatewayResult.Fallback("AI_OPT_IN_REQUIRED")
        if (request.input.isBlank() || request.input.encodeToByteArray().size > MAX_AI_INPUT_BYTES) {
            return AiGatewayResult.Fallback("INVALID_AI_REQUEST")
        }
        if (request.locale.length !in 2..32 || !idempotencyPattern.matches(idempotencyKey)) {
            return AiGatewayResult.Fallback("INVALID_AI_REQUEST")
        }
        val body = buildJsonObject {
            put("purpose", request.purpose.wireName)
            put("input", request.input)
            put("locale", request.locale)
            put("optedIn", true)
            request.context?.let { context ->
                put("context", buildJsonObject {
                    put("caseVersionId", context.caseVersionId)
                    put("feedbackCode", context.feedbackCode)
                    put("feedbackStatus", context.feedbackStatus)
                    put("anchorIds", buildJsonArray { context.anchorIds.forEach { add(JsonPrimitive(it)) } })
                    put("limitationIds", buildJsonArray { context.limitationIds.forEach { add(JsonPrimitive(it)) } })
                    context.claimText?.let { claimText -> put("claimText", claimText) }
                    context.claimScope?.let { claimScope -> put("claimScope", claimScope) }
                    context.nextAction?.let { nextAction -> put("nextAction", nextAction) }
                })
            }
        }.toString()
        val response = authorizedRequest(
            path = "/v1/ai/assist",
            method = "POST",
            headers = mapOf(
                "Accept" to "application/vnd.evidrilo.ai-assist-result.v2+json",
                "Content-Type" to "application/json",
                "Idempotency-Key" to idempotencyKey,
            ),
            body = body,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            idempotencyKey = idempotencyKey,
        )
        return when (response) {
            is AuthorizedAiRequest.Deferred -> AiGatewayResult.Deferred(response.reason)
            is AuthorizedAiRequest.Failed -> response.result
            is AuthorizedAiRequest.Ready -> mapResponse(
                response.response,
                invalidCode = "INVALID_AI_ASSIST_RESPONSE",
                parse = ::parseAssist,
            ) { parsed ->
                if (parsed.status == "fallback") {
                    AiGatewayResult.Fallback(
                        parsed.reasonCode ?: "AI_UNAVAILABLE",
                        requestId = parsed.requestId,
                        creditCost = parsed.creditCost,
                    )
                } else {
                    AiGatewayResult.AssistFound(parsed)
                }
            }
        }
    }

    suspend fun startConversation(
        context: AiAssistContext,
        learnerLimitation: String?,
        locale: String,
        optedIn: Boolean,
        idempotencyKey: String,
    ): AiConversationGatewayResult {
        if (!optedIn) return AiConversationGatewayResult.Fallback("AI_OPT_IN_REQUIRED")
        if (!isValidContext(context)
            || learnerLimitation?.length?.let { it > 1_000 } == true
            || !isValidLocale(locale)
            || !conversationIdempotencyPattern.matches(idempotencyKey))
            return AiConversationGatewayResult.Fallback("INVALID_AI_CONVERSATION_REQUEST")

        val body = buildJsonObject {
            put("optedIn", true)
            put("locale", locale)
            put("context", context.toJson())
            learnerLimitation?.let { put("learnerLimitation", it) }
        }.toString()
        val response = authorizedRequest(
            path = "/v1/ai/conversations",
            method = "POST",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "Idempotency-Key" to idempotencyKey,
            ),
            body = body,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            idempotencyKey = idempotencyKey,
        )
        return when (response) {
            is AuthorizedAiRequest.Deferred -> AiConversationGatewayResult.Deferred(response.reason)
            is AuthorizedAiRequest.Failed -> AiConversationGatewayResult.Failed(response.result)
            is AuthorizedAiRequest.Ready -> parseSession(response.response, context.caseVersionId)
                ?.let(AiConversationGatewayResult::SessionStarted)
                ?: AiConversationGatewayResult.Failed(
                    AiGatewayResult.Failed("INVALID_AI_CONVERSATION_SESSION_RESPONSE", retryable = false),
                )
        }
    }

    suspend fun sendConversationTurn(
        sessionId: String,
        purpose: AiAssistPurpose,
        input: String,
        locale: String,
        optedIn: Boolean,
        context: AiAssistContext,
        learnerLimitation: String?,
        history: List<AiConversationHistoryMessage>,
        idempotencyKey: String,
    ): AiConversationGatewayResult {
        if (!optedIn) return AiConversationGatewayResult.Fallback("AI_OPT_IN_REQUIRED")
        if (!isValidSessionId(sessionId)
            || !isValidContext(context)
            || input.isBlank()
            || input.length > AI_CONVERSATION_MAX_MESSAGE_CHARS
            || learnerLimitation?.length?.let { it > 1_000 } == true
            || !isValidLocale(locale)
            || !conversationIdempotencyPattern.matches(idempotencyKey)
            || !isValidHistory(history, context))
            return AiConversationGatewayResult.Fallback("INVALID_AI_CONVERSATION_REQUEST")

        val body = buildJsonObject {
            put("purpose", purpose.wireName)
            put("input", input)
            put("locale", locale)
            put("optedIn", true)
            put("context", context.toJson())
            learnerLimitation?.let { put("learnerLimitation", it) }
            put("history", buildJsonArray {
                history.forEach { message ->
                    add(buildJsonObject {
                        put("role", message.role)
                        put("text", message.text)
                        put("groundedAnchorIds", buildJsonArray {
                            message.groundedAnchorIds.forEach { add(JsonPrimitive(it)) }
                        })
                    })
                }
            })
        }.toString()
        val response = authorizedRequest(
            path = "/v1/ai/conversations/$sessionId/turns",
            method = "POST",
            headers = mapOf(
                "Accept" to "application/vnd.evidrilo.ai-conversation-turn.v2+json",
                "Content-Type" to "application/json",
                "Idempotency-Key" to idempotencyKey,
            ),
            body = body,
            operation = RemoteOperationKind.IDEMPOTENT_MUTATION,
            idempotencyKey = idempotencyKey,
        )
        return when (response) {
            is AuthorizedAiRequest.Deferred -> AiConversationGatewayResult.Deferred(response.reason)
            is AuthorizedAiRequest.Failed -> AiConversationGatewayResult.Failed(response.result)
            is AuthorizedAiRequest.Ready -> parseTurn(response.response, purpose, context, learnerLimitation)
                ?.let { turn ->
                    if (turn.status == "fallback") {
                        AiConversationGatewayResult.Fallback(
                            turn.reasonCode!!,
                            turn.turnsUsed,
                            turn.turnsRemaining,
                            turn.requestId,
                            turn.creditCost,
                        )
                    } else {
                        AiConversationGatewayResult.TurnReceived(turn)
                    }
                }
                ?: AiConversationGatewayResult.Failed(
                    AiGatewayResult.Failed("INVALID_AI_CONVERSATION_TURN_RESPONSE", retryable = false),
                )
        }
    }

    suspend fun clearConversation(sessionId: String): AiConversationGatewayResult {
        if (!isValidSessionId(sessionId)) {
            return AiConversationGatewayResult.Fallback("INVALID_AI_CONVERSATION_REQUEST")
        }
        val response = authorizedRequest(
            path = "/v1/ai/conversations/$sessionId",
            method = "DELETE",
            operation = RemoteOperationKind.NON_IDEMPOTENT_MUTATION,
            acceptNotFound = true,
        )
        return when (response) {
            is AuthorizedAiRequest.Deferred -> AiConversationGatewayResult.Deferred(response.reason)
            is AuthorizedAiRequest.Failed -> AiConversationGatewayResult.Failed(response.result)
            is AuthorizedAiRequest.Ready -> {
                if (response.response.statusCode == 404 || parseClear(response.response, sessionId)) {
                    AiConversationGatewayResult.Cleared(sessionId)
                } else {
                    AiConversationGatewayResult.Failed(
                        AiGatewayResult.Failed("INVALID_AI_CONVERSATION_CLEAR_RESPONSE", retryable = false),
                    )
                }
            }
        }
    }

    private suspend fun authorizedRequest(
        path: String,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        operation: RemoteOperationKind = RemoteOperationKind.READ_ONLY,
        idempotencyKey: String? = null,
        acceptNotFound: Boolean = false,
    ): AuthorizedAiRequest {
        if (!configuration.isConfigured) return AuthorizedAiRequest.Deferred(AiDeferralReason.NOT_CONFIGURED)
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return AuthorizedAiRequest.Deferred(AiDeferralReason.SECURE_STORAGE)
        } ?: return AuthorizedAiRequest.Deferred(AiDeferralReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) return AuthorizedAiRequest.Deferred(AiDeferralReason.AUTH_REQUIRED)
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return AuthorizedAiRequest.Deferred(AiDeferralReason.SESSION_EXPIRED)
        }
        val requestHeaders = buildMap {
            put("Accept", "application/json")
            put("Authorization", "Bearer ${session.material.accessToken}")
            putAll(headers)
        }
        val response = try {
            transport.request(
                method = method,
                url = "${configuration.normalizedApiBaseUrl}$path",
                headers = requestHeaders,
                body = body,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return AuthorizedAiRequest.Failed(
                remoteFailure(
                    operation = operation,
                    idempotencyKey = idempotencyKey,
                    failure = RemoteFailureKind.TRANSPORT,
                ),
            )
        }
        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                AuthorizedAiRequest.Deferred(AiDeferralReason.AUTH_REQUIRED)

            response.statusCode == 404 && acceptNotFound -> AuthorizedAiRequest.Ready(response)

            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 ->
                AuthorizedAiRequest.Failed(
                    remoteFailure(
                        operation = operation,
                        idempotencyKey = idempotencyKey,
                        failure = RemoteFailureKind.TRANSIENT_HTTP,
                    ),
                )

            response.statusCode !in 200..299 ->
                AuthorizedAiRequest.Failed(
                    AiGatewayResult.Failed(
                        code = "AI_REQUEST_REJECTED",
                        retryable = false,
                    ),
                )

            else -> AuthorizedAiRequest.Ready(response)
        }
    }

    private fun remoteFailure(
        operation: RemoteOperationKind,
        idempotencyKey: String?,
        failure: RemoteFailureKind,
    ): AiGatewayResult.Failed {
        val resolution = resolveRemoteFailure(
            connectivity = transport.deviceConnectivity,
            operation = operation,
            failure = failure,
            requestWasDispatched = true,
            idempotencyKey = idempotencyKey,
        )
        val code = when (resolution.state) {
            RemoteFailureState.OFFLINE -> "AI_OFFLINE"
            RemoteFailureState.OUTCOME_UNKNOWN -> "AI_OUTCOME_UNKNOWN"
            RemoteFailureState.SERVICE_UNAVAILABLE -> "AI_UNAVAILABLE"
            RemoteFailureState.REJECTED -> "AI_REQUEST_REJECTED"
            RemoteFailureState.CANCELLED -> "AI_CANCELLED"
        }
        return AiGatewayResult.Failed(
            code = code,
            retryable = resolution.retryAllowed,
            outcomeUnknown = resolution.state == RemoteFailureState.OUTCOME_UNKNOWN,
            reconciliationRequired = resolution.reconciliationRequired,
            sameIntentReplayAllowed = resolution.sameIntentReplayAllowed,
            idempotencyKey = idempotencyKey.takeIf { resolution.sameIntentReplayAllowed },
        )
    }

    private fun <T> mapResponse(
        response: AccountHttpResponse,
        invalidCode: String,
        parse: (AccountHttpResponse) -> T?,
        map: (T) -> AiGatewayResult,
    ): AiGatewayResult = if (response.body.encodeToByteArray().size > MAX_AI_RESPONSE_BYTES) {
        AiGatewayResult.Failed(invalidCode, retryable = false)
    } else {
        parse(response)?.let(map) ?: AiGatewayResult.Failed(invalidCode, retryable = false)
    }

    private fun parseSession(response: AccountHttpResponse, expectedCaseVersionId: String): AiConversationSessionInfo? =
        runCatching {
            require(response.statusCode in 200..299)
            require(response.body.encodeToByteArray().size <= MAX_AI_RESPONSE_BYTES)
            val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
            require(root.keys == conversationSessionKeys)
            require(root.strictString("schema") == "evidrilo.ai-conversation-session")
            require(root.strictString("version") == "1")
            require(root.strictString("status") == "active")
            val sessionId = root.strictString("sessionId").also { require(sessionIdPattern.matches(it)) }
            val caseVersionId = root.strictString("caseVersionId")
                .also { require(caseVersionPattern.matches(it) && it == expectedCaseVersionId) }
            val fingerprint = root.strictString("contextFingerprint")
                .also { require(contextFingerprintPattern.matches(it)) }
            val turnLimit = root.int("turnLimit").also { require(it == AI_CONVERSATION_TURN_LIMIT) }
            val turnsUsed = root.int("turnsUsed").also { require(it in 0..turnLimit) }
            val expiresAt = root.strictString("expiresAt").also { require(dateTimeOffsetPattern.matches(it)) }
            AiConversationSessionInfo(
                sessionId,
                caseVersionId,
                fingerprint,
                turnLimit,
                turnsUsed,
                expiresAt,
                root.requestId(),
            )
        }.getOrNull()

    private fun parseTurn(
        response: AccountHttpResponse,
        purpose: AiAssistPurpose,
        context: AiAssistContext,
        learnerLimitation: String?,
    ): AiConversationTurn? = runCatching {
        require(response.statusCode in 200..299)
        require(response.body.encodeToByteArray().size <= MAX_AI_RESPONSE_BYTES)
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        val version = root.strictString("version").also { require(it == "1" || it == "2") }
        val hasCreditCost = version == "2"
        val requiredKeys = if (hasCreditCost) conversationTurnRequiredKeys + "creditCost" else conversationTurnRequiredKeys
        val allowedKeys = if (hasCreditCost) conversationTurnAllowedKeys + "creditCost" else conversationTurnAllowedKeys
        require(root.keys.containsAll(requiredKeys))
        require(root.keys.all { it in allowedKeys })
        require(root.strictString("schema") == "evidrilo.ai-conversation-turn")
        val creditCost = if (hasCreditCost) root.int("creditCost").also { require(it in 0..MAX_AI_CREDIT_GRANT) } else null
        val status = root.strictString("status").also { require(it == "success" || it == "fallback") }
        val kind = root.strictOptionalString("kind")
        val text = root.strictOptionalString("text")
        val reasonCode = root.strictOptionalString("reasonCode")
        val groundedAnchorIds = root.stringArray("groundedAnchorIds", maxItems = 32)
        require(groundedAnchorIds.distinct().size == groundedAnchorIds.size)
        val allowedAnchors = (context.anchorIds + context.limitationIds).toSet()
        require(groundedAnchorIds.all { it in allowedAnchors })
        val proposal = root.strictOptionalObject("proposal")?.let { parseProposal(it, allowedAnchors, groundedAnchorIds) }
        val autoApplied = root.boolean("autoApplied").also { require(!it) }
        val turnsUsed = root.int("turnsUsed").also { require(it in 0..AI_CONVERSATION_TURN_LIMIT) }
        val turnsRemaining = root.int("turnsRemaining").also { require(it in 0..AI_CONVERSATION_TURN_LIMIT) }
        require(turnsUsed + turnsRemaining == AI_CONVERSATION_TURN_LIMIT)
        val requestId = root.requestId()

        if (status == "success") {
            require(creditCost == null || creditCost in 1..MAX_AI_CREDIT_GRANT)
            require(kind != null && text != null && text.isNotBlank() && text.length <= AI_CONVERSATION_MAX_MESSAGE_CHARS)
            require(reasonCode == null)
            require(groundedAnchorIds.isNotEmpty())
            require(
                when (purpose) {
                    AiAssistPurpose.EXPLAIN_FEEDBACK -> kind == "explanation"
                    AiAssistPurpose.REFLECTION_QUESTION -> kind == "reflection_question"
                    AiAssistPurpose.LANGUAGE_ALTERNATIVE -> kind in setOf("language_alternative", "draft_proposal")
                },
            )
            require((kind == "draft_proposal") == (proposal != null))
            if (proposal != null) {
                val currentValue = when (proposal.field) {
                    "claim_text" -> context.claimText
                    "claim_scope" -> context.claimScope
                    "learner_limitation" -> learnerLimitation
                    "next_action" -> context.nextAction
                    else -> error("proposal field not allowed")
                }
                require(proposal.beforeValue == currentValue)
                require(proposal.suggestedValue != proposal.beforeValue)
            }
        } else {
            require(kind == null && text == null && proposal == null)
            require(reasonCode != null && reasonPattern.matches(reasonCode))
            require(groundedAnchorIds.isEmpty())
        }
        AiConversationTurn(
            status,
            kind,
            text,
            reasonCode,
            groundedAnchorIds,
            proposal,
            turnsUsed,
            turnsRemaining,
            requestId,
            autoApplied,
            creditCost,
        )
    }.getOrNull()

    private fun parseProposal(
        value: JsonObject,
        allowedAnchors: Set<String>,
        groundedAnchorIds: List<String>,
    ): AiConversationProposal {
        require(value.keys == conversationProposalKeys)
        val field = value.strictString("field").also {
            require(it in setOf("claim_text", "claim_scope", "learner_limitation", "next_action"))
        }
        val beforeValue = when (val raw = value["beforeValue"]) {
            JsonNull -> null
            is JsonPrimitive -> raw.contentOrNull?.also { require(raw.isString) }
                ?: error("before value must be a string or null")
            else -> error("before value required")
        }
        require(beforeValue == null || beforeValue.length <= AI_CONVERSATION_MAX_MESSAGE_CHARS)
        val suggestedValue = value.strictString("suggestedValue")
            .also { require(it.isNotBlank() && it.length <= AI_CONVERSATION_MAX_MESSAGE_CHARS) }
        val anchors = value.stringArray("anchorIds", maxItems = 32).also {
            require(it.isNotEmpty() && it.distinct().size == it.size)
            require(it.all { id -> id in allowedAnchors && id in groundedAnchorIds })
        }
        return AiConversationProposal(field, beforeValue, suggestedValue, anchors)
    }

    private fun parseClear(response: AccountHttpResponse, expectedSessionId: String): Boolean = runCatching {
        require(response.statusCode in 200..299)
        require(response.body.encodeToByteArray().size <= MAX_AI_RESPONSE_BYTES)
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == conversationClearKeys)
        require(root.strictString("schema") == "evidrilo.ai-conversation-clear")
        require(root.strictString("version") == "1")
        require(root.strictString("status") == "cleared")
        require(root.strictString("sessionId") == expectedSessionId)
        root.requestId()
        true
    }.getOrDefault(false)

    private fun isValidContext(context: AiAssistContext): Boolean {
        val validAnchors = context.anchorIds.size in 1..32
            && context.anchorIds.distinct().size == context.anchorIds.size
            && context.anchorIds.all(anchorIdPattern::matches)
        val validLimitations = context.limitationIds.size <= 32
            && context.limitationIds.distinct().size == context.limitationIds.size
            && context.limitationIds.all(anchorIdPattern::matches)
        return caseVersionPattern.matches(context.caseVersionId)
            && feedbackCodePattern.matches(context.feedbackCode)
            && context.feedbackStatus in validFeedbackStatuses
            && validAnchors
            && validLimitations
            && context.anchorIds.none { it in context.limitationIds }
            && (context.claimText?.length ?: 0) <= 2_000
            && (context.claimScope?.length ?: 0) <= 128
            && (context.nextAction?.length ?: 0) <= 1_000
    }

    private fun isValidHistory(history: List<AiConversationHistoryMessage>, context: AiAssistContext): Boolean {
        if (history.size > AI_CONVERSATION_MAX_HISTORY_MESSAGES) return false
        if (history.sumOf { it.text.length } > AI_CONVERSATION_MAX_HISTORY_CHARS) return false
        val allowedAnchors = (context.anchorIds + context.limitationIds).toSet()
        return history.all { message ->
            message.text.isNotBlank()
                && message.text.length <= AI_CONVERSATION_MAX_MESSAGE_CHARS
                && when (message.role) {
                    "user" -> message.groundedAnchorIds.isEmpty()
                    "assistant" -> message.groundedAnchorIds.size in 1..32
                        && message.groundedAnchorIds.distinct().size == message.groundedAnchorIds.size
                        && message.groundedAnchorIds.all { it in allowedAnchors }
                    else -> false
                }
        }
    }

    private fun isValidLocale(locale: String): Boolean = localePattern.matches(locale)

    private fun isValidSessionId(sessionId: String): Boolean = sessionIdPattern.matches(sessionId)

    private fun AiAssistContext.toJson() = buildJsonObject {
        put("caseVersionId", caseVersionId)
        put("feedbackCode", feedbackCode)
        put("feedbackStatus", feedbackStatus)
        put("anchorIds", buildJsonArray { anchorIds.forEach { add(JsonPrimitive(it)) } })
        put("limitationIds", buildJsonArray { limitationIds.forEach { add(JsonPrimitive(it)) } })
        claimText?.let { put("claimText", it) }
        claimScope?.let { put("claimScope", it) }
        nextAction?.let { put("nextAction", it) }
    }

    private fun parseCredits(response: AccountHttpResponse): AiCredits? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == creditsKeys)
        require(root.string("schema") == "evidrilo.ai-credits")
        require(root.string("version") == "1")
        val consent = root.boolean("consentRecorded")
        val available = root.int("available")
        require(available in 0..MAX_AI_CREDIT_BALANCE)
        val grants = root["grants"]?.jsonArray?.map { element ->
            val grant = element as? JsonObject ?: error("grant object required")
            require(grant.keys == grantKeys)
            val kind = grant.string("grantKind")?.also { require(it in setOf("free_once", "subscription_month")) }
                ?: error("grant kind required")
            val key = grant.string("grantKey")?.also { require(it.length in 1..64) }
                ?: error("grant key required")
            val granted = grant.int("granted").also { require(it in 1..MAX_AI_CREDIT_GRANT) }
            val reserved = grant.int("reserved").also { require(it in 0..MAX_AI_CREDIT_GRANT) }
            val consumed = grant.int("consumed").also { require(it in 0..MAX_AI_CREDIT_GRANT) }
            val grantAvailable = grant.int("available").also {
                require(it in 0..MAX_AI_CREDIT_GRANT && it == granted - reserved - consumed)
            }
            val expiresAt = grant.optionalString("expiresAt")?.also { require(dateTimeOffsetPattern.matches(it)) }
            AiCreditGrant(kind, key, granted, reserved, consumed, grantAvailable, expiresAt)
        } ?: error("grants required")
        require(grants.sumOf { it.available } == available)
        AiCredits(consent, available, grants, root.requestId())
    }.getOrNull()

    private fun parseAssist(response: AccountHttpResponse): AiAssistResult? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        val version = root.string("version")?.also { require(it == "1" || it == "2") }
            ?: error("version required")
        val hasCreditCost = version == "2"
        require(root.keys == if (hasCreditCost) assistKeys + "creditCost" else assistKeys)
        require(root.string("schema") == "evidrilo.ai-assist-result")
        val creditCost = if (hasCreditCost) root.int("creditCost").also { require(it in 0..MAX_AI_CREDIT_GRANT) } else null
        val status = root.string("status")?.also { require(it == "success" || it == "fallback") }
            ?: error("status required")
        val text = root.optionalString("text")
        val reason = root.optionalString("reasonCode")
        val promptVersion = root.string("promptVersion")
            ?.also { require(promptVersionPattern.matches(it)) }
            ?: error("prompt version required")
        val groundedAnchorIds = root["groundedAnchorIds"]?.jsonArray?.map { element ->
            (element as? JsonPrimitive)?.contentOrNull
                ?.also { require(anchorIdPattern.matches(it)) }
                ?: error("grounded anchor id required")
        } ?: error("grounded anchor ids required")
        require(groundedAnchorIds.distinct().size == groundedAnchorIds.size)
        require(groundedAnchorIds.size <= 32)
        val requestId = root.requestId()
        if (status == "success") {
            require(creditCost == null || creditCost in 1..MAX_AI_CREDIT_GRANT)
            require(!text.isNullOrBlank() && text.length <= 2000)
            require(reason == null)
            require(groundedAnchorIds.isNotEmpty())
        } else {
            require(text == null)
            require(reason != null && reasonPattern.matches(reason))
            require(groundedAnchorIds.isEmpty())
        }
        AiAssistResult(status, text, reason, promptVersion, groundedAnchorIds, requestId, creditCost)
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.optionalString(name: String): String? = when (val value = this[name]) {
        null -> null
        is JsonPrimitive -> value.contentOrNull
        else -> error("$name must be a string or null")
    }

    private fun JsonObject.strictString(name: String): String =
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?: error("$name must be a string")

    private fun JsonObject.strictOptionalString(name: String): String? = when {
        name !in this -> null
        this[name] is JsonPrimitive && this[name] !== JsonNull -> (this[name] as JsonPrimitive)
            .takeIf { it.isString }?.contentOrNull ?: error("$name must be a string")
        else -> error("$name must be omitted or a string")
    }

    private fun JsonObject.strictOptionalObject(name: String): JsonObject? = when {
        name !in this -> null
        this[name] is JsonObject -> this[name] as JsonObject
        else -> error("$name must be omitted or an object")
    }

    private fun JsonObject.stringArray(name: String, maxItems: Int): List<String> =
        this[name]?.jsonArray?.map { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                ?.also { require(anchorIdPattern.matches(it)) }
                ?: error("$name item must be a valid string")
        }?.also { require(it.size <= maxItems) } ?: error("$name required")

    private fun JsonObject.boolean(name: String): Boolean =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
            ?: error("$name required")

    private fun JsonObject.int(name: String): Int =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()
            ?: error("$name required")

    private fun JsonObject.requestId(): String =
        strictString("requestId").also { require(requestIdPattern.matches(it)) }

    private sealed interface AuthorizedAiRequest {
        data class Ready(val response: AccountHttpResponse) : AuthorizedAiRequest
        data class Deferred(val reason: AiDeferralReason) : AuthorizedAiRequest
        data class Failed(val result: AiGatewayResult.Failed) : AuthorizedAiRequest
    }

    private companion object {
        const val MAX_AI_CREDIT_GRANT = 200
        const val MAX_AI_CREDIT_BALANCE = 220
        val creditsKeys = setOf("schema", "version", "consentRecorded", "available", "grants", "requestId")
        val grantKeys = setOf("grantKind", "grantKey", "granted", "reserved", "consumed", "available", "expiresAt")
        val assistKeys = setOf("schema", "version", "status", "text", "reasonCode", "promptVersion", "groundedAnchorIds", "requestId")
        val conversationSessionKeys = setOf(
            "schema", "version", "status", "sessionId", "caseVersionId", "contextFingerprint",
            "turnLimit", "turnsUsed", "expiresAt", "requestId",
        )
        val conversationTurnRequiredKeys = setOf(
            "schema", "version", "status", "groundedAnchorIds", "autoApplied", "turnsUsed", "turnsRemaining", "requestId",
        )
        val conversationTurnAllowedKeys = conversationTurnRequiredKeys + setOf(
            "kind", "text", "reasonCode", "proposal",
        )
        val conversationProposalKeys = setOf("field", "beforeValue", "suggestedValue", "anchorIds")
        val conversationClearKeys = setOf("schema", "version", "status", "sessionId", "requestId")
        val requestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
        val idempotencyPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
        val conversationIdempotencyPattern = Regex("^[A-Za-z0-9_-]{8,80}$")
        val promptVersionPattern = Regex("^[a-z0-9._-]{1,64}$")
        val anchorIdPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
        val caseVersionPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
        val feedbackCodePattern = Regex("^[A-Z][A-Z0-9_]{2,63}$")
        val localePattern = Regex("^[A-Za-z0-9-]{1,32}$")
        val sessionIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        val contextFingerprintPattern = Regex("^[a-f0-9]{64}$")
        val reasonPattern = Regex("^[A-Z0-9_]{3,64}$")
        val dateTimeOffsetPattern = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-](?:(?:0\\d|1[0-3]):[0-5]\\d|14:00))$")
        val validFeedbackStatuses = setOf("INCOMPLETE", "PASS", "ACTION_REQUIRED", "CANNOT_ASSESS")
    }
}

data class AiClientConfiguration(
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

fun createPlatformAiGateway(): AiGateway {
    val accountConfiguration = createAccountClientConfiguration()
    return AiGateway(
        configuration = AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
        transport = createAccountHttpTransport(),
        secureSessionStore = SecureSessionStoreFactory.create(),
        nowEpochSeconds = { Clock.System.now().epochSeconds },
    )
}
