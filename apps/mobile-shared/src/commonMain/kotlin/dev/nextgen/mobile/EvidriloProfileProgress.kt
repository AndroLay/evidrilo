package dev.nextgen.mobile

import androidx.compose.material3.Text as RawText
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.practice.*
import dev.nextgen.mobile.domain.project.*
import dev.nextgen.mobile.storage.LocalStorageReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recorded local work, never a mastery score, streak or research-quality estimate. */
@Composable
internal fun EvidriloProfileProgress(
    signedIn: Boolean, profileName: String, projects: List<StudentProjectDraft>,
    loading: Boolean, loadError: String?, hasPro: Boolean,
    onOpenAccount: () -> Unit, onOpenProjects: () -> Unit,
    onResumeProject: (StudentProjectDraft) -> Unit, onOpenPractice: () -> Unit,
    onOpenHistory: () -> Unit, onOpenPro: () -> Unit, onRetryProjects: () -> Unit,
    onPreferences: () -> Unit, onNotifications: () -> Unit,
    onPrivacy: () -> Unit, onSupport: () -> Unit,
    onOpenPracticeLesson: (PracticeLessonId) -> Unit = { onOpenPractice() },
) {
    val courseStore = rememberPracticeCourseStore()
    var courseRead by remember(courseStore) { mutableStateOf<LocalStorageReadResult<PracticeCourseState>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(courseStore, retry) { courseRead = withContext(Dispatchers.IO) { courseStore.load() } }
    val course = courseRead?.value ?: PracticeCourseState()
    val courseAvailable = courseRead is LocalStorageReadResult.Success
    val visible = projects.filter { it.status != StudentProjectStatus.TRASHED }
    val active = visible.filter { StudentProjectDraftRules.countsTowardActiveLimit(it.status) }.sortedByDescending { it.updatedAtEpochMillis }
    val completed = course.sessions.values.count { it.stage == PracticeLessonStage.COMPLETE }
    var explainProgress by remember { mutableStateOf(false) }
    val settingsLabel=uiText("Settings")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(uiText("Profile"), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        IconButton(onPreferences, Modifier.semantics { contentDescription = settingsLabel }) {
            EvidriloIcon(EvidriloIconName.SETTINGS, tint = EvidriloColors.Cobalt)
        }
    }
    if (dev.nextgen.mobile.billing.REVENUECAT_PRO_FEATURE_ENABLED) EvidriloProEntry(onOpenPro)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, color = EvidriloColors.Tint) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { EvidriloLogoMark(size = 42.dp) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RawText(profileName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(uiText(if (hasPro) "Evidrilo Pro" else if (signedIn) "Account connected" else "Local workspace"),
                style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
        TextButton(onOpenAccount) { Text(uiText(if (signedIn) "Account" else "Sign in"), color = EvidriloColors.Cobalt) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(uiText("Your recorded work"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconButton({ explainProgress = true }, Modifier.semantics { contentDescription = "How progress is counted" }) {
            EvidriloIcon(EvidriloIconName.QUESTION, tint = EvidriloColors.Slate, modifier = Modifier.size(20.dp))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileRecordedCount(EvidriloIconName.FOLDER, if (loading || loadError != null) "—" else active.size.toString(), "Active projects", Modifier.weight(1f))
        ProfileRecordedCount(EvidriloIconName.FILE, if (loading || loadError != null) "—" else visible.sumOf { project -> project.evidenceItems.count { it.excerpt.isNotBlank() } }.toString(), "Notes", Modifier.weight(1f))
        ProfileRecordedCount(EvidriloIconName.BOOK, if (!courseAvailable) "—" else completed.toString(), "Cases finished", Modifier.weight(1f))
    }
    when {
        loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = EvidriloColors.Cobalt, trackColor = EvidriloColors.Tint)
        loadError != null -> {
            Text(loadError, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            EvidriloSecondaryButton("Retry projects", onRetryProjects)
        }
        active.isNotEmpty() -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Keep going"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onOpenProjects) { Text(uiText("All projects"), color = EvidriloColors.Cobalt) }
            }
            active.take(2).forEach { project ->
                val progress = StudentProjectDraftRules.requiredFieldProgress(project)
                val ratio = if (progress.totalRequired == 0) 0f else progress.filledRequired.toFloat() / progress.totalRequired
                EvidriloPressableCard(onClick = { onResumeProject(project) }, faceColor = EvidriloColors.Atmosphere,
                    borderColor = EvidriloColors.Atmosphere, lipColor = EvidriloColors.Tint) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EvidriloIcon(EvidriloIconName.FOLDER, tint = EvidriloColors.Cobalt)
                            RawText(project.title.ifBlank { "Untitled project" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = EvidriloColors.Cobalt)
                        }
                        LinearProgressIndicator(progress = { ratio }, Modifier.fillMaxWidth().height(6.dp), color = EvidriloColors.Cobalt, trackColor = EvidriloColors.Tint)
                        Text(uiText("${progress.filledRequired}/${progress.totalRequired} required responses recorded", "${progress.filledRequired}/${progress.totalRequired} isian wajib tercatat"), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                    }
                }
            }
        }
        else -> EvidriloWorkspaceRow(EvidriloIconName.PLUS, "Start a project", "Your question, your material", onOpenProjects)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(uiText("Your practice", "Latihan Anda"), Modifier.weight(1f), style=MaterialTheme.typography.titleLarge)
        Text(if (courseAvailable) "$completed / ${PracticeLessonId.entries.size}" else "—",style=MaterialTheme.typography.titleMedium,color=EvidriloColors.Cobalt)
    }
    if (courseRead == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color=EvidriloColors.Cobalt)
    else if (!courseAvailable) {
        Text(uiText("Practice progress couldn't be read.", "Progres latihan tidak dapat dibaca."))
        TextButton({ retry++ }) { Text(uiText("Try again", "Coba lagi")) }
    } else {
        PracticeLessonId.entries.forEach { lesson ->
            val session=course.sessions[lesson]
            val finished=session?.stage==PracticeLessonStage.COMPLETE
            val title=when(lesson) { PracticeLessonId.TABLET -> uiText("Trace observations","Telusuri pengamatan"); PracticeLessonId.STUDIES -> uiText("Compare sources","Bandingkan sumber"); PracticeLessonId.SURVEY -> uiText("Reconsider data","Tinjau ulang data") }
            val status=if(finished) uiText("Completed","Selesai") else if(session!=null) uiText("Continue your attempt","Lanjutkan latihan") else uiText("Not started","Belum dimulai")
            EvidriloWorkspaceRow(if(finished) EvidriloIconName.CHECK else courseIcon(lesson),title,status,
                { onOpenPracticeLesson(lesson) })
        }
        Text(uiText("Completed practice stays recorded, even when Pro access ends.", "Latihan yang selesai tetap tercatat meski akses Pro berakhir."),
            style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
    }
    EvidriloWorkspaceRow(EvidriloIconName.HISTORY, uiText("Local history","Riwayat lokal"), null, onOpenHistory)
    if (explainProgress) AlertDialog(onDismissRequest = { explainProgress = false }, title = { Text("Recorded progress") },
        text = { Text("Projects and notes come from this device. Response bars count required fields with recorded content; finished cases come from saved Practice sessions. These counts are not grades or assessments of research quality. Previously finished Pro cases remain history, not proof of current access.") },
        confirmButton = { TextButton({ explainProgress = false }) { Text(uiText("Got it")) } })
}

@Composable
private fun ProfileRecordedCount(icon: EvidriloIconName, value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EvidriloIcon(icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(22.dp))
        AnimatedContent(value, label = "Recorded count") { Text(it, style = MaterialTheme.typography.headlineSmall) }
        Text(uiText(label), style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
}
