package dev.nextgen.mobile.security

import dev.nextgen.mobile.account.AccountSummary

class SecureSessionMaterial(
    val accessToken: String,
    val expiresAtEpochSeconds: Long,
    val refreshToken: String? = null,
) {
    init {
        require(isSafeHttpToken(accessToken)) { "Access token is invalid." }
        require(expiresAtEpochSeconds > 0) { "Session expiry must be positive." }
        require(refreshToken == null || isSafeHttpToken(refreshToken)) { "Refresh token is invalid." }
    }

    override fun equals(other: Any?): Boolean = other is SecureSessionMaterial
        && accessToken == other.accessToken
        && expiresAtEpochSeconds == other.expiresAtEpochSeconds
        && refreshToken == other.refreshToken

    override fun hashCode(): Int = ((31 * accessToken.hashCode()) + expiresAtEpochSeconds.hashCode()) * 31 + refreshToken.hashCode()

    override fun toString(): String = "SecureSessionMaterial(redacted)"

    private fun isSafeHttpToken(value: String): Boolean =
        value.length in 1..MAX_SESSION_TOKEN_LENGTH && value.all { it.code in 0x21..0x7e }
}

private const val MAX_SESSION_TOKEN_LENGTH = 8 * 1024

data class StoredAccountSession(
    val account: AccountSummary,
    val material: SecureSessionMaterial,
) {
    override fun toString(): String = "StoredAccountSession(account=$account, material=redacted)"
}

interface SecureSessionStore {
    fun read(): StoredAccountSession?

    fun write(session: StoredAccountSession)

    fun clear()
}

class NoopSecureSessionStore : SecureSessionStore {
    override fun read(): StoredAccountSession? = null

    override fun write(session: StoredAccountSession) = Unit

    override fun clear() = Unit
}

object SecureSessionRecordCodec {
    fun encode(session: StoredAccountSession): String = listOf(
        session.account.accountId,
        session.account.emailVerified.toString(),
        session.material.expiresAtEpochSeconds.toString(),
        session.material.accessToken,
        session.material.refreshToken.orEmpty(),
        session.account.googleLinked?.toString().orEmpty(),
        session.account.appleLinked?.toString().orEmpty(),
        session.account.email
            ?.takeIf { session.account.emailVerified && isSafeVerifiedEmail(it) }
            .orEmpty(),
    ).joinToString(".") { hex(it) }

    fun decode(value: String): StoredAccountSession? = runCatching {
        val fields = value.split('.')
        if (fields.size !in 4..8) return@runCatching null

        val decoded = fields.map { unhex(it) ?: return@runCatching null }
        val accountId = decoded[0].takeIf(::isSafeAccountId) ?: return@runCatching null
        val verified = decoded[1].toBooleanStrictOrNull() ?: return@runCatching null
        val expiry = decoded[2].toLongOrNull() ?: return@runCatching null
        val accessToken = decoded[3].takeIf { it.isNotBlank() } ?: return@runCatching null
        val storedGoogleLinked = if (fields.size >= 6) decoded[5].toBooleanStrictOrNull() else null
        if (fields.size >= 6 && storedGoogleLinked == null && decoded[5].isNotBlank()) return@runCatching null
        val appleLinked = if (fields.size >= 7) decoded[6].toBooleanStrictOrNull() else null
        if (fields.size >= 7 && appleLinked == null && decoded[6].isNotBlank()) return@runCatching null
        val email = if (fields.size >= 8) decoded[7].takeIf { it.isNotBlank() } else null
        if (email != null && (!verified || !isSafeVerifiedEmail(email))) return@runCatching null
        val sessionRefreshToken = if (fields.size >= 5) decoded[4].takeIf { it.isNotBlank() } else null
        StoredAccountSession(
            account = AccountSummary(
                accountId = accountId,
                emailVerified = verified,
                googleLinked = storedGoogleLinked,
                appleLinked = appleLinked,
                email = email,
            ),
            material = SecureSessionMaterial(accessToken, expiry, sessionRefreshToken),
        )
    }.getOrNull()

    private fun isSafeAccountId(value: String): Boolean =
        value.length in 1..MAX_ACCOUNT_ID_LENGTH &&
            value.all {
                it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_'
            }

    private fun isSafeVerifiedEmail(value: String): Boolean =
        value.length in 3..254 &&
            value.count { it == '@' } == 1 &&
            value.substringBefore('@').isNotBlank() &&
            value.substringAfter('@').contains('.') &&
            value.none { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f }

    private fun hex(value: String): String = value.encodeToByteArray()
        .joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun unhex(value: String): String? {
        if (value.length % 2 != 0 || value.any { it !in "0123456789abcdefABCDEF" }) return null
        return runCatching {
            value.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
                .decodeToString()
        }.getOrNull()
    }
}

private const val MAX_ACCOUNT_ID_LENGTH = 128

expect fun createSecureSessionStore(): SecureSessionStore
