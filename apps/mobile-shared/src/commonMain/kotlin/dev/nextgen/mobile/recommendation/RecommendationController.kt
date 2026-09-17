package dev.nextgen.mobile.recommendation

import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.analytics.AnalyticsEvent
import dev.nextgen.mobile.analytics.newAnalyticsEventId
import dev.nextgen.mobile.analytics.recommendationAcceptedAnalyticsEvent
import dev.nextgen.mobile.analytics.recommendationDismissedAnalyticsEvent
import dev.nextgen.mobile.analytics.recommendationShownAnalyticsEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import kotlin.coroutines.cancellation.CancellationException

internal data class RecommendationLifecycleKey(
    val accountId: String,
    val sessionGeneration: Long,
    val consentGeneration: Long,
)

internal sealed interface RecommendationUiState {
    data object Hidden : RecommendationUiState

    data object Loading : RecommendationUiState

    data class Available(
        val recommendation: RecommendationPayload,
        val target: RecommendationLaunchTarget,
        val interactionId: String,
    ) : RecommendationUiState

    data object ActionInProgress : RecommendationUiState

    data object Abstained : RecommendationUiState

    data class Unsupported(val caseVersionId: String) : RecommendationUiState

    data object Expired : RecommendationUiState

    data class Unavailable(val code: String) : RecommendationUiState

    data object Rejected : RecommendationUiState
}

