// port-lint: source lr1/lane_table/construct/merge.rs
package io.github.kotlinmania.lalrpop.lr1.lanetable.construct

import io.github.kotlinmania.lalrpop.InPlaceUnificationTable
import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.multimap.Multimap
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.multimap.VecCollection
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.lr1.core.Action
import io.github.kotlinmania.lalrpop.lr1.core.State<TokenSet>
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.lanetable.construct.stateset.StateSet
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.LaneTable
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.contextset.ContextSet

/**
 * The "merge" phase of the algorithm is described in "Step 3c" of
 * [the README][r].  It consists of walking through the various
 * states in the lane table and merging them into sets of states that
 * have compatible context sets; if we encounter a state S that has a
 * successor T but where the context set of S is not compatible with
 * T, then we will clone T into a new T2 (and hopefully the context
 * set of S will be compatible with the reduced context of T2).
 *
 * [r]: ../README.md
 */
class Merge internal constructor(
    private val table: LaneTable,
    private val states: MutableList<State<TokenSet>>,
    private val visited: Set<StateIndex>,
    private val originalIndices: Map<StateIndex, StateIndex>,
    private val clones: Multimap<StateIndex, VecCollection<StateIndex>, StateIndex>,
    private val targetStates: MutableList<StateIndex>,
    private val contextSets: ContextSets,
) {
    companion object {
        fun new(
            table: LaneTable,
            unify: InPlaceUnificationTable<StateSet, ContextSet>,
            states: MutableList<State<TokenSet>>,
            stateSets: Map<StateIndex, StateSet>,
            inconsistentState: StateIndex,
        ): Merge = Merge(
            table = table,
            states = states,
            visited = set(),
            originalIndices = map(),
            clones = Multimap(collectionFactory = { VecCollection() }),
            targetStates = mutableListOf(inconsistentState),
            contextSets = ContextSets(unify = unify, stateSets = stateSets),
        )
    }

    fun start(beachheadState: StateIndex) {
        // Since we always start walks from beachhead states, and they
        // are not reachable from anyone else, this state should not
        // have been unioned with anything else yet.
        walk(beachheadState)
    }

    fun patchTargetStarts(actions: Set<Action>) {
        for (targetState in this.targetStates) {
            val contextSet = this.contextSets.contextSet(targetState)
            contextSet.apply(this.states[targetState.value], actions)
        }
    }

    /**
     * If `state` is a cloned state, find its original index.  Useful
     * for indexing into the lane table and so forth.
     */
    private fun originalIndex(state: StateIndex): StateIndex =
        this.originalIndices[state] ?: state

    private fun successors(state: StateIndex): Set<StateIndex>? =
        this.table.successors(originalIndex(state))

    private fun walk(state: StateIndex) {
        if (!this.visited.add(state)) {
            return
        }

        val succs = this.successors(state) ?: return
        for (successor in succs.toList()) {
            if (this.contextSets.union(state, successor)) {
                walk(successor)
            } else {
                // search for an existing clone with which we can merge
                val existingClones = this.clones.get(successor)?.asList().orEmpty()
                val contextSetsRef = this.contextSets
                val existingClone = existingClones.firstOrNull { successor1 ->
                    contextSetsRef.union(state, successor1)
                }

                if (existingClone != null) {
                    patchLinks(state, successor, existingClone)
                    walk(existingClone)
                } else {
                    // if we do not find one, we have to make a new clone
                    val successor1 = clone(successor)
                    if (this.contextSets.union(state, successor1)) {
                        patchLinks(state, successor, successor1)
                        walk(successor1)
                    } else {
                        throw MergeFailureException(originalIndex(state), originalIndex(successor1))
                    }
                }
            }
        }
    }

    private fun clone(state: StateIndex): StateIndex {
        // create a new state with same contents as the old one
        val newIndex = StateIndex(this.states.size)
        val oldState = this.states[state.value]
        val newState = oldState.copy(
            index = newIndex,
            items = oldState.items.copy(vec = oldState.items.vec.toMutableList()),
            shifts = map<TerminalString, StateIndex>().also { it.putAll(oldState.shifts) },
            reductions = oldState.reductions.toMutableList(),
            gotos = map<NonterminalString, StateIndex>().also { it.putAll(oldState.gotos) },
        )
        this.states.add(newState)

        // track the original index and clones
        val originalIndex = originalIndex(state)
        this.originalIndices[newIndex] = originalIndex
        this.clones.push(originalIndex, newIndex)

        // create a new unify key for this new state
        val contextSet = this.table.contextSet(originalIndex)
        this.contextSets.newState(newIndex, contextSet)

        // keep track of the clones of the target state
        if (originalIndex == this.targetStates[0]) {
            this.targetStates.add(newIndex)
        }

        return newIndex
    }

    private fun patchLinks(
        predecessor: StateIndex,
        originalSuccessor: StateIndex,
        clonedSuccessor: StateIndex,
    ) {
        val state = this.states[predecessor.value]
        for ((terminal, targetState) in state.shifts.toList()) {
            if (targetState == originalSuccessor) {
                state.shifts[terminal] = clonedSuccessor
            }
        }
        for ((nonterminal, targetState) in state.gotos.toList()) {
            if (targetState == originalSuccessor) {
                state.gotos[nonterminal] = clonedSuccessor
            }
        }
    }
}

internal class ContextSets(
    private val stateSets: Map<StateIndex, StateSet>,
    private val unify: InPlaceUnificationTable<StateSet, ContextSet>,
) {
    fun contextSet(state: StateIndex): ContextSet {
        val stateSet = this.stateSets[state]!!
        return this.unify.probeValue(stateSet)
    }

    fun union(source: StateIndex, target: StateIndex): Boolean {
        val set1 = this.stateSets[source]!!
        val set2 = this.stateSets[target]!!
        return this.unify.unifyVarVar(set1, set2)
    }

    fun newState(newIndex: StateIndex, contextSet: ContextSet) {
        val stateSet = this.unify.newKey(contextSet)
        this.stateSets[newIndex] = stateSet
    }
}
