package dev.nextgen.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.practice.PracticeCourseAccessRules
import dev.nextgen.mobile.domain.project.StudentProjectDraftRules
import dev.nextgen.mobile.billing.EvidriloProPlan

/** Public product information only. This surface never loads offers or changes account access. */
@Composable
internal fun EvidriloProComparisonScreen(
    signedIn: Boolean,
    plansAvailable: Boolean,
    onChoosePlan: (String) -> Unit,
    onClose: () -> Unit,
) {
    var selectedPlan by remember { mutableStateOf(EvidriloProPlan.MONTHLY) }
    Column(Modifier.fillMaxSize().background(EvidriloColors.Canvas)) {
        Box(Modifier.weight(1f)) {
        EvidriloContentColumn(includeBottomSafeArea = false, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EvidriloBackGesture("Close plan comparison", onClose)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EvidriloProEmblem(60.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Make room for more.", style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() })
                    Text("Evidrilo Pro", style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Cobalt)
                }
            }
            EvidriloPlanComparison()
            Text("Choose a plan", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EvidriloProPlan.entries.forEach { plan ->
                val selected = selectedPlan == plan
                val largeText = LocalDensity.current.fontScale > 1.4f
                val color by animateColorAsState(if (selected) EvidriloColors.Tint else EvidriloColors.Atmosphere, label = "Plan selection")
                Surface(modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = { selectedPlan = plan }),
                    shape = RoundedCornerShape(18.dp), color = color) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = EvidriloColors.Cobalt, unselectedColor = EvidriloColors.Slate))
                            if (largeText) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(plan.label, style = MaterialTheme.typography.titleMedium)
                                Text(plan.referencePrice, style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Cobalt)
                                Text(plan.period, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                            } else {
                            Text(plan.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(plan.referencePrice, style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Cobalt)
                                Text(plan.period, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                            }
                            }
                        }
                }
            }
            }
            EvidriloExplanation("Plan details", "Free includes the first Practice case; Pro opens all three. Free credits are granted once to a verified account after AI consent. Active Pro adds 200 credits each month, including annual plans. Earned credits accumulate without expiring. AI also needs internet, consent and an available service. Choosing a plan never starts a purchase: review and confirm the store offer first.")
            if (!plansAvailable) Text("Pro plans are unavailable in this build. Free stays available.", style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
        }
        Surface(color = EvidriloColors.Card, shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("USD reference prices · final price in your store. Renews each ${selectedPlan.renewalPeriod} unless cancelled.","Harga acuan USD · harga akhir di toko Anda. Diperpanjang setiap ${uiText(selectedPlan.renewalPeriod)} kecuali dibatalkan."),
                    style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
                EvidriloPrimaryButton("Continue ${selectedPlan.label}", { onChoosePlan(selectedPlan.productId) },
                    enabled = plansAvailable, trailingIcon = EvidriloIconName.ARROW_FORWARD)
                TextButton(onClose, Modifier.fillMaxWidth()) { Text(uiText("Continue with Free"), color = EvidriloColors.Cobalt) }
            }
        }
    }
}

/** Counts come from shipped domain content and limits, rather than a separate pricing claim. */
@Composable
internal fun EvidriloPlanComparison() {
    Surface(shape = RoundedCornerShape(20.dp), color = EvidriloColors.Atmosphere) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Included", Modifier.weight(1.45f), style = MaterialTheme.typography.labelLarge)
                Text(uiText("Free"), Modifier.weight(.8f), style = MaterialTheme.typography.titleMedium)
                Text("Pro", Modifier.weight(.8f), style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Cobalt)
            }
            HorizontalDivider(color = EvidriloColors.PatternBlue)
            PlanComparisonRow(uiText("Project spaces","Ruang proyek"), StudentProjectDraftRules.FREE_ACTIVE_PROJECT_LIMIT.toString(),
                StudentProjectDraftRules.PRO_ACTIVE_PROJECT_LIMIT.toString())
            HorizontalDivider(color = EvidriloColors.PatternBlue)
            PlanComparisonRow("Practice cases", PracticeCourseAccessRules.FREE_CASE_COUNT.toString(), PracticeCourseAccessRules.proCaseCount.toString())
            HorizontalDivider(color = EvidriloColors.PatternBlue)
            PlanComparisonRow("AI credits", "+20", "+200", freeNote = "One time", proNote = "Each month")
            HorizontalDivider(color = EvidriloColors.PatternBlue)
        }
    }
}

@Composable
private fun PlanComparisonRow(feature: String, free: String, pro: String, freeNote: String? = null, proNote: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics(mergeDescendants = true) {
        contentDescription = "$feature. Free: $free ${freeNote.orEmpty()}. Pro: $pro ${proNote.orEmpty()}."
    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(feature, Modifier.weight(1.45f), style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.weight(.8f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(free, style = MaterialTheme.typography.titleLarge)
            freeNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Slate) }
        }
        Column(Modifier.weight(.8f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(pro, style = MaterialTheme.typography.titleLarge, color = EvidriloColors.Cobalt)
            proNote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Slate) }
        }
    }
}

/** Debug host bridge for the same public comparison; no offer, account or entitlement is created. */
@Composable
public fun EvidriloProComparisonVisualPreview(onClose: () -> Unit) {
    EvidriloProComparisonScreen(signedIn = false, plansAvailable = false, onChoosePlan = {}, onClose = onClose)
}
