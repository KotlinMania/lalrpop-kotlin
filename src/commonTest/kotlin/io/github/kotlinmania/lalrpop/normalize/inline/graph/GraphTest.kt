// port-lint: source src/normalize/inline/graph/test.rs
package io.github.kotlinmania.lalrpop.normalize.inline.graph

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
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.normalize.NormErrorException
import io.github.kotlinmania.lalrpop.normalize.lowerHelper
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class GraphTest {
    @Test
    fun testInlineSelfCycle() {
        val grammar = parseGrammar(
            """
    grammar;
    extern { }
    #[inline] A: () = A;
""",
        ).getOrThrow()
        val lowered = lowerHelper(Session.test(), grammar, true)
        try {
            inlineOrder(lowered)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }
    }

    @Test
    fun testInlineCycle3() {
        val grammar = parseGrammar(
            """
    grammar;
    extern { }
    #[inline] A: () = B;
    #[inline] B: () = C;
    #[inline] C: () = A;
""",
        ).getOrThrow()
        val lowered = lowerHelper(Session.test(), grammar, true)
        try {
            inlineOrder(lowered)
            fail("expected error")
        } catch (_: NormErrorException) {
            // ok
        }
    }

    @Test
    fun testInlineOrder() {
        // because C references A, we inline A first.
        val grammar = parseGrammar(
            """
    grammar;
    extern { }
    #[inline] A: () = B;
    B: () = C;
    #[inline] C: () = A;
""",
        ).getOrThrow()
        val lowered = lowerHelper(Session.test(), grammar, true)
        val a = NonterminalString(Atom.from("A"))
        val c = NonterminalString(Atom.from("C"))
        assertEquals(listOf(a, c), inlineOrder(lowered))
    }
}
