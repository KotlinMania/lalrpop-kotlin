// port-lint: source lr1/laneTable/construct/mod.rs
//! Generate rust parser code using the lane table algorithm
package io.github.kotlinmania.lalrpop.lr1.laneTable.construct

import io.github.kotlinmania.lalrpop.InPlaceUnificationTable
import io.github.kotlinmania.btree.BTreeMap
import io.github.kotlinmania.btree.BTreeSet
import io.github.kotlinmania.lalrpop.collections.map.map
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.stateGraph.StateGraph
import io.github.kotlinmania.lalrpop.lr1.lookahead.TokenSet
import io.github.kotlinmania.lalrpop.lr1.build.TableConstructionErrorException
import io.github.kotlinmania.lalrpop.lr1.build.buildLr0States
import io.github.kotlinmania.lalrpop.lr1.core.Action
import io.github.kotlinmania.lalrpop.lr1.core.Conflict
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.Items
import io.github.kotlinmania.lalrpop.lr1.core.State<Nil>
import io.github.kotlinmania.lalrpop.lr1.core.State<TokenSet>
import io.github.kotlinmania.lalrpop.lr1.core.State
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.core.TableConstructionError
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.laneTable.conflictingActions
import io.github.kotlinmania.lalrpop.lr1.laneTable.construct.stateSet.StateSet
import io.github.kotlinmania.lalrpop.lr1.laneTable.lane.LaneTracer
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.ConflictIndex
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.LaneTable
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.RowConflictException
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.contextSet.ContextSet
import io.github.kotlinmania.lalrpop.lr1.laneTable.table.contextSet.OverlappingLookaheadException

