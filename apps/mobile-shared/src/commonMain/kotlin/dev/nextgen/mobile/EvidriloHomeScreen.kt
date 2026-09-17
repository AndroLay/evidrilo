package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.audio.AudioPlaybackState
import dev.nextgen.mobile.audio.EvidriloAudioListenControl
import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.design.resources.evidriloCaseFolioDrawable
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.LocalStorageNotice
import dev.nextgen.mobile.recommendation.RecommendationUiState
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun EvidriloHomeScreen(
    state: ConclusionState,
    history: ConclusionSessionSnapshot?,
    storageNotice: LocalStorageNotice?,
    onPrimaryAction: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenPacks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    recommendation: RecommendationUiState = RecommendationUiState.Hidden,
    onAcceptRecommendation: () -> Unit = {},
    onDismissRecommendation: () -> Unit = {},
    onRetryRecommendation: () -> Unit = {},
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    val case = homeCaseForState(state)
    val observations = homeObservationPreview(case)
    val primaryAction = homePrimaryAction(state)

    EvidriloRootSurface(
        selected = EvidriloRootDestination.PRACTICE,
        onPractice = { },
        onPacks = onOpenPacks,
        onHistory = onOpenHistory,
    ) {
        EvidriloContentColumn {
            EvidriloBrandHeader(onSettings = onOpenSettings)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Make your case.",
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(
                    "Start with the evidence.",
                    style = MaterialTheme.typography.headlineLarge,
                    color = EvidriloColors.Cobalt,
                )
                Text(
                    "Build a conclusion from supplied observations, then keep the claim within what the case can support.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            EvidriloAudioListenControl(
                state = audioState,
                onListen = onListen,
                onPauseOrResume = onPauseOrResumeAudio,
                onStopAudio = onStopAudio,
            )

            EvidriloCaseFolio(case = case, observations = observations)

            EvidriloRecommendationCard(
                state = recommendation,
                onAccept = onAcceptRecommendation,
                onDismiss = onDismissRecommendation,
                onRetry = onRetryRecommendation,
            )

            Text(
                "Synthetic case · ${observations.size} supplied ${if (observations.size == 1) "observation" else "observations"}",
                style = MaterialTheme.typography.labelMedium,
            )

            EvidriloPrimaryButton(
                label = primaryAction.label,
                onClick = onPrimaryAction,
            )
            Text(
                "Free practice works offline. Your draft stays on this device.",
                style = MaterialTheme.typography.bodySmall,
            )

            EvidriloSettingsGroup {
                EvidriloSettingsRow(
                    icon = EvidriloIconName.BOOK,
                    title = "Practice guide",
                    subtitle = "Evidence · claim · limits · revision",
                    onClick = onOpenGuide,
                )
                EvidriloDivider()
                EvidriloSettingsRow(
                    icon = EvidriloIconName.LAYERS,
                    title = "Practice packs",
                    subtitle = "Optional cases · access is configured separately",
                    onClick = onOpenPacks,
                )
            }

            if (history != null) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = EvidriloColors.SuccessSurface),
                    border = BorderStroke(1.dp, EvidriloColors.Separator),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Local comparison ready", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Your latest evidence-change comparison is available in History.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            storageNotice?.let { notice ->
                EvidriloRecoveryNotice(notice = notice)
            }
        }
    }
}

@Composable
private fun EvidriloCaseFolio(
    case: ConclusionCase,
    observations: List<EvidriloHomeObservation>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(192.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(EvidriloColors.Cobalt),
            ) {
                Image(
                    painter = painterResource(evidriloCaseFolioDrawable),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "FREE PRACTICE",
                    style = MaterialTheme.typography.labelSmall,
                    color = EvidriloColors.Cobalt,
                )
                Text(case.title, style = MaterialTheme.typography.titleLarge)
                Text(case.description, style = MaterialTheme.typography.bodyMedium)
            }
            val observationRowModifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
            if (homeObservationLayout(observations.size) == EvidriloHomeObservationLayout.FIT) {
                Row(
                    modifier = observationRowModifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    observations.forEach { observation ->
                        EvidriloObservationCell(observation, Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    modifier = observationRowModifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    observations.forEach { observation ->
                        EvidriloObservationCell(observation, Modifier.width(128.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidriloObservationCell(
    observation: EvidriloHomeObservation,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(EvidriloColors.White)
            .semantics(mergeDescendants = true) {
                contentDescription = "${observation.label}: ${observation.value}"
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(observation.label, style = MaterialTheme.typography.labelMedium)
        Text(observation.value, style = MaterialTheme.typography.titleMedium)
    }
}
