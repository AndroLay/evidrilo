package dev.nextgen.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.conclusion.*
import dev.nextgen.mobile.domain.practice.*
import dev.nextgen.mobile.navigation.EvidriloSystemBackHandler
import dev.nextgen.mobile.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Practice-only host. Tablet events stay with its original host.
 * The course record contains only onramp/progress and the two new synthetic cases.
 */
@Composable
public fun EvidriloPracticeCourseScreen(
    tabletState: ConclusionState,
    onLessonVisibilityChanged: (Boolean) -> Unit = {},
    onTabletEvent: (ConclusionEvent) -> Unit,
    onExit: () -> Unit,
    onOpenProjects: () -> Unit,
    tabletCase: ConclusionCase = ConclusionCases.M0_T2,
    onTabletContextChanged: (Boolean) -> Unit = {},
    onSelectionSound: () -> Unit = {},
    tabletAudioControls: @Composable () -> Unit = {},
    onOpenTabletHistory: (() -> Unit)? = null,
    store: PracticeCourseStore? = null,
    temporaryPreview: Boolean = false,
) {
    val localStore = store ?: rememberPracticeCourseStore()
    var course by remember(localStore) { mutableStateOf(PracticeCourseState()) }
    var ready by remember(localStore) { mutableStateOf(false) }
    var canSave by remember(localStore) { mutableStateOf(false) }
    var loadedStatus by remember(localStore) { mutableStateOf(LocalStorageStatus.AVAILABLE) }
    var saved by remember(localStore) { mutableStateOf<PracticeCourseState?>(null) }
    var saveResult by remember(localStore) { mutableStateOf(LocalStorageWriteResult.SAVED) }
    var retry by remember(localStore) { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    var restart by remember { mutableStateOf<PracticeLessonId?>(null) }
    var replaceUnreadable by remember { mutableStateOf(false) }
    var failedExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var exiting by remember { mutableStateOf(false) }
    var selectedPracticeLesson by remember { mutableStateOf(PracticeLessonId.TABLET) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val saveMutex = remember(localStore) { Mutex() }

    LaunchedEffect(localStore) {
        val result = withContext(Dispatchers.Default) { localStore.load() }
        course = result.value ?: PracticeCourseState()
        saved = course
        loadedStatus = result.status
        canSave = result.status in setOf(LocalStorageStatus.AVAILABLE, LocalStorageStatus.RECOVERED)
        ready = true
    }
    LaunchedEffect(course, ready, canSave, retry, exiting) {
        if (ready && canSave && !exiting && course != saved) {
            delay(250)
            val snapshot = course
            val result = saveMutex.withLock { withContext(Dispatchers.Default) { localStore.save(snapshot) } }
            saveResult = result
            if (result == LocalStorageWriteResult.SAVED) saved = snapshot
        }
    }
    fun send(event: PracticeCourseEvent) {
        if (ready && !exiting) course = PracticeCourseReducer.reduce(course, event)
    }
    fun exitSafely(destination: () -> Unit) {
        if (exiting) return
        focus.clearFocus()
        if (!canSave || temporaryPreview) { destination(); return }
        exiting = true
        val snapshot = course
        scope.launch {
            val result = saveMutex.withLock { withContext(Dispatchers.Default) { localStore.save(snapshot) } }
            saveResult = result
            exiting = false
            if (result == LocalStorageWriteResult.SAVED) { saved = snapshot; destination() }
            else failedExit = destination
        }
    }
    val active = course.active
    LaunchedEffect(active) { onLessonVisibilityChanged(active != null) }
    DisposableEffect(Unit) { onDispose { onLessonVisibilityChanged(false) } }
    val session = active?.let { course.sessions[it] }
    val tabletContent = active == PracticeLessonId.TABLET &&
        session?.stage in setOf(PracticeLessonStage.INSPECT, PracticeLessonStage.COMPLETE)
    LaunchedEffect(tabletContent) { onTabletContextChanged(tabletContent) }
    DisposableEffect(Unit) { onDispose { onTabletContextChanged(false) } }
    LaunchedEffect(ready, tabletState, course.sessions[PracticeLessonId.TABLET]?.stage, exiting) {
        if (ready && !exiting && tabletState is ConclusionState.EvidenceChangeSummary &&
            course.sessions[PracticeLessonId.TABLET]?.stage == PracticeLessonStage.INSPECT)
            send(PracticeCourseEvent.TabletCompleted)
    }
    EvidriloSystemBackHandler(enabled = ready && !showHelp && restart == null && !replaceUnreadable && failedExit == null) {
        if (active == null) exitSafely(onExit)
        else if (tabletContent || session?.initial != null || session?.stage == PracticeLessonStage.MISSION)
            send(PracticeCourseEvent.Leave)
        else send(PracticeCourseEvent.Back)
    }
    val storageLabel = when {
        exiting -> "Saving before leaving…"
        !canSave -> if (loadedStatus == LocalStorageStatus.CORRUPT) "Saved practice needs recovery" else "Practice is temporary on this screen"
        saveResult != LocalStorageWriteResult.SAVED -> "Not saved · try again"
        temporaryPreview -> "Temporary preview · synthetic data"
        course != saved -> "Saving on this device…"
        else -> "Saved on this device · synthetic data"
    }
    if (!ready) {
        PracticeFrame({ exitSafely(onExit) }, {}, "Opening local practice", footer = { Text("Reading this device's practice record…") }) {
            Text("Opening your practice.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 32.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp))
        }
    } else if (tabletContent) {
        EvidriloPracticeScreen(
            state = tabletState,
            onEvent = { event ->
                if (!exiting) {
                    onTabletEvent(event)
                    if (event == ConclusionEvent.Reset) {
                        send(PracticeCourseEvent.Restart(PracticeLessonId.TABLET))
                        send(PracticeCourseEvent.Leave)
                    }
                }
            },
            onExit = { send(PracticeCourseEvent.Leave) },
            case = tabletCase,
            onOpenProjects = { exitSafely(onOpenProjects) },
            onSelectionSound = onSelectionSound,
            audioControls = tabletAudioControls,
            onOpenHistory = onOpenTabletHistory?.let { destination -> { exitSafely(destination) } },
        )
    } else if (session == null) {
        EvidriloPracticeLobby(
            course = course,
            selected = selectedPracticeLesson,
            busy = exiting,
            onSelect = { selectedPracticeLesson = it },
            onOpen = { id ->
                val progress = course.sessions[id]
                if (id == PracticeLessonId.TABLET && progress?.stage == PracticeLessonStage.COMPLETE && tabletState is ConclusionState.Intro)
                    restart = id
                else send(PracticeCourseEvent.Open(id))
            },
            onExit = { exitSafely(onExit) },
            onHelp = { showHelp = true },
            onOpenProjects = { exitSafely(onOpenProjects) },
            storageNotice = {
                CourseStorageNotice(loadedStatus, canSave, saveResult, { retry++ }, { replaceUnreadable = true })
            },
        )
    } else {
        val problem = PracticeCourseReducer.inputProblem(session)
        val action: () -> Unit = {
            if (!exiting) when (session.stage) {
                PracticeLessonStage.CHANGE -> send(PracticeCourseEvent.ReviewChange)
                PracticeLessonStage.REVISION -> send(PracticeCourseEvent.SubmitRevision)
                PracticeLessonStage.FINAL_REVIEW -> send(PracticeCourseEvent.Finish)
                PracticeLessonStage.COMPLETE -> send(PracticeCourseEvent.Leave)
                else -> {
                    send(PracticeCourseEvent.Continue)
                    if (session.lesson == PracticeLessonId.TABLET && session.stage == PracticeLessonStage.PREDICTION &&
                        tabletState is ConclusionState.Intro) onTabletEvent(ConclusionEvent.Begin)
                }
            }
        }
        CourseLessonScreen(
            session = session,
            storageLabel = storageLabel,
            problem = problem,
            onEvent = ::send,
            onContinue = action,
            onLeave = { send(PracticeCourseEvent.Leave) },
            onHelp = { showHelp = true },
            onRestart = { restart = session.lesson },
            onOpenProjects = { exitSafely(onOpenProjects) },
            onNextCase = {
                send(PracticeCourseEvent.Open(if (session.lesson == PracticeLessonId.STUDIES) PracticeLessonId.SURVEY else PracticeLessonId.TABLET))
            },
            onRetrySave = { retry++ },
            busy = exiting,
        )
    }
    if (showHelp) CourseHelpSheet(session, { showHelp = false })
    restart?.let { id ->
        AlertDialog(onDismissRequest = { restart = null },
            title = { Text("Start a new attempt?") },
            text = { Text("This replaces only this case's saved attempt and comparison. Other cases and your Projects are retained.") },
            confirmButton = { TextButton(onClick = {
                if (!exiting) {
                    if (id == PracticeLessonId.TABLET) onTabletEvent(ConclusionEvent.Reset)
                    send(PracticeCourseEvent.Restart(id)); restart = null
                }
            }) { Text("Start a new attempt") } },
            dismissButton = { TextButton(onClick = { restart = null }) { Text("Keep this attempt") } })
    }
    if (replaceUnreadable) {
        AlertDialog(onDismissRequest = { replaceUnreadable = false },
            title = { Text("Replace the unreadable practice record?") },
            text = { Text("The new course record will replace the unreadable course data. Existing tablet history and Projects use separate storage.") },
            confirmButton = { TextButton(onClick = {
                if (exiting) return@TextButton
                exiting = true
                focus.clearFocus()
                scope.launch {
                    val replacement = course
                    val result = saveMutex.withLock { withContext(Dispatchers.Default) { localStore.save(replacement) } }
                    saveResult = result
                    if (result == LocalStorageWriteResult.SAVED) { canSave = true; loadedStatus = LocalStorageStatus.AVAILABLE; saved = replacement }
                    replaceUnreadable = false
                    exiting = false
                }
            }) { Text("Save a fresh record") } },
            dismissButton = { TextButton(onClick = { replaceUnreadable = false }) { Text("Keep temporary practice") } })
    }
    failedExit?.let { destination ->
        AlertDialog(onDismissRequest = { failedExit = null },
            title = { Text("The latest practice could not be saved.") },
            text = { Text("Keep this screen open and retry to retain your latest changes, or leave with the previously saved record.") },
            confirmButton = { TextButton(onClick = { failedExit = null; retry++ }) { Text("Keep and retry") } },
            dismissButton = { TextButton(onClick = { failedExit = null; destination() }) { Text("Leave anyway") } })
    }
}

@Composable
private fun CourseStorageNotice(status: LocalStorageStatus, canSave: Boolean, result: LocalStorageWriteResult, retry: () -> Unit, replace: () -> Unit) {
    if (!canSave) {
        PracticeMessage("Local progress is unavailable",
            if (status == LocalStorageStatus.CORRUPT) "The saved course record could not be read. It has been kept; this attempt is temporary."
            else "You can practice here, but this build cannot retain this course record when you leave.", EvidriloIconName.INFO)
        if (status == LocalStorageStatus.CORRUPT || status == LocalStorageStatus.FAILED)
            EvidriloSecondaryButton("Save a fresh course record", replace)
    } else if (result != LocalStorageWriteResult.SAVED) {
        PracticeMessage("The latest change is not saved", "Keep the screen open and retry saving.", EvidriloIconName.INFO)
        EvidriloSecondaryButton("Retry saving", retry)
    }
}
