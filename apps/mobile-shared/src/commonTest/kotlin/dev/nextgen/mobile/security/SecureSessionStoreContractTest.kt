package dev.nextgen.mobile.security

import dev.nextgen.mobile.account.AccountSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class SecureSessionStoreContractTest {
    @Test
    fun session_material_rejects_control_characters_before_http_header_use() {
        assertFails {
            SecureSessionMaterial("access\n-token", 200L)
        }
        assertFails {
            SecureSessionMaterial("access-token", 200L, refreshToken = "refresh\r-token")
        }
    }

    @Test
    fun memory_contract_round_trips_and_clears_without_plaintext_diagnostic_output() {
        val store = MemoryContractStore()
        val record = StoredAccountSession(
            account = AccountSummary("123e4567-e89b-42d3-a456-426614174000", emailVerified = true),
            material = SecureSessionMaterial("access-token", 200L),
        )

        store.write(record)

        assertEquals(record, store.read())
        val encoded = SecureSessionRecordCodec.encode(record)
        assertEquals(5, encoded.split('.').size)
        assertFalse("refresh-token" in encoded)
        assertNull(store.toString().takeIf { "access-token" in it })
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun refresh_token_round_trips_inside_the_existing_secure_record_boundary() {
        val record = StoredAccountSession(
            account = AccountSummary("123e4567-e89b-42d3-a456-426614174000", emailVerified = true),
            material = SecureSessionMaterial("access-token", 200L, refreshToken = "refresh-token"),
        )

        val encoded = SecureSessionRecordCodec.encode(record)
        val restored = SecureSessionRecordCodec.decode(encoded)

        assertEquals(5, encoded.split('.').size)
        assertEquals(record, restored)
        assertNotNull(restored).material.refreshToken
        assertFalse("refresh-token" in restored.toString())
    }

    @Test
    fun legacy_four_field_record_remains_readable_without_a_refresh_token() {
        val oldRecord = listOf("account-123", "true", "200", "access-token")
            .joinToString(".") { it.encodeToByteArray().joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            } }

        val restored = SecureSessionRecordCodec.decode(oldRecord)

        assertNotNull(restored)
        assertEquals("account-123", restored.account.accountId)
        assertEquals("access-token", restored.material.accessToken)
        assertNull(restored.material.refreshToken)
    }

    @Test
    fun secure_record_decoder_rejects_unsafe_account_ids_before_boundary_use() {
        val record = StoredAccountSession(
            account = AccountSummary("account\nidentifier", emailVerified = true),
            material = SecureSessionMaterial("access-token", 200L),
        )

        val restored = runCatching {
            SecureSessionRecordCodec.decode(SecureSessionRecordCodec.encode(record))
        }.getOrNull()

        assertNull(restored)
    }
}

private class MemoryContractStore : SecureSessionStore {
    private var value: StoredAccountSession? = null

    override fun read(): StoredAccountSession? = value

    override fun write(session: StoredAccountSession) {
        value = session
    }

    override fun clear() {
        value = null
    }

    override fun toString(): String = "MemoryContractStore(redacted)"
}
