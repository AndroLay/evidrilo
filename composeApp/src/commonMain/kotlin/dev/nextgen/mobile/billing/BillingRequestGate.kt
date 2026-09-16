package dev.nextgen.mobile.billing

/**
 * Binds an asynchronous billing callback to both its request generation and
 * the RevenueCat customer identity that initiated it. Account changes must
 * invalidate callbacks from the previous customer before they can update UI.
 */
internal data class BillingRequestToken(
    val generation: Long,
    val accountId: String?,
)

internal class BillingRequestGate {
    private var generation = 0L

    fun begin(accountId: String?): BillingRequestToken {
        generation = nextGeneration(generation)
        return BillingRequestToken(generation, accountId)
    }

    fun invalidate() {
        generation = nextGeneration(generation)
    }

    fun isCurrent(
        token: BillingRequestToken,
        currentAccountId: String?,
    ): Boolean = token.generation == generation && token.accountId == currentAccountId

    private fun nextGeneration(value: Long): Long =
        if (value == Long.MAX_VALUE) 0L else value + 1L
}
