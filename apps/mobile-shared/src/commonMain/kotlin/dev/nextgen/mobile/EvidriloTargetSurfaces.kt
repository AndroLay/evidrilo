package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.rotate
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
    showProfileItem: Boolean = selected == EvidriloTargetSection.PROFILE,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        EvidriloColors.White,
                        EvidriloColors.White,
                        EvidriloColors.Canvas,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            TargetAmbientBackdrop()
            content()
        }
        EvidriloTargetBottomNavigation(
            selected = selected,
            onNavigate = onNavigate,
            showProfileItem = showProfileItem,
        )
    }
}

@Composable
private fun TargetAmbientBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        EvidriloColors.White,
                        EvidriloColors.White,
                        EvidriloColors.Atmosphere,
                    ),
                ),
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = EvidriloColors.PatternBlue.copy(alpha = 0.12f),
                radius = size.width * 0.70f,
                center = Offset(size.width * 1.04f, size.height * 0.03f),
            )
            drawCircle(
                color = EvidriloColors.PatternCobalt.copy(alpha = 0.06f),
                radius = size.width * 0.48f,
                center = Offset(size.width * -0.08f, size.height * 0.92f),
            )
            val topRibbon = Path().apply {
                moveTo(size.width * 0.62f, 0f)
                cubicTo(
                    size.width * 0.83f,
                    size.height * 0.10f,
                    size.width * 0.94f,
                    size.height * 0.18f,
                    size.width * 1.08f,
                    size.height * 0.22f,
                )
                lineTo(size.width * 1.08f, size.height * 0.30f)
                cubicTo(
                    size.width * 0.90f,
                    size.height * 0.22f,
                    size.width * 0.78f,
                    size.height * 0.14f,
                    size.width * 0.62f,
                    size.height * 0.06f,
                )
                close()
            }
            drawPath(topRibbon, EvidriloColors.PatternBlue.copy(alpha = 0.10f))
        }
    }
}

@Composable
private fun EvidriloTargetBottomNavigation(
    selected: EvidriloTargetSection,
    onNavigate: (EvidriloTargetSection) -> Unit,
    showProfileItem: Boolean,
) {
    HorizontalDivider(color = EvidriloColors.Separator)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EvidriloColors.White)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        EvidriloTargetNavigationItem(
            label = "Home",
            icon = if (selected == EvidriloTargetSection.HOME) {
                EvidriloIconName.HOME_FILLED
            } else {
                EvidriloIconName.HOME
            },
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
            icon = EvidriloIconName.EVIDENCE_GRAPH,
            section = EvidriloTargetSection.EVIDENCE,
            selected = selected == EvidriloTargetSection.EVIDENCE,
            onClick = { onNavigate(EvidriloTargetSection.EVIDENCE) },
        )
        EvidriloTargetNavigationItem(
            label = "Action",
            icon = if (selected == EvidriloTargetSection.ACTION) {
                EvidriloIconName.CHECK_FILLED
            } else {
                EvidriloIconName.CHECK
            },
            section = EvidriloTargetSection.ACTION,
            selected = selected == EvidriloTargetSection.ACTION,
            onClick = { onNavigate(EvidriloTargetSection.ACTION) },
        )
        if (showProfileItem) {
            EvidriloTargetNavigationItem(
                label = "Profile",
                icon = if (selected == EvidriloTargetSection.PROFILE) {
                    EvidriloIconName.ACCOUNT_FILLED
                } else {
                    EvidriloIconName.ACCOUNT
                },
                section = EvidriloTargetSection.PROFILE,
                selected = selected == EvidriloTargetSection.PROFILE,
                onClick = { onNavigate(EvidriloTargetSection.PROFILE) },
            )
        }
    }
}

@Composable
private fun TargetCompactHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    centered: Boolean = true,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EvidriloIconButton(
            icon = EvidriloIconName.ARROW_BACK,
            contentDescription = "Back to $backLabel",
            onClick = onBack,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            textAlign = if (centered) {
                androidx.compose.ui.text.style.TextAlign.Center
            } else {
                androidx.compose.ui.text.style.TextAlign.Start
            },
        )
        if (onMore != null) {
            EvidriloIconButton(
                icon = EvidriloIconName.MORE,
                contentDescription = "More options for $title",
                onClick = onMore,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "More options unavailable for $title"
                        stateDescription = "Unavailable"
                    },
                contentAlignment = Alignment.Center,
            ) {
                EvidriloIcon(EvidriloIconName.MORE, tint = EvidriloColors.Outline)
            }
        }
    }
}

@Composable
private fun TargetBrandPageHeader(onSettings: (() -> Unit)? = null) {
    EvidriloBrandHeader(onSettings = onSettings)
}

@Composable
private fun TargetCobaltCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 18.dp,
    spacing: androidx.compose.ui.unit.Dp = 9.dp,
    backgroundStartColor: Color = EvidriloColors.CobaltPressed,
    waveAlpha: Float = 0.64f,
    ribbonAlpha: Float = 0.16f,
    waveOvalAlpha: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = EvidriloColors.Cobalt,
        contentColor = EvidriloColors.White,
    ) {
        Box {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            backgroundStartColor,
                            EvidriloColors.Cobalt,
                            EvidriloColors.CobaltBright,
                        ),
                        start = Offset(size.width * 0.08f, size.height),
                        end = Offset(size.width * 0.98f, 0f),
                    ),
                )
                val sweep = Path().apply {
                    moveTo(size.width * -0.12f, size.height * 0.54f)
                    cubicTo(
                        size.width * 0.20f,
                        size.height * 0.60f,
                        size.width * 0.56f,
                        size.height * 0.74f,
                        size.width * 0.88f,
                        size.height * 0.54f,
                    )
                    cubicTo(
                        size.width * 1.00f,
                        size.height * 0.48f,
                        size.width * 1.08f,
                        size.height * 0.36f,
                        size.width * 1.10f,
                        size.height * 0.28f,
                    )
                    lineTo(size.width * 1.10f, size.height * 0.78f)
                    cubicTo(
                        size.width * 0.72f,
                        size.height * 0.96f,
                        size.width * 0.32f,
                        size.height * 0.92f,
                        size.width * -0.12f,
                        size.height * 0.82f,
                    )
                    close()
                }
                drawPath(
                    path = sweep,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            EvidriloColors.DeepNavy.copy(alpha = waveAlpha),
                            EvidriloColors.CobaltPressed.copy(alpha = waveAlpha * 0.72f),
                        ),
                        start = Offset(size.width * 0.04f, size.height * 0.48f),
                        end = Offset(size.width * 0.96f, size.height * 0.92f),
                    ),
                )
                drawOval(
                    color = EvidriloColors.DeepNavy.copy(alpha = waveOvalAlpha),
                    topLeft = Offset(size.width * -0.26f, size.height * 0.50f),
                    size = Size(size.width * 1.52f, size.height * 0.60f),
                )
                val ribbon = Path().apply {
                    moveTo(size.width * 0.62f, 0f)
                    cubicTo(
                        size.width * 0.84f,
                        size.height * 0.12f,
                        size.width * 0.90f,
                        size.height * 0.25f,
                        size.width * 1.10f,
                        size.height * 0.34f,
                    )
                    lineTo(size.width * 1.10f, size.height)
                    lineTo(size.width * 0.94f, size.height)
                    cubicTo(
                        size.width * 0.74f,
                        size.height * 0.66f,
                        size.width * 0.74f,
                        size.height * 0.24f,
                        size.width * 0.50f,
                        0f,
                    )
                    close()
                }
                drawPath(ribbon, EvidriloColors.PatternBlue.copy(alpha = ribbonAlpha))
                drawCircle(
                    color = EvidriloColors.CobaltBright.copy(alpha = 0.24f),
                    radius = size.width * 0.46f,
                    center = Offset(size.width * 1.02f, size.height * 0.10f),
                )
            }
            Column(
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
        }
    }
}

