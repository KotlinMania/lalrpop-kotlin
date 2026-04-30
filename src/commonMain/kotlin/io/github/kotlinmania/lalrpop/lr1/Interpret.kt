// port-lint: source lr1/interpret.rs
/** LR(1) interpreter. Just builds up parse trees. Intended for testing. */
package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.ParseTree
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.grammar.parsetree.TerminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Production

data class InterpretError<L : Lookahead<L>>(
    val state: State<L>,
    val token: Token,
)

class InterpretErrorException(val error: InterpretError<*>) :
    RuntimeException("Interpret error at state ${error.state.index}: unexpected token ${error.token}")

/** Feed in the given tokens and then EOF, returning the final parse tree that is reduced. */
fun <L : LookaheadInterpret<L>> interpret(
    states: List<State<L>>,
    tokens: MutableList<TerminalString>,
): Result<ParseTree> {
    println("interpret(tokens=$tokens)")
    val m = Machine.new(states)
    return m.execute(tokens.iterator())
}

/** Feed in the given tokens and returns the states on the stack. */
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

    private fun reductionFor(state: State<L>, token: Token): Production? {
        val lookahead = state.reductions.firstOrNull()?.first ?: return null
        return lookahead.reduction(state, token)
    }

    fun executePartial(tokens: Iterator<TerminalString>): Result<Unit> {
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
                continue
            }

            val production = reductionFor(state, Token.Terminal(terminal))
            if (production != null) {
                val more = reduce(production)
                check(more)
            } else {
                return Result.failure(InterpretErrorException(InterpretError(state, Token.Terminal(terminal))))
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
            val production = reductionFor(state, Token.Eof)
            if (production == null) {
                return Result.failure(InterpretErrorException(InterpretError(state, Token.Eof)))
            }
            if (!reduce(production)) {
                check(dataStack.size == 1)
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

interface LookaheadInterpret<Self : Lookahead<Self>> : Lookahead<Self> {
    fun reduction(state: State<Self>, token: Token): Production?
}
