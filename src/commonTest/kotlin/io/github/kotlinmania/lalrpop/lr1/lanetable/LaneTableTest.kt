// port-lint: source lr1/lane_table/test.rs
package io.github.kotlinmania.lalrpop.lr1.lanetable

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
import io.github.kotlinmania.lalrpop.grammar.repr.Symbol
import io.github.kotlinmania.lalrpop.lr1.BuildOutcome
import io.github.kotlinmania.lalrpop.lr1.buildLr0StatesOrError
import io.github.kotlinmania.lalrpop.lr1.buildStates
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.Nil
import io.github.kotlinmania.lalrpop.lr1.State
import io.github.kotlinmania.lalrpop.lr1.StateGraph
import io.github.kotlinmania.lalrpop.lr1.StateIndex
import io.github.kotlinmania.lalrpop.lr1.TableConstructionError
import io.github.kotlinmania.lalrpop.lr1.first.FirstSets
import io.github.kotlinmania.lalrpop.lr1.interpret
import io.github.kotlinmania.lalrpop.lr1.interpretPartial
import io.github.kotlinmania.lalrpop.lr1.lanetable.construct.LaneTableConstruct
import io.github.kotlinmania.lalrpop.lr1.lanetable.lane.LaneTracer
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.ConflictIndex
import io.github.kotlinmania.lalrpop.lr1.lanetable.table.LaneTable
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

private fun tokens(vararg t: String): MutableList<TerminalString> =
    t.map { TerminalString.quoted(Atom.from(it)) }.toMutableList()

private fun sym(t: String): Symbol =
    if (t[0].isUpperCase()) {
        Symbol.Nonterminal(nt(t))
    } else {
        Symbol.Terminal(term(t))
    }

private fun term(t: String): TerminalString = TerminalString.quoted(Atom.from(t))

private fun nt(t: String): NonterminalString = NonterminalString(Atom.from(t))

private fun traverse(states: List<State<Nil>>, tokens: Array<out String>): StateIndex {
    return interpretPartial(states, tokens.map { term(it) })
        .getOrThrow()
        .last()
}

/**
 * A simplified version of the paper initial grammar; this version
 * only has one inconsistent state (the same state they talk about in
 * the paper).
 */
internal fun paperExampleG0(): Grammar = normalizedGrammar(
    """
grammar;

pub G: () = {
    X "c",
    Y "d",
};

X: () = {
    "e" X,
    "e",}
;

Y: () = {
    "e" Y,
    "e"
};
""",
)

/**
 * A (corrected) version of the sample grammar G1 from the paper. The
 * grammar as written in the text omits some items, but the diagrams
 * seem to contain the full set. I believe this is one of the
 * smallest examples that still requires splitting states from the
 * LR0 states.
 */
internal fun paperExampleG1(): Grammar = normalizedGrammar(
    """
grammar;

pub G: () = {
    // if "a" is input, then lookahead "d" means "reduce X"
    // and lookahead "c" means "reduce "Y"
    "a" X "d",
    "a" Y "c",

    // if "b" is input, then lookahead "d" means "reduce Y"
    // and lookahead "c" means "reduce X.
    "b" X "c",
    "b" Y "d",
};

X: () = {
    "e" X,
    "e",
};

Y: () = {
    "e" Y,
    "e"
};
""",
)

/** A variation on G1 to omit the possibility of shifting */
internal fun exampleG2(): Grammar = normalizedGrammar(
    """
grammar;

pub G = {
        "a" X "d",
        "a" Y "c",
        "b" X "c",
        "b" Y "d",
};

X = {
        "e"
};

Y = {
        "e"
};
""",
)

private fun expectLr0Failure(
    grammar: Grammar,
    start: NonterminalString,
): TableConstructionError<Nil> =
    when (val outcome = buildLr0StatesOrError(grammar, start)) {
        is BuildOutcome.Ok -> error("expected build_lr0_states to fail")
        is BuildOutcome.Err -> outcome.error
    }

