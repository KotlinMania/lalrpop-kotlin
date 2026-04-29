// transliterated from upstream module root
package io.github.kotlinmania.lalrpop.normalize.macroexpand

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.ActionKind
import io.github.kotlinmania.lalrpop.grammar.parsetree.Alternative
import io.github.kotlinmania.lalrpop.grammar.parsetree.Attribute
import io.github.kotlinmania.lalrpop.grammar.parsetree.AttributeArg
import io.github.kotlinmania.lalrpop.grammar.parsetree.Condition
import io.github.kotlinmania.lalrpop.grammar.parsetree.ConditionOp
import io.github.kotlinmania.lalrpop.grammar.parsetree.ExprSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.GrammarItem
import io.github.kotlinmania.lalrpop.grammar.INLINE
import io.github.kotlinmania.lalrpop.grammar.parsetree.MacroSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.Name
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalData
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.Path
import io.github.kotlinmania.lalrpop.grammar.parsetree.RepeatOp
import io.github.kotlinmania.lalrpop.grammar.parsetree.RepeatSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.grammar.parsetree.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.SymbolKind
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalLiteral
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.Tuple
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeRef
import io.github.kotlinmania.lalrpop.grammar.parsetree.isMacroDef
import io.github.kotlinmania.lalrpop.grammar.parsetree.Visibility
import io.github.kotlinmania.lalrpop.normalize.normutil.Symbols
import io.github.kotlinmania.lalrpop.normalize.normutil.analyzeExpr
import io.github.kotlinmania.lalrpop.normalize.resolve.resolve
import io.github.kotlinmania.lalrpop.normalize.returnErr
import io.github.kotlinmania.lalrpop.collections.Map
import io.github.kotlinmania.lalrpop.collections.map

fun expandMacros(input: Grammar, recursionLimit: Int): Grammar {
    val inputResolved = resolve(input)

    val items = inputResolved.items

    val macroDefs: MutableList<GrammarItem> = mutableListOf()
    val remainingItems: MutableList<GrammarItem> = mutableListOf()
    for (item in items) {
        if (item.isMacroDef()) macroDefs.add(item) else remainingItems.add(item)
    }

    val macroDefsMap: MutableMap<NonterminalString, NonterminalData> = macroDefs
        .map { md ->
            when (md) {
                is GrammarItem.Nonterminal -> md.data.name to md.data
                else -> error("expected macro def to be a nonterminal")
            }
        }
        .toMap()
        .toMutableMap()

    val expander = MacroExpander.new(macroDefsMap)
    expander.expand(remainingItems, recursionLimit)

    inputResolved.items = remainingItems
    return inputResolved
}

private class MacroExpander(
    val macroDefs: MutableMap<NonterminalString, NonterminalData>,
    val expansionSet: MutableSet<NonterminalString>,
    val expansionStack: MutableList<Symbol>,
) {
    companion object {
        fun new(macroDefs: MutableMap<NonterminalString, NonterminalData>): MacroExpander =
            MacroExpander(
                macroDefs = macroDefs,
                expansionStack = mutableListOf(),
                expansionSet = mutableSetOf(),
            )
    }
}

private fun MacroExpander.expand(items: MutableList<GrammarItem>, recursionLimit: Int) {
    var counter = 0 // Number of items
    var loopCounter = 0

    while (true) {
        // Find any macro uses in items added since last round and
        // replace them in place with the expanded version:
        for (i in counter until items.size) {
            this.replaceItem(items[i])
        }
        counter = items.size
        loopCounter += 1

        // No more expansion to do.
        if (this.expansionStack.isEmpty()) {
            return
        }

        if (loopCounter > recursionLimit) {
            // Too much recursion
            // We know unwrap() is safe, because we just checked isEmpty()
            val sym = this.expansionStack.removeAt(this.expansionStack.size - 1)
            returnErr(
                sym.span,
                "Exceeded recursion cap ($recursionLimit) while expanding this macro.  " +
                    "This typically is a symptom of infinite recursion during macro resolution.  " +
                    "If you believe the recursion will complete eventually, you can increase this " +
                    "limit using Configuration::set_macro_recursion_limit().",
            )
        }

        // Drain expansion stack:
        while (this.expansionStack.isNotEmpty()) {
            val sym = this.expansionStack.removeAt(this.expansionStack.size - 1)
            when (val kind = sym.kind) {
                is SymbolKind.Macro ->
                    items.add(this.expandMacroSymbol(sym.span, kind.sym))
                is SymbolKind.Expr ->
                    items.add(this.expandExprSymbol(sym.span, kind.expr))
                is SymbolKind.Repeat ->
                    items.add(this.expandRepeatSymbol(sym.span, kind.sym))
                SymbolKind.Lookahead -> items.add(this.expandLookaroundSymbol(
                    sym.span,
                    "@L",
                    ActionKind.Lookahead,
                ))
                SymbolKind.Lookbehind -> items.add(this.expandLookaroundSymbol(
                    sym.span,
                    "@R",
                    ActionKind.Lookbehind,
                ))
                else -> error("don't know how to expand `$sym`")
            }
        }
    }
}

