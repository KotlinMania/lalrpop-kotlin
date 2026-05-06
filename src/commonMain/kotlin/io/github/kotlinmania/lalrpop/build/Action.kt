// port-lint: source build/action.rs
/**
 * Code for generating action code.
 *
 * From the outside, action fns have one of two forms. If they take
 * symbols as input, e.g. from a production like `X = Y Z => ...`
 * (which takes Y and Z as input), they have this form:
 *
 * ```text
 * function __action17<
 *     input,                       // user-declared type parameters (*)
 * >(
 *     input: &input str,           // user-declared parameters
 *     __0: (size, size, size),     // symbols being reduced, if any
 *     ...
 *     __N: (size, Foo, size),      // each has a type (L, T, L)
 * ) -> boxed-like Expr
 * ```
 *
 * Otherwise, they have this form:
 *
 * ```text
 * function __action17<
 *     input,                       // user-declared type parameters (*)
 * >(
 *     input: &input str,           // user-declared parameters
 *    __lookbehind: &size,          // value for @R -- "end of previous token"
 *    __lookahead: &size,           // value for @L -- "start of next token"
 * ) -> boxed-like Expr
 * ```
 *
 * * -- in this case, those "user-declared" parameters are inserted by
 *   the "internal tokenizer".
 */
package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.grammar.parsetree.Visibility
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFnDefnKind
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.InlineActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.InlinedSymbol
import io.github.kotlinmania.lalrpop.grammar.repr.LookaroundActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.UserActionFnDefn
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust

fun emitActionCode(grammar: Grammar, out: RustWrite) {
    for ((i, defn) in grammar.actionFnDefns.withIndex()) {
        rust(out, "")

        // we always thread the parameters through to the action code,
        // even if they are not used, and hence we need to disable the
        // unused variables lint, which otherwise gets very excited.
        if (grammar.parameters.isNotEmpty()) {
            rust(out, "#[allow(unused_variables)]")
        }

        when (val kind = defn.kind) {
            is ActionFnDefnKind.User ->
                emitUserActionCode(grammar, out, i, defn, kind.data)
            is ActionFnDefnKind.Lookaround ->
                emitLookaroundActionCode(grammar, out, i, kind.data)
            is ActionFnDefnKind.Inline ->
                emitInlineActionCode(grammar, out, i, defn, kind.data)
        }
    }
}

private fun retTypeString(grammar: Grammar, defn: ActionFnDefn): String =
    if (defn.fallible) {
        "Result<${defn.retType},${grammar.prefix}lalrpop_util::ParseError<" +
            "${grammar.types.terminalLocType()},${grammar.types.terminalTokenType()},${grammar.types.errorType()}>>"
    } else {
        "${defn.retType}"
    }

private fun emitUserActionCode(
    grammar: Grammar,
    out: RustWrite,
    index: Int,
    defn: ActionFnDefn,
    data: UserActionFnDefn,
) {
    val retType = retTypeString(grammar, defn)

    // For each symbol to be reduced, we will receive
    // a (L, T, L) triple where the Ls are locations and
    // the T is the data. Ignore the locations and bind
    // the data to the name the user gave.
    val arguments: MutableList<String> = data.argPatterns
        .zip(data.argTypes.map { grammar.types.spannedType(it) })
        .map { (name, ty) -> "(_, $name, _): $ty" }
        .toMutableList()

    // If this is a reduce of an empty production, we will
    // automatically add position information in the form of
    // lookbehind/lookahead values. Otherwise, those values would be
    // determined from the arguments themselves.
    if (data.argPatterns.isEmpty()) {
        arguments.add("${grammar.prefix}lookbehind: &${grammar.types.terminalLocType()}")
        arguments.add("${grammar.prefix}lookahead: &${grammar.types.terminalLocType()}")
    }

    rust(
        out,
        "#[allow(clippy::too_many_arguments, clippy::needless_lifetimes, clippy::just_underscores_and_digits)]"
    )
    out.fnHeader(Visibility.Priv, "${grammar.prefix}action$index")
        .withGrammar(grammar)
        .withParameters(arguments)
        .withReturnType(retType)
        .emit()

    rust(out, "{{")

    // The user did not provide any code
    if (data.code != "()") {
        rust(out, "{}", data.code)
    }

    rust(out, "}}")
}

