// port-lint: source lr1/error/test.rs
package io.github.kotlinmania.lalrpop.lr1.error

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
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.lr1.buildStates
import io.github.kotlinmania.lalrpop.lr1.TableConstructionErrorException
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.TableConstructionError
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.message.AsciiCanvas
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

private fun buildStatesError(
    grammar: io.github.kotlinmania.lalrpop.grammar.repr.Grammar,
    start: NonterminalString,
): TableConstructionError<TokenSet> =
    try {
        buildStates(grammar, start)
        error("expected build_states to fail")
    } catch (e: TableConstructionErrorException) {
        e.lr1Inner ?: error("expected TokenSet table construction error")
    }

class ErrorTest {
    @Test
    fun priorityConflict() {
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
                val err = buildStatesError(grammar, nt("Ty"))
                val cx = ErrorReportingCx.new(grammar, err.states, err.conflicts)
                val conflicts = tokenConflicts(err.conflicts)
                val conflict = conflicts[0][0]

                println("conflict=$conflict")

                when (val r = cx.classify(conflict)) {
                    is ConflictClassification.Precedence -> {
                        println("shift=${r.shift}, reduce=${r.reduce}, nonterminal=${r.nonterminal}")
                        assertEquals(5, r.shift.symbols.size) // Ty -> Ty -> Ty
                        assertEquals(3, r.shift.cursor) // Ty -> Ty -> Ty
                        assertEquals(r.shift.symbols, r.reduce.symbols)
                        assertEquals(r.shift.cursor, r.reduce.cursor)
                        assertEquals(nt("Ty"), r.nonterminal)
                    }
                    else -> fail("wrong classification $r")
                }
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun exprBracedConflict() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;
pub Expr: () = {
    "Id" => (),
    "Id" "{" "}" => (),
    "Expr" "+" "Id" => (),
    "if" Expr "{" "}" => (),
};
""",
            )
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val err = buildStatesError(grammar, nt("Expr"))
                val cx = ErrorReportingCx.new(grammar, err.states, err.conflicts)
                val conflicts = tokenConflicts(err.conflicts)
                val conflict = conflicts[0][0]

                println("conflict=$conflict")

                when (val r = cx.classify(conflict)) {
                    is ConflictClassification.InsufficientLookahead -> {}
                    else -> fail("wrong classification $r")
                }
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun suggestQuestionConflict() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
        grammar;

        pub E: () = {
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
                val err = buildStatesError(grammar, nt("E"))
                val cx = ErrorReportingCx.new(grammar, err.states, err.conflicts)
                val conflicts = tokenConflicts(err.conflicts)
                val conflict = conflicts[0][0]

                println("conflict=$conflict")

                when (val r = cx.classify(conflict)) {
                    is ConflictClassification.SuggestQuestion -> {
                        assertEquals(nt("OPT_L"), r.nonterminal)
                        assertEquals(
                            Symbol.Terminal(TerminalString.quoted(Atom.from("L"))),
                            r.symbol,
                        )
                    }
                    else -> fail("wrong classification $r")
                }
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun suggestInlineConflict() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;

pub ImportDecl: () = {
    "import" <Path> ";" => (),
    "import" <Path> "." "*" ";" => (),
};

Path: () = {
    <head: Ident> <tail: ("." <Ident>)*> => ()
};

Ident = r#"[a-zA-Z][a-zA-Z0-9]*"#;
""",
            )
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val err = buildStatesError(grammar, nt("ImportDecl"))
                val cx = ErrorReportingCx.new(grammar, err.states, err.conflicts)
                val conflicts = tokenConflicts(err.conflicts)
                val conflict = conflicts[0][0]

                println("conflict=$conflict")

                when (val r = cx.classify(conflict)) {
                    is ConflictClassification.SuggestInline -> {
                        assertEquals(nt("Path"), r.nonterminal)
                    }
                    else -> fail("wrong classification $r")
                }
            } finally {
                lr1Tls.drop()
            }
        }
    }

    /** This example used to cause an out-of-bounds error. */
    @Test
    fun issue249() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
grammar;

pub Func = StructDecl* VarDecl*;
StructDecl = "<" StructParameter* ">";
StructParameter = "may_dangle"?;
VarDecl = "let";
""",
            )
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val err = buildStatesError(grammar, nt("Func"))
                val cx = ErrorReportingCx.new(grammar, err.states, err.conflicts)
                val conflicts = tokenConflicts(err.conflicts)
                for (conflict in conflicts.flatten()) {
                    println("conflict=$conflict")
                    cx.classify(conflict)
                }
            } finally {
                lr1Tls.drop()
            }
        }
    }

    private fun verifyErrors(
        grammarText: String,
        pubState: String,
        uniqueConflicts: Int,
        terminalCount: Int, // Must include Eof.
        text: String,
    ) {
        Tls.testString(grammarText).use {
            val grammar = normalizedGrammar(grammarText)
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val err = buildStatesError(grammar, nt(pubState))
                val cx = ErrorReportingCx.new(grammar, err.states, err.conflicts)
                val conflicts = tokenConflicts(err.conflicts)
                assertEquals(uniqueConflicts, conflicts.size) // One group of conflicts
                for (conflict in conflicts) {
                    assertEquals(terminalCount, conflict.size) // terminal count
                }

                var calls = 0
                cx.reportErrors { message ->
                    val canvas = AsciiCanvas.new(0, message.minWidth())
                    message.emit(canvas)
                    assertTrue(
                        canvas.toStrings()
                            .map { it.toString() }
                            .joinToString("\n")
                            .contains(text),
                    )
                    calls += 1
                    assertTrue(calls <= uniqueConflicts)
                }
                assertEquals(uniqueConflicts, calls)
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun compressErrors() {
        val grammar = """
grammar;

pub A: () = {
        "a" B "z",
        "a" C "z",
}

B: () = {
        "b",
        "q"
}

C: () = {
        "c",
        "q"
}
"""
        verifyErrors(grammar, "A", 1, 6, "Ambiguous grammar")
    }

    @Test
    fun ambiguousReduction() {
        val grammar = """
grammar;

A: () = {
        "a" "c",
        "a" "b"? "c"
}

pub B: () = {
        "x" A "z"
}
"""
        verifyErrors(grammar, "B", 1, 6, "same reduction")
    }
}
