// port-lint: source src/lr1/mod.rs
//! Naive LR(1) generation algorithm.
package io.github.kotlinmania.lalrpop_kotlin.lr1

import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop_kotlin.lr1.build.buildLr1States
import io.github.kotlinmania.lalrpop_kotlin.lr1.buildLalr.buildLalrStates
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.Lr1State
import io.github.kotlinmania.lalrpop_kotlin.lr1.error.reportError as reportErrorImpl
import io.github.kotlinmania.lalrpop_kotlin.lr1.report.generateReport as generateReportImpl

// Re-exports from submodules
typealias Lr1Result = MutableList<Lr1State>

fun reportError(
    out: StringBuilder,
    grammar: Grammar,
    error: io.github.kotlinmania.lalrpop_kotlin.lr1.core.Lr1TableConstructionError,
) {
    reportErrorImpl(grammar, error) { message ->
        message.emitToCanvas(80).writeTo(out)
    }
}

fun buildStates(grammar: Grammar, start: NonterminalString): Lr1Result {
    val lr1States: Lr1Result = if (!grammar.algorithm.lalr) {
        buildLr1States(grammar, start)
    } else {
        buildLalrStates(grammar, start)
    }

    rewriteStateIndices(grammar, lr1States)

    return lr1States
}

fun generateReport(
    out: StringBuilder,
    lr1result: Lr1Result,
) {
    generateReportImpl(out, lr1result)
}

/// By packing all states which start a reduction we can generate a smaller goto table as any
/// states not starting a reduction will not need a row
private fun rewriteStateIndices(grammar: Grammar, states: MutableList<Lr1State>) {
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
        state.index.value = newIndex
    }

    for (state in states) {
        for (goto in state.gotos.values) {
            goto.value = stateRewrite[goto.value]
        }
        for (shift in state.shifts.values) {
            shift.value = stateRewrite[shift.value]
        }
    }
}
