package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.buildLalrStates
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.runtime.Production
import io.github.kotlinmania.lalrpop.runtime.ProductionAction
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

/**
 * Runs the real lalrpop-kotlin front end on a tiny grammar, then verifies that
 * [tablesFromLr1States] builds packed `ShortArray`s consistent with the LR(1) state
 * machine the front end produced.
 *
 * Per-grammar production count, and so the size of the productions array, comes from
 * the front end's `grammar.nonterminals.values.flatMap { it.productions }`. The
 * action lambdas are stubs in this test — we are validating the *table* builder, not
 * the action-body translator. End-to-end parses against real action lambdas land in
 * a follow-up test once the action emitter exists.
 */
class TablesBuilderTest {

    /**
     * Stub action lambda: throws with a clear message if the driver ever invokes it.
     * The table-builder test does not run the driver, so the lambda is only here to
     * fill the productions array with non-null entries.
     */
    private fun <S, L> stubAction(): ProductionAction<S, L> =
        ProductionAction { _, _ ->
            error("table-builder test: action lambda should not be invoked")
        }

    @Test
    fun buildsTablesForSAToB() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
                grammar;
                extern { enum Tok { "a" => .., "b" => .. } }
                pub S: () = "a" "b" => ();
                """.trimIndent(),
            )

            Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                try {
                    val states = buildLalrStates(grammar, nt("S"))

                    // Hand-produced productions array, one entry per grammar production
                    // in the order tablesFromLr1States walks them (nonterminal-by-nt
                    // declaration order, then production order within each nonterminal).
                    val productionCount = grammar.nonterminals.values.sumOf { it.productions.size }
                    val productions: Array<Production<Unit, Int>> =
                        Array(productionCount) {
                            Production(
                                nonterminalId = 0,
                                rhsLength = 0,
                                action = stubAction(),
                            )
                        }

                    val tables = tablesFromLr1States(
                        grammar = grammar,
                        states = states,
                        productions = productions,
                        acceptProductionId = 0,
                    )

                    // Number of states / terminals / nonterminals comes from the front
                    // end. Cross-check that the builder set these consistently.
                    assertEquals(states.size, tables.numStates)
                    assertEquals(grammar.terminals.all.size, tables.numTerminals)
                    assertEquals(grammar.nonterminals.size, tables.numNonterminals)

                    // Sanity invariants on the action arrays.
                    assertEquals(
                        tables.numStates * tables.numTerminals,
                        tables.action.size,
                    )
                    assertEquals(tables.numStates, tables.eofAction.size)
                    assertEquals(
                        tables.numStates * tables.numNonterminals,
                        tables.goto.size,
                    )

                    // For a non-empty grammar, *some* cell in the action table must be
                    // non-zero (either a shift or a reduce). A table of all zeros means
                    // the builder ate the state graph and produced nothing useful.
                    assertTrue(
                        tables.action.any { it != 0.toShort() } ||
                            tables.eofAction.any { it != 0.toShort() },
                        "built tables have no non-error actions — builder produced an empty machine",
                    )

                    // Specifically: the start state (state 0) on the first terminal
                    // (terminal 0) should be a shift — the only legal first move of
                    // S = "a" "b" is to shift "a".
                    val startState0FirstTerminal = tables.action[0 * tables.numTerminals + 0]
                    assertTrue(
                        startState0FirstTerminal > 0,
                        "expected shift in (state=0, terminal=0), got $startState0FirstTerminal",
                    )
                } finally {
                    lr1Tls.drop()
                }
            }
        }
    }

    @Test
    fun rejectsProductionArraySizeMismatch() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
                grammar;
                extern { enum Tok { "a" => .. } }
                pub S: () = "a" => ();
                """.trimIndent(),
            )

            Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                try {
                    val states = buildLalrStates(grammar, nt("S"))
                    val wrongSizeProductions: Array<Production<Unit, Int>> = arrayOf()

                    val ex = kotlin.runCatching {
                        tablesFromLr1States(
                            grammar = grammar,
                            states = states,
                            productions = wrongSizeProductions,
                            acceptProductionId = 0,
                        )
                    }.exceptionOrNull()

                    assertTrue(
                        ex is IllegalArgumentException,
                        "expected IllegalArgumentException, got ${ex?.let { it::class }}: $ex",
                    )
                    assertTrue(
                        ex.message!!.contains("does not match grammar's"),
                        "expected size-mismatch message, got: ${ex.message}",
                    )
                } finally {
                    lr1Tls.drop()
                }
            }
        }
    }
}
