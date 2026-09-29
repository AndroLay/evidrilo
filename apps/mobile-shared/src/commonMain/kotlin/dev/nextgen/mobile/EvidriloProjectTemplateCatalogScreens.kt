package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.ProjectTemplateDefinition
import dev.nextgen.mobile.domain.project.ProjectTemplateCatalog
import dev.nextgen.mobile.domain.project.ProjectTemplateCatalogSnapshot
import dev.nextgen.mobile.domain.project.ProjectTemplateExample
import dev.nextgen.mobile.domain.project.ProjectTemplateExampleKind
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.project.TemplateSelectionResult
import dev.nextgen.mobile.domain.project.ProjectAiScaffoldProposal
import dev.nextgen.mobile.projectcatalog.ProjectTemplateCatalogGatewayResult
import dev.nextgen.mobile.projectcatalog.ProjectTemplateCatalogUnavailableReason
import dev.nextgen.mobile.projectcatalog.ProjectTemplateRemoteFamily
import dev.nextgen.mobile.projectcatalog.ProjectTemplateSummary

internal sealed interface ProjectTemplateRemoteUiState<out T> {
    data object NotRequested : ProjectTemplateRemoteUiState<Nothing>
    data object Loading : ProjectTemplateRemoteUiState<Nothing>
    data class Loaded<T>(val value: T) : ProjectTemplateRemoteUiState<T>
    data class Unavailable(val reason: ProjectTemplateCatalogUnavailableReason) : ProjectTemplateRemoteUiState<Nothing>
    data class Failed(val code: String, val retryable: Boolean) : ProjectTemplateRemoteUiState<Nothing>
}

internal const val projectTemplateCatalogBrowseInstructions =
    "Swipe sideways or choose a type. Screen readers move focus through cards and activate one."

internal fun <T> ProjectTemplateCatalogGatewayResult<T>.toRemoteUiState(): ProjectTemplateRemoteUiState<T> = when (this) {
    is ProjectTemplateCatalogGatewayResult.Loaded -> ProjectTemplateRemoteUiState.Loaded(value)
    is ProjectTemplateCatalogGatewayResult.Unavailable -> ProjectTemplateRemoteUiState.Unavailable(reason)
    is ProjectTemplateCatalogGatewayResult.Failed -> ProjectTemplateRemoteUiState.Failed(code, retryable)
}

internal data class ProjectTemplateFamilyOverview(
    val family: ProjectTemplateFamily,
    val summary: String,
    val selectionCue: String,
    val whenItMayFit: String,
    val workToOrganize: List<String>,
    val pointsToCheck: List<String>,
)

internal data class ProjectTemplateFamilyCardDesign(
    val icon: EvidriloIconName,
    val cue: String,
)

internal fun projectTemplateFamilyCardDesign(family: ProjectTemplateFamily): ProjectTemplateFamilyCardDesign =
    when (family) {
        ProjectTemplateFamily.EXPERIMENTAL_LABORATORY -> ProjectTemplateFamilyCardDesign(
            EvidriloIconName.LIGHTNING,
            "Change and measure",
        )
        ProjectTemplateFamily.OBSERVATIONAL_SURVEY -> ProjectTemplateFamilyCardDesign(
            EvidriloIconName.LIST,
            "Observe patterns",
        )
        ProjectTemplateFamily.LITERATURE_REVIEW -> ProjectTemplateFamilyCardDesign(
            EvidriloIconName.BOOK,
            "Compare sources",
        )
        ProjectTemplateFamily.QUALITATIVE_INTERVIEW_FIELD_STUDY -> ProjectTemplateFamilyCardDesign(
            EvidriloIconName.CHAT_BUBBLE,
            "Hear perspectives",
        )
        ProjectTemplateFamily.DESIGN_ENGINEERING -> ProjectTemplateFamilyCardDesign(
            EvidriloIconName.CHECKLIST,
            "Build and test",
        )
    }

