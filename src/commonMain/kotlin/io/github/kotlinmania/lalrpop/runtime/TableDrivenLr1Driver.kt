package io.github.kotlinmania.lalrpop.runtime

/**
 * A grammar-agnostic LR(1) driver that consumes [ParseTables] plus a token stream and
 * returns a typed result.
 *
 * This is the per-grammar shim that data-driven generated parsers route through. The
 * generated parser declares its own [ParseTables] (packed transition arrays + typed
 * productions array + the per-grammar `Symbol` sealed class) and a tiny entry point
 * that constructs this driver and calls [parse].
 *
 * Strict-typing constraint: the result type [Tree] is the start nonterminal's symbol
 * type — exposed as a generic so the entry point can declare a typed return without
 * widening to `S` or `Any`. The cast at the accept step is checked against the sealed
 * `Symbol` hierarchy via the same reified-pop machinery action lambdas use.
 *
 * Today this driver is standalone — it does not implement the existing
 * [ParserDefinition] interface in `StateMachine.kt`. Step 1.5 of the migration will
 * either widen this to a `ParserDefinition` adapter (so the existing well-tested
 * [Parser] driver loop walks it) or replace `Parser` with this driver entirely. Both
 * paths leave the action-lambda contract — which is what generated code commits to —
 * untouched, so locking the contract here is what step 1 needs.
 */
internal class TableDrivenLr1Driver<S, L>(
    private val tables: ParseTables<S, L>,
    private val tokens: Iterator<TerminalToken<S, L>>,
    private val eofLocation: L,
) {
    private val stateStack: ArrayDeque<Int> = ArrayDeque<Int>().apply { addLast(0) }
    private val symbolStack: ParseStack<S, L> = ParseStack(initialCapacity = 32)

    /** Drive the parser until accept, error, or end of token stream. */
    fun parse(): ParseOutcome<S, L> {
        var lookahead: TerminalToken<S, L>? = if (tokens.hasNext()) tokens.next() else null

        while (true) {
            val topState = stateStack.last()
            val rawAction: Short
            val tokenSpan: ProductionSpan<L>?
            val tokenSymbol: S?
            val tokenTerminalId: Int

            if (lookahead != null) {
                rawAction = tables.actionAt(topState, lookahead.terminalId)
                tokenSpan = ProductionSpan(lookahead.start, lookahead.end)
                tokenSymbol = lookahead.symbol
                tokenTerminalId = lookahead.terminalId
            } else {
                rawAction = tables.eofActionAt(topState)
                tokenSpan = null
                tokenSymbol = null
                tokenTerminalId = -1
            }

            when {
                rawAction > 0 -> {
                    // Shift — push the lookahead onto both stacks, advance.
                    val targetState = rawAction.toInt() - 1
                    val tok = lookahead
                        ?: error("driver shifted on EOF — table compiled wrong at state $topState")
                    symbolStack.push(tok.start, tok.symbol, tok.end)
                    stateStack.addLast(targetState)
                    lookahead = if (tokens.hasNext()) tokens.next() else null
                }

                rawAction < 0 -> {
                    // Reduce — pop rhsLength entries, run the action, push the produced
                    // symbol with its computed span, then take the GOTO transition.
                    val productionId = -(rawAction.toInt() + 1)
                    val production = tables.productions[productionId]
                    val popped = symbolStack.popLocatedRun(production.rhsLength)
                    repeat(production.rhsLength) { stateStack.removeLast() }

                    // Span of the reduction: leftmost popped start to rightmost popped
                    // end. For an empty (epsilon) production we have nothing popped, so
                    // we collapse to the lookahead's start (or EOF span when there is
                    // no lookahead).
                    val span: ProductionSpan<L> =
                        if (popped.isNotEmpty()) {
                            ProductionSpan(popped.first().start, popped.last().end)
                        } else {
                            val anchor = lookahead?.start ?: eofLocation
                            ProductionSpan(anchor, anchor)
                        }

                    // Build a temporary stack for the action lambda. The lambda's
                    // reified pops drain it in RHS order (rightmost first).
                    val actionStack = ParseStack<S, L>(initialCapacity = popped.size)
                    for (entry in popped) actionStack.push(entry)
                    val result = production.action.reduce(actionStack, span)
                    check(actionStack.isEmpty()) {
                        "production $productionId left ${actionStack.size} unpopped " +
                        "symbols — action lambda doesn't match declared rhsLength " +
                        "${production.rhsLength}"
                    }

                    val produced = result.getOrElse { cause ->
                        // The production's user code signaled failure. Surface it as a
                        // typed parse failure carrying the cause, the production we
                        // were running, and the span we'd computed for it. The driver
                        // does not retry — a fallible reduce that fails terminates the
                        // parse. Error recovery (which would intercept here in a fuller
                        // implementation) lives in step 2 of the migration plan.
                        return ParseOutcome.ProductionFailure(
                            productionId = productionId,
                            span = span,
                            cause = cause,
                        )
                    }

                    if (productionId == tables.acceptProductionId) {
                        return ParseOutcome.Success(produced, span)
                    }

                    val newTopState = stateStack.last()
                    val nextState = tables.gotoAt(newTopState, production.nonterminalId.toInt())
                    symbolStack.push(span.start, produced, span.end)
                    stateStack.addLast(nextState)
                }

                else -> {
                    // Error — no recovery in step 1. Report what we had.
                    return ParseOutcome.Failure(
                        state = topState,
                        unexpectedTerminalId = tokenTerminalId,
                        unexpectedSymbol = tokenSymbol,
                        location = tokenSpan?.start ?: eofLocation,
                    )
                }
            }
        }
    }
}

/**
 * One terminal pulled from the lexer.
 *
 * `terminalId` indexes into [ParseTables.action]'s columns and matches the terminal IDs
 * emitted by the codegen for this grammar.
 *
 * `symbol` is the typed `Symbol` variant the action lambdas will see when this terminal
 * is later consumed by a reduction. Wrapping happens at the lexer/driver boundary so the
 * action lambdas never have to call a `tokenToSymbol` adapter mid-reduction.
 */
internal data class TerminalToken<S, L>(
    val terminalId: Int,
    val symbol: S,
    val start: L,
    val end: L,
)

/**
 * Outcome of a single parse run.
 *
 * Step 1 has no error recovery — error states return immediately with diagnostic info.
 * Step 2 wires this through the existing [ParserDefinition.usesErrorRecovery] machinery.
 *
 * Two distinct failure shapes:
 *
 * - [Failure] is a *table* error: the parse table had `0` (error) for the
 *   `(state, terminal)` cell. The driver caught it before invoking any user code.
 * - [ProductionFailure] is a *user-code* error: the parse table told us to reduce a
 *   fallible production, the action lambda ran, and it returned `Result.failure`. The
 *   driver propagates the cause unchanged.
 */
internal sealed class ParseOutcome<out S, out L> {
    data class Success<out S, out L>(val tree: S, val span: ProductionSpan<L>) :
        ParseOutcome<S, L>()

    data class Failure<out S, out L>(
        val state: Int,
        val unexpectedTerminalId: Int,
        val unexpectedSymbol: S?,
        val location: L,
    ) : ParseOutcome<S, L>()

    data class ProductionFailure<out S, out L>(
        val productionId: Int,
        val span: ProductionSpan<L>,
        val cause: Throwable,
    ) : ParseOutcome<S, L>()
}
