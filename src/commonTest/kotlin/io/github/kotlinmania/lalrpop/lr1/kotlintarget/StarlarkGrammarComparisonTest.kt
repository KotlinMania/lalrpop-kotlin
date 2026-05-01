package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.buildLalrStates
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the lalrpop-kotlin front end on the actual `grammar.lalrpop` from starlark-kotlin
 * and reports the structural counts the new data-driven path would produce. The numbers
 * surface for cross-checking against starlark-kotlin's hand-translated artifacts:
 *
 * - `GrammarReducers.kt`'s big `when` dispatcher has 302 rule cases.
 * - `GrammarSymbol.kt` declares 51 typed `Variant` cases.
 *
 * If the front end's normalized production count diverges materially from 302, or the
 * inferred number of distinct stack-element types diverges materially from 51, that's a
 * red flag for the hand-translation — either it dropped rules, collapsed variants
 * unsoundly, or skipped error-recovery paths.
 *
 * The test asserts the front end can ingest the grammar at all (ignoring parse errors
 * means a deeper problem) and prints the counts so the comparison is visible from the
 * test runner's output. Hard equality assertions against the hand-translated numbers
 * are left to a follow-up once we know the front end's output is sane.
 */
class StarlarkGrammarComparisonTest {

    @Test
    fun frontEndCanIngestStarlarkGrammar() {
        Tls.test().use {
            val grammar = normalizedGrammar(STARLARK_GRAMMAR_LALRPOP)

            val productionCount = grammar.nonterminals.values.sumOf { it.productions.size }
            val nonterminalCount = grammar.nonterminals.size
            val terminalCount = grammar.terminals.all.size

            println(
                "starlark grammar — front-end normalized counts: " +
                    "terminals=$terminalCount, nonterminals=$nonterminalCount, " +
                    "productions=$productionCount"
            )

            // Sanity: a 509-line LALRPOP grammar should not normalize to zero.
            assertTrue(productionCount > 0, "no productions — front end ate the grammar")
            assertTrue(terminalCount > 0, "no terminals")
            assertTrue(nonterminalCount > 0, "no nonterminals")

            // The starlark grammar mentions ~60 terminals in its `extern { enum Tok }`
            // block (51 keyword/symbol/bracket entries plus 5 typed value tokens
            // INDENT/DEDENT/\n/IDENTIFIER/INTEGER/FLOAT/STRING/FSTRING) so the
            // normalized terminal set should be in that ballpark — at least 50.
            assertTrue(
                terminalCount >= 50,
                "terminal count $terminalCount looks too low for the starlark grammar — " +
                "extern token block alone declares ~60",
            )
        }
    }

    @Test
    fun lr1StateMachineBuildsForStarlarkGrammar() {
        Tls.test().use {
            val grammar = normalizedGrammar(STARLARK_GRAMMAR_LALRPOP)

            Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                try {
                    // Public start nonterminal of the grammar is `Starlark`.
                    val states = buildLalrStates(grammar, NonterminalString(Atom.from("Starlark")))

                    println(
                        "starlark grammar — LR(1) state count: ${states.size}",
                    )

                    assertTrue(states.size > 0, "LR(1) builder produced no states")
                    // A Starlark-sized grammar has hundreds of states. If we get fewer
                    // than 100, the builder has collapsed the machine wrongly or the
                    // grammar lost rules in normalization.
                    assertTrue(
                        states.size >= 100,
                        "state count ${states.size} looks too low for the starlark grammar",
                    )
                } finally {
                    lr1Tls.drop()
                }
            }
        }
    }

    @Test
    fun tablesBuilderProducesNonEmptyTablesForStarlarkGrammar() {
        Tls.test().use {
            val grammar = normalizedGrammar(STARLARK_GRAMMAR_LALRPOP)

            Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                try {
                    val states = buildLalrStates(grammar, NonterminalString(Atom.from("Starlark")))
                    val productionCount = grammar.nonterminals.values.sumOf { it.productions.size }

                    val productions = Array(productionCount) {
                        io.github.kotlinmania.lalrpop.runtime.Production<Unit, Int>(
                            nonterminalId = 0,
                            rhsLength = 0,
                            action = io.github.kotlinmania.lalrpop.runtime.ProductionAction { _, _ ->
                                error("starlark comparison test — action not invoked")
                            },
                        )
                    }

                    val tables = tablesFromLr1States(
                        grammar = grammar,
                        states = states,
                        productions = productions,
                        acceptProductionId = 0,
                    )

                    val nonZeroAction = tables.action.count { it != 0.toShort() }
                    val nonZeroEofAction = tables.eofAction.count { it != 0.toShort() }
                    val nonZeroGoto = tables.goto.count { it != 0.toShort() }

                    println(
                        "starlark grammar — packed table cells: " +
                            "action=${tables.action.size} (non-zero $nonZeroAction), " +
                            "eofAction=${tables.eofAction.size} (non-zero $nonZeroEofAction), " +
                            "goto=${tables.goto.size} (non-zero $nonZeroGoto)"
                    )

                    assertTrue(
                        nonZeroAction > 0,
                        "action table is all zero — table builder produced an empty machine",
                    )
                    assertTrue(
                        nonZeroGoto > 0,
                        "goto table is all zero — table builder produced no nonterminal transitions",
                    )

                    // Tables size should be states × terminals (for action) and
                    // states × nonterminals (for goto). Cross-check the dimensions.
                    val expectedActionSize = tables.numStates * tables.numTerminals
                    val expectedGotoSize = tables.numStates * tables.numNonterminals
                    assertTrue(
                        tables.action.size == expectedActionSize,
                        "action table size ${tables.action.size} != $expectedActionSize",
                    )
                    assertTrue(
                        tables.goto.size == expectedGotoSize,
                        "goto table size ${tables.goto.size} != $expectedGotoSize",
                    )
                } finally {
                    lr1Tls.drop()
                }
            }
        }
    }
}
