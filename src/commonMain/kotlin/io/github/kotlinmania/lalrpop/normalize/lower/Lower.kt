// port-lint: source src/normalize/lower/mod.rs
//! Lower
//!
package io.github.kotlinmania.lalrpop.normalize.lower

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.collections.map.Map
import io.github.kotlinmania.lalrpop.collections.map.map
import io.github.kotlinmania.lalrpop.grammar.parseTree.ActionKind
import io.github.kotlinmania.lalrpop.grammar.parseTree.ArgPattern
import io.github.kotlinmania.lalrpop.grammar.parseTree.ExprSymbol
import io.github.kotlinmania.lalrpop.grammar.parseTree.GrammarItem
import io.github.kotlinmania.lalrpop.grammar.parseTree.InternToken
import io.github.kotlinmania.lalrpop.grammar.parseTree.Lifetime
import io.github.kotlinmania.lalrpop.grammar.parseTree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.parseTree.Name
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.Path
import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.grammar.parseTree.Symbol as PtSymbol
import io.github.kotlinmania.lalrpop.grammar.parseTree.SymbolKind
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeBound
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeRef
import io.github.kotlinmania.lalrpop.grammar.parseTree.WhereClause as PtWhereClause
import io.github.kotlinmania.lalrpop.grammar.parseTree.Grammar as PtGrammar
import io.github.kotlinmania.lalrpop.grammar.parseTree.asNonterminal
import io.github.kotlinmania.lalrpop.grammar.pattern.Pattern
import io.github.kotlinmania.lalrpop.grammar.pattern.PatternKind
import io.github.kotlinmania.lalrpop.grammar.parseTree.readAlgorithm
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFn
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFnDefnKind
import io.github.kotlinmania.lalrpop.grammar.repr.Algorithm
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar as RGrammar
import io.github.kotlinmania.lalrpop.grammar.repr.LookaroundActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.LrCodeGeneration
import io.github.kotlinmania.lalrpop.grammar.repr.NominalTypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.NonterminalData
import io.github.kotlinmania.lalrpop.grammar.repr.Parameter
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol as RSymbol
import io.github.kotlinmania.lalrpop.grammar.repr.TerminalSet
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.grammar.repr.Types
import io.github.kotlinmania.lalrpop.grammar.repr.UserActionFnDefn
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause as RWhereClause
import io.github.kotlinmania.lalrpop.normalize.normUtil.Symbols
import io.github.kotlinmania.lalrpop.normalize.normUtil.Presence
import io.github.kotlinmania.lalrpop.normalize.normUtil.analyzeExpr
import io.github.kotlinmania.lalrpop.normalize.normUtil.checkBetweenBraces
import io.github.kotlinmania.lalrpop.normalize.condComp.cfgActive
import io.github.kotlinmania.lalrpop.normalize.returnErr
import io.github.kotlinmania.lalrpop.lr1.lookahead.Token

fun lower(session: Session, grammar: PtGrammar, types: Types): RGrammar {
    val state = LowerState.new(session, types, grammar)
    return state.lower(session, grammar)
}

private class LowerState(
    val session: Session,
    var prefix: String,
    val actionFnDefns: MutableList<ActionFnDefn>,
    val nonterminals: Map<NonterminalString, NonterminalData>,
    val conversions: MutableList<Pair<TerminalString, Pattern<TypeRepr>>>,
    var internToken: InternToken?,
    var types: Types,
    var usesErrorRecovery: Boolean,
) {
    companion object {
        fun new(session: Session, types: Types, grammar: PtGrammar): LowerState = LowerState(
            session = session,
            prefix = grammar.prefix,
            actionFnDefns = mutableListOf(),
            nonterminals = map(),
            conversions = mutableListOf(),
            types = types,
            internToken = null,
            usesErrorRecovery = false,
        )
    }
}

