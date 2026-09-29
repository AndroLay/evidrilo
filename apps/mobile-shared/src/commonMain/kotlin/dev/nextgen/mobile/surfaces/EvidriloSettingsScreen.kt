package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import dev.nextgen.mobile.EvidriloColors
import dev.nextgen.mobile.EvidriloPrimaryButton
import dev.nextgen.mobile.EvidriloSecondaryButton
import dev.nextgen.mobile.EvidriloThemeMode
import dev.nextgen.mobile.evidriloNextThemeMode
import dev.nextgen.mobile.evidriloThemeModeLabel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED
import dev.nextgen.mobile.audio.AudioSettings
import dev.nextgen.mobile.notifications.NotificationPermissionState
import dev.nextgen.mobile.notifications.NotificationPreferences
import dev.nextgen.mobile.sync.SyncConsent
import dev.nextgen.mobile.sync.syncPresentation
import dev.nextgen.mobile.billing.REVENUECAT_PRO_FEATURE_ENABLED
import dev.nextgen.mobile.storage.LocalStorageNotice
import dev.nextgen.mobile.storage.LocalStorageStatus
import dev.nextgen.mobile.surfaces.cloudSyncDisclosure
import dev.nextgen.mobile.surfaces.disclosureStateDescription

internal enum class EvidriloSettingsSection {
    HUB,
    NOTIFICATIONS,
    WORKSPACE_PREFERENCES,
    PRIVACY_DATA,
}

