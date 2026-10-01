package dev.nextgen.mobile

import androidx.lifecycle.compose.LifecycleEventEffect

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
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.Text as RawText
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
import dev.nextgen.mobile.account.AccountOAuthProvider
import dev.nextgen.mobile.account.AccountSession
import dev.nextgen.mobile.account.AccountSessionController
import dev.nextgen.mobile.account.AccountUnavailableReason
import dev.nextgen.mobile.account.ACCOUNT_AUTH_ENABLED
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED
import dev.nextgen.mobile.account.EvidriloAccountRequiredGate
import dev.nextgen.mobile.account.EvidriloAccountScreen
import dev.nextgen.mobile.account.createAccountClientConfiguration
import dev.nextgen.mobile.account.createAccountGateway
import dev.nextgen.mobile.account.createAccountHttpTransport
import dev.nextgen.mobile.account.shouldShowSignedInAccountInProfile
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
import dev.nextgen.mobile.billing.REVENUECAT_PRO_FEATURE_ENABLED
import dev.nextgen.mobile.billing.billingIdentityMatches
import dev.nextgen.mobile.billing.BillingUiState
import dev.nextgen.mobile.billing.PremiumAccess
import dev.nextgen.mobile.billing.EvidriloPremiumPaywall
import dev.nextgen.mobile.billing.PremiumPracticeEvent
import dev.nextgen.mobile.billing.PremiumPracticeReducer
import dev.nextgen.mobile.billing.PremiumPracticeState
import dev.nextgen.mobile.billing.RevenueCatCustomerCenter
import dev.nextgen.mobile.billing.RevenueCatManagedPaywall
import dev.nextgen.mobile.billing.createRevenueCatUiAvailability
import dev.nextgen.mobile.billing.withRequestedProPlan
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
import dev.nextgen.mobile.domain.project.StudentProjectDeadlineChange
import dev.nextgen.mobile.domain.project.StudentProjectClaimRecord
import dev.nextgen.mobile.domain.project.StudentProjectLimitationActionRecord
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatRequest
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatResult
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatDeferredReason
import dev.nextgen.mobile.projectcatalog.createProjectAiInstallationIdStore
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldDecision
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldSettlementGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiScaffoldSettlementResult
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiConsentGatewayResult
import dev.nextgen.mobile.projectcatalog.PROJECT_AI_CONSENT_POLICY_VERSION
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistRequest
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistResult
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistDeferredReason
import dev.nextgen.mobile.projectcatalog.ProjectAiLocalContextBindingResult
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistSelectedEvidence
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistOutcome
import dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistSettlementResult
import dev.nextgen.mobile.projectcatalog.ProjectAiActivityGateway
import dev.nextgen.mobile.projectcatalog.ProjectAiActivityHistoryResult
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
    val languageStore = rememberLanguageSettingsStore()
    val languageController = remember(languageStore) { EvidriloLanguageController(languageStore) }
    androidx.compose.runtime.CompositionLocalProvider(LocalEvidriloLanguage provides languageController.language) {
        dev.nextgen.mobile.navigation.EvidriloBackGestureHost {
            EvidriloAppContent(billingGateway, languageController, themeController)
        }
    }
}

