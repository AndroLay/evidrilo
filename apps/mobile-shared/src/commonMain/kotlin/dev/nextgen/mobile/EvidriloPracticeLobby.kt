package dev.nextgen.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.practice.*

/** Presentation metadata only. The course reducer remains the source of access and progress. */
internal data class PracticePathStation(val lesson: PracticeLessonId, val title: String, val minutes: String)

internal val freePracticePath = listOf(
    PracticePathStation(PracticeLessonId.TABLET, "Trace observations", "8–12 min"),
    PracticePathStation(PracticeLessonId.STUDIES, "Compare sources", "8–10 min"),
    PracticePathStation(PracticeLessonId.SURVEY, "Reconsider data", "6–8 min"),
)

@Composable
internal fun EvidriloPracticeLobby(
    course: PracticeCourseState, selected: PracticeLessonId, busy: Boolean,
    onSelect: (PracticeLessonId) -> Unit, onOpen: (PracticeLessonId) -> Unit,
    onExit: () -> Unit, onHelp: () -> Unit, onOpenProjects: () -> Unit,
    storageNotice: @Composable () -> Unit,
    stations: List<PracticePathStation> = freePracticePath,
) {
    val session = course.sessions[selected]
    val complete = session?.stage == PracticeLessonStage.COMPLETE
    val completed = stations.count { course.sessions[it.lesson]?.stage == PracticeLessonStage.COMPLETE }
    PracticeFrame(onExit, onHelp, "${stations.size} Free cases · choose any", footer = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(PracticeCourseContent.title(selected), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    EvidriloPrimaryButton(when { complete -> "Revisit case"; session != null -> "Resume case"; else -> "Start case" },
                        { onOpen(selected) }, enabled = !busy, trailingIcon = EvidriloIconName.ARROW_FORWARD)
                }
                EvidriloIconButton(EvidriloIconName.FOLDER, "Open my projects", onOpenProjects, enabled = !busy)
            }
        }
    }) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EvidriloLogoMark(size = 44.dp)
                Surface(shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp), color = EvidriloColors.Atmosphere) {
                    Text("Every strong claim starts with evidence.", Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Ink)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your evidence trail", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                Text("$completed/${stations.size} completed", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate,
                    modifier = Modifier.semantics { contentDescription = "$completed of ${stations.size} practice attempts completed. Not a mastery score." })
            }
            storageNotice()
            PracticeTrail(stations, selected, course, busy, onSelect)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EvidriloProEmblem(36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("More investigations ahead", style = MaterialTheme.typography.titleMedium)
                    Text("Additional Pro practice is planned.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
            }
            EvidriloExplanation("What Practice records", "These are synthetic cases. Completion records your attempt, not a grade or proof of mastery. Your project work stays separate. Choose any case; the path does not lock later cases.")
        }
    }
}