private fun emitLookaroundActionCode(
    grammar: Grammar,
    out: RustWrite,
    index: Int,
    data: LookaroundActionFnDefn,
) {
    rust(out, "#[allow(clippy::needless_lifetimes)]")
    out.fnHeader(Visibility.Priv, "${grammar.prefix}action$index")
        .withGrammar(grammar)
        .withParameters(
            listOf(
                "${grammar.prefix}lookbehind: &${grammar.types.terminalLocType()}",
                "${grammar.prefix}lookahead: &${grammar.types.terminalLocType()}",
            )
        )
        .withReturnType("${grammar.types.terminalLocType()}")
        .emit()

    rust(out, "{{")
    when (data) {
        is LookaroundActionFnDefn.Lookahead -> {
            // take the lookahead, if any; otherwise, we are
            // at EOF, so taker the lookbehind (end of last
            // pushed token); if that is missing too, then
            // supply default.
            rust(out, "*{}lookahead", grammar.prefix)
        }
        is LookaroundActionFnDefn.Lookbehind -> {
            // take lookbehind or supply default
            rust(out, "*{}lookbehind", grammar.prefix)
        }
    }
    rust(out, "}}")
}

private fun emitInlineActionCode(
    grammar: Grammar,
    out: RustWrite,
    index: Int,
    defn: ActionFnDefn,
    data: InlineActionFnDefn,
) {
    val retType = retTypeString(grammar, defn)

    val argTypes = data.symbols.flatMap { sym ->
        when (sym) {
            is InlinedSymbol.Original -> listOf(sym.sym)
            is InlinedSymbol.Inlined -> sym.syms.toList()
        }
    }.map { it.ty(grammar.types) }

    // this is the number of symbols we expect to be passed in; it is
    // distinct from data.symbols.len(), because sometimes we have
    // inlined actions with no input symbols
    val numFlatArgs = argTypes.size

    val arguments: MutableList<String> = argTypes
        .map { grammar.types.spannedType(it) }
        .withIndex()
        .map { (i, t) -> "${grammar.prefix}$i: $t" }
        .toMutableList()

    // If no symbols are being reduced, add in the
    // lookbehind/lookahead.
    if (arguments.isEmpty()) {
        arguments.add("${grammar.prefix}lookbehind: &${grammar.types.terminalLocType()}")
        arguments.add("${grammar.prefix}lookahead: &${grammar.types.terminalLocType()}")
    }

    rust(
        out,
        "#[allow(clippy::too_many_arguments, clippy::needless_lifetimes,\n    clippy::just_underscores_and_digits)]"
    )
    out.fnHeader(Visibility.Priv, "${grammar.prefix}action$index")
        .withGrammar(grammar)
        .withParameters(arguments)
        .withReturnType(retType)
        .emit()
    rust(out, "{{")

    // For each inlined thing, compute the start/end locations.
    // Do this first so that none of the arguments have been moved
    // yet and we can easily access their locations.
    var argCounter = 0
    var tempCounter = 0
    for (symbol in data.symbols) {
        when (symbol) {
            is InlinedSymbol.Original -> {
                argCounter += 1
            }
            is InlinedSymbol.Inlined -> {
                val syms = symbol.syms
                if (syms.isNotEmpty()) {
                    // If we are reducing symbols, then start and end
                    // can be the start/end location of the first/last
                    // symbol respectively. Easy peezy.

                    rust(
                        out,
                        "let {}start{} = {}{}.0;",
                        grammar.prefix,
                        tempCounter,
                        grammar.prefix,
                        argCounter,
                    )

                    val lastArgIndex = argCounter + syms.size - 1
                    rust(
                        out,
                        "let {}end{} = {}{}.2;",
                        grammar.prefix,
                        tempCounter,
                        grammar.prefix,
                        lastArgIndex,
                    )
                } else {
                    // If we have no symbols, then `argCounter`
                    // represents index of the first symbol after this
                    // inlined item (if any), and `argCounter-1`
                    // represents index of the symbol before this
                    // item.

                    if (argCounter > 0) {
                        rust(
                            out,
                            "let {}start{} = {}{}.2;",
                            grammar.prefix,
                            tempCounter,
                            grammar.prefix,
                            argCounter - 1,
                        )
                    } else if (numFlatArgs > 0) {
                        rust(
                            out,
                            "let {}start{} = {}{}.0;",
                            grammar.prefix,
                            tempCounter,
                            grammar.prefix,
                            argCounter,
                        )
                    } else {
                        rust(
                            out,
                            "let {}start{} = *{}lookbehind;",
                            grammar.prefix,
                            tempCounter,
                            grammar.prefix,
                        )
                    }

                    if (argCounter < numFlatArgs) {
                        rust(
                            out,
                            "let {}end{} = {}{}.0;",
                            grammar.prefix,
                            tempCounter,
                            grammar.prefix,
                            argCounter,
                        )
                    } else if (numFlatArgs > 0) {
                        rust(
                            out,
                            "let {}end{} = {}{}.2;",
                            grammar.prefix,
                            tempCounter,
                            grammar.prefix,
                            numFlatArgs - 1,
                        )
                    } else {
                        rust(
                            out,
                            "let {}end{} = *{}lookahead;",
                            grammar.prefix,
                            tempCounter,
                            grammar.prefix,
                        )
                    }
                }

                tempCounter += 1
                argCounter += syms.size
            }
        }
    }

    // Now create temporaries for the inlined things
    argCounter = 0
    tempCounter = 0

    // if there are type parameters then type annotation is required
    val annotate = grammar.nonLifetimeTypeParameters().isNotEmpty()
    val lparen = if (annotate) "::<" else "("

    for (symbol in data.symbols) {
        when (symbol) {
            is InlinedSymbol.Original -> {
                argCounter += 1
            }
            is InlinedSymbol.Inlined -> {
                val inlinedAction = symbol.action
                val syms = symbol.syms
                // execute the inlined reduce action
                rust(
                    out,
                    "let {}temp{} = {}action{}{}",
                    grammar.prefix,
                    tempCounter,
                    grammar.prefix,
                    inlinedAction.index(),
                    lparen,
                )
                for (t in grammar.nonLifetimeTypeParameters()) {
                    rust(out, "{},", t)
                }
                if (annotate) {
                    rust(out, ">(")
                }
                for (parameter in grammar.parameters) {
                    rust(out, "{},", parameter.name)
                }
                for (i in 0 until syms.size) {
                    rust(out, "{}{},", grammar.prefix, argCounter + i)
                }
                if (syms.isEmpty()) {
                    rust(out, "&{}start{},", grammar.prefix, tempCounter)
                    rust(out, "&{}end{},", grammar.prefix, tempCounter)
                }

                if (grammar.actionIsFallible(inlinedAction)) {
                    rust(out, ")?;")
                } else {
                    rust(out, ");")
                }

                // wrap up the inlined value along with its span
                rust(
                    out,
                    "let {}temp{} = ({}start{}, {}temp{}, {}end{});",
                    grammar.prefix,
                    tempCounter,
                    grammar.prefix,
                    tempCounter,
                    grammar.prefix,
                    tempCounter,
                    grammar.prefix,
                    tempCounter,
                )

                tempCounter += 1
                argCounter += syms.size
            }
        }
    }

    val finalActionFallible = grammar.actionIsFallible(data.action)
    val (okBegin, okEnd) = when {
        defn.fallible && finalActionFallible -> "" to ""
        !defn.fallible && !finalActionFallible -> "" to ""
        defn.fallible && !finalActionFallible -> "Ok(" to ")"
        else -> error("unreachable: non-fallible defn with fallible inner action")
    }

    rust(
        out,
        "{}{}action{}{}",
        okBegin,
        grammar.prefix,
        data.action.index(),
        lparen,
    )
    for (t in grammar.nonLifetimeTypeParameters()) {
        rust(out, "{},", t)
    }
    if (annotate) {
        rust(out, ">(")
    }
    for (parameter in grammar.parameters) {
        rust(out, "{},", parameter.name)
    }
    argCounter = 0
    tempCounter = 0
    for (symbol in data.symbols) {
        when (symbol) {
            is InlinedSymbol.Original -> {
                rust(out, "{}{},", grammar.prefix, argCounter)
                argCounter += 1
            }
            is InlinedSymbol.Inlined -> {
                rust(out, "{}temp{},", grammar.prefix, tempCounter)
                tempCounter += 1
                argCounter += symbol.syms.size
            }
        }
    }
    require(data.symbols.isNotEmpty())
    rust(out, "){}", okEnd)

    rust(out, "}}")
}
