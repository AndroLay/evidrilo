package dev.nextgen.mobile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.audio.AudioPlaybackState
import dev.nextgen.mobile.audio.EvidriloAudioListenControl
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionFact
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.EVIDRILO_M0_T2_CASE_ID
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.recommendation.RecommendationUiState
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.LocalStorageNotice

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
    onOpenProjectCatalog: () -> Unit,
    projects: List<StudentProjectDraft> = emptyList(),
    projectsLoading: Boolean = false,
    projectsLoadError: String? = null,
    onRetryProjects: () -> Unit = {},
    onOpenProjects: () -> Unit = onOpenProjectCatalog,
    onCreateProject: () -> Unit = onOpenProjects,
    onResumeProject: (StudentProjectDraft) -> Unit = {},
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
    var casePracticeExpanded by remember { mutableStateOf(false) }
    val metrics = targetWorkspaceMetrics(case, draft)
    val claimStarted = draft.claimText.trim().length >= 20
    val nextStep = when {
        metrics.selectedEvidenceCount == 0 -> 0
        !claimStarted -> 1
        metrics.actionCount == 0 -> 2
        else -> -1
    }

    EvidriloTargetSurface(EvidriloTargetSection.HOME, onNavigate) {
        EvidriloContentColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            EvidriloBrandHeader(onSettings = onOpenSettings)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Your research starts here.",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Start with your assignment and build a traceable evidence trail.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
            storageNotice
                ?.takeIf { it.isError }
                ?.let { notice ->
                    Spacer(Modifier.height(18.dp))
                    EvidriloRecoveryNotice(notice = notice)
                }
            Spacer(Modifier.height(16.dp))
            HomeProjectsSection(
                projects = projects,
                isLoading = projectsLoading,
                loadError = projectsLoadError,
                onRetry = onRetryProjects,
                onOpenProjects = onOpenProjects,
                onCreateProject = onCreateProject,
                onResumeProject = onResumeProject,
            )
            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = { casePracticeExpanded = !casePracticeExpanded },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EvidriloIcon(EvidriloIconName.BOOK, tint = EvidriloColors.Cobalt, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("Optional guided case", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (casePracticeExpanded) "Tap to hide the worked example" else "Explore a worked example offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidriloColors.Slate,
                        )
                    }
                    EvidriloIcon(
                        if (casePracticeExpanded) EvidriloIconName.CHEVRON_DOWN else EvidriloIconName.CHEVRON_RIGHT,
                        tint = EvidriloColors.Slate,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (casePracticeExpanded) {
                Spacer(Modifier.height(12.dp))
                HomeCaseSpotlight(
                    case = case,
                    draftPieces = metrics.draftCompletenessPercent / 20,
                    onStartPractice = onStartPractice,
                )
                Spacer(Modifier.height(16.dp))
                Text("In this case", style = MaterialTheme.typography.titleLarge)
                HomeLearningStep(
                    icon = EvidriloIconName.EVIDENCE_GRAPH,
                    title = "Explore the supplied evidence",
                    detail = if (metrics.selectedEvidenceCount > 0) {
                        "${metrics.selectedEvidenceCount} observations selected"
                    } else {
                        "See what the supplied facts actually show"
                    },
                    isNext = nextStep == 0,
                    isDone = metrics.selectedEvidenceCount > 0,
                    onClick = onOpenEvidence,
                )
                HomeLearningStep(
                    icon = EvidriloIconName.FILE,
                    title = "Write a bounded claim",
                    detail = if (claimStarted) "A claim is in your draft" else "Keep your wording within the evidence",
                    isNext = nextStep == 1,
                    isDone = claimStarted,
                    onClick = onOpenWorkspace,
                )
                HomeLearningStep(
                    icon = EvidriloIconName.CHECKLIST,
                    title = "Choose a next action",
                    detail = if (metrics.actionCount > 0) "A next action is in your draft" else "Turn a limitation into a useful action",
                    isNext = nextStep == 2,
                    isDone = metrics.actionCount > 0,
                    onClick = onOpenAction,
                )
            }
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = EvidriloColors.Separator)
            HomeUtilityRow(
                icon = EvidriloIconName.FOLDER,
                title = "Explore project types",
                detail = "Browse five academic project families",
                onClick = onOpenProjectCatalog,
            )
            HorizontalDivider(color = EvidriloColors.Separator)
            HomeUtilityRow(
                icon = EvidriloIconName.BOOK,
                title = "How Evidrilo works",
                detail = "A quick guide to the evidence flow",
                onClick = onOpenGuide,
            )
            if (history != null) {
                HorizontalDivider(color = EvidriloColors.Separator)
                HomeUtilityRow(
                    icon = EvidriloIconName.HISTORY,
                    title = "Your recent changes",
                    detail = "Compare what changed in your reasoning",
                    onClick = onOpenHistory,
                )
            }
            Spacer(Modifier.height(18.dp))
            if (recommendation !is RecommendationUiState.Hidden) {
                EvidriloRecommendationCard(
                    state = recommendation,
                    onAccept = onAcceptRecommendation,
                    onDismiss = onDismissRecommendation,
                    onRetry = onRetryRecommendation,
                )
                Spacer(Modifier.height(16.dp))
            }
            audioState?.let { state ->
                EvidriloAudioListenControl(
                    state = state,
                    onListen = onListen,
                    onPauseOrResume = onPauseOrResumeAudio,
                    onStopAudio = onStopAudio,
                )
            }
        }
    }
}