private fun LowerState.lower(session: Session, grammar: PtGrammar): RGrammar {
    val startSymbols = this.synthesizeStartSymbols(grammar)

    val uses: MutableList<String> = mutableListOf()
    val internalTokenPath = Path(
        absolute = false,
        ids = mutableListOf(Atom.from("Token")),
    )

    for (item in grammar.items) {
        when (item) {
            is GrammarItem.Use -> {
                uses.add(item.code)
            }

            is GrammarItem.MatchToken -> {
                // The declarations in the match token are handled
                // fully by the `token_check` when it constructs the
                //  `InternToken` -- there is nothing left to do here.
            }

            is GrammarItem.InternToken -> {
                val data = item.inner
                val span = grammar.span
                val inputStr: TypeRepr = TypeRepr.Ref(
                    lifetime = Lifetime.input(),
                    mutable = false,
                    referent = TypeRepr.Nominal(NominalTypeRepr(
                        path = Path.str(),
                        types = mutableListOf(),
                    )),
                )
                this.conversions.addAll(
                    data.matchEntries.withIndex().mapNotNull { (index, matchEntry) ->
                        when (val userName = matchEntry.userName) {
                            is MatchMapping.Terminal -> {
                                val pattern = Pattern(
                                    span = span,
                                    kind = PatternKind.TupleStruct(
                                        path = internalTokenPath,
                                        pats = mutableListOf(
                                            Pattern(
                                                span = span,
                                                kind = PatternKind.Usize(index),
                                            ),
                                            Pattern(
                                                span = span,
                                                kind = PatternKind.Choose(inputStr),
                                            ),
                                        ),
                                    ),
                                )

                                userName.terminal to pattern
                            }
                            MatchMapping.Skip -> null
                        }
                    },
                )
                this.internToken = data
            }

            is GrammarItem.ExternToken -> {
                val data = item.inner
                val enumToken = data.enumToken
                if (enumToken != null) {
                    this.conversions.addAll(
                        enumToken.conversions
                            .filter { conversion -> cfgActive(session, conversion.attributes) }
                            .map { conversion ->
                                conversion.from to conversion.to.map { t -> t.typeRepr() }
                            },
                    )
                }
            }

            is GrammarItem.Nonterminal -> {
                val nt = item.data
                val ntName = nt.name
                val productions: MutableList<Production> = nt
                    .alternatives
                    .map { alt ->
                        val ntType = this.types.nonterminalType(ntName)
                        val symbols = this.symbols(alt.expr.symbols)

                        val action = this.actionKind(ntType, alt.expr, symbols, alt.action)
                        Production(
                            nonterminal = ntName,
                            span = alt.span,
                            symbols = symbols,
                            action = action,
                        )
                    }
                    .toMutableList()
                this.nonterminals[ntName] = NonterminalData(
                    visibility = nt.visibility,
                    attributes = nt.attributes,
                    span = nt.span,
                    productions = productions,
                )
            }
        }
    }

    val parameters: MutableList<Parameter> = grammar
        .parameters
        .map { p ->
            Parameter(
                name = p.name,
                ty = p.ty.typeRepr(),
            )
        }
        .toMutableList()

    val whereClauses: MutableList<RWhereClause> = grammar
        .whereClauses
        .flatMap { wc -> this.lowerWhereClause(wc) }
        .toMutableList()

    val algorithm = Algorithm.default()

    // FIXME Error recovery only works for parse tables so temporarily only generate parse tables for
    // testing
    if (this.session.unitTest && !this.usesErrorRecovery) {
        algorithm.codegen = LrCodeGeneration.TestAll
    }

    readAlgorithm(grammar.attributes, algorithm)

    val allTerminals: MutableList<TerminalString> = this
        .conversions
        .map { c -> c.first }
        .toMutableList()
        .also {
            if (this.usesErrorRecovery) {
                it.add(TerminalString.Error)
            }
        }

    val terminalBits: Map<TerminalString, Int> = map<TerminalString, Int>().also { m ->
        for ((i, t) in allTerminals.withIndex()) {
            m[t] = i
        }
    }

    return RGrammar(
        usesErrorRecovery = this.usesErrorRecovery,
        prefix = this.prefix,
        startNonterminals = startSymbols,
        uses = uses,
        actionFnDefns = this.actionFnDefns,
        nonterminals = this.nonterminals,
        conversions = map<TerminalString, Pattern<TypeRepr>>().also { m ->
            for ((k, v) in this.conversions) m[k] = v
        },
        types = this.types,
        typeParameters = grammar.typeParameters,
        parameters = parameters,
        whereClauses = whereClauses,
        algorithm = algorithm,
        internToken = this.internToken,
        terminals = TerminalSet(
            all = allTerminals,
            bits = terminalBits,
        ),
        moduleAttributes = grammar.moduleAttributes,
    )
}

private fun LowerState.synthesizeStartSymbols(
    grammar: PtGrammar,
): Map<NonterminalString, NonterminalString> {
    val result: Map<NonterminalString, NonterminalString> = map()
    for (nt in grammar.items.mapNotNull { it.asNonterminal() }.filter { it.visibility.isPub() }) {
        // create a synthetic symbol `__Foo` for each public symbol `Foo`
        // with a rule like:
        //
        //     __Foo = Foo;
        val fakeName = NonterminalString(Atom.from("${this.prefix}${nt.name}"))
        val ntType = this.types.nonterminalType(nt.name)
        this.types.addType(fakeName, ntType)
        val expr = ExprSymbol(
            symbols = mutableListOf(PtSymbol.new(
                nt.span,
                SymbolKind.Nonterminal(fakeName),
            )),
        )
        val symbols: MutableList<RSymbol> = mutableListOf(RSymbol.Nonterminal(nt.name))
        val actionFn = this.actionFn(ntType, false, expr, symbols, null)
        val production = Production(
            nonterminal = fakeName,
            symbols = symbols,
            action = actionFn,
            span = nt.span,
        )
        this.nonterminals[fakeName] = NonterminalData(
            visibility = nt.visibility,
            attributes = mutableListOf(),
            span = nt.span,
            productions = mutableListOf(production),
        )
        result[nt.name] = fakeName
    }
    return result
}

