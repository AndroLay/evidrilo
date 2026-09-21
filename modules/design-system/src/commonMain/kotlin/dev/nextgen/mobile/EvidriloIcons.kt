package dev.nextgen.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

public enum class EvidriloIconName {
    ARROW_BACK,
    ARROW_FORWARD,
    CHEVRON_RIGHT,
    CHEVRON_DOWN,
    SETTINGS,
    CHECKLIST,
    LAYERS,
    HISTORY,
    FILE,
    BOOK,
    SHIELD,
    ACCOUNT,
    INFO,
    QUESTION,
    CHECK,
    ALERT,
    FOLDER,
    HOME,
    LINK,
    CALENDAR,
    MORE,
    CROWN,
    DATABASE,
    BELL,
    UPLOAD,
    LIST,
    PLUS,
    LOCK,
    SPARK,
    EVIDENCE_GRAPH,
    LIGHTNING,
    CHECK_FILLED,
    HOME_FILLED,
    ACCOUNT_FILLED,
    FOLDER_FILLED,
}

@Composable
public fun EvidriloIcon(
    name: EvidriloIconName,
    tint: Color = EvidriloColors.Ink,
    modifier: Modifier = Modifier.size(24.dp),
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        val strokeScale = ((scaleX + scaleY) / 2f).coerceAtLeast(0.01f)
        val stroke = Stroke(
            // The drawing commands use a 24 x 24 coordinate system. Compensate
            // for the canvas scale so icons keep a consistent visual stroke at
            // 16, 24, 26, and 48 dp instead of becoming filled blobs.
            width = 1.8.dp.toPx() / strokeScale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        scale(scaleX, scaleY, pivot = Offset.Zero) {
            when (name) {
                EvidriloIconName.ARROW_BACK -> drawArrowBack(tint, stroke)
                EvidriloIconName.ARROW_FORWARD -> drawArrowForward(tint, stroke)
                EvidriloIconName.CHEVRON_RIGHT -> drawChevronRight(tint, stroke)
                EvidriloIconName.CHEVRON_DOWN -> drawChevronDown(tint, stroke)
                EvidriloIconName.SETTINGS -> drawSettings(tint, stroke)
                EvidriloIconName.CHECKLIST -> drawChecklist(tint, stroke)
                EvidriloIconName.LAYERS -> drawLayers(tint, stroke)
                EvidriloIconName.HISTORY -> drawHistory(tint, stroke)
                EvidriloIconName.FILE -> drawFile(tint, stroke)
                EvidriloIconName.BOOK -> drawBook(tint, stroke)
                EvidriloIconName.SHIELD -> drawShield(tint, stroke)
                EvidriloIconName.ACCOUNT -> drawAccount(tint, stroke)
                EvidriloIconName.INFO -> drawInfo(tint, stroke)
                EvidriloIconName.QUESTION -> drawQuestion(tint, stroke)
                EvidriloIconName.CHECK -> drawCheck(tint, stroke)
                EvidriloIconName.ALERT -> drawAlert(tint, stroke)
                EvidriloIconName.FOLDER -> drawFolder(tint, stroke)
                EvidriloIconName.HOME -> drawHome(tint, stroke)
                EvidriloIconName.LINK -> drawLink(tint, stroke)
                EvidriloIconName.CALENDAR -> drawCalendar(tint, stroke)
                EvidriloIconName.MORE -> drawMore(tint)
                EvidriloIconName.CROWN -> drawCrown(tint, stroke)
                EvidriloIconName.DATABASE -> drawDatabase(tint, stroke)
                EvidriloIconName.BELL -> drawBell(tint, stroke)
                EvidriloIconName.UPLOAD -> drawUpload(tint, stroke)
                EvidriloIconName.LIST -> drawList(tint, stroke)
                EvidriloIconName.PLUS -> drawPlus(tint, stroke)
                EvidriloIconName.LOCK -> drawLock(tint, stroke)
                EvidriloIconName.SPARK -> drawSpark(tint, stroke)
                EvidriloIconName.EVIDENCE_GRAPH -> drawEvidenceGraph(tint, stroke)
                EvidriloIconName.LIGHTNING -> drawLightning(tint)
                EvidriloIconName.CHECK_FILLED -> drawFilledCheck(tint, stroke)
                EvidriloIconName.HOME_FILLED -> drawFilledHome(tint)
                EvidriloIconName.ACCOUNT_FILLED -> drawFilledAccount(tint)
                EvidriloIconName.FOLDER_FILLED -> drawFilledFolder(tint)
            }
        }
    }
}

