package dev.nextgen.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
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
    var preview by remember { mutableStateOf(GetStartedTemporaryPreview()) }
    val content = getStartedPreviewFor(tourState.step, preview)
    val stepTitle = when (tourState.step) {
        GetStartedTourStep.WELCOME -> "Start with what you have"
        GetStartedTourStep.ORGANIZE -> "Choose what to add first"
        GetStartedTourStep.REVIEW -> "Choose what to review"
    }

    EvidriloContentColumn {
        EvidriloBrandHeader(onSettings = null)
        Text("Start with your work", style = MaterialTheme.typography.displayLarge)
        Text(
            "Choose a starting point. You can change it later.",
            style = MaterialTheme.typography.bodyLarge,
        )
        storageNotice?.takeIf { it.isError }?.let { notice -> EvidriloRecoveryNotice(notice) }
        GetStartedProgress(tourState.step)
        Text(stepTitle, style = MaterialTheme.typography.headlineSmall)
        when (tourState.step) {
            GetStartedTourStep.WELCOME -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GetStartedOnboardingChoice(
                    label = GetStartedStartingPoint.ASSIGNMENT.label,
                    selected = preview.startingPoint == GetStartedStartingPoint.ASSIGNMENT,
                    onSelect = { preview = preview.copy(startingPoint = GetStartedStartingPoint.ASSIGNMENT) },
                )
                GetStartedOnboardingChoice(
                    label = GetStartedStartingPoint.QUESTION.label,
                    selected = preview.startingPoint == GetStartedStartingPoint.QUESTION,
                    onSelect = { preview = preview.copy(startingPoint = GetStartedStartingPoint.QUESTION) },
                )
                GetStartedOnboardingChoice(
                    label = GetStartedStartingPoint.BLANK.label,
                    selected = preview.startingPoint == GetStartedStartingPoint.BLANK,
                    onSelect = { preview = preview.copy(startingPoint = GetStartedStartingPoint.BLANK) },
                )
            }
            GetStartedTourStep.ORGANIZE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    GetStartedFirstMaterial.SOURCES,
                    GetStartedFirstMaterial.OBSERVATIONS,
                    GetStartedFirstMaterial.NOT_SURE,
                ).forEach { option ->
                    GetStartedOnboardingChoice(
                        label = option.label,
                        selected = preview.firstMaterial == option,
                        onSelect = { preview = preview.copy(firstMaterial = option) },
                    )
                }
            }
            GetStartedTourStep.REVIEW -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    GetStartedReviewFocus.TRACE,
                    GetStartedReviewFocus.LIMITATIONS,
                    GetStartedReviewFocus.CHANGES,
                ).forEach { option ->
                    GetStartedOnboardingChoice(
                        label = option.label,
                        selected = preview.reviewFocus == option,
                        onSelect = { preview = preview.copy(reviewFocus = option) },
                    )
                }
            }
        }
        EvidriloTintPanel {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Preview", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
                Text(content.title, style = MaterialTheme.typography.titleLarge)
                Text(content.detail, style = MaterialTheme.typography.bodyMedium)
                Text(content.disclaimer, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
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
private fun GetStartedOnboardingChoice(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val choiceColors = evidriloChoiceColors(selected)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = RoundedCornerShape(14.dp),
        color = choiceColors.container,
        contentColor = choiceColors.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
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
