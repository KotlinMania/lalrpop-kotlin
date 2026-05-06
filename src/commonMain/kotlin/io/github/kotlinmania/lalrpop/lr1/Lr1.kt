// transliterated from upstream module root
/** Naive LR(1) generation algorithm. */
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.report.generateReport as generateReportImpl

fun buildStatesOrError(grammar: Grammar, start: NonterminalString): BuildOutcome<TokenSet> {
    return try {
        BuildOutcome.Ok(buildStates(grammar, start))
    } catch (e: TableConstructionErrorException) {
        BuildOutcome.Err(e.lr1Inner ?: throw e)
    }
}

fun buildStates(grammar: Grammar, start: NonterminalString): MutableList<State<TokenSet>> {
    val lr1States: MutableList<State<TokenSet>> = if (!grammar.algorithm.lalr) {
        buildLr1States(grammar, start)
    } else {
        buildLalrStates(grammar, start)
    }

    rewriteStateIndices(grammar, lr1States)

    return lr1States
}

fun generateReport(
    out: Appendable,
    lr1result: MutableList<State<TokenSet>>,
) {
    generateReportImpl(out, lr1result)
}

/**
 * By packing all states which start a reduction we can generate a smaller goto table as any
 * states not starting a reduction will not need a row
 */
private fun rewriteStateIndices(grammar: Grammar, states: MutableList<State<TokenSet>>) {
    val startStates = MutableList(states.size) { false }
    for ((index, state) in states.withIndex()) {
        check(state.index.value == index)
        if (grammar.nonterminals.keys.any { nonterminal -> state.gotos.containsKey(nonterminal) }) {
            startStates[index] = true
        }
    }

    // Since the sort is stable and we put starting states first, the initial state is still 0
    states.sortBy { state -> if (!startStates[state.index.value]) 1 else 0 }

    val stateRewrite = MutableList(states.size) { 0 }
    for ((newIndex, state) in states.withIndex()) {
        stateRewrite[state.index.value] = newIndex
        state.index = StateIndex(newIndex)
    }

    for (state in states) {
        for (nonterminal in state.gotos.keys.toList()) {
            val goto = state.gotos.getValue(nonterminal)
            state.gotos[nonterminal] = StateIndex(stateRewrite[goto.value])
        }
        for (terminal in state.shifts.keys.toList()) {
            val shift = state.shifts.getValue(terminal)
            state.shifts[terminal] = StateIndex(stateRewrite[shift.value])
        }
    }
}
