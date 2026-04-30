// port-lint: source lr1/lookahead.rs
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.collections.Collection
import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol

interface Lookahead<Self : Lookahead<Self>> : Collection<Self>, Comparable<Self> {
    fun fmtAsItemSuffix(): String
}

class Nil : Lookahead<Nil>, LookaheadBuild<Nil>, LookaheadInterpret<Nil> {
    override fun push(item: Nil): Boolean = false

    override fun fmtAsItemSuffix(): String = ""

    override fun epsilonMoves(
        lr: Lr<Nil>,
        nt: NonterminalString,
        remainder: List<Symbol>,
        lookahead: Nil,
    ): MutableList<Item<Nil>> = lr.items(nt, 0, lookahead)

    override fun reduction(state: State<Nil>, token: Token): Production? =
        state.reductions.firstOrNull()?.second

    override fun equals(other: Any?): Boolean = other is Nil
    override fun hashCode(): Int = 0
    override fun toString(): String = "Nil"
    override fun compareTo(other: Nil): Int = 0

    companion object {
        fun conflicts(thisState: State<Nil>): MutableList<Conflict<Nil>> {
            val index = thisState.index

            val conflicts: MutableList<Conflict<Nil>> = mutableListOf()

            for ((terminal, nextState) in thisState.shifts) {
                for ((_, production) in thisState.reductions) {
                    conflicts.add(
                        Conflict(
                            state = index,
                            lookahead = Nil(),
                            production = production,
                            action = Action.Shift(terminal, nextState),
                        )
                    )
                }
            }

            if (thisState.reductions.size > 1) {
                for (i in 1 until thisState.reductions.size) {
                    val (_, production) = thisState.reductions[i]
                    val otherProduction = thisState.reductions[0].second
                    conflicts.add(
                        Conflict(
                            state = index,
                            lookahead = Nil(),
                            production = production,
                            action = Action.Reduce(otherProduction),
                        )
                    )
                }
            }

            return conflicts
        }
    }
}

/**
 * I have semi-arbitrarily decided to use the term "token" to mean
 * either one of the terminals of our language, or else the
 * pseudo-symbol EOF that represents "end of input".
 */
sealed class Token : Comparable<Token> {
    abstract override fun toString(): String

    object Eof : Token() {
        override fun toString(): String = "Eof"
    }

    object Error : Token() {
        override fun toString(): String = "Error"
    }

    data class Terminal(val terminalString: TerminalString) : Token() {
        override fun toString(): String = "$terminalString"
    }

    fun unwrapTerminal(): TerminalString = when (this) {
        is Terminal -> terminalString
        Eof, Error -> error("`unwrapTerminal()` invoked but with EOF or Error")
    }

    override fun compareTo(other: Token): Int {
        val ao = ordinal()
        val bo = other.ordinal()
        if (ao != bo) return ao - bo
        return when (this) {
            is Terminal -> this.terminalString.compareTo((other as Terminal).terminalString)
            else -> 0
        }
    }

    private fun ordinal(): Int = when (this) {
        Eof -> 0
        Error -> 1
        is Terminal -> 2
    }

    companion object {
        @Deprecated("use `Eof` instead", ReplaceWith("Token.Eof"))
        val EOF: Token = Eof
    }
}

class TokenSet() : Lookahead<TokenSet>, LookaheadBuild<TokenSet>, LookaheadInterpret<TokenSet> {
    internal val bitSet: MutableSet<Int> = mutableSetOf()

    override fun fmtAsItemSuffix(): String = " $this"

    override fun push(item: TokenSet): Boolean = unionWith(item)

    override fun epsilonMoves(
        lr: Lr<TokenSet>,
        nt: NonterminalString,
        remainder: List<Symbol>,
        lookahead: TokenSet,
    ): MutableList<Item<TokenSet>> {
        val firstSet = lr.firstSets.first1(remainder, lookahead)
        return lr.items(nt, 0, firstSet)
    }

    override fun reduction(state: State<TokenSet>, token: Token): Production? =
        state.reductions
            .filter { (tokens, _) -> tokens.contains(token) }
            .map { (_, production) -> production }
            .firstOrNull()

