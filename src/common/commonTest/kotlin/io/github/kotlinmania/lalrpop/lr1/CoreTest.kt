package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun nt(name: String): NonterminalString = NonterminalString(Atom.from(name))

private fun quotedTerm(name: String): TerminalString = TerminalString.quoted(Atom.from(name))

class CoreTest {
    @Test
    fun shiftedItemClonesMutableTokenSetLookahead() {
        val grammar =
            normalizedGrammar(
                """
grammar;
    extern { enum Tok { "A" => .., "B" => .. } }
    S = "A" "B";
""",
            )
        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            val prod = grammar.productionsFor(nt("S")).single()
            val tokA = Token.Terminal(quotedTerm("A"))
            val tokB = Token.Terminal(quotedTerm("B"))

            val initialLookahead = TokenSet.from(tokA)
            initialLookahead.insertEof()
            val origin = Item(production = prod, index = 0, lookahead = initialLookahead)

            val shiftedPair = assertNotNull(origin.shiftedItem())
            val shifted = shiftedPair.second
            assertEquals(1, shifted.index)
            assertEquals(origin.lookahead, shifted.lookahead)

            assertTrue(shifted.lookahead.takeEof())
            shifted.lookahead.insert(tokB)

            assertTrue(origin.lookahead.containsEof(), "mutating shifted item lookahead must not strip Eof from origin")
            assertFalse(origin.lookahead.contains(tokB), "mutating shifted item lookahead must not leak terminals into origin")
            assertTrue(origin.lookahead.contains(tokA))
        } finally {
            lr1Tls.drop()
        }
    }

    @Test
    fun itemsAndActionImplementDerivedLexicographicalOrdering() {
        val grammar =
            normalizedGrammar(
                """
grammar;
    extern { enum Tok { "a" => .., "b" => .. } }
    S = "a" "b";
""",
            )
        val prod = grammar.productionsFor(nt("S")).single()
        val item0 = Item.lr0(prod, 0)
        val item1 = Item.lr0(prod, 1)

        val emptyItems: Lr0Items = Items(mutableListOf())
        val firstItems: Lr0Items = Items(mutableListOf(item0))
        val prefixItems: Lr0Items = Items(mutableListOf(item0, item1))
        val laterItems: Lr0Items = Items(mutableListOf(item1))

        val sorted = listOf(laterItems, prefixItems, emptyItems, firstItems).sorted()
        assertEquals(listOf(emptyItems, firstItems, prefixItems, laterItems), sorted)

        val shiftA0 = Action.Shift(quotedTerm("a"), StateIndex(0))
        val shiftA1 = Action.Shift(quotedTerm("a"), StateIndex(1))
        val shiftB0 = Action.Shift(quotedTerm("b"), StateIndex(0))
        val reduceS = Action.Reduce(prod)
        assertEquals(
            listOf(shiftA0, shiftA1, shiftB0, reduceS),
            listOf(reduceS, shiftB0, shiftA1, shiftA0).sorted(),
        )
    }

    @Test
    fun stateStackPrefixPopPushAndProduceFollowUpstreamDiagramInvariants() {
        val grammar =
            normalizedGrammar(
                """
grammar;
    extern { enum Tok { "a" => .., "b" => .., "d" => .. } }
    X = "a" "b" C C;
    Y = "b" C C;
    C = "d";
""",
            )
        val prodX = grammar.productionsFor(nt("X")).single()
        val prodY = grammar.productionsFor(nt("Y")).single()
        val prodC = grammar.productionsFor(nt("C")).single()

        // Mirrors the upstream `State.willPop` stack diagram:
        //   X = "a" "b" C (*) C
        //   Y = "b" C (*) C
        //   C = (*) "d"
        val diagramState: Lr0State =
            State(
                index = StateIndex(3),
                items =
                    Items(
                        mutableListOf(
                            Item.lr0(prodX, 3),
                            Item.lr0(prodY, 2),
                            Item.lr0(prodC, 0),
                        ),
                    ),
            )
        val symA: Symbol = Symbol.Terminal(quotedTerm("a"))
        val symB: Symbol = Symbol.Terminal(quotedTerm("b"))
        val symC: Symbol = Symbol.Nonterminal(nt("C"))

        assertEquals(listOf(symA, symB, symC), diagramState.maxPrefix())
        assertEquals(listOf(symB, symC), diagramState.willPop())
        assertEquals(listOf(symC), diagramState.willPush())
        assertNull(diagramState.willProduce())

        val singleReturnState: Lr0State =
            State(
                index = StateIndex(4),
                items =
                    Items(
                        mutableListOf(
                            Item.lr0(prodX, 3),
                            Item.lr0(prodC, 0),
                        ),
                    ),
            )
        assertEquals(nt("X"), singleReturnState.willProduce())

        val startState: Lr0State =
            State(
                index = StateIndex(0),
                items = Items(mutableListOf(Item.lr0(prodX, 0))),
            )
        assertEquals(emptyList(), startState.maxPrefix())
        assertEquals(emptyList(), startState.willPop())
        assertEquals(emptyList(), startState.willPush())
        assertNull(startState.willProduce())
    }

    @Test
    fun itemStateIndexTokenFormattingAndSymbolSetsMatchUpstream() {
        val grammar =
            normalizedGrammar(
                """
grammar;
    extern { enum Tok { "b" => .., "d" => .. } }
    A = "b" C "d";
    C = "d";
""",
            )
        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            val prodA = grammar.productionsFor(nt("A")).single()
            val itemMid = Item(prodA, 1, TokenSet.eof())
            assertEquals("A = \"b\" (*) C \"d\" [Eof]", itemMid.toString())
            assertTrue(itemMid.canShift())
            assertTrue(itemMid.canShiftNonterminal(nt("C")))
            assertFalse(itemMid.canShiftNonterminal(nt("A")))
            assertFalse(itemMid.canReduce())

            val setsMid = itemMid.symbolSets()
            assertEquals(listOf<Symbol>(Symbol.Terminal(quotedTerm("b"))), setsMid.prefix)
            assertEquals(Symbol.Nonterminal(nt("C")), setsMid.cursor)
            assertEquals(listOf<Symbol>(Symbol.Terminal(quotedTerm("d"))), setsMid.suffix)

            val itemEnd = Item.lr0(prodA, 3)
            assertEquals("A = \"b\" C \"d\" (*)", itemEnd.toString())
            assertTrue(itemEnd.canReduce())
            assertNull(itemEnd.shiftedItem())
            assertNull(itemEnd.shiftSymbol())
            val setsEnd = itemEnd.symbolSets()
            assertNull(setsEnd.cursor)
            assertEquals(emptyList(), setsEnd.suffix)
            assertEquals(SymbolSets(emptyList(), null, emptyList()), SymbolSets.new())

            val idx = StateIndex(7)
            assertEquals("S7", idx.toString())
            assertEquals("7", idx.display())
            assertEquals("Eof", Token.Eof.toString())
            assertEquals("Error", Token.Error.toString())
            assertEquals("\"b\"", Token.Terminal(quotedTerm("b")).toString())
        } finally {
            lr1Tls.drop()
        }
    }

    @Test
    fun canonicalLrResultPreservesTypedTableConstructionError() {
        val ambiguousGrammar =
            normalizedGrammar(
                """
grammar;
    pub E: () = {
        "x",
        E "+" E,
    };
""",
            )
        Tls.install(Session.new(), FileText.new("", "")).use {
            val lr1Tls = Lr1Tls.install(ambiguousGrammar.terminals)
            try {
                val startNt = ambiguousGrammar.startNonterminals.values.first()
                val outcome = buildStatesOrError(ambiguousGrammar, startNt)
                assertTrue(outcome is BuildOutcome.Err)

                val lrResult: Lr1Result = LrResult.Err(outcome.error)
                val out = StringBuilder()
                generateReport(out, lrResult)
                val report = out.toString()
                assertTrue(report.contains("Failure"))
                assertTrue(report.contains("conflicts"))
            } finally {
                lr1Tls.drop()
            }
        }
    }
}
