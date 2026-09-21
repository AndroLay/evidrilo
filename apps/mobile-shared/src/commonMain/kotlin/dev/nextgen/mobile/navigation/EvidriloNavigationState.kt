package dev.nextgen.mobile.navigation

internal data class EvidriloNavigationState(
    val stack: List<EvidriloDestination> = listOf(EvidriloDestination.HOME),
) {
    init {
        require(stack.isNotEmpty()) { "Navigation stack cannot be empty." }
    }

    val current: EvidriloDestination
        get() = stack.last()

    fun open(destination: EvidriloDestination): EvidriloNavigationState =
        if (current == destination) this else copy(stack = stack + destination)

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
