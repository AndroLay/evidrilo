package dev.nextgen.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Short, finite artwork sequences; no ticker or repeating offscreen animation. */
@Composable
internal fun rememberGetStartedReveal(trigger: Any, duration: Int = 900): Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
    }
    return progress
}

@Composable
internal fun GetStartedAtmosphere(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val wash = EvidriloColors.PaleBlue
    val edge = EvidriloColors.PatternBlue
    Box(modifier) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val radius = size.minDimension * .49f
            drawCircle(Brush.radialGradient(listOf(wash, wash.copy(alpha = 0f)), center, radius), radius, center)
            // A sparse constellation, rather than a full background pattern.
            listOf(.12f to .22f, .88f to .33f, .18f to .77f, .84f to .81f).forEachIndexed { index, point ->
                drawCircle(edge.copy(alpha = .65f), (if (index % 2 == 0) 3f else 5f).dp.toPx(), Offset(size.width * point.first, size.height * point.second))
            }
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GetStartedChoices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, groupLabel: String, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.selectableGroup().semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            val color by animateColorAsState(if (active) EvidriloColors.PrimaryAction else EvidriloColors.Atmosphere, tween(180), label = "Preview selection")
            Box(
                Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(15.dp)).background(color)
                    .selectable(active, onClick = { onSelect(index) }, role = Role.RadioButton)
                    .padding(horizontal = 11.dp, vertical = 13.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) EvidriloColors.White else EvidriloColors.Ink)
            }
        }
    }
}

/** Scatter of paper strips, not an achievement or a learner score. */
@Composable
internal fun GetStartedPaperBurst(trigger: Int, modifier: Modifier = Modifier) {
    val progress = rememberGetStartedReveal(trigger, 1600)
    val colors = listOf(EvidriloColors.Cobalt, EvidriloColors.CobaltBright, EvidriloColors.PatternBlue)
    Canvas(modifier.clearAndSetSemantics {}) {
        val p = progress.value
        if (p > 0f && p < 1f) {
            repeat(18) { index ->
                val angle = index * 2.3999632f
                val distance = size.minDimension * (.12f + p * (.27f + (index % 4) * .025f))
                val point = center + Offset(cos(angle) * distance, sin(angle) * distance + p * p * size.height * .12f)
                val alpha = (1f - p * p).coerceIn(0f, 1f)
                drawContext.canvas.save()
                drawContext.canvas.translate(point.x, point.y)
                drawContext.canvas.rotate(index * 27f + p * 170f)
                drawRoundRect(colors[index % colors.size].copy(alpha = alpha), Offset(-3.dp.toPx(), -6.dp.toPx()), androidx.compose.ui.geometry.Size(6.dp.toPx(), 12.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                drawContext.canvas.restore()
            }
        }
    }
}