private fun DrawScope.drawStyledLine(color: Color, start: Offset, end: Offset, stroke: Stroke) {
    drawLine(color, start, end, strokeWidth = stroke.width, cap = stroke.cap)
}

private fun DrawScope.drawArrowBack(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(20f, 12f), Offset(4f, 12f), stroke)
    drawStyledLine(color, Offset(4f, 12f), Offset(11f, 5f), stroke)
    drawStyledLine(color, Offset(4f, 12f), Offset(11f, 19f), stroke)
}

private fun DrawScope.drawArrowForward(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(4f, 12f), Offset(20f, 12f), stroke)
    drawStyledLine(color, Offset(20f, 12f), Offset(13f, 5f), stroke)
    drawStyledLine(color, Offset(20f, 12f), Offset(13f, 19f), stroke)
}

private fun DrawScope.drawChevronRight(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(9f, 5f), Offset(16f, 12f), stroke)
    drawStyledLine(color, Offset(16f, 12f), Offset(9f, 19f), stroke)
}

private fun DrawScope.drawChevronDown(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(5f, 9f), Offset(12f, 16f), stroke)
    drawStyledLine(color, Offset(12f, 16f), Offset(19f, 9f), stroke)
}

private fun DrawScope.drawSettings(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 8f, center = Offset(12f, 12f), style = stroke)
    drawCircle(color, radius = 2.5f, center = Offset(12f, 12f), style = stroke)
    repeat(8) { index ->
        val angle = index * (PI / 4.0)
        val start = Offset(
            x = 12f + (9.5f * cos(angle)).toFloat(),
            y = 12f + (9.5f * sin(angle)).toFloat(),
        )
        val end = Offset(
            x = 12f + (11f * cos(angle)).toFloat(),
            y = 12f + (11f * sin(angle)).toFloat(),
        )
        drawStyledLine(color, start, end, stroke)
    }
}

private fun DrawScope.drawChecklist(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(6f, 3f)
        lineTo(18f, 3f)
        lineTo(21f, 6f)
        lineTo(21f, 21f)
        lineTo(6f, 21f)
        close()
        moveTo(18f, 3f)
        lineTo(18f, 6f)
        lineTo(21f, 6f)
    }
    drawPath(path, color, style = stroke)
    drawStyledLine(color, Offset(9f, 11f), Offset(11f, 13f), stroke)
    drawStyledLine(color, Offset(11f, 13f), Offset(15f, 9f), stroke)
    drawStyledLine(color, Offset(9f, 17f), Offset(17f, 17f), stroke)
}

private fun DrawScope.drawLayers(color: Color, stroke: Stroke) {
    drawLayerShape(color, stroke, y = 6f)
    drawLayerShape(color, stroke, y = 11f)
    drawLayerShape(color, stroke, y = 16f)
}

private fun DrawScope.drawLayerShape(color: Color, stroke: Stroke, y: Float) {
    val path = Path().apply {
        moveTo(12f, y - 4f)
        lineTo(21f, y)
        lineTo(12f, y + 4f)
        lineTo(3f, y)
        close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawHistory(color: Color, stroke: Stroke) {
    drawArc(
        color = color,
        startAngle = -55f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(3f, 3f),
        size = Size(18f, 18f),
        style = stroke,
    )
    drawStyledLine(color, Offset(4f, 8f), Offset(4f, 14f), stroke)
    drawStyledLine(color, Offset(4f, 14f), Offset(9f, 14f), stroke)
    drawStyledLine(color, Offset(17f, 3f), Offset(21f, 3f), stroke)
    drawStyledLine(color, Offset(21f, 3f), Offset(21f, 7f), stroke)
}

private fun DrawScope.drawFile(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(6f, 3f)
        lineTo(16f, 3f)
        lineTo(21f, 8f)
        lineTo(21f, 21f)
        lineTo(6f, 21f)
        close()
        moveTo(16f, 3f)
        lineTo(16f, 8f)
        lineTo(21f, 8f)
    }
    drawPath(path, color, style = stroke)
    drawStyledLine(color, Offset(9f, 13f), Offset(17f, 13f), stroke)
    drawStyledLine(color, Offset(9f, 17f), Offset(15f, 17f), stroke)
}

private fun DrawScope.drawBook(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(4f, 5f)
        cubicTo(7f, 3f, 9f, 4f, 12f, 6f)
        cubicTo(15f, 4f, 17f, 3f, 20f, 5f)
        lineTo(20f, 20f)
        cubicTo(17f, 18f, 15f, 18f, 12f, 20f)
        cubicTo(9f, 18f, 7f, 18f, 4f, 20f)
        close()
        moveTo(12f, 6f)
        lineTo(12f, 20f)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawShield(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(12f, 3f)
        lineTo(20f, 6f)
        lineTo(19f, 13f)
        cubicTo(18f, 17f, 15f, 20f, 12f, 21f)
        cubicTo(9f, 20f, 6f, 17f, 5f, 13f)
        lineTo(4f, 6f)
        close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawAccount(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 4f, center = Offset(12f, 7f), style = stroke)
    val path = Path().apply {
        moveTo(4f, 21f)
        cubicTo(4f, 16f, 8f, 14f, 12f, 14f)
        cubicTo(16f, 14f, 20f, 16f, 20f, 21f)
        close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawInfo(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke)
    drawCircle(color, radius = 1f, center = Offset(12f, 7f))
    drawStyledLine(color, Offset(12f, 11f), Offset(12f, 17f), stroke)
}

private fun DrawScope.drawQuestion(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke)
    val path = Path().apply {
        moveTo(9.5f, 9.5f)
        cubicTo(9.5f, 6.5f, 14.5f, 6.5f, 14.5f, 9.5f)
        cubicTo(14.5f, 11.5f, 12f, 11.5f, 12f, 14f)
    }
    drawPath(path, color, style = stroke)
    drawCircle(color, radius = 1f, center = Offset(12f, 18f))
}

private fun DrawScope.drawCheck(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke)
    drawStyledLine(color, Offset(7f, 12f), Offset(10.5f, 15.5f), stroke)
    drawStyledLine(color, Offset(10.5f, 15.5f), Offset(17f, 8.5f), stroke)
}

private fun DrawScope.drawAlert(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 9f, center = Offset(12f, 12f), style = stroke)
    drawStyledLine(color, Offset(12f, 7f), Offset(12f, 13f), stroke)
    drawCircle(color, radius = 1f, center = Offset(12f, 17f))
}

