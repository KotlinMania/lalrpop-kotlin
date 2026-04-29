// port-lint: source lr1/codegen/ascent.rs
//! A compiler from an LR(1) table to a [recursive ascent] parser.
//!
//! [recursive ascent]: https://en.wikipedia.org/wiki/RecursiveAscentParser
package io.github.kotlinmania.lalrpop.lr1.codegen

import io.github.kotlinmania.lalrpop.Escape
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.collections.multimap.Multimap
import io.github.kotlinmania.lalrpop.collections.multimap.VecCollection
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.parsetree.Visibility
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause
import io.github.kotlinmania.lalrpop.lr1.StateGraph
import io.github.kotlinmania.lalrpop.lr1.Token
import io.github.kotlinmania.lalrpop.lr1.core.State<TokenSet>
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust
import io.github.kotlinmania.lalrpop.tls.Tls

object Ascent {
    fun compile(
        grammar: Grammar,
        userStartSymbol: NonterminalString,
        startSymbol: NonterminalString,
        states: List<State<TokenSet>>,
        actionModule: String,
        out: RustWrite,
    ) {
        compileAscent(grammar, userStartSymbol, startSymbol, states, actionModule, out)
    }
}

fun compileAscent(
    grammar: Grammar,
    userStartSymbol: NonterminalString,
    startSymbol: NonterminalString,
    states: List<State<TokenSet>>,
    actionModule: String,
    out: RustWrite,
) {
    val graph = StateGraph.new(states)
    val ascent = newAscent(
        grammar,
        userStartSymbol,
        startSymbol,
        graph,
        states,
        actionModule,
        out,
    )
    ascent.write()
}

class RecursiveAscent(
    val graph: StateGraph,

    /**
     * for each state, the set of symbols that it will require for
     * input
     */
    val stateInputs: List<StackSuffix>,

    /** type parameters for the `Nonterminal` type */
    val nonterminalTypeParams: List<TypeParameter>,

    val nonterminalWhereClauses: List<WhereClause>,
)

/**
 * Tracks the suffix of the stack (that is, top-most elements) that any
 * particular state is aware of. We break the suffix into two parts:
 * optional and fixed, which always look like this:
 *
 * ```text
 * ... A B C X Y Z
 * ~~~ ~~~~~ ~~~~~
 * |    |     |
 * |    |   Fixed (top of the stack)
 * |    |
 * |  Optional (will be popped after the fixed portion)
 * |
 * Prefix (stuff we do not know about that is also on the stack
 * ```
 *
 * The idea of an "optional" member is not that it may or may not be
 * on the stack. The entire suffix will always be on the stack. An
 * *optional* member is one that *we* may or may not *consume*. So
 * the above stack suffix could occur given a state with items like:
 *
 * ```text
 * NT1 = A B C X Y Z (*) "."
 * NT2 = X Y Z (*) ","
 * ```
 *
 * Depending on what comes next, if we reduce NT1, we will consume
 * all six symbols, but if we reduce NT2, we will only reduce three.
 */
data class StackSuffix(
    /** all symbols that are known to be on the stack (optional + fixed). */
    val all: List<Symbol>,

    /**
     * optional symbols will be consumed by *some* reductions in this
     * state, but not all
     */
    var lenOptional: Int,
) {
    fun len(): Int = this.all.size

    /**
     * returns the (optional, fixed) -- number of optional
     * items in stack prefix and number of fixed
     */
    fun optionalFixedLens(): Pair<Int, Int> = Pair(this.lenOptional, this.len() - this.lenOptional)

    fun isNotEmpty(): Boolean = this.len() > 0

    fun optional(): List<Symbol> = this.all.subList(0, this.lenOptional)

    fun fixed(): List<Symbol> = this.all.subList(this.lenOptional, this.all.size)
}

