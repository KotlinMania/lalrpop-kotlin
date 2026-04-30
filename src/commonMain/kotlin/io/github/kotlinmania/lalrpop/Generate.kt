// port-lint: source generate.rs
/** Generate valid parse trees. */
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol

sealed class ParseTree {
    data class Nonterminal(val nt: NonterminalString, val trees: MutableList<ParseTree>) : ParseTree() {
        override fun toString(): String = "[${nt}: ${Sep(", ", trees)}]"
    }

    data class Terminal(val t: TerminalString) : ParseTree() {
        override fun toString(): String = "$t"
    }

    fun terminals(): MutableList<TerminalString> {
        val vec: MutableList<TerminalString> = mutableListOf()
        pushTerminals(vec)
        return vec
    }

    private fun pushTerminals(vec: MutableList<TerminalString>) {
        when (this) {
            is Terminal -> vec.add(t)
            is Nonterminal -> {
                for (tree in trees) tree.pushTerminals(vec)
            }
        }
    }
}

/**
 * Minimal port of `rand::rngs::ChaCha20Rng` — only the `randomRange`
 * usage in this file is required. Callers provide any deterministic
 * RNG satisfying this interface.
 */
interface ChaCha20Rng {
    fun randomRange(rangeEnd: Int): Int
}

fun randomParseTree(
    grammar: Grammar,
    symbol: NonterminalString,
    rng: ChaCha20Rng,
): ParseTree {
    val generator = Generator(
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
        val index: Int = rng.randomRange(productions.size)
        val production = productions[index]
        val trees: MutableList<ParseTree> = mutableListOf()
        for (sym in production.symbols) {
            val t = symbol(sym) ?: return null
            trees.add(t)
        }
        return ParseTree.Nonterminal(nt, trees)
    }

    fun symbol(symbol: Symbol): ParseTree? = when (symbol) {
        is Symbol.Nonterminal -> nonterminal(symbol.nt)
        is Symbol.Terminal -> ParseTree.Terminal(symbol.term)
    }
}

private const val MAX_DEPTH: Int = 7000
