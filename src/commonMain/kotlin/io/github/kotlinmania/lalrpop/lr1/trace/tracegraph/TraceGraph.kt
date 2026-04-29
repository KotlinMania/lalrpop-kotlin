// transliterated from upstream module root
package io.github.kotlinmania.lalrpop.lr1.trace.tracegraph

import io.github.kotlinmania.lalrpop.EdgeDirection
import io.github.kotlinmania.lalrpop.EdgeRef
import io.github.kotlinmania.lalrpop.Graph
import io.github.kotlinmania.lalrpop.NodeIndex
import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.Lookahead
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.SymbolSets
import io.github.kotlinmania.lalrpop.lr1.example.Example
import io.github.kotlinmania.lalrpop.lr1.example.ExampleSymbol
import io.github.kotlinmania.lalrpop.lr1.example.Reduction
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets

/**
 * Trace graphs are used to summarize how it is that we came to be in
 * a state where we can take some particular shift/reduce action; put
 * another way, how it is that we came to be in a state with some
 * particular LR(1) item.
 *
 * The nodes in the graph are each labeled with a TraceGraphNode and
 * hence take one of two forms:
 *
 * - TraceGraphNode::Item -- represents an LR0 item. These nodes are
 * used for the starting/end points in the graph only.  Basically a
 * complete trace stretches from the start item to some end item,
 * and all intermediate nodes are nonterminals.
 * - TraceGraphNode::Nonterminal -- if this graph is for a shift,
 * then these represent items where the cursor is at the beginning:
 * `X = (*) ...`. If the graph is for a reduce, they represent
 * items where a reduce is possible without shifting any more
 * terminals (though further reductions may be needed): `X =
 * ... (*) ...s` where `FIRST(...s)` includes `\epsilon`.
 *
 * The edges in the graph are also important. They are labeled with
 * `SymbolSets` instances, meaning that each carries a (prefix,
 * cursor, and suffix) tuple. The label on an edge `A -> B` means
 * that transitioning from a state containing `A` to a state
 * containing `B` is possible if you:
 *
 * - shift the symbols in `prefix`
 * - `B` will produce the symbol in `cursor`
 * - shift the symbols in `suffix` after `B` is popped
 */
class TraceGraph(
    // A -L-> B means:
    //
    //     Transition from a state containing A to a state containing
    //     B by (pushing|popping) the symbols L.
    //
    // If this trace graph represents a shift backtrace, then the
    // labels are symbols that are pushed. Otherwise they are labels
    // that are popped.
    internal val graph: Graph<TraceGraphNode, SymbolSets>,
    internal val indices: Map<TraceGraphNode, NodeIndex>,
) {
    companion object {
        fun new(): TraceGraph = TraceGraph(
            graph = Graph(),
            indices = map(),
        )
    }

    fun addNode(node: TraceGraphNode): NodeIndex {
        val graph = this.graph
        return this.indices.getOrPut(node) { graph.addNode(node) }
    }

    fun addEdge(from: TraceGraphNode, to: TraceGraphNode, labels: SymbolSets) {
        val fromIdx = this.addNode(from)
        val toIdx = this.addNode(to)
        if (!this.graph.edgesDirected(fromIdx, EdgeDirection.Outgoing)
                .any { edge -> edge.target() == toIdx && edge.weight() == labels }
        ) {
            this.graph.addEdge(fromIdx, toIdx, labels)
        }
    }

    fun lr0Examples(lr0Item: Item<Nil>): PathEnumerator =
        PathEnumerator.new(this, lr0Item)

    fun lr1Examples(firstSets: FirstSets, item: Item<TokenSet>): FilteredPathEnumerator =
        FilteredPathEnumerator.new(firstSets, this, item.toLr0(), item.lookahead.clone())

    override fun toString(): String {
        val sb = StringBuilder("[")
        var first = true
        for ((node, index) in this.indices) {
            for (edge in this.graph.edgesDirected(index, EdgeDirection.Outgoing)) {
                val label = edge.weight()
                if (!first) sb.append(", ")
                first = false
                sb.append(
                    TraceGraphEdge(
                        from = node,
                        to = this.graph[edge.target()],
                        label = Triple(label.prefix, label.cursor, label.suffix),
                    ).toString()
                )
            }
        }
        sb.append("]")
        return sb.toString()
    }
}

sealed class TraceGraphNode : Comparable<TraceGraphNode> {
    data class Nonterminal(val nonterminal: NonterminalString) : TraceGraphNode()
    data class ItemNode(val item: Item<Nil>) : TraceGraphNode()

    override fun compareTo(other: TraceGraphNode): Int {
        val o1 = ordinal()
        val o2 = other.ordinal()
        if (o1 != o2) return o1 - o2
        return when (this) {
            is Nonterminal -> this.nonterminal.compareTo((other as Nonterminal).nonterminal)
            is ItemNode -> this.item.compareTo((other as ItemNode).item)
        }
    }

