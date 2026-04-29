// port-lint: source lr1/trace/shift/test.rs
package io.github.kotlinmania.lalrpop.lr1.trace.shift

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
import io.github.kotlinmania.lalrpop.lr1.TableConstructionErrorException
import io.github.kotlinmania.lalrpop.lr1.buildStates
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.TableConstructionError<TokenSet>
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.tls.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.trace.Tracer
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertTrue

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

class ShiftTest {
    @Test
    fun shiftBacktrace1() {
        // This grammar yields a S/R conflict. Is it `(int -> int) -> int`
        // or `int -> (int -> int)`?

        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;
pub Ty: () = {
    "int" => (),
    "bool" => (),
    <t1:Ty> "->" <t2:Ty> => (),
};
""",
            )
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val firstSets = FirstSets.new(grammar)
                val err: TableConstructionError<TokenSet> = try {
                    buildStates(grammar, nt("Ty"))
                    error("expected build_states to fail")
                } catch (e: TableConstructionErrorException) {
                    @Suppress("UNCHECKED_CAST")
                    e.inner as TableConstructionError<TokenSet>
                }
                val conflict = err.conflicts[0]
                println("conflict=$conflict")

                // Gin up the LR0 item involved in the shift/reduce conflict:
                //
                //     Ty = Ty (*) -> Ty (shift)
                //
                // from the item that we can reduce:
                //
                //     Ty = Ty -> Ty (*) (reduce)

                assertTrue(conflict.production.symbols.size == 3)
                val item = Item.lr0(conflict.production, 1)
                println("item=$item")
                val tracer = Tracer.new(firstSets, err.states)
                val graph = tracer.backtraceShift(conflict.state, item)
                expectDebug(
                    graph,
                    """
[
    (Nonterminal(Ty) -([], Some(Ty), ["->", Ty])-> Nonterminal(Ty)),
    (Nonterminal(Ty) -([Ty], Some("->"), [Ty])-> Item(Ty = Ty (*) "->" Ty)),
    (Item(Ty = Ty "->" (*) Ty) -([Ty, "->"], Some(Ty), [])-> Nonterminal(Ty))
]
""".trim(),
                )

                val list = graph.lr0Examples(item).asSequence().map { it.paintUnstyled() }.toList()
                expectDebug(
                    list,
                    """
[
    [
        "  Ty "->" Ty "->" Ty",
        "  │       └─Ty─────┤",
        "  └─Ty─────────────┘"
    ]
]
""".trim(),
                )
            } finally {
                lr1Tls.drop()
            }
        }
    }
}
