package dev.nextgen.mobile.billing

import dev.nextgen.mobile.domain.conclusion.ConclusionCase
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionReducer
import dev.nextgen.mobile.domain.conclusion.ConclusionState

sealed interface PremiumPracticeState {
    data object Hidden : PremiumPracticeState

    data class Locked(
        val billing: BillingPresentation,
    ) : PremiumPracticeState

    data class Catalog(
        val billing: BillingPresentation,
        val cases: List<ConclusionCase> = ConclusionCases.premium,
        val selectedCaseId: String? = cases.firstOrNull()?.id,
    ) : PremiumPracticeState {
        val selectedCase: ConclusionCase?
            get() = cases.firstOrNull { it.id == selectedCaseId }
    }

    data class Practice(
        val billing: BillingPresentation,
        val cases: List<ConclusionCase>,
        val case: ConclusionCase,
        val conclusion: ConclusionState,
    ) : PremiumPracticeState
}

sealed interface PremiumPracticeEvent {
    data object Open : PremiumPracticeEvent

    data class BillingResult(val outcome: BillingOutcome) : PremiumPracticeEvent

    data class SelectOffer(val productId: String) : PremiumPracticeEvent

    data class SelectCase(val caseId: String) : PremiumPracticeEvent

    data object BeginSelectedCase : PremiumPracticeEvent

    data class PracticeEvent(val event: ConclusionEvent) : PremiumPracticeEvent

    data object Back : PremiumPracticeEvent

    data object Close : PremiumPracticeEvent
}

class PremiumPracticeReducer(
    private val premiumCases: List<ConclusionCase> = ConclusionCases.premium,
) {
    fun reduce(
        state: PremiumPracticeState,
        event: PremiumPracticeEvent,
    ): PremiumPracticeState = when (event) {
        PremiumPracticeEvent.Open -> PremiumPracticeState.Locked(BillingPresentation.loading())

        is PremiumPracticeEvent.BillingResult -> applyBillingResult(state, event.outcome)

        is PremiumPracticeEvent.SelectOffer -> if (state is PremiumPracticeState.Locked) {
            state.copy(billing = state.billing.selectOffer(event.productId))
        } else {
            state
        }

        is PremiumPracticeEvent.SelectCase -> if (state is PremiumPracticeState.Catalog &&
            state.cases.any { it.id == event.caseId }
        ) {
            state.copy(selectedCaseId = event.caseId)
        } else {
            state
        }

        PremiumPracticeEvent.BeginSelectedCase -> if (state is PremiumPracticeState.Catalog) {
            state.selectedCase?.let { selectedCase ->
                PremiumPracticeState.Practice(
                    billing = state.billing,
                    cases = state.cases,
                    case = selectedCase,
                    conclusion = ConclusionReducer(case = selectedCase)
                        .reduce(ConclusionState.Intro, ConclusionEvent.Begin),
                )
            } ?: state
        } else {
            state
        }

        is PremiumPracticeEvent.PracticeEvent -> if (state is PremiumPracticeState.Practice) {
            state.copy(
                conclusion = ConclusionReducer(case = state.case).reduce(
                    state.conclusion,
                    event.event,
                ),
            )
        } else {
            state
        }

        PremiumPracticeEvent.Back -> when (state) {
            is PremiumPracticeState.Practice -> PremiumPracticeState.Catalog(
                billing = state.billing,
                cases = state.cases,
                selectedCaseId = state.case.id,
            )

            is PremiumPracticeState.Catalog,
            is PremiumPracticeState.Locked,
            PremiumPracticeState.Hidden,
            -> PremiumPracticeState.Hidden
        }

        PremiumPracticeEvent.Close -> PremiumPracticeState.Hidden
    }

    private fun applyBillingResult(
        state: PremiumPracticeState,
        outcome: BillingOutcome,
    ): PremiumPracticeState {
        val previousOffer = when (state) {
            is PremiumPracticeState.Locked -> state.billing.offer
            is PremiumPracticeState.Catalog -> state.billing.offer
            is PremiumPracticeState.Practice -> state.billing.offer
            PremiumPracticeState.Hidden -> null
        }
        val previousOffers = when (state) {
            is PremiumPracticeState.Locked -> state.billing.offers
            is PremiumPracticeState.Catalog -> state.billing.offers
            is PremiumPracticeState.Practice -> state.billing.offers
            PremiumPracticeState.Hidden -> emptyList()
        }
        val presentation = BillingPresentation.fromOutcome(outcome, previousOffer, previousOffers)
        return when (state) {
            is PremiumPracticeState.Catalog -> when (outcome) {
                is BillingOutcome.Access -> if (outcome.value == PremiumAccess.UNLOCKED) {
                    state.copy(billing = presentation)
                } else {
                    PremiumPracticeState.Locked(presentation)
                }

                else -> state.copy(billing = presentation)
            }

            is PremiumPracticeState.Practice -> when (outcome) {
                is BillingOutcome.Access -> if (outcome.value == PremiumAccess.UNLOCKED) {
                    state.copy(billing = presentation)
                } else {
                    PremiumPracticeState.Locked(presentation)
                }

                else -> state.copy(billing = presentation)
            }

            is PremiumPracticeState.Locked -> if (outcome is BillingOutcome.Access && outcome.value == PremiumAccess.UNLOCKED) {
                PremiumPracticeState.Catalog(
                    billing = presentation,
                    cases = premiumCases,
                )
            } else {
                PremiumPracticeState.Locked(presentation)
            }

            PremiumPracticeState.Hidden -> PremiumPracticeState.Hidden
        }
    }
}
