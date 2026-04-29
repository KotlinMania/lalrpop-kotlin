// transliterated from upstream module root
/**
 * Resolves identifiers to decide if they are macros, terminals, or
 * nonterminals. Rewrites the parse tree accordingly.
 */
package io.github.kotlinmania.lalrpop.normalize.resolve

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map
import io.github.kotlinmania.lalrpop.grammar.parsetree.Alternative
import io.github.kotlinmania.lalrpop.grammar.parsetree.ExprSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.GrammarItem
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchItem
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.grammar.parsetree.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.SymbolKind
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.normalize.returnErr

fun resolve(grammar: Grammar): Grammar {
    resolveInPlace(grammar)
    return grammar
}

private fun resolveInPlace(grammar: Grammar) {
    val globals = run {
        val nonterminalIdentifiers = grammar
            .items
            .asSequence()
            .mapNotNull { it.asNonterminalItem() }
            .map { nt -> Triple(nt.span, nt.name.atom, Def.Nonterminal(nt.args.size)) }

        val terminalIdentifiers = grammar
            .items
            .asSequence()
            .mapNotNull { it.asExternTokenItem() }
            .flatMap { externToken -> externToken.enumToken?.let { sequenceOf(it) } ?: emptySequence() }
            .flatMap { enumToken -> enumToken.conversions.asSequence() }
            .mapNotNull { conversion ->
                when (val from = conversion.from) {
                    is TerminalString.Literal, TerminalString.Error -> null
                    is TerminalString.Bare -> Triple(conversion.span, from.atom, Def.Terminal)
                }
            }

        // Extract all the bare identifiers that appear in the RHS of a `match` declaration.
        // Example:
        //     match {
        //         r"(?)begin" => "BEGIN",
        //     } else {
        //         r"[a-zA-Z_][a-zA-Z0-9_]*" => ID,
        // This would result in `listOf(ID)`.
        val matchIdentifiers = grammar
            .items
            .asSequence()
            .mapNotNull { it.asMatchTokenItem() }
            .flatMap { matchToken -> matchToken.contents.asSequence() }
            .flatMap { matchContents -> matchContents.items.asSequence() }
            .mapNotNull { item ->
                if (item is MatchItem.Mapped) {
                    val mapping = item.mapping
                    if (mapping is MatchMapping.Terminal) {
                        val term = mapping.terminal
                        if (term is TerminalString.Bare) {
                            Triple(item.span(), term.atom, Def.Terminal)
                        } else null
                    } else null
                } else null
            }

        val allIdentifiers = nonterminalIdentifiers + terminalIdentifiers + matchIdentifiers

        val identifiers: Map<Atom, Def> = map()
        for ((span, id, def) in allIdentifiers) {
            val oldDef = identifiers.put(id, def)
            if (oldDef != null) {
                val description = def.description()
                val oldDescription = oldDef.description()
                if (description == oldDescription) {
                    returnErr(span, "two ${description}s declared with the name `$id`")
                } else {
                    returnErr(
                        span,
                        "$description and $oldDescription both declared with the name `$id`",
                    )
                }
            }
        }

        ScopeChain(previous = null, identifiers = identifiers)
    }

    val validator = Validator(globals = globals)

    validator.validate(grammar)
}

private fun GrammarItem.asNonterminalItem() = when (this) {
    is GrammarItem.Nonterminal -> data
    else -> null
}

private fun GrammarItem.asExternTokenItem() = when (this) {
    is GrammarItem.ExternToken -> inner
    else -> null
}

private fun GrammarItem.asMatchTokenItem() = when (this) {
    is GrammarItem.MatchToken -> inner
    else -> null
}

private class Validator(
    val globals: ScopeChain,
)

private sealed class Def {
    data object Terminal : Def()
    data class Nonterminal(val arity: Int) : Def() // argument is the number of macro arguments
    data object MacroArg : Def()

    fun description(): String = when (this) {
        is Terminal -> "terminal"
        is Nonterminal -> if (arity == 0) "nonterminal" else "macro"
        is MacroArg -> "macro argument"
    }
}

private class ScopeChain(
    val previous: ScopeChain?,
    val identifiers: Map<Atom, Def>,
) {
    fun def(id: Atom): Def? =
        identifiers[id] ?: previous?.def(id)
}

private fun Validator.validate(grammar: Grammar) {
    for (item in grammar.items) {
        when (item) {
            is GrammarItem.Use -> {}
            is GrammarItem.MatchToken -> {}
            is GrammarItem.InternToken -> {}
            is GrammarItem.ExternToken -> {}
            is GrammarItem.Nonterminal -> {
                val data = item.data
                val identifiers = this.validateMacroArgs(data.span, data.args)
                val locals = ScopeChain(
                    previous = this.globals,
                    identifiers = identifiers,
                )
                for (alternative in data.alternatives) {
                    this.validateAlternative(locals, alternative)
                }
            }
        }
    }
}

