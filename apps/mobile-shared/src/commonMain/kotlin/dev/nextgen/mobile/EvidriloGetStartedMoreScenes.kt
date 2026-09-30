package dev.nextgen.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun GetStartedAiScene(preview: GetStartedSceneSelections, height: Dp) {
    var showAvailability by remember { mutableStateOf(false) }
    GetStartedAtmosphere(Modifier.fillMaxWidth().heightIn(min = height)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(EvidriloColors.Tint), contentAlignment = Alignment.Center) {
                    EvidriloIcon(EvidriloIconName.CHAT_BUBBLE, tint = EvidriloColors.Cobalt, modifier = Modifier.size(26.dp))
                }
                Column {
                    Text("Example chat", style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Ink)
                    Text("Choose the help you need", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
            }
            GetStartedChoices(getStartedAiExamples.map { it.label }, preview.aiPrompt.value, { preview.aiPrompt.value = it }, "Choose an example conversation")
            AnimatedContent(
                preview.aiPrompt.value,
                transitionSpec = { (slideInVertically(tween(350)) { it / 5 } + scaleIn(spring(), initialScale = .92f) + fadeIn(tween(250)))
                    .togetherWith(slideOutVertically(tween(180)) { -it / 8 } + fadeOut(tween(140))) },
                label = "Example chat change",
            ) { selected ->
                val example = getStartedAiExamples[selected]
                val answer = rememberGetStartedReveal(selected, 650)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.align(Alignment.End).widthIn(max = 290.dp), color = EvidriloColors.PrimaryAction, shape = RoundedCornerShape(19.dp, 19.dp, 5.dp, 19.dp)) {
                        Text(example.question, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.White)
                    }
                    Surface(
                        Modifier.align(Alignment.Start).widthIn(max = 300.dp).graphicsLayer { alpha = answer.value; translationY = (1 - answer.value) * 18.dp.toPx() },
                        color = EvidriloColors.Card, shape = RoundedCornerShape(5.dp, 19.dp, 19.dp, 19.dp), shadowElevation = 3.dp,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("A possible next step", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                            Text(example.suggestion, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Ink)
                        }
                    }
                }
            }
            TextButton(onClick = { showAvailability = true }) {
                EvidriloIcon(EvidriloIconName.INFO, tint = EvidriloColors.Slate, modifier = Modifier.size(18.dp))
                Text("AI availability", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Slate)
            }
        }
    }
    if (showAvailability) {
        AlertDialog(
            onDismissRequest = { showAvailability = false },
            containerColor = EvidriloColors.Card,
            titleContentColor = EvidriloColors.Ink,
            textContentColor = EvidriloColors.Slate,
            title = { Text("You stay in charge") },
            text = {
                Text("AI is optional. It needs an eligible account and enabled services. Project assistance also needs a supported, published template.\n\nGeneral chat does not automatically send your project. You choose what to share. Suggestions are not scientific validation.\n\nThis introduction only shows example conversations.", modifier = Modifier.verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { showAvailability = false }) { Text("Got it") } },
        )
    }
}

@Composable
internal fun GetStartedPortabilityScene(preview: GetStartedSceneSelections, height: Dp) {
    val format = GetStartedExportFormat.entries[preview.export.value]
    GetStartedAtmosphere(Modifier.fillMaxWidth().heightIn(min = height)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height((height - 165.dp).coerceAtLeast(170.dp)), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    preview.export.value,
                    transitionSpec = { (slideInVertically(tween(340)) { -it / 3 } + scaleIn(spring(), initialScale = .78f) + fadeIn(tween(220)))
                        .togetherWith(slideOutVertically(tween(220)) { it / 3 } + scaleOut(tween(220), targetScale = .8f) + fadeOut(tween(170))) },
                    label = "Transform the export document",
                ) { selected ->
                    GetStartedDocument(GetStartedExportFormat.entries[selected])
                }
                Row(Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 6.dp).clip(RoundedCornerShape(12.dp)).background(EvidriloColors.Tint).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EvidriloIcon(EvidriloIconName.HISTORY, tint = EvidriloColors.Cobalt, modifier = Modifier.size(17.dp))
                    Text("Local revisions", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Ink)
                }
            }
            GetStartedChoices(GetStartedExportFormat.entries.map { it.label }, preview.export.value, { preview.export.value = it }, "Choose a format preview", Modifier.fillMaxWidth())
            Text(format.detail, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = EvidriloColors.Slate)
            Text("Format preview · no file is created", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Slate)
        }
    }
}

