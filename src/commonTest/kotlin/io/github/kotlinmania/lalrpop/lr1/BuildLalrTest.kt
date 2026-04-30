// port-lint: source lr1/build_lalr/test.rs
package io.github.kotlinmania.lalrpop.lr1

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
import io.github.kotlinmania.lalrpop.expectDebug
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

private fun tokens(vararg xs: String): MutableList<TerminalString> =
    xs.mapTo(mutableListOf()) { TerminalString.quoted(Atom.from(it)) }

class BuildLalrTest {
    @Test
    fun figure923() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
                grammar;
                extern { enum Tok { "-" => .., "N" => .., "(" => .., ")" => .. } }
                S: () = E       => ();
                E: () = {
                    E "-" T     => (),
                    T           => ()
                };
                T: () = {
                    "N"         => (),
                    "(" E ")"   => ()
                };
                """.trimIndent(),
            )

            Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                try {
                    val states = buildLalrStates(grammar, nt("S"))
                    val tree = interpret(states, tokens("N", "-", "(", "N", "-", "N", ")")).getOrThrow()
                    expectDebug(
                        tree,
                        """[S: [E: [E: [T: "N"]], "-", [T: "(", [E: [E: [T: "N"]], "-", [T: "N"]], ")"]]]""",
                    )
                } finally {
                    lr1Tls.drop()
                }
            }
        }
    }
}