private fun MacroExpander.replaceItem(item: GrammarItem) {
    when (item) {
        is GrammarItem.MatchToken -> {}
        is GrammarItem.ExternToken -> {}
        is GrammarItem.InternToken -> {}
        is GrammarItem.Use -> {}
        is GrammarItem.Nonterminal -> {
            // Should not encounter macro definitions here,
            // they've already been siphoned off.
            check(!item.data.isMacroDef())

            for (alternative in item.data.alternatives) {
                this.replaceSymbols(alternative.expr.symbols)
            }
        }
    }
}

private fun MacroExpander.replaceSymbols(symbols: MutableList<Symbol>) {
    for (symbol in symbols) {
        this.replaceSymbol(symbol)
    }
}

private fun MacroExpander.replaceSymbol(symbol: Symbol) {
    when (val kind = symbol.kind) {
        is SymbolKind.AmbiguousId -> {
            error("ambiguous id `${kind.atom}` encountered after name resolution")
        }
        is SymbolKind.Macro -> {
            for (sym in kind.sym.args) {
                this.replaceSymbol(sym)
            }
        }
        is SymbolKind.Expr -> {
            this.replaceSymbols(kind.expr.symbols)
        }
        is SymbolKind.Repeat -> {
            this.replaceSymbol(kind.sym.symbol)
        }
        is SymbolKind.Terminal, is SymbolKind.Nonterminal, SymbolKind.Error -> {
            return
        }
        is SymbolKind.Choose -> {
            this.replaceSymbol(kind.sym)
            return
        }
        is SymbolKind.Name -> {
            this.replaceSymbol(kind.sym)
            return
        }
        is SymbolKind.TupleKind -> {
            this.replaceSymbol(kind.sym)
            return
        }
        SymbolKind.Lookahead, SymbolKind.Lookbehind -> {}
    }

    // only symbols we intend to expand fallthrough to here

    val key = NonterminalString(Atom.from(symbol.canonicalForm()))
    val replacement = SymbolKind.Nonterminal(key)
    val toExpand = Symbol(span = symbol.span, kind = symbol.kind)
    symbol.kind = replacement
    if (this.expansionSet.add(key)) {
        this.expansionStack.add(toExpand)
    }
}

/** //////////////////////////////////////////////////////////////////////// */
// Macro expansion

private fun MacroExpander.expandMacroSymbol(span: Span, msym: MacroSymbol): GrammarItem {
    val msymName = NonterminalString(Atom.from(msym.canonicalForm()))

    val mdef = this.macroDefs[msym.name]
        ?: returnErr(span, "no macro definition found for `${msym.name}`")

    if (mdef.args.size != msym.args.size) {
        returnErr(
            span,
            "expected ${mdef.args.size} arguments to `${msym.name}` but found ${msym.args.size}",
        )
    }

    val args: Map<NonterminalString, SymbolKind> = map<NonterminalString, SymbolKind>().also { out ->
        for ((name, kind) in mdef.args.zip(msym.args.map { it.kind })) {
            out[name] = kind
        }
    }

    val typeDecl = mdef.typeDecl?.let { tr -> this.macroExpandTypeRef(args, tr) }

    // due to the import of `?`, it a bit awkward to write this with an iterator
    val alternatives: MutableList<Alternative> = mutableListOf()

    for (alternative in mdef.alternatives) {
        if (!this.evaluateCond(args, alternative.condition)) {
            continue
        }
        alternatives.add(Alternative(
            span = span,
            expr = this.macroExpandExprSymbol(args, alternative.expr),
            condition = null,
            action = alternative.action,
            attributes = alternative.attributes.toMutableList(),
        ))
    }

    return GrammarItem.Nonterminal(NonterminalData(
        visibility = mdef.visibility,
        span = span,
        name = msymName,
        attributes = mdef.attributes.toMutableList(),
        args = mutableListOf(),
        typeDecl = typeDecl,
        alternatives = alternatives,
    ))
}

