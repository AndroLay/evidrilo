package dev.nextgen.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.practice.*
import kotlin.math.round

internal fun courseIcon(id: PracticeLessonId): EvidriloIconName = when (id) {
    PracticeLessonId.TABLET -> EvidriloIconName.EVIDENCE_GRAPH
    PracticeLessonId.STUDIES -> EvidriloIconName.BOOK
    PracticeLessonId.SURVEY -> EvidriloIconName.FILE
}

@Composable
internal fun CoursePathMap(course: PracticeCourseState) {
    val completed = course.sessions.values.count { it.stage == PracticeLessonStage.COMPLETE }
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        contentDescription = completed.toString() + " of three practice attempts completed. Completion is not a mastery score."
    }, verticalAlignment = Alignment.CenterVertically) {
        PracticeLessonId.entries.forEachIndexed { index, id ->
            if (index > 0) HorizontalDivider(Modifier.weight(1f).padding(horizontal = 12.dp), color = EvidriloColors.Separator)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(48.dp).background(EvidriloColors.Tint, CircleShape), contentAlignment = Alignment.Center) {
                    EvidriloIcon(if (course.sessions[id]?.stage == PracticeLessonStage.COMPLETE) EvidriloIconName.CHECK else courseIcon(id),
                        tint = EvidriloColors.Cobalt)
                }
                Text(when (id) {
                    PracticeLessonId.TABLET -> "Observe"
                    PracticeLessonId.STUDIES -> "Compare"
                    PracticeLessonId.SURVEY -> "Reconsider"
                }, style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
            }
        }
    }
}

@Composable
internal fun CourseProgress(session: PracticeLessonSession, revisionPart: Int) {
    val target = when (session.stage) {
        PracticeLessonStage.MISSION -> .06f
        PracticeLessonStage.PREDICTION -> .12f
        PracticeLessonStage.INSPECT -> .24f
        PracticeLessonStage.ORGANIZE -> .34f
        PracticeLessonStage.CLAIM -> .44f
        PracticeLessonStage.SCOPE -> .51f
        PracticeLessonStage.LIMITS -> .58f
        PracticeLessonStage.ACTION -> .65f
        PracticeLessonStage.FEEDBACK -> .71f
        PracticeLessonStage.CHANGE -> .77f
        PracticeLessonStage.REVISION -> .81f + revisionPart * .03f
        PracticeLessonStage.FINAL_REVIEW -> .97f
        PracticeLessonStage.COMPLETE -> 1f
    }
    val progress by animateFloatAsState(target, tween(220), label = "courseProgress")
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).semantics {
        contentDescription = "Investigation progress"
    }, color = EvidriloColors.Cobalt, trackColor = EvidriloColors.Separator)
}

@Composable
internal fun CourseEvidence(id: PracticeLessonId, draft: PracticeLessonDraft, changed: Boolean, edit: (PracticeLessonDraft) -> Unit) {
    if (id == PracticeLessonId.STUDIES) {
        val cards = PracticeCourseContent.studies + if (changed) listOf(PracticeCourseContent.fourthStudy) else emptyList()
        cards.forEach { card -> CourseStudyCard(card, card.id in draft.evidence, {
            edit(draft.copy(evidence = if (card.id in draft.evidence) draft.evidence - card.id else draft.evidence + card.id))
        }) }
        if (changed) Text("D adds a different recall finding: no observed difference at 14 days. Its new context stays visible alongside A and B.",
            style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        CourseEvidenceLinks(cards.map { it.id }, draft.evidence)
    } else {
        CourseSurveyChart(changed)
        val rows = if (changed) PracticeCourseContent.correctedResponses else PracticeCourseContent.responses
        val quiet = rows.count { it.quiet }
        listOf(PracticeCourseContent.QUIET to ("Quiet space" to quiet), PracticeCourseContent.GROUP to ("Group space" to rows.size - quiet)).forEach { (anchor, copy) ->
            PracticeChoice(copy.first, copy.second.toString() + "/" + rows.size + if (changed) " unique respondents" else " submitted rows",
                anchor in draft.evidence, { edit(draft.copy(evidence = if (anchor in draft.evidence) draft.evidence - anchor else draft.evidence + anchor)) }, checkbox = true)
        }
        var inspectRows by rememberSaveable(changed) { mutableStateOf(false) }
        TextButton({ inspectRows = !inspectRows }) { Text(if (inspectRows) "Hide the anonymous rows" else "Inspect the anonymous rows") }
        AnimatedVisibility(inspectRows) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { response ->
                    Text(response.id + " · respondent " + response.respondent + " · " + if (response.quiet) "quiet space" else "group space",
                        style = MaterialTheme.typography.bodyMedium)
                }
                Text("Fictional identifiers. The sample is small and self-selected.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
        }
        CourseEvidenceLinks(listOf(PracticeCourseContent.QUIET, PracticeCourseContent.GROUP), draft.evidence)
    }
}

@Composable
internal fun CourseStudyCard(card: PracticeStudyCard, selected: Boolean, onToggle: (() -> Unit)?) {
    var expanded by rememberSaveable(card.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onToggle != null) PracticeChoice(card.title, card.result, selected, onToggle, checkbox = true)
        else Column(Modifier.fillMaxWidth().background(EvidriloColors.Tint, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Text(card.result, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton({ expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (expanded) "Hide " + card.id + " details" else "Read context, method and limit") }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Context" to card.context, "Method" to card.method, "Limit" to card.limitation).forEach { (label, value) ->
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                }
                Text(card.id + " · synthetic practice material", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Cobalt)
            }
        }
    }
}

