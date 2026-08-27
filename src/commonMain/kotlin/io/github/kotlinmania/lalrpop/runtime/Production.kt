package io.github.kotlinmania.lalrpop.runtime

/**
 * A single LALRPOP production, expressed as data the runtime can interpret.
 *
 * The data-driven generated parser declares one [Production] per grammar rule, holding:
 *
 * - `nonterminalId` — index used by the GOTO table to find the next state after the
 *   production reduces.
 * - `rhsLength` — how many stack entries the production pops before invoking [action].
 * - `action` — typed lambda that consumes the popped span [start..end] and the popped
 *   stack entries, and returns the symbol to push for the produced nonterminal.
 *
 * The action lambda receives the [ParseStack] and is expected to call [ParseStack.pop]
 * the appropriate number of times in the order the rule's RHS lists. Returning a typed
 * `S` (the per-grammar `Symbol` sealed class) means there is no `Any` payload — the
 * compiler enforces that the produced symbol is one of the declared variants.
 */
internal class Production<S, L>(
    val nonterminalId: Short,
    val rhsLength: Int,
    val action: ProductionAction<S, L>,
)

/**
 * A production action — typed lambda contract.
 *
 * Defined as a `fun interface` rather than a plain `(…) -> Result<S>` function type so
 * the generated tables can name the parameters at the call site, which makes generated
 * action bodies easier to read in stack traces and IDE tooltips.
 *
 * Returns `Result<S>` because a production can fail (the user's grammar action runs
 * arbitrary Kotlin code that may throw, and LALRPOP's existing `actionNNN` helpers
 * model failure with the same stdlib `Result` type — see the fallible reducers in the
 * current hand-ported `LrGrammar.kt` for the convention). The driver inspects the
 * result, pushes on success, and propagates the throwable as a parse failure on
 * `Result.failure`. Strict typing constraint holds: the success payload is a typed
 * `S` variant, never `Any` or `Any?`.
 */
internal fun interface ProductionAction<S, L> {
    fun reduce(stack: ParseStack<S, L>, span: ProductionSpan<L>): Result<S>
}

/**
 * Source span covered by a single reduction.
 *
 * `start` is the start location of the leftmost RHS symbol; `end` is the end location of
 * the rightmost. For empty (epsilon) productions both endpoints collapse to the lookahead
 * location at the point of reduction. The driver computes these from the popped stack
 * entries before invoking the action.
 */
internal data class ProductionSpan<out L>(
    val start: L,
    val end: L,
)
