// port-lint: source grammar/repr.rs
// Compiled representation of a grammar. Simplified, normalized
// version of `parseTree`. The normalization passes produce this
// representation incrementally.
package io.github.kotlinmania.lalrpop.grammar.repr

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.btree.BTreeMap
import io.github.kotlinmania.lalrpop.collections.map.map
import io.github.kotlinmania.lalrpop.grammar.freeVariables.freeVariables
import io.github.kotlinmania.lalrpop.grammar.pattern.Pattern
import io.github.kotlinmania.lalrpop.message.message.Content
// Kotlin callers import them directly from their defining package
// (grammar.parseTree).
import io.github.kotlinmania.lalrpop.grammar.parseTree.ArgPattern
import io.github.kotlinmania.lalrpop.grammar.parseTree.Attribute
import io.github.kotlinmania.lalrpop.grammar.parseTree.InternToken
import io.github.kotlinmania.lalrpop.grammar.parseTree.Lifetime
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.Path
import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalLiteral
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeBound
import io.github.kotlinmania.lalrpop.grammar.parseTree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.parseTree.Visibility
import io.github.kotlinmania.lalrpop.grammar.parseTree.display
import io.github.kotlinmania.lalrpop.grammar.parseTree.toContent
import io.github.kotlinmania.lalrpop.lr1.lookahead.Lookahead

data class Grammar(
    // a unique prefix that can be appended to identifiers to ensure
    // that they do not conflict with any action strings
    var prefix: String,

    // algorithm user requested for this parser
    var algorithm: Algorithm,

    // true if the grammar mentions the `!` terminal anywhere
    var usesErrorRecovery: Boolean,

    // these are the nonterminals that were declared to be public; the
    // key is the user name for the symbol, the value is the
    // artificial symbol we introduce, which will always have a single
    // production like `Foo' = Foo`.
    var startNonterminals: BTreeMap<NonterminalString, NonterminalString>,

    // the "import foo;" statements that the user declared
    var uses: MutableList<String>,

    // type parameters declared on the grammar, like `grammar<T>;`
    var typeParameters: MutableList<TypeParameter>,

    // actual parameters declared on the grammar, like the `x: u32` in `grammar(x: u32);`
    var parameters: MutableList<Parameter>,

    // where clauses declared on the grammar, like `grammar<T> where T: Sized`
    var whereClauses: MutableList<WhereClause>,

    // optional tokenizer Dfa; this is only needed if the user did not supply
    // an extern token declaration
    var internToken: InternToken?,

    // the grammar proper:
    var actionFnDefns: MutableList<ActionFnDefn>,
    var terminals: TerminalSet,
    var nonterminals: BTreeMap<NonterminalString, NonterminalData>,
    var conversions: BTreeMap<TerminalString, Pattern<TypeRepr>>,
    var types: Types,
    var moduleAttributes: MutableList<String>,
) {
    fun pattern(t: TerminalString): Pattern<TypeRepr> = conversions.getValue(t)

    fun productionsFor(nonterminal: NonterminalString): List<Production> =
        nonterminals[nonterminal]?.productions ?: emptyList()

    fun userParameterRefs(): String {
        val result = StringBuilder()
        for (parameter in parameters) {
            result.append("${parameter.name}, ")
        }
        return result.toString()
    }

    fun actionIsFallible(f: ActionFn): Boolean =
        actionFnDefns[f.index()].fallible

    fun nonLifetimeTypeParameters(): List<TypeParameter> =
        typeParameters.filter {
            when (it) {
                is TypeParameter.LifetimeTp -> false
                is TypeParameter.Id -> true
            }
        }
}

@Suppress("unused")
sealed class WhereClause : Comparable<WhereClause> {
    data class Forall(
        val binder: MutableList<TypeParameter>,
        val clause: WhereClause,
    ) : WhereClause()

    // `T: Foo`
    data class Bound(
        val subject: TypeRepr,
        val bound: TypeBound<TypeRepr>,
    ) : WhereClause()

    override fun compareTo(other: WhereClause): Int = toString().compareTo(other.toString())

    override fun toString(): String = when (this) {
        is Forall -> "for<${Sep(", ", binder)}> $clause"
        is Bound -> "$subject: ${bound.whereClauseDisplayPlaceholder()}"
    }

    // `TypeBound<T>::display()` is defined in parseTree.kt as an extension.
    private fun TypeBound<TypeRepr>.whereClauseDisplayPlaceholder(): String =
        this.display()
}