/**
 * When we lower where clauses into `repr::WhereClause`, they get
 * flattened; so we may go from `T: Foo + Bar` into `[T: Foo, T:
 * Bar]`. We also convert to `TypeRepr` and so forth.
 */
private fun LowerState.lowerWhereClause(wc: PtWhereClause<TypeRef>): List<RWhereClause> {
    return when (wc) {
        is PtWhereClause.LifetimeClause -> wc.bounds.map { bound ->
            RWhereClause.Bound(
                subject = TypeRepr.LifetimeRepr(wc.lifetime),
                bound = TypeBound.LifetimeBound(bound),
            )
        }

        is PtWhereClause.Type -> wc.bounds
            .map { bound ->
                RWhereClause.Bound(
                    subject = wc.ty.typeRepr(),
                    bound = bound.map { it.typeRepr() },
                ) as RWhereClause
            }
            .map { bound ->
                if (wc.forall.isEmpty()) {
                    bound
                } else {
                    RWhereClause.Forall(
                        binder = wc.forall.toMutableList(),
                        clause = bound,
                    )
                }
            }
    }
}

private fun LowerState.actionKind(
    ntType: TypeRepr,
    expr: ExprSymbol,
    symbols: MutableList<RSymbol>,
    action: ActionKind?,
): ActionFn {
    return when (action) {
        is ActionKind.Lookahead -> this.lookaheadActionFn()
        is ActionKind.Lookbehind -> this.lookbehindActionFn()
        is ActionKind.User -> this.actionFn(ntType, false, expr, symbols, action.code)
        is ActionKind.Fallible -> this.actionFn(ntType, true, expr, symbols, action.code)
        null -> this.actionFn(ntType, false, expr, symbols, null)
    }
}

private fun LowerState.lookaheadActionFn(): ActionFn {
    val actionFnDefn = ActionFnDefn(
        fallible = false,
        retType = this.types.terminalLocType(),
        kind = ActionFnDefnKind.Lookaround(LookaroundActionFnDefn.Lookahead),
    )

    return this.addActionFn(actionFnDefn)
}

private fun LowerState.lookbehindActionFn(): ActionFn {
    val actionFnDefn = ActionFnDefn(
        fallible = false,
        retType = this.types.terminalLocType(),
        kind = ActionFnDefnKind.Lookaround(LookaroundActionFnDefn.Lookbehind),
    )

    return this.addActionFn(actionFnDefn)
}

