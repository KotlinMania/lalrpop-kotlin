// transliterated from upstream module root
/** LR(1) state construction algorithm. */
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.KernelSet as KKernelSet
import io.github.kotlinmania.lalrpop.Kernel as KKernelInterface
import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.collections.Multimap
import io.github.kotlinmania.lalrpop.collections.VecCollection
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.lanetable.buildLaneTableStates
import io.github.kotlinmania.lalrpop.tls.Tls

internal typealias ConstructionFunction = (Grammar, NonterminalString) -> MutableList<State<TokenSet>>

internal typealias Index = StateIndex

private fun buildLr1StatesLegacy(
    grammar: Grammar,
    start: NonterminalString,
): MutableList<State<TokenSet>> {
    val eof = TokenSet.eof()
    val lr1: Lr<TokenSet> = Lr.new(grammar, start, eof)
    lr1.setPermitEarlyStop(true)
    return when (val outcome = lr1.buildStatesOrError()) {
        is BuildOutcome.Ok -> outcome.states
        is BuildOutcome.Err -> throw TableConstructionErrorException(outcome.error, outcome.error)
    }
}

/**
 * Mirrors the Rust `Result<Vec<State<L>>, TableConstructionError<L>>`
 * returned by `build_lr0_states` / `build_lr1_states`. Use this typed
 * accessor when the caller needs to inspect the typed
 * [TableConstructionError] payload without relying on a runtime cast
 * out of the erased [TableConstructionErrorException.inner].
 */
sealed class BuildOutcome<L : Lookahead<L>> {
    data class Ok<L : Lookahead<L>>(val states: MutableList<State<L>>) : BuildOutcome<L>()
    data class Err<L : Lookahead<L>>(val error: TableConstructionError<L>) : BuildOutcome<L>()
}

fun buildLr0StatesOrError(
    grammar: Grammar,
    start: NonterminalString,
): BuildOutcome<Nil> = Lr.new(grammar, start, Nil()).buildStatesOrError()


fun useLaneTable(): Boolean {
    return true
}

fun buildLr1States(grammar: Grammar, start: NonterminalString): MutableList<State<TokenSet>> {
    val (methodName: String, methodFn: ConstructionFunction) = if (useLaneTable()) {
        Pair("lane", ::buildLaneTableStates)
    } else {
        Pair("legacy", ::buildLr1StatesLegacy)
    }

    // profile! { &Tls::session(), formatCall("LR(1) state construction ({})", methodName), { methodFn(grammar, start) } }
    val _unusedLabel = "LR(1) state construction ($methodName)"
    return methodFn(grammar, start)
}

fun buildLr0States(
    grammar: Grammar,
    start: NonterminalString,
): MutableList<State<Nil>> {
    val lr1 = Lr.new(grammar, start, Nil())
    return lr1.buildStates()
}

