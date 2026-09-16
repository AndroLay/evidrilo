package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import dev.nextgen.mobile.billing.BillingOperation
import dev.nextgen.mobile.billing.BillingOutcome
import dev.nextgen.mobile.billing.BillingOffer
import dev.nextgen.mobile.billing.PremiumAccess
import dev.nextgen.mobile.recommendation.RecommendationReason
import dev.nextgen.mobile.recommendation.RecommendationStatus
import dev.nextgen.mobile.recommendation.RecommendationPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsEventFactoryTest {
    @Test
    fun recommendation_events_are_typed_minimal_and_contract_safe() {
        val shown = recommendationShownAnalyticsEvent("M0_T2:1", "evidence")
        val accepted = recommendationAcceptedAnalyticsEvent("M0_T2:1", null)
        val dismissed = recommendationDismissedAnalyticsEvent("M0_T2:1", "limits")

        assertEquals(AnalyticsEventName.RECOMMENDATION_SHOWN, shown.name)
        assertEquals(AnalyticsEventName.RECOMMENDATION_ACCEPTED, accepted.name)
        assertEquals(AnalyticsEventName.RECOMMENDATION_DISMISSED, dismissed.name)
        assertEquals("home_recommendation", shown.properties.surfaceId)
        assertEquals("M0_T2:1", accepted.properties.caseVersionId)
        assertEquals("limits", dismissed.properties.skillId)
        assertTrue(shown.validate())
        assertTrue(accepted.validate())
        assertTrue(dismissed.validate())
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(shown.clientEventId))
    }

    @Test
    fun billing_outcomes_map_to_safe_conversion_actions_and_error_codes() {
        assertEquals(
            "purchase_completed",
            billingAnalyticsAction(
                BillingOperation.PURCHASE,
                BillingOutcome.Access(PremiumAccess.UNLOCKED),
            ),
        )
        assertEquals(
            "restore_pending",
            billingAnalyticsAction(
                BillingOperation.RESTORE,
                BillingOutcome.Pending(BillingOperation.RESTORE, "pending"),
            ),
        )
        assertEquals(
            "purchase_cancelled",
            billingAnalyticsAction(BillingOperation.PURCHASE, BillingOutcome.Cancelled),
        )
        assertEquals(
            "restore_failed",
            billingAnalyticsAction(
                BillingOperation.RESTORE,
                BillingOutcome.Failure(BillingOperation.RESTORE, "provider detail is not logged"),
            ),
        )
        assertEquals(
            null,
            billingAnalyticsAction(
                BillingOperation.LOAD_OFFER,
                BillingOutcome.OfferAvailable(BillingOffer("monthly", "Monthly", "$1.00")),
            ),
        )
        assertEquals(
            "BILLING_LOAD_OFFER",
            billingAnalyticsErrorCode(
                BillingOperation.LOAD_OFFER,
                BillingOutcome.Failure(BillingOperation.LOAD_OFFER, "provider detail is not logged"),
            ),
        )
        assertEquals(
            null,
            billingAnalyticsErrorCode(
                BillingOperation.PURCHASE,
                BillingOutcome.Access(PremiumAccess.UNLOCKED),
            ),
        )
    }

    @Test
    fun funnel_premium_and_error_events_are_contract_safe_without_user_content() {
        val started = practiceStartedAnalyticsEvent(
            attemptId = "123e4567-e89b-42d3-a456-426614174001",
            caseVersionId = "evidrilo-m0-t2-v1",
        )
        val paywall = paywallViewedAnalyticsEvent(surfaceId = "premium")
        val premium = premiumActionAnalyticsEvent(
            action = "purchase_started",
            productId = "monthly",
        )
        val error = clientErrorAnalyticsEvent(
            errorCode = "BILLING_UNAVAILABLE",
            surfaceId = "premium",
        )

        assertEquals(AnalyticsEventName.PRACTICE_STARTED, started.name)
        assertEquals(AnalyticsEventName.PAYWALL_VIEWED, paywall.name)
        assertEquals(AnalyticsEventName.PREMIUM_ACTION, premium.name)
        assertEquals(AnalyticsEventName.CLIENT_ERROR, error.name)
        assertEquals("monthly", premium.properties.productId)
        assertEquals("BILLING_UNAVAILABLE", error.properties.errorCode)
        assertTrue(started.validate())
        assertTrue(paywall.validate())
        assertTrue(premium.validate())
        assertTrue(error.validate())
    }

    @Test
    fun generated_event_ids_are_v4_and_completion_events_are_contract_safe() {
        val event = attemptCompletedAnalyticsEvent(
            attemptId = "123e4567-e89b-42d3-a456-426614174001",
            caseVersionId = "evidrilo-m0-t2-v1",
            evaluation = ConclusionEvaluation(checks = emptyList()),
        )

        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(event.clientEventId))
        assertEquals("PASS", event.properties.outcome)
        assertTrue(event.validate())
    }

    @Test
    fun action_required_evaluations_remain_explicit_and_revision_tracks_change() {
        val evaluation = ConclusionEvaluation(
            checks = emptyList(),
            primaryFeedback = dev.nextgen.mobile.domain.conclusion.ConclusionFeedbackItem(
                code = "ACTION",
                status = ConclusionStatus.ACTION_REQUIRED,
                priority = dev.nextgen.mobile.domain.conclusion.ConclusionPriority.P1,
                field = dev.nextgen.mobile.domain.conclusion.ConclusionField.CLAIM_TEXT,
                anchorIds = emptyList(),
                message = "Revise the claim.",
                why = "The claim needs a bound.",
                nextAction = "Revise it.",
            ),
        )
        val initial = dev.nextgen.mobile.domain.conclusion.ConclusionDraft(caseId = "case")
        val revised = initial.copy(claimText = "A bounded claim.")

        assertEquals(
            "ACTION_REQUIRED",
            attemptCompletedAnalyticsEvent("123e4567-e89b-42d3-a456-426614174001", "case:v1", evaluation)
                .properties.outcome,
        )
        assertEquals(
            true,
            revisionRecordedAnalyticsEvent(
                "123e4567-e89b-42d3-a456-426614174001",
                "case:v1",
                initial,
                revised,
            ).properties.revisionChanged,
        )
    }
}
