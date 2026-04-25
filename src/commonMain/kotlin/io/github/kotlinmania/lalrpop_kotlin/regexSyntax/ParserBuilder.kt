// port-lint: helper crate-regex_syntax
// Helper: escape() and ParserBuilder match the `regex_syntax` crate's
// public surface that LALRPOP's lexer/re/mod.rs consumes. The parse
// implementation lives in Parser.kt.
package io.github.kotlinmania.lalrpop_kotlin.regexSyntax

/**
 * Escape all regex metacharacters in `s` so that the result matches `s`
 * literally when parsed as a regex. Mirrors `regex_syntax::escape`.
 */
fun escape(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        if (isMetacharacter(c)) sb.append('\\')
        sb.append(c)
    }
    return sb.toString()
}

private fun isMetacharacter(c: Char): Boolean = when (c) {
    '\\', '.', '+', '*', '?', '(', ')', '|', '[', ']', '{', '}', '^', '$', '#', '&', '-', '~' -> true
    else -> false
}

/**
 * Builder for a regex parser. Mirrors `regex_syntax::ParserBuilder`.
 *
 * LALRPOP sets `utf8(enable_unicode)` and `unicode(enable_unicode)` where
 * `enable_unicode = cfg!(feature = "unicode")`. In the Kotlin port we
 * default both to `true`; the flags are retained for API parity.
 */
class ParserBuilder {
    private var utf8: Boolean = true
    private var unicode: Boolean = true

    fun utf8(yes: Boolean): ParserBuilder { utf8 = yes; return this }
    fun unicode(yes: Boolean): ParserBuilder { unicode = yes; return this }

    fun build(): Parser = Parser(utf8 = utf8, unicode = unicode)
}

/**
 * A regex parser. Mirrors `regex_syntax::Parser`. The `parse` method
 * returns a [Result] mirroring Rust's `Result<Hir, Error>`.
 */
class Parser internal constructor(
    private val utf8: Boolean,
    private val unicode: Boolean,
) {
    fun parse(s: String): Result<Hir> = runCatching {
        RegexParser(s, utf8 = utf8, unicode = unicode).parseTopLevel()
    }
}
