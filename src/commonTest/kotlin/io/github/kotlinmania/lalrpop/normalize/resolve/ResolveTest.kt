// port-lint: source normalize/resolve/test.rs
package io.github.kotlinmania.lalrpop.normalize.resolve

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

import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.normalize.NormErrorException
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private fun checkErr(expectedErr: String, grammar: String) {
    val expectedErrRegex = Regex(expectedErr)

    // the string will have a `>>>` and `<<<` in it, which serve to
    // indicate the span where an error is expected.
    val startIndex = grammar.indexOf(">>>")
    check(startIndex >= 0) { "missing `>>>` marker" }
    val endMarkerIndex = grammar.lastIndexOf("<<<")
    check(endMarkerIndex >= 0) { "missing `<<<` marker" }

    check(startIndex <= endMarkerIndex)

    // Strip marker sequences.
    val chars = grammar.toCharArray()
    val out = CharArray(chars.size)
    var o = 0
    var i = 0
    while (i < chars.size) {
        if (i + 2 < chars.size && chars[i] == '>' && chars[i + 1] == '>' && chars[i + 2] == '>') {
            i += 3
            continue
        }
        if (i + 2 < chars.size && chars[i] == '<' && chars[i + 1] == '<' && chars[i + 2] == '<') {
            i += 3
            continue
        }
        out[o] = chars[i]
        o += 1
        i += 1
    }
    val grammar2 = out.concatToString(0, o)

    val endIndex = endMarkerIndex - 3

    val parsedGrammar = parseGrammar(grammar2).getOrThrow()
    val err = try {
        resolve(parsedGrammar)
        fail("expected error for grammar")
    } catch (e: NormErrorException) {
        e.err
    }
    assertEquals(Span(startIndex, endIndex), err.span)
    check(expectedErrRegex.containsMatchIn(err.message)) {
        "unexpected error text `${err.message}`, did not match `$expectedErrRegex`"
    }
}

class ResolveTest {
    @Test
    fun unknownNonterminal() {
        checkErr("no definition found for `Y`", """grammar; X = X >>>Y<<<;""")
    }

    @Test
    fun unknownNonterminalInMacroArg() {
        checkErr(
            "no definition found for `Y`",
            """grammar; X = X Id<>>>Y<<<>; Id<T> = T;""",
        )
    }

    @Test
    fun unknownNonterminalInRepeatQuestion() {
        checkErr("no definition found for `Y`", """grammar; X = >>>Y<<<?;""")
    }

    @Test
    fun unknownNonterminalTwo() {
        checkErr(
            "no definition found for `Expr`",
            """grammar; Term = { <n:"Num"> => n.as_num(), "A" <>>>Expr<<<> "B" };""",
        )
    }

    @Test
    fun doubleNonterminal() {
        checkErr(
            "two nonterminals declared with the name `A`",
            """grammar; A = "Foo"; >>>A<<< = "Bar";""",
        )
    }

    @Test
    fun repeatedMacroArg() {
        checkErr(
            "multiple macro arguments declared with the name `Y`",
            """grammar; >>>X<Y,Y><<< = "foo";""",
        )
    }

    @Test
    fun overlappingTerminalAndNonterminal() {
        checkErr(
            "terminal and nonterminal both declared with the name `A`",
            """grammar; A = "Foo"; extern { enum Foo { >>>A => Foo::A(..) <<<} }""",
        )
    }
}
