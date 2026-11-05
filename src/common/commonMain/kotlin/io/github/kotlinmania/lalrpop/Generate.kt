// port-lint: source generate.rs
/** Generate valid parse trees. */
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.rand.CryptoRng
import io.github.kotlinmania.rand.Rng
import io.github.kotlinmania.rand.RngCore
import io.github.kotlinmania.rand.SeedableRng
import io.github.kotlinmania.rand.rngs.StdRng

internal sealed class ParseTree {
    data class Nonterminal(
        val nt: NonterminalString,
        val trees: MutableList<ParseTree>,
    ) : ParseTree() {
        override fun toString(): String = "[$nt: ${Sep(", ", trees)}]"
    }

    data class Terminal(
        val t: TerminalString,
    ) : ParseTree() {
        override fun toString(): String = "$t"
    }

    fun terminals(): MutableList<TerminalString> {
        val vec: MutableList<TerminalString> = mutableListOf()
        pushTerminals(vec)
        return vec
    }

    private fun pushTerminals(vec: MutableList<TerminalString>) {
        when (this) {
            is Terminal -> {
                vec.add(t)
            }

            is Nonterminal -> {
                for (tree in trees) tree.pushTerminals(vec)
            }
        }
    }
}

/**
 * Cryptographic 20-round ChaCha20 pseudo-random number generator (RFC 7539
 * 10 double-rounds over a 256-bit key and 64-bit counter).
 */
internal class ChaCha20Rng private constructor(
    private val key: UIntArray,
) : CryptoRng,
    SeedableRng,
    Rng {
    private var counter: ULong = 0uL
    private val buffer = ULongArray(8)
    private var bufferIndex: Int = 8

    init {
        require(key.size == 8) { "key must contain 8 32-bit words" }
    }

    override fun seedFromU64(seed: ULong) {
        val reseeded = Companion.seedFromU64(seed)
        for (i in 0 until 8) {
            key[i] = reseeded.key[i]
        }
        counter = 0uL
        bufferIndex = 8
    }

    override fun nextU32(): UInt {
        val v = nextU64()
        return (v shr 32).toUInt()
    }

    override fun nextU64(): ULong {
        if (bufferIndex >= 8) {
            refill()
        }
        return buffer[bufferIndex++]
    }

    override fun fillBytes(dest: ByteArray) {
        var offset = 0
        while (offset < dest.size) {
            val chunk = nextU64()
            for (b in 0 until 8) {
                if (offset + b < dest.size) {
                    dest[offset + b] = ((chunk shr (b * 8)) and 0xffuL).toByte()
                }
            }
            offset += 8
        }
    }

    private fun refill() {
        val state = UIntArray(16)
        state[0] = 0x61707865u
        state[1] = 0x3320646eu
        state[2] = 0x79622d32u
        state[3] = 0x6b206574u
        for (i in 0 until 8) {
            state[4 + i] = key[i]
        }
        state[12] = counter.toUInt()
        state[13] = (counter shr 32).toUInt()
        state[14] = 0u
        state[15] = 0u
        counter++

        val working = state.copyOf()
        repeat(10) {
            quarterRound(working, 0, 4, 8, 12)
            quarterRound(working, 1, 5, 9, 13)
            quarterRound(working, 2, 6, 10, 14)
            quarterRound(working, 3, 7, 11, 15)

            quarterRound(working, 0, 5, 10, 15)
            quarterRound(working, 1, 6, 11, 12)
            quarterRound(working, 2, 7, 8, 13)
            quarterRound(working, 3, 4, 9, 14)
        }

        for (i in 0 until 16) {
            working[i] = working[i] + state[i]
        }
        for (i in 0 until 8) {
            val low = working[i * 2].toULong()
            val high = working[i * 2 + 1].toULong()
            buffer[i] = low or (high shl 32)
        }
        bufferIndex = 0
    }

    private fun quarterRound(st: UIntArray, a: Int, b: Int, c: Int, d: Int) {
        st[a] = st[a] + st[b]
        st[d] = (st[d] xor st[a]).rotateLeft(16)
        st[c] = st[c] + st[d]
        st[b] = (st[b] xor st[c]).rotateLeft(12)
        st[a] = st[a] + st[b]
        st[d] = (st[d] xor st[a]).rotateLeft(8)
        st[c] = st[c] + st[d]
        st[b] = (st[b] xor st[c]).rotateLeft(7)
    }

    companion object {
        fun fromSeed(seed: ByteArray): ChaCha20Rng {
            require(seed.size == 32) { "seed must be 32 bytes" }
            val key = UIntArray(8)
            for (i in 0 until 8) {
                val off = i * 4
                key[i] = (seed[off].toUByte().toUInt()) or
                    ((seed[off + 1].toUByte().toUInt()) shl 8) or
                    ((seed[off + 2].toUByte().toUInt()) shl 16) or
                    ((seed[off + 3].toUByte().toUInt()) shl 24)
            }
            return ChaCha20Rng(key)
        }

        fun seedFromU64(seed: ULong): ChaCha20Rng {
            val rng = StdRng.seedFromU64(seed)
            val seedBytes = ByteArray(32)
            rng.fillBytes(seedBytes)
            return fromSeed(seedBytes)
        }

        fun fromRng(rng: RngCore): ChaCha20Rng {
            val seedBytes = ByteArray(32)
            rng.fillBytes(seedBytes)
            return fromSeed(seedBytes)
        }
    }
}

internal fun randomParseTree(
    grammar: Grammar,
    symbol: NonterminalString,
    rng: ChaCha20Rng,
): ParseTree {
    val generator =
        Generator(
            grammar = grammar,
            rng = rng,
            depth = 0,
        )
    while (true) {
        // sometimes, the random walk overflows the stack, so we have a max, and if
        // it is exceeded, we just try again
        val result = generator.nonterminal(symbol)
        if (result != null) {
            return result
        }
        generator.depth = 0
    }
}

private class Generator(
    val grammar: Grammar,
    val rng: ChaCha20Rng,
    var depth: Int,
) {
    fun nonterminal(nt: NonterminalString): ParseTree? {
        if (depth > MAX_DEPTH) {
            return null
        }

        depth += 1
        val productions = grammar.productionsFor(nt)
        val index: Int = rng.randomRange(0 until productions.size)
        val production = productions[index]
        val trees: MutableList<ParseTree> = mutableListOf()
        for (sym in production.symbols) {
            val t = symbol(sym) ?: return null
            trees.add(t)
        }
        return ParseTree.Nonterminal(nt, trees)
    }

    fun symbol(symbol: Symbol): ParseTree? =
        when (symbol) {
            is Symbol.Nonterminal -> nonterminal(symbol.nt)
            is Symbol.Terminal -> ParseTree.Terminal(symbol.term)
        }
}

private const val MAX_DEPTH: Int = 7000
