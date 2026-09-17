package dev.nextgen.mobile.account

import dev.nextgen.mobile.security.StoredAccountSession

internal interface AccountRepository {
    val state: AccountSession

    fun restore(): AccountSession

    fun beginSignIn(): AccountSession

    fun acceptGatewayResult(result: AccountGatewayResult): AccountSession

    fun acceptVerifiedSession(session: StoredAccountSession): AccountSession

    fun markExpired(): AccountSession

    fun signOut(): AccountSession
}
