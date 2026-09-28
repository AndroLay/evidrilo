package dev.nextgen.mobile.projectcatalog

import dev.nextgen.mobile.account.AccountHttpResponse
import dev.nextgen.mobile.account.AccountHttpTransport
import dev.nextgen.mobile.account.MAX_ACCOUNT_HTTP_BODY_BYTES
import dev.nextgen.mobile.account.isAllowedApiBaseUrl
import dev.nextgen.mobile.domain.project.ProjectTemplateCatalog
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import dev.nextgen.mobile.domain.project.ProjectAiStageOperation
import dev.nextgen.mobile.domain.project.ProjectTemplateAiOperationCapability
import dev.nextgen.mobile.network.DeviceConnectivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.coroutines.cancellation.CancellationException

data class ProjectTemplateClientConfiguration(
    val apiBaseUrl: String,
) {
    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = isAllowedApiBaseUrl(normalizedApiBaseUrl)
}

data class ProjectTemplateRemoteFamily(
    val family: ProjectTemplateFamily,
    val selectableTemplateCount: Int,
)

data class ProjectTemplateSummary(
    val id: String,
    val version: Int,
    val family: ProjectTemplateFamily,
    val title: String,
    val summary: String,
)

enum class ProjectTemplateCatalogUnavailableReason {
    NOT_CONFIGURED,
    OFFLINE,
}

sealed interface ProjectTemplateCatalogGatewayResult<out T> {
    data class Loaded<T>(val value: T) : ProjectTemplateCatalogGatewayResult<T>

    data class Unavailable(
        val reason: ProjectTemplateCatalogUnavailableReason,
    ) : ProjectTemplateCatalogGatewayResult<Nothing>

    data class Failed(
        val code: String,
        val message: String,
        val retryable: Boolean,
    ) : ProjectTemplateCatalogGatewayResult<Nothing>
}

