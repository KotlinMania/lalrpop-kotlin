// port-lint: helper crate-regexSyntax
// Helper: escape() and ParserBuilder match the `regexSyntax` crate
// public surface that LALRPOP lexer/re/mod.rs consumes. The parse
// implementation lives in Parser.kt.
package io.github.kotlinmania.lalrpop.regexsyntax

/**
 * Escape all regex metacharacters in `s` so that the result matches `s`
 * literally when parsed as a regex. Mirrors `regexSyntax::escape`.
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
 * Builder for a regex parser. Mirrors `regexSyntax::ParserBuilder`.
 *
 * LALRPOP sets `utf8(enableUnicode)` and `unicode(enableUnicode)` where
 * `enableUnicode = cfg(feature = "unicode")`. In the Kotlin port we
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
 * A regex parser. Mirrors `regexSyntax::Parser`. The `parse` method
 * returns a [Result] mirroring the upstream `Result<Hir, Error>`.
 */
class Parser internal constructor(
    private val utf8: Boolean,
    private val unicode: Boolean,
) {
    fun parse(s: String): Result<Hir> = runCatching {
        RegexParser(s, utf8 = utf8, unicode = unicode).parseTopLevel()
    }
}
