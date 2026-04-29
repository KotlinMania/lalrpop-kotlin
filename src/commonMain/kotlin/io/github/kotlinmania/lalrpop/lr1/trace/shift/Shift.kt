// port-lint: source lr1/trace/shift/mod.rs
package io.github.kotlinmania.lalrpop.lr1.trace.shift

import io.github.kotlinmania.lalrpop.collections.map.ComparablePair
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.core.Item<Nil>
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.trace.Tracer
import io.github.kotlinmania.lalrpop.lr1.trace.tracegraph.TraceGraph
import io.github.kotlinmania.lalrpop.lr1.trace.tracegraph.TraceGraphNode
import io.github.kotlinmania.lalrpop.lr1.Token

/**
 * A backtrace explaining how a particular shift:
 *
 * X = ...p (*) Token ...
 *
 * came to be in the list of items for some state S. This backtrace
 * always has a particular form. First, we can walk back over the
 * prefix, which will bring us to some set of states S1 all of which
 * contain the same item, but with the cursor at the front:
 *
 * X = (*) ...p Token ...
 *
 * Then we can walk back within those states some number of epsilon
 * moves, traversing nonterminals of the form:
 *
 * Y = (*) X ...s
 *
 * (Note that each nonterminal `Y` may potentially have many
 * productions of this form. I am not sure yet if they all matter or
 * not.)
 *
 * Finally, either we are in the start state, or else we reach some
 * production of the form:
 *
 * Z = ...p (*) Y ...s
 *
 * Ultimately this "trace" is best represented as a DAG. The problem
 * is that some of those nonterminals could, for example, be
 * optional.
 */
fun Tracer.backtraceShift(
    itemState: StateIndex,
    item: Item<Nil>,
): TraceGraph {
    val symbolSets = item.symbolSets()

    // The states `S`
    val predStates = this.stateGraph.traceBack(itemState, symbolSets.prefix)

    // Add the edge `[X] -{...p,Token,...s}-> [X = ...p (*) Token ...s]`
    this.traceGraph.addEdge(
        TraceGraphNode.from(item.production.nonterminal),
        TraceGraphNode.from(item),
        symbolSets,
    )

    for (predState in predStates) {
        traceEpsilonEdges(predState, item.production.nonterminal)
    }

    return this.traceGraph
}

// Because item.index is 0, we know we are at an index
// like:
//
//     Y = (*) ...
//
// This can only arise if `Y` is the start nonterminal
// or if there is an epsilon move from another item
// like:
//
//     Z = ...p (*) Y ...
//
// So search for items like Z.
private fun Tracer.traceEpsilonEdges(itemState: StateIndex, nonterminal: NonterminalString) // "Y"
{
    if (this.visitedSet.add(ComparablePair(itemState, nonterminal))) {
        for (predItem in this.states[itemState.value].items.vec) {
            if (predItem.canShiftNonterminal(nonterminal)) {
                if (predItem.index > 0) {
                    // Add an edge:
                    //
                    //     [Z = ...p (*) Y ...s] -(...p,Y,...s)-> [Y]
                    this.traceGraph.addEdge(
                        TraceGraphNode.from(predItem),
                        TraceGraphNode.from(nonterminal),
                        predItem.symbolSets(),
                    )
                } else {
                    // Trace back any incoming edges to [Z = ...p (*) Y ...].
                    val predNonterminal = predItem.production.nonterminal
                    this.traceGraph.addEdge(
                        TraceGraphNode.from(predNonterminal),
                        TraceGraphNode.from(nonterminal),
                        predItem.symbolSets(),
                    )
                    traceEpsilonEdges(itemState, predNonterminal)
                }
            }
        }
    }
}
