package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun sha256_challenge_uses_base64url_without_padding() {
        assertEquals(
            "ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0",
            pkceChallengeFor("abc"),
        )
    }

    @Test
    fun generated_pkce_material_is_nonempty_and_url_safe() {
        val pair = createPkcePair()

        assertTrue(pair.verifier.length >= 43)
        assertTrue(pair.state.length >= 24)
        assertTrue(pair.verifier.all { it.isLetterOrDigit() || it in "-._~" })
        assertTrue(pair.state.all { it.isLetterOrDigit() || it in "-._~" })
        assertEquals(pkceChallengeFor(pair.verifier), pair.challenge)
    }
}
