// port-lint: source src/lr1/trace/mod.rs
package io.github.kotlinmania.lalrpop_kotlin.lr1.trace

import io.github.kotlinmania.lalrpop_kotlin.collections.set.Set
import io.github.kotlinmania.lalrpop_kotlin.collections.set.set
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop_kotlin.lr1.stateGraph.StateGraph
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.Lr1State
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop_kotlin.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop_kotlin.lr1.trace.traceGraph.TraceGraph

// mod reduce
// mod shift
// mod trace_graph

class Tracer(
    internal val states: List<Lr1State>,
    internal val firstSets: FirstSets,
    internal val stateGraph: StateGraph,
    internal var traceGraph: TraceGraph,
    internal val visitedSet: Set<Pair<StateIndex, NonterminalString>>,
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

// pub use self::trace_graph::TraceGraph
