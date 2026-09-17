package dev.nextgen.mobile.account

internal data class PkcePair(
    val verifier: String,
    val challenge: String,
    val state: String,
)

internal expect fun secureRandomBytes(size: Int): ByteArray

internal fun createPkcePair(): PkcePair {
    val verifier = base64Url(secureRandomBytes(32))
    val state = base64Url(secureRandomBytes(24))
    return PkcePair(
        verifier = verifier,
        challenge = pkceChallengeFor(verifier),
        state = state,
    )
}

internal fun pkceChallengeFor(verifier: String): String =
    base64Url(sha256(verifier.encodeToByteArray()))

private fun base64Url(bytes: ByteArray): String {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    val output = StringBuilder((bytes.size * 4 + 2) / 3)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xff
        val second = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else 0
        val third = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else 0
        output.append(alphabet[first ushr 2])
        output.append(alphabet[((first and 0x03) shl 4) or (second ushr 4)])
        if (index + 1 < bytes.size) output.append(alphabet[((second and 0x0f) shl 2) or (third ushr 6)])
        if (index + 2 < bytes.size) output.append(alphabet[third and 0x3f])
        index += 3
    }
    return output.toString()
}

internal fun sha256(input: ByteArray): ByteArray {
    val messageLength = input.size.toLong()
    val paddedLength = (((input.size + 9) + 63) / 64) * 64
    val message = ByteArray(paddedLength)
    input.copyInto(message)
    message[input.size] = 0x80.toByte()
    for (shift in 0 until 8) {
        message[paddedLength - 1 - shift] = ((messageLength * 8) ushr (shift * 8)).toByte()
    }

    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85.toInt()
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a.toInt()
    var h4 = 0x510e527f
    var h5 = 0x9b05688c.toInt()
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19
    val roundConstants = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
        0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
        0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
        0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
        0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
        0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
        0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
        0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
        0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
        0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )
    val words = IntArray(64)
    for (offset in message.indices step 64) {
        for (index in 0 until 16) {
            val start = offset + index * 4
            words[index] = (message[start].toInt() and 0xff shl 24) or
                (message[start + 1].toInt() and 0xff shl 16) or
                (message[start + 2].toInt() and 0xff shl 8) or
                (message[start + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val value = words[index - 2]
            val smallSigma1 = value.rotateRight(17) xor value.rotateRight(19) xor (value ushr 10)
            val previous = words[index - 15]
            val smallSigma0 = previous.rotateRight(7) xor previous.rotateRight(18) xor (previous ushr 3)
            words[index] = words[index - 16] + smallSigma0 + words[index - 7] + smallSigma1
        }
        var a = h0
        var b = h1
        var c = h2
        var d = h3
        var e = h4
        var f = h5
        var g = h6
        var h = h7
        for (index in 0 until 64) {
            val bigSigma1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choose = (e and f) xor (e.inv() and g)
            val temporary1 = h + bigSigma1 + choose + roundConstants[index] + words[index]
            val bigSigma0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temporary2 = bigSigma0 + majority
            h = g
            g = f
            f = e
            e = d + temporary1
            d = c
            c = b
            b = a
            a = temporary1 + temporary2
        }
        h0 += a
        h1 += b
        h2 += c
        h3 += d
        h4 += e
        h5 += f
        h6 += g
        h7 += h
    }
    return byteArrayOf(
        (h0 shr 24).toByte(), (h0 shr 16).toByte(), (h0 shr 8).toByte(), h0.toByte(),
        (h1 shr 24).toByte(), (h1 shr 16).toByte(), (h1 shr 8).toByte(), h1.toByte(),
        (h2 shr 24).toByte(), (h2 shr 16).toByte(), (h2 shr 8).toByte(), h2.toByte(),
        (h3 shr 24).toByte(), (h3 shr 16).toByte(), (h3 shr 8).toByte(), h3.toByte(),
        (h4 shr 24).toByte(), (h4 shr 16).toByte(), (h4 shr 8).toByte(), h4.toByte(),
        (h5 shr 24).toByte(), (h5 shr 16).toByte(), (h5 shr 8).toByte(), h5.toByte(),
        (h6 shr 24).toByte(), (h6 shr 16).toByte(), (h6 shr 8).toByte(), h6.toByte(),
        (h7 shr 24).toByte(), (h7 shr 16).toByte(), (h7 shr 8).toByte(), h7.toByte(),
    )
}

private fun Int.rotateRight(distance: Int): Int = (this ushr distance) or (this shl (32 - distance))
