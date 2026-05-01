package io.github.kotlinmania.lalrpop.runtime

/**
 * The data half of a generated parser.
 *
 * A generated grammar declares one [ParseTables] instance carrying the LR(1) state
 * machine for that grammar — packed `ShortArray`s for the transition tables plus the
 * typed productions array. The driver ([TableDrivenLr1Driver]) is generic and grammar-
 * agnostic; only this data is per-grammar.
 *
 * Encoding of the action arrays follows the upstream LALRPOP convention also used by
 * [ShortAction]:
 *
 * - `value > 0`  →  shift to state `value - 1`
 * - `value < 0`  →  reduce by production `-(value + 1)`
 * - `value == 0` →  error
 *
 * The `action` array is row-major: index = `state * numTerminals + terminal`.
 * The `goto` array is row-major:   index = `state * numNonterminals + nonterminal`,
 * holding the target state directly (zero is a sentinel for "no transition," so generated
 * tables encode states as 1-based and the driver subtracts 1 on read).
 *
 * `acceptProductionId` is the production index for the synthetic start rule. Reducing
 * by that production ends the parse and returns the produced symbol as the result.
 */
class ParseTables<S, L>(
    val numStates: Int,
    val numTerminals: Int,
    val numNonterminals: Int,
    val action: ShortArray,
    val eofAction: ShortArray,
    val goto: ShortArray,
    val productions: Array<Production<S, L>>,
    val acceptProductionId: Int,
) {
    init {
        require(action.size == numStates * numTerminals) {
            "action table size ${action.size} != $numStates * $numTerminals"
        }
        require(eofAction.size == numStates) {
            "eofAction table size ${eofAction.size} != $numStates"
        }
        require(goto.size == numStates * numNonterminals) {
            "goto table size ${goto.size} != $numStates * $numNonterminals"
        }
        require(acceptProductionId in productions.indices) {
            "acceptProductionId $acceptProductionId out of range [0, ${productions.size})"
        }
    }

    internal fun actionAt(state: Int, terminal: Int): Short =
        action[state * numTerminals + terminal]

    internal fun eofActionAt(state: Int): Short =
        eofAction[state]

    internal fun gotoAt(state: Int, nonterminal: Int): Int {
        val raw = goto[state * numNonterminals + nonterminal].toInt()
        check(raw != 0) {
            "goto($state, $nonterminal) is unset — table compiled wrong, or LR(1) " +
            "state machine inconsistent with productions"
        }
        return raw - 1
    }
}
