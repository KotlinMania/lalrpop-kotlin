// port-lint: source src/grammar/parse_tree.rs
// The "parse-tree" is what is produced by the parser. We use it do
// some pre-expansion and so forth before creating the proper AST.
package io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree

import io.github.kotlinmania.lalrpop_kotlin.Atom
import io.github.kotlinmania.lalrpop_kotlin.Sep
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.NominalTypeRepr
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.Algorithm
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.LrCodeGeneration
import io.github.kotlinmania.lalrpop_kotlin.lexer.dfa.Dfa
import io.github.kotlinmania.lalrpop_kotlin.message.message.Content
import io.github.kotlinmania.lalrpop_kotlin.message.builder.InlineBuilder
import io.github.kotlinmania.lalrpop_kotlin.tls.Tls
import io.github.kotlinmania.lalrpop_kotlin.collections.set.Set
import io.github.kotlinmania.lalrpop_kotlin.lr1.lookahead.Lookahead
import io.github.kotlinmania.lalrpop_kotlin.grammar.pattern.Pattern
import io.github.kotlinmania.lalrpop_kotlin.grammar.consts.INPUT_LIFETIME
import io.github.kotlinmania.lalrpop_kotlin.grammar.consts.LALR
import io.github.kotlinmania.lalrpop_kotlin.grammar.consts.TABLE_DRIVEN
import io.github.kotlinmania.lalrpop_kotlin.grammar.consts.RECURSIVE_ASCENT
import io.github.kotlinmania.lalrpop_kotlin.grammar.consts.TEST_ALL

data class Grammar(
    // see field `prefix` in `grammar::repr::Grammar`
    var prefix: String,
    var span: Span,
    var typeParameters: MutableList<TypeParameter>,
    var parameters: MutableList<Parameter>,
    var whereClauses: MutableList<WhereClause<TypeRef>>,
    var items: MutableList<GrammarItem>,
    var attributes: MutableList<Attribute>,
    var moduleAttributes: MutableList<String>,
)

data class Span(val start: Int, val end: Int) : Comparable<Span> {
    override fun compareTo(other: Span): Int {
        val c = start.compareTo(other.start)
        return if (c != 0) c else end.compareTo(other.end)
    }
    companion object {
        val DEFAULT = Span(0, 0)
    }
}

fun Span.toContent(): Content {
    val fileText = Tls.fileText()
    val string = fileText.spanStr(this)
    // Insert an Adjacent block to prevent wrapping inside this string:
    return InlineBuilder.new()
        .beginAdjacent()
        .text(string)
        .end()
        .end()
}

sealed class GrammarItem {
    data class MatchToken(val inner: io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.MatchToken) : GrammarItem()
    data class ExternToken(val inner: io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.ExternToken) : GrammarItem()
    data class InternToken(val inner: io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.InternToken) : GrammarItem()
    data class Nonterminal(val data: NonterminalData) : GrammarItem()
    data class Use(val code: String) : GrammarItem()
}

data class MatchToken(
    var contents: MutableList<MatchContents>,
    var span: Span,
) {
    companion object {
        fun new(contents: MatchContents, span: Span): MatchToken =
            MatchToken(contents = mutableListOf(contents), span = span)
    }

    // Not really sure if this is the best way to do it
    fun add(contents: MatchContents): MatchToken {
        val newContents = this.contents.toMutableList()
        newContents.add(contents)
        return MatchToken(contents = newContents, span = span)
    }
}

data class MatchContents(
    var items: MutableList<MatchItem>,
)

// FIXME: Validate that MatchSymbol is actually a TerminalString::Literal
//          and that MatchMapping is an Id or String
sealed class MatchItem {
    data class CatchAll(val span: Span) : MatchItem()
    data class Unmapped(val symbol: MatchSymbol, val span: Span) : MatchItem()
    data class Mapped(val symbol: MatchSymbol, val mapping: MatchMapping, val span: Span) : MatchItem()

    fun span(): Span = when (this) {
        is CatchAll -> span
        is Unmapped -> span
        is Mapped -> span
    }
}

typealias MatchSymbol = TerminalLiteral

sealed class MatchMapping : Comparable<MatchMapping> {
    data class Terminal(val terminal: TerminalString) : MatchMapping() {
        override fun toString(): String = terminal.toString()
    }
    data object Skip : MatchMapping() {
        override fun toString(): String = "{ }"
    }

