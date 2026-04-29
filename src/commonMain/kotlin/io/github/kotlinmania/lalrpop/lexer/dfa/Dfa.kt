// port-lint: source lexer/dfa/mod.rs
/**
 * Constructs a Dfa which picks the longest matching regular
 * expression from the input.
 */
package io.github.kotlinmania.lalrpop.lexer.dfa

import io.github.kotlinmania.lalrpop.Kernel
import io.github.kotlinmania.lalrpop.KernelSet
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.lexer.nfa.Nfa
import io.github.kotlinmania.lalrpop.lexer.nfa.NfaConstructionError
import io.github.kotlinmania.lalrpop.lexer.nfa.NfaConstructionException
import io.github.kotlinmania.lalrpop.lexer.nfa.NfaStateIndex
import io.github.kotlinmania.lalrpop.lexer.nfa.Other as NfaOther
import io.github.kotlinmania.lalrpop.lexer.nfa.START as NFA_START
import io.github.kotlinmania.lalrpop.lexer.nfa.Test
import io.github.kotlinmania.lalrpop.lexer.Hir

data class Dfa(val states: List<State>)

@Deprecated("use `Dfa` instead", ReplaceWith("Dfa"))
typealias DFA = Dfa

data class Precedence(val value: Int) : Comparable<Precedence> {
    override fun compareTo(other: Precedence): Int = value.compareTo(other.value)
}

sealed class DfaConstructionError {
    data class NfaConstructionErr(
        val index: NfaIndex,
        val error: NfaConstructionError,
    ) : DfaConstructionError()

    /** Either of the two regexs listed could match, and they have equal priority. */
    data class Ambiguity(val match0: NfaIndex, val match1: NfaIndex) : DfaConstructionError()
}

class DfaConstructionException(val error: DfaConstructionError) : RuntimeException(error.toString())

@Deprecated(
    "use `DfaConstructionError` instead",
    ReplaceWith("DfaConstructionError"),
)
typealias DFAConstructionError = DfaConstructionError

fun buildDfa(regexs: List<Hir>, precedences: List<Precedence>): Result<Dfa> = runCatching {
    check(regexs.size == precedences.size)
    val nfas: List<Nfa> = regexs.mapIndexed { i, r ->
        try {
            Nfa.fromRe(r).getOrThrow()
        } catch (e: NfaConstructionException) {
            throw DfaConstructionException(
                DfaConstructionError.NfaConstructionErr(NfaIndex(i), e.error)
            )
        }
    }

    val builder = DfaBuilder(nfas = nfas, precedences = precedences.toList())
    builder.build()
}