private fun newAscent(
    grammar: Grammar,
    userStartSymbol: NonterminalString,
    startSymbol: NonterminalString,
    graph: StateGraph,
    states: List<State<TokenSet>>,
    actionModule: String,
    out: RustWrite,
): CodeGenerator<RecursiveAscent> {
    val (nonterminalTypeParams, nonterminalWhereClauses) =
        CodeGenerator.filterTypeParametersAndWhereClauses(
            grammar,
            grammar.types.nonterminalTypes(),
        )

    val stateInputs = states.map { state -> stateInputFor(state) }

    return CodeGenerator.new(
        grammar,
        userStartSymbol,
        startSymbol,
        states,
        out,
        false,
        actionModule,
        RecursiveAscent(
            graph = graph,
            stateInputs = stateInputs,
            nonterminalTypeParams = nonterminalTypeParams,
            nonterminalWhereClauses = nonterminalWhereClauses,
        ),
    )
}

/** Compute the stack suffix that the state expects on entry. */
private fun stateInputFor(state: State<TokenSet>): StackSuffix {
    val maxPrefix = state.maxPrefix()
    val willPop = state.willPop()
    return StackSuffix(
        all = maxPrefix,
        lenOptional = maxPrefix.size - willPop.size,
    )
}

private fun CodeGenerator<RecursiveAscent>.write() {
    this.writeParseMod { this1 ->
        this1.writeStartFn()
        rust(this1.out, "")
        this1.writeReturnTypeDefn()
        for (i in this1.states.indices) {
            this1.writeStateFn(StateIndex(i))
        }
    }
}

private fun CodeGenerator<RecursiveAscent>.writeReturnTypeDefn() {
    // sometimes some of the variants are not used, particularly
    // if we are generating multiple parsers from the same file:
    rust(this.out, "#[allow(dead_code)]")
    rust(
        this.out,
        "enum ${this.prefix}Nonterminal<${Sep(", ", this.custom.nonterminalTypeParams)}>",
    )

    if (this.custom.nonterminalWhereClauses.isNotEmpty()) {
        rust(
            this.out,
            " where ${Sep(", ", this.custom.nonterminalWhereClauses)}",
        )
    }

    rust(this.out, " {")

    // make an enum with one variant per nonterminal; I considered
    // making different enums per state, but this would mean we
    // have to unwrap and rewrap as we pass up the stack, which
    // seems silly
    for (nt in this.grammar.nonterminals.keys) {
        val ty = this.types
            .spannedType(this.types.nonterminalType(nt))
        rust(this.out, "${Escape(nt)}($ty),")
    }

    rust(this.out, "}")
}

// Generates a function `parseFoo` that will parse an entire
// input as `Foo`. An error is reported if the entire input is not
// consumed.
private fun CodeGenerator<RecursiveAscent>.writeStartFn() {
    val phantomData = this.phantomDataExpr()
    this.startParserFn()
    this.defineTokens()

    this.nextToken("lookahead", "tokens")
    rust(
        this.out,
        "match ${this.prefix}state0(${this.grammar.userParameterRefs()}&mut ${this.prefix}tokens, ${this.prefix}lookahead, $phantomData)? {",
    )

    // extra tokens?
    rust(this.out, "(Some(${this.prefix}lookahead), _) => {")
    rust(
        this.out,
        "Err(${this.prefix}lalrpop_util::ParseError::ExtraToken { token: ${this.prefix}lookahead })",
    )
    rust(this.out, "}")

    // otherwise, we expect to see only the goal terminal
    rust(
        this.out,
        "(None, ${this.prefix}Nonterminal::${Escape(this.startSymbol)}((_, ${this.prefix}nt, _))) => {",
    )
    rust(this.out, "Ok(${this.prefix}nt)")
    rust(this.out, "}")

    // nothing else should be possible
    rust(this.out, "_ => unreachable!(),")
    rust(this.out, "}")

    this.endParserFn()
}

/**
 * Writes the function that corresponds to a given state. This
 * function takes arguments corresponding to the stack slots of
 * the LR(1) machine. It consumes tokens and handles reduces
 * etc. It will return once it has popped at least one symbol off
 * of the LR stack.
 *
 * Note that for states which have a custom kind, this function
 * emits nothing at all other than a possible comment explaining
 * the state.
 */
