package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.storage.ConclusionSessionSnapshot
import dev.nextgen.mobile.storage.LocalStorageNotice
import dev.nextgen.mobile.surfaces.HistorySurfaceAvailability
import dev.nextgen.mobile.surfaces.historySurfaceAvailability

@Composable
internal fun EvidriloHistoryScreen(
    history: ConclusionSessionSnapshot?,
    storageNotice: LocalStorageNotice?,
    onStartPractice: () -> Unit,
    onClear: () -> Unit,
    onOpenDelta: () -> Unit = {},
    onBack: () -> Unit,
    onNavigate: (EvidriloTargetSection) -> Unit,
    selectedSection: EvidriloTargetSection,
    backLabel: String = "Home",
) {
    val availableHistory = history?.takeIf {
        historySurfaceAvailability(it) == HistorySurfaceAvailability.AVAILABLE
    }

    EvidriloTargetSurface(
        selected = selectedSection,
        onNavigate = onNavigate,
    ) {
        EvidriloContentColumn {
            EvidriloBackButton(label = backLabel, onClick = onBack)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("History", style = MaterialTheme.typography.displayMedium)
                Text(
                    "A quiet record of the latest evidence-change comparison on this device.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                "This is not an account timeline. The free core keeps one completed comparison locally and never uploads it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            storageNotice?.let { notice ->
                EvidriloRecoveryNotice(notice = notice)
            }

            if (availableHistory != null) {
                EvidriloHistorySnapshotCard(availableHistory)
                EvidriloPrimaryButton(
                    label = "View evidence delta",
                    onClick = onOpenDelta,
                )
                EvidriloSecondaryButton(
                    label = "Clear latest comparison",
                    onClick = onClear,
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
                    border = BorderStroke(1.dp, EvidriloColors.Separator),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No comparison saved yet.", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Complete the free evidence workflow and the evidence-change round to create one local comparison.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                EvidriloPrimaryButton(
                    label = "Start evidence review",
                    onClick = onStartPractice,
                )
            }
        }
    }
}

@Composable
private fun EvidriloHistorySnapshotCard(snapshot: ConclusionSessionSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LATEST COMPARISON", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
            }
            Text("Base round", style = MaterialTheme.typography.titleLarge)
            EvidriloHistoryValue("Case", snapshot.initialDraft.caseId)
            EvidriloHistoryValue(
                "Evidence",
                snapshot.initialDraft.evidenceRefs.ifEmpty { listOf("None") }.joinToString(),
            )
            EvidriloDivider()
            Text("Changed-evidence round", style = MaterialTheme.typography.titleLarge)
            EvidriloHistoryValue("Case", snapshot.currentDraft.caseId)
            EvidriloHistoryValue(
                "Evidence",
                snapshot.currentDraft.evidenceRefs.ifEmpty { listOf("None") }.joinToString(),
            )
            EvidriloTintPanel {
                Text("Read-only local record", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The saved comparison keeps learner-authored drafts and the active evidence boundary. It is not a score or learning claim.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EvidriloHistoryValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
