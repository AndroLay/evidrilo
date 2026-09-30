package dev.nextgen.mobile.domain.project

data class StudentProjectDraft(
    val id: String,
    val templateSnapshot: ProjectTemplateDefinition?,
    val title: String,
    val fieldValues: Map<String, String>,
    val revision: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val sources: List<StudentProjectSourceRecord> = emptyList(),
    val themes: List<StudentProjectSynthesisTheme> = emptyList(),
    val claimEvidenceSourceIds: Set<String> = emptySet(),
    val evidenceItems: List<StudentProjectEvidenceItem> = emptyList(),
    val findings: List<StudentProjectFindingRecord> = emptyList(),
    val evidenceRelations: List<StudentProjectEvidenceRelation> = emptyList(),
    val revisionSnapshots: List<StudentProjectRevisionSnapshot> = emptyList(),
    val status: StudentProjectStatus = StudentProjectStatus.DRAFT,
    val trashedAtEpochMillis: Long? = null,
    /** Metadata only. Attachment bytes live in the project attachment store, never in this draft JSON. */
    val attachments: List<StudentProjectAttachmentRef> = emptyList(),
    /** Student-authored, independently addressable claims; empty on legacy drafts and before the first claim is added. */
    val claims: List<StudentProjectClaimRecord> = emptyList(),
    /** Student-authored limitations/actions with explicit affected finding/claim links. */
    val limitationActions: List<StudentProjectLimitationActionRecord> = emptyList(),
    /** Optional date-only deadline in ISO-8601 form (yyyy-MM-dd); it carries no reminder or timezone behavior. */
    val deadlineDate: String? = null,
)

/** Stable metadata pointer to a separately stored local file; it is not a trust or safety verdict. */
data class StudentProjectAttachmentRef(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)

object StudentProjectAttachmentRules {
    const val MAX_ATTACHMENTS = 50
    const val MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024
    const val MAX_FILE_NAME_CHARS = 160
    const val MAX_MIME_TYPE_CHARS = 128
    private val idPattern = Regex("[A-Za-z0-9_-]{1,96}")
    private val sha256Pattern = Regex("[a-f0-9]{64}")
    private val mimeTypePattern = Regex("[a-z0-9.+-]+/[a-z0-9.+-]+")

    fun validate(reference: StudentProjectAttachmentRef): List<String> = buildList {
        if (!idPattern.matches(reference.id) ||
            reference.fileName.isBlank() || reference.fileName.length > MAX_FILE_NAME_CHARS ||
            reference.fileName == "." || reference.fileName == ".." ||
            reference.fileName.any { it.code < 0x20 || it.code == 0x7f || it == '/' || it == '\\' || it == ':' } ||
            reference.mimeType.length > MAX_MIME_TYPE_CHARS || !mimeTypePattern.matches(reference.mimeType) ||
            reference.sizeBytes !in 1..MAX_ATTACHMENT_BYTES || !sha256Pattern.matches(reference.sha256)
        ) add("PROJECT_ATTACHMENT_REFERENCE_INVALID")
    }
}

enum class StudentProjectStatus {
    DRAFT,
    ACTIVE,
    COMPLETED,
    ARCHIVED,
    TRASHED,
}

data class StudentProjectSourceRecord(
    val id: String,
    val title: String = "",
    val authors: String = "",
    val year: String = "",
    val sourceType: String = "",
    val doiOrUrl: String = "",
    val accessedOn: String = "",
    val citationText: String = "",
    val selectionStatus: SourceSelectionStatus = SourceSelectionStatus.TO_REVIEW,
    val exclusionReason: String = "",
    val reportedAim: String = "",
    val reportedContext: String = "",
    val reportedMethod: String = "",
    val reportedFindings: String = "",
    val reportedLimitations: String = "",
    val studentNotes: String = "",
    val studentChecked: Boolean = false,
)

enum class SourceSelectionStatus {
    TO_REVIEW,
    SELECTED,
    EXCLUDED,
}

data class StudentProjectSynthesisTheme(
    val id: String,
    val title: String = "",
    val synthesis: String = "",
    val sourceIds: Set<String> = emptySet(),
    val conflictOrVariation: String = "",
)

/** A student-entered excerpt or note anchored to one recorded source. */
data class StudentProjectEvidenceItem(
    val id: String,
    val sourceId: String,
    val excerpt: String = "",
    val locator: String = "",
    val studentChecked: Boolean = false,
)

/** A student-authored synthesis statement; storing it does not validate its meaning. */
data class StudentProjectFindingRecord(
    val id: String,
    val statement: String = "",
    val scopeNote: String = "",
    val uncertaintyNote: String = "",
)

enum class StudentProjectClaimReviewStatus {
    DRAFT,
    READY_FOR_REVIEW,
    NEEDS_REVISION,
}

/** Student-authored claim. The review status is not an evaluator or quality verdict. */
data class StudentProjectClaimRecord(
    val id: String,
    val statement: String = "",
    val scopeNote: String = "",
    val limitationsNote: String = "",
    val reviewStatus: StudentProjectClaimReviewStatus = StudentProjectClaimReviewStatus.DRAFT,
)