private fun MacroExpander.macroExpandTypeRefs(
    args: Map<NonterminalString, SymbolKind>,
    typeRefs: List<TypeRef>,
): MutableList<TypeRef> {
    return typeRefs.map { tr -> this.macroExpandTypeRef(args, tr) }.toMutableList()
}

private fun MacroExpander.macroExpandTypeRef(
    args: Map<NonterminalString, SymbolKind>,
    typeRef: TypeRef,
): TypeRef {
    return when (typeRef) {
        is TypeRef.Tuple -> TypeRef.Tuple(this.macroExpandTypeRefs(args, typeRef.types))
        is TypeRef.Slice -> TypeRef.Slice(this.macroExpandTypeRef(args, typeRef.ty))
        is TypeRef.Nominal -> TypeRef.Nominal(
            path = typeRef.path,
            types = this.macroExpandTypeRefs(args, typeRef.types),
        )
        is TypeRef.LifetimeRef -> TypeRef.LifetimeRef(typeRef.lifetime)
        is TypeRef.OfSymbol -> TypeRef.OfSymbol(typeRef.kind)
        is TypeRef.Ref -> TypeRef.Ref(
            lifetime = typeRef.lifetime,
            mutable = typeRef.mutable,
            referent = this.macroExpandTypeRef(args, typeRef.referent),
        )
        is TypeRef.Id -> {
            val sym = args[NonterminalString(typeRef.atom)]
            if (sym != null) {
                TypeRef.OfSymbol(sym)
            } else {
                TypeRef.Nominal(
                    path = Path.fromId(typeRef.atom),
                    types = mutableListOf(),
                )
            }
        }
        is TypeRef.TraitObject -> TypeRef.TraitObject(
            path = typeRef.path,
            types = this.macroExpandTypeRefs(args, typeRef.types),
        )
        is TypeRef.Fn -> TypeRef.Fn(
            forall = typeRef.forall.toMutableList(),
            path = typeRef.path,
            parameters = this.macroExpandTypeRefs(args, typeRef.parameters),
            ret = typeRef.ret?.let { this.macroExpandTypeRef(args, it) },
        )
    }
}

private fun MacroExpander.evaluateCond(
    args: Map<NonterminalString, SymbolKind>,
    optCond: Condition?,
): Boolean {
    if (optCond != null) {
        val c = optCond
        val lhsSym = args.getValue(c.lhs)
        return when (lhsSym) {
            is SymbolKind.Terminal -> {
                val term = lhsSym.terminal
                if (term is TerminalString.Literal && term.literal is TerminalLiteral.Quoted) {
                    val lhs = term.literal.atom
                    when (c.op) {
                        ConditionOp.Equals -> lhs == c.rhs
                        ConditionOp.NotEquals -> lhs != c.rhs
                        ConditionOp.Match -> this.reMatch(c.span, lhs, c.rhs)
                        ConditionOp.NotMatch -> !this.reMatch(c.span, lhs, c.rhs)
                    }
                } else {
                    returnErr(
                        c.span,
                        "invalid condition LHS `${c.lhs}`, expected a string literal, not `$lhsSym`",
                    )
                }
            }
            else -> {
                returnErr(
                    c.span,
                    "invalid condition LHS `${c.lhs}`, expected a string literal, not `$lhsSym`",
                )
            }
        }
    } else {
        return true
    }
}

private fun MacroExpander.reMatch(span: Span, lhs: Atom, regex: Atom): Boolean {
    val re = try {
        Hir(regex.asRef())
    } catch (err: Exception) {
        returnErr(span, "invalid regular expression `$regex`: ${err.message}")
    }
    return re.containsMatchIn(lhs.asRef())
}

private fun MacroExpander.macroExpandSymbols(
    args: Map<NonterminalString, SymbolKind>,
    expr: List<Symbol>,
): MutableList<Symbol> {
    return expr.map { s -> this.macroExpandSymbol(args, s) }.toMutableList()
}

