package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.billing.BillingGateway
import dev.nextgen.mobile.billing.BillingOutcome
import dev.nextgen.mobile.billing.PremiumAccess
import dev.nextgen.mobile.billing.createPlatformBillingGateway
import dev.nextgen.mobile.domain.askready.AskReadyChannel
import dev.nextgen.mobile.domain.askready.AskReadyCriterion
import dev.nextgen.mobile.domain.askready.AskReadyDraft
import dev.nextgen.mobile.domain.askready.AskReadyFeedbackItem
import dev.nextgen.mobile.domain.askready.AskReadyFeedbackState
import dev.nextgen.mobile.domain.askready.AskReadyPracticeEvent
import dev.nextgen.mobile.domain.askready.AskReadyPracticeReducer
import dev.nextgen.mobile.domain.askready.AskReadyPracticeState
import dev.nextgen.mobile.domain.askready.AskReadyScenario
import dev.nextgen.mobile.domain.askready.buildAskReadyPreview
import dev.nextgen.mobile.domain.askready.displayLabel

@Composable
fun App() {
    EvidriloTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val billingGateway = remember { createPlatformBillingGateway() }
            EvidriloApp(billingGateway)
        }
    }
}

@Composable
private fun AskReadyApp(billingGateway: BillingGateway) {
    val reducer = remember { AskReadyPracticeReducer() }
    var state by remember { mutableStateOf<AskReadyPracticeState>(AskReadyPracticeState.Start) }
    val dispatch: (AskReadyPracticeEvent) -> Unit = { event ->
        state = reducer.reduce(state, event)
    }

    when (val current = state) {
        AskReadyPracticeState.Start -> StartScreen(
            onStart = { dispatch(AskReadyPracticeEvent.BeginDraft) },
        )

        is AskReadyPracticeState.Drafting -> DraftScreen(
            title = "Build a specific request",
            draft = current.draft,
            validationMessage = current.validationMessage,
            onDraftChange = { dispatch(AskReadyPracticeEvent.UpdateDraft(it)) },
            onSubmit = { dispatch(AskReadyPracticeEvent.SubmitDraft) },
            onReset = { dispatch(AskReadyPracticeEvent.ResetPractice) },
        )

        is AskReadyPracticeState.Feedback -> FeedbackScreen(
            draft = current.draft,
            items = current.report.items,
            isFinal = current.isFinal,
            onRevise = { dispatch(AskReadyPracticeEvent.BeginRevision) },
            onPremium = { dispatch(AskReadyPracticeEvent.RequirePracticePack) },
            onReset = { dispatch(AskReadyPracticeEvent.ResetPractice) },
        )

        is AskReadyPracticeState.Revision -> DraftScreen(
            title = "Revise once, then send with confidence",
            draft = current.draft,
            validationMessage = null,
            onDraftChange = { dispatch(AskReadyPracticeEvent.UpdateDraft(it)) },
            onSubmit = { dispatch(AskReadyPracticeEvent.SubmitDraft) },
            onReset = { dispatch(AskReadyPracticeEvent.ResetPractice) },
        )

        is AskReadyPracticeState.PremiumLocked -> {
            LaunchedEffect(current.entitlement) {
                billingGateway.loadPracticePackOffer { outcome ->
                    dispatch(outcome.toAskReadyEvent())
                }
            }
            PremiumLockedScreen(
                state = current,
                onBack = { dispatch(AskReadyPracticeEvent.LeavePremium) },
                onPurchase = {
                    billingGateway.purchasePracticePack { outcome ->
                        dispatch(outcome.toAskReadyEvent())
                    }
                },
                onRestore = {
                    billingGateway.restorePurchases { outcome ->
                        dispatch(outcome.toAskReadyEvent())
                    }
                },
            )
        }

        is AskReadyPracticeState.PremiumUnlocked -> PracticePackScreen(
            state = current,
            onScenario = { dispatch(AskReadyPracticeEvent.SelectScenario(it)) },
            onStart = { dispatch(AskReadyPracticeEvent.BeginScenario) },
            onBack = { dispatch(AskReadyPracticeEvent.LeavePremium) },
            onReset = { dispatch(AskReadyPracticeEvent.ResetPractice) },
        )

        is AskReadyPracticeState.Error -> ErrorScreen(
            message = current.message,
            onBack = { dispatch(AskReadyPracticeEvent.DismissError) },
        )
    }
}

