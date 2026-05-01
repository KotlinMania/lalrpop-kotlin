package io.github.kotlinmania.lalrpop.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Hand-built tables for a tiny grammar. This is the contract validation step from
 * INTERPRETER.md: prove that a generated parser, expressed as packed `ShortArray`s plus
 * a typed productions array, can be driven through [TableDrivenLr1Driver] to produce a
 * typed result.
 *
 * Grammar:
 *
 * ```
 * Goal → S
 * S    → "a" "b"
 * ```
 *
 * Terminals:  T_A = 0, T_B = 1
 * Nonterminals: NT_GOAL = 0, NT_S = 1
 *
 * Canonical LR(0) state machine for this grammar:
 *
 * ```
 * state 0:  Goal → · S         action[0, a] = shift 1     goto[0, S] = 3
 *           S    → · "a" "b"
 *
 * state 1:  S    → "a" · "b"   action[1, b] = shift 2
 *
 * state 2:  S    → "a" "b" ·   eofAction[2]  = reduce S→"a" "b"  (production 1)
 *
 * state 3:  Goal → S ·         eofAction[3]  = reduce Goal→S      (production 0, accept)
 * ```
 *
 * Production indices: 0 = Goal → S (accept), 1 = S → "a" "b".
 */
private sealed class ToyTree {
    data class TerminalA(val text: String) : ToyTree()
    data class TerminalB(val text: String) : ToyTree()
    data class S(val a: TerminalA, val b: TerminalB) : ToyTree()
    data class Goal(val s: S) : ToyTree()
}

private object ToyGrammar {
    const val NUM_STATES = 4
    const val NUM_TERMINALS = 2
    const val NUM_NONTERMINALS = 2

    const val T_A = 0
    const val T_B = 1

    const val NT_GOAL = 0
    const val NT_S = 1

    /** action[state * NUM_TERMINALS + terminal]. Encoding per [ParseTables]. */
    val ACTION: ShortArray = ShortArray(NUM_STATES * NUM_TERMINALS) { 0 }.also {
        // state 0, terminal a → shift 1  → encoded as 1 + 1 = 2
        it[0 * NUM_TERMINALS + T_A] = 2
        // state 1, terminal b → shift 2  → encoded as 2 + 1 = 3
        it[1 * NUM_TERMINALS + T_B] = 3
    }

    /** eofAction[state]. */
    val EOF_ACTION: ShortArray = ShortArray(NUM_STATES) { 0 }.also {
        // state 2, EOF → reduce production 1 (S→AB) → encoded as -(1 + 1) = -2.
        // After reducing, the driver pops 2 states + 2 symbols, lands back in state 0
        // with an S on top, takes GOTO[0, NT_S] to state 3, and re-runs the EOF action.
        it[2] = -2
        // state 3, EOF → reduce production 0 (Goal→S, accept) → encoded as -(0 + 1) = -1
        it[3] = -1
    }

    /** goto[state * NUM_NONTERMINALS + nonterminal]; 1-based with 0 = no transition. */
    val GOTO: ShortArray = ShortArray(NUM_STATES * NUM_NONTERMINALS) { 0 }.also {
        // state 0, NT_S → state 3  → encoded as 3 + 1 = 4
        it[0 * NUM_NONTERMINALS + NT_S] = 4
    }

    val PRODUCTIONS: Array<Production<ToyTree, Int>> = arrayOf(
        // Production 0: Goal → S
        Production(
            nonterminalId = NT_GOAL.toShort(),
            rhsLength = 1,
            action = ProductionAction { stack, _ ->
                val s = stack.pop<ToyTree.S>()
                ToyTree.Goal(s)
            },
        ),
        // Production 1: S → "a" "b"
        Production(
            nonterminalId = NT_S.toShort(),
            rhsLength = 2,
            action = ProductionAction { stack, _ ->
                val b = stack.pop<ToyTree.TerminalB>()
                val a = stack.pop<ToyTree.TerminalA>()
                ToyTree.S(a, b)
            },
        ),
    )

