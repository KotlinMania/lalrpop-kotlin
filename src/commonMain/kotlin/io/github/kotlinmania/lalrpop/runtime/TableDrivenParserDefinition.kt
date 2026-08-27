package io.github.kotlinmania.lalrpop.runtime

/**
 * A [ParserDefinition] implemented as a thin shim over [ParseTables] plus a small set of
 * per-grammar callbacks.
 *
 * This is the integration point for the data-driven design: a generated parser declares
 * its own `Symbol` sealed class, its `ParseTables`, and a handful of callbacks (token →
 * terminal-id, token → symbol, throwable → parse error), wraps them in this class, and
 * hands the result to [Parser.drive]. The same well-tested [Parser] driver loop walks
 * the data-driven definition that walks today's hand-translated [LrGrammar.kt].
 *
 * Strict typing throughout: the `Success` produced is the same `S` (per-grammar Symbol
 * sealed class) the action lambdas return. Callers cast with `is` against the start
 * nonterminal's variant if they want a tighter type at the call site.
 *
 * Index types are concrete:
 * - `TokenIndex = Int`         — terminal id, indexes into [ParseTables.action] columns.
 * - `StateIndex = Int`         — state id, indexes into [ParseTables.action] rows.
 * - `ReduceIndex = Int`        — production id, indexes into [ParseTables.productions].
 * - `NonterminalIndex = Int`   — nonterminal id, indexes into [ParseTables.goto] columns.
 * - `Action = IntAction`     — action encoding shared with the existing `LrGrammar.kt`.
 * - `Success = S`              — the start production's produced symbol.
 */
internal class TableDrivenParserDefinition<S, L, T, E>(
    private val tables: ParseTables<S, L>,
    /** Maps a lexer token to its terminal id, or `null` if the token is unrecognized. */
    private val tokenToTerminalId: (T) -> Int?,
    /** Wraps a recognized lexer token in the corresponding Symbol variant. */
    private val tokenToSymbol: (terminalId: Int, token: T) -> S,
    /**
     * Maps the throwable a fallible production action returned (`Result.failure(cause)`)
     * to a typed [ParseError]. The codegen for a generated grammar plants typed
     * exceptions inside its action lambdas; this callback is where it unwraps them so
     * the parse failure surfaces as a structured error rather than a bare throwable.
     */
    private val mapActionFailure: (Throwable) -> ParseError<L, T, E>,
    /** The location to report for the parse start. */
    private val initialLocation: L,
    /** True if the grammar declares `extern Token { ... ! ... }` for error recovery. */
    private val supportsErrorRecovery: Boolean,
    /** Wraps a recovered error span in a Symbol variant the action lambdas can consume. */
    private val errorRecoverySymbolOf: (ErrorRecovery<L, T, E>) -> S,
    /** Per-state list of human-readable expected-terminal names, used in error messages. */
    private val expectedTokensFor: (state: Int) -> List<String>,
) : ParserDefinition<L, E, T, Int, S, S, Int, IntAction, Int, Int> {

    override fun startLocation(): L = initialLocation

    override fun startState(): Int = 0

    override fun tokenToIndex(token: T): Int? = tokenToTerminalId(token)

    override fun action(state: Int, tokenIndex: Int): IntAction =
        IntAction(tables.actionAt(state, tokenIndex).toInt())

    override fun errorAction(state: Int): IntAction {
        // The error-action column is appended after the regular terminal columns in the
        // upstream LALRPOP encoding. A grammar without error recovery has no such column;
        // we report 0 (error) so the driver falls through to its no-recovery path.
        if (!supportsErrorRecovery) return IntAction(0)
        // Generated tables that DO support recovery store the error column at the end of
        // each row. Codegen will plumb this through; the current shim returns 0 until
        // that path is added.
        return IntAction(0)
    }

    override fun eofAction(state: Int): IntAction =
        IntAction(tables.eofActionAt(state).toInt())

    override fun goto(state: Int, nt: Int): Int =
        tables.gotoAt(state, nt)

    override fun tokenToSymbol(tokenIndex: Int, token: T): S =
        tokenToSymbol.invoke(tokenIndex, token)

    override fun expectedTokens(state: Int): List<String> = expectedTokensFor(state)

    override fun usesErrorRecovery(): Boolean = supportsErrorRecovery

    override fun errorRecoverySymbol(recovery: ErrorRecovery<L, T, E>): S =
        errorRecoverySymbolOf(recovery)

    override fun reduce(
        reduceIndex: Int,
        startLocation: L?,
        states: MutableList<Int>,
        symbols: MutableList<Triple<L, S, L>>,
    ): ParseResult<S, L, T, E>? {
        val production = tables.productions[reduceIndex]
        val n = production.rhsLength

        // Pop the popped run off symbols (preserving stack order so the action lambda
        // sees the correct rightmost-first ordering on its reified pops). Pop the same
        // count off states.
        check(symbols.size >= n) {
            "reduce $reduceIndex underflow: have ${symbols.size}, need $n"
        }
        val popped = ArrayList<Triple<L, S, L>>(n)
        for (i in 0 until n) {
            popped.add(symbols[symbols.size - n + i])
        }
        repeat(n) { symbols.removeLast() }
        repeat(n) { states.removeLast() }

        val span: ProductionSpan<L> = if (popped.isNotEmpty()) {
            ProductionSpan(popped.first().first, popped.last().third)
        } else {
            // Empty (epsilon) production. Anchor to the lookahead's start, falling back
            // to the previous symbol's end, then to the parser's initial location.
            val anchor = startLocation
                ?: symbols.lastOrNull()?.third
                ?: initialLocation
            ProductionSpan(anchor, anchor)
        }

        val actionStack = ParseStack<S, L>(initialCapacity = n)
        for (entry in popped) actionStack.push(entry.first, entry.second, entry.third)

        val result = production.action.reduce(actionStack, span)

        check(actionStack.isEmpty()) {
            "production $reduceIndex left ${actionStack.size} unpopped symbols — " +
            "action lambda doesn't match declared rhsLength $n"
        }

        val produced = result.getOrElse { cause ->
            return ParseResult.Failure(mapActionFailure(cause))
        }

        if (reduceIndex == tables.acceptProductionId) {
            return ParseResult.Success(produced)
        }

        val newTopState = states.last()
        val nextState = tables.gotoAt(newTopState, production.nonterminalId.toInt())
        symbols.add(Triple(span.start, produced, span.end))
        states.add(nextState)
        return null
    }

    override fun simulateReduce(action: Int): SimulatedReduce<Int> {
        val production = tables.productions[action]
        if (action == tables.acceptProductionId) {
            return SimulatedReduce.Accept
        }
        return SimulatedReduce.Reduce(
            statesToPop = production.rhsLength,
            nonterminalProduced = production.nonterminalId.toInt(),
        )
    }
}
