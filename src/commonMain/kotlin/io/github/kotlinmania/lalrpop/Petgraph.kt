// port-lint: source external/petgraph/graph
// Minimal port of `petgraph::graph::Graph` — directed graph with
// node indices and typed edge weights. Only the methods referenced
// from LALRPOP `stateGraph` are provided.
package io.github.kotlinmania.lalrpop

internal data class NodeIndex(private val value: Int) : Comparable<NodeIndex> {
    fun index(): Int = value
    override fun compareTo(other: NodeIndex): Int = value.compareTo(other.value)

    companion object {
        fun new(value: Int): NodeIndex = NodeIndex(value)
    }
}

internal enum class EdgeDirection {
    Incoming,
    Outgoing,
}

internal class EdgeRef<E> internal constructor(
    val source: NodeIndex,
    val target: NodeIndex,
    val weight: E,
)

internal class Graph<N, E>(
    nodesCapacity: Int = 0,
    edgesCapacity: Int = 0,
) {
    private val nodes: MutableList<N> = ArrayList(nodesCapacity)
    private val outgoing: MutableList<MutableList<EdgeRef<E>>> = ArrayList(nodesCapacity)
    private val incoming: MutableList<MutableList<EdgeRef<E>>> = ArrayList(nodesCapacity)

    fun addNode(weight: N): NodeIndex {
        val idx = NodeIndex.new(nodes.size)
        nodes.add(weight)
        outgoing.add(mutableListOf())
        incoming.add(mutableListOf())
        return idx
    }

    fun addEdge(source: NodeIndex, target: NodeIndex, weight: E) {
        val edge = EdgeRef(source, target, weight)
        outgoing[source.index()].add(edge)
        incoming[target.index()].add(edge)
    }

    fun extendWithEdges(edges: Iterable<Triple<NodeIndex, NodeIndex, E>>) {
        for ((s, t, w) in edges) addEdge(s, t, w)
    }

    operator fun get(node: NodeIndex): N = nodes[node.index()]

    fun nodeCount(): Int = nodes.size

    fun nodeWeight(node: NodeIndex): N? =
        if (node.index() in nodes.indices) nodes[node.index()] else null

    fun neighbors(source: NodeIndex): Iterable<NodeIndex> =
        outgoing[source.index()].map { it.target }

    fun edgesDirected(node: NodeIndex, direction: EdgeDirection): Iterable<EdgeRef<E>> =
        when (direction) {
            EdgeDirection.Outgoing -> outgoing[node.index()].asReversed()
            EdgeDirection.Incoming -> incoming[node.index()].asReversed()
        }

    companion object {
        fun <N, E> withCapacity(nodes: Int, edges: Int): Graph<N, E> =
            Graph(nodes, edges)
    }
}
