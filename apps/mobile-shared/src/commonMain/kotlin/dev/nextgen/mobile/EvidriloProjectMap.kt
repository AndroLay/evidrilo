package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.max
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.nextgen.mobile.domain.project.*

internal data class ProjectMapNode(val key: String, val lane: Int, val kind: String, val text: String, val placeholder: Boolean = false)
internal data class ProjectMapEdge(val from: String, val to: String, val label: String, val rationale: String = "")
internal data class ProjectMapData(val nodes: List<ProjectMapNode>, val edges: List<ProjectMapEdge>, val invalidLinks: Int)

/** Derives only explicit student-recorded links. Never infers academic support from proximity. */
internal fun projectMapData(draft: StudentProjectDraft): ProjectMapData {
    val nodes = buildList {
        draft.sources.forEach { add(ProjectMapNode("source:${it.id}",0,"Source",it.title.ifBlank { "Untitled source" },it.title.isBlank())) }
        draft.evidenceItems.forEach { add(ProjectMapNode("note:${it.id}",1,"Evidence note",it.excerpt.ifBlank { "Empty note" },it.excerpt.isBlank())) }
        draft.findings.forEach { add(ProjectMapNode("finding:${it.id}",2,"Finding",it.statement.ifBlank { "Empty finding" },it.statement.isBlank())) }
        draft.themes.forEach { add(ProjectMapNode("theme:${it.id}",2,"Comparison",it.title.ifBlank { "Untitled comparison" },it.title.isBlank())) }
        StudentProjectDraftRules.effectiveClaims(draft).forEach { add(ProjectMapNode("claim:${it.id}",3,"Claim",it.statement.ifBlank { "Empty claim" },it.statement.isBlank())) }
        draft.limitationActions.forEach { add(ProjectMapNode("action:${it.id}",4,"Boundary / next action",listOf(it.boundary,it.nextAction).filter { v -> v.isNotBlank() }.joinToString(" · ").ifBlank { "Empty boundary" },it.boundary.isBlank() && it.nextAction.isBlank())) }
    }
    val candidate = buildList {
        draft.evidenceItems.forEach { add(ProjectMapEdge("source:${it.sourceId}","note:${it.id}","Recorded source")) }
        draft.evidenceRelations.forEach {
            add(ProjectMapEdge("note:${it.evidenceId}","${if (it.targetType == StudentProjectEvidenceTargetType.CLAIM) "claim" else "finding"}:${it.targetId}",
                when(it.relation) { StudentProjectEvidenceRelationType.SUPPORTS -> "Supports"; StudentProjectEvidenceRelationType.CONTRADICTS -> "Contradicts"; StudentProjectEvidenceRelationType.PROVIDES_CONTEXT -> "Context" },it.rationale))
        }
        draft.themes.forEach { theme -> theme.sourceIds.forEach { add(ProjectMapEdge("source:$it","theme:${theme.id}","Selected for comparison")) } }
        draft.limitationActions.forEach { action ->
            action.affectedFindingIds.forEach { add(ProjectMapEdge("finding:$it","action:${action.id}","Affected by boundary")) }
            action.affectedClaimIds.forEach { add(ProjectMapEdge("claim:$it","action:${action.id}","Affected by boundary")) }
        }
    }
    val keys = nodes.map { it.key }.toSet()
    val valid = candidate.filter { it.from in keys && it.to in keys }
    return ProjectMapData(nodes, valid, candidate.size-valid.size)
}

