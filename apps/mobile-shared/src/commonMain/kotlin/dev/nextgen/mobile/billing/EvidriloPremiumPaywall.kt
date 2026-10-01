package dev.nextgen.mobile.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
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
import dev.nextgen.mobile.EvidriloBackGesture
import dev.nextgen.mobile.EvidriloProHeader
import dev.nextgen.mobile.EvidriloColors
import dev.nextgen.mobile.EvidriloContentColumn
import dev.nextgen.mobile.EvidriloPrimaryButton
import dev.nextgen.mobile.EvidriloSecondaryButton
import dev.nextgen.mobile.EvidriloStatusChip
import dev.nextgen.mobile.EvidriloTargetCard
import dev.nextgen.mobile.evidriloChoiceColors

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
    onChooseAnotherPlan: (() -> Unit)? = null,
    onManageSubscription: (() -> Unit)? = null,
) {
    val model = premiumPaywallModel(billing.copy(isBusy = isBusy))

    Box(Modifier.fillMaxSize().background(EvidriloColors.Canvas)) {
        EvidriloContentColumn {
            EvidriloBackGesture(label = backLabel, onClick = onBack)
            EvidriloProHeader()

            EvidriloTargetCard {
                Text("Subscription status", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Slate)
                EvidriloStatusChip(label = model.state.displayLabel(), tone = model.state.tone())
                Text(model.title, style = MaterialTheme.typography.titleMedium)
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
            }

            if (model.canPurchase) {
                EvidriloPrimaryButton(
                    label = if (model.isBusy) "Processing purchase…"
                        else "Subscribe ${EvidriloProPlan.forProduct(model.selectedProductId)?.label ?: "to Pro"}",
                    onClick = onPurchase,
                    enabled = !model.isBusy,
                )
            }
            if (model.offers.isNotEmpty() && model.state != PremiumPaywallState.UNLOCKED) {
                Text("Your store confirms the price before purchase. Subscriptions renew automatically each month or year unless cancelled through your store. Both plans add 200 AI credits per active month.",
                    style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
            }
            if (model.canRestore) {
                EvidriloSecondaryButton(
                    label = if (model.isBusy) "Restoring purchase…" else "Restore purchase",
                    onClick = onRestore,
                    enabled = !model.isBusy,
                )
            }
            if (model.state == PremiumPaywallState.UNLOCKED) {
                onManageSubscription?.let { manage ->
                    EvidriloPrimaryButton("Manage subscription", manage, enabled = !model.isBusy)
                }
            }
            if (model.showRetry) {
                EvidriloSecondaryButton(
                    label = "Try again",
                    onClick = onRetry,
                    enabled = !model.isBusy,
                )
                onChooseAnotherPlan?.let { choose ->
                    EvidriloSecondaryButton("Choose another plan", choose, enabled = !model.isBusy)
                }
            }
            if (managedPaywallAvailable && model.state != PremiumPaywallState.UNLOCKED) {
                EvidriloSecondaryButton(
                    label = "View subscription plans",
                    onClick = onOpenManagedPaywall,
                    enabled = !model.isBusy,
                )
            }
            EvidriloSecondaryButton(label = if (model.state == PremiumPaywallState.UNLOCKED) "Done" else "Continue with Free", onClick = onBack)
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                "The first synthetic Practice case is Free. Pro opens all three. Purchases and access follow your account's subscription status.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PremiumOfferChoice(
    offer: BillingOffer,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val choiceColors = evidriloChoiceColors(selected)
    val plan = EvidriloProPlan.forProduct(offer.productId)
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
                contentDescription = "${plan?.label ?: offer.title}, ${offer.price} ${plan?.period.orEmpty()}"
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = choiceColors.container,
            contentColor = choiceColors.content,
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = choiceColors.border,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(plan?.label ?: offer.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (selected) "Selected subscription" else "Select this subscription",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(offer.price, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Cobalt)
                plan?.let { Text(it.period, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
            }
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
    PremiumPaywallState.LOCKED,
    PremiumPaywallState.EMPTY,
    -> dev.nextgen.mobile.EvidriloStatusTone.NEUTRAL
    PremiumPaywallState.PENDING,
    PremiumPaywallState.CANCELLED,
    -> dev.nextgen.mobile.EvidriloStatusTone.WARNING
    PremiumPaywallState.ERROR,
    PremiumPaywallState.UNKNOWN,
    -> dev.nextgen.mobile.EvidriloStatusTone.ERROR
}
