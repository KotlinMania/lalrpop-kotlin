// transliterated from upstream module root
/**
 * If an extern token is provided, then this pass validates that
 * terminal IDs have conversions. Otherwise, it generates a
 * tokenizer. This can only be done after macro expansion because
 * some macro arguments never make it into an actual production and
 * are only used in `if` conditions; we import string literals for
 * those, but they do not have to have a defined conversion.
 */
package io.github.kotlinmania.lalrpop.normalize.tokencheck

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.collections.Set
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.INPUT_PARAMETER
import io.github.kotlinmania.lalrpop.grammar.parsetree.Alternative
import io.github.kotlinmania.lalrpop.grammar.parsetree.ExprSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.GrammarItem
import io.github.kotlinmania.lalrpop.grammar.parsetree.InternToken
import io.github.kotlinmania.lalrpop.grammar.parsetree.Lifetime
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchEntry
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchItem
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchToken
import io.github.kotlinmania.lalrpop.grammar.parsetree.Parameter
import io.github.kotlinmania.lalrpop.grammar.parsetree.Path
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.grammar.parsetree.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.SymbolKind
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalLiteral
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeRef
import io.github.kotlinmania.lalrpop.grammar.parsetree.enumToken
import io.github.kotlinmania.lalrpop.grammar.parsetree.matchToken
import io.github.kotlinmania.lalrpop.lexer.dfa.DfaConstructionError
import io.github.kotlinmania.lalrpop.lexer.dfa.DfaConstructionException
import io.github.kotlinmania.lalrpop.lexer.dfa.Precedence
import io.github.kotlinmania.lalrpop.lexer.dfa.buildDfa
import io.github.kotlinmania.lalrpop.lexer.nfa.NfaConstructionError
import io.github.kotlinmania.lalrpop.lexer.Regex
import io.github.kotlinmania.lalrpop.lexer.parseLiteral
import io.github.kotlinmania.lalrpop.lexer.parseRegex
import io.github.kotlinmania.lalrpop.normalize.returnErr

internal fun validate(grammar: Grammar): Grammar {
    val mode: TokenMode = run {
        val mode: TokenMode = run {
            val enumToken = grammar.enumToken()
            if (enumToken != null) {
                check(grammar.matchToken() == null) {
                    "validator permitted both an extern/match section"
                }
                val conversions: Set<TerminalString> = set()
                for (conversion in enumToken.conversions) {
                    conversions.add(conversion.from)
                }
                TokenMode.Extern(conversions)
            } else {
                TokenMode.Internal(MatchBlock.new(grammar.matchToken()))
            }
        }

        val validator = Validator(grammar = grammar, mode = mode)
        validator.validate()
        validator.mode
    }

    when (mode) {
        is TokenMode.Extern -> {
            // If using an external tokenizer, we are all done at this point.
        }
        is TokenMode.Internal -> {
            // Otherwise, construct the `InternToken` item.
            construct(grammar, mode.matchBlock)
        }
    }

    return grammar
}

/** //////////////////////////////////////////////////////////////////////// */
// Validation phase -- this phase walks the grammar and visits all
// terminals. If using an external set of tokens, it checks that all
// terminals have a defined conversion to some pattern. Otherwise,
// it collects all terminals into the `allLiterals` set for later use.

private class Validator(
    val grammar: Grammar,
    var mode: TokenMode,
)

private sealed class TokenMode {
    /**
     * If there is an `extern { ... }` section that defines
     * conversions of the form `TERMINAL => PATTERN`, then this is a
     * set of those terminals. These are the only terminals that the
     * user should be using.
     */
    data class Extern(val conversions: Set<TerminalString>) : TokenMode()

    /**
     * Otherwise, we are synthesizing the tokenizer. In that case,
     * matchBlock summarizes the data from the matchblock-section in
     * the grammar, if any. If there was no matchblock, or the section
     * contains a wildcard, the user can also import additional
     * terminals in the grammar.
     */
    data class Internal(val matchBlock: MatchBlock) : TokenMode()
}

