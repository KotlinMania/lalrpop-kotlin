package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Production as ReprProduction
import io.github.kotlinmania.lalrpop.lr1.Lr1State
import io.github.kotlinmania.lalrpop.lr1.Token
import io.github.kotlinmania.lalrpop.runtime.ParseTables
import io.github.kotlinmania.lalrpop.runtime.Production

/**
 * Build packed [ParseTables] from an LR(1) state machine plus a grammar.
 *
 * Pure data transformation — no source emission, no codegen text. Walks
 * `state.shifts` / `state.reductions` / `state.gotos` exactly the same way the
 * existing Rust-output backend in `lr1/codegen/ParseTable.kt` does, but stores the
 * results as `ShortArray`s in a [ParseTables] instance instead of writing them as
 * Rust source.
 *
 * Encoding follows the contract in [ParseTables]: positive value = shift target
 * (1-based), negative = reduce id (1-based, negated), zero = error.
 *
 * The caller supplies the typed [Production] array and `acceptProductionId`. Those
 * are not derivable from the front-end state machine alone — they require knowing the
 * action lambdas, which is the codegen step that translates user grammar code to
 * Kotlin. By taking them as inputs, this builder is decoupled from the action-body
 * translation problem and can be exercised against real grammars whose actions are
 * supplied externally (e.g. for test fixtures, hand-written lambdas, or a future
 * codegen pass).
 */
internal fun <S, L> tablesFromLr1States(
    grammar: Grammar,
    states: List<Lr1State>,
    productions: Array<Production<S, L>>,
    acceptProductionId: Int,
): ParseTables<S, L> {
    val numStates = states.size
    val numTerminals = grammar.terminals.all.size
    val nonterminalKeys = grammar.nonterminals.keys.toList()
    val numNonterminals = nonterminalKeys.size

    // Production → reduce-id map. Mirrors the construction in
    // `lr1/codegen/ParseTable.kt` (see the `reduceIndices` build loop): walk
    // nonterminal-by-nonterminal in declaration order, assigning a sequential id to
    // each production. The same order is used by the Rust-output backend, so the
    // Kotlin and Rust paths agree on production ids when they share a grammar.
    val reduceIndices: Map<ReprProduction, Int> = buildMap {
        var idx = 0
        for (nt in grammar.nonterminals.values) {
            for (p in nt.productions) {
                put(p, idx)
                idx += 1
            }
        }
    }

    require(productions.size == reduceIndices.size) {
        "productions array size ${productions.size} does not match grammar's " +
        "production count ${reduceIndices.size} — caller must supply one " +
        "Production<S, L> per grammar production, in the same order"
    }

    val action = ShortArray(numStates * numTerminals)
    val eofAction = ShortArray(numStates)
    val goto = ShortArray(numStates * numNonterminals)

    for ((stateIdx, state) in states.withIndex()) {
        for ((tIdx, terminal) in grammar.terminals.all.withIndex()) {
            val shiftTarget = state.shifts[terminal]
            action[stateIdx * numTerminals + tIdx] = if (shiftTarget != null) {
                encodeShift(shiftTarget.value)
            } else {
                encodeReduction(state, Token.Terminal(terminal), reduceIndices)
            }
        }

        eofAction[stateIdx] = encodeReduction(state, Token.Eof, reduceIndices)

        for ((ntIdx, nt) in nonterminalKeys.withIndex()) {
            val gotoTarget = state.gotos[nt]
            goto[stateIdx * numNonterminals + ntIdx] = if (gotoTarget != null) {
                // [ParseTables] stores GOTO as 1-based with 0 = "no transition".
                (gotoTarget.value + 1).toShort()
            } else {
                0
            }
        }
    }

    return ParseTables(
        numStates = numStates,
        numTerminals = numTerminals,
        numNonterminals = numNonterminals,
        action = action,
        eofAction = eofAction,
        goto = goto,
        productions = productions,
        acceptProductionId = acceptProductionId,
    )
}

/**
 * Encode a shift action. Shift targets are stored as `state + 1` so that 0 stays
 * available as the error sentinel.
 */
private fun encodeShift(targetState: Int): Short {
    val encoded = targetState + 1
    check(encoded > 0 && encoded <= Short.MAX_VALUE.toInt()) {
        "shift target $targetState does not fit in the encoded ShortArray range"
    }
    return encoded.toShort()
}

/**
 * Find the reduction in [state] whose lookahead set contains [token] and encode it as
 * `-(reduceId + 1)`. Returns 0 if no reduction is registered for the token.
 *
 * Mirrors the algorithm in `writeReduction` in the Rust-output backend's
 * `ParseTable.kt`.
 */
private fun encodeReduction(
    state: Lr1State,
    token: Token,
    reduceIndices: Map<ReprProduction, Int>,
): Short {
    val reduction = state.reductions
        .asSequence()
        .filter { (lookahead, _) -> lookahead.contains(token) }
        .map { (_, production) -> production }
        .firstOrNull()
        ?: return 0

    val reduceId = reduceIndices.getValue(reduction)
    val encoded = -(reduceId + 1)
    check(encoded < 0 && encoded >= Short.MIN_VALUE.toInt()) {
        "reduce id $reduceId does not fit in the encoded ShortArray range"
    }
    return encoded.toShort()
}
