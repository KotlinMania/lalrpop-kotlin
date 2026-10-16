// transliterated from upstream module root
/** A parser and representation of regular expressions. */
package io.github.kotlinmania.lalrpop.lexer

import io.github.kotlinmania.regexsyntax.hir.Hir
import io.github.kotlinmania.regexsyntax.parser.ParserBuilder
import io.github.kotlinmania.regexsyntax.Error as RegexSyntaxError
import io.github.kotlinmania.regexsyntax.escape as regexSyntaxEscape

internal typealias Regex = Hir
internal typealias RegexError = RegexSyntaxError

/** Convert a string literal into a parsed regular expression. */
internal fun parseLiteral(s: String): Regex =
    parseRegex(regexSyntaxEscape(s)).getOrElse {
        error("failed to parse literal regular expression")
    }

/** Parse a regular expression like `a+` etc. */
internal fun parseRegex(s: String): Result<Regex> {
    val enableUnicode = true
    val expr =
        ParserBuilder
            .new()
            .utf8(enableUnicode)
            .unicode(enableUnicode)
            .build()
            .parse(s)
    return expr
}
