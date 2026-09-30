package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloProjectAiStageAssistPanelTest {
    private val current = ProjectAiStageAssistContextIdentity(
        accountId = "account-a",
        projectId = "project-a",
        templateId = "template-a",
        templateVersion = 2,
        projectRevision = 7,
        stageId = "frame-question",
        operationId = "refine-question",
    )

    @Test
    fun applyRequiresTheSameSavedProjectStageAndAtLeastOneSelectedProposal() {
        assertTrue(canApplyProjectAiStageAssistPreview(current, current, isDirty = false, hasSelectedProposals = true))
        assertFalse(canApplyProjectAiStageAssistPreview(current, current, isDirty = true, hasSelectedProposals = true))
        assertFalse(canApplyProjectAiStageAssistPreview(current, current.copy(projectId = "project-b"), false, true))
        assertFalse(canApplyProjectAiStageAssistPreview(current, current.copy(projectRevision = 8), false, true))
        assertFalse(canApplyProjectAiStageAssistPreview(current, current.copy(templateVersion = 3), false, true))
        assertFalse(canApplyProjectAiStageAssistPreview(current, current.copy(stageId = "another-stage"), false, true))
        assertFalse(canApplyProjectAiStageAssistPreview(current, current.copy(operationId = "another-operation"), false, true))
        assertFalse(canApplyProjectAiStageAssistPreview(current, current, isDirty = false, hasSelectedProposals = false))
    }

    @Test
    fun proposalCanBeReviewedBeforeOneIsSelectedButNotAfterContextChanges() {
        assertTrue(canReviewProjectAiStageAssistPreview(current, current, isDirty = false))
        assertFalse(canReviewProjectAiStageAssistPreview(current, current, isDirty = true))
        assertFalse(canReviewProjectAiStageAssistPreview(current, current.copy(projectRevision = 8), isDirty = false))
    }

    @Test
    fun stalePreviewCanBeDismissedButNeverShownAcrossAccountOrStageBoundaries() {
        val newerRevision = current.copy(projectRevision = current.projectRevision + 1)
        assertTrue(current.sameStage(newerRevision))
        assertTrue(canDismissProjectAiStageAssistPreview(current, newerRevision))
        assertFalse(current.sameStage(newerRevision.copy(accountId = "account-b")))
        assertFalse(current.sameStage(newerRevision.copy(projectId = "project-b")))
        assertFalse(current.sameStage(newerRevision.copy(stageId = "analysis")))
    }
}