    companion object {
        fun new(): TokenSet = with { _ -> TokenSet() }

        /** A TokenSet containing all possible terminals + EOF. */
        fun all(): TokenSet {
            val s = new()
            with { terminals ->
                for (i in 0 until terminals.all.size) {
                    s.bitSet.add(i)
                }
                s.insertEof()
            }
            return s
        }

        fun eof(): TokenSet {
            val set = new()
            set.insertEof()
            return set
        }

        fun from(token: Token): TokenSet {
            val set = new()
            set.insert(token)
            return set
        }

        fun conflicts(thisState: State<TokenSet>): MutableList<Conflict<TokenSet>> {
            val conflicts: MutableList<Conflict<TokenSet>> = mutableListOf()

            for ((terminal, nextState) in thisState.shifts) {
                val token: Token = Token.Terminal(terminal)
                val inconsistent = thisState.reductions.mapNotNull { (reduceTokens, production) ->
                    if (reduceTokens.contains(token)) production else null
                }
                val set = TokenSet.from(token)
                for (production in inconsistent) {
                    conflicts.add(
                        Conflict(
                            state = thisState.index,
                            lookahead = set.clone(),
                            production = production,
                            action = Action.Shift(terminal, nextState),
                        )
                    )
                }
            }

            val len = thisState.reductions.size
            for (i in 0 until len) {
                for (j in i + 1 until len) {
                    val (iTokens, iProduction) = thisState.reductions[i]
                    val (jTokens, jProduction) = thisState.reductions[j]

                    if (iTokens.isDisjoint(jTokens)) {
                        continue
                    }

                    conflicts.add(
                        Conflict(
                            state = thisState.index,
                            lookahead = iTokens.intersection(jTokens),
                            production = iProduction,
                            action = Action.Reduce(jProduction),
                        )
                    )
                }
            }

            return conflicts
        }
    }

    private fun eofBit(): Int = with { terminals -> terminals.all.size }

    private fun bit(lookahead: Token): Int = with { t -> bitWith(lookahead, t) }

    private fun bitWith(lookahead: Token, terminals: TerminalSet): Int = when (lookahead) {
        Token.Eof -> terminals.all.size
        Token.Error -> terminals.all.size + 1
        is Token.Terminal -> terminals.bits[lookahead.terminalString]!!
    }

    fun reserve(len: Int) {
        // bitSet.reserveLen(len) — MutableSet has no reserve; no-op
    }

    fun len(): Int = bitSet.size

    fun insert(lookahead: Token): Boolean {
        val bit = bit(lookahead)
        return bitSet.add(bit)
    }

    fun insertWith(lookahead: Token, terminals: TerminalSet): Boolean {
        val bit = bitWith(lookahead, terminals)
        return bitSet.add(bit)
    }

    fun insertEof(): Boolean {
        val bit = eofBit()
        return bitSet.add(bit)
    }

    fun unionWith(set: TokenSet): Boolean {
        val len = this.len()
        this.bitSet.addAll(set.bitSet)
        return this.len() != len
    }

    fun intersection(set: TokenSet): TokenSet {
        val result = TokenSet()
        result.bitSet.addAll(this.bitSet)
        result.bitSet.retainAll(set.bitSet)
        return result
    }

    fun contains(token: Token): Boolean = bitSet.contains(bit(token))

    fun containsEof(): Boolean = bitSet.contains(eofBit())

    /**
     * If this set contains EOF, removes it from the set and returns
     * true. Otherwise, returns false.
     */
    fun takeEof(): Boolean {
        val eofBit = eofBit()
        val containsEof = bitSet.contains(eofBit)
        bitSet.remove(eofBit)
        return containsEof
    }

    fun isDisjoint(other: TokenSet): Boolean = bitSet.none { it in other.bitSet }

    fun isIntersecting(other: TokenSet): Boolean = !isDisjoint(other)

    fun iter(): TokenSetIter = TokenSetIter(bitSet.sorted().iterator())

    fun clone(): TokenSet {
        val result = TokenSet()
        result.bitSet.addAll(this.bitSet)
        return result
    }

    operator fun iterator(): Iterator<Token> = iter()

    fun intoIter(): TokenSetIter = iter()

    fun fmt(fmt: StringBuilder) {
        val terminals: MutableList<Token> = mutableListOf()
        for (t in iter()) terminals.add(t)
        fmt.append(terminals.toString())
    }

    override fun toString(): String = buildString { fmt(this) }

    override fun equals(other: Any?): Boolean = other is TokenSet && other.bitSet == this.bitSet
    override fun hashCode(): Int = bitSet.hashCode()

    override fun compareTo(other: TokenSet): Int {
        val a = this.bitSet.sorted()
        val b = other.bitSet.sorted()
        val n = minOf(a.size, b.size)
        for (k in 0 until n) {
            val c = a[k].compareTo(b[k])
            if (c != 0) return c
        }
        return a.size.compareTo(b.size)
    }
}

class TokenSetIter(private val bitSet: Iterator<Int>) : Iterator<Token> {
    override fun hasNext(): Boolean = bitSet.hasNext()

    override fun next(): Token {
        val bit = bitSet.next()
        return with { terminals ->
            when {
                bit == terminals.all.size + 1 -> Token.Error
                bit == terminals.all.size -> Token.Eof
                else -> Token.Terminal(terminals.all[bit])
            }
        }
    }
}

private fun <RET> with(op: (TerminalSet) -> RET): RET = Lr1Tls.with(op)