    override fun compareTo(other: MatchMapping): Int = toString().compareTo(other.toString())

    // Debug impl
    fun debugString(): String = when (this) {
        is Terminal -> "${terminal}"
        Skip -> "{ }"
    }

    override fun toString(): String = when (this) {
        is Terminal -> terminal.toString()
        Skip -> "{ }"
    }
}

/**
 * Intern tokens are not typed by the user: they are synthesized in
 * the absence of an "extern" declaration with information about the
 * string literals etc that appear in the grammar.
 */
data class InternToken(
    /**
     * Set of `r"foo"` and `"foo"` literals extracted from the
     * grammar. Sorted by order of increasing precedence.
     */
    var matchEntries: MutableList<MatchEntry>,
    var dfa: Dfa,
)

/**
 * In `token_check`, as we prepare to generate a tokenizer, we
 * combine any `match` declaration the user may have given with the
 * set of literals (e.g. `"foo"` or `r"[a-z]"`) that appear elsewhere
 * in their in the grammar to produce a series of `MatchEntry`. Each
 * `MatchEntry` roughly corresponds to one line in a `match` declaration.
 *
 * So e.g. if you had
 *
 * ```lalrpop
 * match {
 *    r"(?i)BEGIN" => "BEGIN",
 *    "+" => "+",
 * } else {
 *    _
 * }
 *
 * ID = r"[a-zA-Z]+"
 * ```
 *
 * This would correspond to three match entries:
 * - `MatchEntry { match_literal: r"(?i)BEGIN", user_name: "BEGIN", precedence: 2 }`
 * - `MatchEntry { match_literal: "+", user_name: "+", precedence: 3 }`
 * - `MatchEntry { match_literal: "r[a-zA-Z]+"", user_name: r"[a-zA-Z]+", precedence: 0 }`
 *
 * A couple of things to note:
 *
 * - Literals appearing in the grammar are converting into an "identity" mapping
 * - Each match group G is combined with the implicit priority IP of 1 for literals and 0 for
 *   regex to yield the final precedence; the formula is `G*2 + IP`.
 */
data class MatchEntry(
    /**
     * The precedence of this match entry.
     *
     * NB: This field must go first, so that `PartialOrd` sorts by precedence first!
     */
    var precedence: Int,
    var matchLiteral: TerminalLiteral,
    var userName: MatchMapping,
) : Comparable<MatchEntry> {
    override fun compareTo(other: MatchEntry): Int {
        val c = precedence.compareTo(other.precedence)
        if (c != 0) return c
        val cc = matchLiteral.compareTo(other.matchLiteral)
        if (cc != 0) return cc
        return userName.compareTo(other.userName)
    }
}

data class ExternToken(
    var span: Span,
    var associatedTypes: MutableList<AssociatedType>,
    var enumToken: EnumToken?,
) {
    fun associatedType(name: Atom): AssociatedType? =
        associatedTypes.firstOrNull { it.typeName == name }
}

data class AssociatedType(
    var typeSpan: Span,
    var typeName: Atom,
    var typeRef: TypeRef,
)

data class EnumToken(
    var typeName: TypeRef,
    var typeSpan: Span,
    var conversions: MutableList<Conversion>,
)

data class Conversion(
    var span: Span,
    var attributes: MutableList<Attribute>,
    var from: TerminalString,
    var to: Pattern<TypeRef>,
)

data class Path(
    var absolute: Boolean,
    var ids: MutableList<Atom>,
) : Comparable<Path> {
    override fun compareTo(other: Path): Int {
        val c = absolute.compareTo(other.absolute)
        if (c != 0) return c
        val len = minOf(ids.size, other.ids.size)
        for (i in 0 until len) {
            val cc = ids[i].compareTo(other.ids[i])
            if (cc != 0) return cc
        }
        return ids.size.compareTo(other.ids.size)
    }

    override fun toString(): String =
        (if (absolute) "::" else "") + Sep("::", ids).toString()

    fun asId(): Atom? =
        if (!absolute && ids.size == 1) ids[0] else null

    companion object {
        fun fromId(id: Atom): Path = Path(absolute = false, ids = mutableListOf(id))
        fun usize(): Path = Path(absolute = false, ids = mutableListOf(Atom.from("usize")))
        fun str(): Path = Path(absolute = false, ids = mutableListOf(Atom.from("str")))
        fun vec(): Path = Path(
            absolute = false,
            ids = mutableListOf(Atom.from("alloc"), Atom.from("vec"), Atom.from("Vec")),
        )
        fun option(): Path = Path(absolute = false, ids = mutableListOf(Atom.from("Option")))
    }
}

