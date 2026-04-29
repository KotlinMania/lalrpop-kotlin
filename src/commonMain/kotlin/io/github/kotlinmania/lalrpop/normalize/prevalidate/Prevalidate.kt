// port-lint: source normalize/prevalidate/mod.rs
//! Validate checks some basic safety conditions.
package io.github.kotlinmania.lalrpop.normalize.prevalidate

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.collections.Multimap
import io.github.kotlinmania.lalrpop.collections.VecCollection
import io.github.kotlinmania.lalrpop.collections.set
import io.github.kotlinmania.lalrpop.grammar.parsetree.ActionKind
import io.github.kotlinmania.lalrpop.grammar.parsetree.Alternative
import io.github.kotlinmania.lalrpop.grammar.parsetree.Attribute
import io.github.kotlinmania.lalrpop.grammar.parsetree.AttributeArg
import io.github.kotlinmania.lalrpop.grammar.CFG
import io.github.kotlinmania.lalrpop.grammar.ERROR
import io.github.kotlinmania.lalrpop.grammar.parsetree.ExprSymbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.ExternToken
import io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.GrammarItem
import io.github.kotlinmania.lalrpop.grammar.INLINE
import io.github.kotlinmania.lalrpop.grammar.LALR
import io.github.kotlinmania.lalrpop.grammar.LOCATION
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchToken
import io.github.kotlinmania.lalrpop.grammar.RECURSIVE_ASCENT
import io.github.kotlinmania.lalrpop.grammar.parsetree.Symbol
import io.github.kotlinmania.lalrpop.grammar.parsetree.SymbolKind
import io.github.kotlinmania.lalrpop.grammar.TABLE_DRIVEN
import io.github.kotlinmania.lalrpop.grammar.TEST_ALL
import io.github.kotlinmania.lalrpop.grammar.parsetree.readAlgorithm
import io.github.kotlinmania.lalrpop.grammar.repr.Algorithm
import io.github.kotlinmania.lalrpop.grammar.repr.LrCodeGeneration
import io.github.kotlinmania.lalrpop.normalize.normutil.Symbols
import io.github.kotlinmania.lalrpop.normalize.normutil.analyzeExpr
import io.github.kotlinmania.lalrpop.normalize.normutil.checkBetweenBraces
import io.github.kotlinmania.lalrpop.normalize.precedence.ASSOC_ATTR
import io.github.kotlinmania.lalrpop.normalize.precedence.Assoc
import io.github.kotlinmania.lalrpop.normalize.precedence.LVL_ARG
import io.github.kotlinmania.lalrpop.normalize.precedence.PREC_ATTR
import io.github.kotlinmania.lalrpop.normalize.precedence.SIDE_ARG
import io.github.kotlinmania.lalrpop.normalize.returnErr

fun validate(grammar: Grammar) {
    val matchToken: MatchToken? = grammar
        .items
        .asSequence()
        .mapNotNull { if (it is GrammarItem.MatchToken) it.inner else null }
        .firstOrNull()

    val externToken: ExternToken? = grammar
        .items
        .asSequence()
        .mapNotNull { if (it is GrammarItem.ExternToken) it.inner else null }
        .firstOrNull()

    val validator = Validator(
        grammar = grammar,
        matchToken = matchToken,
        externToken = externToken,
    )

    validator.validate()
}

private class Validator(
    val grammar: Grammar,
    val matchToken: MatchToken?,
    val externToken: ExternToken?,
)

