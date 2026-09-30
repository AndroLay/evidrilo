package dev.nextgen.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.*
import dev.nextgen.mobile.recommendation.RecommendationUiState
import dev.nextgen.mobile.storage.LocalStorageNotice

internal data class HomeProjectSummary(
    val totalCount: Int,
    val activeCount: Int,
    val completedCount: Int,
    val archivedCount: Int,
    val activeLimit: Int,
) {
    val remainingActiveSlots: Int get() = (activeLimit - activeCount).coerceAtLeast(0)
}

internal fun homeProjectSummary(
    projects: List<StudentProjectDraft>,
    activeLimit: Int,
): HomeProjectSummary {
    val safeLimit = activeLimit.coerceAtLeast(1)
    val visibleProjects = projects.filter { it.status != StudentProjectStatus.TRASHED }
    return HomeProjectSummary(
        totalCount = visibleProjects.size,
        activeCount = visibleProjects.count { StudentProjectDraftRules.countsTowardActiveLimit(it.status) },
        completedCount = visibleProjects.count { it.status == StudentProjectStatus.COMPLETED },
        archivedCount = visibleProjects.count { it.status == StudentProjectStatus.ARCHIVED },
        activeLimit = safeLimit,
    )
}

internal fun shouldShowHomeProjectListAction(
    summary: HomeProjectSummary,
    isLoading: Boolean,
    loadError: String?,
): Boolean = summary.totalCount > 0 && !isLoading && loadError == null