private fun buildTable(
    grammar: Grammar,
    goal: String,
    tokens: Array<out String>,
): LaneTable {
    val lr0Err: TableConstructionError<Nil> = expectLr0Failure(grammar, nt(goal))

    // Push the `tokens` to find the index of the inconsistent state
    val inconsistentStateIndex = traverse(lr0Err.states, tokens)
    assertTrue(
        lr0Err.conflicts.any { c -> c.state == inconsistentStateIndex },
    )
    val inconsistentState = lr0Err.states[inconsistentStateIndex.value]
    println("inconsistent_state=${inconsistentState.items}")

    // Extract conflicting items and trace the lanes, constructing a table
    val conflictingItems = conflictingActions(inconsistentState)
    println("conflicting_items=$conflictingItems")
    val firstSets = FirstSets.new(grammar)
    val stateGraph = StateGraph.new(lr0Err.states)
    val tracer = LaneTracer.new(
        grammar,
        nt("G"),
        lr0Err.states,
        firstSets,
        stateGraph,
        conflictingItems.size,
    )
    for ((i, conflictingItem) in conflictingItems.withIndex()) {
        tracer.startTrace(
            inconsistentState.index,
            ConflictIndex.new(i),
            conflictingItem,
        )
    }

    return tracer.intoTable()
}