/** Data summarizing the matchblock, along with any literals we scraped up. */
private class MatchBlock(
    /**
     * This map stores the matchblock entries. If matchCatchAll
     * is true, then we will grow this set with "identity mappings"
     * for new literals that we find.
     */
    val matchEntries: MutableList<MatchEntry> = mutableListOf(),

    /**
     * The names of all terminals the user can legally type. If
     * `matchCatchAll` is true, then if we encounter additional
     * terminal literals in the grammar, we will add them to this
     * set.
     */
    val matchUserNames: Set<TerminalString> = set(),

    /**
     * For each terminal literal that we have to find, the span
     * where it appeared in user source.  This can either be in the
     * matchblock section or else in the grammar somewhere (if added
     * due to a catch-all, or there is no matchblock section).
     */
    val spans: Map<TerminalLiteral, Span> = map(),

    /** True if we should permit unrecognized literals to be used. */
    var catchAll: Precedence? = null,
) {
    companion object {
        /**
         * Creates a [MatchBlock] by reading the data out of the
         * matchblock-section that the user provided (if any).
         */
        fun new(optMatchToken: MatchToken?): MatchBlock {
            val matchBlock = MatchBlock()
            if (optMatchToken != null) {
                for ((idx, mc) in optMatchToken.contents.withIndex()) {
                    val precedence = optMatchToken.contents.size - idx
                    for (item in mc.items) {
                        when (item) {
                            is MatchItem.Unmapped -> {
                                matchBlock.addMatchEntry(
                                    precedence,
                                    item.symbol,
                                    MatchMapping.Terminal(TerminalString.Literal(item.symbol)),
                                    item.span,
                                )
                            }
                            is MatchItem.Mapped -> {
                                matchBlock.addMatchEntry(
                                    precedence,
                                    item.symbol,
                                    item.mapping,
                                    item.span,
                                )
                            }
                            is MatchItem.CatchAll -> {
                                matchBlock.catchAll = Precedence(precedence)
                            }
                        }
                    }
                }
            } else {
                // an absent grammar matchblock is treated as a wildcard catch-all
                matchBlock.catchAll = Precedence(0)
            }
            return matchBlock
        }
    }

    fun addMatchEntry(
        matchGroupPrecedence: Int,
        sym: TerminalLiteral,
        userName: MatchMapping,
        span: Span,
    ) {
        val oldSpan = spans.put(sym, span)
        if (oldSpan != null) {
            returnErr(span, "multiple match entries for `$sym`")
        }

        // NB: It legal for multiple regex to produce same terminal.
        if (userName is MatchMapping.Terminal) {
            matchUserNames.add(userName.terminal)
        }

        matchEntries.add(
            MatchEntry(
                precedence = matchGroupPrecedence * 2 + sym.basePrecedence(),
                matchLiteral = sym,
                userName = userName,
            )
        )
    }

    fun addLiteralFromGrammar(sym: TerminalLiteral, span: Span) {
        // Already saw this literal, maybe in an entry of the grammar's match-table, maybe in the grammar.
        if (matchUserNames.contains(TerminalString.Literal(sym))) {
            return
        }

        val matchGroupPrecedence = catchAll?.value
            ?: returnErr(span, "terminal `$sym` does not have a match mapping defined for it")

        matchUserNames.add(TerminalString.Literal(sym))

        matchEntries.add(
            MatchEntry(
                precedence = matchGroupPrecedence * 2 + sym.basePrecedence(),
                matchLiteral = sym,
                userName = MatchMapping.Terminal(TerminalString.Literal(sym)),
            )
        )

        spans[sym] = span
    }
}

private fun Validator.validate() {
    for (item in grammar.items) {
        when (item) {
            is GrammarItem.Use -> {}
            is GrammarItem.MatchToken -> {}
            is GrammarItem.ExternToken -> {}
            is GrammarItem.InternToken -> {}
            is GrammarItem.Nonterminal -> {
                for (alternative in item.data.alternatives) {
                    validateAlternative(alternative)
                }
            }
        }
    }
}

private fun Validator.validateAlternative(alternative: Alternative) {
    check(alternative.condition == null) // macro expansion should have removed these
    validateExpr(alternative.expr)
}

private fun Validator.validateExpr(expr: ExprSymbol) {
    for (symbol in expr.symbols) {
        validateSymbol(symbol)
    }
}

private fun Validator.validateSymbol(symbol: Symbol) {
    when (val kind = symbol.kind) {
        is SymbolKind.Expr -> validateExpr(kind.expr)
        is SymbolKind.Terminal -> validateTerminal(symbol.span, kind.terminal)
        is SymbolKind.Nonterminal -> {}
        is SymbolKind.Repeat -> validateSymbol(kind.sym.symbol)
        is SymbolKind.Choose -> validateSymbol(kind.sym)
        is SymbolKind.Name -> validateSymbol(kind.sym)
        is SymbolKind.TupleKind -> validateSymbol(kind.sym)
        SymbolKind.Lookahead, SymbolKind.Lookbehind, SymbolKind.Error -> {}
        is SymbolKind.AmbiguousId -> error("ambiguous id `${kind.atom}` encountered after name resolution")
        is SymbolKind.Macro -> error("macro not removed: $symbol")
    }
}

