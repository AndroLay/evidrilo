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
import dev.nextgen.mobile.domain.conclusion.ConclusionEvaluation
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
            icon = EvidriloIconName.FOLDER,
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
            icon = EvidriloIconName.LAYERS,
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
    onOpenWorkspace: () -> Unit,
    onStartPractice: () -> Unit,
) {
    EvidriloTargetSurface(EvidriloTargetSection.SOURCES, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "SOURCES",
                title = "Start with what is supplied.",
                body = "This free case keeps its source material bundled and available offline.",
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
            EvidriloPrimaryButton(label = "Review project overview", onClick = onOpenWorkspace)
            EvidriloSecondaryButton(label = "Practice this case", onClick = onStartPractice)
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
) {
    val metrics = targetWorkspaceMetrics(case, draft)
    EvidriloTargetSurface(EvidriloTargetSection.HOME, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "PROJECT OVERVIEW",
                title = case.title,
                body = case.description,
            )
            TargetProgressCard(metrics)
            TargetSectionRow(
                icon = EvidriloIconName.LAYERS,
                title = "Evidence map",
                subtitle = "${metrics.evidenceCount} supplied observations · ${metrics.gapCount} open gap",
                onClick = onOpenEvidence,
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
    onOpenClaimTrace: () -> Unit,
    onStartPractice: () -> Unit,
) {
    val selectedEvidence = draft.evidenceRefs.toSet()
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "EVIDENCE MAP",
                title = "See what holds up.",
                body = "Each observation is anchored to the supplied case. A selected fact is not automatically a stronger claim.",
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
            EvidriloPrimaryButton(label = "Open requirement trace", onClick = onOpenClaimTrace)
            EvidriloSecondaryButton(label = "Open practice", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetActionScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenVerify: () -> Unit,
    onStartPractice: () -> Unit,
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
            EvidriloPrimaryButton(label = "Verify claim boundary", onClick = onOpenVerify)
            EvidriloSecondaryButton(label = "Review the case", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetClaimTraceScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onBack: () -> Unit,
    onOpenAction: () -> Unit,
    onStartPractice: () -> Unit,
) {
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            EvidriloBackButton(label = "Evidence map", onClick = onBack)
            TargetPageIntro(
                eyebrow = "REQUIREMENT TRACE",
                title = "Where does this come from?",
                body = "Follow the supplied requirement, observations, and boundary before deciding what the claim can carry.",
            )
            EvidriloWorkspaceTraceCard(case = case, draft = draft)
            EvidriloTintPanel {
                Text("Offline source locator", style = MaterialTheme.typography.titleSmall)
                Text(
                    "This case is bundled locally. Document-level page locators are not available, so Evidrilo keeps the fact text and IDs visible instead of inventing a citation.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloPrimaryButton(label = "Continue to action plan", onClick = onOpenAction)
            EvidriloSecondaryButton(label = "Open practice", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetVerifyClaimScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation?,
    canRevise: Boolean,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onBack: () -> Unit,
    onRevise: () -> Unit,
    onStartPractice: () -> Unit,
) {
    EvidriloTargetSurface(EvidriloTargetSection.ACTION, onNavigate) {
        EvidriloContentColumn {
            EvidriloBackButton(label = "Action plan", onClick = onBack)
            TargetPageIntro(
                eyebrow = "VERIFY CLAIM · CLAIM BOUNDARY",
                title = "Does the claim stay inside the evidence?",
                body = "Verification reports the reducer's deterministic checks. It does not generate a replacement conclusion.",
            )
            if (evaluation == null) {
                EvidriloTintPanel {
                    Text("Not assessed yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Complete the local practice draft to see anchored feedback for this claim.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EvidriloPrimaryButton(label = "Open practice", onClick = onStartPractice)
            } else {
                EvidriloClaimBoundaryCard(case = case, draft = draft, evaluation = evaluation)
                if (canRevise) {
                    EvidriloPrimaryButton(label = "Revise once", onClick = onRevise)
                } else {
                    EvidriloPrimaryButton(label = "Open practice", onClick = onStartPractice)
                }
            }
            EvidriloSecondaryButton(label = "Back to action plan", onClick = onBack)
        }
    }
}

@Composable
internal fun EvidriloTargetEvidenceDeltaScreen(
    case: ConclusionCase,
    before: ConclusionDraft,
    after: ConclusionDraft,
    evaluation: ConclusionEvaluation?,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onStartChallenge: () -> Unit,
) {
    EvidriloTargetSurface(EvidriloTargetSection.ACTION, onNavigate) {
        EvidriloContentColumn {
            EvidriloBackButton(label = "Action plan", onClick = onBack)
            TargetPageIntro(
                eyebrow = "EVIDENCE DELTA",
                title = "See what changed.",
                body = "One revision is kept beside the original so you can inspect the change without losing learner authorship.",
            )
            EvidriloEvidenceDeltaCard(
                before = before,
                after = after,
                title = "Before feedback → after one revision",
            )
            evaluation?.let { finalEvaluation ->
                EvidriloClaimBoundaryCard(
                    case = case,
                    draft = after,
                    evaluation = finalEvaluation,
                )
            } ?: EvidriloTintPanel {
                Text("Verification unavailable", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The revision snapshot is available locally, but there is no completed evaluation to display.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloPrimaryButton(label = "Open local history", onClick = onOpenHistory)
            EvidriloSecondaryButton(label = "Try the evidence-change challenge", onClick = onStartChallenge)
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
) {
    EvidriloTargetSurface(EvidriloTargetSection.PROFILE, onNavigate) {
        EvidriloContentColumn {
            TargetPageIntro(
                eyebrow = "PROFILE",
                title = "Keep your practice yours.",
                body = profileSubtitle,
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
internal fun TargetPageIntro(
    eyebrow: String,
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
        Text(title, style = MaterialTheme.typography.displayMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge)
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
