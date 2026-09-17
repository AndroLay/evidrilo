package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.nextgen.mobile.account.AccountGatewayResult
import dev.nextgen.mobile.account.AccountSession
import dev.nextgen.mobile.account.AccountSessionController
import dev.nextgen.mobile.account.EvidriloAccountScreen
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountGateway
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.account.subscribeAccountAuthRedirect
import dev.nextgen.mobile.account.toSettingsSubtitle
import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.analytics.AnalyticsEvent
import dev.nextgen.mobile.analytics.attemptCompletedAnalyticsEvent
import dev.nextgen.mobile.analytics.billingAnalyticsAction
import dev.nextgen.mobile.analytics.billingAnalyticsErrorCode
import dev.nextgen.mobile.analytics.clientErrorAnalyticsEvent
import dev.nextgen.mobile.analytics.createAnalyticsConsentStore
import dev.nextgen.mobile.analytics.createPlatformAnalyticsGateway
import dev.nextgen.mobile.analytics.newAnalyticsEventId
import dev.nextgen.mobile.analytics.paywallViewedAnalyticsEvent
import dev.nextgen.mobile.analytics.premiumActionAnalyticsEvent
import dev.nextgen.mobile.analytics.practiceStartedAnalyticsEvent
import dev.nextgen.mobile.analytics.revisionRecordedAnalyticsEvent
import dev.nextgen.mobile.audio.AudioCoordinator
import dev.nextgen.mobile.audio.EVIDRILO_AUDIO_CATALOG
import dev.nextgen.mobile.audio.AudioEffectId
import dev.nextgen.mobile.audio.AudioNarrationCopy
import dev.nextgen.mobile.audio.AudioNarrationId
import dev.nextgen.mobile.audio.AudioPlaybackState
import dev.nextgen.mobile.audio.AudioSettings
import dev.nextgen.mobile.audio.EvidriloAudioListenControl
import dev.nextgen.mobile.billing.BillingGateway
import dev.nextgen.mobile.billing.BillingOperation
import dev.nextgen.mobile.billing.BillingOutcome
import dev.nextgen.mobile.billing.BillingPresentation
import dev.nextgen.mobile.billing.BillingRequestGate
import dev.nextgen.mobile.billing.PremiumPracticeEvent
import dev.nextgen.mobile.billing.PremiumPracticeReducer
import dev.nextgen.mobile.billing.PremiumPracticeState
import dev.nextgen.mobile.billing.RevenueCatCustomerCenter
import dev.nextgen.mobile.billing.RevenueCatManagedPaywall
import dev.nextgen.mobile.billing.createRevenueCatUiAvailability
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionCheckResult
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
import dev.nextgen.mobile.domain.conclusion.ConclusionEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionFeedbackItem
import dev.nextgen.mobile.domain.conclusion.ConclusionField
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionReducer
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus
import dev.nextgen.mobile.storage.ConclusionSessionPhase
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.ConclusionSessionStore
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import dev.nextgen.mobile.storage.LocalStorageStatus
import dev.nextgen.mobile.storage.createConclusionSessionStore
import dev.nextgen.mobile.storage.createConclusionHistoryStore
import dev.nextgen.mobile.storage.createOnboardingStore
import dev.nextgen.mobile.storage.recoverCorruptLocalStorage
import dev.nextgen.mobile.storage.storageNoticeFor
import dev.nextgen.mobile.audio.createAudioSettingsStore
import dev.nextgen.mobile.audio.createPlatformAudioEngine
import dev.nextgen.mobile.security.SecureSessionStoreFactory
import dev.nextgen.mobile.navigation.EvidriloDestination
import dev.nextgen.mobile.navigation.EvidriloNavigationState
import dev.nextgen.mobile.recommendation.RecommendationCaseRegistry
import dev.nextgen.mobile.recommendation.RecommendationClientConfiguration
import dev.nextgen.mobile.recommendation.RecommendationController
import dev.nextgen.mobile.recommendation.RecommendationGateway
import dev.nextgen.mobile.recommendation.RecommendationLifecycleKey
import dev.nextgen.mobile.recommendation.RecommendationUiState
import dev.nextgen.mobile.sync.SyncConsent
import dev.nextgen.mobile.sync.SyncGatewayResult
import dev.nextgen.mobile.sync.SyncQueueFlushResult
import dev.nextgen.mobile.sync.SyncQueuePullResult
import dev.nextgen.mobile.sync.SyncQueueCoordinator
import dev.nextgen.mobile.sync.SyncQueueMutation
import dev.nextgen.mobile.sync.SyncCommandIntent
import dev.nextgen.mobile.sync.SyncRequestGate
import dev.nextgen.mobile.sync.createPlatformSyncGateway
import dev.nextgen.mobile.sync.createSyncConsentStore
import dev.nextgen.mobile.sync.createSyncQueueStore
import dev.nextgen.mobile.sync.syncCommandFor
import dev.nextgen.mobile.sync.syncConsentClearOutcome
import kotlin.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun EvidriloApp(billingGateway: BillingGateway) {
    val reducer = remember { ConclusionReducer() }
    val premiumReducer = remember { PremiumPracticeReducer() }
    val sessionStore = remember { createConclusionSessionStore() }
    val historyStore = remember { createConclusionHistoryStore() }
    val onboardingStore = remember { createOnboardingStore() }
    val audioSettingsStore = remember { createAudioSettingsStore() }
    val initialAudioSettings = remember { audioSettingsStore.load() }
    var audioSettings by remember { mutableStateOf(initialAudioSettings) }
    var audioStorageStatus by remember { mutableStateOf<LocalStorageStatus?>(null) }
    var audioState by remember { mutableStateOf<AudioPlaybackState>(AudioPlaybackState.Idle) }
    val audioCoordinator = remember {
        AudioCoordinator(
            engine = createPlatformAudioEngine(),
            catalog = EVIDRILO_AUDIO_CATALOG,
            initialSettings = initialAudioSettings,
            onStateChanged = { nextState -> audioState = nextState },
        )
    }
    DisposableEffect(audioCoordinator) {
        onDispose { audioCoordinator.close() }
    }
    val analyticsConsentStore = remember { createAnalyticsConsentStore() }
    val syncConsentStore = remember { createSyncConsentStore() }
    val syncQueueStore = remember { createSyncQueueStore() }
    val syncGateway = remember { createPlatformSyncGateway() }
    val syncCoordinator = remember { SyncQueueCoordinator(syncQueueStore, syncGateway) }
    val syncRequestGate = remember { SyncRequestGate() }
    val accountConfiguration = remember { createAccountClientConfiguration() }
    val accountGateway = remember(accountConfiguration) { createAccountGateway() }
    val secureSessionStore = remember { SecureSessionStoreFactory.create() }
    val analyticsGateway = remember { createPlatformAnalyticsGateway() }
    val accountScope = rememberCoroutineScope()
    var syncJob by remember { mutableStateOf<Job?>(null) }
    val accountController = remember {
        AccountSessionController(
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val recommendationGateway = remember(accountConfiguration, secureSessionStore) {
        RecommendationGateway(
            configuration = RecommendationClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val recommendationRegistry = remember { RecommendationCaseRegistry() }
    val initialSessionLoad = remember {
        recoverCorruptLocalStorage(sessionStore.load()) { sessionStore.clear() }
    }
    val savedSnapshot = initialSessionLoad.value
    val initialOnboardingLoad = remember { onboardingStore.load() }
    var onboardingCompleted by remember(savedSnapshot) {
        mutableStateOf(initialOnboardingLoad.value == true || savedSnapshot != null)
    }
    var onboardingRequested by remember { mutableStateOf(false) }
    var onboardingStorageStatus by remember { mutableStateOf(initialOnboardingLoad.status) }
    val initialAnalyticsConsentLoad = remember { analyticsConsentStore.load() }
    var analyticsConsent by remember {
        mutableStateOf(initialAnalyticsConsentLoad.value ?: AnalyticsConsent.NOT_GRANTED)
    }
    var analyticsConsentStorageStatus by remember { mutableStateOf(initialAnalyticsConsentLoad.status) }
    var recommendationConsentGeneration by remember { mutableStateOf(0L) }
    val initialSyncConsentLoad = remember { syncConsentStore.load() }
    val initialSyncQueueLoad = remember { syncQueueStore.load() }
    var syncConsent by remember {
        mutableStateOf(initialSyncConsentLoad.value ?: SyncConsent.NOT_GRANTED)
    }
    var syncConsentStorageStatus by remember { mutableStateOf(initialSyncConsentLoad.status) }
    var syncStorageStatus by remember { mutableStateOf(initialSyncQueueLoad.status) }
    var syncPendingCount by remember { mutableStateOf(initialSyncQueueLoad.value?.pending?.size ?: 0) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var accountSession by remember { mutableStateOf<AccountSession>(accountController.state) }
    var accountRestoreComplete by remember { mutableStateOf(false) }
    var lastSyncAccountId by remember { mutableStateOf<String?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var recommendationSessionGeneration by remember { mutableStateOf(0L) }
    val currentAccountBusy by rememberUpdatedState(accountBusy)
    fun currentBillingAccountId(): String? =
        (accountSession as? AccountSession.SignedIn)?.account?.accountId
    fun refreshSyncQueueState() {
        val loaded = syncQueueStore.load()
        syncStorageStatus = loaded.status
        syncPendingCount = loaded.value?.pending?.size ?: 0
    }

    fun syncGatewayMessage(result: SyncGatewayResult): String = when (result) {
        is SyncGatewayResult.Deferred -> when (result.reason) {
            dev.nextgen.mobile.sync.SyncDeferralReason.NOT_CONFIGURED ->
                "Cloud sync is not configured in this build; local progress remains safe."
            dev.nextgen.mobile.sync.SyncDeferralReason.AUTH_REQUIRED ->
                "Sign in again before syncing progress."
            dev.nextgen.mobile.sync.SyncDeferralReason.SESSION_EXPIRED ->
                "Your session expired; sign in again before syncing progress."
            dev.nextgen.mobile.sync.SyncDeferralReason.SECURE_STORAGE ->
                "Secure session storage is unavailable; no progress was sent."
        }
        is SyncGatewayResult.Failed -> result.message
        is SyncGatewayResult.PushCompleted,
        is SyncGatewayResult.PullCompleted,
        -> "Progress sync completed."
    }

    fun syncResultMessage(
        pull: SyncQueuePullResult,
        flush: SyncQueueFlushResult,
    ): String = when {
        pull is SyncQueuePullResult.Deferred -> syncGatewayMessage(pull.result)
        pull is SyncQueuePullResult.Failed -> "Progress sync could not advance safely (${pull.code})."
        flush is SyncQueueFlushResult.Deferred -> syncGatewayMessage(flush.result)
        flush is SyncQueueFlushResult.Failed -> "Progress sync could not be saved safely (${flush.code})."
        pull is SyncQueuePullResult.Completed && pull.response.changes.isNotEmpty() ->
            "Progress cursor checked. Remote draft text is never downloaded; drafts stay local."
        else -> "Progress sync checked. Draft text remains on this device."
    }

    fun syncNow() {
        val account = (accountSession as? AccountSession.SignedIn)?.account
        if (account == null) {
            syncStatusMessage = "Sign in before syncing progress."
            return
        }
        if (syncConsent != SyncConsent.GRANTED) {
            syncStatusMessage = "Enable cloud progress sync explicitly before syncing."
            return
        }
        if (syncBusy) return
        val loaded = syncQueueStore.load()
        if (loaded.value == null || loaded.status in setOf(
                LocalStorageStatus.UNAVAILABLE,
                LocalStorageStatus.CORRUPT,
                LocalStorageStatus.FAILED,
            )
        ) {
            syncStorageStatus = loaded.status
            syncStatusMessage = "Local sync storage is unavailable; no progress was sent."
            return
        }
        val requestedConsent = syncConsent
        val requestToken = syncRequestGate.begin(account.accountId, requestedConsent)
        syncBusy = true
        syncJob = accountScope.launch {
            try {
                val syncResult = syncCoordinator.sync(account.accountId, requestedConsent)
                val pull = syncResult.pull
                val flush = syncResult.flush
                if (!syncRequestGate.isCurrent(
                        requestToken,
                        currentBillingAccountId(),
                        syncConsent,
                    )
                ) {
                    return@launch
                }
                refreshSyncQueueState()
                syncStatusMessage = syncResultMessage(pull, flush)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (syncRequestGate.isCurrent(requestToken, currentBillingAccountId(), syncConsent)) {
                    syncStatusMessage = "Progress sync is temporarily unavailable; local data was kept."
                }
            } finally {
                if (syncRequestGate.isCurrent(requestToken, currentBillingAccountId(), syncConsent)) {
                    syncBusy = false
                }
            }
        }
    }

    fun queueSyncCommand(command: SyncCommandIntent) {
        val account = (accountSession as? AccountSession.SignedIn)?.account ?: return
        if (syncConsent != SyncConsent.GRANTED) return
        when (syncCoordinator.enqueue(account.accountId, command)) {
            SyncQueueMutation.ENQUEUED -> {
                refreshSyncQueueState()
                syncNow()
            }
            SyncQueueMutation.DUPLICATE -> syncNow()
            SyncQueueMutation.CONFLICT ->
                syncStatusMessage = "A conflicting local progress event was kept for review."
            SyncQueueMutation.QUEUE_FULL ->
                syncStatusMessage = "The local sync queue is full; local practice remains available."
            SyncQueueMutation.ACCOUNT_MISMATCH ->
                syncStatusMessage = "The queued progress belongs to another account and was not sent."
            SyncQueueMutation.INVALID,
            SyncQueueMutation.STORE_UNAVAILABLE,
            -> syncStatusMessage = "Local sync storage is unavailable; no progress was sent."
        }
    }
    var sessionStorageStatus by remember { mutableStateOf(initialSessionLoad.status) }
    var navigationState by remember(savedSnapshot) {
        mutableStateOf(
            EvidriloNavigationState(
                stack = listOf(
                    if (savedSnapshot == null) EvidriloDestination.HOME else EvidriloDestination.PRACTICE,
                ),
            ),
        )
    }
    var premiumState by remember {
        mutableStateOf<PremiumPracticeState>(PremiumPracticeState.Hidden)
    }
    var premiumBusy by remember { mutableStateOf(false) }
    var premiumRequestId by remember { mutableStateOf(0) }
    var billingIdentityRequestId by remember { mutableStateOf(0) }
    var revenueCatPaywallVisible by remember { mutableStateOf(false) }
    var customerCenterVisible by remember { mutableStateOf(false) }
    val revenueCatUiAvailability = remember { createRevenueCatUiAvailability() }
    val billingRequestGate = remember { BillingRequestGate() }
    LaunchedEffect(accountController) {
        val result = try {
            accountGateway.restore()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AccountGatewayResult.Offline
        }
        accountSession = accountController.acceptGatewayResult(result)
        accountRestoreComplete = true
    }
    DisposableEffect(accountGateway) {
        val unsubscribe = subscribeAccountAuthRedirect { url ->
            if (!currentAccountBusy) {
                accountBusy = true
                accountSession = accountController.beginSignIn()
                accountScope.launch {
                    try {
                        val result = try {
                            accountGateway.completeRedirect(url)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            AccountGatewayResult.Offline
                        }
                        accountSession = accountController.acceptGatewayResult(result)
                    } finally {
                        accountBusy = false
                    }
                }
            }
        }
        onDispose { unsubscribe() }
    }
    fun performAccountOperation(
        showSigningInState: Boolean,
        operation: suspend () -> AccountGatewayResult,
    ) {
        if (!accountBusy) {
            accountBusy = true
            if (showSigningInState) accountSession = accountController.beginSignIn()
            accountScope.launch {
                try {
                    val result = try {
                        operation()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        AccountGatewayResult.Offline
                    }
                    accountSession = accountController.acceptGatewayResult(result)
                } finally {
                    accountBusy = false
                }
            }
        }
    }
    val case = ConclusionCases.M0_T2
    val backendCaseVersionId = requireNotNull(case.remoteCaseVersionId)
    val initialHistoryLoad = remember {
        recoverCorruptLocalStorage(historyStore.load()) { historyStore.clear() }
    }
    var historySnapshot by remember { mutableStateOf(initialHistoryLoad.value) }
    var historyStorageStatus by remember { mutableStateOf(initialHistoryLoad.status) }
    var state by remember(savedSnapshot) {
        mutableStateOf(savedSnapshot?.restore(reducer) ?: ConclusionState.Intro)
    }
    LaunchedEffect(navigationState.current, accountSession) {
        audioCoordinator.stop()
    }
    val playEffect: (AudioEffectId) -> Unit = { id -> audioCoordinator.playEffect(id) }
    var activeAttemptId by remember { mutableStateOf(newAnalyticsEventId()) }
    val clearHistory: () -> Unit = {
        val result = historyStore.clear()
        historyStorageStatus = result.status
        if (result == LocalStorageWriteResult.CLEARED) {
            historySnapshot = null
        }
    }
    val storageNotice = storageNoticeFor(
        sessionStorageStatus,
        historyStorageStatus,
        onboardingStorageStatus,
        analyticsConsentStorageStatus,
        syncConsentStorageStatus,
        syncStorageStatus,
    )
    fun emitAnalytics(event: AnalyticsEvent) {
        if (analyticsConsent == AnalyticsConsent.GRANTED) {
            accountScope.launch {
                analyticsGateway.sendWithRetry(event, analyticsConsent)
            }
        }
    }
    var recommendationState by remember { mutableStateOf<RecommendationUiState>(RecommendationUiState.Hidden) }
    val recommendationController = remember(recommendationGateway, recommendationRegistry) {
        RecommendationController(
            gateway = recommendationGateway,
            registry = recommendationRegistry,
            emitAnalytics = { event -> emitAnalytics(event) },
            onStateChanged = { recommendationState = it },
        )
    }
    fun emitBillingAnalytics(
        operation: BillingOperation,
        outcome: BillingOutcome,
        productId: String? = null,
    ) {
        billingAnalyticsAction(operation, outcome)?.let { action ->
            emitAnalytics(premiumActionAnalyticsEvent(action, productId))
        }
        billingAnalyticsErrorCode(operation, outcome)?.let { errorCode ->
            emitAnalytics(clientErrorAnalyticsEvent(errorCode, surfaceId = "premium"))
        }
    }
    val dispatch: (ConclusionEvent) -> Unit = { event ->
        val currentState = state
        if (event == ConclusionEvent.Reset ||
            event == ConclusionEvent.Submit ||
            event == ConclusionEvent.BeginRevision ||
            event == ConclusionEvent.BeginEvidenceChange ||
            event == ConclusionEvent.SubmitEvidenceChange ||
            event == ConclusionEvent.FinishEvidenceChange
        ) {
            audioCoordinator.stop()
        }
        if (event == ConclusionEvent.Begin && currentState is ConclusionState.Intro) {
            activeAttemptId = newAnalyticsEventId()
        }
        if (event == ConclusionEvent.Reset && currentState is ConclusionState.EvidenceChangeSummary) {
            val result = historyStore.save(currentState.toHistorySnapshot())
            historyStorageStatus = result.status
            if (result == LocalStorageWriteResult.SAVED) {
                historySnapshot = currentState.toHistorySnapshot()
            }
        }
        val nextState = reducer.reduce(currentState, event)
        state = nextState
        when {
            event == ConclusionEvent.Submit && nextState is ConclusionState.Incomplete ->
                playEffect(AudioEffectId.ERROR)
            event == ConclusionEvent.Submit &&
                (nextState is ConclusionState.Feedback || nextState is ConclusionState.Summary) ->
                playEffect(AudioEffectId.SUCCESS)
            event == ConclusionEvent.SubmitEvidenceChange &&
                nextState is ConclusionState.Incomplete ->
                playEffect(AudioEffectId.ERROR)
            event == ConclusionEvent.SubmitEvidenceChange &&
                nextState is ConclusionState.EvidenceChangeFeedback ->
                playEffect(AudioEffectId.SUCCESS)
            event == ConclusionEvent.BeginRevision -> playEffect(AudioEffectId.SELECTION)
            event == ConclusionEvent.BeginEvidenceChange -> playEffect(AudioEffectId.CHALLENGE_REVEAL)
            event == ConclusionEvent.FinishEvidenceChange -> playEffect(AudioEffectId.SUCCESS)
        }
        when {
            event == ConclusionEvent.Begin && currentState is ConclusionState.Intro ->
                emitAnalytics(
                    practiceStartedAnalyticsEvent(
                        attemptId = activeAttemptId,
                        caseVersionId = backendCaseVersionId,
                    ),
                )

            event == ConclusionEvent.Submit &&
                currentState is ConclusionState.Drafting &&
                nextState is ConclusionState.Feedback -> emitAnalytics(
                attemptCompletedAnalyticsEvent(
                    attemptId = activeAttemptId,
                    caseVersionId = backendCaseVersionId,
                    evaluation = nextState.evaluation,
                ),
            )

            event == ConclusionEvent.Submit &&
                currentState is ConclusionState.Drafting &&
                nextState is ConclusionState.Incomplete -> emitAnalytics(
                attemptCompletedAnalyticsEvent(
                    attemptId = activeAttemptId,
                    caseVersionId = backendCaseVersionId,
                    evaluation = ConclusionEvaluation(
                        checks = emptyList(),
                        primaryFeedback = nextState.feedback,
                    ),
                ),
            )

            event == ConclusionEvent.Submit &&
                currentState is ConclusionState.Revision &&
                nextState is ConclusionState.Summary -> emitAnalytics(
                revisionRecordedAnalyticsEvent(
                    attemptId = activeAttemptId,
                    caseVersionId = backendCaseVersionId,
                    initialDraft = nextState.initialDraft,
                    revisedDraft = nextState.revisedDraft,
                ),
            )

            event == ConclusionEvent.FinishEvidenceChange &&
                nextState is ConclusionState.EvidenceChangeSummary -> emitAnalytics(
                attemptCompletedAnalyticsEvent(
                    attemptId = activeAttemptId,
                    caseVersionId = backendCaseVersionId,
                    evaluation = nextState.challengeEvaluation,
                ),
            )
        }
        when {
            event == ConclusionEvent.Begin && currentState is ConclusionState.Intro ->
                queueSyncCommand(
                    syncCommandFor(
                        commandType = dev.nextgen.mobile.sync.SyncCommandType.ATTEMPT_STARTED,
                        attemptId = activeAttemptId,
                        draft = ConclusionDraft(caseId = case.id),
                        revisionNumber = 0,
                        caseVersionId = backendCaseVersionId,
                    ),
                )

            event == ConclusionEvent.Submit &&
                currentState is ConclusionState.Drafting &&
                (nextState is ConclusionState.Feedback || nextState is ConclusionState.Incomplete) ->
                queueSyncCommand(
                    syncCommandFor(
                        commandType = dev.nextgen.mobile.sync.SyncCommandType.ATTEMPT_SUBMITTED,
                        attemptId = activeAttemptId,
                        draft = when (nextState) {
                            is ConclusionState.Feedback -> nextState.draft
                            is ConclusionState.Incomplete -> nextState.draft
                        },
                        revisionNumber = 0,
                        caseVersionId = backendCaseVersionId,
                    ),
                )

            event == ConclusionEvent.Submit &&
                currentState is ConclusionState.Revision &&
                nextState is ConclusionState.Summary ->
                queueSyncCommand(
                    syncCommandFor(
                        commandType = dev.nextgen.mobile.sync.SyncCommandType.REVISION_RECORDED,
                        attemptId = activeAttemptId,
                        draft = nextState.revisedDraft,
                        revisionNumber = 1,
                        caseVersionId = backendCaseVersionId,
                    ),
                )
        }
        sessionStorageStatus = nextState.persist(sessionStore).status
        if (nextState is ConclusionState.Intro) {
            navigationState = navigationState.resetToHome()
        }
        if (nextState is ConclusionState.EvidenceChangeSummary) {
            val result = historyStore.save(nextState.toHistorySnapshot())
            historyStorageStatus = result.status
            if (result == LocalStorageWriteResult.SAVED) {
                historySnapshot = nextState.toHistorySnapshot()
            }
        }
    }
    val signedInAccount = (accountSession as? AccountSession.SignedIn)?.account
    val recommendationLifecycleKey = signedInAccount?.accountId?.let { accountId ->
        RecommendationLifecycleKey(
            accountId = accountId,
            sessionGeneration = recommendationSessionGeneration,
            consentGeneration = recommendationConsentGeneration,
        )
    }
    val recommendationCanDisplay = navigationState.current == EvidriloDestination.HOME &&
        state is ConclusionState.Intro &&
        signedInAccount?.emailVerified == true &&
        analyticsConsent == AnalyticsConsent.GRANTED
    LaunchedEffect(
        recommendationLifecycleKey,
        recommendationCanDisplay,
        navigationState.current,
        state,
    ) {
        val key = recommendationLifecycleKey
        if (recommendationCanDisplay && key != null) {
            recommendationController.load(key, AnalyticsConsent.GRANTED, canDisplay = true)
        } else {
            recommendationController.invalidate()
        }
    }
    DisposableEffect(recommendationController) {
        onDispose { recommendationController.invalidate() }
    }
    fun acceptRecommendation() {
        if (!recommendationCanDisplay) return
        accountScope.launch {
            recommendationController.accept { suggestedCase ->
                if (state is ConclusionState.Intro && suggestedCase.id == case.id) {
                    dispatch(ConclusionEvent.Begin)
                    navigationState = navigationState.open(EvidriloDestination.PRACTICE)
                }
            }
        }
    }
    fun dismissRecommendation() {
        accountScope.launch { recommendationController.dismiss() }
    }
    fun retryRecommendation() {
        val key = recommendationLifecycleKey ?: return
        if (!recommendationCanDisplay) return
        accountScope.launch {
            recommendationController.reload(key, AnalyticsConsent.GRANTED, canDisplay = true)
        }
    }
    val completeOnboarding: () -> Unit = {
        onboardingStorageStatus = onboardingStore.complete().status
        onboardingCompleted = true
        onboardingRequested = false
    }
    val dispatchPremium: (PremiumPracticeEvent) -> Unit = { event ->
        when {
            event is PremiumPracticeEvent.SelectOffer ->
                audioCoordinator.playEffect(AudioEffectId.PREMIUM_STATE)
            event is PremiumPracticeEvent.SelectCase ->
                audioCoordinator.playEffect(AudioEffectId.SELECTION)
            event == PremiumPracticeEvent.BeginSelectedCase ->
                audioCoordinator.playEffect(AudioEffectId.SELECTION)
            event is PremiumPracticeEvent.PracticeEvent &&
                event.event == ConclusionEvent.Submit ->
                audioCoordinator.playEffect(AudioEffectId.SUCCESS)
            event == PremiumPracticeEvent.Back || event == PremiumPracticeEvent.Close ->
                audioCoordinator.stop()
        }
        premiumState = premiumReducer.reduce(premiumState, event)
    }
    fun refreshBillingAccessAfterManagedUi() {
        val requestToken = billingRequestGate.begin(currentBillingAccountId())
        billingGateway.refreshAccess { outcome ->
            if (billingRequestGate.isCurrent(requestToken, currentBillingAccountId())) {
                emitBillingAnalytics(BillingOperation.REFRESH_ACCESS, outcome)
                dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
            }
        }
    }
    val closeManagedBillingUi: () -> Unit = {
        revenueCatPaywallVisible = false
        customerCenterVisible = false
        refreshBillingAccessAfterManagedUi()
    }
    val openManagedPaywall: () -> Unit = {
        emitAnalytics(paywallViewedAnalyticsEvent(surfaceId = "revenuecat_paywall"))
        revenueCatPaywallVisible = true
    }
    val openCustomerCenter: () -> Unit = {
        customerCenterVisible = true
    }
    LaunchedEffect(accountSession, accountRestoreComplete) {
        recommendationSessionGeneration += 1
        billingRequestGate.invalidate()
        syncRequestGate.invalidate()
        syncJob?.cancel()
        syncJob = null
        syncBusy = false
        val requestId = billingIdentityRequestId + 1
        billingIdentityRequestId = requestId
        if (accountRestoreComplete) {
            when (val syncSession = accountSession) {
                is AccountSession.SignedIn -> {
                    val queuedAccountId = syncQueueStore.load().value?.accountId
                    if ((lastSyncAccountId != null && lastSyncAccountId != syncSession.account.accountId) ||
                        (queuedAccountId != null && queuedAccountId != syncSession.account.accountId)
                    ) {
                        syncCoordinator.clear()
                        refreshSyncQueueState()
                        syncStatusMessage = "Previous-account progress was cleared from the local sync queue."
                    }
                    lastSyncAccountId = syncSession.account.accountId
                }

                AccountSession.SigningIn,
                AccountSession.AwaitingOAuthCallback,
                -> {
                    syncCoordinator.clear()
                    refreshSyncQueueState()
                    lastSyncAccountId = null
                }

                AccountSession.SignedOut,
                AccountSession.Expired,
                is AccountSession.PasswordRecovery,
                is AccountSession.Unavailable,
                -> {
                    val hadAccountBoundQueue = syncQueueStore.load().value?.accountId != null
                    syncCoordinator.clear()
                    refreshSyncQueueState()
                    if (hadAccountBoundQueue) {
                        syncStatusMessage = "Signed-out progress was cleared from the local sync queue."
                    }
                    lastSyncAccountId = null
                }
            }
        }
        when (val session = accountSession) {
            is AccountSession.SignedIn -> billingGateway.identifyCustomer(
                appUserId = session.account.accountId,
            ) { outcome ->
                if (requestId == billingIdentityRequestId &&
                    currentBillingAccountId() == session.account.accountId
                ) {
                    dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
                }
            }

            AccountSession.SignedOut,
            AccountSession.Expired,
            is AccountSession.PasswordRecovery,
            is AccountSession.Unavailable,
            -> billingGateway.resetCustomer { outcome ->
                if (requestId == billingIdentityRequestId && currentBillingAccountId() == null) {
                    dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
                }
            }

            AccountSession.SigningIn,
            AccountSession.AwaitingOAuthCallback,
            -> Unit
        }
    }
    LaunchedEffect(accountSession, syncConsent, accountRestoreComplete) {
        syncRequestGate.invalidate()
        syncBusy = false
        if (accountRestoreComplete &&
            accountSession is AccountSession.SignedIn &&
            syncConsent == SyncConsent.GRANTED
        ) {
            syncNow()
        }
    }
    val closePremium: () -> Unit = {
        premiumRequestId += 1
        billingRequestGate.invalidate()
        premiumBusy = false
        dispatchPremium(PremiumPracticeEvent.Close)
    }
    val leavePremium: () -> Unit = {
        closePremium()
        navigationState = navigationState.back()
    }
    val openRootPracticeFromPremium: () -> Unit = {
        closePremium()
        navigationState = navigationState.resetToHome()
    }
    val openRootHistoryFromPremium: () -> Unit = {
        closePremium()
        navigationState = navigationState.resetToHome().open(EvidriloDestination.HISTORY)
    }
    val returnToPremiumCatalog: () -> Unit = {
        dispatchPremium(PremiumPracticeEvent.Back)
    }
    val openPremium: () -> Unit = {
        emitAnalytics(paywallViewedAnalyticsEvent(surfaceId = "premium"))
        navigationState = navigationState.open(EvidriloDestination.PREMIUM)
        dispatchPremium(PremiumPracticeEvent.Open)
        val requestId = premiumRequestId + 1
        premiumRequestId = requestId
        val requestToken = billingRequestGate.begin(currentBillingAccountId())
        premiumBusy = true
        billingGateway.refreshAccess { outcome ->
            if (requestId == premiumRequestId &&
                billingRequestGate.isCurrent(requestToken, currentBillingAccountId())
            ) {
                emitBillingAnalytics(BillingOperation.REFRESH_ACCESS, outcome)
                dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
                billingGateway.loadPracticePackOffer { offerOutcome ->
                    if (requestId == premiumRequestId &&
                        billingRequestGate.isCurrent(requestToken, currentBillingAccountId())
                    ) {
                        emitBillingAnalytics(BillingOperation.LOAD_OFFER, offerOutcome)
                        dispatchPremium(PremiumPracticeEvent.BillingResult(offerOutcome))
                        premiumBusy = false
                    }
                }
            }
        }
    }
    val returnToHome: () -> Unit = {
        // Practice is a root workflow. Returning from any utility surface
        // must not strand the user in that surface's stack entry, and this
        // does not reset the persisted/evaluator state.
        navigationState = navigationState.resetToHome()
    }
    val syncStorageAvailable = syncStorageStatus !in setOf(
        LocalStorageStatus.UNAVAILABLE,
        LocalStorageStatus.CORRUPT,
        LocalStorageStatus.FAILED,
    )
    val setSyncConsent: (SyncConsent) -> Unit = { consent ->
        syncRequestGate.invalidate()
        syncJob?.cancel()
        syncJob = null
        syncBusy = false
        val result = syncConsentStore.save(consent)
        syncConsentStorageStatus = result.status
        if (result == LocalStorageWriteResult.SAVED) {
            syncConsent = consent
            if (consent == SyncConsent.NOT_GRANTED) {
                val clearResult = syncCoordinator.clear()
                syncStorageStatus = clearResult.status
                val pendingCountAfterReload = if (clearResult == LocalStorageWriteResult.CLEARED) {
                    0
                } else {
                    syncQueueStore.load().value?.pending?.size ?: 0
                }
                val clearOutcome = syncConsentClearOutcome(clearResult, pendingCountAfterReload)
                syncPendingCount = clearOutcome.pendingCount
                syncStatusMessage = clearOutcome.message
            } else {
                syncStatusMessage = "Cloud sync enabled for this verified account."
            }
        }
    }

    val setAudioSettings: (AudioSettings) -> Unit = { settings ->
        audioCoordinator.updateSettings(settings)
        audioSettings = settings
        audioStorageStatus = audioSettingsStore.save(settings).status
    }
    val playNarration: (AudioNarrationId, String) -> Unit = { id, sourceText ->
        audioCoordinator.playNarration(id, sourceText)
    }
    val pauseOrResumeAudio: () -> Unit = { audioCoordinator.pauseOrResume() }
    val stopAudio: () -> Unit = { audioCoordinator.stop() }

    val onboardingPresentation = evidriloOnboardingPresentation(
        completed = onboardingCompleted,
        hasSavedPractice = savedSnapshot != null,
        forceShow = onboardingRequested,
    )
    if (onboardingPresentation.isVisible) {
        EvidriloOnboardingScreen(
            presentation = onboardingPresentation,
            onStart = {
                completeOnboarding()
                if (state is ConclusionState.Intro) {
                    dispatch(ConclusionEvent.Begin)
                }
                navigationState = navigationState.open(EvidriloDestination.PRACTICE)
            },
            onSkip = completeOnboarding,
            audioState = audioState,
            onListen = {
                playNarration(
                    AudioNarrationId.ONBOARDING,
                    AudioNarrationCopy.onboarding(onboardingPresentation),
                )
            },
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
        )
    } else if (revenueCatPaywallVisible) {
        RevenueCatManagedPaywall(onDismiss = closeManagedBillingUi)
    } else if (customerCenterVisible) {
        RevenueCatCustomerCenter(onDismiss = closeManagedBillingUi)
    } else if (premiumState !is PremiumPracticeState.Hidden) {
        EvidriloPremiumSurface(
            state = premiumState,
            isBusy = premiumBusy,
            managedPaywallAvailable = revenueCatUiAvailability.canPresent,
            onOpenManagedPaywall = openManagedPaywall,
            onPurchase = {
                val current = premiumState
                if (!premiumBusy && current is PremiumPracticeState.Locked && current.billing.canPurchase) {
                    val requestId = premiumRequestId
                    val requestToken = billingRequestGate.begin(currentBillingAccountId())
                    val productId = current.billing.selectedOffer?.productId
                    premiumBusy = true
                    emitAnalytics(premiumActionAnalyticsEvent("purchase_started", productId))
                    billingGateway.purchasePracticePack(current.billing.selectedOffer?.productId) { outcome ->
                        if (requestId == premiumRequestId &&
                            billingRequestGate.isCurrent(requestToken, currentBillingAccountId())
                        ) {
                            emitBillingAnalytics(BillingOperation.PURCHASE, outcome, productId)
                            dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
                            premiumBusy = false
                        }
                    }
                }
            },
            onRestore = {
                val current = premiumState
                if (!premiumBusy && current is PremiumPracticeState.Locked) {
                    val requestId = premiumRequestId
                    val requestToken = billingRequestGate.begin(currentBillingAccountId())
                    premiumBusy = true
                    emitAnalytics(premiumActionAnalyticsEvent("restore_started"))
                    billingGateway.restorePurchases { outcome ->
                        if (requestId == premiumRequestId &&
                            billingRequestGate.isCurrent(requestToken, currentBillingAccountId())
                        ) {
                            emitBillingAnalytics(BillingOperation.RESTORE, outcome)
                            dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
                            premiumBusy = false
                        }
                    }
                }
            },
            onSelectOffer = { dispatchPremium(PremiumPracticeEvent.SelectOffer(it)) },
            onSelectCase = { dispatchPremium(PremiumPracticeEvent.SelectCase(it)) },
            onBeginCase = { dispatchPremium(PremiumPracticeEvent.BeginSelectedCase) },
            onPracticeEvent = { dispatchPremium(PremiumPracticeEvent.PracticeEvent(it)) },
            onOpenPractice = openRootPracticeFromPremium,
            onOpenHistory = openRootHistoryFromPremium,
            onReturnToCatalog = returnToPremiumCatalog,
            backLabel = when (navigationState.stack.dropLast(1).lastOrNull()) {
                EvidriloDestination.SETTINGS -> "Settings"
                EvidriloDestination.HISTORY -> "History"
                EvidriloDestination.GUIDE -> "Guide"
                EvidriloDestination.PRACTICE -> "Practice"
                else -> "Home"
            },
            onBack = leavePremium,
            audioState = audioState,
            onNarration = playNarration,
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
            onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
        )
    } else if (navigationState.current == EvidriloDestination.ACCOUNT) {
        EvidriloAccountScreen(
            session = accountSession,
            isBusy = accountBusy,
            accountConfigured = accountConfiguration.isConfigured,
            onBack = { navigationState = navigationState.back() },
            onSignIn = { email, password ->
                performAccountOperation(true) { accountGateway.signIn(email, password) }
            },
            onSignUp = { email, password ->
                performAccountOperation(true) { accountGateway.signUp(email, password) }
            },
            onResetPassword = { email ->
                performAccountOperation(true) { accountGateway.requestPasswordReset(email) }
            },
            onGoogleSignIn = {
                performAccountOperation(true) { accountGateway.startGoogleSignIn() }
            },
            onCancelOAuth = {
                performAccountOperation(false) { accountGateway.signOut() }
            },
            onUpdatePassword = { password ->
                performAccountOperation(false) { accountGateway.updatePassword(password) }
            },
            onSignOut = {
                performAccountOperation(false) { accountGateway.signOut() }
            },
            onDeleteAccount = {
                performAccountOperation(false) { accountGateway.deleteAccount() }
            },
        )
    } else if (navigationState.current == EvidriloDestination.HISTORY) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloHistoryScreen(
            history = historySnapshot,
            storageNotice = storageNotice,
            onStartPractice = {
                dispatch(ConclusionEvent.Reset)
                navigationState = navigationState.open(EvidriloDestination.PRACTICE)
                dispatch(ConclusionEvent.Begin)
            },
            onClear = clearHistory,
            onBack = { navigationState = navigationState.back() },
            onOpenPractice = { navigationState = navigationState.back() },
            onOpenPacks = openPremium,
            backLabel = if (previousDestination == EvidriloDestination.SETTINGS) "Settings" else "Home",
        )
    } else if (navigationState.current == EvidriloDestination.GUIDE) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloGuideScreen(
            backLabel = when (previousDestination) {
                EvidriloDestination.SETTINGS -> "Settings"
                EvidriloDestination.PRACTICE -> "Practice"
                else -> "Home"
            },
            onBack = { navigationState = navigationState.back() },
            onOpenPractice = {
                if (state is ConclusionState.Intro) {
                    dispatch(ConclusionEvent.Begin)
                }
                navigationState = navigationState.open(EvidriloDestination.PRACTICE)
            },
            onReplayOnboarding = { onboardingRequested = true },
            audioState = audioState,
            onListen = { playNarration(AudioNarrationId.GUIDE, AudioNarrationCopy.guide()) },
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
        )
    } else if (navigationState.current == EvidriloDestination.SETTINGS) {
        EvidriloSettingsScreen(
            historyAvailable = historySnapshot != null,
            storageNotice = storageNotice,
            analyticsConsent = analyticsConsent,
            onSetAnalyticsConsent = { consent ->
                analyticsConsentStorageStatus = analyticsConsentStore.save(consent).status
                if (analyticsConsent != consent) recommendationConsentGeneration += 1
                analyticsConsent = consent
            },
            syncConsent = syncConsent,
            syncSignedIn = accountSession is AccountSession.SignedIn,
            syncPendingCount = syncPendingCount,
            syncStorageAvailable = syncStorageAvailable,
            syncBusy = syncBusy,
            syncStatusMessage = syncStatusMessage,
            onSetSyncConsent = setSyncConsent,
            onSyncNow = ::syncNow,
            onOpenPremium = openPremium,
            onOpenGuide = { navigationState = navigationState.open(EvidriloDestination.GUIDE) },
            onOpenAccount = { navigationState = navigationState.open(EvidriloDestination.ACCOUNT) },
            onOpenSupport = { navigationState = navigationState.open(EvidriloDestination.SUPPORT) },
            accountSubtitle = accountSession.toSettingsSubtitle(),
            onOpenAbout = { navigationState = navigationState.open(EvidriloDestination.ABOUT) },
            onResetPractice = { dispatch(ConclusionEvent.Reset) },
            onClearHistory = clearHistory,
            onBack = { navigationState = navigationState.back() },
            audioSettings = audioSettings,
            audioStorageStatus = audioStorageStatus,
            onSetAudioSettings = setAudioSettings,
        )
    } else if (navigationState.current == EvidriloDestination.SUPPORT) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloSupportScreen(
            onBack = { navigationState = navigationState.back() },
            onOpenPremium = openPremium,
            onOpenAccount = { navigationState = navigationState.open(EvidriloDestination.ACCOUNT) },
            customerCenterAvailable = revenueCatUiAvailability.canPresent,
            onOpenCustomerCenter = openCustomerCenter,
            backLabel = if (previousDestination == EvidriloDestination.SETTINGS) "Settings" else "Home",
            audioState = audioState,
            onListen = { playNarration(AudioNarrationId.SUPPORT, AudioNarrationCopy.support()) },
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
        )
    } else if (navigationState.current == EvidriloDestination.ABOUT) {
        EvidriloAboutScreen(
            onBack = { navigationState = navigationState.back() },
            backLabel = "Settings",
        )
    } else if (navigationState.current == EvidriloDestination.HOME) {
        EvidriloHomeScreen(
            state = state,
            history = historySnapshot,
            storageNotice = storageNotice,
            onPrimaryAction = {
                if (state is ConclusionState.Intro) {
                    dispatch(ConclusionEvent.Begin)
                }
                navigationState = navigationState.open(EvidriloDestination.PRACTICE)
            },
            onOpenGuide = { navigationState = navigationState.open(EvidriloDestination.GUIDE) },
            onOpenPacks = openPremium,
            onOpenHistory = { navigationState = navigationState.open(EvidriloDestination.HISTORY) },
            onOpenSettings = { navigationState = navigationState.open(EvidriloDestination.SETTINGS) },
            recommendation = recommendationState,
            onAcceptRecommendation = ::acceptRecommendation,
            onDismissRecommendation = ::dismissRecommendation,
            onRetryRecommendation = ::retryRecommendation,
            audioState = audioState,
            onListen = { playNarration(AudioNarrationId.CASE_OBJECTIVE, AudioNarrationCopy.case(case)) },
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
        )
    } else {
        when (val current = state) {
            ConclusionState.Intro -> EvidriloHomeScreen(
                state = current,
                history = historySnapshot,
                storageNotice = storageNotice,
                onPrimaryAction = {
                    if (state is ConclusionState.Intro) {
                        dispatch(ConclusionEvent.Begin)
                    }
                    navigationState = navigationState.open(EvidriloDestination.PRACTICE)
                },
                onOpenGuide = { navigationState = navigationState.open(EvidriloDestination.GUIDE) },
                onOpenPacks = openPremium,
                onOpenHistory = { navigationState = navigationState.open(EvidriloDestination.HISTORY) },
                onOpenSettings = { navigationState = navigationState.open(EvidriloDestination.SETTINGS) },
                recommendation = recommendationState,
                onAcceptRecommendation = ::acceptRecommendation,
                onDismissRecommendation = ::dismissRecommendation,
                onRetryRecommendation = ::retryRecommendation,
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.CASE_OBJECTIVE, AudioNarrationCopy.case(case)) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
            )

            is ConclusionState.Drafting -> EvidriloDraftScreen(
                case = case,
                title = "Build a bounded conclusion",
                draft = current.draft,
                validationMessage = current.validationMessage,
                onDraftChange = { dispatch(ConclusionEvent.UpdateDraft(it)) },
                onSubmit = { dispatch(ConclusionEvent.Submit) },
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.CASE_FACT, AudioNarrationCopy.case(case)) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
                onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
            )

            is ConclusionState.Incomplete -> EvidriloDraftScreen(
                case = case,
                title = "Complete the conclusion",
                draft = current.draft,
                validationMessage = current.feedback.message,
                onDraftChange = { dispatch(ConclusionEvent.UpdateDraft(it)) },
                onSubmit = { dispatch(ConclusionEvent.Submit) },
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                initialStep = current.feedback.field.toDraftStep(),
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.CASE_FACT, AudioNarrationCopy.case(case)) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
                onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
            )

            is ConclusionState.Feedback -> EvidriloFeedbackScreen(
                draft = current.draft,
                evaluation = current.evaluation,
                onRevise = { dispatch(ConclusionEvent.BeginRevision) },
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.FEEDBACK, AudioNarrationCopy.feedback()) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
            )

            is ConclusionState.Revision -> EvidriloDraftScreen(
                case = case,
                title = "Revise once with the feedback",
                draft = current.draft,
                validationMessage = current.validationMessage,
                onDraftChange = { dispatch(ConclusionEvent.UpdateDraft(it)) },
                onSubmit = { dispatch(ConclusionEvent.Submit) },
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                initialStep = EvidriloDraftStep.CLAIM,
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.REVISION, AudioNarrationCopy.revision()) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
                onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
            )

            is ConclusionState.Summary -> EvidriloSummaryScreen(
                initialDraft = current.initialDraft,
                revisedDraft = current.revisedDraft,
                finalEvaluation = current.finalEvaluation,
                onStartChallenge = { dispatch(ConclusionEvent.BeginEvidenceChange) },
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.REVISION, AudioNarrationCopy.revision()) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
            )

            is ConclusionState.EvidenceChangeDrafting -> EvidriloDraftScreen(
                case = ConclusionCases.EVIDENCE_CHANGE,
                title = "Rebuild the conclusion after the evidence change",
                draft = current.draft,
                validationMessage = current.validationMessage,
                onDraftChange = { dispatch(ConclusionEvent.UpdateEvidenceChangeDraft(it)) },
                onSubmit = { dispatch(ConclusionEvent.SubmitEvidenceChange) },
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                initialStep = EvidriloDraftStep.LIMITS,
                audioState = audioState,
                onListen = {
                    playNarration(
                        AudioNarrationId.CHALLENGE,
                        AudioNarrationCopy.case(ConclusionCases.EVIDENCE_CHANGE),
                    )
                },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
                onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
            )

            is ConclusionState.EvidenceChangeFeedback -> EvidriloEvidenceChangeFeedbackScreen(
                baseDraft = current.baseDraft,
                draft = current.draft,
                evaluation = current.evaluation,
                onFinish = { dispatch(ConclusionEvent.FinishEvidenceChange) },
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.CHALLENGE, AudioNarrationCopy.challenge()) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
            )

            is ConclusionState.EvidenceChangeSummary -> EvidriloEvidenceChangeSummaryScreen(
                baseDraft = current.baseDraft,
                challengeDraft = current.challengeDraft,
                challengeEvaluation = current.challengeEvaluation,
                onReset = { dispatch(ConclusionEvent.Reset) },
                onBack = returnToHome,
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.CHALLENGE, AudioNarrationCopy.challenge()) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
            )
        }
    }
}

