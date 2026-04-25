// port-lint: source src/lr1/interpret.rs
//! LR(1) interpreter. Just builds up parse trees. Intended for testing.
package io.github.kotlinmania.lalrpop_kotlin.lr1.interpret

import io.github.kotlinmania.lalrpop_kotlin.ParseTree
import io.github.kotlinmania.lalrpop_kotlin.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop_kotlin.grammar.repr.Production
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.State
import io.github.kotlinmania.lalrpop_kotlin.lr1.core.StateIndex
import io.github.kotlinmania.lalrpop_kotlin.lr1.lookahead.Lookahead
import io.github.kotlinmania.lalrpop_kotlin.lr1.lookahead.Token

typealias InterpretError<L> = Pair<State<L>, Token>

/// Feed in the given tokens and then EOF, returning the final parse tree that is reduced.
fun <L : LookaheadInterpret<L>> interpret(
    states: List<State<L>>,
    tokens: MutableList<TerminalString>,
): ParseTree {
    println("interpret(tokens=$tokens)")
    val m = Machine.new(states)
    return m.execute(tokens.iterator())
}

/// Feed in the given tokens and returns the states on the stack.
fun <L : LookaheadInterpret<L>> interpretPartial(
    states: List<State<L>>,
    tokens: Iterable<TerminalString>,
): MutableList<StateIndex> {
    val m = Machine.new(states)
    m.executePartial(tokens.iterator())
    return m.stateStack
}

private class Machine<L : LookaheadInterpret<L>>(
    val states: List<State<L>>,
    val stateStack: MutableList<StateIndex>,
    val dataStack: MutableList<ParseTree>,
) {
    companion object {
        fun <L : LookaheadInterpret<L>> new(states: List<State<L>>): Machine<L> = Machine(
            states = states,
            stateStack = mutableListOf(),
            dataStack = mutableListOf(),
        )
    }

    fun topState(): State<L> {
        val index = stateStack.last()
        return states[index.value]
    }

    fun executePartial(tokens: Iterator<TerminalString>) {
        check(stateStack.isEmpty())
        check(dataStack.isEmpty())

        stateStack.add(StateIndex(0))

        var token: TerminalString? = if (tokens.hasNext()) tokens.next() else null
        while (token != null) {
            val terminal = token
            val state = topState()

            println("state=$state")
            println("terminal=$terminal")

            // check whether we can shift this token
            val nextIndex = state.shifts[terminal]
            if (nextIndex != null) {
                dataStack.add(ParseTree.Terminal(terminal))
                stateStack.add(nextIndex)
                token = if (tokens.hasNext()) tokens.next() else null
            } else {
                val production = reduction(state, Token.Terminal(terminal))
                if (production != null) {
                    val more = reduce(production)
                    check(more)
                } else {
                    throw InterpretErrorException(state, Token.Terminal(terminal))
                }
            }
        }
    }

    fun execute(tokens: Iterator<TerminalString>): ParseTree {
        executePartial(tokens)

        // drain now for EOF
        while (true) {
            val state = topState()
            val production = reduction(state, Token.Eof)
            if (production == null) {
                throw InterpretErrorException(state, Token.Eof)
            } else {
                if (!reduce(production)) {
                    check(dataStack.size == 1)
                    return dataStack.removeLast()
                }
            }
        }
    }

    private fun reduction(state: State<L>, token: Token): Production? {
        val lookahead = state.reductions.firstOrNull()?.first ?: return null
        return lookahead.reduction(state, token)
    }

    fun reduce(production: Production): Boolean {
        println("reduce=$production")

        val args = production.symbols.size

        // remove the top N items from the data stack
        val popped: MutableList<ParseTree> = mutableListOf()
        for (i in 0 until args) {
            popped.add(dataStack.removeLast())
        }
        popped.reverse()

        // remove the top N states
        for (i in 0 until args) {
            stateStack.removeLast()
        }

        // construct the new, reduced tree and push it on the stack
        val tree: ParseTree = ParseTree.Nonterminal(production.nonterminal, popped)
        dataStack.add(tree)

        // recover the state and extract the "Goto" action
        val receivingState = topState()
        val gotoState = receivingState.gotos[production.nonterminal]
        return if (gotoState != null) {
            stateStack.add(gotoState)
            true // keep going
        } else {
            false // all done
        }
    }
}

class InterpretErrorException(val state: State<*>, val token: Token) : RuntimeException()

fun ParseTree.displayString(): String = toString()

interface LookaheadInterpret<Self : Lookahead<Self>> : Lookahead<Self> {
    fun reduction(state: State<Self>, token: Token): Production?
}
