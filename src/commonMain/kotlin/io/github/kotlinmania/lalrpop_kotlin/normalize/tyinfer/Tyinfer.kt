// port-lint: source src/normalize/tyinfer/mod.rs
package io.github.kotlinmania.lalrpop_kotlin.normalize.tyinfer

import io.github.kotlinmania.lalrpop_kotlin.Atom
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.ActionKind
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Alternative
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.ArgPattern
import io.github.kotlinmania.lalrpop_kotlin.grammar.consts.ERROR
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Grammar
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.GrammarItem
import io.github.kotlinmania.lalrpop_kotlin.grammar.consts.LOCATION
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Lifetime
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.MatchMapping
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.NonterminalData
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Path
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Symbol
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.SymbolKind
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Tuple
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TypeParameter
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TypeRef
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.asNonterminal
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.enumToken
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.externToken
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.internToken
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.NominalTypeRepr
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.Types
import io.github.kotlinmania.lalrpop_kotlin.normalize.normUtil.AlternativeAction
import io.github.kotlinmania.lalrpop_kotlin.normalize.NormErrorException
import io.github.kotlinmania.lalrpop_kotlin.normalize.normUtil.Symbols
import io.github.kotlinmania.lalrpop_kotlin.normalize.normUtil.analyzeAction
import io.github.kotlinmania.lalrpop_kotlin.normalize.returnErr
import io.github.kotlinmania.lalrpop_kotlin.lr1.lookahead.Token

fun inferTypes(grammar: Grammar): Types {
    val inferencer = TypeInferencer.new(grammar)
    return inferencer.inferTypes()
}

private class TypeInferencer(
    val stack: MutableList<NonterminalString>,
    val nonterminals: MutableMap<NonterminalString, Nt>,
    val types: Types,
    val typeParameters: MutableSet<Atom>,
) {
    companion object {
        fun new(grammar: Grammar): TypeInferencer {
            val types = makeTypes(grammar)

            val nonterminals: MutableMap<NonterminalString, Nt> = grammar
                .items
                .mapNotNull { it.asNonterminal() }
                .map { data ->
                    check(!data.isMacroDef()) // normalized away by now
                    data.name to Nt.new(data)
                }
                .toMap()
                .toMutableMap()

            val typeParameters: MutableSet<Atom> = grammar
                .typeParameters
                .mapNotNull { p ->
                    when (p) {
                        is TypeParameter.LifetimeTp -> null
                        is TypeParameter.Id -> p.atom
                    }
                }
                .toMutableSet()

            return TypeInferencer(
                stack = mutableListOf(),
                nonterminals = nonterminals,
                types = types,
                typeParameters = typeParameters,
            )
        }
    }
}

private data class Nt(
    val span: Span,
    val typeDecl: TypeRef?,
    val alternatives: List<Alternative>,
) {
    companion object {
        fun new(data: NonterminalData): Nt = Nt(
            span = data.span,
            typeDecl = data.typeDecl,
            alternatives = data.alternatives,
        )
    }
}

private fun makeTypes(grammar: Grammar): Types {
    val optExternToken = grammar.externToken()

    // Determine error type (if any).
    val errorType: TypeRepr? = optExternToken?.associatedType(Atom.from(ERROR))
        ?.typeRef?.typeRepr()

    // Determine location type and enum type. If using an internal
    // token, that's specified by us, not user.
    val internToken = grammar.internToken()
    return if (internToken != null) {
        val locType = // usize
            TypeRepr.usize()
        val inputStr = // &'input str
            TypeRepr.Ref(
                lifetime = Lifetime.input(),
                mutable = false,
                referent = TypeRepr.str(),
            )
        val enumType = // Token<'input>
            TypeRepr.Nominal(NominalTypeRepr(
                path = Path(
                    absolute = false,
                    ids = mutableListOf(Atom.from("Token")),
                ),
                types = mutableListOf(TypeRepr.LifetimeRepr(Lifetime.input())),
            ))

        val types = Types(grammar.prefix, locType, errorType, enumType)

        for (matchEntry in internToken.matchEntries) {
            val userName = matchEntry.userName
            if (userName is MatchMapping.Terminal) {
                types.addTermType(userName.terminal, inputStr)
            }
        }

        types
    } else {
        val externToken = optExternToken!!
        val locType = externToken
            .associatedType(Atom.from(LOCATION))
            ?.typeRef?.typeRepr()
        val enumType = externToken
            .enumToken!!
            .typeName
            .typeRepr()
        val types = Types(grammar.prefix, locType, errorType, enumType)

        // For each defined conversion, figure out the type of the
        // terminal and enter it into `types` by hand if it is not the
        // default. For terminals with custom types, the user should
        // have one or more bindings in the pattern -- if more than
        // one, make a tuple.
        //
        // e.g. "(" => Lparen(..) ==> no custom type
        //      "Num" => Num(<u32>) ==> custom type is u32
        //      "Fraction" => Real(<u32>,<u32>) ==> custom type is (u32, u32)
        for (conversion in listOfNotNull(grammar.enumToken()).flatMap { et -> et.conversions }) {
            val tys: MutableList<TypeRepr> = mutableListOf()
            conversion.to.forEachBinding { ty -> tys.add(ty.typeRepr()) }
            if (tys.isEmpty()) {
                continue
            }
            val ty = maybeTuple(tys)
            types.addTermType(conversion.from, ty)
        }

        types
    }
}

