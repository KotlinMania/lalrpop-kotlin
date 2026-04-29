// port-lint: ignore
// transliterated from upstream module root
package io.github.kotlinmania.lalrpop.lr1.report

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.lr1.Lookahead
import io.github.kotlinmania.lalrpop.lr1.Nil
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.core.Action
import io.github.kotlinmania.lalrpop.lr1.core.Conflict
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.Items
import io.github.kotlinmania.lalrpop.lr1.core.State
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.core.TableConstructionError

/**
 * Result analogue used by the report generator: either a successful
 * list of states, or a construction error capturing partial states
 * plus the list of conflicts we found.
 */
sealed class LrResult<L : Lookahead<L>> {
    data class Ok<L : Lookahead<L>>(val states: List<State<L>>) : LrResult<L>()
    data class Err<L : Lookahead<L>>(val error: TableConstructionError<L>) : LrResult<L>()
}

fun <L : Lookahead<L>> generateReport(
    out: Appendable,
    lr1result: LrResult<L>,
) {
    val generator = ReportGenerator(out)
    generator.reportLrTableConstruction(lr1result)
}

/**
 * Convenience overload for the common-case typealias used by `Lr1.kt`
 * where only the successful states list is available.
 */
fun generateReport(
    out: Appendable,
    lr1result: List<io.github.kotlinmania.lalrpop.lr1.core.State<TokenSet>>,
) {
    generateReport(out, LrResult.Ok(lr1result))
}

private const val INDENT_STRING: String = "    "

private typealias ConflictStateMap<L> = Map<StateIndex, MutableList<Conflict<L>>>

private class ReportGenerator<W : Appendable>(
    val out: W,
) {
    fun <L : Lookahead<L>> reportLrTableConstruction(
        lr1result: LrResult<L>,
    ) {
        writeHeader()
        writeSectionHeader("Summary")
        out.appendLine()
        when (lr1result) {
            is LrResult.Ok -> {
                out.appendLine("Constructed ${lr1result.states.size} states")
                reportStates(lr1result.states, map())
            }
            is LrResult.Err -> {
                val tableConstructionError = lr1result.error
                out.appendLine("Failure")
                out.appendLine("Constructed ${tableConstructionError.states.size} states")
                out.appendLine("Has ${tableConstructionError.conflicts.size} conflicts")
                val (sr, rr, conflictMap) =
                    processConflicts(tableConstructionError.conflicts)
                if (sr > 0) {
                    out.appendLine("${INDENT_STRING}shift/reduce:  $sr")
                }
                if (rr > 0) {
                    out.appendLine("${INDENT_STRING}reduce/reduce: $rr")
                }
                out.append("States with conflicts: ")
                for (state in conflictMap.keys) {
                    out.append(" $state")
                }
                out.appendLine()
                reportStates(tableConstructionError.states, conflictMap)
            }
        }
    }

    private fun <L : Lookahead<L>> processConflicts(
        conflicts: List<Conflict<L>>,
    ): Triple<Int, Int, Map<StateIndex, MutableList<Conflict<L>>>> {
        var sr = 0
        var rr = 0
        val conflictMap: Map<StateIndex, MutableList<Conflict<L>>> = map()
        for (conflict in conflicts) {
            when (conflict.action) {
                is Action.Shift -> sr += 1
                is Action.Reduce -> rr += 1
            }
            conflictMap.getOrPut(conflict.state) { mutableListOf() }.add(conflict)
        }
        return Triple(sr, rr, conflictMap)
    }

    private fun <L : Lookahead<L>> reportStates(
        states: List<State<L>>,
        conflictMap: Map<StateIndex, MutableList<Conflict<L>>>,
    ) {
        writeSectionHeader("State Table")
        for (state in states) {
            out.appendLine()
            reportState(state, conflictMap[state.index])
        }
    }

    private fun <L : Lookahead<L>> reportState(
        state: State<L>,
        conflictsOpt: List<Conflict<L>>?,
    ) {
        out.appendLine("State ${state.index} {")
        writeItems(state.items)
        if (state.reductions.isNotEmpty()) {
            out.appendLine()
            writeReductions(state.reductions)
        }

        val maxWidth = getWidthForGotos(state)

        if (!(state.shifts.size > 0)) {
            out.appendLine()
            writeShifts(state.shifts, maxWidth)
        }

        if (!(state.gotos.size > 0)) {
            out.appendLine()
            writeGotos(state.gotos, maxWidth)
        }

        if (conflictsOpt != null) {
            for (conflict in conflictsOpt) {
                writeConflict(conflict)
            }
        }

        out.appendLine("}")
    }

    private fun <L : Lookahead<L>> writeConflict(conflict: Conflict<L>) {
        out.appendLine()
        when (val action = conflict.action) {
            is Action.Shift -> {
                val terminal = action.terminal
                val state = action.state
                val maxWidth = maxOf(
                    terminal.displayLen(),
                    conflict.production.nonterminal.len(),
                )
                out.appendLine("${INDENT_STRING}shift/reduce conflict")
                out.append("${INDENT_STRING}${INDENT_STRING}reduction ")
                writeProduction(conflict.production, maxWidth)
                val sterminal = terminal.toString()
                out.appendLine(
                    "${INDENT_STRING}${INDENT_STRING}shift     ${sterminal.padEnd(maxWidth)}    shift and goto $state"
                )
            }
            is Action.Reduce -> {
                val otherProduction = action.production
                val maxWidth = maxOf(
                    otherProduction.nonterminal.len(),
                    conflict.production.nonterminal.len(),
                )
                out.appendLine("${INDENT_STRING}reduce/reduce conflict")
                out.append("${INDENT_STRING}${INDENT_STRING}reduction ")
                writeProduction(conflict.production, maxWidth)
                out.append("${INDENT_STRING}${INDENT_STRING}reduction ")
                writeProduction(otherProduction, maxWidth)
            }
        }
        writeLookahead(conflict.lookahead)
    }

    private fun <L : Lookahead<L>> writeItems(items: Items<L>) {
        val maxWidth = getMaxLength(items.vec.map { NonterminalDisplayLen(it.production.nonterminal) })

        for (item in items.vec) {
            out.appendLine()
            writeItem(item, maxWidth)
        }
    }

    private fun <L : Lookahead<L>> writeItem(item: Item<L>, maxWidth: Int) {
        out.append(INDENT_STRING)
        // stringize it first to allow handle :width by Display for string
        val s = item.production.nonterminal.toString()
        out.append("${s.padEnd(maxWidth)} ->")
        for (i in 0 until item.index) {
            out.append(" ${item.production.symbols[i]}")
        }
        out.append(" .")
        for (i in item.index until item.production.symbols.size) {
            out.append(" ${item.production.symbols[i]}")
        }
        out.appendLine()
        writeLookahead(item.lookahead)
    }

    private fun writeShifts(
        shifts: Map<TerminalString, StateIndex>,
        maxWidth: Int,
    ) {
        for (entry in shifts) {
            out.append(INDENT_STRING)
            // stringize it first to allow handle :width by Display for string
            val s = entry.key.toString()
            out.appendLine("${s.padEnd(maxWidth)} shift and goto ${entry.value}")
        }
    }

    private fun <L : Lookahead<L>> writeReductions(reductions: List<Pair<L, Production>>) {
        val maxWidth = getMaxLength(reductions.map { NonterminalDisplayLen(it.second.nonterminal) })
        for (reduction in reductions) {
            out.appendLine()
            writeReduction(reduction, maxWidth)
        }
    }

    private fun writeProduction(production: Production, maxWidth: Int) {
        out.append("${production.nonterminal.toString().padEnd(maxWidth)} ->")
        for (symbol in production.symbols) {
            out.append(" $symbol")
        }
        out.appendLine()
    }

    private fun <L : Lookahead<L>> writeReduction(
        reduction: Pair<L, Production>,
        maxWidth: Int,
    ) {
        val production = reduction.second
        out.append("${INDENT_STRING}reduction ")
        writeProduction(production, maxWidth)
        writeLookahead(reduction.first)
    }

    private fun <L : Lookahead<L>> writeLookahead(lookahead: L) {
        if (lookahead.hasAnythingToPrint()) {
            out.append("${INDENT_STRING}${INDENT_STRING}lookahead")
            lookahead.printTo(out)
            out.appendLine()
        }
    }

    private fun writeGotos(
        gotos: Map<NonterminalString, StateIndex>,
        maxWidth: Int,
    ) {
        for (entry in gotos) {
            out.append(INDENT_STRING)
            // stringize it first to allow handle :width by Display for string
            val s = entry.key.toString()
            out.appendLine("${s.padEnd(maxWidth)} goto ${entry.value}")
        }
    }

    private fun writeSectionHeader(title: String) {
        out.appendLine("\n$title")
        out.appendLine("----------------------------------------")
    }

    private fun writeHeader() {
        out.appendLine("Lalrpop Report File")
        out.appendLine("========================================")
    }
}

