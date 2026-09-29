package dev.nextgen.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED
import dev.nextgen.mobile.billing.REVENUECAT_PRO_FEATURE_ENABLED
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
            .background(EvidriloColors.Canvas),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            TargetAmbientBackdrop()
            content()
        }
        if (shouldShowTargetBottomNavigation(selected)) {
            EvidriloTargetBottomNavigation(
                selected = selected,
                onNavigate = onNavigate,
            )
        }
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
                        EvidriloColors.Canvas,
                        EvidriloColors.Canvas,
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        color = EvidriloColors.Card,
        shadowElevation = 4.dp,
    ) {
    Column {
        HorizontalDivider(color = EvidriloColors.Separator)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 5.dp),
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
        }
    }
}
}

@Composable
private fun TargetCompactHeader(
    title: String,
    onBack: () -> Unit,
    backLabel: String,
    centered: Boolean = true,
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
    }
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
        color = EvidriloColors.PrimaryAction,
        contentColor = EvidriloColors.White,
    ) {
        Box {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            backgroundStartColor,
                            EvidriloColors.PrimaryAction,
                            EvidriloColors.CobaltPressed,
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
    // Rounded, friendly icon tile with a soft blue wash — the recurring
    // "chip" that anchors every list row and card header.
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(18.dp),
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
    val pillColor by animateColorAsState(
        targetValue = if (selected) EvidriloColors.PaleBlue else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "navPill",
    )
    val contentTint by animateColorAsState(
        targetValue = if (selected) EvidriloColors.Cobalt else EvidriloColors.Slate,
        animationSpec = tween(durationMillis = 220),
        label = "navTint",
    )
    val iconLift by animateDpAsState(
        targetValue = if (selected) (-2).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navLift",
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(50))
                .background(pillColor),
            contentAlignment = Alignment.Center,
        ) {
            EvidriloIcon(
                name = icon,
                tint = contentTint,
                modifier = Modifier.size(23.dp).offset(y = iconLift),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentTint,
        )
    }
}

