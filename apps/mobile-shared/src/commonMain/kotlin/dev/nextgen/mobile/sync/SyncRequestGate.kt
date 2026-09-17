package dev.nextgen.mobile.sync

/**
 * Binds async sync results to the account and consent state that initiated
 * them. A result from an older session must not update visible state after an
 * account switch, sign-out, or consent change.
 */
internal data class SyncRequestToken(
    val generation: Long,
    val accountId: String,
    val consent: SyncConsent,
)

internal class SyncRequestGate {
    private var generation: Long = 0

    fun begin(accountId: String, consent: SyncConsent): SyncRequestToken {
        generation = nextGeneration(generation)
        return SyncRequestToken(generation, accountId, consent)
    }

    fun invalidate() {
        generation = nextGeneration(generation)
    }

    fun isCurrent(
        token: SyncRequestToken,
        currentAccountId: String?,
        currentConsent: SyncConsent,
    ): Boolean = token.generation == generation &&
        token.accountId == currentAccountId &&
        token.consent == currentConsent

    private fun nextGeneration(value: Long): Long =
        if (value == Long.MAX_VALUE) 0 else value + 1
}