class LaneTableTest {
    @Test
    fun g0Conflict1() {
        Tls.test().use {
            val grammar = paperExampleG0()
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val table = buildTable(grammar, "G", arrayOf("e"))
                println("$table")
                // conflictingActions={
                //     Shift("e") // C0
                //     Reduce(X = "e" => ActionFn(4)) // C1
                //     Reduce(Y = "e" => ActionFn(6)) // C2
                // }
                expectDebug(
                    table,
                    """
| State | C0    | C1    | C2    | Successors |
| S0    |       | ["c"] | ["d"] | {S3}       |
| S3    | ["e"] | []    | []    | {S3}       |
""".trimStart(),
                )
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun paperExampleG1Conflict1() {
        Tls.test().use {
            val grammar = paperExampleG1()
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val table = buildTable(grammar, "G", arrayOf("a", "e"))
                println("$table")
                // conflictingActions={
                //     Shift("e") // C0
                //     Reduce(X = "e" => ActionFn(6)) // C1
                //     Reduce(Y = "e" => ActionFn(8)) // C2
                // }
                expectDebug(
                    table,
                    """
| State | C0    | C1    | C2    | Successors |
| S1    |       | ["d"] | ["c"] | {S5}       |
| S2    |       | ["c"] | ["d"] | {S5}       |
| S5    | ["e"] | []    | []    | {S5}       |
""".trimStart(),
                )
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun paperExampleG0Build() {
        Tls.test().use {
            val grammar = paperExampleG0()
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val lr0Err: TableConstructionError<Nil> = expectLr0Failure(grammar, nt("G"))
                val states = LaneTableConstruct.new(grammar, nt("G")).construct()

                // we do not require more *states* than LR(0), just different lookahead
                assertEquals(lr0Err.states.size, states.size)

                val tree = interpret(states, tokens("e", "c")).getOrThrow()
                expectDebug(tree, """[G: [X: "e"], "c"]""")

                val tree2 = interpret(states, tokens("e", "e", "c")).getOrThrow()
                expectDebug(tree2, """[G: [X: "e", [X: "e"]], "c"]""")

                val tree3 = interpret(states, tokens("e", "e", "d")).getOrThrow()
                expectDebug(tree3, """[G: [Y: "e", [Y: "e"]], "d"]""")

                assertTrue(interpret(states, tokens("e", "e", "e")).isFailure)
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun paperExampleG1Build() {
        Tls.test().use {
            val grammar = paperExampleG1()
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val lr0Err: TableConstructionError<Nil> = expectLr0Failure(grammar, nt("__G"))
                val states = buildStates(grammar, nt("__G"))

                // we require more *states* than LR(0), not just different lookahead
                assertEquals(1, states.size - lr0Err.states.size)

                val tree = interpret(states, tokens("a", "e", "e", "d")).getOrThrow()
                expectDebug(tree, """[__G: [G: "a", [X: "e", [X: "e"]], "d"]]""")

                val tree2 = interpret(states, tokens("b", "e", "e", "d")).getOrThrow()
                expectDebug(tree2, """[__G: [G: "b", [Y: "e", [Y: "e"]], "d"]]""")

                assertTrue(interpret(states, tokens("e", "e", "e")).isFailure)
            } finally {
                lr1Tls.drop()
            }
        }
    }

    // The G1 example has a non-conflicting shift in the state with the reduce/reduce conflict.  This
    // test exercises the case where the reduce/reduce is the only difference.
    @Test
    fun exampleG2Build() {
        Tls.test().use {
            val grammar = exampleG2()

            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val lr0Err: TableConstructionError<Nil> = expectLr0Failure(grammar, nt("__G"))
                val states = buildStates(grammar, nt("__G"))

                // we require more *states* than LR(0), not just different lookahead
                assertEquals(1, states.size - lr0Err.states.size)

                val tree = interpret(states, tokens("a", "e", "d")).getOrThrow()
                expectDebug(tree, """[__G: [G: "a", [X: "e"], "d"]]""")

                val tree2 = interpret(states, tokens("b", "e", "d")).getOrThrow()
                expectDebug(tree2, """[__G: [G: "b", [Y: "e"], "d"]]""")
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun largeConflict1() {
        Tls.test().use {
            val grammar = paperExampleLarge()
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val table = buildTable(grammar, "G", arrayOf("x", "s", "k", "t"))
                println("$table")

                // conflictingActions={
                //     Shift("s") // C0
                //     Reduce(U = U "k" "t") // C1
                //     Reduce(X = "k" "t") // C2
                //     Reduce(Y = "k" "t") // C3
                // }

                expectDebug(
                    table,
                    """
| State | C0    | C1    | C2         | C3    | Successors |
| S1    |       | ["k"] |            |       | {S5}       |
| S2    |       | ["k"] |            |       | {S7}       |
| S3    |       | ["k"] |            |       | {S7}       |
| S4    |       | ["k"] |            |       | {S7}       |
| S5    |       |       | ["a"]      | ["r"] | {S16}      |
| S7    |       |       | ["c", "w"] | ["d"] | {S16}      |
| S16   |       |       |            |       | {S27}      |
| S27   | ["s"] | ["k"] | []         | []    | {S32}      |
| S32   |       |       | ["z"]      | ["u"] | {S16}      |
""".trimStart(),
                )

                // ^^ This differs in some particulars from what appears in the
                // paper, but I believe it to be correct, and the paper to be wrong.
                //
                // Here is the table using the state names from the paper. I have
                // marked the differences with `(*)`. Note that the paper does not
                // include the C0 column (the shift).
                //
                // | State | pi1   | pi2   | pi3        | Successors |
                // | B     | ["k"] |       | *1         | {G}        |
                // | C     | ["k"] |       | *1         | {G}        |
                // | D     | ["k"] |       | *1         | {G}        |
                // | E     | ["k"] |       |            | {F}        |
                // | F     |       | ["r"] | ["a"]      | {H}        |
                // | G     |       | ["d"] | ["c", "w"] | {H}        |
                // | H     |       |       |            | {I}        |
                // | I     | ["k"] |       |            | {J}        |
                // | J     |       | ["u"] | ["z"] *2   | {H}        |
                //
                // *1 - the paper lists "a", "b", and "r" here as lookaheads.  We
                // do not. This is because when we trace back pi3, we never reach
                // those states, as we have already acquired the necessary token
                // of context earlier. I can imagine a distinct lane tracing
                // algorithm that considers *sets* of conflicts and only
                // terminates when all sets have context, but it much more
                // complex to implement, and seems to add little value.
                //
                // *2 - the paper does not list this context, and yet it seems to
                // present. If you trace back "t" and "k" you reach state J which
                // has the item "X = k t (*)". This "unepsilons" to "X = k t U (*)
                // X P", and the lookahead from the "X" here is FIRST(P) which is
                // "z".
            } finally {
                lr1Tls.drop()
            }
        }
    }

    @Test
    fun paperExampleLargeBuild() {
        Tls.test().use {
            val grammar = paperExampleLarge()
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val states = LaneTableConstruct.new(grammar, nt("G")).construct()

                val tree = interpret(states, tokens("y", "s", "k", "t", "c", "b")).getOrThrow()
                expectDebug(
                    tree,
                    """[G: "y", [W: [U: "s"], [X: "k", "t"], [C: "c"]], "b"]""",
                )
            } finally {
                lr1Tls.drop()
            }
        }
    }
}

internal fun paperExampleLarge(): Grammar = normalizedGrammar(
    """
grammar;

pub G: () = {
    "x" W "a",
    "x" V "t",
    "y" W "b",
    "y" V "t",
    "z" W "r",
    "z" V "b",
    "u" U X "a",
    "u" U Y "r",
};

W: () = {
    U X C
};

V: () = {
    U Y "d"
};

X: () = {
    "k" "t" U X P,
    "k" "t"
};

Y: () = {
    "k" "t" U Y "u",
    "k" "t"
};

U: () = {
    U "k" "t",
    "s"
};

E: () = {
    "a",
    "b",
    "c",
    "v",
};

C: () = {
    "c",
    "w"
};

P: () = {
    "z"
};
""",
)
