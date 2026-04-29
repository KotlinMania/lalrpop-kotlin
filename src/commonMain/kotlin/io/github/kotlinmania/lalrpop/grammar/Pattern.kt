// port-lint: source grammar/pattern.rs
// The definition of patterns is shared between the parse-tree and the
// repr, but customized by a type T that represents the different type
// representations.
package io.github.kotlinmania.lalrpop.grammar

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.grammar.parsetree.Path
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span

data class Pattern<T>(
    var span: Span,
    var kind: PatternKind<T>,
) {
    fun forEachBinding(mapFn: (T) -> Unit) {
        map(mapFn)
    }

    fun <U> map(mapFn: (T) -> U): Pattern<U> = Pattern(
        span = span,
        kind = kind.map(mapFn),
    )

    fun fmt(): String = "$kind"

    override fun toString(): String = fmt()
}

data class FieldPattern<T>(
    var fieldSpan: Span,
    var fieldName: Atom,
    var pattern: Pattern<T>,
) {
    fun <U> map(mapFn: (T) -> U): FieldPattern<U> = FieldPattern(
        fieldName = fieldName,
        fieldSpan = fieldSpan,
        pattern = pattern.map(mapFn),
    )

    fun fmt(): String = "$fieldName: $pattern"

    override fun toString(): String = fmt()
}

sealed class PatternKind<T> {
    data class Enum<T>(val path: Path, val pats: MutableList<Pattern<T>>) : PatternKind<T>()

    data class Struct<T>(
        val path: Path,
        val fields: MutableList<FieldPattern<T>>,
        /* trailing ..? */
        val dotdot: Boolean,
    ) : PatternKind<T>()

    data class PathKind<T>(val path: Path) : PatternKind<T>()

    data class Tuple<T>(val pats: MutableList<Pattern<T>>) : PatternKind<T>()

    data class TupleStruct<T>(val path: Path, val pats: MutableList<Pattern<T>>) : PatternKind<T>()

    data class Usize<T>(val value: Int) : PatternKind<T>()

    class Underscore<T> : PatternKind<T>() {
        override fun equals(other: Any?): Boolean = other is Underscore<*>
        override fun hashCode(): Int = 1
    }

    class DotDot<T> : PatternKind<T>() {
        override fun equals(other: Any?): Boolean = other is DotDot<*>
        override fun hashCode(): Int = 2
    }

    data class Choose<T>(val ty: T) : PatternKind<T>()

    data class CharLiteral<T>(val c: Atom) : PatternKind<T>()

    data class StringKind<T>(val s: String) : PatternKind<T>()

    fun fmt(): String = when (this) {
        is PathKind -> "$path"
        is Enum -> "$path(${Sep(", ", pats)})"
        is Struct -> when {
            !dotdot -> "$path { ${Sep(", ", fields)} }"
            fields.isEmpty() -> "$path { .. }"
            else -> "$path { ${Sep(", ", fields)}, .. }"
        }
        is Tuple -> "(${Sep(", ", pats)})"
        is TupleStruct -> "$path(${Sep(", ", pats)})"
        is Underscore -> "_"
        is DotDot -> ".."
        is Usize -> "$value"
        is Choose -> "$ty"
        is CharLiteral -> "'$c'"
        is StringKind -> "\"$s\""
    }

    override fun toString(): String = fmt()

    fun <U> map(mapFn: (T) -> U): PatternKind<U> {
        return when (this) {
            is PathKind -> PathKind(path)
            is Enum -> Enum(
                path,
                pats.map { pat -> pat.map(mapFn) }.toMutableList(),
            )
            is Struct -> Struct(
                path,
                fields.map { pat -> pat.map(mapFn) }.toMutableList(),
                dotdot,
            )
            is Tuple -> {
                Tuple(pats.map { p -> p.map(mapFn) }.toMutableList())
            }
            is TupleStruct -> {
                TupleStruct(path, pats.map { p -> p.map(mapFn) }.toMutableList())
            }
            is Underscore -> PatternKind.Underscore()
            is DotDot -> PatternKind.DotDot()
            is Usize -> PatternKind.Usize(value)
            is Choose -> PatternKind.Choose(mapFn(ty))
            is CharLiteral -> PatternKind.CharLiteral(c)
            is StringKind -> PatternKind.StringKind(s)
        }
    }
}