    private fun ordinal(): Int = when (this) {
        is Nonterminal -> 0
        is ItemNode -> 1
    }

    companion object {
        fun from(value: NonterminalString): TraceGraphNode = Nonterminal(value)
        fun <L : Lookahead<L>> from(value: Item<L>): TraceGraphNode = ItemNode(value.toLr0())
    }
}

// This just exists to help with the `Debug` implementation
private data class TraceGraphEdge(
    val from: TraceGraphNode,
    val to: TraceGraphNode,
    val label: Triple<List<io.github.kotlinmania.lalrpop.grammar.repr.Symbol>,
        io.github.kotlinmania.lalrpop.grammar.repr.Symbol?,
        List<io.github.kotlinmania.lalrpop.grammar.repr.Symbol>>,
) {
    override fun toString(): String = "($from -$label-> $to)"
}

/** //////////////////////////////////////////////////////////////////////// */
// PathEnumerator
//
// The path enumerator walks a trace graph searching for paths that
// start at a given item and terminate at another item. If such a path
// is found, you can then find the complete list of symbols by calling
// `symbolsAndCursor` and also get access to the state.

class PathEnumerator internal constructor(
    internal val graph: TraceGraph,
    internal val stack: MutableList<EnumeratorState>,
) : Iterator<Example> {
    companion object {
        internal fun new(graph: TraceGraph, lr0Item: Item<Nil>): PathEnumerator {
            val startState = graph.indices[TraceGraphNode.ItemNode(lr0Item)]!!
            val enumerator = PathEnumerator(graph = graph, stack = mutableListOf())
            val edges = enumerator.incomingEdges(startState).iterator()
            enumerator.stack.add(
                EnumeratorState(
                    index = startState,
                    symbolSets = SymbolSets.new(),
                    edges = edges,
                )
            )
            enumerator.findNextTrace()
            return enumerator
        }
    }

    /**
     * Advance to the next example. Returns false if there are no more
     * examples.
     */
    fun advance(): Boolean {
        // If we have not yet exhausted all the examples, then the top
        // of the stack should be the last target item that we
        // found. Pop it off.
        val topState = this.stack.removeLastOrNull()
        return if (topState != null) {
            check(this.graph.graph[topState.index] is TraceGraphNode.ItemNode)

            findNextTrace()
        } else {
            false
        }
    }

    internal fun incomingEdges(index: NodeIndex): Iterable<EdgeRef<SymbolSets>> =
        this.graph.graph.edgesDirected(index, EdgeDirection.Incoming)

    /**
     * This is the main operation, written in CPS style and hence it
     * can seem a bit confusing. The idea is that `findNextTrace`
     * is called when we are ready to consider the next child of
     * whatever is on the top of the stack. It simply withdraws
     * that next child (if any) and hands it to `pushNext`.
     */
    private fun findNextTrace(): Boolean {
        return if (this.stack.isNotEmpty()) {
            val topOfStack = this.stack.last()
            val nextEdge = if (topOfStack.edges.hasNext()) topOfStack.edges.next() else null
            pushNextChildIfAny(nextEdge)
        } else {
            false
        }
    }

    /**
     * Invoked with the next child (if any) of the node on the top of
     * the stack.
     *
     * If `next` is not null, we simply call `pushNextChild`.
     *
     * If `next` is null, then the node on the top of
     * the stack *has* no next child, and so it is popped, and then
     * we call `findNextTrace` again to start with the next child
     * of the new top of the stack.
     */
    private fun pushNextChildIfAny(next: EdgeRef<SymbolSets>?): Boolean {
        return if (next != null) {
            val index = next.source()
            val symbolSets = next.weight()
            pushNextChild(index, symbolSets)
        } else {
            this.stack.removeLast()
            findNextTrace()
        }
    }

    /**
     * Push the next child of the top of the stack onto the stack,
     * making the child the new top.
     *
     * If the child is an `Item` node, we have found the next trace,
     * and hence our search terminates. We push the symbols from this
     * item node into the symbols vector and then return true.
     *
     * Otherwise, we check whether this new node would cause a cycle.
     * If so, we do *not* push it, and instead just call
     * `findNextTrace` again to proceed to the next child of the
     * current top.
     *
     * Finally, if the new node would NOT cause a cycle, then we can
     * push it onto the stack so that it becomes the new top, and
     * call `findNextTrace` to start searching its children.
     */
    private fun pushNextChild(index: NodeIndex, symbolSets: SymbolSets): Boolean {
        return when (this.graph.graph[index]) {
            is TraceGraphNode.ItemNode -> {
                // If we reached an item like
                //
                //     X = ...p (*) ...s
                //
                // then we are done, but we still need to push on the
                // symbols `...p`.
                val edges = incomingEdges(index).iterator()
                this.stack.add(
                    EnumeratorState(
                        index = index,
                        symbolSets = symbolSets,
                        edges = edges,
                    )
                )
                true
            }
            is TraceGraphNode.Nonterminal -> {
                // If this node already appears on the stack, do not
                // visit its children.
                if (this.stack.none { state -> state.index == index }) {
                    val edges = incomingEdges(index).iterator()
                    this.stack.add(
                        EnumeratorState(
                            index = index,
                            symbolSets = symbolSets,
                            edges = edges,
                        )
                    )
                }
                findNextTrace()
            }
        }
    }

    fun foundTrace(): Boolean = this.stack.isNotEmpty()

    /**
     * Returns the 1-context for the current trace. In other words,
     * the set of tokens that may appear next in the input. If this
     * trace was derived from a shiftable item, this will always be
     * the terminal that was to be shifted; if derived from a reduce
     * item, this constitutes the set of lookaheads that will trigger
     * a reduce.
     */
    fun first0(firstSets: FirstSets): TokenSet {
        check(foundTrace())
        val syms: MutableList<io.github.kotlinmania.lalrpop.grammar.repr.Symbol> =
            mutableListOf()
        this.stack[1].symbolSets.cursor?.let { syms.add(it) }
        for (s in this.stack) {
            syms.addAll(s.symbolSets.suffix)
        }
        return firstSets.first0(syms)
    }

    fun example(): Example {
        check(foundTrace())

        val symbols: MutableList<ExampleSymbol> = mutableListOf()

        for (s in this.stack.asReversed()) {
            for (sym in s.symbolSets.prefix) {
                symbols.add(ExampleSymbol.SymbolValue(sym))
            }
        }

        val cursor = symbols.size

        val cursorSym = this.stack[1].symbolSets.cursor
        if (cursorSym != null) {
            symbols.add(ExampleSymbol.SymbolValue(cursorSym))
        } else {
            if (this.stack[1].symbolSets.prefix.isEmpty()) {
                symbols.add(ExampleSymbol.Epsilon)
            }
        }

        for (s in this.stack) {
            for (sym in s.symbolSets.suffix) {
                symbols.add(ExampleSymbol.SymbolValue(sym))
            }
        }

        var cursorStart = 0
        var cursorEnd = symbols.size

        val reductions: MutableList<Reduction> = mutableListOf()
        for (state in this.stack.subList(1, this.stack.size).asReversed()) {
            val nonterminal = when (val node = this.graph.graph[state.index]) {
                is TraceGraphNode.Nonterminal -> node.nonterminal
                is TraceGraphNode.ItemNode -> node.item.production.nonterminal
            }
            val reduction = Reduction(
                start = cursorStart,
                end = cursorEnd,
                nonterminal = nonterminal,
            )
            cursorStart += state.symbolSets.prefix.size
            cursorEnd -= state.symbolSets.suffix.size
            reductions.add(reduction)
        }
        reductions.reverse()

        return Example(
            symbols = symbols,
            cursor = cursor,
            reductions = reductions,
        )
    }

    override fun hasNext(): Boolean = foundTrace()

    override fun next(): Example {
        if (foundTrace()) {
            val example = example()
            advance()
            return example
        } else {
            throw NoSuchElementException()
        }
    }
}

