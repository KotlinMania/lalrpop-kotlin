// port-lint: source src/lr1/trace/traceGraph/test.rs
package io.github.kotlinmania.lalrpop.lr1.trace.traceGraph

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
import io.github.kotlinmania.lalrpop.grammar.parseTree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.grammar.repr.ActionFn
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.SymbolSets
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test

private fun nt(name: String): NonterminalString = NonterminalString(Atom.from(name))

private fun syms(vararg names: String): MutableList<Symbol> =
    names.map { Symbol.Nonterminal(nt(it)) as Symbol }.toMutableList()

private fun production(name: String, vararg sym: String): Production = Production(
    nonterminal = nt(name),
    symbols = syms(*sym),
    action = ActionFn.new(0),
    span = Span(0, 0),
)

class TraceGraphTest {
    @Test
    fun enumerator() {
        Tls.test().use {
            // Build this graph:
            //
            //     X = X0 (*) X1
            //     ^
            //     |
            //   {X0}
            //     |
            // +-> X <-- Z = Z0 (*) X Z1
            // |
            // Y = Y0 (*) X Y1
            //
            // which enumerates out to:
            //
            //    [Y0 X0 (*) X1 Y1]
            //    [Z0 X0 (*) X1 Z1]

            val productions = listOf(
                production("X", "X0", "X1"),
                production("Y", "Y0", "X", "Y1"),
                production("Z", "Z0", "X", "Z1"),
            )

            val graph = TraceGraph.new()

            val item0 = Item.lr0(productions[0], 1) // X = X0 (*) X1
            graph.addEdge(TraceGraphNode.from(nt("X")), TraceGraphNode.from(item0), item0.symbolSets())

            val item1 = Item.lr0(productions[1], 1) // Y = Y0 (*) X Y1
            graph.addEdge(TraceGraphNode.from(item1), TraceGraphNode.from(nt("X")), item1.symbolSets())

            val item2 = Item.lr0(productions[2], 1) // Z = Z0 (*) X Z1
            graph.addEdge(TraceGraphNode.from(item2), TraceGraphNode.from(nt("X")), item2.symbolSets())

            val enumerator = graph.lr0Examples(Item.lr0(productions[0], 1))
            val list = enumerator.asSequence().map { it.paintUnstyled() }.toList()
            expectDebug(
                list,
                """
[
    [
        "  Z0 X0 X1 Z1",
        "  │  └─X─┘  │",
        "  └─Z───────┘"
    ],
    [
        "  Y0 X0 X1 Y1",
        "  │  └─X─┘  │",
        "  └─Y───────┘"
    ]
]
""".trim(),
            )
        }
    }

    @Test
    fun enumerator1() {
        Tls.test().use {
            // Build this graph:
            //
            //     W = W0 W1 (*)
            //     ^
            //  {W0,W1}
            //     |
            //     W
            //     ^
            //   {X0}
            //     |
            // +-> X <-- Z = Z0 (*) X Z1
            // |
            // Y = Y0 (*) X Y1
            //
            // which enumerates out to:
            //
            //    [Y0 X0 (*) X1 Y1]
            //    [Z0 X0 (*) X1 Z1]

            val productions = listOf(
                production("W", "W0", "W1"),
                production("X", "X0", "W", "X1"), // where X1 may be empty
                production("Y", "Y0", "X", "Y1"),
                production("Z", "Z0", "X", "Z1"),
            )

            val graph = TraceGraph.new()

            val item0 = Item.lr0(productions[0], 2) // W = W0 W1 (*)
            graph.addEdge(TraceGraphNode.from(nt("W")), TraceGraphNode.from(item0), item0.symbolSets())

            graph.addEdge(
                TraceGraphNode.from(nt("X")),
                TraceGraphNode.from(nt("W")),
                SymbolSets(
                    prefix = productions[1].symbols.subList(0, 1),
                    cursor = productions[1].symbols[1],
                    suffix = productions[1].symbols.subList(2, productions[1].symbols.size),
                ),
            )

            val item1 = Item.lr0(productions[2], 1)
            graph.addEdge(TraceGraphNode.from(item1), TraceGraphNode.from(nt("X")), item1.symbolSets())

            val item2 = Item.lr0(productions[3], 1)
            graph.addEdge(TraceGraphNode.from(item2), TraceGraphNode.from(nt("X")), item2.symbolSets())

            val enumerator = graph.lr0Examples(Item.lr0(productions[0], 2))
            val list = enumerator.asSequence().map { it.paintUnstyled() }.toList()
            expectDebug(
                list,
                """
[
    [
        "  Z0 X0 W0 W1 X1 Z1",
        "  │  │  └─W─┘  │  │",
        "  │  └─X───────┘  │",
        "  └─Z─────────────┘"
    ],
    [
        "  Y0 X0 W0 W1 X1 Y1",
        "  │  │  └─W─┘  │  │",
        "  │  └─X───────┘  │",
        "  └─Y─────────────┘"
    ]
]
""".trim(),
            )
        }
    }
}
