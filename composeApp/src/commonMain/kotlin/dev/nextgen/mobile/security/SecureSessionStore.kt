package dev.nextgen.mobile.security

import dev.nextgen.mobile.account.AccountSummary

internal class SecureSessionMaterial(
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

internal data class StoredAccountSession(
    val account: AccountSummary,
    val material: SecureSessionMaterial,
) {
    override fun toString(): String = "StoredAccountSession(account=$account, material=redacted)"
}

internal interface SecureSessionStore {
    fun read(): StoredAccountSession?

    fun write(session: StoredAccountSession)

    fun clear()
}

internal class NoopSecureSessionStore : SecureSessionStore {
    override fun read(): StoredAccountSession? = null

    override fun write(session: StoredAccountSession) = Unit

    override fun clear() = Unit
}

internal object SecureSessionRecordCodec {
    fun encode(session: StoredAccountSession): String = listOf(
        session.account.accountId,
        session.account.emailVerified.toString(),
        session.material.expiresAtEpochSeconds.toString(),
        session.material.accessToken,
        session.material.refreshToken.orEmpty(),
    ).joinToString(".") { hex(it) }

    fun decode(value: String): StoredAccountSession? = runCatching {
        val fields = value.split('.')
        if (fields.size != 4 && fields.size != 5) return@runCatching null

        val decoded = fields.map { unhex(it) ?: return@runCatching null }
        val accountId = decoded[0].takeIf(::isSafeAccountId) ?: return@runCatching null
        val verified = decoded[1].toBooleanStrictOrNull() ?: return@runCatching null
        val expiry = decoded[2].toLongOrNull() ?: return@runCatching null
        val accessToken = decoded[3].takeIf { it.isNotBlank() } ?: return@runCatching null
        val refreshToken = if (fields.size == 5) decoded[4].takeIf { it.isNotBlank() } else null
        StoredAccountSession(
            account = AccountSummary(accountId, verified),
            material = SecureSessionMaterial(accessToken, expiry, refreshToken),
        )
    }.getOrNull()

    private fun isSafeAccountId(value: String): Boolean =
        value.length in 1..MAX_ACCOUNT_ID_LENGTH &&
            value.all {
                it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_'
            }

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

internal expect fun createSecureSessionStore(): SecureSessionStore
