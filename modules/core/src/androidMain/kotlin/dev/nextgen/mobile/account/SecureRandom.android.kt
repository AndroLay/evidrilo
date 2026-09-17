package dev.nextgen.mobile.account

import java.security.SecureRandom

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size > 0)
    return ByteArray(size).also(SecureRandom()::nextBytes)
}
