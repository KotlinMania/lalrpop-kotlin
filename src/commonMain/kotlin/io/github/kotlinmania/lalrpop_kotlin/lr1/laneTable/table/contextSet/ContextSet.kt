// port-lint: source src/lr1/lane_table/table/context_set/mod.rs
//! A key part of the lane-table algorithm is the idea of a CONTEXT
//! SET (my name, the paper has no name for this). Basically it
//! represents the LR1 context under which a given conflicting action
//! would take place.
//!
//! So, for example, imagine this grammar:
//!
//! ```notrust
//! A = B x
//!   | C y
//! B = z
//! C = z
//! ```
//!
//! This gives rise to states like:
//!
//! - `S0 = { * B x, * C y, B = * z, C = * z }`
//! - `S1 = { B = z *, C = z * }`
//!
//! This second state has two conflicting items. Let's call them
//! conflicts 0 and 1 respectively. The conflict set would then have
//! two entries (one for each conflict) and it would map each of them
//! to a TokenSet supplying context. So when we trace everything
//! out we might get a ContextSet of:
//!
//! - `[ 0: x, 1: y ]`
//!
//! In general, you want to ensure that the token sets of all
//! conflicting items are pairwise-disjoint, or else if you get to a
//! state that has both of those items (which, by definition, does
//! arise) you won't know which to take. In this case, we're all set,
//! because item 0 occurs only with lookahead `x` and item 1 with
//! lookahead `y`.
package io.github.kotlinmania.lalrpop_kotlin.lr1.laneTable.table.contextSet

import io.github.kotlinmania.lalrpop_kotlin.collections.map.Map
import io.github.kotlinmania.lalrpop_kotlin.collections.set.Set
import io.github.kotlinmania.lalrpop_kotlin.collections.map.map
import io.github.kotlinmania.lalrpop_kotlin.lr1.lookahead.TokenSet
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.Action
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.Lr1State
import io.github.kotlinmania.lalrpop_kotlin.lr1.laneTable.table.ConflictIndex

// mod test

class ContextSet(
    private val values: MutableList<TokenSet>,
) {
    companion object {
        fun new(numConflicts: Int): ContextSet = ContextSet(
            values = MutableList(numConflicts) { TokenSet.new() },
        )

        /// Attempts to union `set1` with `set2`, producing a new set.
        /// Throws `OverlappingLookaheadException` if that would produce
        /// an invalid (overlapping) conflict set.
        fun union(set1: ContextSet, set2: ContextSet): ContextSet {
            val result = set1.clone()
            for ((i, t) in set2.values.withIndex()) {
                result.insert(ConflictIndex.new(i), t)
            }
            return result
        }
    }

    fun clone(): ContextSet = ContextSet(
        values = values.map { it.clone() }.toMutableList(),
    )

    /// Attempts to merge the values `conflict: set` into this
    /// conflict set. If this would result in an invalid conflict set
    /// (where two conflicts have overlapping lookahead), then throws
    /// `OverlappingLookaheadException` and has no effect.
    ///
    /// Assuming no errors, returns `true` if this resulted in any
    /// modifications, and `false` otherwise.
    fun insert(conflict: ConflictIndex, set: TokenSet): Boolean {
        for ((i, value) in values.withIndex()) {
            val index = ConflictIndex.new(i)
            if (index != conflict && value.isIntersecting(set)) {
                throw OverlappingLookaheadException
            }
        }

        return values[conflict.index].unionWith(set)
    }

    fun apply(state: Lr1State, actions: Set<Action>) {
        // create a map from each action to its lookahead
        val lookaheads: Map<Action, TokenSet> = map()
        val actionsIter = actions.iterator()
        var idx = 0
        while (actionsIter.hasNext() && idx < values.size) {
            lookaheads[actionsIter.next()] = values[idx]
            idx++
        }

        for ((i, reduction) in state.reductions.withIndex()) {
            val (_, production) = reduction
            val action: Action = Action.Reduce(production)
            state.reductions[i] = Pair(lookaheads[action]!!.clone(), production)
        }
    }
}

object OverlappingLookaheadException : RuntimeException() {
    private fun readResolve(): Any = OverlappingLookaheadException
}
