// port-lint: source src/normalize/inline/test.rs
package io.github.kotlinmania.lalrpop.normalize.inline

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
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.normalize.lowerHelper
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun inlinedGrammar(text: String): Grammar {
    val g = parseGrammar(text).getOrThrow()
    val lowered = lowerHelper(Session.test(), g, true)
    return inline(lowered)
}

class InlineTest {
    @Test
    fun sri() {
        // This grammar gets a shift-reduce conflict because if the input
        // is "&" (*) "L", then we see two possibilities, and we must decide
        // between them:
        //
        // "&" (*) "L" E
        //  |       |  |
        //  +-------+--|
        //          |
        //          E
        //
        // or
        //
        // "&"      (*) "L"
        //  |            |
        //  |  OPT_L     E
        //  |   |        |
        //  +---+---+----+
        //          |
        //          E
        //
        // to some extent this may be a false conflict, in that inlined
        // rules would address it, but it's an interesting one for
        // producing a useful error message.

        val grammar = inlinedGrammar(
            """
        grammar;

        E: () = {
            "L",
            "&" OPT_L E
        };

        #[inline] OPT_L: () = {
            (),
            "L"
        };
    """,
        )

        val nt = NonterminalString(Atom.from("E"))

        // After inlining, we expect:
        //
        // E = "L"
        // E = "&" E
        // E = "&" "L" E
        //
        // Note that the `()` also gets inlined.
        val eProductions = grammar.productionsFor(nt)
        assertEquals(3, eProductions.size)
        assertEquals("""["L"]""", "${eProductions[0].symbols}")
        assertEquals("""["&", E]""", "${eProductions[1].symbols}")
        assertEquals("""["&", "L", E]""", "${eProductions[2].symbols}")
    }

    @Test
    fun issue55() {
        val grammar = inlinedGrammar(
            """
grammar;

pub E: () = {
    "X" "{" <a:AT*> <e:ET> <b:AT*> "}" => ()
};

AT: () = {
    "type" ";" => ()
};

ET: () = {
    "enum" "{" "}" => ()
};
    """,
        )
        val nt = NonterminalString(Atom.from("E"))

        // The problem in issue #55 was that we would inline both `AT*`
        // the same way, so we ended up with `E = X { ET }` and `E = X {
        // AT+ ET AT+ }` but not `E = X { AT+ ET }` or `E = X { ET AT+ }`.
        assertTrue(grammar.productionsFor(nt).size == 4)
    }
}
