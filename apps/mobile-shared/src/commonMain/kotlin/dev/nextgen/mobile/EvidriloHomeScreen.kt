package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
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
import dev.nextgen.mobile.storage.createProjectSectionBookmarkStore
import androidx.compose.runtime.remember

internal data class HomeProjectSummary(
    val totalCount: Int,
    val activeCount: Int,
    val completedCount: Int,
    val archivedCount: Int,
    val activeLimit: Int,
) {
    val remainingActiveSlots: Int get() = (activeLimit - totalCount).coerceAtLeast(0)
}

internal fun homeProjectSummary(
    projects: List<StudentProjectDraft>,
    activeLimit: Int,
): HomeProjectSummary {
    val safeLimit = activeLimit.coerceAtLeast(1)
    val visibleProjects = projects.filter { it.status != StudentProjectStatus.TRASHED }
    return HomeProjectSummary(
        totalCount = projects.size,
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
    onOpenCases: () -> Unit = {},
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
        EvidriloPageHeading("Keep your ideas moving.")
        when {
            projectsLoading -> Surface(shape = RoundedCornerShape(22.dp), color = EvidriloColors.Atmosphere) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(uiText("Opening your workspace…","Membuka ruang kerjamu…"), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = EvidriloColors.Cobalt, trackColor = EvidriloColors.Tint)
                }
            }
            projectsLoadError != null -> EvidriloTargetCard {
                Text(uiText("Let's recover your projects","Pulihkan proyekmu"), style = MaterialTheme.typography.titleLarge)
                Text(projectsLoadError, color = EvidriloColors.Slate, style = MaterialTheme.typography.bodyMedium)
                EvidriloPrimaryButton("Try again", onRetryProjects)
            }
            current != null -> HomeContinueProject(current) { onResumeProject(current) }
            else -> Surface(shape = RoundedCornerShape(24.dp), color = EvidriloColors.Atmosphere) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    EvidriloLogoMark(size = 72.dp)
                    Text(uiText("Your next chapter starts here.","Mulai langkah baru di sini."), style = MaterialTheme.typography.headlineSmall)
                    Text(uiText("Bring a question. Build the rest as you go.","Mulai dengan pertanyaan. Kembangkan selangkah demi selangkah."), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    EvidriloPrimaryButton("Create a project", onCreateProject, trailingIcon = EvidriloIconName.PLUS)
                }
            }
        }
        if (!projectsLoading && projectsLoadError == null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(uiText("${summary.totalCount} local ${if (summary.totalCount == 1) "project" else "projects"}", "${summary.totalCount} proyek lokal"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("${summary.totalCount} of ${summary.activeLimit} project spaces", "${summary.totalCount} dari ${summary.activeLimit} ruang proyek"), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                TextButton(onOpenProjects) { Text(uiText("View projects","Lihat proyek"), color = EvidriloColors.Cobalt) }
            }
            if (current != null) EvidriloWorkspaceRow(EvidriloIconName.PLUS, uiText("New project"), uiText("Start with your own question"), onCreateProject)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Find your starting point","Temukan titik awalmu"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onOpenProjectCatalog) { Text(uiText("Explore"), color = EvidriloColors.Cobalt) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(projectTemplateFamilyOverviews, key = { it.family.id }) { overview ->
                    val family = overview.family
                    val design = projectTemplateFamilyCardDesign(family)
                    EvidriloHomeCatalogCover(family,design.cue) {onSelectProjectFamily(family)}
                }
            }
        }
        EvidriloPracticeEntry(onOpenPractice)
        EvidriloCasesEntry(onOpenCases)
        if (recommendation !is RecommendationUiState.Hidden) EvidriloRecommendationCard(recommendation, onAcceptRecommendation, onDismissRecommendation, onRetryRecommendation)
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun HomeContinueProject(project: StudentProjectDraft, onContinue: () -> Unit) {
    val progress = StudentProjectDraftRules.requiredFieldProgress(project)
    val bookmarkStore = remember { createProjectSectionBookmarkStore() }
    val bookmarkLoad = remember(project.id, project.revision) { bookmarkStore.read(projectSectionBookmarkKey(project)) }
    val sections = studentProjectEditorSections(project)
    val resumeSection = sections.firstOrNull { it.navigationId == bookmarkLoad.value } ?: sections.first()
    val nextTask = projectWorkSuggestions(project).firstOrNull()
    val enter = rememberGetStartedReveal(project.id, 650)
    Box(Modifier.fillMaxWidth()) {
        Surface(Modifier.matchParentSize().graphicsLayer { rotationZ = -2.5f; translationY = 5.dp.toPx() }, shape = RoundedCornerShape(24.dp), color = EvidriloColors.Tint) {}
        Surface(Modifier.fillMaxWidth().graphicsLayer { translationY = (1 - enter.value) * 10.dp.toPx() }, shape = RoundedCornerShape(24.dp), color = EvidriloColors.Card, shadowElevation = 3.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EvidriloIcon(EvidriloIconName.FOLDER, tint = EvidriloColors.Cobalt, modifier = Modifier.size(27.dp))
                    Text(uiText("Your current project","Proyek yang sedang dikerjakan"), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate, modifier = Modifier.weight(1f))
                    EvidriloLogoMark(size = 34.dp)
                }
                RawText(project.title.ifBlank { "Untitled project" }, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(uiText(resumeSection.title), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Cobalt)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(progress = { if (progress.totalRequired == 0) 0f else progress.filledRequired.toFloat()/progress.totalRequired }, modifier = Modifier.weight(1f).height(6.dp), color = EvidriloColors.Cobalt, trackColor = EvidriloColors.Tint)
                    Text(uiText("${progress.filledRequired}/${progress.totalRequired} responses", "${progress.filledRequired}/${progress.totalRequired} isian"), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                nextTask?.takeIf { it.sectionId != resumeSection.navigationId }?.let {
                    Text(uiText("Next: ","Berikutnya: ")+uiText(it.title), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
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
