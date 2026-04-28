// port-lint: source lr1/core/mod.rs
//! Core LR(1) types.
package io.github.kotlinmania.lalrpop.lr1.core

import io.github.kotlinmania.lalrpop.Prefix
import io.github.kotlinmania.btree.BTreeMap
import io.github.kotlinmania.lalrpop.collections.map.map
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.lr1.lookahead.Lookahead
import io.github.kotlinmania.lalrpop.lr1.lookahead.Nil
import io.github.kotlinmania.lalrpop.lr1.lookahead.Token
import io.github.kotlinmania.lalrpop.lr1.lookahead.TokenSet

data class Item<L : Lookahead<L>>(
    val production: Production,
    /** the dot comes before `index`, so `index` would be 1 for X = A (*) B C */
    val index: Int,
    val lookahead: L,
) : Comparable<Item<L>> {
    fun <L1 : Lookahead<L1>> withLookahead(l: L1): Item<L1> = Item(
        production = this.production,
        index = this.index,
        lookahead = l,
    )

    fun prefix(): List<Symbol> = production.symbols.subList(0, index)

    fun symbolSets(): SymbolSets {
        val symbols = production.symbols
        return if (canShift()) {
            SymbolSets(
                prefix = symbols.subList(0, index),
                cursor = symbols[index],
                suffix = symbols.subList(index + 1, symbols.size),
            )
        } else {
            SymbolSets(
                prefix = symbols.subList(0, index),
                cursor = null,
                suffix = emptyList(),
            )
        }
    }

    fun toLr0(): Item<Nil> = Item(
        production = production,
        index = index,
        lookahead = Nil(),
    )

    fun canShift(): Boolean = index < production.symbols.size

    fun canShiftNonterminal(nt: NonterminalString): Boolean {
        val s = shiftSymbol() ?: return false
        val head = s.first
        return head is Symbol.Nonterminal && head.nt == nt
    }

    fun canReduce(): Boolean = index == production.symbols.size

    fun shiftedItem(): Pair<Symbol, Item<L>>? =
        if (canShift()) {
            Pair(
                production.symbols[index],
                Item(
                    production = production,
                    index = index + 1,
                    lookahead = lookahead,
                ),
            )
        } else {
            null
        }

    fun shiftSymbol(): Pair<Symbol, List<Symbol>>? =
        if (canShift()) {
            Pair(
                production.symbols[index],
                production.symbols.subList(index + 1, production.symbols.size),
            )
        } else {
            null
        }

    override fun toString(): String {
        val head = "${production.nonterminal} =${Prefix(" ", production.symbols.subList(0, index))} (*)${Prefix(" ", production.symbols.subList(index, production.symbols.size))}"
        return head + lookahead.fmtAsItemSuffix()
    }

    override fun compareTo(other: Item<L>): Int {
        val c1 = production.compareTo(other.production)
        if (c1 != 0) return c1
        val c2 = index.compareTo(other.index)
        if (c2 != 0) return c2
        return lookahead.compareTo(other.lookahead)
    }

    companion object {
        fun lr0(production: Production, index: Int): Item<Nil> = Item(
            production = production,
            index = index,
            lookahead = Nil(),
        )
    }
}



data class StateIndex(var value: Int) : Comparable<StateIndex> {
    override fun toString(): String = "S$value"
    fun display(): String = "$value"
    override fun compareTo(other: StateIndex): Int = value.compareTo(other.value)
}

data class Items<L : Lookahead<L>>(
    val vec: MutableList<Item<L>>,
)