/** A student-recorded boundary and next step; links identify affected records, not an evaluator verdict. */
data class StudentProjectLimitationActionRecord(
    val id: String,
    val boundary: String = "",
    val reason: String = "",
    val nextAction: String = "",
    val affectedFindingIds: Set<String> = emptySet(),
    val affectedClaimIds: Set<String> = emptySet(),
)

enum class StudentProjectEvidenceTargetType {
    FINDING,
    CLAIM,
}

enum class StudentProjectEvidenceRelationType {
    SUPPORTS,
    CONTRADICTS,
    PROVIDES_CONTEXT,
}

/** A user-selected typed relationship, not an automated judgment of support. */
data class StudentProjectEvidenceRelation(
    val targetType: StudentProjectEvidenceTargetType,
    val targetId: String,
    val evidenceId: String,
    val relation: StudentProjectEvidenceRelationType,
    val rationale: String = "",
)

enum class StudentProjectRevisionActor {
    STUDENT,
    CONFIRMED_ASSISTANCE,
    IMPORT,
    SYSTEM,
}

/** Immutable, bounded checkpoint of student-authored project content. */
data class StudentProjectRevisionSnapshot(
    val revision: Int,
    val savedAtEpochMillis: Long,
    val actor: StudentProjectRevisionActor,
    val changeSummary: String,
    val title: String,
    val fieldValues: Map<String, String>,
    val sources: List<StudentProjectSourceRecord>,
    val themes: List<StudentProjectSynthesisTheme>,
    val claimEvidenceSourceIds: Set<String>,
    val evidenceItems: List<StudentProjectEvidenceItem>,
    val findings: List<StudentProjectFindingRecord>,
    val evidenceRelations: List<StudentProjectEvidenceRelation>,
    val attachments: List<StudentProjectAttachmentRef> = emptyList(),
    val claims: List<StudentProjectClaimRecord> = emptyList(),
    val limitationActions: List<StudentProjectLimitationActionRecord> = emptyList(),
    val deadlineDate: String? = null,
)

/** Date-only conversion helpers shared by local persistence and the Compose date picker. */
object StudentProjectDeadlineDate {
    private const val MIN_EPOCH_DAY = -719_162 // 0001-01-01
    private const val MAX_EPOCH_DAY = 2_932_896 // 9999-12-31
    private const val EPOCH_DAY_OFFSET = 719_468
    private val isoDatePattern = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")

    fun parse(value: String): Int? {
        val match = isoDatePattern.matchEntire(value) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        if (year !in 1..9_999 || month !in 1..12) return null
        val daysInMonth = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        if (day !in 1..daysInMonth) return null

        var adjustedYear = year
        if (month <= 2) adjustedYear -= 1
        val era = adjustedYear / 400
        val yearOfEra = adjustedYear - era * 400
        val adjustedMonth = month + if (month > 2) -3 else 9
        val dayOfYear = (153 * adjustedMonth + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return (era * 146_097 + dayOfEra - EPOCH_DAY_OFFSET).takeIf { it in MIN_EPOCH_DAY..MAX_EPOCH_DAY }
    }

    fun format(epochDay: Int): String? {
        if (epochDay !in MIN_EPOCH_DAY..MAX_EPOCH_DAY) return null
        val shiftedDay = epochDay + EPOCH_DAY_OFFSET
        val era = shiftedDay / 146_097
        val dayOfEra = shiftedDay - era * 146_097
        val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        var year = yearOfEra + era * 400
        val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthPart = (5 * dayOfYear + 2) / 153
        val day = dayOfYear - (153 * monthPart + 2) / 5 + 1
        val month = monthPart + if (monthPart < 10) 3 else -9
        if (month <= 2) year += 1
        return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    }

    private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
}

sealed interface StudentProjectDeadlineChange {
    data object Keep : StudentProjectDeadlineChange
    data class Set(val deadlineDate: String?) : StudentProjectDeadlineChange
}

data class StudentProjectFieldDefinition(
    val id: String,
    val label: String,
    val prompt: String,
    val requiredForStructureCheck: Boolean = false,
)

object ManualLiteratureSynthesisFields {
    const val ASSIGNMENT_BRIEF = "assignment_brief"
    const val RESEARCH_QUESTION = "research_question"
    const val AIM = "aim"
    const val SCOPE = "scope"
    const val SELECTION_METHOD = "selection_method"
    const val SEARCH_SCOPE = "search_scope"
    const val SYNTHESIS = "synthesis"
    const val CLAIM = "claim"
    const val CLAIM_SCOPE = "claim_scope"
    const val LIMITATIONS = "limitations"
    const val NEXT_ACTION = "next_action"

    val all = listOf(
        StudentProjectFieldDefinition(ASSIGNMENT_BRIEF, "Assignment brief", "What does the assignment ask you to do?", true),
        StudentProjectFieldDefinition(RESEARCH_QUESTION, "Research question", "What question will guide this synthesis?", true),
        StudentProjectFieldDefinition(AIM, "Aim", "What do you want this project to clarify?"),
        StudentProjectFieldDefinition(SCOPE, "Scope and boundaries", "What is included, and what is outside this project?"),
        StudentProjectFieldDefinition(SELECTION_METHOD, "Source selection approach", "How did you decide which sources to include or exclude?", true),
        StudentProjectFieldDefinition(SEARCH_SCOPE, "Search and access notes", "Where and when did you look? Record only what you actually did."),
        StudentProjectFieldDefinition(SYNTHESIS, "Student-written synthesis", "What patterns or differences do your recorded sources report?", true),
        StudentProjectFieldDefinition(CLAIM, "Your claim", "What bounded statement do you want the sources to support?", true),
        StudentProjectFieldDefinition(CLAIM_SCOPE, "Claim boundary", "For which context, population, or conditions is this claim intended?", true),
        StudentProjectFieldDefinition(LIMITATIONS, "Limitations and uncertainty", "What is missing, inconsistent, or not established by this project?", true),
        StudentProjectFieldDefinition(NEXT_ACTION, "Next step", "What should you check, clarify, or do next?"),
    )

