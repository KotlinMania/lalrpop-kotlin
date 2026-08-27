// transliterated from upstream module root
/** A parser and representation of regular expressions. */
package io.github.kotlinmania.lalrpop.lexer

import io.github.kotlinmania.lalrpop.regexsyntax.Hir
import io.github.kotlinmania.lalrpop.regexsyntax.ParserBuilder
import io.github.kotlinmania.lalrpop.regexsyntax.RegexSyntaxError
import io.github.kotlinmania.lalrpop.regexsyntax.escape as regexSyntaxEscape

internal typealias Regex = Hir
internal typealias RegexError = RegexSyntaxError

/** Convert a string literal into a parsed regular expression. */
internal fun parseLiteral(s: String): Regex {
    return parseRegex(regexSyntaxEscape(s)).getOrElse {
        error("failed to parse literal regular expression")
    }
}

/** Parse a regular expression like `a+` etc. */
internal fun parseRegex(s: String): Result<Regex> {
    val enableUnicode = true
    val expr = ParserBuilder()
        .utf8(enableUnicode)
        .unicode(enableUnicode)
        .build()
        .parse(s)
    return expr
}
