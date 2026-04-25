// port-lint: helper crate-regex_syntax
// Helper: minimal port of the Rust `regex_syntax` crate, scoped to the
// subset of the Hir API that LALRPOP's lexer/nfa builder traverses. This
// crate is a compilation dependency of LALRPOP that has no Kotlin analog;
// the full crate is out-of-scope for line-by-line translation, so this
// helper stands in as an equivalent-shape library. Consumers:
//   - src/lexer/re/mod.rs       -> uses Hir as `Regex`, plus ParserBuilder
//   - src/lexer/nfa/mod.rs      -> walks HirKind, Class, ClassUnicodeRange,
//                                  ClassBytesRange, Repetition, Literal
package io.github.kotlinmania.lalrpop_kotlin.regexSyntax

/**
 * High-level intermediate representation of a regular expression.
 *
 * Mirrors `regex_syntax::hir::Hir`. An `Hir` is a thin wrapper around a
 * `HirKind`; the NFA builder switches on `.kind()` to traverse.
 */
data class Hir(private val kindValue: HirKind) {
    fun kind(): HirKind = kindValue
}

/**
 * The variant of [Hir]. Mirrors `regex_syntax::hir::HirKind`. LALRPOP's NFA
 * builder handles every variant listed here; `Look` causes a construction
 * error because LALRPOP doesn't support look-around or anchors.
 */
sealed class HirKind {
    object Empty : HirKind()
    data class Literal(val literal: RegexLiteral) : HirKind()
    data class Class(val cls: RegexClass) : HirKind()
    data class Look(val kind: LookKind) : HirKind()
    data class Capture(val capture: RegexCapture) : HirKind()
    data class Repetition(val repetition: RegexRepetition) : HirKind()
    data class Concat(val exprs: List<Hir>) : HirKind()
    data class Alternation(val exprs: List<Hir>) : HirKind()
}

/**
 * A sequence of bytes representing a literal match. Mirrors
 * `regex_syntax::hir::Literal`. The NFA builder iterates the bytes in
 * reverse to build a chain of single-byte test edges.
 */
data class RegexLiteral(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegexLiteral) return false
        return bytes.contentEquals(other.bytes)
    }
    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * A character class. Mirrors `regex_syntax::hir::Class`, which is either
 * a unicode class (iterating `ClassUnicodeRange`) or a bytes class
 * (iterating `ClassBytesRange`). LALRPOP supports both.
 */
sealed class RegexClass {
    abstract val ranges: List<Any>

    data class Unicode(override val ranges: List<ClassUnicodeRange>) : RegexClass()
    data class Bytes(override val ranges: List<ClassBytesRange>) : RegexClass()
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
 * A capture group. Mirrors `regex_syntax::hir::Capture`.
 *
 * LALRPOP rejects named captures (`NamedCaptures` error) but passes
 * through unnamed captures transparently to the inner expression.
 */
data class RegexCapture(val name: String?, val sub: Hir)

/**
 * A repetition (quantifier) such as `a*`, `a+`, `a?`, `a{n,m}`.
 * Mirrors `regex_syntax::hir::Repetition`. LALRPOP rejects non-greedy
 * repetitions (`NonGreedy` error) since its matcher always picks the
 * longest match.
 */
data class RegexRepetition(
    val min: UInt,
    val max: UInt?,
    val greedy: Boolean,
    val sub: Hir,
)

/**
 * Look-around / anchor kind. LALRPOP does not support any of these and
 * rejects them at NFA construction time with `LookAround`. The variants
 * are present for API completeness but not meaningfully distinguished.
 */
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
 * `regex_syntax::Error`. LALRPOP wraps this in a `Box<Error>` aliased as
 * `RegexError` — see `lexer/re/mod.rs`.
 */
class RegexSyntaxError(message: String, val position: Int = -1) : RuntimeException(message)
