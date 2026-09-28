package dev.nextgen.mobile.account

data class AccountSummary(
    val accountId: String,
    val emailVerified: Boolean,
    /** Null means the provider identity list has not been verified on this device yet. */
    val googleLinked: Boolean? = null,
)
