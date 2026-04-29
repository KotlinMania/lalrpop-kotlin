// port-lint: source lexer/nfa/test.rs
package io.github.kotlinmania.lalrpop.lexer.nfa

import io.github.kotlinmania.lalrpop.lexer.parseRegex
import io.github.kotlinmania.lalrpop.lexer.nfa.Test as NfaTestEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NfaTest {
    @Test
    fun edgeIter() {
        val nfa = Nfa.new()
        val s0 = nfa.newState(StateKind.Neither)
        val s1 = nfa.newState(StateKind.Neither)
        val s2 = nfa.newState(StateKind.Neither)
        val s3 = nfa.newState(StateKind.Neither)

        nfa.pushEdgeNoop(s2, s3)
        nfa.pushEdgeNoop(s0, s1)
        nfa.pushEdgeNoop(s0, s3)
        nfa.pushEdgeNoop(s1, s2)

        // check that if we mixed up the indies between Noop/Other, we'd get wrong thing here
        nfa.pushEdgeOther(s0, s2)

        val s0Edges: List<NfaStateIndex> = nfa.noopEdges(s0).map { it.to }.toList()
        val s1Edges: List<NfaStateIndex> = nfa.noopEdges(s1).map { it.to }.toList()
        val s2Edges: List<NfaStateIndex> = nfa.noopEdges(s2).map { it.to }.toList()
        val s3Edges: List<NfaStateIndex> = nfa.noopEdges(s3).map { it.to }.toList()

        val s0OtherEdges: List<NfaStateIndex> = nfa.otherEdges(s0).map { it.to }.toList()
        val s0TestEdges: List<NfaStateIndex> = nfa.testEdges(s0).map { it.to }.toList()

        assertEquals(listOf(s1, s3), s0Edges)
        assertEquals(listOf(s2), s1Edges)
        assertEquals(listOf(s3), s2Edges)
        assertEquals(emptyList(), s3Edges)

        assertEquals(listOf(s2), s0OtherEdges)
        assertEquals(emptyList(), s0TestEdges)
    }

    @Test
    fun identifierRegex() {
        val ident = parseRegex("""[a-zA-Z_][a-zA-Z0-9_]*""").getOrThrow()
        val nfa = Nfa.fromRe(ident).getOrThrow()
        assertNull(interpret(nfa, "0123"))
        assertEquals("hello0123", interpret(nfa, "hello0123"))
        assertEquals("hello0123", interpret(nfa, "hello0123 abc"))
        assertEquals("_0123", interpret(nfa, "_0123 abc"))
    }

    @Test
    fun regexStarGroup() {
        val ident = parseRegex("""(abc)*""").getOrThrow()
        val nfa = Nfa.fromRe(ident).getOrThrow()
        assertEquals("abcabcabc", interpret(nfa, "abcabcabcab"))
    }

    @Test
    fun regexNumber() {
        val num = parseRegex("""[0-9]+""").getOrThrow()
        val nfa = Nfa.fromRe(num).getOrThrow()
        assertEquals("123", interpret(nfa, "123"))
    }

    @Test
    fun dotNewline() {
        val num = parseRegex(""".""").getOrThrow()
        val nfa = Nfa.fromRe(num).getOrThrow()
        assertNull(interpret(nfa, "\n"))
    }

    @Test
    fun maxRange() {
        val num = parseRegex("""ab{2,4}""").getOrThrow()
        val nfa = Nfa.fromRe(num).getOrThrow()
        assertNull(interpret(nfa, "a"))
        assertNull(interpret(nfa, "ab"))
        assertEquals("abb", interpret(nfa, "abb"))
        assertEquals("abbb", interpret(nfa, "abbb"))
        assertEquals("abbbb", interpret(nfa, "abbbb"))
        assertEquals("abbbb", interpret(nfa, "abbbbb"))
        assertNull(interpret(nfa, "ac"))
    }

    @Test
    fun literal() {
        // This test requires regex unicode case support
        val num = parseRegex("""(?i:aBCdeF)""").getOrThrow()
        val nfa = Nfa.fromRe(num).getOrThrow()
        assertEquals("abcdef", interpret(nfa, "abcdef"))
        assertEquals("AbcDEf", interpret(nfa, "AbcDEf"))
    }

    // Test that uses of disallowed features trigger errors
    // during Nfa construction:

    @Test
    fun captures() {
        val num1 = parseRegex("""(aBCdeF)""").getOrThrow()
        Nfa.fromRe(num1).getOrThrow() // captures are ok

        val num2 = parseRegex("""(?:aBCdeF)""").getOrThrow()
        Nfa.fromRe(num2).getOrThrow() // non-captures are ok

        val num3 = parseRegex("""(?P<foo>aBCdeF)""").getOrThrow() // named captures are not
        assertEquals(
            NfaConstructionError.NamedCaptures,
            (Nfa.fromRe(num3).exceptionOrNull() as NfaConstructionException).error,
        )
    }

    @Test
    fun lineBoundaries() {
        val num1 = parseRegex("""^aBCdeF""").getOrThrow()
        assertEquals(
            NfaConstructionError.LookAround,
            (Nfa.fromRe(num1).exceptionOrNull() as NfaConstructionException).error,
        )

        val num2 = parseRegex("""aBCdeF$""").getOrThrow()
        assertEquals(
            NfaConstructionError.LookAround,
            (Nfa.fromRe(num2).exceptionOrNull() as NfaConstructionException).error,
        )
    }

    @Test
    fun textBoundaries() {
        val num1 = parseRegex("""(?m)^aBCdeF""").getOrThrow()
        assertEquals(
            NfaConstructionError.LookAround,
            (Nfa.fromRe(num1).exceptionOrNull() as NfaConstructionException).error,
        )

        val num2 = parseRegex("""(?m)aBCdeF$""").getOrThrow()
        assertEquals(
            NfaConstructionError.LookAround,
            (Nfa.fromRe(num2).exceptionOrNull() as NfaConstructionException).error,
        )
    }

    @Test
    fun wordBoundaries() {
        val num1 = parseRegex("""\baBCdeF""").getOrThrow()
        assertEquals(
            NfaConstructionError.LookAround,
            (Nfa.fromRe(num1).exceptionOrNull() as NfaConstructionException).error,
        )

        val num2 = parseRegex("""aBCdeF\B""").getOrThrow()
        assertEquals(
            NfaConstructionError.LookAround,
            (Nfa.fromRe(num2).exceptionOrNull() as NfaConstructionException).error,
        )
    }

    @Test
    fun issue101() {
        val num = parseRegex("""(1|0?)""").getOrThrow()
        Nfa.fromRe(num).getOrThrow()
    }
}