internal val projectTemplateFamilyOverviews = listOf(
    ProjectTemplateFamilyOverview(
        family = ProjectTemplateFamily.EXPERIMENTAL_LABORATORY,
        summary = "Investigate how a planned change relates to a measured outcome.",
        selectionCue = "You change a condition and measure what happens.",
        whenItMayFit = "Your assignment asks you to change a condition and observe a measurable result.",
        workToOrganize = listOf(
            "Frame the question and identify what will change and what will be measured.",
            "Describe the procedure, comparison conditions, and any repeated trials.",
            "Record observations, measurement context, and limitations.",
        ),
        pointsToCheck = listOf(
            "A single measurement or uncontrolled conditions limit what can be concluded.",
            "A difference in results does not by itself prove what caused the difference.",
        ),
    ),
    ProjectTemplateFamilyOverview(
        family = ProjectTemplateFamily.OBSERVATIONAL_SURVEY,
        summary = "Study patterns in existing conditions or responses without assigning an intervention.",
        selectionCue = "You observe existing patterns or collect survey responses without assigning a treatment.",
        whenItMayFit = "You observe people, events, or measurements as they already occur, or collect survey responses.",
        workToOrganize = listOf(
            "Define the question, population, sample, and variables or survey topics.",
            "Record how observations or responses were collected and who may be missing.",
            "Summarize patterns while keeping the data and analysis traceable.",
        ),
        pointsToCheck = listOf(
            "An association between variables does not establish cause and effect.",
            "Sampling, non-response, and question wording can affect what the results represent.",
        ),
    ),
    ProjectTemplateFamilyOverview(
        family = ProjectTemplateFamily.LITERATURE_REVIEW,
        summary = "Answer a focused question by comparing and synthesizing existing sources.",
        selectionCue = "You compare and synthesize research or other existing sources.",
        whenItMayFit = "Your task asks what existing research or publications say about a defined topic.",
        workToOrganize = listOf(
            "Set the review question, scope, and how sources will be found and selected.",
            "Record source details and why each source belongs in the review.",
            "Compare findings, methods, limitations, and points of disagreement.",
        ),
        pointsToCheck = listOf(
            "A list of sources is not yet a synthesis; explain how the sources relate.",
            "Source quality and relevance require judgment beyond citation completeness.",
        ),
    ),
    ProjectTemplateFamilyOverview(
        family = ProjectTemplateFamily.QUALITATIVE_INTERVIEW_FIELD_STUDY,
        summary = "Explore experiences, meanings, or settings through interviews or field observations.",
        selectionCue = "You explore people's experiences or a setting through interviews or field observation.",
        whenItMayFit = "You need to understand how people describe an experience or how something happens in context.",
        workToOrganize = listOf(
            "Describe the research question, setting, participant context, and collection approach.",
            "Protect privacy and follow the consent and ethics requirements for the assignment.",
            "Connect interpretations and themes to contextualized notes or quotations.",
        ),
        pointsToCheck = listOf(
            "Interpretations should preserve context and consider evidence that does not fit a theme.",
            "This overview cannot provide participant consent or replace an ethics review.",
        ),
    ),
    ProjectTemplateFamilyOverview(
        family = ProjectTemplateFamily.DESIGN_ENGINEERING,
        summary = "Develop or test a solution against stated user, design, or technical requirements.",
        selectionCue = "You design or build a solution and test it against stated criteria.",
        whenItMayFit = "Your assignment asks you to design, build, compare, or evaluate a solution.",
        workToOrganize = listOf(
            "State the need, intended users, requirements, and constraints.",
            "Record design decisions, prototypes, test criteria, and observed results.",
            "Compare results with the criteria and explain trade-offs and limitations.",
        ),
        pointsToCheck = listOf(
            "Meeting a test criterion does not alone establish broad usability or real-world impact.",
            "User relevance and suitability may need feedback from the people affected.",
        ),
    ),
)

internal fun projectTemplateFamilyOverview(family: ProjectTemplateFamily): ProjectTemplateFamilyOverview =
    projectTemplateFamilyOverviews.single { it.family == family }

