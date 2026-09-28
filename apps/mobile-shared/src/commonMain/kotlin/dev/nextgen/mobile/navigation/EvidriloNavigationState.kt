package dev.nextgen.mobile.navigation

internal data class EvidriloNavigationState(
    val stack: List<EvidriloDestination> = listOf(EvidriloDestination.HOME),
) {
    companion object {
        /** Restores the exact protected or public destination requested before sign-in. */
        fun afterSuccessfulAccountGate(destination: EvidriloDestination): EvidriloNavigationState =
            EvidriloNavigationState().open(destination)
    }

    init {
        require(stack.isNotEmpty()) { "Navigation stack cannot be empty." }
    }

    val current: EvidriloDestination
        get() = stack.last()

    val canHandleSystemBack: Boolean
        get() = stack.size > 1

    fun open(destination: EvidriloDestination): EvidriloNavigationState =
        if (current == destination) this else copy(stack = stack + destination)

    /** Opens Account above only public routes, so Back never exposes a gated route. */
    fun openAccountGate(): EvidriloNavigationState {
        val lastProtectedRoute = stack.indexOfLast(EvidriloDestination::requiresAuthenticatedFreeAccess)
        val safePublicStack = stack
            .drop(lastProtectedRoute + 1)
            .filterNot { it == EvidriloDestination.ACCOUNT }
            .ifEmpty { listOf(EvidriloDestination.GUIDE) }
        return EvidriloNavigationState(stack = safePublicStack).open(EvidriloDestination.ACCOUNT)
    }

    /**
     * Selects a root destination without retaining a stale stacked route.
     * The active case and draft live outside navigation, so changing tabs does
     * not discard learner work.
     */
    fun selectRoot(destination: EvidriloDestination): EvidriloNavigationState = when (destination) {
        EvidriloDestination.HOME -> resetToHome()
        EvidriloDestination.SOURCES,
        EvidriloDestination.EVIDENCE,
        EvidriloDestination.ACTION,
        EvidriloDestination.PROFILE,
        -> EvidriloNavigationState(stack = listOf(EvidriloDestination.HOME, destination))

        else -> error("${destination.name} is not a root destination")
    }

    fun back(): EvidriloNavigationState =
        if (stack.size == 1) this else copy(stack = stack.dropLast(1))

    fun resetToHome(): EvidriloNavigationState =
        EvidriloNavigationState()
}
