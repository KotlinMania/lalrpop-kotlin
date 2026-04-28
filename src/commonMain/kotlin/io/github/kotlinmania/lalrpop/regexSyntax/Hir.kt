// port-lint: helper crate-regexSyntax
// Helper: minimal port of the Rust `regexSyntax` crate, scoped to the
// subset of the Hir API that LALRPOP lexer/nfa builder traverses. This
// crate is a compilation dependency of LALRPOP that has no Kotlin analog;
// the full crate is out-of-scope for line-by-line translation, so this
// helper stands in as an equivalent-shape library. Consumers:
//   - src/lexer/re/mod.rs       -> uses Hir as `Hir`, plus ParserBuilder
//   - src/lexer/nfa/mod.rs      -> walks HirKind, Class, ClassUnicodeRange,
//                                  ClassBytesRange, Repetition, Literal
package io.github.kotlinmania.lalrpop.regexSyntax

/**
 * High-level intermediate representation of a regular expression.
 *
 * Mirrors `regexSyntax::hir::Hir`. An `Hir` is a thin wrapper around a
 * `HirKind`; the Nfa builder switches on `.kind()` to traverse.
 */
data class Hir(private val kindValue: HirKind) {
    fun kind(): HirKind = kindValue

    /**
     * Mirrors `regexSyntax::hir::Hir`'s `Display` implementation: returns the
     * regex pattern source the Hir was parsed from. LALRPOP relies on
     * this in `internToken::compile` to round-trip regexes through
     * Rust string-debug formatting. Without it the data-class
     * auto-generated `toString` would leak `Hir(kindValue=...)` into
     * the generated Rust source.
     */
    override fun toString(): String = kindValue.toString()
}

/**
 * The variant of [Hir]. Mirrors `regexSyntax::hir::HirKind`. LALRPOP Nfa
 * builder handles every variant listed here; `Look` causes a construction
 * error because LALRPOP does not support look-around or anchors.
 */
sealed class HirKind {
    // Mirrors `regexSyntax::hir::HirKind`'s `Display` implementation. Each
    // variant overrides `toString()` explicitly because the
    // `data class`-generated toString would otherwise shadow any
    // override on the sealed parent and emit `Literal(literal=...)`
    // strings into the generated Rust source.
    object Empty : HirKind() {
        override fun toString(): String = ""
    }

    data class Literal(val literal: RegexLiteral) : HirKind() {
        override fun toString(): String = literal.toString()
    }

    data class Class(val cls: RegexClass) : HirKind() {
        override fun toString(): String = cls.toString()
    }

    data class Look(val kind: LookKind) : HirKind() {
        override fun toString(): String = kind.regexSource()
    }

    data class Capture(val capture: RegexCapture) : HirKind() {
        override fun toString(): String = capture.toString()
    }

    data class Repetition(val repetition: RegexRepetition) : HirKind() {
        override fun toString(): String = repetition.toString()
    }

    data class Concat(val exprs: List<Hir>) : HirKind() {
        override fun toString(): String = buildString { exprs.forEach { append(it) } }
    }

    data class Alternation(val exprs: List<Hir>) : HirKind() {
        // Mirrors `regexSyntax::hir::Hir`'s `Display` implementation for
        // alternation: each branch is wrapped in `(?:...)` and the
        // whole thing wrapped again. So `false|true` round-trips as
        // `(?:(?:false)|(?:true))`. Without the per-branch wrapper the
        // generated Rust source has just `false|true`, which matches
        // semantically but disagrees with upstream byte-for-byte.
        override fun toString(): String =
            exprs.joinToString(separator = "|", prefix = "(?:", postfix = ")") { "(?:$it)" }
    }
}

/**
 * A sequence of bytes representing a literal match. Mirrors
 * `regexSyntax::hir::Literal`. The Nfa builder iterates the bytes in
 * reverse to build a chain of single-byte test edges.
 */
