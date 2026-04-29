// port-lint: source lr1/trace/reduce/test.rs
package io.github.kotlinmania.lalrpop.lr1.trace.reduce

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
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.build.TableConstructionErrorException
import io.github.kotlinmania.lalrpop.lr1.buildStates
import io.github.kotlinmania.lalrpop.lr1.core.Item
import io.github.kotlinmania.lalrpop.lr1.core.TableConstructionError<TokenSet>
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.interpret.interpretPartial
import io.github.kotlinmania.lalrpop.lr1.lookahead.Token
import io.github.kotlinmania.lalrpop.lr1.lookahead.TokenSet
import io.github.kotlinmania.lalrpop.lr1.tls.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.trace.Tracer
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

private fun term(t: String): TerminalString = TerminalString.quoted(Atom.from(t))

private fun terms(vararg t: String): MutableList<TerminalString> =
    t.map { term(it) }.toMutableList()

private fun testGrammar1(): Grammar = normalizedGrammar(
    """
    grammar;

    pub Start: () = Stmt;

    pub Stmt: () = {
        Exprs ";",
        Exprs
    };

    Exprs: () = {
        Expr,
        Exprs "," Expr
    };

    Expr: () = {
        "Int",
        Expr "+" "Int",
    };
""",
)

class ReduceTest {
    @Test
    fun backtrace1() {
        Tls.test().use {
            val grammar = testGrammar1()
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val firstSets = FirstSets.new(grammar)
                val states = buildStates(grammar, nt("Start"))
                val tracer = Tracer.new(firstSets, states)
                val stateStack = interpretPartial(states, terms("Int"))
                val topState = stateStack.last()

                // Top state will have items like:
                //
                // Expr = "Int" (*) [EOF],
                // Expr = "Int" (*) ["+"],
                // Expr = "Int" (*) [","],
                // Expr = "Int" (*) [";"]
                //
                // Select the ";" one.
                val semi: Token = Token.Terminal(term(";"))
                val semiItem = states[topState.value]
                    .items
                    .vec
                    .first { item -> item.lookahead.contains(semi) }

                val backtrace = tracer.backtraceReduce(topState, semiItem.toLr0())

                println("$backtrace")

                val pictures = backtrace.lr0Examples(semiItem.toLr0()).asSequence()
                    .map { it.paintUnstyled() }.toList()
                expectDebug(
                    pictures,
                    """
[
    [
        "  Exprs "," "Int"  ╷ ";"",
        "  │         └─Expr─┤   │",
        "  ├─Exprs──────────┘   │",
        "  └─Stmt───────────────┘"
    ],
    [
        "  Exprs "," "Int"  ╷ "," Expr",
        "  │         └─Expr─┤        │",
        "  ├─Exprs──────────┘        │",
        "  └─Exprs───────────────────┘"
    ],
    [
        "  "Int"   ╷ ";"",
        "  ├─Expr──┤   │",
        "  ├─Exprs─┘   │",
        "  └─Stmt──────┘"
    ],
    [
        "  "Int"   ╷ "," Expr",
        "  ├─Expr──┤        │",
        "  ├─Exprs─┘        │",
        "  └─Exprs──────────┘"
    ],
    [
        "  "Int"  ╷ "+" "Int"",
        "  ├─Expr─┘         │",
        "  └─Expr───────────┘"
    ]
]
""".trim(),
                )
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun backtrace2() {
        Tls.test().use {
            // This grammar yields a S/R conflict. Is it (int -> int) -> int
            // or int -> (int -> int)?
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
                val tracer = Tracer.new(firstSets, err.states)
                val conflict = err.conflicts[0]
                println("conflict=$conflict")
                val item = Item(
                    production = conflict.production,
                    index = conflict.production.symbols.size,
                    lookahead = conflict.lookahead,
                )
                println("item=$item")
                val backtrace = tracer.backtraceReduce(conflict.state, item.toLr0())
                println("$backtrace")
                expectDebug(
                    backtrace,
                    """
[
    (Nonterminal(Ty) -([Ty, "->"], Some(Ty), [])-> Item(Ty = Ty "->" (*) Ty)),
    (Nonterminal(Ty) -([Ty, "->"], Some(Ty), [])-> Nonterminal(Ty)),
    (Nonterminal(Ty) -([Ty, "->", Ty], None, [])-> Item(Ty = Ty "->" Ty (*))),
    (Item(Ty = (*) Ty "->" Ty) -([], Some(Ty), ["->", Ty])-> Nonterminal(Ty))
]
""".trim(),
                )

                // Check that we can successfully enumerate and paint the examples
                // here.
                val pictures = backtrace.lr1Examples(firstSets, item).asSequence()
                    .map { it.paintUnstyled() }.toList()
                expectDebug(
                    pictures,
                    """
[
    [
        "  Ty "->" Ty "->" Ty",
        "  ├─Ty─────┘       │",
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

    @Test
    fun reduceBacktrace3Graph() {
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
                val item = Item(
                    production = conflict.production,
                    index = conflict.production.symbols.size,
                    lookahead = conflict.lookahead,
                )
                println("item=$item")
                val tracer = Tracer.new(firstSets, err.states)
                val graph = tracer.backtraceReduce(conflict.state, item.toLr0())
                expectDebug(
                    graph,
                    """
[
    (Nonterminal(Ty) -([Ty, "->"], Some(Ty), [])-> Item(Ty = Ty "->" (*) Ty)),
    (Nonterminal(Ty) -([Ty, "->"], Some(Ty), [])-> Nonterminal(Ty)),
    (Nonterminal(Ty) -([Ty, "->", Ty], None, [])-> Item(Ty = Ty "->" Ty (*))),
    (Item(Ty = (*) Ty "->" Ty) -([], Some(Ty), ["->", Ty])-> Nonterminal(Ty))
]
""".trim(),
                )

                val list = graph.lr1Examples(firstSets, item).asSequence()
                    .map { it.paintUnstyled() }.toList()
                expectDebug(
                    list,
                    """
[
    [
        "  Ty "->" Ty "->" Ty",
        "  ├─Ty─────┘       │",
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

    @Test
    fun backtraceFilter() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
    grammar;

    pub Start: () = Stmt;

    pub Stmt: () = {
        Exprs ";"
    };

    Exprs: () = {
        Expr,
        Exprs "," Expr
    };

    Expr: () = {
        ExprAtom ExprSuffix
    };

    ExprSuffix: () = {
        (),
        "?",
    };

    ExprAtom: () = {
        "Int",
    };
""",
            )
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val states = buildStates(grammar, nt("Start"))
                val firstSets = FirstSets.new(grammar)
                val tracer = Tracer.new(firstSets, states)
                val stateStack = interpretPartial(states, terms("Int"))
                val topState = stateStack.last()

                // Top state will have an item like:
                //
                // Expr = "Int" (*) [",", ";"],
                val semi: Token = Token.Terminal(term(";"))
                val lr1Item = states[topState.value]
                    .items
                    .vec
                    .first { item -> item.lookahead.contains(semi) }

                val backtrace = tracer.backtraceReduce(topState, lr1Item.toLr0())

                println("$backtrace")

                // With no filtering, we get examples with both `;` and `,` as
                // lookahead (though `ExprSuffix` is in the way).
                val pictures = backtrace.lr0Examples(lr1Item.toLr0()).asSequence()
                    .map { it.paintUnstyled() }.toList()
                expectDebug(
                    pictures,
                    """
[
    [
        "  Exprs "," "Int"      ╷ ExprSuffix ";"",
        "  │         ├─ExprAtom─┘          │   │",
        "  │         └─Expr────────────────┤   │",
        "  ├─Exprs─────────────────────────┘   │",
        "  └─Stmt──────────────────────────────┘"
    ],
    [
        "  Exprs "," "Int"      ╷ ExprSuffix "," Expr",
        "  │         ├─ExprAtom─┘          │        │",
        "  │         └─Expr────────────────┤        │",
        "  ├─Exprs─────────────────────────┘        │",
        "  └─Exprs──────────────────────────────────┘"
    ],
    [
        "  "Int"      ╷ ExprSuffix ";"",
        "  ├─ExprAtom─┘          │   │",
        "  ├─Expr────────────────┤   │",
        "  ├─Exprs───────────────┘   │",
        "  └─Stmt────────────────────┘"
    ],
    [
        "  "Int"      ╷ ExprSuffix "," Expr",
        "  ├─ExprAtom─┘          │        │",
        "  ├─Expr────────────────┤        │",
        "  ├─Exprs───────────────┘        │",
        "  └─Exprs────────────────────────┘"
    ]
]
""".trim(),
                )

                // Select those with `;` as lookahead
                val semiItem = lr1Item.withLookahead(TokenSet.from(semi))
                val pictures2 = backtrace.lr1Examples(firstSets, semiItem).asSequence()
                    .map { it.paintUnstyled() }.toList()
                expectDebug(
                    pictures2,
                    """
[
    [
        "  Exprs "," "Int"      ╷ ExprSuffix ";"",
        "  │         ├─ExprAtom─┘          │   │",
        "  │         └─Expr────────────────┤   │",
        "  ├─Exprs─────────────────────────┘   │",
        "  └─Stmt──────────────────────────────┘"
    ],
    [
        "  "Int"      ╷ ExprSuffix ";"",
        "  ├─ExprAtom─┘          │   │",
        "  ├─Expr────────────────┤   │",
        "  ├─Exprs───────────────┘   │",
        "  └─Stmt────────────────────┘"
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
