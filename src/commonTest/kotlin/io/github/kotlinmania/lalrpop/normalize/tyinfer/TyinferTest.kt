// port-lint: source normalize/tyinfer/test.rs
package io.github.kotlinmania.lalrpop.normalize.tyinfer

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

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.TypeRepr
import io.github.kotlinmania.lalrpop.normalize.NormErrorException
import io.github.kotlinmania.lalrpop.normalize.macroExpand.expandMacros
import io.github.kotlinmania.lalrpop.normalize.tokenCheck.validate as tokenCheckValidate
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import io.github.kotlinmania.lalrpop.parser.parseTypeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private fun typeRepr(s: String): TypeRepr {
    val typeRef = parseTypeRef(s).getOrThrow()
    return typeRef.typeRepr()
}

private fun compare(g1: String, expected: List<Pair<String, String>>) {
    val parsed = parseGrammar(g1).getOrThrow()
    val expanded = expandMacros(parsed, 20)
    val grammar = tokenCheckValidate(expanded)
    val types = inferTypes(grammar)

    println("types table: $types")

    for ((ntId, ntType) in expected) {
        val id = NonterminalString(Atom.from(ntId))
        val ty = typeRepr(ntType)
        println("expected type of $id is $ty")
        assertEquals(ty, types.nonterminalType(id))
    }
}

class TyinferTest {
    @Test
    fun testPairsAndTokens() {
        compare(
            """
grammar;
    extern { enum Tok { "Hi" => Hi(..), "Ho" => Ho(..) } }
    X = Y Z;
    Y: Foo = "Hi";
    Z = "Ho";
""",
            listOf("X" to "(Foo, Tok)", "Y" to "Foo", "Z" to "Tok"),
        )
    }

    @Test
    fun testCycleDirect() {
        val grammar = parseGrammar(
            """
grammar;
    extern { enum Tok { "Hi" => Hi(..), "Ho" => Ho(..) } }
    X = {
        X Y,
        <Y> => vec![<>]
    };
    Y = "Hi";
""",
        ).getOrThrow()

        val actual = expandMacros(grammar, 20)
        try {
            inferTypes(actual)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }
    }

    @Test
    fun testCycleIndirect() {
        val grammar = parseGrammar(
            """
grammar;
    extern { enum Tok { } }
    A = B;
    B = C;
    C = D;
    D = A;
""",
        ).getOrThrow()

        val actual = expandMacros(grammar, 20)
        try {
            inferTypes(actual)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }
    }

    @Test
    fun testMacroExpansion() {
        compare(
            """
grammar;
    extern { enum Tok { "Id" => Id(..) } }
    Two<X>: (X, X) = X X;
    Ids = Two<"Id">;
""",
            listOf("Ids" to "(Tok, Tok)", """Two<"Id">""" to "(Tok, Tok)"),
        )
    }

    @Test
    fun testMacroExpansionInfer() {
        compare(
            """
grammar;
    extern { enum Tok { "Id" => Id(..) } }
    Two<X> = X X;
    Ids = Two<"Id">;
""",
            listOf("Ids" to "(Tok, Tok)", """Two<"Id">""" to "(Tok, Tok)"),
        )
    }

    @Test
    fun testTypeQuestion() {
        compare(
            """
grammar;
    extern { enum Tok { "Hi" => Hi(..) } }
    X = Y?;
    Y = "Hi";
""",
            listOf("X" to "Option<Tok>", "Y" to "Tok"),
        )
    }

    @Test
    fun testStarPlusQuestion() {
        compare(
            """
grammar;
    extern { enum Tok { "Hi" => Hi(..) } }
    A = Z*;
    X = "Hi"*;
    Y = "Hi"+;
    Z = "Hi"?;
""",
            listOf(
                "A" to "alloc::vec::Vec<Option<Tok>>",
                "X" to "alloc::vec::Vec<Tok>",
                "Y" to "alloc::vec::Vec<Tok>",
                "Z" to "Option<Tok>",
            ),
        )
    }

    @Test
    fun testLookahead() {
        compare(
            """
grammar;
    extern { type Location = usize; enum Tok { } }
    A = @L;
""",
            listOf("A" to "usize"),
        )
    }

    @Test
    fun testSpannedMacro() {
        compare(
            """
        grammar;
        extern { type Location = usize; enum Tok { "Foo" => Foo(..) } }
        A = Spanned<"Foo">;
        Spanned<T> = {
            @L T @R
        };
""",
            listOf("A" to "(usize, Tok, usize)"),
        )
    }

    @Test
    fun testAction() {
        compare(
            """
grammar;
    extern { enum Tok { "+" => .., "foo" => .. } }

    X = {
        Y,
        <l:X> "+" <r:Y> => l + r
    };

    Y: i32 = "foo" => 22;
""",
            listOf("X" to "i32", "Y" to "i32"),
        )
    }

    @Test
    fun testInconsistentAction() {
        val grammar = parseGrammar(
            """
grammar;
    extern { enum Tok { "+" => .., "foo" => .., "bar" => .. } }

    X = {
        Y,
        Z,
        <l:X> "+" <r:Y> => l + r
    };

    Y: i32 = "foo" => 22;

    Z: u32 = "bar" => 22;
""",
        ).getOrThrow()

        val actual = expandMacros(grammar, 20)
        try {
            inferTypes(actual)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }
    }

    @Test
    fun customToken() {
        compare(
            """
grammar;
extern { enum Tok { N => N(<u32>) } }
A = N;
""",
            listOf("A" to "u32"),
        )
    }

    @Test
    fun internToken() {
        compare(
            """
grammar;
    Z = @L "Ho" @R;
""",
            listOf("Z" to """(usize, &'input str, usize)"""),
        )
    }

    @Test
    fun error() {
        compare(
            """
grammar;
    Z = !;
""",
            listOf(
                "Z" to "__lalrpop_util::ErrorRecovery<usize, Token<'input>, &'static str>",
            ),
        )
    }

    @Test
    fun testTupleMismatch() {
        val grammar = parseGrammar(
            """
grammar;
    extern { enum Tok { "a" => .., "b" => .., "c" => .. } }
    Foo: String = {
        <(a, b):Bar> => a + b,
        <(a, (b, c)):Bam> => a + b + c
    };
    Bar = "a" "b" "c";
    Bam = "b" Bar;
""",
        ).getOrThrow()

        val actual = expandMacros(grammar, 20)
        try {
            inferTypes(actual)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }
    }
}
