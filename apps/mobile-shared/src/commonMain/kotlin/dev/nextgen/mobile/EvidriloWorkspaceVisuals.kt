package dev.nextgen.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases

/** Original process diagrams: each family gets its own structure, not a verdict or sample data. */
@Composable
internal fun EvidriloFamilyArtwork(family: ProjectTemplateFamily, modifier: Modifier = Modifier) {
    val color = EvidriloColors.Cobalt
    val track = EvidriloColors.PatternBlue
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = EvidriloColors.Tint) {
        Box(Modifier.fillMaxWidth().height(92.dp)) {
            Canvas(Modifier.fillMaxSize().padding(18.dp).clearAndSetSemantics {}) {
                val w = size.width; val h = size.height
                fun link(a: Offset, b: Offset) = drawLine(track, a, b, 3.dp.toPx(), StrokeCap.Round)
                fun point(x: Float, y: Float) { drawCircle(color, 5.dp.toPx(), Offset(x*w,y*h)) }
                when (family) {
                    ProjectTemplateFamily.EXPERIMENTAL_LABORATORY -> {
                        val p=Path().apply { moveTo(0f,h*.8f); cubicTo(w*.3f,h*.8f,w*.4f,h*.2f,w*.65f,h*.2f); lineTo(w,h*.2f) }
                        drawPath(p,color, style=Stroke(3.dp.toPx(), cap=StrokeCap.Round));point(.15f,.8f);point(.65f,.2f)
                    }
                    ProjectTemplateFamily.OBSERVATIONAL_SURVEY -> listOf(.2f,.4f,.7f,.5f,.85f).forEachIndexed { i,v ->
                        drawLine(color,Offset(w*i/5+w*.08f,h),Offset(w*i/5+w*.08f,h*(1-v)),8.dp.toPx(),StrokeCap.Round)
                    }
                    ProjectTemplateFamily.LITERATURE_REVIEW -> {
                        listOf(.15f,.5f,.85f).forEach { y -> link(Offset(w*.15f,h*y),Offset(w*.8f,h*.5f));point(.15f,y) };point(.8f,.5f)
                    }
                    ProjectTemplateFamily.QUALITATIVE_INTERVIEW_FIELD_STUDY -> {
                        listOf(.2f,.8f).forEach { x -> link(Offset(w*x,h*.2f),Offset(w*.5f,h*.75f));point(x,.2f) };point(.5f,.75f)
                    }
                    ProjectTemplateFamily.DESIGN_ENGINEERING -> {
                        val p=Path().apply { moveTo(w*.2f,h*.3f); cubicTo(w*.2f,-h*.2f,w*.8f,-h*.2f,w*.8f,h*.5f); cubicTo(w*.8f,h*1.2f,w*.2f,h*1.2f,w*.2f,h*.5f) }
                        drawPath(p,color,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round));point(.2f,.3f);point(.8f,.5f)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EvidriloCasesEntry(onOpen: () -> Unit) {
    EvidriloPressableCard(onClick = onOpen, faceColor = EvidriloColors.Atmosphere,
        borderColor = EvidriloColors.Atmosphere, lipColor = EvidriloColors.Tint) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EvidriloIcon(EvidriloIconName.EVIDENCE_GRAPH, tint = EvidriloColors.Cobalt, modifier = Modifier.size(38.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText("Case library"), style = MaterialTheme.typography.titleLarge)
                Text(uiText("See how evidence changes a claim."), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
            }
            EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = EvidriloColors.Cobalt)
        }
    }
}

@Composable
internal fun EvidriloCasesScreen(case: ConclusionCase, hasPro: Boolean, accessChecking: Boolean, onOpenCase: () -> Unit, onOpenProCases: () -> Unit, onBack: () -> Unit) {
    EvidriloContentColumn(includeBottomSafeArea = false) {
        EvidriloBackGesture("Home", onBack)
        EvidriloPageHeading("Cases worth questioning.", "Explore a worked investigation at your own pace.")
        EvidriloFamilyArtwork(ProjectTemplateFamily.LITERATURE_REVIEW)
        EvidriloPressableCard(onClick = onOpenCase, faceColor = EvidriloColors.Atmosphere, borderColor = EvidriloColors.Atmosphere, lipColor = EvidriloColors.Tint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Free · synthetic case"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Cobalt)
                EvidriloIcon(EvidriloIconName.EVIDENCE_GRAPH, tint = EvidriloColors.Cobalt)
            }
            Text(case.title, style = MaterialTheme.typography.titleLarge)
            Text(uiText("Read the observations. Trace a claim. Reconsider it when evidence changes."), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
        EvidriloPrimaryButton("Open worked case", onOpenCase, trailingIcon = EvidriloIconName.ARROW_FORWARD)
        Text(uiText("Go deeper"), style = MaterialTheme.typography.titleLarge)
        EvidriloPressableCard(onClick = onOpenProCases, faceColor = EvidriloColors.Atmosphere, borderColor = EvidriloColors.Atmosphere, lipColor = EvidriloColors.Tint) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                EvidriloProEmblem(44.dp)
                Column(Modifier.weight(1f)) {
                    Text("${ConclusionCases.premium.size} premium investigations", style = MaterialTheme.typography.titleMedium)
                    Text(if (accessChecking) "Checking your Pro access…" else if (hasPro) "Review your Pro case collection" else "Explore with verified Pro access", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                EvidriloIcon(if (accessChecking) EvidriloIconName.HISTORY else if (hasPro) EvidriloIconName.ARROW_FORWARD else EvidriloIconName.LOCK, tint = EvidriloColors.Cobalt)
            }
            ConclusionCases.premium.forEach { Text(it.title, style = MaterialTheme.typography.bodyMedium) }
        }
        EvidriloExplanation("Cases and Practice", "Cases let you explore supplied evidence and an explainable claim/action workflow. Practice guides you through three short investigations. These synthetic learning records stay separate from your own projects; they are not sources you collected or a measure of mastery.")
    }
}
