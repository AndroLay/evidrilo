package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EvidriloAboutScreen(
    onBack: () -> Unit,
    backLabel: String = "Settings",
) {
    EvidriloContentColumn {
        EvidriloBackButton(label = backLabel, onClick = onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvidriloLogoMark(contentDescription = "Evidrilo app icon")
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("About Evidrilo", style = MaterialTheme.typography.displayMedium)
                Text("Build conclusions you can defend", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            "Evidrilo helps a learner connect supplied observations to a bounded conclusion, then see what changes when one observation is unavailable.",
            style = MaterialTheme.typography.bodyLarge,
        )

        EvidriloAboutPanel(
            title = "What this app does",
            body = "It checks evidence links, claim scope, limitations, and one practical next action using deterministic rules anchored to the supplied case.",
        )
        EvidriloAboutPanel(
            title = "What this app does not do",
            body = "It does not grade science, determine truth, generate an answer for you, or claim a learning outcome. Unsupported input produces an explicit bounded result.",
        )
        EvidriloAboutPanel(
            title = "Privacy boundary",
            body = "The free evidence workflow is local-first. Optional account, sync, AI, and billing integrations remain separate boundaries and are not required for the free case.",
        )
        EvidriloAboutPanel(
            title = "Open-source and attribution",
            body = "The repository contains the reproducible application source, setup instructions, and retained third-party notices. Final publication assets and owner eligibility checks remain separate release gates.",
        )
    }
}

@Composable
private fun EvidriloAboutPanel(title: String, body: String) {
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
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