    val byId = all.associateBy(StudentProjectFieldDefinition::id)
}

data class StudentProjectStructureReport(
    val missingRequiredFieldIds: List<String>,
    val incompleteSourceIds: List<String>,
    val unlinkedThemeIds: List<String>,
    val invalidClaimEvidenceIds: List<String>,
    val incompleteEvidenceIds: List<String>,
    val unlinkedEvidenceIds: List<String>,
    val unlinkedFindingIds: List<String>,
    val unlinkedClaimIds: List<String>,
) {
    val claimHasNoEvidenceLinks: Boolean get() = unlinkedClaimIds.isNotEmpty()
    val hasOpenStructureIssues: Boolean
        get() = missingRequiredFieldIds.isNotEmpty() || incompleteSourceIds.isNotEmpty() ||
            unlinkedThemeIds.isNotEmpty() || invalidClaimEvidenceIds.isNotEmpty() ||
            incompleteEvidenceIds.isNotEmpty() || unlinkedEvidenceIds.isNotEmpty() ||
            unlinkedFindingIds.isNotEmpty() || unlinkedClaimIds.isNotEmpty()
}

data class RequiredProjectFieldProgress(
    val filledRequired: Int,
    val totalRequired: Int,
) {
    val missingRequired: Int get() = totalRequired - filledRequired
    val isComplete: Boolean get() = missingRequired == 0
}

sealed interface StudentProjectDraftCreateResult {
    data class Created(val draft: StudentProjectDraft) : StudentProjectDraftCreateResult
    data class Unavailable(val code: String) : StudentProjectDraftCreateResult
    data class Invalid(val issues: List<String>) : StudentProjectDraftCreateResult
}

/**
 * Local project rules. Required-field progress describes presence only; it
 * does not assess the quality, truth, or academic merit of any response.
 */
object StudentProjectDraftRules {
    const val FREE_ACTIVE_PROJECT_LIMIT = 5
    const val PRO_ACTIVE_PROJECT_LIMIT = 50
    const val MAX_STORED_PROJECTS = 500
    const val MAX_ENCODED_DRAFT_BYTES = 2 * 1024 * 1024
    const val MAX_ID_CHARS = 96
    const val MAX_TITLE_CHARS = 160
    const val MAX_FIELD_CHARS = 8_000
    const val MAX_FIELD_COUNT = 32
    const val MAX_SOURCE_RECORDS = 100
    const val MAX_SYNTHESIS_THEMES = 50
    const val MAX_EVIDENCE_ITEMS = 200
    const val MAX_FINDINGS = 100
    const val MAX_EVIDENCE_RELATIONS = 500
    const val MAX_LIMITATION_ACTIONS = 100
    const val MAX_REVISION_SNAPSHOTS = 100
    const val MAX_CLAIMS = 100
    const val PRIMARY_CLAIM_TARGET_ID = "claim-primary"
    const val TRASH_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000

    private val idPattern = Regex("[A-Za-z0-9_-]{1,$MAX_ID_CHARS}")

    fun create(
        id: String,
        template: ProjectTemplateDefinition,
        title: String,
        createdAtEpochMillis: Long,
        fieldValues: Map<String, String> = emptyMap(),
        revisionActor: StudentProjectRevisionActor = StudentProjectRevisionActor.STUDENT,
    ): StudentProjectDraftCreateResult {
        val selection = ProjectTemplateCatalog.select(
            ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(template)),
            template.id,
            template.version,
        )
        if (selection !is TemplateSelectionResult.Selected) {
            return StudentProjectDraftCreateResult.Unavailable(
                (selection as TemplateSelectionResult.Unavailable).code,
            )
        }

        val draft = StudentProjectDraft(
            id = id,
            templateSnapshot = selection.template,
            title = title,
            fieldValues = fieldValues.toMap(),
            revision = 1,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = createdAtEpochMillis,
        )
        val issues = validate(draft)
        return if (issues.isEmpty()) {
            StudentProjectDraftCreateResult.Created(initializeRevisionHistory(draft, revisionActor))
        } else {
            StudentProjectDraftCreateResult.Invalid(issues)
        }
    }

    fun createManual(
        id: String,
        title: String,
        createdAtEpochMillis: Long,
    ): StudentProjectDraftCreateResult {
        val draft = StudentProjectDraft(
            id = id,
            templateSnapshot = null,
            title = title,
            fieldValues = emptyMap(),
            revision = 1,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = createdAtEpochMillis,
        )
        val issues = validate(draft)
        return if (issues.isEmpty()) {
            StudentProjectDraftCreateResult.Created(initializeRevisionHistory(draft))
        } else {
            StudentProjectDraftCreateResult.Invalid(issues)
        }
    }

