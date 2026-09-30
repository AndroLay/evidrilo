package dev.nextgen.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules

/** The same blue paper-prism emblem is used at every Pro entry. */
@Composable
internal fun EvidriloProEmblem(size: Dp = 56.dp) {
    val light = EvidriloColors.CobaltBright
    val face = EvidriloColors.PrimaryAction
    val edge = EvidriloColors.CobaltPressed
    Surface(Modifier.size(size).clearAndSetSemantics {}, shape = RoundedCornerShape(size * .28f), color = EvidriloColors.Tint) {
        Canvas(Modifier.fillMaxSize().padding(size * .16f)) {
            val width = this.size.width
            val height = this.size.height
            fun facet(vararg points: Pair<Float, Float>) = Path().apply {
                points.forEachIndexed { i, (x,y) -> if (i == 0) moveTo(x * width, y * height) else lineTo(x * width, y * height) }; close()
            }
            drawPath(facet(.5f to .04f, .94f to .27f, .5f to .51f, .06f to .27f), light)
            drawPath(facet(.06f to .27f, .5f to .51f, .5f to .97f, .06f to .72f), face)
            drawPath(facet(.5f to .51f, .94f to .27f, .94f to .72f, .5f to .97f), edge)
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
                Text("More project space. Deeper evidence cases.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
            EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = EvidriloColors.Cobalt)
        }
    }
}

@Composable
internal fun EvidriloProHeader() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = EvidriloColors.Atmosphere) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    EvidriloProEmblem(64.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Evidrilo Pro", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                        Text("Make room for more.", style = MaterialTheme.typography.bodyLarge, color = EvidriloColors.Slate)
                    }
                }
                Text("Follow bigger questions. Keep the evidence in view.", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Ink)
            }
        }
        ProBenefit(EvidriloIconName.FOLDER, "More room for projects",
            "Up to ${StudentProjectDraftRules.PRO_ACTIVE_PROJECT_LIMIT} active projects with verified Pro access.")
        ProBenefit(EvidriloIconName.EVIDENCE_GRAPH, "Explore deeper cases",
            "${ConclusionCases.premium.size} additional evidence-linked cases, separate from your projects.")
        Text("Your three Free practice cases stay available. More Pro investigations are planned.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
    }
}

@Composable
private fun ProBenefit(icon: EvidriloIconName, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = EvidriloColors.Tint) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                EvidriloIcon(icon, tint = EvidriloColors.Cobalt)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
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
