package dev.nextgen.mobile.recommendation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal const val MAX_RECOMMENDATION_RESPONSE_BYTES: Int = 16 * 1024

private val recommendationJson = Json {
    isLenient = false
    ignoreUnknownKeys = false
}

private val recommendationKeys = setOf(
    "schema",
    "version",
    "status",
    "calculationVersion",
    "caseVersionId",
    "objective",
    "reasonCode",
    "evidenceReferences",
    "requestId",
)

private val recommendationIdentifierPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
private val recommendationRequestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")

internal fun parseRecommendation(
    body: String,
    json: Json = recommendationJson,
): RecommendationParseResult {
    if (body.encodeToByteArray().size > MAX_RECOMMENDATION_RESPONSE_BYTES) {
        return RecommendationParseResult.Rejected("RECOMMENDATION_BODY_TOO_LARGE")
    }

    return runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("object required")
        require(root.keys == recommendationKeys)
        require(root.requiredString("schema") == "evidrilo.recommendation")
        require(root.requiredString("version") == "1")
        val status = when (root.requiredString("status")) {
            "recommended" -> RecommendationStatus.RECOMMENDED
            "abstain" -> RecommendationStatus.ABSTAIN
            else -> error("unknown status")
        }
        require(root.requiredString("calculationVersion") == "recommendation.v1")
        val caseVersionId = root.optionalString("caseVersionId")
        val objective = root.optionalString("objective")
        val reason = root.requiredString("reasonCode").toRecommendationReason()
        val evidenceReferences = root.requiredStringArray("evidenceReferences")
        val requestId = root.requiredString("requestId")
        require(recommendationRequestIdPattern.matches(requestId))

        when (status) {
            RecommendationStatus.RECOMMENDED -> {
                require(caseVersionId != null && recommendationIdentifierPattern.matches(caseVersionId))
                require(objective != null && objective.length in 1..200 && objective.isNotBlank())
                require(evidenceReferences.size in 1..128)
                require(reason in RECOMMENDED_REASONS)
            }

            RecommendationStatus.ABSTAIN -> {
                require(caseVersionId == null)
                require(objective == null)
                require(evidenceReferences.isEmpty())
                require(reason in ABSTAIN_REASONS)
            }
        }

        RecommendationParseResult.Valid(
            RecommendationPayload(
                status = status,
                calculationVersion = "recommendation.v1",
                caseVersionId = caseVersionId,
                objective = objective,
                reason = reason,
                evidenceReferences = evidenceReferences,
                requestId = requestId,
            ),
        )
    }.getOrElse {
        RecommendationParseResult.Rejected("INVALID_RECOMMENDATION_RESPONSE")
    }
}

private val RECOMMENDED_REASONS = setOf(
    RecommendationReason.START_HERE,
    RecommendationReason.PRACTICE_ACTION_REQUIRED,
    RecommendationReason.NEXT_PRACTICE,
)

private val ABSTAIN_REASONS = setOf(
    RecommendationReason.NO_ELIGIBLE_CASE,
    RecommendationReason.INSUFFICIENT_PROJECTION,
)

private fun String.toRecommendationReason(): RecommendationReason = when (this) {
    "START_HERE" -> RecommendationReason.START_HERE
    "PRACTICE_ACTION_REQUIRED" -> RecommendationReason.PRACTICE_ACTION_REQUIRED
    "NEXT_PRACTICE" -> RecommendationReason.NEXT_PRACTICE
    "NO_ELIGIBLE_CASE" -> RecommendationReason.NO_ELIGIBLE_CASE
    "INSUFFICIENT_PROJECTION" -> RecommendationReason.INSUFFICIENT_PROJECTION
    else -> error("unknown reason")
}

private fun JsonObject.requiredString(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        ?.takeIf { it.isNotBlank() }
        ?: error("string required")

private fun JsonObject.optionalString(key: String): String? = when (val value = this[key]) {
    null, JsonNull -> null
    is JsonPrimitive -> value.takeIf { it.isString }?.contentOrNull ?: error("string or null required")
    else -> error("string or null required")
}

private fun JsonObject.requiredStringArray(key: String): List<String> =
    (this[key] as? JsonArray)?.map { element ->
        (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.also { require(recommendationIdentifierPattern.matches(it)) }
            ?: error("string array required")
    } ?: error("array required")

