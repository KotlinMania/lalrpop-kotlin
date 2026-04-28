// port-lint: source lr1/example/test.rs
package io.github.kotlinmania.lalrpop.lr1.example

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
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

private fun term(t: String): TerminalString = TerminalString.quoted(Atom.from(t))

private fun symNt(name: String): ExampleSymbol =
    ExampleSymbol.SymbolValue(Symbol.Nonterminal(nt(name)))

private val EPSILON: ExampleSymbol = ExampleSymbol.Epsilon

//  01234567890123456789012
//  A1   B2  C3  D4 E5 F6
//  |             |     |
//  +-LongLabel22-+     |
//  |                   |
//  +-Label-------------+
private fun longLabel1Example(): Example = Example(
    symbols = mutableListOf(symNt("A1"), symNt("B2"), symNt("C3"), symNt("D4"), symNt("E5"), symNt("F6")),
    cursor = 5,
    reductions = mutableListOf(
        Reduction(
            start = 0,
            end = 4,
            nonterminal = nt("LongLabel22"),
        ),
        Reduction(
            start = 0,
            end = 6,
            nonterminal = nt("Label"),
        ),
    ),
)

class ExampleTest {
    @Test
    fun longLabel1Positions() {
        Tls.test().use {
            val example = longLabel1Example()
            val lengths = example.lengths()
            val positions = example.positions(lengths)
            assertEquals(mutableListOf(0, 5, 9, 13, 16, 19, 22), positions)
        }
    }

    @Test
    fun longLabel1Strings() {
        Tls.test().use {
            val strings = longLabel1Example().paintUnstyled()
            expectDebug(
                strings,
                """
[
    "  A1   B2  C3  D4 E5 F6",
    "  ├─LongLabel22─┘     │",
    "  └─Label─────────────┘"
]
""".trim(),
            )
        }
    }

    // Example with some empty sequences and
    // other edge cases.
    //
    //  012345678901234567890123456789012345
    //         A1  B2  C3 D4 E5       F6
    //  |   |           |       |   | |   |
    //  +-X-+           |       |   | |   |
    //  |               |       |   | |   |
    //  +-MegaLongLabel-+       |   | |   |
    //                          |   | |   |
    //                          +-Y-+ |   |
    //                                |   |
    //                                +-Z-+
    private fun emptyLabelsExample(): Example = Example(
        //                       0    1            2            3            4            5            6        7
        symbols = mutableListOf(EPSILON, symNt("A1"), symNt("B2"), symNt("C3"), symNt("D4"), symNt("E5"), EPSILON, symNt("F6")),
        cursor = 5,
        reductions = mutableListOf(
            Reduction(
                start = 0,
                end = 1,
                nonterminal = nt("X"),
            ),
            Reduction(
                start = 0,
                end = 4,
                nonterminal = nt("MegaLongLabel"),
            ),
            Reduction(
                start = 6,
                end = 7,
                nonterminal = nt("Y"),
            ),
            Reduction(
                start = 7,
                end = 8,
                nonterminal = nt("Z"),
            ),
        ),
    )

    @Test
    fun emptyLabelsPositions() {
        Tls.test().use {
            val example = emptyLabelsExample()
            val lengths = example.lengths()
            val positions = example.positions(lengths)
            //                                          A1  B2  C3  D4  E5      F6
            assertEquals(mutableListOf(0, 7, 11, 15, 18, 21, 24, 30, 36), positions)
        }
    }

    @Test
    fun emptyLabelsStrings() {
        Tls.test().use {
            val strings = emptyLabelsExample().paintUnstyled()
            expectDebug(
                strings,
                """
[
    "  ╷    ╷ A1  B2  C3 D4 E5 ╷   ╷ F6  ╷",
    "  ├─X──┘          │       │   │ │   │",
    "  └─MegaLongLabel─┘       │   │ │   │",
    "                          └─Y─┘ │   │",
    "                                └─Z─┘"
]
""".trim(),
            )
        }
    }

    // _return_      _A_ Expression _B_
    // |            |                  |
    // +-ExprAtom---+                  |
    // |            |                  |
    // +-ExprSuffix-+                  |
    // |                               |
    // +-ExprSuffix--------------------+
    private fun singleTokenExample(): Example = Example(
        //                       0           1          2                 3
        symbols = mutableListOf(symNt("_return_"), symNt("_A_"), symNt("Expression"), symNt("_B_")),
        cursor = 5,
        reductions = mutableListOf(
            Reduction(
                start = 0,
                end = 1,
                nonterminal = nt("ExprAtom"),
            ),
            Reduction(
                start = 0,
                end = 1,
                nonterminal = nt("ExprSuffix"),
            ),
            Reduction(
                start = 0,
                end = 4,
                nonterminal = nt("ExprSuffix"),
            ),
        ),
    )

    @Test
    fun singleTokenStrings() {
        Tls.test().use {
            val strings = singleTokenExample().paintUnstyled()
            expectDebug(
                strings,
                """
[
    "  _return_     ╷ _A_ Expression _B_",
    "  ├─ExprAtom───┤                  │",
    "  ├─ExprSuffix─┘                  │",
    "  └─ExprSuffix────────────────────┘"
]
""".trim(),
            )
        }
    }
}
