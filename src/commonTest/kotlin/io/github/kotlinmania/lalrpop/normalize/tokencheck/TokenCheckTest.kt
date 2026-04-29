// port-lint: source normalize/token_check/test.rs
package io.github.kotlinmania.lalrpop.normalize.tokencheck

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

import io.github.kotlinmania.lalrpop.checkNormErr
import io.github.kotlinmania.lalrpop.grammar.parsetree.Grammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.MatchMapping
import io.github.kotlinmania.lalrpop.grammar.parsetree.internToken
import io.github.kotlinmania.lalrpop.lexer.dfa.interpret
import io.github.kotlinmania.lalrpop.normalize.NormErrorException
import io.github.kotlinmania.lalrpop.normalize.resolve.resolve
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import kotlin.test.Test
import kotlin.test.fail

private fun validateGrammar(grammar: String): Grammar {
    val parsedGrammar = parseGrammar(grammar).getOrElse { error("parse grammar") }
    val resolved = resolve(parsedGrammar)
    return validate(resolved)
}

private fun checkErr(expectedErr: String, grammar: String, span: String) {
    val err = try {
        validateGrammar(grammar)
        fail("expected error for grammar")
    } catch (e: NormErrorException) {
        e.err
    }
    checkNormErr(expectedErr, span, err)
}

private fun checkInternToken(grammar: String, expectedTokens: List<Pair<String, String>>) {
    val parsedGrammar = validateGrammar(grammar)
    val internToken = parsedGrammar.internToken() ?: error("intern_token")
    println("intern_token: $internToken")
    for ((input, expectedUserName) in expectedTokens) {
        val actualUserName = interpret(internToken.dfa, input)?.let { (index, text) ->
            val userName = internToken.matchEntries[index.index()].userName
            userName to text
        }
        val actualUserNameStr = formatRustDebug(actualUserName)
        if (expectedUserName != actualUserNameStr) {
            error(
                "input `$input` matched `$actualUserNameStr` but we expected `$expectedUserName`",
            )
        }
    }
}

/**
 * Mirror the upstream `format("{:?}", actualUserName)` for an
 * `Option<(&MatchMapping, &str)>`. The `MatchMapping::Terminal` `Debug`
 * implementation prints the underlying terminal literal (already quoted for
 * `"foo"` or `r#"foo"#`); the `&str` half is rendered with surrounding
 * double quotes the way the upstream `{:?}` does for strings.
 */
private fun formatRustDebug(actual: Pair<MatchMapping, String>?): String {
    if (actual == null) return "None"
    val (userName, text) = actual
    return "Some(($userName, \"$text\"))"
}

class TokenCheckTest {
    @Test
    fun unknownTerminal() {
        checkErr(
            """terminal `"\+"` does not have a pattern defined for it""",
            """grammar; extern { enum Term { } } X = X "+";""",
            """                                        ~~~ """,
        )
    }

    @Test
    fun unknownIdTerminal() {
        checkErr(
            """terminal `"foo"` does not have a pattern defined for it""",
            """grammar; extern { enum Term { } } X = X "foo";""",
            """                                        ~~~~~ """,
        )
    }

    @Test
    fun tickInputLifetimeAlreadyDeclared() {
        checkErr(
            """.*the `'input` lifetime is implicit and cannot be declared""",
            """grammar<'input>; X = X "foo";""",
            """~~~~~~~                      """,
        )
    }

    @Test
    fun inputParameterAlreadyDeclared() {
        checkErr(
            """.*the `input` parameter is implicit and cannot be declared""",
            """grammar(input:u32); X = X "foo";""",
            """~~~~~~~                         """,
        )
    }

    @Test
    fun invalidRegularExpressionUnterminatedGroup() {
        checkErr(
            """unclosed group""",
            """grammar; X = X r"(123";""",
            """               ~~~~~~~ """,
        )
    }

    @Test
    fun quotedLiterals() {
        checkInternToken(
            """grammar; X = X "+" "-" "foo" "(" ")";""",
            listOf(
                "+" to """Some(("+", "+"))""",
                "-" to """Some(("-", "-"))""",
                "(" to """Some(("(", "("))""",
                ")" to """Some((")", ")"))""",
                "foo" to """Some(("foo", "foo"))""",
                "<" to """None""",
            ),
        )
    }

    @Test
    fun regexLiterals() {
        checkInternToken(
            """grammar; X = X r"[a-z]+" r"[0-9]+";""",
            listOf(
                "a" to """Some((r#"[a-z]+"#, "a"))""",
                "def" to """Some((r#"[a-z]+"#, "def"))""",
                "1" to """Some((r#"[0-9]+"#, "1"))""",
                "9123456" to """Some((r#"[0-9]+"#, "9123456"))""",
            ),
        )
    }

