package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.StudentProjectDraft
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.domain.project.StudentProjectStatus
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
    recommendation: RecommendationUiState = RecommendationUiState.Hidden,
    onAcceptRecommendation: () -> Unit = {},
    onDismissRecommendation: () -> Unit = {},
    onRetryRecommendation: () -> Unit = {},
) {
    EvidriloTargetSurface(EvidriloTargetSection.HOME, onNavigate) {
        EvidriloContentColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            EvidriloBrandHeader(onSettings = onOpenSettings)

            storageNotice
                ?.takeIf { it.isError }
                ?.let { notice ->
                    Spacer(Modifier.height(14.dp))
                    EvidriloRecoveryNotice(notice = notice)
                }

            Spacer(Modifier.height(12.dp))
            HomeProjectsSection(
                projects = projects,
                isLoading = projectsLoading,
                loadError = projectsLoadError,
                activeProjectLimit = activeProjectLimit,
                onRetry = onRetryProjects,
                onOpenProjects = onOpenProjects,
                onCreateProject = onCreateProject,
                onResumeProject = onResumeProject,
            )

            Spacer(Modifier.height(16.dp))
            HomeProjectCatalogSection(
                onOpenCatalog = onOpenProjectCatalog,
                onSelectFamily = onSelectProjectFamily,
            )

            if (recommendation !is RecommendationUiState.Hidden) {
                Spacer(Modifier.height(24.dp))
                EvidriloRecommendationCard(
                    state = recommendation,
                    onAccept = onAcceptRecommendation,
                    onDismiss = onDismissRecommendation,
                    onRetry = onRetryRecommendation,
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
    activeProjectLimit: Int,
    onRetry: () -> Unit,
    onOpenProjects: () -> Unit,
    onCreateProject: () -> Unit,
    onResumeProject: (StudentProjectDraft) -> Unit,
) {
    val summary = homeProjectSummary(projects, activeProjectLimit)
    val primaryProject = projects
        .asSequence()
        .filter { StudentProjectDraftRules.countsTowardActiveLimit(it.status) }
        .maxByOrNull(StudentProjectDraft::updatedAtEpochMillis)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Projects", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            if (projects.isNotEmpty() && !isLoading && loadError == null) {
                TextButton(
                    onClick = onOpenProjects,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text("View all")
                }
            }
        }

        when {
            isLoading -> EvidriloTargetCard {
                Text("Loading projects…", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
            }
            loadError != null -> EvidriloTargetCard {
                Text("Projects couldn’t be loaded", style = MaterialTheme.typography.titleMedium)
                Text(loadError, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                EvidriloPrimaryButton(label = "Try again", onClick = onRetry)
            }
            else -> {
                HomeProjectSummaryCard(summary)
                when {
                    primaryProject != null -> EvidriloTargetCard {
                        Text(
                            primaryProject.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            primaryProject.templateSnapshot?.title ?: "Manual project",
                            style = MaterialTheme.typography.bodySmall,
                            color = EvidriloColors.Slate,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            requiredProjectProgressLabel(
                                StudentProjectDraftRules.requiredFieldProgress(primaryProject),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = EvidriloColors.Cobalt,
                        )
                        EvidriloPrimaryButton(
                            label = "Continue project",
                            onClick = { onResumeProject(primaryProject) },
                        )
                    }
                    summary.totalCount == 0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Start with a project", style = MaterialTheme.typography.titleMedium)
                        EvidriloPrimaryButton(label = "Create a project", onClick = onCreateProject)
                    }
                    else -> EvidriloTargetCard {
                        Text("No active projects", style = MaterialTheme.typography.titleMedium)
                        Text("Completed and archived work stays saved.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeProjectSummaryCard(summary: HomeProjectSummary) {
    EvidriloTargetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvidriloIcon(
                EvidriloIconName.FOLDER_FILLED,
                tint = EvidriloColors.Cobalt,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${summary.totalCount} ${if (summary.totalCount == 1) "project" else "projects"}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("On this device", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
        }
        Text(
            "${summary.activeCount} of ${summary.activeLimit} active · " +
                "${summary.completedCount} completed · ${summary.archivedCount} archived",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeProjectCatalogSection(
    onOpenCatalog: () -> Unit,
    onSelectFamily: (ProjectTemplateFamily) -> Unit,
) {
    val listState = rememberLazyListState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Explore project types",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(
                onClick = onOpenCatalog,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text("More")
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(projectTemplateFamilyOverviews, key = { it.family.id }) { overview ->
                HomeProjectFamilyPreviewCard(
                    overview = overview,
                    onClick = { onSelectFamily(overview.family) },
                )
            }
        }
    }
}

@Composable
private fun HomeProjectFamilyPreviewCard(
    overview: ProjectTemplateFamilyOverview,
    onClick: () -> Unit,
) {
    val design = projectTemplateFamilyCardDesign(overview.family)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(250.dp)
            .height(164.dp * fontScale)
            .semantics(mergeDescendants = true) {
                contentDescription = "${overview.family.displayName}. ${design.cue}. Open overview."
                role = Role.Button
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = EvidriloColors.PaleBlue,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EvidriloIcon(
                            design.icon,
                            tint = EvidriloColors.Cobalt,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    design.cue,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = EvidriloColors.Cobalt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                overview.family.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Open overview",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = EvidriloColors.Cobalt,
                )
                EvidriloIcon(
                    EvidriloIconName.CHEVRON_RIGHT,
                    tint = EvidriloColors.Cobalt,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
