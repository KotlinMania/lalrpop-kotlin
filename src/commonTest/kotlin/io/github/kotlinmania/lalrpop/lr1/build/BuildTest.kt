// port-lint: source lr1/build/test.rs
package io.github.kotlinmania.lalrpop.lr1.build

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
import io.github.kotlinmania.lalrpop.ChaCha20Rng
import io.github.kotlinmania.lalrpop.compare
import io.github.kotlinmania.lalrpop.expectDebug
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.core.Items
import io.github.kotlinmania.lalrpop.lr1.core.State
import io.github.kotlinmania.lalrpop.lr1.interpret.interpret
import io.github.kotlinmania.lalrpop.lr1.lookahead.Token
import io.github.kotlinmania.lalrpop.lr1.lookahead.TokenSet
import io.github.kotlinmania.lalrpop.lr1.tls.Lr1Tls
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.randomParseTree
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

private const val ITERATIONS: Int = 96

/**
 * Stand-in for the upstream `ChaCha20Rng::seedFromU64(0)`. The test only
 * requires a deterministic source of `randomRange`; we import Kotlin
 * `Random` seeded with `0` for reproducibility within a single run.
 */
private class SeededRng(seed: Long) : ChaCha20Rng {
    private val rng: Random = Random(seed)
    override fun randomRange(rangeEnd: Int): Int = rng.nextInt(rangeEnd)
}

private fun randomTest(grammar: Grammar, states: List<State<TokenSet>>, startSymbol: NonterminalString) {
    val rng: ChaCha20Rng = SeededRng(0L)

    for (i in 0 until ITERATIONS) {
        val inputTree = randomParseTree(grammar, startSymbol, rng)
        val outputTree = interpret(states, inputTree.terminals())

        println("test $i")
        println("input_tree = $inputTree")
        println("output_tree = $outputTree")

        compare(outputTree, inputTree)
    }
}

private fun tokens(vararg t: String): MutableList<TerminalString> =
    t.map { TerminalString.quoted(Atom.from(it)) }.toMutableList()

private fun items(grammar: Grammar, nonterminal: String, index: Int, la: Token): Items<TokenSet> {
    val set = TokenSet.from(la)
    val lr1: Lr<TokenSet> = Lr.new(grammar, nt(nonterminal), set.clone())
    return lr1.transitiveClosure(lr1.items(nt(nonterminal), index, set))
}

class BuildTest {
    @Test
    fun startState() {
        val grammar = normalizedGrammar(
            """
grammar;
    extern { enum Tok { "C" => .., "D" => .. } }
    A = B "C";
    B: Option<u32> = {
        "D" => Some(1),
        () => None
    };
""",
        )
        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            val items = items(grammar, "A", 0, Token.Eof)
            expectDebug(
                items.vec,
                """[
    A = (*) B "C" [Eof],
    B = (*) ["C"],
    B = (*) "D" ["C"]
]""",
            )
        } finally {
            lr1Tls.drop()
        }
    }

    @Test
    fun startState1() {
        val grammar = normalizedGrammar(
            """
grammar;
extern { enum Tok { "B1" => .., "C1" => .. } }
A = B C;
B: Option<u32> = {
    "B1" => Some(1),
    () => None
};
C: Option<u32> = {
    "C1" => Some(1),
    () => None
};
""",
        )

        val lr1Tls = Lr1Tls.install(grammar.terminals)
        try {
            expectDebug(
                items(grammar, "A", 0, Token.Eof).vec,
                """[
    A = (*) B C [Eof],
    B = (*) ["C1", Eof],
    B = (*) "B1" ["C1", Eof]
]""",
            )

            expectDebug(
                items(grammar, "A", 1, Token.Eof).vec,
                """[
    A = B (*) C [Eof],
    C = (*) [Eof],
    C = (*) "C1" [Eof]
]""",
            )
        } finally {
            lr1Tls.drop()
        }
    }

    @Test
    fun exprGrammar1() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;
    extern { enum Tok { "-" => .., "N" => .., "(" => .., ")" => .. } }

    S: () =
        E => ();

    E: () = {
        E "-" T => (),
        T => ()
    };

    T: () = {
        "N" => (),
        "(" E ")" => ()
    };