private class DfaBuilder(
    val nfas: List<Nfa>,
    val precedences: List<Precedence>,
) {
    fun build(): Dfa {
        val kernelSet: KernelSet<DfaItemSet, DfaStateIndex> = KernelSet()
        val states: MutableList<State> = mutableListOf()

        val startStateIndex = startState(kernelSet)
        check(startStateIndex == START)

        while (true) {
            val itemSet = kernelSet.next() ?: break

            // collect all the specific tests we expect from any of
            // the items in this state
            val tests: Set<Test> = set()
            for (item in itemSet.items) {
                for (edge in nfa(item).testEdges(item.nfaState)) {
                    tests.add(edge.label)
                }
            }
            val disjointTests = removeOverlap(tests)

            // if any Nfa is in an accepting state, that makes this
            // Dfa state an accepting state
            val allAccepts: MutableList<Pair<Precedence, NfaIndex>> = mutableListOf()
            for (item in itemSet.items) {
                if (nfa(item).isAcceptingState(item.nfaState)) {
                    allAccepts.add(precedences[item.nfaIndex.value] to item.nfaIndex)
                }
            }

            // if all NFAs are in a rejecting state, that makes this
            // Dfa a rejecting state
            val allRejects: Boolean = itemSet.items.all { item ->
                nfa(item).isRejectingState(item.nfaState)
            }

            val kind: Kind = when {
                allRejects || itemSet.items.isEmpty() -> Kind.Reject
                allAccepts.isEmpty() -> Kind.Neither
                allAccepts.size == 1 -> Kind.Accepts(allAccepts[0].second)
                else -> {
                    allAccepts.sortWith(compareBy({ it.first }, { it.second }))
                    val (bestPriority, bestNfa) = allAccepts[allAccepts.size - 1]
                    val (nextPriority, nextNfa) = allAccepts[allAccepts.size - 2]
                    if (bestPriority == nextPriority) {
                        throw DfaConstructionException(
                            DfaConstructionError.Ambiguity(match0 = bestNfa, match1 = nextNfa)
                        )
                    }
                    Kind.Accepts(bestNfa)
                }
            }

            // for each specific test, find what happens if we see a
            // character matching that test
            val testEdges: MutableList<Pair<Test, DfaStateIndex>> = mutableListOf()
            for (test in disjointTests) {
                val items: MutableList<Item> = mutableListOf()
                for (item in itemSet.items) {
                    val accepted = acceptTest(item, test)
                    if (accepted != null) items.add(accepted)
                }

                // at least one of those items should accept this test
                check(items.isNotEmpty())

                testEdges.add(test to kernelSet.addState(transitiveClosure(items)))
            }

            testEdges.sortWith(compareBy({ it.first }, { it.second }))

            // Consider what there is some character that does not meet
            // any of the tests. In this case, we can just ignore all
            // the test edges for each of the items and just union all
            // the "other" edges -- because if it were one of those
            // test edges, then that transition is represented above.
            val otherTransitions: MutableList<Item> = mutableListOf()
            for (item in itemSet.items) {
                val accepted = acceptOther(item)
                if (accepted != null) otherTransitions.add(accepted)
            }

            // we never know the full set
            check(itemSet.items.isEmpty() || otherTransitions.isNotEmpty())

            val otherEdge = kernelSet.addState(transitiveClosure(otherTransitions))

            val state = State(
                itemSet = itemSet,
                kind = kind,
                testEdges = testEdges,
                otherEdge = otherEdge,
            )

            states.add(state)
        }

        return Dfa(states = states)
    }

    fun startState(kernelSet: KernelSet<DfaItemSet, DfaStateIndex>): DfaStateIndex {
        // starting state is at the beginning of all regular expressions
        val items: List<Item> = (0 until nfas.size).map { i ->
            Item(nfaIndex = NfaIndex(i), nfaState = NFA_START)
        }
        val itemSet = transitiveClosure(items.toMutableList())
        return kernelSet.addState(itemSet)
    }

    fun acceptTest(item: Item, test: Test): Item? {
        val nfa = nfa(item)

        val matchingTest = nfa.testEdges(item.nfaState)
            .filter { edge -> edge.label.intersects(test) }
            .map { edge -> item.to(edge.to) }

        val matchingOther = nfa.otherEdges(item.nfaState)
            .map { edge -> item.to(edge.to) }

        return (matchingTest + matchingOther).firstOrNull()
    }

    fun acceptOther(item: Item): Item? {
        val nfa = nfa(item)
        return nfa.otherEdges(item.nfaState)
            .map { edge -> item.to(edge.to) }
            .firstOrNull()
    }

    fun transitiveClosure(initial: MutableList<Item>): DfaItemSet {
        val items = initial
        val observed: MutableSet<Item> = items.toMutableSet()

        var counter = 0
        while (counter < items.size) {
            val item = items[counter]
            val nfa = nfa(item)
            val derived = nfa.noopEdges(item.nfaState)
                .map { edge -> item.to(edge.to) }
                .filter { observed.add(it) }
                .toList()
            items.addAll(derived)
            counter += 1
        }

        items.sort()
        // dedup preserving order
        val dedup: MutableList<Item> = mutableListOf()
        for (item in items) {
            if (dedup.isEmpty() || dedup.last() != item) dedup.add(item)
        }

        return DfaItemSet(items = dedup)
    }

    fun nfa(item: Item): Nfa = nfas[item.nfaIndex.value]
}

data class State(
    internal val itemSet: DfaItemSet,
    val kind: Kind,
    val testEdges: List<Pair<Test, DfaStateIndex>>,
    val otherEdge: DfaStateIndex,
)

sealed class Kind {
    data class Accepts(val nfa: NfaIndex) : Kind()
    object Reject : Kind()
    object Neither : Kind()
}

data class NfaIndex(val value: Int) : Comparable<NfaIndex> {
    override fun compareTo(other: NfaIndex): Int = value.compareTo(other.value)
    fun index(): Int = value
    override fun toString(): String = "NfaIndex($value)"
}

@Deprecated("use `NfaIndex` instead", ReplaceWith("NfaIndex"))
typealias NFAIndex = NfaIndex

data class DfaStateIndex(val value: Int) : Comparable<DfaStateIndex> {
    override fun compareTo(other: DfaStateIndex): Int = value.compareTo(other.value)
    fun index(): Int = value
    override fun toString(): String = "Dfa$value"
}

@Deprecated("use `DfaStateIndex` instead", ReplaceWith("DfaStateIndex"))
typealias DFAStateIndex = DfaStateIndex

internal typealias DfaKernelSet = KernelSet<DfaItemSet, DfaStateIndex>

internal typealias Index = DfaStateIndex

data class DfaItemSet(val items: List<Item>) : Kernel<DfaItemSet, DfaStateIndex>, Comparable<DfaItemSet> {
    override fun index(c: Int): DfaStateIndex = DfaStateIndex(c)

    override fun compareTo(other: DfaItemSet): Int {
        val n = minOf(items.size, other.items.size)
        for (i in 0 until n) {
            val cmp = items[i].compareTo(other.items[i])
            if (cmp != 0) return cmp
        }
        return items.size.compareTo(other.items.size)
    }
}

data class Item(
    val nfaIndex: NfaIndex,
    val nfaState: NfaStateIndex,
) : Comparable<Item> {
    fun to(s: NfaStateIndex): Item = Item(nfaIndex = nfaIndex, nfaState = s)

    override fun compareTo(other: Item): Int {
        val cmp = nfaIndex.compareTo(other.nfaIndex)
        return if (cmp != 0) cmp else nfaState.compareTo(other.nfaState)
    }

    override fun toString(): String = "($nfaIndex:$nfaState)"
}

val START: DfaStateIndex = DfaStateIndex(0)

fun Dfa.state(index: DfaStateIndex): State = states[index.value]