@Composable
private fun EvidriloPremiumSurface(
    state: PremiumPracticeState,
    isBusy: Boolean,
    managedPaywallAvailable: Boolean,
    onOpenManagedPaywall: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onSelectOffer: (String) -> Unit,
    onSelectCase: (String) -> Unit,
    onBeginCase: () -> Unit,
    onPracticeEvent: (ConclusionEvent) -> Unit,
    onOpenPractice: () -> Unit,
    onOpenHistory: () -> Unit,
    onReturnToCatalog: () -> Unit,
    backLabel: String,
    onBack: () -> Unit,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onNarration: (AudioNarrationId, String) -> Unit = { _, _ -> },
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
    onSelectionSound: () -> Unit = {},
) {
    when (state) {
        PremiumPracticeState.Hidden -> Unit

        is PremiumPracticeState.Locked -> EvidriloRootSurface(
            selected = EvidriloRootDestination.PACKS,
            onPractice = onOpenPractice,
            onPacks = { },
            onHistory = onOpenHistory,
        ) {
            EvidriloPremiumLockedScreen(
                billing = state.billing.copy(isBusy = isBusy),
                managedPaywallAvailable = managedPaywallAvailable,
                onOpenManagedPaywall = onOpenManagedPaywall,
                onPurchase = onPurchase,
                onRestore = onRestore,
                onSelectOffer = onSelectOffer,
                onBack = onBack,
                backLabel = backLabel,
            )
        }

        is PremiumPracticeState.Catalog -> EvidriloRootSurface(
            selected = EvidriloRootDestination.PACKS,
            onPractice = onOpenPractice,
            onPacks = { },
            onHistory = onOpenHistory,
        ) {
            EvidriloPremiumCatalogScreen(
                state = state,
                onSelectCase = onSelectCase,
                onBeginCase = onBeginCase,
                onBack = onBack,
                backLabel = backLabel,
            )
        }

        is PremiumPracticeState.Practice -> when (val current = state.conclusion) {
            ConclusionState.Intro -> EvidriloRootSurface(
                selected = EvidriloRootDestination.PACKS,
                onPractice = onOpenPractice,
                onPacks = { },
                onHistory = onOpenHistory,
            ) {
                EvidriloPremiumCatalogScreen(
                    state = PremiumPracticeState.Catalog(
                        billing = state.billing,
                        cases = state.cases,
                        selectedCaseId = state.case.id,
                    ),
                    onSelectCase = onSelectCase,
                    onBeginCase = { onPracticeEvent(ConclusionEvent.Begin) },
                    onBack = onBack,
                    backLabel = backLabel,
                )
            }

            is ConclusionState.Drafting -> EvidriloDraftScreen(
                case = state.case,
                title = "Premium practice · ${state.case.title}",
                draft = current.draft,
                validationMessage = current.validationMessage,
                onDraftChange = { onPracticeEvent(ConclusionEvent.UpdateDraft(it)) },
                onSubmit = { onPracticeEvent(ConclusionEvent.Submit) },
                onReset = onBack,
                audioState = audioState,
                onListen = { onNarration(AudioNarrationId.CASE_FACT, AudioNarrationCopy.case(state.case)) },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
                onSelectionSound = onSelectionSound,
            )

            is ConclusionState.Incomplete -> EvidriloDraftScreen(
                case = state.case,
                title = "Complete the premium conclusion",
                draft = current.draft,
                validationMessage = current.feedback.message,
                onDraftChange = { onPracticeEvent(ConclusionEvent.UpdateDraft(it)) },
                onSubmit = { onPracticeEvent(ConclusionEvent.Submit) },
                onReset = onBack,
                audioState = audioState,
                onListen = { onNarration(AudioNarrationId.CASE_FACT, AudioNarrationCopy.case(state.case)) },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
                onSelectionSound = onSelectionSound,
            )

            is ConclusionState.Feedback -> EvidriloFeedbackScreen(
                draft = current.draft,
                evaluation = current.evaluation,
                onRevise = { onPracticeEvent(ConclusionEvent.BeginRevision) },
                onReset = onBack,
                audioState = audioState,
                onListen = { onNarration(AudioNarrationId.FEEDBACK, AudioNarrationCopy.feedback()) },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
            )

            is ConclusionState.Revision -> EvidriloDraftScreen(
                case = state.case,
                title = "Revise the premium conclusion once",
                draft = current.draft,
                validationMessage = current.validationMessage,
                onDraftChange = { onPracticeEvent(ConclusionEvent.UpdateDraft(it)) },
                onSubmit = { onPracticeEvent(ConclusionEvent.Submit) },
                onReset = onBack,
                audioState = audioState,
                onListen = { onNarration(AudioNarrationId.REVISION, AudioNarrationCopy.revision()) },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
                onSelectionSound = onSelectionSound,
            )

            is ConclusionState.Summary -> EvidriloPremiumSummaryScreen(
                initialDraft = current.initialDraft,
                revisedDraft = current.revisedDraft,
                finalEvaluation = current.finalEvaluation,
                onBack = onReturnToCatalog,
                audioState = audioState,
                onListen = { onNarration(AudioNarrationId.REVISION, AudioNarrationCopy.revision()) },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
            )

            is ConclusionState.EvidenceChangeDrafting -> EvidriloDraftScreen(
                case = ConclusionCases.EVIDENCE_CHANGE,
                title = "Rebuild the premium conclusion after the evidence change",
                draft = current.draft,
                validationMessage = current.validationMessage,
                onDraftChange = { onPracticeEvent(ConclusionEvent.UpdateEvidenceChangeDraft(it)) },
                onSubmit = { onPracticeEvent(ConclusionEvent.SubmitEvidenceChange) },
                onReset = onBack,
                audioState = audioState,
                onListen = {
                    onNarration(
                        AudioNarrationId.CHALLENGE,
                        AudioNarrationCopy.case(ConclusionCases.EVIDENCE_CHANGE),
                    )
                },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
                onSelectionSound = onSelectionSound,
            )

            is ConclusionState.EvidenceChangeFeedback -> EvidriloEvidenceChangeFeedbackScreen(
                baseDraft = current.baseDraft,
                draft = current.draft,
                evaluation = current.evaluation,
                onFinish = { onPracticeEvent(ConclusionEvent.FinishEvidenceChange) },
                onReset = onBack,
                audioState = audioState,
                onListen = { onNarration(AudioNarrationId.CHALLENGE, AudioNarrationCopy.challenge()) },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
            )

            is ConclusionState.EvidenceChangeSummary -> EvidriloEvidenceChangeSummaryScreen(
                baseDraft = current.baseDraft,
                challengeDraft = current.challengeDraft,
                challengeEvaluation = current.challengeEvaluation,
                onReset = onBack,
                audioState = audioState,
                onListen = { onNarration(AudioNarrationId.CHALLENGE, AudioNarrationCopy.challenge()) },
                onPauseOrResumeAudio = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
            )
        }
    }
}