private fun DrawScope.drawFolder(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(3f, 7f)
        lineTo(9f, 7f)
        lineTo(11f, 9f)
        lineTo(21f, 9f)
        lineTo(19f, 20f)
        lineTo(4f, 20f)
        close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawHome(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(4f, 11f)
        lineTo(12f, 4f)
        lineTo(20f, 11f)
        lineTo(19f, 20f)
        lineTo(5f, 20f)
        close()
    }
    drawPath(path, color, style = stroke)
    drawStyledLine(color, Offset(10f, 20f), Offset(10f, 14f), stroke)
    drawStyledLine(color, Offset(10f, 14f), Offset(14f, 14f), stroke)
    drawStyledLine(color, Offset(14f, 14f), Offset(14f, 20f), stroke)
}

private fun DrawScope.drawLink(color: Color, stroke: Stroke) {
    drawArc(color, 135f, 180f, false, Offset(2f, 8f), Size(10f, 8f), style = stroke)
    drawArc(color, -45f, 180f, false, Offset(12f, 8f), Size(10f, 8f), style = stroke)
    drawStyledLine(color, Offset(8f, 16f), Offset(16f, 8f), stroke)
}

private fun DrawScope.drawCalendar(color: Color, stroke: Stroke) {
    drawRoundRect(color, Offset(4f, 5f), Size(16f, 15f), CornerRadius(2f, 2f), style = stroke)
    drawStyledLine(color, Offset(4f, 9f), Offset(20f, 9f), stroke)
    drawStyledLine(color, Offset(8f, 3f), Offset(8f, 7f), stroke)
    drawStyledLine(color, Offset(16f, 3f), Offset(16f, 7f), stroke)
}

private fun DrawScope.drawMore(color: Color) {
    drawCircle(color, 1.7f, Offset(6f, 12f))
    drawCircle(color, 1.7f, Offset(12f, 12f))
    drawCircle(color, 1.7f, Offset(18f, 12f))
}

private fun DrawScope.drawCrown(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(4f, 7f)
        lineTo(8f, 11f)
        lineTo(12f, 5f)
        lineTo(16f, 11f)
        lineTo(20f, 7f)
        lineTo(18f, 18f)
        lineTo(6f, 18f)
        close()
    }
    drawPath(path, color, style = stroke)
    drawStyledLine(color, Offset(7f, 21f), Offset(17f, 21f), stroke)
}

private fun DrawScope.drawDatabase(color: Color, stroke: Stroke) {
    drawOval(color, Offset(4f, 3f), Size(16f, 6f), style = stroke)
    drawArc(color, 0f, 180f, false, Offset(4f, 6f), Size(16f, 6f), style = stroke)
    drawArc(color, 0f, 180f, false, Offset(4f, 12f), Size(16f, 6f), style = stroke)
    drawStyledLine(color, Offset(4f, 6f), Offset(4f, 18f), stroke)
    drawStyledLine(color, Offset(20f, 6f), Offset(20f, 18f), stroke)
}

