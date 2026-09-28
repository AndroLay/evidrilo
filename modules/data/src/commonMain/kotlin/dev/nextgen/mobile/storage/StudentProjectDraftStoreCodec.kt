package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import dev.nextgen.mobile.domain.project.ProjectTemplateAiOperationCapability
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectAttachmentRef
import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectClaimReviewStatus
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelationType
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectRevisionActor
import dev.nextgen.mobile.domain.project.StudentProjectRevisionSnapshot
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.domain.project.SourceSelectionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

sealed interface ProjectDraftEncodingResult {
    data class Encoded(val value: String) : ProjectDraftEncodingResult
    data class Invalid(val code: String) : ProjectDraftEncodingResult
}

object StudentProjectDraftStoreCodec {
    private const val SCHEMA = "evidrilo.local-project-drafts"
    private const val VERSION = "9"
    private val json = Json { isLenient = false }
    private val rootKeys = setOf("schema", "version", "projects")
    private val draftKeysV1 = setOf(
        "id", "templateSnapshot", "title", "fieldValues", "revision", "createdAtEpochMillis", "updatedAtEpochMillis",
    )
    private val draftKeysV2 = draftKeysV1 + setOf(
        "sources", "themes", "claimEvidenceSourceIds", "status", "trashedAtEpochMillis",
    )
    private val draftKeysV3 = draftKeysV2 + setOf("evidenceItems", "findings", "evidenceRelations")
    private val draftKeysV4 = draftKeysV3 + "revisionSnapshots"
    private val draftKeysV6 = draftKeysV4 + "attachments"
    private val draftKeysV7 = draftKeysV6 + "claims"
    private val draftKeysV8 = draftKeysV7 + "limitationActions"
    private val templateKeys = setOf(
        "id", "version", "family", "title", "summary", "intendedOutput", "inputFields", "steps",
        "methodSpecificLimitations", "provenanceRequirements", "accessibilityExpectations", "examples", "publication",
    )
    private val inputKeys = setOf("id", "kind", "label", "required")
    private val stepKeysV8 = setOf("id", "title", "inputFieldIds")
    private val stepKeysV9 = stepKeysV8 + "aiOperations"
    private val aiOperationKeys = setOf("id", "inputFieldIds", "outputFieldIds")
    private val legacyExampleKeys = setOf("id", "summary", "reviewed")
    private val exampleKeys = legacyExampleKeys + "kind"
    private val sourceKeys = setOf(
        "id", "title", "authors", "year", "sourceType", "doiOrUrl", "accessedOn", "citationText",
        "selectionStatus", "exclusionReason", "reportedAim", "reportedContext", "reportedMethod",
        "reportedFindings", "reportedLimitations", "studentNotes", "studentChecked",
    )
    private val themeKeys = setOf("id", "title", "synthesis", "sourceIds", "conflictOrVariation")
    private val evidenceItemKeys = setOf("id", "sourceId", "excerpt", "locator", "studentChecked")
    private val findingKeys = setOf("id", "statement", "scopeNote", "uncertaintyNote")
    private val evidenceRelationKeys = setOf("targetType", "targetId", "evidenceId", "relation", "rationale")
    private val revisionSnapshotKeysV5 = setOf(
        "revision", "savedAtEpochMillis", "actor", "changeSummary", "title", "fieldValues", "sources", "themes",
        "claimEvidenceSourceIds", "evidenceItems", "findings", "evidenceRelations",
    )
    private val revisionSnapshotKeysV6 = revisionSnapshotKeysV5 + "attachments"
    private val revisionSnapshotKeysV7 = revisionSnapshotKeysV6 + "claims"
    private val revisionSnapshotKeysV8 = revisionSnapshotKeysV7 + "limitationActions"
    private val claimKeys = setOf("id", "statement", "scopeNote", "limitationsNote", "reviewStatus")
    private val limitationActionKeys = setOf(
        "id", "boundary", "reason", "nextAction", "affectedFindingIds", "affectedClaimIds",
    )

