// port-lint: source lr1/trace/mod.rs
package io.github.kotlinmania.lalrpop.lr1.trace

import io.github.kotlinmania.lalrpop.collections.ComparablePair
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.Lr1State
import io.github.kotlinmania.lalrpop.lr1.StateGraph
import io.github.kotlinmania.lalrpop.lr1.StateIndex
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.trace.tracegraph.TraceGraph

class Tracer(
    internal val states: List<Lr1State>,
    internal val firstSets: FirstSets,
    internal val stateGraph: StateGraph,
    internal var traceGraph: TraceGraph,
    internal val visitedSet: Set<ComparablePair<StateIndex, NonterminalString>>,
) {
    companion object {
        fun new(firstSets: FirstSets, states: List<Lr1State>): Tracer = Tracer(
            states = states,
            firstSets = firstSets,
            stateGraph = StateGraph.new(states),
            traceGraph = TraceGraph.new(),
            visitedSet = set(),
        )
    }
}