private fun CodeGenerator<RecursiveAscent>.writeStateFn(thisIndex: StateIndex) {
    val thisState = this.states[thisIndex.value]
    val inputs = this.custom.stateInputs[thisIndex.value]

    rust(this.out, "")

    // Leave a comment explaining what this state is.
    if (Tls.session().emitComments) {
        rust(this.out, "// State ${thisIndex.value}")
        rust(this.out, "//     AllInputs = ${inputs.all}")
        rust(this.out, "//     OptionalInputs = ${inputs.optional()}")
        rust(this.out, "//     FixedInputs = ${inputs.fixed()}")
        rust(this.out, "//     WillPushLen = ${thisState.willPush().size}")
        rust(this.out, "//     WillPush = ${thisState.willPush()}")
        // Mirror the upstream `Debug for Option<T>`: `None` for null, `Some(x)`
        // for non-null. Kotlin `null.toString()` would emit `null`.
        val willProduce = thisState.willProduce()
        val willProduceStr = if (willProduce == null) "None" else "Some($willProduce)"
        rust(this.out, "//     WillProduce = $willProduceStr")
        rust(this.out, "//")
        for (item in thisState.items.vec) {
            rust(this.out, "//     $item")
        }
        rust(this.out, "//")
        for ((terminal, action) in thisState.shifts) {
            rust(this.out, "//   $terminal -> $action")
        }
        for ((tokens, action) in thisState.reductions) {
            rust(this.out, "//   $tokens -> $action")
        }
        rust(this.out, "//")
        for ((nt, state) in thisState.gotos) {
            rust(this.out, "//     $nt -> $state")
        }
    }

    this.emitStateFnHeader("state", thisIndex.value, inputs)

    // possibly move some fixed inputs into optional stack slots
    val stackSuffix = this.adjustInputs(thisIndex, inputs)

    // set to true if goto actions are worth generating
    var fallthrough = false

    rust(this.out, "match ${this.prefix}lookahead {")

    // first emit shifts:
    for ((terminal, nextIndex) in thisState.shifts) {
        val symName = "${this.prefix}sym${inputs.len()}"
        this.consumeTerminal(terminal, symName)

        // transition to the new state
        if (this.transition("result", stackSuffix, nextIndex, arrayOf("tokens"))) {
            fallthrough = true
        }

        rust(this.out, "}")
    }

    // now emit reduces. It frequently happens that many tokens
    // trigger the same reduction, so group these by the
    // production that we are going to be reducing.
    val reductions: Multimap<Production, VecCollection<Token>, Token> = Multimap { VecCollection() }
    for ((tokens, production) in thisState.reductions) {
        for (t in tokens) {
            reductions.push(production, t)
        }
    }
    for ((production, tokenColl) in reductions) {
        val tokens = tokenColl.asList()
        for ((index, token) in tokens.withIndex()) {
            val pattern = when (token) {
                is Token.Terminal -> "Some(${this.matchTerminalPattern(token.terminalString)})"
                Token.Error ->
                    error("Error recovery is not implemented for recursive ascent parsers")
                Token.Eof -> "None"
            }
            if (index < tokens.size - 1) {
                rust(this.out, "$pattern |")
            } else {
                rust(this.out, "$pattern => {")
            }
        }

        this.emitReduceAction("result", stackSuffix, production)

        if (production.symbols.isNotEmpty()) {
            // if we popped anything off of the stack, then this frame is done
            rust(this.out, "return Ok(${this.prefix}result);")
        } else {
            fallthrough = true
        }

        rust(this.out, "}")
    }

    // if we hit this, the next token is not recognized, so generate an error
    rust(this.out, "_ => {")
    // The terminals which would have resulted in a successful parse in this state
    val successfulTerminals = this.grammar.terminals.all.filter { terminal ->
        thisState.shifts.containsKey(terminal) ||
            thisState.reductions.any { (t, _) -> t.contains(Token.Terminal(terminal)) }
    }

    rust(this.out, "#[allow(clippy::needless_raw_string_hashes)]")
    rust(this.out, "let ${this.prefix}expected = alloc::vec![")
    for (terminal in successfulTerminals) {
        // Try to avoid terminals escaping
        rust(this.out, "r###\"$terminal\"###.to_string(),")
    }
    rust(this.out, "];")

    // check if we have found an unrecognized token or EOF
    rust(this.out, "return Err(")
    rust(this.out, "match ${this.prefix}lookahead {")

    rust(this.out, "Some(${this.prefix}token) => {")
    rust(
        this.out,
        "${this.prefix}lalrpop_util::ParseError::UnrecognizedToken {",
    )
    rust(this.out, "token: ${this.prefix}token,")
    rust(this.out, "expected: ${this.prefix}expected,")
    rust(this.out, "}")
    rust(this.out, "}")

    rust(this.out, "None => {")

    // find the location of the last symbol on stack
    val (optional, fixed) = stackSuffix.optionalFixedLens()
    if (fixed > 0) {
        rust(
            this.out,
            "let ${this.prefix}location = ${this.prefix}sym${stackSuffix.len() - 1}.2;",
        )
    } else if (optional > 0) {
        rust(this.out, "let ${this.prefix}location = ")
        for (index in (optional - 1) downTo 0) {
            rust(
                this.out,
                "${this.prefix}sym$index.as_ref().map(|sym| sym.2.clone()).unwrap_or_else(|| {",
            )
        }
        rust(this.out, "Default::default()")
        for (ignored in 0 until optional) {
            rust(this.out, "})")
        }
        rust(this.out, ";")
    } else {
        rust(this.out, "let ${this.prefix}location = Default::default();")
    }

    rust(
        this.out,
        "${this.prefix}lalrpop_util::ParseError::UnrecognizedEof {",
    )
    rust(this.out, "location: ${this.prefix}location,")
    rust(this.out, "expected: ${this.prefix}expected,")
    rust(this.out, "}")
    rust(this.out, "}")

    rust(this.out, "}") // Error match
    rust(this.out, ")")

    rust(this.out, "}") // Wildcard match case
    rust(this.out, "}") // match

    // finally, emit gotos (if relevant)
    if (fallthrough && thisState.gotos.isNotEmpty()) {
        // Sometimes we write loops that unconditionally only loop once
        rust(this.out, "#[allow(clippy::never_loop)]")
        rust(this.out, "loop {")

        // In most states, we know precisely when the top stack
        // slot will be consumed (basically, when we reduce or
        // when we transition to another state). But in some states,
        // we may not know. Consider:
        //
        //     X = A (*) "0" ["."]
        //     X = A (*) B ["."]
        //     B = (*) "0" "1" ["."]
        //
        // Now if we see a `"0"` this *could* be the start of a `B
        // = "0" "1"` or it could be the continuation of `X = A
        // "0"`. We will not know until we see the *next* character
        // (which will either be `"0"` or `"."`). If it turns out to be
        // `X = A "0"`, then the state handling the `"0"` will reduce
        // and consume the `A` and the `"0"`. But otherwise it will shift
        // the `"1"` and leave the `A` unprocessed.
        //
        // In cases like this, the [adjustInputs] routine will
        // have taken the top of the stack ("A") and put it into
        // an `Option`. After the state processing the `"0"`
        // returns then, we can check this option to see whether
        // it has popped the `"A"` (in which case we ought to
        // return) or not (in which case we ought to shift the `B`
        // value that it returned to us).
        val topSlotOptional = stackSuffix.isNotEmpty() && stackSuffix.fixed().isEmpty()
        if (topSlotOptional) {
            rust(
                this.out,
                "if ${this.prefix}sym${stackSuffix.len() - 1}.is_none() {",
            )
            rust(this.out, "return Ok(${this.prefix}result);")
            rust(this.out, "}")
        }

        rust(
            this.out,
            "let (${this.prefix}lookahead, ${this.prefix}nt) = ${this.prefix}result;",
        )

        rust(this.out, "match ${this.prefix}nt {")
        for ((nt, nextIndex) in thisState.gotos) {
            // The nonterminal we are shifting becomes symN, where
            // N is the number of inputs to this state (which are
            // numbered sym0..sym(N-1)). It is never optional
            // because we always transition to a state with at
            // least *one* fixed input.
            rust(
                this.out,
                "${this.prefix}Nonterminal::${Escape(nt)}(${this.prefix}sym${stackSuffix.len()}) => {",
            )
            this.transition("result", stackSuffix, nextIndex, arrayOf("tokens", "lookahead"))
            rust(this.out, "}")
        }

        // Errors are not possible in the goto phase; a missing entry
        // indicates parse successfully completed, so just bail out.
        if (thisState.gotos.size != this.grammar.nonterminals.keys.size) {
            rust(this.out, "_ => {")
            rust(
                this.out,
                "return Ok((${this.prefix}lookahead, ${this.prefix}nt));",
            )
            rust(this.out, "}")
        }

        rust(this.out, "}") // match

        rust(this.out, "}") // while/loop
    } else if (fallthrough) {
        rust(this.out, "return Ok(${this.prefix}result);")
    }

    rust(this.out, "}") // function }
}