    fun encode(drafts: List<StudentProjectDraft>): ProjectDraftEncodingResult {
        if (drafts.size > StudentProjectDraftRules.MAX_STORED_PROJECTS) {
            return ProjectDraftEncodingResult.Invalid("TOO_MANY_PROJECT_DRAFTS")
        }
        if (drafts.map(StudentProjectDraft::id).toSet().size != drafts.size) {
            return ProjectDraftEncodingResult.Invalid("DUPLICATE_PROJECT_ID")
        }

        val projectElements = drafts.map { draft ->
            val issue = StudentProjectDraftRules.validate(draft).firstOrNull()
            if (issue != null) return ProjectDraftEncodingResult.Invalid(issue)
            val element = draft.toJson()
            if (element.toString().encodeToByteArray().size > StudentProjectDraftRules.MAX_ENCODED_DRAFT_BYTES) {
                return ProjectDraftEncodingResult.Invalid("PROJECT_DRAFT_TOO_LARGE")
            }
            element
        }
        val root = buildJsonObject {
            put("schema", SCHEMA)
            put("version", VERSION)
            put("projects", JsonArray(projectElements))
        }
        val encoded = root.toString()
        return if (encoded.encodeToByteArray().size <= MAX_TOTAL_STORAGE_BYTES) {
            ProjectDraftEncodingResult.Encoded(encoded)
        } else {
            ProjectDraftEncodingResult.Invalid("PROJECT_DRAFT_STORAGE_TOO_LARGE")
        }
    }

    fun decode(encoded: String): LocalStorageReadResult<List<StudentProjectDraft>> {
        if (encoded.encodeToByteArray().size > MAX_TOTAL_STORAGE_BYTES) return LocalStorageReadResult.Corrupt
        return runCatching {
            val root = json.parseToJsonElement(encoded).jsonObject
            require(root.keys == rootKeys)
            require(root.requiredString("schema") == SCHEMA)
            val version = root.requiredString("version")
            require(version in setOf("1", "2", "3", "4", "5", "6", "7", "8", VERSION))
            val projects = root["projects"]?.jsonArray ?: error("projects required")
            require(projects.size <= StudentProjectDraftRules.MAX_STORED_PROJECTS)
            val drafts = projects.map { element ->
                require(element.toString().encodeToByteArray().size <= StudentProjectDraftRules.MAX_ENCODED_DRAFT_BYTES)
                element.jsonObject.toDraft(version)
            }
            require(drafts.map(StudentProjectDraft::id).toSet().size == drafts.size)
            require(drafts.all { StudentProjectDraftRules.validate(it).isEmpty() })
            LocalStorageReadResult.Success(drafts.sortedByDescending(StudentProjectDraft::updatedAtEpochMillis))
        }.getOrElse { LocalStorageReadResult.Corrupt }
    }

    private fun StudentProjectDraft.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("templateSnapshot", templateSnapshot?.toJson() ?: JsonNull)
        put("title", title)
        put("fieldValues", buildJsonObject {
            fieldValues.keys.sorted().forEach { key -> put(key, fieldValues.getValue(key)) }
        })
        put("revision", revision)
        put("createdAtEpochMillis", createdAtEpochMillis)
        put("updatedAtEpochMillis", updatedAtEpochMillis)
        putJsonArray("sources") { sources.forEach { add(it.toJson()) } }
        putJsonArray("themes") { themes.forEach { add(it.toJson()) } }
        put("claimEvidenceSourceIds", textArray(claimEvidenceSourceIds.sorted()))
        put("status", status.name.lowercase())
        put("trashedAtEpochMillis", trashedAtEpochMillis?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("evidenceItems") { evidenceItems.forEach { add(it.toJson()) } }
        putJsonArray("findings") { findings.forEach { add(it.toJson()) } }
        putJsonArray("evidenceRelations") { evidenceRelations.forEach { add(it.toJson()) } }
        putJsonArray("revisionSnapshots") { revisionSnapshots.forEach { add(it.toJson()) } }
        putJsonArray("attachments") { attachments.forEach { add(it.toJson()) } }
        putJsonArray("claims") { claims.forEach { add(it.toJson()) } }
        putJsonArray("limitationActions") { limitationActions.forEach { add(it.toJson()) } }
    }

