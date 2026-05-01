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
class Production<S, L>(
    val nonterminalId: Short,
    val rhsLength: Int,
    val action: ProductionAction<S, L>,
)

/**
 * A production action — typed lambda contract.
 *
 * Defined as a `fun interface` rather than a plain `(…) -> S` function type so the
 * generated tables can name the parameters at the call site, which makes generated
 * action bodies easier to read in stack traces and IDE tooltips.
 */
fun interface ProductionAction<S, L> {
    fun reduce(stack: ParseStack<S, L>, span: ProductionSpan<L>): S
}

/**
 * Source span covered by a single reduction.
 *
 * `start` is the start location of the leftmost RHS symbol; `end` is the end location of
 * the rightmost. For empty (epsilon) productions both endpoints collapse to the lookahead
 * location at the point of reduction. The driver computes these from the popped stack
 * entries before invoking the action.
 */
data class ProductionSpan<L>(
    val start: L,
    val end: L,
)
