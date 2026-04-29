// port-lint: source lr1/first/test.rs
package io.github.kotlinmania.lalrpop.lr1.first

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.lr1.lookahead.Token
import io.github.kotlinmania.lalrpop.lr1.lookahead.TokenSet
import io.github.kotlinmania.lalrpop.lr1.tls.Lr1Tls
import io.github.kotlinmania.lalrpop.normalizedGrammar
import kotlin.test.Test
import kotlin.test.assertEquals

private fun nt(t: String): Symbol =
    Symbol.Nonterminal(NonterminalString(Atom.from(t)))

private fun term(t: String): Symbol =
    Symbol.Terminal(TerminalString.quoted(Atom.from(t)))

private fun la(t: String): Token =
    Token.Terminal(TerminalString.quoted(Atom.from(t)))

private fun first0(first: FirstSets, symbols: List<Symbol>): List<Token> {
    val v = first.first0(symbols)
    return v.iter().asSequence().toList()
}

private fun first1(first: FirstSets, symbols: List<Symbol>, lookahead: Token): List<Token> {
    val v = first.first1(symbols, TokenSet.from(lookahead))
    return v.iter().asSequence().toList()
}

class FirstTest {
    @Test
    fun basicFirst1() {
        val grammar = normalizedGrammar(
            """
    grammar;
    A = B "C";
    B: Option<u32> = {
        "D" => Some(1),
        => None
    };
    X = "E"; // intentionally unreachable
""",
        )
        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            val firstSets = FirstSets.new(grammar)

            assertEquals(
                listOf(la("C"), la("D")),
                first1(firstSets, listOf(nt("A")), Token.Eof),
            )

            assertEquals(
                listOf(la("D"), Token.Eof),
                first1(firstSets, listOf(nt("B")), Token.Eof),
            )

            assertEquals(
                listOf(la("D"), la("E")),
                first1(firstSets, listOf(nt("B"), term("E")), Token.Eof),
            )

            assertEquals(
                listOf(la("D"), la("E")),
                first1(firstSets, listOf(nt("B"), nt("X")), Token.Eof),
            )
        } finally {
            lr1Tls.drop()
        }
    }

    @Test
    fun basicFirst0() {
        val grammar = normalizedGrammar(
            """
    grammar;
    A = B "C";
    B: Option<u32> = {
        "D" => Some(1),
        => None
    };
    X = "E"; // intentionally unreachable
""",
        )
        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            val firstSets = FirstSets.new(grammar)

            assertEquals(listOf(la("C"), la("D")), first0(firstSets, listOf(nt("A"))))

            assertEquals(listOf(la("D"), Token.Eof), first0(firstSets, listOf(nt("B"))))

            assertEquals(
                listOf(la("D"), la("E")),
                first0(firstSets, listOf(nt("B"), term("E"))),
            )

            assertEquals(
                listOf(la("D"), la("E")),
                first0(firstSets, listOf(nt("B"), nt("X"))),
            )

            assertEquals(listOf(la("E")), first0(firstSets, listOf(nt("X"))))
        } finally {
            lr1Tls.drop()
        }
    }
}