private fun CodeGenerator<RecursiveAscent>.emitStateFnHeader(
    fnKind: String, // e.g. "state", "custom"
    fnIndex: Int, // state index, custom kind index, etc
    suffix: StackSuffix,
) {
    val optionalPrefix = suffix.optional()
    val fixedPrefix = suffix.fixed()

    val tripleType = this.tripleType()
    val parseErrorType = this.types.parseErrorType()

    val (fnArgs, startsWithTerminal) = this.fnArgs(optionalPrefix, fixedPrefix)

    this.out
        .fnHeader(
            Visibility.Priv,
            "${this.prefix}$fnKind$fnIndex",
        )
        .withGrammar(this.grammar)
        .withTypeParameters(
            listOf(
                "${this.prefix}TOKENS: Iterator<Item=Result<$tripleType,$parseErrorType>>",
            ),
        )
        .withParameters(fnArgs)
        .withReturnType(
            "Result<(Option<$tripleType>, ${this.prefix}Nonterminal<${Sep(", ", this.custom.nonterminalTypeParams)}>), $parseErrorType>",
        )
        .emit()

    rust(this.out, "{")

    rust(
        this.out,
        "let mut ${this.prefix}result: (Option<$tripleType>, ${this.prefix}Nonterminal<${Sep(", ", this.custom.nonterminalTypeParams)}>);",
    )

    // shift lookahead is necessary; see [startsWithTerminal] above
    if (startsWithTerminal) {
        this.nextToken("lookahead", "tokens")
    }
}

