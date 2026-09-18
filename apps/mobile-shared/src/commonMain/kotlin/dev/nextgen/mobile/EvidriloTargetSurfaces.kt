package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.audio.AudioPlaybackState
import dev.nextgen.mobile.audio.EvidriloAudioListenControl
import dev.nextgen.mobile.recommendation.RecommendationUiState
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.LocalStorageNotice

internal enum class EvidriloTargetSection {
    HOME,
    SOURCES,
    EVIDENCE,
    ACTION,
    PROFILE,
}

@Composable
internal fun EvidriloTargetSurface(
    selected: EvidriloTargetSection,
    onNavigate: (EvidriloTargetSection) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EvidriloColors.White),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            content()
        }
        EvidriloTargetBottomNavigation(selected = selected, onNavigate = onNavigate)
    }
}

@Composable
private fun EvidriloTargetBottomNavigation(
    selected: EvidriloTargetSection,
    onNavigate: (EvidriloTargetSection) -> Unit,
) {
    HorizontalDivider(color = EvidriloColors.Separator)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .safeDrawingPadding()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EvidriloTargetNavigationItem(
            label = "Home",
            icon = EvidriloIconName.HOME,
            section = EvidriloTargetSection.HOME,
            selected = selected == EvidriloTargetSection.HOME,
            onClick = { onNavigate(EvidriloTargetSection.HOME) },
        )
        EvidriloTargetNavigationItem(
            label = "Sources",
            icon = EvidriloIconName.FILE,
            section = EvidriloTargetSection.SOURCES,
            selected = selected == EvidriloTargetSection.SOURCES,
            onClick = { onNavigate(EvidriloTargetSection.SOURCES) },
        )
        EvidriloTargetNavigationItem(
            label = "Evidence",
            icon = EvidriloIconName.LINK,
            section = EvidriloTargetSection.EVIDENCE,
            selected = selected == EvidriloTargetSection.EVIDENCE,
            onClick = { onNavigate(EvidriloTargetSection.EVIDENCE) },
        )
        EvidriloTargetNavigationItem(
            label = "Action",
            icon = EvidriloIconName.CHECKLIST,
            section = EvidriloTargetSection.ACTION,
            selected = selected == EvidriloTargetSection.ACTION,
            onClick = { onNavigate(EvidriloTargetSection.ACTION) },
        )
        EvidriloTargetNavigationItem(
            label = "Profile",
            icon = EvidriloIconName.ACCOUNT,
            section = EvidriloTargetSection.PROFILE,
            selected = selected == EvidriloTargetSection.PROFILE,
            onClick = { onNavigate(EvidriloTargetSection.PROFILE) },
        )
    }
}

@Composable
private fun RowScope.EvidriloTargetNavigationItem(
    label: String,
    icon: EvidriloIconName,
    section: EvidriloTargetSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        EvidriloIcon(
            name = icon,
            tint = if (selected) EvidriloColors.Cobalt else EvidriloColors.Slate,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) EvidriloColors.Cobalt else EvidriloColors.Slate,
        )
    }
}