    val TABLES: ParseTables<ToyTree, Int> = ParseTables(
        numStates = NUM_STATES,
        numTerminals = NUM_TERMINALS,
        numNonterminals = NUM_NONTERMINALS,
        action = ACTION,
        eofAction = EOF_ACTION,
        goto = GOTO,
        productions = PRODUCTIONS,
        acceptProductionId = 0,
    )
}

class TableDrivenLr1DriverTest {

    @Test
    fun parsesAbToTypedTree() {
        val tokens = listOf(
            TerminalToken<ToyTree, Int>(
                terminalId = ToyGrammar.T_A,
                symbol = ToyTree.TerminalA("a"),
                start = 0,
                end = 1,
            ),
            TerminalToken<ToyTree, Int>(
                terminalId = ToyGrammar.T_B,
                symbol = ToyTree.TerminalB("b"),
                start = 1,
                end = 2,
            ),
        )

        val driver = TableDrivenLr1Driver(
            tables = ToyGrammar.TABLES,
            tokens = tokens.iterator(),
            eofLocation = 2,
        )

        val outcome = driver.parse()

        when (outcome) {
            is ParseOutcome.Success -> {
                val expected = ToyTree.Goal(
                    s = ToyTree.S(
                        a = ToyTree.TerminalA("a"),
                        b = ToyTree.TerminalB("b"),
                    ),
                )
                assertEquals(expected, outcome.tree)
                assertEquals(0, outcome.span.start)
                assertEquals(2, outcome.span.end)
            }
            is ParseOutcome.Failure -> fail("expected success but got failure: $outcome")
        }
    }

    @Test
    fun rejectsUnexpectedTerminalAtStart() {
        val tokens = listOf(
            TerminalToken<ToyTree, Int>(
                terminalId = ToyGrammar.T_B,
                symbol = ToyTree.TerminalB("b"),
                start = 0,
                end = 1,
            ),
        )

        val driver = TableDrivenLr1Driver(
            tables = ToyGrammar.TABLES,
            tokens = tokens.iterator(),
            eofLocation = 1,
        )

        val outcome = driver.parse()

        assertTrue(outcome is ParseOutcome.Failure, "expected failure")
        assertEquals(0, outcome.state)
        assertEquals(ToyGrammar.T_B, outcome.unexpectedTerminalId)
        assertEquals(0, outcome.location)
    }

    @Test
    fun rejectsEofAfterFirstTerminal() {
        val tokens = listOf(
            TerminalToken<ToyTree, Int>(
                terminalId = ToyGrammar.T_A,
                symbol = ToyTree.TerminalA("a"),
                start = 0,
                end = 1,
            ),
        )

        val driver = TableDrivenLr1Driver(
            tables = ToyGrammar.TABLES,
            tokens = tokens.iterator(),
            eofLocation = 1,
        )

        val outcome = driver.parse()

        assertTrue(outcome is ParseOutcome.Failure, "expected failure")
        assertEquals(1, outcome.state)
        assertEquals(-1, outcome.unexpectedTerminalId)
    }

    @Test
    fun productionLambdaProducesTypedSubtree() {
        // Verifies the action lambda contract end-to-end: a typed Production<S, L>'s
        // action receives a typed ParseStack and returns a typed S without any Any-cast
        // along the path.
        val tokens = listOf(
            TerminalToken<ToyTree, Int>(
                terminalId = ToyGrammar.T_A,
                symbol = ToyTree.TerminalA("hello"),
                start = 10,
                end = 15,
            ),
            TerminalToken<ToyTree, Int>(
                terminalId = ToyGrammar.T_B,
                symbol = ToyTree.TerminalB("world"),
                start = 15,
                end = 20,
            ),
        )

        val outcome = TableDrivenLr1Driver(
            tables = ToyGrammar.TABLES,
            tokens = tokens.iterator(),
            eofLocation = 20,
        ).parse()

        assertTrue(outcome is ParseOutcome.Success)
        val tree = outcome.tree
        assertTrue(tree is ToyTree.Goal, "expected ToyTree.Goal at the root, got $tree")
        assertEquals("hello", tree.s.a.text)
        assertEquals("world", tree.s.b.text)
        assertEquals(10, outcome.span.start)
        assertEquals(20, outcome.span.end)
    }
}