@Composable
private fun PracticeTrail(stations: List<PracticePathStation>, selected: PracticeLessonId, course: PracticeCourseState,
    busy: Boolean, onSelect: (PracticeLessonId) -> Unit) {
    val rowHeight = 132.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val reveal = rememberGetStartedReveal("practice-trail", 550)
    val track = EvidriloColors.Tint
    val line = EvidriloColors.PatternBlue
    Box(Modifier.fillMaxWidth().selectableGroup()) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val tileCenter = 64.dp.toPx()
            val step = rowHeight.toPx()
            val path = Path()
            stations.forEachIndexed { index, _ ->
                val x = if (index % 2 == 0) tileCenter else size.width - tileCenter
                val y = step * (index + .5f)
                if (index == 0) path.moveTo(x, y)
                else {
                    val previousX = if (index % 2 == 1) tileCenter else size.width - tileCenter
                    path.cubicTo(previousX, y - step * .55f, x, y - step * .45f, x, y)
                }
            }
            drawPath(path, track, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
            val measure = PathMeasure().apply { setPath(path, false) }
            val shown = Path()
            measure.getSegment(0f, measure.length * reveal.value, shown, true)
            drawPath(shown, line, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        }
        Column {
            stations.forEachIndexed { index, station ->
                key(station.lesson) {
                    val active = station.lesson == selected
                    val progress = course.sessions[station.lesson]
                    val done = progress?.stage == PracticeLessonStage.COMPLETE
                    val interactions = remember { MutableInteractionSource() }
                    val pressed by interactions.collectIsPressedAsState()
                    val lift by animateFloatAsState(if (pressed) 4f else if (active) -3f else 0f, tween(150), label = "Practice station press")
                    val description = when { done -> "Completed · revisit"; progress != null -> "Saved attempt"; active -> "Ready to start"; else -> "Available" }
                    Row(Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(16.dp))
                        .selectable(active, enabled = !busy, role = Role.RadioButton, interactionSource = interactions, indication = null,
                            onClick = { onSelect(station.lesson) })
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${station.title}. ${PracticeCourseContent.title(station.lesson)}. ${station.minutes}. $description. Free case."
                            stateDescription = if (active) "Selected" else "Not selected"
                        }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (index % 2 == 0) PracticeStationTile(station.lesson, active, done, lift)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(station.title, style = MaterialTheme.typography.titleMedium, color = if (active) EvidriloColors.Cobalt else EvidriloColors.Ink)
                            Text(station.minutes, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                            Text(description, style = MaterialTheme.typography.labelSmall, color = if (active || done) EvidriloColors.Cobalt else EvidriloColors.Slate)
                        }
                        if (index % 2 == 1) PracticeStationTile(station.lesson, active, done, lift)
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeStationTile(lesson: PracticeLessonId, selected: Boolean, completed: Boolean, lift: Float) {
    val face by animateColorAsState(if (selected) EvidriloColors.PrimaryAction else EvidriloColors.Atmosphere, tween(180), label = "Station selection")
    val base = if (selected) EvidriloColors.CobaltPressed else EvidriloColors.PatternBlue
    val icon = if (completed) EvidriloIconName.CHECK else courseIcon(lesson)
    Box(Modifier.size(112.dp, 100.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val diamond = Path().apply {
                val cx = size.width / 2; val cy = size.height / 2
                moveTo(cx - 7.dp.toPx(), 11.dp.toPx())
                cubicTo(cx, 7.dp.toPx(), cx, 7.dp.toPx(), cx + 7.dp.toPx(), 11.dp.toPx())
                lineTo(size.width - 8.dp.toPx(), cy - 4.dp.toPx())
                cubicTo(size.width, cy, size.width, cy + 2.dp.toPx(), size.width - 8.dp.toPx(), cy + 6.dp.toPx())
                lineTo(cx + 7.dp.toPx(), size.height - 16.dp.toPx())
                cubicTo(cx, size.height - 12.dp.toPx(), cx, size.height - 12.dp.toPx(), cx - 7.dp.toPx(), size.height - 16.dp.toPx())
                lineTo(8.dp.toPx(), cy + 6.dp.toPx())
                cubicTo(0f, cy + 2.dp.toPx(), 0f, cy, 8.dp.toPx(), cy - 4.dp.toPx())
                close()
            }
            drawContext.canvas.save()
            drawContext.canvas.translate(0f, 7.dp.toPx())
            drawPath(diamond, base)
            drawContext.canvas.restore()
            drawContext.canvas.save()
            drawContext.canvas.translate(0f, lift.dp.toPx())
            drawPath(diamond, face)
            drawContext.canvas.restore()
        }
        PracticeEvidenceObject(lesson, Modifier.size(76.dp).graphicsLayer { translationY = lift.dp.toPx() - 13.dp.toPx() })
        if (completed) Surface(Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 8.dp).size(24.dp),
            shape = RoundedCornerShape(12.dp), color = EvidriloColors.PrimaryAction) {
            Box(contentAlignment = Alignment.Center) { EvidriloIcon(icon, tint = EvidriloColors.White, modifier = Modifier.size(15.dp)) }
        }
    }
}

/** Small original evidence models: side faces and cast shadows give the objects real depth. */
@Composable
private fun PracticeEvidenceObject(lesson: PracticeLessonId, modifier: Modifier) {
    val paper = EvidriloColors.Card
    val edge = EvidriloColors.PatternBlue
    val ink = EvidriloColors.PrimaryAction
    val bright = EvidriloColors.CobaltBright
    val side = EvidriloColors.CobaltPressed
    val shadow = EvidriloColors.DeepNavy.copy(alpha = .12f)
    Canvas(modifier.clearAndSetSemantics {}) {
        withTransform({ scale(size.width / 80f, size.height / 80f, pivot = Offset.Zero) }) {
            fun polygon(vararg points: Pair<Float, Float>) = Path().apply {
                points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }; close()
            }
            fun sheet(x: Float, y: Float, width: Float, height: Float) {
                drawPath(polygon(x to y, x + width to y - 8, x + width to y + height - 8, x to y + height), edge)
                drawPath(polygon(x to y - 3, x + width to y - 11, x + width to y + height - 11, x to y + height - 3), paper)
            }
            drawOval(shadow, Offset(12f, 60f), androidx.compose.ui.geometry.Size(58f, 12f))
            when (lesson) {
                PracticeLessonId.TABLET -> {
                    sheet(18f, 24f, 44f, 39f)
                    sheet(14f, 17f, 44f, 39f)
                    drawLine(ink, Offset(23f, 25f), Offset(49f, 20f), 4f, StrokeCap.Round)
                    drawLine(edge, Offset(23f, 35f), Offset(47f, 30f), 3f, StrokeCap.Round)
                    drawLine(edge, Offset(23f, 43f), Offset(39f, 40f), 3f, StrokeCap.Round)
                    drawPath(polygon(49f to 41f, 60f to 34f, 65f to 42f, 53f to 49f), bright)
                    drawPath(polygon(53f to 49f, 65f to 42f, 65f to 47f, 53f to 54f), side)
                }
                PracticeLessonId.STUDIES -> {
                    sheet(11f, 23f, 30f, 39f)
                    sheet(40f, 15f, 29f, 39f)
                    drawLine(ink, Offset(18f, 29f), Offset(32f, 25f), 4f, StrokeCap.Round)
                    drawLine(edge, Offset(18f, 39f), Offset(32f, 35f), 3f, StrokeCap.Round)
                    drawLine(edge, Offset(18f, 47f), Offset(28f, 44f), 3f, StrokeCap.Round)
                    drawLine(bright, Offset(47f, 22f), Offset(60f, 18f), 4f, StrokeCap.Round)
                    drawLine(edge, Offset(47f, 31f), Offset(60f, 27f), 3f, StrokeCap.Round)
                    drawLine(edge, Offset(47f, 39f), Offset(57f, 36f), 3f, StrokeCap.Round)
                    drawCircle(ink, 8f, Offset(40f, 55f))
                    drawCircle(paper, 3f, Offset(37.5f, 55f), style = Stroke(1.5f))
                    drawCircle(paper, 3f, Offset(42.5f, 55f), style = Stroke(1.5f))
                }
                PracticeLessonId.SURVEY -> {
                    drawPath(polygon(10f to 53f, 41f to 40f, 71f to 55f, 40f to 69f), edge)
                    drawPath(polygon(10f to 49f, 41f to 36f, 71f to 51f, 40f to 65f), paper)
                    listOf(Triple(22f, 51f, 18f), Triple(39f, 51f, 35f), Triple(56f, 53f, 25f)).forEach { (x, y, height) ->
                        drawPath(polygon(x - 5 to y - height, x + 4 to y - height + 4, x + 4 to y + 4, x - 5 to y), ink)
                        drawPath(polygon(x + 4 to y - height + 4, x + 10 to y - height + 1, x + 10 to y + 1, x + 4 to y + 4), side)
                        drawPath(polygon(x - 5 to y - height, x + 1 to y - height - 3, x + 10 to y - height + 1, x + 4 to y - height + 4), bright)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EvidriloPracticeEntry(onClick: () -> Unit) {
    EvidriloPressableCard(onClick = onClick, faceColor = EvidriloColors.Atmosphere,
        borderColor = EvidriloColors.Tint, lipColor = EvidriloColors.PatternBlue) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EvidriloIcon(EvidriloIconName.EVIDENCE_GRAPH, tint = EvidriloColors.Cobalt, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Find your evidence trail", style = MaterialTheme.typography.titleMedium)
                Text("3 Free cases · practice a useful move", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
            EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = EvidriloColors.Cobalt)
        }
    }
}