    fun validate(draft: StudentProjectDraft): List<String> = buildList {
        if (!idPattern.matches(draft.id)) add("INVALID_PROJECT_ID")
        if (draft.title.isBlank() || draft.title.length > MAX_TITLE_CHARS || '\u0000' in draft.title) {
            add("INVALID_PROJECT_TITLE")
        }
        if (draft.revision < 1) add("INVALID_PROJECT_REVISION")
        if (draft.createdAtEpochMillis < 0 || draft.updatedAtEpochMillis < draft.createdAtEpochMillis) {
            add("INVALID_PROJECT_TIMESTAMP")
        }
        if (draft.deadlineDate != null && StudentProjectDeadlineDate.parse(draft.deadlineDate) == null) {
            add("PROJECT_DEADLINE_INVALID")
        }
        val knownFieldIds = if (draft.templateSnapshot == null) {
            ManualLiteratureSynthesisFields.byId.keys
        } else {
            val template = draft.templateSnapshot
            if (template.inputFields.size > MAX_FIELD_COUNT) add("TOO_MANY_TEMPLATE_FIELDS")
            if (ProjectTemplateCatalog.validateReadableProjectTemplateSnapshot(template).isNotEmpty()) {
                add("INVALID_PROJECT_TEMPLATE_SNAPSHOT")
            }
            template.inputFields.map(ProjectTemplateInputField::id).toSet()
        }
        if (draft.fieldValues.keys.any { it !in knownFieldIds }) add("UNKNOWN_PROJECT_FIELD")
        if (draft.fieldValues.values.any { value -> value.length > MAX_FIELD_CHARS || '\u0000' in value }) {
            add("PROJECT_FIELD_VALUE_INVALID")
        }
        if (draft.sources.size > MAX_SOURCE_RECORDS) add("TOO_MANY_PROJECT_SOURCES")
        if (draft.sources.map(StudentProjectSourceRecord::id).toSet().size != draft.sources.size) {
            add("DUPLICATE_PROJECT_SOURCE_ID")
        }
        if (draft.sources.any { source ->
                !idPattern.matches(source.id) || listOf(
                    source.title, source.authors, source.year, source.sourceType, source.doiOrUrl,
                    source.accessedOn, source.citationText, source.exclusionReason, source.reportedAim,
                    source.reportedContext, source.reportedMethod, source.reportedFindings,
                    source.reportedLimitations, source.studentNotes,
                ).any { it.length > MAX_FIELD_CHARS || '\u0000' in it }
            }
        ) add("PROJECT_SOURCE_INVALID")
        if (draft.themes.size > MAX_SYNTHESIS_THEMES) add("TOO_MANY_SYNTHESIS_THEMES")
        if (draft.themes.map(StudentProjectSynthesisTheme::id).toSet().size != draft.themes.size) {
            add("DUPLICATE_SYNTHESIS_THEME_ID")
        }
        val sourceIds = draft.sources.map(StudentProjectSourceRecord::id).toSet()
        if (draft.themes.any { theme ->
                !idPattern.matches(theme.id) ||
                    listOf(theme.title, theme.synthesis, theme.conflictOrVariation)
                        .any { it.length > MAX_FIELD_CHARS || '\u0000' in it } ||
                    theme.sourceIds.any { it !in sourceIds }
            }
        ) add("PROJECT_SYNTHESIS_THEME_INVALID")
        if (draft.claimEvidenceSourceIds.any { it !in sourceIds }) add("INVALID_CLAIM_EVIDENCE_LINK")
        if (draft.evidenceItems.size > MAX_EVIDENCE_ITEMS) add("TOO_MANY_PROJECT_EVIDENCE_ITEMS")
        if (draft.evidenceItems.map(StudentProjectEvidenceItem::id).toSet().size != draft.evidenceItems.size) {
            add("DUPLICATE_PROJECT_EVIDENCE_ID")
        }
        val evidenceIds = draft.evidenceItems.map(StudentProjectEvidenceItem::id).toSet()
        if (draft.evidenceItems.any { evidence ->
                !idPattern.matches(evidence.id) || evidence.sourceId !in sourceIds ||
                    listOf(evidence.excerpt, evidence.locator).any { it.length > MAX_FIELD_CHARS || '\u0000' in it }
            }
        ) add("PROJECT_EVIDENCE_ITEM_INVALID")
        if (draft.findings.size > MAX_FINDINGS) add("TOO_MANY_PROJECT_FINDINGS")
        if (draft.findings.map(StudentProjectFindingRecord::id).toSet().size != draft.findings.size) {
            add("DUPLICATE_PROJECT_FINDING_ID")
        }
        val findingIds = draft.findings.map(StudentProjectFindingRecord::id).toSet()
        if (draft.findings.any { finding ->
                !idPattern.matches(finding.id) ||
                    listOf(finding.statement, finding.scopeNote, finding.uncertaintyNote)
                        .any { it.length > MAX_FIELD_CHARS || '\u0000' in it }
            }
        ) add("PROJECT_FINDING_INVALID")
        if (draft.claims.size > MAX_CLAIMS) add("TOO_MANY_PROJECT_CLAIMS")
        if (draft.claims.map(StudentProjectClaimRecord::id).toSet().size != draft.claims.size) {
            add("DUPLICATE_PROJECT_CLAIM_ID")
        }
        if (draft.claims.any { claim ->
                !idPattern.matches(claim.id) ||
                    listOf(claim.statement, claim.scopeNote, claim.limitationsNote)
                        .any { it.length > MAX_FIELD_CHARS || '\u0000' in it }
            }
        ) add("PROJECT_CLAIM_INVALID")
        val claimIds = draft.claims.map(StudentProjectClaimRecord::id).toSet()
        if (draft.limitationActions.size > MAX_LIMITATION_ACTIONS) add("TOO_MANY_PROJECT_LIMITATION_ACTIONS")
        if (draft.limitationActions.map(StudentProjectLimitationActionRecord::id).toSet().size != draft.limitationActions.size) {
            add("DUPLICATE_PROJECT_LIMITATION_ACTION_ID")
        }
        val effectiveClaimIds = effectiveClaims(draft).mapTo(mutableSetOf(), StudentProjectClaimRecord::id)
        if (draft.limitationActions.any { item ->
                !idPattern.matches(item.id) ||
                    listOf(item.boundary, item.reason, item.nextAction)
                        .any { it.length > MAX_FIELD_CHARS || '\u0000' in it } ||
                    item.affectedFindingIds.any { it !in findingIds } ||
                    item.affectedClaimIds.any { it !in effectiveClaimIds }
            }
        ) add("PROJECT_LIMITATION_ACTION_INVALID")
        if (draft.evidenceRelations.size > MAX_EVIDENCE_RELATIONS) add("TOO_MANY_PROJECT_EVIDENCE_RELATIONS")
        val relationKeys = draft.evidenceRelations.map { relation ->
            "${relation.targetType}:${relation.targetId}:${relation.evidenceId}"
        }
        if (relationKeys.toSet().size != relationKeys.size) add("DUPLICATE_PROJECT_EVIDENCE_RELATION")
        if (draft.evidenceRelations.any { relation ->
                relation.evidenceId !in evidenceIds ||
                    !idPattern.matches(relation.targetId) ||
                    (relation.targetType == StudentProjectEvidenceTargetType.FINDING && relation.targetId !in findingIds) ||
                    (relation.targetType == StudentProjectEvidenceTargetType.CLAIM &&
                        (if (claimIds.isEmpty()) relation.targetId != PRIMARY_CLAIM_TARGET_ID else relation.targetId !in claimIds)) ||
                    relation.rationale.length > MAX_FIELD_CHARS || '\u0000' in relation.rationale
            }
        ) add("PROJECT_EVIDENCE_RELATION_INVALID")
        if (draft.attachments.size > StudentProjectAttachmentRules.MAX_ATTACHMENTS) {
            add("TOO_MANY_PROJECT_ATTACHMENTS")
        }
        if (draft.attachments.map { it.id.lowercase() }.toSet().size != draft.attachments.size) {
            add("DUPLICATE_PROJECT_ATTACHMENT_ID")
        }
        if (draft.attachments.any { StudentProjectAttachmentRules.validate(it).isNotEmpty() }) {
            add("PROJECT_ATTACHMENT_REFERENCE_INVALID")
        }
        if (draft.revisionSnapshots.size > MAX_REVISION_SNAPSHOTS) add("TOO_MANY_PROJECT_REVISION_SNAPSHOTS")
        val snapshotRevisions = draft.revisionSnapshots.map(StudentProjectRevisionSnapshot::revision)
        if (snapshotRevisions.toSet().size != snapshotRevisions.size || snapshotRevisions != snapshotRevisions.sorted()) {
            add("PROJECT_REVISION_HISTORY_INVALID")
        }
        if (draft.revisionSnapshots.any { snapshot ->
                snapshot.revision < 1 || snapshot.revision > draft.revision ||
                    snapshot.savedAtEpochMillis < draft.createdAtEpochMillis ||
                    snapshot.savedAtEpochMillis > draft.updatedAtEpochMillis ||
                    snapshot.changeSummary.isBlank() || snapshot.changeSummary.length > 240 ||
                    '\u0000' in snapshot.changeSummary
            }
        ) add("PROJECT_REVISION_SNAPSHOT_INVALID")
        draft.revisionSnapshots.forEach { snapshot ->
            val snapshotDraft = draft.copy(
                title = snapshot.title,
                fieldValues = snapshot.fieldValues,
                revision = snapshot.revision,
                updatedAtEpochMillis = snapshot.savedAtEpochMillis,
                sources = snapshot.sources,
                themes = snapshot.themes,
                claimEvidenceSourceIds = snapshot.claimEvidenceSourceIds,
                evidenceItems = snapshot.evidenceItems,
                findings = snapshot.findings,
                evidenceRelations = snapshot.evidenceRelations,
                attachments = snapshot.attachments,
                claims = snapshot.claims,
                limitationActions = snapshot.limitationActions,
                deadlineDate = snapshot.deadlineDate,
                revisionSnapshots = emptyList(),
                status = StudentProjectStatus.DRAFT,
                trashedAtEpochMillis = null,
            )
            if (validate(snapshotDraft).isNotEmpty()) add("PROJECT_REVISION_SNAPSHOT_CONTENT_INVALID")
        }
        if (draft.status == StudentProjectStatus.TRASHED) {
            val trashedAt = draft.trashedAtEpochMillis
            if (trashedAt == null) {
                add("PROJECT_TRASH_TIMESTAMP_REQUIRED")
            } else if (trashedAt < draft.createdAtEpochMillis || trashedAt > draft.updatedAtEpochMillis) {
                add("PROJECT_TRASH_TIMESTAMP_INVALID")
            }
        }
        if (draft.status != StudentProjectStatus.TRASHED && draft.trashedAtEpochMillis != null) {
            add("PROJECT_TRASH_TIMESTAMP_UNEXPECTED")
        }
        if (estimatedEncodedDraftUpperBoundBytes(draft) > MAX_ENCODED_DRAFT_BYTES) {
            add("PROJECT_DRAFT_TOO_LARGE")
        }
    }

