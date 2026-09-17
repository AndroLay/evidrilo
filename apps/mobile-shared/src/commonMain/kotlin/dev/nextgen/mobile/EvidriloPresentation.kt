package dev.nextgen.mobile

import dev.nextgen.mobile.billing.BillingUiState
import dev.nextgen.mobile.domain.conclusion.ConclusionCheck
import dev.nextgen.mobile.domain.conclusion.ConclusionFactType
import dev.nextgen.mobile.domain.conclusion.ConclusionField
import dev.nextgen.mobile.domain.conclusion.ConclusionStatus

internal fun ConclusionStatus.displayLabel(): String = when (this) {
    ConclusionStatus.INCOMPLETE -> "Incomplete"
    ConclusionStatus.ACTION_REQUIRED -> "Action needed"
    ConclusionStatus.PASS -> "Pass"
    ConclusionStatus.CANNOT_ASSESS -> "Cannot assess from the supplied information"
}

internal fun BillingUiState.displayLabel(): String = when (this) {
    BillingUiState.LOADING -> "Checking access"
    BillingUiState.LOCKED -> "Premium locked"
    BillingUiState.OFFER_AVAILABLE -> "Offer available"
    BillingUiState.UNLOCKED -> "Premium unlocked"
    BillingUiState.UNAVAILABLE -> "Offer unavailable"
    BillingUiState.CANCELLED -> "Purchase cancelled"
    BillingUiState.FAILED -> "Purchase failed"
    BillingUiState.PENDING -> "Purchase pending"
    BillingUiState.UNKNOWN -> "Purchase needs reconciliation"
}

internal fun ConclusionCheck.displayLabel(): String = when (this) {
    ConclusionCheck.GOAL_CONNECTEDNESS -> "Goal connection"
    ConclusionCheck.EVIDENCE_ANCHORING -> "Evidence anchoring"
    ConclusionCheck.SCOPE_UNCERTAINTY -> "Scope and uncertainty"
    ConclusionCheck.ACTIONABLE_IMPLICATION -> "Actionable implication"
}

internal fun ConclusionField.displayLabel(): String = when (this) {
    ConclusionField.CASE_ID -> "Case"
    ConclusionField.RELATION -> "Relation"
    ConclusionField.EVIDENCE_REFS -> "Evidence facts"
    ConclusionField.CLAIM_TEXT -> "Claim text"
    ConclusionField.SCOPE -> "Scope"
    ConclusionField.LIMITATION_REFS -> "Limitation facts"
    ConclusionField.LIMITATION_NOTE -> "Limitation note"
    ConclusionField.IMPLICATION -> "Next action"
    ConclusionField.IMPLICATION_REASON -> "Next-action reason"
}

internal fun ConclusionFactType.displayLabel(): String = when (this) {
    ConclusionFactType.AIM -> "Aim"
    ConclusionFactType.CONTEXT -> "Context"
    ConclusionFactType.OBSERVATION -> "Observation"
    ConclusionFactType.LIMITATION -> "Limitation"
    ConclusionFactType.BOUNDARY -> "Boundary"
}
