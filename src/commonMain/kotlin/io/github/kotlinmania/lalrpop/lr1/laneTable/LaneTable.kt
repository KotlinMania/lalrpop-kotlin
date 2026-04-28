// port-lint: source lr1/laneTable/mod.rs
package io.github.kotlinmania.lalrpop.lr1.laneTable

import io.github.kotlinmania.btree.BTreeSet
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.lookahead.Lookahead
import io.github.kotlinmania.lalrpop.lr1.MutableList<State<TokenSet>>
import io.github.kotlinmania.lalrpop.lr1.lookahead.Nil
import io.github.kotlinmania.lalrpop.lr1.lookahead.TokenSet
import io.github.kotlinmania.lalrpop.lr1.core.Action
import io.github.kotlinmania.lalrpop.lr1.core.Conflict
import io.github.kotlinmania.lalrpop.lr1.core.State
import io.github.kotlinmania.lalrpop.lr1.laneTable.construct.LaneTableConstruct

fun buildLaneTableStates(grammar: Grammar, start: NonterminalString): MutableList<State<TokenSet>> =
    LaneTableConstruct.new(grammar, start).construct()

internal fun <L : Lookahead<L>> conflictingActions(
    state: State<L>,
): BTreeSet<Action> {
    val conflicts: MutableList<Conflict<L>> = when {
        state.reductions.isEmpty() && state.shifts.isEmpty() -> mutableListOf()
        state.reductions.firstOrNull()?.first is TokenSet -> {
            @Suppress("UNCHECKED_CAST")
            TokenSet.conflicts(state as State<TokenSet>) as MutableList<Conflict<L>>
        }
        else -> {
            @Suppress("UNCHECKED_CAST")
            Nil.conflicts(state as State<Nil>) as MutableList<Conflict<L>>
        }
    }
    val reductions = conflicts.map { c -> Action.Reduce(c.production) as Action }
    val actions = conflicts.map { c -> c.action }
    val result: BTreeSet<Action> = io.github.kotlinmania.lalrpop.collections.set.set()
    for (r in reductions) result.add(r)
    for (a in actions) result.add(a)
    return result
}