@Composable
internal fun CourseStudyBoard(draft: PracticeLessonDraft, edit: (PracticeLessonDraft) -> Unit) {
    PracticeCourseContent.studies.forEach { card ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium)
            Text(card.result, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PracticeEvidenceGroup.entries.forEach { group ->
                    FilterChip(
                        selected = draft.groups[card.id] == group,
                        onClick = { edit(draft.copy(groups = draft.groups + (card.id to group))) },
                        label = { Text(when (group) {
                            PracticeEvidenceGroup.SIMILAR -> "Similar"
                            PracticeEvidenceGroup.DIFFERENT -> "Different"
                            PracticeEvidenceGroup.CONTEXT -> "Context"
                        }, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                            contentDescription = card.id + ": " + when (group) {
                                PracticeEvidenceGroup.SIMILAR -> "similar finding"
                                PracticeEvidenceGroup.DIFFERENT -> "different finding"
                                PracticeEvidenceGroup.CONTEXT -> "context, a different outcome"
                            }
                        },
                    )
                }
            }
            HorizontalDivider(color = EvidriloColors.Separator)
        }
    }
    Text("Similar describes a reported direction; it does not mean the methods or effect sizes are interchangeable.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
}

@Composable
internal fun CourseSelectedLinks(id: PracticeLessonId, draft: PracticeLessonDraft, changed: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Connected records · " + if (changed) "changed set" else "initial set", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
        if (draft.evidence.isEmpty()) Text("No record connected.", style = MaterialTheme.typography.bodyMedium)
        draft.evidence.sorted().forEach { anchor ->
            Text(if (id == PracticeLessonId.STUDIES) {
                (PracticeCourseContent.studies + PracticeCourseContent.fourthStudy).firstOrNull { it.id == anchor }?.let { it.id + " · " + it.result } ?: (anchor + " · unavailable")
            } else when (anchor) {
                PracticeCourseContent.QUIET -> if (changed) "COUNT-QUIET · 4/7 unique respondents" else "COUNT-QUIET · 5/8 submitted rows"
                PracticeCourseContent.GROUP -> if (changed) "COUNT-GROUP · 3/7 unique respondents" else "COUNT-GROUP · 3/8 submitted rows"
                else -> anchor + " · unavailable"
            }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun CourseSurveyChart(changed: Boolean) {
    val active = if (changed) PracticeCourseContent.correctedResponses else PracticeCourseContent.responses
    val previous = PracticeCourseContent.responses
    Column(Modifier.fillMaxWidth().background(EvidriloColors.Tint, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(if (changed) "After removing R07 · 7 unique respondents" else "Initial survey · 8 submitted rows", style = MaterialTheme.typography.titleMedium)
        Text("One fictional campus · self-selected sample", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        listOf(true to "Quiet space", false to "Group space").forEach { (quiet, label) ->
            val count = active.count { it.quiet == quiet }
            val before = previous.count { it.quiet == quiet }
            val ratio = count.toFloat() / active.size
            val animation = remember(changed, quiet) { Animatable(before.toFloat() / previous.size) }
            LaunchedEffect(changed, quiet) { animation.animateTo(ratio, tween(280)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(count.toString() + "/" + active.size + " · " + (round(ratio * 1000) / 10).toString() + "%", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(EvidriloColors.Separator, CircleShape)) {
                    Box(Modifier.fillMaxWidth(animation.value.coerceIn(0f, 1f)).height(10.dp).background(EvidriloColors.Cobalt, CircleShape))
                }
                if (changed) Text("Before: " + before + "/" + previous.size + " submitted rows", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
        }
        Text("Share of the active set · 0–100%", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
}

@Composable
private fun CourseEvidenceLinks(anchors: List<String>, selected: Set<String>) {
    val progress = anchors.map { id ->
        val value by animateFloatAsState(if (id in selected) 1f else 0f, tween(220), label = "courseEvidenceLink")
        value
    }
    val ink = EvidriloColors.Cobalt
    val track = EvidriloColors.Separator
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(42.dp)) {
            anchors.forEachIndexed { index, _ ->
                val x = size.width * (index + .5f) / anchors.size
                val end = Offset(size.width / 2, size.height - 4.dp.toPx())
                val path = Path().apply {
                    moveTo(x, 4.dp.toPx())
                    cubicTo(x, size.height * .65f, end.x, size.height * .3f, end.x, end.y)
                }
                drawPath(path, track, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                val measure = PathMeasure().apply { setPath(path, false) }
                val segment = Path()
                measure.getSegment(0f, measure.length * progress[index], segment, true)
                drawPath(segment, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(if (progress[index] > 0) ink else track, 3.5.dp.toPx(), Offset(x, 4.dp.toPx()))
            }
        }
        Text(anchors.count { it in selected }.toString() + " records connected · selection is not a correctness score",
            style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}