/**
 * For each terminal, we map it to a small integer from 0 to N.
 * This struct contains the mappings to go back and forth.
 */
data class TerminalSet(
    var all: MutableList<TerminalString>,
    var bits: BTreeMap<TerminalString, Int>,
)

data class NonterminalData(
    var visibility: Visibility,
    var span: Span,
    var attributes: MutableList<Attribute>,
    var productions: MutableList<Production>,
)

data class Algorithm(
    var lalr: Boolean,
    var codegen: LrCodeGeneration,
) {
    companion object {
        fun default(): Algorithm = Algorithm(
            lalr = false,
            codegen = LrCodeGeneration.TableDriven,
        )
    }
}

enum class LrCodeGeneration {
    TableDriven, RecursiveAscent, TestAll,
}

data class Parameter(
    var name: Atom,
    var ty: TypeRepr,
) {
    override fun toString(): String = "$name: $ty"
}

data class Production(
    // this overlaps with the key in the hashmap, obviously, but it
    // handy to have it
    var nonterminal: NonterminalString,
    var symbols: MutableList<Symbol>,
    var action: ActionFn,
    var span: Span,
) : Comparable<Production> {
    override fun compareTo(other: Production): Int {
        val c = nonterminal.compareTo(other.nonterminal)
        if (c != 0) return c
        val len = minOf(symbols.size, other.symbols.size)
        for (i in 0 until len) {
            val cc = symbols[i].compareTo(other.symbols[i])
            if (cc != 0) return cc
        }
        val cs = symbols.size.compareTo(other.symbols.size)
        if (cs != 0) return cs
        val ca = action.compareTo(other.action)
        if (ca != 0) return ca
        return span.compareTo(other.span)
    }

    override fun toString(): String =
        "$nonterminal = ${Sep(", ", symbols)} => $action;"
}

sealed class Symbol : Comparable<Symbol> {
    // Per-subclass toString — see TypeRepr / Visibility for the full
    // explanation of why a parent-level override gets silently shadowed
    // by `data class`-generated toString. Mirrors the upstream
    // `Display for Symbol`.
    data class Nonterminal(val nt: NonterminalString) : Symbol() {
        override fun toString(): String = "$nt"
    }

    data class Terminal(val term: TerminalString) : Symbol() {
        override fun toString(): String = "$term"
    }

    override fun compareTo(other: Symbol): Int {
        // Mirror the upstream `(derive(Ord))` on the upstream enum:
        // sorts by variant declaration order first (Nonterminal < Terminal),
        // then by the variant inner value. Toing-string-comparison gave
        // the wrong order in BTreeMap iteration over `state.shifts`,
        // which flipped state-construction order in the LR algorithm
        // and produced different state numbering than upstream.
        val ao = ordinal()
        val bo = other.ordinal()
        if (ao != bo) return ao - bo
        return when (this) {
            is Nonterminal -> nt.compareTo((other as Nonterminal).nt)
            is Terminal -> term.compareTo((other as Terminal).term)
        }
    }

    private fun ordinal(): Int = when (this) {
        is Nonterminal -> 0
        is Terminal -> 1
    }

    fun isTerminal(): Boolean = when (this) {
        is Terminal -> true
        is Nonterminal -> false
    }

    fun ty(t: Types): TypeRepr = when (this) {
        is Terminal -> t.terminalType(term)
        is Nonterminal -> t.nonterminalType(nt)
    }
}

fun Symbol.toContent(): Content = when (this) {
    is Symbol.Nonterminal -> nt.toContent()
    is Symbol.Terminal -> term.toContent()
}

data class ActionFnDefn(
    var fallible: Boolean,
    var retType: TypeRepr,
    var kind: ActionFnDefnKind,
) {
    fun toFnString(name: String): String = when (val k = kind) {
        is ActionFnDefnKind.User -> k.data.toFnString(this, name)
        is ActionFnDefnKind.Inline -> k.data.toFnString(name)
        is ActionFnDefnKind.Lookaround -> k.data.toString()
    }

    override fun toString(): String = toFnString("_")
}

sealed class ActionFnDefnKind {
    data class User(val data: UserActionFnDefn) : ActionFnDefnKind()
    data class Inline(val data: InlineActionFnDefn) : ActionFnDefnKind()
    data class Lookaround(val data: LookaroundActionFnDefn) : ActionFnDefnKind()
}