// Compute the set of arguments that the function for a state or
// custom-kind expects.  The argument `symbols` represents the top
// portion of the stack which this function expects to be given.
// Each of them will be given an argument like `sym3: &mut
// Option<Sym3>` where `Sym3` is the type of the symbol.
//
// Returns a list of argument names and a flag if this function resulted
// from pushing a terminal (in which case the lookahead must be
// computed internally).
private fun CodeGenerator<RecursiveAscent>.fnArgs(
    optionalPrefix: List<Symbol>,
    fixedPrefix: List<Symbol>,
): Pair<List<String>, Boolean> {
    check(
        // start state:
        (optionalPrefix.isEmpty() && fixedPrefix.isEmpty()) ||
            /* any other state: */ fixedPrefix.isNotEmpty(),
    )
    val tripleType = this.tripleType()

    // to reduce the size of the generated code, if the state
    // results from shifting a terminal, then we do not pass the
    // lookahead in as an argument, but rather we load it as the
    // first thing in this function; this saves some space because
    // there are more edges than there are states in the graph.
    val startsWithTerminal = fixedPrefix.lastOrNull()?.isTerminal() ?: false

    val baseArgs: MutableList<String> = mutableListOf(
        "${this.prefix}tokens: &mut ${this.prefix}TOKENS",
    )
    if (!startsWithTerminal) {
        baseArgs.add("${this.prefix}lookahead: Option<$tripleType>")
    }

    // "Optional symbols" may or may not be consumed, so take an
    // `&mut Option`
    val optionalArgs = (0 until optionalPrefix.size).asSequence().map { i ->
        "${this.prefix}sym$i: &mut Option<${this.types.spannedType(optionalPrefix[i].ty(this.types))}>"
    }

    // "Fixed symbols" will be consumed before we return, so take the value itself
    val fixedArgs = (0 until fixedPrefix.size).asSequence().map { i ->
        "${this.prefix}sym${optionalPrefix.size + i}: ${this.types.spannedType(fixedPrefix[i].ty(this.types))}"
    }

    val allArgs: List<String> = (
        baseArgs.asSequence() +
            optionalArgs +
            fixedArgs +
            sequenceOf("_: ${this.phantomDataType()}")
        ).toList()

    return Pair(allArgs, startsWithTerminal)
}