@Composable
private fun EvidriloPremiumLockedScreen(
    billing: BillingPresentation,
    managedPaywallAvailable: Boolean,
    onOpenManagedPaywall: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onSelectOffer: (String) -> Unit,
    onBack: () -> Unit,
    backLabel: String,
) {
    EvidriloContentColumn {
        EvidriloBackButton(label = backLabel, onClick = onBack)
        EvidriloEyebrow("EVIDRILO PREMIUM")
        Text("Practice two more evidence-linked cases", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The free tablet case remains complete and usable. Premium adds two distinct practice cases without changing the free flow.",
            style = MaterialTheme.typography.bodyLarge,
        )
        EvidriloTintPanel {
            Text("Access status", style = MaterialTheme.typography.titleMedium)
            Text(billing.state.displayLabel(), style = MaterialTheme.typography.labelLarge)
            Text(billing.message, style = MaterialTheme.typography.bodyMedium)
            if (billing.offers.isNotEmpty()) {
                Text("Choose a plan", style = MaterialTheme.typography.titleSmall)
                billing.offers.forEach { offer ->
                    EvidriloChoiceButton(
                        label = "${offer.title}\n${offer.price}",
                        selected = billing.selectedOffer?.productId == offer.productId,
                        onClick = { onSelectOffer(offer.productId) },
                    )
                }
            }
        }
        EvidriloPrimaryButton(
            label = if (billing.isBusy) "Checking premium access…" else "Unlock premium practice",
            onClick = onPurchase,
            enabled = billing.canPurchase,
        )
        EvidriloSecondaryButton(
            label = "Restore purchase",
            onClick = onRestore,
            enabled = billing.canRestore,
        )
        if (managedPaywallAvailable) {
            EvidriloSecondaryButton(
                label = "Open managed RevenueCat plans",
                onClick = onOpenManagedPaywall,
            )
        }
        EvidriloSecondaryButton(label = "Keep the free case", onClick = onBack)
    }
}

