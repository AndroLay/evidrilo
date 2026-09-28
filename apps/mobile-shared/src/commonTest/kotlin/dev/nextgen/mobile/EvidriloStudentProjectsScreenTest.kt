package dev.nextgen.mobile

import dev.nextgen.mobile.domain.project.RequiredProjectFieldProgress
import dev.nextgen.mobile.domain.project.ManualLiteratureSynthesisFields
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectTemplateInputField
import dev.nextgen.mobile.domain.project.ProjectTemplateInputKind
import dev.nextgen.mobile.domain.project.ProjectTemplatePublication
import dev.nextgen.mobile.domain.project.ProjectTemplateStep
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectClaimReviewStatus
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelationType
import dev.nextgen.mobile.domain.project.StudentProjectEvidenceTargetType
import dev.nextgen.mobile.domain.project.StudentProjectFindingRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.projectcatalog.StudentProjectDraftFlowResult
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloStudentProjectsScreenTest {
    @Test
    fun home_project_load_state_distinguishes_loading_and_recoverable_failures() {
        assertEquals(null, StudentProjectListUiState.Loading.toHomeErrorMessage())
        assertEquals(null, StudentProjectListUiState.Loaded(emptyList()).toHomeErrorMessage())
        assertEquals(
            "Local project storage is unavailable.",
            StudentProjectListUiState.StorageUnavailable.toHomeErrorMessage(),
        )
        assertEquals(
            "Saved project data could not be read safely.",
            StudentProjectListUiState.StorageCorrupt.toHomeErrorMessage(),
        )
        assertEquals(
            "Evidrilo could not read your saved projects.",
            StudentProjectListUiState.StorageFailed.toHomeErrorMessage(),
        )
    }

    @Test
    fun manualEditorUsesOneClearlyNamedSectionAtATimeAndFinishesWithReview() {
        val sections = studentProjectEditorSections(removalDraft())

        assertEquals(
            listOf(
                "Project basics",
                "Sources and files",
                "Evidence notes",
                "Findings and comparison",
                "Claims and evidence links",
                "Limitations and next steps",
                "Review",
            ),
            sections.map(StudentProjectEditorSection::title),
        )
        assertEquals(
            ManualLiteratureSynthesisFields.all.take(6).map { it.id },
            sections.first().fieldIds,
        )
        assertEquals(
            listOf(ManualLiteratureSynthesisFields.CLAIM, ManualLiteratureSynthesisFields.CLAIM_SCOPE),
            sections.single { it.kind == StudentProjectEditorSectionKind.CLAIMS }.fieldIds,
        )
        assertEquals(
            listOf(ManualLiteratureSynthesisFields.LIMITATIONS, ManualLiteratureSynthesisFields.NEXT_ACTION),
            sections.single { it.kind == StudentProjectEditorSectionKind.LIMITATIONS_AND_NEXT_STEPS }.fieldIds,
        )
        assertEquals(StudentProjectEditorSectionKind.REVIEW, sections.last().kind)
    }

    @Test
    fun templateStepsRemainDistinctAndUnassignedFieldsAreStillReachable() {
        val template = ProjectTemplateDefinition(
            id = "reviewed-template",
            version = 1,
            family = ProjectTemplateFamily.LITERATURE_REVIEW,
            title = "Literature synthesis",
            summary = "A bounded template.",
            intendedOutput = "A synthesis.",
            inputFields = listOf(
                ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
                ProjectTemplateInputField("evidence", ProjectTemplateInputKind.DATA, "Evidence", true),
                ProjectTemplateInputField("extra", ProjectTemplateInputKind.ANALYSIS, "Additional note", false),
            ),
            steps = listOf(
                ProjectTemplateStep("question-step", "Frame the question", listOf("question")),
                ProjectTemplateStep("evidence-step", "Examine material", listOf("evidence")),
                ProjectTemplateStep("empty-step", "No available inputs", emptyList()),
            ),
            methodSpecificLimitations = emptyList(),
            provenanceRequirements = emptyList(),
            accessibilityExpectations = emptyList(),
            examples = emptyList(),
            publication = ProjectTemplatePublication.PUBLISHED,
        )

        val sections = studentProjectEditorSections(removalDraft(templateSnapshot = template))

        assertEquals(
            listOf("question-step", "evidence-step"),
            sections.filter { it.kind == StudentProjectEditorSectionKind.TEMPLATE_STEP }.map(StudentProjectEditorSection::id),
        )
        assertEquals(listOf("question"), sections[1].fieldIds)
        assertEquals(listOf("evidence"), sections[2].fieldIds)
        assertEquals(listOf("extra"), sections.single { it.kind == StudentProjectEditorSectionKind.TEMPLATE_ADDITIONAL_FIELDS }.fieldIds)
        assertEquals("Review", sections.last().title)
    }

    @Test
    fun finalReviewIncludesEveryManualFieldEvenWhenItIsStillEmpty() {
        val draft = removalDraft().copy(
            fieldValues = mapOf(
                ManualLiteratureSynthesisFields.RESEARCH_QUESTION to "How does the evidence vary?",
                ManualLiteratureSynthesisFields.CLAIM to "The reports differ by context.",
            ),
        )

        val reviewFields = studentProjectReviewFields(draft)

        assertEquals(ManualLiteratureSynthesisFields.all.map { it.id }, reviewFields.map(StudentProjectReviewField::id))
        assertEquals("How does the evidence vary?", reviewFields.single { it.id == ManualLiteratureSynthesisFields.RESEARCH_QUESTION }.value)
        assertEquals("", reviewFields.single { it.id == ManualLiteratureSynthesisFields.NEXT_ACTION }.value)
    }

    @Test
    fun finalReviewIncludesEveryTemplateFieldIncludingUnassignedFields() {
        val template = ProjectTemplateDefinition(
            id = "reviewed-template",
            version = 1,
            family = ProjectTemplateFamily.LITERATURE_REVIEW,
            title = "Literature synthesis",
            summary = "A bounded template.",
            intendedOutput = "A synthesis.",
            inputFields = listOf(
                ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
                ProjectTemplateInputField("extra", ProjectTemplateInputKind.ANALYSIS, "Additional note", false),
            ),
            steps = listOf(ProjectTemplateStep("question-step", "Frame the question", listOf("question"))),
            methodSpecificLimitations = emptyList(),
            provenanceRequirements = emptyList(),
            accessibilityExpectations = emptyList(),
            examples = emptyList(),
            publication = ProjectTemplatePublication.PUBLISHED,
        )
        val draft = removalDraft(templateSnapshot = template).copy(fieldValues = mapOf("extra" to "Recorded separately."))

        val reviewFields = studentProjectReviewFields(draft)

        assertEquals(listOf("question", "extra"), reviewFields.map(StudentProjectReviewField::id))
        assertEquals("", reviewFields.first().value)
        assertEquals("Recorded separately.", reviewFields.last().value)
    }

    @Test
    fun editorProgressIsClampedAndHasAnAccessibleSectionDescription() {
        assertEquals(0.25f, studentProjectEditorProgress(0, 4))
        assertEquals(1f, studentProjectEditorProgress(9, 4))
        assertEquals(0f, studentProjectEditorProgress(0, 0))
        assertEquals("Section 2 of 4: Sources and files", studentProjectEditorProgressDescription(1, 4, "Sources and files"))
    }

    @Test
    fun removingFindingDetachesOnlyThatFindingFromStudentRecordedLimitationActions() {
        val action = StudentProjectLimitationActionRecord(
            id = "limit-a",
            boundary = "One setting was observed.",
            reason = "The other setting was unavailable.",
            nextAction = "Repeat the missing comparison.",
            affectedFindingIds = setOf("finding-a", "finding-b"),
            affectedClaimIds = setOf("claim-a"),
        )

        val result = detachFindingFromLimitationActions(listOf(action), "finding-a")

        assertEquals(setOf("finding-b"), result.single().affectedFindingIds)
        assertEquals(setOf("claim-a"), result.single().affectedClaimIds)
        assertEquals("One setting was observed.", result.single().boundary)
        assertEquals("The other setting was unavailable.", result.single().reason)
        assertEquals("Repeat the missing comparison.", result.single().nextAction)
    }

    @Test
    fun removingClaimDetachesOnlyThatClaimFromStudentRecordedLimitationActions() {
        val action = StudentProjectLimitationActionRecord(
            id = "limit-a",
            boundary = "A claim boundary.",
            affectedFindingIds = setOf("finding-a"),
            affectedClaimIds = setOf("claim-a", "claim-b"),
        )

        val result = detachClaimFromLimitationActions(listOf(action), "claim-a")

        assertEquals(setOf("finding-a"), result.single().affectedFindingIds)
        assertEquals(setOf("claim-b"), result.single().affectedClaimIds)
        assertEquals("A claim boundary.", result.single().boundary)
    }

    @Test
    fun sourceRemovalPreviewNamesEveryLinkedItemAndUntrackedAnalysisBoundary() {
        val source = StudentProjectSourceRecord(id = "source-a")
        val evidence = listOf(
            StudentProjectEvidenceItem(id = "evidence-a", sourceId = "source-a", excerpt = "First observation"),
            StudentProjectEvidenceItem(id = "evidence-b", sourceId = "source-a", excerpt = "Second observation"),
            StudentProjectEvidenceItem(id = "evidence-c", sourceId = "source-b", excerpt = "Unrelated observation"),
        )
        val finding = StudentProjectFindingRecord(id = "finding-a", statement = "Finding dependent on Study A")
        val claim = StudentProjectClaimRecord(
            id = "claim-a",
            statement = "Claim dependent on Study A",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val relations = listOf(
            StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.FINDING,
                finding.id,
                "evidence-a",
                StudentProjectEvidenceRelationType.SUPPORTS,
            ),
            StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.FINDING,
                finding.id,
                "evidence-b",
                StudentProjectEvidenceRelationType.PROVIDES_CONTEXT,
            ),
            StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.CLAIM,
                claim.id,
                "evidence-a",
                StudentProjectEvidenceRelationType.CONTRADICTS,
            ),
            StudentProjectEvidenceRelation(
                StudentProjectEvidenceTargetType.FINDING,
                "unrelated-finding",
                "evidence-c",
                StudentProjectEvidenceRelationType.SUPPORTS,
            ),
        )
        val draft = removalDraft(
            sources = listOf(source, StudentProjectSourceRecord(id = "source-b")),
            evidenceItems = evidence,
            evidenceRelations = relations,
            findings = listOf(finding),
            claims = listOf(claim),
            themes = listOf(
                StudentProjectSynthesisTheme("theme-a", "Study A theme", "Synthesis A", setOf("source-a")),
                StudentProjectSynthesisTheme("theme-b", "Study B theme", "Synthesis B", setOf("source-b")),
            ),
            claimEvidenceSourceIds = setOf("source-a"),
        )
        val impact = projectSourceRemovalImpact(
            source = source,
            draft = draft,
        )

        assertEquals(listOf("evidence-a", "evidence-b"), impact.evidenceNotes.map(StudentProjectEvidenceItem::id))
        assertEquals(listOf("finding-a", "finding-a", "claim-a"), impact.relationships.map { it.relation.targetId })
        assertEquals(listOf("theme-a"), impact.themes.map(StudentProjectSynthesisTheme::id))
        assertEquals(true, impact.removesClaimSourceLink)
        assertEquals("claim-a", impact.claimSourceLinkedClaim?.id)
        val warning = projectSourceRemovalWarning(source, impact)
        assertEquals(true, warning.contains("First observation (#evidence-a)"))
        assertEquals(true, warning.contains("Finding dependent on Study A (#finding-a)"))
        assertEquals(true, warning.contains("Claim dependent on Study A (#claim-a)"))
        assertEquals(true, warning.contains("Study A theme (#theme-a)"))
        assertEquals(true, warning.contains("Claim-to-source link to #source-a is removed from Claim dependent on Study A (#claim-a)"))
        assertEquals(true, warning.contains("free-text analysis and output are not linked automatically"))
        assertEquals(true, warning.contains("save a new revision"))
    }

    @Test
    fun evidenceRemovalPreviewNamesRelatedFindingAndClaimAndRequiresRecheck() {
        val evidence = StudentProjectEvidenceItem(id = "evidence-a", sourceId = "source-a")
        val finding = StudentProjectFindingRecord(id = "finding-a", statement = "Finding remains")
        val claim = StudentProjectClaimRecord(id = "claim-a", statement = "Claim needs review")
        val draft = removalDraft(
            sources = listOf(StudentProjectSourceRecord(id = "source-a")),
            evidenceItems = listOf(evidence),
            findings = listOf(finding),
            claims = listOf(claim),
            evidenceRelations = listOf(
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.FINDING,
                    finding.id,
                    evidence.id,
                    StudentProjectEvidenceRelationType.SUPPORTS,
                ),
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.CLAIM,
                    claim.id,
                    evidence.id,
                    StudentProjectEvidenceRelationType.PROVIDES_CONTEXT,
                ),
            ),
        )
        val impact = projectEvidenceRemovalImpact(
            evidence = evidence,
            draft = draft,
        )

        val warning = projectEvidenceRemovalWarning(impact)
        assertEquals(true, warning.contains("Finding remains (#finding-a)"))
        assertEquals(true, warning.contains("Claim needs review (#claim-a)"))
        assertEquals(true, warning.contains("Needs revision"))
        assertEquals(true, warning.contains("save a new revision"))
    }

    @Test
    fun requiredFieldProgressIsExplicitlyPresenceOnlyAndNotAnAcademicGrade() {
        assertEquals(
            "Required information: 2 of 5 fields filled. This is not a grade.",
            requiredProjectProgressLabel(RequiredProjectFieldProgress(filledRequired = 2, totalRequired = 5)),
        )
    }

    @Test
    fun localProjectLoadErrorsDoNotLookLikeAnEmptyProjectList() {
        assertEquals(
            StudentProjectListUiState.StorageUnavailable,
            StudentProjectDraftFlowResult.StorageUnavailable.toStudentProjectListUiState(),
        )
        assertEquals(
            StudentProjectListUiState.StorageCorrupt,
            StudentProjectDraftFlowResult.StorageCorrupt.toStudentProjectListUiState(),
        )
    }

    @Test
    fun attachmentRemovalSaveFailureSaysTheFileRemainsAttached() {
        assertEquals(
            "Project storage is full. The file is still attached because its removal was not saved.",
            studentProjectAttachmentRemovalFailureMessage("PROJECT_DRAFT_STORAGE_LIMIT_REACHED"),
        )
    }

    @Test
    fun archiveReadProgressUsesHonestByteCountsAndSaysNoProjectDataChanged() {
        assertEquals(
            "Reading archive: 1 MB of 4 MB. Your project data has not changed.",
            studentProjectImportProgressLabel(StudentProjectFileImportProgress(1_048_576, 4_194_304)),
        )
        assertEquals(
            "Reading archive: 2 MB. Your project data has not changed.",
            studentProjectImportProgressLabel(StudentProjectFileImportProgress(2_097_152, null)),
        )
    }
}

private fun removalDraft(
    templateSnapshot: ProjectTemplateDefinition? = null,
    sources: List<StudentProjectSourceRecord> = emptyList(),
    evidenceItems: List<StudentProjectEvidenceItem> = emptyList(),
    evidenceRelations: List<StudentProjectEvidenceRelation> = emptyList(),
    findings: List<StudentProjectFindingRecord> = emptyList(),
    claims: List<StudentProjectClaimRecord> = emptyList(),
    themes: List<StudentProjectSynthesisTheme> = emptyList(),
    claimEvidenceSourceIds: Set<String> = emptySet(),
): StudentProjectDraft = StudentProjectDraft(
    id = "project-a",
    templateSnapshot = templateSnapshot,
    title = "Project",
    fieldValues = mapOf("synthesis" to "Student-authored output."),
    revision = 1,
    createdAtEpochMillis = 1,
    updatedAtEpochMillis = 1,
    sources = sources,
    evidenceItems = evidenceItems,
    evidenceRelations = evidenceRelations,
    findings = findings,
    claims = claims,
    themes = themes,
    claimEvidenceSourceIds = claimEvidenceSourceIds,
)
