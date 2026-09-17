package dev.nextgen.mobile.account

const val MAX_AUTH_REDIRECT_URL_BYTES: Int = 8 * 1024

sealed interface AuthRedirect {
    data class Code(
        val code: String,
        val state: String,
    ) : AuthRedirect

    class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: String,
        val state: String?,
        val type: String?,
    ) : AuthRedirect {
        override fun toString(): String = "AuthRedirect.Tokens(redacted)"
    }

    data object Invalid : AuthRedirect
}

fun parseAuthRedirect(
    rawUrl: String,
    expectedRedirectUrl: String,
): AuthRedirect {
    if (!isBoundedAuthRedirectUrl(rawUrl) || !isBoundedAuthRedirectUrl(expectedRedirectUrl)) {
        return AuthRedirect.Invalid
    }
    val expectedBase = expectedRedirectUrl.substringBeforeAny("?", "#")
    val rawBase = rawUrl.substringBeforeAny("?", "#")
    if (!sameRedirectBase(rawBase, expectedBase)) return AuthRedirect.Invalid

    val query = rawUrl.substringAfter('?', "").substringBefore('#')
    val fragment = rawUrl.substringAfter('#', "")
    val queryParameters = parseParameters(query) ?: return AuthRedirect.Invalid
    val fragmentParameters = parseParameters(fragment) ?: return AuthRedirect.Invalid

    if (queryParameters["error"] != null || fragmentParameters["error"] != null) {
        return AuthRedirect.Invalid
    }

    val code = queryParameters["code"]
    val codeState = queryParameters["state"]
    if (code != null && !codeState.isNullOrBlank() && code.isNotBlank()) {
        return AuthRedirect.Code(code, codeState)
    }

    val accessToken = fragmentParameters["access_token"]
    val refreshToken = fragmentParameters["refresh_token"]
    val expiresIn = fragmentParameters["expires_in"]
    if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank() && !expiresIn.isNullOrBlank()) {
        return AuthRedirect.Tokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            state = fragmentParameters["state"],
            type = fragmentParameters["type"],
        )
    }
    return AuthRedirect.Invalid
}

fun isBoundedAuthRedirectUrl(value: String): Boolean =
    value.length <= MAX_AUTH_REDIRECT_URL_BYTES &&
        value.encodeToByteArray().size <= MAX_AUTH_REDIRECT_URL_BYTES

fun encodeAuthUrlComponent(value: String): String {
    val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    return buildString {
        value.encodeToByteArray().forEach { byte ->
            val number = byte.toInt() and 0xff
            val character = number.toChar()
            if (number < 128 && character in unreserved) {
                append(character)
            } else {
                append('%')
                append(number.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}

private fun sameRedirectBase(actual: String, expected: String): Boolean {
    if (actual.length != expected.length) return false
    return actual.zip(expected).all { (actualChar, expectedChar) ->
        actualChar == expectedChar ||
            (actualChar in 'A'..'Z' && actualChar.lowercaseChar() == expectedChar) ||
            (expectedChar in 'A'..'Z' && expectedChar.lowercaseChar() == actualChar)
    }
}

private fun parseParameters(value: String): Map<String, String>? {
    if (value.isBlank()) return emptyMap()
    val result = linkedMapOf<String, String>()
    value.split('&').forEach { pair ->
        if (pair.isBlank()) return@forEach
        val keyPart = pair.substringBefore('=', pair)
        val valuePart = pair.substringAfter('=', "")
        val key = decodeAuthUrlComponent(keyPart) ?: return null
        val decodedValue = decodeAuthUrlComponent(valuePart) ?: return null
        if (key.isBlank() || result.containsKey(key)) return null
        result[key] = decodedValue
    }
    return result
}

private fun decodeAuthUrlComponent(value: String): String? {
    val output = StringBuilder()
    var index = 0
    while (index < value.length) {
        when {
            value[index] == '+' -> {
                output.append(' ')
                index++
            }

            value[index] == '%' -> {
                val bytes = mutableListOf<Byte>()
                while (index < value.length && value[index] == '%') {
                    if (index + 2 >= value.length) return null
                    val high = value[index + 1].digitToIntOrNull(16) ?: return null
                    val low = value[index + 2].digitToIntOrNull(16) ?: return null
                    bytes += ((high shl 4) or low).toByte()
                    index += 3
                }
                output.append(
                    runCatching { bytes.toByteArray().decodeToString(throwOnInvalidSequence = true) }
                        .getOrElse { return null },
                )
            }

            else -> {
                output.append(value[index])
                index++
            }
        }
    }
    return output.toString()
}

private fun String.substringBeforeAny(vararg delimiters: String): String {
    val indexes = delimiters.mapNotNull { delimiter -> indexOf(delimiter).takeIf { it >= 0 } }
    val index = indexes.minOrNull() ?: length
    return substring(0, index)
}