    fun countsTowardActiveLimit(status: StudentProjectStatus): Boolean =
        status == StudentProjectStatus.DRAFT || status == StudentProjectStatus.ACTIVE

    fun activeProjectLimit(hasVerifiedProEntitlement: Boolean): Int =
        if (hasVerifiedProEntitlement) PRO_ACTIVE_PROJECT_LIMIT else FREE_ACTIVE_PROJECT_LIMIT

    fun captureRevisionSnapshot(
        draft: StudentProjectDraft,
        actor: StudentProjectRevisionActor,
        changeSummary: String,
    ): StudentProjectRevisionSnapshot = StudentProjectRevisionSnapshot(
        revision = draft.revision,
        savedAtEpochMillis = draft.updatedAtEpochMillis,
        actor = actor,
        changeSummary = changeSummary,
        title = draft.title,
        fieldValues = draft.fieldValues.toMap(),
        sources = draft.sources.toList(),
        themes = draft.themes.toList(),
        claimEvidenceSourceIds = draft.claimEvidenceSourceIds.toSet(),
        evidenceItems = draft.evidenceItems.toList(),
        findings = draft.findings.toList(),
        evidenceRelations = draft.evidenceRelations.toList(),
        attachments = draft.attachments.toList(),
        claims = draft.claims.toList(),
        limitationActions = draft.limitationActions.toList(),
        deadlineDate = draft.deadlineDate,
    )