@Composable
internal fun EvidriloProjectMapDialog(draft: StudentProjectDraft, onClose: () -> Unit) {
    val data = remember(draft) { projectMapData(draft) }
    var selected by remember(draft.id) { mutableStateOf<ProjectMapNode?>(null) }
    var listMode by remember(draft.id) { mutableStateOf(false) }
    var linkedOnly by remember(draft.id) { mutableStateOf(false) }
    var focusLane by remember(draft.id) { mutableStateOf<Int?>(null) }
    var focusRequest by remember(draft.id) {mutableIntStateOf(0)}
    val linked=remember(data){data.edges.flatMap {listOf(it.from,it.to)}.toSet()}
    val displayed=remember(data,linkedOnly){if(linkedOnly) data.copy(nodes=data.nodes.filter {it.key in linked}) else data}
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        dev.nextgen.mobile.navigation.EvidriloBackGestureHost {
        EvidriloBackGesture("Close project map", onClose)
        Surface(Modifier.fillMaxSize(), color = EvidriloColors.Canvas) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("Project map"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClose) { Text(uiText("Close")) }
                }
                RawText(draft.title, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText("${data.nodes.size} records · ${data.edges.size} links", "${data.nodes.size} catatan · ${data.edges.size} hubungan"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    EvidriloIconButton(if(listMode) EvidriloIconName.EVIDENCE_GRAPH else EvidriloIconName.LIST,uiText(if(listMode) "Show graph" else "Show list"),{listMode=!listMode})
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=!linkedOnly,onClick={linkedOnly=false},label={Text(uiText("All records"))})
                    FilterChip(selected=linkedOnly,onClick={linkedOnly=true},label={Text(uiText("Connected"))})
                }
                if (data.nodes.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    EvidriloIcon(EvidriloIconName.EVIDENCE_GRAPH, tint = EvidriloColors.Cobalt, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(uiText("Your map starts with your material."), style = MaterialTheme.typography.titleLarge)
                    Text(uiText("Record a source, then add notes and explicit links. Nothing is filled in for you."), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                } else if (displayed.nodes.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    Text(uiText("No linked records yet. Switch to All records to find your next connection."),style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
                } else if (listMode) LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(displayed.nodes,key={it.key}) {node ->
                        EvidriloMapRecordRow(node,data.edges.count {it.from==node.key || it.to==node.key},{selected=node})
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(uiText("Supports"), style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Cobalt)
                        Text(uiText("Contradicts"), style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Error)
                        Text(uiText("Context · dashed"), style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Slate)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        displayed.nodes.map {it.lane}.distinct().sorted().forEach {lane ->
                            TextButton({focusLane=lane;focusRequest++}) {Text(uiText(listOf("Sources","Notes","Findings","Claims","Next actions")[lane]))}
                        }
                    }
                    ProjectMapCanvas(displayed, Modifier.weight(1f).fillMaxWidth(), focusLane,focusRequest,{selected=it})
                }
                if (data.invalidLinks > 0) Text(uiText("${data.invalidLinks} links refer to missing records. Review them in your project.", "${data.invalidLinks} hubungan merujuk catatan yang hilang. Tinjau kembali di proyek."),style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Error)
                EvidriloExplanation("Reading this map", "Explore sideways. Tap a record to follow its connections.\nSupports, contradicts and context are relationships you selected, not Evidrilo verdicts. Placement does not imply a link. Source-level legacy claim links are not converted into note-to-claim relationships. This map uses the current project material, including unsaved changes, and does not upload it.")
            }
        }
        }
    }
    selected?.let { node ->
        AlertDialog(onDismissRequest={selected=null}, containerColor=EvidriloColors.Card, title={Text(uiText(node.kind))}, text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                RawText(if(node.placeholder) uiText(node.text) else node.text)
                val links = data.edges.filter { it.from==node.key || it.to==node.key }
                if (links.isEmpty()) Text(uiText("No explicit links recorded."), color=EvidriloColors.Slate)
                links.forEach {edge ->
                    val other=data.nodes.first {it.key==if(edge.from==node.key) edge.to else edge.from}
                    Text(uiText(edge.label),style=MaterialTheme.typography.labelLarge,color=EvidriloColors.Cobalt)
                    EvidriloMapRecordRow(other,data.edges.count {it.from==other.key || it.to==other.key},{selected=other})
                    if(edge.rationale.isNotBlank()) RawText(edge.rationale,style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
                }
            }
        }, confirmButton={TextButton({selected=null}) {Text(uiText("Close record"))}})
    }
}

/** Debug-host visual fixture: no project, history, account or provider storage is touched. */
@Composable
public fun EvidriloProjectMapVisualPreview(onClose: () -> Unit) {
    val draft=remember { StudentProjectDraft(id="map_preview",templateSnapshot=null,title="Synthetic preview · campus shade",fieldValues=emptyMap(),revision=0,createdAtEpochMillis=0,updatedAtEpochMillis=0,
        sources=listOf(StudentProjectSourceRecord("a","Supplied reading A"),StudentProjectSourceRecord("b","Supplied reading B")),
        evidenceItems=listOf(StudentProjectEvidenceItem("na","a","Shade reduced exposure in the supplied example."),StudentProjectEvidenceItem("nb","b","The second setting had different surfaces and measurements.")),
        claims=listOf(StudentProjectClaimRecord("c","Shade may reduce exposure in this supplied example.")),
        evidenceRelations=listOf(StudentProjectEvidenceRelation(StudentProjectEvidenceTargetType.CLAIM,"c","na",StudentProjectEvidenceRelationType.SUPPORTS,"Selected within the example's scope."),StudentProjectEvidenceRelation(StudentProjectEvidenceTargetType.CLAIM,"c","nb",StudentProjectEvidenceRelationType.CONTRADICTS,"Recorded challenge to transferring the result to another campus.")),
        limitationActions=listOf(StudentProjectLimitationActionRecord("l",boundary="Campus applicability is unknown.",nextAction="Compare local conditions.",affectedClaimIds=setOf("c")))) }
    EvidriloProjectMapDialog(draft,onClose)
}

@Composable
private fun EvidriloMapRecordRow(node:ProjectMapNode,degree:Int,onOpen:()->Unit) {
    Surface(onClick=onOpen,shape=RoundedCornerShape(16.dp),color=EvidriloColors.Atmosphere) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            EvidriloIcon(mapNodeIcon(node.lane),tint=EvidriloColors.Cobalt)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(uiText(node.kind),style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Cobalt)
                RawText(if(node.placeholder) uiText(node.text) else node.text,style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                Text(uiText("$degree connections", "$degree hubungan"),style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
            }
            EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT,tint=EvidriloColors.Cobalt)
        }
    }
}

