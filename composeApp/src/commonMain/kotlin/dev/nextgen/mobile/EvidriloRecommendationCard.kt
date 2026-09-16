package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.recommendation.RecommendationUiState
import dev.nextgen.mobile.recommendation.toCardPresentation

@Composable
internal fun EvidriloRecommendationCard(
    state: RecommendationUiState,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        RecommendationUiState.Hidden,
        RecommendationUiState.Abstained,
        is RecommendationUiState.Unsupported,
        RecommendationUiState.Expired,
        RecommendationUiState.Rejected,
        -> Unit

        RecommendationUiState.Loading -> EvidriloRecommendationStatus(
            message = "Loading suggested practice",
            description = "Loading a suggested practice. Local practice remains available.",
        )

        RecommendationUiState.ActionInProgress -> EvidriloRecommendationStatus(
            message = "Starting suggested practice",
            description = "Starting the suggested practice. Local practice remains available.",
        )

        is RecommendationUiState.Unavailable -> EvidriloRecommendationUnavailable(onRetry)

        is RecommendationUiState.Available -> {
            val presentation = state.recommendation.toCardPresentation()
            if (presentation != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = presentation.contentDescription
                        },
                    colors = CardDefaults.cardColors(containerColor = EvidriloColors.Tint),
                    border = BorderStroke(1.dp, EvidriloColors.Separator),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("SUGGESTED NEXT STEP", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
                        Text(presentation.title, style = MaterialTheme.typography.titleLarge)
                        Text("Focus: ${presentation.objective}", style = MaterialTheme.typography.bodyLarge)
                        Text(presentation.explanation, style = MaterialTheme.typography.bodyMedium)
                        Text(presentation.evidenceSummary, style = MaterialTheme.typography.labelMedium)
                        EvidriloPrimaryButton(label = presentation.acceptLabel, onClick = onAccept)
                        EvidriloSecondaryButton(label = presentation.dismissLabel, onClick = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidriloRecommendationStatus(
    message: String,
    description: String,
) {
    EvidriloTintPanel(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EvidriloRecommendationUnavailable(
    onRetry: () -> Unit,
) {
    EvidriloTintPanel(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = recommendationUnavailableCopy()
        },
    ) {
        Text(recommendationUnavailableCopy(), style = MaterialTheme.typography.bodyMedium)
        EvidriloSecondaryButton(label = "Try again", onClick = onRetry)
    }
}
