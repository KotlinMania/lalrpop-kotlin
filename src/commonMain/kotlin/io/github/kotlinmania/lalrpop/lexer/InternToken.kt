// port-lint: ignore
// transliterated from upstream module root
/**
 * Generates an iterator type `Matcher` that emits a state-machine-based
 * tokenizer.
 */
package io.github.kotlinmania.lalrpop.lexer

import io.github.kotlinmania.lalrpop.grammar.parsetree.InternToken
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalLiteral
import io.github.kotlinmania.lalrpop.lexer.parseLiteral
import io.github.kotlinmania.lalrpop.lexer.parseRegex
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust

/**
 * Direct port of upstream `lexer::internToken::compile`. Emits an
 * `<prefix>internToken` Rust module that builds a `MatcherBuilder`
 * from the grammar regex set.
 */
fun compileInternToken(
    grammar: Grammar,
    internToken: InternToken,
    out: RustWrite,
) {
    val prefix = grammar.prefix

    rust(out, "#[rustfmt::skip]")
    rust(out, "mod {0}intern_token {", prefix)
    rust(out, "#![allow(unused_imports)]")
    out.writeUses("super::", grammar)
    rust(
        out,
        "pub fn new_builder() -> {0}lalrpop_util::lexer::MatcherBuilder {",
        prefix,
    )

    // create a sequence of (regex, skip) pairs in the order the grammar
    // gave us, mirroring upstream chained `.map(...)` pipeline.
    val regexStrings: Sequence<Pair<String, Boolean>> = internToken.matchEntries.asSequence()
        .map { matchEntry ->
            val regex = when (val literal = matchEntry.matchLiteral) {
                is TerminalLiteral.Quoted -> parseLiteral(literal.atom.toString())
                is TerminalLiteral.Hir -> parseRegex(literal.atom.toString()).getOrThrow()
            }
            val skip = when (matchEntry.userName) {
                is MatchMapping.Terminal -> false
                MatchMapping.Skip -> true
            }
            regex to skip
        }
        .map { (regex, skip) -> regex.toString() to skip }
        .map { (regexStr, skip) ->
            // the upstream `format("{regexStr:?}")` adds quotes and escapes; the
            // Kotlin equivalent is rustDebugQuote which mirrors `<&str as Debug>::fmt`.
            rustDebugQuote(regexStr) to skip
        }

    var containsSkip = false

    rust(out, "let {0}strs: &[(&str, bool)] = &[", prefix)
    for ((literal, skip) in regexStrings) {
        rust(out, "({0}, {1}),", literal, skip)
        containsSkip = containsSkip || skip
    }

    if (!containsSkip) {
        // Upstream branches on the `unicode` feature; the Kotlin port mirrors
        // the default-feature path (`unicode` enabled).
        rust(out, """(r"\s+", true),""")
    }

    rust(out, "];")

    rust(
        out,
        "{0}lalrpop_util::lexer::MatcherBuilder::new({0}strs.iter().copied()).unwrap()",
        prefix,
    )

    rust(out, "}") // function     rust(out, "}") // mod
}

/**
 * Mirrors the upstream `<&str as Debug>::fmt`: the result is the input string
 * wrapped in double quotes with control characters and embedded quotes
 * escaped using Rust escape syntax. Used to produce the
 * `format("{regexStr:?}")` literal that upstream emits into generated
 * code.
 */
internal fun rustDebugQuote(s: String): String = buildString {
    append('"')
    for (c in s) {
        val cp = c.code
        when {
            cp == 0x22 -> append("\\\"")
            cp == 0x5C -> append("\\\\")
            cp == 0x0A -> append("\\n")
            cp == 0x0D -> append("\\r")
            cp == 0x09 -> append("\\t")
            cp == 0x00 -> append("\\0")
            cp < 0x20 || cp == 0x7F -> append("\\x").append(cp.toString(16).padStart(2, '0'))
            // Mirror the upstream `<&str as Debug>::fmt` for non-ASCII Unicode:
            // characters outside the printable ASCII range are escaped
            // as `\u{HH..}`. Without this, the upstream-emitted regex
            // source for `r"\s"` (which contains raw NEL / NBSP / etc.
            // characters) round-trips through Debug as raw bytes
            // instead of `\u{85}\u{a0}...`, breaking byte parity.
            cp >= 0x7F -> append("\\u{").append(cp.toString(16)).append('}')
            else -> append(c)
        }
    }
    append('"')
}
