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

    override fun toString(): String = kind.toString()
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

    override fun toString(): String = "$fieldName: $pattern"
}

sealed class PatternKind<T> {
    abstract override fun toString(): String

    data class Enum<T>(val path: Path, val pats: MutableList<Pattern<T>>) : PatternKind<T>() {
        override fun toString(): String = "$path(${Sep(", ", pats)})"
    }

    data class Struct<T>(
        val path: Path,
        val fields: MutableList<FieldPattern<T>>,
        /* trailing ..? */
        val dotdot: Boolean,
    ) : PatternKind<T>() {
        override fun toString(): String = when {
            !dotdot -> "$path { ${Sep(", ", fields)} }"
            fields.isEmpty() -> "$path { .. }"
            else -> "$path { ${Sep(", ", fields)}, .. }"
        }
    }

    data class PathKind<T>(val path: Path) : PatternKind<T>() {
        override fun toString(): String = "$path"
    }

    data class Tuple<T>(val pats: MutableList<Pattern<T>>) : PatternKind<T>() {
        override fun toString(): String = "(${Sep(", ", pats)})"
    }

    data class TupleStruct<T>(val path: Path, val pats: MutableList<Pattern<T>>) : PatternKind<T>() {
        override fun toString(): String = "$path(${Sep(", ", pats)})"
    }

    data class Usize<T>(val value: Int) : PatternKind<T>() {
        override fun toString(): String = "$value"
    }

    class Underscore<T> : PatternKind<T>() {
        override fun equals(other: Any?): Boolean = other is Underscore<*>
        override fun hashCode(): Int = 1
        override fun toString(): String = "_"
    }

    class DotDot<T> : PatternKind<T>() {
        override fun equals(other: Any?): Boolean = other is DotDot<*>
        override fun hashCode(): Int = 2
        override fun toString(): String = ".."
    }

    data class Choose<T>(val ty: T) : PatternKind<T>() {
        override fun toString(): String = "$ty"
    }

    data class CharLiteral<T>(val c: Atom) : PatternKind<T>() {
        override fun toString(): String = "'$c'"
    }

    data class StringKind<T>(val s: String) : PatternKind<T>() {
        override fun toString(): String = "\"$s\""
    }

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
