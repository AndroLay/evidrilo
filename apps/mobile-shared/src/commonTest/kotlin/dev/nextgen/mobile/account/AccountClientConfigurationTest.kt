package dev.nextgen.mobile.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountClientConfigurationTest {
    @Test
    fun valid_public_client_configuration_is_available_without_exposing_values() {
        val configuration = AccountClientConfiguration(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_synthetic",
            redirectUrl = "evidrilo://auth/callback",
            apiBaseUrl = "https://api.example.test",
        )

        assertTrue(configuration.isConfigured)
        assertTrue(configuration.apiConfigured)
        assertEquals("evidrilo://auth/callback", configuration.redirectUrl)
        assertFalse(configuration.toString().contains("sb_publishable_synthetic"))
        assertFalse(configuration.toString().contains("example.supabase.co"))
    }

    @Test
    fun secret_shaped_keys_and_non_https_urls_fail_closed() {
        val secretKey = AccountClientConfiguration(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_secret_synthetic",
        )
        val serviceRoleKey = AccountClientConfiguration(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "service_role_synthetic",
        )
        val insecureUrl = AccountClientConfiguration(
            supabaseUrl = "http://example.supabase.co",
            publishableKey = "sb_publishable_synthetic",
        )

        assertFalse(secretKey.isConfigured)
        assertFalse(serviceRoleKey.isConfigured)
        assertFalse(insecureUrl.isConfigured)
    }

    @Test
    fun redirect_url_is_fixed_to_the_registered_mobile_callback() {
        val configuration = AccountClientConfiguration(
            supabaseUrl = "https://example.supabase.co",
            publishableKey = "sb_publishable_synthetic",
            redirectUrl = "https://example.test/callback",
        )

        assertFalse(configuration.isConfigured)
    }
}