// helpers

private interface LookaheadPrinter {
    fun print(out: Appendable)
    fun hasAnythingToPrint(): Boolean
}

private object NilLookaheadPrinter : LookaheadPrinter {
    override fun print(out: Appendable) {
        // no-op
    }

    override fun hasAnythingToPrint(): Boolean = false
}

private class TokenSetLookaheadPrinter(val ts: TokenSet) : LookaheadPrinter {
    override fun print(out: Appendable) {
        for (i in ts.bitSet) {
            out.append(" $i")
        }
    }

    override fun hasAnythingToPrint(): Boolean = ts.len() > 0
}

private fun <L : Lookahead<L>> L.lookaheadPrinter(): LookaheadPrinter = when (this) {
    is Nil -> NilLookaheadPrinter
    is TokenSet -> TokenSetLookaheadPrinter(this)
    else -> error("unsupported Lookahead type: $this")
}

private fun <L : Lookahead<L>> L.printTo(out: Appendable) {
    this.lookaheadPrinter().print(out)
}

private fun <L : Lookahead<L>> L.hasAnythingToPrint(): Boolean =
    this.lookaheadPrinter().hasAnythingToPrint()

private interface HasDisplayLen {
    fun displayLen(): Int
}

private class TerminalDisplayLen(val t: TerminalString) : HasDisplayLen {
    override fun displayLen(): Int = t.displayLen()
}

private class NonterminalDisplayLen(val nt: NonterminalString) : HasDisplayLen {
    override fun displayLen(): Int = nt.len()
}

private fun <I : HasDisplayLen> getMaxLength(m: Iterable<I>): Int =
    m.map { it.displayLen() }.fold(0) { a, b -> maxOf(a, b) }

private fun <L : Lookahead<L>> getWidthForGotos(state: State<L>): Int {
    val shiftsMaxWidth = getMaxLength(state.shifts.keys.map { TerminalDisplayLen(it) })
    val gotosMaxWidth = getMaxLength(state.gotos.keys.map { NonterminalDisplayLen(it) })
    return maxOf(shiftsMaxWidth, gotosMaxWidth)
}