sealed class TypeRef {
    // (T1, T2)
    data class Tuple(val types: MutableList<TypeRef>) : TypeRef()

    // [T]
    data class Slice(val ty: TypeRef) : TypeRef()

    // Foo<'a, 'b, T1, T2>, Foo::Bar, etc
    data class Nominal(val path: Path, val types: MutableList<TypeRef>) : TypeRef()

    data class Ref(val lifetime: Lifetime?, val mutable: Boolean, val referent: TypeRef) : TypeRef()

    // `dyn Trait`
    data class TraitObject(val path: Path, val types: MutableList<TypeRef>) : TypeRef()

    // 'x ==> only should appear within nominal types, but what do we care
    data class LifetimeRef(val lifetime: Lifetime) : TypeRef()

    // Foo or Bar ==> treated specially since macros may care
    data class Id(val atom: Atom) : TypeRef()

    // <N> ==> type of a nonterminal, emitted by macro expansion
    data class OfSymbol(val kind: SymbolKind) : TypeRef()

    data class Fn(
        val forall: MutableList<TypeParameter>,
        val path: Path,
        val parameters: MutableList<TypeRef>,
        val ret: TypeRef?,
    ) : TypeRef()

    override fun toString(): String = when (this) {
        is Tuple -> "(${Sep(", ", types)})"
        is Slice -> "[${ty}]"
        is Nominal -> if (types.isEmpty()) "$path" else "$path<${Sep(", ", types)}>"
        is TraitObject -> if (types.isEmpty()) "dyn $path" else "dyn $path<${Sep(", ", types)}>"
        is LifetimeRef -> "$lifetime"
        is Id -> "$atom"
        is OfSymbol -> "`$kind`"
        is Ref -> when {
            lifetime == null && !mutable -> "&$referent"
            lifetime != null && !mutable -> "&$lifetime $referent"
            lifetime == null && mutable -> "&mut $referent"
            else -> "&$lifetime mut $referent"
        }
        is Fn -> buildString {
            append("dyn ")
            if (forall.isNotEmpty()) {
                append("for<${Sep(", ", forall)}> ")
            }
            append("$path(${Sep(", ", parameters)})")
            if (ret != null) append(" -> $ret")
        }
    }

    // Converts a TypeRef to a TypeRepr, assuming no inference is
    // required etc. This is safe for all types a user can directly
    // type, but not safe for the result of expanding macros.
    fun typeRepr(): TypeRepr = when (this) {
        is Tuple -> TypeRepr.Tuple(types.map { it.typeRepr() }.toMutableList())
        is Slice -> TypeRepr.Slice(ty.typeRepr())
        is Nominal -> TypeRepr.Nominal(NominalTypeRepr(
            path = path,
            types = types.map { it.typeRepr() }.toMutableList(),
        ))
        is LifetimeRef -> TypeRepr.LifetimeRepr(lifetime)
        is Id -> TypeRepr.Nominal(NominalTypeRepr(
            path = Path.fromId(atom),
            types = mutableListOf(),
        ))
        is OfSymbol -> error("OfSymbol produced by parser")
        is Ref -> TypeRepr.Ref(
            lifetime = lifetime,
            mutable = mutable,
            referent = referent.typeRepr(),
        )
        is TraitObject -> TypeRepr.TraitObject(NominalTypeRepr(
            path = path,
            types = types.map { it.typeRepr() }.toMutableList(),
        ))
        is Fn -> TypeRepr.Fn(
            forall = forall.toMutableList(),
            path = path,
            parameters = parameters.map { it.typeRepr() }.toMutableList(),
            ret = ret?.typeRepr(),
        )
    }
}

