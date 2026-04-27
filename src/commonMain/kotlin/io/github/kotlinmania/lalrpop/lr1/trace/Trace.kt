// port-lint: source src/lr1/trace/mod.rs
package io.github.kotlinmania.lalrpop.lr1.trace

import io.github.kotlinmania.lalrpop.collections.map.ComparablePair
import io.github.kotlinmania.lalrpop.collections.set.Set
import io.github.kotlinmania.lalrpop.collections.set.set
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.stateGraph.StateGraph
import io.github.kotlinmania.lalrpop.lr1.core.Lr1State
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.trace.traceGraph.TraceGraph

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
            // Upstream: `BTreeSet<(StateIndex, NonterminalString)>`
            // (auto-derived `Ord` on the tuple). [ComparablePair]
            // mirrors that derive so the BTreeSet orders entries the
            // same way Rust does.
            visitedSet = set(),
        )
    }
}
