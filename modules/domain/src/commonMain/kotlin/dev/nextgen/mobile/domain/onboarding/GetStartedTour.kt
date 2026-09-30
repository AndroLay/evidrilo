package dev.nextgen.mobile.domain.onboarding

enum class GetStartedStatus(val storageValue: String) {
    NOT_STARTED("not_started"),
    SKIPPED("skipped"),
    COMPLETED("completed");

    companion object {
        /** Returns null for unknown stored values so callers can fail closed. */
        fun fromStorage(value: String?, legacyCompleted: Boolean): GetStartedStatus? = when (value) {
            null -> if (legacyCompleted) COMPLETED else NOT_STARTED
            "not_started" -> NOT_STARTED
            "skipped" -> SKIPPED
            "completed" -> COMPLETED
            else -> null
        }
    }
}

enum class GetStartedTourStep {
    WELCOME,
    ORGANIZE,
    REVIEW,
    ASSISTANCE,
    PORTABILITY,
    READY,
}

sealed interface GetStartedTourEvent {
    data object Next : GetStartedTourEvent
    data object Back : GetStartedTourEvent
}

/** A self-paced product introduction. It never creates project or research data. */
data class GetStartedTourState(
    val step: GetStartedTourStep = GetStartedTourStep.WELCOME,
    val isComplete: Boolean = false,
) {
    val canContinue: Boolean
        get() = !isComplete

    fun reduce(event: GetStartedTourEvent): GetStartedTourState {
        if (isComplete) return this
        return when (event) {
            GetStartedTourEvent.Next -> {
                if (!canContinue) return this
                val next = GetStartedTourStep.entries.getOrNull(step.ordinal + 1)
                if (next == null) copy(isComplete = true) else copy(step = next)
            }

            GetStartedTourEvent.Back -> {
                val previous = GetStartedTourStep.entries.getOrNull(step.ordinal - 1) ?: return this
                copy(step = previous)
            }
        }
    }
}
