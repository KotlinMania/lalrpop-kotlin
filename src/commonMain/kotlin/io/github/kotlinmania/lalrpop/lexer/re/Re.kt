// port-lint: source lexer/re/mod.rs
//! A parser and representation of regular expressions.
package io.github.kotlinmania.lalrpop.lexer.re

import io.github.kotlinmania.lalrpop.regexSyntax.Hir
import io.github.kotlinmania.lalrpop.regexSyntax.ParserBuilder
import io.github.kotlinmania.lalrpop.regexSyntax.RegexSyntaxError
import io.github.kotlinmania.lalrpop.regexSyntax.escape as regexSyntaxEscape


/** Convert a string literal into a parsed regular expression. */
fun parseLiteral(s: String): Hir =
    parseRegex(regexSyntaxEscape(s)).getOrElse {
        throw RuntimeException("failed to parse literal regular expression")
    }

/** Parse a regular expression like `a+` etc. */
fun parseRegex(s: String): Result<Hir> {
    val enableUnicode = true
    return ParserBuilder()
        .utf8(enableUnicode)
        .unicode(enableUnicode)
        .build()
        .parse(s)
}