@Composable
private fun GetStartedDocument(format: GetStartedExportFormat) {
    val paper = EvidriloColors.Card
    val shadow = EvidriloColors.PatternBlue
    val pale = EvidriloColors.Tint
    val cobalt = EvidriloColors.PrimaryAction
    val entry = rememberGetStartedReveal(format, 800)
    Box(Modifier.size(166.dp, 170.dp).graphicsLayer { rotationZ = -8f * (1 - entry.value) }) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val left = 20.dp.toPx()
            val top = 10.dp.toPx()
            val width = size.width - 36.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            if (format == GetStartedExportFormat.PROJECT) {
                val folder = Path().apply {
                    moveTo(left, top + 25.dp.toPx()); lineTo(left + 47.dp.toPx(), top + 25.dp.toPx()); lineTo(left + 64.dp.toPx(), top + 42.dp.toPx())
                    lineTo(left + width, top + 42.dp.toPx()); lineTo(left + width, bottom); lineTo(left, bottom); close()
                }
                drawPath(folder, shadow)
                drawRoundRect(cobalt, Offset(left - 6.dp.toPx(), top + 56.dp.toPx()), Size(width + 12.dp.toPx(), bottom - top - 48.dp.toPx()), CornerRadius(15.dp.toPx()))
            } else {
                drawRoundRect(shadow.copy(alpha = .5f), Offset(left + 5.dp.toPx(), top + 7.dp.toPx()), Size(width, bottom - top), CornerRadius(13.dp.toPx()))
                val fold = 29.dp.toPx()
                val document = Path().apply {
                    moveTo(left + 12.dp.toPx(), top); lineTo(left + width - fold, top); lineTo(left + width, top + fold)
                    lineTo(left + width, bottom - 12.dp.toPx()); quadraticBezierTo(left + width, bottom, left + width - 12.dp.toPx(), bottom)
                    lineTo(left + 12.dp.toPx(), bottom); quadraticBezierTo(left, bottom, left, bottom - 12.dp.toPx())
                    lineTo(left, top + 12.dp.toPx()); quadraticBezierTo(left, top, left + 12.dp.toPx(), top); close()
                }
                drawPath(document, paper)
                val corner = Path().apply { moveTo(left + width - fold, top); lineTo(left + width - fold, top + fold); lineTo(left + width, top + fold); close() }
                drawPath(corner, pale)
                repeat(3) { row ->
                    drawRoundRect(pale, Offset(left + 17.dp.toPx(), top + (48 + row * 15).dp.toPx()), Size((if (row == 2) 48 else 77).dp.toPx(), 5.dp.toPx()), CornerRadius(3.dp.toPx()))
                }
            }
        }
        if (format == GetStartedExportFormat.PROJECT) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EvidriloIcon(EvidriloIconName.FOLDER, tint = EvidriloColors.White, modifier = Modifier.size(32.dp))
                Text(".evproj", style = MaterialTheme.typography.titleMedium, color = EvidriloColors.White)
            }
        } else {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 21.dp).clip(RoundedCornerShape(10.dp)).background(cobalt).padding(horizontal = 16.dp, vertical = 11.dp)) {
                Text(if (format == GetStartedExportFormat.MARKDOWN) "MD" else format.label, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.White)
            }
        }
    }
}

@Composable
internal fun GetStartedReadyScene(preview: GetStartedSceneSelections, height: Dp) {
    val entry = rememberGetStartedReveal(preview.celebration.value, 900)
    GetStartedAtmosphere(
        Modifier.fillMaxWidth().height(height).clickable(role = Role.Button, onClickLabel = "Replay the welcome celebration") { preview.celebration.value++ }
            .semantics(mergeDescendants = true) { contentDescription = "Your next project in Evidrilo. Tap to replay the paper celebration." },
    ) {
        Box(Modifier.align(Alignment.Center).size(252.dp).clip(CircleShape).background(EvidriloColors.Tint.copy(alpha = .6f)))
        Surface(
            Modifier.align(Alignment.Center).width(228.dp).graphicsLayer { rotationZ = -7f; translationY = 38.dp.toPx(); scaleX = .85f + entry.value * .15f; scaleY = scaleX },
            shape = RoundedCornerShape(24.dp), color = EvidriloColors.PatternBlue,
        ) { Box(Modifier.height(176.dp)) }
        Surface(
            Modifier.align(Alignment.Center).width(228.dp).graphicsLayer { rotationZ = 3f; translationY = 26.dp.toPx() + (1f - entry.value) * 40.dp.toPx(); alpha = entry.value },
            shape = RoundedCornerShape(24.dp), color = EvidriloColors.Card, shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("YOUR NEXT PROJECT", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
                Text("An idea worth\nexploring.", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EvidriloIcon(EvidriloIconName.QUESTION, tint = EvidriloColors.Cobalt, modifier = Modifier.size(20.dp))
                    Text("Start with your question", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter).padding(top = 7.dp).size(100.dp).graphicsLayer { scaleX = .6f + entry.value * .4f; scaleY = scaleX }) {
            EvidriloLogoMark(size = 100.dp)
        }
        GetStartedPaperBurst(preview.celebration.value, Modifier.matchParentSize())
        Text("Tap for another little celebration", Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
}