private fun MacroExpander.macroExpandExprSymbol(
    args: Map<NonterminalString, SymbolKind>,
    expr: ExprSymbol,
): ExprSymbol {
    return ExprSymbol(
        symbols = this.macroExpandSymbols(args, expr.symbols),
    )
}

private fun MacroExpander.macroExpandSymbol(
    args: Map<NonterminalString, SymbolKind>,
    symbol: Symbol,
): Symbol {
    val kind: SymbolKind = when (val k = symbol.kind) {
        is SymbolKind.Expr -> SymbolKind.Expr(this.macroExpandExprSymbol(args, k.expr))
        is SymbolKind.Terminal -> SymbolKind.Terminal(k.terminal)
        is SymbolKind.Nonterminal -> args[k.nt] ?: SymbolKind.Nonterminal(k.nt)
        is SymbolKind.Macro -> SymbolKind.Macro(MacroSymbol(
            name = k.sym.name,
            args = this.macroExpandSymbols(args, k.sym.args),
        ))
        is SymbolKind.Repeat -> SymbolKind.Repeat(RepeatSymbol(
            op = k.sym.op,
            symbol = this.macroExpandSymbol(args, k.sym.symbol),
        ))
        is SymbolKind.Choose -> SymbolKind.Choose(this.macroExpandSymbol(args, k.sym))
        is SymbolKind.Name -> SymbolKind.Name(
            Name.new(k.name.mutable, k.name.name),
            this.macroExpandSymbol(args, k.sym),
        )
        is SymbolKind.TupleKind -> SymbolKind.TupleKind(
            Tuple.new(k.tuple.tuples.toMutableList()),
            this.macroExpandSymbol(args, k.sym),
        )
        SymbolKind.Lookahead -> SymbolKind.Lookahead
        SymbolKind.Lookbehind -> SymbolKind.Lookbehind
        SymbolKind.Error -> SymbolKind.Error
        is SymbolKind.AmbiguousId -> {
            error("ambiguous id `${k.atom}` encountered after name resolution")
        }
    }

    return Symbol(
        span = symbol.span,
        kind = kind,
    )
}

/** //////////////////////////////////////////////////////////////////////// */
// Expr expansion

private fun MacroExpander.expandExprSymbol(span: Span, expr: ExprSymbol): GrammarItem {
    val name = NonterminalString(Atom.from(expr.canonicalForm()))

    val pair: Pair<ActionKind?, TypeRef> = when (val syms = analyzeExpr(expr)) {
        is Symbols.Named -> {
            val exId = syms.list[0].second
            val exSym = syms.list[0].third
            returnErr(
                span,
                "named symbols like `$exId:$exSym` are only allowed at the top-level of a nonterminal",
            )
        }
        is Symbols.Anon -> {
            val action: ActionKind? = if (syms.list.size == 1) {
                action("<>")
            } else {
                action("(<>)")
            }
            val ty = maybeTuple(
                syms.list.map { (_, s) -> TypeRef.OfSymbol(s.kind) }.toMutableList(),
            )
            action to ty
        }
    }
    val action = pair.first
    val tyRef = pair.second

    return GrammarItem.Nonterminal(NonterminalData(
        visibility = Visibility.Priv,
        span = span,
        name = name,
        attributes = inline(span),
        args = mutableListOf(),
        typeDecl = tyRef,
        alternatives = mutableListOf(Alternative(
            span = span,
            expr = expr,
            condition = null,
            action = action,
            attributes = mutableListOf(),
        )),
    ))
}

/** //////////////////////////////////////////////////////////////////////// */
// Expr expansion

