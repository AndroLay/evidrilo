package dev.nextgen.mobile.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.EvidriloBackButton
import dev.nextgen.mobile.EvidriloCobaltCard
import dev.nextgen.mobile.EvidriloColors
import dev.nextgen.mobile.EvidriloContentColumn
import dev.nextgen.mobile.EvidriloIcon
import dev.nextgen.mobile.EvidriloIconName
import dev.nextgen.mobile.EvidriloPrimaryButton
import dev.nextgen.mobile.EvidriloSecondaryButton
import dev.nextgen.mobile.EvidriloStatusChip
import dev.nextgen.mobile.EvidriloStatusTone
import dev.nextgen.mobile.EvidriloTargetCard
import dev.nextgen.mobile.EvidriloTintPanel

@Composable
internal fun EvidriloPremiumPaywall(
    billing: BillingPresentation,
    isBusy: Boolean,
    managedPaywallAvailable: Boolean,
    onOpenManagedPaywall: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onSelectOffer: (String) -> Unit,
    onBack: () -> Unit,
    backLabel: String,
) {
    val model = premiumPaywallModel(billing.copy(isBusy = isBusy))

    EvidriloContentColumn {
        EvidriloBackButton(label = backLabel, onClick = onBack)
        Text("EVIDRILO PREMIUM", style = MaterialTheme.typography.labelSmall, color = EvidriloColors.Cobalt)
        Text(model.title, style = MaterialTheme.typography.displayMedium)
        Text(
            "Practice two more evidence-linked cases when the free case is not enough. Your free practice remains usable offline.",
            style = MaterialTheme.typography.bodyLarge,
        )

        EvidriloCobaltCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = EvidriloColors.White.copy(alpha = 0.18f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        EvidriloIcon(EvidriloIconName.CROWN, tint = EvidriloColors.White)
                    }
                }
                Column(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Evidrilo Pro", style = MaterialTheme.typography.titleLarge, color = EvidriloColors.White)
                    Text("Unlock deeper analysis.", style = MaterialTheme.typography.bodyLarge, color = EvidriloColors.White)
                }
            }
            Text(
                "Access is granted only by the configured entitlement. This screen never grants access locally.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.White.copy(alpha = 0.9f),
            )
        }

        EvidriloTargetCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Access status", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                EvidriloStatusChip(label = model.state.displayLabel(), tone = model.state.tone())
            }
            Text(model.message, style = MaterialTheme.typography.bodyMedium)
            if (model.state == PremiumPaywallState.LOADING) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = EvidriloColors.Cobalt,
                        strokeWidth = 3.dp,
                    )
                    Text("Checking provider access…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (model.offers.isNotEmpty() && model.state != PremiumPaywallState.UNLOCKED) {
            Text("Choose a plan", style = MaterialTheme.typography.titleLarge)
            model.offers.forEach { offer ->
                PremiumOfferChoice(
                    offer = offer,
                    selected = offer.productId == model.selectedProductId,
                    enabled = !model.isBusy,
                    onClick = { onSelectOffer(offer.productId) },
                )
            }
        } else if (model.state == PremiumPaywallState.EMPTY) {
            EvidriloTintPanel {
                Text("No approved plans are available", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Only monthly and yearly subscriptions can appear here. Try again when the provider is configured.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (model.canPurchase) {
            EvidriloPrimaryButton(
                label = if (model.isBusy) "Processing purchase…" else "Unlock practice pack",
                onClick = onPurchase,
                enabled = !model.isBusy,
            )
        }
        if (model.canRestore) {
            EvidriloSecondaryButton(
                label = if (model.isBusy) "Restoring purchase…" else "Restore purchase",
                onClick = onRestore,
                enabled = !model.isBusy,
            )
        }
        if (model.showRetry) {
            EvidriloSecondaryButton(
                label = "Try again",
                onClick = onRetry,
                enabled = !model.isBusy,
            )
        }
        if (managedPaywallAvailable && model.state != PremiumPaywallState.UNLOCKED) {
            EvidriloSecondaryButton(
                label = "Open managed RevenueCat plans",
                onClick = onOpenManagedPaywall,
                enabled = !model.isBusy,
            )
        }
        EvidriloSecondaryButton(label = "Keep the free case", onClick = onBack)
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            "No account, network, database, AI, or subscription is required for the free core.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PremiumOfferChoice(
    offer: BillingOffer,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${offer.title}, ${offer.price}"
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) EvidriloColors.Tint else EvidriloColors.White,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) EvidriloColors.Cobalt else EvidriloColors.Separator,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(offer.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (selected) "Selected subscription" else "Select this subscription",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(offer.price, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Cobalt)
        }
    }
}

private fun PremiumPaywallState.displayLabel(): String = when (this) {
    PremiumPaywallState.LOADING -> "Checking access"
    PremiumPaywallState.LOCKED -> "Premium locked"
    PremiumPaywallState.OFFERS_AVAILABLE -> "Plans available"
    PremiumPaywallState.EMPTY -> "Plans unavailable"
    PremiumPaywallState.ERROR -> "Purchase failed"
    PremiumPaywallState.PENDING -> "Purchase pending"
    PremiumPaywallState.CANCELLED -> "Purchase cancelled"
    PremiumPaywallState.UNKNOWN -> "Needs reconciliation"
    PremiumPaywallState.UNLOCKED -> "Premium unlocked"
}

private fun PremiumPaywallState.tone(): dev.nextgen.mobile.EvidriloStatusTone = when (this) {
    PremiumPaywallState.LOADING,
    PremiumPaywallState.OFFERS_AVAILABLE,
    PremiumPaywallState.UNLOCKED,
    -> dev.nextgen.mobile.EvidriloStatusTone.INFO
    PremiumPaywallState.LOCKED -> dev.nextgen.mobile.EvidriloStatusTone.NEUTRAL
    PremiumPaywallState.EMPTY,
    PremiumPaywallState.PENDING,
    PremiumPaywallState.CANCELLED,
    -> dev.nextgen.mobile.EvidriloStatusTone.WARNING
    PremiumPaywallState.ERROR,
    PremiumPaywallState.UNKNOWN,
    -> dev.nextgen.mobile.EvidriloStatusTone.ERROR
}