    /** Basic test for match mappings. */
    // This test requires regex unicode case support
    // (cfgAttr(not(feature = "unicode"), ignore))
    @Test
    fun matchMappings() {
        checkInternToken(
            """grammar; match { r"(?i)begin" => "BEGIN" } else { "abc" => ALPHA } X = "BEGIN" ALPHA;""",
            listOf(
                "BEGIN" to """Some(("BEGIN", "BEGIN"))""",
                "begin" to """Some(("BEGIN", "begin"))""",
                "abc" to """Some((ALPHA, "abc"))""",
            ),
        )
    }

    /**
     * Match mappings, exercising precedence. Here the ID regex *would*
     * be ambiguous with the begin regex.
     */
    // This test requires regex unicode case support
    // (cfgAttr(not(feature = "unicode"), ignore))
    @Test
    fun matchPrecedence() {
        checkInternToken(
            """grammar; match { r"(?i)begin" => "BEGIN" } else { r"\w+" => ID } X = ();""",
            listOf(
                "BEGIN" to """Some(("BEGIN", "BEGIN"))""",
                "begin" to """Some(("BEGIN", "begin"))""",
                "abc" to """Some((ID, "abc"))""",
            ),
        )
    }

    /** Test that, without a `catch-all`, using unrecognized literals is an error. */
    @Test
    fun invalidMatchLiteral() {
        checkErr(
            """terminal `"foo"` does not have a match mapping defined for it""",
            """grammar; match { r"(?i)begin" => "BEGIN" } X = "foo";""",
            """                                               ~~~~~ """,
        )
    }

    /** Test that, without a `catch-all`, using unrecognized literals is an error. */
    @Test
    fun invalidMatchRegexLiteral() {
        checkErr(
            """terminal `r#"foo"#` does not have a match mapping defined for it""",
            """grammar; match { r"(?i)begin" => "BEGIN" } X = r"foo";""",
            """                                               ~~~~~~ """,
        )
    }

    /** Test that, with a catch-all, the previous two examples work. */
    // This test requires regex unicode case support
    // (cfgAttr(not(feature = "unicode"), ignore))
    @Test
    fun matchCatchAll() {
        val grammar = """grammar; match { r"(?i)begin" => "BEGIN", _ } X = { "foo", r"foo" };"""
        // assert(validateGrammar(grammar).isOk())
        validateGrammar(grammar)
    }

    /**
     * Test that a `catch-all` can be import in the first `match` arm.
     * Before the pull request to close [issue 325](https://github.com/lalrpop/lalrpop/issues/325),
     * the usage of the `catch-all` symbol was not allowed in the first arm of a `match` block.
     */
    @Test
    fun matchCatchAllInFirstArm() {
        val grammar = """
        grammar;
        match {
            r"[a-z]",
            _
        } else {
            r"[[:word:]]+"
        }
        pub Term = {
            Num,
            "(" <Term> ")",
            r"[[:word:]]+" => format!("Id({})", <>),
        };
        Num: String = r"[0-9]+" => <>.to_string();
"""
        validateGrammar(grammar)
        checkInternToken(
            grammar,
            listOf(
                "x" to """Some((r#"[a-z]"#, "x"))""",
                "xy" to """Some((r#"[[:word:]]+"#, "xy"))""",
            ),
        )
    }

    // This test requires regex unicode case support
    // (cfgAttr(not(feature = "unicode"), ignore))
    @Test
    fun complexMatch() {
        val grammar = """
        grammar;
        match {
            "abc"        => "ABC",
            r"(?i)begin" => BEGIN
        }

        pub Query: String = {
            "ABC" BEGIN => String::from("Success")
        };
"""
        // assert(validateGrammar(grammar).isOk())
        validateGrammar(grammar)
    }

    /**
     * Test that overlapping regular expressions are still forbidden within one level
     * of a match declaration.
     */
    // This test requires regex unicode case support
    // (cfgAttr(not(feature = "unicode"), ignore))
    @Test
    fun ambiguityWithinMatch() {
        checkErr(
            """ambiguity detected between the terminal `r#"b"#` and the terminal `r#"\(\?i\)b"#`""",
            """grammar; match { r"(?i)b" => "B", r"b" => "b" }""",
            """                                  ~~~~~~~~~~~~ """,
        )
    }

    /**
     * Test that using the **exact same regular expression** twice is
     * forbidden, even across multiple levels of the match expression.
     * No good reason to do that.
     */
    @Test
    fun sameLiteralTwice() {
        checkErr(
            """multiple match entries for `r#"\[bB\]"#`""",
            """grammar; match { r"[bB]" => "B" } else { r"[bB]" => "b" }""",
            """                                         ~~~~~~~~~~~~~~~ """,
        )
    }
}