/** An action function written by a user. */
data class UserActionFnDefn(
    var argPatterns: MutableList<ArgPattern>,
    var argTypes: MutableList<TypeRepr>,
    var code: String,
) {
    fun toFnString(defn: ActionFnDefn, name: String): String {
        val argStrings: List<String> = argPatterns.zip(argTypes).map { (n, ty) -> "$n: $ty" }
        return "fn $name(${Sep(", ", argStrings)}) -> ${defn.retType} { $code }"
    }
}

/**
 * An action function generated by the inlining pass.  If we were
 * inlining `A = B C D` (with action 44) into `X = Y A Z` (with
 * action 22), this would look something like:
 *
 * ```noCompile
 * function __action66(__0: Y, __1: B, __2: C, __3: D, __4: Z) {
 *     __action22(__0, __action44(__1, __2, __3), __4)
 * }
 * ```
 */
data class InlineActionFnDefn(
    /** in the example above, this would be `action22` */
    var action: ActionFn,

    /** in the example above, this would be `Y, {action44: B, C, D}, Z` */
    var symbols: MutableList<InlinedSymbol>,
) {
    fun toFnString(name: String): String {
        val argStrings: List<String> = symbols.map { inlineSym ->
            when (inlineSym) {
                is InlinedSymbol.Original -> "${inlineSym.sym}"
                is InlinedSymbol.Inlined -> "${inlineSym.action}(${Sep(", ", inlineSym.syms)})"
            }
        }
        return "fn $name(..) { $action(${Sep(", ", argStrings)}) }"
    }
}

sealed class LookaroundActionFnDefn {
    data object Lookahead : LookaroundActionFnDefn()
    data object Lookbehind : LookaroundActionFnDefn()
}

sealed class InlinedSymbol {
    data class Original(val sym: Symbol) : InlinedSymbol()
    data class Inlined(val action: ActionFn, val syms: MutableList<Symbol>) : InlinedSymbol()
}

sealed class TypeRepr : Comparable<TypeRepr> {
    /**
     * Mirrors `implementation Display for TypeRepr` AND `implementation Debug for TypeRepr`
     * (the Debug implementation defers to Display). Each subclass overrides
     * `toString()` explicitly: a `data class` auto-generates its own
     * toString which shadows any override placed on the sealed parent,
     * so a single override on `TypeRepr` was being silently bypassed
     * and the emitted Rust source contained
     * `Ref(lifetime='input, mutable=false, referent=Nominal(data=str))`
     * instead of `&'input str`.
     */
    abstract override fun toString(): String

    data class Tuple(val types: MutableList<TypeRepr>) : TypeRepr() {
        override fun toString(): String = "(${Sep(", ", types)})"
    }

    data class Slice(val ty: TypeRepr) : TypeRepr() {
        override fun toString(): String = "[$ty]"
    }

    data class Nominal(val data: NominalTypeRepr) : TypeRepr() {
        override fun toString(): String = "$data"
    }

    data class Associated(val typeParameter: Atom, val id: Atom) : TypeRepr() {
        override fun toString(): String = "$typeParameter::$id"
    }

    data class LifetimeRepr(val lifetime: Lifetime) : TypeRepr() {
        override fun toString(): String = "$lifetime"
    }

    data class Ref(val lifetime: Lifetime?, val mutable: Boolean, val referent: TypeRepr) : TypeRepr() {
        override fun toString(): String = when {
            lifetime == null && !mutable -> "&$referent"
            lifetime != null && !mutable -> "&$lifetime $referent"
            lifetime == null && mutable -> "&mut $referent"
            else -> "&$lifetime mut $referent"
        }
    }

    data class TraitObject(val data: NominalTypeRepr) : TypeRepr() {
        override fun toString(): String = "dyn $data"
    }

    data class Fn(
        val forall: MutableList<TypeParameter>,
        val path: Path,
        val parameters: MutableList<TypeRepr>,
        val ret: TypeRepr?,
    ) : TypeRepr() {
        override fun toString(): String = buildString {
            append("dyn ")
            if (forall.isNotEmpty()) {
                append("for<${Sep(", ", forall)}> ")
            }
            append("$path(${Sep(", ", parameters)})")
            if (ret != null) append(" -> $ret")
        }
    }

