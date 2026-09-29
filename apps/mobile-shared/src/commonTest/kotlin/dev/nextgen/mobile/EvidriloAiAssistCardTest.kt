package dev.nextgen.mobile

import dev.nextgen.mobile.ai.AiAssistPurpose
import dev.nextgen.mobile.ai.AiConversationGatewayResult
import dev.nextgen.mobile.ai.AiConversationProposal
import dev.nextgen.mobile.ai.AiConversationTurn
import dev.nextgen.mobile.ai.AiGatewayResult
import dev.nextgen.mobile.ai.AiCreditGrant
import dev.nextgen.mobile.ai.AiCredits
import dev.nextgen.mobile.domain.conclusion.ConclusionField
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EvidriloAiAssistCardTest {
    @Test
    fun `stale paid ai response reports its actual charge without exposing its answer`() {
        val response = AiConversationGatewayResult.TurnReceived(
            AiConversationTurn(
                status = "success",
                kind = "explanation",
                text = "This answer belongs to the previous project context.",
                reasonCode = null,
                groundedAnchorIds = listOf("OBS-01"),
                proposal = null,
                turnsUsed = 1,
                turnsRemaining = 4,
                requestId = "req-stale-paid-001",
                autoApplied = false,
                creditCost = 4,
            ),
        )

        val state = assertIs<EvidriloAiAssistUiState.Unavailable>(
            staleAiConversationRecoveryState(response, remainingCredits = 7),
        )

        assertEquals(4, state.creditCost)
        assertEquals(7, state.remainingCredits)
        assertEquals("req-stale-paid-001", state.requestId)
        assertFalse(state.retryable)
        assertTrue(state.message.contains("previous workspace state"))
        assertFalse(state.message.contains("This answer belongs"))
    }

    @Test
    fun `stale session-expired fallback reports the actual charge and refreshed balance`() {
        val response = AiConversationGatewayResult.Fallback(
            reasonCode = "AI_CONVERSATION_SESSION_EXPIRED",
            requestId = "req-stale-expired-001",
            creditCost = 2,
        )

        val state = assertIs<EvidriloAiAssistUiState.Unavailable>(
            staleAiConversationRecoveryState(response, remainingCredits = 5),
        )

        assertEquals(2, state.creditCost)
        assertEquals(5, state.remainingCredits)
        assertEquals("req-stale-expired-001", state.requestId)
        assertFalse(state.retryable)
    }

    @Test
    fun `credit allowance copy makes no tier promise before consent`() {
        val credits = AiCredits(
            consentRecorded = false,
            available = 0,
            grants = emptyList(),
            requestId = "req-ai-test",
        )

        val label = aiCreditAllowanceLabel(credits)

        assertEquals("Review consent to see available credits", label)
        assertFalse(label.any(Char::isDigit))
        assertFalse(label.contains("Free", ignoreCase = true))
        assertFalse(label.contains("Pro", ignoreCase = true))
    }

    @Test
    fun `credit balance explains the server returned grant scopes and expiry`() {
        val credits = AiCredits(
            consentRecorded = true,
            available = 217,
            grants = listOf(
                AiCreditGrant("free_once", "free_once", 20, 1, 2, 17, null),
                AiCreditGrant("subscription_month", "2026-09", 200, 0, 0, 200, "2026-10-01T00:00:00Z"),
            ),
            requestId = "req-ai-test",
        )

        assertEquals(
            "217 available\nFree · one-time: 17 available\nPro · current period: 200 available · expires 2026-10-01",
            aiCreditAllowanceLabel(credits),
        )
    }

    @Test
    fun `consented zero balance states when no grant is active`() {
        val credits = AiCredits(
            consentRecorded = true,
            available = 0,
            grants = emptyList(),
            requestId = "req-ai-test",
        )

        assertEquals("0 available\nNo active AI credit grants", aiCreditAllowanceLabel(credits))
    }

    @Test
    fun `chat reports actual successful and fallback credit costs`() {
        assertEquals("1 AI credit used", aiCreditUsageLabel(1, error = false))
        assertEquals("4 AI credits used", aiCreditUsageLabel(4, error = false))
        assertEquals("No AI credits were charged", aiCreditUsageLabel(0, error = true))
        assertEquals(
            "1 AI credit was charged before this turn became unavailable",
            aiCreditUsageLabel(1, error = true),
        )
        assertEquals(
            "2 AI credits were charged before this turn became unavailable",
            aiCreditUsageLabel(2, error = true),
        )
    }

    @Test
    fun chat_quick_actions_stay_inside_the_bounded_ai_contract() {
        val actions = evidriloAiChatQuickActions()

        assertEquals(
            listOf(
                AiAssistPurpose.EXPLAIN_FEEDBACK,
                AiAssistPurpose.EXPLAIN_FEEDBACK,
                AiAssistPurpose.REFLECTION_QUESTION,
            ),
            actions.map { it.purpose },
        )
        assertTrue(actions.all { it.label.isNotBlank() && it.description.isNotBlank() })
        assertTrue(actions.all { it.prompt.isNotBlank() })
    }

    @Test
    fun next_prompts_follow_the_last_help_type_and_current_scope_issue() {
        val suggestions = evidriloAiNextPromptRecommendations(
            context = EvidriloAiPromptRecommendationContext(
                status = ConclusionStatus.ACTION_REQUIRED,
                field = ConclusionField.SCOPE,
                allowedAnchorIds = listOf("OBS-WARM-01", "LIMIT-STIR-01"),
                groundedAnchorIds = listOf("OBS-WARM-01"),
            ),
            previousPurpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
        )

        assertEquals(
            listOf(AiAssistPurpose.REFLECTION_QUESTION, AiAssistPurpose.LANGUAGE_ALTERNATIVE),
            suggestions.map { it.purpose },
        )
        assertEquals("Reconsider the claim boundary", suggestions.first().label)
        assertTrue(suggestions.first().prompt.contains("claim"))
        assertTrue(suggestions.all { it.prompt.isNotBlank() && it.description.isNotBlank() })
    }

    @Test
    fun incomplete_feedback_never_recommends_rewording_as_a_substitute_for_missing_work() {
        val suggestions = evidriloAiNextPromptRecommendations(
            context = EvidriloAiPromptRecommendationContext(
                status = ConclusionStatus.INCOMPLETE,
                field = ConclusionField.EVIDENCE_REFS,
                allowedAnchorIds = listOf("AIM-01"),
                groundedAnchorIds = listOf("AIM-01"),
            ),
            previousPurpose = AiAssistPurpose.REFLECTION_QUESTION,
        )

        assertEquals(listOf(AiAssistPurpose.EXPLAIN_FEEDBACK), suggestions.map { it.purpose })
        assertFalse(suggestions.any { it.purpose == AiAssistPurpose.LANGUAGE_ALTERNATIVE })
        assertTrue(suggestions.single().prompt.contains("required"))
    }

    @Test
    fun next_prompt_recommendations_fail_closed_without_matching_grounded_anchors() {
        val suggestions = evidriloAiNextPromptRecommendations(
            context = EvidriloAiPromptRecommendationContext(
                status = ConclusionStatus.ACTION_REQUIRED,
                field = ConclusionField.CLAIM_TEXT,
                allowedAnchorIds = listOf("OBS-WARM-01"),
                groundedAnchorIds = listOf("FOREIGN-01"),
            ),
            previousPurpose = AiAssistPurpose.EXPLAIN_FEEDBACK,
        )

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun chat_request_is_disabled_without_credits_after_consent() {
        val credits = dev.nextgen.mobile.ai.AiCredits(
            consentRecorded = true,
            available = 0,
            grants = emptyList(),
            requestId = "req-ai-test",
        )

        assertEquals(false, aiChatCanRequest(credits))
    }

    @Test
    fun clear_is_unavailable_while_a_chat_request_is_still_processing() {
        assertFalse(aiChatCanClear(hasTranscript = false, requestInFlight = false))
        assertFalse(aiChatCanClear(hasTranscript = true, requestInFlight = true))
        assertTrue(aiChatCanClear(hasTranscript = true, requestInFlight = false))
    }

    @Test
    fun unclear_clear_outcome_keeps_the_same_session_available_for_safe_retry() {
        val state = aiConversationClearStateAfterGatewayResult(
            sessionId = "session-123",
            accountId = "account-456",
            result = AiConversationGatewayResult.Failed(
                AiGatewayResult.Failed(
                    code = "AI_OUTCOME_UNKNOWN",
                    retryable = false,
                    outcomeUnknown = true,
                ),
            ),
        )

        val failure = assertIs<EvidriloAiConversationClearState.Failed>(state)
        assertEquals("session-123", failure.sessionId)
        assertEquals("account-456", failure.accountId)
        assertTrue(failure.retryable)
    }

    @Test
    fun confirmed_clear_does_not_leave_a_failure_notice() {
        val state = aiConversationClearStateAfterGatewayResult(
            sessionId = "session-123",
            accountId = "account-456",
            result = AiConversationGatewayResult.Cleared("session-123"),
        )

        assertEquals(EvidriloAiConversationClearState.Idle, state)
    }

    @Test
    fun a_new_ai_turn_waits_until_the_previous_session_clear_finishes() {
        assertFalse(
            aiChatCanSendDuringConversationClear(
                EvidriloAiConversationClearState.Clearing("session-123", "account-456"),
            ),
        )
        assertTrue(aiChatCanSendDuringConversationClear(EvidriloAiConversationClearState.Idle))
        assertTrue(
            aiChatCanSendDuringConversationClear(
                EvidriloAiConversationClearState.Failed(
                    "session-123",
                    "account-456",
                    retryable = false,
                ),
            ),
        )
    }

    @Test
    fun answer_keeps_grounded_anchor_ids_visible_to_the_presentation_state() {
        val answer = EvidriloAiAssistUiState.Answer(
            text = "The claim stays bounded.",
            remainingCredits = 9,
            groundedAnchorIds = listOf("OBS-01", "LIMIT-01"),
        )

        assertEquals(listOf("OBS-01", "LIMIT-01"), answer.groundedAnchorIds)
    }

    @Test
    fun proposal_application_requires_the_current_value_and_updates_only_learner_fields() {
        val draft = ConclusionDraft(
            claimText = "Warm water causes faster dissolution.",
            scope = ConclusionScope.GENERAL_CAUSAL_CLAIM,
            limitationNote = "One trial per condition.",
        )
        val claim = AiConversationProposal(
            "claim_text",
            draft.claimText,
            "The warm-water trial dissolved the tablet in less time.",
            listOf("OBS-WARM-01"),
        )
        val changed = applyGroundedAiProposal(draft, claim)

        assertEquals("The warm-water trial dissolved the tablet in less time.", changed?.claimText)
        assertEquals(draft.scope, changed?.scope)
        assertEquals(draft.limitationNote, changed?.limitationNote)
        assertEquals(
            null,
            applyGroundedAiProposal(draft, claim.copy(beforeValue = "A stale claim")),
        )
    }

    @Test
    fun proposal_application_rejects_unmapped_scopes_and_non_draft_next_actions() {
        val draft = ConclusionDraft(scope = ConclusionScope.LIMITED_COMPARISON)
        val unknownScope = AiConversationProposal(
            "claim_scope",
            "LIMITED_COMPARISON",
            "OBSERVED_COMPARISON_ONLY",
            listOf("OBS-WARM-01"),
        )
        val action = AiConversationProposal(
            "next_action",
            "Repeat the measurement.",
            "Control stirring in a repeat trial.",
            listOf("LIMIT-TRIAL-01"),
        )

        assertEquals(null, applyGroundedAiProposal(draft, unknownScope))
        assertEquals(null, applyGroundedAiProposal(draft, action))
    }

    @Test
    fun inline_proposal_edit_preserves_the_original_field_before_value_and_anchors() {
        val proposal = AiConversationProposal(
            field = "claim_text",
            beforeValue = "The original claim.",
            suggestedValue = "The generated suggestion.",
            anchorIds = listOf("OBS-WARM-01", "LIMIT-STIR-01"),
        )

        val edited = editGroundedAiProposal(proposal, "  A student-edited claim.  ")

        assertEquals("claim_text", edited?.field)
        assertEquals("The original claim.", edited?.beforeValue)
        assertEquals("A student-edited claim.", edited?.suggestedValue)
        assertEquals(listOf("OBS-WARM-01", "LIMIT-STIR-01"), edited?.anchorIds)
    }

    @Test
    fun inline_proposal_edit_accepts_only_supported_claim_scopes() {
        val proposal = AiConversationProposal(
            field = "claim_scope",
            beforeValue = ConclusionScope.GENERAL_CAUSAL_CLAIM.name,
            suggestedValue = "UNRECOGNIZED_SCOPE",
            anchorIds = listOf("OBS-WARM-01"),
        )

        val edited = editGroundedAiProposal(proposal, ConclusionScope.LIMITED_COMPARISON.name)

        assertEquals(ConclusionScope.LIMITED_COMPARISON.name, edited?.suggestedValue)
        assertEquals(proposal.beforeValue, edited?.beforeValue)
        assertEquals(proposal.anchorIds, edited?.anchorIds)
        assertEquals(
            null,
            editGroundedAiProposal(proposal, ConclusionScope.UNSUPPORTED.name),
        )
    }

    @Test
    fun inline_proposal_edit_rejects_blank_oversized_and_unmapped_values() {
        val claim = AiConversationProposal(
            field = "claim_text",
            beforeValue = null,
            suggestedValue = "A generated claim.",
            anchorIds = listOf("OBS-WARM-01"),
        )
        val limitation = claim.copy(field = "learner_limitation", suggestedValue = "A limitation.")
        val nextAction = claim.copy(field = "next_action")

        assertEquals(null, editGroundedAiProposal(claim, "   "))
        assertEquals(null, editGroundedAiProposal(claim, "x".repeat(321)))
        assertEquals(null, editGroundedAiProposal(limitation, "x".repeat(301)))
        assertEquals(null, editGroundedAiProposal(nextAction, "A new action."))
    }

    @Test
    fun edited_proposal_is_still_checked_against_the_current_draft_before_application() {
        val draft = ConclusionDraft(claimText = "The original claim.")
        val proposal = AiConversationProposal(
            field = "claim_text",
            beforeValue = "The original claim.",
            suggestedValue = "The generated suggestion.",
            anchorIds = listOf("OBS-WARM-01"),
        )

        val edited = requireNotNull(editGroundedAiProposal(proposal, "My revised claim."))
        val applied = applyGroundedAiProposal(draft, edited)

        assertEquals("My revised claim.", applied?.claimText)
        assertEquals(draft.scope, applied?.scope)
        assertEquals(draft.evidenceRefs, applied?.evidenceRefs)
        assertEquals(
            null,
            applyGroundedAiProposal(draft.copy(claimText = "Changed elsewhere."), edited),
        )
    }
}