@Composable
internal fun EvidriloTargetHomeScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    history: ConclusionSessionSnapshot?,
    storageNotice: LocalStorageNotice? = null,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenAction: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartPractice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit = {},
    recommendation: RecommendationUiState = RecommendationUiState.Hidden,
    onAcceptRecommendation: () -> Unit = {},
    onDismissRecommendation: () -> Unit = {},
    onRetryRecommendation: () -> Unit = {},
    audioState: AudioPlaybackState? = null,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    val metrics = targetWorkspaceMetrics(case, draft)
    EvidriloTargetSurface(EvidriloTargetSection.HOME, onNavigate) {
        EvidriloContentColumn {
            EvidriloBrandHeader(onSettings = onOpenSettings)
            Text(
                text = "What are you trying\nto finish?",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = "Build a conclusion you can trace back to the evidence.",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (audioState != null) {
                EvidriloAudioListenControl(
                    state = audioState,
                    onListen = onListen,
                    onPauseOrResume = onPauseOrResumeAudio,
                    onStopAudio = onStopAudio,
                )
            }
            EvidriloRecommendationCard(
                state = recommendation,
                onAccept = onAcceptRecommendation,
                onDismiss = onDismissRecommendation,
                onRetry = onRetryRecommendation,
            )
            storageNotice?.let { notice -> EvidriloRecoveryNotice(notice = notice) }
            TargetProjectCard(case = case, metrics = metrics, onClick = onOpenWorkspace)
            TargetMetricStrip(metrics = metrics)
            TargetSectionRow(
                icon = EvidriloIconName.FILE,
                title = "Supplied sources",
                subtitle = "${metrics.evidenceCount} observations in this case",
                onClick = onOpenSources,
            )
            TargetSectionRow(
                icon = EvidriloIconName.LAYERS,
                title = "Evidence map",
                subtitle = "See what supports the conclusion and what remains bounded",
                onClick = onOpenEvidence,
            )
            TargetSectionRow(
                icon = EvidriloIconName.CHECKLIST,
                title = "Action plan",
                subtitle = if (draft.implication == null) "Choose a next action after the review" else "Your next action is recorded locally",
                onClick = onOpenAction,
            )
            TargetSectionRow(
                icon = EvidriloIconName.HISTORY,
                title = "History",
                subtitle = targetHistorySubtitle(history),
                onClick = onOpenHistory,
            )
            TargetSectionRow(
                icon = EvidriloIconName.BOOK,
                title = "Practice guide",
                subtitle = "Evidence · claim · limits · revision",
                onClick = onOpenGuide,
            )
            EvidriloPrimaryButton(label = "Start practice", onClick = onStartPractice)
            Text(
                text = "No account or network is required for the free core.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun EvidriloTargetSourcesScreen(
    case: ConclusionCase,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onStartPractice: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    EvidriloTargetSurface(EvidriloTargetSection.SOURCES, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "SOURCES",
                title = "Start with what is supplied.",
                body = "This free case keeps its source material bundled and available offline.",
                onBack = onBack,
            )
            TargetFactCard(
                fact = case.facts.first { it.type == ConclusionFactType.AIM },
                status = "Requirement",
            )
            case.facts
                .filter { it.type == ConclusionFactType.OBSERVATION }
                .forEach { fact -> TargetFactCard(fact = fact, status = "Supplied evidence") }
            EvidriloTintPanel {
                Text("Import is intentionally out of the competition slice", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The current free core does not pretend to parse arbitrary documents. Future ingestion can be added behind an explicit content contract.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloPrimaryButton(label = "Practice this case", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetWorkspaceScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenAction: () -> Unit,
    onStartPractice: () -> Unit,
    onOpenTrace: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val metrics = targetWorkspaceMetrics(case, draft)
    EvidriloTargetSurface(EvidriloTargetSection.HOME, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "PROJECT OVERVIEW",
                title = case.title,
                body = case.description,
                onBack = onBack,
            )
            TargetProgressCard(metrics)
            TargetSectionRow(
                icon = EvidriloIconName.LAYERS,
                title = "Evidence map",
                subtitle = "${metrics.evidenceCount} supplied observations · ${metrics.gapCount} open gap",
                onClick = onOpenEvidence,
            )
            TargetSectionRow(
                icon = EvidriloIconName.LINK,
                title = "Claim trace",
                subtitle = "Follow the requirement back to its anchors",
                onClick = onOpenTrace,
            )
            TargetSectionRow(
                icon = EvidriloIconName.CHECKLIST,
                title = "Action plan",
                subtitle = if (draft.implication == null) "Not started" else "Next action captured",
                onClick = onOpenAction,
            )
            EvidriloTintPanel {
                Text("One bounded revision", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Evidrilo keeps the free learning loop small: inspect, write, receive anchored feedback, revise once, then compare what changed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloPrimaryButton(label = "Continue practice", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetEvidenceScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onStartPractice: () -> Unit,
    onOpenTrace: () -> Unit = {},
    onOpenVerify: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val selectedEvidence = draft.evidenceRefs.toSet()
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "EVIDENCE MAP",
                title = "See what holds up.",
                body = "Each observation is anchored to the supplied case. A selected fact is not automatically a stronger claim.",
                onBack = onBack,
            )
            TargetFactCard(
                fact = case.facts.first { it.type == ConclusionFactType.AIM },
                status = "Requirement",
            )
            case.facts
                .filter { it.type == ConclusionFactType.OBSERVATION }
                .forEach { fact ->
                    TargetFactCard(
                        fact = fact,
                        status = if (fact.id in selectedEvidence) "Selected in draft" else "Available",
                    )
                }
            TargetFactCard(
                fact = case.facts.first { it.type == ConclusionFactType.BOUNDARY },
                status = "Claim boundary",
            )
            EvidriloTintPanel {
                Text("Not yet proven", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The evidence supports a limited comparison. It cannot establish a general causal effect from this case alone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloSecondaryButton(label = "Trace the requirement", onClick = onOpenTrace)
            EvidriloPrimaryButton(label = "Verify the claim boundary", onClick = onOpenVerify)
            EvidriloPrimaryButton(label = "Open practice", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetActionScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onStartPractice: () -> Unit,
    onOpenVerify: () -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val actionTitle = draft.implication.targetActionLabel()
    val actionReason = draft.implicationReason.ifBlank {
        "Complete the practice flow to connect a next action to the remaining evidence gap."
    }
    EvidriloTargetSurface(EvidriloTargetSection.ACTION, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "ACTION PLAN",
                title = "What matters next?",
                body = "A useful conclusion ends with an action that respects the evidence boundary.",
                onBack = onBack,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = EvidriloColors.Tint),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("NEXT ACTION", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
                    Text(actionTitle, style = MaterialTheme.typography.headlineSmall)
                    Text(actionReason, style = MaterialTheme.typography.bodyLarge)
                }
            }
            TargetFactCard(
                fact = case.facts.first { it.type == ConclusionFactType.LIMITATION },
                status = "Keep visible",
            )
            EvidriloTintPanel {
                Text("Bounded by design", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The action is a practice decision, not a promise that the case proves more than it contains.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloSecondaryButton(label = "Check the claim boundary", onClick = onOpenVerify)
            EvidriloPrimaryButton(label = "Review the case", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetClaimTraceScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenVerify: () -> Unit,
    onBack: () -> Unit,
) {
    val aim = case.facts.first { it.type == ConclusionFactType.AIM }
    val observations = case.factsOfType(ConclusionFactType.OBSERVATION)
    val selected = draft.evidenceRefs.toSet()
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "REQUIREMENT TRACE",
                title = "Trace the claim.",
                body = "See why this requirement exists and where the support comes from.",
                onBack = onBack,
            )
            EvidriloTargetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = EvidriloColors.Tint,
                    ) { Box(contentAlignment = Alignment.Center) { EvidriloIcon(EvidriloIconName.FILE, tint = EvidriloColors.Cobalt) } }
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Requirement 1", style = MaterialTheme.typography.titleLarge)
                        Text(aim.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    EvidriloStatusChip(
                        label = targetEvidenceStatus(case, draft).label(),
                        tone = targetEvidenceStatus(case, draft).tone(),
                    )
                }
            }
            EvidriloTargetCard {
                Text("Why this is required", style = MaterialTheme.typography.titleLarge)
                Text(
                    "The supplied case asks you to compare the observed conditions, then state a bounded conclusion with visible limitations.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            EvidriloTargetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EvidriloIcon(EvidriloIconName.LINK, tint = EvidriloColors.Cobalt)
                    Text("Supporting evidence", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp))
                }
                Text("Observation anchors from the bundled case.", style = MaterialTheme.typography.bodyMedium)
                observations.forEach { fact ->
                    TargetAnchorRow(
                        label = fact.displayLabel ?: "Observation",
                        locator = fact.displayValue ?: fact.id,
                        detail = fact.text,
                        selected = fact.id in selected,
                    )
                }
            }
            EvidriloPrimaryButton(label = "Open claim boundary", onClick = onOpenVerify)
            EvidriloSecondaryButton(label = "Back to evidence map", onClick = onOpenEvidence)
        }
    }
}

@Composable
internal fun EvidriloTargetVerifyClaimScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onStartPractice: () -> Unit,
    onBack: () -> Unit,
) {
    val boundary = case.facts.first { it.type == ConclusionFactType.BOUNDARY }
    val status = targetEvidenceStatus(case, draft)
    EvidriloTargetSurface(EvidriloTargetSection.ACTION, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "VERIFY CLAIM",
                title = "Stay inside the boundary.",
                body = "Verify only what the selected evidence can support. The learner remains the author of the claim.",
                onBack = onBack,
            )
            EvidriloTargetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Claim status", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    EvidriloStatusChip(label = status.label(), tone = status.tone())
                }
                Text(
                    draft.claimText.ifBlank { "No claim written yet. Start the practice flow to write one." },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            EvidriloCobaltCard {
                Text("CLAIM BOUNDARY", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.White.copy(alpha = 0.8f))
                Text(boundary.text, style = MaterialTheme.typography.titleLarge, color = EvidriloColors.White)
                Text(
                    "This boundary is visible alongside the claim; it is not a hidden score or an AI decision.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.White.copy(alpha = 0.9f),
                )
            }
            EvidriloTargetCard {
                Text("Anchors in this review", style = MaterialTheme.typography.titleLarge)
                case.factsOfType(ConclusionFactType.OBSERVATION)
                    .filter { it.id in draft.evidenceRefs }
                    .ifEmpty { listOf(boundary) }
                    .forEach { fact ->
                        TargetAnchorRow(
                            label = fact.displayLabel ?: fact.type.displayName(),
                            locator = fact.displayValue ?: fact.id,
                            detail = fact.text,
                            selected = fact.type == ConclusionFactType.OBSERVATION,
                        )
                    }
            }
            EvidriloPrimaryButton(label = "Write or revise this claim", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetEvidenceDeltaScreen(
    case: ConclusionCase,
    before: ConclusionDraft,
    after: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onBack: () -> Unit,
) {
    val delta = targetEvidenceDelta(before, after)
    EvidriloTargetSurface(EvidriloTargetSection.HOME, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "EVIDENCE DELTA",
                title = "What changed?",
                body = "Compare the learner-authored drafts and keep the evidence change explainable.",
                onBack = onBack,
            )
            EvidriloCobaltCard {
                Text("REVISION IMPACT", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.White.copy(alpha = 0.8f))
                Text(
                    if (delta.hasChanges) "The latest revision changed the visible reasoning." else "No learner-authored changes recorded yet.",
                    style = MaterialTheme.typography.titleLarge,
                    color = EvidriloColors.White,
                )
                Text(
                    "Evidence anchors, claim wording, limits, and next action remain separate fields.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.White.copy(alpha = 0.9f),
                )
            }
            DeltaRow("Evidence added", delta.addedEvidenceIds.ifEmpty { listOf("None") }.joinToString())
            DeltaRow("Evidence removed", delta.removedEvidenceIds.ifEmpty { listOf("None") }.joinToString())
            DeltaRow("Claim text", if (delta.claimChanged) "Changed" else "Unchanged")
            DeltaRow("Scope and limits", if (delta.scopeChanged || delta.limitationsChanged) "Changed" else "Unchanged")
            DeltaRow("Next action", if (delta.actionChanged) "Changed" else "Unchanged")
            EvidriloTintPanel {
                Text("Case boundary", style = MaterialTheme.typography.titleSmall)
                Text(case.facts.first { it.type == ConclusionFactType.BOUNDARY }.text, style = MaterialTheme.typography.bodyMedium)
            }
            EvidriloPrimaryButton(label = "Back to history", onClick = onBack)
        }
    }
}

@Composable
internal fun EvidriloTargetProfileScreen(
    profileSubtitle: String,
    history: ConclusionSessionSnapshot?,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenPremium: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    EvidriloTargetSurface(EvidriloTargetSection.PROFILE, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "PROFILE",
                title = "Keep your practice yours.",
                body = profileSubtitle,
                onBack = onBack,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
                border = BorderStroke(1.dp, EvidriloColors.Separator),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LOCAL-FIRST PRACTICE", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
                    Text("No account required", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "The free core remains available offline. Sign in only when you choose sync or account features.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            EvidriloSettingsRow(
                icon = EvidriloIconName.LAYERS,
                title = "Premium cases",
                subtitle = "Two additional cases · monthly/yearly access",
                onClick = onOpenPremium,
            )
            EvidriloSettingsRow(
                icon = EvidriloIconName.HISTORY,
                title = "History",
                subtitle = targetHistorySubtitle(history),
                onClick = onOpenHistory,
            )
            EvidriloSettingsRow(
                icon = EvidriloIconName.ACCOUNT,
                title = "Account and sync",
                subtitle = "Optional sign-in and recovery",
                onClick = onOpenAccount,
            )
            EvidriloSettingsRow(
                icon = EvidriloIconName.SETTINGS,
                title = "Settings",
                subtitle = "Privacy, audio, consent, and local recovery",
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun TargetPageIntro(
    eyebrow: String,
    title: String,
    body: String,
    onBack: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        onBack?.let { EvidriloBackButton(label = "Back", onClick = it) }
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
        Text(title, style = MaterialTheme.typography.displayMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun TargetEvidenceStatus.label(): String = when (this) {
    TargetEvidenceStatus.NOT_ASSESSED -> "Not assessed"
    TargetEvidenceStatus.PARTIALLY_SUPPORTED -> "Partially supported"
    TargetEvidenceStatus.SUPPORTED -> "Supported"
}

private fun TargetEvidenceStatus.tone(): EvidriloStatusTone = when (this) {
    TargetEvidenceStatus.NOT_ASSESSED -> EvidriloStatusTone.NEUTRAL
    TargetEvidenceStatus.PARTIALLY_SUPPORTED -> EvidriloStatusTone.WARNING
    TargetEvidenceStatus.SUPPORTED -> EvidriloStatusTone.SUCCESS
}

@Composable
private fun TargetAnchorRow(
    label: String,
    locator: String,
    detail: String,
    selected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $locator. $detail"
                stateDescription = if (selected) "Selected evidence" else "Available evidence"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(13.dp),
            color = if (selected) EvidriloColors.SuccessSurface else EvidriloColors.Tint,
        ) {
            Box(contentAlignment = Alignment.Center) {
                EvidriloIcon(
                    name = if (selected) EvidriloIconName.CHECK else EvidriloIconName.FILE,
                    tint = if (selected) EvidriloColors.Success else EvidriloColors.Cobalt,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(locator, style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
    }
}

@Composable
private fun DeltaRow(label: String, value: String) {
    EvidriloTargetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
    }
}

@Composable
private fun TargetProjectCard(
    case: ConclusionCase,
    metrics: TargetWorkspaceMetrics,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open project ${case.title}"
                role = Role.Button
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Cobalt),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("CURRENT PROJECT", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.White.copy(alpha = 0.78f))
            Text(case.title, style = MaterialTheme.typography.headlineSmall, color = EvidriloColors.White)
            Text(case.description, style = MaterialTheme.typography.bodyLarge, color = EvidriloColors.White.copy(alpha = 0.9f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TargetMetric("${metrics.coveragePercent}%", "covered", EvidriloColors.White)
                TargetMetric("${metrics.evidenceCount}", "evidence", EvidriloColors.White)
                TargetMetric("${metrics.gapCount}", "open gap", EvidriloColors.White)
            }
        }
    }
}

@Composable
private fun TargetProgressCard(metrics: TargetWorkspaceMetrics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Cobalt),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WORKSPACE COVERAGE", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text("${metrics.coveragePercent}%", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.White)
            }
            EvidriloProgressBar(progress = metrics.coveragePercent / 100f)
            Text(
                if (metrics.gapCount == 0) "The current draft connects all required signals." else "One or more signals still need a learner decision.",
                style = MaterialTheme.typography.bodyLarge,
                color = EvidriloColors.White,
            )
        }
    }
}

@Composable
private fun TargetMetricStrip(metrics: TargetWorkspaceMetrics) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TargetMetricCard("${metrics.requirementCount}", "requirement")
        TargetMetricCard("${metrics.evidenceCount}", "evidence")
        TargetMetricCard("${metrics.actionCount}", "action")
    }
}

@Composable
private fun RowScope.TargetMetricCard(value: String, label: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TargetMetric(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.78f))
    }
}

@Composable
private fun TargetSectionRow(
    icon: EvidriloIconName,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
                role = Role.Button
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = EvidriloColors.Tint,
        ) {
            Box(contentAlignment = Alignment.Center) {
                EvidriloIcon(icon, tint = EvidriloColors.Cobalt)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
    }
}

@Composable
private fun TargetFactCard(fact: ConclusionFact, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.uppercase(), style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
                Spacer(Modifier.weight(1f))
                fact.displayValue?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Ink) }
            }
            Text(fact.displayLabel ?: fact.type.displayName(), style = MaterialTheme.typography.titleLarge)
            Text(fact.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun ConclusionFactType.displayName(): String = when (this) {
    ConclusionFactType.AIM -> "Requirement"
    ConclusionFactType.CONTEXT -> "Context"
    ConclusionFactType.OBSERVATION -> "Observation"
    ConclusionFactType.LIMITATION -> "Limitation"
    ConclusionFactType.BOUNDARY -> "Boundary"
}

private fun ConclusionImplication?.targetActionLabel(): String = when (this) {
    ConclusionImplication.REPEAT_TRIALS -> "Repeat the comparison"
    ConclusionImplication.CONTROL_STIRRING -> "Control stirring"
    ConclusionImplication.LIMIT_CLAIM -> "Limit the claim"
    ConclusionImplication.NOT_APPLICABLE -> "No further action"
    ConclusionImplication.UNSUPPORTED,
    null,
    -> "Choose a next action"
}
