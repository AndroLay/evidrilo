package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import dev.nextgen.mobile.domain.onboarding.GetStartedTourStep

internal data class GetStartedSceneCopy(
    val section: String,
    val title: String,
    val body: String,
    val action: String,
)

internal fun getStartedScene(step: GetStartedTourStep): GetStartedSceneCopy = when (step) {
    GetStartedTourStep.WELCOME -> GetStartedSceneCopy(
        "Meet Evidrilo", "Big ideas.\nSmall next steps.",
        "A place to turn your questions into work you can explain.",
        "Let's explore",
    )
    GetStartedTourStep.ORGANIZE -> GetStartedSceneCopy(
        "Project structures", "A home for\nyour next idea.",
        "Five project structures. Swipe to explore—or start blank.",
        "Continue",
    )
    GetStartedTourStep.REVIEW -> GetStartedSceneCopy(
        "Evidence links", "Make the\nconnection.",
        "Drag a note onto a claim. Keep your reasoning connected.",
        "Continue",
    )
    GetStartedTourStep.ASSISTANCE -> GetStartedSceneCopy(
        "Optional AI", "A nudge when\nyou need one.",
        "Explore, plan and review with optional AI. You decide what to keep.",
        "Continue",
    )
    GetStartedTourStep.PORTABILITY -> GetStartedSceneCopy(
        "History and exports", "Keep it.\nShape it. Share it.",
        "Save local revisions. Take your work with you as a report or a project archive.",
        "Continue",
    )
    GetStartedTourStep.READY -> GetStartedSceneCopy(
        "Make it yours", "Your question.\nYour next chapter.",
        "Start your own project. Or explore three cases in Practice.",
        "Open My Projects",
    )
}

/** Selections survive chapter navigation; none is bound to any application store. */
internal class GetStartedSceneSelections(
    val family: MutableState<Int>,
    val material: MutableState<Int>,
    val aiPrompt: MutableState<Int>,
    val export: MutableState<Int>,
    val welcomePulse: MutableState<Int>,
    val noteLinked: MutableState<Boolean>,
    val celebration: MutableState<Int>,
)

@Composable
internal fun rememberGetStartedSceneSelections() = GetStartedSceneSelections(
    family = rememberSaveable { mutableStateOf(0) },
    material = rememberSaveable { mutableStateOf(0) },
    aiPrompt = rememberSaveable { mutableStateOf(0) },
    export = rememberSaveable { mutableStateOf(1) },
    welcomePulse = rememberSaveable { mutableStateOf(0) },
    noteLinked = rememberSaveable { mutableStateOf(false) },
    celebration = rememberSaveable { mutableStateOf(0) },
)

internal enum class GetStartedProjectFamily(
    val label: String,
    val displayName: String,
    val icon: EvidriloIconName,
    val sections: List<String>,
) {
    LITERATURE("Literature", "Literature synthesis", EvidriloIconName.BOOK,
        listOf("Research question", "Reading notes", "Themes & claims")),
    SURVEY("Survey", "Observation / survey", EvidriloIconName.LIST,
        listOf("Question & scope", "Observations", "Findings & limits")),
    LAB("Experiment", "Lab experiment", EvidriloIconName.LAYERS,
        listOf("Question & variables", "Trial records", "Interpretation & limits")),
    QUALITATIVE("Qualitative", "Qualitative inquiry", EvidriloIconName.CHAT_BUBBLE,
        listOf("Study focus", "Coded notes", "Themes & boundaries")),
    DESIGN("Design", "Design / engineering", EvidriloIconName.CHECKLIST,
        listOf("Design question", "Options & tests", "Decisions & limits")),
}

internal data class GetStartedAiExample(val label: String, val question: String, val suggestion: String)

internal val getStartedAiExamples = listOf(
    GetStartedAiExample("Explore", "Where can I start?",
        "Pick one question, one setting, and something you can observe."),
    GetStartedAiExample("Plan", "What should I do next?",
        "Separate what you have from what you need. Choose one small next action."),
    GetStartedAiExample("Review", "Does my claim reach too far?",
        "Trace it to a note. Keep the wording within the evidence you actually have."),
)

internal enum class GetStartedExportFormat(val label: String, val extension: String, val detail: String) {
    MARKDOWN("Markdown", "md", "A text report you can keep editing."),
    PDF("PDF", "pdf", "A readable report you can share."),
    DOCX("DOCX", "docx", "A report you can open in a document editor."),
    CSV("CSV", "csv", "Structured project rows for a spreadsheet."),
    PROJECT(".evproj", "evproj", "A project archive you can import into Evidrilo."),
}