@Composable
internal fun EvidriloTargetSourcesScreen(
    case: ConclusionCase,
    remoteContentStatus: String,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenWorkspace: () -> Unit,
    onStartPractice: () -> Unit,
    onOpenProjects: () -> Unit = onOpenWorkspace,
) {
    EvidriloTargetSurface(EvidriloTargetSection.SOURCES, onNavigate) {
        EvidriloContentColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            includeBottomSafeArea = false,
        ) {
            TargetPageIntro(
                title = "Start with your\nmaterials.",
                body = "Organize an assignment, criteria, and source notes inside a project. You decide what becomes a finding.",
                compact = true,
            )
            Text(
                EvidriloSourcesCopy.intro,
                style = MaterialTheme.typography.bodySmall,
            )
            TargetSourcesGraphic()
            EvidriloTintPanel {
                Text("CASE CONTENT", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
                Text(case.title, style = MaterialTheme.typography.titleMedium)
                Text(remoteContentStatus, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "This example stays separate from your projects and remains available offline.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TargetSourceRow(
                icon = EvidriloIconName.FILE,
                title = "Assignment Brief",
                subtitle = EvidriloSourcesCopy.assignmentBriefSubtitle,
            )
            TargetSourceRow(
                icon = EvidriloIconName.FILE,
                title = "Rubric",
                subtitle = EvidriloSourcesCopy.rubricSubtitle,
            )
            TargetSourceRow(
                icon = EvidriloIconName.FILE,
                title = "Sources",
                subtitle = EvidriloSourcesCopy.sourcesSubtitle,
            )
            EvidriloPrimaryButton(label = "Open My Projects", onClick = onOpenProjects)
            EvidriloSecondaryButton(label = "Explore the bundled case", onClick = onOpenWorkspace)
            Text(
                EvidriloSourcesCopy.importBoundary,
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
        }
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
                label = "AIM",
                tint = EvidriloColors.Error,
                scale = artworkScale,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = maxWidth * 0.14f, y = 34.dp * artworkScale)
                    .rotate(-12f),
            )
            TargetDocumentBadge(
                label = "FACTS",
                tint = EvidriloColors.Cobalt,
                scale = artworkScale,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 10.dp * artworkScale),
            )
            TargetDocumentBadge(
                label = "LIMITS",
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
                        color = EvidriloColors.PrimaryAction,
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
                color = if (label == "LIMITS") {
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
                        color = if (label == "LIMITS") tint else EvidriloColors.White,
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
        EvidriloContentColumn(includeBottomSafeArea = false) {
            TargetCompactHeader(
                title = EvidriloTargetWorkspaceCopy.caseHeading,
                onBack = { onNavigate(EvidriloTargetSection.HOME) },
                backLabel = "Home",
                centered = false,
            )
            TargetPageIntro(
                title = case.title,
                body = "Evidence-backed local workspace",
            )
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
                    status = "${metrics.selectedEvidenceCount}/${metrics.suppliedObservationCount} selected",
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
        EvidriloContentColumn(includeBottomSafeArea = false) {
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
            EvidriloPrimaryButton(
                label = targetEvidencePrimaryActionLabel(metrics.evidenceStatus),
                onClick = onOpenClaimTrace,
            )
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
    val lens = evidenceLensFor(case, draft)
    var selectedFactId by remember(case.id) { mutableStateOf<String?>(null) }
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn(includeBottomSafeArea = false) {
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
            if (lens.entries.isEmpty()) {
                TargetPageStatePanel(
                    state = TargetPageState.EMPTY,
                    title = "No supplied observations",
                    body = "This case has no observation facts available for the evidence lens.",
                )
            } else {
                lens.entries.forEach { entry ->
                    val detail = targetEvidenceLensDetail(case, draft, entry.factId)
                    TargetEvidenceLensCard(
                        detail = detail,
                        fallbackText = entry.text,
                        onClick = { selectedFactId = entry.factId },
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
    selectedFactId?.let { factId ->
        TargetEvidenceDetailSheet(
            case = case,
            draft = draft,
            factId = factId,
            onDismiss = { selectedFactId = null },
        )
    }
}

@Composable
private fun TargetEvidenceLensCard(
    detail: TargetEvidenceLensDetail,
    fallbackText: String,
    onClick: () -> Unit,
) {
    val fact = detail.fact
    val label = fact?.displayLabel ?: when (detail.availability) {
        TargetFactAvailability.AVAILABLE -> "Supplied observation"
        TargetFactAvailability.UNAVAILABLE -> "Unavailable reference"
        TargetFactAvailability.INCOMPATIBLE_TYPE -> "Not an observation"
    }
    val status = when (detail.availability) {
        TargetFactAvailability.UNAVAILABLE -> "Unavailable"
        TargetFactAvailability.INCOMPATIBLE_TYPE -> "Not an observation"
        TargetFactAvailability.AVAILABLE -> if (detail.selected) "Selected" else "Available"
    }
    EvidriloTargetCard(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Inspect $label, ${detail.factId}. $status."
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TargetIconTile(
                icon = if (detail.selected && detail.availability == TargetFactAvailability.AVAILABLE) {
                    EvidriloIconName.CHECK
                } else {
                    EvidriloIconName.FILE
                },
                tint = if (detail.selected && detail.availability == TargetFactAvailability.AVAILABLE) {
                    EvidriloColors.Success
                } else {
                    EvidriloColors.Cobalt
                },
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    EvidriloStatusChip(
                        label = status,
                        tone = when (detail.availability) {
                            TargetFactAvailability.UNAVAILABLE,
                            TargetFactAvailability.INCOMPATIBLE_TYPE,
                            -> EvidriloStatusTone.WARNING
                            TargetFactAvailability.AVAILABLE -> if (detail.selected) {
                                EvidriloStatusTone.SUCCESS
                            } else {
                                EvidriloStatusTone.NEUTRAL
                            }
                        },
                    )
                }
                Text("Bundled case · ${detail.caseTitle}", style = MaterialTheme.typography.labelMedium)
                Text(detail.factId, style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
                Text(fact?.text ?: fallbackText, style = MaterialTheme.typography.bodyMedium)
                Text("Tap to inspect source and usage", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TargetEvidenceDetailSheet(
    case: ConclusionCase,
    draft: ConclusionDraft,
    factId: String,
    onDismiss: () -> Unit,
) {
    val detail = targetEvidenceLensDetail(case, draft, factId)
    val fact = detail.fact
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Evidence detail", style = MaterialTheme.typography.headlineSmall)
            Text(detail.caseTitle, style = MaterialTheme.typography.titleMedium)
            TargetDetailLine("Origin", "Bundled case record")
            TargetDetailLine("Case ID", detail.caseId)
            TargetDetailLine("Case version", detail.caseVersionId ?: "Not provided by this case")
            TargetDetailLine("Fact ID", detail.factId)
            TargetDetailLine("Availability", detail.availability.targetDisplayLabel())
            when (detail.availability) {
                TargetFactAvailability.AVAILABLE -> {
                    TargetDetailLine("Record type", fact!!.type.targetDisplayLabel())
                    TargetDetailLine(
                        "Relationship to this draft",
                        if (detail.selected) {
                            "Selected by you as an evidence anchor; support is assessed separately."
                        } else {
                            "Available in the case, but not selected in this draft."
                        },
                    )
                    fact.displayValue?.let { TargetDetailLine("Recorded value", it) }
                    TargetDetailLine("Supplied text", fact.text)
                    TargetDetailLine(
                        "Interpretation boundary",
                        "Selection records your choice; it does not by itself establish that this observation supports the claim.",
                    )
                }
                TargetFactAvailability.UNAVAILABLE -> {
                    TargetDetailLine(
                        "Draft reference",
                        if (detail.selected) "This draft references this ID, but its target is unavailable."
                        else "This ID is not referenced by the current draft.",
                    )
                    TargetDetailLine(
                        "What is known",
                        "This reference is not present in the active case version. No substitute observation is shown.",
                    )
                }
                TargetFactAvailability.INCOMPATIBLE_TYPE -> {
                    TargetDetailLine("Record type", fact!!.type.targetDisplayLabel())
                    TargetDetailLine(
                        "Draft reference",
                        if (detail.selected) {
                            "This draft contains the ID, but it is not counted as a selected observation."
                        } else {
                            "This case fact is not selected as evidence."
                        },
                    )
                    TargetDetailLine("Supplied text", fact.text)
                }
            }
            TargetDetailLine("Source locator", "Page or section locator is not supplied in this case.")
            EvidriloSecondaryButton(label = "Close", onClick = onDismiss)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TargetRequirementDetailSheet(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onDismiss: () -> Unit,
) {
    val detail = targetRequirementTraceDetail(case, draft)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Requirement trace", style = MaterialTheme.typography.headlineSmall)
            Text(detail.caseTitle, style = MaterialTheme.typography.titleMedium)
            TargetDetailLine("Origin", "Requirement supplied with this case")
            TargetDetailLine("Case ID", detail.caseId)
            TargetDetailLine("Case version", detail.caseVersionId ?: "Not provided by this case")
            detail.requirement?.let { requirement ->
                TargetDetailLine("Requirement ID", requirement.id)
                TargetDetailLine("Requirement text", requirement.text)
            } ?: TargetDetailLine("Requirement", "No requirement fact is supplied in the active case.")

            Text("Selected evidence in this draft", style = MaterialTheme.typography.titleMedium)
            if (detail.selectedEvidence.isEmpty()) {
                Text("No supplied observations are selected.", style = MaterialTheme.typography.bodyMedium)
            } else {
                detail.selectedEvidence.forEach { fact ->
                    TargetDetailLine(fact.displayLabel ?: fact.id, "${fact.id}\n${fact.text}")
                }
            }
            detail.unavailableEvidenceIds.forEach { id ->
                TargetDetailLine(
                    "Unavailable reference",
                    "This draft references $id, but it is not present in the active case version; no substitute is used.",
                )
            }
            detail.incompatibleEvidenceIds.forEach { id ->
                TargetDetailLine("Not an observation", "$id exists in the case but is not treated as selected evidence.")
            }

            Text("Recorded relationship", style = MaterialTheme.typography.titleMedium)
            TargetDetailLine(
                "Student-recorded relation",
                detail.studentRecordedRelation?.targetDisplayLabel()
                    ?: "No relationship has been recorded by you.",
            )
            TargetDetailLine("Evidence assessment", detail.supportStatus.targetDisplayLabel())
            Text(
                "This bounded case check explains the current draft against supplied rules; it does not establish universal scientific truth.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Claim boundary", style = MaterialTheme.typography.titleMedium)
            detail.boundary?.let { boundary ->
                TargetDetailLine(boundary.id, boundary.text)
            } ?: Text("No claim-boundary fact is supplied in the active case.", style = MaterialTheme.typography.bodyMedium)
            EvidriloSecondaryButton(label = "Close", onClick = onDismiss)
        }
    }
}

@Composable
private fun TargetDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Slate)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun TargetFactAvailability.targetDisplayLabel(): String = when (this) {
    TargetFactAvailability.AVAILABLE -> "Available in active case"
    TargetFactAvailability.UNAVAILABLE -> "Unavailable in active case"
    TargetFactAvailability.INCOMPATIBLE_TYPE -> "Present, but not an observation"
}

private fun ConclusionFactType.targetDisplayLabel(): String = when (this) {
    ConclusionFactType.AIM -> "Requirement"
    ConclusionFactType.CONTEXT -> "Case context"
    ConclusionFactType.OBSERVATION -> "Supplied observation"
    ConclusionFactType.LIMITATION -> "Stated limitation"
    ConclusionFactType.BOUNDARY -> "Claim boundary"
}

private fun ConclusionRelation.targetDisplayLabel(): String = when (this) {
    ConclusionRelation.OBSERVED_DIFFERENCE -> "Observed difference"
    ConclusionRelation.LIMITED_OBSERVATION -> "Limited observation"
    ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE -> "Cannot conclude from this case"
    ConclusionRelation.UNSUPPORTED -> "Unmapped relation"
}

@Composable
private fun TargetMissingEvidenceCard(body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = EvidriloColors.WarningSurface,
        border = BorderStroke(2.dp, EvidriloColors.Warning.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
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
        EvidriloContentColumn(includeBottomSafeArea = false) {
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
    var isRequirementDetailOpen by remember(case.id) { mutableStateOf(false) }
    var selectedEvidenceFactId by remember(case.id) { mutableStateOf<String?>(null) }
    EvidriloTargetSurface(EvidriloTargetSection.EVIDENCE, onNavigate) {
        EvidriloContentColumn(includeBottomSafeArea = false) {
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
                TargetRequirementCard(
                    fact = requirement,
                    status = metrics.evidenceStatus,
                    onClick = { isRequirementDetailOpen = true },
                )
            }
            TargetTraceInfoCard(
                title = "Why this is required",
                body = "This requirement is part of the supplied case brief. Evidrilo keeps the requirement, evidence anchors, and claim boundary visible together.",
                icon = EvidriloIconName.FILE,
            )
            TargetSupportingEvidenceCard(
                case = case,
                draft = draft,
                onInspectFact = { selectedEvidenceFactId = it },
            )
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
    if (isRequirementDetailOpen) {
        TargetRequirementDetailSheet(
            case = case,
            draft = draft,
            onDismiss = { isRequirementDetailOpen = false },
        )
    }
    selectedEvidenceFactId?.let { factId ->
        TargetEvidenceDetailSheet(
            case = case,
            draft = draft,
            factId = factId,
            onDismiss = { selectedEvidenceFactId = null },
        )
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
        EvidriloContentColumn(includeBottomSafeArea = false) {
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
                    val limitationSummary = case.facts
                        .filter { it.id in draft.limitationRefs }
                        .joinToString { it.displayLabel ?: it.text }
                        .ifBlank { "No limitation selected yet" }
                    Text("Limitations: $limitationSummary", style = MaterialTheme.typography.bodyMedium)
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
    var showVerificationDetails by remember(evaluation) { mutableStateOf(false) }
    EvidriloTargetSurface(EvidriloTargetSection.ACTION, onNavigate) {
        EvidriloContentColumn(includeBottomSafeArea = false) {
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
                if (canRevise) {
                    EvidriloPrimaryButton(label = "Revise once", onClick = onRevise)
                } else {
                    EvidriloPrimaryButton(label = "Open claim review", onClick = onStartPractice)
                }
                EvidriloSecondaryButton(
                    label = if (showVerificationDetails) "Hide verification details" else "Review verification details",
                    onClick = { showVerificationDetails = !showVerificationDetails },
                )
                if (showVerificationDetails) {
                    EvidriloVerificationDetailCard(evaluation = evaluation)
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
    challengeAvailable: Boolean = true,
) {
    EvidriloTargetSurface(EvidriloTargetSection.ACTION, onNavigate) {
        EvidriloContentColumn(includeBottomSafeArea = false) {
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
                    "This saved comparison includes the before/after drafts but does not store a verification record.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            EvidriloPrimaryButton(label = "Open local history", onClick = onOpenHistory)
            if (challengeAvailable) {
                EvidriloSecondaryButton(label = "Try the evidence-change challenge", onClick = onStartChallenge)
            }
        }
    }
}

@Composable
internal fun EvidriloTargetProfileScreen(
    signedIn: Boolean,
    profileSubtitle: String,
    history: ConclusionSessionSnapshot?,
    onBack: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
    onOpenPremium: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWorkspacePreferences: () -> Unit,
    onOpenPrivacyData: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenLocalProjects: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    EvidriloTargetSurface(EvidriloTargetSection.PROFILE, onNavigate) {
        EvidriloContentColumn {
            EvidriloBackButton(label = "Home", onClick = onBack)
            TargetPageIntro(
                title = "Your workspace.",
                body = if (TEMPORARY_GUEST_MODE_ENABLED) {
                    "Projects, catalog, case work, and history stay on this device."
                } else if (signedIn) {
                    "Manage your account, projects,\nsettings, and Evidrilo Pro."
                } else {
                    "Your projects stay on this device."
                },
            )
            TargetProfileSummaryCard(
                signedIn = signedIn,
                profileSubtitle = profileSubtitle,
                history = history,
                onClick = onOpenAccount,
            )
            if (REVENUECAT_PRO_FEATURE_ENABLED) TargetCobaltCard(
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
                icon = EvidriloIconName.FOLDER,
                title = "My projects",
                subtitle = "Open projects stored on this device",
                onClick = onOpenLocalProjects,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.SETTINGS,
                title = "Workspace preferences",
                subtitle = "Customize your workspace",
                onClick = onOpenWorkspacePreferences,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.BELL,
                title = "Notifications",
                subtitle = "Off by default · local reminders",
                onClick = onOpenNotifications,
            )
            if (!TEMPORARY_GUEST_MODE_ENABLED) TargetSettingsRow(
                icon = EvidriloIconName.DATABASE,
                title = "Export & backup",
                subtitle = "Account export when signed in",
                onClick = onOpenAccount,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.SHIELD,
                title = "Privacy & data",
                subtitle = "Your data, your control",
                onClick = onOpenPrivacyData,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.HISTORY,
                title = "Local history",
                subtitle = "Review changes on this device",
                onClick = onOpenHistory,
            )
            TargetSettingsRow(
                icon = EvidriloIconName.QUESTION,
                title = "Support",
                subtitle = "Help and account options",
                onClick = onOpenSupport,
            )
        }
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
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
        EvidriloContentColumn(
            includeBottomSafeArea = !shouldShowTargetBottomNavigation(selectedSection),
        ) {
            EvidriloBackButton(label = backLabel, onClick = onBack)
            TargetPageIntro(
                title = "Track what changed.",
                body = "Follow how evidence, claims, and actions changed.",
            )
            storageNotice
                ?.takeIf { it.isError }
                ?.let { notice -> EvidriloRecoveryNotice(notice = notice) }
            if (history == null || summary == null) {
                EvidriloTargetCard {
                    Text("No comparison saved yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Complete the free evidence workflow and one revision to create a local before/after record.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                EvidriloPrimaryButton(label = "Start evidence review", onClick = onStartPractice)
            } else {
                EvidriloTargetCard {
                    Text("This workspace", style = MaterialTheme.typography.titleLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TargetMetric(summary.evidenceRemoved.toString(), "evidence removed", EvidriloColors.Ink, EvidriloColors.Slate, Modifier.weight(1f))
                        TargetMetric(summary.evidenceAdded.toString(), "evidence added", EvidriloColors.Ink, EvidriloColors.Slate, Modifier.weight(1f))
                        TargetMetric(summary.actionsChanged.toString(), "actions changed", EvidriloColors.Ink, EvidriloColors.Slate, Modifier.weight(1f))
                    }
                }
                Text("Latest local comparison", style = MaterialTheme.typography.titleLarge)
                TargetHistoryEventRow(
                    title = targetHistoryEvidenceLabel(summary),
                    body = "${summary.evidenceAdded} added · ${summary.evidenceRemoved} removed",
                )
                TargetHistoryEventRow(
                    title = targetHistoryResultLabel(history),
                    body = "The learner-authored before/after state is available locally.",
                )
                TargetHistoryEventRow(
                    title = "Boundary kept visible",
                    body = "History does not turn a bounded case into a scientific-truth score.",
                )
                EvidriloPrimaryButton(label = "View evidence delta", onClick = onOpenDelta)
                EvidriloSecondaryButton(label = "Clear local comparison", onClick = { confirmClear = true })
            }
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
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
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
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TargetIconTile(icon = icon, size = 40.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Inspect requirement ${fact.id}. ${status.targetDisplayLabel()}."
            }
    }
    EvidriloTargetCard(modifier = cardModifier) {
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
        if (onClick != null) {
            Text("Tap to inspect requirement origin and links", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
        }
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
                    1 -> EvidriloColors.Teal
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
                    1 -> EvidriloColors.Teal
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
        }
    }
}

@Composable
private fun TargetSupportingEvidenceCard(
    case: ConclusionCase,
    draft: ConclusionDraft,
    onInspectFact: (String) -> Unit,
) {
    EvidriloTargetCard {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TargetIconTile(icon = EvidriloIconName.LINK)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Evidence linked to this requirement", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Available observations are listed; selected anchors are marked. Selection alone is not a support verdict.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        evidenceLensFor(case, draft).entries.forEach { entry ->
            val detail = targetEvidenceLensDetail(case, draft, entry.factId)
                TargetTraceEvidenceRow(
                    detail = detail,
                    onClick = { onInspectFact(detail.factId) },
                )
        }
    }
}

@Composable
private fun TargetTraceEvidenceRow(
    detail: TargetEvidenceLensDetail,
    onClick: () -> Unit,
) {
    val fact = detail.fact
    val status = when (detail.availability) {
        TargetFactAvailability.UNAVAILABLE -> "Unavailable"
        TargetFactAvailability.INCOMPATIBLE_TYPE -> "Not an observation"
        TargetFactAvailability.AVAILABLE -> if (detail.selected) "Selected" else "Available"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Inspect evidence ${detail.factId}. $status."
            }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TargetIconTile(
            icon = EvidriloIconName.FILE,
            tint = if (detail.selected && detail.availability == TargetFactAvailability.AVAILABLE) {
                EvidriloColors.Cobalt
            } else {
                EvidriloColors.Slate
            },
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                fact?.displayLabel ?: when (detail.availability) {
                    TargetFactAvailability.AVAILABLE -> "Observation"
                    TargetFactAvailability.UNAVAILABLE -> "Unavailable reference"
                    TargetFactAvailability.INCOMPATIBLE_TYPE -> "Other case fact"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(detail.factId, style = MaterialTheme.typography.labelMedium)
        }
        EvidriloStatusChip(
            label = status,
            tone = when (detail.availability) {
                TargetFactAvailability.UNAVAILABLE,
                TargetFactAvailability.INCOMPATIBLE_TYPE,
                -> EvidriloStatusTone.WARNING
                TargetFactAvailability.AVAILABLE -> if (detail.selected) {
                    EvidriloStatusTone.SUCCESS
                } else {
                    EvidriloStatusTone.NEUTRAL
                }
            },
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
                    Text(metrics.selectedEvidenceSummary, style = MaterialTheme.typography.bodyMedium)
                    Text("·", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    Text(
                        "${metrics.suppliedObservationCount - metrics.selectedEvidenceCount} not selected",
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
private fun TargetCoverageSection(
    title: String,
    onOpen: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(modifier = Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    signedIn: Boolean,
    profileSubtitle: String,
    history: ConclusionSessionSnapshot?,
    onClick: () -> Unit,
) {
    val summary = targetHistorySummary(history)
    val interactive = !TEMPORARY_GUEST_MODE_ENABLED || signedIn
    EvidriloTargetCard(
        modifier = if (interactive) {
            Modifier
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (signedIn) "Open student account" else "Open account options"
                    role = Role.Button
                }
        } else {
            Modifier
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(50), color = EvidriloColors.PrimaryAction) {
                Box(contentAlignment = Alignment.Center) {
                    Text("S", style = MaterialTheme.typography.headlineSmall, color = EvidriloColors.White)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (signedIn) "Student account" else "Local student", style = MaterialTheme.typography.titleLarge)
                Text(profileSubtitle, style = MaterialTheme.typography.bodyMedium)
            }
            if (interactive) EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
        }
        EvidriloDivider()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TargetProfileMetric(
                icon = EvidriloIconName.FILE,
                value = if (signedIn || TEMPORARY_GUEST_MODE_ENABLED) {
                    summary?.let { "${it.evidenceAdded + it.evidenceRemoved}" } ?: "—"
                } else {
                    "—"
                },
                label = when {
                    !signedIn && !TEMPORARY_GUEST_MODE_ENABLED -> "account history"
                    summary == null -> "no saved comparison"
                    else -> "evidence changes"
                },
            )
            TargetProfileMetric(
                icon = EvidriloIconName.FOLDER,
                value = "Local",
                label = "project storage",
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
private fun TargetGroupedRow(
    icon: EvidriloIconName,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $subtitle"
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TargetIconTile(icon = icon, size = 44.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate)
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
            "${metrics.selectedEvidenceCount}/${metrics.suppliedObservationCount}",
            "selected / supplied",
        )
        TargetMetricCard(EvidriloIconName.ARROW_FORWARD, "${metrics.actionCount}", "next actions")
    }
}

@Composable
private fun RowScope.TargetMetricCard(icon: EvidriloIconName, value: String, label: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(12.dp),
                color = EvidriloColors.PaleBlue,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    EvidriloIcon(icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(20.dp))
                }
            }
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TargetMetric(
    value: String,
    label: String,
    valueColor: androidx.compose.ui.graphics.Color,
    labelColor: androidx.compose.ui.graphics.Color = valueColor.copy(alpha = 0.78f),
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
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