@Composable
internal fun EvidriloProjectTemplateCatalogScreen(
    listState: LazyListState,
    quickGuideExpanded: Boolean,
    remoteFamilies: ProjectTemplateRemoteUiState<List<ProjectTemplateRemoteFamily>>,
    onRetryRemoteFamilies: () -> Unit,
    onToggleQuickGuide: () -> Unit,
    onBack: () -> Unit,
    onOpenProjects: () -> Unit,
    onStartBlankProject: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onSelectFamily: (ProjectTemplateFamily) -> Unit,
) {
    var showCatalogHelp by remember { mutableStateOf(false) }
    if (showCatalogHelp) {
        AlertDialog(
            onDismissRequest = { showCatalogHelp = false },
            title = { Text("How to use this catalog") },
            text = {
                Text(
                    "$projectTemplateCatalogBrowseInstructions An overview explains a project type; it does not choose for you.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showCatalogHelp = false }) { Text("Got it") }
            },
        )
    }

    EvidriloTargetSurface(selected = EvidriloTargetSection.HOME, onNavigate = onNavigate) {
        EvidriloContentColumn {
            EvidriloBrandHeader(onSettings = null)
            EvidriloBackButton(label = "Home", onClick = onBack)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Explore project types",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                )
                IconButton(
                    onClick = { showCatalogHelp = true },
                    modifier = Modifier.semantics {
                        contentDescription = "How to use this catalog"
                    },
                ) {
                    EvidriloIcon(EvidriloIconName.QUESTION, tint = EvidriloColors.Cobalt)
                }
            }
            Text(
                "Choose an overview based on your assignment.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
            ProjectTemplateCatalogStatus(remoteFamilies, onRetryRemoteFamilies)
            EvidriloPrimaryButton(label = "Start a blank project", onClick = onStartBlankProject)
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projectTemplateFamilyOverviews, key = { it.family.id }) { overview ->
                    ProjectTemplateFamilyCard(
                        overview = overview,
                        selectableTemplateCount = (remoteFamilies as? ProjectTemplateRemoteUiState.Loaded)
                            ?.value?.singleOrNull { it.family == overview.family }?.selectableTemplateCount,
                        onClick = { onSelectFamily(overview.family) },
                    )
                }
            }
            ProjectFamilyQuickGuide(
                expanded = quickGuideExpanded,
                onToggle = onToggleQuickGuide,
                onOpenFamily = onSelectFamily,
            )
            EvidriloSecondaryButton(label = "My projects", onClick = onOpenProjects)
        }
    }
}