internal class RecommendationController(
    private val gateway: RecommendationGateway,
    private val registry: RecommendationCaseRegistry,
    private val emitAnalytics: (AnalyticsEvent) -> Unit,
    private val onStateChanged: (RecommendationUiState) -> Unit,
    private val newInteractionId: () -> String = ::newAnalyticsEventId,
    private val wait: suspend (Long) -> Unit = ::defaultRecommendationControllerWait,
) {
    private var activeKey: RecommendationLifecycleKey? = null
    private var loadedKey: RecommendationLifecycleKey? = null
    private var activeConsent: AnalyticsConsent = AnalyticsConsent.NOT_GRANTED
    private var requestInFlight = false
    private var requestToken = 0L
    private var state: RecommendationUiState = RecommendationUiState.Hidden

    suspend fun load(
        key: RecommendationLifecycleKey,
        consent: AnalyticsConsent,
        canDisplay: Boolean,
    ) {
        if (!canDisplay || consent != AnalyticsConsent.GRANTED) {
            invalidate()
            return
        }
        if (activeKey == key && (requestInFlight || loadedKey == key)) return

        activeKey = key
        activeConsent = consent
        loadedKey = null
        requestInFlight = true
        val token = ++requestToken
        publish(RecommendationUiState.Loading)

        val result = try {
            gateway.nextWithRetry(consent, wait)
        } catch (cancellation: CancellationException) {
            if (isCurrent(key, token)) {
                requestInFlight = false
                activeKey = null
                loadedKey = null
                publish(RecommendationUiState.Hidden)
            }
            throw cancellation
        }
        if (!isCurrent(key, token)) return
        requestInFlight = false
        loadedKey = key
        handleResult(result, key, token, consent)
    }

    suspend fun reload(
        key: RecommendationLifecycleKey,
        consent: AnalyticsConsent,
        canDisplay: Boolean,
    ) {
        if (!canDisplay || consent != AnalyticsConsent.GRANTED) {
            invalidate()
            return
        }
        if (activeKey == key) {
            loadedKey = null
            requestInFlight = false
            requestToken += 1
            publish(RecommendationUiState.Hidden)
        } else {
            activeKey = null
        }
        load(key, consent, canDisplay = true)
    }

    suspend fun accept(onLaunch: (ConclusionCase) -> Unit) {
        val available = state as? RecommendationUiState.Available ?: return
        val key = activeKey ?: return
        if (requestInFlight) return

        val token = requestToken
        val interactionId = newInteractionId()
        requestInFlight = true
        publish(RecommendationUiState.ActionInProgress)
        try {
            gateway.interactWithRetry(
                recommendation = available.recommendation,
                interaction = RecommendationInteraction.ACCEPTED,
                clientEventId = interactionId,
                consent = activeConsent,
                wait = wait,
            )
        } catch (cancellation: CancellationException) {
            if (isCurrent(key, token)) {
                requestInFlight = false
                loadedKey = key
                publish(
                    RecommendationUiState.Available(
                        recommendation = available.recommendation,
                        target = available.target,
                        interactionId = available.interactionId,
                    ),
                )
            }
            throw cancellation
        } catch (_: Exception) {
            // The local launch and typed analytics event remain useful when the
            // interaction write is temporarily unavailable.
        }
        if (!isCurrent(key, token)) return
        emitAnalytics(
            recommendationAcceptedAnalyticsEvent(
                caseVersionId = available.recommendation.caseVersionId ?: return,
                skillId = null,
            ),
        )
        requestInFlight = false
        loadedKey = key
        publish(RecommendationUiState.Hidden)
        onLaunch(available.target.localCase)
    }

    suspend fun dismiss() {
        val available = state as? RecommendationUiState.Available ?: return
        val key = activeKey ?: return
        if (requestInFlight) return

        val token = requestToken
        val interactionId = newInteractionId()
        requestInFlight = true
        loadedKey = key
        publish(RecommendationUiState.Hidden)
        emitAnalytics(
            recommendationDismissedAnalyticsEvent(
                caseVersionId = available.recommendation.caseVersionId ?: return,
                skillId = null,
            ),
        )
        try {
            gateway.interactWithRetry(
                recommendation = available.recommendation,
                interaction = RecommendationInteraction.DISMISSED,
                clientEventId = interactionId,
                consent = activeConsent,
                wait = wait,
            )
        } catch (cancellation: CancellationException) {
            if (isCurrent(key, token)) {
                requestInFlight = false
                loadedKey = key
                publish(
                    RecommendationUiState.Available(
                        recommendation = available.recommendation,
                        target = available.target,
                        interactionId = available.interactionId,
                    ),
                )
            }
            throw cancellation
        } catch (_: Exception) {
            // Dismissal is local-first; a failed remote interaction is safe to retry later.
        }
        if (isCurrent(key, token)) requestInFlight = false
    }

    fun invalidate() {
        requestToken += 1
        activeKey = null
        loadedKey = null
        activeConsent = AnalyticsConsent.NOT_GRANTED
        requestInFlight = false
        publish(RecommendationUiState.Hidden)
    }

    private suspend fun handleResult(
        result: RecommendationGatewayResult,
        key: RecommendationLifecycleKey,
        token: Long,
        consent: AnalyticsConsent,
    ) {
        when (result) {
            is RecommendationGatewayResult.Found -> {
                val target = registry.resolve(result.recommendation)
                if (target == null) {
                    publish(
                        RecommendationUiState.Unsupported(
                            result.recommendation.caseVersionId ?: "unsupported",
                        ),
                    )
                    return
                }
                if (result.recommendation.toCardPresentation() == null) {
                    publish(RecommendationUiState.Rejected)
                    return
                }
                val interactionId = newInteractionId()
                // Keep the card visible for accessibility, but keep actions
                // locked until the best-effort `shown` write has finished. A
                // concurrent accept/dismiss could otherwise race the same
                // recommendation lifecycle with two interaction writes.
                requestInFlight = true
                publish(
                    RecommendationUiState.Available(
                        recommendation = result.recommendation,
                        target = target,
                        interactionId = interactionId,
                    ),
                )
                // Publishing invokes the screen callback synchronously. That
                // callback may invalidate this lifecycle (for example after a
                // consent or account change), so never write analytics or
                // interaction data for a key that is no longer current.
                if (!isCurrent(key, token)) return
                try {
                    val caseVersionId = result.recommendation.caseVersionId
                    if (caseVersionId == null) {
                        if (isCurrent(key, token)) publish(RecommendationUiState.Rejected)
                        return
                    }
                    emitAnalytics(
                        recommendationShownAnalyticsEvent(
                            caseVersionId = caseVersionId,
                            skillId = null,
                        ),
                    )
                    gateway.interactWithRetry(
                        recommendation = result.recommendation,
                        interaction = RecommendationInteraction.SHOWN,
                        clientEventId = interactionId,
                        consent = consent,
                        wait = wait,
                    )
                } catch (cancellation: CancellationException) {
                    if (isCurrent(key, token)) {
                        requestInFlight = false
                        loadedKey = key
                        publish(
                            RecommendationUiState.Available(
                                recommendation = result.recommendation,
                                target = target,
                                interactionId = interactionId,
                            ),
                        )
                    }
                    throw cancellation
                } catch (_: Exception) {
                    // Showing is best effort and must not hide a usable local card.
                } finally {
                    if (isCurrent(key, token)) {
                        requestInFlight = false
                        loadedKey = key
                    }
                }
            }

            RecommendationGatewayResult.Abstained -> publish(RecommendationUiState.Abstained)

            is RecommendationGatewayResult.Deferred -> publish(result.reason.toUiState())

            is RecommendationGatewayResult.Failed -> publish(
                if (result.retryable) {
                    RecommendationUiState.Unavailable(result.code.safeRecommendationCode())
                } else {
                    RecommendationUiState.Rejected
                },
            )
        }
    }

    private fun isCurrent(key: RecommendationLifecycleKey, token: Long): Boolean =
        activeKey == key && requestToken == token

    private fun publish(next: RecommendationUiState) {
        state = next
        onStateChanged(next)
    }
}

private fun RecommendationDeferralReason.toUiState(): RecommendationUiState = when (this) {
    RecommendationDeferralReason.SESSION_EXPIRED -> RecommendationUiState.Expired
    RecommendationDeferralReason.AUTH_REQUIRED,
    RecommendationDeferralReason.CONSENT_REQUIRED,
    -> RecommendationUiState.Hidden
    RecommendationDeferralReason.SECURE_STORAGE -> RecommendationUiState.Unavailable("SECURE_STORAGE")
    RecommendationDeferralReason.NOT_CONFIGURED -> RecommendationUiState.Unavailable("NOT_CONFIGURED")
}

private fun String.safeRecommendationCode(): String =
    takeIf { it.matches(Regex("^[A-Z][A-Z0-9_]{2,63}$")) } ?: "RECOMMENDATION_UNAVAILABLE"

private suspend fun defaultRecommendationControllerWait(delayMillis: Long) {
    kotlinx.coroutines.delay(delayMillis)
}
