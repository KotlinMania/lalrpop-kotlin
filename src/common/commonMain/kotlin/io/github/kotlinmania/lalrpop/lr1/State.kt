package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol

internal data class StateIndex(
    val value: Int,
) : Comparable<StateIndex> {
    val field0: Int
        get() = value

    fun fmt(fmt: Appendable) {
        fmt.append("S$value")
    }

    fun fmtDisplay(fmt: Appendable) {
        fmt.append("$value")
    }

    override fun toString(): String = buildString { fmt(this) }

    fun display(): String = buildString { fmtDisplay(this) }

    override fun compareTo(other: StateIndex): Int = value.compareTo(other.value)
}

internal data class State<L : Lookahead<L>>(
    var index: StateIndex,
    val items: Items<L>,
    val shifts: Map<TerminalString, StateIndex> = map(),
    val reductions: MutableList<Pair<L, Production>> = mutableListOf(),
    val gotos: Map<NonterminalString, StateIndex> = map(),
) {
    /**
     * Returns the set of symbols which must appear on the stack to
     * be in this state. This is the *maximum* prefix of any item,
     * basically.
     */
    fun maxPrefix(): List<Symbol> {
        // Each state function takes as argument the longest prefix of any
        // item. Note that all items must have compatible prefixes.
        val prefix =
            items.vec
                .map(Item<L>::prefix)
                .maxByOrNull { symbols -> symbols.size }!!

        check(items.vec.all { item -> prefix.endsWith(item.production.symbols.subList(0, item.index)) })

        return prefix
    }

    /**
     * Returns the set of symbols from the stack that must be popped
     * for this state to return. If we have a state like:
     *
     * ```text
     * X = A B C (*) C
     * Y = B C (*) C
     * C = (*) ...
     * ```
     *
     * This would return `[B, C]`. For every state other than the
     * start state, this will return a list of length at least 1.
     * For the start state, returns `[]`.
     */
    fun willPop(): List<Symbol> {
        val prefix =
            items.vec
                .filter { item -> item.index > 0 }
                .map(Item<L>::prefix)
                .minByOrNull { symbols -> symbols.size }
                .orEmpty()

        check(items.vec.filter { item -> item.index > 0 }.all { item -> item.prefix().endsWith(prefix) })

        return prefix
    }

    fun willPush(): List<Symbol> =
        items.vec
            .filter { item -> item.index > 0 }
            .map { item -> item.production.symbols.subList(item.index, item.production.symbols.size) }
            .minByOrNull { symbols -> symbols.size }
            .orEmpty()

    /**
     * Returns the type of nonterminal that this state will produce;
     * if `null` is returned, then this state may produce more than
     * one kind of nonterminal.
     *
     * Note -- currently, the start state returns `null` instead of
     * the goal symbol.
     */
    fun willProduce(): NonterminalString? {
        val returnableNonterminals: MutableList<NonterminalString> =
            items.vec
                .filter { item -> item.index > 0 }
                .map { item -> item.production.nonterminal }
                .dedup()
                .toMutableList()
        return if (returnableNonterminals.size == 1) {
            returnableNonterminals.removeLast()
        } else {
            null
        }
    }
}

internal typealias Lr0State = State<Nil>

internal typealias Lr1State = State<TokenSet>

private fun <T> List<T>.endsWith(suffix: List<T>): Boolean {
    if (suffix.size > this.size) return false
    val offset = this.size - suffix.size
    for (i in suffix.indices) {
        if (this[offset + i] != suffix[i]) return false
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
