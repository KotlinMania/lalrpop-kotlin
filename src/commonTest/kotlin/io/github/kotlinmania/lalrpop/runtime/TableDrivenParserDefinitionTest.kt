package io.github.kotlinmania.lalrpop.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Drives the same toy grammar from [TableDrivenLr1DriverTest] but through the existing
 * `Parser<...>` driver loop in `StateMachine.kt`, via the [TableDrivenParserDefinition]
 * adapter. Validates that the data-driven shape integrates cleanly with the
 * already-tested upstream-style runtime — step 1.5 of the migration plan.
 */

private sealed class ToyTok {
    data class A(val text: String) : ToyTok()
    data class B(val text: String) : ToyTok()
}

private sealed class DefToySym {
    data class TerminalA(val text: String) : DefToySym()
    data class TerminalB(val text: String) : DefToySym()
    data class S(val a: TerminalA, val b: TerminalB) : DefToySym()
    data class Goal(val s: S) : DefToySym()
}

/** Custom user-error type carried in `ParseError.User`. Empty for this toy grammar. */
private object ToyUserError

private object ToyDef {
    private const val NUM_STATES = 4
    private const val NUM_TERMINALS = 2
    private const val NUM_NONTERMINALS = 2
    private const val T_A = 0
    private const val T_B = 1
    private const val NT_GOAL = 0
    private const val NT_S = 1

    private val ACTION = ShortArray(NUM_STATES * NUM_TERMINALS).also {
        it[0 * NUM_TERMINALS + T_A] = 2  // state 0, a → shift 1
        it[1 * NUM_TERMINALS + T_B] = 3  // state 1, b → shift 2
    }

    private val EOF_ACTION = ShortArray(NUM_STATES).also {
        it[2] = -2  // reduce production 1 (S → a b)
        it[3] = -1  // reduce production 0 (Goal → S, accept)
    }

    private val GOTO = ShortArray(NUM_STATES * NUM_NONTERMINALS).also {
        it[0 * NUM_NONTERMINALS + NT_S] = 4  // state 0, S → state 3 (1-based)
    }

    private val PRODUCTIONS: Array<Production<DefToySym, Int>> = arrayOf(
        Production(
            nonterminalId = NT_GOAL.toShort(),
            rhsLength = 1,
            action = ProductionAction { stack, _ ->
                val s = stack.pop<DefToySym.S>()
                Result.success(DefToySym.Goal(s))
            },
        ),
        Production(
            nonterminalId = NT_S.toShort(),
            rhsLength = 2,
            action = ProductionAction { stack, _ ->
                val b = stack.pop<DefToySym.TerminalB>()
                val a = stack.pop<DefToySym.TerminalA>()
                Result.success(DefToySym.S(a, b))
            },
        ),
    )

    private val TABLES: ParseTables<DefToySym, Int> = ParseTables(
        numStates = NUM_STATES,
        numTerminals = NUM_TERMINALS,
        numNonterminals = NUM_NONTERMINALS,
        action = ACTION,
        eofAction = EOF_ACTION,
        goto = GOTO,
        productions = PRODUCTIONS,
        acceptProductionId = 0,
    )

    val DEFINITION: TableDrivenParserDefinition<DefToySym, Int, ToyTok, ToyUserError> =
        TableDrivenParserDefinition(
            tables = TABLES,
            tokenToTerminalId = { tok ->
                when (tok) {
                    is ToyTok.A -> T_A
                    is ToyTok.B -> T_B
                }
            },
            tokenToSymbol = { _, tok ->
                when (tok) {
                    is ToyTok.A -> DefToySym.TerminalA(tok.text)
                    is ToyTok.B -> DefToySym.TerminalB(tok.text)
                }
            },
            mapActionFailure = { cause ->
                // The toy grammar's productions never fail. If they did, the caller
                // would supply a structured mapper here.
                throw cause
            },
            initialLocation = 0,
            supportsErrorRecovery = false,
            errorRecoverySymbolOf = {
                error("toy grammar declares no error recovery")
            },
            expectedTokensFor = { _ -> emptyList() },
        )
}

class TableDrivenParserDefinitionTest {

    @Test
    fun parsesAbThroughExistingParserDriver() {
        val tokens: List<TokResult<Int, ToyTok, ToyUserError>> = listOf(
            TokResult.Ok(Triple(0, ToyTok.A("hello"), 5)),
            TokResult.Ok(Triple(5, ToyTok.B("world"), 10)),
        )

        val outcome: ParseResult<DefToySym, Int, ToyTok, ToyUserError> =
            Parser.drive(ToyDef.DEFINITION, tokens.iterator())

        when (outcome) {
            is ParseResult.Success -> {
                val tree = outcome.value
                assertTrue(tree is DefToySym.Goal, "expected Goal at root, got $tree")
                assertEquals("hello", tree.s.a.text)
                assertEquals("world", tree.s.b.text)
            }
            is ParseResult.Failure -> fail("expected success but got failure: ${outcome.error}")
        }
    }

    @Test
    fun unexpectedTerminalSurfacesAsUnrecognizedToken() {
        val tokens: List<TokResult<Int, ToyTok, ToyUserError>> = listOf(
            TokResult.Ok(Triple(0, ToyTok.B("oops"), 4)),
        )

        val outcome = Parser.drive(ToyDef.DEFINITION, tokens.iterator())

        assertTrue(outcome is ParseResult.Failure, "expected failure, got $outcome")
        val error = outcome.error
        assertTrue(
            error is ParseError.UnrecognizedToken,
            "expected UnrecognizedToken, got ${error::class}: $error",
        )
        // Token span survives end-to-end through the existing driver.
        assertEquals(0, error.token.first)
        assertEquals(4, error.token.third)
    }

    @Test
    fun prematureEofSurfacesAsUnrecognizedEof() {
        val tokens: List<TokResult<Int, ToyTok, ToyUserError>> = listOf(
            TokResult.Ok(Triple(0, ToyTok.A("alone"), 5)),
        )

        val outcome = Parser.drive(ToyDef.DEFINITION, tokens.iterator())

        assertTrue(outcome is ParseResult.Failure, "expected failure, got $outcome")
        val error = outcome.error
        assertTrue(
            error is ParseError.UnrecognizedEof,
            "expected UnrecognizedEof, got ${error::class}: $error",
        )
    }
}
