package dev.nextgen.mobile.content

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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

internal data class ContentClientConfiguration(
    val apiBaseUrl: String,
) {
    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() {
            val normalized = normalizedApiBaseUrl
            if (!normalized.startsWith("https://", ignoreCase = true)) return false
            val authority = normalized.substringAfter("//", "").substringBeforeAny('/', '?', '#')
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

internal data class PublishedCaseSummary(
    val caseId: String,
    val caseVersionId: String,
    val title: String,
    val contentHash: String,
    val evaluatorVersion: String,
    val skillTags: List<String>,
    val objective: String,
    val difficulty: Int,
    val evidenceReferences: List<String>,
    val facts: List<PublishedCaseFact>,
    val rules: List<PublishedCaseRule>,
    val variants: List<PublishedCaseVariant>,
    val requestId: String,
)

internal data class PublishedCaseFact(
    val id: String,
    val type: String,
    val text: String,
)

internal data class PublishedCaseRule(
    val id: String,
    val outcome: String,
    val anchorIds: List<String>,
)

internal data class PublishedCaseVariant(
    val id: String,
    val removedFactIds: List<String>,
)

internal enum class ContentDeferralReason {
    NOT_CONFIGURED,
    AUTH_REQUIRED,
    SESSION_EXPIRED,
    SECURE_STORAGE,
}

internal sealed interface PublishedCaseGatewayResult {
    data class Found(val case: PublishedCaseSummary) : PublishedCaseGatewayResult

    data class Deferred(val reason: ContentDeferralReason) : PublishedCaseGatewayResult

    data class Failed(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : PublishedCaseGatewayResult
}

internal class PublishedCaseGateway(
    private val configuration: ContentClientConfiguration,
    private val transport: AccountHttpTransport,
    private val secureSessionStore: SecureSessionStore,
    private val nowEpochSeconds: () -> Long,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun get(caseVersionId: String): PublishedCaseGatewayResult {
        if (!contentIdentifierPattern.matches(caseVersionId)) {
            return PublishedCaseGatewayResult.Failed(
                code = "INVALID_CASE_VERSION",
                message = "The case version is invalid.",
                retryable = false,
            )
        }
        if (!configuration.isConfigured) {
            return PublishedCaseGatewayResult.Deferred(ContentDeferralReason.NOT_CONFIGURED)
        }
        val session = try {
            secureSessionStore.read()
        } catch (_: Exception) {
            return PublishedCaseGatewayResult.Deferred(ContentDeferralReason.SECURE_STORAGE)
        } ?: return PublishedCaseGatewayResult.Deferred(ContentDeferralReason.AUTH_REQUIRED)
        if (!session.account.emailVerified) {
            return PublishedCaseGatewayResult.Deferred(ContentDeferralReason.AUTH_REQUIRED)
        }
        if (session.material.expiresAtEpochSeconds <= nowEpochSeconds()) {
            return PublishedCaseGatewayResult.Deferred(ContentDeferralReason.SESSION_EXPIRED)
        }

        val response = try {
            transport.request(
                method = "GET",
                url = "${configuration.normalizedApiBaseUrl}/v1/cases/$caseVersionId",
                headers = mapOf(
                    "Accept" to "application/json",
                    "Authorization" to "Bearer ${session.material.accessToken}",
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return PublishedCaseGatewayResult.Failed(
                code = "OFFLINE",
                message = "Published content is unavailable offline.",
                retryable = true,
            )
        }
        return when {
            response.statusCode == 401 || response.statusCode == 403 ->
                PublishedCaseGatewayResult.Deferred(ContentDeferralReason.AUTH_REQUIRED)

            response.statusCode == 404 -> PublishedCaseGatewayResult.Failed(
                code = "CASE_NOT_FOUND",
                message = "The published case was not found.",
                retryable = false,
            )

            response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
                response.statusCode in 500..599 -> PublishedCaseGatewayResult.Failed(
                code = "CONTENT_UNAVAILABLE",
                message = "Published content is temporarily unavailable.",
                retryable = true,
            )

            response.statusCode !in 200..299 -> PublishedCaseGatewayResult.Failed(
                code = "CONTENT_REQUEST_REJECTED",
                message = "Published content could not be loaded.",
                retryable = false,
            )

            else -> parseResponse(response, caseVersionId)?.let(PublishedCaseGatewayResult::Found)
                ?: PublishedCaseGatewayResult.Failed(
                    code = "INVALID_CONTENT_RESPONSE",
                    message = "The published content response is invalid.",
                    retryable = false,
                )
        }
    }

    private fun parseResponse(
        response: AccountHttpResponse,
        expectedCaseVersionId: String,
    ): PublishedCaseSummary? = runCatching {
        val root = json.parseToJsonElement(response.body) as? JsonObject ?: error("object required")
        require(root.keys == publishedCaseKeys)
        require(root.string("schema") == "evidrilo.case-summary")
        require(root.string("version") == "1")
        val caseId = root.string("caseId")?.also { require(contentIdentifierPattern.matches(it)) }
            ?: error("case id required")
        val caseVersionId = root.string("caseVersionId")?.also {
            require(contentIdentifierPattern.matches(it))
            require(it == expectedCaseVersionId)
        } ?: error("case version required")
        val title = root.string("title")?.also { require(it.isNotBlank() && it.length <= 200) }
            ?: error("title required")
        val contentHash = root.string("contentHash")?.also { require(contentHashPattern.matches(it)) }
            ?: error("content hash required")
        val evaluatorVersion = root.string("evaluatorVersion")?.also {
            require(contentIdentifierPattern.matches(it))
        } ?: error("evaluator version required")
        val skillTags = root.array("skillTags")?.map { element ->
            val tag = (element as? JsonPrimitive)?.contentOrNull ?: error("skill tag required")
            require(contentIdentifierPattern.matches(tag))
            tag
        }?.also { require(it.isNotEmpty() && it.size <= 32) } ?: error("skills required")
        val objective = root.string("objective")?.also {
            require(it.isNotBlank() && it.length <= 200)
        } ?: error("objective required")
        val difficultyValue = (root["difficulty"] as? JsonPrimitive)?.longOrNull
            ?: error("difficulty required")
        require(difficultyValue in 1L..5L)
        val evidenceReferences = root.array("evidenceReferences")?.map { element ->
            val reference = (element as? JsonPrimitive)?.contentOrNull ?: error("evidence reference required")
            require(contentIdentifierPattern.matches(reference))
            reference
        }?.also { require(it.isNotEmpty() && it.size <= 128) } ?: error("evidence references required")
        val facts = root.array("facts")?.map { element ->
            val fact = element as? JsonObject ?: error("fact object required")
            require(fact.keys == publishedFactKeys)
            val id = fact.string("id")?.also { require(contentIdentifierPattern.matches(it)) }
                ?: error("fact id required")
            val type = fact.string("type")?.also { require(it in publishedFactTypes) }
                ?: error("fact type required")
            val text = fact.string("text")?.also { require(it.isNotBlank() && it.length <= 2000) }
                ?: error("fact text required")
            PublishedCaseFact(id, type, text)
        }?.also { facts ->
            require(facts.isNotEmpty() && facts.size <= 128)
            require(facts.map { it.id }.toSet().size == facts.size)
        } ?: error("facts required")
        val factIds = facts.map { it.id }.toSet()
        val rules = root.array("rules")?.map { element ->
            val rule = element as? JsonObject ?: error("rule object required")
            require(rule.keys == publishedRuleKeys)
            val id = rule.string("id")?.also { require(contentIdentifierPattern.matches(it)) }
                ?: error("rule id required")
            val outcome = rule.string("outcome")?.also { require(it in publishedRuleOutcomes) }
                ?: error("rule outcome required")
            val anchorIds = rule.array("anchorIds")?.map { anchorElement ->
                val anchorId = (anchorElement as? JsonPrimitive)?.contentOrNull ?: error("rule anchor required")
                require(contentIdentifierPattern.matches(anchorId))
                require(anchorId in factIds)
                anchorId
            }?.also { require(it.isNotEmpty() && it.size <= 32) } ?: error("rule anchors required")
            PublishedCaseRule(id, outcome, anchorIds)
        }?.also { rules ->
            require(rules.isNotEmpty() && rules.size <= 64)
            require(rules.map { it.id }.toSet().size == rules.size)
        } ?: error("rules required")
        val variants = root.array("variants")?.map { element ->
            val variant = element as? JsonObject ?: error("variant object required")
            require(variant.keys == publishedVariantKeys)
            val id = variant.string("id")?.also { require(contentIdentifierPattern.matches(it)) }
                ?: error("variant id required")
            val removedFactIds = variant.array("removedFactIds")?.map { factElement ->
                val factId = (factElement as? JsonPrimitive)?.contentOrNull ?: error("variant fact required")
                require(contentIdentifierPattern.matches(factId))
                require(factId in factIds)
                factId
            }?.also { require(it.isNotEmpty() && it.size <= 128) } ?: error("variant facts required")
            PublishedCaseVariant(id, removedFactIds)
        }?.also { variants ->
            require(variants.isNotEmpty() && variants.size <= 32)
            require(variants.map { it.id }.toSet().size == variants.size)
        } ?: error("variants required")
        val requestId = root.string("requestId")?.also { require(contentRequestIdPattern.matches(it)) }
            ?: error("request id required")
        PublishedCaseSummary(
            caseId,
            caseVersionId,
            title,
            contentHash,
            evaluatorVersion,
            skillTags,
            objective,
            difficultyValue.toInt(),
            evidenceReferences,
            facts,
            rules,
            variants,
            requestId,
        )
    }.getOrNull()

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray
}

private val publishedCaseKeys = setOf(
    "schema",
    "version",
    "caseId",
    "caseVersionId",
    "title",
    "contentHash",
    "evaluatorVersion",
    "skillTags",
    "objective",
    "difficulty",
    "evidenceReferences",
    "facts",
    "rules",
    "variants",
    "requestId",
)
private val publishedFactKeys = setOf("id", "type", "text")
private val publishedRuleKeys = setOf("id", "outcome", "anchorIds")
private val publishedVariantKeys = setOf("id", "removedFactIds")
private val publishedFactTypes = setOf("aim", "context", "observation", "limitation", "boundary")
private val publishedRuleOutcomes = setOf("PASS", "ACTION_REQUIRED", "CANNOT_ASSESS", "INCOMPLETE")
private val contentIdentifierPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
private val contentHashPattern = Regex("^[a-f0-9]{64}$")
private val contentRequestIdPattern = Regex("^[A-Za-z0-9_-]{8,128}$")

internal fun createPlatformPublishedCaseGateway(): PublishedCaseGateway {
    val accountConfiguration = createAccountClientConfiguration()
    return PublishedCaseGateway(
        configuration = ContentClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
        transport = createAccountHttpTransport(),
        secureSessionStore = SecureSessionStoreFactory.create(),
        nowEpochSeconds = { Clock.System.now().epochSeconds },
    )
}