@Composable
private fun StartScreen(onStart: () -> Unit) {
    ContentColumn {
        Eyebrow("ASKREADY · ACADEMIC HELP REQUESTS")
        Text("Make “I’m stuck” easier to act on.", style = MaterialTheme.typography.headlineMedium)
        Text(
            "AskReady helps you turn a vague academic-help message into a specific request that explains what you tried, what is unclear, and what next step would help.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "It does not answer your assignment, invent campus policy, or predict whether someone will approve your request.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("One focused practice", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Object · Context · Attempt · Concrete ask · Next step · Privacy",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start with an assignment")
        }
        Text(
            "Your practice stays local in this prototype. Only include the minimum context you are comfortable reviewing.",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun DraftScreen(
    title: String,
    draft: AskReadyDraft,
    validationMessage: String?,
    onDraftChange: (AskReadyDraft) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
) {
    ContentColumn {
        Eyebrow(draft.scenario.displayLabel().uppercase())
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Use only the minimum context needed. AskReady checks the structure of the request, not whether your academic work is correct.",
            style = MaterialTheme.typography.bodyMedium,
        )
        DraftField("What exactly are you asking about?", draft.specificObject) {
            onDraftChange(draft.copy(specificObject = it))
        }
        DraftField("Minimum context", draft.context, singleLine = false) {
            onDraftChange(draft.copy(context = it.take(400)))
        }
        DraftField("What have you tried?", draft.attempt, singleLine = false) {
            onDraftChange(draft.copy(attempt = it.take(400)))
        }
        DraftField("What help do you want?", draft.concreteAsk, singleLine = false) {
            onDraftChange(draft.copy(concreteAsk = it.take(300)))
        }
        Text("Preferred channel", style = MaterialTheme.typography.titleSmall)
        AskReadyChannel.entries.forEach { channel ->
            ChannelOption(
                channel = channel,
                selected = draft.channel == channel,
                onClick = { onDraftChange(draft.copy(channel = channel)) },
            )
        }
        DraftField("What next step would help?", draft.nextStep, singleLine = false) {
            onDraftChange(draft.copy(nextStep = it.take(300)))
        }
        if (validationMessage != null) {
            Text(
                validationMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
            Text("Review this request")
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Clear this practice")
        }
    }
}

@Composable
private fun FeedbackScreen(
    draft: AskReadyDraft,
    items: List<AskReadyFeedbackItem>,
    isFinal: Boolean,
    onRevise: () -> Unit,
    onPremium: () -> Unit,
    onReset: () -> Unit,
) {
    ContentColumn {
        Eyebrow(if (isFinal) "FINAL CHECK" else "FIRST CHECK")
        Text("Review the request", style = MaterialTheme.typography.headlineMedium)
        Text(
            "These checks describe what is present in the draft. They do not predict approval or judge your academic answer.",
            style = MaterialTheme.typography.bodyMedium,
        )
        items.forEach { item -> AskReadyFeedbackCard(item) }
        HorizontalDivider()
        Text("Request preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                buildAskReadyPreview(draft),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (!isFinal) {
            Button(onClick = onRevise, modifier = Modifier.fillMaxWidth()) {
                Text("Revise once")
            }
        } else {
            Text(
                "Your final practice result is ready to review before you send anything.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedButton(onClick = onPremium, modifier = Modifier.fillMaxWidth()) {
            Text("Explore Practice Pack")
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Clear this practice")
        }
    }
}

@Composable
private fun AskReadyFeedbackCard(item: AskReadyFeedbackItem) {
    val accent = when (item.state) {
        AskReadyFeedbackState.PASS -> MaterialTheme.colorScheme.primary
        AskReadyFeedbackState.NEEDS_WORK -> MaterialTheme.colorScheme.error
        AskReadyFeedbackState.NOT_ENOUGH_CONTEXT -> MaterialTheme.colorScheme.tertiary
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                item.criterion.displayLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
            Text(item.state.displayLabel(), fontWeight = FontWeight.Bold)
            Text(item.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PremiumLockedScreen(
    state: AskReadyPracticeState.PremiumLocked,
    onBack: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
) {
    ContentColumn {
        Eyebrow("OPTIONAL PRACTICE PACK")
        Text("Practice beyond one assignment", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Unlock bounded scenarios for lab/code problems, concept clarification, and academic processes. The free assignment request stays available.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("What unlocks", style = MaterialTheme.typography.titleMedium)
                Text("Lab/code · Concept clarification · Academic process", style = MaterialTheme.typography.bodyMedium)
                Text("Local scenario selection and a follow-up checklist.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(state.offerStatusLabel, style = MaterialTheme.typography.bodyMedium)
        val notice = state.notice
        if (notice != null) {
            Text(notice, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = onPurchase,
            enabled = state.canPurchase,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Unlock Practice Pack")
        }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text("Restore purchase")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Keep the free flow")
        }
    }
}

@Composable
private fun PracticePackScreen(
    state: AskReadyPracticeState.PremiumUnlocked,
    onScenario: (AskReadyScenario) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    ContentColumn {
        Eyebrow("PRACTICE PACK UNLOCKED")
        Text("Choose a focused situation", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Each scenario uses the same request structure. AskReady still does not answer academic questions or predict a recipient's response.",
            style = MaterialTheme.typography.bodyMedium,
        )
        listOf(
            AskReadyScenario.LAB_CODE,
            AskReadyScenario.CONCEPT_CLARIFICATION,
            AskReadyScenario.ACADEMIC_PROCESS,
        ).forEach { scenario ->
            ScenarioOption(
                scenario = scenario,
                selected = state.selectedScenario == scenario,
                onClick = { onScenario(scenario) },
            )
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start this scenario")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Return to this request")
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Return to start")
        }
    }
}

@Composable
private fun ScenarioOption(
    scenario: AskReadyScenario,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(scenario.displayLabel())
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(scenario.displayLabel())
        }
    }
}

@Composable
private fun ChannelOption(
    channel: AskReadyChannel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(channel.displayLabel())
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(channel.displayLabel())
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    ContentColumn {
        Eyebrow("NEEDS ATTENTION")
        Text("We could not review that yet", style = MaterialTheme.typography.headlineMedium)
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to the request")
        }
    }
}

@Composable
private fun DraftField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
    )
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ContentColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private fun BillingOutcome.toAskReadyEvent(): AskReadyPracticeEvent = when (this) {
    is BillingOutcome.OfferAvailable -> AskReadyPracticeEvent.PracticePackOfferLoaded(
        "${offer.title} · ${offer.price}",
    )

    is BillingOutcome.Access -> if (value == PremiumAccess.UNLOCKED) {
        AskReadyPracticeEvent.PurchaseSucceeded
    } else {
        AskReadyPracticeEvent.PurchaseFailed("No active Practice Pack entitlement was found.")
    }

    is BillingOutcome.Failure -> AskReadyPracticeEvent.PurchaseFailed(message)
    is BillingOutcome.Pending -> AskReadyPracticeEvent.PurchaseFailed(message)
    is BillingOutcome.Unknown -> AskReadyPracticeEvent.PurchaseFailed(message)
    BillingOutcome.Cancelled -> AskReadyPracticeEvent.PurchaseCancelled
}
