// transliterated from upstream module root
/**
 * Code to trace out a single lane, collecting information into the
 * lane table as we go.
 */
package io.github.kotlinmania.lalrpop.lr1.lanetable.lane

import io.github.kotlinmania.lalrpop.collections.ComparablePair
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.Lookahead
import io.github.kotlinmania.lalrpop.lr1.StateGraph
import io.github.kotlinmania.lalrpop.lr1.Token
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.Action
import io.github.kotlinmania.lalrpop.lr1.Item
import io.github.kotlinmania.lalrpop.lr1.State
import io.github.kotlinmania.lalrpop.lr1.StateIndex
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.ConflictIndex
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.LaneTable

class LaneTracer<L : Lookahead<L>>(
    private val states: List<State<L>>,
    private val firstSets: FirstSets,
    private val stateGraph: StateGraph,
    private var table: LaneTable,
    private val startNt: NonterminalString,
) {
    companion object {
        fun <L : Lookahead<L>> new(
            grammar: Grammar,
            startNt: NonterminalString,
            states: List<State<L>>,
            firstSets: FirstSets,
            stateGraph: StateGraph,
            conflicts: Int,
        ): LaneTracer<L> = LaneTracer(
            states = states,
            firstSets = firstSets,
            stateGraph = stateGraph,
            startNt = startNt,
            table = LaneTable.new(grammar, conflicts),
        )
    }

    fun intoTable(): LaneTable = this.table

    fun startTrace(
        state: StateIndex,
        conflict: ConflictIndex,
        action: Action,
    ) {
        val visitedSet: Set<ComparablePair<StateIndex, Item<Nil>>> = set()

        // if the conflict item is a "shift" item, then the context
        // is always the terminal to shift (and conflicts only arise
        // around shifting terminal, so it must be a terminal)
        when (action) {
            is Action.Shift -> {
                val tokenSet = TokenSet.new()
                tokenSet.insert(Token.Terminal(action.terminal))
                this.table.addLookahead(state, conflict, tokenSet)
            }

            is Action.Reduce -> {
                val prod = action.production
                val item = Item.lr0(prod, prod.symbols.size)
                this.table.addLookahead(state, conflict, TokenSet.new())
                continueTrace(state, conflict, item, visitedSet)
            }
        }
    }

    private fun continueTrace(
        state: StateIndex,
        conflict: ConflictIndex,
        item: Item<Nil>,
        visited: Set<ComparablePair<StateIndex, Item<Nil>>>,
    ) {
        // debug("continueTrace:  state={:?}, index={:?}", state, item.index);
        if (!visited.add(ComparablePair(state, item))) {
            return
        }

        if (item.index > 0) {
            // This item was reached by shifting some symbol.  We need
            // to unshift that symbol, which means we walk backwards
            // to predecessors of `state` in the state graph.
            //
            // Example:
            //
            //     X = ...p T (*) ...s
            //
            // Here we would be "unshifting" T, which means we will
            // walk to predecessors of the current state that were
            // reached by shifting T. Those predecessors will contain
            // an item like `X = ...p (*) T ...s`, which we will then
            // process in turn.
            val unshiftedItem = Item(
                production = item.production,
                index = item.index - 1,
                lookahead = item.lookahead,
            )
            val shiftedSymbol = item.production.symbols[unshiftedItem.index]
            val predecessors = this.stateGraph.predecessors(state, shiftedSymbol)
            for (predecessor in predecessors) {
                this.table.addSuccessor(predecessor, state)
                continueTrace(predecessor, conflict, unshiftedItem, visited)
            }
            return
        }

        // Either: we are in the start state, or this item was
        // reached by an epsilon transition. We have to
        // "unepsilon", which means that we search elsewhere in
        // the state for where the epsilon transition could have
        // come from.
        //
        // Example:
        //
        //     X = (*) ...
        //
        // We will search for other items in the same state like:
        //
        //     Y = ...p (*) X ...s
        //
        // We can then insert `FIRST(...s)` as lookahead for
        // `conflict`. If `...s` may derive epsilon, though, we
        // have to recurse and search with the previous item.

        val stateItems = this.states[state.value].items.vec
        val nonterminal = item.production.nonterminal
        if (nonterminal == this.startNt) {
            // as a special case, if the `X` above is the special, synthetic
            // start-terminal, then the only thing that comes afterwards is EOF.
            this.table.addLookahead(state, conflict, TokenSet.eof())
        }

        // NB: Under the normal LR terms, the start nonterminal will
        // only have one production like `X' = X`, in which case this
        // loop is useless, but sometimes in tests we do not observe
        // that restriction, so do it anyway.
        for (predItem in stateItems.filter { it.canShiftNonterminal(nonterminal) }) {
            val symbolSets = predItem.symbolSets()
            val first = this.firstSets.first0(symbolSets.suffix)
            val derivesEpsilon = first.takeEof()
            this.table.addLookahead(state, conflict, first)
            if (derivesEpsilon) {
                continueTrace(state, conflict, predItem.toLr0(), visited)
            }
        }
    }
}