class Lr<L>(
    val grammar: Grammar,
    val firstSets: FirstSets,
    val startNt: NonterminalString,
    val startLookahead: L,
    permitEarlyStop: Boolean,
) where L : Lookahead<L>, L : LookaheadBuild<L> {
    var permitEarlyStop: Boolean = permitEarlyStop
        private set

    companion object {
        fun <L> new(grammar: Grammar, startNt: NonterminalString, startLookahead: L): Lr<L>
            where L : Lookahead<L>, L : LookaheadBuild<L> = Lr(
            grammar = grammar,
            firstSets = FirstSets.new(grammar),
            startNt = startNt,
            startLookahead = startLookahead,
            permitEarlyStop = false,
        )
    }

    fun setPermitEarlyStop(v: Boolean) {
        permitEarlyStop = v
    }

    fun buildStates(): MutableList<State<L>> =
        when (val outcome = buildStatesOrError()) {
            is BuildOutcome.Ok -> outcome.states
            is BuildOutcome.Err -> throw TableConstructionErrorException(outcome.error)
        }

    fun buildStatesOrError(): BuildOutcome<L> {
        val session = Tls.session()
        val kernelSet: KKernelSet<Kernel<L>, StateIndex> = KKernelSet()
        val states: MutableList<State<L>> = mutableListOf()
        val conflicts: MutableList<Conflict<L>> = mutableListOf()

        // create the starting state
        kernelSet.addState(Kernel.start(items(startNt, 0, startLookahead)))

        while (true) {
            val k = kernelSet.next() ?: break
            val seedItems = k.items
            val items = transitiveClosure(seedItems)
            val index = StateIndex(states.size)

            if (index.value % 5000 == 0 && index.value > 0) {
                session.log(Level.Verbose) { "${index.value} states created so far." }
            }

            val thisState = State(
                index = index,
                items = items.copy(vec = items.vec.toMutableList()),
                shifts = map(),
                reductions = mutableListOf(),
                gotos = map(),
            )

            // group the items that we can transition into by shifting
            // over a term or nonterm
            val transitions: Multimap<Symbol, VecCollection<Pair<Item<Nil>, L>>, Pair<Item<Nil>, L>> =
                Multimap(collectionFactory = { VecCollection() })
            for (item in items.vec) {
                val shifted = item.shiftedItem() ?: continue
                val symbol = shifted.first
                val shiftedItem = shifted.second
                transitions.push(
                    symbol,
                    Pair(Item.lr0(shiftedItem.production, shiftedItem.index), shiftedItem.lookahead),
                )
            }

            for ((symbol, shiftedItemsCollection) in transitions) {
                val shiftedItems: MutableList<Item<L>> = shiftedItemsCollection.asList()
                    .map { (lr0Item, lookahead) -> lr0Item.withLookahead(lookahead) }
                    .toMutableList()

                // Not entirely obvious: if the original set of items
                // is sorted to begin with (and it is), then this new
                // set of shifted items is *also* sorted. This is
                // because it is produced from the old items by simply
                // incrementing the index by 1.
                val nextState = kernelSet.addState(Kernel.shifted(shiftedItems))

                when (symbol) {
                    is Symbol.Terminal -> {
                        val prev = thisState.shifts.put(symbol.term, nextState)
                        check(prev == null) // cannot have a shift/shift conflict
                    }
                    is Symbol.Nonterminal -> {
                        val prev = thisState.gotos.put(symbol.nt, nextState)
                        check(prev == null)
                    }
                }
            }

            // finally, consider the reductions
            for (item in items.vec.filter { it.canReduce() }) {
                thisState.reductions.add(Pair(item.lookahead, item.production))
            }

            // check for conflicts
            conflicts.addAll(startLookahead.conflicts(thisState))

            // extract a new state
            states.add(thisState)

            if (permitEarlyStop && session.stopAfter(conflicts.size)) {
                session.log(Level.Verbose) { "${conflicts.size} conflicts encountered, stopping." }
                break
            }
        }

        return if (conflicts.isNotEmpty()) {
            BuildOutcome.Err(TableConstructionError(states, conflicts))
        } else {
            BuildOutcome.Ok(states)
        }
    }

    fun items(id: NonterminalString, index: Int, lookahead: L): MutableList<Item<L>> =
        grammar.productionsFor(id)
            .map { production ->
                check(index <= production.symbols.size)
                Item(
                    production = production,
                    index = index,
                    lookahead = lookahead,
                )
            }
            .toMutableList()

    // expands `state` with epsilon moves
    fun transitiveClosure(items: MutableList<Item<L>>): Items<L> {
        val stack: MutableList<Item<Nil>> = items.map { it.toLr0() }.toMutableList()
        val map: Multimap<Item<Nil>, LookaheadCollection<L>, L> =
            Multimap(collectionFactory = { LookaheadCollection() })
        for (item in items) {
            map.push(item.toLr0(), item.lookahead)
        }

        while (stack.isNotEmpty()) {
            val item = stack.removeLast()
            val lookahead = map.get(item)!!.value!!

            val shiftSymbol = item.shiftSymbol()

            // Check whether this is an item where the cursor
            // is resting on a non-terminal:
            //
            // I = ... (*) X z... [lookahead]
            //
            // The `nt` will be X and the `remainder` will be `z...`.
            val nt: NonterminalString
            val remainder: List<Symbol>
            if (shiftSymbol == null) {
                continue // requires a reduce
            } else {
                val (sym, rest) = shiftSymbol
                when (sym) {
                    is Symbol.Terminal -> continue // requires a shift
                    is Symbol.Nonterminal -> {
                        nt = sym.nt
                        remainder = rest
                    }
                }
            }

            // In that case, for each production of `X`, we are also
            // in a state where the cursor rests at the start of that production:
            //
            // X = (*) a... [lookahead']
            // X = (*) b... [lookahead']
            //
            // Here `lookahead'` is computed based on the `remainder` and our
            // `lookahead`. In LR1 at least, it is the union of:
            //
            //   (a) FIRST(remainder)
            //   (b) if remainder may when epsilon, also our lookahead.
            for (newItem in epsilonMoves(this, nt, remainder, lookahead)) {
                val newItem0 = newItem.toLr0()
                if (map.push(newItem0, newItem.lookahead)) {
                    stack.add(newItem0)
                }
            }
        }

        val finalItems: MutableList<Item<L>> = mutableListOf()
        for ((lr0Item, coll) in map) {
            finalItems.add(lr0Item.withLookahead(coll.value!!))
        }

        return Items(vec = finalItems)
    }
}