/** Anonymous, read-only client for versioned published project templates. */
class ProjectTemplateCatalogGateway(
    private val configuration: ProjectTemplateClientConfiguration,
    private val transport: AccountHttpTransport,
    private val json: Json = Json { isLenient = false },
) {
    suspend fun listFamilies(): ProjectTemplateCatalogGatewayResult<List<ProjectTemplateRemoteFamily>> =
        when (val response = get("/v1/project-template-families", "CATALOG_NOT_FOUND")) {
            is ProjectTemplateCatalogGatewayResult.Loaded -> {
                val families = parseFamilies(response.value.body)
                if (families == null) invalidResponse() else ProjectTemplateCatalogGatewayResult.Loaded(families)
            }

            is ProjectTemplateCatalogGatewayResult.Unavailable -> response
            is ProjectTemplateCatalogGatewayResult.Failed -> response
        }

    suspend fun listTemplates(
        family: ProjectTemplateFamily,
    ): ProjectTemplateCatalogGatewayResult<List<ProjectTemplateSummary>> {
        val templates = mutableListOf<ProjectTemplateSummary>()
        val seenIds = mutableSetOf<String>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null

        repeat(MAX_CATALOG_PAGES) {
            val path = buildString {
                append("/v1/project-templates?family=")
                append(family.id)
                append("&limit=")
                append(CATALOG_PAGE_SIZE)
                cursor?.let {
                    append("&afterTemplateId=")
                    append(it)
                }
            }
            when (val response = get(path, "TEMPLATE_NOT_FOUND")) {
                is ProjectTemplateCatalogGatewayResult.Unavailable -> return response
                is ProjectTemplateCatalogGatewayResult.Failed -> return response
                is ProjectTemplateCatalogGatewayResult.Loaded -> {
                    val page = parseTemplatePage(response.value.body, family) ?: return invalidResponse()
                    if (page.templates.any { !seenIds.add(it.id) }) return invalidResponse()
                    templates += page.templates
                    cursor = page.nextAfterTemplateId
                    if (cursor == null) return ProjectTemplateCatalogGatewayResult.Loaded(templates.toList())
                    if (!seenCursors.add(cursor) || page.templates.isEmpty()) return invalidResponse()
                }
            }
        }

        return if (cursor == null) {
            ProjectTemplateCatalogGatewayResult.Loaded(templates.toList())
        } else {
            ProjectTemplateCatalogGatewayResult.Failed(
                code = "CATALOG_PAGE_LIMIT_EXCEEDED",
                message = "The project-template catalog is too large to load safely.",
                retryable = false,
            )
        }
    }

    suspend fun getTemplate(
        templateId: String,
        expectedVersion: Int,
        expectedFamily: ProjectTemplateFamily,
    ): ProjectTemplateCatalogGatewayResult<ProjectTemplateDefinition> {
        if (!templateIdPattern.matches(templateId) || expectedVersion < 1) {
            return ProjectTemplateCatalogGatewayResult.Failed(
                code = "INVALID_TEMPLATE_VERSION",
                message = "The project template reference is invalid.",
                retryable = false,
            )
        }

        return when (val response = get(
            "/v1/project-templates/$templateId/versions/$expectedVersion",
            "TEMPLATE_NOT_FOUND",
        )) {
            is ProjectTemplateCatalogGatewayResult.Unavailable -> response
            is ProjectTemplateCatalogGatewayResult.Failed -> response
            is ProjectTemplateCatalogGatewayResult.Loaded -> {
                val template = parseTemplateDetail(response.value.body, templateId, expectedVersion, expectedFamily)
                    ?: return invalidResponse()
                when (ProjectTemplateCatalog.validateReadablePublishedTemplate(template).isEmpty()) {
                    true -> ProjectTemplateCatalogGatewayResult.Loaded(template)
                    false -> ProjectTemplateCatalogGatewayResult.Failed(
                        code = "TEMPLATE_NOT_READY",
                        message = "This template detail is invalid or lacks required review metadata.",
                        retryable = false,
                    )
                }
            }
        }
    }

    private suspend fun get(
        path: String,
        notFoundCode: String,
    ): ProjectTemplateCatalogGatewayResult<AccountHttpResponse> {
        if (!configuration.isConfigured) {
            return ProjectTemplateCatalogGatewayResult.Unavailable(ProjectTemplateCatalogUnavailableReason.NOT_CONFIGURED)
        }

        val response = try {
            transport.request(
                method = "GET",
                url = "${configuration.normalizedApiBaseUrl}$path",
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (transport.deviceConnectivity == DeviceConnectivity.OFFLINE) {
                return ProjectTemplateCatalogGatewayResult.Unavailable(ProjectTemplateCatalogUnavailableReason.OFFLINE)
            }
            return ProjectTemplateCatalogGatewayResult.Failed(
                code = "CATALOG_UNAVAILABLE",
                message = "The project-template catalog could not be reached.",
                retryable = true,
            )
        }

        if (response.statusCode == 404) {
            return ProjectTemplateCatalogGatewayResult.Failed(
                code = notFoundCode,
                message = "The published project template was not found.",
                retryable = false,
            )
        }
        if (response.statusCode == 408 || response.statusCode == 425 || response.statusCode == 429 ||
            response.statusCode in 500..599
        ) {
            return ProjectTemplateCatalogGatewayResult.Failed(
                code = "CATALOG_UNAVAILABLE",
                message = "The project-template catalog is temporarily unavailable.",
                retryable = true,
            )
        }
        if (response.statusCode !in 200..299) {
            return ProjectTemplateCatalogGatewayResult.Failed(
                code = "CATALOG_REQUEST_REJECTED",
                message = "The project-template catalog request was rejected.",
                retryable = false,
            )
        }
        if (response.body.encodeToByteArray().size > MAX_ACCOUNT_HTTP_BODY_BYTES) return invalidResponse()

        return ProjectTemplateCatalogGatewayResult.Loaded(response)
    }

    private fun parseFamilies(body: String): List<ProjectTemplateRemoteFamily>? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("object required")
        require(root.keys == familyResponseKeys)
        require(root.string("schema") == "evidrilo.project-template-families")
        require(root.string("version") == "1")
        require(root.string("requestId")?.let(requestIdPattern::matches) == true)
        val entries = root.array("families")?.map { element ->
            val item = element as? JsonObject ?: error("family offering required")
            require(item.keys == familyOfferingKeys)
            val familyObject = item.objectValue("family") ?: error("family required")
            require(familyObject.keys == familyKeys)
            val id = familyObject.string("id") ?: error("family id required")
            val family = ProjectTemplateFamily.entries.singleOrNull { it.id == id } ?: error("unknown family")
            require(familyObject.string("displayName") == family.displayName)
            val count = item.integer("selectableTemplateCount") ?: error("count required")
            require(count >= 0)
            ProjectTemplateRemoteFamily(family, count)
        } ?: error("families required")
        require(entries.size == ProjectTemplateFamily.entries.size)
        require(entries.map { it.family }.toSet() == ProjectTemplateFamily.entries.toSet())
        entries.sortedBy { it.family.ordinal }
    }.getOrNull()

    private fun parseTemplatePage(
        body: String,
        expectedFamily: ProjectTemplateFamily,
    ): TemplatePage? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("object required")
        require(root.keys == catalogResponseKeys)
        require(root.string("schema") == "evidrilo.project-template-catalog")
        require(root.string("version") == "1")
        require(root.string("requestId")?.let(requestIdPattern::matches) == true)
        val next = root["nextAfterTemplateId"]?.let { element ->
            if (element is JsonPrimitive && element.content == "null" && !element.isString) {
                null
            } else {
                (element as? JsonPrimitive)?.contentOrNull?.also { require(templateIdPattern.matches(it)) }
                    ?: error("cursor invalid")
            }
        } ?: if ("nextAfterTemplateId" in root) null else error("cursor required")
        val entries = root.array("templates")?.map { element ->
            val item = element as? JsonObject ?: error("template summary required")
            require(item.keys == summaryKeys)
            val id = item.string("id")?.also { require(templateIdPattern.matches(it)) }
                ?: error("template id required")
            val version = item.integer("version")?.also { require(it > 0) } ?: error("version required")
            val family = item.string("family")?.let(::familyFor) ?: error("family required")
            require(family == expectedFamily)
            val title = item.string("title")?.also { require(it.isNotBlank() && it.length <= MAX_TITLE_LENGTH) }
                ?: error("title required")
            val summary = item.string("summary")?.also { require(it.isNotBlank() && it.length <= MAX_TEXT_LENGTH) }
                ?: error("summary required")
            require(item.string("publication") == "published")
            ProjectTemplateSummary(id, version, family, title, summary)
        } ?: error("templates required")
        require(entries.size <= CATALOG_PAGE_SIZE)
        TemplatePage(entries, next)
    }.getOrNull()

    private fun parseTemplateDetail(
        body: String,
        expectedId: String,
        expectedVersion: Int,
        expectedFamily: ProjectTemplateFamily,
    ): ProjectTemplateDefinition? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: error("object required")
        require(root.keys == detailResponseKeys)
        require(root.string("schema") == "evidrilo.project-template-detail")
        require(root.string("version") == "1")
        require(root.string("requestId")?.let(requestIdPattern::matches) == true)
        val item = root.objectValue("template") ?: error("template required")
        require(item.keys == detailKeys)
        val id = item.string("id")?.also { require(templateIdPattern.matches(it) && it == expectedId) }
            ?: error("template id required")
        val version = item.integer("version")?.also { require(it == expectedVersion) }
            ?: error("template version required")
        val family = item.string("family")?.let(::familyFor) ?: error("family required")
        require(family == expectedFamily)
        val title = item.string("title")?.also { require(it.isNotBlank() && it.length <= MAX_TITLE_LENGTH) }
            ?: error("title required")
        val summary = item.string("summary")?.also { require(it.isNotBlank() && it.length <= MAX_TEXT_LENGTH) }
            ?: error("summary required")
        val intendedOutput = item.string("intendedOutput")?.also {
            require(it.isNotBlank() && it.length <= MAX_TEXT_LENGTH)
        } ?: error("intended output required")
        val inputFields = item.array("inputFields")?.map { element ->
            val field = element as? JsonObject ?: error("input field required")
            require(field.keys == inputFieldKeys)
            val fieldId = field.string("id")?.also { require(identifierPattern.matches(it)) }
                ?: error("field id required")
            val kind = field.string("kind")?.let(::inputKindFor) ?: error("input kind required")
            val label = field.string("label")?.also { require(it.isNotBlank() && it.length <= MAX_LABEL_LENGTH) }
                ?: error("field label required")
            val required = field.boolean("required") ?: error("required flag required")
            ProjectTemplateInputField(fieldId, kind, label, required)
        }?.also { require(it.isNotEmpty() && it.size <= MAX_INPUT_FIELDS) } ?: error("input fields required")
        val steps = item.array("steps")?.map { element ->
            val step = element as? JsonObject ?: error("step required")
            require(step.keys == stepKeys || step.keys == legacyStepKeys)
            val stepId = step.string("id")?.also { require(identifierPattern.matches(it)) }
                ?: error("step id required")
            val stepTitle = step.string("title")?.also { require(it.isNotBlank() && it.length <= MAX_LABEL_LENGTH) }
                ?: error("step title required")
            val inputIds = step.array("inputFieldIds")?.map { value ->
                val inputId = (value as? JsonPrimitive)?.contentOrNull ?: error("input id required")
                require(identifierPattern.matches(inputId))
                inputId
            }?.also { require(it.size <= MAX_INPUT_FIELDS) } ?: error("input ids required")
            val aiOperations = step.array("aiOperations")?.map { operationElement ->
                val operation = operationElement as? JsonObject ?: error("AI operation required")
                require(operation.keys == aiOperationKeys)
                val operationId = operation.string("id")?.also { id ->
                    require(ProjectAiStageOperation.entries.any { it.id == id })
                } ?: error("AI operation id required")
                fun fieldIds(key: String) = operation.array(key)?.map { value ->
                    val fieldId = (value as? JsonPrimitive)?.contentOrNull ?: error("AI field id required")
                    require(identifierPattern.matches(fieldId))
                    fieldId
                }?.also { require(it.size <= MAX_INPUT_FIELDS) } ?: error("AI field ids required")
                ProjectTemplateAiOperationCapability(
                    id = operationId,
                    inputFieldIds = fieldIds("inputFieldIds"),
                    outputFieldIds = fieldIds("outputFieldIds"),
                )
            }?.also { require(it.size <= MAX_AI_OPERATIONS) }
                ?: if ("aiOperations" !in step) emptyList() else error("AI operations must be an array")
            ProjectTemplateStep(stepId, stepTitle, inputIds, aiOperations)
        }?.also { require(it.isNotEmpty() && it.size <= MAX_STEPS) } ?: error("steps required")
        val limitations = item.textList("methodSpecificLimitations") ?: error("method limits required")
        val provenance = item.textList("provenanceRequirements") ?: error("provenance required")
        val accessibility = item.textList("accessibilityExpectations") ?: error("accessibility expectations required")
        val examples = item.array("examples")?.map { element ->
            val example = element as? JsonObject ?: error("example required")
            require(example.keys == exampleKeys || example.keys == legacyExampleKeys)
            val exampleId = example.string("id")?.also { require(identifierPattern.matches(it)) }
                ?: error("example id required")
            val exampleSummary = example.string("summary")?.also {
                require(it.isNotBlank() && it.length <= MAX_TEXT_LENGTH)
            } ?: error("example summary required")
            val reviewed = example.boolean("reviewed") ?: error("review status required")
            val kind = if ("kind" in example) {
                example.string("kind")?.let(::exampleKindFor) ?: error("unknown example kind")
            } else {
                ProjectTemplateExampleKind.UNSPECIFIED
            }
            ProjectTemplateExample(exampleId, exampleSummary, reviewed, kind)
        }?.also { require(it.size <= MAX_EXAMPLES) } ?: error("examples required")
        require(item.string("publication") == "published")
        val publishedAt = item.string("publishedAt")?.also { require(dateTimePattern.matches(it)) }
            ?: error("published timestamp required")
        require(publishedAt.isNotBlank())

        ProjectTemplateDefinition(
            id = id,
            version = version,
            family = family,
            title = title,
            summary = summary,
            intendedOutput = intendedOutput,
            inputFields = inputFields,
            steps = steps,
            methodSpecificLimitations = limitations,
            provenanceRequirements = provenance,
            accessibilityExpectations = accessibility,
            examples = examples,
            publication = ProjectTemplatePublication.PUBLISHED,
        )
    }.getOrNull()

    private fun familyFor(id: String): ProjectTemplateFamily? = ProjectTemplateFamily.entries.singleOrNull { it.id == id }

    private fun inputKindFor(id: String): ProjectTemplateInputKind? = when (id) {
        "assignment_brief" -> ProjectTemplateInputKind.ASSIGNMENT_BRIEF
        "research_question" -> ProjectTemplateInputKind.RESEARCH_QUESTION
        "hypothesis" -> ProjectTemplateInputKind.HYPOTHESIS
        "source" -> ProjectTemplateInputKind.SOURCE
        "data" -> ProjectTemplateInputKind.DATA
        "analysis" -> ProjectTemplateInputKind.ANALYSIS
        "claim" -> ProjectTemplateInputKind.CLAIM
        "limitation" -> ProjectTemplateInputKind.LIMITATION
        "next_action" -> ProjectTemplateInputKind.NEXT_ACTION
        else -> null
    }

    private fun exampleKindFor(id: String): ProjectTemplateExampleKind? = when (id) {
        "unspecified" -> ProjectTemplateExampleKind.UNSPECIFIED
        "normal" -> ProjectTemplateExampleKind.NORMAL
        "edge_or_conflicting" -> ProjectTemplateExampleKind.EDGE_OR_CONFLICTING
        else -> null
    }

    private fun invalidResponse(): ProjectTemplateCatalogGatewayResult.Failed =
        ProjectTemplateCatalogGatewayResult.Failed(
            code = "INVALID_CATALOG_RESPONSE",
            message = "The project-template catalog response is invalid.",
            retryable = false,
        )

    private data class TemplatePage(
        val templates: List<ProjectTemplateSummary>,
        val nextAfterTemplateId: String?,
    )

    private companion object {
        const val CATALOG_PAGE_SIZE = 24
        const val MAX_CATALOG_PAGES = 8
        const val MAX_TITLE_LENGTH = 160
        const val MAX_LABEL_LENGTH = 200
        const val MAX_TEXT_LENGTH = 1200
        const val MAX_INPUT_FIELDS = 32
        const val MAX_STEPS = 48
        const val MAX_EXAMPLES = 24
        const val MAX_AI_OPERATIONS = 8
        val templateIdPattern = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
        val identifierPattern = templateIdPattern
        val requestIdPattern = Regex("[A-Za-z0-9_-]{8,128}")
        val dateTimePattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})")
        val familyResponseKeys = setOf("schema", "version", "families", "requestId")
        val familyOfferingKeys = setOf("family", "selectableTemplateCount")
        val familyKeys = setOf("id", "displayName")
        val catalogResponseKeys = setOf("schema", "version", "templates", "nextAfterTemplateId", "requestId")
        val summaryKeys = setOf("id", "version", "family", "title", "summary", "publication")
        val detailResponseKeys = setOf("schema", "version", "template", "requestId")
        val detailKeys = setOf(
            "id", "version", "family", "title", "summary", "intendedOutput", "inputFields", "steps",
            "methodSpecificLimitations", "provenanceRequirements", "accessibilityExpectations", "examples",
            "publication", "publishedAt",
        )
        val inputFieldKeys = setOf("id", "kind", "label", "required")
        val legacyStepKeys = setOf("id", "title", "inputFieldIds")
        val stepKeys = legacyStepKeys + "aiOperations"
        val aiOperationKeys = setOf("id", "inputFieldIds", "outputFieldIds")
        val legacyExampleKeys = setOf("id", "summary", "reviewed")
        val exampleKeys = legacyExampleKeys + "kind"
    }
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.integer(name: String): Int? = (this[name] as? JsonPrimitive)
    ?.takeUnless(JsonPrimitive::isString)
    ?.intOrNull

private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.array(name: String): JsonArray? = this[name] as? JsonArray

private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.textList(name: String): List<String>? = array(name)?.map { element ->
    (element as? JsonPrimitive)?.contentOrNull?.also { require(it.isNotBlank() && it.length <= 1200) }
        ?: error("text required")
}?.also { require(it.isNotEmpty() && it.size <= 24) }