data class RegexLiteral(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegexLiteral) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()

    /**
     * Mirrors `regexSyntax::hir::Literal`'s `Display` implementation. ASCII bytes
     * pass through verbatim; bytes that are special in regex syntax
     * (`. \ + * ? ( ) | [ ] { } ^ $ #`) are backslash-escaped; non-ASCII
     * bytes are emitted as `\xNN` hex escapes — matching upstream
     * `Display::fmt` cases.
     */
    override fun toString(): String = buildString {
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            when {
                u in 0x20..0x7E -> {
                    val c = u.toChar()
                    if (c in regexMetaChars) append('\\')
                    append(c)
                }
                else -> append("\\x").append(u.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }

    private companion object {
        val regexMetaChars: kotlin.collections.Set<Char> =
            setOf('.', '\\', '+', '*', '?', '(', ')', '|', '[', ']', '{', '}', '^', '$', '#')
    }
}

/**
 * A character class. Mirrors `regexSyntax::hir::Class`, which is either
 * a unicode class (iterating `ClassUnicodeRange`) or a bytes class
 * (iterating `ClassBytesRange`). LALRPOP supports both.
 */
sealed class RegexClass {
    abstract val ranges: List<Any>

    // Each subclass overrides toString explicitly. Mirrors
    // `regexSyntax::hir::Class`'s `Display` implementation: emits the
    // bracket-delimited character-class regex source.
    data class Unicode(override val ranges: List<ClassUnicodeRange>) : RegexClass() {
        // Mirror `regexSyntax::hir::Class`'s `Display` implementation: emit raw
        // characters in the regex source string. `internToken::compile`
        // wraps this string in the upstream Debug formatting (`format("{:?}",
        // regex)`), which is what produces the `\t`, `\r`, `\u{HH}`
        // escape sequences in the generated Rust source. Emitting
        // pre-escaped sequences here would lead to double-escaping
        // (`\\t`, `\\u{...}`) once the Debug wrapper runs.
        override fun toString(): String =
            ranges.joinToString(separator = "", prefix = "[", postfix = "]") { range ->
                if (range.start() == range.end()) "${range.start()}"
                else "${range.start()}-${range.end()}"
            }
    }

    data class Bytes(override val ranges: List<ClassBytesRange>) : RegexClass() {
        override fun toString(): String =
            ranges.joinToString(separator = "", prefix = "(?-u:[", postfix = "])") { range ->
                if (range.start() == range.end()) "\\x${range.start().toString(16).uppercase().padStart(2, '0')}"
                else "\\x${range.start().toString(16).uppercase().padStart(2, '0')}-\\x${range.end().toString(16).uppercase().padStart(2, '0')}"
            }
    }
}

/** Inclusive range of unicode code points (characters). */
data class ClassUnicodeRange(val startValue: Char, val endValue: Char) {
    fun start(): Char = startValue
    fun end(): Char = endValue
}

/** Inclusive range of bytes. */
data class ClassBytesRange(val startValue: UByte, val endValue: UByte) {
    fun start(): UByte = startValue
    fun end(): UByte = endValue
}

/**
 * A capture group. Mirrors `regexSyntax::hir::Capture`.
 *
 * LALRPOP rejects named captures (`NamedCaptures` error) but passes
 * through unnamed captures transparently to the inner expression.
 */
data class RegexCapture(val name: String?, val sub: Hir) {
    /** `regexSyntax::hir::Capture::Display`: emits `(?P<name>sub)` or `(sub)`. */
    override fun toString(): String =
        if (name != null) "(?P<$name>$sub)" else "($sub)"
}

/**
 * A repetition (quantifier) such as `a*`, `a+`, `a?`, `a{n,m}`.
 * Mirrors `regexSyntax::hir::Repetition`. LALRPOP rejects non-greedy
 * repetitions (`NonGreedy` error) since its matcher always picks the
 * longest match.
 */
data class RegexRepetition(
    val min: UInt,
    val max: UInt?,
    val greedy: Boolean,
    val sub: Hir,
) {
    /**
     * `regexSyntax::hir::Repetition::Display`: emits `sub*`, `sub+`,
     * `sub?`, or `sub{n,m}` according to `(min, max)`. The `?` suffix
     * is appended when `!greedy`.
     */
    override fun toString(): String = buildString {
        append(sub)
        when {
            min == 0u && max == null -> append('*')
            min == 1u && max == null -> append('+')
            min == 0u && max == 1u -> append('?')
            min == max -> append("{$min}")
            max == null -> append("{$min,}")
            else -> append("{$min,$max}")
        }
        if (!greedy) append('?')
    }
}

/**
 * Look-around / anchor kind. LALRPOP does not support any of these and
 * rejects them at Nfa construction time with `LookAround`. The variants
 * are present for API completeness but not meaningfully distinguished.
 */
/**
 * Mirrors the `Display` shape of `regexSyntax::hir::Look` for the
 * subset of patterns LALRPOP does not reject. None of these emit through
 * the codegen path (LALRPOP rejects look-around at Nfa construction),
 * so the strings here are placeholders that match upstream symbols.
 */
internal fun LookKind.regexSource(): String = when (this) {
    LookKind.StartLine -> "(?m:^)"
    LookKind.EndLine -> "(?m:${'$'})"
    LookKind.StartText -> "\\A"
    LookKind.EndText -> "\\z"
    LookKind.WordAscii -> "(?-u:\\b)"
    LookKind.WordAsciiNegate -> "(?-u:\\B)"
    LookKind.WordUnicode -> "\\b"
    LookKind.WordUnicodeNegate -> "\\B"
    LookKind.WordStartAscii -> "(?-u:\\b{start})"
    LookKind.WordEndAscii -> "(?-u:\\b{end})"
    LookKind.WordStartUnicode -> "\\b{start}"
    LookKind.WordEndUnicode -> "\\b{end}"
    LookKind.WordStartHalfAscii -> "(?-u:\\b{start-half})"
    LookKind.WordEndHalfAscii -> "(?-u:\\b{end-half})"
    LookKind.WordStartHalfUnicode -> "\\b{start-half}"
    LookKind.WordEndHalfUnicode -> "\\b{end-half}"
}

enum class LookKind {
    StartLine,
    EndLine,
    StartText,
    EndText,
    WordAscii,
    WordAsciiNegate,
    WordUnicode,
    WordUnicodeNegate,
    WordStartAscii,
    WordEndAscii,
    WordStartUnicode,
    WordEndUnicode,
    WordStartHalfAscii,
    WordEndHalfAscii,
    WordStartHalfUnicode,
    WordEndHalfUnicode,
}

/**
 * Parsing error raised by [ParserBuilder.parse]. Mirrors
 * `regexSyntax::Error`. LALRPOP wraps this in a `Box<Error>` aliased as
 * `RegexSyntaxError` — see `lexer/re/mod.rs`.
 */
class RegexSyntaxError(message: String, val position: Int = -1) : RuntimeException(message)
