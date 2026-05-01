package io.github.kotlinmania.lalrpop.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Toy `Symbol` hierarchy stand-in. A real grammar will declare its own; the runtime is
 * generic over the sealed class.
 */
private sealed class ToyS {
    data class TerminalA(val text: String) : ToyS()
    data class TerminalB(val n: Int) : ToyS()
    data class Pair(val a: TerminalA, val b: TerminalB) : ToyS()
}

class ParseStackTest {

    @Test
    fun popReturnsTheTypedVariant() {
        val stack = ParseStack<ToyS, Int>()
        stack.push(0, ToyS.TerminalA("hello"), 5)

        val popped: ToyS.TerminalA = stack.pop()

        assertEquals("hello", popped.text)
        assertTrue(stack.isEmpty())
    }

    @Test
    fun popReportsTheVariantMismatchInsteadOfReturningNullOrAny() {
        val stack = ParseStack<ToyS, Int>()
        stack.push(0, ToyS.TerminalA("hello"), 5)

        val ex = assertFailsWith<ParseStackTypeException> {
            stack.pop<ToyS.TerminalB>()
        }

        assertEquals("TerminalB", ex.expected)
        assertEquals("TerminalA", ex.actual)
    }

    @Test
    fun popLocatedRunReturnsEntriesInStackOrder() {
        val stack = ParseStack<ToyS, Int>()
        stack.push(0, ToyS.TerminalA("a"), 1)
        stack.push(1, ToyS.TerminalB(42), 3)
        stack.push(3, ToyS.TerminalA("c"), 4)

        val run = stack.popLocatedRun(2)

        assertEquals(2, run.size)
        assertEquals(ToyS.TerminalB(42), run[0].symbol)
        assertEquals(ToyS.TerminalA("c"), run[1].symbol)
        assertEquals(1, stack.size)
    }

    @Test
    fun reductionLambdaSeesTypedInputsAndProducesTypedOutput() {
        val stack = ParseStack<ToyS, Int>()
        stack.push(0, ToyS.TerminalA("hello"), 5)
        stack.push(5, ToyS.TerminalB(7), 6)

        val production = Production<ToyS, Int>(
            nonterminalId = 0,
            rhsLength = 2,
            action = ProductionAction { s, _ ->
                // Pops come off in reverse RHS order — top of stack is the rightmost
                // RHS symbol — matching the convention upstream LALRPOP uses in its
                // generated reducers.
                val b = s.pop<ToyS.TerminalB>()
                val a = s.pop<ToyS.TerminalA>()
                ToyS.Pair(a, b)
            },
        )

        // Driver-equivalent: pop a run, build the span, invoke the action.
        val popped = stack.popLocatedRun(production.rhsLength)
        val span = ProductionSpan(start = popped.first().start, end = popped.last().end)
        // Re-push them so the lambda's pops drain them — mirrors how the real driver
        // hands the inputs to the lambda.
        for (entry in popped) stack.push(entry)
        val produced = production.action.reduce(stack, span)

        assertEquals(ToyS.Pair(ToyS.TerminalA("hello"), ToyS.TerminalB(7)), produced)
        assertTrue(stack.isEmpty())
        assertEquals(0, span.start)
        assertEquals(6, span.end)
    }

    @Test
    fun popLocatedRunRejectsUnderflow() {
        val stack = ParseStack<ToyS, Int>()
        stack.push(0, ToyS.TerminalA("solo"), 1)

        assertFailsWith<IllegalStateException> {
            stack.popLocatedRun(2)
        }
    }

    @Test
    fun popLocatedRunOfZeroIsEmptyAndDoesNotMutate() {
        val stack = ParseStack<ToyS, Int>()
        stack.push(0, ToyS.TerminalA("only"), 1)

        val run = stack.popLocatedRun(0)

        assertTrue(run.isEmpty())
        assertEquals(1, stack.size)
    }

    @Test
    fun peekDepthZeroIsTopAndDoesNotPop() {
        val stack = ParseStack<ToyS, Int>()
        stack.push(0, ToyS.TerminalA("bottom"), 1)
        stack.push(1, ToyS.TerminalB(99), 2)

        assertEquals(ToyS.TerminalB(99), stack.peek(0).symbol)
        assertEquals(ToyS.TerminalA("bottom"), stack.peek(1).symbol)
        assertEquals(2, stack.size)
    }
}
