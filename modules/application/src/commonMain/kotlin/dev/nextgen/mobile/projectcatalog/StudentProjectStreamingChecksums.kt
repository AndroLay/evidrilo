package dev.nextgen.mobile.projectcatalog

/** Incremental SHA-256 used while attachment bytes are copied through bounded buffers. */
internal class StudentProjectSha256Accumulator {
    private val state = intArrayOf(
        0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
        0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
    )
    private val block = ByteArray(64)
    private var blockSize = 0
    private var byteCount = 0L
    private var finished = false

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        require(!finished && offset >= 0 && length >= 0 && offset + length <= bytes.size)
        byteCount += length
        var cursor = offset
        var remaining = length
        while (remaining > 0) {
            val copied = minOf(64 - blockSize, remaining)
            bytes.copyInto(block, blockSize, cursor, cursor + copied)
            blockSize += copied
            cursor += copied
            remaining -= copied
            if (blockSize == 64) {
                processBlock(block)
                blockSize = 0
            }
        }
    }

    fun digest(): ByteArray {
        check(!finished)
        finished = true
        val bitLength = byteCount * 8
        block[blockSize++] = 0x80.toByte()
        if (blockSize > 56) {
            block.fill(0.toByte(), blockSize, 64)
            processBlock(block)
            blockSize = 0
        }
        block.fill(0.toByte(), blockSize, 56)
        for (index in 0 until 8) {
            block[63 - index] = (bitLength ushr (index * 8)).toByte()
        }
        processBlock(block)
        return ByteArray(32) { index ->
            val word = state[index / 4]
            (word ushr (24 - (index % 4) * 8)).toByte()
        }
    }

    private fun processBlock(input: ByteArray) {
        val words = IntArray(64)
        for (index in 0 until 16) {
            val start = index * 4
            words[index] = ((input[start].toInt() and 0xff) shl 24) or
                ((input[start + 1].toInt() and 0xff) shl 16) or
                ((input[start + 2].toInt() and 0xff) shl 8) or
                (input[start + 3].toInt() and 0xff)
        }
        for (index in 16 until 64) {
            val value = words[index - 2]
            val smallSigma1 = value.rotateRight(17) xor value.rotateRight(19) xor (value ushr 10)
            val previous = words[index - 15]
            val smallSigma0 = previous.rotateRight(7) xor previous.rotateRight(18) xor (previous ushr 3)
            words[index] = words[index - 16] + smallSigma0 + words[index - 7] + smallSigma1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]
        for (index in 0 until 64) {
            val bigSigma1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choose = (e and f) xor (e.inv() and g)
            val temporary1 = h + bigSigma1 + choose + ROUND_CONSTANTS[index] + words[index]
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
        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }

    private fun Int.rotateRight(distance: Int): Int = (this ushr distance) or (this shl (32 - distance))

    private companion object {
        val ROUND_CONSTANTS = intArrayOf(
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
    }
}

internal class StudentProjectCrc32Accumulator {
    private var crc = 0xffff_ffffL

    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
        for (index in offset until offset + length) {
            crc = crc xor (bytes[index].toLong() and 0xff)
            repeat(8) {
                crc = if (crc and 1L != 0L) (crc ushr 1) xor 0xedb8_8320L else crc ushr 1
            }
        }
    }

    fun value(): Long = crc xor 0xffff_ffffL
}

internal fun ByteArray.toLowerHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