@Composable
private fun EvidriloPremiumCatalogScreen(
    state: PremiumPracticeState.Catalog,
    onSelectCase: (String) -> Unit,
    onBeginCase: () -> Unit,
    onBack: () -> Unit,
    backLabel: String,
) {
    EvidriloContentColumn {
        EvidriloBackButton(label = backLabel, onClick = onBack)
        EvidriloEyebrow("EVIDRILO PREMIUM · UNLOCKED")
        Text("Choose a focused case", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Each case keeps the same bounded conclusion method: supplied facts, limitations, one revision, and learner-authored text.",
            style = MaterialTheme.typography.bodyMedium,
        )
        state.cases.forEach { premiumCase ->
            EvidriloChoiceButton(
                label = premiumCase.title + "\n" + premiumCase.description,
                selected = state.selectedCaseId == premiumCase.id,
                onClick = { onSelectCase(premiumCase.id) },
            )
        }
        EvidriloPrimaryButton(
            label = "Start selected case",
            onClick = onBeginCase,
            enabled = state.selectedCase != null,
        )
        EvidriloSecondaryButton(label = "Return to free practice", onClick = onBack)
    }
}

@Composable
private fun EvidriloPremiumSummaryScreen(
    initialDraft: ConclusionDraft,
    revisedDraft: ConclusionDraft,
    finalEvaluation: ConclusionEvaluation,
    onBack: () -> Unit,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    EvidriloContentColumn {
        EvidriloBackButton(label = "Packs", onClick = onBack)
        EvidriloEyebrow("EVIDRILO PREMIUM · REVISION COMPLETE")
        Text("Compare the premium practice", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The initial draft and one revision remain learner-authored. This premium session is not added to free-core local comparison history.",
            style = MaterialTheme.typography.bodyMedium,
        )
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        EvidriloDraftSnapshot("Before feedback", initialDraft)
        HorizontalDivider()
        EvidriloDraftSnapshot("After one revision", revisedDraft)
        finalEvaluation.primaryFeedback?.let { feedback ->
            EvidriloFeedbackCard(feedback, prominent = true)
        } ?: EvidriloNotice(
            status = ConclusionStatus.PASS,
            title = "The premium conclusion passes the bounded checks",
            body = "The selected evidence, scope, limitations, and next action remain connected.",
        )
        EvidriloPrimaryButton(label = "Return to premium cases", onClick = onBack)
    }
}