data class State<L : Lookahead<L>>(
    val index: StateIndex,
    val items: Items<L>,
    val shifts: BTreeMap<TerminalString, StateIndex> = map(),
    val reductions: MutableList<Pair<L, Production>> = mutableListOf(),
    val gotos: BTreeMap<NonterminalString, StateIndex> = map(),
) {
    /**
     * Returns the set of symbols which must appear on the stack to
     * be in this state. This is the *maximum* prefix of any item,
     * basically.
     */
    fun maxPrefix(): List<Symbol> {
        val prefix = items.vec
            .map { it.prefix() }
            .maxByOrNull { it.size }!!

        check(items.vec.all { item -> endsWith(prefix, item.production.symbols.subList(0, item.index)) })

        return prefix
    }

    /**
     * Returns the set of symbols from the stack that must be popped
     * for this state to return.
     */
    fun willPop(): List<Symbol> {
        val prefix = items.vec
            .filter { it.index > 0 }
            .map { it.prefix() }
            .minByOrNull { it.size }
            ?: emptyList()

        check(items.vec.filter { it.index > 0 }.all { item -> endsWith(item.prefix(), prefix) })

        return prefix
    }

    fun willPush(): List<Symbol> =
        items.vec
            .filter { it.index > 0 }
            .map { it.production.symbols.subList(it.index, it.production.symbols.size) }
            .minByOrNull { it.size }
            ?: emptyList()

    /**
     * Returns the type of nonterminal that this state will produce;
     * if `None` is returned, then this state may produce more than
     * one kind of nonterminal.
     *
     * FIXME -- currently, the start state returns `None` instead of
     * the goal symbol.
     */
    fun willProduce(): NonterminalString? {
        val returnableNonterminals: MutableList<NonterminalString> = items.vec
            .filter { it.index > 0 }
            .map { it.production.nonterminal }
            .dedup()
            .toMutableList()
        return if (returnableNonterminals.size == 1) {
            returnableNonterminals.removeLast()
        } else {
            null
        }
    }
}

private fun <T> endsWith(list: List<T>, suffix: List<T>): Boolean {
    if (suffix.size > list.size) return false
    val offset = list.size - suffix.size
    for (i in suffix.indices) {
        if (list[offset + i] != suffix[i]) return false
    }
    return true
}

private fun <T> Iterable<T>.dedup(): List<T> {
    val out = mutableListOf<T>()
    for (t in this) {
        if (out.isEmpty() || out.last() != t) out.add(t)
    }
    return out
}


sealed class Action : Comparable<Action> {
    data class Shift(val terminal: TerminalString, val state: StateIndex) : Action()
    data class Reduce(val production: Production) : Action()

    override fun compareTo(other: Action): Int {
        val o1 = ordinal()
        val o2 = other.ordinal()
        if (o1 != o2) return o1 - o2
        return when (this) {
            is Shift -> {
                val that = other as Shift
                val c = terminal.compareTo(that.terminal)
                if (c != 0) c else state.compareTo(that.state)
            }
            is Reduce -> production.compareTo((other as Reduce).production)
        }
    }

    private fun ordinal(): Int = when (this) {
        is Shift -> 0
        is Reduce -> 1
    }
}

data class Conflict<L>(
    // when in this state...
    val state: StateIndex,
    // with the following lookahead...
    val lookahead: L,
    // we can reduce...
    val production: Production,
    // but we can also...
    val action: Action,
)


data class TableConstructionError<L : Lookahead<L>>(
    // LR(1) state set, possibly incomplete if construction is
    // configured to terminate early.
    val states: MutableList<State<L>>,
    // Conflicts (non-empty) found in those states.
    val conflicts: MutableList<Conflict<L>>,
)

// LrResult and MutableList<State<TokenSet>> exist in Rust as Result type aliases.
// In Kotlin they would be sealed sum types; they are elided here
// in favor of throwing TableConstructionError directly at the call site.

/** `A = B C (*) D E F` or `A = B C (*)` */
data class SymbolSets(
    val prefix: List<Symbol>,       // both cases, [B, C]
    val cursor: Symbol?,            // first [D], second []
    val suffix: List<Symbol>,       // first [E, F], second []
) {
    companion object {
        fun new(): SymbolSets = SymbolSets(
            prefix = emptyList(),
            cursor = null,
            suffix = emptyList(),
        )
    }
}