private fun Validator.validateMacroArgs(
    span: Span,
    args: List<NonterminalString>,
): Map<Atom, Def> {
    for ((index, arg) in args.withIndex()) {
        if (args.subList(0, index).contains(arg)) {
            returnErr(span, "multiple macro arguments declared with the name `$arg`")
        }
    }
    val result: Map<Atom, Def> = map()
    for (nt in args) {
        result[nt.atom] = Def.MacroArg
    }
    return result
}

private fun Validator.validateAlternative(
    scope: ScopeChain,
    alternative: Alternative,
) {
    val condition = alternative.condition
    if (condition != null) {
        val def = this.validateId(scope, condition.span, condition.lhs.atom)
        when (def) {
            is Def.MacroArg -> { /* OK */ }
            else -> {
                returnErr(
                    condition.span,
                    "only macro arguments can be used in conditions, " +
                        "not ${def.description()}s like `${condition.lhs}`",
                )
            }
        }
    }

    this.validateExpr(scope, alternative.expr)
}

private fun Validator.validateExpr(scope: ScopeChain, expr: ExprSymbol) {
    for (symbol in expr.symbols) {
        this.validateSymbol(scope, symbol)
    }
}

private fun Validator.validateSymbol(scope: ScopeChain, symbol: Symbol) {
    when (val kind = symbol.kind) {
        is SymbolKind.Expr -> {
            this.validateExpr(scope, kind.expr)
        }
        is SymbolKind.AmbiguousId -> {
            this.rewriteAmbiguousId(scope, symbol)
        }
        is SymbolKind.Terminal -> { /* see postvalidate! */ }
        is SymbolKind.Nonterminal -> {
            // in normal operation, the parser never produces Nonterminal(_) entries,
            // but during testing we do produce nonterminal entries
            val def = this.validateId(scope, symbol.span, kind.nt.atom)
            when (def) {
                is Def.Nonterminal -> if (def.arity != 0) {
                    returnErr(
                        symbol.span,
                        "`${kind.nt}` is a ${def.description()}, not a nonterminal",
                    )
                }
                is Def.MacroArg -> { /* OK */ }
                is Def.Terminal -> {
                    returnErr(
                        symbol.span,
                        "`${kind.nt}` is a ${def.description()}, not a nonterminal",
                    )
                }
            }
        }
        is SymbolKind.Macro -> {
            val msym = kind.sym
            check(msym.args.isNotEmpty())
            val def = this.validateId(scope, symbol.span, msym.name.atom)
            when (def) {
                is Def.Terminal, is Def.MacroArg -> returnErr(
                    symbol.span,
                    "`${msym.name}` is a ${def.description()}, not a macro",
                )
                is Def.Nonterminal -> {
                    if (def.arity == 0) {
                        returnErr(
                            symbol.span,
                            "`${msym.name}` is a ${def.description()}, not a macro",
                        )
                    } else if (def.arity != msym.args.size) {
                        returnErr(
                            symbol.span,
                            "wrong number of arguments to `${msym.name}`: " +
                                "expected ${def.arity}, found ${msym.args.size}",
                        )
                    }
                }
            }

            for (arg in msym.args) {
                this.validateSymbol(scope, arg)
            }
        }
        is SymbolKind.Repeat -> {
            this.validateSymbol(scope, kind.sym.symbol)
        }
        is SymbolKind.Choose -> this.validateSymbol(scope, kind.sym)
        is SymbolKind.Name -> this.validateSymbol(scope, kind.sym)
        is SymbolKind.TupleKind -> this.validateSymbol(scope, kind.sym)
        SymbolKind.Lookahead, SymbolKind.Lookbehind, SymbolKind.Error -> {}
    }
}

private fun Validator.rewriteAmbiguousId(scope: ScopeChain, symbol: Symbol) {
    val kind = symbol.kind
    val id = if (kind is SymbolKind.AmbiguousId) {
        kind.atom
    } else {
        error("Should never happen.")
    }
    symbol.kind = when (val def = this.validateId(scope, symbol.span, id)) {
        is Def.MacroArg -> SymbolKind.Nonterminal(NonterminalString(id))
        is Def.Nonterminal -> if (def.arity == 0) {
            SymbolKind.Nonterminal(NonterminalString(id))
        } else {
            returnErr(symbol.span, "`$id` is a macro")
        }
        is Def.Terminal -> SymbolKind.Terminal(TerminalString.Bare(id))
    }
}

private fun Validator.validateId(scope: ScopeChain, span: Span, id: Atom): Def =
    scope.def(id) ?: returnErr(span, "no definition found for `$id`")