@Composable
private fun EvidriloDraftScreen(
    case: ConclusionCase,
    title: String,
    draft: ConclusionDraft,
    validationMessage: String?,
    onDraftChange: (ConclusionDraft) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)? = null,
    initialStep: EvidriloDraftStep = EvidriloDraftStep.EVIDENCE,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
    onSelectionSound: () -> Unit = {},
) {
    var step by remember(case.id, initialStep) { mutableStateOf(initialStep) }

    EvidriloContentColumn {
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        EvidriloEyebrow(if (case.changeNotice == null) "FREE CASE" else "EVIDENCE CHANGE")
        Text(
            when (step) {
                EvidriloDraftStep.EVIDENCE -> title
                EvidriloDraftStep.CLAIM -> "Write a bounded claim"
                EvidriloDraftStep.LIMITS -> "Name the limits and next action"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            when (step) {
                EvidriloDraftStep.EVIDENCE -> "Start with the supplied facts. Select only observations that directly support the relationship you want to describe."
                EvidriloDraftStep.CLAIM -> "Write what your selected evidence supports, then choose how far the claim can go."
                EvidriloDraftStep.LIMITS -> "Name the limitations that still matter and one practical next action."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        EvidriloDraftStepIndicator(current = step)
        case.changeNotice?.let { notice ->
            EvidriloNotice(
                status = ConclusionStatus.ACTION_REQUIRED,
                title = "The supplied evidence changed",
                body = notice,
            )
        }
        if (validationMessage != null) {
            EvidriloNotice(
                status = ConclusionStatus.INCOMPLETE,
                title = "Finish the required input",
                body = validationMessage,
            )
        }
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )

        when (step) {
            EvidriloDraftStep.EVIDENCE -> {
                EvidriloCaseFactsCard(case)
                EvidriloSectionTitle("1. What relationship are you making?")
                ConclusionRelation.entries
                    .filter { it != ConclusionRelation.UNSUPPORTED }
                    .forEach { relation ->
                        EvidriloChoiceButton(
                            label = relation.displayLabel(),
                            selected = draft.relation == relation,
                            onClick = {
                                onSelectionSound()
                                onDraftChange(draft.copy(relation = relation))
                            },
                        )
                    }

                EvidriloSectionTitle("2. Which observations support it?")
                Text(
                    "Choose the supplied observation facts. Their IDs become anchors in feedback.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                case.factsOfType(ConclusionFactType.OBSERVATION).forEach { fact ->
                    EvidriloFactChoice(
                        fact = fact,
                        selected = fact.id in draft.evidenceRefs,
                        onClick = {
                            onSelectionSound()
                            onDraftChange(
                                draft.copy(evidenceRefs = draft.evidenceRefs.toggle(fact.id)),
                            )
                        },
                    )
                }
                EvidriloPrimaryButton(
                    label = "Continue to claim",
                    onClick = { step = EvidriloDraftStep.CLAIM },
                )
                EvidriloSecondaryButton(label = "Reset this practice", onClick = onReset)
            }

            EvidriloDraftStep.CLAIM -> {
                EvidriloTintPanel {
                    Text("Selected evidence", style = MaterialTheme.typography.titleSmall)
                    Text(
                        draft.evidenceRefs.ifEmpty { listOf("No observation selected yet") }.joinToString(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EvidriloSectionTitle("3. Write the conclusion")
                OutlinedTextField(
                    value = draft.claimText,
                    onValueChange = { onDraftChange(draft.copy(claimText = it.take(400))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your claim") },
                    supportingText = {
                        Text(draft.claimText.length.toString() + "/320 characters · minimum 20")
                    },
                    minLines = 4,
                )
                EvidriloSectionTitle("4. How far does the claim go?")
                ConclusionScope.entries
                    .filter { it != ConclusionScope.UNSUPPORTED }
                    .forEach { scope ->
                        EvidriloChoiceButton(
                            label = scope.displayLabel(),
                            selected = draft.scope == scope,
                            onClick = {
                                onSelectionSound()
                                onDraftChange(draft.copy(scope = scope))
                            },
                        )
                    }
                EvidriloSecondaryButton(
                    label = "Back to evidence",
                    onClick = { step = EvidriloDraftStep.EVIDENCE },
                )
                EvidriloPrimaryButton(
                    label = "Continue to limits",
                    onClick = { step = EvidriloDraftStep.LIMITS },
                )
                EvidriloSecondaryButton(label = "Reset this practice", onClick = onReset)
            }

            EvidriloDraftStep.LIMITS -> {
                EvidriloSectionTitle("5. Which limitations matter?")
                Text(
                    "Choose the supplied limitations that constrain your claim.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                case.factsOfType(ConclusionFactType.LIMITATION).forEach { fact ->
                    EvidriloFactChoice(
                        fact = fact,
                        selected = fact.id in draft.limitationRefs,
                        onClick = {
                            onSelectionSound()
                            onDraftChange(
                                draft.copy(limitationRefs = draft.limitationRefs.toggle(fact.id)),
                            )
                        },
                    )
                }
                OutlinedTextField(
                    value = draft.limitationNote,
                    onValueChange = { onDraftChange(draft.copy(limitationNote = it.take(300))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Explain the limitation") },
                    supportingText = {
                        Text(draft.limitationNote.length.toString() + "/240 characters · minimum 10")
                    },
                    minLines = 3,
                )

                EvidriloSectionTitle("6. What is the next action?")
                ConclusionImplication.entries
                    .filter { it != ConclusionImplication.UNSUPPORTED }
                    .forEach { implication ->
                        EvidriloChoiceButton(
                            label = implication.displayLabel(),
                            selected = draft.implication == implication,
                            onClick = {
                                onSelectionSound()
                                onDraftChange(draft.copy(implication = implication))
                            },
                        )
                    }
                OutlinedTextField(
                    value = draft.implicationReason,
                    onValueChange = { onDraftChange(draft.copy(implicationReason = it.take(300))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Why this action?") },
                    supportingText = {
                        Text(draft.implicationReason.length.toString() + "/240 characters · minimum 10")
                    },
                    minLines = 3,
                )

                EvidriloSecondaryButton(
                    label = "Back to claim",
                    onClick = { step = EvidriloDraftStep.CLAIM },
                )
                EvidriloPrimaryButton(
                    label = "Review my conclusion",
                    onClick = onSubmit,
                )
                EvidriloSecondaryButton(label = "Reset this practice", onClick = onReset)
            }
        }
    }
}

private enum class EvidriloDraftStep(val label: String) {
    EVIDENCE("Evidence"),
    CLAIM("Claim"),
    LIMITS("Limits"),
}

private fun ConclusionField.toDraftStep(): EvidriloDraftStep = when (this) {
    ConclusionField.CASE_ID,
    ConclusionField.RELATION,
    ConclusionField.EVIDENCE_REFS,
    -> EvidriloDraftStep.EVIDENCE
    ConclusionField.CLAIM_TEXT,
    ConclusionField.SCOPE,
    -> EvidriloDraftStep.CLAIM
    ConclusionField.LIMITATION_REFS,
    ConclusionField.LIMITATION_NOTE,
    ConclusionField.IMPLICATION,
    ConclusionField.IMPLICATION_REASON,
    -> EvidriloDraftStep.LIMITS
}

@Composable
private fun EvidriloDraftStepIndicator(current: EvidriloDraftStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EvidriloDraftStep.entries.forEach { step ->
            val selected = current == step
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        this.selected = selected
                    }
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    step.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) EvidriloColors.Cobalt else EvidriloColors.Slate,
                )
                HorizontalDivider(
                    color = if (selected) EvidriloColors.Cobalt else EvidriloColors.Separator,
                    thickness = if (selected) 3.dp else 1.dp,
                )
            }
        }
    }
}

@Composable
private fun EvidriloFeedbackScreen(
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation,
    onRevise: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)? = null,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    EvidriloContentColumn {
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        EvidriloEyebrow("FEEDBACK · ONE REVISION AVAILABLE")
        Text("See what the case supports", style = MaterialTheme.typography.headlineMedium)
        Text(
            "These checks are bounded to the supplied facts. They are not a grade or a claim that the real-world experiment is scientifically complete.",
            style = MaterialTheme.typography.bodyMedium,
        )
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        evaluation.primaryFeedback?.let { feedback ->
            EvidriloFeedbackCard(feedback, prominent = true)
        } ?: EvidriloNotice(
            status = ConclusionStatus.PASS,
            title = "All four bounded checks pass",
            body = "Your conclusion is connected to the selected facts, limits its scope, and names a supported next action.",
        )
        if (evaluation.checks.isNotEmpty()) {
            EvidriloSectionTitle("Check details")
            evaluation.checks.forEach { check -> EvidriloCheckCard(check) }
        }
        EvidriloDraftSnapshot("Current conclusion", draft)
        EvidriloPrimaryButton(label = "Revise once", onClick = onRevise)
        EvidriloSecondaryButton(label = "Finish and reset", onClick = onReset)
    }
}

@Composable
private fun EvidriloSummaryScreen(
    initialDraft: ConclusionDraft,
    revisedDraft: ConclusionDraft,
    finalEvaluation: ConclusionEvaluation,
    onStartChallenge: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)? = null,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    EvidriloContentColumn {
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        EvidriloEyebrow("SUMMARY · REVISION COMPLETE")
        Text("Compare your reasoning", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The first draft remains stored beside the single revision. Evidrilo does not replace either draft with a generated answer.",
            style = MaterialTheme.typography.bodyMedium,
        )
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        EvidriloDraftSnapshot("Before feedback", initialDraft)
        HorizontalDivider()
        EvidriloDraftSnapshot("After one revision", revisedDraft)
        finalEvaluation.primaryFeedback?.let { feedback ->
            EvidriloFeedbackCard(feedback, prominent = true)
        } ?: EvidriloNotice(
            status = ConclusionStatus.PASS,
            title = "The revised conclusion passes the bounded checks",
            body = "You connected the selected evidence, scope, limitations, and next action.",
        )
        EvidriloPrimaryButton(label = "Try the evidence-change challenge", onClick = onStartChallenge)
        EvidriloSecondaryButton(label = "Start a new practice", onClick = onReset)
    }
}

@Composable
private fun EvidriloEvidenceChangeFeedbackScreen(
    baseDraft: ConclusionDraft,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation,
    onFinish: () -> Unit,
    onReset: () -> Unit,
    onBack: (() -> Unit)? = null,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    EvidriloContentColumn {
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        EvidriloEyebrow("FEEDBACK · EVIDENCE CHANGE")
        Text("Re-evaluate the changed case", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This feedback uses only the observations still supplied in the challenge. There is no second revision in this round.",
            style = MaterialTheme.typography.bodyMedium,
        )
        EvidriloNotice(
            status = ConclusionStatus.ACTION_REQUIRED,
            title = "The cold-water observation is unavailable",
            body = ConclusionCases.EVIDENCE_CHANGE.changeNotice.orEmpty(),
        )
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        evaluation.primaryFeedback?.let { feedback ->
            EvidriloFeedbackCard(feedback, prominent = true)
        } ?: EvidriloNotice(
            status = ConclusionStatus.PASS,
            title = "The changed-evidence checks pass",
            body = "Your new conclusion stays within the observations and limitations available in this round.",
        )
        if (evaluation.checks.isNotEmpty()) {
            EvidriloSectionTitle("Challenge check details")
            evaluation.checks.forEach { check -> EvidriloCheckCard(check) }
        }
        EvidriloDraftSnapshot("Base revision remains unchanged", baseDraft)
        EvidriloDraftSnapshot("Challenge conclusion", draft)
        EvidriloPrimaryButton(label = "See the comparison", onClick = onFinish)
        EvidriloSecondaryButton(label = "Leave challenge", onClick = onReset)
    }
}

@Composable
private fun EvidriloEvidenceChangeSummaryScreen(
    baseDraft: ConclusionDraft,
    challengeDraft: ConclusionDraft,
    challengeEvaluation: ConclusionEvaluation,
    onReset: () -> Unit,
    onBack: (() -> Unit)? = null,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    EvidriloContentColumn {
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        EvidriloEyebrow("COMPARISON · LOCAL HISTORY")
        Text("See what changed with the evidence", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The base revision is kept beside the fresh challenge conclusion. The comparison is stored locally as the latest entry and can be cleared from the start screen.",
            style = MaterialTheme.typography.bodyMedium,
        )
        EvidriloNotice(
            status = ConclusionStatus.ACTION_REQUIRED,
            title = "Evidence change recorded",
            body = ConclusionCases.EVIDENCE_CHANGE.changeNotice.orEmpty(),
        )
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        EvidriloDraftSnapshot("Base revision", baseDraft)
        HorizontalDivider()
        EvidriloDraftSnapshot("Changed-evidence conclusion", challengeDraft)
        challengeEvaluation.primaryFeedback?.let { feedback ->
            EvidriloFeedbackCard(feedback, prominent = true)
        } ?: EvidriloNotice(
            status = ConclusionStatus.PASS,
            title = "The challenge conclusion passes the bounded checks",
            body = "The latest comparison preserves the learner-authored conclusions and active fact anchors.",
        )
        EvidriloPrimaryButton(label = "Return to local practice", onClick = onReset)
    }
}

@Composable
private fun EvidriloCaseFactsCard(case: ConclusionCase) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Supplied case facts", style = MaterialTheme.typography.titleMedium)
            case.facts.forEach { fact -> EvidriloFactRow(fact) }
        }
    }
}

@Composable
private fun EvidriloFactRow(fact: ConclusionFact) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            fact.id + " · " + fact.type.displayLabel(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(fact.text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EvidriloFactChoice(
    fact: ConclusionFact,
    selected: Boolean,
    onClick: () -> Unit,
) {
    EvidriloChoiceButton(
        label = fact.id + " · " + fact.text,
        selected = selected,
        onClick = onClick,
        role = Role.Checkbox,
    )
}

@Composable
private fun EvidriloChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    role: Role = Role.RadioButton,
) {
    val interactionModifier = when (role) {
        Role.Checkbox -> Modifier.toggleable(
            value = selected,
            role = Role.Checkbox,
            onValueChange = { onClick() },
        )

        else -> Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(interactionModifier)
            .semantics(mergeDescendants = true) {
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) EvidriloColors.Tint else EvidriloColors.White,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) EvidriloColors.Cobalt else EvidriloColors.Separator,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EvidriloFeedbackCard(
    feedback: ConclusionFeedbackItem,
    prominent: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (prominent) EvidriloColors.Tint else EvidriloColors.Surface,
        ),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(feedback.status.displayLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                feedback.code + " · " + feedback.field.displayLabel(),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(feedback.message, style = MaterialTheme.typography.bodyLarge)
            Text("Why: " + feedback.why, style = MaterialTheme.typography.bodyMedium)
            Text("Next: " + feedback.nextAction, style = MaterialTheme.typography.bodyMedium)
            Text("Anchors: " + feedback.anchorIds.joinToString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EvidriloCheckCard(check: ConclusionCheckResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(check.check.displayLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(check.status.displayLabel(), style = MaterialTheme.typography.labelLarge)
            Text(check.reason, style = MaterialTheme.typography.bodyMedium)
            Text("Anchors: " + check.anchorIds.joinToString(), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EvidriloNotice(
    status: ConclusionStatus,
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ConclusionStatus.PASS -> EvidriloColors.SuccessSurface
                ConclusionStatus.ACTION_REQUIRED -> EvidriloColors.Tint
                ConclusionStatus.INCOMPLETE,
                ConclusionStatus.CANNOT_ASSESS,
                -> EvidriloColors.ErrorSurface
            },
        ),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(status.displayLabel(), style = MaterialTheme.typography.labelLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun EvidriloDraftSnapshot(
    title: String,
    draft: ConclusionDraft,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            EvidriloSnapshotRow("Relation", draft.relation?.displayLabel() ?: "Not selected")
            EvidriloSnapshotRow("Evidence", draft.evidenceRefs.ifEmpty { listOf("None") }.joinToString())
            EvidriloSnapshotRow("Claim", draft.claimText.ifBlank { "Not written" })
            EvidriloSnapshotRow("Scope", draft.scope?.displayLabel() ?: "Not selected")
            EvidriloSnapshotRow("Limitations", draft.limitationRefs.ifEmpty { listOf("None") }.joinToString())
            EvidriloSnapshotRow("Limitation note", draft.limitationNote.ifBlank { "Not written" })
            EvidriloSnapshotRow("Next action", draft.implication?.displayLabel() ?: "Not selected")
            EvidriloSnapshotRow("Action reason", draft.implicationReason.ifBlank { "Not written" })
        }
    }
}

@Composable
internal fun EvidriloSnapshotRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun EvidriloSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
internal fun EvidriloEyebrow(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

private fun List<String>.toggle(id: String): List<String> =
    if (id in this) filterNot { it == id } else this + id

private fun ConclusionSessionSnapshot.restore(reducer: ConclusionReducer): ConclusionState =
    when (phase) {
        ConclusionSessionPhase.DRAFTING -> ConclusionState.Drafting(currentDraft)
        ConclusionSessionPhase.FEEDBACK -> ConclusionState.Feedback(
            initialDraft = initialDraft,
            draft = currentDraft,
            evaluation = reducer.evaluate(currentDraft),
        )
        ConclusionSessionPhase.REVISION -> ConclusionState.Revision(
            initialDraft = initialDraft,
            draft = currentDraft,
            initialEvaluation = reducer.evaluate(initialDraft),
        )
        ConclusionSessionPhase.SUMMARY -> ConclusionState.Summary(
            initialDraft = initialDraft,
            revisedDraft = currentDraft,
            initialEvaluation = reducer.evaluate(initialDraft),
            finalEvaluation = reducer.evaluate(currentDraft),
        )
        ConclusionSessionPhase.EVIDENCE_CHANGE_DRAFTING -> ConclusionState.EvidenceChangeDrafting(
            baseDraft = initialDraft,
            baseEvaluation = reducer.evaluate(initialDraft),
            draft = currentDraft,
        )
        ConclusionSessionPhase.EVIDENCE_CHANGE_FEEDBACK -> ConclusionState.EvidenceChangeFeedback(
            baseDraft = initialDraft,
            baseEvaluation = reducer.evaluate(initialDraft),
            draft = currentDraft,
            evaluation = reducer.evaluateEvidenceChange(currentDraft),
        )
        ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY -> ConclusionState.EvidenceChangeSummary(
            baseDraft = initialDraft,
            baseEvaluation = reducer.evaluate(initialDraft),
            challengeDraft = currentDraft,
            challengeEvaluation = reducer.evaluateEvidenceChange(currentDraft),
        )
    }

private fun ConclusionState.persist(store: ConclusionSessionStore): LocalStorageWriteResult =
    when (this) {
        ConclusionState.Intro -> store.clear()
        is ConclusionState.Drafting -> store.save(
            ConclusionSessionSnapshot(ConclusionSessionPhase.DRAFTING, draft, draft),
        )
        is ConclusionState.Incomplete -> store.save(
            ConclusionSessionSnapshot(ConclusionSessionPhase.DRAFTING, draft, draft),
        )
        is ConclusionState.Feedback -> store.save(
            ConclusionSessionSnapshot(ConclusionSessionPhase.FEEDBACK, initialDraft, draft),
        )
        is ConclusionState.Revision -> store.save(
            ConclusionSessionSnapshot(ConclusionSessionPhase.REVISION, initialDraft, draft),
        )
        is ConclusionState.Summary -> store.save(
            ConclusionSessionSnapshot(ConclusionSessionPhase.SUMMARY, initialDraft, revisedDraft),
        )
        is ConclusionState.EvidenceChangeDrafting -> store.save(
            ConclusionSessionSnapshot(
                ConclusionSessionPhase.EVIDENCE_CHANGE_DRAFTING,
                baseDraft,
                draft,
            ),
        )
        is ConclusionState.EvidenceChangeFeedback -> store.save(
            ConclusionSessionSnapshot(
                ConclusionSessionPhase.EVIDENCE_CHANGE_FEEDBACK,
                baseDraft,
                draft,
            ),
        )
        is ConclusionState.EvidenceChangeSummary -> store.save(
            ConclusionSessionSnapshot(
                ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY,
                baseDraft,
                challengeDraft,
            ),
        )
    }

private fun ConclusionState.EvidenceChangeSummary.toHistorySnapshot(): ConclusionSessionSnapshot =
    ConclusionSessionSnapshot(
        phase = ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY,
        initialDraft = baseDraft,
        currentDraft = challengeDraft,
    )

private fun ConclusionRelation.displayLabel(): String = when (this) {
    ConclusionRelation.OBSERVED_DIFFERENCE -> "The observations show a difference"
    ConclusionRelation.LIMITED_OBSERVATION -> "This is a limited observation"
    ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE -> "The case cannot establish a broader claim"
    ConclusionRelation.UNSUPPORTED -> "Unsupported relation"
}

private fun ConclusionScope.displayLabel(): String = when (this) {
    ConclusionScope.THIS_OBSERVATION -> "Only this observation"
    ConclusionScope.LIMITED_COMPARISON -> "This limited comparison"
    ConclusionScope.GENERAL_CAUSAL_CLAIM -> "A general causal claim"
    ConclusionScope.UNSUPPORTED -> "Unsupported scope"
}

private fun ConclusionImplication.displayLabel(): String = when (this) {
    ConclusionImplication.REPEAT_TRIALS -> "Repeat the trials"
    ConclusionImplication.CONTROL_STIRRING -> "Control stirring"
    ConclusionImplication.LIMIT_CLAIM -> "Limit the claim"
    ConclusionImplication.NOT_APPLICABLE -> "No further action applies"
    ConclusionImplication.UNSUPPORTED -> "Unsupported action"
}