private fun LowerState.actionFn(
    ntType: TypeRepr,
    fallible: Boolean,
    expr: ExprSymbol,
    symbols: MutableList<RSymbol>,
    action: String?,
): ActionFn {
    val normalizedSymbols = analyzeExpr(expr)

    val resolvedAction: String = when (action) {
        is String -> action
        else -> {
            // If the user declared a type `()`, or we inferred
            // it, then there is only one possible action that
            // will type-check (`()`), so supply that. Otherwise,
            // default is to include all selected items.
            if (ntType.isUnit()) {
                "()"
            } else {
                val len = when (normalizedSymbols) {
                    is Symbols.Named -> normalizedSymbols.list.size
                    is Symbols.Anon -> normalizedSymbols.list.size
                }
                if (len == 1) {
                    "<>"
                } else {
                    "(<>)"
                }
            }
        }
    }

    // Note that the action fn takes ALL of the symbols in `expr`
    // as arguments, and some of them are simply dropped based on
    // the user's selections.

    // The set of argument types is thus the type of all symbols:
    val argTypes: MutableList<TypeRepr> =
        symbols.map { s -> s.ty(this.types) }.toMutableList()

    val actionFnDefn: ActionFnDefn = when (normalizedSymbols) {
        is Symbols.Named -> {
            val names = normalizedSymbols.list
            // if there are named symbols, we want to give the
            // arguments the names that the user gave them:
            val argNames = names.map { (index, name, _) -> index to name }.iterator()
            val argPatterns = patterns(argNames, symbols.size)

            val finalAction: String = when (checkBetweenBraces(resolvedAction)) {
                Presence.None -> resolvedAction
                Presence.Normal -> {
                    val nameStrs: List<String> =
                        names.map { (_, name, _) -> name.name() }
                    val nameStr = nameStrs.joinToString(", ")
                    resolvedAction.replace("<>", nameStr)
                }
                Presence.InCurlyBrackets -> {
                    val nameStrs: List<String> =
                        names.map { (_, name, _) -> name.name() }
                    val nameStr = nameStrs.joinToString(", ")
                    resolvedAction.replace("<>", nameStr)
                }
            }

            ActionFnDefn(
                fallible = fallible,
                retType = ntType,
                kind = ActionFnDefnKind.User(UserActionFnDefn(
                    argPatterns = argPatterns,
                    argTypes = argTypes,
                    code = finalAction,
                )),
            )
        }
        is Symbols.Anon -> {
            val anonSymbols = normalizedSymbols.list
            val names: List<Atom> = (0 until anonSymbols.size)
                .map { i -> this.freshName(i) }

            val pIndices = anonSymbols.map { (index, _) -> index }
            val pNames: List<ArgPattern> = names.map { ArgPattern.NamePat(Name.immut(it)) }
            val argPatterns = patterns(pIndices.zip(pNames).iterator(), symbols.size)

            val nameStr: String = names.joinToString(", ") { it.asRef() }

            val occurrences = resolvedAction.split("<>").size - 1
            val finalAction: String = if (occurrences > 1) {
                if (occurrences != names.size) {
                    // Here the error span will be based on the anon_symbols
                    // since that is what I have the span information for.

                    // Alternatively, one could pass in the action span
                    // information instead of just the action string.
                    val spanStart = anonSymbols.first().second.span

                    val spanEnd = anonSymbols.last().second.span

                    val symbolsSpan = Span(spanStart.start, spanEnd.end)

                    returnErr(
                        symbolsSpan,
                        "When there are multiple `<>` in the action, " +
                            "there must be the same number of sources for the `<>`s. " +
                            "Found $occurrences `<`>`s and ${names.size} anonymous sources.",
                    )
                }
                names.fold(resolvedAction) { acc, name -> acc.replaceFirst("<>", name.asRef()) }
            } else {
                resolvedAction.replace("<>", nameStr)
            }
            ActionFnDefn(
                fallible = fallible,
                retType = ntType,
                kind = ActionFnDefnKind.User(UserActionFnDefn(
                    argPatterns = argPatterns,
                    argTypes = argTypes,
                    code = finalAction,
                )),
            )
        }
    }

    return this.addActionFn(actionFnDefn)
}

private fun LowerState.addActionFn(actionFnDefn: ActionFnDefn): ActionFn {
    val index = ActionFn.new(this.actionFnDefns.size)
    this.actionFnDefns.add(actionFnDefn)
    return index
}

private fun LowerState.symbols(symbols: List<PtSymbol>): MutableList<RSymbol> =
    symbols.map { sym -> this.symbol(sym) }.toMutableList()

private fun LowerState.symbol(symbol: PtSymbol): RSymbol {
    return when (val kind = symbol.kind) {
        is SymbolKind.Terminal -> RSymbol.Terminal(kind.terminal)
        is SymbolKind.Nonterminal -> RSymbol.Nonterminal(kind.nt)
        is SymbolKind.Choose -> this.symbol(kind.sym)
        is SymbolKind.Name -> this.symbol(kind.sym)
        is SymbolKind.TupleKind -> this.symbol(kind.sym)
        SymbolKind.Error -> {
            this.usesErrorRecovery = true
            RSymbol.Terminal(TerminalString.Error)
        }

        is SymbolKind.Macro,
        is SymbolKind.Repeat,
        is SymbolKind.Expr,
        is SymbolKind.AmbiguousId,
        SymbolKind.Lookahead,
        SymbolKind.Lookbehind -> error(
            "symbol `$symbol` should have been normalized away by now",
        )
    }
}

private fun LowerState.freshName(i: Int): Atom =
    Atom.from("${this.prefix}$i")

private fun patterns(
    chosen: Iterator<Pair<Int, ArgPattern>>,
    numArgs: Int,
): MutableList<ArgPattern> {
    val blank = Atom.from("_")

    var nextChosen: Pair<Int, ArgPattern>? = if (chosen.hasNext()) chosen.next() else null

    val result = (0 until numArgs)
        .map { index ->
            val nc = nextChosen
            if (nc != null && nc.first == index) {
                nextChosen = if (chosen.hasNext()) chosen.next() else null
                nc.second
            } else {
                ArgPattern.NamePat(Name.immut(blank))
            }
        }
        .toMutableList()

    check(nextChosen == null)

    return result
}