private fun Validator.validate() {
    val allowedNames = listOf(
        Atom.from(LALR),
        Atom.from(TABLE_DRIVEN),
        Atom.from(RECURSIVE_ASCENT),
        Atom.from(TEST_ALL),
    )
    for (attribute in this.grammar.attributes) {
        if (!allowedNames.contains(attribute.id)) {
            returnErr(attribute.idSpan, "unrecognized attribute `${attribute.id}`")
        }
    }

    for (item in this.grammar.items) {
        when (item) {
            is GrammarItem.Use -> {}

            is GrammarItem.MatchToken -> {
                val data = item.inner
                if (data.span != this.matchToken!!.span) {
                    returnErr(data.span, "multiple match definitions are not permitted")
                }

                // Only error if a custom lexer is specified, having a custom types is ok
                val d = this.externToken
                if (d != null) {
                    if (d.enumToken != null) {
                        returnErr(
                            d.span,
                            "extern (with custom tokens) and match definitions are mutually exclusive",
                        )
                    }
                }
            }

            is GrammarItem.ExternToken -> {
                val data = item.inner
                if (data.span != this.externToken!!.span) {
                    returnErr(data.span, "multiple extern definitions are not permitted")
                }

                // Only error if a custom lexer is specified, having a custom types is ok
                val d = this.matchToken
                if (d != null) {
                    if (data.enumToken != null) {
                        returnErr(
                            d.span,
                            "match and extern (with custom tokens) definitions are mutually exclusive",
                        )
                    }
                }

                val allowedExternNames = listOf(Atom.from(LOCATION), Atom.from(ERROR))
                val newNames = set<Atom>()
                for (associatedType in data.associatedTypes) {
                    if (!allowedExternNames.contains(associatedType.typeName)) {
                        returnErr(
                            associatedType.typeSpan,
                            "associated type `${associatedType.typeName}` not recognized, " +
                                "try one of the following: ${Sep(", ", allowedExternNames)}",
                        )
                    } else if (!newNames.add(associatedType.typeName)) {
                        returnErr(
                            associatedType.typeSpan,
                            "associated type `${associatedType.typeName}` already specified",
                        )
                    }
                }
            }
            is GrammarItem.Nonterminal -> {
                val data = item.data
                if (data.visibility.isPub() && data.args.isNotEmpty()) {
                    returnErr(data.span, "macros cannot be marked public")
                }
                val inlineAttribute = Atom.from(INLINE)
                val cfgAttribute = Atom.from(CFG)
                val knownAttributes = listOf(inlineAttribute, cfgAttribute)
                val foundAttributes = set<Atom>()
                for (attribute in data.attributes) {
                    if (!knownAttributes.contains(attribute.id)) {
                        returnErr(attribute.idSpan, "unrecognized attribute `${attribute.id}`")
                    } else if (!foundAttributes.add(attribute.id)) {
                        returnErr(attribute.idSpan, "duplicate attribute `${attribute.id}`")
                    } else if (attribute.id == inlineAttribute && data.visibility.isPub()) {
                        returnErr(attribute.idSpan, "public items cannot be marked #[inline]")
                    } else if (attribute.id == cfgAttribute) {
                        this.validateCfgAttr(attribute)
                    }
                }

                this.validatePrecedence(data.alternatives)

                for (alternative in data.alternatives) {
                    this.validateAlternative(alternative)
                }
            }
            is GrammarItem.InternToken -> {}
        }
    }
}

