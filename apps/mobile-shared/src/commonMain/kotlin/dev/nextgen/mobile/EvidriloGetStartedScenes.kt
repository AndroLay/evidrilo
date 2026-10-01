package dev.nextgen.mobile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun GetStartedWelcomeScene(preview: GetStartedSceneSelections, height: Dp) {
    val entry = rememberGetStartedReveal(preview.welcomePulse.value, 1100)
    val drift = rememberGetStartedReveal(preview.welcomePulse.value, 2800)
    val spokenWelcome=uiText("Your question, notes, claim and next step, connected in Evidrilo")
    val spokenReplay=uiText("Replay the introduction artwork")
    val labelWidth = 152.dp + 90.dp * (LocalDensity.current.fontScale - 1f).coerceIn(0f, .5f)
    val line = EvidriloColors.PatternBlue
    val cobalt = EvidriloColors.Cobalt
    GetStartedAtmosphere(
        Modifier.fillMaxWidth().height(height).clickable(
            role = Role.Button, onClickLabel = spokenReplay,
            onClick = { preview.welcomePulse.value++ },
        ).semantics(mergeDescendants = true) { contentDescription = spokenWelcome },
    ) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val radius = size.minDimension * .36f
            drawCircle(line.copy(alpha = .55f), radius, center, style = Stroke(1.5.dp.toPx()))
            drawCircle(line.copy(alpha = .28f), radius * .73f, center, style = Stroke(1.dp.toPx()))
            repeat(3) { index ->
                val angle = (drift.value * 2f * PI + index * 2.1).toFloat()
                drawCircle(cobalt, 4.dp.toPx(), center + Offset(cos(angle) * radius, sin(angle) * radius))
            }
        }
        Box(
            Modifier.align(Alignment.Center).size(146.dp).graphicsLayer {
                scaleX = .72f + entry.value * .28f
                scaleY = scaleX
                rotationZ = (1f - entry.value) * -20f
            }.clip(CircleShape).background(EvidriloColors.Tint),
            contentAlignment = Alignment.Center,
        ) { EvidriloLogoMark(size = 116.dp) }
        val items = listOf(
            Triple("Question", EvidriloIconName.QUESTION, Alignment.TopStart),
            Triple("Notes", EvidriloIconName.BOOK, Alignment.TopEnd),
            Triple("Claim", EvidriloIconName.LINK, Alignment.BottomStart),
            Triple("Next step", EvidriloIconName.ARROW_FORWARD, Alignment.BottomEnd),
        )
        items.forEachIndexed { index, (label, icon, alignment) ->
            val isLeft = index % 2 == 0
            val shift = sin((drift.value * 4 * PI + index).toFloat()) * 5f
            Surface(
                Modifier.align(alignment).padding(horizontal = 7.dp, vertical = if (index == 1 || index == 2) 62.dp else 20.dp)
                    .width(labelWidth).graphicsLayer {
                        alpha = entry.value
                        translationX = (if (isLeft) -1 else 1) * (1 - entry.value) * 65.dp.toPx()
                        translationY = shift.dp.toPx()
                        rotationZ = (if (isLeft) -1 else 1) * (7f + (1f - entry.value) * 10f)
                    },
                shape = RoundedCornerShape(18.dp), color = EvidriloColors.Card,
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 17.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    EvidriloIcon(icon, tint = cobalt, modifier = Modifier.size(22.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Ink)
                }
            }
        }
        Text("Tap to bring it together", Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
}