@Composable
private fun EvidriloAppContent(
    billingGateway: BillingGateway,
    languageController: EvidriloLanguageController,
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
    val accountBoundFeaturesEnabled = !TEMPORARY_GUEST_MODE_ENABLED
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
    val projectAiGeneralChatGateway = remember(accountConfiguration, secureSessionStore) {
        ProjectAiGeneralChatGateway(
            configuration = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val projectAiStageAssistGateway = remember(accountConfiguration, secureSessionStore) {
        ProjectAiStageAssistGateway(
            configuration = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val projectAiActivityGateway = remember(accountConfiguration, secureSessionStore) {
        ProjectAiActivityGateway(
            configuration = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl),
            transport = createAccountHttpTransport(),
            secureSessionStore = secureSessionStore,
            nowEpochSeconds = { Clock.System.now().epochSeconds },
        )
    }
    val projectAiInstallationId = remember { createProjectAiInstallationIdStore().getOrCreate() }
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
    var projectAiConsentState by remember { mutableStateOf<ProjectAiConsentUiState>(ProjectAiConsentUiState.Unknown) }
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
    var accountAuthRestoreComplete by remember { mutableStateOf(false) }
    var revenueCatIdentityAccountId by remember { mutableStateOf<String?>(null) }
    var billingIdentityReload by remember { mutableStateOf(0) }
    var lastSyncAccountId by remember { mutableStateOf<String?>(null) }
    var accountBusy by remember { mutableStateOf(false) }
    var accountExportJson by remember { mutableStateOf<String?>(null) }
    var accountExportError by remember { mutableStateOf<AccountUnavailableReason?>(null) }
    var remoteContentStatus by remember { mutableStateOf("Offline-ready bundled case") }
    var platformProgress by remember { mutableStateOf<PlatformProgressSummary?>(null) }
    var platformEntitlements by remember { mutableStateOf<PlatformEntitlements?>(null) }
    var platformStatus by remember { mutableStateOf<String?>(null) }
    var aiCredits by remember { mutableStateOf<AiCredits?>(null) }
    var aiCreditBalanceRefreshing by remember { mutableStateOf(false) }
    var aiCreditBalanceFailureMessage by remember { mutableStateOf<String?>(null) }
    var aiCreditBalanceCanRetry by remember { mutableStateOf(true) }
    var aiCreditBalanceRequestGeneration by remember { mutableStateOf(0L) }
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
    fun refreshAiCreditBalance(updateAssistState: Boolean = false) {
        val signedIn = accountSession as? AccountSession.SignedIn
        val accountId = signedIn?.account?.accountId
        if (!accountBoundFeaturesEnabled || !accountRestoreComplete ||
            accountId == null || signedIn.account.emailVerified.not()
        ) {
            aiCreditBalanceRefreshing = false
            if (updateAssistState) aiAssistState = EvidriloAiAssistUiState.SignInRequired
            return
        }
        if (aiCreditBalanceRefreshing) return

        val generation = aiCreditBalanceRequestGeneration + 1
        aiCreditBalanceRequestGeneration = generation
        aiCreditBalanceRefreshing = true
        aiCreditBalanceFailureMessage = null
        aiCreditBalanceCanRetry = true
        accountScope.launch {
            try {
                val result = try {
                    aiGateway.getCredits()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    AiGatewayResult.Failed("AI_CREDITS_UNAVAILABLE", retryable = true)
                }
                if (generation != aiCreditBalanceRequestGeneration ||
                    !accountRestoreComplete || currentBillingAccountId() != accountId
                ) return@launch

                when (result) {
                    is AiGatewayResult.CreditsFound -> {
                        aiCredits = result.value
                        aiCreditBalanceFailureMessage = null
                        aiCreditBalanceCanRetry = false
                        if (updateAssistState) aiAssistState = EvidriloAiAssistUiState.Ready(result.value)
                    }
                    is AiGatewayResult.Deferred -> {
                        aiCreditBalanceFailureMessage = "AI credits require an available verified account and API session."
                        aiCreditBalanceCanRetry = false
                        if (updateAssistState) aiAssistState = EvidriloAiAssistUiState.Unavailable(
                            message = aiCreditBalanceFailureMessage.orEmpty(),
                            retryable = false,
                        )
                    }
                    is AiGatewayResult.Fallback -> {
                        aiCreditBalanceFailureMessage = "The current AI credit balance could not be confirmed. Try refreshing before another request."
                        aiCreditBalanceCanRetry = true
                        if (updateAssistState) aiAssistState = EvidriloAiAssistUiState.Unavailable(
                            message = "AI credits could not be loaded; deterministic feedback remains available.",
                            retryable = true,
                        )
                    }
                    is AiGatewayResult.Failed -> {
                        aiCreditBalanceFailureMessage = if (result.outcomeUnknown) {
                            "The latest AI credit balance could not be confirmed. It may be out of date."
                        } else {
                            "AI credits are temporarily unavailable. Your manual project workflow remains available."
                        }
                        aiCreditBalanceCanRetry = result.retryable || result.outcomeUnknown
                        if (updateAssistState) aiAssistState = EvidriloAiAssistUiState.Unavailable(
                            message = "AI credits are temporarily unavailable; deterministic feedback remains available.",
                            retryable = result.retryable,
                        )
                    }
                    is AiGatewayResult.AssistFound -> {
                        aiCreditBalanceFailureMessage = "The API returned an unexpected response for AI credits."
                        aiCreditBalanceCanRetry = false
                        if (updateAssistState) aiAssistState = EvidriloAiAssistUiState.Unavailable(
                            message = aiCreditBalanceFailureMessage.orEmpty(),
                            retryable = false,
                        )
                    }
                }
            } finally {
                if (generation == aiCreditBalanceRequestGeneration) aiCreditBalanceRefreshing = false
            }
        }
    }
    val projectAiGeneralChatRequestCoordinator = remember(accountScope, projectAiGeneralChatGateway, aiGateway) {
        ProjectAiGeneralChatRequestCoordinator(
            scope = accountScope,
            currentAccountId = { currentBillingAccountId() },
            sendMessage = { request, key -> projectAiGeneralChatGateway.sendMessage(request, key) },
            refreshCredits = {
                (aiGateway.getCredits() as? AiGatewayResult.CreditsFound)?.value
            },
            onCreditsUpdated = {
                aiCredits = it
                aiCreditBalanceFailureMessage = null
                aiCreditBalanceCanRetry = false
            },
        )
    }
    fun requestGeneralChatMessage(
        message: String,
        locale: String,
        onResult: (ProjectAiGeneralChatResult) -> Unit,
    ) {
        if (!accountBoundFeaturesEnabled) {
            onResult(ProjectAiGeneralChatResult.Deferred(ProjectAiGeneralChatDeferredReason.AUTH_REQUIRED))
            return
        }
        val session = accountSession as? AccountSession.SignedIn
        if (!accountRestoreComplete || session == null || !session.account.emailVerified) {
            onResult(ProjectAiGeneralChatResult.Deferred(ProjectAiGeneralChatDeferredReason.AUTH_REQUIRED))
            return
        }
        if (projectAiConsentState !is ProjectAiConsentUiState.Granted) {
            onResult(ProjectAiGeneralChatResult.Rejected("PROJECT_AI_CONSENT_REQUIRED"))
            return
        }
        val installationId = projectAiInstallationId
        if (installationId == null) {
            onResult(ProjectAiGeneralChatResult.Deferred(ProjectAiGeneralChatDeferredReason.SECURE_STORAGE))
            return
        }
        val requestAccountId = session.account.accountId
        projectAiGeneralChatRequestCoordinator.send(
            accountId = requestAccountId,
            request = ProjectAiGeneralChatRequest(
                installationId = installationId,
                locale = locale,
                message = message,
                consentConfirmed = true,
            ),
            idempotencyKey = newAnalyticsEventId(),
            onResult = onResult,
        )
    }
    fun canUseRevenueCatForCurrentAccount(): Boolean =
        REVENUECAT_PRO_FEATURE_ENABLED &&
            accountRestoreComplete &&
            billingIdentityMatches(currentBillingAccountId(), revenueCatIdentityAccountId)
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
        if (!accountBoundFeaturesEnabled) {
            syncStatusMessage = "Cloud sync is temporarily unavailable in guest mode."
            return
        }
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
    var projectAiStageAssistState by remember { mutableStateOf<ProjectAiStageAssistUiState>(ProjectAiStageAssistUiState.Idle) }
    var projectAiStageAssistRequestToken by remember { mutableStateOf<String?>(null) }
    var projectAiActivityHistoryState by remember { mutableStateOf<ProjectAiActivityHistoryUiState>(ProjectAiActivityHistoryUiState.NotRequested) }
    var projectAiActivityRequestGeneration by remember { mutableStateOf(0L) }
    var projectAiRequestToken by remember { mutableStateOf<String?>(null) }
    var projectAiRequestInFlight by remember { mutableStateOf(false) }
    var projectAiConsentRequestGeneration by remember { mutableStateOf(0L) }
    var projectAiBoundAccountId by remember { mutableStateOf<String?>(null) }
    var studentProjectListState by remember {
        mutableStateOf<StudentProjectListUiState>(StudentProjectListUiState.Loading)
    }
    var studentProjectListReload by remember { mutableStateOf(0) }
    var studentProjectNotice by remember { mutableStateOf<String?>(null) }
    var pendingProjectDeletionNotice by remember { mutableStateOf<String?>(null) }
    var studentProjectSaveError by remember { mutableStateOf<String?>(null) }
    var activeStudentProjectDraft by remember { mutableStateOf<StudentProjectDraft?>(null) }
    var projectExportIntentId by remember { mutableStateOf<String?>(null) }
    var projectOpinionComposeIntent by remember { mutableStateOf<ProjectOpinionIntent?>(null) }
    var profilePracticeLesson by remember { mutableStateOf<dev.nextgen.mobile.domain.practice.PracticeLessonId?>(null) }
    var practiceProjectSectionIntent by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(navigationState.current) {
        if (navigationState.current != EvidriloDestination.PROJECTS) practiceProjectSectionIntent = null
    }
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
    var practiceAccessChecking by remember { mutableStateOf(false) }
    var practiceAccessReload by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { practiceAccessReload += 1 }
    var proComparisonVisible by remember { mutableStateOf(false) }
    var preferredProProductId by remember { mutableStateOf<String?>(null) }
    var premiumRequestId by remember { mutableStateOf(0) }
    var premiumOpenRequestStarted by remember { mutableStateOf(false) }
    var premiumWaitingForIdentity by remember { mutableStateOf(false) }
    var premiumBillingRequestActive by remember { mutableStateOf(false) }
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

    fun refreshProjectAiActivity(projectId: String?, cursor: String? = null) {
        val signedIn = accountSession as? AccountSession.SignedIn
        val accountId = signedIn?.account?.accountId
        val installationId = projectAiInstallationId
        val previousPage = projectAiActivityHistoryState as? ProjectAiActivityHistoryUiState.Loaded
        val loadingMore = cursor != null
        if (TEMPORARY_GUEST_MODE_ENABLED || !accountRestoreComplete || accountId == null ||
            signedIn?.account?.emailVerified != true || installationId == null
        ) {
            projectAiActivityHistoryState = ProjectAiActivityHistoryUiState.Unavailable(
                accountId = accountId,
                projectId = projectId,
                message = "A verified account session is required to view server activity metadata.",
            )
            return
        }
        if (loadingMore && (previousPage == null || previousPage.accountId != accountId ||
                previousPage.projectId != projectId || previousPage.nextCursor != cursor || previousPage.loadingMore)
        ) return
        val requestGeneration = projectAiActivityRequestGeneration + 1
        projectAiActivityRequestGeneration = requestGeneration
        projectAiActivityHistoryState = if (loadingMore) {
            previousPage!!.copy(loadingMore = true, loadMoreError = null)
        } else {
            ProjectAiActivityHistoryUiState.Loading(accountId, projectId)
        }
        accountScope.launch {
            val result = try {
                projectAiActivityGateway.list(installationId, projectId, limit = 20, cursor = cursor)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiActivityHistoryResult.Unavailable("PROJECT_AI_ACTIVITY_UNAVAILABLE")
            }
            if (projectAiActivityRequestGeneration != requestGeneration) return@launch
            if (projectId != null && activeStudentProjectDraft?.id != projectId) {
                projectAiActivityHistoryState = if (loadingMore && previousPage != null) {
                    previousPage.copy(loadingMore = false, loadMoreError = "PROJECT_CONTEXT_CHANGED")
                } else {
                    ProjectAiActivityHistoryUiState.NotRequested
                }
                return@launch
            }
            if (!projectAiSessionMatchesOwner(accountId, currentBillingAccountId())) return@launch
            projectAiActivityHistoryState = when (result) {
                is ProjectAiActivityHistoryResult.Loaded -> {
                    val entries = if (loadingMore) previousPage!!.entries + result.activities else result.activities
                    ProjectAiActivityHistoryUiState.Loaded(
                        accountId = accountId,
                        projectId = projectId,
                        entries = entries.distinctBy { it.activityId },
                        nextCursor = result.nextCursor,
                    )
                }
                is ProjectAiActivityHistoryResult.Deferred -> if (loadingMore) {
                    previousPage!!.copy(loadingMore = false, loadMoreError = "AUTH_REQUIRED")
                } else {
                    ProjectAiActivityHistoryUiState.Unavailable(
                        accountId, projectId, "Sign in again with a verified account to view activity metadata.",
                    )
                }
                is ProjectAiActivityHistoryResult.Unavailable -> if (loadingMore) {
                    previousPage!!.copy(loadingMore = false, loadMoreError = result.code)
                } else {
                    ProjectAiActivityHistoryUiState.Unavailable(
                        accountId, projectId, "Activity metadata is temporarily unavailable (${result.code}).",
                    )
                }
                is ProjectAiActivityHistoryResult.Rejected -> if (loadingMore) {
                    previousPage!!.copy(loadingMore = false, loadMoreError = result.code)
                } else {
                    ProjectAiActivityHistoryUiState.Unavailable(
                        accountId, projectId, "The activity request was rejected (${result.code}).",
                    )
                }
            }
        }
    }

    fun loadMoreProjectAiActivity(projectId: String?, cursor: String) =
        refreshProjectAiActivity(projectId, cursor)

    fun settleProjectAiScaffold(
        requestId: String,
        decision: ProjectAiScaffoldDecision,
        creditCost: Int,
        projectAlreadyApplied: Boolean,
    ) {
        if (TEMPORARY_GUEST_MODE_ENABLED ||
            !projectAiSessionMatchesOwner(projectAiBoundAccountId, currentBillingAccountId())
        ) {
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
                            "Selected AI suggestions were applied. The valid preview cost ${result.creditCost} credit${if (result.creditCost == 1) "" else "s"}."
                        ProjectAiScaffoldDecision.DISMISS -> "AI suggestions discarded. The valid preview cost ${result.creditCost} credit${if (result.creditCost == 1) "" else "s"}; dismissal does not change the charge."
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
                        "A verified, active account session is required to record this preview outcome. " +
                            if (projectAlreadyApplied) "Your project change is already saved locally." else "The suggestion remains unapplied.",
                        canRetry = true,
                    )
                }
                is ProjectAiScaffoldSettlementResult.Unavailable -> {
                    projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                        requestId, decision, creditCost, projectAlreadyApplied,
                        "The preview outcome could not be recorded. Its verified token-based charge was already settled. " +
                            if (projectAlreadyApplied) "Your project change is already saved locally; retry to reconcile the outcome before requesting more AI." else "The suggestion remains unapplied; retry to reconcile the outcome.",
                        canRetry = true,
                    )
                }
                is ProjectAiScaffoldSettlementResult.Rejected -> {
                    projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                        requestId, decision, creditCost, projectAlreadyApplied,
                        "The server could not record this preview outcome (${result.code}); the valid preview charge was already settled. " +
                            if (projectAlreadyApplied) "Your project change remains saved locally." else "The suggestion remains unapplied.",
                        canRetry = false,
                    )
                }
                is ProjectAiScaffoldSettlementResult.Failed -> {
                    projectAiScaffoldState = ProjectAiScaffoldUiState.SettlementFailed(
                        requestId, decision, creditCost, projectAlreadyApplied,
                        if (result.outcomeUnknown) {
                            "The preview outcome update is unknown; its valid-preview charge is already settled. Retry the same request to reconcile it. " +
                                if (projectAlreadyApplied) "Your project change remains saved locally." else "The suggestion remains unapplied."
                        } else {
                            "The preview outcome could not be recorded (${result.code}); its valid-preview charge is already settled. " +
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
        if (TEMPORARY_GUEST_MODE_ENABLED ||
            !projectAiSessionMatchesOwner(ownerAccountId, currentBillingAccountId())
        ) return
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
                    message = "An abandoned AI preview outcome could not be recorded. Its valid-preview charge was already settled; retry to reconcile the outcome before another request.",
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

    fun createStudentProjectFromTemplate(
        template: dev.nextgen.mobile.domain.project.ProjectTemplateDefinition,
        accessDestination: EvidriloDestination,
    ) {
        if (!requireDestinationAccess(accessDestination)) return
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

    fun startStudentProject(template: dev.nextgen.mobile.domain.project.ProjectTemplateDefinition) =
        createStudentProjectFromTemplate(template, EvidriloDestination.PROJECT_TEMPLATE_DETAIL)

    fun startStarterStudentProject(template: dev.nextgen.mobile.domain.project.ProjectTemplateDefinition) =
        createStudentProjectFromTemplate(template, EvidriloDestination.PROJECT_FAMILY_DETAIL)

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
        val activeCount = loaded.projects.size
        val activeLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(
            projectProEntitlementActive.value,
        )
        if (activeCount >= activeLimit) {
            navigationState = navigationState.open(EvidriloDestination.PROJECTS)
        } else {
            navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG)
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
                projectAiAccountRequirementMessage(
                    guestModeEnabled = TEMPORARY_GUEST_MODE_ENABLED,
                    consentManagement = false,
                ),
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
        if (TEMPORARY_GUEST_MODE_ENABLED ||
            !projectAiSessionMatchesOwner(requestAccountId, currentBillingAccountId())
        ) {
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
        if (TEMPORARY_GUEST_MODE_ENABLED ||
            !projectAiSessionMatchesOwner(requestAccountId, currentBillingAccountId())
        ) {
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
                        studentProjectNotice = projectAiClientValidationFailureMessage(issue, result.creditCost)
                        discardProjectAiPreview(result.requestId, result.creditCost)
                    }
                }
                else -> {
                    projectAiRequestToken = null
                    projectAiScaffoldState = result.toProjectAiScaffoldUiState()
                    val creditOutcomeNeedsRefresh =
                        (result is ProjectAiScaffoldGatewayResult.Unavailable &&
                            result.code == "PROJECT_AI_USAGE_SETTLEMENT_UNKNOWN") ||
                            (result is ProjectAiScaffoldGatewayResult.Failed && result.outcomeUnknown)
                    if (creditOutcomeNeedsRefresh) {
                        val credits = try {
                            aiGateway.getCredits()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            null
                        }
                        if (credits is dev.nextgen.mobile.ai.AiGatewayResult.CreditsFound &&
                            projectAiSessionMatchesOwner(requestAccountId, currentBillingAccountId())
                        ) {
                            aiCredits = credits.value
                        }
                    }
                }
            }
        }
    }

    fun refreshProjectAiConsent() {
        val accountId = currentBillingAccountId()
        if (TEMPORARY_GUEST_MODE_ENABLED || accountId == null) {
            projectAiConsentState = ProjectAiConsentUiState.Unavailable(
                projectAiAccountRequirementMessage(
                    guestModeEnabled = TEMPORARY_GUEST_MODE_ENABLED,
                    consentManagement = true,
                ),
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
        if (TEMPORARY_GUEST_MODE_ENABLED || accountId == null) {
            projectAiConsentState = ProjectAiConsentUiState.Unavailable(
                projectAiAccountRequirementMessage(
                    guestModeEnabled = TEMPORARY_GUEST_MODE_ENABLED,
                    consentManagement = true,
                ),
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
        if (TEMPORARY_GUEST_MODE_ENABLED || accountId == null) {
            projectAiConsentState = ProjectAiConsentUiState.Unavailable(
                projectAiAccountRequirementMessage(
                    guestModeEnabled = TEMPORARY_GUEST_MODE_ENABLED,
                    consentManagement = true,
                ),
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

    fun finishProjectAiStageAssistRequest(
        result: ProjectAiStageAssistResult,
        accountId: String,
        identity: ProjectAiStageAssistContextIdentity,
        stageTitle: String,
        request: ProjectAiStageAssistRequest,
        idempotencyKey: String,
        consentGeneration: Long,
        uiRequestToken: String,
    ) {
        if (projectAiStageAssistRequestToken != uiRequestToken) return
        projectAiStageAssistRequestToken = null
        val accountStillMatches = projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
        projectAiStageAssistState = when (result) {
            is ProjectAiStageAssistResult.Preview -> {
                val preview = result.value
                if (preview.projectId != request.projectId || preview.baseProjectRevision != request.baseProjectRevision ||
                    preview.projectBindingGeneration != request.baseProjectBindingGeneration ||
                    preview.consentGeneration.toLong() != consentGeneration || preview.stageId != request.stageId ||
                    preview.operationId != request.operationId || preview.templateId != request.templateId ||
                    preview.templateVersion != request.templateVersion
                ) {
                    ProjectAiStageAssistUiState.Rejected(
                        "The server response did not match the selected project revision. It was not applied.",
                        identity,
                    )
                } else {
                    // Keep a response bound to its originating account/revision even if either
                    // changed while the request was in flight. The panel hides cross-account
                    // content and exposes same-account stale previews only for dismissal.
                    ProjectAiStageAssistUiState.Preview(
                        ProjectAiStageAssistSession(
                            accountId = accountId,
                            projectId = request.projectId,
                            templateId = request.templateId,
                            templateVersion = request.templateVersion,
                            projectRevision = request.baseProjectRevision,
                            bindingGeneration = request.baseProjectBindingGeneration,
                            stageId = request.stageId,
                            operationId = request.operationId,
                            selectedFieldValues = request.selectedFieldValues,
                            selectedEvidence = request.selectedEvidence,
                            preview = preview,
                        ),
                    )
                }
            }
            is ProjectAiStageAssistResult.Deferred -> if (result.reason == ProjectAiStageAssistDeferredReason.AUTH_REQUIRED ||
                result.reason == ProjectAiStageAssistDeferredReason.SESSION_EXPIRED
            ) {
                ProjectAiStageAssistUiState.Unavailable(
                    "The verified account session is unavailable. No suggestion was applied; manual work remains available.",
                    identity,
                )
            } else {
                ProjectAiStageAssistUiState.Unavailable("Project AI is not configured for this request.", identity)
            }
            is ProjectAiStageAssistResult.Unavailable -> {
                if (result.code == "PROJECT_AI_USAGE_SETTLEMENT_UNKNOWN" && accountStillMatches) {
                    accountScope.launch {
                        val credits = try { aiGateway.getCredits() } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) { null }
                        if (credits is AiGatewayResult.CreditsFound &&
                            projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
                        ) aiCredits = credits.value
                    }
                }
                ProjectAiStageAssistUiState.Unavailable(
                    if (result.code == "PROJECT_AI_USAGE_SETTLEMENT_UNKNOWN") {
                        "Project AI could not confirm the credit settlement. Check your shared balance before retrying; the project was not changed."
                    } else {
                        "Project AI is unavailable (${result.code}). Your saved project is unchanged; continue manually or retry later."
                    },
                    identity,
                )
            }
            is ProjectAiStageAssistResult.Rejected -> {
                val replayConflict = result.code in setOf("PROJECT_AI_REQUEST_REPLAYED", "PROJECT_AI_REQUEST_IN_PROGRESS")
                if (replayConflict) {
                    if (accountStillMatches) {
                        accountScope.launch {
                            val credits = try { aiGateway.getCredits() } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Exception) { null }
                            if (credits is AiGatewayResult.CreditsFound &&
                                projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
                            ) aiCredits = credits.value
                        }
                    }
                    ProjectAiStageAssistUiState.OutcomeUnknown(
                        accountId = accountId,
                        context = identity,
                        stageTitle = stageTitle,
                        request = request,
                        idempotencyKey = idempotencyKey,
                        consentGeneration = consentGeneration,
                    )
                } else {
                    if (accountStillMatches) {
                        result.creditCost?.let { cost ->
                            studentProjectNotice = "Consent or project state changed after provider processing. The response was withheld; actual provider usage cost $cost shared AI credit${if (cost == 1) "" else "s"}. No project change was applied."
                            refreshProjectAiActivity(request.projectId)
                            accountScope.launch {
                                val credits = try { aiGateway.getCredits() } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (_: Exception) { null }
                                if (credits is AiGatewayResult.CreditsFound &&
                                    projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
                                ) aiCredits = credits.value
                            }
                        }
                    }
                    ProjectAiStageAssistUiState.Rejected(
                        if (result.creditCost != null) {
                            "The response was withheld because consent or the project changed during processing. ${result.creditCost} credit${if (result.creditCost == 1) "" else "s"} was charged for verified provider usage; no project change was applied."
                        } else {
                            "Project AI rejected this request (${result.code}). Your saved project is unchanged."
                        },
                        identity,
                    )
                }
            }
            is ProjectAiStageAssistResult.Failed -> if (result.outcomeUnknown || result.sameIntentReplayAllowed) {
                if (accountStillMatches) {
                    accountScope.launch {
                        val credits = try { aiGateway.getCredits() } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) { null }
                        if (credits is AiGatewayResult.CreditsFound &&
                            projectAiSessionMatchesOwner(accountId, currentBillingAccountId())
                        ) aiCredits = credits.value
                    }
                }
                ProjectAiStageAssistUiState.OutcomeUnknown(
                    accountId = accountId,
                    context = identity,
                    stageTitle = stageTitle,
                    request = request,
                    idempotencyKey = result.idempotencyKey,
                    consentGeneration = consentGeneration,
                )
            } else {
                ProjectAiStageAssistUiState.Unavailable(
                    "The request could not be completed (${result.code}). Your saved project is unchanged.",
                    identity,
                )
            }
        }
    }

    fun requestProjectAiStageAssist(
        stageId: String,
        operationId: String,
        selectedFields: Map<String, String>,
        selectedEvidence: List<ProjectAiStageAssistSelectedEvidence>,
    ) {
        val draft = activeStudentProjectDraft
        val template = draft?.templateSnapshot
        val signedIn = accountSession as? AccountSession.SignedIn
        val accountId = signedIn?.account?.accountId
        val unresolvedOwner = when (val current = projectAiStageAssistState) {
            is ProjectAiStageAssistUiState.Requesting -> current.context.accountId
            is ProjectAiStageAssistUiState.OutcomeUnknown -> current.accountId
            is ProjectAiStageAssistUiState.Preview -> current.session.accountId
            is ProjectAiStageAssistUiState.Settling -> current.context?.accountId
            is ProjectAiStageAssistUiState.SettlementFailed -> current.session.accountId
            ProjectAiStageAssistUiState.Idle,
            is ProjectAiStageAssistUiState.Unavailable,
            is ProjectAiStageAssistUiState.Rejected,
            -> null
        }
        if (unresolvedOwner != null) {
            studentProjectNotice = if (unresolvedOwner == accountId) {
                "Review, apply, or dismiss the previous Project AI result before requesting another one."
            } else {
                "A prior Project AI result is private to another signed-in account. Sign in to that account to reconcile it before starting another Project AI request."
            }
            return
        }
        if (TEMPORARY_GUEST_MODE_ENABLED || !accountRestoreComplete || signedIn == null ||
            accountId == null || !signedIn.account.emailVerified
        ) {
            projectAiStageAssistState = ProjectAiStageAssistUiState.Unavailable(
                "Sign in with a verified account before using Project AI. Your project remains local and manual editing is still available.",
            )
            return
        }
        if (draft == null || template == null ||
            template.publication != dev.nextgen.mobile.domain.project.ProjectTemplatePublication.PUBLISHED ||
            studentProjectEditorIsDirty
        ) {
            projectAiStageAssistState = ProjectAiStageAssistUiState.Unavailable(
                "Save a project created from a published method before requesting stage assistance. No context was sent.",
            )
            return
        }
        val stage = template.steps.singleOrNull { it.id == stageId }
        val operation = stage?.aiOperations?.singleOrNull { it.id == operationId }
        if (operation == null || selectedFields.isEmpty() && selectedEvidence.isEmpty() ||
            selectedFields.keys.any { it !in operation.inputFieldIds } ||
            selectedFields.values.any { it.length > 8_000 || '\u0000' in it } ||
            selectedEvidence.size > 32 || selectedEvidence.map(ProjectAiStageAssistSelectedEvidence::id).distinct().size != selectedEvidence.size
        ) {
            projectAiStageAssistState = ProjectAiStageAssistUiState.Rejected(
                "The selected project context does not match this stage. Review the selections and try again.",
            )
            return
        }
        val identity = ProjectAiStageAssistContextIdentity(
            accountId = accountId,
            projectId = draft.id,
            templateId = template.id,
            templateVersion = template.version,
            projectRevision = draft.revision,
            stageId = stageId,
            operationId = operationId,
        )
        val requestToken = newAnalyticsEventId()
        projectAiStageAssistRequestToken = requestToken
        projectAiStageAssistState = ProjectAiStageAssistUiState.Requesting(identity, stage.title)
        accountScope.launch {
            val consentResult = try {
                projectAiConsentGateway.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_UNAVAILABLE", retryable = true)
            }
            if (projectAiStageAssistRequestToken != requestToken) return@launch
            if (!projectAiSessionMatchesOwner(accountId, currentBillingAccountId())) {
                projectAiStageAssistRequestToken = null
                projectAiStageAssistState = ProjectAiStageAssistUiState.Idle
                return@launch
            }
            val consent = (consentResult as? ProjectAiConsentGatewayResult.State)?.value
            if (consent == null || !consent.granted || consent.policyVersion != PROJECT_AI_CONSENT_POLICY_VERSION) {
                projectAiConsentState = consentResult.toProjectAiConsentUiState()
                projectAiStageAssistState = ProjectAiStageAssistUiState.Unavailable(
                    "Current account consent could not be verified. No project content was sent; review consent and retry.",
                    identity,
                )
                projectAiStageAssistRequestToken = null
                return@launch
            }
            projectAiConsentState = ProjectAiConsentUiState.Granted
            val installationId = projectAiInstallationId
            if (installationId == null) {
                projectAiStageAssistState = ProjectAiStageAssistUiState.Unavailable(
                    "Secure installation storage is unavailable. No project content was sent.",
                    identity,
                )
                projectAiStageAssistRequestToken = null
                return@launch
            }
            val selectedEvidenceIds = selectedEvidence.map(ProjectAiStageAssistSelectedEvidence::id).sorted()
            val binding = try {
                projectAiStageAssistGateway.registerLocalProjectContext(
                    projectId = draft.id,
                    installationId = installationId,
                    templateId = template.id,
                    templateVersion = template.version,
                    projectRevision = draft.revision,
                    availableEvidenceIds = selectedEvidenceIds,
                    explicitlyConfirmedForRequest = true,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiLocalContextBindingResult.Unavailable("PROJECT_AI_CONTEXT_SYNC_FAILED")
            }
            if (projectAiStageAssistRequestToken != requestToken) return@launch
            if (!projectAiSessionMatchesOwner(accountId, currentBillingAccountId()) ||
                activeStudentProjectDraft?.let { it.id == draft.id && it.revision == draft.revision } != true ||
                studentProjectEditorIsDirty
            ) {
                projectAiStageAssistRequestToken = null
                projectAiStageAssistState = ProjectAiStageAssistUiState.Idle
                return@launch
            }
            val registered = binding as? ProjectAiLocalContextBindingResult.Registered
            if (registered == null || registered.projectRevision != draft.revision) {
                projectAiStageAssistState = ProjectAiStageAssistUiState.Unavailable(
                    when (binding) {
                        is ProjectAiLocalContextBindingResult.Deferred -> "A verified account session is required to register this project revision. No AI request was sent."
                        is ProjectAiLocalContextBindingResult.Unavailable -> "The server could not securely register the selected project context. No AI request was sent."
                        is ProjectAiLocalContextBindingResult.Rejected -> "The server rejected this project context (${binding.code}). Save or review the project and retry."
                        is ProjectAiLocalContextBindingResult.Registered -> "The registered project revision did not match the current revision. No AI request was sent."
                    },
                    identity,
                )
                projectAiStageAssistRequestToken = null
                return@launch
            }
            val request = ProjectAiStageAssistRequest(
                installationId = installationId,
                projectId = draft.id,
                templateId = template.id,
                templateVersion = template.version,
                stageId = stageId,
                operationId = operationId,
                baseProjectRevision = draft.revision,
                baseProjectBindingGeneration = registered.bindingGeneration,
                selectedFieldValues = selectedFields.toMap(),
                selectedEvidence = selectedEvidence.toList(),
                locale = "en",
            )
            val result = try {
                projectAiStageAssistGateway.generatePreview(
                    request = request,
                    idempotencyKey = requestToken,
                    explicitlyConfirmedForRequest = true,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiStageAssistResult.Unavailable("PROJECT_AI_UNAVAILABLE")
            }
            finishProjectAiStageAssistRequest(
                result = result,
                accountId = accountId,
                identity = identity,
                stageTitle = stage.title,
                request = request,
                idempotencyKey = requestToken,
                consentGeneration = consent.generation,
                uiRequestToken = requestToken,
            )
        }
    }

    fun retryProjectAiStageAssistRequest(unknown: ProjectAiStageAssistUiState.OutcomeUnknown) {
        val signedIn = accountSession as? AccountSession.SignedIn
        if (unknown.idempotencyKey == null || signedIn?.account?.emailVerified != true ||
            !projectAiSessionMatchesOwner(unknown.accountId, currentBillingAccountId()) ||
            projectAiStageAssistState != unknown || projectAiStageAssistRequestToken != null
        ) return

        val uiRequestToken = newAnalyticsEventId()
        projectAiStageAssistRequestToken = uiRequestToken
        projectAiStageAssistState = ProjectAiStageAssistUiState.Requesting(unknown.context, unknown.stageTitle)
        accountScope.launch {
            val consentResult = try {
                projectAiConsentGateway.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_UNAVAILABLE", retryable = true)
            }
            if (projectAiStageAssistRequestToken != uiRequestToken) return@launch
            val consent = (consentResult as? ProjectAiConsentGatewayResult.State)?.value
            if (!projectAiSessionMatchesOwner(unknown.accountId, currentBillingAccountId()) ||
                consent == null || !consent.granted || consent.policyVersion != PROJECT_AI_CONSENT_POLICY_VERSION ||
                consent.generation != unknown.consentGeneration
            ) {
                projectAiStageAssistRequestToken = null
                projectAiConsentState = consentResult.toProjectAiConsentUiState()
                projectAiStageAssistState = unknown
                return@launch
            }
            projectAiConsentState = ProjectAiConsentUiState.Granted
            val result = try {
                projectAiStageAssistGateway.generatePreview(
                    request = unknown.request,
                    idempotencyKey = requireNotNull(unknown.idempotencyKey),
                    explicitlyConfirmedForRequest = true,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiStageAssistResult.Failed(
                    "PROJECT_AI_OUTCOME_UNKNOWN",
                    retryable = true,
                    outcomeUnknown = true,
                    sameIntentReplayAllowed = true,
                    idempotencyKey = unknown.idempotencyKey,
                )
            }
            finishProjectAiStageAssistRequest(
                result = result,
                accountId = unknown.accountId,
                identity = unknown.context,
                stageTitle = unknown.stageTitle,
                request = unknown.request,
                idempotencyKey = unknown.idempotencyKey,
                consentGeneration = consent.generation,
                uiRequestToken = uiRequestToken,
            )
        }
    }

    fun settleProjectAiStageAssist(
        session: ProjectAiStageAssistSession,
        outcome: ProjectAiStageAssistOutcome,
        resultProjectRevision: Int? = null,
        resultBindingGeneration: Long? = null,
        message: String = "Recording this AI suggestion outcome…",
    ) {
        if (!projectAiSessionMatchesOwner(session.accountId, currentBillingAccountId())) {
            projectAiStageAssistState = ProjectAiStageAssistUiState.SettlementFailed(
                session, outcome, resultProjectRevision, resultBindingGeneration,
                "The original account must be active to reconcile this AI activity.", canRetry = true,
            )
            return
        }
        projectAiStageAssistState = ProjectAiStageAssistUiState.Settling(message, session.contextIdentity)
        accountScope.launch {
            var finalBindingGeneration = resultBindingGeneration
            if (outcome == ProjectAiStageAssistOutcome.APPLIED || outcome == ProjectAiStageAssistOutcome.EDITED) {
                val resultDraft = activeStudentProjectDraft?.takeIf {
                    it.id == session.projectId && it.revision == resultProjectRevision
                }
                if (resultDraft == null) {
                    projectAiStageAssistState = ProjectAiStageAssistUiState.SettlementFailed(
                        session, ProjectAiStageAssistOutcome.STALE, null, null,
                        "The applied revision is no longer the current local project revision.", canRetry = false,
                    )
                    return@launch
                }
                if (finalBindingGeneration == null) {
                    val binding = try {
                        projectAiStageAssistGateway.registerLocalProjectContext(
                            projectId = resultDraft.id,
                            installationId = projectAiInstallationId ?: return@launch,
                            templateId = session.templateId,
                            templateVersion = session.templateVersion,
                            projectRevision = resultDraft.revision,
                            availableEvidenceIds = session.selectedEvidence.map(ProjectAiStageAssistSelectedEvidence::id).sorted(),
                            explicitlyConfirmedForRequest = true,
                        )
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        ProjectAiLocalContextBindingResult.Unavailable("PROJECT_AI_CONTEXT_SYNC_FAILED")
                    }
                    finalBindingGeneration = (binding as? ProjectAiLocalContextBindingResult.Registered)?.bindingGeneration
                }
                if (finalBindingGeneration == null) {
                    projectAiStageAssistState = ProjectAiStageAssistUiState.SettlementFailed(
                        session, outcome, resultProjectRevision, null,
                        "The suggestion is saved locally, but the server could not register its new revision. Retry to reconcile activity; the token-based preview charge is already settled.",
                        canRetry = true,
                    )
                    return@launch
                }
            }
            val settled = try {
                projectAiStageAssistGateway.settle(
                    requestId = session.preview.requestId,
                    installationId = projectAiInstallationId ?: return@launch,
                    outcome = outcome,
                    baseProjectRevision = session.projectRevision,
                    baseProjectBindingGeneration = session.bindingGeneration,
                    resultProjectRevision = resultProjectRevision,
                    resultProjectBindingGeneration = finalBindingGeneration,
                    expectedCreditCost = session.preview.creditCost,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiStageAssistSettlementResult.Unavailable("PROJECT_AI_SETTLEMENT_UNAVAILABLE")
            }
            if (!projectAiSessionMatchesOwner(session.accountId, currentBillingAccountId())) {
                projectAiStageAssistState = if (settled is ProjectAiStageAssistSettlementResult.Settled) {
                    ProjectAiStageAssistUiState.Idle
                } else {
                    ProjectAiStageAssistUiState.SettlementFailed(
                        session, outcome, resultProjectRevision, finalBindingGeneration,
                        "The original account must be active to reconcile this AI activity outcome.", canRetry = true,
                    )
                }
                return@launch
            }
            when (settled) {
                is ProjectAiStageAssistSettlementResult.Settled -> {
                    projectAiStageAssistState = ProjectAiStageAssistUiState.Idle
                    studentProjectNotice = when (settled.outcome) {
                        ProjectAiStageAssistOutcome.APPLIED, ProjectAiStageAssistOutcome.EDITED ->
                            "Selected AI suggestions were saved as a new project revision. ${settled.creditCost} shared AI credit${if (settled.creditCost == 1) "" else "s"} reflect actual provider usage."
                        ProjectAiStageAssistOutcome.DISMISSED, ProjectAiStageAssistOutcome.STALE ->
                            "AI suggestions were not applied. The valid preview cost ${settled.creditCost} credit${if (settled.creditCost == 1) "" else "s"}; dismissal does not reverse provider usage."
                    }
                    refreshProjectAiActivity(session.projectId)
                    val credits = try { aiGateway.getCredits() } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) { null }
                    if (credits is AiGatewayResult.CreditsFound &&
                        projectAiSessionMatchesOwner(session.accountId, currentBillingAccountId())
                    ) aiCredits = credits.value
                }
                is ProjectAiStageAssistSettlementResult.Deferred -> projectAiStageAssistState = ProjectAiStageAssistUiState.SettlementFailed(
                    session, outcome, resultProjectRevision, finalBindingGeneration,
                    "A verified account session is required to record the AI activity. The preview charge is already settled.", canRetry = true,
                )
                is ProjectAiStageAssistSettlementResult.Unavailable -> projectAiStageAssistState = ProjectAiStageAssistUiState.SettlementFailed(
                    session, outcome, resultProjectRevision, finalBindingGeneration,
                    "The AI activity could not be recorded (${settled.code}). The preview charge is already settled.", canRetry = true,
                )
                is ProjectAiStageAssistSettlementResult.Rejected -> projectAiStageAssistState = ProjectAiStageAssistUiState.SettlementFailed(
                    session, outcome, resultProjectRevision, finalBindingGeneration,
                    "The server rejected the AI activity outcome (${settled.code}). Your local project remains unchanged unless it was already applied.", canRetry = false,
                )
                is ProjectAiStageAssistSettlementResult.Failed -> projectAiStageAssistState = ProjectAiStageAssistUiState.SettlementFailed(
                    session, outcome, resultProjectRevision, finalBindingGeneration,
                    "The AI activity outcome is uncertain (${settled.code}); its preview charge is already settled.",
                    canRetry = settled.retryable || settled.sameIntentReplayAllowed,
                )
            }
        }
    }

    fun applyProjectAiStageAssist(
        session: ProjectAiStageAssistSession,
        selectedProposalIds: Set<String>,
        editedValues: Map<String, String>,
    ) {
        val current = activeStudentProjectDraft
        val proposals = session.preview.items.filter { it.kind == dev.nextgen.mobile.projectcatalog.ProjectAiStageAssistItemKind.PROPOSAL }
        val selected = proposals.filter { it.id in selectedProposalIds }
        val targets = selected.mapNotNull { it.targetFieldId }
        val currentTemplate = current?.templateSnapshot
        if (!projectAiSessionMatchesOwner(session.accountId, currentBillingAccountId()) || current == null ||
            current.id != session.projectId || current.revision != session.projectRevision ||
            currentTemplate?.id != session.templateId || currentTemplate?.version != session.templateVersion ||
            studentProjectEditorIsDirty || selected.isEmpty() || selected.size != selectedProposalIds.size ||
            targets.size != selected.size || targets.distinct().size != targets.size
        ) {
            settleProjectAiStageAssist(session, ProjectAiStageAssistOutcome.STALE, message = "The saved project changed. The AI suggestions are stale and will not be applied.")
            return
        }
        val beforeValues = selected.associate { item -> item.targetFieldId!! to item.beforeValue.orEmpty() }
        val confirmedValues = selected.associate { item -> item.targetFieldId!! to editedValues[item.id].orEmpty() }
        if (beforeValues.keys != confirmedValues.keys || beforeValues.any { (fieldId, value) ->
                session.selectedFieldValues[fieldId] != value
            }
        ) {
            projectAiStageAssistState = ProjectAiStageAssistUiState.Rejected(
                "The preview no longer matches the selected saved fields. Nothing was applied.", session.contextIdentity,
            )
            settleProjectAiStageAssist(session, ProjectAiStageAssistOutcome.STALE)
            return
        }
        projectAiStageAssistState = ProjectAiStageAssistUiState.Settling(
            "Rechecking consent and saving only the selected fields…", session.contextIdentity,
        )
        accountScope.launch {
            val consentResult = try { projectAiConsentGateway.refresh() } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiConsentGatewayResult.Failed("PROJECT_AI_CONSENT_UNAVAILABLE", retryable = true)
            }
            val consent = (consentResult as? ProjectAiConsentGatewayResult.State)?.value
            val latest = activeStudentProjectDraft
            if (consent == null || !consent.granted || consent.policyVersion != PROJECT_AI_CONSENT_POLICY_VERSION ||
                consent.generation != session.preview.consentGeneration.toLong() ||
                !projectAiSessionMatchesOwner(session.accountId, currentBillingAccountId()) || latest == null ||
                latest.id != session.projectId || latest.revision != session.projectRevision || studentProjectEditorIsDirty
            ) {
                projectAiConsentState = consentResult.toProjectAiConsentUiState()
                settleProjectAiStageAssist(
                    session,
                    ProjectAiStageAssistOutcome.STALE,
                    message = "Consent or the saved project changed. No AI suggestion was applied.",
                )
                return@launch
            }
            val result = studentProjectDraftFlow.applyAiStageAssistToProject(
                projectId = session.projectId,
                expectedTemplateId = session.templateId,
                expectedTemplateVersion = session.templateVersion,
                stageId = session.stageId,
                operationId = session.operationId,
                expectedBaseRevision = session.projectRevision,
                expectedBeforeValues = beforeValues,
                confirmedValues = confirmedValues,
            )
            if (result !is StudentProjectDraftFlowResult.Value) {
                studentProjectSaveError = studentProjectDraftFlowMessage(result)
                settleProjectAiStageAssist(session, ProjectAiStageAssistOutcome.STALE, message = "The project reducer rejected a stale or unsupported proposal; nothing was applied.")
                return@launch
            }
            activeStudentProjectDraft = result.value
            replaceStudentProjectInList(result.value)
            studentProjectEditorIsDirty = false
            studentProjectSaveError = null
            val binding = try {
                projectAiStageAssistGateway.registerLocalProjectContext(
                    projectId = result.value.id,
                    installationId = projectAiInstallationId ?: return@launch,
                    templateId = session.templateId,
                    templateVersion = session.templateVersion,
                    projectRevision = result.value.revision,
                    availableEvidenceIds = session.selectedEvidence.map(ProjectAiStageAssistSelectedEvidence::id).sorted(),
                    explicitlyConfirmedForRequest = true,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ProjectAiLocalContextBindingResult.Unavailable("PROJECT_AI_CONTEXT_SYNC_FAILED")
            }
            val bindingGeneration = (binding as? ProjectAiLocalContextBindingResult.Registered)
                ?.takeIf { it.projectRevision == result.value.revision }
                ?.bindingGeneration
            settleProjectAiStageAssist(
                session = session,
                outcome = if (selected.any { editedValues[it.id].orEmpty() != it.afterValue.orEmpty() }) {
                    ProjectAiStageAssistOutcome.EDITED
                } else ProjectAiStageAssistOutcome.APPLIED,
                resultProjectRevision = result.value.revision,
                resultBindingGeneration = bindingGeneration,
                message = "The selected suggestion is saved locally; recording its activity and revision…",
            )
        }
    }

    fun dismissProjectAiStageAssist(session: ProjectAiStageAssistSession) {
        val currentDraft = activeStudentProjectDraft
        val outcome = if (currentDraft?.id != session.projectId ||
            currentDraft.revision != session.projectRevision || studentProjectEditorIsDirty
        ) ProjectAiStageAssistOutcome.STALE else ProjectAiStageAssistOutcome.DISMISSED
        settleProjectAiStageAssist(
            session,
            outcome,
            message = "Recording dismissal…",
        )
    }

    fun retryProjectAiStageAssistSettlement(failed: ProjectAiStageAssistUiState.SettlementFailed) {
        settleProjectAiStageAssist(
            failed.session,
            failed.outcome,
            failed.resultProjectRevision,
            failed.resultBindingGeneration,
            "Retrying the same activity settlement…",
        )
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
        if (TEMPORARY_GUEST_MODE_ENABLED ||
            !projectAiSessionMatchesOwner(projectAiBoundAccountId, currentBillingAccountId()) ||
            preview == null || preview.requestId != requestId || preview.proposal != proposal || preview.creditCost != creditCost
        ) {
            studentProjectNotice = "This AI suggestion no longer matches the active account or project. No suggestion was applied."
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
        if (TEMPORARY_GUEST_MODE_ENABLED ||
            !projectAiSessionMatchesOwner(projectAiBoundAccountId, currentBillingAccountId()) ||
            preview == null || preview.requestId != requestId || preview.proposal != proposal || preview.creditCost != creditCost
        ) {
            studentProjectNotice = "This AI suggestion no longer matches the active account or project. No suggestion was applied."
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

    fun resumeStudentProject(projectId: String, sectionId: String? = null) {
        if (!requireDestinationAccess(EvidriloDestination.PROJECTS)) return
        releaseActiveProjectAiPreview()
        when (val result = studentProjectDraftFlow.resume(projectId)) {
            is StudentProjectDraftFlowResult.Value -> {
                activeStudentProjectDraft = result.value
                studentProjectSaveError = null
                studentProjectEditorIsDirty = false
                studentProjectExitConfirmation = false
                val targetSection = studentProjectEditorSections(result.value).firstOrNull { it.navigationId == sectionId }
                if (targetSection != null) {
                    val positionSaved = dev.nextgen.mobile.storage.createProjectSectionBookmarkStore().write(
                        projectSectionBookmarkKey(result.value), targetSection.navigationId,
                    ) == dev.nextgen.mobile.storage.LocalStorageWriteResult.SAVED
                    studentProjectNotice = if (positionSaved) null
                        else "Your project opened, but its section position could not be saved. Choose ${targetSection.title} from Sections."
                }
                practiceProjectSectionIntent = null
                navigationState = navigationState.open(EvidriloDestination.PROJECT_EDITOR)
            }
            else -> {
                if (projectExportIntentId == projectId) projectExportIntentId = null
                studentProjectNotice = studentProjectDraftFlowMessage(result)
            }
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
        deadlineDate: String?,
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
            deadlineChange = StudentProjectDeadlineChange.Set(deadlineDate),
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
            projectAiGeneralChatRequestCoordinator.cancel()
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

    fun permanentlyDeleteStudentProject(project: StudentProjectDraft): Boolean {
        if (!requireDestinationAccess(EvidriloDestination.PROJECTS)) return false
        val result = studentProjectDraftFlow.permanentlyDelete(project.id)
        val deleted = result is StudentProjectDraftFlowResult.Value ||
            studentProjectDraftFlow.resume(project.id) == StudentProjectDraftFlowResult.NotFound
        if (deleted) {
            dev.nextgen.mobile.storage.createProjectSectionBookmarkStore().remove(projectSectionBookmarkKey(project))
            pendingProjectDeletionNotice = if (result is StudentProjectDraftFlowResult.Value) null else
                "The project was removed. Attachment cleanup is pending; Evidrilo will retry it when the project list opens."
            studentProjectNotice = pendingProjectDeletionNotice
            studentProjectListReload += 1
        } else studentProjectNotice = studentProjectDraftFlowMessage(result)
        return deleted
    }

    LaunchedEffect(
        navigationState.current,
        studentProjectListReload,
        accountSession,
        accountRestoreComplete,
        revenueCatIdentityAccountId,
    ) {
        if (navigationState.current == EvidriloDestination.PROJECTS ||
            (navigationState.current == EvidriloDestination.HOME || navigationState.current == EvidriloDestination.PROFILE)
        ) {
            studentProjectListState = StudentProjectListUiState.Loading
            val result = studentProjectDraftFlow.list()
            studentProjectListState = result.toStudentProjectListUiState()
            studentProjectNotice = when (result) {
                is StudentProjectDraftFlowResult.Value -> if (practiceProjectSectionIntent != null)
                    "Choose a project to use this move with your own material. Practice answers remain separate." else pendingProjectDeletionNotice
                else -> studentProjectDraftFlowMessage(result)
            }
            pendingProjectDeletionNotice = null
            projectProEntitlementActive.value = false
            if (canUseRevenueCatForCurrentAccount()) {
                val entitlementToken = billingRequestGate.begin(currentBillingAccountId())
                billingGateway.refreshAccess { outcome ->
                    if (billingRequestGate.isCurrent(entitlementToken, currentBillingAccountId()) &&
                        (navigationState.current == EvidriloDestination.PROJECTS || (navigationState.current == EvidriloDestination.HOME || navigationState.current == EvidriloDestination.PROFILE))
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
    // The local Practice host receives only a fresh entitlement bound to this signed-in account.
    LaunchedEffect(navigationState.current, accountSession, accountRestoreComplete, revenueCatIdentityAccountId, practiceAccessReload) {
        if (navigationState.current in setOf(EvidriloDestination.PRACTICE, EvidriloDestination.CASES)) {
            practiceAccessChecking = canUseRevenueCatForCurrentAccount()
            projectProEntitlementActive.value = false
            if (canUseRevenueCatForCurrentAccount()) {
                val entitlementToken = billingRequestGate.begin(currentBillingAccountId())
                billingGateway.refreshAccess { outcome ->
                    if (billingRequestGate.isCurrent(entitlementToken, currentBillingAccountId()) &&
                        navigationState.current in setOf(EvidriloDestination.PRACTICE, EvidriloDestination.CASES)
                    ) {
                        projectProEntitlementActive.value = outcome is BillingOutcome.Access && outcome.value == PremiumAccess.UNLOCKED
                        practiceAccessChecking = false
                    }
                }
            }
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
        if (!ACCOUNT_AUTH_ENABLED) {
            accountAuthRestoreComplete = true
            accountRestoreComplete = true
            return@LaunchedEffect
        }
        val result = try {
            accountGateway.restore()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AccountGatewayResult.Offline
        }
        acceptAccountGatewayResult(result)
        accountRestoreComplete = true
        accountAuthRestoreComplete = true
    }
    DisposableEffect(accountGateway) {
        if (!ACCOUNT_AUTH_ENABLED) {
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
        if (!ACCOUNT_AUTH_ENABLED) return
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
        if (TEMPORARY_GUEST_MODE_ENABLED) {
            remoteBaseCase = null
            remoteContentStatus = "Offline-ready bundled case"
            return@LaunchedEffect
        }
        if (TEMPORARY_GUEST_MODE_ENABLED || account == null || !account.emailVerified) {
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
        aiCreditBalanceRequestGeneration += 1
        aiCreditBalanceRefreshing = false
        aiCreditBalanceFailureMessage = null
        aiCreditBalanceCanRetry = true
        aiAssistState = if (accountBoundFeaturesEnabled && accountRestoreComplete && accountSession is AccountSession.SignedIn) {
            EvidriloAiAssistUiState.Loading
        } else {
            EvidriloAiAssistUiState.SignInRequired
        }
        if (!accountBoundFeaturesEnabled || !accountRestoreComplete || accountSession !is AccountSession.SignedIn) {
            return@LaunchedEffect
        }
        refreshAiCreditBalance(updateAssistState = true)
    }
    LaunchedEffect(accountSession, accountRestoreComplete) {
        platformProgress = null
        platformEntitlements = null
        platformStatus = if (!accountBoundFeaturesEnabled) {
            "Platform services are temporarily paused in local mode."
        } else if (accountRestoreComplete && accountSession is AccountSession.SignedIn) {
            "Refreshing verified platform projections…"
        } else {
            null
        }
        if (!accountBoundFeaturesEnabled || !accountRestoreComplete || accountSession !is AccountSession.SignedIn) {
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
        if (!accountBoundFeaturesEnabled || !accountRestoreComplete || accountSession !is AccountSession.SignedIn) return@LaunchedEffect

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
        if (accountBoundFeaturesEnabled && accountSession is AccountSession.SignedIn) {
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
        val signedIn = accountSession as? AccountSession.SignedIn ?: return
        if (!accountRestoreComplete || !accountBoundFeaturesEnabled || !signedIn.account.emailVerified ||
            !analyticsTransmissionAllowed(analyticsConsent, TEMPORARY_GUEST_MODE_ENABLED)) return
        val boundAccount = currentBillingAccountId()
        accountScope.launch {
            val current = accountSession as? AccountSession.SignedIn
            if (current?.account?.emailVerified == true && currentBillingAccountId() == boundAccount &&
                analyticsTransmissionAllowed(analyticsConsent, TEMPORARY_GUEST_MODE_ENABLED)) {
                analyticsGateway.sendWithRetry(event, analyticsConsent)
            }
        }
    }
    val requestAiAssist: (AiAssistPurpose, String, List<AiConversationHistoryMessage>) -> Unit = { purpose, question, history ->
        val feedback = targetEvaluationFor(state)?.primaryFeedback
        when {
            !accountBoundFeaturesEnabled -> aiAssistState = EvidriloAiAssistUiState.Unavailable(
                message = "AI assistance is temporarily paused in local mode. Deterministic feedback remains available.",
                retryable = false,
            )
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
                        val accountStillMatches = accountId != null && currentBillingAccountId() == accountId
                        val refreshedCredits = if (accountStillMatches) {
                            try {
                                aiGateway.getCredits() as? AiGatewayResult.CreditsFound
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                        val currentCredits = refreshedCredits?.value?.takeIf {
                            accountId != null && currentBillingAccountId() == accountId
                        }
                        if (currentCredits != null) aiCredits = currentCredits
                        val staleRecoveryState = if (accountId != null && currentBillingAccountId() == accountId) {
                            staleAiConversationRecoveryState(result, currentCredits?.available)
                        } else {
                            null
                        }
                        val canRecoverStaleRequest = aiAssistRequestContextKey == requestContextKey &&
                            latestAiAssistantContextKey.value != requestContextKey
                        if (canRecoverStaleRequest) {
                            recoverFromStaleAiRequest(requestContextKey)
                            if (staleRecoveryState != null && accountId != null &&
                                currentBillingAccountId() == accountId && aiAssistRequestContextKey == null
                            ) {
                                aiAssistState = staleRecoveryState
                            }
                        }
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
                                creditCost = result.value.creditCost,
                                groundedAnchorIds = result.value.groundedAnchorIds,
                                requestId = result.value.requestId,
                                turnsUsed = result.value.turnsUsed,
                                proposal = result.value.proposal,
                            )
                        }
                        is AiConversationGatewayResult.Fallback -> {
                            val refreshedCredits = aiGateway.getCredits()
                            val currentCredits = (refreshedCredits as? AiGatewayResult.CreditsFound)?.value
                            if (currentCredits != null) aiCredits = currentCredits
                            aiConversationSession = activeSession.copy(turnsUsed = result.turnsUsed ?: activeSession.turnsUsed)
                            aiAssistState = EvidriloAiAssistUiState.Unavailable(
                                message = "AI could not answer this turn. No draft change was made; deterministic feedback remains authoritative.",
                                retryable = result.reasonCode == "AI_PROVIDER_UNAVAILABLE" ||
                                    result.reasonCode == "AI_PROVIDER_TIMEOUT" ||
                                    result.reasonCode == "AI_UNAVAILABLE",
                                turnsUsed = result.turnsUsed,
                                requestId = result.requestId,
                                creditCost = result.creditCost,
                                remainingCredits = currentCredits?.available,
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
    val clearAiConversation: () -> Unit = clearAiConversationAction@{
        if (!accountBoundFeaturesEnabled) {
            aiConversationSession = null
            aiConversationContextKey = null
            aiConversationClearState = EvidriloAiConversationClearState.Idle
            return@clearAiConversationAction
        }
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
        if (accountBoundFeaturesEnabled && failure?.sessionId == sessionId &&
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
        if (accountBoundFeaturesEnabled && accountSession is AccountSession.SignedIn) {
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
    val recommendationCanDisplay = accountBoundFeaturesEnabled &&
        navigationState.current == EvidriloDestination.HOME &&
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
        if (event is PremiumPracticeEvent.BillingResult && preferredProProductId != null) {
            premiumState = premiumReducer.reduce(premiumState, PremiumPracticeEvent.SelectOffer(preferredProProductId!!))
        }
    }
    fun refreshBillingAccessAfterManagedUi() {
        if (!canUseRevenueCatForCurrentAccount()) return
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
    val openManagedPaywall: () -> Unit = paywallAction@{
        if (!canUseRevenueCatForCurrentAccount()) return@paywallAction
        emitAnalytics(paywallViewedAnalyticsEvent(surfaceId = "revenuecat_paywall"))
        revenueCatPaywallVisible = true
    }
    val openCustomerCenter: () -> Unit = customerCenterAction@{
        if (!canUseRevenueCatForCurrentAccount()) return@customerCenterAction
        customerCenterVisible = true
    }
    LaunchedEffect(accountSession, accountRestoreComplete, billingIdentityReload) {
        recommendationSessionGeneration += 1
        billingRequestGate.invalidate()
        syncRequestGate.invalidate()
        syncJob?.cancel()
        syncJob = null
        syncBusy = false
        val requestId = billingIdentityRequestId + 1
        billingIdentityRequestId = requestId
        val activeBillingAccountId = currentBillingAccountId()
        if (revenueCatIdentityAccountId != null &&
            revenueCatIdentityAccountId != activeBillingAccountId
        ) {
            projectProEntitlementActive.value = false
            premiumRequestId += 1
            premiumBillingRequestActive = false
            premiumWaitingForIdentity = false
            premiumOpenRequestStarted = false
            premiumBusy = false
            billingRequestGate.invalidate()
            dispatchPremium(PremiumPracticeEvent.Close)
        }
        revenueCatIdentityAccountId = null
        if (accountBoundFeaturesEnabled && accountRestoreComplete) {
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
        if (!REVENUECAT_PRO_FEATURE_ENABLED || !accountRestoreComplete) return@LaunchedEffect
        when (val session = accountSession) {
            is AccountSession.SignedIn -> billingGateway.identifyCustomer(
                appUserId = session.account.accountId,
            ) { outcome ->
                if (requestId == billingIdentityRequestId &&
                    currentBillingAccountId() == session.account.accountId
                ) {
                    if (outcome is BillingOutcome.Access) {
                        revenueCatIdentityAccountId = session.account.accountId
                        if (!(premiumWaitingForIdentity &&
                                navigationState.current == EvidriloDestination.PREMIUM)
                        ) {
                            dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
                        }
                    } else {
                        revenueCatIdentityAccountId = null
                        if (premiumWaitingForIdentity &&
                            navigationState.current == EvidriloDestination.PREMIUM
                        ) {
                            premiumWaitingForIdentity = false
                            premiumOpenRequestStarted = false
                            premiumBusy = false
                        }
                        dispatchPremium(PremiumPracticeEvent.BillingResult(outcome))
                    }
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
        if (accountBoundFeaturesEnabled && accountRestoreComplete &&
            accountSession is AccountSession.SignedIn &&
            syncConsent == SyncConsent.GRANTED
        ) {
            syncNow()
        }
    }
    val closePremium: () -> Unit = {
        preferredProProductId = null
        premiumRequestId += 1
        premiumOpenRequestStarted = false
        premiumWaitingForIdentity = false
        premiumBillingRequestActive = false
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
    val openProComparison: () -> Unit = {
        preferredProProductId = null
        proComparisonVisible = true
    }
    val openPremium: () -> Unit = premiumAction@{
        if (!REVENUECAT_PRO_FEATURE_ENABLED) return@premiumAction
        if (!requireDestinationAccess(EvidriloDestination.PREMIUM)) return@premiumAction
        if (!accountRestoreComplete || accountSession !is AccountSession.SignedIn) return@premiumAction
        if (premiumOpenRequestStarted &&
            navigationState.current == EvidriloDestination.PREMIUM &&
            !premiumWaitingForIdentity
        ) {
            return@premiumAction
        }
        val openingPremium = navigationState.current != EvidriloDestination.PREMIUM
        premiumOpenRequestStarted = true
        if (openingPremium) emitAnalytics(paywallViewedAnalyticsEvent(surfaceId = "premium"))
        navigationState = navigationState.open(EvidriloDestination.PREMIUM)
        dispatchPremium(PremiumPracticeEvent.Open)
        if (!canUseRevenueCatForCurrentAccount()) {
            premiumWaitingForIdentity = true
            premiumBillingRequestActive = false
            premiumBusy = true
            billingIdentityReload += 1
            return@premiumAction
        }
        premiumWaitingForIdentity = false
        if (premiumBillingRequestActive) return@premiumAction
        premiumBillingRequestActive = true
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
                        dispatchPremium(PremiumPracticeEvent.BillingResult(offerOutcome.withRequestedProPlan(preferredProProductId)))
                        premiumBillingRequestActive = false
                        premiumOpenRequestStarted = false
                        premiumBusy = false
                    }
                }
            }
        }
    }
    LaunchedEffect(
        navigationState.current,
        accountSession,
        accountRestoreComplete,
        revenueCatIdentityAccountId,
    ) {
        if (navigationState.current == EvidriloDestination.PREMIUM &&
            canUseRevenueCatForCurrentAccount() &&
            ((premiumState is PremiumPracticeState.Hidden && !premiumOpenRequestStarted) ||
                premiumWaitingForIdentity)
        ) {
            premiumWaitingForIdentity = false
            premiumOpenRequestStarted = false
            openPremium()
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
    var practiceTabletContext by remember { mutableStateOf(false) }
    var practiceLessonOpen by remember { mutableStateOf(false) }
    val startTargetPractice: () -> Unit = {
        if (requireDestinationAccess(EvidriloDestination.PRACTICE)) {
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
        !proComparisonVisible &&
        (navigationState.current != EvidriloDestination.PRACTICE || practiceTabletContext) &&
        accountRestoreComplete &&
        accountSession is AccountSession.SignedIn &&
        !onboardingPresentation.isVisible &&
        !accountGateVisible &&
        !revenueCatPaywallVisible &&
        !customerCenterVisible &&
        premiumState is PremiumPracticeState.Hidden &&
        navigationState.current !in setOf(
            EvidriloDestination.HOME,
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
    val generalChatAccount = accountSession as? AccountSession.SignedIn
    val projectAiStageAssistAccountAvailable = accountBoundFeaturesEnabled && accountRestoreComplete &&
        generalChatAccount?.account?.emailVerified == true
    val aiCreditBalancePresentation = presentAiCreditBalance(
        accountReady = accountBoundFeaturesEnabled && accountRestoreComplete &&
            generalChatAccount?.account?.emailVerified == true,
        refreshing = aiCreditBalanceRefreshing,
        credits = aiCredits,
        failureMessage = aiCreditBalanceFailureMessage,
        canRetry = aiCreditBalanceCanRetry,
    )
    val projectAiStageAssistAccountMessage = when {
        !accountRestoreComplete -> "Checking the secure account session…"
        !accountBoundFeaturesEnabled -> "AI is paused in local guest mode. Your project stays available offline."
        generalChatAccount == null -> "Sign in with a verified account to use Project AI. Manual project work remains available."
        !generalChatAccount.account.emailVerified -> "Verify your email before sending selected project context to AI."
        else -> null
    }
    val generalChatAccessMessage = when {
        !accountRestoreComplete -> "Checking your secure account session…"
        !accountBoundFeaturesEnabled -> "AI is paused in local guest mode. Projects and evidence remain available on this device."
        generalChatAccount == null -> "Sign in with a verified account to use AI. The rest of Evidrilo remains available without sign-in."
        !generalChatAccount.account.emailVerified -> "Verify your email before sending a question to AI."
        else -> null
    }
    val generalChatCanOpenAccount = accountBoundFeaturesEnabled &&
        accountRestoreComplete &&
        (generalChatAccount == null || !generalChatAccount.account.emailVerified)
    val generalChatVisible = navigationState.current == EvidriloDestination.HOME &&
        !proComparisonVisible &&
        !onboardingPresentation.isVisible &&
        !accountGateVisible &&
        !revenueCatPaywallVisible &&
        !customerCenterVisible &&
        premiumState is PremiumPracticeState.Hidden
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
    EvidriloWorkspaceShell(
        destination = navigationState.current,
        showNavigation = accountRestoreComplete && !onboardingPresentation.isVisible && !accountGateVisible && !practiceLessonOpen && !proComparisonVisible,
        onSelect = { destination ->
            if (requireDestinationAccess(destination)) {
                releaseActiveProjectAiPreview()
                navigationState = navigationState.resetToHome().open(destination)
            }
        },
    ) {
    if (!accountRestoreComplete) {
        EvidriloLoadingScreen(mode = EvidriloLoadingMode.APP_BOOTSTRAP)
    } else if (onboardingPresentation.isVisible) {
        EvidriloOnboardingScreen(
            tourState = onboardingTour,
            language=languageController.language,
            languageSaveFailed=languageController.saveFailed,
            onSetLanguage=languageController::select,
            onConfirmLanguage={languageController.select(languageController.language);!languageController.saveFailed},
            onStartPractice={advanceGetStartedTour();navigationState=navigationState.resetToHome().open(EvidriloDestination.PRACTICE)},
            onNext = ::advanceGetStartedTour,
            onBack = { dispatchGetStartedTourEvent(GetStartedTourEvent.Back) },
            onSkip = ::skipGetStartedTour,
            onStartProject = {
                advanceGetStartedTour()
                navigationState = navigationState.resetToHome().open(EvidriloDestination.PROJECT_CATALOG)
            },
            storageNotice = onboardingStorageStatus.notice(),
        )
    } else if (proComparisonVisible) {
        EvidriloProComparisonScreen(
            signedIn = accountSession is AccountSession.SignedIn,
            plansAvailable = REVENUECAT_PRO_FEATURE_ENABLED,
            onChoosePlan = { productId ->
                preferredProProductId = productId
                proComparisonVisible = false
                openPremium()
            },
            onClose = { proComparisonVisible = false },
        )
    } else if (accountGateVisible) {
        EvidriloAccountRequiredGate(
            accountConfigured = accountConfiguration.isConfigured,
            revenueCatProEnabled = REVENUECAT_PRO_FEATURE_ENABLED,
            onSignIn = { openAccountGate(navigationState.current) },
            onOpenLocalProjects = {
                navigationState = navigationState.resetToHome().open(EvidriloDestination.PROJECTS)
            },
            onOpenSupport = { navigationState = navigationState.open(EvidriloDestination.SUPPORT) },
            storageNotice = onboardingStorageStatus.notice(),
        )
    } else if (revenueCatPaywallVisible) {
        EvidriloBackGesture("Close purchase options", closeManagedBillingUi)
        RevenueCatManagedPaywall(onDismiss = closeManagedBillingUi)
    } else if (customerCenterVisible) {
        EvidriloBackGesture("Close subscription management", closeManagedBillingUi)
        RevenueCatCustomerCenter(onDismiss = closeManagedBillingUi)
    } else if (premiumState !is PremiumPracticeState.Hidden) {
        EvidriloPremiumSurface(
            state = premiumState,
            isBusy = premiumBusy,
            managedPaywallAvailable = revenueCatUiAvailability.canPresent,
            onOpenManagedPaywall = openManagedPaywall,
            onPurchase = {
                val current = premiumState
                if (canUseRevenueCatForCurrentAccount() && !premiumBusy && current is PremiumPracticeState.Locked && current.billing.canPurchase) {
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
                if (canUseRevenueCatForCurrentAccount() && !premiumBusy && current is PremiumPracticeState.Locked) {
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
            onChooseAnotherPlan = {
                leavePremium()
                openProComparison()
            },
            onSelectOffer = {
                preferredProProductId = it
                dispatchPremium(PremiumPracticeEvent.SelectOffer(it))
            },
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
            onStartBlankProject = ::startManualStudentProject,
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
            onStartStarterProject = ::startStarterStudentProject,
            onStartBlankProject = ::startManualStudentProject,
            onBack = { navigationState = navigationState.back() },
            onNavigate = openTargetSection,
        )
    } else if (navigationState.current == EvidriloDestination.PROJECT_TEMPLATE_DETAIL) {
        EvidriloProjectTemplateDetailScreen(
            templateSummary = selectedProjectTemplateSummary,
            state = projectTemplateDetailState,
            notice = studentProjectNotice,
            projectAiAccountKey = currentBillingAccountId(),
            aiCreditBalance = aiCreditBalancePresentation,
            projectAiState = projectAiScaffoldState,
            projectAiConsentState = projectAiConsentState,
            onRetry = { projectTemplateDetailReload += 1 },
            onRefreshAiCreditBalance = ::refreshAiCreditBalance,
            onRefreshProjectAiConsent = ::refreshProjectAiConsent,
            onGrantProjectAiConsent = ::grantProjectAiConsent,
            onRevokeProjectAiConsent = ::revokeProjectAiConsent,
            onStartProject = ::startStudentProject,
            onStartBlankProject = ::startManualStudentProject,
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
            onResume = { project -> resumeStudentProject(project.id, practiceProjectSectionIntent) },
            onExportProject = { project -> projectExportIntentId=project.id; resumeStudentProject(project.id,studentProjectEditorSections(project).last().navigationId) },
            onCreateManualProject = { navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG) },
            onMarkCompleted = ::completeStudentProject,
            onArchive = ::archiveStudentProject,
            onMoveToTrash = ::trashStudentProject,
            onRestore = ::restoreStudentProject,
            onPermanentlyDelete = ::permanentlyDeleteStudentProject,
            onImportProject = ::importStudentProject,
            onRestoreArchiveRevision = ::restoreStudentProjectFromArchive,
            activeLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(projectProEntitlementActive.value),
            onOpenPremium = openProComparison,
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
                onResume = { project -> resumeStudentProject(project.id, practiceProjectSectionIntent) },
            onExportProject = { project -> projectExportIntentId=project.id; resumeStudentProject(project.id,studentProjectEditorSections(project).last().navigationId) },
                onCreateManualProject = { navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG) },
                onMarkCompleted = ::completeStudentProject,
                onArchive = ::archiveStudentProject,
                onMoveToTrash = ::trashStudentProject,
                onRestore = ::restoreStudentProject,
                onPermanentlyDelete = ::permanentlyDeleteStudentProject,
                onImportProject = ::importStudentProject,
                onRestoreArchiveRevision = ::restoreStudentProjectFromArchive,
                activeLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(projectProEntitlementActive.value),
                onOpenPremium = openProComparison,
                onBack = { navigationState = navigationState.back() },
                onNavigate = openTargetSection,
            )
        } else {
            EvidriloStudentProjectEditorScreen(
                draft = draft,
                attachmentStore = studentProjectAttachmentStore,
                projectAiAccountKey = currentBillingAccountId(),
                aiCreditBalance = aiCreditBalancePresentation,
                projectAiState = projectAiScaffoldState,
                projectAiConsentState = projectAiConsentState,
                notice = studentProjectNotice,
                isDirty = studentProjectEditorIsDirty,
                showExitConfirmation = studentProjectExitConfirmation,
                saveError = studentProjectSaveError,
                onDirtyChanged = { studentProjectEditorIsDirty = it },
                onSave = { title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions, deadlineDate ->
                    saveStudentProject(title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions, deadlineDate)
                },
                onAutosave = { title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions, deadlineDate ->
                    saveStudentProject(title, values, sources, themes, claimLinks, evidence, findings, relations, claims, limitationActions, deadlineDate, checkpoint = false)
                },
                onAddAttachment = ::addStudentProjectAttachment,
                onRemoveAttachment = ::removeStudentProjectAttachment,
                onRestoreRevision = ::restoreStudentProjectRevision,
                onRefreshAiCreditBalance = ::refreshAiCreditBalance,
                onRefreshProjectAiConsent = ::refreshProjectAiConsent,
                onGrantProjectAiConsent = ::grantProjectAiConsent,
                onRevokeProjectAiConsent = ::revokeProjectAiConsent,
                onRequestProjectAi = { projectId, brief, question, fields, revision, projectDataConsent ->
                    draft.templateSnapshot
                        ?.takeIf { it.publication == dev.nextgen.mobile.domain.project.ProjectTemplatePublication.PUBLISHED }
                        ?.let { template ->
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
                projectAiStageAssistState = projectAiStageAssistState,
                projectAiAccountAvailable = projectAiStageAssistAccountAvailable,
                projectAiAccountMessage = projectAiStageAssistAccountMessage,
                onRequestProjectAiStageAssist = ::requestProjectAiStageAssist,
                onApplyProjectAiStageAssist = ::applyProjectAiStageAssist,
                onDismissProjectAiStageAssist = ::dismissProjectAiStageAssist,
                onRetryProjectAiStageAssistSettlement = ::retryProjectAiStageAssistSettlement,
                projectAiActivityHistoryState = projectAiActivityHistoryState,
                onRefreshProjectAiActivity = ::refreshProjectAiActivity,
                onLoadMoreProjectAiActivity = ::loadMoreProjectAiActivity,
                onRetryUnknownProjectAiStageAssist = ::retryProjectAiStageAssistRequest,
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
                exportRequested = projectExportIntentId == draft.id,
                onConsumeExportRequest = { projectExportIntentId=null },
                onAskAiOpinion = { saved ->
                    val excerpt=projectOpinionIntent(saved)
                    projectOpinionComposeIntent=excerpt.copy(prompt=excerpt.prompt.take(3920)+"\nReply in ${languageController.language.nativeName}.")
                },
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
        val profileSignedIn = shouldShowSignedInAccountInProfile(
            session = accountSession,
            accountAuthRestoreComplete = accountAuthRestoreComplete,
        )
        EvidriloTargetProfileScreen(
            signedIn = profileSignedIn,
            profileName = if (profileSignedIn) (accountSession as? AccountSession.SignedIn)?.account?.email?.takeIf { it.isNotBlank() } ?: uiText("Your account") else uiText("Local student"),
            projects = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects.orEmpty(),
            projectsLoading = studentProjectListState is StudentProjectListUiState.Loading,
            projectsError = studentProjectListState.toHomeErrorMessage(),
            hasVerifiedPro = canUseRevenueCatForCurrentAccount() && projectProEntitlementActive.value,
            onRetryProjects = { studentProjectListReload += 1 },
            onResumeProject = { project -> resumeStudentProject(project.id) },
            onOpenPractice = { profilePracticeLesson = null; startTargetPractice() },
            onOpenPracticeLesson = { lesson -> profilePracticeLesson = lesson; startTargetPractice() },
            profileSubtitle = if (!accountAuthRestoreComplete) {
                "Checking account…"
            } else {
                accountSession.toSettingsSubtitle()
            },
            history = historySnapshot,
            onBack = { navigationState = navigationState.back() },
            onNavigate = openTargetSection,
            onOpenPremium = openProComparison,
            onOpenHistory = {
                if (requireDestinationAccess(EvidriloDestination.HISTORY)) {
                    navigationState = navigationState.open(EvidriloDestination.HISTORY)
                }
            },
            onOpenWorkspacePreferences = {
                navigationState = navigationState.open(EvidriloDestination.SETTINGS)
            },
            onOpenNotifications = {
                navigationState = navigationState.open(EvidriloDestination.NOTIFICATIONS)
            },
            onOpenPrivacyData = {
                navigationState = navigationState.open(EvidriloDestination.PRIVACY_DATA)
            },
            onOpenAccount = {
                navigationState = navigationState.open(EvidriloDestination.ACCOUNT)
            },
            onOpenLocalProjects = { navigationState = navigationState.open(EvidriloDestination.PROJECTS) },
            onOpenSupport = { navigationState = navigationState.open(EvidriloDestination.SUPPORT) },
        )
    } else if (navigationState.current == EvidriloDestination.ACCOUNT) {
        EvidriloAccountScreen(
            session = accountSession,
            isBusy = accountBusy,
            accountRestoreComplete = accountAuthRestoreComplete,
            accountConfigured = accountConfiguration.isConfigured,
            googleConfigured = accountConfiguration.isProviderConfigured(AccountOAuthProvider.GOOGLE),
            appleConfigured = accountConfiguration.isProviderConfigured(AccountOAuthProvider.APPLE),
            accountBoundFeaturesEnabled = accountBoundFeaturesEnabled,
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
            onAppleSignIn = {
                performAccountOperation(showSigningInState = true) {
                    accountGateway.startAppleSignIn()
                }
            },
            onGoogleLink = {
                performAccountOperation(false) { accountGateway.startGoogleIdentityLink() }
            },
            onCancelGoogleLink = {
                performAccountOperation(false) { accountGateway.cancelGoogleIdentityLink() }
            },
            onAppleLink = {
                performAccountOperation(false) { accountGateway.startAppleIdentityLink() }
            },
            onCancelAppleLink = {
                performAccountOperation(false) { accountGateway.cancelAppleIdentityLink() }
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
            onOpenPractice = startTargetPractice,
            onReplayOnboarding = ::requestGetStartedTour,
            audioState = audioState,
            onListen = { playNarration(AudioNarrationId.GUIDE, AudioNarrationCopy.guide()) },
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
        )
    } else if (navigationState.current in setOf(
            EvidriloDestination.SETTINGS,
            EvidriloDestination.NOTIFICATIONS,
            EvidriloDestination.WORKSPACE_PREFERENCES,
            EvidriloDestination.PRIVACY_DATA,
        )
    ) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloSettingsScreen(
            section = when (navigationState.current) {
                EvidriloDestination.NOTIFICATIONS -> EvidriloSettingsSection.NOTIFICATIONS
                EvidriloDestination.WORKSPACE_PREFERENCES -> EvidriloSettingsSection.WORKSPACE_PREFERENCES
                EvidriloDestination.PRIVACY_DATA -> EvidriloSettingsSection.PRIVACY_DATA
                else -> EvidriloSettingsSection.HUB
            },
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
            onOpenPremium = openProComparison,
            onOpenGuide = { navigationState = navigationState.open(EvidriloDestination.GUIDE) },
            onOpenGuidedCase = startTargetPractice,
            onOpenHistory = { navigationState = navigationState.open(EvidriloDestination.HISTORY) },
            onOpenAccount = { navigationState = navigationState.open(EvidriloDestination.ACCOUNT) },
            onOpenSupport = { navigationState = navigationState.open(EvidriloDestination.SUPPORT) },
            accountSubtitle = accountSession.toSettingsSubtitle(),
            onOpenAbout = { navigationState = navigationState.open(EvidriloDestination.ABOUT) },
            onResetPractice = { dispatch(ConclusionEvent.Reset) },
            onClearHistory = clearHistory,
            onBack = { navigationState = navigationState.back() },
            backLabel = when (previousDestination) {
                EvidriloDestination.SETTINGS -> "Settings"
                EvidriloDestination.PROFILE -> "Profile"
                else -> "Home"
            },
            audioSettings = audioSettings,
            audioStorageStatus = audioStorageStatus,
            onSetAudioSettings = setAudioSettings,
            language = languageController.language,
            languageSaveFailed = languageController.saveFailed,
            onSetLanguage = languageController::select,
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
            onOpenNotifications = {
                navigationState = navigationState.open(EvidriloDestination.NOTIFICATIONS)
            },
            onOpenWorkspacePreferences = {
                navigationState = navigationState.open(EvidriloDestination.WORKSPACE_PREFERENCES)
            },
            onOpenPrivacyData = {
                navigationState = navigationState.open(EvidriloDestination.PRIVACY_DATA)
            },
        )
    } else if (navigationState.current == EvidriloDestination.SUPPORT) {
        val previousDestination = navigationState.stack.dropLast(1).lastOrNull()
        EvidriloSupportScreen(
            onBack = { navigationState = navigationState.back() },
            onOpenPremium = openProComparison,
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
    } else if (navigationState.current == EvidriloDestination.PRACTICE) {
        EvidriloPracticeCourseScreen(
            initialSelectedLesson = profilePracticeLesson,
            onConsumeInitialLesson = { profilePracticeLesson = null },
            hasVerifiedProAccess = canUseRevenueCatForCurrentAccount() && projectProEntitlementActive.value,
            proAccessChecking = practiceAccessChecking && canUseRevenueCatForCurrentAccount(),
            onOpenPro = openProComparison,
            onLessonVisibilityChanged = { practiceLessonOpen = it },
            tabletState = state,
            onTabletEvent = { event -> dispatch(event) },
            tabletCase = baseCase,
            onExit = returnToHome,
            onOpenProjects = {
                releaseActiveProjectAiPreview()
                navigationState = navigationState.open(EvidriloDestination.PROJECTS)
            },
            onCarrySkillToProjects = { lesson ->
                releaseActiveProjectAiPreview()
                practiceProjectSectionIntent = when (lesson) {
                    dev.nextgen.mobile.domain.practice.PracticeLessonId.TABLET -> "claims-and-evidence-links"
                    dev.nextgen.mobile.domain.practice.PracticeLessonId.STUDIES -> "findings-and-synthesis"
                    dev.nextgen.mobile.domain.practice.PracticeLessonId.SURVEY -> "limitations-and-next-steps"
                }
                studentProjectNotice = "Choose a project to use this move with your own material. Practice answers remain separate."
                navigationState = navigationState.open(EvidriloDestination.PROJECTS)
            },
            onTabletContextChanged = { practiceTabletContext = it },
            onOpenTabletHistory = {
                navigationState = navigationState.resetToHome().open(EvidriloDestination.HISTORY)
            },
            onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
            tabletAudioControls = {
                EvidriloAudioListenControl(audioState, {
                    playNarration(AudioNarrationId.CASE_FACT, AudioNarrationCopy.case(case))
                }, pauseOrResumeAudio, stopAudio)
            },
        )
    } else if (navigationState.current == EvidriloDestination.CASES) {
        EvidriloCasesScreen(case = case, hasPro = canUseRevenueCatForCurrentAccount() && projectProEntitlementActive.value,
            accessChecking = practiceAccessChecking,
            onOpenCase = { releaseActiveProjectAiPreview(); navigationState = navigationState.open(EvidriloDestination.SOURCES) }, onOpenProCases = openPremium,
            onBack = { navigationState = navigationState.back() })
    } else if (navigationState.current == EvidriloDestination.HOME) {
        EvidriloTargetHomeScreen(
            storageNotice = storageNotice,
            onNavigate = openTargetSection,
            onOpenProjectCatalog = {
                navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG)
            },
            onSelectProjectFamily = { family ->
                selectedProjectTemplateFamily = family
                navigationState = navigationState.open(EvidriloDestination.PROJECT_FAMILY_DETAIL)
            },
            projects = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects.orEmpty(),
            projectsLoading = studentProjectListState is StudentProjectListUiState.Loading,
            projectsLoadError = studentProjectListState.toHomeErrorMessage(),
            activeProjectLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(
                projectProEntitlementActive.value,
            ),
            onRetryProjects = { studentProjectListReload += 1 },
            onOpenProjects = { navigationState = navigationState.open(EvidriloDestination.PROJECTS) },
            onCreateProject = ::beginManualProjectFromHome,
            onResumeProject = { project -> resumeStudentProject(project.id) },
            onOpenSettings = { openTargetSection(EvidriloTargetSection.PROFILE) },
            onOpenPractice = startTargetPractice,
            onOpenCases = { navigationState = navigationState.open(EvidriloDestination.CASES) },
            recommendation = recommendationState,
            onAcceptRecommendation = ::acceptRecommendation,
            onDismissRecommendation = ::dismissRecommendation,
            onRetryRecommendation = ::retryRecommendation,
        )
    } else {
        EvidriloLegacyPracticeSurface(
            state = state,
            case = case,
            onEvent = { dispatch(it) },
            onBack = returnToHome,
            audioState = audioState,
            onNarration = playNarration,
            onPauseOrResumeAudio = pauseOrResumeAudio,
            onStopAudio = stopAudio,
            onSelectionSound = { playEffect(AudioEffectId.SELECTION) },
            onNavigate = openTargetSection,
            onOpenHistory = {
                dispatch(ConclusionEvent.Reset)
                navigationState = navigationState.resetToHome().open(EvidriloDestination.HISTORY)
            },
            onStartChallenge = {
                dispatch(ConclusionEvent.BeginEvidenceChange)
                navigationState = navigationState.resetToHome().open(EvidriloDestination.PRACTICE)
            },
            introContent = {
                EvidriloTargetHomeScreen(
                    storageNotice = storageNotice,
                    onNavigate = openTargetSection,
                    onOpenProjectCatalog = {
                        navigationState = navigationState.open(EvidriloDestination.PROJECT_CATALOG)
                    },
                    onSelectProjectFamily = { family ->
                        selectedProjectTemplateFamily = family
                        navigationState = navigationState.open(EvidriloDestination.PROJECT_FAMILY_DETAIL)
                    },
                    projects = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects.orEmpty(),
                    projectsLoading = studentProjectListState is StudentProjectListUiState.Loading,
                    projectsLoadError = studentProjectListState.toHomeErrorMessage(),
                    activeProjectLimit = dev.nextgen.mobile.domain.project.StudentProjectDraftRules.activeProjectLimit(
                        projectProEntitlementActive.value,
                    ),
                    onRetryProjects = { studentProjectListReload += 1 },
                    onOpenProjects = { navigationState = navigationState.open(EvidriloDestination.PROJECTS) },
                    onCreateProject = ::beginManualProjectFromHome,
                    onResumeProject = { project -> resumeStudentProject(project.id) },
                    onOpenSettings = { openTargetSection(EvidriloTargetSection.PROFILE) },
            onOpenPractice = startTargetPractice,
                    onOpenCases = { navigationState = navigationState.open(EvidriloDestination.CASES) },
                    recommendation = recommendationState,
                    onAcceptRecommendation = ::acceptRecommendation,
                    onDismissRecommendation = ::dismissRecommendation,
                    onRetryRecommendation = ::retryRecommendation,
                )
            },
        )
    }

    } // Workspace shell; overlays retain their original full-screen host.

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

    EvidriloGeneralAiChat(
        opinionIntent = projectOpinionComposeIntent,
        onConsumeOpinionIntent = { projectOpinionComposeIntent=null },
        isOpinionCurrent = { id,revision ->
            val result=studentProjectDraftFlow.resume(id)
            result is StudentProjectDraftFlowResult.Value && result.value.revision==revision
        },
        editorMode = navigationState.current == EvidriloDestination.PROJECT_EDITOR,
        visible = generalChatVisible,
        accountKey = currentBillingAccountId(),
        projects = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects.orEmpty()
            .mapNotNull { project ->
                projectAiEntryProjectOption(
                    projectId = project.id,
                    title = project.title,
                    isActive = project.status == StudentProjectStatus.DRAFT || project.status == StudentProjectStatus.ACTIVE,
                    templatePublished = project.templateSnapshot?.publication ==
                        dev.nextgen.mobile.domain.project.ProjectTemplatePublication.PUBLISHED,
                    hasDeclaredAiOperation = project.templateSnapshot?.steps?.any { it.aiOperations.isNotEmpty() } == true,
                )
            },
        projectsLoading = studentProjectListState is StudentProjectListUiState.Loading,
        accessMessage = generalChatAccessMessage,
        apiConfigured = dev.nextgen.mobile.ai.AiClientConfiguration(accountConfiguration.normalizedApiBaseUrl).isConfigured,
        aiCreditBalance = aiCreditBalancePresentation,
        consentState = projectAiConsentState,
        canOpenAccount = generalChatCanOpenAccount,
        onOpenAccount = { navigationState = navigationState.open(EvidriloDestination.ACCOUNT) },
        onRefreshConsent = ::refreshProjectAiConsent,
        onRefreshAiCreditBalance = ::refreshAiCreditBalance,
        onGrantConsent = ::grantProjectAiConsent,
        onRevokeConsent = ::revokeProjectAiConsent,
        onOpenProjects = { navigationState = navigationState.open(EvidriloDestination.PROJECTS) },
        onOpenProject = { projectId ->
            val project = (studentProjectListState as? StudentProjectListUiState.Loaded)?.projects?.firstOrNull { it.id == projectId }
            val section = project?.templateSnapshot?.steps?.firstOrNull { it.aiOperations.isNotEmpty() }?.id?.let { "template-step:$it" }
            resumeStudentProject(projectId, section)
        },
        activityHistoryState = projectAiActivityHistoryState,
        onRefreshActivityHistory = ::refreshProjectAiActivity,
        onLoadMoreActivityHistory = ::loadMoreProjectAiActivity,
        onSendMessage = ::requestGeneralChatMessage,
    )

    }
}

@Composable
private fun EvidriloLegacyPracticeSurface(
    state: ConclusionState,
    case: ConclusionCase,
    onEvent: (ConclusionEvent) -> Unit,
    onBack: () -> Unit,
    audioState: AudioPlaybackState,
    onNarration: (AudioNarrationId, String) -> Unit,
    onPauseOrResumeAudio: () -> Unit,
    onStopAudio: () -> Unit,
    onSelectionSound: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenHistory: () -> Unit,
    onStartChallenge: () -> Unit,
    introContent: @Composable () -> Unit,
) {
    when (val current = state) {
        ConclusionState.Intro -> introContent()

        is ConclusionState.Drafting -> EvidriloDraftScreen(
            case = case,
            title = "Build a bounded conclusion",
            draft = current.draft,
            validationMessage = current.validationMessage,
            onDraftChange = { onEvent(ConclusionEvent.UpdateDraft(it)) },
            onSubmit = { onEvent(ConclusionEvent.Submit) },
            onReset = { onEvent(ConclusionEvent.Reset) },
            onBack = onBack,
            audioState = audioState,
            onListen = { onNarration(AudioNarrationId.CASE_FACT, AudioNarrationCopy.case(case)) },
            onPauseOrResumeAudio = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
            onSelectionSound = onSelectionSound,
        )

        is ConclusionState.Incomplete -> EvidriloDraftScreen(
            case = case,
            title = "Complete the conclusion",
            draft = current.draft,
            validationMessage = current.feedback.message,
            onDraftChange = { onEvent(ConclusionEvent.UpdateDraft(it)) },
            onSubmit = { onEvent(ConclusionEvent.Submit) },
            onReset = { onEvent(ConclusionEvent.Reset) },
            onBack = onBack,
            initialStep = current.feedback.field.toDraftStep(),
            audioState = audioState,
            onListen = { onNarration(AudioNarrationId.CASE_FACT, AudioNarrationCopy.case(case)) },
            onPauseOrResumeAudio = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
            onSelectionSound = onSelectionSound,
        )

        is ConclusionState.Feedback -> EvidriloFeedbackScreen(
            case = case,
            draft = current.draft,
            evaluation = current.evaluation,
            onRevise = { onEvent(ConclusionEvent.BeginRevision) },
            onReset = { onEvent(ConclusionEvent.Reset) },
            onBack = onBack,
            audioState = audioState,
            onListen = { onNarration(AudioNarrationId.FEEDBACK, AudioNarrationCopy.feedback()) },
            onPauseOrResumeAudio = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )

        is ConclusionState.Revision -> EvidriloDraftScreen(
            case = case,
            title = "Revise once with the feedback",
            draft = current.draft,
            validationMessage = current.validationMessage,
            onDraftChange = { onEvent(ConclusionEvent.UpdateDraft(it)) },
            onSubmit = { onEvent(ConclusionEvent.Submit) },
            onReset = { onEvent(ConclusionEvent.Reset) },
            onBack = onBack,
            initialStep = initialDraftStepFor(current),
            audioState = audioState,
            onListen = { onNarration(AudioNarrationId.REVISION, AudioNarrationCopy.revision()) },
            onPauseOrResumeAudio = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
            onSelectionSound = onSelectionSound,
        )

        is ConclusionState.Summary -> EvidriloTargetEvidenceDeltaScreen(
            case = case,
            before = current.initialDraft,
            after = current.revisedDraft,
            evaluation = current.finalEvaluation,
            onNavigate = onNavigate,
            onBack = onBack,
            onOpenHistory = onOpenHistory,
            onStartChallenge = onStartChallenge,
        )

        is ConclusionState.EvidenceChangeDrafting -> EvidriloDraftScreen(
            case = ConclusionCases.EVIDENCE_CHANGE,
            title = "Rebuild the conclusion after the evidence change",
            draft = current.draft,
            validationMessage = current.validationMessage,
            onDraftChange = { onEvent(ConclusionEvent.UpdateEvidenceChangeDraft(it)) },
            onSubmit = { onEvent(ConclusionEvent.SubmitEvidenceChange) },
            onReset = { onEvent(ConclusionEvent.Reset) },
            onBack = onBack,
            initialStep = initialDraftStepFor(current),
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
            onFinish = { onEvent(ConclusionEvent.FinishEvidenceChange) },
            onReset = { onEvent(ConclusionEvent.Reset) },
            onBack = onBack,
            audioState = audioState,
            onListen = { onNarration(AudioNarrationId.CHALLENGE, AudioNarrationCopy.challenge()) },
            onPauseOrResumeAudio = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )

        is ConclusionState.EvidenceChangeSummary -> EvidriloEvidenceChangeSummaryScreen(
            baseDraft = current.baseDraft,
            challengeDraft = current.challengeDraft,
            challengeEvaluation = current.challengeEvaluation,
            onReset = { onEvent(ConclusionEvent.Reset) },
            onBack = onBack,
            audioState = audioState,
            onListen = { onNarration(AudioNarrationId.CHALLENGE, AudioNarrationCopy.challenge()) },
            onPauseOrResumeAudio = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
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
    onChooseAnotherPlan: () -> Unit,
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
                        onChooseAnotherPlan = onChooseAnotherPlan,
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
    onChooseAnotherPlan: () -> Unit,
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
        onChooseAnotherPlan = onChooseAnotherPlan,
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
        EvidriloBackGesture(label = backLabel, onClick = onBack)
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
        EvidriloBackGesture("Free workflow", onBack)
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
        EvidriloBackGesture(label = "Packs", onClick = onBack)
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
        EvidriloPrimaryButton(label = "Explore cases", onClick = onBack)
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
    EvidriloPracticeDraftScreen(
        case = case,
        title = title,
        draft = draft,
        validationMessage = validationMessage,
        onDraftChange = onDraftChange,
        onSubmit = onSubmit,
        onReset = onReset,
        onBack = onBack,
        initialStep = initialStep,
        onSelectionSound = onSelectionSound,
        audioControls = {
            EvidriloAudioListenControl(audioState, onListen, onPauseOrResumeAudio, onStopAudio)
        },
    )
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
    EvidriloPracticeFeedbackScreen(
        case = case,
        draft = draft,
        evaluation = evaluation,
        onRevise = onRevise,
        onReset = onReset,
        onBack = onBack,
        audioControls = {
            EvidriloAudioListenControl(audioState, onListen, onPauseOrResumeAudio, onStopAudio)
        },
    )
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
        onBack?.let { back ->
            EvidriloBackGesture(label = "Home", onClick = back)
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
    EvidriloPracticeChangedFeedbackScreen(
        baseDraft = baseDraft,
        draft = draft,
        evaluation = evaluation,
        onFinish = onFinish,
        onReset = onReset,
        onBack = onBack,
        audioControls = {
            EvidriloAudioListenControl(audioState, onListen, onPauseOrResumeAudio, onStopAudio)
        },
    )
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
    EvidriloPracticeChangedSummaryScreen(
        baseDraft = baseDraft,
        challengeDraft = challengeDraft,
        challengeEvaluation = challengeEvaluation,
        onReset = onReset,
        onBack = onBack,
        audioControls = {
            EvidriloAudioListenControl(audioState, onListen, onPauseOrResumeAudio, onStopAudio)
        },
    )
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
    val choiceColors = evidriloChoiceColors(selected)
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
            containerColor = choiceColors.container,
            contentColor = choiceColors.content,
        ),
        border = BorderStroke(width = 2.dp, color = choiceColors.border),
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
        if(label in setOf("Claim","Limitation note","Action reason")) RawText(value,style=MaterialTheme.typography.bodyMedium)
        else Text(value, style = MaterialTheme.typography.bodyMedium)
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