private fun Validator.validatePrecedence(alternatives: List<Alternative>) {
    val withPrecedence = alternatives.any { alt ->
        alt.attributes.any { attr ->
            attr.id == Atom.from(PREC_ATTR) || attr.id == Atom.from(ASSOC_ATTR)
        }
    }

    if (alternatives.isEmpty() || !withPrecedence) {
        return
    }

    // Used to check the absence of associativity attributes at the minimum level.
    var minLvl: UInt = UInt.MAX_VALUE
    var minPrecAnn: Attribute? = null

    // Check that at least the first alternative has a precedence attribute
    val first = alternatives.first()
    val attrPrecOpt0 = first.attributes.firstOrNull { attr -> attr.id == Atom.from(PREC_ATTR) }
    if (attrPrecOpt0 == null) {
        returnErr(first.span, "missing precedence attribute on the first alternative")
    }

    // Check that attributes are well-formed
    for (alt in alternatives) {
        val attrPrecOpt = alt.attributes.firstOrNull { attr -> attr.id == Atom.from(PREC_ATTR) }
        val attrAssocOpt = alt.attributes.firstOrNull { attr -> attr.id == Atom.from(ASSOC_ATTR) }

        if (attrPrecOpt != null) {
            val argEq = attrPrecOpt.getArgEqual()
            when {
                argEq != null && argEq.first == Atom.from(LVL_ARG) -> {
                    val lvl = argEq.second.toUIntOrNull()
                    if (lvl != null) {
                        if (lvl < minLvl) {
                            minLvl = lvl
                            minPrecAnn = attrAssocOpt
                        } else if (lvl == minLvl && minPrecAnn == null && attrAssocOpt != null) {
                            minPrecAnn = attrAssocOpt
                        }
                    } else {
                        returnErr(
                            attrPrecOpt.idSpan,
                            "could not parse the precedence level `${argEq.second}`, expected integer",
                        )
                    }
                }
                argEq != null -> returnErr(
                    attrPrecOpt.idSpan,
                    "invalid argument `${argEq.first}` for precedence attribute, expected `$LVL_ARG`",
                )
                else -> returnErr(
                    attrPrecOpt.idSpan,
                    "missing argument for precedence attribute, expected `$LVL_ARG`",
                )
            }
        }

        if (attrAssocOpt != null) {
            val argEq = attrAssocOpt.getArgEqual()
            when {
                argEq != null && argEq.first == Atom.from(SIDE_ARG) -> {
                    if (Assoc.parse(argEq.second) == null) {
                        returnErr(
                            attrAssocOpt.idSpan,
                            "could not parse the associativity `${argEq.second}`, expected `left`, `right`, `none` or `all`",
                        )
                    }
                }
                argEq != null -> returnErr(
                    attrAssocOpt.idSpan,
                    "invalid argument `${argEq.first}` for associativity attribute, expected `$SIDE_ARG`",
                )
                else -> returnErr(
                    attrAssocOpt.idSpan,
                    "missing argument for associativity attribute, expected `$SIDE_ARG`",
                )
            }
        }
    }

    val attr = minPrecAnn
    if (attr != null) {
        returnErr(
            attr.idSpan,
            "cannot set associativity on the first precedence level $minLvl",
        )
    }
}

private fun Validator.validateAlternative(alternative: Alternative) {
    this.validateExpr(alternative.expr)

    val allowedNames = listOf(
        Atom.from(PREC_ATTR),
        Atom.from(ASSOC_ATTR),
        Atom.from(CFG),
    )

    for (attribute in alternative.attributes) {
        if (!allowedNames.contains(attribute.id)) {
            returnErr(attribute.idSpan, "unrecognized attribute `${attribute.id}`")
        }
    }

    when (val syms = analyzeExpr(alternative.expr)) {
        is Symbols.Named -> {
            if (alternative.action == null) {
                val sym = syms.list.first().third
                returnErr(
                    sym.span,
                    "named symbols (like `$sym`) require a custom action",
                )
            }
        }
        is Symbols.Anon -> {
            val emptyString = ""
            val action = when (val a = alternative.action) {
                is ActionKind.User -> a.code
                is ActionKind.Fallible -> a.code
                else -> emptyString
            }
            if (checkBetweenBraces(action).isInCurlyBrackets()) {
                returnErr(
                    alternative.span,
                    "Using `<>` between curly braces (e.g., `{{<>}}`) only works when your parsed values have been given names (e.g., `<x:Foo>`, not just `<Foo>`)",
                )
            }
        }
    }
}

private fun Validator.validateExpr(expr: ExprSymbol) {
    for (symbol in expr.symbols) {
        this.validateSymbol(symbol)
    }

    val chosen: List<Symbol> = expr
        .symbols
        .filter { sym -> sym.kind is SymbolKind.Choose }

    val named: Multimap<Atom, VecCollection<Symbol>, Symbol> =
        Multimap { VecCollection<Symbol>() }
    for (sym in expr.symbols) {
        val k = sym.kind
        if (k is SymbolKind.Name) {
            named.push(k.name.name, sym)
        }
    }

    if (chosen.isNotEmpty() && !named.isEmpty()) {
        val firstNamed = named.iterator().next().second.asList()[0]
        returnErr(
            chosen[0].span,
            "anonymous symbols like this one cannot be combined with " +
                "named symbols like `$firstNamed`",
        )
    }

    for ((name, syms) in named) {
        val list = syms.asList()
        if (list.size > 1) {
            returnErr(
                list[1].span,
                "multiple symbols named `$name` are not permitted",
            )
        }
    }
}