private fun mapNodeIcon(lane:Int)=when(lane) {0->EvidriloIconName.BOOK;1->EvidriloIconName.FILE;2->EvidriloIconName.LAYERS;3->EvidriloIconName.LINK;else->EvidriloIconName.CHECKLIST}

/** Virtualizes visible rows and clips offscreen paths; no node text or link is inferred. */
@Composable
private fun ProjectMapCanvas(data:ProjectMapData,modifier:Modifier,focusLane:Int?,focusRequest:Int,onSelect:(ProjectMapNode)->Unit) {
    val density=LocalDensity.current
    val rowHeight=130.dp*density.fontScale.coerceAtLeast(1f)
    val rowPx=with(density){rowHeight.toPx()}
    val lanes=remember(data){data.nodes.groupBy {it.lane}}
    val activeLanes=remember(lanes){lanes.keys.sorted()}
    val rows=lanes.values.maxOfOrNull {it.size} ?: 1
    val positions=remember(data){data.nodes.associate {it.key to (activeLanes.indexOf(it.lane) to lanes.getValue(it.lane).indexOf(it))}}
    val degrees=remember(data){data.edges.flatMap {listOf(it.from,it.to)}.groupingBy {it}.eachCount()}
    val horizontal=rememberScrollState();val vertical=rememberScrollState()
    val blue=EvidriloColors.Cobalt;val context=EvidriloColors.Slate;val contradiction=EvidriloColors.Error
    LaunchedEffect(focusLane,focusRequest) { if(focusLane!=null) {val index=activeLanes.indexOf(focusLane);if(index>=0) horizontal.animateScrollTo(with(density){(index*232).dp.toPx().toInt()})} }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(EvidriloColors.Atmosphere)) {
        val viewportHeight=maxHeight
        val visibleRows=(maxHeight.value/rowHeight.value).toInt()+3
        val firstRow by remember(vertical,rowPx){derivedStateOf {(vertical.value/rowPx).toInt().coerceAtLeast(0)}}
        val visible=remember(data,firstRow,visibleRows){data.nodes.filter {positions.getValue(it.key).second in (firstRow-1)..(firstRow+visibleRows)}}
        Box(Modifier.fillMaxSize().horizontalScroll(horizontal).verticalScroll(vertical)) {
            Box(Modifier.width((activeLanes.size*232+16).dp).height(rowHeight*rows+56.dp)) {
                Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
                    val viewportStart=vertical.value.toFloat();val viewportEnd=viewportStart+with(density){viewportHeight.toPx()}
                    data.edges.forEach {edge ->
                        val a=positions.getValue(edge.from);val b=positions.getValue(edge.to)
                        val start=Offset((a.first*232+210).dp.toPx(),rowPx*a.second+rowPx/2+36.dp.toPx())
                        val end=Offset((b.first*232+16).dp.toPx(),rowPx*b.second+rowPx/2+36.dp.toPx())
                        if(max(start.y,end.y)>=viewportStart && min(start.y,end.y)<=viewportEnd) {
                            val path=Path().apply {moveTo(start.x,start.y);cubicTo(start.x+60.dp.toPx(),start.y,end.x-60.dp.toPx(),end.y,end.x,end.y)}
                            val color=if(edge.label=="Contradicts") contradiction else if(edge.label=="Supports") blue else context
                            drawPath(path,color.copy(alpha=.72f),style=Stroke(2.dp.toPx(),cap=StrokeCap.Round,pathEffect=if(edge.label=="Context") PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(),5.dp.toPx())) else null))
                            val arrow=Path().apply {moveTo(end.x-7.dp.toPx(),end.y-4.dp.toPx());lineTo(end.x,end.y);lineTo(end.x-7.dp.toPx(),end.y+4.dp.toPx())}
                            drawPath(arrow,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
                        }
                    }
                }
                activeLanes.forEachIndexed {index,lane -> Text(uiText(listOf("Sources","Notes","Findings","Claims","Next actions")[lane]),Modifier.offset(x=(index*232+16).dp,y=8.dp),style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Slate)}
                visible.forEach {node -> key(node.key) {
                    val position=positions.getValue(node.key)
                    val label=uiText(node.kind)+": "+(if(node.placeholder) uiText(node.text) else node.text)+". "+uiText("Open recorded links")
                    Surface(onClick={onSelect(node)},modifier=Modifier.offset(x=(position.first*232+16).dp,y=rowHeight*position.second+36.dp).width(194.dp).height(rowHeight-16.dp)
                        .semantics {contentDescription=label},shape=RoundedCornerShape(16.dp),color=EvidriloColors.Card,shadowElevation=2.dp) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                                EvidriloIcon(mapNodeIcon(node.lane),tint=EvidriloColors.Cobalt,modifier=Modifier.size(17.dp))
                                Text(uiText(node.kind),style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Cobalt)
                            }
                            RawText(if(node.placeholder) uiText(node.text) else node.text,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                            Text(uiText("${degrees[node.key] ?: 0} connections", "${degrees[node.key] ?: 0} hubungan"),style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Slate)
                        }
                    }
                }}
            }
        }
    }
}
