package dev.nextgen.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.onboarding.GetStartedTourState
import dev.nextgen.mobile.domain.onboarding.GetStartedTourStep
import dev.nextgen.mobile.storage.LocalStorageNotice

@Composable
internal fun EvidriloOnboardingScreen(
    tourState: GetStartedTourState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onStartProject: () -> Unit,
    storageNotice: LocalStorageNotice? = null,
) {
    EvidriloContentColumn {
        EvidriloBrandHeader(onSettings = null)
        Text("Start with your work", style = MaterialTheme.typography.displayLarge)
        Text(
            "Bring an assignment, research question, or a blank page. Evidrilo helps you keep the reasoning connected as your work develops.",
            style = MaterialTheme.typography.bodyLarge,
        )
        storageNotice?.takeIf { it.isError }?.let { notice -> EvidriloRecoveryNotice(notice) }
        GetStartedProgress(tourState.step)
        EvidriloTintPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val (heading, detail) = when (tourState.step) {
                    GetStartedTourStep.WELCOME -> "Begin with a question" to
                        "Use your own assignment or research question. If you are not ready, start with a blank project and add details later."
                    GetStartedTourStep.ORGANIZE -> "Keep each part distinct" to
                        "Record source material, observations, your interpretation, limitations, and next actions separately. Nothing is treated as evidence until you choose and record it."
                    GetStartedTourStep.REVIEW -> "Review the reasoning" to
                        "Trace a claim to the evidence you selected, see what remains uncertain, and revise when the record changes. Evidrilo does not decide scientific truth."
                }
                Text(heading, style = MaterialTheme.typography.titleLarge)
                Text(detail, style = MaterialTheme.typography.bodyMedium)
            }
        }

        EvidriloPrimaryButton(
            label = if (tourState.step == GetStartedTourStep.REVIEW) "Open My Projects" else "Continue",
            onClick = if (tourState.step == GetStartedTourStep.REVIEW) onStartProject else onNext,
            enabled = tourState.canContinue,
        )
        if (tourState.step != GetStartedTourStep.WELCOME) {
            EvidriloSecondaryButton(label = "Back", onClick = onBack)
        }
        EvidriloSecondaryButton(
            label = "Skip for now",
            onClick = onSkip,
        )
    }
}

@Composable
private fun GetStartedProgress(step: GetStartedTourStep) {
    val titles = listOf("Your question", "Your materials", "Your review")
    val current = step.ordinal + 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Get Started progress"
                stateDescription = "Section $current of ${titles.size}: ${titles[step.ordinal]}"
                progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 1f..titles.size.toFloat(), titles.size - 1)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        titles.indices.forEach { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (index < current) EvidriloColors.Cobalt else EvidriloColors.PaleBlue),
            )
        }
    }
}