sealed class WhereClause<T> : Comparable<WhereClause<T>> {
    // 'a: 'b + 'c
    data class LifetimeClause<T>(
        val lifetime: Lifetime,
        val bounds: MutableList<Lifetime>,
    ) : WhereClause<T>()

    // where for<'a> &'a T: Debug + Into<usize>
    data class Type<T>(
        val forall: MutableList<TypeParameter>,
        val ty: T,
        val bounds: MutableList<TypeBound<T>>,
    ) : WhereClause<T>()

    override fun compareTo(other: WhereClause<T>): Int = toString().compareTo(other.toString())
}

fun <T> WhereClause<T>.display(): String = when (this) {
    is WhereClause.LifetimeClause<T> -> buildString {
        append("$lifetime:")
        for ((i, b) in bounds.withIndex()) {
            if (i != 0) append(" +")
            append(" $b")
        }
    }
    is WhereClause.Type<T> -> buildString {
        if (forall.isNotEmpty()) {
            append("for<")
            for ((i, l) in forall.withIndex()) {
                if (i != 0) append(", ")
                append("$l")
            }
            append("> ")
        }
        append("$ty: ")
        for ((i, b) in bounds.withIndex()) {
            if (i != 0) append(" +")
            append(" ${b.display()}")
        }
    }
}

sealed class TypeBound<T> : Comparable<TypeBound<T>> {
    // The `'a` in `T: 'a`.
    data class LifetimeBound<T>(val lifetime: Lifetime) : TypeBound<T>()

    // `for<'a> FnMut(&'a usize)`
    data class Fn<T>(
        val forall: MutableList<TypeParameter>,
        val path: Path,
        val parameters: MutableList<T>,
        val ret: T?,
    ) : TypeBound<T>()

    // `some::Trait` or `some::Trait<Param, ...>` or `some::Trait<Item = Assoc>`
    // or `for<'a> Trait<'a, T>`
    data class Trait<T>(
        val forall: MutableList<TypeParameter>,
        val path: Path,
        val parameters: MutableList<TypeBoundParameter<T>>,
    ) : TypeBound<T>()

    override fun compareTo(other: TypeBound<T>): Int = toString().compareTo(other.toString())

    fun <U> map(f: (T) -> U): TypeBound<U> = when (this) {
        is LifetimeBound -> LifetimeBound(lifetime)
        is Fn -> Fn(
            forall = forall.toMutableList(),
            path = path,
            parameters = parameters.map(f).toMutableList(),
            ret = ret?.let(f),
        )
        is Trait -> Trait(
            forall = forall.toMutableList(),
            path = path,
            parameters = parameters.map { it.map(f) }.toMutableList(),
        )
    }
}

fun <T> TypeBound<T>.display(): String = when (this) {
    is TypeBound.LifetimeBound<T> -> "$lifetime"
    is TypeBound.Fn<T> -> buildString {
        if (forall.isNotEmpty()) {
            append("for<")
            for ((i, l) in forall.withIndex()) {
                if (i != 0) append(", ")
                append("$l")
            }
            append("> ")
        }
        append("$path(")
        for ((i, p) in parameters.withIndex()) {
            if (i != 0) append(", ")
            append("$p")
        }
        append(")")
        if (ret != null) append(" -> $ret")
    }
    is TypeBound.Trait<T> -> buildString {
        if (forall.isNotEmpty()) {
            append("for<")
            for ((i, l) in forall.withIndex()) {
                if (i != 0) append(", ")
                append("$l")
            }
            append("> ")
        }
        append("$path")
        if (parameters.isEmpty()) return@buildString
        append("<")
        for ((i, p) in parameters.withIndex()) {
            if (i != 0) append(", ")
            append(p.display())
        }
        append(">")
    }
}

sealed class TypeBoundParameter<T> : Comparable<TypeBoundParameter<T>> {
    // 'a
    data class LifetimeParam<T>(val lifetime: Lifetime) : TypeBoundParameter<T>()
    // `T` or `'a`
    data class TypeParameterParam<T>(val ty: T) : TypeBoundParameter<T>()
    // `Item = T`
    data class Associated<T>(val id: Atom, val ty: T) : TypeBoundParameter<T>()

    override fun compareTo(other: TypeBoundParameter<T>): Int = toString().compareTo(other.toString())

