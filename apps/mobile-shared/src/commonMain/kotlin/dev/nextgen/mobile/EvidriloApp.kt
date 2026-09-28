package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import dev.nextgen.mobile.account.AccountUnavailableReason
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED
import dev.nextgen.mobile.account.EvidriloAccountRequiredGate
import dev.nextgen.mobile.account.EvidriloAccountScreen
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountGateway
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.account.subscribeAccountAuthRedirect
import dev.nextgen.mobile.account.toSettingsSubtitle
import dev.nextgen.mobile.ai.AiAssistPurpose
import dev.nextgen.mobile.ai.AiAssistContext
import dev.nextgen.mobile.ai.AiConversationGatewayResult
import dev.nextgen.mobile.ai.AiConversationHistoryMessage
import dev.nextgen.mobile.ai.AiConversationProposal
import dev.nextgen.mobile.ai.AiConversationSessionInfo
import dev.nextgen.mobile.ai.AiCredits
import dev.nextgen.mobile.ai.AiGateway
import dev.nextgen.mobile.ai.AiGatewayResult
import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.analytics.analyticsTransmissionAllowed
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
import dev.nextgen.mobile.billing.BillingUiState
import dev.nextgen.mobile.billing.PremiumAccess
import dev.nextgen.mobile.billing.EvidriloPremiumPaywall
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
import dev.nextgen.mobile.domain.onboarding.GetStartedStatus
import dev.nextgen.mobile.domain.onboarding.GetStartedTourEvent
import dev.nextgen.mobile.domain.onboarding.GetStartedTourState
import dev.nextgen.mobile.content.ContentClientConfiguration
import dev.nextgen.mobile.content.PublishedCaseGateway
import dev.nextgen.mobile.content.PublishedCaseGatewayResult
import dev.nextgen.mobile.content.toBundledEvaluatorCase
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldProposal
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldOperation
import dev.nextgen.mobile.domain.project.StudentProjectSourceRecord
import dev.nextgen.mobile.domain.project.StudentProjectStatus
import dev.nextgen.mobile.domain.project.StudentProjectSynthesisTheme
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldDecision
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldSettlementGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldSettlementResult
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentGatewayResult
import dev.nextgen.mobile.projectcatalog.PROJECT_AI_CONSENT_POLICY_VERSION
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldRequest
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldRules
import dev.nextgen.mobile.projectcatalog.StudentProjectDraftFlow
import dev.nextgen.mobile.projectcatalog.StudentProjectDraftFlowResult
import dev.nextgen.mobile.projectcatalog.StudentProjectAttachmentAddReceipt
import dev.nextgen.mobile.projectcatalog.StudentProjectAttachmentRemoveReceipt
import dev.nextgen.mobile.projectcatalog.StudentProjectImportReceipt
import dev.nextgen.mobile.projectcatalog.ProjectTemplateCatalogGateway
import dev.nextgen.mobile.projectcatalog.ProjectTemplateCatalogGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectTemplateClientConfiguration
import dev.nextgen.mobile.projectcatalog.ProjectTemplateRemoteFamily
import dev.nextgen.mobile.projectcatalog.ProjectTemplateSummary
import dev.nextgen.mobile.platform.PlatformEntitlements
import dev.nextgen.mobile.platform.PlatformProjectionGateway
import dev.nextgen.mobile.platform.PlatformProjectionResult
import dev.nextgen.mobile.platform.PlatformProgressSummary
import dev.nextgen.mobile.storage.ConclusionSessionPhase
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.ConclusionSessionStore
import dev.nextgen.mobile.storage.LocalStorageWriteResult
import dev.nextgen.mobile.storage.LocalStorageStatus
import dev.nextgen.mobile.storage.createConclusionSessionStore
import dev.nextgen.mobile.storage.createConclusionHistoryStore
import dev.nextgen.mobile.storage.createStudentProjectDraftStore
import dev.nextgen.mobile.storage.createStudentProjectAttachmentStore
import dev.nextgen.mobile.storage.createOnboardingStore
import dev.nextgen.mobile.storage.onboardingStatusAfterWrite
import dev.nextgen.mobile.storage.notice
import dev.nextgen.mobile.storage.recoverCorruptLocalStorage
import dev.nextgen.mobile.storage.storageNoticeFor
import dev.nextgen.mobile.audio.createAudioSettingsStore
import dev.nextgen.mobile.audio.createPlatformAudioEngine
import dev.nextgen.mobile.security.SecureSessionStoreFactory
import dev.nextgen.mobile.navigation.EvidriloDestination
import dev.nextgen.mobile.navigation.requiresAuthenticatedFreeAccess
import dev.nextgen.mobile.navigation.runWithAuthenticatedAccess
import dev.nextgen.mobile.navigation.EvidriloNavigationState
import dev.nextgen.mobile.navigation.EvidriloSystemBackHandler
import dev.nextgen.mobile.notifications.NotificationPermissionState
import dev.nextgen.mobile.notifications.NotificationPermissionUiState
import dev.nextgen.mobile.notifications.NotificationPreferences
import dev.nextgen.mobile.notifications.NotificationPreferencesGateway
import dev.nextgen.mobile.notifications.NotificationPreferencesGatewayResult
import dev.nextgen.mobile.notifications.NOTIFICATION_PERMISSION_REQUEST_DENIED_MESSAGE
import dev.nextgen.mobile.notifications.SYSTEM_NOTIFICATION_PERMISSION_OFF_MESSAGE
import dev.nextgen.mobile.notifications.NotificationClientConfiguration
import dev.nextgen.mobile.notifications.createLocalNotificationScheduler
import dev.nextgen.mobile.notifications.createNotificationPreferencesStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Composable
internal fun EvidriloApp(
    billingGateway: BillingGateway,
    themeController: EvidriloThemeController = remember { EvidriloThemeController() },
) {
    val bundledBaseCase = ConclusionCases.M0_T2
    var remoteBaseCase by remember { mutableStateOf<ConclusionCase?>(null) }
    val baseCase = remoteBaseCase ?: bundledBaseCase
    val evidenceChangeCase = ConclusionCases.EVIDENCE_CHANGE
    val reducer = remember(baseCase.id, evidenceChangeCase.id) {
        ConclusionReducer(case = baseCase, evidenceChangeCase = evidenceChangeCase)
    }
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
    val projectTemplateCatalogGateway = remember(accountConfiguration) {
        ProjectTemplateCatalogGateway(
            configuration = ProjectTemplateClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
        )
    }
    val projectAiScaffoldGateway = remember(accountConfiguration, secureSessionStore) {
        ProjectAiScaffoldGateway(
            configuration = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val projectAiSettlementGateway = remember(accountConfiguration, secureSessionStore) {
        ProjectAiScaffoldSettlementGateway(
            configuration = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val projectAiConsentGateway = remember(accountConfiguration, secureSessionStore) {
        ProjectAiConsentGateway(
            configuration = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val projectProEntitlementActive = remember { mutableStateOf(false) }
    val studentProjectAttachmentStore = remember { createStudentProjectAttachmentStore() }
    val studentProjectDraftFlow = remember {
        StudentProjectDraftFlow(
            store = createStudentProjectDraftStore(),
            idGenerator = ::newAnalyticsEventId,
            clock = { Clock.System.now().toEpochMilliseconds() },
            hasVerifiedProEntitlement = { projectProEntitlementActive.value },
        )
    }
    val publishedCaseGateway = remember(accountConfiguration, secureSessionStore) {
        PublishedCaseGateway(
            configuration = ContentClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val projectionGateway = remember(accountConfiguration, secureSessionStore) {
        PlatformProjectionGateway(
            configuration = dev.nextgen.mobile.platform.PlatformClientConfiguration(
                accountConfiguration.normalizedApiBaseUrl,
            ),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val aiGateway = remember(accountConfiguration, secureSessionStore) {
        AiGateway(
            configuration = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val analyticsGateway = remember { createPlatformAnalyticsGateway() }
    val accountScope = rememberCoroutineScope()
    val notificationScheduler = remember { createLocalNotificationScheduler() }
    val notificationPreferencesStore = remember { createNotificationPreferencesStore() }
    val notificationPreferencesGateway = remember(accountConfiguration, secureSessionStore) {
        NotificationPreferencesGateway(
            configuration = NotificationClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
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
    var onboardingStatus by remember {
        mutableStateOf(initialOnboardingLoad.value ?: GetStartedStatus.NOT_STARTED)
    }
    var onboardingTour by remember { mutableStateOf(GetStartedTourState()) }
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
    val initialNotificationPreferencesLoad = remember { notificationPreferencesStore.load() }
    var notificationPreferences by remember {
        mutableStateOf(initialNotificationPreferencesLoad.value ?: NotificationPreferences())
    }
    var notificationStorageStatus by remember {
        mutableStateOf(initialNotificationPreferencesLoad.status)
    }
    var notificationPermission by remember {
        mutableStateOf(NotificationPermissionState.UNKNOWN)
    }
    var notificationBusy by remember { mutableStateOf(false) }
    var notificationStatusMessage by remember { mutableStateOf<String?>(null) }
    var accountSession by remember { mutableStateOf<AccountSession>(accountController.state) }
    var accountRestoreComplete by remember { mutableStateOf(TEMPORARY_GUEST_MODE_ENABLED) }
    var lastSyncAccountId by remember { mutableStateOf<String?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountExportJson by remember { mutableStateOf<String?>(null) }
    var accountExportError by remember { mutableStateOf<AccountUnavailableReason?>(null) }
    var remoteContentStatus by remember { mutableStateOf("Offline-ready bundled case") }
    var platformProgress by remember { mutableStateOf<PlatformProgressSummary?>(null) }
    var platformEntitlements by remember { mutableStateOf<PlatformEntitlements?>(null) }
    var platformStatus by remember { mutableStateOf<String?>(null) }
    var aiCredits by remember { mutableStateOf<AiCredits?>(null) }
    var aiAssistState by remember {
        mutableStateOf<EvidriloAiAssistUiState>(EvidriloAiAssistUiState.SignInRequired)
    }
    var aiAssistRequestContextKey by remember { mutableStateOf<String?>(null) }
    var aiConversationSession by remember { mutableStateOf<AiConversationSessionInfo?>(null) }
    var aiConversationContextKey by remember { mutableStateOf<String?>(null) }
    var aiConversationAccountId by remember { mutableStateOf<String?>(null) }
    var aiConversationClearState by remember {
        mutableStateOf<EvidriloAiConversationClearState>(EvidriloAiConversationClearState.Idle)
    }
    var recommendationSessionGeneration by remember { mutableStateOf(0L) }
    val currentAccountBusy by rememberUpdatedState(accountBusy)
    val currentAccountSession by rememberUpdatedState(accountSession)
    fun currentBillingAccountId(): String? =
        (accountSession as? AccountSession.SignedIn)?.account?.accountId
    val latestBillingAccountId = rememberUpdatedState(currentBillingAccountId())
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
                if (TEMPORARY_GUEST_MODE_ENABLED) "Cloud sync is temporarily unavailable in guest mode." else "Sign in again before syncing progress."
            dev.nextgen.mobile.sync.SyncDeferralReason.SESSION_EXPIRED ->
                if (TEMPORARY_GUEST_MODE_ENABLED) "Cloud sync is temporarily unavailable in guest mode." else "Your session expired; sign in again before syncing progress."
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
            syncStatusMessage = if (TEMPORARY_GUEST_MODE_ENABLED) {
                "Cloud sync is temporarily unavailable in guest mode."
            } else {
                "Sign in before syncing progress."
            }
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
                syncStatusMessage = "The local sync queue is full; the local workflow remains available."
            SyncQueueMutation.ACCOUNT_MISMATCH ->
                syncStatusMessage = "The queued progress belongs to another account and was not sent."
            SyncQueueMutation.INVALID,
            SyncQueueMutation.STORE_UNAVAILABLE,
            -> syncStatusMessage = "Local sync storage is unavailable; no progress was sent."
        }
    }
    var sessionStorageStatus by remember { mutableStateOf(initialSessionLoad.status) }
    var navigationState by remember {
        mutableStateOf(
            EvidriloNavigationState(
                // A saved draft is data, not a route. Start in the current
                // product shell and let the learner choose where to resume.
                stack = listOf(EvidriloDestination.HOME),
            ),
        )
    }
    var pendingAccountGateDestination by remember { mutableStateOf<EvidriloDestination?>(null) }

    fun requestGetStartedTour() {
        onboardingTour = GetStartedTourState()
        onboardingRequested = true
    }

    fun openAccountGate(destination: EvidriloDestination) {
        pendingAccountGateDestination = destination
        navigationState = navigationState.openAccountGate()
    }

    fun completeGetStartedTour() {
        val result = onboardingStore.complete()
        onboardingStorageStatus = result.status
        onboardingStatus = onboardingStatusAfterWrite(
            current = onboardingStatus,
            requested = GetStartedStatus.COMPLETED,
            result = result,
        )
        onboardingRequested = false
    }

    fun skipGetStartedTour() {
        val result = onboardingStore.skip()
        onboardingStorageStatus = result.status
        onboardingStatus = onboardingStatusAfterWrite(
            current = onboardingStatus,
            requested = GetStartedStatus.SKIPPED,
            result = result,
        )
        onboardingRequested = false
        onboardingTour = GetStartedTourState()
    }

    fun requireDestinationAccess(destination: EvidriloDestination): Boolean {
        return runWithAuthenticatedAccess(
            destination = destination,
            authenticated = accountSession is AccountSession.SignedIn,
            onAuthenticationRequired = ::openAccountGate,
            action = {},
        )
    }

    fun acceptAccountGatewayResult(result: AccountGatewayResult): AccountSession {
        val accepted = accountController.acceptGatewayResult(result)
        accountSession = accepted
        if (accepted is AccountSession.SignedIn) {
            val requested = pendingAccountGateDestination
            pendingAccountGateDestination = null
            if (requested != null) {
                navigationState = EvidriloNavigationState.afterSuccessfulAccountGate(requested)
            }
        }
        return accepted
    }
    var selectedProjectTemplateFamily by remember {
        mutableStateOf(ProjectTemplateFamily.EXPERIMENTAL_LABORATORY)
    }
    var selectedProjectTemplateSummary by remember { mutableStateOf<ProjectTemplateSummary?>(null) }
    var projectTemplateFamiliesState by remember {
        mutableStateOf<ProjectTemplateRemoteUiState<List<ProjectTemplateRemoteFamily>>>(ProjectTemplateRemoteUiState.NotRequested)
    }
    var projectTemplateFamilyState by remember {
        mutableStateOf<ProjectTemplateRemoteUiState<List<ProjectTemplateSummary>>>(ProjectTemplateRemoteUiState.NotRequested)
    }
    var projectTemplateDetailState by remember {
        mutableStateOf<ProjectTemplateRemoteUiState<dev.nextgen.mobile.domain.project.ProjectTemplateDefinition>>(
            ProjectTemplateRemoteUiState.NotRequested,
        )
    }
    var projectTemplateCatalogReload by remember { mutableStateOf(0) }
    var projectTemplateFamilyReload by remember { mutableStateOf(0) }
    var projectTemplateDetailReload by remember { mutableStateOf(0) }
    var projectAiScaffoldState by remember { mutableStateOf<ProjectAiScaffoldUiState>(ProjectAiScaffoldUiState.Idle) }
    var projectAiRequestToken by remember { mutableStateOf<String?>(null) }
    var projectAiRequestInFlight by remember { mutableStateOf(false) }
    var projectAiConsentState by remember { mutableStateOf<ProjectAiConsentUiState>(ProjectAiConsentUiState.Unknown) }
    var projectAiConsentRequestGeneration by remember { mutableStateOf(0L) }
    var projectAiBoundAccountId by remember { mutableStateOf<String?>(null) }
    var studentProjectListState by remember {
        mutableStateOf<StudentProjectListUiState>(StudentProjectListUiState.Loading)
    }
    var studentProjectListReload by remember { mutableStateOf(0) }
    var studentProjectNotice by remember { mutableStateOf<String?>(null) }
    var studentProjectSaveError by remember { mutableStateOf<String?>(null) }
    var activeStudentProjectDraft by remember { mutableStateOf<StudentProjectDraft?>(null) }
    var studentProjectEditorIsDirty by remember { mutableStateOf(false) }
    var studentProjectExitConfirmation by remember { mutableStateOf(false) }
    val projectTemplateCatalogListState = rememberLazyListState()
    var projectFamilyQuickGuideExpanded by remember { mutableStateOf(false) }
    EvidriloSystemBackHandler(
        enabled = navigationState.canHandleSystemBack,
        onBack = {
            if (navigationState.current == EvidriloDestination.PROJECT_EDITOR && studentProjectEditorIsDirty) {
                studentProjectExitConfirmation = true
            } else {
                navigationState = navigationState.back()
            }
        },
    )
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

    fun replaceStudentProjectInList(draft: StudentProjectDraft) {
        val current = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects.orEmpty()
        studentProjectListState = StudentProjectListUiState.Loaded(
            (current.filterNot { it.id == draft.id } + draft)
                .sortedByDescending(StudentProjectDraft::updatedAtEpochMillis),
        )
    }

    fun settleProjectAiScaffold(
        requestId: String,
        decision: ProjectAiScaffoldDecision,
        creditCost: Int,
        projectAlreadyApplied: Boolean,
    ) {
        if (!projectAiSessionMatchesOwner(projectAiBoundAccountId, currentBillingAccountId())) {
            projectAiScaffoldState = ProjectAiScaffoldUiState.Unavailable(
                "This AI action is unavailable in local guest mode. Your local project remains unchanged.",
            )
            return
        }
        projectAiScaffoldState = ProjectAiScaffoldUiState.Settling(
            requestId = requestId,
            decision = decision,
            creditCost = creditCost,
            projectAlreadyApplied = projectAlreadyApplied,
        )
        accountScope.launch {
            val result = try {
                projectAiSettlementGateway.settle(requestId, decision, creditCost)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiScaffoldSettlementResult.Unavailable("PROJECT_AI_SETTLEMENT_UNAVAILABLE")
            }
            val activeSettlement = projectAiScaffoldState as? ProjectAiScaffoldUiState.Settling
            if (activeSettlement?.requestId != requestId || activeSettlement.decision != decision) return@launch
            when (result) {
                is ProjectAiScaffoldSettlementResult.Settled -> {
                    projectAiRequestToken = null
                    projectAiScaffoldState = ProjectAiScaffoldUiState.Idle
                    studentProjectNotice = when (decision) {
                        ProjectAiScaffoldDecision.APPLY ->
                            "Selected AI suggestions were applied. ${result.creditCost} credit${if (result.creditCost == 1) "" else "s"} confirmed."
                        ProjectAiScaffoldDecision.DISMISS -> "AI suggestions discarded; reserved credits were released."
                    }
                    val credits = try {
                        aiGateway.getCredits()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        null
                    }
                    if (credits is dev.nextgen.mobile.ai.AiGatewayResult.CreditsFound) {
                        aiCredits = credits.value
                    }
                }
                is ProjectAiScaffoldSettlementResult.Deferred -> {
                    projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                        requestId, decision, creditCost, projectAlreadyApplied,
                        "A verified, active account session is required to confirm this AI-credit action. " +
                            if (projectAlreadyApplied) "Your project change is already saved locally." else "The suggestion remains unapplied.",
                        canRetry = true,
                    )
                }
                is ProjectAiScaffoldSettlementResult.Unavailable -> {
                    projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                        requestId, decision, creditCost, projectAlreadyApplied,
                        "AI-credit confirmation is unavailable. " +
                            if (projectAlreadyApplied) "Your project change is already saved locally; retry before requesting more AI." else "The suggestion remains unapplied; retry to release its reservation.",
                        canRetry = true,
                    )
                }
                is ProjectAiScaffoldSettlementResult.Rejected -> {
                    projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                        requestId, decision, creditCost, projectAlreadyApplied,
                        "The server could not confirm this AI-credit action (${result.code}). " +
                            if (projectAlreadyApplied) "Your project change remains saved locally." else "The suggestion remains unapplied.",
                        canRetry = false,
                    )
                }
                is ProjectAiScaffoldSettlementResult.Failed -> {
                    projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                        requestId, decision, creditCost, projectAlreadyApplied,
                        if (result.outcomeUnknown) {
                            "The credit update's outcome is unknown. Retry the same request to reconcile it. " +
                                if (projectAlreadyApplied) "Your project change remains saved locally." else "The suggestion remains unapplied."
                        } else {
                            "The AI-credit action could not be confirmed (${result.code}). " +
                                if (projectAlreadyApplied) "Your project change remains saved locally." else "The suggestion remains unapplied."
                        },
                        canRetry = result.retryable || result.sameIntentReplayAllowed,
                    )
                }
            }
        }
    }

    fun discardProjectAiPreview(requestId: String, creditCost: Int) {
        settleProjectAiScaffold(
            requestId = requestId,
            decision = ProjectAiScaffoldDecision.DISMISS,
            creditCost = creditCost,
            projectAlreadyApplied = false,
        )
    }

    fun retryProjectAiSettlement(failed: ProjectAiScaffoldUiState.SettlementFailed) {
        settleProjectAiScaffold(
            requestId = failed.requestId,
            decision = failed.decision,
            creditCost = failed.creditCost,
            projectAlreadyApplied = failed.projectAlreadyApplied,
        )
    }

    fun releaseAbandonedProjectAiPreview(
        ownerAccountId: String,
        requestId: String,
        creditCost: Int,
    ) {
        if (!projectAiSessionMatchesOwner(ownerAccountId, currentBillingAccountId())) return
        accountScope.launch {
            val result = try {
                projectAiSettlementGateway.settle(
                    requestId,
                    ProjectAiScaffoldDecision.DISMISS,
                    creditCost,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiScaffoldSettlementResult.Unavailable("PROJECT_AI_SETTLEMENT_UNAVAILABLE")
            }
            if (result is ProjectAiScaffoldSettlementResult.Settled) {
                val credits = try {
                    aiGateway.getCredits()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
                if (credits is dev.nextgen.mobile.ai.AiGatewayResult.CreditsFound) {
                    aiCredits = credits.value
                }
            } else if (projectAiRequestToken == null && projectAiScaffoldState == ProjectAiScaffoldUiState.Idle) {
                projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                    requestId = requestId,
                    decision = ProjectAiScaffoldDecision.DISMISS,
                    creditCost = creditCost,
                    projectAlreadyApplied = false,
                    message = "An abandoned AI preview could not be confirmed as released. Retry before the next project-AI request.",
                    canRetry = result !is ProjectAiScaffoldSettlementResult.Rejected,
                )
            }
        }
    }

    fun releaseActiveProjectAiPreview() {
        when (val current = projectAiScaffoldState) {
            is ProjectAiScaffoldUiState.Preview -> discardProjectAiPreview(current.requestId, current.creditCost)
            ProjectAiScaffoldUiState.Idle,
            is ProjectAiScaffoldUiState.Unavailable,
            -> {
                projectAiRequestToken = null
                projectAiScaffoldState = ProjectAiScaffoldUiState.Idle
            }
            ProjectAiScaffoldUiState.Loading,
            -> {
                projectAiRequestToken = null
                projectAiRequestInFlight = false
                projectAiScaffoldState = ProjectAiScaffoldUiState.Idle
            }
            is ProjectAiScaffoldUiState.Settling,
            is ProjectAiScaffoldUiState.SettlementFailed,
            -> Unit
        }
    }

    LaunchedEffect(navigationState.current) {
        if (navigationState.current != EvidriloDestination.PROJECT_TEMPLATE_DETAIL &&
            navigationState.current != EvidriloDestination.PROJECT_EDITOR
        ) {
            releaseActiveProjectAiPreview()
        }
    }

    fun startStudentProject(template: dev.nextgen.mobile.domain.project.ProjectTemplateDefinition) {
        if (!requireDestinationAccess(EvidriloDestination.PROJECT_TEMPLATE_DETAIL)) return
        releaseActiveProjectAiPreview()
        when (val result = studentProjectDraftFlow.start(template, template.title.trim().take(160).ifBlank { "My project" })) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                replaceStudentProjectInList(result.value)
                studentProjectNotice = null
                studentProjectSaveError = null
                studentProjectEditorIsDirty = false
                studentProjectExitConfirmation = false
                navigationState = navigationState.open(EvidriloDestination.PROJECT_EDITOR)
            }
            else -> studentProjectNotice = studentProjectDraftFlowMessage(result)
        }
    }

    fun startManualStudentProject() {
        if (!requireDestinationAccess(EvidriloDestination.PROJECTS)) return
        releaseActiveProjectAiPreview()
        when (val result = studentProjectDraftFlow.startManual("New research project")) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                replaceStudentProjectInList(result.value)
                studentProjectNotice = null
                studentProjectSaveError = null
                studentProjectEditorIsDirty = false
                studentProjectExitConfirmation = false
                navigationState = navigationState.open(EvidriloDestination.PROJECT_EDITOR)
            }
            else -> studentProjectNotice = studentProjectDraftFlowMessage(result)
        }
    }

    fun beginManualProjectFromHome() {
        if (!requireDestinationAccess(EvidriloDestination.HOME)) return
        val loaded = studentProjectListState as? StudentProjectListUiState.Loaded
        if (loaded == null) {
            navigationState = navigationState.open(EvidriloDestination.PROJECTS)
            return
        }
        val activeCount = loaded.projects.count {
            it.status == StudentProjectStatus.DRAFT || it.status == StudentProjectStatus.ACTIVE
        }
        val activeLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(
            projectProEntitlementActive.value,
        )
        if (activeCount >= activeLimit) {
            navigationState = navigationState.open(EvidriloDestination.PROJECTS)
        } else {
            startManualStudentProject()
        }
    }

    fun requestProjectAiScaffold(
        template: dev.nextgen.mobile.domain.project.ProjectTemplateDefinition,
        assignmentBrief: String,
        studentQuestion: String?,
        currentFields: Map<String, String>,
        projectId: String?,
        baseRevision: Int?,
        projectDataConsent: Boolean,
    ) {
        val requestAccountId = currentBillingAccountId()
        if (requestAccountId == null) {
            projectAiScaffoldState = ProjectAiScaffoldUiState.Unavailable(
                "Project AI is temporarily unavailable in local guest mode. Your local project remains available; no context was sent.",
            )
            return
        }
        projectAiBoundAccountId = requestAccountId
        if (!canRequestProjectAi(projectDataConsent, assignmentBrief)) {
            projectAiScaffoldState = ProjectAiScaffoldUiState.Unavailable(
                "Provide an assignment brief and explicitly agree to send the selected context before requesting AI. Nothing was sent.",
            )
            return
        }
        if ((projectId == null) != (baseRevision == null)) {
            projectAiScaffoldState = ProjectAiScaffoldUiState.Unavailable(
                "The selected project identity and revision did not match. No AI request was sent.",
            )
            return
        }
        if (projectAiConsentState !is ProjectAiConsentUiState.Granted) {
            projectAiScaffoldState = ProjectAiScaffoldUiState.Unavailable(
                "Check and grant revocable account-level consent before requesting assistance. No project context was sent.",
            )
            return
        }
        if (projectAiRequestInFlight || projectAiScaffoldState is ProjectAiScaffoldUiState.Loading) {
            studentProjectNotice = "A project-AI request is still being reconciled. Wait for it to finish before starting another."
            return
        }
        if (projectAiScaffoldState is ProjectAiScaffoldUiState.Preview ||
            projectAiScaffoldState is ProjectAiScaffoldUiState.Settling ||
            projectAiScaffoldState is ProjectAiScaffoldUiState.SettlementFailed
        ) {
            studentProjectNotice = "Apply or discard the current AI suggestion and resolve its credit action before requesting another."
            return
        }
        projectAiScaffoldState = ProjectAiScaffoldUiState.Loading
        projectAiRequestInFlight = true
        val request = ProjectAiScaffoldRequest(
            templateId = template.id,
            templateVersion = template.version,
            baseProjectRevision = baseRevision,
            assignmentBrief = assignmentBrief,
            researchQuestion = template.inputFields
                .firstOrNull { it.kind == dev.nextgen.mobile.domain.project.ProjectTemplateInputKind.RESEARCH_QUESTION }
                ?.id?.let(currentFields::get),
            studentQuestion = studentQuestion,
            currentFields = currentFields,
            constraints = emptyList(),
            locale = "en",
            optedIn = projectDataConsent,
            projectDataConsent = projectDataConsent,
            projectDataConsentVersion = ProjectAiScaffoldRules.PROJECT_DATA_CONSENT_VERSION,
            projectId = projectId,
            operation = if (projectId == null) {
                ProjectAiScaffoldOperation.CREATE_PROJECT
            } else {
                ProjectAiScaffoldOperation.ASSIST_PROJECT
            },
        )
        val idempotencyKey = "project-ai-${newAnalyticsEventId()}"
        projectAiRequestToken = idempotencyKey
        accountScope.launch {
            val consentResult = try {
                projectAiConsentGateway.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_UNAVAILABLE", retryable = true)
            }
            if (projectAiRequestToken != idempotencyKey) {
                return@launch
            }
            if (!projectAiSessionMatchesOwner(requestAccountId, currentBillingAccountId())) {
                projectAiRequestToken = null
                projectAiRequestInFlight = false
                projectAiScaffoldState = ProjectAiScaffoldUiState.Idle
                return@launch
            }
            val savedConsent = consentResult as? ProjectAiConsentGatewayResult.State
            if (savedConsent == null || !savedConsent.value.granted ||
                savedConsent.value.policyVersion != PROJECT_AI_CONSENT_POLICY_VERSION
            ) {
                projectAiConsentState = consentResult.toProjectAiConsentUiState()
                projectAiScaffoldState = ProjectAiScaffoldUiState.Unavailable(
                    when (consentResult) {
                        is ProjectAiConsentGatewayResult.State -> "Saved project AI consent is not current. Review and grant it before requesting assistance. No project context was sent."
                        ProjectAiConsentGatewayResult.PolicyStale -> "The project AI consent policy changed. Review and grant it again before requesting assistance. No project context was sent."
                        else -> "Saved project AI consent could not be verified. No project context was sent; check your connection and consent status."
                    },
                )
                projectAiRequestToken = null
                projectAiRequestInFlight = false
                return@launch
            }
            projectAiConsentState = ProjectAiConsentUiState.Granted
            if (!projectAiSessionMatchesOwner(requestAccountId, currentBillingAccountId())) {
                projectAiRequestToken = null
                projectAiRequestInFlight = false
                projectAiScaffoldState = ProjectAiScaffoldUiState.Idle
                return@launch
            }
            val result = try {
                projectAiScaffoldGateway.generatePreview(request, idempotencyKey)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiScaffoldGatewayResult.Unavailable("PROJECT_AI_UNAVAILABLE")
            }
            projectAiRequestInFlight = false
            if (projectAiRequestToken != idempotencyKey) {
                if (result is ProjectAiScaffoldGatewayResult.Preview) {
                    releaseAbandonedProjectAiPreview(requestAccountId, result.requestId, result.creditCost)
                }
                return@launch
            }
            if (!projectAiSessionMatchesOwner(requestAccountId, currentBillingAccountId())) {
                projectAiRequestToken = null
                projectAiRequestInFlight = false
                projectAiScaffoldState = ProjectAiScaffoldUiState.Idle
                if (result is ProjectAiScaffoldGatewayResult.Preview) {
                    releaseAbandonedProjectAiPreview(requestAccountId, result.requestId, result.creditCost)
                }
                return@launch
            }
            when (result) {
                is ProjectAiScaffoldGatewayResult.Preview -> {
                    val issue = ProjectAiScaffoldRules.validate(
                        template,
                        result.proposal,
                        baseRevision,
                        expectedProjectId = projectId,
                    )
                    if (issue == null) {
                        projectAiScaffoldState = result.toProjectAiScaffoldUiState()
                        val credits = try {
                            aiGateway.getCredits()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            null
                        }
                        if (credits is dev.nextgen.mobile.ai.AiGatewayResult.CreditsFound) {
                            aiCredits = credits.value
                        }
                    } else {
                        studentProjectNotice = "The suggestion did not match this reviewed template/revision ($issue); its credit reservation is being released."
                        discardProjectAiPreview(result.requestId, result.creditCost)
                    }
                }
                else -> {
                    projectAiRequestToken = null
                    projectAiScaffoldState = result.toProjectAiScaffoldUiState()
                }
            }
        }
    }

    fun refreshProjectAiConsent() {
        val accountId = currentBillingAccountId()
        if (accountId == null) {
            projectAiConsentState = ProjectAiConsentUiState.Unavailable(
                "Project AI consent management is paused in guest mode. Local project work remains available; no context was sent.",
            )
            return
        }
        val requestGeneration = projectAiConsentRequestGeneration + 1
        projectAiConsentRequestGeneration = requestGeneration
        projectAiConsentState = ProjectAiConsentUiState.Checking
        accountScope.launch {
            val result = try {
                projectAiConsentGateway.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_UNAVAILABLE", retryable = true)
            }
            if (projectAiConsentRequestGeneration == requestGeneration &&
                projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
            ) {
                projectAiConsentState = result.toProjectAiConsentUiState()
            }
        }
    }

    fun grantProjectAiConsent() {
        val accountId = currentBillingAccountId()
        if (accountId == null) {
            projectAiConsentState = ProjectAiConsentUiState.Unavailable(
                "Project AI consent management is paused in guest mode. Local project work remains available; no context was sent.",
            )
            return
        }
        val requestGeneration = projectAiConsentRequestGeneration + 1
        projectAiConsentRequestGeneration = requestGeneration
        projectAiConsentState = ProjectAiConsentUiState.Checking
        accountScope.launch {
            val result = try {
                projectAiConsentGateway.grantAfterExplicitUserAction(explicitlyConfirmed = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_UNAVAILABLE", retryable = true)
            }
            if (projectAiConsentRequestGeneration == requestGeneration &&
                projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
            ) {
                projectAiConsentState = result.toProjectAiConsentUiState()
            }
        }
    }

    fun revokeProjectAiConsent() {
        val accountId = currentBillingAccountId()
        if (accountId == null) {
            projectAiConsentState = ProjectAiConsentUiState.Unavailable(
                "Project AI consent management is paused in guest mode. Local project work remains available; no context was sent.",
            )
            return
        }
        val requestGeneration = projectAiConsentRequestGeneration + 1
        projectAiConsentRequestGeneration = requestGeneration
        releaseActiveProjectAiPreview()
        projectAiConsentState = ProjectAiConsentUiState.Checking
        accountScope.launch {
            val result = try {
                projectAiConsentGateway.revoke()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_UNAVAILABLE", retryable = true)
            }
            if (projectAiConsentRequestGeneration == requestGeneration &&
                projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
            ) {
                projectAiConsentState = result.toProjectAiConsentUiState()
            }
        }
    }

    fun createStudentProjectWithAi(
        template: dev.nextgen.mobile.domain.project.ProjectTemplateDefinition,
        title: String,
        proposal: ProjectAiScaffoldProposal,
        selectedFieldIds: Set<String>,
        editedValues: Map<String, String>,
        requestId: String,
        creditCost: Int,
    ) {
        val preview = projectAiScaffoldState as? ProjectAiScaffoldUiState.Preview
        if (!projectAiSessionMatchesOwner(projectAiBoundAccountId, currentBillingAccountId()) ||
            preview == null || preview.requestId != requestId || preview.proposal != proposal || preview.creditCost != creditCost
        ) {
            studentProjectNotice = "This AI suggestion is unavailable in local guest mode. No suggestion was applied."
            return
        }
        when (val result = studentProjectDraftFlow.startWithAiScaffold(
            template = template,
            title = title,
            proposal = proposal,
            selectedFieldIds = selectedFieldIds,
            editedValues = editedValues,
        )) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                replaceStudentProjectInList(result.value)
                studentProjectNotice = null
                studentProjectSaveError = null
                studentProjectEditorIsDirty = false
                studentProjectExitConfirmation = false
                navigationState = navigationState.open(EvidriloDestination.PROJECT_EDITOR)
                settleProjectAiScaffold(
                    requestId,
                    ProjectAiScaffoldDecision.APPLY,
                    creditCost,
                    projectAlreadyApplied = true,
                )
            }
            else -> studentProjectNotice = studentProjectDraftFlowMessage(result)
        }
    }

    fun applyProjectAiScaffold(
        proposal: ProjectAiScaffoldProposal,
        selectedFieldIds: Set<String>,
        editedValues: Map<String, String>,
        explicitlyReplacedFieldIds: Set<String>,
        requestId: String,
        creditCost: Int,
    ): StudentProjectDraft? {
        val preview = projectAiScaffoldState as? ProjectAiScaffoldUiState.Preview
        if (!projectAiSessionMatchesOwner(projectAiBoundAccountId, currentBillingAccountId()) ||
            preview == null || preview.requestId != requestId || preview.proposal != proposal || preview.creditCost != creditCost
        ) {
            studentProjectNotice = "This AI suggestion is unavailable in local guest mode. No suggestion was applied."
            return null
        }
        val active = activeStudentProjectDraft ?: return null
        return when (val result = studentProjectDraftFlow.applyAiScaffoldToProject(
            projectId = active.id,
            proposal = proposal,
            selectedFieldIds = selectedFieldIds,
            editedValues = editedValues,
            explicitlyReplacedFieldIds = explicitlyReplacedFieldIds,
        )) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                replaceStudentProjectInList(result.value)
                studentProjectSaveError = null
                settleProjectAiScaffold(
                    requestId,
                    ProjectAiScaffoldDecision.APPLY,
                    creditCost,
                    projectAlreadyApplied = true,
                )
                result.value
            }
            else -> {
                studentProjectSaveError = studentProjectDraftFlowMessage(result)
                null
            }
        }
    }

    fun resumeStudentProject(projectId: String) {
        if (!requireDestinationAccess(EvidriloDestination.PROJECTS)) return
        releaseActiveProjectAiPreview()
        when (val result = studentProjectDraftFlow.resume(projectId)) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                studentProjectSaveError = null
                studentProjectEditorIsDirty = false
                studentProjectExitConfirmation = false
                navigationState = navigationState.open(EvidriloDestination.PROJECT_EDITOR)
            }
            else -> studentProjectNotice = studentProjectDraftFlowMessage(result)
        }
    }

    fun saveStudentProject(
        title: String,
        fieldValues: Map<String, String>,
        sources: List<StudentProjectSourceRecord>,
        themes: List<StudentProjectSynthesisTheme>,
        claimEvidenceSourceIds: Set<String>,
        evidenceItems: List<dev.nextgen.mobile.domain.project.StudentProjectEvidenceItem>,
        findings: List<dev.nextgen.mobile.domain.project.StudentProjectFindingRecord>,
        evidenceRelations: List<dev.nextgen.mobile.domain.project.StudentProjectEvidenceRelation>,
        claims: List<StudentProjectClaimRecord>,
        limitationActions: List<StudentProjectLimitationActionRecord>,
        checkpoint: Boolean = true,
    ): StudentProjectDraft? {
        if (!requireDestinationAccess(EvidriloDestination.PROJECT_EDITOR)) return null
        val active = activeStudentProjectDraft ?: return null
        return when (val result = studentProjectDraftFlow.update(
            active.id,
            title,
            fieldValues,
            sources,
            themes,
            claimEvidenceSourceIds,
            evidenceItems,
            findings,
            evidenceRelations,
            claims,
            limitationActions = limitationActions,
            checkpoint = checkpoint,
        )) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                replaceStudentProjectInList(result.value)
                studentProjectSaveError = null
                result.value
            }
            else -> {
                studentProjectSaveError = studentProjectDraftFlowMessage(result)
                null
            }
        }
    }

    suspend fun addStudentProjectAttachment(
        fileName: String,
        bytes: ByteArray,
        ensureActive: () -> Unit,
    ): StudentProjectDraftFlowResult<StudentProjectAttachmentAddReceipt> {
        if (!requireDestinationAccess(EvidriloDestination.PROJECT_EDITOR)) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_SIGN_IN_REQUIRED")
        }
        val active = activeStudentProjectDraft ?: return StudentProjectDraftFlowResult.NotFound
        val result = withContext(NonCancellable + Dispatchers.Default) {
            studentProjectDraftFlow.addAttachment(active.id, fileName, bytes, ensureActive)
        }
        if (result is StudentProjectDraftFlowResult.Value) {
            activeStudentProjectDraft = result.value.project
            replaceStudentProjectInList(result.value.project)
            studentProjectSaveError = null
            studentProjectEditorIsDirty = false
        }
        return result
    }

    suspend fun removeStudentProjectAttachment(
        attachmentId: String,
    ): StudentProjectDraftFlowResult<StudentProjectAttachmentRemoveReceipt> {
        if (!requireDestinationAccess(EvidriloDestination.PROJECT_EDITOR)) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_SIGN_IN_REQUIRED")
        }
        val active = activeStudentProjectDraft ?: return StudentProjectDraftFlowResult.NotFound
        val result = withContext(Dispatchers.Default) {
            studentProjectDraftFlow.removeAttachment(active.id, attachmentId)
        }
        if (result is StudentProjectDraftFlowResult.Value) {
            activeStudentProjectDraft = result.value.project
            replaceStudentProjectInList(result.value.project)
            studentProjectSaveError = null
            studentProjectEditorIsDirty = false
        }
        return result
    }

    fun restoreStudentProjectRevision(revision: Int): StudentProjectDraft? {
        if (!requireDestinationAccess(EvidriloDestination.PROJECT_EDITOR)) return null
        val active = activeStudentProjectDraft ?: return null
        return when (val result = studentProjectDraftFlow.restoreRevision(active.id, revision)) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                replaceStudentProjectInList(result.value)
                studentProjectEditorIsDirty = false
                studentProjectSaveError = null
                result.value
            }
            else -> {
                studentProjectSaveError = studentProjectDraftFlowMessage(result)
                null
            }
        }
    }

    fun handleStudentProjectAction(result: StudentProjectDraftFlowResult<*>): Boolean = when (result) {
            is StudentProjectDraftFlowResult.Value -> {
                studentProjectListReload += 1
                studentProjectNotice = null
                true
            }
            else -> {
                studentProjectNotice = studentProjectDraftFlowMessage(result)
                false
            }
        }

    suspend fun importStudentProject(
        payload: StudentProjectArchiveImportPayload,
        importAsCopy: Boolean,
        archiveWhenAtCapacity: Boolean,
        ensureActive: () -> Unit,
    ): StudentProjectDraftFlowResult<StudentProjectImportReceipt> {
        if (!requireDestinationAccess(EvidriloDestination.PROJECTS)) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_SIGN_IN_REQUIRED")
        }
        val result = withContext(Dispatchers.Default) {
            ensureActive()
            studentProjectDraftFlow.importValidatedArchive(
                preview = payload.preview,
                archiveBytes = payload.bytes,
                attachmentStore = studentProjectAttachmentStore,
                importAsCopy = importAsCopy,
                archiveWhenAtCapacity = archiveWhenAtCapacity,
                ensureActive = ensureActive,
            )
        }
        if (result is StudentProjectDraftFlowResult.Value) {
            replaceStudentProjectInList(result.value.project)
            studentProjectNotice = when {
                result.value.attachmentCleanupFailed -> "Project and attachments were imported, but temporary attachment cleanup did not finish. The saved project remains available."
                result.value.importedAsCopy -> "Project imported as a separate copy with remapped IDs, relationships, and attachments."
                result.value.archivedToRespectCapacity -> "Project imported as archived so it does not use an active project slot."
                else -> "Project imported to this device. It has not been uploaded or academically evaluated."
            }
        }
        return result
    }

    fun restoreStudentProjectFromArchive(
        project: StudentProjectDraft,
    ): StudentProjectDraftFlowResult<StudentProjectImportReceipt> {
        if (!requireDestinationAccess(EvidriloDestination.PROJECTS)) {
            return StudentProjectDraftFlowResult.Rejected("PROJECT_SIGN_IN_REQUIRED")
        }
        val result = studentProjectDraftFlow.restoreImportedProjectAsRevision(project)
        if (result is StudentProjectDraftFlowResult.Value) {
            replaceStudentProjectInList(result.value.project)
            studentProjectNotice = "Archive content was restored as a new local revision. Earlier project history remains available."
        }
        return result
    }

    LaunchedEffect(accountSession) {
        val nextAccountId = currentBillingAccountId()
        val previousAccountId = projectAiBoundAccountId
        projectAiConsentRequestGeneration += 1
        projectAiConsentState = ProjectAiConsentUiState.Unknown
        if (previousAccountId != null && previousAccountId != nextAccountId) {
            projectAiRequestToken = null
            projectAiRequestInFlight = false
            projectAiScaffoldState = ProjectAiScaffoldUiState.Idle
        }
        projectAiBoundAccountId = nextAccountId
    }

    fun archiveStudentProject(project: StudentProjectDraft): Boolean =
        requireDestinationAccess(EvidriloDestination.PROJECTS) &&
            handleStudentProjectAction(studentProjectDraftFlow.archive(project.id))

    fun completeStudentProject(project: StudentProjectDraft): Boolean =
        requireDestinationAccess(EvidriloDestination.PROJECTS) &&
            handleStudentProjectAction(studentProjectDraftFlow.markCompleted(project.id))

    fun trashStudentProject(project: StudentProjectDraft): Boolean =
        requireDestinationAccess(EvidriloDestination.PROJECTS) &&
            handleStudentProjectAction(studentProjectDraftFlow.moveToTrash(project.id))

    fun restoreStudentProject(project: StudentProjectDraft, archivedWhenAtLimit: Boolean): Boolean =
        requireDestinationAccess(EvidriloDestination.PROJECTS) && handleStudentProjectAction(
            if (project.status == StudentProjectStatus.TRASHED) {
                studentProjectDraftFlow.restoreFromTrash(project.id, archivedWhenAtLimit)
            } else {
                studentProjectDraftFlow.reactivate(project.id)
            },
        )

    fun permanentlyDeleteStudentProject(project: StudentProjectDraft): Boolean =
        requireDestinationAccess(EvidriloDestination.PROJECTS) &&
            handleStudentProjectAction(studentProjectDraftFlow.permanentlyDelete(project.id))

    LaunchedEffect(navigationState.current, studentProjectListReload, accountSession, accountRestoreComplete) {
        if (navigationState.current == EvidriloDestination.PROJECTS ||
            navigationState.current == EvidriloDestination.HOME
        ) {
            studentProjectListState = StudentProjectListUiState.Loading
            val result = studentProjectDraftFlow.list()
            studentProjectListState = result.toStudentProjectListUiState()
            studentProjectNotice = when (result) {
                is StudentProjectDraftFlowResult.Value -> null
                else -> studentProjectDraftFlowMessage(result)
            }
            projectProEntitlementActive.value = false
            if (accountSession is AccountSession.SignedIn) {
                val entitlementToken = billingRequestGate.begin(currentBillingAccountId())
                billingGateway.refreshAccess { outcome ->
                    if (billingRequestGate.isCurrent(entitlementToken, currentBillingAccountId()) &&
                        (navigationState.current == EvidriloDestination.PROJECTS || navigationState.current == EvidriloDestination.HOME)
                    ) {
                        projectProEntitlementActive.value = outcome is BillingOutcome.Access && outcome.value == PremiumAccess.UNLOCKED
                    }
                }
            }
        }
    }
    LaunchedEffect(navigationState.current, projectTemplateCatalogReload) {
        if (navigationState.current == EvidriloDestination.PROJECT_CATALOG) {
            projectTemplateFamiliesState = ProjectTemplateRemoteUiState.Loading
            projectTemplateFamiliesState = projectTemplateCatalogGateway.listFamilies().toRemoteUiState()
        }
    }
    LaunchedEffect(navigationState.current, selectedProjectTemplateFamily, projectTemplateFamilyReload) {
        if (navigationState.current == EvidriloDestination.PROJECT_FAMILY_DETAIL) {
            projectTemplateFamilyState = ProjectTemplateRemoteUiState.Loading
            projectTemplateFamilyState = projectTemplateCatalogGateway
                .listTemplates(selectedProjectTemplateFamily)
                .toRemoteUiState()
        }
    }
    LaunchedEffect(navigationState.current, selectedProjectTemplateSummary, projectTemplateDetailReload) {
        val summary = selectedProjectTemplateSummary
        if (navigationState.current == EvidriloDestination.PROJECT_TEMPLATE_DETAIL && summary != null) {
            projectTemplateDetailState = ProjectTemplateRemoteUiState.Loading
            projectTemplateDetailState = projectTemplateCatalogGateway
                .getTemplate(summary.id, summary.version, summary.family)
                .toRemoteUiState()
        }
    }
    LaunchedEffect(accountController) {
        if (TEMPORARY_GUEST_MODE_ENABLED) return@LaunchedEffect
        val result = try {
            accountGateway.restore()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AccountGatewayResult.Offline
        }
        acceptAccountGatewayResult(result)
        accountRestoreComplete = true
    }
    DisposableEffect(accountGateway) {
        if (TEMPORARY_GUEST_MODE_ENABLED) {
            onDispose { }
        } else {
            val unsubscribe = subscribeAccountAuthRedirect { url ->
                if (!currentAccountBusy) {
                    accountBusy = true
                    val existingAccountIsActive = currentAccountSession is AccountSession.SignedIn
                    if (!existingAccountIsActive) accountSession = accountController.beginSignIn()
                    accountScope.launch {
                        try {
                            val result = try {
                                accountGateway.completeRedirect(url)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                AccountGatewayResult.Offline
                            }
                            acceptAccountGatewayResult(result)
                        } finally {
                            accountBusy = false
                        }
                    }
                }
            }
            onDispose { unsubscribe() }
        }
    }
    fun performAccountOperation(
        showSigningInState: Boolean,
        onResult: (AccountGatewayResult) -> Unit = {},
        operation: suspend () -> AccountGatewayResult,
    ) {
        if (TEMPORARY_GUEST_MODE_ENABLED) return
        if (!accountBusy) {
            accountExportJson = null
            accountExportError = null
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
                    acceptAccountGatewayResult(result)
                    onResult(result)
                } finally {
                    accountBusy = false
                }
            }
        }
    }
    var state by remember(savedSnapshot) {
        mutableStateOf(savedSnapshot?.restore(reducer) ?: ConclusionState.Intro)
    }
    val case = targetCaseFor(state, baseCase, evidenceChangeCase)
    val backendCaseVersionId = requireNotNull(baseCase.remoteCaseVersionId)
    LaunchedEffect(accountSession, accountRestoreComplete, state is ConclusionState.Intro) {
        if (!accountRestoreComplete) return@LaunchedEffect
        val account = (accountSession as? AccountSession.SignedIn)?.account
        if (state !is ConclusionState.Intro) {
            remoteContentStatus = "Using the case already loaded for this active review"
            return@LaunchedEffect
        }
        if (account == null || !account.emailVerified) {
            remoteBaseCase = null
            remoteContentStatus = "Offline-ready bundled case"
            return@LaunchedEffect
        }
        when (val result = publishedCaseGateway.get(requireNotNull(bundledBaseCase.remoteCaseVersionId))) {
            is PublishedCaseGatewayResult.Found -> {
                val adapted = result.case.toBundledEvaluatorCase(bundledBaseCase)
                if (adapted == null) {
                    remoteBaseCase = null
                    remoteContentStatus = "Bundled case kept: published content did not match the local evaluator contract"
                } else {
                    remoteBaseCase = adapted
                    remoteContentStatus = "Published case connected · ${result.case.contentHash.take(12)}…"
                }
            }
            is PublishedCaseGatewayResult.Deferred -> {
                remoteBaseCase = null
                remoteContentStatus = "Bundled case kept until a verified account is available"
            }
            is PublishedCaseGatewayResult.Failed -> {
                remoteBaseCase = null
                remoteContentStatus = "Bundled case kept · published content is unavailable"
            }
        }
    }
    LaunchedEffect(accountSession, accountRestoreComplete) {
        val signedInAccountId = (accountSession as? AccountSession.SignedIn)?.account?.accountId
        if (aiConversationAccountId != signedInAccountId) {
            aiConversationSession = null
            aiConversationContextKey = null
            aiConversationAccountId = signedInAccountId
            aiConversationClearState = EvidriloAiConversationClearState.Idle
        }
        aiCredits = null
        aiAssistState = if (accountRestoreComplete && accountSession is AccountSession.SignedIn) {
            EvidriloAiAssistUiState.Loading
        } else {
            EvidriloAiAssistUiState.SignInRequired
        }
        if (!accountRestoreComplete || accountSession !is AccountSession.SignedIn) {
            return@LaunchedEffect
        }
        when (val result = aiGateway.getCredits()) {
            is AiGatewayResult.CreditsFound -> {
                aiCredits = result.value
                aiAssistState = EvidriloAiAssistUiState.Ready(result.value)
            }
            is AiGatewayResult.Deferred -> {
                aiAssistState = EvidriloAiAssistUiState.Unavailable(
                    message = "AI credits are available only to a verified account.",
                    retryable = false,
                )
            }
            is AiGatewayResult.Fallback -> {
                aiAssistState = EvidriloAiAssistUiState.Unavailable(
                    message = "AI credits could not be loaded; deterministic feedback remains available.",
                    retryable = true,
                )
            }
            is AiGatewayResult.Failed -> {
                aiAssistState = EvidriloAiAssistUiState.Unavailable(
                    message = "AI credits are temporarily unavailable; deterministic feedback remains available.",
                    retryable = result.retryable,
                )
            }
            is AiGatewayResult.AssistFound -> {
                aiAssistState = EvidriloAiAssistUiState.Unavailable(
                    message = "AI credits returned an unexpected response.",
                    retryable = false,
                )
            }
        }
    }
    LaunchedEffect(accountSession, accountRestoreComplete) {
        platformProgress = null
        platformEntitlements = null
        platformStatus = if (accountRestoreComplete && accountSession is AccountSession.SignedIn) {
            "Refreshing verified platform projections…"
        } else {
            null
        }
        if (!accountRestoreComplete || accountSession !is AccountSession.SignedIn) {
            return@LaunchedEffect
        }
        val progressResult = projectionGateway.getProgress()
        val entitlementResult = projectionGateway.getEntitlements()
        platformProgress = (progressResult as? PlatformProjectionResult.Found<PlatformProgressSummary>)?.value
        platformEntitlements = (entitlementResult as? PlatformProjectionResult.Found<PlatformEntitlements>)?.value
        platformStatus = when {
            platformProgress != null && platformEntitlements != null ->
                "Progress and entitlement projections are up to date"
            progressResult is PlatformProjectionResult.Deferred || entitlementResult is PlatformProjectionResult.Deferred ->
                "Platform projections need a verified session; local data remains available"
            else -> "Platform projections are temporarily unavailable; local data remains available"
        }
    }
    val initialHistoryLoad = remember {
        recoverCorruptLocalStorage(historyStore.load()) { historyStore.clear() }
    }
    var historySnapshot by remember { mutableStateOf(initialHistoryLoad.value) }
    var historyStorageStatus by remember { mutableStateOf(initialHistoryLoad.status) }
    val hasUnfinishedCase = state !is ConclusionState.Intro
    val hasCompletedCase = historySnapshot != null
    val notificationScheduleLabel = notificationPreferences.scheduleLabel(
        notificationPreferences.activeCategories(
            hasUnfinishedCase = hasUnfinishedCase,
            hasCompletedCase = hasCompletedCase,
        ),
    )
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentNotificationPermissionUiState by rememberUpdatedState(
        NotificationPermissionUiState(
            permission = notificationPermission,
            statusMessage = notificationStatusMessage,
        ),
    )
    DisposableEffect(lifecycleOwner, notificationScheduler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accountScope.launch {
                    val refreshed = notificationScheduler.permissionState()
                    val nextUiState = currentNotificationPermissionUiState.withPermission(refreshed)
                    notificationPermission = nextUiState.permission
                    notificationStatusMessage = nextUiState.statusMessage
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(notificationScheduler) {
        notificationPermission = notificationScheduler.permissionState()
    }
    LaunchedEffect(
        notificationPreferences,
        notificationPermission,
        hasUnfinishedCase,
        hasCompletedCase,
    ) {
        if (!notificationPreferences.enabled) {
            notificationScheduler.cancelAll()
        } else if (notificationPermission == NotificationPermissionState.GRANTED) {
            when (val result = notificationScheduler.schedule(
                preferences = notificationPreferences,
                hasUnfinishedCase = hasUnfinishedCase,
                hasCompletedCase = hasCompletedCase,
            )) {
                is dev.nextgen.mobile.notifications.LocalNotificationScheduleResult.Scheduled -> {
                    notificationStatusMessage = if (result.categories.size == 0) {
                        "No eligible reminder is scheduled yet."
                    } else {
                        "Reminders are scheduled on this device."
                    }
                }
                dev.nextgen.mobile.notifications.LocalNotificationScheduleResult.PermissionDenied -> {
                    notificationPermission = NotificationPermissionState.DENIED
                    notificationStatusMessage =
                        SYSTEM_NOTIFICATION_PERMISSION_OFF_MESSAGE
                }
                dev.nextgen.mobile.notifications.LocalNotificationScheduleResult.Unavailable -> {
                    notificationStatusMessage =
                        "Local reminders are unavailable in this build; the free core remains usable."
                }
                is dev.nextgen.mobile.notifications.LocalNotificationScheduleResult.Failed -> {
                    notificationStatusMessage = result.message
                }
            }
        }
    }
    LaunchedEffect(accountSession, accountRestoreComplete) {
        if (!accountRestoreComplete || accountSession !is AccountSession.SignedIn) return@LaunchedEffect

        when (val result = notificationPreferencesGateway.refresh()) {
            is NotificationPreferencesGatewayResult.Found -> {
                val remote = result.value
                val localDefaults = NotificationPreferences()
                if (remote.revision > 0 || notificationPreferences == localDefaults) {
                    notificationPreferences = remote.preferences
                    notificationStorageStatus = notificationPreferencesStore.save(remote.preferences).status
                    notificationStatusMessage = "Reminder preferences restored for this account."
                } else {
                    when (notificationPreferencesGateway.save(notificationPreferences)) {
                        is NotificationPreferencesGatewayResult.Saved ->
                            notificationStatusMessage = "Local reminder preferences backed up to your account."
                        NotificationPreferencesGatewayResult.Conflict ->
                            notificationStatusMessage = "Reminder preferences changed elsewhere; local settings were kept."
                        else -> Unit
                    }
                }
            }
            NotificationPreferencesGatewayResult.Conflict ->
                notificationStatusMessage = "Reminder preferences changed elsewhere; local settings remain available."
            is NotificationPreferencesGatewayResult.Deferred,
            is NotificationPreferencesGatewayResult.Failed,
            is NotificationPreferencesGatewayResult.Saved,
            -> Unit
        }
    }
    fun saveNotificationPreferences(next: NotificationPreferences) {
        notificationStorageStatus = notificationPreferencesStore.save(next).status
        notificationPreferences = next
        if (accountSession is AccountSession.SignedIn) {
            accountScope.launch {
                when (notificationPreferencesGateway.save(next)) {
                    is NotificationPreferencesGatewayResult.Saved ->
                        notificationStatusMessage = "Reminder preferences saved on this device and account."
                    NotificationPreferencesGatewayResult.Conflict ->
                        notificationStatusMessage = "Reminder preferences changed elsewhere; local settings were kept."
                    is NotificationPreferencesGatewayResult.Deferred,
                    is NotificationPreferencesGatewayResult.Failed,
                    is NotificationPreferencesGatewayResult.Found,
                    -> Unit
                }
            }
        }
    }
    val enableNotifications: () -> Unit = {
        if (!notificationBusy) {
            notificationBusy = true
            accountScope.launch {
                val permission = notificationScheduler.requestPermission()
                notificationPermission = permission
                if (permission == NotificationPermissionState.GRANTED) {
                    saveNotificationPreferences(notificationPreferences.copy(enabled = true))
                    notificationStatusMessage = "Local reminders enabled."
                } else {
                    notificationStatusMessage = when (permission) {
                        NotificationPermissionState.DENIED ->
                            NOTIFICATION_PERMISSION_REQUEST_DENIED_MESSAGE
                        NotificationPermissionState.UNAVAILABLE ->
                            "Local reminders are unavailable in this build."
                        NotificationPermissionState.UNKNOWN ->
                            "Permission was not completed. You can try again later."
                        NotificationPermissionState.GRANTED -> "Local reminders enabled."
                    }
                }
                notificationBusy = false
            }
        }
    }
    val disableNotifications: () -> Unit = {
        notificationScheduler.cancelAll()
        saveNotificationPreferences(notificationPreferences.copy(enabled = false))
        notificationStatusMessage = "Local reminders turned off."
    }
    val targetDraft = targetDraftFor(state, case)
    val aiAssistantContextKey = "${currentBillingAccountId().orEmpty()}|" + evidriloAssistantContextKey(
        case,
        targetDraft,
        targetEvaluationFor(state)?.primaryFeedback,
    )
    val latestAiAssistantContextKey = rememberUpdatedState(aiAssistantContextKey)
    fun recoverFromStaleAiRequest(requestContextKey: String) {
        if (aiAssistRequestContextKey == requestContextKey &&
            latestAiAssistantContextKey.value != requestContextKey
        ) {
            aiAssistRequestContextKey = null
            aiAssistState = if (currentAccountSession is AccountSession.SignedIn) {
                aiCredits?.let { EvidriloAiAssistUiState.Ready(it) }
                    ?: EvidriloAiAssistUiState.Unavailable(
                        "The case changed while AI was responding. Open the current verification to try again.",
                        retryable = false,
                    )
            } else {
                EvidriloAiAssistUiState.SignInRequired
            }
        }
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
        notificationStorageStatus,
    )
    fun emitAnalytics(event: AnalyticsEvent) {
        if (analyticsTransmissionAllowed(analyticsConsent, TEMPORARY_GUEST_MODE_ENABLED)) {
            accountScope.launch {
                analyticsGateway.sendWithRetry(event, analyticsConsent)
            }
        }
    }
    val requestAiAssist: (AiAssistPurpose, String, List<AiConversationHistoryMessage>) -> Unit = { purpose, question, history ->
        val feedback = targetEvaluationFor(state)?.primaryFeedback
        when {
            accountSession !is AccountSession.SignedIn -> aiAssistState = EvidriloAiAssistUiState.SignInRequired
            feedback == null -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                message = "Complete a deterministic verification first; there is no feedback to explain yet.",
                retryable = false,
            )
            aiAssistState is EvidriloAiAssistUiState.Loading -> Unit
            case.remoteCaseVersionId == null -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                message = "AI assistance is available only when this reviewed case has a verified published context. Your deterministic feedback remains available.",
                retryable = false,
            )
            else -> {
                val caseVersionId = requireNotNull(case.remoteCaseVersionId)
                val accountId = currentBillingAccountId()
                val context = AiAssistContext(
                    caseVersionId = caseVersionId,
                    feedbackCode = feedback.code,
                    feedbackStatus = feedback.status.name,
                    anchorIds = feedback.anchorIds,
                    limitationIds = targetDraft.limitationRefs,
                    claimText = targetDraft.claimText.takeIf { it.isNotBlank() },
                    claimScope = targetDraft.scope?.name,
                    nextAction = feedback.nextAction,
                )
                val learnerLimitation = targetDraft.limitationNote.takeIf { it.isNotBlank() }
                val requestContextKey = aiAssistantContextKey
                aiAssistRequestContextKey = requestContextKey
                aiAssistState = EvidriloAiAssistUiState.Loading
                accountScope.launch {
                    var session = if (aiConversationContextKey == requestContextKey &&
                        aiConversationAccountId == accountId
                    ) aiConversationSession else null
                    if (session == null) {
                        val previousSession = aiConversationSession
                        if (previousSession != null && aiConversationAccountId == accountId) {
                            aiGateway.clearConversation(previousSession.sessionId)
                        }
                        aiConversationSession = null
                        aiConversationContextKey = null
                        val started = try {
                            aiGateway.startConversation(
                                context = context,
                                learnerLimitation = learnerLimitation,
                                locale = "en-US",
                                optedIn = true,
                                idempotencyKey = newAnalyticsEventId(),
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            AiConversationGatewayResult.Failed(AiGatewayResult.Failed("AI_UNAVAILABLE", retryable = true))
                        }
                        when (started) {
                            is AiConversationGatewayResult.SessionStarted -> {
                                session = started.value
                                aiConversationSession = started.value
                                aiConversationContextKey = requestContextKey
                                aiConversationAccountId = accountId
                            }
                            is AiConversationGatewayResult.Deferred -> {
                                if (latestAiAssistantContextKey.value == requestContextKey) {
                                    aiAssistState = EvidriloAiAssistUiState.Unavailable(
                                        "AI assistance needs a verified account and an active session.",
                                        retryable = false,
                                    )
                                }
                                return@launch
                            }
                            is AiConversationGatewayResult.Fallback -> {
                                if (latestAiAssistantContextKey.value == requestContextKey) {
                                    aiAssistState = EvidriloAiAssistUiState.Unavailable(
                                        "A grounded AI conversation could not be started. Your deterministic feedback is unchanged.",
                                        retryable = false,
                                    )
                                }
                                return@launch
                            }
                            is AiConversationGatewayResult.Failed -> {
                                if (latestAiAssistantContextKey.value == requestContextKey) {
                                    aiAssistState = EvidriloAiAssistUiState.Unavailable(
                                        "AI assistance is temporarily unavailable; your local feedback was kept.",
                                        retryable = started.error.retryable && !started.error.outcomeUnknown,
                                    )
                                }
                                return@launch
                            }
                            is AiConversationGatewayResult.TurnReceived,
                            is AiConversationGatewayResult.Cleared -> return@launch
                        }
                    }
                    val activeSession = session ?: return@launch
                    if (latestAiAssistantContextKey.value != requestContextKey ||
                        aiAssistRequestContextKey != requestContextKey
                    ) {
                        aiGateway.clearConversation(activeSession.sessionId)
                        if (aiConversationSession?.sessionId == activeSession.sessionId) {
                            aiConversationSession = null
                            aiConversationContextKey = null
                        }
                        recoverFromStaleAiRequest(requestContextKey)
                        return@launch
                    }
                    val result = try {
                        aiGateway.sendConversationTurn(
                            sessionId = activeSession.sessionId,
                            purpose = purpose,
                            input = question.trim().take(2_000),
                            locale = "en-US",
                            optedIn = true,
                            context = context,
                            learnerLimitation = learnerLimitation,
                            history = history.takeLast(4),
                            idempotencyKey = newAnalyticsEventId(),
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        AiConversationGatewayResult.Failed(AiGatewayResult.Failed("AI_UNAVAILABLE", retryable = true))
                    }
                    if (latestAiAssistantContextKey.value != requestContextKey ||
                        aiAssistRequestContextKey != requestContextKey
                    ) {
                        if (aiConversationSession?.sessionId == activeSession.sessionId) {
                            aiGateway.clearConversation(activeSession.sessionId)
                            aiConversationSession = null
                            aiConversationContextKey = null
                        }
                        recoverFromStaleAiRequest(requestContextKey)
                        return@launch
                    }
                    when (result) {
                        is AiConversationGatewayResult.TurnReceived -> {
                            val refreshedCredits = aiGateway.getCredits()
                            val remaining = (refreshedCredits as? AiGatewayResult.CreditsFound)?.value?.available
                            if (refreshedCredits is AiGatewayResult.CreditsFound) {
                                aiCredits = refreshedCredits.value
                            }
                            aiConversationSession = activeSession.copy(turnsUsed = result.value.turnsUsed)
                            aiAssistState = EvidriloAiAssistUiState.Answer(
                                text = requireNotNull(result.value.text),
                                remainingCredits = remaining,
                                groundedAnchorIds = result.value.groundedAnchorIds,
                                requestId = result.value.requestId,
                                turnsUsed = result.value.turnsUsed,
                                proposal = result.value.proposal,
                            )
                        }
                        is AiConversationGatewayResult.Fallback -> {
                            val refreshedCredits = aiGateway.getCredits()
                            if (refreshedCredits is AiGatewayResult.CreditsFound) aiCredits = refreshedCredits.value
                            aiConversationSession = activeSession.copy(turnsUsed = result.turnsUsed ?: activeSession.turnsUsed)
                            aiAssistState = EvidriloAiAssistUiState.Unavailable(
                                message = "AI could not answer this turn. No draft change was made; deterministic feedback remains authoritative.",
                                retryable = result.reasonCode == "AI_PROVIDER_UNAVAILABLE" ||
                                    result.reasonCode == "AI_PROVIDER_TIMEOUT" ||
                                    result.reasonCode == "AI_UNAVAILABLE",
                                turnsUsed = result.turnsUsed,
                                requestId = result.requestId,
                            )
                        }
                        is AiConversationGatewayResult.Deferred -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                            message = "AI assistance needs a verified account and an active session.",
                            retryable = false,
                        )
                        is AiConversationGatewayResult.Failed -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                            message = if (result.error.outcomeUnknown) {
                                "The server may have received this turn, so it was not retried automatically. Check credits before continuing."
                            } else {
                                "AI assistance is temporarily unavailable; your local feedback was kept."
                            },
                            retryable = result.error.retryable && !result.error.outcomeUnknown,
                        )
                        is AiConversationGatewayResult.SessionStarted,
                        is AiConversationGatewayResult.Cleared -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                            message = "AI returned an unexpected conversation response.",
                            retryable = false,
                        )
                    }
                }
            }
        }
    }
    suspend fun clearAiConversationSession(sessionId: String): AiConversationGatewayResult = try {
            aiGateway.clearConversation(sessionId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AiConversationGatewayResult.Failed(
                AiGatewayResult.Failed(
                    code = "AI_UNAVAILABLE",
                    retryable = true,
                    outcomeUnknown = true,
                ),
            )
        }
    val clearAiConversation: () -> Unit = {
        val session = aiConversationSession
        val accountId = currentBillingAccountId()
        val sameAccount = accountId != null && aiConversationAccountId == accountId
        aiConversationSession = null
        aiConversationContextKey = null
        aiConversationClearState = if (accountId != null) {
            EvidriloAiConversationClearState.Clearing(
                sessionId = session?.sessionId?.takeIf { sameAccount },
                accountId = accountId,
            )
        } else {
            EvidriloAiConversationClearState.Idle
        }
        accountScope.launch {
            val clearResult = if (session != null && sameAccount) {
                clearAiConversationSession(session.sessionId)
            } else {
                null
            }
            if (latestBillingAccountId.value != accountId) return@launch
            val refreshed = try {
                aiGateway.getCredits()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                AiGatewayResult.Failed("AI_UNAVAILABLE", retryable = true)
            }
            when (refreshed) {
                is AiGatewayResult.CreditsFound -> {
                    aiCredits = refreshed.value
                    aiAssistState = EvidriloAiAssistUiState.Ready(refreshed.value)
                }
                is AiGatewayResult.Deferred -> aiAssistState = EvidriloAiAssistUiState.SignInRequired
                else -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                    "The local chat was cleared, but credits could not be refreshed.",
                    retryable = refreshed is AiGatewayResult.Failed && refreshed.retryable,
                )
            }
            if (latestBillingAccountId.value == accountId) {
                aiConversationClearState = if (session != null && sameAccount && clearResult != null) {
                    aiConversationClearStateAfterGatewayResult(
                        sessionId = session.sessionId,
                        accountId = requireNotNull(accountId),
                        result = clearResult,
                    )
                } else {
                    EvidriloAiConversationClearState.Idle
                }
            }
        }
    }
    val retryClearAiConversation: (String) -> Unit = { sessionId ->
        val failure = aiConversationClearState as? EvidriloAiConversationClearState.Failed
        val accountId = currentBillingAccountId()
        if (failure?.sessionId == sessionId &&
            failure.accountId == accountId &&
            failure.retryable
        ) {
            aiConversationClearState = EvidriloAiConversationClearState.Clearing(sessionId, failure.accountId)
            accountScope.launch {
                val result = clearAiConversationSession(sessionId)
                if (latestBillingAccountId.value == failure.accountId) {
                    aiConversationClearState = aiConversationClearStateAfterGatewayResult(
                        sessionId = sessionId,
                        accountId = failure.accountId,
                        result = result,
                    )
                }
            }
        }
    }
    val retryAiAssist: () -> Unit = {
        if (accountSession is AccountSession.SignedIn) {
            accountScope.launch {
                when (val result = aiGateway.getCredits()) {
                    is AiGatewayResult.CreditsFound -> {
                        aiCredits = result.value
                        aiAssistState = EvidriloAiAssistUiState.Ready(
                            result.value,
                            conversationTurnsUsed = aiConversationSession?.turnsUsed,
                        )
                    }
                    else -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                        message = "AI credits are still unavailable; deterministic feedback remains available.",
                        retryable = result is AiGatewayResult.Failed && result.retryable,
                    )
                }
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
    val dispatch: (ConclusionEvent) -> Unit = dispatch@{ event ->
        val currentState = state
        if (event == ConclusionEvent.Reset ||
            event == ConclusionEvent.Submit ||
            event == ConclusionEvent.BeginRevision ||
            event == ConclusionEvent.BeginEvidenceChange ||
            event is ConclusionEvent.BeginEvidenceChangeFromSavedDraft ||
            event == ConclusionEvent.SubmitEvidenceChange ||
            event == ConclusionEvent.FinishEvidenceChange
        ) {
            audioCoordinator.stop()
        }
        if (event == ConclusionEvent.Begin && currentState is ConclusionState.Intro) {
            activeAttemptId = newAnalyticsEventId()
            aiCredits?.let { aiAssistState = EvidriloAiAssistUiState.Ready(it) }
        }
        if (event == ConclusionEvent.Reset) {
            completedHistorySnapshot(currentState)?.let { snapshot ->
                val result = historyStore.save(snapshot)
                historyStorageStatus = result.status
                if (result == LocalStorageWriteResult.SAVED) {
                    historySnapshot = snapshot
                }
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
            event == ConclusionEvent.BeginEvidenceChange ||
                event is ConclusionEvent.BeginEvidenceChangeFromSavedDraft ->
                playEffect(AudioEffectId.CHALLENGE_REVEAL)
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
        completedHistorySnapshot(nextState)?.let { snapshot ->
            val result = historyStore.save(snapshot)
            historyStorageStatus = result.status
            if (result == LocalStorageWriteResult.SAVED) {
                historySnapshot = snapshot
            }
        }
    }
    val canApplyAiProposal: (AiConversationProposal) -> Boolean = { proposal ->
        val currentState = state
        val editable = when (currentState) {
            is ConclusionState.Drafting,
            is ConclusionState.Revision,
            is ConclusionState.Incomplete -> true
            is ConclusionState.Feedback -> currentState.canRevise
            else -> false
        }
        editable && applyGroundedAiProposal(targetDraftFor(currentState, case), proposal) != null
    }
    val applyAiProposal: (AiConversationProposal) -> Boolean = apply@{ proposal ->
        val currentState = state
        val updatedDraft = applyGroundedAiProposal(targetDraftFor(currentState, case), proposal)
        if (updatedDraft == null) {
            false
        } else {
            when (currentState) {
                is ConclusionState.Drafting,
                is ConclusionState.Revision,
                is ConclusionState.Incomplete -> dispatch(ConclusionEvent.UpdateDraft(updatedDraft))
                is ConclusionState.Feedback -> {
                    if (!currentState.canRevise) return@apply false
                    dispatch(ConclusionEvent.BeginRevision)
                    if (state !is ConclusionState.Revision) return@apply false
                    dispatch(ConclusionEvent.UpdateDraft(updatedDraft))
                }
                else -> return@apply false
            }
            if (targetDraftFor(state, case) != updatedDraft) {
                false
            } else {
                navigationState = navigationState.open(EvidriloDestination.PRACTICE)
                true
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
    fun dispatchGetStartedTourEvent(event: GetStartedTourEvent) {
        onboardingTour = onboardingTour.reduce(event)
    }

    fun advanceGetStartedTour() {
        val next = onboardingTour.reduce(GetStartedTourEvent.Next)
        onboardingTour = next
        if (next.isComplete) completeGetStartedTour()
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
        // Evidence review is a root workflow. Returning from any utility surface
        // must not strand the user in that surface's stack entry, and this
        // does not reset the persisted/evaluator state.
        releaseActiveProjectAiPreview()
        navigationState = navigationState.resetToHome()
    }
    val openTargetSection: (EvidriloTargetSection) -> Unit = { section ->
        releaseActiveProjectAiPreview()
        navigationState = when (section) {
            EvidriloTargetSection.HOME -> navigationState.selectRoot(EvidriloDestination.HOME)
            EvidriloTargetSection.SOURCES -> navigationState.selectRoot(EvidriloDestination.SOURCES)
            EvidriloTargetSection.EVIDENCE -> navigationState.selectRoot(EvidriloDestination.EVIDENCE)
            EvidriloTargetSection.ACTION -> navigationState.selectRoot(EvidriloDestination.ACTION)
            EvidriloTargetSection.PROFILE -> navigationState.selectRoot(EvidriloDestination.PROFILE)
        }
    }
    val startTargetPractice: () -> Unit = {
        if (requireDestinationAccess(EvidriloDestination.PRACTICE)) {
            if (state is ConclusionState.Intro) {
                dispatch(ConclusionEvent.Begin)
            }
            navigationState = navigationState.open(EvidriloDestination.PRACTICE)
        }
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
        forceShow = onboardingRequested,
    )
    val accountGateVisible = accountRestoreComplete &&
        accountSession !is AccountSession.SignedIn &&
        navigationState.current.requiresAuthenticatedFreeAccess()
    val assistantVisible = !TEMPORARY_GUEST_MODE_ENABLED &&
        accountRestoreComplete &&
        accountSession is AccountSession.SignedIn &&
        !onboardingPresentation.isVisible &&
        !accountGateVisible &&
        !revenueCatPaywallVisible &&
        !customerCenterVisible &&
        premiumState is PremiumPracticeState.Hidden &&
        navigationState.current !in setOf(
            EvidriloDestination.SETTINGS,
            EvidriloDestination.PROFILE,
            EvidriloDestination.ACCOUNT,
            EvidriloDestination.SUPPORT,
            EvidriloDestination.ABOUT,
            EvidriloDestination.PREMIUM,
            EvidriloDestination.PROJECT_CATALOG,
            EvidriloDestination.PROJECT_FAMILY_DETAIL,
            EvidriloDestination.PROJECT_TEMPLATE_DETAIL,
            EvidriloDestination.PROJECTS,
            EvidriloDestination.PROJECT_EDITOR,
        )
    val assistantAiState = if (aiAssistRequestContextKey != null &&
        aiAssistRequestContextKey != aiAssistantContextKey
    ) {
        if (accountSession is AccountSession.SignedIn) {
            aiCredits?.let { EvidriloAiAssistUiState.Ready(it) }
                ?: EvidriloAiAssistUiState.Loading
        } else {
            EvidriloAiAssistUiState.SignInRequired
        }
    } else {
        aiAssistState
    }
    Box(modifier = Modifier.fillMaxSize()) {
    if (!accountRestoreComplete) {
        EvidriloLoadingScreen(mode = EvidriloLoadingMode.APP_BOOTSTRAP)
    } else if (onboardingPresentation.isVisible) {
        EvidriloOnboardingScreen(
            tourState = onboardingTour,
            onNext = ::advanceGetStartedTour,
            onBack = { dispatchGetStartedTourEvent(GetStartedTourEvent.Back) },
            onSkip = ::skipGetStartedTour,
            onStartProject = {
                advanceGetStartedTour()
                navigationState = navigationState.resetToHome().open(EvidriloDestination.PROJECTS)
            },
            storageNotice = onboardingStorageStatus.notice(),
        )
    } else if (accountGateVisible) {
        EvidriloAccountRequiredGate(
            accountConfigured = accountConfiguration.isConfigured,
            onSignIn = { openAccountGate(navigationState.current) },
            onOpenLocalProjects = {
                navigationState = navigationState.resetToHome().open(EvidriloDestination.PROJECTS)
            },
            onOpenSupport = { navigationState = navigationState.open(EvidriloDestination.SUPPORT) },
            storageNotice = onboardingStorageStatus.notice(),
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
            onRetry = openPremium,
            onSelectOffer = { dispatchPremium(PremiumPracticeEvent.SelectOffer(it)) },
            onSelectCase = { dispatchPremium(PremiumPracticeEvent.SelectCase(it)) },
            onBeginCase = { dispatchPremium(PremiumPracticeEvent.BeginSelectedCase) },
            onPracticeEvent = { dispatchPremium(PremiumPracticeEvent.PracticeEvent(it)) },
            onNavigate = { section ->
                closePremium()
                openTargetSection(section)
            },
            onReturnToCatalog = returnToPremiumCatalog,
            backLabel = when (navigationState.stack.dropLast(1).lastOrNull()) {
                EvidriloDestination.SETTINGS -> "Settings"
                EvidriloDestination.HISTORY -> "History"
                EvidriloDestination.GUIDE -> "Guide"
                EvidriloDestination.PRACTICE -> "Review"
                else -> "Home"
            },
            onBack = leavePremium,
            audioState = audioState,
            onNarration = playNarration,
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
            onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
        )
    } else if (navigationState.current == EvidriloDestination.PROJECT_CATALOG) {
        EvidriloProjectTemplateCatalogScreen(
            listState = projectTemplateCatalogListState,
            quickGuideExpanded = projectFamilyQuickGuideExpanded,
            remoteFamilies = projectTemplateFamiliesState,
            onRetryRemoteFamilies = { projectTemplateCatalogReload += 1 },
            onToggleQuickGuide = {
                projectFamilyQuickGuideExpanded = !projectFamilyQuickGuideExpanded
            },
            onBack = { navigationState = navigationState.back() },
            onOpenProjects = {
                studentProjectNotice = null
                navigationState = navigationState.open(EvidriloDestination.PROJECTS)
            },
            onNavigate = openTargetSection,
            onSelectFamily = { family ->
                selectedProjectTemplateFamily = family
                navigationState = navigationState.open(EvidriloDestination.PROJECT_FAMILY_DETAIL)
            },
        )
    } else if (navigationState.current == EvidriloDestination.PROJECT_FAMILY_DETAIL) {
        EvidriloProjectTemplateFamilyScreen(
            overview = projectTemplateFamilyOverview(selectedProjectTemplateFamily),
            remoteTemplates = projectTemplateFamilyState,
            onRetryRemoteTemplates = { projectTemplateFamilyReload += 1 },
            onInspectTemplate = { summary ->
                releaseActiveProjectAiPreview()
                selectedProjectTemplateSummary = summary
                projectTemplateDetailState = ProjectTemplateRemoteUiState.NotRequested
                navigationState = navigationState.open(EvidriloDestination.PROJECT_TEMPLATE_DETAIL)
            },
            onBack = { navigationState = navigationState.back() },
            onNavigate = openTargetSection,
        )
    } else if (navigationState.current == EvidriloDestination.PROJECT_TEMPLATE_DETAIL) {
        EvidriloProjectTemplateDetailScreen(
            templateSummary = selectedProjectTemplateSummary,
            state = projectTemplateDetailState,
            notice = studentProjectNotice,
            projectAiAccountKey = currentBillingAccountId(),
            projectAiState = projectAiScaffoldState,
            projectAiConsentState = projectAiConsentState,
            onRetry = { projectTemplateDetailReload += 1 },
            onRefreshProjectAiConsent = ::refreshProjectAiConsent,
            onGrantProjectAiConsent = ::grantProjectAiConsent,
            onRevokeProjectAiConsent = ::revokeProjectAiConsent,
            onStartProject = ::startStudentProject,
            onRequestProjectAi = { template, projectId, brief, question, fields, revision, consent ->
                requestProjectAiScaffold(template, brief, question, fields, projectId, revision, consent)
            },
            onCreateProjectWithAi = ::createStudentProjectWithAi,
            onDiscardProjectAiPreview = ::discardProjectAiPreview,
            onRetryProjectAiSettlement = ::retryProjectAiSettlement,
            onBack = {
                releaseActiveProjectAiPreview()
                navigationState = navigationState.back()
            },
            onNavigate = openTargetSection,
        )
    } else if (navigationState.current == EvidriloDestination.PROJECTS) {
        EvidriloStudentProjectsScreen(
            state = studentProjectListState,
            notice = studentProjectNotice,
            onRetry = { studentProjectListReload += 1 },
            onOpenCatalog = { navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG) },
            onResume = { project -> resumeStudentProject(project.id) },
            onCreateManualProject = ::startManualStudentProject,
            onMarkCompleted = ::completeStudentProject,
            onArchive = ::archiveStudentProject,
            onMoveToTrash = ::trashStudentProject,
            onRestore = ::restoreStudentProject,
            onPermanentlyDelete = ::permanentlyDeleteStudentProject,
            onImportProject = ::importStudentProject,
            onRestoreArchiveRevision = ::restoreStudentProjectFromArchive,
            activeLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(projectProEntitlementActive.value),
            onOpenPremium = openPremium,
            onBack = { navigationState = navigationState.back() },
            onNavigate = openTargetSection,
        )
    } else if (navigationState.current == EvidriloDestination.PROJECT_EDITOR) {
        val draft = activeStudentProjectDraft
        if (draft == null) {
            EvidriloStudentProjectsScreen(
                state = StudentProjectListUiState.StorageFailed,
                notice = "The selected project is no longer available.",
                onRetry = { studentProjectListReload += 1 },
                onOpenCatalog = { navigationState = navigationState.resetToHome().open(EvidriloDestination.PROJECT_CATALOG) },
                onResume = { project -> resumeStudentProject(project.id) },
                onCreateManualProject = ::startManualStudentProject,
                onMarkCompleted = ::completeStudentProject,
                onArchive = ::archiveStudentProject,
                onMoveToTrash = ::trashStudentProject,
                onRestore = ::restoreStudentProject,
                onPermanentlyDelete = ::permanentlyDeleteStudentProject,
                onImportProject = ::importStudentProject,
                onRestoreArchiveRevision = ::restoreStudentProjectFromArchive,
                activeLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(projectProEntitlementActive.value),
                onOpenPremium = openPremium,
                onBack = { navigationState = navigationState.back() },
                onNavigate = openTargetSection,
            )
        } else {
            EvidriloStudentProjectEditorScreen(
                draft = draft,
                attachmentStore = studentProjectAttachmentStore,
                projectAiAccountKey = currentBillingAccountId(),
                projectAiState = projectAiScaffoldState,
                projectAiConsentState = projectAiConsentState,
                notice = studentProjectNotice,
                isDirty = studentProjectEditorIsDirty,
                showExitConfirmation = studentProjectExitConfirmation,
                saveError = studentProjectSaveError,
                onDirtyChanged = { studentProjectEditorIsDirty = it },
                onSave = { title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions ->
                    saveStudentProject(title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions)
                },
                onAutosave = { title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions ->
                    saveStudentProject(title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions, checkpoint = false)
                },
                onAddAttachment = ::addStudentProjectAttachment,
                onRemoveAttachment = ::removeStudentProjectAttachment,
                onRestoreRevision = ::restoreStudentProjectRevision,
                onRefreshProjectAiConsent = ::refreshProjectAiConsent,
                onGrantProjectAiConsent = ::grantProjectAiConsent,
                onRevokeProjectAiConsent = ::revokeProjectAiConsent,
                onRequestProjectAi = { projectId, brief, question, fields, revision, projectDataConsent ->
                    draft.templateSnapshot?.let { template ->
                        requestProjectAiScaffold(
                            template,
                            brief,
                            question,
                            fields,
                            projectId,
                            revision,
                            projectDataConsent,
                        )
                    }
                },
                onApplyProjectAi = ::applyProjectAiScaffold,
                onDiscardProjectAiPreview = ::discardProjectAiPreview,
                onRetryProjectAiSettlement = ::retryProjectAiSettlement,
                onRequestClose = {
                    if (studentProjectEditorIsDirty) {
                        studentProjectExitConfirmation = true
                    } else {
                        releaseActiveProjectAiPreview()
                        navigationState = navigationState.back()
                    }
                },
                onSaveAndLeave = {
                    studentProjectEditorIsDirty = false
                    studentProjectExitConfirmation = false
                    releaseActiveProjectAiPreview()
                    navigationState = navigationState.back()
                },
                onDiscardAndLeave = {
                    studentProjectEditorIsDirty = false
                    studentProjectExitConfirmation = false
                    releaseActiveProjectAiPreview()
                    navigationState = navigationState.back()
                },
                onCancelExit = { studentProjectExitConfirmation = false },
            )
        }
    } else if (navigationState.current == EvidriloDestination.SOURCES) {
        EvidriloTargetSourcesScreen(
            case = case,
            remoteContentStatus = remoteContentStatus,
            onNavigate = openTargetSection,
            onOpenProjects = {
                navigationState = navigationState.resetToHome().open(EvidriloDestination.PROJECTS)
            },
            onOpenWorkspace = {
                navigationState = navigationState.selectRoot(EvidriloDestination.HOME)
                    .open(EvidriloDestination.WORKSPACE)
            },
            onStartPractice = startTargetPractice,
        )
    } else if (navigationState.current == EvidriloDestination.WORKSPACE) {
        EvidriloTargetWorkspaceScreen(
            case = case,
            draft = targetDraft,
            onNavigate = openTargetSection,
            onOpenEvidence = { openTargetSection(EvidriloTargetSection.EVIDENCE) },
            onOpenAction = { openTargetSection(EvidriloTargetSection.ACTION) },
            onStartPractice = startTargetPractice,
        )
    } else if (navigationState.current == EvidriloDestination.EVIDENCE) {
        EvidriloTargetEvidenceScreen(
            case = case,
            draft = targetDraft,
            onNavigate = openTargetSection,
            onOpenEvidenceLens = {
                navigationState = navigationState.open(EvidriloDestination.EVIDENCE_LENS)
            },
            onOpenClaimTrace = {
                navigationState = navigationState.open(EvidriloDestination.CLAIM_TRACE)
            },
            onStartPractice = startTargetPractice,
        )
    } else if (navigationState.current == EvidriloDestination.EVIDENCE_LENS) {
        EvidriloTargetEvidenceLensScreen(
            case = case,
            draft = targetDraft,
            onNavigate = openTargetSection,
            onBack = { navigationState = navigationState.back() },
            onOpenClaimTrace = { navigationState = navigationState.open(EvidriloDestination.CLAIM_TRACE) },
        )
    } else if (navigationState.current == EvidriloDestination.CLAIM_TRACE) {
        EvidriloTargetClaimTraceScreen(
            case = case,
            draft = targetDraft,
            onNavigate = openTargetSection,
            onBack = { navigationState = navigationState.back() },
            onOpenClaimBoundary = {
                navigationState = navigationState.open(EvidriloDestination.CLAIM_BOUNDARY)
            },
            onOpenAction = { navigationState = navigationState.open(EvidriloDestination.ACTION) },
            onStartPractice = startTargetPractice,
        )
    } else if (navigationState.current == EvidriloDestination.CLAIM_BOUNDARY) {
        EvidriloTargetClaimBoundaryScreen(
            case = case,
            draft = targetDraft,
            evaluation = targetEvaluationFor(state),
            onNavigate = openTargetSection,
            onBack = { navigationState = navigationState.back() },
            onOpenVerify = {
                navigationState = navigationState.open(EvidriloDestination.VERIFY_CLAIM)
            },
            onOpenAction = {
                navigationState = navigationState.open(EvidriloDestination.ACTION)
            },
            onStartPractice = startTargetPractice,
        )
    } else if (navigationState.current == EvidriloDestination.VERIFY_CLAIM) {
        EvidriloTargetVerifyClaimScreen(
            case = case,
            draft = targetDraft,
            evaluation = targetEvaluationFor(state),
            canRevise = state is ConclusionState.Feedback,
            onNavigate = openTargetSection,
            onBack = { navigationState = navigationState.back() },
            onRevise = {
                dispatch(ConclusionEvent.BeginRevision)
                navigationState = navigationState.resetToHome().open(EvidriloDestination.PRACTICE)
            },
            onStartPractice = startTargetPractice,
        )
    } else if (navigationState.current == EvidriloDestination.ACTION) {
        EvidriloTargetActionScreen(
            case = case,
            draft = targetDraft,
            evaluation = targetEvaluationFor(state),
            onNavigate = openTargetSection,
            onOpenVerify = { navigationState = navigationState.open(EvidriloDestination.VERIFY_CLAIM) },
            onStartPractice = startTargetPractice,
        )
    } else if (navigationState.current == EvidriloDestination.EVIDENCE_DELTA) {
        when (val deltaState = state) {
            is ConclusionState.Summary -> EvidriloTargetEvidenceDeltaScreen(
                case = case,
                before = deltaState.initialDraft,
                after = deltaState.revisedDraft,
                evaluation = deltaState.finalEvaluation,
                onNavigate = openTargetSection,
                onBack = { navigationState = navigationState.back() },
                onOpenHistory = {
                    dispatch(ConclusionEvent.Reset)
                    navigationState = navigationState.resetToHome().open(EvidriloDestination.HISTORY)
                },
                onStartChallenge = {
                    dispatch(ConclusionEvent.BeginEvidenceChange)
                    navigationState = navigationState.resetToHome().open(EvidriloDestination.PRACTICE)
                },
            )
            else -> historySnapshot?.let { snapshot ->
                EvidriloTargetEvidenceDeltaScreen(
                    case = case,
                    before = snapshot.initialDraft,
                    after = snapshot.currentDraft,
                    evaluation = null,
                    onNavigate = openTargetSection,
                    onBack = { navigationState = navigationState.back() },
                    onOpenHistory = { navigationState = navigationState.back() },
                    challengeAvailable =
                        snapshot.phase == ConclusionSessionPhase.SUMMARY &&
                            snapshot.currentDraft.caseId == case.id &&
                            state is ConclusionState.Intro,
                    onStartChallenge = {
                        dispatch(ConclusionEvent.BeginEvidenceChangeFromSavedDraft(snapshot.currentDraft))
                        navigationState = navigationState.resetToHome().open(EvidriloDestination.PRACTICE)
                    },
                )
            } ?: EvidriloTargetActionScreen(
                case = case,
                draft = targetDraft,
                evaluation = targetEvaluationFor(state),
                onNavigate = openTargetSection,
                onOpenVerify = { navigationState = navigationState.open(EvidriloDestination.VERIFY_CLAIM) },
                onStartPractice = startTargetPractice,
            )
        }
    } else if (navigationState.current == EvidriloDestination.PROFILE) {
        val profileSignedIn = !TEMPORARY_GUEST_MODE_ENABLED &&
            accountRestoreComplete && accountSession is AccountSession.SignedIn
        EvidriloTargetProfileScreen(
            signedIn = profileSignedIn,
            profileSubtitle = if (TEMPORARY_GUEST_MODE_ENABLED) {
                "Guest mode · Local only"
            } else {
                accountSession.toSettingsSubtitle()
            },
            history = historySnapshot,
            onBack = { navigationState = navigationState.back() },
            onNavigate = openTargetSection,
            onOpenPremium = openPremium,
            onOpenHistory = {
                if (requireDestinationAccess(EvidriloDestination.HISTORY)) {
                    navigationState = navigationState.open(EvidriloDestination.HISTORY)
                }
            },
            onOpenSettings = { navigationState = navigationState.open(EvidriloDestination.SETTINGS) },
            onOpenAccount = {
                if (TEMPORARY_GUEST_MODE_ENABLED) {
                    navigationState = navigationState.open(EvidriloDestination.ACCOUNT)
                } else if (profileSignedIn) {
                    navigationState = navigationState.open(EvidriloDestination.ACCOUNT)
                } else {
                    openAccountGate(EvidriloDestination.PROFILE)
                }
            },
            onOpenLocalProjects = { navigationState = navigationState.open(EvidriloDestination.PROJECTS) },
            onOpenSupport = { navigationState = navigationState.open(EvidriloDestination.SUPPORT) },
        )
    } else if (navigationState.current == EvidriloDestination.ACCOUNT) {
        EvidriloAccountScreen(
            session = accountSession,
            isBusy = accountBusy,
            accountConfigured = accountConfiguration.isConfigured,
            exportJson = accountExportJson,
            exportError = accountExportError,
            persistenceNotice = onboardingStorageStatus.notice(),
            onBack = {
                pendingAccountGateDestination = null
                navigationState = navigationState.back()
            },
            onSignIn = { email, password ->
                performAccountOperation(showSigningInState = true) {
                    accountGateway.signIn(email, password)
                }
            },
            onSignUp = { email, password ->
                performAccountOperation(showSigningInState = true) {
                    accountGateway.signUp(email, password)
                }
            },
            onResetPassword = { email ->
                performAccountOperation(true) { accountGateway.requestPasswordReset(email) }
            },
            onGoogleSignIn = {
                performAccountOperation(showSigningInState = true) {
                    accountGateway.startGoogleSignIn()
                }
            },
            onGoogleLink = {
                performAccountOperation(false) { accountGateway.startGoogleIdentityLink() }
            },
            onCancelGoogleLink = {
                performAccountOperation(false) { accountGateway.cancelGoogleIdentityLink() }
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
            onExportAccount = {
                accountExportJson = null
                accountExportError = null
                performAccountOperation(false, onResult = { result ->
                    when (result) {
                        is AccountGatewayResult.ExportReady -> accountExportJson = result.json
                        is AccountGatewayResult.ExportFailed -> accountExportError = result.reason
                        else -> Unit
                    }
                }) { accountGateway.exportAccount() }
            },
            onDismissExport = {
                accountExportJson = null
                accountExportError = null
            },
        )
    } else if (navigationState.current == EvidriloDestination.HISTORY) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloTargetHistoryScreen(
            history = historySnapshot,
            storageNotice = storageNotice,
            onStartPractice = {
                dispatch(ConclusionEvent.Reset)
                navigationState = navigationState.open(EvidriloDestination.PRACTICE)
                dispatch(ConclusionEvent.Begin)
            },
            onClear = clearHistory,
            onOpenDelta = { navigationState = navigationState.open(EvidriloDestination.EVIDENCE_DELTA) },
            onBack = { navigationState = navigationState.back() },
            onNavigate = openTargetSection,
            selectedSection = if (previousDestination == EvidriloDestination.PROFILE) {
                EvidriloTargetSection.PROFILE
            } else {
                EvidriloTargetSection.HOME
            },
            backLabel = when (previousDestination) {
                EvidriloDestination.SETTINGS -> "Settings"
                EvidriloDestination.PROFILE -> "Profile"
                else -> "Home"
            },
        )
    } else if (navigationState.current == EvidriloDestination.GUIDE) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloGuideScreen(
            backLabel = when (previousDestination) {
                EvidriloDestination.SETTINGS -> "Settings"
                EvidriloDestination.PRACTICE -> "Review"
                else -> "Home"
            },
            onBack = { navigationState = navigationState.back() },
            onOpenPractice = {
                if (requireDestinationAccess(EvidriloDestination.PRACTICE)) {
                    if (state is ConclusionState.Intro) {
                        dispatch(ConclusionEvent.Begin)
                    }
                    navigationState = navigationState.open(EvidriloDestination.PRACTICE)
                }
            },
            onReplayOnboarding = ::requestGetStartedTour,
            audioState = audioState,
            onListen = { playNarration(AudioNarrationId.GUIDE, AudioNarrationCopy.guide()) },
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
        )
    } else if (navigationState.current == EvidriloDestination.SETTINGS) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
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
            backLabel = if (previousDestination == EvidriloDestination.PROFILE) "Profile" else "Home",
            audioSettings = audioSettings,
            audioStorageStatus = audioStorageStatus,
            onSetAudioSettings = setAudioSettings,
            themeMode = themeController.mode,
            onSetThemeMode = themeController::select,
            notificationPreferences = notificationPreferences,
            notificationPermission = notificationPermission,
            notificationScheduleLabel = notificationScheduleLabel,
            notificationStatusMessage = notificationStatusMessage,
            notificationBusy = notificationBusy,
            onEnableNotifications = enableNotifications,
            onDisableNotifications = disableNotifications,
            onSetNotificationPreferences = ::saveNotificationPreferences,
            onOpenNotificationSettings = notificationScheduler::openSystemSettings,
        )
    } else if (navigationState.current == EvidriloDestination.SUPPORT) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloSupportScreen(
            onBack = { navigationState = navigationState.back() },
            onOpenPremium = openPremium,
            onOpenAccount = { navigationState = navigationState.open(EvidriloDestination.ACCOUNT) },
            customerCenterAvailable = revenueCatUiAvailability.canPresent,
            onOpenCustomerCenter = openCustomerCenter,
            backLabel = when (previousDestination) {
                EvidriloDestination.SETTINGS -> "Settings"
                EvidriloDestination.PROFILE -> "Profile"
                else -> "Home"
            },
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
        EvidriloTargetHomeScreen(
            case = case,
            draft = targetDraft,
            history = historySnapshot,
            storageNotice = storageNotice,
            onNavigate = openTargetSection,
            onOpenWorkspace = { navigationState = navigationState.open(EvidriloDestination.WORKSPACE) },
            onOpenSources = { openTargetSection(EvidriloTargetSection.SOURCES) },
            onOpenEvidence = { openTargetSection(EvidriloTargetSection.EVIDENCE) },
            onOpenAction = { openTargetSection(EvidriloTargetSection.ACTION) },
            onOpenHistory = { navigationState = navigationState.resetToHome().open(EvidriloDestination.HISTORY) },
            onOpenProjectCatalog = {
                navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG)
            },
            projects = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects.orEmpty(),
            projectsLoading = studentProjectListState is StudentProjectListUiState.Loading,
            projectsLoadError = studentProjectListState.toHomeErrorMessage(),
            onRetryProjects = { studentProjectListReload += 1 },
            onOpenProjects = { navigationState = navigationState.open(EvidriloDestination.PROJECTS) },
            onCreateProject = ::beginManualProjectFromHome,
            onResumeProject = { project -> resumeStudentProject(project.id) },
            onStartPractice = startTargetPractice,
            onOpenSettings = { openTargetSection(EvidriloTargetSection.PROFILE) },
            onOpenGuide = { navigationState = navigationState.open(EvidriloDestination.GUIDE) },
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
            ConclusionState.Intro -> EvidriloTargetHomeScreen(
                case = case,
                draft = targetDraft,
                history = historySnapshot,
                storageNotice = storageNotice,
                onNavigate = openTargetSection,
                onOpenWorkspace = { navigationState = navigationState.open(EvidriloDestination.WORKSPACE) },
                onOpenSources = { openTargetSection(EvidriloTargetSection.SOURCES) },
                onOpenEvidence = { openTargetSection(EvidriloTargetSection.EVIDENCE) },
                onOpenAction = { openTargetSection(EvidriloTargetSection.ACTION) },
                onOpenHistory = { navigationState = navigationState.resetToHome().open(EvidriloDestination.HISTORY) },
                onOpenProjectCatalog = {
                    navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG)
                },
                projects = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects.orEmpty(),
                projectsLoading = studentProjectListState is StudentProjectListUiState.Loading,
                projectsLoadError = studentProjectListState.toHomeErrorMessage(),
                onRetryProjects = { studentProjectListReload += 1 },
                onOpenProjects = { navigationState = navigationState.open(EvidriloDestination.PROJECTS) },
                onCreateProject = ::beginManualProjectFromHome,
                onResumeProject = { project -> resumeStudentProject(project.id) },
                onStartPractice = startTargetPractice,
                onOpenSettings = { openTargetSection(EvidriloTargetSection.PROFILE) },
                onOpenGuide = { navigationState = navigationState.open(EvidriloDestination.GUIDE) },
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
                case = case,
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
                initialStep = initialDraftStepFor(current),
                audioState = audioState,
                onListen = { playNarration(AudioNarrationId.REVISION, AudioNarrationCopy.revision()) },
                onPauseOrResumeAudio = pauseOrResumeAudio,
                onStopAudio = stopAudio,
                onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
            )

            is ConclusionState.Summary -> EvidriloTargetEvidenceDeltaScreen(
                case = case,
                before = current.initialDraft,
                after = current.revisedDraft,
                evaluation = current.finalEvaluation,
                onNavigate = openTargetSection,
                onBack = returnToHome,
                onOpenHistory = {
                    dispatch(ConclusionEvent.Reset)
                    navigationState = navigationState.resetToHome().open(EvidriloDestination.HISTORY)
                },
                onStartChallenge = {
                    dispatch(ConclusionEvent.BeginEvidenceChange)
                    navigationState = navigationState.resetToHome().open(EvidriloDestination.PRACTICE)
                },
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
                initialStep = initialDraftStepFor(current),
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

    EvidriloFloatingAssistant(
        visible = assistantVisible,
        dockNearTop = navigationState.current == EvidriloDestination.PRACTICE,
        case = case,
        draft = targetDraft,
        feedback = targetEvaluationFor(state)?.primaryFeedback,
        accountId = currentBillingAccountId(),
        aiState = assistantAiState,
        aiClearState = aiConversationClearState,
        onRequestAi = requestAiAssist,
        onClearAiConversation = clearAiConversation,
        onRetryClearAiConversation = retryClearAiConversation,
        canApplyAiProposal = canApplyAiProposal,
        onApplyAiProposal = applyAiProposal,
        onRetryAi = retryAiAssist,
        onOpenAccount = { navigationState = navigationState.open(EvidriloDestination.ACCOUNT) },
        onOpenEvidence = { openTargetSection(EvidriloTargetSection.EVIDENCE) },
        onOpenPractice = startTargetPractice,
        onOpenVerify = { navigationState = navigationState.open(EvidriloDestination.VERIFY_CLAIM) },
    )

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
    onRetry: () -> Unit,
    onSelectOffer: (String) -> Unit,
    onSelectCase: (String) -> Unit,
    onBeginCase: () -> Unit,
    onPracticeEvent: (ConclusionEvent) -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
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

        is PremiumPracticeState.Locked ->
            if (state.billing.state == BillingUiState.LOADING) {
                EvidriloLoadingScreen(
                    mode = EvidriloLoadingMode.NETWORK_PAGE,
                    onBack = onBack,
                )
            } else {
                EvidriloTargetSurface(
                    selected = EvidriloTargetSection.PROFILE,
                    onNavigate = onNavigate,
                ) {
                    EvidriloPremiumLockedScreen(
                        billing = state.billing.copy(isBusy = isBusy),
                        managedPaywallAvailable = managedPaywallAvailable,
                        onOpenManagedPaywall = onOpenManagedPaywall,
                        onPurchase = onPurchase,
                        onRestore = onRestore,
                        onRetry = onRetry,
                        onSelectOffer = onSelectOffer,
                        onBack = onBack,
                        backLabel = backLabel,
                    )
                }
            }

        is PremiumPracticeState.Catalog -> EvidriloTargetSurface(
            selected = EvidriloTargetSection.PROFILE,
            onNavigate = onNavigate,
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
            ConclusionState.Intro -> EvidriloTargetSurface(
                selected = EvidriloTargetSection.PROFILE,
                onNavigate = onNavigate,
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
                title = "Premium evidence case · ${state.case.title}",
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
                case = state.case,
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
                initialStep = initialDraftStepFor(current),
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
                initialStep = initialDraftStepFor(current),
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
    onRetry: () -> Unit,
    onSelectOffer: (String) -> Unit,
    onBack: () -> Unit,
    backLabel: String,
) {
    EvidriloPremiumPaywall(
        billing = billing,
        isBusy = billing.isBusy,
        managedPaywallAvailable = managedPaywallAvailable,
        onOpenManagedPaywall = onOpenManagedPaywall,
        onPurchase = onPurchase,
        onRestore = onRestore,
        onRetry = onRetry,
        onSelectOffer = onSelectOffer,
        onBack = onBack,
        backLabel = backLabel,
    )
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
        EvidriloBrandHeader(onSettings = null)
        EvidriloBackButton(label = backLabel, onClick = onBack)
        Text("Choose a focused case", style = MaterialTheme.typography.displayLarge)
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
        EvidriloSecondaryButton(label = "Return to free workflow", onClick = onBack)
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
        EvidriloBrandHeader(onSettings = null)
        EvidriloBackButton(label = "Packs", onClick = onBack)
        Text("Compare the premium evidence case", style = MaterialTheme.typography.displayLarge)
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
    var confirmReset by remember(case.id, initialStep) { mutableStateOf(false) }
    var showCaseDetails by remember(case.id, initialStep) { mutableStateOf(false) }

    EvidriloContentColumn {
        EvidriloBrandHeader(onSettings = null)
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        Text(
            when (step) {
                EvidriloDraftStep.EVIDENCE -> title
                EvidriloDraftStep.CLAIM -> "Write a bounded claim"
                EvidriloDraftStep.LIMITS -> "Name the limits and next action"
            },
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            when (step) {
                EvidriloDraftStep.EVIDENCE -> "Compare the case prediction with the observations, then choose the facts that support your conclusion."
                EvidriloDraftStep.CLAIM -> "Write what your selected evidence supports, then choose how far the claim can go."
                EvidriloDraftStep.LIMITS -> "Name the limitations that still matter and one practical next action."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        EvidriloDraftStepIndicator(current = step)
        evidenceChangeContextNote(case)?.let { note -> EvidriloContextCard(note) }
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
                EvidriloCaseQuestionCard(case)
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
                EvidriloSecondaryButton(
                    label = if (showCaseDetails) "Hide case details" else "Read all case facts and trace",
                    onClick = { showCaseDetails = !showCaseDetails },
                )
                if (showCaseDetails) {
                    EvidriloWorkspaceTraceCard(case = case, draft = draft)
                    EvidriloCaseFactsCard(case)
                }
                EvidriloPrimaryButton(
                    label = "Continue to claim",
                    onClick = { step = EvidriloDraftStep.CLAIM },
                )
                EvidriloSecondaryButton(label = "Reset this workflow", onClick = { confirmReset = true })
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
                EvidriloSecondaryButton(label = "Reset this workflow", onClick = { confirmReset = true })
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
                EvidriloSecondaryButton(label = "Reset this workflow", onClick = { confirmReset = true })
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset this workflow?") },
            text = {
                Text(
                    "Your current local draft will be discarded and the bundled case will return to its starting state.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    },
                ) {
                    Text("Reset workflow")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun EvidriloCaseQuestionCard(case: ConclusionCase) {
    val aim = case.factsOfType(ConclusionFactType.AIM).firstOrNull()
    val suppliedHypothesis = case.factsOfType(ConclusionFactType.CONTEXT)
        .firstOrNull { it.id.contains("HYP") }

    EvidriloTintPanel {
        Text("Case question", style = MaterialTheme.typography.titleSmall)
        Text(aim?.text ?: case.description, style = MaterialTheme.typography.bodyMedium)
        suppliedHypothesis?.let { hypothesis ->
            Text("Supplied hypothesis · context", style = MaterialTheme.typography.labelLarge)
            Text(hypothesis.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                "This is a prediction to compare with the results, not an observation to select as evidence.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
        }
    }
}

internal enum class EvidriloDraftStep(val label: String) {
    EVIDENCE("Evidence"),
    CLAIM("Claim"),
    LIMITS("Limits"),
}

internal fun initialDraftStepFor(state: ConclusionState): EvidriloDraftStep = when (state) {
    is ConclusionState.Revision -> EvidriloDraftStep.CLAIM
    is ConclusionState.EvidenceChangeDrafting -> EvidriloDraftStep.EVIDENCE
    else -> error("An initial draft step is defined only for revision and evidence-change drafts.")
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
    case: ConclusionCase,
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
        EvidriloBrandHeader(onSettings = null)
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        Text("See what the case supports", style = MaterialTheme.typography.displayLarge)
        Text(
            "These checks are bounded to the supplied facts. They are not a grade or a claim that the real-world experiment is scientifically complete.",
            style = MaterialTheme.typography.bodyLarge,
        )
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        EvidriloClaimBoundaryCard(case = case, draft = draft, evaluation = evaluation)
        EvidriloVerificationDetailCard(evaluation = evaluation)
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
    case: ConclusionCase,
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
        EvidriloBrandHeader(onSettings = null)
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        Text("Compare your reasoning", style = MaterialTheme.typography.displayLarge)
        Text(
            "The first draft remains stored beside the single revision. Evidrilo does not replace either draft with a generated answer.",
            style = MaterialTheme.typography.bodyLarge,
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
        EvidriloEvidenceDeltaCard(
            before = initialDraft,
            after = revisedDraft,
            title = "Revision evidence and field changes",
            case = case,
        )
        EvidriloClaimBoundaryCard(case = case, draft = revisedDraft, evaluation = finalEvaluation)
        EvidriloVerificationDetailCard(evaluation = finalEvaluation)
        finalEvaluation.primaryFeedback?.let { feedback ->
            EvidriloFeedbackCard(feedback, prominent = true)
        } ?: EvidriloNotice(
            status = ConclusionStatus.PASS,
            title = "The revised conclusion passes the bounded checks",
            body = "You connected the selected evidence, scope, limitations, and next action.",
        )
        EvidriloPrimaryButton(label = "Try the evidence-change challenge", onClick = onStartChallenge)
        EvidriloSecondaryButton(label = "Start a new review", onClick = onReset)
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
        EvidriloBrandHeader(onSettings = null)
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        Text("Re-evaluate the changed case", style = MaterialTheme.typography.displayLarge)
        Text(
            "This feedback uses only the observations still supplied in the challenge. There is no second revision in this round.",
            style = MaterialTheme.typography.bodyLarge,
        )
        evidenceChangeContextNote(ConclusionCases.EVIDENCE_CHANGE)?.let { note -> EvidriloContextCard(note) }
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        EvidriloClaimBoundaryCard(
            case = ConclusionCases.EVIDENCE_CHANGE,
            draft = draft,
            evaluation = evaluation,
        )
        EvidriloVerificationDetailCard(evaluation = evaluation)
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
        EvidriloBrandHeader(onSettings = null)
        onBack?.let { back ->
            EvidriloBackButton(label = "Home", onClick = back)
        }
        Text("See what changed with the evidence", style = MaterialTheme.typography.displayLarge)
        Text(
            "The base revision is kept beside the fresh challenge conclusion. The comparison is stored locally as the latest entry and can be cleared from the start screen.",
            style = MaterialTheme.typography.bodyLarge,
        )
        evidenceChangeContextNote(ConclusionCases.EVIDENCE_CHANGE)?.let { note -> EvidriloContextCard(note) }
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )
        EvidriloDraftSnapshot("Base revision", baseDraft)
        HorizontalDivider()
        EvidriloDraftSnapshot("Changed-evidence conclusion", challengeDraft)
        EvidriloEvidenceDeltaCard(
            before = baseDraft,
            after = challengeDraft,
            title = "Changed-evidence comparison",
            case = ConclusionCases.EVIDENCE_CHANGE,
            beforeCase = ConclusionCases.M0_T2,
        )
        EvidriloClaimBoundaryCard(
            case = ConclusionCases.EVIDENCE_CHANGE,
            draft = challengeDraft,
            evaluation = challengeEvaluation,
        )
        EvidriloVerificationDetailCard(evaluation = challengeEvaluation)
        challengeEvaluation.primaryFeedback?.let { feedback ->
            EvidriloFeedbackCard(feedback, prominent = true)
        } ?: EvidriloNotice(
            status = ConclusionStatus.PASS,
            title = "The challenge conclusion passes the bounded checks",
            body = "The latest comparison preserves the learner-authored conclusions and active fact anchors.",
        )
        EvidriloPrimaryButton(label = "Return to workflow", onClick = onReset)
    }
}

@Composable
private fun EvidriloCaseFactsCard(case: ConclusionCase) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) EvidriloColors.Tint else EvidriloColors.White,
        ),
        border = BorderStroke(
            width = 2.dp,
            color = if (selected) EvidriloColors.Cobalt else EvidriloColors.Separator,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (prominent) EvidriloColors.Tint else EvidriloColors.Surface,
        ),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(status.displayLabel(), style = MaterialTheme.typography.labelLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EvidriloContextCard(note: EvidriloContextNote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(note.body, style = MaterialTheme.typography.bodyMedium)
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
