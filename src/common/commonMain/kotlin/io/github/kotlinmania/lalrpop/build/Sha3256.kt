package io.github.kotlinmania.lalrpop.build

/*
 * Pure-Kotlin SHA3-256 implementation based on the FIPS 202 specification of
 * Keccak-f[1600] with a 256-bit output (rate = 1088 bits = 136 bytes,
 * capacity = 512 bits). Shared across all KMP targets so JVM, Android API 24+,
 * Kotlin/Native, and WasmWasi compute identical `// sha3:` file headers
 * without platform-specific crypto libraries.
 */
internal class Sha3256 {
    private val state = LongArray(25)

    fun digestHex(input: ByteArray): String =
        digest(input).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    fun digest(input: ByteArray): ByteArray {
        val rate = 136
        var offset = 0
        while (offset + rate <= input.size) {
            absorbBlock(input, offset, rate)
            keccakF()
            offset += rate
        }
        val tail = ByteArray(rate)
        val remaining = input.size - offset
        for (i in 0 until remaining) {
            tail[i] = input[offset + i]
        }
        tail[remaining] = 0x06
        tail[rate - 1] = (tail[rate - 1].toInt() or 0x80).toByte()
        absorbBlock(tail, 0, rate)
        keccakF()

        val out = ByteArray(32)
        for (i in 0 until 32) {
            val laneIndex = i / 8
            val byteIndex = i % 8
            out[i] = ((state[laneIndex] ushr (8 * byteIndex)) and 0xff).toByte()
        }
        return out
    }

    private fun absorbBlock(block: ByteArray, offset: Int, length: Int) {
        var i = 0
        while (i < length) {
            var lane = 0L
            for (j in 0 until 8) {
                if (i + j < length) {
                    lane = lane or ((block[offset + i + j].toLong() and 0xff) shl (8 * j))
                }
            }
            state[i / 8] = state[i / 8] xor lane
            i += 8
        }
    }

    private fun keccakF() {
        for (round in 0 until 24) {
            val c = LongArray(5)
            for (x in 0 until 5) {
                c[x] = state[x] xor state[x + 5] xor state[x + 10] xor state[x + 15] xor state[x + 20]
            }
            val d = LongArray(5)
            for (x in 0 until 5) {
                d[x] = c[(x + 4) % 5] xor c[(x + 1) % 5].rotl(1)
            }
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    state[x + 5 * y] = state[x + 5 * y] xor d[x]
                }
            }
            val b = LongArray(25)
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    val nx = y
                    val ny = (2 * x + 3 * y) % 5
                    b[nx + 5 * ny] = state[x + 5 * y].rotl(R[x + 5 * y])
                }
            }
            for (x in 0 until 5) {
                for (y in 0 until 5) {
                    state[x + 5 * y] = b[x + 5 * y] xor (b[((x + 1) % 5) + 5 * y].inv() and b[((x + 2) % 5) + 5 * y])
                }
            }
            state[0] = state[0] xor RC[round]
        }
    }

    private fun Long.rotl(n: Int): Long {
        val nn = n % 64
        return if (nn == 0) this else (this shl nn) or (this ushr (64 - nn))
    }

    companion object {
        private val RC =
            longArrayOf(
                0x0000000000000001uL.toLong(),
                0x0000000000008082uL.toLong(),
                0x800000000000808auL.toLong(),
                0x8000000080008000uL.toLong(),
                0x000000000000808buL.toLong(),
                0x0000000080000001uL.toLong(),
                0x8000000080008081uL.toLong(),
                0x8000000000008009uL.toLong(),
                0x000000000000008auL.toLong(),
                0x0000000000000088uL.toLong(),
                0x0000000080008009uL.toLong(),
                0x000000008000000auL.toLong(),
                0x000000008000808buL.toLong(),
                0x800000000000008buL.toLong(),
                0x8000000000008089uL.toLong(),
                0x8000000000008003uL.toLong(),
                0x8000000000008002uL.toLong(),
                0x8000000000000080uL.toLong(),
                0x000000000000800auL.toLong(),
                0x800000008000000auL.toLong(),
                0x8000000080008081uL.toLong(),
                0x8000000000008080uL.toLong(),
                0x0000000080000001uL.toLong(),
                0x8000000080008008uL.toLong(),
            )

        private val R =
            intArrayOf(
                0, 1, 62, 28, 27,
                36, 44, 6, 55, 20,
                3, 10, 43, 25, 39,
                41, 45, 15, 21, 8,
                18, 2, 61, 56, 14,
            )
    }
}
