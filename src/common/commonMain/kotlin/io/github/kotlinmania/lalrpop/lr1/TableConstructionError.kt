package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production

internal sealed class Action : Comparable<Action> {
    data class Shift(
        val terminal: TerminalString,
        val state: StateIndex,
    ) : Action()

    data class Reduce(
        val production: Production,
    ) : Action()

    override fun compareTo(other: Action): Int {
        val o1 = ordinal()
        val o2 = other.ordinal()
        if (o1 != o2) return o1 - o2
        return when (this) {
            is Shift -> {
                val that = other as Shift
                val c = terminal.compareTo(that.terminal)
                if (c != 0) c else state.compareTo(that.state)
            }

            is Reduce -> {
                production.compareTo((other as Reduce).production)
            }
        }
    }

    private fun ordinal(): Int =
        when (this) {
            is Shift -> 0
            is Reduce -> 1
        }
}

internal data class Conflict<L>(
    // when in this state...
    val state: StateIndex,
    // with the following lookahead...
    val lookahead: L,
    // we can reduce...
    val production: Production,
    // but we can also...
    val action: Action,
)

internal typealias Lr0Conflict = Conflict<Nil>

internal typealias Lr1Conflict = Conflict<TokenSet>

internal data class TableConstructionError<L : Lookahead<L>>(
    // LR(1) state set, possibly incomplete if construction is
    // configured to terminate early.
    val states: MutableList<State<L>>,
    // Conflicts (non-empty) found in those states.
    val conflicts: MutableList<Conflict<L>>,
)

internal typealias Lr0TableConstructionError = TableConstructionError<Nil>

internal typealias Lr1TableConstructionError = TableConstructionError<TokenSet>

internal sealed class LrResult<L : Lookahead<L>> {
    data class Ok<L : Lookahead<L>>(
        val states: List<State<L>>,
    ) : LrResult<L>()

    data class Err<L : Lookahead<L>>(
        val error: TableConstructionError<L>,
    ) : LrResult<L>()
}

internal typealias Lr1Result = LrResult<TokenSet>
