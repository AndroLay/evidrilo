package dev.nextgen.mobile

import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldDecision
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentState
import dev.nextgen.mobile.projectcatalog.PROJECT_AI_CONSENT_POLICY_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EvidriloProjectAiScaffoldPanelTest {
    @Test
    fun `project AI explains sign in requirement when guest mode is disabled`() {
        val aiMessage = projectAiAccountRequirementMessage(
            guestModeEnabled = false,
            consentManagement = false,
        )
        val consentMessage = projectAiAccountRequirementMessage(
            guestModeEnabled = false,
            consentManagement = true,
        )
        val guestMessage = projectAiAccountRequirementMessage(
            guestModeEnabled = true,
            consentManagement = false,
        )

        assertTrue(aiMessage.contains("verified account"))
        assertTrue(aiMessage.contains("no context was sent", ignoreCase = true))
        assertFalse(aiMessage.contains("guest mode", ignoreCase = true))
        assertTrue(consentMessage.contains("sign in", ignoreCase = true))
        assertTrue(consentMessage.contains("consent"))
        assertFalse(consentMessage.contains("guest mode", ignoreCase = true))
        assertTrue(guestMessage.contains("guest mode", ignoreCase = true))
    }

    @Test
    fun `project AI transient state is isolated between projects and accounts`() {
        val projectA = projectAiScaffoldPanelStateKey(
            templateId = "reviewed-template",
            projectId = "project-a",
            projectRevision = 3,
            accountId = "account-a",
        )

        assertNotEquals(
            projectA,
            projectAiScaffoldPanelStateKey("reviewed-template", "project-b", 3, "account-a"),
        )
        assertNotEquals(
            projectA,
            projectAiScaffoldPanelStateKey("reviewed-template", "project-a", 3, "account-b"),
        )
    }

    @Test
    fun `per request AI consent is consumed and must be confirmed again`() {
        val firstRequest = ProjectAiRequestConsentState(confirmed = true).consume()

        assertTrue(firstRequest.confirmedForThisRequest)
        assertFalse(firstRequest.nextRequestState.confirmed)

        val secondRequest = firstRequest.nextRequestState.consume()
        assertFalse(secondRequest.confirmedForThisRequest)
        assertFalse(secondRequest.nextRequestState.confirmed)
    }

    @Test
    fun `project creation discloses token-priced credit settlement before request`() {
        val disclosure = projectAiCreditDisclosure(creating = true)

        assertTrue(disclosure.contains("verified provider token usage"))
        assertTrue(disclosure.contains("up to 200 credits per request"))
        assertTrue(disclosure.contains("Only a valid preview"))
        assertTrue(disclosure.contains("rejected, failed, or stale requests release the reservation"))
        assertTrue(disclosure.contains("Dismissing a valid preview does not refund it"))
    }

    @Test
    fun `in-project assistance uses the same token-priced settlement rule`() {
        val disclosure = projectAiCreditDisclosure(creating = false)

        assertTrue(disclosure.contains("verified provider token usage"))
        assertTrue(disclosure.contains("up to 200 credits per request"))
        assertTrue(disclosure.contains("Only a valid preview"))
        assertTrue(disclosure.contains("rejected, failed, or stale requests release the reservation"))
        assertTrue(disclosure.contains("Dismissing a valid preview does not refund it"))
    }

    @Test
    fun `applying or dismissing records the decision without implying a refund`() {
        val applied = projectAiSettlementStatusMessage(ProjectAiScaffoldDecision.APPLY, 7)
        val dismissed = projectAiSettlementStatusMessage(ProjectAiScaffoldDecision.DISMISS, 7)

        assertTrue(applied.contains("7-credit preview charge is already settled"))
        assertTrue(dismissed.contains("7-credit preview charge is already settled"))
        assertFalse(applied.contains("releas"))
        assertFalse(dismissed.contains("releas"))
    }

    @Test
    fun `client validation rejection is honest that a valid preview was already charged`() {
        val notice = projectAiClientValidationFailureMessage("TEMPLATE_MISMATCH", 3)

        assertTrue(notice.contains("3-credit charge is already settled"))
        assertTrue(notice.contains("no project fields were changed"))
        assertFalse(notice.contains("releas"))
    }

    @Test
    fun `project AI requires both explicit consent and an assignment brief`() {
        assertFalse(canRequestProjectAi(dataConsent = false, assignmentBrief = "A real brief"))
        assertFalse(canRequestProjectAi(dataConsent = true, assignmentBrief = " \n "))
        assertTrue(canRequestProjectAi(dataConsent = true, assignmentBrief = "A real brief"))
    }

    @Test
    fun `project preview cannot apply while editor is dirty or its revision identity is stale`() {
        assertFalse(canApplyProjectAiPreview(true, "project-1", "project-1", 4, 5))
        assertFalse(canApplyProjectAiPreview(false, "project-1", "project-1", 4, 4))
        assertFalse(canApplyProjectAiPreview(true, "project-other", "project-1", 4, 4))
        assertTrue(canApplyProjectAiPreview(true, "project-1", "project-1", 4, 4))
    }

    @Test
    fun `disabled provider is presented as unavailable and not generated guidance`() {
        val state = ProjectAiScaffoldGatewayResult.Unavailable("PROJECT_AI_NOT_READY")
            .toProjectAiScaffoldUiState()

        val unavailable = assertIs<ProjectAiScaffoldUiState.Unavailable>(state)
        assertEquals(false, unavailable.retryable)
        assertEquals(true, unavailable.message.contains("not enabled yet"))
    }

    @Test
    fun `saved project AI consent distinguishes current grant from revocation`() {
        val granted = ProjectAiConsentGatewayResult.State(
            ProjectAiConsentState(
                granted = true,
                policyVersion = PROJECT_AI_CONSENT_POLICY_VERSION,
                grantedAt = "2026-09-27T00:00:00Z",
                revokedAt = null,
                generation = 2,
            ),
        ).toProjectAiConsentUiState()
        val revoked = ProjectAiConsentGatewayResult.State(
            ProjectAiConsentState(
                granted = false,
                policyVersion = PROJECT_AI_CONSENT_POLICY_VERSION,
                grantedAt = "2026-09-27T00:00:00Z",
                revokedAt = "2026-09-27T00:10:00Z",
                generation = 3,
            ),
        ).toProjectAiConsentUiState()

        assertEquals(ProjectAiConsentUiState.Granted, granted)
        assertEquals(ProjectAiConsentUiState.NotGranted, revoked)
    }

    @Test
    fun `stale project AI policy is not treated as current consent`() {
        val state = ProjectAiConsentGatewayResult.State(
            ProjectAiConsentState(
                granted = true,
                policyVersion = "project-ai-data.v0",
                grantedAt = "2026-09-27T00:00:00Z",
                revokedAt = null,
                generation = 1,
            ),
        ).toProjectAiConsentUiState()

        val unavailable = assertIs<ProjectAiConsentUiState.Unavailable>(state)
        assertTrue(unavailable.message.contains("out of date"))
    }

    @Test
    fun `unknown request outcome is not offered as a safe automatic retry`() {
        val state = ProjectAiScaffoldGatewayResult.Failed(
            code = "PROJECT_AI_OUTCOME_UNKNOWN",
            retryable = true,
            outcomeUnknown = true,
            sameIntentReplayAllowed = false,
            idempotencyKey = null,
        ).toProjectAiScaffoldUiState()

        val unavailable = assertIs<ProjectAiScaffoldUiState.Unavailable>(state)
        assertEquals(false, unavailable.retryable)
        assertEquals(true, unavailable.message.contains("outcome is unknown"))
    }

    @Test
    fun `only explicitly selected nonblank project fields are sent as additional context`() {
        val selected = selectProjectAiContextFields(
            currentFields = mapOf(
                "assignment_brief" to "Assignment text",
                "research_question" to "Research question",
                "scope" to "Private scope note",
                "limitations" to "",
            ),
            selectedFieldIds = setOf("research_question", "scope", "limitations"),
            assignmentBriefFieldId = "assignment_brief",
        )

        assertEquals(
            mapOf("research_question" to "Research question", "scope" to "Private scope note"),
            selected,
        )
    }
}