    fun initializeRevisionHistory(
        draft: StudentProjectDraft,
        actor: StudentProjectRevisionActor = StudentProjectRevisionActor.STUDENT,
        changeSummary: String = "Project created",
    ): StudentProjectDraft = draft.copy(
        revisionSnapshots = listOf(captureRevisionSnapshot(draft, actor, changeSummary)),
    )

    fun needsRevisionCheckpoint(draft: StudentProjectDraft): Boolean {
        val latest = draft.revisionSnapshots.lastOrNull() ?: return true
        return latest.title != draft.title || latest.fieldValues != draft.fieldValues ||
            latest.sources != draft.sources || latest.themes != draft.themes ||
            latest.claimEvidenceSourceIds != draft.claimEvidenceSourceIds ||
            latest.evidenceItems != draft.evidenceItems || latest.findings != draft.findings ||
            latest.evidenceRelations != draft.evidenceRelations || latest.attachments != draft.attachments ||
            latest.claims != draft.claims || latest.limitationActions != draft.limitationActions ||
            latest.deadlineDate != draft.deadlineDate
    }

    fun retainRevisionSnapshots(snapshots: List<StudentProjectRevisionSnapshot>): List<StudentProjectRevisionSnapshot> {
        // Never silently discard student history. Validation rejects a new
        // checkpoint beyond the explicit cap while preserving every saved one.
        return snapshots
    }

    fun structureReport(draft: StudentProjectDraft): StudentProjectStructureReport {
        val requiredFieldIds = if (draft.templateSnapshot == null) {
            ManualLiteratureSynthesisFields.all
                .filter(StudentProjectFieldDefinition::requiredForStructureCheck)
                .map(StudentProjectFieldDefinition::id)
        } else {
            draft.templateSnapshot.inputFields
                .filter(ProjectTemplateInputField::required)
                .map(ProjectTemplateInputField::id)
        }
        val sourceIds = draft.sources.map(StudentProjectSourceRecord::id).toSet()
        val findingIds = draft.findings.map(StudentProjectFindingRecord::id).toSet()
        val evidenceIds = draft.evidenceItems.map(StudentProjectEvidenceItem::id).toSet()
        val claims = effectiveClaims(draft)
        val linkedEvidenceIds = draft.evidenceRelations.map(StudentProjectEvidenceRelation::evidenceId).toSet()
        val linkedFindingIds = draft.evidenceRelations
            .filter { it.targetType == StudentProjectEvidenceTargetType.FINDING }
            .map(StudentProjectEvidenceRelation::targetId)
            .toSet()
        return StudentProjectStructureReport(
            missingRequiredFieldIds = requiredFieldIds.filter { fieldId ->
                if (fieldId == ManualLiteratureSynthesisFields.CLAIM) claims.none { it.statement.isNotBlank() }
                else draft.fieldValues[fieldId].isNullOrBlank()
            },
            incompleteSourceIds = draft.sources.filter { it.title.isBlank() || it.citationText.isBlank() }
                .map(StudentProjectSourceRecord::id),
            unlinkedThemeIds = draft.themes.filter { it.sourceIds.isEmpty() }
                .map(StudentProjectSynthesisTheme::id),
            invalidClaimEvidenceIds = draft.claimEvidenceSourceIds.filter { it !in sourceIds },
            incompleteEvidenceIds = draft.evidenceItems.filter { it.excerpt.isBlank() }
                .map(StudentProjectEvidenceItem::id),
            unlinkedEvidenceIds = evidenceIds.filter { it !in linkedEvidenceIds },
            unlinkedFindingIds = findingIds.filter { it !in linkedFindingIds },
            unlinkedClaimIds = claims.filter { claim ->
                claim.statement.isNotBlank() && draft.evidenceRelations.none {
                    it.targetType == StudentProjectEvidenceTargetType.CLAIM && it.targetId == claim.id
                }
            }.map(StudentProjectClaimRecord::id),
        )
    }

