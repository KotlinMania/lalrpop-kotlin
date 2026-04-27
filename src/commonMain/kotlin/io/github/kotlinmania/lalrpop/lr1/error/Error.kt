// port-lint: source src/lr1/error/mod.rs
//! Error reporting. For now very stupid and simplistic.
package io.github.kotlinmania.lalrpop.lr1.error

import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.collections.set.Set
import io.github.kotlinmania.lalrpop.collections.set.set
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.grammar.repr.toContent
import io.github.kotlinmania.lalrpop.grammar.parseTree.toContent
import io.github.kotlinmania.lalrpop.lr1.lookahead.Token
import io.github.kotlinmania.lalrpop.lr1.lookahead.TokenSet
import io.github.kotlinmania.lalrpop.lr1.core.Action
import io.github.kotlinmania.lalrpop.lr1.core.Conflict
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.Lr0Item
import io.github.kotlinmania.lalrpop.lr1.core.Lr1Conflict
import io.github.kotlinmania.lalrpop.lr1.core.Lr1State
import io.github.kotlinmania.lalrpop.lr1.core.Lr1TableConstructionError
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.lr1.example.Example
import io.github.kotlinmania.lalrpop.lr1.example.ExampleStyles
import io.github.kotlinmania.lalrpop.lr1.example.ExampleSymbol
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.trace.Tracer
import io.github.kotlinmania.lalrpop.lr1.trace.reduce.backtraceReduce
import io.github.kotlinmania.lalrpop.lr1.trace.shift.backtraceShift
import io.github.kotlinmania.lalrpop.message.builder.Builder
import io.github.kotlinmania.lalrpop.message.builder.BodyCharacter
import io.github.kotlinmania.lalrpop.message.builder.Character
import io.github.kotlinmania.lalrpop.message.Message
import io.github.kotlinmania.lalrpop.message.builder.MessageBuilder
import io.github.kotlinmania.lalrpop.tls.Tls
import io.github.kotlinmania.lalrpop.lr1.lookahead.Lookahead

fun reportError(
    grammar: Grammar,
    error: Lr1TableConstructionError,
    reporter: (Message) -> Unit,
) {
    val cx = ErrorReportingCx.new(grammar, error.states, error.conflicts)
    cx.reportErrors(reporter)
}