""",
            )

            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                // for now, just test that process does not result in an error
                // and yields expected number of states.
                val states = buildLr1States(grammar, nt("S"))
                println("$states")
                assertEquals(if (useLaneTable()) 9 else 16, states.size)

                // execute it on some sample inputs.
                val tree = interpret(states, tokens("N", "-", "(", "N", "-", "N", ")"))
                assertEquals(
                    """[S: [E: [E: [T: "N"]], "-", [T: "(", [E: [E: [T: "N"]], "-", [T: "N"]], ")"]]]""",
                    "$tree",
                )

                // incomplete:
                assertFails { interpret(states, tokens("N", "-", "(", "N", "-", "N")) }

                // incomplete:
                assertFails { interpret(states, tokens("N", "-")) }

                // unexpected character:
                assertFails { interpret(states, tokens("N", "-", ")", "N", "-", "N", "(")) }

                // parens first:
                val tree2 = interpret(states, tokens("(", "N", "-", "N", ")", "-", "N"))
                println("$tree2")
                assertEquals(
                    """[S: [E: [E: [T: "(", [E: [E: [T: "N"]], "-", [T: "N"]], ")"]], "-", [T: "N"]]]""",
                    "$tree2",
                )

                // run some random tests
                randomTest(grammar, states, nt("S"))
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun shiftReduceConflict1() {
        Tls.test().use {
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
            // rules would address it, but it an interesting one for
            // producing a useful error message.

            val grammar = normalizedGrammar(
                """
        grammar;
        extern { enum Tok { "L" => .., "&" => .., } }
        E: () = {
            "L",
            "&" OPT_L E
        };
        OPT_L: () = {
            (),
            "L"
        };
    """,
            )

            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                assertFails { buildLr1States(grammar, nt("E")) }
            } finally {
                lr1Tls.drop()
            }
        }
    }

    /** One of the few grammars that IS LR(0). */
    @Test
    fun lr0ExprGrammarWithExplicitEof() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;

S: () = E "${'$'}";

E: () = {
    E "-" T,
    T,
};

T: () = {
    "N",
    "(" E ")",
};
""",
            )

            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                // for now, just test that process does not result in an error
                // and yields expected number of states.
                val states = buildLr0States(grammar, nt("S"))
                assertEquals(10, states.size)
            } finally {
                lr1Tls.drop()
            }
        }
    }

    /** Without the artificial '$', grammar is not LR(0). */
    @Test
    fun lr0ExprGrammarWithImplicitEof() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;

S: () = E;

E: () = {
    E "-" T,
    T,
};

T: () = {
    "N",
    "(" E ")",
};
""",
            )

            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                assertFails { buildLr0States(grammar, nt("S")) }
            } finally {
                lr1Tls.drop()
            }
        }
    }

    /**
     * When we moved to storing items as (lr0 -> TokenSet) pairs, a bug
     * in the transitive closure routine could cause us to have `(Foo,
     * S0)` and `(Foo, S1)` as distinct items instead of `(Foo, S0|S1)`.
     */
    @Test
    fun issue144() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;

pub ForeignItem: () = {
  AttrsAndVis "item_foreign_fn",
  AttrsAndVis "unsafe" "item_foreign_fn",
};

AttrsAndVis: () = {
    MaybeOuterAttrs visibility,
};

MaybeOuterAttrs: () = {
    OuterAttrs,
    (),
};

visibility: () = {
  "pub",
  (),
};

OuterAttrs: () = {
    OuterAttr,
    OuterAttrs OuterAttr,
};

OuterAttr: () = {
    "#" "[" "]",
};

Ident: () = {
    "IDENT",
};

ty: () = {
    "ty"
};
""",
            )

            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                buildLr1States(grammar, nt("ForeignItem"))
            } finally {
                lr1Tls.drop()
            }
        }
    }

    // Not sure if this is the right spot
    // This test requires regex unicode case support
    @Test
    fun matchGrammar() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;

match {
    r"(?i)select" => SELECT
} else {
    _
}

pub Query = SELECT r"[a-z]+";
""",
            )

            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val states = buildLr0States(grammar, nt("Query"))
                println("states: $states")
            } finally {
                lr1Tls.drop()
            }
        }
    }
}
