// port-lint: source lr1/build_lalr/mod.rs
//! Mega naive LALR(1) generation algorithm.
package io.github.kotlinmania.lalrpop.lr1.buildlalr

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.multimap.Multimap
import io.github.kotlinmania.lalrpop.collections.multimap.SetCollection
import io.github.kotlinmania.lalrpop.collections.map.ComparableList
import io.github.kotlinmania.lalrpop.collections.map.map
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.lr1.Nil
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.build.buildLr1States
import io.github.kotlinmania.lalrpop.lr1.build.useLaneTable
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.Items
import io.github.kotlinmania.lalrpop.lr1.core.State
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.core.TableConstructionError
import io.github.kotlinmania.lalrpop.lr1.build.TableConstructionErrorException

// Intermediate LALR(1) state. Identical to an LR(1) state, but that
// the items can be pushed to. We initially create these with an empty
// set of actions, as well.
private class Lalr1State(
    var index: StateIndex,
    var items: MutableList<Item<TokenSet>>,
    var shifts: Map<TerminalString, StateIndex>,
    var reductions: Multimap<Production, SetCollection<TokenSet>, TokenSet>,
    var gotos: Map<NonterminalString, StateIndex>,
)

fun buildLalrStates(grammar: Grammar, start: NonterminalString): MutableList<State<TokenSet>> {
    // First build the LR(1) states
    val lrStates = buildLr1States(grammar, start)

    // With lane table, there is no reason to do state collapse
    // for LALR. In fact, LALR is pointless!
    if (useLaneTable()) {
        println("Warning: Now that the new lane-table algorithm is the default,")
        println("         #[LALR] mode has no effect and can be removed.")
        return lrStates
    }

    // profile! { &Tls::session(), "LALR(1) state collapse", collapseToLalrStates(&lrStates) }
    return collapseToLalrStates(lrStates)
}

fun collapseToLalrStates(lrStates: List<State<TokenSet>>): MutableList<State<TokenSet>> {
    // Now compress them. This vector stores, for each state, the
    // LALR(1) state to which we will remap it.
    val remap: MutableList<StateIndex> = MutableList(lrStates.size) { StateIndex(0) }
    // Upstream: `BTreeMap<Vec<Item<Nil>>, StateIndex>` (BTreeMap with
    // auto-derived `Ord` on `Vec<T>`). We wrap the kernel in a
    // [ComparableList] so the Kotlin BTreeMap orders by the same
    // lexicographic compare Rust derives.
    val lalr1Map: Map<ComparableList<Item<Nil>>, StateIndex> = map()
    val lalr1States: MutableList<Lalr1State> = mutableListOf()

    for ((lr1Index, lr1State) in lrStates.withIndex()) {
        val lr0Kernel: ComparableList<Item<Nil>> = ComparableList(
            lr1State.items.vec
                .map { it.toLr0() }
                .dedup()
        )

        val lalr1Index = lalr1Map.getOrPut(lr0Kernel) {
            val index = StateIndex(lalr1States.size)
            lalr1States.add(
                Lalr1State(
                    index = index,
                    items = mutableListOf(),
                    shifts = map(),
                    reductions = Multimap(collectionFactory = { SetCollection() }),
                    gotos = map(),
                )
            )
            index
        }

        lalr1States[lalr1Index.value].items.addAll(lr1State.items.vec)

        remap[lr1Index] = lalr1Index
    }

    // The reduction process can leave us with multiple
    // overlapping LR(0) items, whose lookaheads must be
    // unioned. e.g. we may now have:
    //
    //     X = "(" (*) ")" ["Foo"]
    //     X = "(" (*) ")" ["Bar"]
    //
    // which we will convert to:
    //
    //     X = "(" (*) ")" ["Foo", "Bar"]
    for (lalr1State in lalr1States) {
        val items = lalr1State.items
        lalr1State.items = mutableListOf()

        val itemsMap: Multimap<Item<Nil>, UnionTokenSetCollection, TokenSet> =
            Multimap(collectionFactory = { UnionTokenSetCollection() })
        for (item in items) {
            itemsMap.push(Item.lr0(item.production, item.index), item.lookahead)
        }

        val newItems: MutableList<Item<TokenSet>> = mutableListOf()
        for ((lr0Item, coll) in itemsMap) {
            newItems.add(lr0Item.withLookahead(coll.value ?: TokenSet.new()))
        }
        lalr1State.items = newItems
    }

    // Now that items are fully built, create the actions
    for ((lr1Index, lr1State) in lrStates.withIndex()) {
        val lalr1Index = remap[lr1Index]
        val lalr1State = lalr1States[lalr1Index.value]

        for ((terminal, lr1TargetState) in lr1State.shifts) {
            val targetState = remap[lr1TargetState.value]
            val prev = lalr1State.shifts.put(terminal, targetState)
            check((prev ?: targetState) == targetState)
        }

        for ((nt, lr1TargetState) in lr1State.gotos) {
            val targetState = remap[lr1TargetState.value]
            val prev = lalr1State.gotos.put(nt, targetState)
            check((prev ?: targetState) == targetState) // as above
        }

        for ((tokenSet, production) in lr1State.reductions) {
            lalr1State.reductions.push(production, tokenSet)
        }
    }

    // Finally, create the new states and detect conflicts
    val lr1StatesOut: MutableList<State<TokenSet>> = lalr1States.map { lr ->
        val reductions: MutableList<Pair<TokenSet, Production>> = mutableListOf()
        for ((p, tsColl) in lr.reductions) {
            // Flatten: each production maps to one TokenSet (union of pushed tokens),
            // recorded as (TokenSet, Production) pairs in the state.
            for (ts in tsColl.asSet()) {
                reductions.add(Pair(ts, p))
            }
        }
        State(
            index = lr.index,
            items = Items(vec = lr.items.toMutableList()),
            shifts = lr.shifts,
            reductions = reductions,
            gotos = lr.gotos,
        )
    }.toMutableList()

    val conflicts = lr1StatesOut.flatMap { TokenSet.conflicts(it) }.toMutableList()

    return if (conflicts.isNotEmpty()) {
        throw TableConstructionErrorException(
            TableConstructionError(
                states = lr1StatesOut,
                conflicts = conflicts,
            )
        )
    } else {
        lr1StatesOut
    }
}

private fun <T> Iterable<T>.dedup(): List<T> {
    val out = mutableListOf<T>()
    for (t in this) {
        if (out.isEmpty() || out.last() != t) out.add(t)
    }
    return out
}

// Collection variant that keeps a rolling union of TokenSets — used to
// merge per-Item<Nil> lookaheads across lalr1 compression.
private class UnionTokenSetCollection(
    var value: TokenSet? = null,
) : io.github.kotlinmania.lalrpop.collections.multimap.Collection<TokenSet> {
    override fun push(item: TokenSet): Boolean {
        val current = value
        return if (current == null) {
            value = item.clone()
            true
        } else {
            current.unionWith(item)
        }
    }
}
