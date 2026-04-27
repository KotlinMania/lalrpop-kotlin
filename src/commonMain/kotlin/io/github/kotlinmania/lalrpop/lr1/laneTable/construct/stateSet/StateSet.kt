// port-lint: source src/lr1/lane_table/construct/state_set.rs
package io.github.kotlinmania.lalrpop.lr1.laneTable.construct.stateSet

import io.github.kotlinmania.lalrpop.UnifyKey
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.contextSet.ContextSet

/**
 * Mirrors the Rust `type Value = ContextSet;` associated type from
 * `impl UnifyKey for StateSet`. Kotlin doesn't have associated types
 * on interface implementations, so we expose the binding as a
 * top-level typealias.
 */
typealias Value = ContextSet

/**
 * The unification key for a set of states in the lane table
 * algorithm.  Each set of states is associated with a
 * `ContextSet`. When two sets of states are merged, their conflict
 * sets are merged as well; this will fail if that would produce an
 * overlapping conflict set.
 */
data class StateSet(
    private val indexValue: Int,
) : UnifyKey<ContextSet> {
    override fun index(): Int = indexValue

    companion object {
        fun fromIndex(u: Int): StateSet = StateSet(indexValue = u)

        fun tag(): String = "StateSet"

        // FIXME: The `ena` interface is really designed around `UnifyValue`
        // being cheaply cloneable; we should either refactor `ena` a bit or
        // find some other way to associate a `ContextSet` with a state set
        // (for example, we could have each state set be associated with an
        // index that maps to a `ContextSet`), and do the merging ourselves.
        // But this is easier for now, and cloning a `ContextSet` isn't THAT
        // expensive, right? :)
        fun unifyValues(value1: ContextSet, value2: ContextSet): ContextSet? =
            try {
                ContextSet.union(value1, value2)
            } catch (_: io.github.kotlinmania.lalrpop.lr1.laneTable.table.contextSet.OverlappingLookaheadException) {
                null
            }
    }
}