@Composable
private fun HomeProjectsSection(
    projects: List<StudentProjectDraft>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onOpenProjects: () -> Unit,
    onCreateProject: () -> Unit,
    onResumeProject: (StudentProjectDraft) -> Unit,
) {
    val overview = homeProjectOverview(projects)
    val primaryProject = overview.primaryProject
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My projects", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            Text("On this device", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
        }
        if (!isLoading && loadError == null && (primaryProject != null || projects.isNotEmpty())) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (primaryProject != null) {
                    TextButton(
                        onClick = onCreateProject,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("New project") }
                }
                if (projects.isNotEmpty()) {
                    TextButton(
                        onClick = onOpenProjects,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("View all") }
                }
            }
        }
        when {
            isLoading -> EvidriloTargetCard {
                Text("Loading your projects…", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            loadError != null -> EvidriloTargetCard {
                Text("Projects couldn’t be loaded", style = MaterialTheme.typography.titleLarge)
                Text(loadError, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                EvidriloPrimaryButton(label = "Try again", onClick = onRetry)
            }
            primaryProject == null -> EvidriloTargetCard {
                Text("Begin with what you have", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Create a blank project from your own assignment. You can add sources and notes as you find them—nothing is assumed or prefilled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.Slate,
                )
                EvidriloPrimaryButton(label = "Create a project", onClick = onCreateProject)
            }
            else -> {
                EvidriloTargetCard {
                    Text(primaryProject.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        primaryProject.templateSnapshot?.title ?: "Manual project",
                        style = MaterialTheme.typography.bodySmall,
                        color = EvidriloColors.Slate,
                    )
                    Text(
                        requiredProjectProgressLabel(dev.nextgen.mobile.domain.project.StudentProjectDraftRules.requiredFieldProgress(primaryProject)),
                        style = MaterialTheme.typography.labelMedium,
                        color = EvidriloColors.Cobalt,
                    )
                    EvidriloPrimaryButton(label = "Continue project", onClick = { onResumeProject(primaryProject) })
                }
            }
        }
    }
}

@Composable
private fun HomeCaseSpotlight(
    case: ConclusionCase,
    draftPieces: Int,
    onStartPractice: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = EvidriloColors.Card,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Your current case",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = EvidriloColors.Slate,
                )
                Surface(
                    shape = CircleShape,
                    color = EvidriloColors.PaleBlue,
                ) {
                    Text(
                        text = "LOCAL PRACTICE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = EvidriloColors.Cobalt,
                    )
                }
            }
            Text(
                text = case.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HomeObservationPreview(case)
            if (draftPieces > 0) {
                Text(
                    text = "$draftPieces of 5 draft pieces in place",
                    style = MaterialTheme.typography.labelMedium,
                    color = EvidriloColors.Slate,
                )
            }
            EvidriloPrimaryButton(
                label = if (draftPieces > 0) "Continue your case" else "Start this case",
                onClick = onStartPractice,
            )
        }
    }
}

