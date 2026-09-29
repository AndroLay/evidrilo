package dev.nextgen.mobile.account

data class AccountSummary(
    val accountId: String,
    val emailVerified: Boolean,
    /** Null means the provider identity list has not been verified on this device yet. */
    val googleLinked: Boolean? = null,
    val appleLinked: Boolean? = null,
    /** Present only when returned by the verified account response; may be Apple's private relay address. */
    val email: String? = null,
) {
    override fun toString(): String =
        "AccountSummary(accountId=redacted, emailVerified=$emailVerified, googleLinked=$googleLinked, appleLinked=$appleLinked, email=redacted)"
}