@Composable
internal fun EvidriloSettingsScreen(
    section: EvidriloSettingsSection = EvidriloSettingsSection.HUB,
    historyAvailable: Boolean,
    storageNotice: LocalStorageNotice?,
    analyticsConsent: AnalyticsConsent,
    onSetAnalyticsConsent: (AnalyticsConsent) -> Unit,
    syncConsent: SyncConsent,
    syncSignedIn: Boolean,
    syncPendingCount: Int,
    syncStorageAvailable: Boolean,
    syncBusy: Boolean,
    syncStatusMessage: String?,
    onSetSyncConsent: (SyncConsent) -> Unit,
    onSyncNow: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenGuidedCase: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSupport: () -> Unit,
    accountSubtitle: String,
    onOpenAbout: () -> Unit,
    onResetPractice: () -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    backLabel: String = "Home",
    audioSettings: AudioSettings = AudioSettings(),
    audioStorageStatus: LocalStorageStatus? = null,
    onSetAudioSettings: (AudioSettings) -> Unit = {},
    themeMode: EvidriloThemeMode = EvidriloThemeMode.SYSTEM,
    onSetThemeMode: (EvidriloThemeMode) -> Unit = {},
    notificationPreferences: NotificationPreferences = NotificationPreferences(),
    notificationPermission: NotificationPermissionState = NotificationPermissionState.UNKNOWN,
    notificationScheduleLabel: String? = null,
    notificationStatusMessage: String? = null,
    notificationBusy: Boolean = false,
    onEnableNotifications: () -> Unit = {},
    onDisableNotifications: () -> Unit = {},
    onSetNotificationPreferences: (NotificationPreferences) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenWorkspacePreferences: () -> Unit = {},
    onOpenPrivacyData: () -> Unit = {},
) {
    var pendingAction by remember { mutableStateOf<SettingsDestructiveAction?>(null) }
    var readingExpanded by remember { mutableStateOf(false) }
    var localDataExpanded by remember { mutableStateOf(false) }
    var analyticsExpanded by remember { mutableStateOf(false) }
    var syncExpanded by remember { mutableStateOf(false) }
    var audioExpanded by remember { mutableStateOf(false) }
    var notificationExpanded by remember { mutableStateOf(false) }
    val sync = syncPresentation(
        consent = syncConsent,
        signedIn = syncSignedIn,
        pendingCount = syncPendingCount,
        storageAvailable = syncStorageAvailable,
    )

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = null
                        when (action) {
                            SettingsDestructiveAction.RESET_PRACTICE -> onResetPractice()
                            SettingsDestructiveAction.CLEAR_HISTORY -> onClearHistory()
                        }
                    },
                ) {
                    Text(action.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Keep data")
                }
            },
        )
    }

    EvidriloContentColumn {
        EvidriloBackButton(label = backLabel, onClick = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when (section) {
                    EvidriloSettingsSection.HUB -> "Settings"
                    EvidriloSettingsSection.NOTIFICATIONS -> "Notifications"
                    EvidriloSettingsSection.WORKSPACE_PREFERENCES -> "Workspace preferences"
                    EvidriloSettingsSection.PRIVACY_DATA -> "Privacy & data"
                },
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                when (section) {
                    EvidriloSettingsSection.HUB -> "Choose what you want to manage."
                    EvidriloSettingsSection.NOTIFICATIONS -> "Manage reminders on this device."
                    EvidriloSettingsSection.WORKSPACE_PREFERENCES -> "Adjust how your workspace looks and sounds."
                    EvidriloSettingsSection.PRIVACY_DATA -> "Review what stays local and control optional data use."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        storageNotice?.let { notice ->
            EvidriloRecoveryNotice(notice = notice)
        }

        if (section == EvidriloSettingsSection.HUB) {
            EvidriloSectionHeading("Preferences")
            EvidriloSettingsGroup {
                EvidriloSettingsRow(
                    icon = EvidriloIconName.CHECKLIST,
                    title = "Workspace preferences",
                    subtitle = "Theme, reading, and audio",
                    onClick = onOpenWorkspacePreferences,
                )
                EvidriloDivider()
                EvidriloSettingsRow(
                    icon = EvidriloIconName.BELL,
                    title = "Notifications",
                    subtitle = if (notificationPreferences.enabled) "On · local reminders" else "Off by default",
                    onClick = onOpenNotifications,
                )
                EvidriloDivider()
                EvidriloSettingsRow(
                    icon = EvidriloIconName.SHIELD,
                    title = "Privacy & data",
                    subtitle = "Local storage and optional services",
                    onClick = onOpenPrivacyData,
                )
            }

            EvidriloSectionHeading("Your Evidrilo")
            EvidriloSettingsGroup {
                EvidriloSettingsRow(
                    icon = EvidriloIconName.BOOK,
                    title = "How Evidrilo works",
                    subtitle = "Evidence · claims · limits · revision",
                    onClick = onOpenGuide,
                )
                EvidriloDivider()
                EvidriloSettingsRow(
                    icon = EvidriloIconName.LIGHTNING,
                    title = "Try a guided case",
                    subtitle = "A separate worked example",
                    onClick = onOpenGuidedCase,
                )
                if (historyAvailable) {
                    EvidriloDivider()
                    EvidriloSettingsRow(
                        icon = EvidriloIconName.HISTORY,
                        title = "Track what changed",
                        subtitle = "Review your latest local comparison",
                        onClick = onOpenHistory,
                    )
                }
                EvidriloDivider()
                EvidriloSettingsRow(
                    icon = EvidriloIconName.LAYERS,
                    title = "Evidrilo Pro",
                    subtitle = if (REVENUECAT_PRO_FEATURE_ENABLED) {
                        "Optional project capacity and evidence cases"
                    } else {
                        "Unavailable in this build"
                    },
                    onClick = onOpenPremium,
                )
            }

            EvidriloSectionHeading("Account & help")
            EvidriloSettingsGroup {
                EvidriloSettingsRow(
                    icon = EvidriloIconName.ACCOUNT,
                    title = "Account",
                    subtitle = accountSubtitle,
                    onClick = onOpenAccount,
                )
                EvidriloDivider()
                EvidriloSettingsRow(
                    icon = EvidriloIconName.QUESTION,
                    title = "Support and billing",
                    subtitle = "Restore · manage · refund · privacy",
                    onClick = onOpenSupport,
                )
                EvidriloDivider()
                EvidriloSettingsRow(
                    icon = EvidriloIconName.INFO,
                    title = "About Evidrilo",
                    subtitle = "Boundaries, privacy, and attribution",
                    onClick = onOpenAbout,
                )
            }
            EvidriloTintPanel {
                Text("Local-first by default", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (TEMPORARY_GUEST_MODE_ENABLED) {
                        "Free project work stays on this device. Pro requires sign-in and a confirmed RevenueCat entitlement. Server AI, cloud sync, and analytics transmission remain paused."
                    } else {
                        cloudSyncDisclosure() + " Local projects work without an account."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (section == EvidriloSettingsSection.WORKSPACE_PREFERENCES) {
        EvidriloSectionHeading("Appearance")
        EvidriloSettingsGroup {
            EvidriloSettingsRow(
                icon = EvidriloIconName.SPARK,
                title = "Theme",
                subtitle = "Currently: ${evidriloThemeModeLabel(themeMode)}",
                onClick = { onSetThemeMode(evidriloNextThemeMode(themeMode)) },
                stateDescription = evidriloThemeModeLabel(themeMode),
                trailingIcon = EvidriloIconName.CHEVRON_RIGHT,
            )
            EvidriloDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EvidriloThemeMode.entries.forEach { option ->
                    EvidriloThemeChoiceChip(
                        label = evidriloThemeModeLabel(option),
                        selected = themeMode == option,
                        onClick = { onSetThemeMode(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        EvidriloSectionHeading("Workflow")
        EvidriloSettingsGroup {
            EvidriloSettingsRow(
                icon = EvidriloIconName.CHECKLIST,
                title = "Reading and motion",
                subtitle = "Follows your device accessibility settings",
                onClick = { readingExpanded = !readingExpanded },
                selected = readingExpanded,
                stateDescription = disclosureStateDescription(readingExpanded),
                trailingIcon = if (readingExpanded) {
                    EvidriloIconName.CHEVRON_DOWN
                } else {
                    EvidriloIconName.CHEVRON_RIGHT
                },
            )
            if (readingExpanded) {
                EvidriloDivider()
                EvidriloTintPanel(modifier = Modifier.padding(12.dp)) {
                    Text("Reading and motion", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Evidrilo keeps system text-size and reduced-motion behavior in the platform layer. No in-app toggle is advertised until a separate setting is implemented and tested on each target.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            EvidriloDivider()
            EvidriloSettingsRow(
                icon = EvidriloIconName.BOOK,
                title = "Audio assistance",
                subtitle = when {
                    audioSettings.narrationEnabled && audioSettings.effectsEnabled -> "Narration and interaction sounds on"
                    audioSettings.narrationEnabled -> "Narration on · interaction sounds off"
                    audioSettings.effectsEnabled -> "Narration off · interaction sounds on"
                    else -> "Off · visible text remains available"
                },
                onClick = { audioExpanded = !audioExpanded },
                selected = audioExpanded,
                stateDescription = disclosureStateDescription(audioExpanded),
                trailingIcon = if (audioExpanded) {
                    EvidriloIconName.CHEVRON_DOWN
                } else {
                    EvidriloIconName.CHEVRON_RIGHT
                },
            )
            if (audioExpanded) {
                EvidriloDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Audio is optional. The visible text remains complete. Bundled narration is preferred when reviewed assets are available; otherwise Evidrilo uses an offline-capable platform voice and never falls back to the network.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EvidriloPrimaryButton(
                        label = if (audioSettings.narrationEnabled) "Turn narration off" else "Turn narration on",
                        onClick = {
                            onSetAudioSettings(
                                audioSettings.copy(narrationEnabled = !audioSettings.narrationEnabled),
                            )
                        },
                        trailingIcon = null,
                    )
                    EvidriloSecondaryButton(
                        label = if (audioSettings.effectsEnabled) {
                            "Turn interaction sounds off"
                        } else {
                            "Turn interaction sounds on"
                        },
                        onClick = {
                            onSetAudioSettings(
                                audioSettings.copy(effectsEnabled = !audioSettings.effectsEnabled),
                            )
                        },
                    )
                    if (audioStorageStatus in setOf(
                            LocalStorageStatus.UNAVAILABLE,
                            LocalStorageStatus.CORRUPT,
                            LocalStorageStatus.FAILED,
                        )
                    ) {
                        Text(
                            "Audio preference could not be saved on this device; it will remain active for this session.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        }

        if (section == EvidriloSettingsSection.NOTIFICATIONS) {
            EvidriloSectionHeading("Local reminders")
            EvidriloSettingsGroup {
            EvidriloSettingsRow(
                icon = EvidriloIconName.BELL,
                title = "Local reminders",
                subtitle = when {
                    notificationBusy -> "Updating reminder permission…"
                    notificationPreferences.enabled && notificationScheduleLabel != null ->
                        "On · $notificationScheduleLabel"
                    notificationPreferences.enabled -> "On · no eligible case yet"
                    else -> "Off by default"
                },
                onClick = { notificationExpanded = !notificationExpanded },
                selected = notificationExpanded,
                stateDescription = disclosureStateDescription(notificationExpanded),
                trailingIcon = if (notificationExpanded) {
                    EvidriloIconName.CHEVRON_DOWN
                } else {
                    EvidriloIconName.CHEVRON_RIGHT
                },
            )
            if (notificationExpanded) {
                EvidriloDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Local reminders are optional, off by default, and never promotional. Evidrilo schedules them on this device without sending draft text to a server.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EvidriloPrimaryButton(
                        label = when {
                            notificationBusy -> "Updating reminders…"
                            notificationPreferences.enabled -> "Turn reminders off"
                            else -> "Enable local reminders"
                        },
                        onClick = if (notificationPreferences.enabled) {
                            onDisableNotifications
                        } else {
                            onEnableNotifications
                        },
                        enabled = !notificationBusy,
                        trailingIcon = null,
                    )
                    EvidriloSecondaryButton(
                        label = if (notificationPreferences.continueUnfinishedEnabled) {
                            "Unfinished case reminders on"
                        } else {
                            "Unfinished case reminders off"
                        },
                        onClick = {
                            onSetNotificationPreferences(
                                notificationPreferences.copy(
                                    continueUnfinishedEnabled =
                                        !notificationPreferences.continueUnfinishedEnabled,
                                ),
                            )
                        },
                        enabled = notificationPreferences.enabled && !notificationBusy,
                    )
                    EvidriloSecondaryButton(
                        label = if (notificationPreferences.reviewCompletedEnabled) {
                            "Completed review reminders on"
                        } else {
                            "Completed review reminders off"
                        },
                        onClick = {
                            onSetNotificationPreferences(
                                notificationPreferences.copy(
                                    reviewCompletedEnabled =
                                        !notificationPreferences.reviewCompletedEnabled,
                                ),
                            )
                        },
                        enabled = notificationPreferences.enabled && !notificationBusy,
                    )
                    EvidriloSecondaryButton(
                        label = "Cadence · ${notificationPreferences.cadence.label}",
                        onClick = {
                            onSetNotificationPreferences(
                                notificationPreferences.copy(
                                    cadence = when (notificationPreferences.cadence) {
                                        dev.nextgen.mobile.notifications.NotificationCadence.DAILY ->
                                            dev.nextgen.mobile.notifications.NotificationCadence.WEEKLY
                                        dev.nextgen.mobile.notifications.NotificationCadence.WEEKLY ->
                                            dev.nextgen.mobile.notifications.NotificationCadence.DAILY
                                    },
                                ),
                            )
                        },
                        enabled = notificationPreferences.enabled && !notificationBusy,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSetNotificationPreferences(
                                    notificationPreferences.adjustTime(minutes = -15),
                                )
                            },
                            enabled = notificationPreferences.enabled && !notificationBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(2.dp, EvidriloColors.Separator),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EvidriloColors.Cobalt),
                        ) {
                            Text("Earlier")
                        }
                        Text(
                            text = "${notificationPreferences.hour.toString().padStart(2, '0')}:${notificationPreferences.minute.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        OutlinedButton(
                            onClick = {
                                onSetNotificationPreferences(
                                    notificationPreferences.adjustTime(minutes = 15),
                                )
                            },
                            enabled = notificationPreferences.enabled && !notificationBusy,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(2.dp, EvidriloColors.Separator),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = EvidriloColors.Cobalt),
                        ) {
                            Text("Later")
                        }
                    }
                    notificationScheduleLabel?.let { scheduleLabel ->
                        Text(
                            "Next schedule: $scheduleLabel",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } ?: Text(
                        "No reminder is currently scheduled. Start or complete a case, then return here to review the eligible reminder.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    when (notificationPermission) {
                        NotificationPermissionState.GRANTED -> Text(
                            "System notification permission is granted.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        NotificationPermissionState.DENIED -> {
                            Text(
                                "System notification permission is off. Enable it in system settings to receive reminders.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            EvidriloSecondaryButton(
                                label = "Open notification settings",
                                onClick = onOpenNotificationSettings,
                            )
                        }
                        NotificationPermissionState.UNKNOWN -> Text(
                            "Permission is requested only after you choose Enable local reminders.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        NotificationPermissionState.UNAVAILABLE -> Text(
                            "Local reminders are unavailable in this build; the free core remains usable.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    notificationStatusMessage?.let { message ->
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        }

        if (section == EvidriloSettingsSection.PRIVACY_DATA) {
        EvidriloSectionHeading("Data controls")
        EvidriloSettingsGroup {
            EvidriloSettingsRow(
                icon = EvidriloIconName.SHIELD,
                title = "Privacy and local data",
                subtitle = "Stored on this device only",
                onClick = { localDataExpanded = !localDataExpanded },
                selected = localDataExpanded,
                stateDescription = disclosureStateDescription(localDataExpanded),
                trailingIcon = if (localDataExpanded) {
                    EvidriloIconName.CHEVRON_DOWN
                } else {
                    EvidriloIconName.CHEVRON_RIGHT
                },
            )
            if (localDataExpanded) {
                EvidriloDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Drafts and the latest evidence-change comparison stay inside the platform storage adapter. The evaluator does not send learner text to a server.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EvidriloPrimaryButton(
                        label = "Reset current workflow",
                        onClick = { pendingAction = SettingsDestructiveAction.RESET_PRACTICE },
                        trailingIcon = null,
                    )
                    EvidriloSecondaryButton(
                        label = "Clear latest comparison",
                        onClick = { pendingAction = SettingsDestructiveAction.CLEAR_HISTORY },
                        enabled = historyAvailable,
                    )
                }
            }
            EvidriloDivider()
            EvidriloSettingsRow(
                icon = EvidriloIconName.SHIELD,
                title = "Optional product analytics",
                subtitle = if (TEMPORARY_GUEST_MODE_ENABLED) {
                    "Paused in local guest mode"
                } else if (analyticsConsent == AnalyticsConsent.GRANTED) {
                    "On · minimal events only"
                } else {
                    "Off by default"
                },
                onClick = { analyticsExpanded = !analyticsExpanded },
                selected = analyticsExpanded,
                stateDescription = disclosureStateDescription(analyticsExpanded),
                trailingIcon = if (analyticsExpanded) {
                    EvidriloIconName.CHEVRON_DOWN
                } else {
                    EvidriloIconName.CHEVRON_RIGHT
                },
            )
            if (analyticsExpanded) {
                EvidriloDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        if (TEMPORARY_GUEST_MODE_ENABLED) {
                            "Analytics transmission is paused in local guest mode. Your choice here does not send events from this build."
                        } else {
                            "Analytics is optional and off by default. If enabled, a signed-in account may send completion and recommendation events without draft text, passwords, or payment data. Turning it off stops future sends."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    EvidriloPrimaryButton(
                        label = "Enable optional analytics",
                        onClick = { onSetAnalyticsConsent(AnalyticsConsent.GRANTED) },
                        enabled = !TEMPORARY_GUEST_MODE_ENABLED && analyticsConsent != AnalyticsConsent.GRANTED,
                        trailingIcon = null,
                    )
                    EvidriloSecondaryButton(
                        label = "Turn analytics off",
                        onClick = { onSetAnalyticsConsent(AnalyticsConsent.NOT_GRANTED) },
                        enabled = analyticsConsent == AnalyticsConsent.GRANTED,
                    )
                }
            }
            EvidriloDivider()
            EvidriloSettingsRow(
                icon = EvidriloIconName.FOLDER,
                title = sync.title,
                subtitle = syncStatusMessage ?: sync.subtitle,
                onClick = { syncExpanded = !syncExpanded },
                selected = syncExpanded,
                stateDescription = disclosureStateDescription(syncExpanded),
                trailingIcon = if (syncExpanded) {
                    EvidriloIconName.CHEVRON_DOWN
                } else {
                    EvidriloIconName.CHEVRON_RIGHT
                },
            )
            if (syncExpanded) {
                EvidriloDivider()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Cloud sync is optional and off by default. When enabled for a verified account, only redacted progress metadata is sent; learner-authored draft text remains on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (TEMPORARY_GUEST_MODE_ENABLED) {
                        Text(
                            "Cloud sync is paused in local guest mode. Your projects stay on this device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        EvidriloSecondaryButton(
                            label = "Turn cloud sync off",
                            onClick = { onSetSyncConsent(SyncConsent.NOT_GRANTED) },
                            enabled = syncConsent == SyncConsent.GRANTED,
                        )
                    } else {
                        if (!syncSignedIn) {
                            Text(
                                "Sign in first to enable this option.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        EvidriloPrimaryButton(
                            label = "Enable cloud progress sync",
                            onClick = { onSetSyncConsent(SyncConsent.GRANTED) },
                            enabled = syncSignedIn && syncStorageAvailable && syncConsent != SyncConsent.GRANTED,
                            trailingIcon = null,
                        )
                        EvidriloSecondaryButton(
                            label = "Turn cloud sync off",
                            onClick = { onSetSyncConsent(SyncConsent.NOT_GRANTED) },
                            enabled = syncConsent == SyncConsent.GRANTED,
                        )
                        EvidriloSecondaryButton(
                            label = if (syncBusy) "Checking cloud sync…" else "Sync now",
                            onClick = onSyncNow,
                            enabled = sync.canSyncNow && !syncBusy,
                        )
                    }
                }
            }
        }
        EvidriloTintPanel {
            Text("What stays local", style = MaterialTheme.typography.titleSmall)
            Text(
                if (TEMPORARY_GUEST_MODE_ENABLED) {
                    "Project drafts and case work stay on this device. Pro requires sign-in and a confirmed RevenueCat entitlement; server AI, cloud sync, and analytics transmission remain paused."
                } else {
                    cloudSyncDisclosure() + " Local projects work without an account. Sign-in is needed for account-bound learning and project AI when enabled; it does not enable cloud sync."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        }
    }
}

private fun NotificationPreferences.adjustTime(minutes: Int): NotificationPreferences {
    val currentMinutes = hour * 60 + minute
    val nextMinutes = (currentMinutes + minutes).mod(24 * 60)
    return copy(
        hour = nextMinutes / 60,
        minute = nextMinutes % 60,
    )
}

@Composable
private fun EvidriloThemeChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) EvidriloColors.Tint else EvidriloColors.Surface,
        border = BorderStroke(2.dp, if (selected) EvidriloColors.Cobalt else EvidriloColors.Separator),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) EvidriloColors.Ink else EvidriloColors.Slate,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private enum class SettingsDestructiveAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
) {
    RESET_PRACTICE(
        title = "Reset current workflow?",
        message = "This removes the saved in-progress draft. Your completed comparison stays in local history.",
        confirmLabel = "Reset workflow",
    ),
    CLEAR_HISTORY(
        title = "Clear latest comparison?",
        message = "This removes the one completed comparison stored on this device. It cannot be recovered by the free core.",
        confirmLabel = "Clear comparison",
    ),
}