    override fun compareTo(other: TypeRepr): Int {
        // Mirror the upstream `(derive(Ord))` on `enum TypeRepr`: sort
        // by variant declaration order first, then by inner contents.
        // toString-based compare gives a different order (because
        // `&'input str` < `Renamed` in lexicographic ASCII), which
        // flips `__pop_Variant0` / `__pop_Variant1` ordering in the
        // emitted parse table.
        val ao = ordinal()
        val bo = other.ordinal()
        if (ao != bo) return ao - bo
        // Within-variant fallback to toString — the variants we ship
        // (NominalTypeRepr, Path) all have stable Comparable
        // implementations driven by their toString, so this matches
        // upstream recursive Ord derive for the cases LALRPOP
        // exercises.
        return toString().compareTo(other.toString())
    }

    private fun ordinal(): Int = when (this) {
        is Tuple -> 0
        is Slice -> 1
        is Nominal -> 2
        is Associated -> 3
        is LifetimeRepr -> 4
        is Ref -> 5
        is TraitObject -> 6
        is Fn -> 7
    }

    fun isUnit(): Boolean = when (this) {
        is Tuple -> types.isEmpty()
        else -> false
    }

    fun bottomUp(op: (TypeRepr) -> TypeRepr): TypeRepr {
        val result: TypeRepr = when (this) {
            is Tuple -> Tuple(types.map { it.bottomUp(op) }.toMutableList())
            is Slice -> Slice(ty.bottomUp(op))
            is Nominal -> Nominal(NominalTypeRepr(
                path = data.path,
                types = data.types.map { it.bottomUp(op) }.toMutableList(),
            ))
            is Associated -> Associated(typeParameter, id)
            is LifetimeRepr -> LifetimeRepr(lifetime)
            is Ref -> Ref(
                lifetime = lifetime,
                mutable = mutable,
                referent = referent.bottomUp(op),
            )
            is TraitObject -> TraitObject(NominalTypeRepr(
                path = data.path,
                types = data.types.map { it.bottomUp(op) }.toMutableList(),
            ))
            is Fn -> Fn(
                forall = forall.toMutableList(),
                path = path,
                parameters = parameters.map { it.bottomUp(op) }.toMutableList(),
                ret = ret?.bottomUp(op),
            )
        }
        return op(result)
    }

    /**
     * Finds anonymous lifetimes (e.g., `&u32` or `Foo<'_>`) and
     * instantiates them with a name like `__1`. Also computes
     * obvious outlives relationships that are needed (e.g., `&'a T`
     * requires `T: 'a`). The parameters `typeParameters` and
     * `whereClauses` should contain -- on entry -- the
     * type-parameters and where-clauses that currently exist on the
     * grammar. On exit, they will have been modified to include the
     * new type parameters and any implied where clauses.
     */
    fun nameAnonymousLifetimesAndComputeImpliedOutlives(
        prefix: String,
        typeParameters: MutableList<TypeParameter>,
        whereClauses: MutableList<WhereClause>,
    ): TypeRepr {
        fun freshLifetimeName(tps: MutableList<TypeParameter>): Lifetime {
            // Make a name like `__1`:
            val len = tps.size
            val name = Lifetime(Atom.from("'$prefix$len"))
            tps.add(TypeParameter.LifetimeTp(name))
            return name
        }

        return bottomUp { t ->
            when (t) {
                is Tuple, is Slice, is Nominal, is Associated, is TraitObject, is Fn -> t
                is LifetimeRepr -> {
                    if (t.lifetime.isAnonymous()) {
                        LifetimeRepr(freshLifetimeName(typeParameters))
                    } else {
                        t
                    }
                }
                is Ref -> {
                    var lifetime = t.lifetime
                    if (lifetime == null) {
                        lifetime = freshLifetimeName(typeParameters)
                    }

                    // If we have `&'a T`, then we have to compute each
                    // free variable `X` in `T` and ensure that `X: 'a`:
                    val l = lifetime
                    for (tp in t.referent.freeVariables(typeParameters)) {
                        val wc = WhereClause.Bound(
                            subject = fromParameter(tp),
                            bound = TypeBound.LifetimeBound(l),
                        )
                        if (!whereClauses.contains(wc)) {
                            whereClauses.add(wc)
                        }
                    }

                    Ref(
                        lifetime = lifetime,
                        mutable = t.mutable,
                        referent = t.referent,
                    )
                }
            }
        }
    }

