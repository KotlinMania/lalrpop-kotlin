// transliterated from upstream module root
package io.github.kotlinmania.lalrpop.normalize.inline.graph

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.Graph
import io.github.kotlinmania.lalrpop.NodeIndex
import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.grammar.INLINE
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.normalize.returnErr

/**
 * Computes the proper order to inline the various nonterminals in
 * `grammar`. Reports an error if there is an inline
 * cycle. Otherwise, yields an ordering such that we inline X before
 * Y if Y references X.  I actually think it does not matter what
 * order we do the inlining, really, but this order seems better
 * somehow. :) (That is, inline into something before we inline it.)
 */
fun inlineOrder(grammar: Grammar): List<NonterminalString> {
    val graph = NonterminalGraph.new(grammar)
    graph.createNodes()
    graph.addEdges()
    return graph.inlineOrder()
}

private class NonterminalGraph(
    val grammar: Grammar,
    val graph: Graph<NonterminalString, Unit>,
    val nonterminalMap: Map<NonterminalString, NodeIndex>,
) {
    companion object {
        fun new(grammar: Grammar): NonterminalGraph = NonterminalGraph(
            grammar = grammar,
            graph = Graph(),
            nonterminalMap = map(),
        )
    }
}

private enum class WalkState {
    NotVisited,
    Visiting,
    Visited,
}

private fun NonterminalGraph.createNodes() {
    val inline = Atom.from(INLINE)
    for ((name, data) in this.grammar.nonterminals) {
        if (data.attributes.any { a -> a.id == inline }) {
            val index = this.graph.addNode(name)
            this.nonterminalMap[name] = index
        }
    }
}

private fun NonterminalGraph.addEdges() {
    for (production in this.grammar.nonterminals.values.flatMap { d -> d.productions }) {
        val fromIndex = this.nonterminalMap[production.nonterminal]
            ?: continue // this is not an inlined nonterminal

        for (symbol in production.symbols) {
            when (symbol) {
                is Symbol.Nonterminal -> {
                    val toIndex = this.nonterminalMap[symbol.nt]
                    if (toIndex != null) {
                        this.graph.addEdge(fromIndex, toIndex, Unit)
                    }
                }
                is Symbol.Terminal -> {}
            }
        }
    }
}

private fun NonterminalGraph.inlineOrder(): List<NonterminalString> {
    val states: MutableList<WalkState> = MutableList(this.graph.nodeCount()) { WalkState.NotVisited }
    val result: MutableList<NonterminalString> = mutableListOf()
    for (node in this.nonterminalMap.values.toList()) {
        this.walk(states, result, node)
    }
    return result
}

private fun NonterminalGraph.walk(
    states: MutableList<WalkState>,
    result: MutableList<NonterminalString>,
    source: NodeIndex,
) {
    val nt = this.graph.nodeWeight(source)!!

    when (states[source.index()]) {
        WalkState.NotVisited -> {
            states[source.index()] = WalkState.Visiting
            for (target in this.graph.neighbors(source)) {
                this.walk(states, result, target)
            }
            states[source.index()] = WalkState.Visited
            result.add(nt)
        }
        WalkState.Visited -> {}
        WalkState.Visiting -> {
            returnErr(
                this.grammar.nonterminals.getValue(nt).span,
                "cyclic inline directive: `$nt` would have to be inlined into itself",
            )
        }
    }
}
