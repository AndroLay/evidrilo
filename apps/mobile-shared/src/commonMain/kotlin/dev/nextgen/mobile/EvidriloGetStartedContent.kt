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

internal fun getStartedScene(step: GetStartedTourStep): GetStartedSceneCopy = when(step) {
    GetStartedTourStep.WELCOME -> GetStartedSceneCopy("Meet Evidrilo","Big ideas.\nClear next steps.",
        "Start with an assignment, a question or a hypothesis. Evidrilo helps you organize your own sources, observations and claims, then explain what your material supports and what remains uncertain. Explore the workflow with temporary examples.","Let's explore")
    GetStartedTourStep.ORGANIZE -> GetStartedSceneCopy("Choose a structure","A starting point\nthat fits your work.",
        "Choose from five project structures: experiment, survey, literature, qualitative study or design. Each provides places for your own material. Browse the sections before choosing; you can also start without a template. Swipe to compare them.","Continue")
    GetStartedTourStep.WORKSPACE -> GetStartedSceneCopy("Your workspace","One project.\nClear next steps.",
        "Move between sections using the Sections panel. Add source details and files, turn useful passages into notes, and develop your claims. Your work saves locally. A suggested next task helps you find a manageable place to continue.","Continue")
    GetStartedTourStep.REVIEW -> GetStartedSceneCopy("Connect your reasoning","Make the\nconnection.",
        "A note and a claim do different jobs. Record whether the note supports, challenges or gives context to the claim, and explain why. Keep the claim within the material's scope. Try connecting the two temporary cards below.","Continue")
    GetStartedTourStep.GRAPH -> GetStartedSceneCopy("The project map","Your reasoning,\nvisible.",
        "Explore the relationships you recorded, from sources through notes to claims and next actions. Tap a record to follow its connections. Unlinked records stay visible too. The map helps you inspect your reasoning; it does not decide whether a claim is true.","Continue")
    GetStartedTourStep.PRACTICE -> GetStartedSceneCopy("Practice before your project","Learn the move.\nMake it your own.",
        "Three guided cases let you inspect observations, compare studies and reconsider changed data. Make a first response, read specific feedback, then revise. The first case is Free; Pro opens the other two. Practice examples stay separate from your project.","Continue")
    GetStartedTourStep.ASSISTANCE -> GetStartedSceneCopy("Optional AI","A nudge when\nyou need one.",
        "Sign in for optional online AI. Review the text you choose to share and its credit conditions before sending. Ask for ideas, a plan or an opinion on a saved project excerpt. You decide what to keep and check the answer yourself.","Continue")
    GetStartedTourStep.PORTABILITY -> GetStartedSceneCopy("Review and take your work","Keep it.\nShape it. Share it.",
        "Review checks show recorded responses and links, with a practical next action. They are not an academic grade. Preview a report before export, or keep an .evproj backup for recovery. The project menu also lets you complete or permanently delete work with confirmation.","Continue")
    GetStartedTourStep.READY -> GetStartedSceneCopy("Your next step","Your question.\nYour next chapter.",
        "Choose a structure and begin with your own assignment. Free keeps five projects total; Pro keeps fifty and adds 200 AI credits each active month. A verified Free account receives 20 credits once. You can keep working locally and revisit this introduction from Settings.","Choose project structure")
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
