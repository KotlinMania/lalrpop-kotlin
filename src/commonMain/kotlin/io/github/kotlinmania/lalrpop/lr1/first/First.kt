// port-lint: source lr1/first/mod.rs
/** First set construction and computation. */
package io.github.kotlinmania.lalrpop.lr1.first

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.lr1.Token
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls

data class FirstSets(
    val map: Map<NonterminalString, TokenSet>,
) {
    companion object {
        fun new(grammar: Grammar): FirstSets {
            val this_ = FirstSets(map = map())
            var changed = true
            while (changed) {
                changed = false
                for (production in grammar.nonterminals.values.flatMap { it.productions }) {
                    val nt = production.nonterminal
                    val lookahead = this_.first0(production.symbols)
                    val firstSet = this_.map.getOrPut(nt) { TokenSet.new() }
                    changed = firstSet.unionWith(lookahead) || changed
                }
            }
            return this_
        }
    }

    /**
     * Returns `FIRST(...symbols)`. If `...symbols` may derive
     * epsilon, then this returned set will include EOF. (This is
     * kind of repurposing EOF to serve as a binary flag of sorts.)
     */
    fun first0(symbols: Iterable<Symbol>): TokenSet {
        val result = TokenSet.new()

        for (symbol in symbols) {
            when (symbol) {
                is Symbol.Terminal -> {
                    result.insert(Token.Terminal(symbol.term))
                    return result
                }

                is Symbol.Nonterminal -> {
                    var emptyProd = false
                    val set = map[symbol.nt]
                    if (set == null) {
                        // This should only happen during set
                        // construction; it corresponds to an
                        // entry that has not yet been
                        // built. Otherwise, it would mean a
                        // terminal with no productions. Either
                        // way, the resulting first set should be
                        // empty.
                    } else {
                        result.reserve(set.len())
                        Lr1Tls.with { terminals ->
                            for (lookahead in set.iter()) {
                                when (lookahead) {
                                    Token.Eof -> {
                                        emptyProd = true
                                    }
                                    Token.Error, is Token.Terminal -> {
                                        result.insertWith(lookahead, terminals)
                                    }
                                }
                            }
                        }
                    }
                    if (!emptyProd) {
                        return result
                    }
                }
            }
        }

        // control only reaches here if either symbols is empty, or it
        // consists of nonterminals all of which may derive epsilon
        result.insert(Token.Eof)
        return result
    }

    fun first1(symbols: Iterable<Symbol>, lookahead: TokenSet): TokenSet {
        val set = first0(symbols)

        // we import EOF as the signal that `symbols` derives epsilon:
        val epsilon = set.takeEof()

        if (epsilon) {
            set.unionWith(lookahead)
        }

        return set
    }
}
