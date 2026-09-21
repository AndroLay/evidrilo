package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.analytics.AnalyticsConsent
import dev.nextgen.mobile.audio.AudioSettings
import dev.nextgen.mobile.notifications.NotificationPermissionState
import dev.nextgen.mobile.notifications.NotificationPreferences
import dev.nextgen.mobile.sync.SyncConsent
import dev.nextgen.mobile.sync.syncPresentation
import dev.nextgen.mobile.storage.LocalStorageNotice
import dev.nextgen.mobile.storage.LocalStorageStatus
import dev.nextgen.mobile.surfaces.cloudSyncDisclosure
import dev.nextgen.mobile.surfaces.disclosureStateDescription

@Composable
internal fun EvidriloSettingsScreen(
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
    notificationPreferences: NotificationPreferences = NotificationPreferences(),
    notificationPermission: NotificationPermissionState = NotificationPermissionState.UNKNOWN,
    notificationScheduleLabel: String? = null,
    notificationStatusMessage: String? = null,
    notificationBusy: Boolean = false,
    onEnableNotifications: () -> Unit = {},
    onDisableNotifications: () -> Unit = {},
    onSetNotificationPreferences: (NotificationPreferences) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
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
            Text("Settings", style = MaterialTheme.typography.displayMedium)
            Text(
                "Tune the evidence workflow and keep local data understandable.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        storageNotice?.let { notice ->
            EvidriloRecoveryNotice(notice = notice)
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
                title = "Evidence guide",
                subtitle = "Evidence · claim · limits · revision",
                onClick = onOpenGuide,
            )
            EvidriloDivider()
            EvidriloSettingsRow(
                icon = EvidriloIconName.LAYERS,
                title = "Evidence cases",
                subtitle = "Two additional cases · optional monthly/yearly access",
                onClick = onOpenPremium,
            )
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
                    Button(
                        onClick = {
                            onSetAudioSettings(
                                audioSettings.copy(narrationEnabled = !audioSettings.narrationEnabled),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (audioSettings.narrationEnabled) "Turn narration off" else "Turn narration on")
                    }
                    OutlinedButton(
                        onClick = {
                            onSetAudioSettings(
                                audioSettings.copy(effectsEnabled = !audioSettings.effectsEnabled),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (audioSettings.effectsEnabled) {
                                "Turn interaction sounds off"
                            } else {
                                "Turn interaction sounds on"
                            },
                        )
                    }
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
            EvidriloDivider()
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
                    Button(
                        onClick = if (notificationPreferences.enabled) {
                            onDisableNotifications
                        } else {
                            onEnableNotifications
                        },
                        enabled = !notificationBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                notificationBusy -> "Updating reminders…"
                                notificationPreferences.enabled -> "Turn reminders off"
                                else -> "Enable local reminders"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onSetNotificationPreferences(
                                notificationPreferences.copy(
                                    continueUnfinishedEnabled =
                                        !notificationPreferences.continueUnfinishedEnabled,
                                ),
                            )
                        },
                        enabled = notificationPreferences.enabled && !notificationBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (notificationPreferences.continueUnfinishedEnabled) {
                                "Unfinished case reminders on"
                            } else {
                                "Unfinished case reminders off"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onSetNotificationPreferences(
                                notificationPreferences.copy(
                                    reviewCompletedEnabled =
                                        !notificationPreferences.reviewCompletedEnabled,
                                ),
                            )
                        },
                        enabled = notificationPreferences.enabled && !notificationBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (notificationPreferences.reviewCompletedEnabled) {
                                "Completed review reminders on"
                            } else {
                                "Completed review reminders off"
                            },
                        )
                    }
                    OutlinedButton(
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
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cadence · ${notificationPreferences.cadence.label}")
                    }
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
                            OutlinedButton(
                                onClick = onOpenNotificationSettings,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open notification settings")
                            }
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

        EvidriloSectionHeading("Data and account")
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
                    Button(
                        onClick = { pendingAction = SettingsDestructiveAction.RESET_PRACTICE },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset current workflow")
                    }
                    OutlinedButton(
                        onClick = { pendingAction = SettingsDestructiveAction.CLEAR_HISTORY },
                        enabled = historyAvailable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear latest comparison")
                    }
                }
            }
            EvidriloDivider()
            EvidriloSettingsRow(
                icon = EvidriloIconName.SHIELD,
                title = "Optional product analytics",
                subtitle = if (analyticsConsent == AnalyticsConsent.GRANTED) {
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
                        "Analytics is optional and off by default. If enabled, a signed-in account may send completion and recommendation events without draft text, passwords, or payment data. Turning it off stops future sends.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { onSetAnalyticsConsent(AnalyticsConsent.GRANTED) },
                        enabled = analyticsConsent != AnalyticsConsent.GRANTED,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Enable optional analytics")
                    }
                    OutlinedButton(
                        onClick = { onSetAnalyticsConsent(AnalyticsConsent.NOT_GRANTED) },
                        enabled = analyticsConsent == AnalyticsConsent.GRANTED,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Turn analytics off")
                    }
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
                    if (!syncSignedIn) {
                        Text(
                            "Sign in first to enable this option.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Button(
                        onClick = { onSetSyncConsent(SyncConsent.GRANTED) },
                        enabled = syncSignedIn && syncStorageAvailable && syncConsent != SyncConsent.GRANTED,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Enable cloud progress sync")
                    }
                    OutlinedButton(
                        onClick = { onSetSyncConsent(SyncConsent.NOT_GRANTED) },
                        enabled = syncConsent == SyncConsent.GRANTED,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Turn cloud sync off")
                    }
                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = sync.canSyncNow && !syncBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (syncBusy) "Checking cloud sync…" else "Sync now")
                    }
                }
            }
            EvidriloDivider()
            EvidriloSettingsRow(
                icon = EvidriloIconName.ACCOUNT,
                title = "Account",
                subtitle = accountSubtitle,
                onClick = onOpenAccount,
            )
        }

        EvidriloSectionHeading("About")
        EvidriloSettingsGroup {
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
                cloudSyncDisclosure() + " The free core remains usable without login, network, or billing configuration.",
                style = MaterialTheme.typography.bodyMedium,
            )
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