private fun Validator.validateTerminal(span: Span, term: TerminalString) {
    when (val m = mode) {
        // If there is an extern token definition, validate that
        // this terminal has a defined conversion.
        is TokenMode.Extern -> {
            if (!m.conversions.contains(term)) {
                returnErr(span, "terminal `$term` does not have a pattern defined for it")
            }
        }

        // If there is no extern token definition, then collect
        // the terminal literals ("class", r"[a-z]+") into a set.
        is TokenMode.Internal -> {
            when (term) {
                is TerminalString.Bare -> check(m.matchBlock.matchUserNames.contains(term)) {
                    "bare terminal without match entry: $term"
                }
                is TerminalString.Literal -> m.matchBlock.addLiteralFromGrammar(term.literal, span)
                // Error is a builtin terminal that always exists
                TerminalString.Error -> {}
            }
        }
    }
}

/** //////////////////////////////////////////////////////////////////////// */
// Construction phase -- if we are constructing a tokenizer, this
// phase builds up an internal token Dfa.

private fun construct(grammar: Grammar, matchBlock: MatchBlock) {
    val matchEntries = matchBlock.matchEntries
    val spans = matchBlock.spans

    // Sort the matchEntries by order of increasing precedence.
    matchEntries.sort()

    // Build up two vectors, one of parsed regular expressions and
    // one of precedences, that are parallel with `literals`.
    val regexs = ArrayList<Regex>(matchEntries.size)
    val precedences = ArrayList<Precedence>(matchEntries.size)
    for (matchEntry in matchEntries) {
        precedences.add(Precedence(matchEntry.precedence))
        when (val lit = matchEntry.matchLiteral) {
            is TerminalLiteral.Quoted -> {
                regexs.add(parseLiteral(lit.atom.toString()))
            }
            is TerminalLiteral.Hir -> {
                val parsed = parseRegex(lit.atom.toString())
                parsed.fold(
                    onSuccess = { regex -> regexs.add(regex) },
                    onFailure = { error ->
                        val literalSpan = spans[matchEntry.matchLiteral]!!
                        // FIXME -- take offset into account for
                        // span; this requires knowing how many #
                        // the user used, which we do not track
                        returnErr(literalSpan, "invalid regular expression: ${error.message}")
                    },
                )
            }
        }
    }

    val dfa = buildDfa(regexs, precedences).getOrElse { throwable ->
        val e = throwable as? DfaConstructionException
            ?: throw throwable
        when (val err = e.error) {
            is DfaConstructionError.NfaConstructionErr -> {
                val feature = when (err.error) {
                    NfaConstructionError.NamedCaptures -> "named captures (`(?P<foo>...)`)"
                    NfaConstructionError.NonGreedy -> "\"non-greedy\" repetitions (`*?` or `+?`)"
                    NfaConstructionError.LookAround -> "all boundaries like `\\b` or `\\B` or `^` or `$`"
                    NfaConstructionError.ByteRegex -> "byte-based matches"
                }
                val literal = matchEntries[err.index.index()].matchLiteral
                returnErr(spans[literal]!!, "$feature are not supported in regular expressions")
            }
            is DfaConstructionError.Ambiguity -> {
                val literal0 = matchEntries[err.match0.index()].matchLiteral
                val literal1 = matchEntries[err.match1.index()].matchLiteral
                // FIXME(#88) -- it'd be nice to give an example here
                returnErr(
                    spans[literal0]!!,
                    "ambiguity detected between the terminal `$literal0` and the terminal `$literal1`",
                )
            }
        }
    }

    grammar.items.add(
        GrammarItem.InternToken(InternToken(matchEntries = matchEntries, dfa = dfa))
    )

    // we need to inject an "input" parameter for the input string slice as well:

    val inputLifetime = Lifetime.input()
    for (parameter in grammar.typeParameters) {
        if (parameter is TypeParameter.LifetimeTp && parameter.lifetime == inputLifetime) {
            returnErr(
                grammar.span,
                "since there is no external token enum specified, " +
                    "the `'input` lifetime is implicit and cannot be declared",
            )
        }
    }

    val inputParameter = Atom.from(INPUT_PARAMETER)
    for (parameter in grammar.parameters) {
        if (parameter.name == inputParameter) {
            returnErr(
                grammar.span,
                "since there is no external token enum specified, " +
                    "the `input` parameter is implicit and cannot be declared",
            )
        }
    }

    grammar.typeParameters.add(0, TypeParameter.LifetimeTp(inputLifetime))

    val parameter = Parameter(
        name = inputParameter,
        ty = TypeRef.Ref(
            lifetime = inputLifetime,
            mutable = false,
            referent = TypeRef.Id(Atom.from("str")),
        ),
    )
    grammar.parameters.add(parameter)
}