    fun <U> map(f: (T) -> U): TypeBoundParameter<U> = when (this) {
        is LifetimeParam -> LifetimeParam(lifetime)
        is TypeParameterParam -> TypeParameterParam(f(ty))
        is Associated -> Associated(id, f(ty))
    }

    fun display(): String = when (this) {
        is LifetimeParam -> "$lifetime"
        is TypeParameterParam -> "$ty"
        is Associated -> "$id = $ty"
    }
}

sealed class TypeParameter : Comparable<TypeParameter> {
    data class LifetimeTp(val lifetime: Lifetime) : TypeParameter()
    data class Id(val atom: Atom) : TypeParameter()

    override fun compareTo(other: TypeParameter): Int = toString().compareTo(other.toString())

    override fun toString(): String = when (this) {
        is LifetimeTp -> "$lifetime"
        is Id -> "$atom"
    }
}

data class Parameter(
    var name: Atom,
    var ty: TypeRef,
)

sealed class Visibility {
    data class Pub(val path: Path?) : Visibility()
    data class PubIn(val path: Path) : Visibility()
    data object Priv : Visibility()

    fun isPub(): Boolean = when (this) {
        is Pub -> true
        is PubIn -> true
        Priv -> false
    }

    override fun toString(): String = when (this) {
        is Pub -> if (path != null) "pub($path) " else "pub "
        is PubIn -> "pub(in $path) "
        Priv -> ""
    }
}

data class NonterminalData(
    var visibility: Visibility,
    var name: NonterminalString,
    var attributes: MutableList<Attribute>,
    var span: Span,
    var args: MutableList<NonterminalString>, // macro arguments
    var typeDecl: TypeRef?,
    var alternatives: MutableList<Alternative>,
) {
    fun isMacroDef(): Boolean = args.isNotEmpty()
}

data class Attribute(
    var idSpan: Span,
    var id: Atom,
    var arg: AttributeArg,
) {
    /** get the (key, value) of an attribute of the form #[key = "value"] */
    fun getArgEqual(): Pair<Atom, String>? {
        val arg = this.arg
        if (arg is AttributeArg.Paren) {
            val first = arg.attrs.firstOrNull() ?: return null
            val fa = first.arg
            if (fa is AttributeArg.Equal) return first.id to fa.value
        }
        return null
    }
}

sealed class AttributeArg {
    data object Empty : AttributeArg()
    data class Paren(val attrs: MutableList<Attribute>) : AttributeArg()
    data class Equal(val value: String) : AttributeArg()

    companion object {
        fun default(): AttributeArg = Empty
    }
}

data class Alternative(
    var span: Span,
    var expr: ExprSymbol,
    // if C, only legal in macros
    var condition: Condition?,
    // => { code }
    var action: ActionKind?,
    var attributes: MutableList<Attribute>,
)

sealed class ActionKind {
    data class User(val code: String) : ActionKind()
    data class Fallible(val code: String) : ActionKind()
    data object Lookahead : ActionKind()
    data object Lookbehind : ActionKind()
}

data class Condition(
    var span: Span,
    var lhs: NonterminalString, // X
    var rhs: Atom,              // "Foo"
    var op: ConditionOp,
)

enum class ConditionOp {
    // X == "Foo", equality
    Equals,
    // X != "Foo", inequality
    NotEquals,
    // X ~~ "Foo", regexp match
    Match,
    // X !~ "Foo", regexp non-match
    NotMatch,
}

data class Symbol(
    var span: Span,
    var kind: SymbolKind,
) {
    companion object {
        fun new(span: Span, kind: SymbolKind): Symbol = Symbol(span, kind)
    }

    fun canonicalForm(): String = toString()

    fun asTuple(): Pair<Tuple, Symbol>? = when (val k = kind) {
        is SymbolKind.TupleKind -> k.tuple to k.sym
        else -> null
    }

    override fun toString(): String = kind.toString()
}

sealed class SymbolKind {
    // (X Y)
    data class Expr(val expr: ExprSymbol) : SymbolKind()

    // foo, before name resolution
    data class AmbiguousId(val atom: Atom) : SymbolKind()

    // "foo" and foo (after name resolution)
    data class Terminal(val terminal: TerminalString) : SymbolKind()

    // foo, after name resolution
    data class Nonterminal(val nt: NonterminalString) : SymbolKind()

