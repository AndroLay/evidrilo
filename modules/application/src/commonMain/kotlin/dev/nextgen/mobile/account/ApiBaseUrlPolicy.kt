package dev.nextgen.mobile.account

private val localHttpHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

/**
 * Accepts HTTPS service URLs and the three explicit local-development hosts
 * used by a JVM process, Android emulator, and host loopback respectively.
 * Remote cleartext URLs are never accepted by the mobile boundary.
 */
fun isAllowedApiBaseUrl(value: String): Boolean {
    val normalized = value.trim().trimEnd('/')
    val scheme = normalized.substringBefore("://", "").lowercase()
    if (scheme != "https" && scheme != "http") return false

    val authority = normalized.substringAfter("://", "").substringBeforeAny('/', '?', '#')
    if (authority.isBlank() || authority.contains('@') || authority.any(Char::isWhitespace)) return false

    val host = authority.substringBefore(':').lowercase()
    return when (scheme) {
        "https" -> host == "localhost" || host.contains('.')
        "http" -> host in localHttpHosts
        else -> false
    }
}

private fun String.substringBeforeAny(vararg delimiters: Char): String {
    val index = indexOfFirst { it in delimiters }
    return if (index == -1) this else substring(0, index)
}