private fun Validator.validateSymbol(symbol: Symbol) {
    when (val kind = symbol.kind) {
        is SymbolKind.Expr -> {
            this.validateExpr(kind.expr)
        }
        is SymbolKind.AmbiguousId -> { /* see resolve */ }
        is SymbolKind.Terminal -> { /* see postvalidate! */ }
        is SymbolKind.Nonterminal -> { /* see resolve */ }
        is SymbolKind.Error -> {
            val algorithm = Algorithm.default()
            readAlgorithm(this.grammar.attributes, algorithm)
            if (algorithm.codegen == LrCodeGeneration.RecursiveAscent ||
                algorithm.codegen == LrCodeGeneration.TestAll
            ) {
                returnErr(
                    symbol.span,
                    "error recovery is not yet supported by recursive ascent parsers",
                )
            }
        }
        is SymbolKind.Macro -> {
            val msym = kind.sym
            if (msym.args.isEmpty()) {
                returnErr(symbol.span, "macros must have at least one argument")
            }
            for (arg in msym.args) {
                this.validateSymbol(arg)
            }
        }
        is SymbolKind.Repeat -> {
            this.validateSymbol(kind.sym.symbol)
        }
        is SymbolKind.Choose -> this.validateSymbol(kind.sym)
        is SymbolKind.Name -> this.validateSymbol(kind.sym)
        is SymbolKind.TupleKind -> this.validateSymbol(kind.sym)
        SymbolKind.Lookahead, SymbolKind.Lookbehind -> {
            // if using an internal tokenizer, lookahead/lookbehind are ok.
            val externToken = this.externToken
            if (externToken != null) {
                if (externToken.enumToken != null) {
                    // otherwise, the Location type must be specified.
                    val loc = Atom.from(LOCATION)
                    if (this.externToken.associatedType(loc) == null) {
                        returnErr(
                            symbol.span,
                            "lookahead/lookbehind require you to declare the type of " +
                                "a location; add a `type $LOCATION = ..` statement to the extern token " +
                                "block",
                        )
                    }
                }
            }
        }
    }
}

private fun Validator.validateCfgAttr(attr: Attribute) {
    // example of valid formats:
    fun validateCfgArg(attr: Attribute) {
        when {
            attr.id == Atom.from("feature") -> {
                when (attr.arg) {
                    is AttributeArg.Equal -> { /* Ok */ }
                    else -> returnErr(
                        attr.idSpan,
                        "expected a `not()`, `any()`, `all()` or `feature = \"my_feature\" argument",
                    )
                }
            }
            attr.id == Atom.from("not") -> {
                when (val a = attr.arg) {
                    is AttributeArg.Paren -> {
                        val firstAttr = a.attrs.firstOrNull()
                        if (firstAttr != null) {
                            validateCfgArg(firstAttr)
                        } else {
                            returnErr(attr.idSpan, "`not` takes one argument")
                        }
                    }
                    else -> returnErr(attr.idSpan, "`not` takes one argument")
                }
            }
            attr.id == Atom.from("any") -> {
                when (val a = attr.arg) {
                    is AttributeArg.Paren -> if (a.attrs.isNotEmpty()) {
                        for (inner in a.attrs) {
                            validateCfgArg(inner)
                        }
                    } else {
                        returnErr(attr.idSpan, "`any` takes at least one argument")
                    }
                    else -> returnErr(attr.idSpan, "`any` takes at least one argument")
                }
            }
            attr.id == Atom.from("all") -> {
                when (val a = attr.arg) {
                    is AttributeArg.Paren -> if (a.attrs.isNotEmpty()) {
                        for (inner in a.attrs) {
                            validateCfgArg(inner)
                        }
                    } else {
                        returnErr(attr.idSpan, "`all` takes at least one argument")
                    }
                    else -> returnErr(attr.idSpan, "`all` takes at least one argument")
                }
            }
            else -> returnErr(attr.idSpan, "unexpected `cfg` argument `${attr.id}`")
        }
    }
    when (val a = attr.arg) {
        is AttributeArg.Paren -> {
            val firstAttr = a.attrs.firstOrNull()
            if (firstAttr != null) {
                validateCfgArg(firstAttr)
            } else {
                returnErr(attr.idSpan, "`cfg` attributes take one argument")
            }
        }
        else -> returnErr(attr.idSpan, "`cfg` attributes take one argument")
    }
}