private fun DrawScope.drawBell(color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(5f, 17f)
        lineTo(7f, 14f)
        lineTo(7f, 10f)
        cubicTo(7f, 4f, 17f, 4f, 17f, 10f)
        lineTo(17f, 14f)
        lineTo(19f, 17f)
        close()
    }
    drawPath(path, color, style = stroke)
    drawCircle(color, 1.4f, Offset(12f, 20f))
}

private fun DrawScope.drawUpload(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(12f, 17f), Offset(12f, 5f), stroke)
    drawStyledLine(color, Offset(7f, 10f), Offset(12f, 5f), stroke)
    drawStyledLine(color, Offset(12f, 5f), Offset(17f, 10f), stroke)
    drawStyledLine(color, Offset(5f, 19f), Offset(19f, 19f), stroke)
}

private fun DrawScope.drawList(color: Color, stroke: Stroke) {
    listOf(7f, 12f, 17f).forEach { y ->
        drawCircle(color, 1f, Offset(5f, y))
        drawStyledLine(color, Offset(9f, y), Offset(19f, y), stroke)
    }
}

private fun DrawScope.drawPlus(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(12f, 5f), Offset(12f, 19f), stroke)
    drawStyledLine(color, Offset(5f, 12f), Offset(19f, 12f), stroke)
}

private fun DrawScope.drawLock(color: Color, stroke: Stroke) {
    drawRoundRect(color, Offset(5f, 10f), Size(14f, 11f), CornerRadius(2f, 2f), style = stroke)
    drawArc(color, 180f, 180f, false, Offset(8f, 4f), Size(8f, 10f), style = stroke)
}

private fun DrawScope.drawSpark(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(12f, 3f), Offset(12f, 21f), stroke)
    drawStyledLine(color, Offset(3f, 12f), Offset(21f, 12f), stroke)
    drawStyledLine(color, Offset(6f, 6f), Offset(18f, 18f), stroke)
    drawStyledLine(color, Offset(18f, 6f), Offset(6f, 18f), stroke)
}

private fun DrawScope.drawEvidenceGraph(color: Color, stroke: Stroke) {
    drawStyledLine(color, Offset(12f, 7f), Offset(6f, 17f), stroke)
    drawStyledLine(color, Offset(12f, 7f), Offset(18f, 17f), stroke)
    drawCircle(color, radius = 2.8f, center = Offset(12f, 7f), style = stroke)
    drawCircle(color, radius = 2.8f, center = Offset(6f, 17f), style = stroke)
    drawCircle(color, radius = 2.8f, center = Offset(18f, 17f), style = stroke)
}

private fun DrawScope.drawLightning(color: Color) {
    val path = Path().apply {
        moveTo(13.7f, 2.5f)
        lineTo(5.5f, 13f)
        lineTo(11.2f, 13f)
        lineTo(10.2f, 21.5f)
        lineTo(18.5f, 10.2f)
        lineTo(12.9f, 10.2f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawFilledCheck(color: Color, stroke: Stroke) {
    drawCircle(color, radius = 9f, center = Offset(12f, 12f))
    drawStyledLine(Color.White, Offset(7f, 12f), Offset(10.5f, 15.5f), stroke)
    drawStyledLine(Color.White, Offset(10.5f, 15.5f), Offset(17f, 8.5f), stroke)
}

private fun DrawScope.drawFilledHome(color: Color) {
    val path = Path().apply {
        moveTo(3f, 10.5f)
        lineTo(12f, 3f)
        lineTo(21f, 10.5f)
        lineTo(19.5f, 21f)
        lineTo(4.5f, 21f)
        close()
    }
    drawPath(path, color)
    drawRoundRect(Color.White, Offset(10f, 14f), Size(4f, 7f), CornerRadius(1f, 1f))
}

private fun DrawScope.drawFilledAccount(color: Color) {
    drawCircle(color, radius = 4.2f, center = Offset(12f, 7f))
    val path = Path().apply {
        moveTo(3.5f, 21f)
        cubicTo(3.5f, 15.7f, 7.4f, 13.2f, 12f, 13.2f)
        cubicTo(16.6f, 13.2f, 20.5f, 15.7f, 20.5f, 21f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawFilledFolder(color: Color) {
    val path = Path().apply {
        moveTo(2.5f, 7f)
        lineTo(9f, 7f)
        lineTo(11.2f, 9.2f)
        lineTo(21.5f, 9.2f)
        lineTo(19.5f, 20.5f)
        lineTo(4f, 20.5f)
        close()
    }
    drawPath(path, color)
}
