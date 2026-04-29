// port-lint: ignore
// transliterated from upstream module root
/**
 * A key part of the lane-table algorithm is the idea of a CONTEXT
 * SET (my name, the paper has no name for this). Basically it
 * represents the LR1 context under which a given conflicting action
 * would take place.
 *
 * So, for example, imagine this grammar:
 *
 * ```text
 * A = B x
 *   | C y
 * B = z
 * C = z
 * ```
 *
 * This gives rise to states like:
 *
 * - `S0 = { * B x, * C y, B = * z, C = * z }`
 * - `S1 = { B = z *, C = z * }`
 *
 * This second state has two conflicting items. Let call them
 * conflicts 0 and 1 respectively. The conflict set would then have
 * two entries (one for each conflict) and it would map each of them
 * to a TokenSet supplying context. So when we trace everything
 * out we might get a ContextSet of:
 *
 * - `[ 0: x, 1: y ]`
 *
 * In general, you want to ensure that the token sets of all
 * conflicting items are pairwise-disjoint, or else if you get to a
 * state that has both of those items (which, by definition, does
 * arise) you will not know which to take. In this case, we are all set,
 * because item 0 occurs only with lookahead `x` and item 1 with
 * lookahead `y`.
 */
package io.github.kotlinmania.lalrpop.lr1.lanetable.table.contextset

import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.core.Action
import io.github.kotlinmania.lalrpop.lr1.core.State<TokenSet>
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.ConflictIndex

class ContextSet(
    private val values: MutableList<TokenSet>,
) {
    companion object {
        fun new(numConflicts: Int): ContextSet = ContextSet(
            values = MutableList(numConflicts) { TokenSet.new() },
        )

        /**
         * Attempts to union `set1` with `set2`, producing a new set.
         * Throws `OverlappingLookahead` if that would produce
         * an invalid (overlapping) conflict set.
         */
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

    /**
     * Attempts to merge the values `conflict: set` into this
     * conflict set. If this would result in an invalid conflict set
     * (where two conflicts have overlapping lookahead), then throws
     * `OverlappingLookahead` and has no effect.
     *
     * Assuming no errors, returns `true` if this resulted in any
     * modifications, and `false` otherwise.
     */
    fun insert(conflict: ConflictIndex, set: TokenSet): Boolean {
        for ((i, value) in values.withIndex()) {
            val index = ConflictIndex.new(i)
            if (index != conflict && value.isIntersecting(set)) {
                throw OverlappingLookahead
            }
        }

        return values[conflict.index].unionWith(set)
    }

    fun apply(state: State<TokenSet>, actions: Set<Action>) {
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

/**
 * Upstream Rust uses a unit struct `OverlappingLookahead` as an `Err` payload.
 *
 * Kotlin does not have a direct unit-struct equivalent; we model the error as a
 * singleton throwable that can be caught by type.
 */
object OverlappingLookahead : Exception()