@Composable
internal fun GetStartedProjectsScene(preview: GetStartedSceneSelections, height: Dp) {
    val pager = rememberPagerState(initialPage = preview.family.value, pageCount = { GetStartedProjectFamily.entries.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { preview.family.value = pager.currentPage }
    GetStartedAtmosphere(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(
                state = pager, contentPadding = PaddingValues(horizontal = 26.dp), pageSpacing = 12.dp,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                val family = GetStartedProjectFamily.entries[page]
                Surface(
                    Modifier.fillMaxSize().padding(top = 13.dp, bottom = 10.dp).graphicsLayer {
                        rotationZ = distance * -6f
                        scaleX = 1f - abs(distance) * .08f
                        scaleY = scaleX
                        alpha = 1f - abs(distance) * .28f
                    },
                    shape = RoundedCornerShape(24.dp), color = EvidriloColors.Card, shadowElevation = 5.dp,
                ) {
                    Box(Modifier.fillMaxSize()) {
                    val tile=when(family){GetStartedProjectFamily.LAB->0;GetStartedProjectFamily.SURVEY->1;GetStartedProjectFamily.LITERATURE->2;GetStartedProjectFamily.QUALITATIVE->3;GetStartedProjectFamily.DESIGN->4}
                    EvidriloProjectIllustration(dev.nextgen.mobile.domain.project.ProjectTemplateFamily.entries[tile],Modifier.matchParentSize())
                    Column(Modifier.padding(20.dp).padding(end=50.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(EvidriloColors.Tint), contentAlignment = Alignment.Center) {
                                EvidriloIcon(family.icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(25.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(family.label, style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Ink)
                            }
                        }
                        family.sections.forEachIndexed { index, section ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("0${index + 1}", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                                Text(section, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = EvidriloColors.Ink)
                            }
                        }
                        Text("Empty structure. Your own content.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    }
                    }
                }
            }
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.Center) {
                GetStartedProjectFamily.entries.forEachIndexed { index, family ->
                    val selected = index == pager.currentPage
                    val spokenPreview=uiText("Preview ","Pratinjau ")+uiText(family.displayName)
                    Box(
                        Modifier.size(48.dp).semantics { contentDescription = spokenPreview }
                            .selectable(selected, onClick = { scope.launch { pager.animateScrollToPage(index) } }, role = Role.RadioButton),
                        contentAlignment = Alignment.Center,
                    ) {
                        val width by animateFloatAsState(if (selected) 24f else 8f, spring(), label = "Project pager dot")
                        Box(Modifier.width(width.dp).height(8.dp).clip(CircleShape).background(if (selected) EvidriloColors.Cobalt else EvidriloColors.PatternBlue))
                    }
                }
            }
            Text("Swipe to discover · ${pager.currentPage + 1} / 5", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
    }
}

@Composable
internal fun GetStartedEvidenceScene(preview: GetStartedSceneSelections, height: Dp) {
    val linked = preview.noteLinked.value
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var noteBounds by remember { mutableStateOf(Rect.Zero) }
    var claimBounds by remember { mutableStateOf(Rect.Zero) }
    val offset by animateOffsetAsState(if (dragging) dragOffset else Offset.Zero, if (dragging) snap() else spring(), label = "Note return to place")
    val reveal = rememberGetStartedReveal(linked, 750)
    val track = EvidriloColors.PatternBlue
    val active = EvidriloColors.Cobalt
    val lift by animateFloatAsState(if (dragging) 1.05f else 1f, spring(), label = "Note lift")
    GetStartedAtmosphere(Modifier.fillMaxWidth().height(height)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val claimY = maxHeight * .52f
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                val start = Offset(size.width * .5f, size.height * .31f)
                val end = Offset(size.width * .5f, size.height * .53f)
                val path = Path().apply { moveTo(start.x, start.y); cubicTo(start.x - 65.dp.toPx(), start.y + 22.dp.toPx(), end.x + 65.dp.toPx(), end.y - 22.dp.toPx(), end.x, end.y) }
                drawPath(path, track, style = Stroke(2.dp.toPx()))
                if (linked) {
                    val trace = Path()
                    var dot = start
                    repeat(41) { index ->
                        val t = index / 40f * reveal.value
                        val u = 1 - t
                        dot = Offset(u*u*u*start.x + 3*u*u*t*(start.x-65.dp.toPx()) + 3*u*t*t*(end.x+65.dp.toPx()) + t*t*t*end.x,
                            u*u*u*start.y + 3*u*u*t*(start.y+22.dp.toPx()) + 3*u*t*t*(end.y-22.dp.toPx()) + t*t*t*end.y)
                        if (index == 0) trace.moveTo(dot.x, dot.y) else trace.lineTo(dot.x, dot.y)
                    }
                    drawPath(trace, active, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(active, 5.dp.toPx(), dot)
                }
            }
            Surface(
                Modifier.align(Alignment.TopCenter).offset(y = claimY).width(256.dp)
                    .onGloballyPositioned { claimBounds = it.boundsInRoot() }
                    .graphicsLayer { val bounce = if (linked) sin(reveal.value * PI).toFloat() * .05f else 0f; scaleX = 1f + bounce; scaleY = scaleX },
                shape = RoundedCornerShape(22.dp), color = if (linked) EvidriloColors.Tint else EvidriloColors.Atmosphere,
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EvidriloIcon(if (linked) EvidriloIconName.LINK else EvidriloIconName.PLUS, tint = active, modifier = Modifier.size(22.dp))
                        Text("Your interpretation", style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Ink)
                    }
                    Text(if (linked) "1 note linked" else "Drop the note here", style = MaterialTheme.typography.bodyMedium, color = active)
                    Text("Keep limits visible", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
            }
            Surface(
                Modifier.align(Alignment.TopCenter).offset(y = 14.dp).offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .width(210.dp).graphicsLayer { scaleX = lift; scaleY = lift; rotationZ = if (dragging) -5f else -3f }
                    .onGloballyPositioned { noteBounds = it.boundsInRoot() }
                    .pointerInput(linked) {
                        if (!linked) detectDragGestures(
                            onDragStart = { dragging = true; dragOffset = Offset.Zero },
                            onDragCancel = { dragging = false; dragOffset = Offset.Zero },
                            onDragEnd = { if (claimBounds.contains(noteBounds.center)) preview.noteLinked.value = true; dragging = false; dragOffset = Offset.Zero },
                            onDrag = { change, delta -> change.consume(); dragOffset += delta },
                        )
                    }.clickable(onClickLabel = if (linked) "Unlink the example note" else "Link the example note", role = Role.Button) { preview.noteLinked.value = !linked }
                    .semantics(mergeDescendants = true) { stateDescription = if (linked) "Linked to the example claim" else "Not linked. Drag onto the claim, or tap to link." },
                shape = RoundedCornerShape(18.dp), color = EvidriloColors.Card, shadowElevation = if (dragging) 12.dp else 5.dp,
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EvidriloIcon(EvidriloIconName.BOOK, tint = active, modifier = Modifier.size(30.dp))
                    Column {
                        Text("Reading note", style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Ink)
                        Text("A source you choose", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    }
                }
            }
            TextButton(onClick = { preview.noteLinked.value = !linked }, modifier = Modifier.align(Alignment.BottomCenter)) {
                Text(if (linked) "Try again" else "Or tap to connect", color = active, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