    companion object {
        fun fromParameter(tp: TypeParameter): TypeRepr = when (tp) {
            is TypeParameter.LifetimeTp -> LifetimeRepr(tp.lifetime)
            is TypeParameter.Id -> Nominal(NominalTypeRepr(
                path = Path.fromId(tp.atom),
                types = mutableListOf(),
            ))
        }

        fun usize(): TypeRepr = Nominal(NominalTypeRepr(
            path = Path.usize(),
            types = mutableListOf(),
        ))

        fun str(): TypeRepr = Nominal(NominalTypeRepr(
            path = Path.str(),
            types = mutableListOf(),
        ))
    }
}

data class NominalTypeRepr(
    var path: Path,
    var types: MutableList<TypeRepr>,
) : Comparable<NominalTypeRepr> {
    override fun compareTo(other: NominalTypeRepr): Int = toString().compareTo(other.toString())

    override fun toString(): String = if (types.isEmpty()) {
        "$path"
    } else {
        "$path<${Sep(", ", types)}>"
    }
}

class Types(
    prefix: String,
    private var terminalLocType: TypeRepr?,
    private var errorType: TypeRepr?,
    private var terminalTokenType: TypeRepr,
) {
    private var terminalTypes: BTreeMap<TerminalString, TypeRepr> = map()
    private var nonterminalTypes: BTreeMap<NonterminalString, TypeRepr> = map()
    // the following two will be overwritten later
    private var parseErrorType: TypeRepr = TypeRepr.Tuple(mutableListOf())
    private var errorRecoveryType: TypeRepr = TypeRepr.Tuple(mutableListOf())

    init {
        val args = mutableListOf(
            terminalLocType(),
            terminalTokenType,
            errorType(),
        )
        parseErrorType = TypeRepr.Nominal(NominalTypeRepr(
            path = Path(
                absolute = false,
                ids = mutableListOf(
                    Atom.from("${prefix}lalrpop_util"),
                    Atom.from("ParseError"),
                ),
            ),
            types = args.toMutableList(),
        ))
        errorRecoveryType = TypeRepr.Nominal(NominalTypeRepr(
            path = Path(
                absolute = false,
                ids = mutableListOf(
                    Atom.from("${prefix}lalrpop_util"),
                    Atom.from("ErrorRecovery"),
                ),
            ),
            types = args,
        ))
        terminalTypes[TerminalString.Error] = errorRecoveryType
    }

    fun addType(ntId: NonterminalString, ty: TypeRepr) {
        check(nonterminalTypes.put(ntId, ty) == null)
    }

    fun addTermType(term: TerminalString, ty: TypeRepr) {
        check(terminalTypes.put(term, ty) == null)
    }

    fun terminalTokenType(): TypeRepr = terminalTokenType

    fun optTerminalLocType(): TypeRepr? = terminalLocType

    fun terminalLocType(): TypeRepr =
        terminalLocType ?: TypeRepr.Tuple(mutableListOf())

    fun errorType(): TypeRepr = errorType ?: TypeRepr.Ref(
        lifetime = Lifetime.statik(),
        mutable = false,
        referent = TypeRepr.str(),
    )

    fun terminalType(id: TerminalString): TypeRepr =
        terminalTypes[id] ?: terminalTokenType

    fun terminalTypes(): List<TypeRepr> = terminalTypes.values.toList()

    fun lookupNonterminalType(id: NonterminalString): TypeRepr? = nonterminalTypes[id]

    fun nonterminalType(id: NonterminalString): TypeRepr = nonterminalTypes.getValue(id)

    fun nonterminalTypes(): List<TypeRepr> = nonterminalTypes.values.toList()

    fun parseErrorType(): TypeRepr = parseErrorType

    fun errorRecoveryType(): TypeRepr = errorRecoveryType

    /**
     * Returns a type `(L, T, L)` where L is the location type and T
     * is the token type.
     */
    fun tripleType(): TypeRepr = spannedType(terminalTokenType)

    /**
     * Returns a type `(L, T, L)` where L is the location type and T
     * is the argument.
     */
    fun spannedType(ty: TypeRepr): TypeRepr {
        val locationType = terminalLocType()
        return TypeRepr.Tuple(mutableListOf(locationType, ty, locationType))
    }
}

data class ActionFn(private val id: Int) : Comparable<ActionFn> {
    fun index(): Int = id

    override fun compareTo(other: ActionFn): Int = id.compareTo(other.id)

    override fun toString(): String = "ActionFn($id)"

    companion object {
        fun new(x: Int): ActionFn = ActionFn(x)
    }
}