@Composable
private fun TargetIconTile(
    icon: EvidriloIconName,
    tint: androidx.compose.ui.graphics.Color = EvidriloColors.Cobalt,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(16.dp),
        color = EvidriloColors.PaleBlue,
    ) {
        Box(contentAlignment = Alignment.Center) {
            EvidriloIcon(icon, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun TargetStatusChip(status: TargetEvidenceStatus) {
    val tone = when (status) {
        TargetEvidenceStatus.SUPPORTED -> EvidriloStatusTone.SUCCESS
        TargetEvidenceStatus.PARTIALLY_SUPPORTED -> EvidriloStatusTone.WARNING
        TargetEvidenceStatus.CANNOT_ASSESS,
        TargetEvidenceStatus.UNAVAILABLE,
        -> EvidriloStatusTone.ERROR
        TargetEvidenceStatus.NOT_ASSESSED -> EvidriloStatusTone.NEUTRAL
    }
    EvidriloStatusChip(label = status.targetDisplayLabel(), tone = tone)
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(vertical = 1.dp)
            .offset(y = EvidriloTargetLayout.NavigationVisualOffset),
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
            TargetBrandPageHeader(onSettings = onOpenSettings)
            Text(
                text = "What are you trying\nto finish?",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = "Turn your sources into evidence\nand make progress.",
                style = MaterialTheme.typography.bodyLarge.copy(color = EvidriloColors.Slate),
            )
            storageNotice
                ?.takeIf { it.isError }
                ?.let { notice -> EvidriloRecoveryNotice(notice = notice) }
            TargetProjectCard(case = case, metrics = metrics, onClick = onOpenWorkspace)
            TargetSectionRow(
                icon = EvidriloIconName.FOLDER_FILLED,
                title = "My Projects",
                subtitle = "One bundled workspace · local progress",
                onClick = onOpenWorkspace,
            )
            TargetSectionRow(
                icon = EvidriloIconName.LAYERS,
                title = "Explore Templates",
                subtitle = "Get a head start",
                onClick = onOpenGuide,
            )
            if (history != null) {
                TargetSectionRow(
                    icon = EvidriloIconName.HISTORY,
                    title = "Recent changes",
                    subtitle = targetHistorySubtitle(history),
                    onClick = onOpenHistory,
                )
            }
            if (recommendation !is RecommendationUiState.Hidden) {
                EvidriloRecommendationCard(
                    state = recommendation,
                    onAccept = onAcceptRecommendation,
                    onDismiss = onDismissRecommendation,
                    onRetry = onRetryRecommendation,
                )
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
internal fun EvidriloTargetSourcesScreen(
    case: ConclusionCase,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenWorkspace: () -> Unit,
    onStartPractice: () -> Unit,
) {
    var unavailableSource by remember { mutableStateOf<String?>(null) }
    EvidriloTargetSurface(EvidriloTargetSection.SOURCES, onNavigate) {
        EvidriloContentColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TargetBrandPageHeader()
            TargetPageIntro(
                title = "Give Evidrilo\nthe mess.",
                body = "We'll find what matters.",
                compact = true,
            )
            Text(
                "Upload your assignment brief, rubric and sources. Evidrilo will extract requirements, find the evidence, and show you what’s missing.",
                style = MaterialTheme.typography.bodySmall,
            )
            TargetSourcesGraphic()
            // The target Sources surface describes the three supported input
            // lanes. The bundled case remains the inspectable runtime fixture;
            // these rows are intentionally input affordances, not a second
            // projection of every fact in the case.
            TargetSourceRow(
                icon = EvidriloIconName.FILE,
                title = "Assignment Brief",
                subtitle = "PDF, DOC, or paste text",
                onClick = { unavailableSource = "Assignment Brief" },
            )
            TargetSourceRow(
                icon = EvidriloIconName.FILE,
                title = "Rubric",
                subtitle = "PDF, DOC, or image",
                onClick = { unavailableSource = "Rubric" },
            )
            TargetSourceRow(
                icon = EvidriloIconName.FILE,
                title = "Sources",
                subtitle = "PDF, links, notes, or files",
                onClick = { unavailableSource = "Sources" },
            )
            EvidriloPrimaryButton(label = "Map my work", onClick = onOpenWorkspace)
            Text(
                "Arbitrary document import is not configured in this submission slice.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    unavailableSource?.let { sourceTitle ->
        AlertDialog(
            onDismissRequest = { unavailableSource = null },
            title = { Text("$sourceTitle input is unavailable") },
            text = {
                Text(
                    "Arbitrary document ingestion is not configured in this offline submission slice. The bundled case remains available through Evidence and Workspace.",
                )
            },
            confirmButton = {
                TextButton(onClick = { unavailableSource = null }) {
                    Text("Got it")
                }
            },
        )
    }
}

@Composable
private fun TargetSourcesGraphic() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(EvidriloTargetLayout.SourcesGraphicHeight),
        shape = RoundedCornerShape(28.dp),
        color = EvidriloColors.PaleBlue,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val artworkScale = (maxHeight / 220.dp).coerceIn(0.72f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width * 0.50f
                val centerY = size.height * 0.78f
                drawCircle(
                    color = EvidriloColors.White.copy(alpha = 0.70f),
                    radius = size.width * 0.34f,
                    center = Offset(centerX, centerY),
                )
                drawCircle(
                    color = EvidriloColors.PatternBlue.copy(alpha = 0.46f),
                    radius = size.width * 0.43f,
                    center = Offset(centerX, centerY),
                )
                fun cubicPoint(
                    start: Offset,
                    controlA: Offset,
                    controlB: Offset,
                    end: Offset,
                    t: Float,
                ): Offset {
                    val inverse = 1f - t
                    return Offset(
                        x = (inverse * inverse * inverse * start.x) +
                            (3f * inverse * inverse * t * controlA.x) +
                            (3f * inverse * t * t * controlB.x) +
                            (t * t * t * end.x),
                        y = (inverse * inverse * inverse * start.y) +
                            (3f * inverse * inverse * t * controlA.y) +
                            (3f * inverse * t * t * controlB.y) +
                            (t * t * t * end.y),
                    )
                }

                fun drawDottedOrbit(
                    start: Offset,
                    controlA: Offset,
                    controlB: Offset,
                    end: Offset,
                ) {
                    val points = (0..24).map { index ->
                        cubicPoint(start, controlA, controlB, end, index / 24f)
                    }
                    for (index in 0 until 24 step 2) {
                        drawLine(
                            color = EvidriloColors.PatternCobalt,
                            start = points[index],
                            end = points[index + 1],
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }

                drawDottedOrbit(
                    start = Offset(size.width * 0.15f, size.height * 0.72f),
                    controlA = Offset(size.width * -0.02f, size.height * 0.76f),
                    controlB = Offset(size.width * 0.03f, size.height * 0.38f),
                    end = Offset(size.width * 0.34f, size.height * 0.42f),
                )
                drawDottedOrbit(
                    start = Offset(size.width * 0.85f, size.height * 0.72f),
                    controlA = Offset(size.width * 1.02f, size.height * 0.76f),
                    controlB = Offset(size.width * 0.97f, size.height * 0.38f),
                    end = Offset(size.width * 0.66f, size.height * 0.42f),
                )
                drawCircle(
                    color = EvidriloColors.Cobalt,
                    radius = 3.5.dp.toPx(),
                    center = Offset(size.width * 0.15f, size.height * 0.72f),
                )
                drawCircle(
                    color = EvidriloColors.Cobalt,
                    radius = 3.5.dp.toPx(),
                    center = Offset(size.width * 0.85f, size.height * 0.72f),
                )
                val arrowYStart = size.height * 0.41f
                val arrowYEnd = size.height * 0.58f
                drawLine(
                    color = EvidriloColors.Cobalt,
                    start = Offset(centerX, arrowYStart),
                    end = Offset(centerX, arrowYEnd),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = EvidriloColors.Cobalt,
                    start = Offset(centerX, arrowYEnd),
                    end = Offset(centerX - 5.dp.toPx(), arrowYEnd - 7.dp.toPx()),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = EvidriloColors.Cobalt,
                    start = Offset(centerX, arrowYEnd),
                    end = Offset(centerX + 5.dp.toPx(), arrowYEnd - 7.dp.toPx()),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            TargetDocumentBadge(
                label = "PDF",
                tint = EvidriloColors.Error,
                scale = artworkScale,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = maxWidth * 0.14f, y = 34.dp * artworkScale)
                    .rotate(-12f),
            )
            TargetDocumentBadge(
                label = "DOC",
                tint = EvidriloColors.Cobalt,
                scale = artworkScale,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 10.dp * artworkScale),
            )
            TargetDocumentBadge(
                label = "TXT",
                tint = EvidriloColors.Slate,
                scale = artworkScale,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = -(maxWidth * 0.14f), y = 34.dp * artworkScale)
                    .rotate(12f),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-16).dp * artworkScale)
                    .size(width = 190.dp * artworkScale, height = 94.dp * artworkScale),
                shape = RoundedCornerShape(28.dp),
                color = EvidriloColors.White.copy(alpha = 0.36f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(width = 168.dp * artworkScale, height = 72.dp * artworkScale),
                        shape = RoundedCornerShape(24.dp),
                        color = EvidriloColors.Cobalt,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            EvidriloLogoMark(
                                contentDescription = null,
                                modifier = Modifier.size(EvidriloTargetLayout.SourcesLogoSize * artworkScale),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetDocumentBadge(
    label: String,
    tint: Color,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(width = 82.dp * scale, height = 100.dp * scale),
        shape = RoundedCornerShape(20.dp * scale),
        color = EvidriloColors.White.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, EvidriloColors.White.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp * scale, vertical = 9.dp * scale),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                EvidriloIcon(
                    EvidriloIconName.FILE,
                    tint = tint.copy(alpha = 0.34f),
                    modifier = Modifier.size(17.dp * scale),
                )
            }
            Surface(
                modifier = Modifier.size(width = 54.dp * scale, height = 32.dp * scale),
                shape = RoundedCornerShape(8.dp * scale),
                color = if (label == "TXT") {
                    EvidriloColors.White
                } else {
                    tint
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * scale,
                            lineHeight = MaterialTheme.typography.labelLarge.lineHeight * scale,
                        ),
                        color = if (label == "TXT") tint else EvidriloColors.White,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(2) {
                    Surface(
                        modifier = Modifier.weight(1f).height(3.dp * scale),
                        shape = RoundedCornerShape(50),
                        color = tint.copy(alpha = 0.18f),
                    ) {}
                }
            }
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
    val gapSummary = when (metrics.gapCount) {
        null -> "support gap status not assessed"
        0 -> "no open support gaps"
        1 -> "1 open support gap"
        else -> "${metrics.gapCount} open support gaps"
    }
    EvidriloTargetSurface(EvidriloTargetSection.HOME, onNavigate) {
        EvidriloContentColumn {
            TargetBrandPageHeader()
            TargetCompactHeader(
                title = "My Projects",
                onBack = { onNavigate(EvidriloTargetSection.HOME) },
                backLabel = "Home",
                centered = false,
            )
            TargetPageIntro(
                title = case.title,
                body = "Evidence-backed local workspace",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EvidriloIcon(EvidriloIconName.CALENDAR, tint = EvidriloColors.Slate)
                Text("Due date not configured", style = MaterialTheme.typography.bodyMedium)
            }
            TargetProgressCard(metrics)
            TargetMetricStrip(metrics)
            TargetCoverageSection(
                title = "What's covered",
                onOpen = onOpenEvidence,
            ) {
                TargetCoverageRow(
                    number = "1",
                    title = "Case requirement",
                    status = metrics.evidenceStatus.targetDisplayLabel(),
                    tone = metrics.evidenceStatus.toStatusTone(),
                )
                TargetCoverageRow(
                    number = "2",
                    title = "Supplied observations",
                    status = "${metrics.selectedEvidenceCount}/${metrics.evidenceCount} selected",
                    tone = if (metrics.selectedEvidenceCount > 0) EvidriloStatusTone.INFO else EvidriloStatusTone.NEUTRAL,
                )
                TargetCoverageRow(
                    number = "3",
                    title = "Claim boundary",
                    status = if (draft.scope == null) "Not assessed" else "Visible",
                    tone = if (draft.scope == null) EvidriloStatusTone.NEUTRAL else EvidriloStatusTone.SUCCESS,
                )
                TargetCoverageRow(
                    number = "4",
                    title = "Next action",
                    status = if (draft.implication == null) "Not selected" else "Ready",
                    tone = if (draft.implication == null) EvidriloStatusTone.NEUTRAL else EvidriloStatusTone.INFO,
                )
            }
            TargetCoverageSection(
                title = "What needs attention",
                onOpen = onOpenAction,
            ) {
                TargetAttentionRow(
                    title = when {
                        metrics.gapCount == null -> "Complete the evidence review"
                        metrics.gapCount == 0 -> "No open support gap"
                        else -> "$gapSummary"
                    },
                    body = when {
                        metrics.gapCount == null -> "Review the supplied observations before treating the claim as supported."
                        metrics.gapCount == 0 -> "The bounded checks pass; keep the next action within the case boundary."
                        else -> "Review the claim boundary and choose a fact-linked next action."
                    },
                    tone = if (metrics.gapCount == 0) EvidriloStatusTone.SUCCESS else EvidriloStatusTone.WARNING,
                )
            }
            EvidriloPrimaryButton(label = "Continue workspace", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetEvidenceScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenEvidenceLens: () -> Unit,
    onOpenClaimTrace: () -> Unit,
    onStartPractice: () -> Unit,
) {
    val selectedEvidence = draft.evidenceRefs.toSet()
    val metrics = targetWorkspaceMetrics(case, draft)
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            TargetCompactHeader(
                title = "Evidence Map",
                onBack = { onNavigate(EvidriloTargetSection.HOME) },
                backLabel = "Home",
            )
            TargetPageIntro(
                title = "See what holds up.",
                body = "Explore how supplied evidence maps to your requirement — and spot what's still missing.",
                compact = true,
            )
            case.facts.firstOrNull { it.type == ConclusionFactType.AIM }?.let { requirement ->
                TargetRequirementCard(
                    fact = requirement,
                    status = metrics.evidenceStatus,
                    facts = case.facts.filter { it.type == ConclusionFactType.OBSERVATION },
                    selectedIds = selectedEvidence,
                    missingBody = if (metrics.evidenceStatus != TargetEvidenceStatus.SUPPORTED) {
                        when (metrics.evidenceStatus) {
                            TargetEvidenceStatus.NOT_ASSESSED ->
                                "Select supplied observations before treating the claim as supported."
                            TargetEvidenceStatus.PARTIALLY_SUPPORTED ->
                                "Review the evidence that is still missing for a fully bounded claim."
                            TargetEvidenceStatus.CANNOT_ASSESS ->
                                "The current claim cannot be assessed safely from the supplied case."
                            TargetEvidenceStatus.UNAVAILABLE ->
                                "A selected reference is not available in the active case version."
                            TargetEvidenceStatus.SUPPORTED -> null
                        }
                    } else {
                        null
                    },
                )
            }
            EvidriloPrimaryButton(label = "Find the missing evidence", onClick = onOpenClaimTrace)
            EvidriloSecondaryButton(label = "Open evidence lens", onClick = onOpenEvidenceLens)
        }
    }
}

@Composable
internal fun EvidriloTargetEvidenceLensScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onBack: () -> Unit,
    onOpenClaimTrace: () -> Unit,
) {
    val observations = case.facts.filter { it.type == ConclusionFactType.OBSERVATION }
    val selectedIds = draft.evidenceRefs.toSet()
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            TargetCompactHeader(
                title = "Evidence Lens",
                onBack = onBack,
                backLabel = "Evidence Map",
            )
            TargetPageIntro(
                title = "Look closely at the evidence.",
                body = "Inspect the supplied observations before deciding what your claim can support.",
                compact = true,
            )
            if (observations.isEmpty()) {
                TargetPageStatePanel(
                    state = TargetPageState.EMPTY,
                    title = "No supplied observations",
                    body = "This case has no observation facts available for the evidence lens.",
                )
            } else {
                observations.forEach { fact ->
                    TargetEvidenceLensCard(
                        fact = fact,
                        selected = fact.id in selectedIds,
                    )
                }
            }
            EvidriloTintPanel {
                Text("Bounded local evidence", style = MaterialTheme.typography.titleSmall)
                Text(
                    "This case provides stable fact IDs and supplied text. Page locators, extracted citations, and scientific-truth judgments are not available in the offline slice.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloPrimaryButton(label = "Trace this requirement", onClick = onOpenClaimTrace)
        }
    }
}

@Composable
private fun TargetEvidenceLensCard(
    fact: ConclusionFact,
    selected: Boolean,
) {
    EvidriloTargetCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TargetIconTile(
                icon = if (selected) EvidriloIconName.CHECK else EvidriloIconName.FILE,
                tint = if (selected) EvidriloColors.Success else EvidriloColors.Cobalt,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fact.displayLabel ?: "Supplied observation",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    EvidriloStatusChip(
                        label = if (selected) "Selected" else "Available",
                        tone = if (selected) EvidriloStatusTone.SUCCESS else EvidriloStatusTone.NEUTRAL,
                    )
                }
                Text(fact.id, style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
                Text(fact.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TargetMissingEvidenceCard(body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = EvidriloColors.WarningSurface,
        border = BorderStroke(1.dp, EvidriloColors.Warning.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TargetIconTile(icon = EvidriloIconName.ALERT, tint = EvidriloColors.Warning)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Missing evidence", style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Warning)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TargetPageStatePanel(
    state: TargetPageState,
    title: String,
    body: String,
    onRetry: (() -> Unit)? = null,
) {
    val tone = when (state) {
        TargetPageState.CONTENT -> EvidriloStatusTone.INFO
        TargetPageState.NOT_ASSESSED -> EvidriloStatusTone.NEUTRAL
        TargetPageState.LOADING -> EvidriloStatusTone.INFO
        TargetPageState.EMPTY -> EvidriloStatusTone.NEUTRAL
        TargetPageState.OFFLINE -> EvidriloStatusTone.WARNING
        TargetPageState.UNAVAILABLE -> EvidriloStatusTone.NEUTRAL
        TargetPageState.ERROR -> EvidriloStatusTone.ERROR
        TargetPageState.DISABLED -> EvidriloStatusTone.NEUTRAL
        TargetPageState.RECOVERY -> EvidriloStatusTone.WARNING
    }
    EvidriloTargetCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TargetIconTile(
                icon = when (state) {
                    TargetPageState.CONTENT -> EvidriloIconName.INFO
                    TargetPageState.NOT_ASSESSED -> EvidriloIconName.INFO
                    TargetPageState.LOADING -> EvidriloIconName.SPARK
                    TargetPageState.EMPTY -> EvidriloIconName.FILE
                    TargetPageState.OFFLINE -> EvidriloIconName.INFO
                    TargetPageState.UNAVAILABLE -> EvidriloIconName.INFO
                    TargetPageState.ERROR -> EvidriloIconName.ALERT
                    TargetPageState.DISABLED -> EvidriloIconName.SHIELD
                    TargetPageState.RECOVERY -> EvidriloIconName.SPARK
                },
                tint = when (tone) {
                    EvidriloStatusTone.WARNING -> EvidriloColors.Warning
                    EvidriloStatusTone.ERROR -> EvidriloColors.Error
                    EvidriloStatusTone.SUCCESS -> EvidriloColors.Success
                    else -> EvidriloColors.Cobalt
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    EvidriloStatusChip(label = state.targetDisplayLabel(), tone = tone)
                }
                Text(body, style = MaterialTheme.typography.bodyMedium)
                onRetry?.let {
                    EvidriloSecondaryButton(label = "Try again", onClick = it)
                }
            }
        }
    }
}

@Composable
internal fun EvidriloTargetActionScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation? = null,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenVerify: () -> Unit,
    onStartPractice: () -> Unit,
) {
    val metrics = targetWorkspaceMetrics(case, draft)
    val actionTitle = targetNextActionTitle(draft, metrics)
    val actionTrace = actionTraceFor(case, draft, evaluation)
    val actionReason = draft.implicationReason.ifBlank {
        actionTrace.reason.ifBlank {
            when {
                metrics.selectedEvidenceCount == 0 ->
                    "Select the supplied observations that directly help answer the case requirement."
                draft.claimText.isBlank() ->
                    "Write one claim that stays inside the observations you selected."
                draft.scope == null ->
                    "Define what the available evidence can and cannot support."
                else ->
                    "Complete the evidence workflow to connect a next action to the remaining gap."
            }
        }
    }
    EvidriloTargetSurface(EvidriloTargetSection.ACTION, onNavigate) {
        EvidriloContentColumn {
            TargetCompactHeader(
                title = "Action Plan",
                onBack = { onNavigate(EvidriloTargetSection.HOME) },
                backLabel = "Home",
            )
            TargetPageIntro(
                title = "You know what\nmatters next.",
                body = "Turn gaps into progress with a clear,\nevidence-linked plan.",
                compact = true,
            )
            TargetActionHero(
                title = actionTitle,
                reason = actionReason,
                metrics = metrics,
                trace = actionTrace,
                onOpenVerify = onOpenVerify,
            )
            Text("Next steps", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                TargetNextStepRow(
                    number = "2",
                    title = "Review the claim boundary",
                    subtitle = "Keep the claim inside the supplied observations",
                )
                TargetStepConnector()
                TargetNextStepRow(
                    number = "3",
                    title = "Check the evidence anchors",
                    subtitle = "Confirm each selected fact is still available",
                )
                TargetStepConnector()
                TargetNextStepRow(
                    number = "4",
                    title = "Revise and compare",
                    subtitle = "Keep the before/after change visible locally",
                )
            }
            EvidriloSecondaryButton(label = "Review the case", onClick = onStartPractice)
        }
    }
}

private fun EvidriloActionOrigin.actionOriginLabel(): String = when (this) {
    EvidriloActionOrigin.NONE -> "No action selected yet"
    EvidriloActionOrigin.LEARNER_SELECTED -> "Selected by you"
    EvidriloActionOrigin.SUGGESTED_BY_VERIFICATION -> "Suggested by verification"
}

@Composable
internal fun EvidriloTargetClaimTraceScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onBack: () -> Unit,
    onOpenClaimBoundary: () -> Unit,
    onOpenAction: () -> Unit,
    onStartPractice: () -> Unit,
) {
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            TargetCompactHeader(
                title = "Requirement Trace",
                onBack = onBack,
                backLabel = "Evidence Map",
            )
            TargetPageIntro(
                title = "Trace the claim.",
                body = "See why this requirement exists and\nwhere the support comes from.",
            )
            val metrics = targetWorkspaceMetrics(case, draft)
            case.facts.firstOrNull { it.type == ConclusionFactType.AIM }?.let { requirement ->
                TargetRequirementCard(fact = requirement, status = metrics.evidenceStatus)
            }
            TargetTraceInfoCard(
                title = "Why this is required",
                body = "This requirement is part of the supplied case brief. Evidrilo keeps the requirement, evidence anchors, and claim boundary visible together.",
                icon = EvidriloIconName.FILE,
            )
            TargetSupportingEvidenceCard(case = case, draft = draft)
            EvidriloTintPanel {
                Text("Offline source locator", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The competition case is bundled locally. Document page locators are unavailable, so Evidrilo shows stable fact IDs and supplied text instead of inventing citations.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloPrimaryButton(label = "Review claim boundary", onClick = onOpenClaimBoundary)
            EvidriloSecondaryButton(label = "Continue to action plan", onClick = onOpenAction)
            EvidriloSecondaryButton(label = "Open claim review", onClick = onStartPractice)
        }
    }
}

@Composable
internal fun EvidriloTargetClaimBoundaryScreen(
    case: ConclusionCase,
    draft: ConclusionDraft,
    evaluation: ConclusionEvaluation?,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onBack: () -> Unit,
    onOpenVerify: () -> Unit,
    onOpenAction: () -> Unit,
    onStartPractice: () -> Unit,
) {
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn {
            TargetCompactHeader(
                title = "Claim Boundary",
                onBack = onBack,
                backLabel = "Requirement Trace",
            )
            TargetPageIntro(
                title = "Keep the claim inside the case.",
                body = "Review the learner-authored claim, scope, limitations, and anchors before verification.",
                compact = true,
            )
            if (evaluation == null) {
                TargetPageStatePanel(
                    state = TargetPageState.NOT_ASSESSED,
                    title = "Not assessed yet",
                    body = "Complete the local claim review to compare the boundary with the supplied observations.",
                )
                EvidriloTintPanel {
                    Text("Current draft boundary", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Claim: ${draft.claimText.ifBlank { "Not written" }}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Scope: ${draft.scope?.targetDisplayLabel() ?: "Not selected"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Limitation anchors: ${draft.limitationRefs.ifEmpty { listOf("None selected") }.joinToString()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EvidriloPrimaryButton(label = "Open claim review", onClick = onStartPractice)
            } else {
                EvidriloClaimBoundaryCard(case = case, draft = draft, evaluation = evaluation)
                EvidriloPrimaryButton(label = "Verify this claim", onClick = onOpenVerify)
                EvidriloSecondaryButton(label = "Continue to action plan", onClick = onOpenAction)
            }
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
            TargetCompactHeader(title = "Verify Claim", onBack = onBack, backLabel = "Action Plan")
            TargetPageIntro(
                title = "Does the claim stay inside the evidence?",
                body = "Verification reports the reducer's deterministic checks. It does not generate a replacement conclusion.",
            )
            if (evaluation == null) {
                EvidriloTintPanel {
                    Text("Not assessed yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Complete the local evidence draft to see anchored feedback for this claim.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EvidriloPrimaryButton(label = "Open claim review", onClick = onStartPractice)
            } else {
                EvidriloClaimBoundaryCard(case = case, draft = draft, evaluation = evaluation)
                EvidriloVerificationDetailCard(evaluation = evaluation)
                if (canRevise) {
                    EvidriloPrimaryButton(label = "Revise once", onClick = onRevise)
                } else {
                    EvidriloPrimaryButton(label = "Open claim review", onClick = onStartPractice)
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
            TargetCompactHeader(title = "What Changed?", onBack = onBack, backLabel = "Action Plan")
            TargetPageIntro(
                title = "See what changed.",
                body = "One revision is kept beside the original so you can inspect the change without losing learner authorship.",
            )
            EvidriloEvidenceDeltaCard(
                before = before,
                after = after,
                title = "Before feedback → after one revision",
                case = case,
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
    var notificationsUnavailable by remember { mutableStateOf(false) }
    EvidriloTargetSurface(EvidriloTargetSection.PROFILE, onNavigate) {
        EvidriloContentColumn {
            TargetBrandPageHeader()
            TargetPageIntro(
                title = "Your academic\nsystem.",
                body = "Manage your profile, workspace\nsettings, and Evidrilo Pro.",
            )
            TargetProfileSummaryCard(profileSubtitle = profileSubtitle, history = history, onClick = onOpenAccount)
            TargetCobaltCard(
                modifier = Modifier
                    .clickable(onClick = onOpenPremium)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Open Evidrilo Pro premium cases"
                        role = Role.Button
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TargetIconTile(icon = EvidriloIconName.CROWN, tint = EvidriloColors.White)
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Evidrilo Pro", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.White)
                        Text("Unlock deeper evidence cases.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.White)
                        Text("From evidence to action.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.White.copy(alpha = 0.82f))
                    }
                    EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.White)
                }
            }
            TargetSettingsRow(
                icon = EvidriloIconName.SETTINGS,
                title = "Workspace preferences",
                subtitle = "Customize your workspace",
                onClick = onOpenSettings,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.BELL,
                title = "Notifications",
                subtitle = "Off · not configured in this offline slice",
                onClick = { notificationsUnavailable = true },
            )
            TargetSettingsRow(
                icon = EvidriloIconName.DATABASE,
                title = "Export & backup",
                subtitle = "Account export when signed in",
                onClick = onOpenAccount,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.SHIELD,
                title = "Privacy",
                subtitle = "Your data, your control",
                onClick = onOpenSettings,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.LAYERS,
                title = "Premium cases",
                subtitle = "Two additional cases · monthly/yearly access",
                onClick = onOpenPremium,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.QUESTION,
                title = "Local history",
                subtitle = "Review changes on this device",
                onClick = onOpenHistory,
            )
        }
    }
    if (notificationsUnavailable) {
        AlertDialog(
            onDismissRequest = { notificationsUnavailable = false },
            title = { Text("Notifications are off") },
            text = {
                Text(
                    "Evidrilo does not request notification permission or schedule reminders in this offline submission slice. Your local workflow remains available without notifications.",
                )
            },
            confirmButton = {
                TextButton(onClick = { notificationsUnavailable = false }) {
                    Text("Got it")
                }
            },
        )
    }
}

@Composable
private fun TargetSettingsRow(
    icon: EvidriloIconName,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
                role = Role.Button
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TargetIconTile(icon = icon, size = 42.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
        }
    }
}

@Composable
internal fun EvidriloTargetHistoryScreen(
    history: ConclusionSessionSnapshot?,
    storageNotice: LocalStorageNotice?,
    onStartPractice: () -> Unit,
    onClear: () -> Unit,
    onOpenDelta: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
    selectedSection: EvidriloTargetSection,
    backLabel: String = "Home",
) {
    var confirmClear by remember { mutableStateOf(false) }
    val summary = targetHistorySummary(history)
    EvidriloTargetSurface(selected = selectedSection, onNavigate = onNavigate) {
        EvidriloContentColumn {
            TargetBrandPageHeader()
            TargetPageIntro(
                title = "Track what changed.",
                body = "Follow your progress, revisions,\nand resolved evidence gaps.",
            )
            storageNotice
                ?.takeIf { it.isError }
                ?.let { notice -> EvidriloRecoveryNotice(notice = notice) }
            TargetCobaltCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TargetIconTile(icon = EvidriloIconName.HISTORY, tint = EvidriloColors.White)
                    Text("This workspace", modifier = Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleLarge, color = EvidriloColors.White)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TargetMetric(summary.evidenceRemoved.toString(), "gaps reviewed", EvidriloColors.White)
                    TargetMetric(summary.evidenceAdded.toString(), "evidence added", EvidriloColors.White)
                    TargetMetric(summary.actionsChanged.toString(), "actions changed", EvidriloColors.White)
                }
            }
            if (history == null) {
                EvidriloTargetCard {
                    Text("No comparison saved yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Complete the free evidence workflow and one revision to create a local before/after record.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EvidriloPrimaryButton(label = "Start evidence review", onClick = onStartPractice)
            } else {
                Text("Latest local comparison", style = MaterialTheme.typography.titleLarge)
                TargetHistoryEventRow(
                    icon = EvidriloIconName.LAYERS,
                    title = "Evidence relationship updated",
                    body = "${summary.evidenceAdded} added · ${summary.evidenceRemoved} removed",
                )
                TargetHistoryEventRow(
                    icon = EvidriloIconName.CHECKLIST,
                    title = "Claim revision saved",
                    body = "The learner-authored before/after state is available locally.",
                )
                TargetHistoryEventRow(
                    icon = EvidriloIconName.SHIELD,
                    title = "Boundary kept visible",
                    body = "History does not turn a bounded case into a scientific-truth score.",
                )
                EvidriloPrimaryButton(label = "View evidence delta", onClick = onOpenDelta)
                EvidriloSecondaryButton(label = "Clear local comparison", onClick = { confirmClear = true })
            }
            EvidriloBackButton(label = backLabel, onClick = onBack)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear local comparison?") },
            text = {
                Text(
                    "This removes the saved before/after comparison from this device. It does not delete an account or change the bundled case.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) {
                    Text("Clear comparison")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Keep it")
                }
            },
        )
    }
}

@Composable
private fun TargetHistoryEventRow(
    icon: EvidriloIconName,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(16.dp), color = EvidriloColors.PaleBlue) {
            Box(contentAlignment = Alignment.Center) {
                EvidriloIcon(icon, tint = EvidriloColors.Cobalt)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        EvidriloIcon(EvidriloIconName.MORE, tint = EvidriloColors.Slate)
    }
}

@Composable
internal fun TargetPageIntro(
    title: String,
    body: String,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
        )
        Text(body, style = MaterialTheme.typography.bodyLarge.copy(color = EvidriloColors.Slate))
    }
}

@Composable
private fun TargetSourceRow(
    icon: EvidriloIconName,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
                role = Role.Button
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TargetIconTile(icon = icon, size = 38.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(50),
                color = EvidriloColors.PaleBlue,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EvidriloIcon(EvidriloIconName.PLUS, tint = EvidriloColors.Cobalt)
                }
            }
        }
    }
}

@Composable
private fun TargetRequirementCard(
    fact: ConclusionFact,
    status: TargetEvidenceStatus,
    facts: List<ConclusionFact> = emptyList(),
    selectedIds: Set<String> = emptySet(),
    missingBody: String? = null,
) {
    EvidriloTargetCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TargetIconTile(icon = EvidriloIconName.FILE)
            Text("Requirement 1", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TargetStatusChip(status)
        }
        Text(fact.text, style = MaterialTheme.typography.bodyLarge)
        if (facts.isNotEmpty()) {
            TargetEvidenceRail(facts = facts, selectedIds = selectedIds)
        }
        missingBody?.let { body ->
            TargetMissingEvidenceCard(body = body)
        }
    }
}

@Composable
private fun TargetEvidenceRail(
    facts: List<ConclusionFact>,
    selectedIds: Set<String>,
) {
    val rowHeight = 64.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((rowHeight.value * facts.size).dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val railX = 24.dp.toPx()
            val segmentHeight = rowHeight.toPx()
            for (index in 0 until facts.lastIndex) {
                val color = when (index) {
                    0 -> EvidriloColors.Cobalt
                    1 -> Color(0xFF1399B1)
                    else -> EvidriloColors.Success
                }
                drawLine(
                    color = color.copy(alpha = 0.78f),
                    start = Offset(railX, segmentHeight * index + segmentHeight / 2f),
                    end = Offset(railX, segmentHeight * (index + 1) + segmentHeight / 2f),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            facts.forEachIndexed { index, _ ->
                val nodeColor = when (index) {
                    0 -> EvidriloColors.Cobalt
                    1 -> Color(0xFF1399B1)
                    2 -> EvidriloColors.Success
                    else -> EvidriloColors.Warning
                }
                val centerY = segmentHeight * index + segmentHeight / 2f
                drawLine(
                    color = nodeColor,
                    start = Offset(railX, centerY),
                    end = Offset(48.dp.toPx(), centerY),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = EvidriloColors.White,
                    radius = 9.dp.toPx(),
                    center = Offset(railX, centerY),
                )
                drawCircle(
                    color = nodeColor,
                    radius = 9.dp.toPx(),
                    center = Offset(railX, centerY),
                    style = Stroke(width = 2.6.dp.toPx()),
                )
            }
        }
        Column {
            facts.forEachIndexed { index, fact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(48.dp))
                    TargetEvidenceRailCard(
                        fact = fact,
                        selected = fact.id in selectedIds,
                        index = index,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TargetEvidenceRailCard(
    fact: ConclusionFact,
    selected: Boolean,
    index: Int,
) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(50),
                color = if (selected) EvidriloColors.SuccessSurface else EvidriloColors.PaleBlue,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EvidriloIcon(
                        if (selected) EvidriloIconName.CHECK else EvidriloIconName.LINK,
                        tint = if (selected) EvidriloColors.Success else EvidriloColors.Cobalt,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    fact.displayLabel ?: "Observation ${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    fact.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(50),
                color = EvidriloColors.Surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EvidriloIcon(EvidriloIconName.MORE, tint = EvidriloColors.Ink, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun TargetTraceInfoCard(
    title: String,
    body: String,
    icon: EvidriloIconName,
) {
    EvidriloTargetCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            TargetIconTile(icon = icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
        }
    }
}

@Composable
private fun TargetSupportingEvidenceCard(
    case: ConclusionCase,
    draft: ConclusionDraft,
) {
    EvidriloTargetCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TargetIconTile(icon = EvidriloIconName.LINK)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Supporting evidence", style = MaterialTheme.typography.titleLarge)
                Text("Supplied observations connected to this requirement.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        case.facts
            .filter { it.type == ConclusionFactType.OBSERVATION }
            .forEach { fact ->
                TargetTraceEvidenceRow(
                    fact = fact,
                    selected = fact.id in draft.evidenceRefs,
                )
            }
    }
}

@Composable
private fun TargetTraceEvidenceRow(
    fact: ConclusionFact,
    selected: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TargetIconTile(
            icon = EvidriloIconName.FILE,
            tint = if (selected) EvidriloColors.Cobalt else EvidriloColors.Slate,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(fact.displayLabel ?: "Observation", style = MaterialTheme.typography.titleMedium)
            Text(fact.id, style = MaterialTheme.typography.labelMedium)
        }
        EvidriloStatusChip(
            label = if (selected) "Anchored" else "Available",
            tone = if (selected) EvidriloStatusTone.SUCCESS else EvidriloStatusTone.NEUTRAL,
        )
    }
}

@Composable
private fun TargetActionHero(
    title: String,
    reason: String,
    metrics: TargetWorkspaceMetrics,
    trace: EvidriloActionTrace,
    onOpenVerify: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TargetIconTile(icon = EvidriloIconName.LIGHTNING, size = 42.dp)
                Text("Next action", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Cobalt)
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(reason, style = MaterialTheme.typography.bodyMedium)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = EvidriloColors.Surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TargetIconTile(icon = EvidriloIconName.EVIDENCE_GRAPH, size = 34.dp)
                    Text("${metrics.selectedEvidenceCount} supported", style = MaterialTheme.typography.bodyMedium)
                    Text("·", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    Text(
                        "${metrics.evidenceCount - metrics.selectedEvidenceCount} missing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EvidriloColors.Warning,
                    )
                }
            }
            trace.anchor?.let { anchor ->
                Text("Anchored to ${anchor.factId}", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
            }
            if (trace.anchorState == EvidriloActionAnchorState.MISSING) {
                Text(
                    "This action is stale because its limitation anchor is not selected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Error,
                )
            }
            EvidriloPrimaryButton(label = "Start this action", onClick = onOpenVerify)
        }
    }
}

@Composable
private fun TargetStepConnector() {
    Box(
        modifier = Modifier
            .padding(start = 17.dp)
            .size(width = 2.dp, height = 18.dp)
            .background(EvidriloColors.Cobalt.copy(alpha = 0.55f)),
    )
}

@Composable
private fun TargetNextStepRow(
    number: String,
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(50),
            color = EvidriloColors.PaleBlue,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Cobalt)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
    }
}

@Composable
private fun TargetCoverageSection(
    title: String,
    onOpen: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onOpen)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "View all $title"
                            role = Role.Button
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "View all",
                        style = MaterialTheme.typography.labelLarge,
                        color = EvidriloColors.Cobalt,
                    )
                    EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Cobalt)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

@Composable
private fun TargetCoverageRow(
    number: String,
    title: String,
    status: String,
    tone: EvidriloStatusTone,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(34.dp), shape = RoundedCornerShape(50), color = EvidriloColors.PaleBlue) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.titleSmall, color = EvidriloColors.Ink)
            }
        }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        EvidriloStatusChip(label = status, tone = tone)
        EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
    }
}

@Composable
private fun TargetAttentionRow(
    title: String,
    body: String,
    tone: EvidriloStatusTone,
) {
    val container = when (tone) {
        EvidriloStatusTone.SUCCESS -> EvidriloColors.SuccessSurface
        EvidriloStatusTone.WARNING -> EvidriloColors.WarningSurface
        EvidriloStatusTone.ERROR -> EvidriloColors.ErrorSurface
        EvidriloStatusTone.INFO -> EvidriloColors.Tint
        EvidriloStatusTone.NEUTRAL -> EvidriloColors.Surface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TargetIconTile(
                icon = when (tone) {
                    EvidriloStatusTone.SUCCESS -> EvidriloIconName.CHECK
                    EvidriloStatusTone.WARNING,
                    EvidriloStatusTone.ERROR,
                    -> EvidriloIconName.ALERT
                    EvidriloStatusTone.INFO -> EvidriloIconName.LINK
                    EvidriloStatusTone.NEUTRAL -> EvidriloIconName.INFO
                },
                tint = when (tone) {
                    EvidriloStatusTone.SUCCESS -> EvidriloColors.Success
                    EvidriloStatusTone.WARNING -> EvidriloColors.Warning
                    EvidriloStatusTone.ERROR -> EvidriloColors.Error
                    EvidriloStatusTone.INFO -> EvidriloColors.Cobalt
                    EvidriloStatusTone.NEUTRAL -> EvidriloColors.Slate
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TargetProfileSummaryCard(
    profileSubtitle: String,
    history: ConclusionSessionSnapshot?,
    onClick: () -> Unit,
) {
    val summary = targetHistorySummary(history)
    EvidriloTargetCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open local learner profile"
                role = Role.Button
            },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(50), color = EvidriloColors.CobaltBright) {
                Box(contentAlignment = Alignment.Center) {
                    Text("L", style = MaterialTheme.typography.headlineSmall, color = EvidriloColors.White)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Local learner", style = MaterialTheme.typography.titleLarge)
                Text(profileSubtitle, style = MaterialTheme.typography.bodyMedium)
            }
            EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
        }
        EvidriloDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TargetProfileMetric(
                icon = EvidriloIconName.FILE,
                value = "${summary.evidenceAdded + summary.evidenceRemoved}",
                label = "evidence changes",
            )
            TargetProfileMetric(
                icon = EvidriloIconName.FOLDER,
                value = "1",
                label = "active workspace",
            )
        }
    }
}

@Composable
private fun RowScope.TargetProfileMetric(icon: EvidriloIconName, value: String, label: String) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(12.dp),
                color = EvidriloColors.PaleBlue,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EvidriloIcon(icon, tint = EvidriloColors.Cobalt)
                }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun TargetEvidenceStatus.toStatusTone(): EvidriloStatusTone = when (this) {
    TargetEvidenceStatus.SUPPORTED -> EvidriloStatusTone.SUCCESS
    TargetEvidenceStatus.PARTIALLY_SUPPORTED -> EvidriloStatusTone.WARNING
    TargetEvidenceStatus.CANNOT_ASSESS,
    TargetEvidenceStatus.UNAVAILABLE,
    -> EvidriloStatusTone.ERROR
    TargetEvidenceStatus.NOT_ASSESSED -> EvidriloStatusTone.NEUTRAL
}

@Composable
private fun TargetProjectCard(
    case: ConclusionCase,
    metrics: TargetWorkspaceMetrics,
    onClick: () -> Unit,
) {
    val gapValue = metrics.gapCount?.toString() ?: "—"
    TargetCobaltCard(
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Open project ${case.title}"
                role = Role.Button
            },
        padding = 18.dp,
        spacing = 7.dp,
        backgroundStartColor = EvidriloColors.Cobalt,
        waveAlpha = 0f,
        ribbonAlpha = 0.09f,
        waveOvalAlpha = 0f,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvidriloIcon(EvidriloIconName.FILE, tint = EvidriloColors.White)
            Text(
                "Current project",
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = EvidriloColors.White.copy(alpha = 0.90f),
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(end = 62.dp)) {
                Text(case.title, style = MaterialTheme.typography.headlineMedium, color = EvidriloColors.White)
                Text(
                    case.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EvidriloColors.White.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp),
                shape = RoundedCornerShape(50),
                color = EvidriloColors.White.copy(alpha = 0.16f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = EvidriloColors.White)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EvidriloIcon(EvidriloIconName.CALENDAR, tint = EvidriloColors.White.copy(alpha = 0.88f))
            Text(
                "Due date not configured",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.White.copy(alpha = 0.86f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetHomeMetricTile(
                icon = EvidriloIconName.FILE,
                value = metrics.evidenceCount.toString(),
                label = "Evidence",
            )
            TargetHomeMetricTile(
                icon = EvidriloIconName.ALERT,
                value = gapValue,
                label = "Gap",
                valueColor = if (metrics.gapCount == null) EvidriloColors.Warning else EvidriloColors.Warning,
            )
            TargetHomeMetricTile(
                icon = EvidriloIconName.ARROW_FORWARD,
                value = metrics.actionCount.toString(),
                label = "Actions",
            )
        }
    }
}

@Composable
private fun RowScope.TargetHomeMetricTile(
    icon: EvidriloIconName,
    value: String,
    label: String,
    valueColor: Color = EvidriloColors.Ink,
) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        color = EvidriloColors.White.copy(alpha = 0.94f),
    ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
                Spacer(Modifier.weight(1f))
                EvidriloIcon(
                    icon,
                    tint = if (label == "Gap") EvidriloColors.Warning else EvidriloColors.Cobalt,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Ink)
        }
    }
}