private fun MacroExpander.expandRepeatSymbol(span: Span, repeat: RepeatSymbol): GrammarItem {
    val name = NonterminalString(Atom.from(repeat.canonicalForm()))
    val v = Atom.from("v")
    val e = Atom.from("e")

    val baseSymbolTy = TypeRef.OfSymbol(repeat.symbol.kind)

    return when (repeat.op) {
        RepeatOp.Star -> {
            val path = Path.vec()
            val tyRef = TypeRef.Nominal(
                path = path,
                types = mutableListOf(baseSymbolTy),
            )

            val plusRepeat = RepeatSymbol(
                op = RepeatOp.Plus,
                symbol = repeat.symbol,
            )

            GrammarItem.Nonterminal(NonterminalData(
                visibility = Visibility.Priv,
                span = span,
                name = name,
                attributes = inline(span),
                args = mutableListOf(),
                typeDecl = tyRef,
                alternatives = mutableListOf(
                    // X* =
                    Alternative(
                        span = span,
                        expr = ExprSymbol(symbols = mutableListOf()),
                        condition = null,
                        action = action("alloc::vec![]"),
                        attributes = mutableListOf(),
                    ),
                    // X* = <v:X+>
                    Alternative(
                        span = span,
                        expr = ExprSymbol(
                            symbols = mutableListOf(Symbol.new(
                                span,
                                SymbolKind.Name(
                                    Name.immut(v),
                                    Symbol.new(
                                        span,
                                        SymbolKind.Repeat(plusRepeat),
                                    ),
                                ),
                            )),
                        ),
                        condition = null,
                        action = action("v"),
                        attributes = mutableListOf(),
                    ),
                ),
            ))
        }

        RepeatOp.Plus -> {
            val path = Path.vec()
            val tyRef = TypeRef.Nominal(
                path = path,
                types = mutableListOf(baseSymbolTy),
            )

            GrammarItem.Nonterminal(NonterminalData(
                visibility = Visibility.Priv,
                span = span,
                name = name,
                attributes = mutableListOf(),
                args = mutableListOf(),
                typeDecl = tyRef,
                alternatives = mutableListOf(
                    // X+ = X
                    Alternative(
                        span = span,
                        expr = ExprSymbol(
                            symbols = mutableListOf(repeat.symbol),
                        ),
                        condition = null,
                        action = action("alloc::vec![<>]"),
                        attributes = mutableListOf(),
                    ),
                    // X+ = <v:X+> <e:X>
                    Alternative(
                        span = span,
                        expr = ExprSymbol(
                            symbols = mutableListOf(
                                Symbol.new(
                                    span,
                                    SymbolKind.Name(
                                        Name.immut(v),
                                        Symbol.new(
                                            span,
                                            SymbolKind.Nonterminal(name),
                                        ),
                                    ),
                                ),
                                Symbol.new(
                                    span,
                                    SymbolKind.Name(Name.immut(e), repeat.symbol),
                                ),
                            ),
                        ),
                        condition = null,
                        action = action("{ let mut v = v; v.push(e); v }"),
                        attributes = mutableListOf(),
                    ),
                ),
            ))
        }

        RepeatOp.Question -> {
            val path = Path.option()
            val tyRef = TypeRef.Nominal(
                path = path,
                types = mutableListOf(baseSymbolTy),
            )

            GrammarItem.Nonterminal(NonterminalData(
                visibility = Visibility.Priv,
                span = span,
                name = name,
                attributes = inline(span),
                args = mutableListOf(),
                typeDecl = tyRef,
                alternatives = mutableListOf(
                    // X? = X => Some(<>)
                    Alternative(
                        span = span,
                        expr = ExprSymbol(
                            symbols = mutableListOf(repeat.symbol),
                        ),
                        condition = null,
                        action = action("Some(<>)"),
                        attributes = mutableListOf(),
                    ),
                    // X? = { => None; }
                    Alternative(
                        span = span,
                        expr = ExprSymbol(symbols = mutableListOf()),
                        condition = null,
                        action = action("None"),
                        attributes = mutableListOf(),
                    ),
                ),
            ))
        }
    }
}

private fun MacroExpander.expandLookaroundSymbol(
    span: Span,
    name: String,
    action: ActionKind,
): GrammarItem {
    val nameNt = NonterminalString(Atom.from(name))
    return GrammarItem.Nonterminal(NonterminalData(
        visibility = Visibility.Priv,
        span = span,
        name = nameNt,
        attributes = inline(span),
        args = mutableListOf(),
        typeDecl = null,
        alternatives = mutableListOf(Alternative(
            span = span,
            expr = ExprSymbol(symbols = mutableListOf()),
            condition = null,
            action = action,
            attributes = mutableListOf(),
        )),
    ))
}

private fun maybeTuple(v: MutableList<TypeRef>): TypeRef {
    return if (v.size == 1) {
        v.first()
    } else {
        TypeRef.Tuple(v)
    }
}

private fun action(s: String): ActionKind? = ActionKind.User(s)

private fun inline(span: Span): MutableList<Attribute> = mutableListOf(Attribute(
    idSpan = span,
    id = Atom.from(INLINE),
    arg = AttributeArg.Empty,
))