    // foo<..>
    data class Macro(val sym: MacroSymbol) : SymbolKind()

    // X+, X?, X*
    data class Repeat(val sym: RepeatSymbol) : SymbolKind()

    // <X>
    data class Choose(val sym: Symbol) : SymbolKind()

    // <x:X> or <mut x:X>
    data class Name(val name: io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Name, val sym: Symbol) : SymbolKind()

    // <(x, y):X)> or <(x, (mut y, z)):X>
    data class TupleKind(val tuple: Tuple, val sym: Symbol) : SymbolKind()

    // @L
    data object Lookahead : SymbolKind()

    // @R
    data object Lookbehind : SymbolKind()

    data object Error : SymbolKind()

    override fun toString(): String = when (this) {
        is Expr -> "$expr"
        is Terminal -> "$terminal"
        is Nonterminal -> "$nt"
        is AmbiguousId -> "$atom"
        is Macro -> "$sym"
        is Repeat -> "$sym"
        is Choose -> "<$sym>"
        is Name -> "$name:$sym"
        is TupleKind -> "$tuple:$sym"
        Lookahead -> "@L"
        Lookbehind -> "@R"
        Error -> "error"
    }
}

data class Name(
    var mutable: Boolean,
    var name: Atom,
) {
    companion object {
        fun new(mutable: Boolean, name: Atom): Name = Name(mutable, name)
        fun immut(name: Atom): Name = new(false, name)
    }

    override fun toString(): String =
        if (mutable) "mut $name" else name.toString()
}

data class Tuple(
    // Vec<(mutable, name)>
    var tuples: MutableList<ArgPattern>,
) {
    companion object {
        fun new(tuples: MutableList<ArgPattern>): Tuple = Tuple(tuples)
    }

    override fun toString(): String = "(${Sep(", ", tuples)})"
}

sealed class ArgPattern {
    data class NamePat(val name: io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.Name) : ArgPattern()
    data class TuplePat(val tuple: Tuple) : ArgPattern()

    fun name(): String = when (this) {
        is NamePat -> name.name.toString()
        is TuplePat -> tuple.toString()
    }

    override fun toString(): String = when (this) {
        is NamePat -> name.toString()
        is TuplePat -> tuple.toString()
    }
}

sealed class TerminalString : Comparable<TerminalString> {
    data class Literal(val literal: TerminalLiteral) : TerminalString() {
        override fun toString(): String = literal.toString()
    }
    data class Bare(val atom: Atom) : TerminalString() {
        override fun toString(): String = atom.toString()
    }
    data object Error : TerminalString() {
        override fun toString(): String = "error"
    }

    override fun compareTo(other: TerminalString): Int = toString().compareTo(other.toString())

    fun asLiteral(): TerminalLiteral? = when (this) {
        is Literal -> literal
        else -> null
    }

    fun displayLen(): Int = when (this) {
        is Literal -> literal.displayLen()
        is Bare -> atom.len()
        Error -> "error".length
    }

    override fun toString(): String = when (this) {
        is Literal -> literal.toString()
        is Bare -> atom.toString()
        Error -> "error"
    }

    companion object {
        fun quoted(i: Atom): TerminalString = Literal(TerminalLiteral.Quoted(i))
        fun regex(i: Atom): TerminalString = Literal(TerminalLiteral.Regex(i))
    }
}

fun TerminalString.toContent(): Content {
    val session = Tls.session()
    return InlineBuilder.new()
        .text(this)
        .styled(session.terminalSymbol)
        .end()
}

sealed class TerminalLiteral : Comparable<TerminalLiteral> {
    data class Quoted(val atom: Atom) : TerminalLiteral() {
        override fun toString(): String = "\"${atom.asRef()}\""
    }
    data class Regex(val atom: Atom) : TerminalLiteral() {
        // FIXME -- need to determine proper number of #
        override fun toString(): String = "r#\"${atom.asRef()}\"#"
    }

    override fun compareTo(other: TerminalLiteral): Int = toString().compareTo(other.toString())

    /**
     * The *base precedence* is the precedence within a `match { }`
     * block level. It indicates that quoted things like `"foo"` get
     * precedence over regex matches.
     */
    fun basePrecedence(): Int = when (this) {
        is Quoted -> 1
        is Regex -> 0
    }