@Composable
private fun HomeObservationPreview(case: ConclusionCase) {
    val observations = case.factsOfType(ConclusionFactType.OBSERVATION).take(3)
    if (observations.isEmpty()) return
    val isDissolutionCase = case.id == EVIDRILO_M0_T2_CASE_ID
    val values = if (isDissolutionCase) {
        observations.map { it.displayValue?.substringBefore(' ')?.toFloatOrNull() }
    } else {
        emptyList()
    }
    val maximum = if (values.size == observations.size && values.all { it != null }) {
        values.filterNotNull().maxOrNull()
    } else {
        null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = EvidriloColors.PaleBlue,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Supplied observations",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = EvidriloColors.Ink,
                )
                Text(
                    text = "${case.factsOfType(ConclusionFactType.OBSERVATION).size} facts",
                    style = MaterialTheme.typography.labelSmall,
                    color = EvidriloColors.Slate,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                observations.forEachIndexed { index, observation ->
                    HomeObservationCell(
                        observation = observation,
                        fraction = if (maximum != null && maximum > 0f) {
                            (values[index] ?: 0f) / maximum
                        } else {
                            null
                        },
                        index = index,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeObservationCell(
    observation: ConclusionFact,
    fraction: Float?,
    index: Int,
    modifier: Modifier = Modifier,
) {
    var appeared by remember(observation.id) { mutableStateOf(false) }
    val fill by animateFloatAsState(
        targetValue = if (appeared) fraction ?: 1f else 0f,
        animationSpec = tween(durationMillis = 520, delayMillis = index * 90),
        label = "observationFill",
    )
    LaunchedEffect(observation.id) { appeared = true }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = observation.displayLabel ?: "Fact ${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = EvidriloColors.Slate,
            maxLines = 1,
        )
        Text(
            text = observation.displayValue.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = EvidriloColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (fraction != null) Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(EvidriloColors.Tint),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(EvidriloColors.Cobalt),
            )
        }
    }
}

@Composable
private fun HomeLearningStep(
    icon: EvidriloIconName,
    title: String,
    detail: String,
    isNext: Boolean,
    isDone: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isNext) EvidriloColors.PaleBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $detail${if (isNext) ". Suggested next step" else ""}"
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = when {
                isNext -> EvidriloColors.Cobalt
                isDone -> EvidriloColors.PaleBlue
                else -> EvidriloColors.Surface
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                EvidriloIcon(
                    name = if (isDone) EvidriloIconName.CHECK else icon,
                    tint = when {
                        isNext -> EvidriloColors.White
                        isDone -> EvidriloColors.Cobalt
                        else -> EvidriloColors.Slate
                    },
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
        EvidriloIcon(
            name = EvidriloIconName.CHEVRON_RIGHT,
            tint = if (isNext) EvidriloColors.Cobalt else EvidriloColors.Slate,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun HomeStepConnector(isDone: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 35.dp)
            .width(2.dp)
            .height(12.dp)
            .background(if (isDone) EvidriloColors.Cobalt else EvidriloColors.Separator),
    )
}

@Composable
private fun HomeUtilityRow(
    icon: EvidriloIconName,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $detail"
                role = Role.Button
            }
            .padding(vertical = 14.dp)
            .heightIn(min = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EvidriloIcon(icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
        EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate, modifier = Modifier.size(20.dp))
    }
}