@Composable
internal fun ProjectTemplateFamilyCard(
    overview: ProjectTemplateFamilyOverview,
    selectableTemplateCount: Int?,
    onClick: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cardDesign = projectTemplateFamilyCardDesign(overview.family)
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(250.dp)
            .height(224.dp * fontScale)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(overview.family.displayName)
                    append(". ")
                    append(overview.summary)
                    append(". ")
                    append(cardDesign.cue)
                    selectableTemplateCount?.let { append(" $it published templates available to inspect.") }
                    append(" Open overview.")
                }
                role = Role.Button
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = EvidriloColors.PaleBlue,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EvidriloIcon(
                            cardDesign.icon,
                            tint = EvidriloColors.Cobalt,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    cardDesign.cue,
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
            Text(
                overview.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            selectableTemplateCount?.let { count ->
                Text(
                    "$count published ${if (count == 1) "template" else "templates"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = EvidriloColors.Cobalt,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Open overview",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = EvidriloColors.Cobalt,
                )
                EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Cobalt, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun EvidriloProjectTemplateFamilyScreen(
    overview: ProjectTemplateFamilyOverview,
    remoteTemplates: ProjectTemplateRemoteUiState<List<ProjectTemplateSummary>>,
    onRetryRemoteTemplates: () -> Unit,
    onInspectTemplate: (ProjectTemplateSummary) -> Unit,
    onStartBlankProject: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
) {
    EvidriloTargetSurface(selected = EvidriloTargetSection.HOME, onNavigate = onNavigate) {
        EvidriloContentColumn {
            EvidriloBrandHeader(onSettings = null)
            EvidriloBackButton(label = "Project types", onClick = onBack)
            Text(overview.family.displayName, style = MaterialTheme.typography.headlineLarge)
            Text(
                overview.summary,
                style = MaterialTheme.typography.bodyLarge,
                color = EvidriloColors.Slate,
            )
            EvidriloTargetCard {
                Text("Overview only", style = MaterialTheme.typography.titleLarge)
                Text(
                    "This guidance is general. Published templates, when available, are listed separately and include their own scope and limitations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.Slate,
                )
            }
            EvidriloPrimaryButton(label = "Start a blank project", onClick = onStartBlankProject)
            ProjectTemplateFamilySection(
                title = "When this may fit",
                description = overview.whenItMayFit,
            )
            ProjectTemplateFamilySection(
                title = "What you may organize",
                items = overview.workToOrganize,
            )
            ProjectTemplateFamilySection(
                title = "Points to check",
                items = overview.pointsToCheck,
            )
            PublishedTemplateList(
                family = overview.family,
                state = remoteTemplates,
                onRetry = onRetryRemoteTemplates,
                onInspect = onInspectTemplate,
            )
            EvidriloTargetCard {
                Text("Evaluation boundary", style = MaterialTheme.typography.titleLarge)
                Text(
                    "This is a general orientation, not a method-specific evaluation or academic grade. The app should assess only explicit criteria it has been designed and reviewed to assess; ambiguous method, ethics, and evidence-quality decisions may need an educator or domain reviewer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.Slate,
                )
            }
            EvidriloSecondaryButton(label = "Browse other project types", onClick = onBack)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun EvidriloProjectTemplateDetailScreen(
    templateSummary: ProjectTemplateSummary?,
    state: ProjectTemplateRemoteUiState<ProjectTemplateDefinition>,
    notice: String?,
    projectAiAccountKey: String?,
    projectAiState: ProjectAiScaffoldUiState,
    projectAiConsentState: ProjectAiConsentUiState,
    onRetry: () -> Unit,
    onRefreshProjectAiConsent: () -> Unit,
    onGrantProjectAiConsent: () -> Unit,
    onRevokeProjectAiConsent: () -> Unit,
    onStartProject: (ProjectTemplateDefinition) -> Unit,
    onStartBlankProject: () -> Unit,
    onRequestProjectAi: (ProjectTemplateDefinition, String?, String, String?, Map<String, String>, Int?, Boolean) -> Unit,
    onCreateProjectWithAi: (ProjectTemplateDefinition, String, ProjectAiScaffoldProposal, Set<String>, Map<String, String>, String, Int) -> Unit,
    onDiscardProjectAiPreview: (String, Int) -> Unit,
    onRetryProjectAiSettlement: (ProjectAiScaffoldUiState.SettlementFailed) -> Unit,
    onBack: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
) {
    EvidriloTargetSurface(selected = EvidriloTargetSection.HOME, onNavigate = onNavigate) {
        EvidriloContentColumn {
            EvidriloBrandHeader(onSettings = null)
            EvidriloBackButton(label = "Template list", onClick = onBack)
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
            Text(
                (state as? ProjectTemplateRemoteUiState.Loaded)?.value?.title
                    ?: templateSummary?.title
                    ?: "Template details",
                style = MaterialTheme.typography.headlineLarge,
            )
            when (state) {
                ProjectTemplateRemoteUiState.NotRequested -> ProjectTemplateRemoteMessage(
                    title = "Template not selected",
                    message = "Return to the project-type overview and select a published template to inspect.",
                )

                ProjectTemplateRemoteUiState.Loading -> ProjectTemplateRemoteLoading("Loading published template…")

        is ProjectTemplateRemoteUiState.Unavailable -> ProjectTemplateRemoteMessage(
            title = "Template details unavailable",
            message = unavailableCatalogMessage(state.reason),
            retryable = state.reason == ProjectTemplateCatalogUnavailableReason.OFFLINE,
            onRetry = onRetry,
        )

                is ProjectTemplateRemoteUiState.Failed -> ProjectTemplateRemoteMessage(
                    title = "Template details could not be verified",
                    message = "The local project-type overview remains available. This template was not opened.",
                    retryable = state.retryable,
                    onRetry = onRetry,
                )

                is ProjectTemplateRemoteUiState.Loaded -> {
                    val canStartProject = isProjectTemplateSelectable(state.value)
                    ProjectTemplateDetails(state.value, canStartProject)
                    if (canStartProject) {
                        EvidriloProjectAiScaffoldPanel(
                            template = state.value,
                            currentFieldValues = emptyMap(),
                            existingProjectId = null,
                        existingProjectRevision = null,
                        projectTitle = state.value.title,
                        projectAiAccountKey = projectAiAccountKey,
                        state = projectAiState,
                            consentState = projectAiConsentState,
                            onRefreshConsent = onRefreshProjectAiConsent,
                            onGrantConsent = onGrantProjectAiConsent,
                            onRevokeConsent = onRevokeProjectAiConsent,
                            onRequest = { projectId, brief, question, fields, revision, projectDataConsent ->
                                onRequestProjectAi(
                                    state.value,
                                    projectId,
                                    brief,
                                    question,
                                    fields,
                                    revision,
                                    projectDataConsent,
                                )
                            },
                            onCreateProject = { title, proposal, selected, edited, requestId, creditCost ->
                                onCreateProjectWithAi(state.value, title, proposal, selected, edited, requestId, creditCost)
                            },
                            onApplyToProject = { _, _, _, _, _, _ -> null },
                            onDiscardPreview = onDiscardProjectAiPreview,
                            onRetrySettlement = onRetryProjectAiSettlement,
                        )
                        EvidriloPrimaryButton(
                            label = "Start a local project",
                            onClick = { onStartProject(state.value) },
                        )
                    } else {
                        EvidriloTargetCard {
                            Text("Not available for a new project", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "This legacy template can still be inspected, but its examples are not classified and reviewed for both normal and edge scenarios. It cannot start a new project or an AI scaffold.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EvidriloColors.Slate,
                            )
                        }
                    }
                }
            }
            EvidriloSecondaryButton(
                label = "Create a blank project instead",
                onClick = onStartBlankProject,
            )
        }
    }
}

@Composable
private fun ProjectTemplateDetails(template: ProjectTemplateDefinition, canStartProject: Boolean) {
    EvidriloTargetCard {
        Text("Published template · version ${template.version}", style = MaterialTheme.typography.titleMedium)
        Text(template.summary, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        Text("Intended output", style = MaterialTheme.typography.titleMedium)
        Text(template.intendedOutput, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Publication indicates the server's release checks passed; it does not establish that this method fits your assignment. Confirm uncertain choices with your instructor or a relevant reviewer.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
    }
    ProjectTemplateFamilySection(
        title = "Inputs",
        items = template.inputFields.map { field ->
            "${field.label} · ${if (field.required) "required" else "optional"}"
        },
    )
    ProjectTemplateFamilySection(
        title = "Steps",
        items = template.steps.map { step ->
            if (step.inputFieldIds.isEmpty()) step.title else
                "${step.title} · ${step.inputFieldIds.joinToString { id -> template.inputFields.single { it.id == id }.label }}"
        },
    )
    ProjectTemplateFamilySection("Method-specific limitations", items = template.methodSpecificLimitations)
    ProjectTemplateFamilySection("Provenance to record", items = template.provenanceRequirements)
    ProjectTemplateFamilySection("Accessibility expectations", items = template.accessibilityExpectations)
    ProjectTemplateFamilySection(
        title = "Example review metadata",
        items = template.examples.map { example ->
            "${example.scenarioLabel()}: ${example.summary} ${if (example.reviewed) "Publisher marks this example reviewed." else "This example is not marked reviewed."}"
        },
    )
    EvidriloTargetCard {
        Text(
            if (canStartProject) "Starting a project" else "Inspecting a legacy template",
            style = MaterialTheme.typography.titleMedium,
        )
        if (canStartProject) {
            Text(
                "Manual start creates a local draft from this exact template version. Your draft stays on this device; it is not uploaded, synced, or graded.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
        } else {
            Text(
                "This published version is preserved for reading existing work. Its scenario-review metadata is incomplete for creating new work.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
        }
    }
}

internal fun isProjectTemplateSelectable(template: ProjectTemplateDefinition): Boolean =
    ProjectTemplateCatalog.select(
        ProjectTemplateCatalogSnapshot(schemaVersion = 1, templates = listOf(template)),
        templateId = template.id,
        expectedVersion = template.version,
    ) is TemplateSelectionResult.Selected

internal fun ProjectTemplateExample.scenarioLabel(): String = when (kind) {
    ProjectTemplateExampleKind.UNSPECIFIED -> "Scenario type not recorded in this legacy template"
    ProjectTemplateExampleKind.NORMAL -> "Normal workflow (not a correctness label)"
    ProjectTemplateExampleKind.EDGE_OR_CONFLICTING -> "Edge or conflicting scenario"
}

@Composable
private fun PublishedTemplateList(
    family: ProjectTemplateFamily,
    state: ProjectTemplateRemoteUiState<List<ProjectTemplateSummary>>,
    onRetry: () -> Unit,
    onInspect: (ProjectTemplateSummary) -> Unit,
) {
    when (state) {
        ProjectTemplateRemoteUiState.NotRequested -> Unit
        ProjectTemplateRemoteUiState.Loading -> ProjectTemplateRemoteLoading("Checking published templates…")
        is ProjectTemplateRemoteUiState.Unavailable -> ProjectTemplateRemoteMessage(
            title = "Published templates unavailable",
            message = unavailableCatalogMessage(state.reason),
            retryable = state.reason == ProjectTemplateCatalogUnavailableReason.OFFLINE,
            onRetry = onRetry,
        )
        is ProjectTemplateRemoteUiState.Failed -> ProjectTemplateRemoteMessage(
            title = "Published templates could not be verified",
            message = "The overview above remains available. No template has been assumed available.",
            retryable = state.retryable,
            onRetry = onRetry,
        )
        is ProjectTemplateRemoteUiState.Loaded -> {
            if (state.value.isEmpty()) {
                ProjectTemplateRemoteMessage(
                    title = "No published templates yet",
                    message = "This project type has an overview, but no published template is available to inspect yet.",
                )
            } else {
                Text("Published templates", style = MaterialTheme.typography.titleLarge)
                state.value.forEach { summary ->
                    Card(
                        onClick = { onInspect(summary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${summary.title}. Version ${summary.version}. ${summary.summary} Inspect template details."
                                role = Role.Button
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
                        border = BorderStroke(1.dp, EvidriloColors.Separator),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(summary.title, style = MaterialTheme.typography.titleMedium)
                            Text("Version ${summary.version} · ${family.displayName}", style = MaterialTheme.typography.labelMedium)
                            Text(summary.summary, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                            Text("Inspect template details  ›", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectTemplateCatalogStatus(
    state: ProjectTemplateRemoteUiState<List<ProjectTemplateRemoteFamily>>,
    onRetry: () -> Unit,
) {
    when (state) {
        ProjectTemplateRemoteUiState.NotRequested -> Unit
        ProjectTemplateRemoteUiState.Loading -> ProjectTemplateRemoteLoading("Checking published templates…")
        is ProjectTemplateRemoteUiState.Unavailable -> ProjectTemplateRemoteMessage(
            title = when (state.reason) {
                ProjectTemplateCatalogUnavailableReason.NOT_CONFIGURED -> "No online templates"
                ProjectTemplateCatalogUnavailableReason.OFFLINE -> "You’re offline"
            },
            message = when (state.reason) {
                ProjectTemplateCatalogUnavailableReason.NOT_CONFIGURED -> "You can still start a blank project."
                ProjectTemplateCatalogUnavailableReason.OFFLINE -> "These overviews remain available."
            },
            retryable = state.reason == ProjectTemplateCatalogUnavailableReason.OFFLINE,
            onRetry = onRetry,
        )
        is ProjectTemplateRemoteUiState.Failed -> ProjectTemplateRemoteMessage(
            title = "Couldn’t check templates",
            message = "Family overviews remain available.",
            retryable = state.retryable,
            onRetry = onRetry,
        )
        is ProjectTemplateRemoteUiState.Loaded -> {
            val count = state.value.sumOf(ProjectTemplateRemoteFamily::selectableTemplateCount)
            ProjectTemplateRemoteMessage(
                title = if (count == 0) "No published templates yet" else "$count ${if (count == 1) "template" else "templates"} to inspect",
                message = if (count == 0) {
                    "Browse an overview or start blank."
                } else {
                    "Select a type to view them."
                },
            )
        }
    }
}

@Composable
private fun ProjectTemplateRemoteLoading(message: String) {
    EvidriloTargetCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ProjectTemplateRemoteMessage(
    title: String,
    message: String,
    retryable: Boolean = false,
    onRetry: () -> Unit = {},
) {
    EvidriloTargetCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        if (retryable) EvidriloSecondaryButton(label = "Retry", onClick = onRetry)
    }
}

private fun unavailableCatalogMessage(reason: ProjectTemplateCatalogUnavailableReason): String = when (reason) {
    ProjectTemplateCatalogUnavailableReason.NOT_CONFIGURED ->
        "Online templates are not configured in this build. The project-type guidance remains available offline."
    ProjectTemplateCatalogUnavailableReason.OFFLINE ->
        "You are offline. The project-type guidance remains available; retry when connected."
}

@Composable
private fun ProjectFamilyQuickGuide(
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenFamily: (ProjectTemplateFamily) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Not sure which type?",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(
            onClick = onToggle,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(if (expanded) "Hide" else "Help me choose")
        }
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            projectTemplateFamilyOverviews.forEach { overview ->
                ProjectFamilyQuickChoice(
                    overview = overview,
                    onClick = { onOpenFamily(overview.family) },
                )
            }
        }
    }
}

@Composable
private fun ProjectFamilyQuickChoice(
    overview: ProjectTemplateFamilyOverview,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${overview.selectionCue} Open ${overview.family.displayName} overview."
                role = Role.Button
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvidriloIcon(
                projectTemplateFamilyCardDesign(overview.family).icon,
                tint = EvidriloColors.Cobalt,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    overview.family.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    projectTemplateFamilyCardDesign(overview.family).cue,
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Cobalt, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ProjectTemplateFamilySection(
    title: String,
    description: String? = null,
    items: List<String> = emptyList(),
) {
    EvidriloTargetCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Cobalt)
                Text(
                    item,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.Slate,
                )
            }
        }
    }
}
