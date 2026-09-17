package dev.nextgen.mobile.analytics

import dev.nextgen.mobile.account.secureRandomBytes
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import kotlin.time.Clock

internal fun newAnalyticsEventId(): String {
    val bytes = secureRandomBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = bytes.joinToString(separator = "") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return listOf(
        hex.substring(0, 8),
        hex.substring(8, 12),
        hex.substring(12, 16),
        hex.substring(16, 20),
        hex.substring(20),
    ).joinToString("-")
}

internal fun practiceStartedAnalyticsEvent(
    attemptId: String,
    caseVersionId: String,
): AnalyticsEvent = AnalyticsEvent(
    clientEventId = newAnalyticsEventId(),
    name = AnalyticsEventName.PRACTICE_STARTED,
    occurredAt = Clock.System.now().toString(),
    properties = AnalyticsEventProperties(
        attemptId = attemptId,
        caseVersionId = caseVersionId,
    ),
)

internal fun paywallViewedAnalyticsEvent(surfaceId: String): AnalyticsEvent = AnalyticsEvent(
    clientEventId = newAnalyticsEventId(),
    name = AnalyticsEventName.PAYWALL_VIEWED,
    occurredAt = Clock.System.now().toString(),
    properties = AnalyticsEventProperties(surfaceId = surfaceId),
)

internal fun premiumActionAnalyticsEvent(
    action: String,
    productId: String? = null,
): AnalyticsEvent = AnalyticsEvent(
    clientEventId = newAnalyticsEventId(),
    name = AnalyticsEventName.PREMIUM_ACTION,
    occurredAt = Clock.System.now().toString(),
    properties = AnalyticsEventProperties(
        action = action,
        productId = productId,
    ),
)

internal fun clientErrorAnalyticsEvent(
    errorCode: String,
    surfaceId: String? = null,
): AnalyticsEvent = AnalyticsEvent(
    clientEventId = newAnalyticsEventId(),
    name = AnalyticsEventName.CLIENT_ERROR,
    occurredAt = Clock.System.now().toString(),
    properties = AnalyticsEventProperties(
        surfaceId = surfaceId,
        errorCode = errorCode,
    ),
)

internal fun attemptCompletedAnalyticsEvent(
    attemptId: String,
    caseVersionId: String,
    evaluation: ConclusionEvaluation,
): AnalyticsEvent = AnalyticsEvent(
    clientEventId = newAnalyticsEventId(),
    name = AnalyticsEventName.ATTEMPT_COMPLETED,
    occurredAt = Clock.System.now().toString(),
    properties = AnalyticsEventProperties(
        attemptId = attemptId,
        caseVersionId = caseVersionId,
        outcome = evaluation.analyticsOutcome(),
    ),
)

internal fun revisionRecordedAnalyticsEvent(
    attemptId: String,
    caseVersionId: String,
    initialDraft: ConclusionDraft,
    revisedDraft: ConclusionDraft,
): AnalyticsEvent = AnalyticsEvent(
    clientEventId = newAnalyticsEventId(),
    name = AnalyticsEventName.REVISION_RECORDED,
    occurredAt = Clock.System.now().toString(),
    properties = AnalyticsEventProperties(
        attemptId = attemptId,
        caseVersionId = caseVersionId,
        revisionChanged = initialDraft != revisedDraft,
    ),
)

private fun ConclusionEvaluation.analyticsOutcome(): String =
    primaryFeedback?.status?.wireName() ?: "PASS"

private fun ConclusionStatus.wireName(): String = when (this) {
    ConclusionStatus.INCOMPLETE -> "INCOMPLETE"
    ConclusionStatus.ACTION_REQUIRED -> "ACTION_REQUIRED"
    ConclusionStatus.PASS -> "PASS"
    ConclusionStatus.CANNOT_ASSESS -> "CANNOT_ASSESS"
}

internal fun recommendationShownAnalyticsEvent(
    caseVersionId: String,
    skillId: String?,
): AnalyticsEvent = recommendationAnalyticsEvent(
    name = AnalyticsEventName.RECOMMENDATION_SHOWN,
    caseVersionId = caseVersionId,
    skillId = skillId,
)

internal fun recommendationAcceptedAnalyticsEvent(
    caseVersionId: String,
    skillId: String?,
): AnalyticsEvent = recommendationAnalyticsEvent(
    name = AnalyticsEventName.RECOMMENDATION_ACCEPTED,
    caseVersionId = caseVersionId,
    skillId = skillId,
)

internal fun recommendationDismissedAnalyticsEvent(
    caseVersionId: String,
    skillId: String?,
): AnalyticsEvent = recommendationAnalyticsEvent(
    name = AnalyticsEventName.RECOMMENDATION_DISMISSED,
    caseVersionId = caseVersionId,
    skillId = skillId,
)

private fun recommendationAnalyticsEvent(
    name: AnalyticsEventName,
    caseVersionId: String,
    skillId: String?,
): AnalyticsEvent = AnalyticsEvent(
    clientEventId = newAnalyticsEventId(),
    name = name,
    occurredAt = Clock.System.now().toString(),
    properties = AnalyticsEventProperties(
        caseVersionId = caseVersionId,
        skillId = skillId,
        surfaceId = "home_recommendation",
    ),
)
