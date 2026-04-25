// port-lint: source src/lr1/state_graph.rs
package io.github.kotlinmania.lalrpop_kotlin.lr1.stateGraph

import io.github.kotlinmania.lalrpop_kotlin.EdgeDirection
import io.github.kotlinmania.lalrpop_kotlin.Graph
import io.github.kotlinmania.lalrpop_kotlin.NodeIndex
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.State
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop_kotlin.lr1.lookahead.Lookahead

// Each state `s` corresponds to the node in the graph with index
// `s`. The edges are the shift transitions.
class StateGraph(
    private val graph: Graph<Unit, Symbol>,
) {
    companion object {
        fun <L : Lookahead<L>> new(states: List<State<L>>): StateGraph {
            val nodes = states.size
            val edges = states.sumOf { it.shifts.size + it.gotos.size }
            val graph: Graph<Unit, Symbol> = Graph.withCapacity(nodes, edges)

            // First, create the nodes.
            for (i in states.indices) {
                val j = graph.addNode(Unit)
                check(i == j.index())
            }

            // Add in the edges.
            for ((i, state) in states.withIndex()) {
                // Successors of a node arise from:
                // - shifts (found in the `conflicts` and `tokens` maps)
                // - gotos (found in the `gotos` map)
                val shifts = state.shifts.entries.map { (terminal, s) ->
                    Pair(Symbol.Terminal(terminal), s)
                }
                val gotos = state.gotos.entries.map { (nt, s) ->
                    Pair(Symbol.Nonterminal(nt), s)
                }
                val it = (shifts + gotos).map { (symbol, successor) ->
                    Triple(NodeIndex.new(i), NodeIndex.new(successor.value), symbol)
                }
                graph.extendWithEdges(it)
            }

            return StateGraph(graph)
        }
    }

    /// Given a list of symbols `[X, Y, Z]`, traces back from
    /// `initial_state_index` to find the set of states whence we
    /// could have arrived at `initial_state_index` after pushing `X`,
    /// `Y`, and `Z`.
    fun traceBack(
        initialStateIndex: StateIndex,
        initialSymbols: List<Symbol>,
    ): MutableList<StateIndex> {
        val stack: MutableList<Pair<StateIndex, List<Symbol>>> = mutableListOf(
            Pair(initialStateIndex, initialSymbols),
        )
        val result: MutableList<StateIndex> = mutableListOf()
        while (stack.isNotEmpty()) {
            val (stateIndex, symbols) = stack.removeLast()
            if (symbols.isNotEmpty()) {
                val head = symbols.last()
                val tail = symbols.subList(0, symbols.size - 1)
                stack.addAll(
                    graph.edgesDirected(NodeIndex.new(stateIndex.value), EdgeDirection.Incoming)
                        .filter { edge -> edge.weight() == head }
                        .map { edge -> Pair(StateIndex(edge.source().index()), tail) },
                )
            } else {
                result.add(stateIndex)
            }
        }
        result.sort()
        // dedup (assumes sorted)
        val deduped: MutableList<StateIndex> = mutableListOf()
        for (s in result) {
            if (deduped.isEmpty() || deduped.last() != s) deduped.add(s)
        }
        return deduped
    }

    fun successors(stateIndex: StateIndex): Sequence<StateIndex> =
        graph.edgesDirected(NodeIndex.new(stateIndex.value), EdgeDirection.Outgoing)
            .asSequence()
            .map { edge -> StateIndex(edge.target().index()) }

    fun predecessors(stateIndex: StateIndex, symbol: Symbol): Sequence<StateIndex> =
        graph.edgesDirected(NodeIndex.new(stateIndex.value), EdgeDirection.Incoming)
            .asSequence()
            .filter { edge -> edge.weight() == symbol }
            .map { edge -> StateIndex(edge.source().index()) }
}
