// port-lint: source lr1/interpret.rs
/** LR(1) interpreter. Just builds up parse trees. Intended for testing. */
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.ParseTree
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.grammar.parseTree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production
import io.github.kotlinmania.lalrpop.lr1.core.State
import io.github.kotlinmania.lalrpop.lr1.core.StateIndex

data class InterpretError<L : LookaheadInterpret<L>>(
    val state: State<L>,
    val token: Token,
)

private class Error

private class Formatter(val sb: StringBuilder = StringBuilder())

fun fmt(tree: ParseTree, fmt: Formatter, error: Error): Result<Unit> {
    return fmt(tree, fmt)
}

fun fmt(tree: ParseTree, fmt: Formatter): Result<Unit> {
    when (tree) {
        is ParseTree.Nonterminal -> {
            val id = tree.nt
            val trees = tree.trees
            fmt.sb.append("[").append(id).append(": ").append(Sep(", ", trees)).append("]")
        }
        is ParseTree.Terminal -> {
            val id = tree.t
            fmt.sb.append(id)
        }
    }
    return Result.success(Unit)
}

fun reduction(state: State<Nil>, token: Token): Production? {
    return state.reductions
        .asSequence()
        .map { (_, production) -> production }
        .firstOrNull()
}

fun reduction(state: State<TokenSet>, token: Token): Production? {
    return state.reductions
        .asSequence()
        .filter { (tokens, _) -> tokens.contains(token) }
        .map { (_, production) -> production }
        .firstOrNull()
}

/// Feed in the given tokens and then EOF, returning the final parse tree that is reduced.
fun <L : LookaheadInterpret<L>> interpret(
    states: List<State<L>>,
    tokens: MutableList<TerminalString>,
): Result<ParseTree> {
    println("interpret(tokens=$tokens)")
    val m = Machine.new(states)
    return m.execute(tokens.iterator())
}

/// Feed in the given tokens and returns the states on the stack.
fun <L : LookaheadInterpret<L>> interpretPartial(
    states: List<State<L>>,
    tokens: Iterable<TerminalString>,
): Result<MutableList<StateIndex>> {
    val m = Machine.new(states)
    return m.executePartial(tokens.iterator()).map { m.stateStack }
}

private class Machine<L : LookaheadInterpret<L>>(
    private val states: List<State<L>>,
    val stateStack: MutableList<StateIndex>,
    private val dataStack: MutableList<ParseTree>,
) {
    companion object {
        fun <L : LookaheadInterpret<L>> new(states: List<State<L>>): Machine<L> = Machine(
            states = states,
            stateStack = mutableListOf(),
            dataStack = mutableListOf(),
        )
    }

    private fun topState(): State<L> {
        val index = stateStack.last()
        return states[index.value]
    }

    fun executePartial(tokens: Iterator<TerminalString>): Result<Unit> {
        if (stateStack.isNotEmpty() || dataStack.isNotEmpty()) {
            return Result.failure(IllegalStateException("state/data stacks must be empty"))
        }

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
                continue
            }

            val production = if (state.reductions.firstOrNull()?.first is TokenSet) {
                reduction(state as State<TokenSet>, Token.Terminal(terminal))
            } else {
                reduction(state as State<Nil>, Token.Terminal(terminal))
            }
            if (production == null) {
                return Result.failure(InterpretErrorException(InterpretError(state, Token.Terminal(terminal))))
            }

            val more = reduce(production)
            if (!more) {
                return Result.failure(IllegalStateException("interpreter completed early"))
            }
        }

        return Result.success(Unit)
    }

    fun execute(tokens: Iterator<TerminalString>): Result<ParseTree> {
        val partial = executePartial(tokens)
        if (partial.isFailure) {
            return Result.failure(partial.exceptionOrNull()!!)
        }

        // drain now for EOF
        while (true) {
            val state = topState()
            val production = if (state.reductions.firstOrNull()?.first is TokenSet) {
                reduction(state as State<TokenSet>, Token.Eof)
            } else {
                reduction(state as State<Nil>, Token.Eof)
            }
            if (production == null) {
                return Result.failure(InterpretErrorException(InterpretError(state, Token.Eof)))
            }

            if (!reduce(production)) {
                if (dataStack.size != 1) {
                    return Result.failure(IllegalStateException("expected one parse tree"))
                }
                return Result.success(dataStack.removeLast())
            }
        }
    }

    private fun reduce(production: Production): Boolean {
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

class InterpretErrorException<L : LookaheadInterpret<L>>(val error: InterpretError<L>) :
    RuntimeException()

interface LookaheadInterpret<Self : Lookahead<Self>> : Lookahead<Self> {
    fun reduction(state: State<Self>, token: Token): Production?
}