private fun TypeInferencer.inferTypes(): Types {
    val ids: List<NonterminalString> = this.nonterminals.keys.toList()

    for (id in ids) {
        this.nonterminalType(id)
        check(this.types.lookupNonterminalType(id) != null)
    }

    return this.types
}

private fun TypeInferencer.nonterminalType(id: NonterminalString): TypeRepr {
    this.types.lookupNonterminalType(id)?.let { return it }

    val nt = this.nonterminals.getValue(id)
    if (this.stack.contains(id)) {
        returnErr(
            nt.span,
            "cannot infer type of `$id` because it references itself",
        )
    }

    val ty = this.push(id) { self ->
        if (nt.typeDecl != null) {
            return@push self.typeRef(nt.typeDecl)
        }

        // Try to compute the types of all alternatives; note that
        // some may result in an error. Don't report these errors
        // (yet).
        val alternativeTypes: MutableList<TypeRepr> = mutableListOf()
        val alternativeErrors: MutableList<NormErrorException> = mutableListOf()
        for (alt in nt.alternatives) {
            try {
                alternativeTypes.add(self.alternativeType(alt))
            } catch (e: NormErrorException) {
                alternativeErrors.add(e)
            }
        }

        // if it never succeeded, report first error
        if (alternativeTypes.isEmpty()) {
            val firstErr = alternativeErrors.firstOrNull()
            if (firstErr != null) {
                throw firstErr
            } else {
                // if nothing succeeded, and nothing errored,
                // must have been nothing to start with
                returnErr(
                    nt.span,
                    "nonterminal `$id` has no alternatives and hence parse cannot succeed",
                )
            }
        }

        // otherwise, check that all the cases where we had success agree
        val tail = alternativeTypes.subList(1, alternativeTypes.size)
        val altTail = nt.alternatives.subList(1, nt.alternatives.size)
        for ((i, pair) in tail.zip(altTail).withIndex()) {
            val (t, alt) = pair
            if (alternativeTypes[0] != t) {
                returnErr(
                    alt.span,
                    "type of alternative #${i + 1 + 1} is `$t`, " +
                        "but type of first alternative is `${alternativeTypes[0]}`",
                )
            }
        }

        // and use that type
        alternativeTypes.removeAt(alternativeTypes.size - 1)
    }

    for (alt in nt.alternatives) {
        val symbols = alt.expr.symbols
        for ((t, s) in symbols.mapNotNull { it.asTuple() }) {
            val ty2 = when (val k = s.kind) {
                is SymbolKind.Nonterminal -> this.nonterminalType(k.nt)
                else -> returnErr(
                    s.span,
                    "expected a nonterminal in tuple, but found `${s.kind}`",
                )
            }

            validateTuple(s.span, t, ty2)
        }
    }

    this.types.addType(id, ty)
    return ty
}

private inline fun <R> TypeInferencer.push(id: NonterminalString, f: (TypeInferencer) -> R): R {
    this.stack.add(id)
    val r = f(this)
    check(this.stack.removeAt(this.stack.size - 1) == id)
    return r
}

