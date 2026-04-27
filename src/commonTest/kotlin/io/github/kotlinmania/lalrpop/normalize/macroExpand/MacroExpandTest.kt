// port-lint: source src/normalize/macroExpand/test.rs
package io.github.kotlinmania.lalrpop.normalize.macroExpand

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

import io.github.kotlinmania.lalrpop.compare
import io.github.kotlinmania.lalrpop.normalize.NormErrorException
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import kotlin.test.Test
import kotlin.test.fail

class MacroExpandTest {
    @Test
    fun testComma() {
        val grammar = parseGrammar(
            """
grammar;
    Comma<E>: Vec<E> =
       <v:(<E> ",")*> <e:E?> =>
           v.into_iter().chain(e.into_iter()).collect();

    Ids = Comma<"Id">;
""",
        ).getOrThrow()

        val actual = expandMacros(grammar, 20)

        val expected = parseGrammar(
            """
grammar;
    Ids = `Comma<"Id">`;

    `Comma<"Id">`: Vec<#"Id"#> =
        <v:`(<"Id"> ",")*`> <e:`"Id"?`> => v.into_iter().chain(e.into_iter()).collect();

    #[inline]
    `"Id"?`: Option<#"Id"#> = {
        "Id" => Some(<>),
        => None
    };

    #[inline]
    `(<"Id"> ",")*`: alloc::vec::Vec<#`(<"Id"> ",")`#> = {
        => alloc::vec![],
        <v:`(<"Id"> ",")+`> => v,
    };

    #[inline]
    `(<"Id"> ",")`: #"Id"# = {
        <"Id"> "," => <>,
    };

    `(<"Id"> ",")+`: alloc::vec::Vec<#`(<"Id"> ",")`#> = {
        `(<"Id"> ",")` => alloc::vec![<>],
        <v:`(<"Id"> ",")+`> <e:`(<"Id"> ",")`> => { let mut v = v; v.push(e); v },
    };
""",
        ).getOrThrow()

        compare(actual, expected)
    }

    @Test
    fun testIfMatch() {
        val grammar = parseGrammar(
            """
grammar;
    Expr<E> = {
       "A" if E == "A*C",
       "B" if E ~~ "^A*C${'$'}",
       "C" if E != "A*C",
       "D" if E !~ "^A*C${'$'}"
    };

    Expr1 = Expr<"A*C">;
    Expr2 = Expr<"AAC">;
    Expr3 = Expr<"ABC">;
""",
        ).getOrThrow()

        val actual = expandMacros(grammar, 20)

        val expected = parseGrammar(
            """
grammar;
    Expr1 = `Expr<"A*C">`;
    Expr2 = `Expr<"AAC">`;
    Expr3 = `Expr<"ABC">`;

    `Expr<"ABC">` = { "C", "D" };
    `Expr<"AAC">` = { "B", "C" };
    `Expr<"A*C">` = { "A", "D" };
""",
        ).getOrThrow()

        compare(actual, expected)
    }

    @Test
    fun testLookahead() {
        val grammar = parseGrammar(
            """
        grammar;
        Expr = @L;
""",
        ).getOrThrow()

        val actual = expandMacros(grammar, 20)

        val expected = parseGrammar(
            """
        grammar;
        Expr = `@L`;
        #[inline] `@L` = =>@L;
""",
        ).getOrThrow()

        compare(actual, expected)
    }

    @Test
    fun testExcessiveRecursion() {
        val grammar = parseGrammar(
            """
        grammar;
        A<I> = { "x" I "y" I "z", A<("." I)> }
        pub P = A<()>;
        """,
        ).getOrThrow()

        try {
            expandMacros(grammar, 20)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }

        // the upstream `grammar2.clone()` is a deep clone; in Kotlin we re-parse to
        // get a fresh tree because the parse-tree types share mutable inner
        // state and `data class.copy()` is only shallow.
        val grammar2Source = """
         grammar;
         A<I> = { "a" B<("." I)> };
         B<I> = { "b" C<("," I)> };
         C<I> = { "c" I };
         pub D = A<"d"> B<"d"> C<"d">;
         """

        try {
            expandMacros(parseGrammar(grammar2Source).getOrThrow(), 2)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }

        // expandMacros(grammar2, 3).isOk()
        expandMacros(parseGrammar(grammar2Source).getOrThrow(), 3)
    }
}