internal class ErrorReportingCx private constructor(
    val grammar: Grammar,
    val firstSets: FirstSets,
    val states: List<Lr1State>,
    val conflicts: List<Lr1Conflict>,
) {
    companion object {
        fun new(
            grammar: Grammar,
            states: List<Lr1State>,
            conflicts: List<Lr1Conflict>,
        ): ErrorReportingCx = ErrorReportingCx(
            grammar = grammar,
            firstSets = FirstSets.new(grammar),
            states = states,
            conflicts = conflicts,
        )
    }

    fun reportErrors(reporter: (Message) -> Unit) {
        for (conflictGroup in tokenConflicts(this.conflicts)) {
            val classified: List<Pair<TokenConflict, ConflictClassification>> =
                conflictGroup.map { c -> Pair(c, this.classify(c)) }
            val (naiveMutable, betterConflicts) =
                classified.partition { it.second is ConflictClassification.Naive }
            val naiveConflicts = naiveMutable.toMutableList()
            val conflicts: List<Pair<TokenConflict, ConflictClassification>> =
                if (betterConflicts.isEmpty()) {
                    // If we have a reduce/reduce conflict, we end up with one conflict per token, but
                    // they're all the same reduce/reduce. We don't have a meaningful way to determine
                    // if some lookahead token will be more valuable to the user, so just take the
                    // first one and drop the rest as redundant
                    if (naiveConflicts.firstOrNull()?.first?.action is Action.Reduce) {
                        while (naiveConflicts.size > 1) {
                            naiveConflicts.removeLast()
                        }
                    }
                    naiveConflicts
                } else {
                    betterConflicts
                }
            for ((conflict, conflictClass) in conflicts) {
                reporter(this.reportError(conflict, conflictClass))
            }
        }
    }

    fun reportError(
        conflict: TokenConflict,
        conflictClass: ConflictClassification,
    ): Message = when (conflictClass) {
        is ConflictClassification.Ambiguity ->
            this.reportErrorAmbiguity(conflict, conflictClass.action, conflictClass.reduce)
        is ConflictClassification.Precedence ->
            this.reportErrorPrecedence(
                conflict,
                conflictClass.shift,
                conflictClass.reduce,
                conflictClass.nonterminal,
            )
        is ConflictClassification.SuggestInline ->
            this.reportErrorSuggestInline(
                conflict,
                conflictClass.shift,
                conflictClass.reduce,
                conflictClass.nonterminal,
            )
        is ConflictClassification.SuggestQuestion ->
            this.reportErrorSuggestQuestion(
                conflict,
                conflictClass.shift,
                conflictClass.reduce,
                conflictClass.nonterminal,
                conflictClass.symbol,
            )
        is ConflictClassification.AmbiguousReduction ->
            this.reportErrorAmbiguousReduction(
                conflictClass.reduce,
                conflictClass.span1,
                conflictClass.span2,
            )
        is ConflictClassification.InsufficientLookahead ->
            this.reportErrorInsufficientLookahead(conflict, conflictClass.action, conflictClass.reduce)
        is ConflictClassification.Naive ->
            this.reportErrorNaive(conflict)
    }

    fun reportErrorAmbiguityCore(
        conflict: TokenConflict,
        shift: Example,
        reduce: Example,
    ): Builder<MessageBuilder> {
        val styles = ExampleStyles.ambig()
        return MessageBuilder.new(conflict.production.span)
            .heading()
            .text("Ambiguous grammar detected")
            .end()
            .body()
            .beginLines()
            .wrapText("The following symbols can be reduced in two ways:")
            .push(reduce.toSymbolList(reduce.symbols.size, styles))
            .end()
            .beginLines()
            .wrapText("They could be reduced like so:")
            .push(reduce.intoPicture(styles))
            .end()
            .beginLines()
            .wrapText("Alternatively, they could be reduced like so:")
            .push(shift.intoPicture(styles))
            .end()
    }

    fun reportErrorAmbiguity(
        conflict: TokenConflict,
        shift: Example,
        reduce: Example,
    ): Message =
        this.reportErrorAmbiguityCore(conflict, shift, reduce)
            .wrapText(
                "LALRPOP does not yet support ambiguous grammars. " +
                    "See the LALRPOP manual for advice on " +
                    "making your grammar unambiguous.",
            )
            .end()
            .end()

    fun reportErrorPrecedence(
        conflict: TokenConflict,
        shift: Example,
        reduce: Example,
        nonterminal: NonterminalString,
    ): Message =
        this.reportErrorAmbiguityCore(conflict, shift, reduce)
            .beginWrap()
            .text("Hint:")
            .styled(Tls.session().hintText)
            .text("This looks like a precedence error related to")
            .push(nonterminal.toContent())
            .verbatimed()
            .punctuated(".")
            .text("See the LALRPOP manual for advice on encoding precedence.")
            .end()
            .end()
            .end()

    fun reportErrorNotLr1Core(
        conflict: TokenConflict,
        action: Example,
        reduce: Example,
    ): Builder<MessageBuilder> {
        val styles = ExampleStyles.new()
        val base = MessageBuilder.new(conflict.production.span)
            .heading()
            .text("Local ambiguity detected")
            .end()
            .body()

        val afterSymbols = base
            .beginLines()
            .beginWrap()
            .text("The problem arises after having observed the following symbols")
            .text("in the input:")
            .end()
            .push(
                if (action.cursor >= reduce.cursor) {
                    action.toSymbolList(action.cursor, styles)
                } else {
                    reduce.toSymbolList(reduce.cursor, styles)
                },
            )
            .beginWrap()

        val afterLookahead = when (val look = conflict.lookahead) {
            is Token.Terminal -> afterSymbols
                .text("At that point, if the next token is a")
                .push(look.terminalString.toContent())
                .verbatimed()
                .styled(Tls.session().cursorSymbol)
                .punctuated(",")
            Token.Error -> afterSymbols.text("If an error has been found,")
            Token.Eof -> afterSymbols.text("If the end of the input is reached,")
        }

        val afterSplit = afterLookahead
            .text("then the parser can proceed in two different ways.")
            .end()
            .end()

        val withReduce = this.describeReduce(afterSplit, styles, conflict.production, reduce, "First")

        return when (val action0 = conflict.action) {
            is Action.Shift ->
                this.describeShift(withReduce, styles, action0.terminal, action, "Alternatively")
            is Action.Reduce ->
                this.describeReduce(withReduce, styles, action0.production, action, "Alternatively")
        }
    }

    fun <End> describeShift(
        builder: Builder<End>,
        styles: ExampleStyles,
        lookahead: TerminalString,
        example: Example,
        introWord: String,
    ): Builder<End> {
        // A shift example looks like:
        //
        // ...p1 ...p2 (*) L ...s2 ...s1
        // |     |               |     |
        // |     +-NT1-----------+     |
        // |                           |
        // |           ...             |
        // |                           |
        // +-NT2-----------------------+

        val nt1 = example.reductions[0].nonterminal

        return builder
            .beginLines()
            .beginWrap()
            .text(introWord)
            .punctuated(",")
            .text("the parser could shift the")
            .push(lookahead.toContent())
            .verbatimed()
            .text("token and later use it to construct a")
            .push(nt1.toContent())
            .verbatimed()
            .punctuated(".")
            .text("This might then yield a parse tree like")
            .end()
            .push(example.intoPicture(styles))
            .end()
    }

    fun <End> describeReduce(
        builder: Builder<End>,
        styles: ExampleStyles,
        production: Production,
        example: Example,
        introWord: String,
    ): Builder<End> =
        builder
            .beginLines()
            .beginWrap()
            .text(introWord)
            .punctuated(",")
            .text("the parser could execute the production at")
            .push(production.span.toContent())
            .punctuated(",")
            .text("which would consume the top")
            .text(production.symbols.size)
            .text("token(s) from the stack")
            .text("and produce a")
            .push(production.nonterminal.toContent())
            .verbatimed()
            .punctuated(".")
            .text("This might then yield a parse tree like")
            .end()
            .push(example.intoPicture(styles))
            .end()

    fun reportErrorSuggestInline(
        conflict: TokenConflict,
        shift: Example,
        reduce: Example,
        nonterminal: NonterminalString,
    ): Message {
        val builder = this.reportErrorNotLr1Core(conflict, shift, reduce)

        return builder
            .beginWrap()
            .text("Hint:")
            .styled(Tls.session().hintText)
            .text("It appears you could resolve this problem by adding")
            .text("the attribute `#[inline]` to the definition of")
            .push(nonterminal.toContent())
            .verbatimed()
            .punctuated(".")
            .text("For more information, see the section on inlining")
            .text("in the LALRPOP manual.")
            .end()
            .end()
            .end()
    }

    fun reportErrorSuggestQuestion(
        conflict: TokenConflict,
        shift: Example,
        reduce: Example,
        nonterminal: NonterminalString,
        symbol: Symbol,
    ): Message {
        val builder = this.reportErrorNotLr1Core(conflict, shift, reduce)

        return builder
            .beginWrap()
            .text("Hint:")
            .styled(Tls.session().hintText)
            .text("It appears you could resolve this problem by replacing")
            .text("uses of")
            .push(nonterminal.toContent())
            .verbatimed()
            .text("with")
            .text(symbol) // intentionally disable coloring here, looks better
            .adjacentText("`", "?`")
            .text(
                "(or, alternatively, by adding the attribute `#[inline]` " +
                    "to the definition of",
            )
            .push(nonterminal.toContent())
            .punctuated(").")
            .text("For more information, see the section on inlining")
            .text("in the LALRPOP manual.")
            .end()
            .end()
            .end()
    }

    fun reportErrorAmbiguousReduction(
        reduce: Example,
        span1: Span,
        span2: Span,
    ): Message {
        val fileText = Tls.fileText()
        val styles = ExampleStyles.new()
        val span1Str = fileText.spanText(span1)
        val span2Str = fileText.spanText(span2)

        // Internal lines are 0-indexed, but editors are (always?) 1-indexed
        val span1Line = fileText.lineCol(span1.start).first + 1
        val span2Line = fileText.lineCol(span2.start).first + 1

        return MessageBuilder.new(span1)
            .heading()
            .text("Multiple productions for the same reduction")
            .end()
            .body()
            .beginLines()
            .wrapText(
                "The following symbols can be reduced into a ${reduce.reductions.first().nonterminal} in two ways"
            )
            .push(reduce.toSymbolList(reduce.symbols.size, styles))
            .wrapText(
                "They could be reduced using the production on line $span1Line:"
            )
            .wrapText(span1Str)
            .wrapText("...or using the production on line $span2Line:")
            .wrapText(span2Str)
            .end()
            .end()
            .end()
    }

    fun reportErrorInsufficientLookahead(
        conflict: TokenConflict,
        action: Example,
        reduce: Example,
    ): Message {
        // The reduce example will look something like:
        //
        //
        // ...p1 ...p2 (*) L ...s2 ...s1
        // |     |               |     |
        // |     +-NT1-----------+     |
        // |     |               |     |
        // |     +-...-----------+     |
        // |     |               |     |
        // |     +-NTn-----------+     |
        // |                           |
        // +-NTn+1---------------------+
        //
        // To solve the conflict, essentially, the user needs to
        // modify the grammar so that `NTn` does not appear with `L`
        // in its follow-set. How to guide them in this?

        val builder = this.reportErrorNotLr1Core(conflict, action, reduce)

        return builder
            .wrapText(
                "See the LALRPOP manual for advice on " +
                    "making your grammar LR(1).",
            )
            .end()
            .end()
    }

    /**
     * Naive error reporting. This is a fallback path which (I think)
     * never actually executes.
     */
    fun reportErrorNaive(conflict: TokenConflict): Message {
        var builder: Builder<Builder<MessageBuilder>> = MessageBuilder.new(conflict.production.span)
            .heading()
            .text("Conflict detected")
            .end()
            .body()
            .beginLines()
            .wrapText("when in this state:")
            .indented()
        for (item in this.states[conflict.state.value].items.vec) {
            builder = builder.text("$item")
        }
        var afterWrap: Builder<Builder<MessageBuilder>> = builder
            .end()
            .beginWrap()
            .text("and looking at a token `${conflict.lookahead}`")
            .text("we can reduce to a")
            .push(conflict.production.nonterminal.toContent())
            .verbatimed()
        afterWrap = when (val action = conflict.action) {
            is Action.Shift -> afterWrap.text("but we can also shift")
            is Action.Reduce -> afterWrap
                .text("but we can also reduce to a")
                .text(action.production.nonterminal)
                .verbatimed()
        }
        return afterWrap.end().end().end()
    }

    fun classify(conflict: TokenConflict): ConflictClassification {
        // Find examples from the conflicting action (either a shift
        // or a reduce).
        val actionExamples: MutableList<Example> = when (val action = conflict.action) {
            is Action.Shift -> this.shiftExamples(conflict)
            is Action.Reduce -> this.reduceExamples(conflict.state, action.production, conflict.lookahead)
        }

        // Find examples from the conflicting reduce.
        val reduceExamples: MutableList<Example> = this.reduceExamples(
            conflict.state,
            conflict.production,
            conflict.lookahead,
        )

        // Prefer shorter examples to longer ones.
        actionExamples.sortBy { it.symbols.size }
        reduceExamples.sortBy { it.symbols.size }

        // This really shouldn't happen, but if we've failed to come
        // up with examples, then report a "naive" error.
        if (actionExamples.isEmpty() || reduceExamples.isEmpty()) {
            return ConflictClassification.Naive
        }

        val ambiguity = this.tryClassifyAmbiguity(conflict, actionExamples, reduceExamples)
        if (ambiguity != null) return ambiguity

        val question = this.tryClassifyQuestion(conflict, actionExamples, reduceExamples)
        if (question != null) return question

        val inline = this.tryClassifyInline(conflict, actionExamples, reduceExamples)
        if (inline != null) return inline

        // Give up. Just grab an example from each and pair them up.
        // If there aren't even two examples, something's pretty
        // bogus, but we'll just call it naive.
        return actionExamples.asSequence().zip(reduceExamples.asSequence())
            .firstOrNull()
            ?.let { (action, reduce) ->
                ConflictClassification.InsufficientLookahead(action = action, reduce = reduce)
            }
            ?: ConflictClassification.Naive
    }

    fun tryClassifyAmbiguity(
        conflict: TokenConflict,
        actionExamples: List<Example>,
        reduceExamples: List<Example>,
    ): ConflictClassification? =
        actionExamples.asSequence()
            .flatMap { a -> reduceExamples.asSequence().map { r -> Pair(a, r) } }
            .filter { (action, reduce) -> action.symbols == reduce.symbols }
            .filter { (action, reduce) -> action.cursor == reduce.cursor }
            .map { (action, reduce) ->
                // Consider whether to call this a precedence
                // error. We do this if we are stuck between reducing
                // `T = T S T` and shifting `S`.
                if (conflict.action is Action.Shift) {
                    val term = conflict.action.terminal
                    val nt = conflict.production.nonterminal
                    if (conflict.production.symbols.size == 3 &&
                        conflict.production.symbols[0] == Symbol.Nonterminal(nt) &&
                        conflict.production.symbols[1] == Symbol.Terminal(term) &&
                        conflict.production.symbols[2] == Symbol.Nonterminal(nt)
                    ) {
                        return@map ConflictClassification.Precedence(
                            shift = action,
                            reduce = reduce,
                            nonterminal = nt,
                        )
                    }
                } else if (conflict.action is Action.Reduce) {
                    val prod = conflict.action.production
                    if (action.reductions.firstOrNull()?.nonterminal ==
                        reduce.reductions.firstOrNull()?.nonterminal
                    ) {
                        return@map ConflictClassification.AmbiguousReduction(
                            reduce = action,
                            span1 = conflict.production.span,
                            span2 = prod.span,
                        )
                    }
                }
                ConflictClassification.Ambiguity(
                    action = action,
                    reduce = reduce,
                )
            }
            .firstOrNull()

    fun tryClassifyQuestion(
        conflict: TokenConflict,
        actionExamples: List<Example>,
        reduceExamples: List<Example>,
    ): ConflictClassification? {
        // If we get a shift/reduce conflict and the reduce
        // is of a nonterminal like:
        //
        //     T = { () | U }
        //
        // then suggest replacing T with U?. I'm being a bit lenient
        // here since I do not KNOW that it will help, but it often
        // does, and it's better style anyhow.

        if (conflict.action is Action.Reduce) {
            return null
        }

        Tls.session().log.log(Level.Debug) {
            "try_classify_question: action_examples=$actionExamples"
        }
        Tls.session().log.log(Level.Debug) {
            "try_classify_question: reduce_examples=$reduceExamples"
        }

        val nt = conflict.production.nonterminal
        val ntProductions = this.grammar.productionsFor(nt)
        if (ntProductions.size == 2) {
            for ((i, j) in listOf(Pair(0, 1), Pair(1, 0))) {
                if (ntProductions[i].symbols.isEmpty() && ntProductions[j].symbols.size == 1) {
                    return ConflictClassification.SuggestQuestion(
                        shift = actionExamples[0],
                        reduce = reduceExamples[0],
                        nonterminal = nt,
                        symbol = ntProductions[j].symbols[0],
                    )
                }
            }
        }

        return null
    }

    fun tryClassifyInline(
        conflict: TokenConflict,
        actionExamples: List<Example>,
        reduceExamples: List<Example>,
    ): ConflictClassification? {
        // Inlining can help resolve a shift/reduce conflict because
        // it defers the need to reduce. In particular, if we inlined
        // all the reductions up until the last one, then we would be
        // able to *shift* the lookahead instead of having to reduce.
        // This can be helpful if we can see that shifting would let
        // us delay reducing until the lookahead diverges.

        // Only applicable to shift/reduce:
        if (conflict.action is Action.Reduce) {
            return null
        }

        // FIXME: The logic here finds the first example where inline
        // would help; but maybe we want to restrict it to cases
        // where inlining would help *all* the examples...?

        return actionExamples.asSequence()
            .flatMap { a -> reduceExamples.asSequence().map { r -> Pair(a, r) } }
            .mapNotNull { (shift, reduce) ->
                if (this.tryClassifyInlineExample(shift, reduce)) {
                    val nt = reduce.reductions[0].nonterminal
                    ConflictClassification.SuggestInline(
                        shift = shift,
                        reduce = reduce,
                        nonterminal = nt,
                    )
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    fun tryClassifyInlineExample(shift: Example, reduce: Example): Boolean {
        Tls.session().log.log(Level.Debug) {
            "try_classify_inline_example($shift, $reduce)"
        }

        // In the case of shift, the example will look like
        //
        // ```
        // ... ... (*) L ...s1 ...
        // |   |             |   |
        // |   +-R0----------+   |
        // |  ...                |
        // +-Rn------------------+
        // ```
        //
        // We want to extract the symbols ...s1: these are the
        // things we are able to shift before being forced to
        // make our next hard decision (to reduce R0 or not).
        val shiftUpcoming = shift.symbols.subList(shift.cursor + 1, shift.reductions[0].end)
        Tls.session().log.log(Level.Debug) {
            "try_classify_inline_example: shift_upcoming=$shiftUpcoming"
        }

        // For the reduce, the example might look like
        //
        // ```
        // ...  ...   (*) ...s ...
        // | | |    |        |
        // | | +-R0-+        |
        // | | ...  |        |
        // | +--Ri--+        |
        // |  ...            |
        // +-R(i+1)----------+
        // ```
        //
        // where Ri is the last reduction that requires
        // shifting no additional symbols. In this case, if we
        // inlined R0...Ri, then we know we can shift L.
        val r0End = reduce.reductions[0].end
        val i = reduce.reductions.indexOfFirst { r -> r.end != r0End }
        if (i < 0) {
            return false
        }
        val ri = reduce.reductions[i]
        val reduceUpcoming = reduce.symbols.subList(r0End, ri.end)
        Tls.session().log.log(Level.Debug) {
            "try_classify_inline_example: reduce_upcoming=$reduceUpcoming i=$i"
        }

        // For now, we only suggest inlining a single nonterminal,
        // mostly because I am too lazy to weak the suggestion struct
        // and error messages (but the rest of the code below doesn't
        // make this assumption for the most part).
        if (i != 1) {
            return false
        }

        // Make sure that all the things we are suggesting inlining
        // are distinct so that we are not introducing a cycle.
        val duplicates: Set<NonterminalString> = set()
        if (reduce.reductions.subList(0, i + 1).any { r -> !duplicates.add(r.nonterminal) }) {
            return false
        }

        // Compare the two suffixes to see whether they
        // diverge at some point.
        return shiftUpcoming.asSequence().zip(reduceUpcoming.asSequence())
            .mapNotNull { (shiftSym, reduceSym) ->
                if (shiftSym is ExampleSymbol.SymbolValue && reduceSym is ExampleSymbol.SymbolValue) {
                    if (shiftSym.symbol == reduceSym.symbol) {
                        // same symbol on both; we'll be able to shift them
                        null
                    } else {
                        // different symbols: for this to work, must
                        // have disjoint first sets. Note that we
                        // consider a suffix matching epsilon to be
                        // potentially overlapping, though we could
                        // supply the actual lookahead for more precision.
                        val shiftFirst = this.firstSets.first0(listOf(shiftSym.symbol))
                        val reduceFirst = this.firstSets.first0(listOf(reduceSym.symbol))
                        shiftFirst.isDisjoint(reduceFirst)
                    }
                } else {
                    // we don't expect to encounter any
                    // epsilons, I don't think, because those
                    // only occur with an empty reduce at the
                    // top level
                    false
                }
            }
            .firstOrNull()
            ?: false
    }

    fun shiftExamples(conflict: TokenConflict): MutableList<Example> {
        Tls.session().log.log(Level.Verbose) { "Gathering shift examples" }
        val state = this.states[conflict.state.value]
        val conflictingItems = this.conflictingShiftItems(state, conflict)
        val out: MutableList<Example> = mutableListOf()
        for (item in conflictingItems) {
            val tracer = Tracer.new(this.firstSets, this.states)
            val shiftTrace = tracer.backtraceShift(conflict.state, item)
            val localExamples: MutableList<Example> = mutableListOf()
            val iter = shiftTrace.lr0Examples(item)
            while (iter.hasNext()) {
                localExamples.add(iter.next())
            }
            out.addAll(localExamples)
        }
        return out
    }

    fun reduceExamples(
        state: StateIndex,
        production: Production,
        lookahead: Token,
    ): MutableList<Example> {
        Tls.session().log.log(Level.Verbose) { "Gathering reduce examples" }
        val item = Item(
            production = production,
            index = production.symbols.size,
            lookahead = TokenSet.from(lookahead),
        )
        val tracer = Tracer.new(this.firstSets, this.states)
        val reduceTrace = tracer.backtraceReduce(state, item.toLr0())
        val iter = reduceTrace.lr1Examples(this.firstSets, item)
        val out: MutableList<Example> = mutableListOf()
        while (iter.hasNext()) {
            out.add(iter.next())
        }
        return out
    }

    fun conflictingShiftItems(
        state: Lr1State,
        conflict: TokenConflict,
    ): Set<Lr0Item> {
        // Lookahead must be a terminal, not EOF.
        // Find an item J like `Bar = ... (*) L ...`.
        val lookahead: Symbol = Symbol.Terminal(conflict.lookahead.unwrapTerminal())
        val out: Set<Lr0Item> = set()
        for (i in state.items.vec) {
            if (!i.canShift()) continue
            if (i.production.symbols[i.index] != lookahead) continue
            out.add(i.toLr0())
        }
        return out
    }
}

internal sealed class ConflictClassification {
    /**
     * The grammar is ambiguous. This means we have two examples of
     * precisely the same set of symbols which can be reduced in two
     * distinct ways.
     */
    data class Ambiguity(val action: Example, val reduce: Example) : ConflictClassification()

    /**
     * The grammar is ambiguous, and moreover it looks like a
     * precedence error. This means that the reduction is to a
     * nonterminal `T` and the shift is some symbol sandwiched
     * between two instances of `T`.
     */
    data class Precedence(
        val shift: Example,
        val reduce: Example,
        val nonterminal: NonterminalString,
    ) : ConflictClassification()

    /**
     * Suggest inlining `nonterminal`. Makes sense if there are two
     * levels in the reduction tree in both examples, and the suffix
     * after the inner reduction is the same in all cases.
     */
    data class SuggestInline(
        val shift: Example,
        val reduce: Example,
        val nonterminal: NonterminalString,
    ) : ConflictClassification()

    /**
     * Like the previous, but suggest replacing `nonterminal` with
     * `symbol?`. Makes sense if the thing to be inlined consists of
     * two alternatives, `X = symbol | ()`.
     */
    data class SuggestQuestion(
        val shift: Example,
        val reduce: Example,
        val nonterminal: NonterminalString,
        val symbol: Symbol,
    ) : ConflictClassification()

    /**
     * We have two matching sets of symbols that can reduce to the same
     * nonterminal.  This is particularly likely with macros, such as a rule
     * like `X = Y | Y Z?`.  In this case, the normal ambiguity message seems
     * to say the same thing twice, which is confusing, so clarify
     */
    data class AmbiguousReduction(
        val reduce: Example,
        val span1: Span,
        val span2: Span,
    ) : ConflictClassification()

    /** Can't say much beyond that a conflict occurred. */
    data class InsufficientLookahead(val action: Example, val reduce: Example) : ConflictClassification()

    /** Really can't say *ANYTHING*. */
    object Naive : ConflictClassification()
}

internal typealias TokenConflict = Conflict<Token>

internal fun tokenConflicts(
    conflicts: List<Conflict<TokenSet>>,
): List<List<TokenConflict>> =
    conflicts.map { conflict ->
        val out: MutableList<TokenConflict> = mutableListOf()
        for (token in conflict.lookahead) {
            out.add(
                Conflict(
                    state = conflict.state,
                    lookahead = token,
                    production = conflict.production,
                    action = conflict.action,
                )
            )
        }
        out
    }