private fun TypeInferencer.typeRef(typeRef: TypeRef): TypeRepr {
    return when (typeRef) {
        is TypeRef.Tuple -> {
            val types = typeRef.types
                .map { t -> this.typeRef(t) }
                .toMutableList()
            TypeRepr.Tuple(types)
        }
        is TypeRef.Slice -> TypeRepr.Slice(this.typeRef(typeRef.ty))
        is TypeRef.Nominal -> {
            if (typeRef.path.ids.size == 2 && this.typeParameters.contains(typeRef.path.ids[0])) {
                return TypeRepr.Associated(
                    typeParameter = typeRef.path.ids[0],
                    id = typeRef.path.ids[1],
                )
            }

            val types = typeRef.types
                .map { t -> this.typeRef(t) }
                .toMutableList()
            TypeRepr.Nominal(NominalTypeRepr(
                path = typeRef.path,
                types = types,
            ))
        }
        is TypeRef.LifetimeRef -> TypeRepr.LifetimeRepr(typeRef.lifetime)
        is TypeRef.Id -> TypeRepr.Nominal(NominalTypeRepr(
            path = Path.fromId(typeRef.atom),
            types = mutableListOf(),
        ))
        is TypeRef.Ref -> TypeRepr.Ref(
            lifetime = typeRef.lifetime,
            mutable = typeRef.mutable,
            referent = this.typeRef(typeRef.referent),
        )
        is TypeRef.OfSymbol -> this.symbolType(typeRef.kind)
        is TypeRef.TraitObject -> {
            val types = typeRef.types
                .map { t -> this.typeRef(t) }
                .toMutableList()
            TypeRepr.TraitObject(NominalTypeRepr(
                path = typeRef.path,
                types = types,
            ))
        }
        is TypeRef.Fn -> TypeRepr.Fn(
            forall = typeRef.forall.toMutableList(),
            path = typeRef.path,
            parameters = typeRef.parameters
                .map { t -> this.typeRef(t) }
                .toMutableList(),
            ret = typeRef.ret?.let { this.typeRef(it) },
        )
    }
}

private fun TypeInferencer.alternativeType(alt: Alternative): TypeRepr {
    return when (val action = analyzeAction(alt)) {
        is AlternativeAction.User -> when (action.code) {
            is ActionKind.User, is ActionKind.Fallible -> {
                returnErr(
                    alt.span,
                    "cannot infer types if there is custom action code",
                )
            }
            is ActionKind.Lookahead, is ActionKind.Lookbehind -> {
                this.types.optTerminalLocType()!!
            }
        }

        is AlternativeAction.Default -> when (val syms = action.symbols) {
            is Symbols.Named -> {
                returnErr(
                    alt.span,
                    "cannot infer types in the presence of named symbols like " +
                        "`${syms.list[0].second}:${syms.list[0].third}`",
                )
            }

            is Symbols.Anon -> {
                val symbolTypes: MutableList<TypeRepr> = syms.list
                    .map { (_, sym) -> this.symbolType(sym.kind) }
                    .toMutableList()
                maybeTuple(symbolTypes)
            }
        }
    }
}

private fun TypeInferencer.symbolType(symbol: SymbolKind): TypeRepr {
    return when (symbol) {
        is SymbolKind.Terminal -> this.types.terminalType(symbol.terminal)
        is SymbolKind.Nonterminal -> this.nonterminalType(symbol.nt)
        is SymbolKind.Choose -> this.symbolType(symbol.sym.kind)
        is SymbolKind.Name -> this.symbolType(symbol.sym.kind)
        is SymbolKind.TupleKind -> this.symbolType(symbol.sym.kind)
        SymbolKind.Error -> this.types.errorRecoveryType()

        is SymbolKind.Repeat,
        is SymbolKind.Expr,
        is SymbolKind.Macro,
        is SymbolKind.AmbiguousId,
        SymbolKind.Lookahead,
        SymbolKind.Lookbehind -> {
            error("symbol `$symbol` should have been expanded away")
        }
    }
}

private fun maybeTuple(v: MutableList<TypeRepr>): TypeRepr {
    return if (v.size == 1) {
        v.first()
    } else {
        TypeRepr.Tuple(v)
    }
}

private fun validateTuple(span: Span, tuple: Tuple, nt: TypeRepr) {
    when (nt) {
        is TypeRepr.Tuple -> {
            val items = nt.types
            if (items.size != tuple.tuples.size) {
                returnErr(
                    span,
                    "expected a tuple of length ${items.size}, " +
                        "but found a tuple of length ${tuple.tuples.size}",
                )
            }

            for ((item, tupleItem) in items.zip(tuple.tuples)) {
                if (tupleItem is ArgPattern.TuplePat) {
                    validateTuple(span, tupleItem.tuple, item)
                }
            }
        }
        else -> error("expected a tuple type, but found `$nt`")
    }
}