/**
 * Examine the states that we may transition to. Unless this is
 * the start state, we will always take at least 1 fixed input:
 * the most recently pushed symbol (let call it `symX`), and we
 * may have others as well. But if this state can transition to
 * another state can takes some of those inputs as optional
 * parameters, we need to convert them them options. This
 * function thus emits code to move each sum `symX` into an
 * option, and returns an adjusted stack-suffix that reflects the
 * changes made.
 */
private fun CodeGenerator<RecursiveAscent>.adjustInputs(
    stateIndex: StateIndex,
    inputs: StackSuffix,
): StackSuffix {
    val result = inputs.copy()

    val topOpt = this.custom.graph.successors(stateIndex).any { succState ->
        val succInputs = this.custom.stateInputs[succState.value]

        // Check for a successor state with a suffix like:
        //
        //     ... OPT_1 ... OPT_N FIXED_1
        //
        // (Remember that *every* successor state will have
        // at least one fixed input.)
        //
        // So basically we are looking for states
        // that, when they return, may *optionally* have consumed
        // the top of our stack.
        check(succInputs.fixed().isNotEmpty())
        succInputs.fixed().size == 1 && succInputs.optional().isNotEmpty()
    }

    // If we find a successor that may optionally consume the top
    // of our stack, convert our fixed inputs into optional ones.
    //
    // (Here we convert *all* fixed inputs. Honestly, I cannot
    // remember if this is necessary, or just for simplicity. I
    // suspect the latter. --nmatsakis)
    if (topOpt) {
        val startNum = inputs.optional().size
        for (symNum in startNum until startNum + inputs.fixed().size) {
            rust(
                this.out,
                "let ${this.prefix}sym$symNum = &mut Some(${this.prefix}sym$symNum);",
            )
        }
        result.lenOptional = result.len()
    }

    return result
}

/**
 * Given that we have, locally, `optional` number of optional stack slots
 * followed by `fixed` number of fixed stack slots, prepare the inputs
 * to be supplied to `inputs`. Returns a string of names for this inputs.
 */
private fun CodeGenerator<RecursiveAscent>.popSyms(
    optional: Int,
    fixed: Int,
    inputs: StackSuffix,
): List<String> {
    val totalHave = optional + fixed
    val totalNeed = inputs.len()
    val out = mutableListOf<String>()
    val havesRange = (totalHave - totalNeed) until totalHave // number relative to us
    val needsRange = 0 until totalNeed // number relative to them
    for ((h, n) in havesRange.zip(needsRange)) {
        val name = "${this.prefix}sym$h"
        val haveOptional = h < optional
        val needOptional = n < inputs.lenOptional

        // if we have something stored in an `Option`, but the next state
        // consumes it unconditionally, then "pop" it
        if (haveOptional && !needOptional) {
            rust(this.out, "let $name = $name.take().unwrap();")
        } else {
            // we should never have something stored
            // unconditionally that the next state only
            // "maybe" consumes -- we should have fixed this
            // in the [adjustInputs] phase
            check(haveOptional == needOptional)
        }
        out.add(name)
    }
    return out
}