@Composable
internal fun EvidriloTargetHomeScreen(
    storageNotice: LocalStorageNotice? = null,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenProjectCatalog: () -> Unit,
    onSelectProjectFamily: (ProjectTemplateFamily) -> Unit,
    projects: List<StudentProjectDraft> = emptyList(),
    projectsLoading: Boolean = false,
    projectsLoadError: String? = null,
    activeProjectLimit: Int = StudentProjectDraftRules.FREE_ACTIVE_PROJECT_LIMIT,
    onRetryProjects: () -> Unit = {},
    onOpenProjects: () -> Unit = onOpenProjectCatalog,
    onCreateProject: () -> Unit = onOpenProjects,
    onResumeProject: (StudentProjectDraft) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenPractice: () -> Unit = {},
    recommendation: RecommendationUiState = RecommendationUiState.Hidden,
    onAcceptRecommendation: () -> Unit = {},
    onDismissRecommendation: () -> Unit = {},
    onRetryRecommendation: () -> Unit = {},
) {
    val summary = homeProjectSummary(projects, activeProjectLimit)
    val current = projects.filter { StudentProjectDraftRules.countsTowardActiveLimit(it.status) }.maxByOrNull { it.updatedAtEpochMillis }
    EvidriloContentColumn(includeBottomSafeArea = false, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        EvidriloBrandHeader(onSettings = onOpenSettings)
        storageNotice?.takeIf { it.isError }?.let { EvidriloRecoveryNotice(it) }
        EvidriloPageHeading("One idea.\nA clear next step.", "Your questions, notes and reasoning—in one place.")
        when {
            projectsLoading -> Surface(shape = RoundedCornerShape(22.dp), color = EvidriloColors.Atmosphere) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Opening your workspace…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = EvidriloColors.Cobalt, trackColor = EvidriloColors.Tint)
                }
            }
            projectsLoadError != null -> EvidriloTargetCard {
                Text("Let's recover your projects", style = MaterialTheme.typography.titleLarge)
                Text(projectsLoadError, color = EvidriloColors.Slate, style = MaterialTheme.typography.bodyMedium)
                EvidriloPrimaryButton("Try again", onRetryProjects)
            }
            current != null -> HomeContinueProject(current) { onResumeProject(current) }
            else -> Surface(shape = RoundedCornerShape(24.dp), color = EvidriloColors.Atmosphere) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EvidriloLogoMark(size = 72.dp)
                    Text("Your next chapter starts here.", style = MaterialTheme.typography.headlineSmall)
                    Text("Bring a question. Build the rest as you go.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    EvidriloPrimaryButton("Create a project", onCreateProject, trailingIcon = EvidriloIconName.PLUS)
                }
            }
        }
        if (!projectsLoading && projectsLoadError == null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${summary.totalCount} local ${if (summary.totalCount == 1) "project" else "projects"}", style = MaterialTheme.typography.titleMedium)
                    Text("${summary.activeCount} of ${summary.activeLimit} active", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                TextButton(onOpenProjects) { Text("View projects", color = EvidriloColors.Cobalt) }
            }
            if (current != null) EvidriloWorkspaceRow(EvidriloIconName.PLUS, "New project", "Start with your own question", onCreateProject)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Find your starting point", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onOpenProjectCatalog) { Text("Explore", color = EvidriloColors.Cobalt) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(projectTemplateFamilyOverviews, key = { it.family.id }) { overview ->
                    val family = overview.family
                    val design = projectTemplateFamilyCardDesign(family)
                    EvidriloPressableCard(onClick = { onSelectProjectFamily(family) }, modifier = Modifier.width(160.dp), faceColor = EvidriloColors.Atmosphere, borderColor = EvidriloColors.Atmosphere, lipColor = EvidriloColors.Tint) {
                        Box(Modifier.fillMaxWidth().height(82.dp), contentAlignment = Alignment.Center) {
                            EvidriloIcon(design.icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(40.dp))
                        }
                        Text(projectFamilyShortName(family), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
                        Text("Explore a structure", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate, modifier = Modifier.padding(14.dp))
                    }
                }
            }
        }
        EvidriloPracticeEntry(onOpenPractice)
        EvidriloWorkspaceRow(EvidriloIconName.EVIDENCE_GRAPH, "Explore a worked case", "Sources, evidence, and a next move · separate from your projects", { onNavigate(EvidriloTargetSection.SOURCES) })
        if (recommendation !is RecommendationUiState.Hidden) EvidriloRecommendationCard(recommendation, onAcceptRecommendation, onDismissRecommendation, onRetryRecommendation)
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun HomeContinueProject(project: StudentProjectDraft, onContinue: () -> Unit) {
    val progress = StudentProjectDraftRules.requiredFieldProgress(project)
    val enter = rememberGetStartedReveal(project.id, 650)
    Box(Modifier.fillMaxWidth()) {
        Surface(Modifier.matchParentSize().graphicsLayer { rotationZ = -2.5f; translationY = 5.dp.toPx() }, shape = RoundedCornerShape(24.dp), color = EvidriloColors.Tint) {}
        Surface(Modifier.fillMaxWidth().graphicsLayer { translationY = (1 - enter.value) * 10.dp.toPx() }, shape = RoundedCornerShape(24.dp), color = EvidriloColors.Card, shadowElevation = 3.dp) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EvidriloIcon(EvidriloIconName.FOLDER, tint = EvidriloColors.Cobalt, modifier = Modifier.size(27.dp))
                    Text("Pick up where you left off", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate, modifier = Modifier.weight(1f))
                    EvidriloLogoMark(size = 34.dp)
                }
                Text(project.title.ifBlank { "Untitled project" }, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${progress.filledRequired}/${progress.totalRequired} required fields · structure only", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                EvidriloPrimaryButton("Continue project", onContinue, trailingIcon = EvidriloIconName.ARROW_FORWARD)
            }
        }
    }
}

internal fun projectFamilyShortName(family: ProjectTemplateFamily): String = when (family) {
    ProjectTemplateFamily.EXPERIMENTAL_LABORATORY -> "Experiment"
    ProjectTemplateFamily.OBSERVATIONAL_SURVEY -> "Survey"
    ProjectTemplateFamily.LITERATURE_REVIEW -> "Literature"
    ProjectTemplateFamily.QUALITATIVE_INTERVIEW_FIELD_STUDY -> "Qualitative"
    ProjectTemplateFamily.DESIGN_ENGINEERING -> "Design"
}
