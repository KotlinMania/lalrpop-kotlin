// port-lint: source src/lr1/trace/reduce/mod.rs
package io.github.kotlinmania.lalrpop_kotlin.lr1.trace.reduce

import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.Lr0Item
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop_kotlin.lr1.trace.Tracer
import io.github.kotlinmania.lalrpop_kotlin.lr1.trace.traceGraph.TraceGraph
import io.github.kotlinmania.lalrpop_kotlin.lr1.trace.traceGraph.TraceGraphNode

// mod test

fun Tracer.backtraceReduce(
    itemState: StateIndex,
    item: Lr0Item,
): TraceGraph {
    traceReduceItem(itemState, item)
    return this.traceGraph
}

private fun Tracer.traceReduceItem(itemState: StateIndex, item: Lr0Item) {
    // We start out with an item
    //
    //     X = ...p (*) ...s
    //
    // which we can (eventually) reduce, though we may have to do
    // some epsilon reductions first if ...s is non-empty. We want
    // to trace back until we have (at least) one element of
    // context for the reduction.
    val nonterminal = item.production.nonterminal // X

    // Add an edge
    //
    //     [X] -{...p,_,...s}-> [X = ...p (*) ...s]
    //
    // because to reach that item we pushed `...p` from the start
    // of `X` and afterwards we expect to see `...s`.
    this.traceGraph.addEdge(
        TraceGraphNode.from(nonterminal),
        TraceGraphNode.from(item),
        item.symbolSets(),
    )

    // Walk back to the set of states S where we had:
    //
    //     X = (*) ...p
    val predStates = this.stateGraph.traceBack(itemState, item.prefix())

    // Add in edges from [X] to all the places [X] can be consumed.
    for (predState in predStates) {
        traceReduceFromState(predState, nonterminal)
    }
}

// We know that we can reduce the nonterminal `Y`. We want to find
// at least one element of context, so we search back to find out
// who will consume that reduced value. So search for those items
// that can shift a `Y`:
//
//     Z = ... (*) Y ...s
//
// If we find that `...s` is potentially empty, then we haven't
// actually found any context, and so we may have to keep
// searching.
private fun Tracer.traceReduceFromState(
    itemState: StateIndex,
    nonterminal: NonterminalString,
) // "Y"
{
    if (!this.visitedSet.add(Pair(itemState, nonterminal))) {
        return
    }
    for (predItem in this.states[itemState.value].items.vec.filter { it.canShiftNonterminal(nonterminal) }) {
        // Found a state:
        //
        //     Z = ...p (*) Y ...s
        //
        // If `...s` does not match `\epsilon`, then we are done,
        // because `FIRST(...s)` will provide a token of context.
        // But otherwise we have to keep searching backwards.

        val symbolSets = predItem.symbolSets()

        val firstSuffix = this.firstSets.first0(symbolSets.suffix)
        val continueTracing = firstSuffix.containsEof()

        if (!continueTracing) {
            // Add an edge
            //
            //    [Z = ...p (*) Y ...s] -(...p,Y,...s)-> [Y]
            //
            // and stop.
            this.traceGraph.addEdge(
                TraceGraphNode.from(predItem),
                TraceGraphNode.from(nonterminal),
                symbolSets,
            )
        } else {
            // Add an edge
            //
            //    [Z] -{..p}-> [Y]
            //
            // because we can reduce by consuming `...p`
            // tokens, and continue tracing.
            this.traceGraph.addEdge(
                TraceGraphNode.from(predItem.production.nonterminal),
                TraceGraphNode.from(nonterminal),
                symbolSets,
            )

            traceReduceItem(itemState, predItem.toLr0())
        }
    }
}
