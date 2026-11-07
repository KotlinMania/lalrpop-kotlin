/** Core LR(1) types. */
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.Prefix
import io.github.kotlinmania.lalrpop.collections.compareLists
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol

internal data class Item<L : Lookahead<L>>(
    val production: Production,
    /**
     * the dot comes before `index`, so `index` would be 1 for X = A (*) B C
     */
    val index: Int,
    val lookahead: L,
) : Comparable<Item<L>> {
    fun <L1 : Lookahead<L1>> withLookahead(l: L1): Item<L1> =
        Item(
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

    fun toLr0(): Lr0Item =
        Item(
            production = production,
            index = index,
            lookahead = Nil(),
        )

    fun canShift(): Boolean = index < production.symbols.size

    fun canShiftNonterminal(nt: NonterminalString): Boolean {
        val matchVal = this.shiftSymbol()
        return when {
            matchVal != null && matchVal.first is Symbol.Nonterminal -> {
                val (shifted) = matchVal.first as Symbol.Nonterminal
                shifted == nt
            }

            else -> {
                false
            }
        }
    }

    fun canReduce(): Boolean = index == production.symbols.size

    fun shiftedItem(): Pair<Symbol, Item<L>>? =
        if (canShift()) {
            Pair(
                production.symbols[index],
                Item(
                    production = production,
                    index = index + 1,
                    lookahead = lookahead.clone(),
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

    fun fmt(fmt: Appendable) {
        fmt.append(
            "${production.nonterminal} =" +
                "${Prefix(" ", production.symbols.subList(0, index))} (*)" +
                "${Prefix(" ", production.symbols.subList(index, production.symbols.size))}",
        )
        fmt.append(lookahead.fmtAsItemSuffix())
    }

    override fun toString(): String = buildString { fmt(this) }

    override fun compareTo(other: Item<L>): Int {
        val c1 = production.compareTo(other.production)
        if (c1 != 0) return c1
        val c2 = index.compareTo(other.index)
        if (c2 != 0) return c2
        return lookahead.compareTo(other.lookahead)
    }

    companion object {
        fun lr0(production: Production, index: Int): Lr0Item =
            Item(
                production = production,
                index = index,
                lookahead = Nil(),
            )
    }
}

internal typealias Lr0Item = Item<Nil>

internal typealias Lr1Item = Item<TokenSet>

internal data class Items<L : Lookahead<L>>(
    val vec: MutableList<Item<L>>,
) : Comparable<Items<L>> {
    override fun compareTo(other: Items<L>): Int = compareLists(vec, other.vec)
}

internal typealias Lr0Items = Items<Nil>

internal typealias Lr1Items = Items<TokenSet>

/**
 * `A = B C (*) D E F` or `A = B C (*)`
 */
internal data class SymbolSets(
    val prefix: List<Symbol>, // both cases, [B, C]
    val cursor: Symbol?, // first [D], second []
    val suffix: List<Symbol>, // first [E, F], second []
) {
    companion object {
        fun new(): SymbolSets =
            SymbolSets(
                prefix = emptyList(),
                cursor = null,
                suffix = emptyList(),
            )
    }
}