    /** Projects a legacy scalar claim only when the first-class list is absent. */
    fun effectiveClaims(draft: StudentProjectDraft): List<StudentProjectClaimRecord> {
        if (draft.claims.isNotEmpty()) return draft.claims
        val legacyStatement = draft.fieldValues[ManualLiteratureSynthesisFields.CLAIM].orEmpty()
        return if (legacyStatement.isBlank()) emptyList() else listOf(
            StudentProjectClaimRecord(PRIMARY_CLAIM_TARGET_ID, statement = legacyStatement),
        )
    }

    fun primaryClaim(claims: List<StudentProjectClaimRecord>): StudentProjectClaimRecord? =
        claims.firstOrNull { it.id == PRIMARY_CLAIM_TARGET_ID } ?: claims.firstOrNull()

    /** Removes a source and its explicit links without rewriting student-authored analysis. */
    fun removeSource(draft: StudentProjectDraft, sourceId: String): StudentProjectDraft {
        val evidenceIds = draft.evidenceItems.asSequence()
            .filter { it.sourceId == sourceId }
            .map(StudentProjectEvidenceItem::id)
            .toSet()
        if (draft.sources.none { it.id == sourceId } && evidenceIds.isEmpty() &&
            draft.themes.none { sourceId in it.sourceIds } && sourceId !in draft.claimEvidenceSourceIds
        ) return draft

        val affectedClaimIds = draft.evidenceRelations.asSequence()
            .filter {
                it.targetType == StudentProjectEvidenceTargetType.CLAIM &&
                    it.evidenceId in evidenceIds
            }
            .map(StudentProjectEvidenceRelation::targetId)
            .toMutableSet()
        if (sourceId in draft.claimEvidenceSourceIds) {
            primaryClaim(effectiveClaims(draft))?.id?.let(affectedClaimIds::add)
        }

        return draft.copy(
            sources = draft.sources.filterNot { it.id == sourceId },
            themes = draft.themes.map { it.copy(sourceIds = it.sourceIds - sourceId) },
            claimEvidenceSourceIds = draft.claimEvidenceSourceIds - sourceId,
            evidenceItems = draft.evidenceItems.filterNot { it.id in evidenceIds },
            evidenceRelations = draft.evidenceRelations.filterNot { it.evidenceId in evidenceIds },
            claims = claimsNeedingReview(draft, affectedClaimIds),
        )
    }

    /** Removes one evidence note and its links; dependent claims remain for student review. */
    fun removeEvidenceNote(draft: StudentProjectDraft, evidenceId: String): StudentProjectDraft {
        if (draft.evidenceItems.none { it.id == evidenceId }) return draft
        val affectedClaimIds = draft.evidenceRelations.asSequence()
            .filter {
                it.targetType == StudentProjectEvidenceTargetType.CLAIM &&
                    it.evidenceId == evidenceId
            }
            .map(StudentProjectEvidenceRelation::targetId)
            .toSet()
        return draft.copy(
            evidenceItems = draft.evidenceItems.filterNot { it.id == evidenceId },
            evidenceRelations = draft.evidenceRelations.filterNot { it.evidenceId == evidenceId },
            claims = claimsNeedingReview(draft, affectedClaimIds),
        )
    }

    private fun claimsNeedingReview(
        draft: StudentProjectDraft,
        affectedClaimIds: Set<String>,
    ): List<StudentProjectClaimRecord> {
        if (affectedClaimIds.isEmpty()) return draft.claims
        val claims = effectiveClaims(draft)
        return claims.map { claim ->
            if (claim.id in affectedClaimIds) {
                claim.copy(reviewStatus = StudentProjectClaimReviewStatus.NEEDS_REVISION)
            } else {
                claim
            }
        }
    }

