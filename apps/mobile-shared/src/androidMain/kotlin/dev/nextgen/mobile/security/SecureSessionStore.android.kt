package dev.nextgen.mobile.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_NAME = "AndroidKeyStore"
private const val KEY_ALIAS = "evidrilo.session.key.v1"
private const val PREFERENCES_NAME = "evidrilo_secure_session_v1"
private const val SESSION_KEY = "encrypted_session"
private const val CIPHERTEXT_SEPARATOR = ":"

object AndroidSecureSessionStorage {
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun createStore(): SecureSessionStore =
        applicationContext?.let(::AndroidSecureSessionStore) ?: NoopSecureSessionStore()
}

internal actual fun createSecureSessionStore(): SecureSessionStore = AndroidSecureSessionStorage.createStore()

private class AndroidSecureSessionStore(
    context: Context,
) : SecureSessionStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): StoredAccountSession? {
        val stored = preferences.getString(SESSION_KEY, null) ?: return null
        return decrypt(stored)?.let(SecureSessionRecordCodec::decode)
            ?: throw IllegalStateException("Secure session payload is invalid.")
    }

    override fun write(session: StoredAccountSession) {
        val encoded = SecureSessionRecordCodec.encode(session)
        val encrypted = encrypt(encoded)
        check(preferences.edit().putString(SESSION_KEY, encrypted).commit()) {
            "Secure session could not be persisted."
        }
    }

    override fun clear() {
        check(preferences.edit().remove(SESSION_KEY).commit()) {
            "Secure session could not be cleared."
        }
    }

    private fun encrypt(value: String): String {
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        return listOf(encode(iv), encode(ciphertext)).joinToString(CIPHERTEXT_SEPARATOR)
    }

    private fun decrypt(value: String): String? {
        val parts = value.split(CIPHERTEXT_SEPARATOR)
        if (parts.size != 2) return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
            cipher.doFinal(decode(parts[1])).decodeToString()
        }.getOrNull()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_NAME).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(value: ByteArray): String =
        Base64.encodeToString(value, Base64.NO_WRAP or Base64.URL_SAFE)

    private fun decode(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)
}
