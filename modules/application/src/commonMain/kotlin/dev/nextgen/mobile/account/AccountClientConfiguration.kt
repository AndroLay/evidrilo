package dev.nextgen.mobile.account

const val DEFAULT_ACCOUNT_AUTH_REDIRECT_URL = "evidrilo://auth/callback"

/**
 * Build-time client configuration. It deliberately contains only values that
 * are safe for a mobile binary; server secrets never belong here.
 */
data class AccountClientConfiguration(
    val supabaseUrl: String,
    val publishableKey: String,
    val redirectUrl: String = DEFAULT_ACCOUNT_AUTH_REDIRECT_URL,
    val apiBaseUrl: String = "",
) {
    val isConfigured: Boolean
        get() = isHttpsBaseUrl(supabaseUrl) && isPublishableKey(publishableKey)
            && redirectUrl == DEFAULT_ACCOUNT_AUTH_REDIRECT_URL

    val apiConfigured: Boolean
        get() = apiBaseUrl.isBlank() || isHttpsBaseUrl(apiBaseUrl)

    val normalizedSupabaseUrl: String
        get() = supabaseUrl.trim().trimEnd('/')

    val normalizedApiBaseUrl: String
        get() = apiBaseUrl.trim().trimEnd('/')

    override fun toString(): String =
        "AccountClientConfiguration(configured=$isConfigured, apiConfigured=$apiConfigured)"

    private fun isPublishableKey(value: String): Boolean {
        val normalized = value.trim().lowercase()
        if (normalized.isBlank()) return false
        if (normalized.contains("service_role") || normalized.contains("sb_secret")) return false
        if (normalized.contains("secret")) return false
        return normalized.length >= 12
    }

    private fun isHttpsBaseUrl(value: String): Boolean {
        val normalized = value.trim().trimEnd('/')
        if (!normalized.startsWith("https://", ignoreCase = true)) return false
        val authorityAndPath = normalized.substringAfter("//", "")
        val authority = authorityAndPath
            .substringBeforeAny('/', '?', '#')
        if (authority.isBlank() || authority.contains('@') || authority.any(Char::isWhitespace)) return false
        if (authorityAndPath.removePrefix(authority).isNotEmpty()) return false
        return authority.contains('.') || authority == "localhost"
    }

    private fun String.substringBeforeAny(vararg delimiters: Char): String {
        val index = indexOfFirst { it in delimiters }
        return if (index == -1) this else substring(0, index)
    }
}
