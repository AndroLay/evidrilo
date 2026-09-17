package dev.nextgen.mobile.analytics

import kotlin.time.Clock
import kotlin.time.Instant

enum class AnalyticsConsent {
    GRANTED,
    NOT_GRANTED,
}

enum class AnalyticsEventName(
    val wireName: String,
) {
    PRACTICE_STARTED("practice_started"),
    ATTEMPT_COMPLETED("attempt_completed"),
    REVISION_RECORDED("revision_recorded"),
    PAYWALL_VIEWED("paywall_viewed"),
    PREMIUM_ACTION("premium_action"),
    CLIENT_ERROR("client_error"),
    RECOMMENDATION_SHOWN("recommendation_shown"),
    RECOMMENDATION_ACCEPTED("recommendation_accepted"),
    RECOMMENDATION_DISMISSED("recommendation_dismissed"),
}

data class AnalyticsEventProperties(
    val attemptId: String? = null,
    val caseVersionId: String? = null,
    val outcome: String? = null,
    val skillId: String? = null,
    val revisionChanged: Boolean? = null,
    val surfaceId: String? = null,
    val action: String? = null,
    val productId: String? = null,
    val errorCode: String? = null,
)

data class AnalyticsEvent(
    val clientEventId: String,
    val name: AnalyticsEventName,
    val occurredAt: String = Clock.System.now().toString(),
    val properties: AnalyticsEventProperties = AnalyticsEventProperties(),
)

fun AnalyticsEvent.validate(): Boolean {
    if (!analyticsUuidPattern.matches(clientEventId) || occurredAt.isBlank() || occurredAt.length > 64 ||
        runCatching { Instant.parse(occurredAt) }.isFailure
    ) {
        return false
    }
    val properties = properties
    if (properties.attemptId != null && !analyticsUuidPattern.matches(properties.attemptId)) return false
    if (properties.caseVersionId != null && !analyticsIdentifierPattern.matches(properties.caseVersionId)) return false
    if (properties.skillId != null && !analyticsIdentifierPattern.matches(properties.skillId)) return false
    if (properties.surfaceId != null && !analyticsIdentifierPattern.matches(properties.surfaceId)) return false
    if (properties.action != null && !analyticsActionPattern.matches(properties.action)) return false
    if (properties.productId != null && properties.productId !in ANALYTICS_PRODUCTS) return false
    if (properties.errorCode != null && !analyticsErrorCodePattern.matches(properties.errorCode)) return false
    if (properties.outcome != null && properties.outcome !in ANALYTICS_OUTCOMES) return false
    return when (name) {
        AnalyticsEventName.PRACTICE_STARTED ->
            properties.attemptId != null && properties.caseVersionId != null

        AnalyticsEventName.ATTEMPT_COMPLETED ->
            properties.attemptId != null && properties.caseVersionId != null && properties.outcome != null

        AnalyticsEventName.REVISION_RECORDED ->
            properties.attemptId != null && properties.caseVersionId != null && properties.revisionChanged != null

        AnalyticsEventName.PAYWALL_VIEWED -> properties.surfaceId != null

        AnalyticsEventName.PREMIUM_ACTION ->
            properties.action != null && properties.action in ANALYTICS_PREMIUM_ACTIONS

        AnalyticsEventName.CLIENT_ERROR -> properties.errorCode != null

        AnalyticsEventName.RECOMMENDATION_SHOWN,
        AnalyticsEventName.RECOMMENDATION_ACCEPTED,
        AnalyticsEventName.RECOMMENDATION_DISMISSED,
        -> properties.caseVersionId != null
    }
}

private val analyticsUuidPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
)
private val analyticsIdentifierPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
private val analyticsActionPattern = Regex("^[a-z][a-z0-9_]{2,63}$")
private val analyticsErrorCodePattern = Regex("^[A-Z][A-Z0-9_]{2,63}$")
private val ANALYTICS_OUTCOMES = setOf("PASS", "ACTION_REQUIRED", "INCOMPLETE", "CANNOT_ASSESS")
private val ANALYTICS_PRODUCTS = setOf("monthly", "yearly")
private val ANALYTICS_PREMIUM_ACTIONS = setOf(
    "purchase_started",
    "purchase_completed",
    "purchase_cancelled",
    "purchase_failed",
    "purchase_pending",
    "purchase_unknown",
    "restore_started",
    "restore_completed",
    "restore_cancelled",
    "restore_failed",
    "restore_pending",
    "restore_unknown",
)