class TableConstructionErrorException(
    val inner: TableConstructionError<*>,
    val lr1Inner: Lr1TableConstructionError? = null,
) : RuntimeException()

/// Except for the initial state, the kernel sets always contain
/// a set of "seed" items where something has been pushed (that is,
/// index > 0). In other words, items like this:
///
///    A = ...p (*) ...
///
/// where ...p is non-empty. We now have to expand to include any
/// epsilon moves:
///
///    A = ... (*) B ...
///    B = (*) ...        // added by transitiveClosure algorithm
///
/// But note that the state is completely identified by its
/// kernel set: the same kernel sets always expand to the
/// same transitive closures, and different kernel sets
/// always expand to different transitive closures. The
/// first point is obvious, but the latter point follows
/// because the transitive closure algorithm only adds
/// items where `index == 0`, and hence it can never add an
/// item found in a kernel set.
data class Kernel<L>(
    val items: MutableList<Item<L>>,
) : KKernelInterface<Kernel<L>, StateIndex> where L : Lookahead<L>, L : LookaheadBuild<L> {
    companion object {
        fun <L> start(items: MutableList<Item<L>>): Kernel<L>
            where L : Lookahead<L>, L : LookaheadBuild<L> {
            // In start state, kernel should have only items with `index == 0`.
            check(items.all { item -> item.index == 0 })
            return Kernel(items)
        }

        fun <L> shifted(items: MutableList<Item<L>>): Kernel<L>
            where L : Lookahead<L>, L : LookaheadBuild<L> {
            // Assert that this kernel consists only of shifted items
            // where `index > 0`. This assertion could cost real time to
            // check so only do it in debug mode.
            check(items.all { item -> item.index > 0 })
            return Kernel(items)
        }
    }

    override fun index(c: Int): StateIndex = StateIndex(c)

    override fun compareTo(other: Kernel<L>): Int {
        val n = minOf(items.size, other.items.size)
        for (i in 0 until n) {
            val c = items[i].compareTo(other.items[i])
            if (c != 0) return c
        }
        return items.size.compareTo(other.items.size)
    }
}

interface LookaheadBuild<Self> where Self : Lookahead<Self>, Self : LookaheadBuild<Self> {
    // Given that there exists an item
    //
    //     X = ... (*) Y ...s [L]
    //
    // where `nt` is `Y`, `remainder` is `...s`, and `lookahead` is
    // `L`, computes the new items resulting from epsilon moves (if
    // any). The technique of doing this will depend on the amount of
    // lookahead.
    //
    // For example, if we have an LR0 item, then for each `Y = ...`
    // production, we just add an `Y = (*) ...` item. But for LR1
    // items, we have to add multiple items where we consider the
    // lookahead from `FIRST(...s, L)`.
    fun epsilonMoves(
        lr: Lr<Self>,
        nt: NonterminalString,
        remainder: List<Symbol>,
        lookahead: Self,
    ): MutableList<Item<Self>>
}

// Route both static-dispatch trait methods through concrete type checks.
private fun <L> epsilonMoves(
    lr: Lr<L>,
    nt: NonterminalString,
    remainder: List<Symbol>,
    lookahead: L,
): MutableList<Item<L>> where L : Lookahead<L>, L : LookaheadBuild<L> =
    lookahead.epsilonMoves(lr, nt, remainder, lookahead)

// Since `Multimap` needs a `Collection` factory, provide a one-item
// collection that keeps the last-pushed lookahead for each kernel
// (so closure can recover the merged lookahead at the end).
internal class LookaheadCollection<L>(
    var value: L? = null,
) : io.github.kotlinmania.lalrpop.collections.Collection<L>
    where L : Lookahead<L> {
    override fun push(item: L): Boolean {
        if (value == null) {
            value = item
            return true
        }
        val changed = (value as L).push(item)
        return changed
    }
}
