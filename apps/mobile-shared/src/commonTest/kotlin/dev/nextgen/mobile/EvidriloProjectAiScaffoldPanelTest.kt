package dev.nextgen.mobile

import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentState
import dev.nextgen.mobile.projectcatalog.PROJECT_AI_CONSENT_POLICY_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EvidriloProjectAiScaffoldPanelTest {
    @Test
    fun `project creation discloses scaffold credit cost and settlement rule before request`() {
        val disclosure = projectAiCreditDisclosure(creating = true)

        assertTrue(disclosure.contains("3 AI credits"))
        assertTrue(disclosure.contains("only if you apply"))
        assertTrue(disclosure.contains("dismissal or failure releases"))
    }

    @Test
    fun `in-project assistance discloses one-credit cost and settlement rule before request`() {
        val disclosure = projectAiCreditDisclosure(creating = false)

        assertTrue(disclosure.contains("1 AI credit"))
        assertTrue(disclosure.contains("only if you apply"))
        assertTrue(disclosure.contains("dismissal or failure releases"))
    }

    @Test
    fun `project AI requires both explicit consent and an assignment brief`() {
        assertFalse(canRequestProjectAi(dataConsent = false, assignmentBrief = "A real brief"))
        assertFalse(canRequestProjectAi(dataConsent = true, assignmentBrief = " \n "))
        assertTrue(canRequestProjectAi(dataConsent = true, assignmentBrief = "A real brief"))
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