/**
 * Emit code to shift/goto into the state [nextIndex]. Returns
 * `true` if the current state may be valid after the target
 * state returns, or `false` if [transition] will just return
 * afterwards.
 *
 * # Arguments
 *
 * - [intoResult]: name of variable to store result from target state into
 * - [stackSuffix]: the suffix of the LR stack that current state is aware of,
 *   and how it is distributed into optional/fixed slots
 * - [nextIndex]: target state
 * - [otherArgsIn]: other arguments we are threading along
 */
private fun CodeGenerator<RecursiveAscent>.transition(
    intoResult: String,
    stackSuffix: StackSuffix,
    nextIndex: StateIndex,
    otherArgsIn: Array<String>,
): Boolean {
    // the depth of the suffix of the stack that we are aware of
    // in the current state, including the newly shifted token
    var (optional, fixed) = stackSuffix.optionalFixedLens()
    fixed += 1 // we just shifted another symbol
    val total = optional + fixed
    check(total == stackSuffix.len() + 1)

    // symbols that the next state expects; will always be include
    // at least one fixed input
    val nextInputs = this.custom.stateInputs[nextIndex.value]
    check(nextInputs.fixed().isNotEmpty())
    check(nextInputs.len() <= total)

    val transferSyms = this.popSyms(optional, fixed, nextInputs)

    val otherArgs: List<String> = otherArgsIn
        .map { s -> "${this.prefix}$s" }
        .toList()

    val fnName = "${this.prefix}state${nextIndex.value}"

    // invoke next state, transferring the top `m` tokens
    val phantomDataExpr = this.phantomDataExpr()
    rust(
        this.out,
        "${this.prefix}$intoResult = $fnName(${this.grammar.userParameterRefs()}${Sep(", ", otherArgs)}, ${Sep(", ", transferSyms)}, $phantomDataExpr)?;",
    )

    // if the target state takes at least **two** fixed tokens,
    // then it will have consumed the top of **our** stack frame,
    // so we should just return
    return if (nextInputs.fixed().size >= 2) {
        rust(this.out, "return Ok(${this.prefix}$intoResult);")
        false
    } else {
        true
    }
}

/**
 * Executes a reduction of [production], storing the result into
 * the variable named by [intoVar], which should have type
 * `(Option<(L,T,L)>, Nonterminal)` in the emitted Rust output.
 */
