// port-lint: source grammar/free_variables/test.rs
package io.github.kotlinmania.lalrpop.grammar

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

import io.github.kotlinmania.lalrpop.expectDebug
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test

class FreeVariablesTest {
    @Test
    fun otherNames() {
        // Check that `Foo` does not end up in the list of free variables.
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar<'a, T>(x: &'a mut Foo, y: Vec<T>);

pub Foo: () = ();
""",
            )

            val p0 = grammar.parameters[0]
            expectDebug(
                p0.ty.freeVariables(grammar.typeParameters),
                """[
    Lifetime(
        'a
    )
]""",
            )

            val p1 = grammar.parameters[1]
            expectDebug(
                p1.ty.freeVariables(grammar.typeParameters),
                """[
    Id(
        Atom('T' type=inline)
    )
]""",
            )
        }
    }
}