internal class EnumeratorState(
    val index: NodeIndex,
    val symbolSets: SymbolSets,
    val edges: Iterator<EdgeRef<SymbolSets>>,
)

/** //////////////////////////////////////////////////////////////////////// */
// FilteredPathEnumerator
//
// Like the path enumerator, but tests for examples with some specific
// lookahead

class FilteredPathEnumerator internal constructor(
    private val base: PathEnumerator,
    private val firstSets: FirstSets,
    private val lookahead: TokenSet,
) : Iterator<Example> {
    companion object {
        internal fun new(
            firstSets: FirstSets,
            graph: TraceGraph,
            lr0Item: Item<Nil>,
            lookahead: TokenSet,
        ): FilteredPathEnumerator = FilteredPathEnumerator(
            base = PathEnumerator.new(graph, lr0Item),
            firstSets = firstSets,
            lookahead = lookahead,
        )
    }

    private var cached: Example? = null

    override fun hasNext(): Boolean {
        if (cached != null) return true
        while (base.foundTrace()) {
            val firsts = base.first0(firstSets)
            if (firsts.isIntersecting(lookahead)) {
                cached = base.example()
                base.advance()
                return true
            }
            base.advance()
        }
        return false
    }

    override fun next(): Example {
        if (!hasNext()) throw NoSuchElementException()
        val e = cached!!
        cached = null
        return e
    }
}