    /** Conservative JSON-size bound shared by validation and both storage codecs. */
    fun estimatedEncodedDraftUpperBoundBytes(draft: StudentProjectDraft): Long {
        var bytes = 8_192L
        fun record(extra: Long = 1_024L) { bytes += extra }
        fun text(value: String) { bytes += value.length.toLong() * 6L + 16L }
        fun texts(values: Iterable<String>) = values.forEach(::text)
        fun fields(values: Map<String, String>) {
            record(values.size.toLong() * 192L)
            values.forEach { (key, value) -> text(key); text(value) }
        }
        fun source(value: StudentProjectSourceRecord) {
            record()
            texts(listOf(
                value.id, value.title, value.authors, value.year, value.sourceType, value.doiOrUrl,
                value.accessedOn, value.citationText, value.selectionStatus.name, value.exclusionReason,
                value.reportedAim, value.reportedContext, value.reportedMethod, value.reportedFindings,
                value.reportedLimitations, value.studentNotes,
            ))
        }
        fun theme(value: StudentProjectSynthesisTheme) {
            record(640L)
            texts(listOf(value.id, value.title, value.synthesis, value.conflictOrVariation))
            texts(value.sourceIds)
        }
        fun evidence(value: StudentProjectEvidenceItem) {
            record(640L)
            texts(listOf(value.id, value.sourceId, value.excerpt, value.locator))
        }
        fun finding(value: StudentProjectFindingRecord) {
            record(640L)
            texts(listOf(value.id, value.statement, value.scopeNote, value.uncertaintyNote))
        }
        fun claim(value: StudentProjectClaimRecord) {
            record(640L)
            texts(listOf(value.id, value.statement, value.scopeNote, value.limitationsNote, value.reviewStatus.name))
        }
        fun relation(value: StudentProjectEvidenceRelation) {
            record(640L)
            texts(listOf(value.targetType.name, value.targetId, value.evidenceId, value.relation.name, value.rationale))
        }
        fun attachment(value: StudentProjectAttachmentRef) {
            record(640L)
            texts(listOf(value.id, value.fileName, value.mimeType, value.sha256))
        }
        fun limitationAction(value: StudentProjectLimitationActionRecord) {
            record(768L)
            texts(listOf(value.id, value.boundary, value.reason, value.nextAction))
            texts(value.affectedFindingIds)
            texts(value.affectedClaimIds)
        }
        fun collections(
            sources: List<StudentProjectSourceRecord>,
            themes: List<StudentProjectSynthesisTheme>,
            sourceLinks: Set<String>,
            evidenceItems: List<StudentProjectEvidenceItem>,
            findings: List<StudentProjectFindingRecord>,
            relations: List<StudentProjectEvidenceRelation>,
            attachments: List<StudentProjectAttachmentRef>,
            claims: List<StudentProjectClaimRecord>,
            limitationActions: List<StudentProjectLimitationActionRecord>,
        ) {
            sources.forEach(::source)
            themes.forEach(::theme)
            texts(sourceLinks)
            evidenceItems.forEach(::evidence)
            findings.forEach(::finding)
            relations.forEach(::relation)
            attachments.forEach(::attachment)
            claims.forEach(::claim)
            limitationActions.forEach(::limitationAction)
        }

        text(draft.id)
        text(draft.title)
        text(draft.deadlineDate.orEmpty())
        fields(draft.fieldValues)
        draft.templateSnapshot?.let { template ->
            record(4_096L)
            texts(listOf(template.id, template.family.id, template.title, template.summary, template.intendedOutput, template.publication.name))
            template.inputFields.forEach { field ->
                record(512L)
                texts(listOf(field.id, field.kind.name, field.label))
            }
            template.steps.forEach { step ->
                record(512L)
                texts(listOf(step.id, step.title) + step.inputFieldIds)
            }
            texts(template.methodSpecificLimitations)
            texts(template.provenanceRequirements)
            texts(template.accessibilityExpectations)
            template.examples.forEach { example ->
                record(512L)
                texts(listOf(example.id, example.summary, example.kind.name))
            }
        }
        collections(
            draft.sources,
            draft.themes,
            draft.claimEvidenceSourceIds,
            draft.evidenceItems,
            draft.findings,
            draft.evidenceRelations,
            draft.attachments,
            draft.claims,
            draft.limitationActions,
        )
        draft.revisionSnapshots.forEach { snapshot ->
            record(2_048L)
            text(snapshot.title)
            text(snapshot.deadlineDate.orEmpty())
            fields(snapshot.fieldValues)
            collections(
                snapshot.sources,
                snapshot.themes,
                snapshot.claimEvidenceSourceIds,
                snapshot.evidenceItems,
                snapshot.findings,
                snapshot.evidenceRelations,
                snapshot.attachments,
                snapshot.claims,
                snapshot.limitationActions,
            )
        }
        return bytes
    }

    fun requiredFieldProgress(
        template: ProjectTemplateDefinition,
        fieldValues: Map<String, String>,
    ): RequiredProjectFieldProgress {
        val requiredFields = template.inputFields.filter(ProjectTemplateInputField::required)
        val filled = requiredFields.count { field -> !fieldValues[field.id].isNullOrBlank() }
        return RequiredProjectFieldProgress(filledRequired = filled, totalRequired = requiredFields.size)
    }

    fun requiredFieldProgress(draft: StudentProjectDraft): RequiredProjectFieldProgress {
        val fields = draft.templateSnapshot?.inputFields
            ?.filter(ProjectTemplateInputField::required)
            ?.map(ProjectTemplateInputField::id)
            ?: ManualLiteratureSynthesisFields.all
                .filter(StudentProjectFieldDefinition::requiredForStructureCheck)
                .map(StudentProjectFieldDefinition::id)
        val claims = effectiveClaims(draft)
        val filled = fields.count { fieldId ->
            if (fieldId == ManualLiteratureSynthesisFields.CLAIM) claims.any { it.statement.isNotBlank() }
            else !draft.fieldValues[fieldId].isNullOrBlank()
        }
        return RequiredProjectFieldProgress(filledRequired = filled, totalRequired = fields.size)
    }
}
