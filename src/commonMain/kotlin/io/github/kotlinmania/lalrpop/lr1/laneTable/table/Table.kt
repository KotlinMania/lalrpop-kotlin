// port-lint: source lr1/lane_table/table/mod.rs
//! The "Lane Table". In the paper, this is depicted like so:
//!
//! ```text
//! +-------+----+-----+----+------------+
//! + State | C1 | ... | Cn | Successors |
//! +-------+----+-----+----+------------+
//! ```
//!
//! where each row summarizes some state that potentially contributes
//! lookahead to the conflict.
package io.github.kotlinmania.lalrpop.lr1.laneTable.table

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.multimap.Multimap
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.multimap.SetCollection
import io.github.kotlinmania.lalrpop.collections.map.ComparablePair
import io.github.kotlinmania.lalrpop.collections.map.map
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.contextSet.ContextSet
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.contextSet.OverlappingLookaheadException

data class ConflictIndex(val index: Int) : Comparable<ConflictIndex> {
    override fun compareTo(other: ConflictIndex): Int = index.compareTo(other.index)

    companion object {
        fun new(index: Int): ConflictIndex = ConflictIndex(index)
    }
}

class LaneTable(
    private val grammar: Grammar,
    private val conflicts: Int,
    // Upstream: `BTreeMap<(StateIndex, ConflictIndex), TokenSet>` (BTreeMap
    // with auto-derived `Ord` on tuples). We use [ComparablePair] so
    // the Kotlin BTreeMap orders pairs the same way the upstream
    // `(A, B): Ord` does (compare `first`, then `second`).
    private val lookaheads: Map<ComparablePair<StateIndex, ConflictIndex>, TokenSet> = map(),
    private val successors: Multimap<StateIndex, SetCollection<StateIndex>, StateIndex> =
        Multimap(collectionFactory = { SetCollection() }),
) {
    companion object {
        fun new(grammar: Grammar, conflicts: Int): LaneTable = LaneTable(
            grammar = grammar,
            conflicts = conflicts,
        )
    }

    fun addLookahead(state: StateIndex, conflict: ConflictIndex, tokens: TokenSet) {
        val key = ComparablePair(state, conflict)
        val existing = lookaheads.getOrPut(key) { TokenSet.new() }
        existing.unionWith(tokens)
    }

    fun addSuccessor(state: StateIndex, succ: StateIndex) {
        successors.push(state, succ)
    }

    /**
     * Unions together the lookaheads for each column and returns a
     * context set containing all of them. For an LALR(1) grammar,
     * these token sets will be mutually disjoint, as discussed in
     * the README; otherwise `Err` will be returned.
     */
    fun columns(): ContextSet {
        val columns = ContextSet.new(this.conflicts)
        for ((key, set) in lookaheads) {
            val (_, conflictIndex) = key
            columns.insert(conflictIndex, set)
        }
        return columns
    }

    fun successors(state: StateIndex): Set<StateIndex>? =
        successors.get(state)?.asSet()

    /**
     * Returns the state of states in the table that are **not**
     * reachable from another state in the table. These are called
     * "beachhead states".
     */
    fun beachheadStates(): Set<StateIndex> {
        // set of all states that are reachable from another state
        val reachable: Set<StateIndex> = set()
        for ((_, succ) in successors) {
            for (s in succ.asSet()) reachable.add(s)
        }

        val result: Set<StateIndex> = set()
        for ((key, _) in lookaheads) {
            val (stateIndex, _) = key
            if (stateIndex !in reachable) {
                result.add(stateIndex)
            }
        }
        return result
    }

    fun contextSet(state: StateIndex): ContextSet {
        val set = ContextSet.new(this.conflicts)
        for ((key, tokenSet) in lookaheads) {
            val (stateIndex, conflictIndex) = key
            if (stateIndex == state) {
                set.insert(conflictIndex, tokenSet)
            }
        }
        return set
    }

    /**
     * Returns a map containing all states that appear in the table,
     * along with the context set for each state (i.e., each row in
     * the table, basically). Throws `OverlappingLookaheadException`
     * wrapping the offending `StateIndex` if any state has a conflict
     * between the context sets even within its own row.
     */
    fun rows(): Map<StateIndex, ContextSet> {
        val map: Map<StateIndex, ContextSet> = map()
        for ((key, tokenSet) in lookaheads) {
            val (stateIndex, conflictIndex) = key
            val cs = map.getOrPut(stateIndex) { ContextSet.new(this.conflicts) }
            try {
                cs.insert(conflictIndex, tokenSet)
            } catch (e: OverlappingLookaheadException) {
                throw RowConflictException(stateIndex)
            }
        }

        // In some cases, there are states that have no context at
        // all, only successors. In that case, make sure to add an
        // empty row for them.
        for ((stateIndex, _) in successors) {
            map.getOrPut(stateIndex) { ContextSet.new(this.conflicts) }
        }

        return map
    }

    override fun toString(): String {
        val indices: Set<StateIndex> = set()
        for ((key, _) in lookaheads) {
            val (state, _) = key
            indices.add(state)
        }
        for ((key, _) in successors) {
            indices.add(key)
        }

        val header: List<String> = buildList {
            add("State")
            for (i in 0 until conflicts) add("C$i")
            add("Successors")
        }

        val rows: List<List<String>> = indices.map { index ->
            buildList {
                add(index.toString())
                for (i in 0 until conflicts) {
                    val ts = lookaheads[ComparablePair(index, ConflictIndex.new(i))]
                    add(ts?.toString() ?: "")
                }
                val succ = successors.get(index)
                add(succ?.asSet()?.toString() ?: "")
            }
        }

        val table: List<List<String>> = listOf(header) + rows

        val columns = 2 + conflicts

        val widths: List<Int> = (0 until columns).map { c ->
            // find the max width of any row at this column
            table.maxOf { r -> r[c].length }
        }

        val sb = StringBuilder()
        for (row in table) {
            sb.append("| ")
            for ((i, column) in row.withIndex()) {
                if (i > 0) {
                    sb.append(" | ")
                }
                sb.append(column.padEnd(widths[i]))
            }
            sb.append(" |\n")
        }
        return sb.toString()
    }
}

class RowConflictException(val state: StateIndex) : RuntimeException()