    private fun StudentProjectSourceRecord.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("authors", authors)
        put("year", year)
        put("sourceType", sourceType)
        put("doiOrUrl", doiOrUrl)
        put("accessedOn", accessedOn)
        put("citationText", citationText)
        put("selectionStatus", selectionStatus.name.lowercase())
        put("exclusionReason", exclusionReason)
        put("reportedAim", reportedAim)
        put("reportedContext", reportedContext)
        put("reportedMethod", reportedMethod)
        put("reportedFindings", reportedFindings)
        put("reportedLimitations", reportedLimitations)
        put("studentNotes", studentNotes)
        put("studentChecked", studentChecked)
    }

    private fun StudentProjectSynthesisTheme.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("synthesis", synthesis)
        put("sourceIds", textArray(sourceIds.sorted()))
        put("conflictOrVariation", conflictOrVariation)
    }

    private fun StudentProjectEvidenceItem.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("sourceId", sourceId)
        put("excerpt", excerpt)
        put("locator", locator)
        put("studentChecked", studentChecked)
    }

    private fun StudentProjectFindingRecord.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("statement", statement)
        put("scopeNote", scopeNote)
        put("uncertaintyNote", uncertaintyNote)
    }

    private fun StudentProjectEvidenceRelation.toJson(): JsonObject = buildJsonObject {
        put("targetType", targetType.name.lowercase())
        put("targetId", targetId)
        put("evidenceId", evidenceId)
        put("relation", relation.name.lowercase())
        put("rationale", rationale)
    }

    private fun StudentProjectAttachmentRef.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("sizeBytes", sizeBytes)
        put("sha256", sha256)
    }

    private fun StudentProjectClaimRecord.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("statement", statement)
        put("scopeNote", scopeNote)
        put("limitationsNote", limitationsNote)
        put("reviewStatus", reviewStatus.name.lowercase())
    }

    private fun StudentProjectLimitationActionRecord.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("boundary", boundary)
        put("reason", reason)
        put("nextAction", nextAction)
        put("affectedFindingIds", textArray(affectedFindingIds.sorted()))
        put("affectedClaimIds", textArray(affectedClaimIds.sorted()))
    }

    private fun StudentProjectRevisionSnapshot.toJson(): JsonObject = buildJsonObject {
        put("revision", revision)
        put("savedAtEpochMillis", savedAtEpochMillis)
        put("actor", actor.name.lowercase())
        put("changeSummary", changeSummary)
        put("title", title)
        put("fieldValues", buildJsonObject {
            fieldValues.keys.sorted().forEach { key -> put(key, fieldValues.getValue(key)) }
        })
        putJsonArray("sources") { sources.forEach { add(it.toJson()) } }
        putJsonArray("themes") { themes.forEach { add(it.toJson()) } }
        put("claimEvidenceSourceIds", textArray(claimEvidenceSourceIds.sorted()))
        putJsonArray("evidenceItems") { evidenceItems.forEach { add(it.toJson()) } }
        putJsonArray("findings") { findings.forEach { add(it.toJson()) } }
        putJsonArray("evidenceRelations") { evidenceRelations.forEach { add(it.toJson()) } }
        putJsonArray("attachments") { attachments.forEach { add(it.toJson()) } }
        putJsonArray("claims") { claims.forEach { add(it.toJson()) } }
        putJsonArray("limitationActions") { limitationActions.forEach { add(it.toJson()) } }
    }

    private fun ProjectTemplateDefinition.toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("version", version)
        put("family", family.id)
        put("title", title)
        put("summary", summary)
        put("intendedOutput", intendedOutput)
        putJsonArray("inputFields") {
            inputFields.forEach { field -> add(buildJsonObject {
                put("id", field.id)
                put("kind", field.kind.toWireValue())
                put("label", field.label)
                put("required", field.required)
            }) }
        }
        putJsonArray("steps") {
            steps.forEach { step -> add(buildJsonObject {
                put("id", step.id)
                put("title", step.title)
                put("inputFieldIds", JsonArray(step.inputFieldIds.map(::JsonPrimitive)))
                putJsonArray("aiOperations") {
                    step.aiOperations.forEach { operation -> add(buildJsonObject {
                        put("id", operation.id)
                        put("inputFieldIds", JsonArray(operation.inputFieldIds.map(::JsonPrimitive)))
                        put("outputFieldIds", JsonArray(operation.outputFieldIds.map(::JsonPrimitive)))
                    }) }
                }
            }) }
        }
        put("methodSpecificLimitations", textArray(methodSpecificLimitations))
        put("provenanceRequirements", textArray(provenanceRequirements))
        put("accessibilityExpectations", textArray(accessibilityExpectations))
        putJsonArray("examples") {
            examples.forEach { example -> add(buildJsonObject {
                put("id", example.id)
                put("summary", example.summary)
                put("reviewed", example.reviewed)
                put("kind", example.kind.toWireValue())
            }) }
        }
        put("publication", publication.name.lowercase())
    }

    private fun JsonObject.toDraft(version: String): StudentProjectDraft {
        require(keys == when (version) {
            "1" -> draftKeysV1
            "2" -> draftKeysV2
            "3" -> draftKeysV3
            "4", "5" -> draftKeysV4
            "6" -> draftKeysV6
            "7" -> draftKeysV7
            else -> draftKeysV8
        })
        val fieldValues = getValue("fieldValues").jsonObject.mapValues { (_, value) ->
            value.primitiveString()
        }
        val templateElement = getValue("templateSnapshot")
        if (version == "1") require(templateElement != JsonNull)
        return StudentProjectDraft(
            id = requiredString("id"),
            templateSnapshot = if (templateElement == JsonNull) null else templateElement.jsonObject.toTemplate(version),
            title = requiredString("title"),
            fieldValues = fieldValues,
            revision = requiredInt("revision"),
            createdAtEpochMillis = requiredLong("createdAtEpochMillis"),
            updatedAtEpochMillis = requiredLong("updatedAtEpochMillis"),
            sources = if (version == "1") emptyList() else getValue("sources").jsonArray.map { it.jsonObject.toSource() },
            themes = if (version == "1") emptyList() else getValue("themes").jsonArray.map { it.jsonObject.toTheme() },
            claimEvidenceSourceIds = if (version == "1") emptySet() else getValue("claimEvidenceSourceIds").textArray().toSet(),
            status = if (version == "1") StudentProjectStatus.DRAFT else StudentProjectStatus.entries.single {
                it.name.equals(requiredString("status"), ignoreCase = true)
            },
            trashedAtEpochMillis = if (version == "1" || getValue("trashedAtEpochMillis") == JsonNull) {
                null
            } else {
                (getValue("trashedAtEpochMillis") as? JsonPrimitive)?.longOrNull
                    ?: error("trash timestamp must be an integer")
            },
            evidenceItems = if (version == "1" || version == "2") emptyList() else getValue("evidenceItems").jsonArray.map {
                it.jsonObject.toEvidenceItem()
            },
            findings = if (version == "1" || version == "2") emptyList() else getValue("findings").jsonArray.map {
                it.jsonObject.toFinding()
            },
            evidenceRelations = if (version == "1" || version == "2") emptyList() else getValue("evidenceRelations").jsonArray.map {
                it.jsonObject.toEvidenceRelation()
            },
            revisionSnapshots = if (version !in setOf("4", "5", "6", "7", "8", "9")) emptyList() else getValue("revisionSnapshots").jsonArray.map {
                it.jsonObject.toRevisionSnapshot(version)
            },
            attachments = if (version !in setOf("6", "7", "8", "9")) emptyList() else getValue("attachments").jsonArray.map {
                it.jsonObject.toAttachmentRef()
            },
            claims = if (version in setOf("7", "8", "9")) getValue("claims").jsonArray.map { it.jsonObject.toClaim() }
            else legacyClaims(fieldValues),
            limitationActions = if (version in setOf("8", "9")) {
                getValue("limitationActions").jsonArray.map { it.jsonObject.toLimitationAction() }
            } else {
                emptyList()
            },
        )
    }

    private fun JsonObject.toSource(): StudentProjectSourceRecord {
        require(keys == sourceKeys)
        return StudentProjectSourceRecord(
            id = requiredString("id"),
            title = requiredString("title"),
            authors = requiredString("authors"),
            year = requiredString("year"),
            sourceType = requiredString("sourceType"),
            doiOrUrl = requiredString("doiOrUrl"),
            accessedOn = requiredString("accessedOn"),
            citationText = requiredString("citationText"),
            selectionStatus = SourceSelectionStatus.entries.single {
                it.name.equals(requiredString("selectionStatus"), ignoreCase = true)
            },
            exclusionReason = requiredString("exclusionReason"),
            reportedAim = requiredString("reportedAim"),
            reportedContext = requiredString("reportedContext"),
            reportedMethod = requiredString("reportedMethod"),
            reportedFindings = requiredString("reportedFindings"),
            reportedLimitations = requiredString("reportedLimitations"),
            studentNotes = requiredString("studentNotes"),
            studentChecked = requiredBoolean("studentChecked"),
        )
    }

    private fun JsonObject.toTheme(): StudentProjectSynthesisTheme {
        require(keys == themeKeys)
        return StudentProjectSynthesisTheme(
            id = requiredString("id"),
            title = requiredString("title"),
            synthesis = requiredString("synthesis"),
            sourceIds = getValue("sourceIds").textArray().toSet(),
            conflictOrVariation = requiredString("conflictOrVariation"),
        )
    }

    private fun JsonObject.toEvidenceItem(): StudentProjectEvidenceItem {
        require(keys == evidenceItemKeys)
        return StudentProjectEvidenceItem(
            id = requiredString("id"),
            sourceId = requiredString("sourceId"),
            excerpt = requiredString("excerpt"),
            locator = requiredString("locator"),
            studentChecked = requiredBoolean("studentChecked"),
        )
    }

    private fun JsonObject.toFinding(): StudentProjectFindingRecord {
        require(keys == findingKeys)
        return StudentProjectFindingRecord(
            id = requiredString("id"),
            statement = requiredString("statement"),
            scopeNote = requiredString("scopeNote"),
            uncertaintyNote = requiredString("uncertaintyNote"),
        )
    }

    private fun JsonObject.toEvidenceRelation(): StudentProjectEvidenceRelation {
        require(keys == evidenceRelationKeys)
        return StudentProjectEvidenceRelation(
            targetType = StudentProjectEvidenceTargetType.entries.single {
                it.name.equals(requiredString("targetType"), ignoreCase = true)
            },
            targetId = requiredString("targetId"),
            evidenceId = requiredString("evidenceId"),
            relation = StudentProjectEvidenceRelationType.entries.single {
                it.name.equals(requiredString("relation"), ignoreCase = true)
            },
            rationale = requiredString("rationale"),
        )
    }

    private fun JsonObject.toRevisionSnapshot(version: String): StudentProjectRevisionSnapshot {
        require(keys == when (version) {
            "6" -> revisionSnapshotKeysV6
            "7" -> revisionSnapshotKeysV7
            "8", "9" -> revisionSnapshotKeysV8
            else -> revisionSnapshotKeysV5
        })
        return StudentProjectRevisionSnapshot(
            revision = requiredInt("revision"),
            savedAtEpochMillis = requiredLong("savedAtEpochMillis"),
            actor = StudentProjectRevisionActor.entries.single {
                it.name.equals(requiredString("actor"), ignoreCase = true)
            },
            changeSummary = requiredString("changeSummary"),
            title = requiredString("title"),
            fieldValues = getValue("fieldValues").jsonObject.mapValues { (_, value) -> value.primitiveString() },
            sources = getValue("sources").jsonArray.map { it.jsonObject.toSource() },
            themes = getValue("themes").jsonArray.map { it.jsonObject.toTheme() },
            claimEvidenceSourceIds = getValue("claimEvidenceSourceIds").textArray().toSet(),
            evidenceItems = getValue("evidenceItems").jsonArray.map { it.jsonObject.toEvidenceItem() },
            findings = getValue("findings").jsonArray.map { it.jsonObject.toFinding() },
            evidenceRelations = getValue("evidenceRelations").jsonArray.map { it.jsonObject.toEvidenceRelation() },
            attachments = if (version in setOf("6", "7", "8", "9")) getValue("attachments").jsonArray.map { it.jsonObject.toAttachmentRef() }
            else emptyList(),
            claims = if (version in setOf("7", "8", "9")) getValue("claims").jsonArray.map { it.jsonObject.toClaim() }
            else legacyClaims(getValue("fieldValues").jsonObject.mapValues { (_, value) -> value.primitiveString() }),
            limitationActions = if (version in setOf("8", "9")) {
                getValue("limitationActions").jsonArray.map { it.jsonObject.toLimitationAction() }
            } else {
                emptyList()
            },
        )
    }

    private fun JsonObject.toAttachmentRef(): StudentProjectAttachmentRef {
        require(keys == setOf("id", "fileName", "mimeType", "sizeBytes", "sha256"))
        return StudentProjectAttachmentRef(
            id = requiredString("id"),
            fileName = requiredString("fileName"),
            mimeType = requiredString("mimeType"),
            sizeBytes = requiredLong("sizeBytes"),
            sha256 = requiredString("sha256"),
        )
    }

    private fun JsonObject.toClaim(): StudentProjectClaimRecord {
        require(keys == claimKeys)
        return StudentProjectClaimRecord(
            id = requiredString("id"),
            statement = requiredString("statement"),
            scopeNote = requiredString("scopeNote"),
            limitationsNote = requiredString("limitationsNote"),
            reviewStatus = StudentProjectClaimReviewStatus.entries.single {
                it.name.equals(requiredString("reviewStatus"), ignoreCase = true)
            },
        )
    }

    private fun JsonObject.toLimitationAction(): StudentProjectLimitationActionRecord {
        require(keys == limitationActionKeys)
        return StudentProjectLimitationActionRecord(
            id = requiredString("id"),
            boundary = requiredString("boundary"),
            reason = requiredString("reason"),
            nextAction = requiredString("nextAction"),
            affectedFindingIds = getValue("affectedFindingIds").textArray().toSet(),
            affectedClaimIds = getValue("affectedClaimIds").textArray().toSet(),
        )
    }

    private fun legacyClaims(fieldValues: Map<String, String>): List<StudentProjectClaimRecord> =
        fieldValues[ManualLiteratureSynthesisFields.CLAIM]
            ?.takeIf(String::isNotBlank)
            ?.let { listOf(StudentProjectClaimRecord(StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID, statement = it)) }
            .orEmpty()

    private fun JsonObject.toTemplate(version: String): ProjectTemplateDefinition {
        require(keys == templateKeys)
        val familyId = requiredString("family")
        val family = ProjectTemplateFamily.entries.singleOrNull { it.id == familyId } ?: error("unknown family")
        val inputs = getValue("inputFields").jsonArray.map { element ->
            val field = element.jsonObject
            require(field.keys == inputKeys)
            ProjectTemplateInputField(
                id = field.requiredString("id"),
                kind = ProjectTemplateInputKind.entries.singleOrNull {
                    it.toWireValue() == field.requiredString("kind")
                } ?: error("unknown input kind"),
                label = field.requiredString("label"),
                required = field.requiredBoolean("required"),
            )
        }
        val steps = getValue("steps").jsonArray.map { element ->
            val step = element.jsonObject
            require(if (version == "9") step.keys == stepKeysV9 else step.keys == stepKeysV8)
            val aiOperations = if (version == "9") {
                step.getValue("aiOperations").jsonArray.map { operationElement ->
                    val operation = operationElement.jsonObject
                    require(operation.keys == aiOperationKeys)
                    ProjectTemplateAiOperationCapability(
                        id = operation.requiredString("id"),
                        inputFieldIds = operation.getValue("inputFieldIds").jsonArray.map { it.primitiveString() },
                        outputFieldIds = operation.getValue("outputFieldIds").jsonArray.map { it.primitiveString() },
                    )
                }
            } else {
                emptyList()
            }
            ProjectTemplateStep(
                id = step.requiredString("id"),
                title = step.requiredString("title"),
                inputFieldIds = step.getValue("inputFieldIds").jsonArray.map { it.primitiveString() },
                aiOperations = aiOperations,
            )
        }
        val examples = getValue("examples").jsonArray.map { element ->
            val example = element.jsonObject
            require(if (version in setOf("5", "6")) example.keys == exampleKeys else example.keys == exampleKeys || example.keys == legacyExampleKeys)
            ProjectTemplateExample(
                id = example.requiredString("id"),
                summary = example.requiredString("summary"),
                reviewed = example.requiredBoolean("reviewed"),
                kind = example["kind"]?.let { kindElement ->
                    ProjectTemplateExampleKind.entries.singleOrNull {
                        it.toWireValue() == kindElement.primitiveString()
                    } ?: error("unknown example kind")
                } ?: ProjectTemplateExampleKind.UNSPECIFIED,
            )
        }
        val publication = ProjectTemplatePublication.entries.singleOrNull {
            it.name.equals(requiredString("publication"), ignoreCase = true)
        } ?: error("unknown publication")
        return ProjectTemplateDefinition(
            id = requiredString("id"),
            version = requiredInt("version"),
            family = family,
            title = requiredString("title"),
            summary = requiredString("summary"),
            intendedOutput = requiredString("intendedOutput"),
            inputFields = inputs,
            steps = steps,
            methodSpecificLimitations = getValue("methodSpecificLimitations").textArray(),
            provenanceRequirements = getValue("provenanceRequirements").textArray(),
            accessibilityExpectations = getValue("accessibilityExpectations").textArray(),
            examples = examples,
            publication = publication,
        )
    }

    private fun JsonObject.requiredString(name: String): String = getValue(name).primitiveString()

    private fun JsonObject.requiredInt(name: String): Int =
        (getValue(name) as? JsonPrimitive)?.intOrNull ?: error("integer required")

    private fun JsonObject.requiredLong(name: String): Long =
        (getValue(name) as? JsonPrimitive)?.longOrNull ?: error("long required")

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        (getValue(name) as? JsonPrimitive)?.booleanOrNull ?: error("boolean required")

    private fun JsonElement.primitiveString(): String =
        (this as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
            ?: error("string required")

    private fun JsonElement.textArray(): List<String> = jsonArray.map { it.primitiveString() }

    private fun ProjectTemplateInputKind.toWireValue(): String = when (this) {
        ProjectTemplateInputKind.ASSIGNMENT_BRIEF -> "assignment_brief"
        ProjectTemplateInputKind.RESEARCH_QUESTION -> "research_question"
        ProjectTemplateInputKind.HYPOTHESIS -> "hypothesis"
        ProjectTemplateInputKind.SOURCE -> "source"
        ProjectTemplateInputKind.DATA -> "data"
        ProjectTemplateInputKind.ANALYSIS -> "analysis"
        ProjectTemplateInputKind.CLAIM -> "claim"
        ProjectTemplateInputKind.LIMITATION -> "limitation"
        ProjectTemplateInputKind.NEXT_ACTION -> "next_action"
    }

    private fun ProjectTemplateExampleKind.toWireValue(): String = when (this) {
        ProjectTemplateExampleKind.UNSPECIFIED -> "unspecified"
        ProjectTemplateExampleKind.NORMAL -> "normal"
        ProjectTemplateExampleKind.EDGE_OR_CONFLICTING -> "edge_or_conflicting"
    }

    private fun textArray(values: List<String>): JsonArray =
        buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    private const val MAX_TOTAL_STORAGE_BYTES = 16 * 1024 * 1024
}
