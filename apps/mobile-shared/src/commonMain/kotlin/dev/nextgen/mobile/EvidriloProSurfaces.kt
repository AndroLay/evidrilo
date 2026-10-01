package dev.nextgen.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.billing.BillingPresentation
import dev.nextgen.mobile.billing.BillingUiState
import dev.nextgen.mobile.billing.EvidriloPremiumPaywall
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.practice.PracticeCourseAccessRules
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules

/** The same blue paper-prism emblem is used at every Pro entry. */
@Composable
internal fun EvidriloProEmblem(size: Dp = 56.dp) {
    Surface(Modifier.size(size).clearAndSetSemantics {}, shape = androidx.compose.foundation.shape.CircleShape,
        color=EvidriloColors.Tint) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            EvidriloIcon(EvidriloIconName.CROWN,tint=EvidriloColors.Cobalt,modifier=Modifier.size(size*.58f))
        }
    }
}

@Composable
internal fun EvidriloProEntry(onClick: () -> Unit) {
    EvidriloPressableCard(onClick = onClick, faceColor = EvidriloColors.Atmosphere,
        borderColor = EvidriloColors.Tint, lipColor = EvidriloColors.PatternBlue,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Explore Evidrilo Pro plans and available access"; role = Role.Button }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EvidriloProEmblem()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Evidrilo Pro", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Ink)
                Text("More projects. The full Practice trail.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
            EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = EvidriloColors.Cobalt)
        }
    }
}

@Composable
internal fun EvidriloProHeader() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = EvidriloColors.Atmosphere) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EvidriloProEmblem(64.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Evidrilo Pro", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text("Make room for more.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
                    }
                }
            }
        }
        ProBenefit(EvidriloIconName.FOLDER, uiText("${StudentProjectDraftRules.PRO_ACTIVE_PROJECT_LIMIT} projects total","${StudentProjectDraftRules.PRO_ACTIVE_PROJECT_LIMIT} proyek total"))
        ProBenefit(EvidriloIconName.BOOK, "All ${PracticeCourseAccessRules.proCaseCount} Practice cases")
        ProBenefit(EvidriloIconName.LIGHTNING, "200 AI credits each active month")
        EvidriloExplanation("Credits & access", "Pro needs verified subscription access. Free receives 20 AI credits once after account verification and AI consent; active Pro adds 200 each month, including annual plans. Earned credits accumulate and do not expire. AI needs internet, consent and an available service. Your first Practice case stays Free.")
    }
}

@Composable
private fun ProBenefit(icon: EvidriloIconName, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = EvidriloColors.Tint) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                EvidriloIcon(icon, tint = EvidriloColors.Cobalt)
            }
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    }
}

/** A disabled-provider visual fixture for the debug host; it never grants access or calls a service. */
@Composable
public fun EvidriloProVisualPreview(onClose: () -> Unit) {
    var notice by remember { mutableStateOf("Visual preview · no account, provider or payment is connected.") }
    EvidriloPremiumPaywall(
        billing = BillingPresentation(BillingUiState.UNAVAILABLE, notice),
        isBusy = false, managedPaywallAvailable = false,
        onOpenManagedPaywall = {}, onPurchase = {},
        onRestore = { notice = "This visual preview cannot restore or change a subscription." },
        onRetry = { notice = "Plans come from the provider. This preview does not connect to it." },
        onSelectOffer = {}, onBack = onClose, backLabel = "Close preview",
    )
}