@Composable
private fun TargetProgressCard(metrics: TargetWorkspaceMetrics) {
    EvidriloTargetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Project progress", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("${metrics.draftCompletenessPercent}%", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Cobalt)
            EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
        }
        EvidriloProgressBar(metrics.draftCompletenessPercent / 100f)
    }
}

@Composable
private fun TargetMetricStrip(metrics: TargetWorkspaceMetrics) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TargetMetricCard(EvidriloIconName.LIST, "${metrics.requirementCount}", "requirements")
        TargetMetricCard(EvidriloIconName.FILE,
            "${metrics.selectedEvidenceCount}/${metrics.evidenceCount}",
            "evidence items",
        )
        TargetMetricCard(EvidriloIconName.ARROW_FORWARD, "${metrics.actionCount}", "next actions")
    }
}

@Composable
private fun RowScope.TargetMetricCard(icon: EvidriloIconName, value: String, label: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = EvidriloColors.PaleBlue,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EvidriloIcon(icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(19.dp))
                    }
                }
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(label, style = MaterialTheme.typography.bodySmall)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
                role = Role.Button
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(16.dp),
                color = EvidriloColors.PaleBlue,
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

private fun targetNextActionTitle(
    draft: ConclusionDraft,
    metrics: TargetWorkspaceMetrics,
): String = when {
    draft.implication != null -> draft.implication.targetActionLabel()
    metrics.selectedEvidenceCount == 0 -> "Map the supplied evidence"
    draft.claimText.isBlank() -> "Write a bounded claim"
    draft.scope == null -> "Set the claim boundary"
    draft.limitationRefs.isEmpty() && draft.limitationNote.isBlank() -> "State the case limitation"
    else -> "Choose a next action"
}
