package dev.nextgen.mobile.domain.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StudentProjectDraftTest {
    @Test
    fun `required field progress counts presence without grading content`() {
        val template = publishedTemplate()
        val progress = StudentProjectDraftRules.requiredFieldProgress(
            template = template,
            fieldValues = mapOf("question" to "  A bounded question?  ", "optional" to "extra"),
        )

        assertEquals(1, progress.filledRequired)
        assertEquals(2, progress.totalRequired)
        assertEquals(1, progress.missingRequired)
    }

    @Test
    fun `draft creation rejects a draft template`() {
        val result = StudentProjectDraftRules.create(
            id = "local-project-1",
            template = publishedTemplate().copy(publication = ProjectTemplatePublication.DRAFT),
            title = "My project",
            createdAtEpochMillis = 100,
        )

        assertIs<StudentProjectDraftCreateResult.Unavailable>(result)
        assertEquals("TEMPLATE_NOT_READY", result.code)
    }

    @Test
    fun `draft creation keeps the published template snapshot`() {
        val template = publishedTemplate()
        val result = StudentProjectDraftRules.create(
            id = "local-project-1",
            template = template,
            title = template.title,
            createdAtEpochMillis = 100,
        )

        assertIs<StudentProjectDraftCreateResult.Created>(result)
        assertEquals(template, result.draft.templateSnapshot)
        assertEquals(1, result.draft.revision)
        assertEquals(100, result.draft.updatedAtEpochMillis)
        assertTrue(result.draft.fieldValues.isEmpty())
        assertEquals(listOf(1), result.draft.revisionSnapshots.map(StudentProjectRevisionSnapshot::revision))
        assertFalse(StudentProjectDraftRules.needsRevisionCheckpoint(result.draft))
    }

    @Test
    fun `existing legacy project keeps its unclassified template snapshot`() {
        val legacyTemplate = publishedTemplate().copy(
            examples = listOf(ProjectTemplateExample("legacy-example", "Historical example.", true)),
        )
        val legacyProject = StudentProjectDraft(
            id = "legacy-project-1",
            templateSnapshot = legacyTemplate,
            title = "Existing project",
            fieldValues = emptyMap(),
            revision = 1,
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 100,
        )

        assertTrue(StudentProjectDraftRules.validate(legacyProject).isEmpty())
        assertIs<StudentProjectDraftCreateResult.Unavailable>(
            StudentProjectDraftRules.create(
                id = "new-legacy-project",
                template = legacyTemplate,
                title = "New project",
                createdAtEpochMillis = 100,
            ),
        )
    }

    @Test
    fun `manual project is genuinely blank and has no reviewed template identity`() {
        val result = StudentProjectDraftRules.createManual(
            id = "manual-project-1",
            title = "My first literature synthesis",
            createdAtEpochMillis = 100,
        )

        val project = assertIs<StudentProjectDraftCreateResult.Created>(result).draft
        assertEquals(null, project.templateSnapshot)
        assertTrue(project.fieldValues.isEmpty())
        assertTrue(project.sources.isEmpty())
        assertTrue(project.themes.isEmpty())
        assertTrue(project.claimEvidenceSourceIds.isEmpty())
        assertEquals(1, project.revisionSnapshots.size)
        assertEquals(StudentProjectStatus.DRAFT, project.status)
        assertEquals(0, StudentProjectDraftRules.requiredFieldProgress(project).filledRequired)
        assertTrue(StudentProjectDraftRules.structureReport(project).hasOpenStructureIssues)
    }

    @Test
    fun `structure report checks links and presence without scoring academic quality`() {
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            fieldValues = ManualLiteratureSynthesisFields.all
                .filter(StudentProjectFieldDefinition::requiredForStructureCheck)
                .associate { it.id to "Student-entered content" },
            sources = listOf(StudentProjectSourceRecord(id = "source-1")),
            themes = listOf(StudentProjectSynthesisTheme(id = "theme-1", title = "Theme")),
            claimEvidenceSourceIds = setOf("source-1"),
        )

        val report = StudentProjectDraftRules.structureReport(project)

        assertTrue(report.missingRequiredFieldIds.isEmpty())
        assertEquals(listOf("source-1"), report.incompleteSourceIds)
        assertEquals(listOf("theme-1"), report.unlinkedThemeIds)
        assertTrue(report.invalidClaimEvidenceIds.isEmpty())
        assertTrue(report.hasOpenStructureIssues)
        assertTrue(StudentProjectDraftRules.validate(project).isEmpty())
    }

    @Test
    fun `evidence and findings have stable typed links without grading their meaning`() {
        val source = StudentProjectSourceRecord(id = "source-1", title = "Study", citationText = "Study citation")
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            sources = listOf(source),
            evidenceItems = listOf(StudentProjectEvidenceItem(
                id = "evidence-1",
                sourceId = source.id,
                excerpt = "The article reports an association.",
                locator = "Results section",
                studentChecked = true,
            )),
            findings = listOf(StudentProjectFindingRecord(
                id = "finding-1",
                statement = "The reviewed sources report an association.",
            )),
            evidenceRelations = listOf(
                StudentProjectEvidenceRelation(
                    targetType = StudentProjectEvidenceTargetType.FINDING,
                    targetId = "finding-1",
                    evidenceId = "evidence-1",
                    relation = StudentProjectEvidenceRelationType.PROVIDES_CONTEXT,
                ),
                StudentProjectEvidenceRelation(
                    targetType = StudentProjectEvidenceTargetType.CLAIM,
                    targetId = StudentProjectDraftRules.PRIMARY_CLAIM_TARGET_ID,
                    evidenceId = "evidence-1",
                    relation = StudentProjectEvidenceRelationType.SUPPORTS,
                ),
            ),
        )

        assertTrue(StudentProjectDraftRules.validate(project).isEmpty())
        val report = StudentProjectDraftRules.structureReport(project.copy(
            fieldValues = mapOf(ManualLiteratureSynthesisFields.CLAIM to "A bounded student-written claim."),
        ))
        assertTrue(report.unlinkedEvidenceIds.isEmpty())
        assertTrue(report.unlinkedFindingIds.isEmpty())
        assertFalse(report.claimHasNoEvidenceLinks)
        assertTrue(report.hasOpenStructureIssues)
    }

    @Test
    fun `evidence relation cannot point to missing evidence or finding`() {
        val source = StudentProjectSourceRecord(id = "source-1", title = "Study", citationText = "Citation")
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            sources = listOf(source),
            evidenceItems = listOf(StudentProjectEvidenceItem(id = "evidence-1", sourceId = source.id, excerpt = "Note")),
            findings = listOf(StudentProjectFindingRecord(id = "finding-1", statement = "Finding")),
            evidenceRelations = listOf(StudentProjectEvidenceRelation(
                targetType = StudentProjectEvidenceTargetType.FINDING,
                targetId = "missing-finding",
                evidenceId = "missing-evidence",
                relation = StudentProjectEvidenceRelationType.SUPPORTS,
            )),
        )

        assertTrue("PROJECT_EVIDENCE_RELATION_INVALID" in StudentProjectDraftRules.validate(project))
    }

    @Test
    fun `each claim has a stable evidence target and structure reports unlinked claims`() {
        val source = StudentProjectSourceRecord(id = "source-1", title = "Study", citationText = "Citation")
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            sources = listOf(source),
            evidenceItems = listOf(
                StudentProjectEvidenceItem(id = "evidence-1", sourceId = source.id, excerpt = "Observation one"),
                StudentProjectEvidenceItem(id = "evidence-2", sourceId = source.id, excerpt = "Observation two"),
            ),
            claims = listOf(
                StudentProjectClaimRecord(id = "claim-a", statement = "A bounded claim.", scopeNote = "In this sample."),
                StudentProjectClaimRecord(id = "claim-b", statement = "A second bounded claim."),
            ),
            evidenceRelations = listOf(
                StudentProjectEvidenceRelation(
                    targetType = StudentProjectEvidenceTargetType.CLAIM,
                    targetId = "claim-a",
                    evidenceId = "evidence-1",
                    relation = StudentProjectEvidenceRelationType.SUPPORTS,
                    rationale = "The observation is relevant to claim A.",
                ),
            ),
        )

        assertTrue(StudentProjectDraftRules.validate(project).isEmpty())
        assertEquals(listOf("claim-b"), StudentProjectDraftRules.structureReport(project).unlinkedClaimIds)

        val dangling = project.copy(evidenceRelations = project.evidenceRelations + StudentProjectEvidenceRelation(
            targetType = StudentProjectEvidenceTargetType.CLAIM,
            targetId = "claim-missing",
            evidenceId = "evidence-2",
            relation = StudentProjectEvidenceRelationType.CONTRADICTS,
        ))
        assertTrue("PROJECT_EVIDENCE_RELATION_INVALID" in StudentProjectDraftRules.validate(dangling))
    }

    @Test
    fun `limitation actions retain stable links to affected findings and claims`() {
        val finding = StudentProjectFindingRecord(id = "finding-one", statement = "A reported pattern")
        val claim = StudentProjectClaimRecord(id = "claim-one", statement = "A bounded claim")
        val limitation = StudentProjectLimitationActionRecord(
            id = "limit-one",
            boundary = "Only two sources describe this outcome.",
            reason = "The remaining sources do not report the same measure.",
            nextAction = "Check whether another source reports the measure.",
            affectedFindingIds = setOf(finding.id),
            affectedClaimIds = setOf(claim.id),
        )
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            findings = listOf(finding),
            claims = listOf(claim),
            limitationActions = listOf(limitation),
        )

        assertTrue(StudentProjectDraftRules.validate(project).isEmpty())
        assertTrue(StudentProjectDraftRules.needsRevisionCheckpoint(project))
        assertEquals(
            listOf(limitation),
            StudentProjectDraftRules.captureRevisionSnapshot(
                project,
                StudentProjectRevisionActor.STUDENT,
                "Added a limitation and next action",
            ).limitationActions,
        )
    }

    @Test
    fun `limitation action rejects references to missing project records`() {
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            limitationActions = listOf(
                StudentProjectLimitationActionRecord(
                    id = "limit-one",
                    boundary = "A boundary",
                    affectedFindingIds = setOf("finding-missing"),
                    affectedClaimIds = setOf("claim-missing"),
                ),
            ),
        )

        assertTrue("PROJECT_LIMITATION_ACTION_INVALID" in StudentProjectDraftRules.validate(project))
    }

    @Test
    fun `removing a source removes only its links and flags dependent claims for student review`() {
        val sourceA = StudentProjectSourceRecord(id = "source-a", title = "Study A")
        val sourceB = StudentProjectSourceRecord(id = "source-b", title = "Study B")
        val evidenceA = StudentProjectEvidenceItem(id = "evidence-a", sourceId = sourceA.id, excerpt = "A result")
        val evidenceB = StudentProjectEvidenceItem(id = "evidence-b", sourceId = sourceB.id, excerpt = "B result")
        val finding = StudentProjectFindingRecord(id = "finding-a", statement = "A finding")
        val claimA = StudentProjectClaimRecord(
            id = "claim-a",
            statement = "A claim based on Study A",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val claimB = StudentProjectClaimRecord(
            id = "claim-b",
            statement = "A claim based on Study B",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val original = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            fieldValues = mapOf("synthesis" to "Keep this student-authored analysis for review."),
            sources = listOf(sourceA, sourceB),
            evidenceItems = listOf(evidenceA, evidenceB),
            findings = listOf(finding),
            themes = listOf(
                StudentProjectSynthesisTheme("theme-a", "Theme A", "A synthesis", setOf(sourceA.id)),
                StudentProjectSynthesisTheme("theme-b", "Theme B", "B synthesis", setOf(sourceB.id)),
            ),
            claimEvidenceSourceIds = setOf(sourceA.id, sourceB.id),
            claims = listOf(claimA, claimB),
            evidenceRelations = listOf(
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.FINDING,
                    finding.id,
                    evidenceA.id,
                    StudentProjectEvidenceRelationType.SUPPORTS,
                ),
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.CLAIM,
                    claimA.id,
                    evidenceA.id,
                    StudentProjectEvidenceRelationType.SUPPORTS,
                ),
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.CLAIM,
                    claimB.id,
                    evidenceB.id,
                    StudentProjectEvidenceRelationType.PROVIDES_CONTEXT,
                ),
            ),
        )

        val updated = StudentProjectDraftRules.removeSource(original, sourceA.id)

        assertEquals(listOf(sourceB), updated.sources)
        assertEquals(listOf(evidenceB), updated.evidenceItems)
        assertEquals(listOf(finding), updated.findings)
        assertEquals(listOf("theme-a", "theme-b"), updated.themes.map(StudentProjectSynthesisTheme::id))
        assertEquals(emptySet(), updated.themes.first().sourceIds)
        assertEquals(setOf(sourceB.id), updated.themes.last().sourceIds)
        assertEquals(setOf(sourceB.id), updated.claimEvidenceSourceIds)
        assertEquals(listOf(claimB.id), updated.evidenceRelations.map(StudentProjectEvidenceRelation::targetId))
        assertEquals(StudentProjectClaimReviewStatus.NEEDS_REVISION, updated.claims.first().reviewStatus)
        assertEquals(StudentProjectClaimReviewStatus.READY_FOR_REVIEW, updated.claims.last().reviewStatus)
        assertEquals(original.fieldValues, updated.fieldValues)
        assertEquals(original.revision, updated.revision)
        assertEquals(original.revisionSnapshots, updated.revisionSnapshots)
    }

    @Test
    fun `removing an evidence note flags only its linked claims for recheck`() {
        val source = StudentProjectSourceRecord(id = "source-a", title = "Study A")
        val evidenceA = StudentProjectEvidenceItem(id = "evidence-a", sourceId = source.id, excerpt = "A result")
        val evidenceB = StudentProjectEvidenceItem(id = "evidence-b", sourceId = source.id, excerpt = "B result")
        val finding = StudentProjectFindingRecord(id = "finding-a", statement = "A finding")
        val claimA = StudentProjectClaimRecord(
            id = "claim-a",
            statement = "A claim",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val claimB = StudentProjectClaimRecord(
            id = "claim-b",
            statement = "Another claim",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )
        val original = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft.copy(
            sources = listOf(source),
            evidenceItems = listOf(evidenceA, evidenceB),
            findings = listOf(finding),
            claims = listOf(claimA, claimB),
            evidenceRelations = listOf(
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.FINDING,
                    finding.id,
                    evidenceA.id,
                    StudentProjectEvidenceRelationType.SUPPORTS,
                ),
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.CLAIM,
                    claimA.id,
                    evidenceA.id,
                    StudentProjectEvidenceRelationType.CONTRADICTS,
                ),
                StudentProjectEvidenceRelation(
                    StudentProjectEvidenceTargetType.CLAIM,
                    claimB.id,
                    evidenceB.id,
                    StudentProjectEvidenceRelationType.SUPPORTS,
                ),
            ),
        )

        val updated = StudentProjectDraftRules.removeEvidenceNote(original, evidenceA.id)

        assertEquals(listOf(evidenceB), updated.evidenceItems)
        assertEquals(listOf(finding), updated.findings)
        assertEquals(listOf(claimB.id), updated.evidenceRelations.map(StudentProjectEvidenceRelation::targetId))
        assertEquals(StudentProjectClaimReviewStatus.NEEDS_REVISION, updated.claims.first().reviewStatus)
        assertEquals(StudentProjectClaimReviewStatus.READY_FOR_REVIEW, updated.claims.last().reviewStatus)
    }

    @Test
    fun `claim edits participate in immutable revision checkpoints`() {
        val initial = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft
        val withClaim = initial.copy(claims = listOf(StudentProjectClaimRecord(
            id = "claim-primary",
            statement = "A bounded statement.",
            reviewStatus = StudentProjectClaimReviewStatus.READY_FOR_REVIEW,
        )))

        assertTrue(StudentProjectDraftRules.needsRevisionCheckpoint(withClaim))
        assertEquals(withClaim.claims, StudentProjectDraftRules.captureRevisionSnapshot(
            withClaim,
            StudentProjectRevisionActor.STUDENT,
            "claim added",
        ).claims)
    }

    @Test
    fun `autosaved content is detected as newer than the last immutable checkpoint`() {
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft

        val autosaved = project.copy(
            fieldValues = mapOf(ManualLiteratureSynthesisFields.RESEARCH_QUESTION to "What changed?"),
            updatedAtEpochMillis = 200,
        )

        assertTrue(StudentProjectDraftRules.needsRevisionCheckpoint(autosaved))
        assertEquals(emptyMap(), project.revisionSnapshots.single().fieldValues)
        assertEquals(1, project.revisionSnapshots.single().revision)
    }

    @Test
    fun `revision retention never silently prunes intermediate checkpoints`() {
        val base = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft
        val history = (1..12).map { revision ->
            StudentProjectDraftRules.captureRevisionSnapshot(
                base.copy(revision = revision, updatedAtEpochMillis = 100L + revision),
                StudentProjectRevisionActor.STUDENT,
                "checkpoint $revision",
            )
        }
        val project = base.copy(
            revision = 12,
            updatedAtEpochMillis = 112,
            revisionSnapshots = StudentProjectDraftRules.retainRevisionSnapshots(history),
        )

        assertEquals(history, project.revisionSnapshots)
        assertTrue(StudentProjectDraftRules.validate(project).isEmpty())
    }

    @Test
    fun `attachment metadata participates in revision checkpoints without carrying file bytes`() {
        val project = assertIs<StudentProjectDraftCreateResult.Created>(
            StudentProjectDraftRules.createManual("manual-project-1", "My project", 100),
        ).draft
        val attachment = StudentProjectAttachmentRef(
            id = "d07a9f35-9da0-4b68-bd6c-809d269f50fa",
            fileName = "study-notes.txt",
            mimeType = "text/plain",
            sizeBytes = 12,
            sha256 = "0".repeat(64),
        )
        val attached = project.copy(attachments = listOf(attachment))

        assertTrue(StudentProjectDraftRules.validate(attached).isEmpty())
        assertTrue("DUPLICATE_PROJECT_ATTACHMENT_ID" in StudentProjectDraftRules.validate(
            attached.copy(attachments = listOf(attachment, attachment.copy(id = attachment.id.uppercase()))),
        ))
        assertTrue(StudentProjectDraftRules.needsRevisionCheckpoint(attached))
        assertEquals(listOf(attachment), StudentProjectDraftRules.captureRevisionSnapshot(
            attached,
            StudentProjectRevisionActor.STUDENT,
            "Attachment added",
        ).attachments)
        assertTrue("TOO_MANY_PROJECT_ATTACHMENTS" in StudentProjectDraftRules.validate(
            attached.copy(attachments = (1..51).map { attachment.copy(id = "d07a9f35-9da0-4b68-bd6c-${it.toString().padStart(12, '0')}") }),
        ))
    }

    @Test
    fun `manual fields and verified entitlement define bounded per-install quotas`() {
        assertEquals(5, StudentProjectDraftRules.activeProjectLimit(hasVerifiedProEntitlement = false))
        assertEquals(50, StudentProjectDraftRules.activeProjectLimit(hasVerifiedProEntitlement = true))
        assertTrue(StudentProjectDraftRules.countsTowardActiveLimit(StudentProjectStatus.DRAFT))
        assertTrue(StudentProjectDraftRules.countsTowardActiveLimit(StudentProjectStatus.ACTIVE))
        assertFalse(StudentProjectDraftRules.countsTowardActiveLimit(StudentProjectStatus.COMPLETED))
        assertFalse(StudentProjectDraftRules.countsTowardActiveLimit(StudentProjectStatus.ARCHIVED))
        assertFalse(StudentProjectDraftRules.countsTowardActiveLimit(StudentProjectStatus.TRASHED))
    }

    @Test
    fun `field validation rejects unknown keys and oversized responses`() {
        val template = publishedTemplate()
        val invalid = StudentProjectDraft(
            id = "local-project-1",
            templateSnapshot = template,
            title = "My project",
            fieldValues = mapOf("not-a-template-field" to "value"),
            revision = 1,
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 100,
        )

        val issues = StudentProjectDraftRules.validate(invalid)

        assertTrue("UNKNOWN_PROJECT_FIELD" in issues)
        assertTrue("PROJECT_FIELD_VALUE_INVALID" in StudentProjectDraftRules.validate(invalid.copy(
            fieldValues = mapOf("question" to "x".repeat(StudentProjectDraftRules.MAX_FIELD_CHARS + 1)),
        )))
    }

    private fun publishedTemplate() = ProjectTemplateDefinition(
        id = "reviewed-template",
        version = 3,
        family = ProjectTemplateFamily.EXPERIMENTAL_LABORATORY,
        title = "Reviewed template",
        summary = "A reviewed summary.",
        intendedOutput = "A bounded report.",
        inputFields = listOf(
            ProjectTemplateInputField("question", ProjectTemplateInputKind.RESEARCH_QUESTION, "Question", true),
            ProjectTemplateInputField("method", ProjectTemplateInputKind.ASSIGNMENT_BRIEF, "Method", true),
            ProjectTemplateInputField("optional", ProjectTemplateInputKind.HYPOTHESIS, "Optional prediction", false),
        ),
        steps = listOf(ProjectTemplateStep("frame", "Frame the project", listOf("question", "method"))),
        methodSpecificLimitations = listOf("A bounded limitation."),
        provenanceRequirements = listOf("Record source provenance."),
        accessibilityExpectations = listOf("Use text labels."),
        examples = listOf(
            ProjectTemplateExample("example-normal", "Reviewed nominal example.", true, ProjectTemplateExampleKind.NORMAL),
            ProjectTemplateExample(
                "example-edge",
                "Reviewed edge or conflicting example.",
                true,
                ProjectTemplateExampleKind.EDGE_OR_CONFLICTING,
            ),
        ),
        publication = ProjectTemplatePublication.PUBLISHED,
    )
}