    fun displayLen(): Int = when (this) {
        is Quoted -> atom.len()
        is Regex -> atom.len() + "####r".length
    }

    override fun toString(): String = when (this) {
        is Quoted -> "\"${atom.asRef()}\"" // the Debug impl adds the `"` and escaping
        is Regex -> "r#\"${atom.asRef()}\"#" // FIXME -- need to determine proper number of #
    }
}

data class NonterminalString(val atom: Atom) : Comparable<NonterminalString> {
    fun len(): Int = atom.len()

    override fun compareTo(other: NonterminalString): Int = atom.compareTo(other.atom)

    override fun toString(): String = atom.toString()
}

fun NonterminalString.toContent(): Content {
    val session = Tls.session()
    return InlineBuilder.new()
        .text(this)
        .styled(session.nonterminalSymbol)
        .end()
}

data class Lifetime(val atom: Atom) : Comparable<Lifetime> {
    companion object {
        fun anonymous(): Lifetime = Lifetime(Atom.from("'_"))
        fun statik(): Lifetime = Lifetime(Atom.from("'static"))
        fun input(): Lifetime = Lifetime(Atom.from(INPUT_LIFETIME))
    }

    fun isAnonymous(): Boolean = this == anonymous()

    fun len(): Int = atom.len()

    override fun compareTo(other: Lifetime): Int = atom.compareTo(other.atom)

    override fun toString(): String = atom.toString()
}

enum class RepeatOp {
    Star, Plus, Question;

    override fun toString(): String = when (this) {
        Plus -> "+"
        Star -> "*"
        Question -> "?"
    }
}

data class RepeatSymbol(
    var op: RepeatOp,
    var symbol: Symbol,
) {
    fun canonicalForm(): String = toString()

    override fun toString(): String = "$symbol$op"
}

data class ExprSymbol(
    var symbols: MutableList<Symbol>,
) {
    fun canonicalForm(): String = toString()

    override fun toString(): String = "(${Sep(" ", symbols)})"
}

data class MacroSymbol(
    var name: NonterminalString,
    var args: MutableList<Symbol>,
) {
    fun canonicalForm(): String = toString()

    override fun toString(): String = "$name<${Sep(", ", args)}>"
}

fun Grammar.externToken(): ExternToken? =
    items.asSequence()
        .mapNotNull { it.asExternToken() }
        .firstOrNull()

fun Grammar.enumToken(): EnumToken? =
    items.asSequence()
        .mapNotNull { it.asExternToken() }
        .mapNotNull { it.enumToken }
        .firstOrNull()

fun Grammar.internToken(): InternToken? =
    items.asSequence()
        .mapNotNull { it.asInternToken() }
        .firstOrNull()

fun Grammar.matchToken(): MatchToken? =
    items.asSequence()
        .mapNotNull { it.asMatchToken() }
        .firstOrNull()

fun GrammarItem.isMacroDef(): Boolean = when (this) {
    is GrammarItem.Nonterminal -> data.isMacroDef()
    else -> false
}

fun GrammarItem.asNonterminal(): NonterminalData? = when (this) {
    is GrammarItem.Nonterminal -> data
    else -> null
}

fun GrammarItem.asMatchToken(): MatchToken? = when (this) {
    is GrammarItem.MatchToken -> inner
    else -> null
}

fun GrammarItem.asExternToken(): ExternToken? = when (this) {
    is GrammarItem.ExternToken -> inner
    else -> null
}

fun GrammarItem.asInternToken(): InternToken? = when (this) {
    is GrammarItem.InternToken -> inner
    else -> null
}

fun readAlgorithm(attributes: List<Attribute>, algorithm: Algorithm) {
    for (attribute in attributes) {
        when {
            attribute.id.toString() == LALR -> algorithm.lalr = true
            attribute.id.toString() == TABLE_DRIVEN -> algorithm.codegen = LrCodeGeneration.TableDriven
            attribute.id.toString() == RECURSIVE_ASCENT -> algorithm.codegen = LrCodeGeneration.RecursiveAscent
            attribute.id.toString() == TEST_ALL -> algorithm.codegen = LrCodeGeneration.TestAll
            else -> error("validation permitted unknown attribute: ${attribute.id}")
        }
    }
}