class LaneTableConstruct(
    private val grammar: Grammar,
    private val firstSets: FirstSets,
    private val startNt: NonterminalString,
) {
    companion object {
        fun new(grammar: Grammar, startNt: NonterminalString): LaneTableConstruct =
            LaneTableConstruct(
                grammar = grammar,
                firstSets = FirstSets.new(grammar),
                startNt = startNt,
            )
    }

    fun construct(): MutableList<State<TokenSet>> {
        val lr0States: MutableList<State<Nil>> = try {
            // In this case, the grammar is actually
            // LR(0). This is very rare -- it means that the
            // grammar does not need lookahead to execute. In
            // principle, we could stop here, except that if
            // we do so, then the lookahead values that we get
            // are very broad.
            //
            // Broad lookahead values will cause "eager"
            // reduce at runtime -- i.e., if there is some
            // scenario where the lookahead tells you we are
            // in error, but we would have to reduce a few
            // states before we see it. This, in turn, can
            // cause infinite loops around error recovery
            // (#240).
            //
            // Since we want to behave as a LR(1) parser
            // would, we will just go ahead and run the
            // algorithm.
            buildLr0States(grammar, startNt)
        } catch (e: TableConstructionErrorException) {
            (e.inner as TableConstructionError<io.github.kotlinmania.lalrpop.lr1.lookahead.Nil>).states
        }

        // Convert the LR(0) states into LR(0-1) states.
        val states: MutableList<State<TokenSet>> = promoteLr0States(lr0States)

        // For each inconsistent state, apply the lane-table algorithm to
        // resolve it.
        var i = 0
        while (true) {
            if (i >= states.size) break

            try {
                resolveInconsistencies(states, StateIndex(i))
            } catch (_: UnresolvedInconsistencyException) {
                // We failed because of irreconcilable conflicts
                // somewhere. Just compute the conflicts from the final set of
                // states.
                val conflicts: MutableList<Conflict<TokenSet>> = states
                    .flatMap { TokenSet.conflicts(it) }
                    .toMutableList()
                throw TableConstructionErrorException(
                    TableConstructionError(states = states, conflicts = conflicts),
                )
            }

            i++
        }

        return states
    }

    /**
     * Given a set of LR0 states, returns LR1 states where the lookahead
     * is always `TokenSet::all()`. We refer to these states as LR(0-1)
     * states in the README.
     */
    private fun promoteLr0States(lr0: List<State<Nil>>): MutableList<State<TokenSet>> {
        val all = TokenSet.all()
        return lr0.map { s ->
            val items = s.items.vec
                .map { item ->
                    Item(
                        production = item.production,
                        index = item.index,
                        lookahead = all.clone(),
                    )
                }
                .toMutableList()
            val reductions = s.reductions
                .map { (_, p) -> Pair(all.clone(), p) }
                .toMutableList()
            State(
                index = s.index,
                items = Items(vec = items),
                shifts = s.shifts,
                reductions = reductions,
                gotos = s.gotos,
            )
        }.toMutableList()
    }

    private fun resolveInconsistencies(
        states: MutableList<State<TokenSet>>,
        inconsistentState: StateIndex,
    ) {
        var actions: BTreeSet<Action> = conflictingActions(states[inconsistentState.value])
        if (actions.isEmpty()) {
            // This can mean one of two things: only shifts, or a
            // single reduction. We have to be careful about states
            // with a single reduction: even though such a state is
            // not inconsistent (there is only one possible course of
            // action), we still want to run the lane table algorithm,
            // because otherwise we get states with "complete"
            // lookahead, which messes with error recovery.
            //
            // In particular, if there is too much lookahead, we will
            // reduce even when it is inappropriate to do so.
            val collected: BTreeSet<Action> = io.github.kotlinmania.lalrpop.collections.set.set()
            for ((_, prod) in states[inconsistentState.value].reductions) {
                collected.add(Action.Reduce(prod))
            }
            actions = collected
            if (actions.isEmpty()) {
                return
            }
        }

        val table = buildLaneTable(states, inconsistentState, actions)

        // Consider first the "LALR" case, where the lookaheads for each
        // action are completely disjoint.
        if (attemptLalr(states[inconsistentState.value], table, actions)) {
            return
        }

        // Construct the initial states; each state will map to a
        // context-set derived from its row in the lane-table. This is
        // fallible, because a state may be internally inconsistent.
        //
        // (To handle unification, we also map each state to a
        // `StateSet` that is its entry in the `ena` table.)
        val rows: BTreeMap<StateIndex, ContextSet> = try {
            table.rows()
        } catch (e: RowConflictException) {
            throw UnresolvedInconsistencyException(e.state)
        }
        val unify = InPlaceUnificationTable<StateSet, ContextSet>(
            keyFromIndex = { idx -> StateSet.fromIndex(idx) },
            unifyValues = { a, b -> StateSet.unifyValues(a, b) },
        )
        val stateSets: BTreeMap<StateIndex, StateSet> = map()
        for ((stateIndex, contextSet) in rows) {
            val stateSet = unify.newKey(contextSet.clone())
            stateSets[stateIndex] = stateSet
        }

        // Now merge state-sets, cloning states where needed.
        val merge = Merge.new(
            table = table,
            unify = unify,
            states = states,
            stateSets = stateSets,
            inconsistentState = inconsistentState,
        )
        val beachheadStates = table.beachheadStates()
        for (beachheadState in beachheadStates) {
            try {
                merge.start(beachheadState)
            } catch (e: MergeFailureException) {
                throw UnresolvedInconsistencyException(e.source)
            }
        }
        merge.patchTargetStarts(actions)
    }

    private fun attemptLalr(
        state: State<TokenSet>,
        table: LaneTable,
        actions: BTreeSet<Action>,
    ): Boolean =
        try {
            val columns = table.columns()
            columns.apply(state, actions)
            true
        } catch (_: OverlappingLookaheadException) {
            false
        }

    private fun buildLaneTable(
        states: List<State<TokenSet>>,
        inconsistentState: StateIndex,
        actions: BTreeSet<Action>,
    ): LaneTable {
        val stateGraph = StateGraph.new(states)
        val tracer = LaneTracer.new<TokenSet>(
            grammar = grammar,
            startNt = startNt,
            states = states,
            firstSets = firstSets,
            stateGraph = stateGraph,
            conflicts = actions.size,
        )
        for ((i, action) in actions.withIndex()) {
            tracer.startTrace(inconsistentState, ConflictIndex.new(i), action)
        }
        return tracer.intoTable()
    }
}

internal class UnresolvedInconsistencyException(val source: StateIndex) : RuntimeException()

internal class MergeFailureException(val source: StateIndex, val target: StateIndex) : RuntimeException()
