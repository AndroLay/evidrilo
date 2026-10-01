package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.nextgen.mobile.domain.project.StudentProjectDraft

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EvidriloProjectEditorScaffold(
    projectTitle: String, sectionTitle: String, sectionKey: Int,
    progress: Float, progressDescription: String, saveStatus: String,
    sections: List<StudentProjectEditorSection>, sectionIssue: String?,
    onSelectSection: (Int) -> Boolean, draft: StudentProjectDraft,
    nextLabel: String?, onNext: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reveal = rememberGetStartedReveal(sectionKey, 240)
    val shownProgress by animateFloatAsState(progress, tween(240), label = "Section navigation")
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showMap by remember(draft.id) { mutableStateOf(false) }
    if (drawer.isOpen) EvidriloBackGesture("Close sections", { scope.launch { drawer.close() } })
    ModalNavigationDrawer(drawerState = drawer, scrimColor = EvidriloColors.Ink.copy(alpha = .24f), drawerContent = {
        ModalDrawerSheet(modifier = Modifier.widthIn(max = 320.dp), drawerContainerColor = EvidriloColors.Card.copy(alpha = .97f)) {
            Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Sections"), style = MaterialTheme.typography.headlineSmall)
                RawText(projectTitle, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                sectionIssue?.let { Text(it, color = EvidriloColors.Error, style = MaterialTheme.typography.bodySmall) }
                sections.forEachIndexed { index, section ->
                    EvidriloWorkspaceRow(projectSectionIcon(section.kind), uiText(section.title), null, {
                        if (onSelectSection(index)) scope.launch { drawer.close() }
                    }, selected = index == sectionKey)
                }
                EvidriloExplanation("Moving between sections", "Work is saved before moving to another section. If that save fails, this panel stays open and your current work is retained. Swipe left or touch outside to close.")
            }
        }
    }) {
    Column(Modifier.fillMaxSize().background(EvidriloColors.Canvas).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RawText(projectTitle.ifBlank { "Untitled project" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate, maxLines = 2, overflow = TextOverflow.Ellipsis)
                EvidriloIconButton(EvidriloIconName.EVIDENCE_GRAPH, "Open project map", { showMap = true })
                TextButton({ scope.launch { drawer.open() } }) {
                    EvidriloIcon(EvidriloIconName.LIST, tint = EvidriloColors.Cobalt, modifier = Modifier.size(22.dp))
                    Text(uiText("Sections"), Modifier.padding(start = 6.dp), color = EvidriloColors.Cobalt)
                }
            }
            Text(uiText(sectionTitle), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            LinearProgressIndicator(progress = { shownProgress }, Modifier.fillMaxWidth().height(7.dp).semantics { contentDescription = progressDescription; progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }, color = EvidriloColors.Cobalt, trackColor = EvidriloColors.Tint)
            AnimatedContent(saveStatus, label = "Local save feedback") { status ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                    val failed = status.startsWith("Not saved")
                    EvidriloIcon(if (failed) EvidriloIconName.ALERT else if (status.startsWith("Saved")) EvidriloIconName.CHECK else EvidriloIconName.HISTORY,
                        tint = if (failed) EvidriloColors.Error else EvidriloColors.Cobalt, modifier = Modifier.size(16.dp))
                    Text(status, style = MaterialTheme.typography.labelSmall, color = if (failed) EvidriloColors.Error else EvidriloColors.Slate)
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().graphicsLayer { translationX = (1 - reveal.value) * 14.dp.toPx(); alpha = .7f + reveal.value * .3f }
                .verticalScroll(remember(sectionKey) { ScrollState(0) }).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
        if (nextLabel != null) {
            HorizontalDivider(color = EvidriloColors.Separator)
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                EvidriloPrimaryButton(nextLabel, onNext, trailingIcon = EvidriloIconName.ARROW_FORWARD)
            }
        }
    }
    }
    if (showMap) EvidriloProjectMapDialog(draft, { showMap = false })
}

@Composable
internal fun EvidriloProjectSectionsDialog(
    sections: List<StudentProjectEditorSection>, selected: Int, issue: String?,
    onSelect: (Int) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = EvidriloColors.Card,
        title = { Text("Your workspace") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Move between sections. Your work is saved before switching.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                issue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Error) }
                sections.forEachIndexed { index, section ->
                    EvidriloWorkspaceRow(projectSectionIcon(section.kind), uiText(section.title), null, { onSelect(index) }, selected = selected == index)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(uiText("Close"), color = EvidriloColors.Cobalt) } },
    )
}

internal fun projectSectionIcon(kind: StudentProjectEditorSectionKind): EvidriloIconName = when (kind) {
    StudentProjectEditorSectionKind.PROJECT_BASICS -> EvidriloIconName.QUESTION
    StudentProjectEditorSectionKind.TEMPLATE_STEP, StudentProjectEditorSectionKind.TEMPLATE_ADDITIONAL_FIELDS -> EvidriloIconName.CHECKLIST
    StudentProjectEditorSectionKind.SOURCES_AND_FILES -> EvidriloIconName.BOOK
    StudentProjectEditorSectionKind.EVIDENCE_NOTES -> EvidriloIconName.FILE
    StudentProjectEditorSectionKind.FINDINGS_AND_SYNTHESIS -> EvidriloIconName.LAYERS
    StudentProjectEditorSectionKind.CLAIMS -> EvidriloIconName.LINK
    StudentProjectEditorSectionKind.LIMITATIONS_AND_NEXT_STEPS -> EvidriloIconName.ARROW_FORWARD
    StudentProjectEditorSectionKind.REVIEW -> EvidriloIconName.CHECKLIST
}

@Composable
internal fun EvidriloProjectRecordCard(title: String, detail: String, initiallyExpanded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Surface(shape = RoundedCornerShape(16.dp), color = EvidriloColors.Atmosphere) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(200))) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                }
                EvidriloIcon(if (expanded) EvidriloIconName.CHEVRON_DOWN else EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Cobalt)
            }
            if (expanded) Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
internal fun EvidriloExportChooser(options: List<Pair<String, () -> Unit>>) {
    var selected by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(options[selected].first), Modifier.weight(1f))
                EvidriloIcon(EvidriloIconName.CHEVRON_DOWN, tint = EvidriloColors.Cobalt)
            }
            DropdownMenu(expanded, { expanded = false },containerColor=EvidriloColors.Card) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(text = { Text(uiText(option.first)) }, onClick = { selected = index; expanded = false })
                }
            }
        }
        EvidriloPrimaryButton(uiText("Preview file", "Tinjau berkas"), options[selected].second, trailingIcon = EvidriloIconName.ARROW_FORWARD)
        EvidriloExplanation("About these exports", "Reports contain your recorded material, not verified research. The .evproj archive preserves project data, revision history, and verified attachments for recovery. CSV cells that may act as formulas are prefixed with an apostrophe; review values in your spreadsheet app.")
    }
}
