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
import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectClaimReviewStatus
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelationType
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.domain.project.StudentProjectRevisionActor
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class StudentProjectDraftCodecTest {
    @Test
    fun `codec round trips a draft and its exact published template snapshot`() {
        val draft = draft()

        val encoded = StudentProjectDraftStoreCodec.encode(listOf(draft))

        assertIs<ProjectDraftEncodingResult.Encoded>(encoded)
        val decoded = StudentProjectDraftStoreCodec.decode(encoded.value)
        assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(decoded)
        assertEquals(listOf(draft), decoded.value)
    }

    @Test
    fun `codec rejects unsupported schema and malformed payload`() {
        assertIs<LocalStorageReadResult.Corrupt>(StudentProjectDraftStoreCodec.decode("not-json"))
        assertIs<LocalStorageReadResult.Corrupt>(StudentProjectDraftStoreCodec.decode(
            """{"schema":"evidrilo.local-project-drafts","version":"10","projects":[]}""",
        ))
    }

    @Test
    fun `codec reads and rewrites version six attachment references as metadata only`() {
        val encodedV6 = """{"schema":"evidrilo.local-project-drafts","version":"6","projects":[{"id":"manual-project-1","templateSnapshot":null,"title":"First research project","fieldValues":{},"revision":1,"createdAtEpochMillis":100,"updatedAtEpochMillis":100,"sources":[],"themes":[],"claimEvidenceSourceIds":[],"status":"draft","trashedAtEpochMillis":null,"evidenceItems":[],"findings":[],"evidenceRelations":[],"revisionSnapshots":[],"attachments":[{"id":"d07a9f35-9da0-4b68-bd6c-809d269f50fa","fileName":"study-notes.txt","mimeType":"text/plain","sizeBytes":12,"sha256":"${"0".repeat(64)}"}]}]}"""

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encodedV6),
        ).value.orEmpty().single()
        val rewritten = assertIs<ProjectDraftEncodingResult.Encoded>(
            StudentProjectDraftStoreCodec.encode(listOf(decoded)),
        )
        val root = Json.parseToJsonElement(rewritten.value).jsonObject
        val project = root.getValue("projects").jsonArray.single().jsonObject
        val attachment = project.getValue("attachments").jsonArray.single().jsonObject

        assertEquals("9", root.getValue("version").jsonPrimitive.content)
        assertEquals("study-notes.txt", attachment.getValue("fileName").jsonPrimitive.content)
        assertEquals(12, attachment.getValue("sizeBytes").jsonPrimitive.content.toInt())
        assertEquals(setOf("id", "fileName", "mimeType", "sizeBytes", "sha256"), attachment.keys)
    }

    @Test
    fun `version five projects remain readable and gain an empty attachment list`() {
        val encodedV5 = """{"schema":"evidrilo.local-project-drafts","version":"5","projects":[{"id":"manual-project-1","templateSnapshot":null,"title":"First research project","fieldValues":{},"revision":1,"createdAtEpochMillis":100,"updatedAtEpochMillis":100,"sources":[],"themes":[],"claimEvidenceSourceIds":[],"status":"draft","trashedAtEpochMillis":null,"evidenceItems":[],"findings":[],"evidenceRelations":[],"revisionSnapshots":[] }]}"""

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encodedV5),
        ).value.orEmpty().single()

        assertEquals(emptyList(), decoded.attachments)
        val rewritten = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(decoded)))
        assertEquals("9", Json.parseToJsonElement(rewritten.value).jsonObject.getValue("version").jsonPrimitive.content)
    }

    @Test
    fun `codec rejects duplicate local project identifiers`() {
        val result = StudentProjectDraftStoreCodec.encode(listOf(draft(), draft()))

        assertIs<ProjectDraftEncodingResult.Invalid>(result)
        assertEquals("DUPLICATE_PROJECT_ID", result.code)
    }

    @Test
    fun `codec enforces project count and a domain-aligned encoded draft budget`() {
        val tooMany = (1..501).map { index -> draft(id = "local-project-$index") }
        val tooManyResult = StudentProjectDraftStoreCodec.encode(tooMany)
        assertIs<ProjectDraftEncodingResult.Invalid>(tooManyResult)
        assertEquals("TOO_MANY_PROJECT_DRAFTS", tooManyResult.code)

        val base = draft()
        val largeTemplate = requireNotNull(base.templateSnapshot).copy(
            inputFields = (1..StudentProjectDraftRules.MAX_FIELD_COUNT).map { index ->
                ProjectTemplateInputField("field-$index", ProjectTemplateInputKind.DATA, "Field $index", true)
            },
            steps = listOf(ProjectTemplateStep("frame", "Record the project", (1..StudentProjectDraftRules.MAX_FIELD_COUNT).map { "field-$it" })),
        )
        val largeBase = base.copy(
            templateSnapshot = largeTemplate,
            fieldValues = largeTemplate.inputFields.associate { field -> field.id to "x".repeat(8_000) },
            revision = 2,
            updatedAtEpochMillis = base.createdAtEpochMillis + 2,
        )
        val largeButSaveable = largeBase.copy(revision = base.revision, revisionSnapshots = emptyList())
        assertTrue(StudentProjectDraftRules.validate(largeButSaveable).isEmpty())
        assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(largeButSaveable)))
        val snapshots = (1..2).map { revision ->
            StudentProjectDraftRules.captureRevisionSnapshot(
                largeBase.copy(revision = revision, updatedAtEpochMillis = 100L + revision),
                StudentProjectRevisionActor.STUDENT,
                "checkpoint $revision",
            )
        }
        val tooLarge = largeBase.copy(revisionSnapshots = snapshots)
        assertTrue("PROJECT_DRAFT_TOO_LARGE" in StudentProjectDraftRules.validate(tooLarge))
        val tooLargeResult = StudentProjectDraftStoreCodec.encode(listOf(tooLarge))
        assertIs<ProjectDraftEncodingResult.Invalid>(tooLargeResult)
        assertEquals("PROJECT_DRAFT_TOO_LARGE", tooLargeResult.code)
    }

    @Test
    fun `codec rejects an unselectable or malformed template snapshot`() {
        val draft = draft().copy(
            templateSnapshot = requireNotNull(draft().templateSnapshot).copy(publication = ProjectTemplatePublication.DRAFT),
        )

        val result = StudentProjectDraftStoreCodec.encode(listOf(draft))

        assertIs<ProjectDraftEncodingResult.Invalid>(result)
        assertEquals("INVALID_PROJECT_TEMPLATE_SNAPSHOT", result.code)
    }

    @Test
    fun `empty storage decodes as an empty list`() {
        val encoded = StudentProjectDraftStoreCodec.encode(emptyList())
        assertIs<ProjectDraftEncodingResult.Encoded>(encoded)
        assertEquals(
            emptyList(),
            assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
                StudentProjectDraftStoreCodec.decode(encoded.value),
            ).value,
        )
    }

    @Test
    fun `codec reads a version two blank project without inventing a template`() {
        val encoded = """{"schema":"evidrilo.local-project-drafts","version":"2","projects":[{"id":"manual-project-1","templateSnapshot":null,"title":"First research project","fieldValues":{},"revision":1,"createdAtEpochMillis":100,"updatedAtEpochMillis":100,"sources":[],"themes":[],"claimEvidenceSourceIds":[],"status":"draft","trashedAtEpochMillis":null}]}"""

        val decoded = StudentProjectDraftStoreCodec.decode(encoded)

        val project = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(decoded).value.orEmpty().single()
        assertEquals("First research project", project.title)
        assertNull(project.templateSnapshot)
        assertEquals(emptyMap<String, String>(), project.fieldValues)
        assertEquals(emptyList(), project.sources)
        assertEquals(emptyList(), project.themes)
        assertEquals(emptyList(), project.evidenceItems)
        assertEquals(emptyList(), project.findings)
        assertEquals(emptyList(), project.evidenceRelations)
        assertEquals(emptyList(), project.revisionSnapshots)
    }

    @Test
    fun `codec round trips source matrix themes claim links and lifecycle`() {
        val source = StudentProjectSourceRecord(
            id = "source-1",
            title = "A student-entered source",
            authors = "Example Author",
            year = "2024",
            sourceType = "journal article",
            doiOrUrl = "https://example.invalid/article",
            accessedOn = "2026-09-27",
            citationText = "Example Author (2024). A title.",
            selectionStatus = dev.nextgen.mobile.domain.project.SourceSelectionStatus.SELECTED,
            reportedFindings = "The source reports a bounded observation.",
            studentChecked = true,
        )
        val project = draft().copy(
            sources = listOf(source),
            themes = listOf(StudentProjectSynthesisTheme(
                id = "theme-1",
                title = "Reported pattern",
                synthesis = "A student-authored comparison.",
                sourceIds = setOf(source.id),
                conflictOrVariation = "Sources report different contexts.",
            )),
            claimEvidenceSourceIds = setOf(source.id),
            status = StudentProjectStatus.TRASHED,
            trashedAtEpochMillis = 200,
        )

        val encoded = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(project)))
        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encoded.value),
        )

        assertEquals(listOf(project), decoded.value)
    }

    @Test
    fun `codec round trips stable evidence finding and typed claim relations`() {
        val project = draft().copy(
            sources = listOf(StudentProjectSourceRecord(
                id = "source-1",
                title = "Student-entered source",
                citationText = "Author (2024). Title.",
            )),
            evidenceItems = listOf(StudentProjectEvidenceItem(
                id = "evidence-1",
                sourceId = "source-1",
                excerpt = "The source reports an association.",
                locator = "Table 2",
                studentChecked = true,
            )),
            findings = listOf(StudentProjectFindingRecord(
                id = "finding-1",
                statement = "The sources report an association.",
                scopeNote = "In the studied population.",
                uncertaintyNote = "Methods differ.",
            )),
            evidenceRelations = listOf(
                StudentProjectEvidenceRelation(
                    targetType = StudentProjectEvidenceTargetType.FINDING,
                    targetId = "finding-1",
                    evidenceId = "evidence-1",
                    relation = StudentProjectEvidenceRelationType.PROVIDES_CONTEXT,
                    rationale = "Different population.",
                ),
                StudentProjectEvidenceRelation(
                    targetType = StudentProjectEvidenceTargetType.CLAIM,
                    targetId = "claim-primary",
                    evidenceId = "evidence-1",
                    relation = StudentProjectEvidenceRelationType.SUPPORTS,
                ),
            ),
        )

        val encoded = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(project)))
        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encoded.value),
        )

        assertEquals("9", Json.parseToJsonElement(encoded.value).jsonObject.getValue("version").toString().trim('"'))
        assertEquals(listOf(project), decoded.value)
    }

    @Test
    fun `codec round trips multiple stable claims and claim review state`() {
        val project = draft().copy(
            claims = listOf(
                StudentProjectClaimRecord(
                    id = "claim-one",
                    statement = "A bounded statement.",
                    scopeNote = "In the studied context.",
                    limitationsNote = "One source is not directly comparable.",
                    reviewStatus = StudentProjectClaimReviewStatus.NEEDS_REVISION,
                ),
                StudentProjectClaimRecord(
                    id = "claim-two",
                    statement = "A separate statement.",
                    reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
                ),
            ),
        )

        val encoded = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(project)))
        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encoded.value),
        )

        assertEquals("9", Json.parseToJsonElement(encoded.value).jsonObject.getValue("version").toString().trim('"'))
        assertEquals(project.claims, decoded.value.orEmpty().single().claims)
    }

    @Test
    fun `codec round trips linked limitation actions in current and immutable revision data`() {
        val finding = StudentProjectFindingRecord(id = "finding-one", statement = "A reported pattern")
        val claim = StudentProjectClaimRecord(id = "claim-one", statement = "A bounded claim")
        val limitation = StudentProjectLimitationActionRecord(
            id = "limit-one",
            boundary = "Two sources report this outcome.",
            reason = "Other sources use a different measure.",
            nextAction = "Compare studies that use the same measure.",
            affectedFindingIds = setOf(finding.id),
            affectedClaimIds = setOf(claim.id),
        )
        val project = StudentProjectDraftRules.initializeRevisionHistory(
            draft().copy(
                findings = listOf(finding),
                claims = listOf(claim),
                limitationActions = listOf(limitation),
            ),
        )

        val encoded = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(project)))
        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encoded.value),
        )

        assertEquals("9", Json.parseToJsonElement(encoded.value).jsonObject.getValue("version").jsonPrimitive.content)
        assertEquals(project, decoded.value.orEmpty().single())
    }

    @Test
    fun `codec upgrades version seven projects without inventing limitation actions`() {
        val current = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(draft()))).value
        val root = Json.parseToJsonElement(current).jsonObject
        val projectV7 = JsonObject(root.getValue("projects").jsonArray.single().jsonObject
            .filterKeys { it != "limitationActions" })
            .withoutTemplateAiOperations()
        val encodedV7 = buildJsonObject {
            put("schema", root.getValue("schema"))
            put("version", "7")
            put("projects", JsonArray(listOf(JsonObject(projectV7))))
        }.toString()

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encodedV7),
        ).value.orEmpty().single()

        assertEquals(emptyList(), decoded.limitationActions)
        val rewritten = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(decoded)))
        assertEquals("9", Json.parseToJsonElement(rewritten.value).jsonObject.getValue("version").jsonPrimitive.content)
    }

    @Test
    fun `codec upgrades a version six scalar claim to a stable primary claim`() {
        val legacy = draft().copy(
            templateSnapshot = null,
            fieldValues = mapOf(ManualLiteratureSynthesisFields.CLAIM to "An older bounded claim."),
            evidenceRelations = listOf(StudentProjectEvidenceRelation(
                targetType = StudentProjectEvidenceTargetType.CLAIM,
                targetId = StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID,
                evidenceId = "evidence-1",
                relation = StudentProjectEvidenceRelationType.SUPPORTS,
            )),
            sources = listOf(StudentProjectSourceRecord(id = "source-1", title = "Study", citationText = "Citation")),
            evidenceItems = listOf(StudentProjectEvidenceItem(id = "evidence-1", sourceId = "source-1", excerpt = "Note")),
        )
        val current = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(legacy))).value
        val root = Json.parseToJsonElement(current).jsonObject
        val project = root.getValue("projects").jsonArray.single().jsonObject
            .filterKeys { it != "claims" && it != "limitationActions" }
        val encodedV6 = buildJsonObject {
            put("schema", root.getValue("schema"))
            put("version", "6")
            put("projects", JsonArray(listOf(JsonObject(project))))
        }.toString()

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encodedV6),
        ).value.orEmpty().single()

        assertEquals(StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID, decoded.claims.single().id)
        assertEquals("An older bounded claim.", decoded.claims.single().statement)
        assertEquals("evidence-1", decoded.evidenceRelations.single().evidenceId)
    }

    @Test
    fun `codec migrates a version three project graph without inventing revision snapshots`() {
        val project = draft().copy(
            sources = listOf(StudentProjectSourceRecord(id = "source-1", title = "Study", citationText = "Citation")),
            evidenceItems = listOf(StudentProjectEvidenceItem(id = "evidence-1", sourceId = "source-1", excerpt = "Note")),
            findings = listOf(StudentProjectFindingRecord(id = "finding-1", statement = "Finding")),
            evidenceRelations = listOf(StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.FINDING,
                "finding-1",
                "evidence-1",
                StudentProjectEvidenceRelationType.SUPPORTS,
            )),
        )
        val root = Json.parseToJsonElement(
            assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(project))).value,
        ).jsonObject
        val v3Project = JsonObject(root.getValue("projects").jsonArray.single().jsonObject
            .filterKeys { it != "revisionSnapshots" && it != "attachments" && it != "claims" && it != "limitationActions" })
            .withoutTemplateAiOperations()
        val encodedV3 = buildJsonObject {
            put("schema", root.getValue("schema"))
            put("version", "3")
            put("projects", JsonArray(listOf(JsonObject(v3Project))))
        }.toString()

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encodedV3),
        )

        assertEquals(listOf(project.copy(revisionSnapshots = emptyList())), decoded.value)
    }

    @Test
    fun `codec preserves immutable revision snapshots and actor metadata`() {
        val initial = StudentProjectDraftRules.initializeRevisionHistory(draft())
        val updated = initial.copy(
            revision = initial.revision + 1,
            updatedAtEpochMillis = 300,
            title = "Renamed project",
        )
        val updatedSnapshot = StudentProjectDraftRules.captureRevisionSnapshot(
            updated,
            StudentProjectRevisionActor.STUDENT,
            "project name",
        )
        val project = updated.copy(revisionSnapshots = initial.revisionSnapshots + updatedSnapshot)

        val encoded = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(project)))
        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encoded.value),
        )

        assertEquals(listOf(project), decoded.value)
    }

    @Test
    fun `codec migrates version eight template snapshots without losing project state`() {
        val original = draft()
        val current = assertIs<ProjectDraftEncodingResult.Encoded>(
            StudentProjectDraftStoreCodec.encode(listOf(original)),
        ).value
        val root = Json.parseToJsonElement(current).jsonObject
        val legacyProject = root.getValue("projects").jsonArray.single().jsonObject.withoutTemplateAiOperations()
        val encodedV8 = buildJsonObject {
            put("schema", root.getValue("schema"))
            put("version", "8")
            put("projects", JsonArray(listOf(legacyProject)))
        }.toString()

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encodedV8),
        )

        assertEquals(listOf(original), decoded.value)
        val rewritten = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(decoded.value.orEmpty()))
        assertEquals("9", Json.parseToJsonElement(rewritten.value).jsonObject.getValue("version").jsonPrimitive.content)
    }

    @Test
    fun `codec persists stage scoped AI capabilities in version nine snapshots`() {
        val capability = ProjectTemplateAiOperationCapability(
            id = "explain_template_step",
            inputFieldIds = emptyList(),
            outputFieldIds = listOf("question"),
        )
        val original = draft().copy(
            templateSnapshot = requireNotNull(draft().templateSnapshot).copy(
                steps = listOf(ProjectTemplateStep("frame", "Frame", listOf("question"), listOf(capability))),
            ),
        )

        val encoded = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(original)))
        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encoded.value),
        )

        assertEquals(listOf(capability), decoded.value.orEmpty().single().templateSnapshot?.steps?.single()?.aiOperations)
    }

    @Test
    fun `codec migrates a version one template draft without changing its content`() {
        val original = draft()
        val encodedV2 = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(original))).value
        val root = Json.parseToJsonElement(encodedV2).jsonObject
        val legacyDraft = JsonObject(root.getValue("projects").jsonArray.single().jsonObject
            .filterKeys { it in setOf("id", "templateSnapshot", "title", "fieldValues", "revision", "createdAtEpochMillis", "updatedAtEpochMillis") })
            .withoutTemplateAiOperations()
        val encodedV1 = buildJsonObject {
            put("schema", root.getValue("schema"))
            put("version", "1")
            put("projects", JsonArray(listOf(JsonObject(legacyDraft))))
        }.toString()

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(encodedV1),
        )

        assertEquals(listOf(original.copy(revisionSnapshots = emptyList())), decoded.value)
    }

    @Test
    fun `codec reads version four template examples without inferring their scenario kind`() {
        val current = assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(draft()))).value
        val legacy = current
            .replace("\"version\":\"9\"", "\"version\":\"4\"")
            .replace(Regex(",\"aiOperations\":\\[\\]"), "")
            .replace(Regex(",\"attachments\":\\[\\]"), "")
            .replace(Regex(",\"claims\":\\[\\]"), "")
            .replace(Regex(",\"limitationActions\":\\[\\]"), "")
            .replace(Regex(",\"kind\":\"(?:normal|edge_or_conflicting)\""), "")

        val decoded = assertIs<LocalStorageReadResult.Success<List<StudentProjectDraft>>>(
            StudentProjectDraftStoreCodec.decode(legacy),
        ).value.orEmpty().single()

        assertEquals(
            listOf(ProjectTemplateExampleKind.UNSPECIFIED, ProjectTemplateExampleKind.UNSPECIFIED),
            decoded.templateSnapshot?.examples?.map(ProjectTemplateExample::kind),
        )
        assertIs<ProjectDraftEncodingResult.Encoded>(StudentProjectDraftStoreCodec.encode(listOf(decoded)))
    }

    private fun JsonObject.withoutTemplateAiOperations(): JsonObject {
        val template = this["templateSnapshot"] as? JsonObject ?: return this
        val steps = template["steps"] as? JsonArray ?: return this
        val legacyTemplate = JsonObject(template.toMutableMap().apply {
            this["steps"] = JsonArray(steps.map { step ->
                JsonObject(step.jsonObject.filterKeys { it != "aiOperations" })
            })
        })
        return JsonObject(toMutableMap().apply { this["templateSnapshot"] = legacyTemplate })
    }

    private fun draft(id: String = "local-project-1") = StudentProjectDraft(
        id = id,
        templateSnapshot = ProjectTemplateDefinition(
            id = "reviewed-template",
            version = 3,
            family = ProjectTemplateFamily.EXPERIMENTAL_LABORATORY,
            title = "Reviewed template",
            summary = "A bounded summary.",
            intendedOutput = "A bounded report.",
            inputFields = listOf(
                ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
            ),
            steps = listOf(ProjectTemplateStep("frame", "Frame", listOf("question"))),
            methodSpecificLimitations = listOf("A stated limitation."),
            provenanceRequirements = listOf("Record source details."),
            accessibilityExpectations = listOf("Use visible text labels."),
            examples = listOf(
                ProjectTemplateExample("example-normal", "Synthetic reviewed normal example.", true, ProjectTemplateExampleKind.NORMAL),
                ProjectTemplateExample(
                    "example-edge", "Synthetic reviewed edge example.", true,
                    ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
                ),
            ),
            publication = ProjectTemplatePublication.PUBLISHED,
        ),
        title = "My project",
        fieldValues = mapOf("question" to "Line one\nLine two | evidence; ✅"),
        revision = 2,
        createdAtEpochMillis = 100,
        updatedAtEpochMillis = 200,
    )
}