private fun CodeGenerator<RecursiveAscent>.emitReduceAction(
    intoVar: String,
    stackSuffix: StackSuffix,
    production: Production,
) {
    val locType = this.types.terminalLocType()

    val (optional, fixed) = stackSuffix.optionalFixedLens()
    val productionInputs = StackSuffix(
        all = production.symbols,
        lenOptional = 0,
    )
    val transferSyms = this.popSyms(optional, fixed, productionInputs)

    // identify the "start" and "end" location for this production; this
    // is typically the start of the first symbol and end of the last symbol we are
    // reducing; but in the case of an empty production, it will come from the
    // lookahead or the end of the last symbol pushed
    val firstSym = transferSyms.firstOrNull()
    val lastSym = transferSyms.lastOrNull()
    if (firstSym != null && lastSym != null) {
        rust(this.out, "let ${this.prefix}start = $firstSym.0.clone();")
        rust(this.out, "let ${this.prefix}end = $lastSym.2.clone();")
    } else if (stackSuffix.len() > 0) {
        // we pop no symbols, so grab from the top of the stack
        // (unless we are in the start state)
        val top = stackSuffix.len() - 1
        val p = this.prefix
        if (stackSuffix.fixed().isNotEmpty()) {
            rust(
                this.out,
                "let ${p}start = ${p}lookahead.as_ref().map(|o| o.0).unwrap_or_else(|| ${p}sym$top.2);",
            )
        } else {
            // top of stack is optional; should not have been popped yet tho
            rust(
                this.out,
                "let ${p}start = ${p}lookahead.as_ref().map(|o| o.0.clone()).unwrap_or_else(|| ${p}sym$top.as_ref().unwrap().2.clone());",
            )
        }
        rust(this.out, "let ${p}end = ${p}start;")
    } else {
        // this only occurs in the start state
        rust(
            this.out,
            "let ${this.prefix}start: $locType = Default::default();",
        )
        rust(this.out, "let ${this.prefix}end = ${this.prefix}start;")
    }

    val transferredSyms = transferSyms.size

    val args = transferSyms.toMutableList()
    if (transferredSyms == 0) {
        args.add("&${this.prefix}start")
        args.add("&${this.prefix}end")
    }

    // invoke the action code
    val isFallible = this.grammar.actionIsFallible(production.action)
    if (isFallible) {
        rust(
            this.out,
            "let ${this.prefix}nt = ${this.actionModule}::${this.prefix}action${production.action.index()}::<${Sep(", ", this.grammar.nonLifetimeTypeParameters())}>(${this.grammar.userParameterRefs()}${Sep(", ", args)})?;",
        )
    } else {
        rust(
            this.out,
            "let ${this.prefix}nt = ${this.actionModule}::${this.prefix}action${production.action.index()}::<${Sep(", ", this.grammar.nonLifetimeTypeParameters())}>(${this.grammar.userParameterRefs()}${Sep(", ", args)});",
        )
    }

    // wrap up the produced value into `Nonterminal` along with
    rust(
        this.out,
        "let ${this.prefix}nt = ${this.prefix}Nonterminal::${Escape(production.nonterminal)}((",
    )
    rust(this.out, "${this.prefix}start,")
    rust(this.out, "${this.prefix}nt,")
    rust(this.out, "${this.prefix}end,")
    rust(this.out, "));")

    // wrap up the result along with the (unused) lookahead
    rust(
        this.out,
        "${this.prefix}$intoVar = (${this.prefix}lookahead, ${this.prefix}nt);",
    )
}

/** Emit a pattern that matches `id` but does not extract any data. */
private fun CodeGenerator<RecursiveAscent>.matchTerminalPattern(id: TerminalString): String {
    val pattern = this.grammar.pattern(id).map { "_" }
    val patternStr = "$pattern"
    return "(_, $patternStr, _)"
}

/**
 * Emit a pattern that matches the terminal [id] and extracts its
 * value, storing that value as the variable named by [letName].
 */
private fun CodeGenerator<RecursiveAscent>.consumeTerminal(id: TerminalString, letName: String) {
    val patternNames: MutableList<String> = mutableListOf()
    val pattern = this.grammar.pattern(id).map {
        val index = patternNames.size
        patternNames.add("${this.prefix}tok$index")
        patternNames.last()
    }

    var patternStr = "$pattern"
    if (patternNames.isEmpty()) {
        patternNames.add("${this.prefix}tok")
        patternStr = "${this.prefix}tok @ $patternStr"
    }

    patternStr = "(${this.prefix}loc1, $patternStr, ${this.prefix}loc2)"

    rust(this.out, "Some($patternStr) => {")

    rust(
        this.out,
        "let $letName = (${this.prefix}loc1, (${patternNames.joinToString(", ")}), ${this.prefix}loc2);",
    )
}

private fun CodeGenerator<RecursiveAscent>.tripleType(): TypeRepr = this.types.tripleType()

private fun CodeGenerator<RecursiveAscent>.nextToken(lookahead: String, tokens: String) {
    rust(
        this.out,
        "let ${this.prefix}$lookahead = match ${this.prefix}$tokens.next() {",
    )
    rust(this.out, "Some(Ok(v)) => Some(v),")
    rust(this.out, "Some(Err(e)) => return Err(e),")
    rust(this.out, "None => None,")
    rust(this.out, "};")
}
