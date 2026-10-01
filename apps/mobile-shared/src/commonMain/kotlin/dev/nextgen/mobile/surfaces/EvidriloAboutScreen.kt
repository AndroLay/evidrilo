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
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED

@Composable
internal fun EvidriloAboutScreen(
    onBack: () -> Unit,
    backLabel: String = "Settings",
) {
    EvidriloContentColumn {
        EvidriloBackGesture(label = backLabel, onClick = onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            EvidriloLogoMark(contentDescription = "Evidrilo app icon")
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("About Evidrilo", style = MaterialTheme.typography.displayMedium)
                Text("Build conclusions you can defend", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            "Turn a question into a project you can explain. Organize sources, connect evidence to claims, keep limits visible, and export your work.",
            style = MaterialTheme.typography.bodyLarge,
        )

        EvidriloAboutPanel(
            title = "What this app does",
            body = "Create a local project from five structure-only starters or a blank page. Record sources and evidence, develop findings and claims, review revisions, and export reports or a recovery archive. Three synthetic Practice cases help you rehearse these moves.",
        )
        EvidriloAboutPanel(
            title = "What this app does not do",
            body = "Structure progress is not a grade. Practice checks are bounded to supplied material. AI suggestions require available services, account access, and separate consent; you review suggestions before using them. Evidrilo does not verify your research or replace your instructor.",
        )
        EvidriloAboutPanel(
            title = "Privacy boundary",
            body = if (TEMPORARY_GUEST_MODE_ENABLED) {
                "Projects, the catalog, case work, and history work in local guest mode. Sign-in is optional; Pro requires a signed-in account and confirmed entitlement. Cloud sync and server AI are disabled in this build. Project data stays on this device."
            } else {
                "Local projects, evidence work and Free practice do not need an account. Online AI needs internet, a verified account, consent, credits and an available service. Projects stay on this device; an AI request sends the context you separately confirm. Billing is not required for local work."
            },
        )
        EvidriloAboutPanel(
            title = "Open-source and attribution",
            body = "The repository contains the reproducible application source, setup instructions, and retained third-party notices. Final publication assets and owner eligibility checks remain separate release gates.",
        )
    }
}

@Composable
private fun EvidriloAboutPanel(title: String, body: String) {
    EvidriloExplanation(title, body)
}
