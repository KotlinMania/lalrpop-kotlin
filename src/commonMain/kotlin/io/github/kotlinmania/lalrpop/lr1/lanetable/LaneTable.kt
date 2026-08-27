// transliterated from upstream module root
package io.github.kotlinmania.lalrpop.lr1.lanetable

import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.Lookahead
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.lr1.Action
import io.github.kotlinmania.lalrpop.lr1.Conflict
import io.github.kotlinmania.lalrpop.lr1.State
import io.github.kotlinmania.lalrpop.lr1.lanetable.construct.LaneTableConstruct

internal fun buildLaneTableStates(grammar: Grammar, start: NonterminalString): MutableList<State<TokenSet>> =
    LaneTableConstruct.new(grammar, start).construct()

internal fun <L : Lookahead<L>> conflictingActions(
    state: State<L>,
): Set<Action> {
    val firstLookahead = state.reductions.firstOrNull()?.first
    val conflicts: MutableList<Conflict<L>> =
        firstLookahead?.conflicts(state) ?: mutableListOf()
    val reductions = conflicts.map { c -> Action.Reduce(c.production) as Action }
    val actions = conflicts.map { c -> c.action }
    val result: Set<Action> = io.github.kotlinmania.lalrpop.collections.set()
    for (r in reductions) result.add(r)
    for (a in actions) result.add(a)
    return result
}
