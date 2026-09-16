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

    fun back(): EvidriloNavigationState =
        if (stack.size == 1) this else copy(stack = stack.dropLast(1))

    fun resetToHome(): EvidriloNavigationState =
        EvidriloNavigationState()
}
