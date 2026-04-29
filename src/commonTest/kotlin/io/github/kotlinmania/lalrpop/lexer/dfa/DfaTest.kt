// port-lint: source lexer/dfa/test.rs
package io.github.kotlinmania.lalrpop.lexer.dfa

import io.github.kotlinmania.lalrpop.lexer.parseRegex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun dfa(inputs: List<Pair<String, Precedence>>): Result<Dfa> {
    val regexs = inputs.map { (s, _) ->
        parseRegex(s).getOrElse { error("unexpected parse error") }
    }
    val precedences = inputs.map { (_, p) -> p }
    return buildDfa(regexs, precedences)
}

private val P1 = Precedence(1)
private val P0 = Precedence(0)

class DfaTest {
    @Test
    fun tokenizer() {
        val dfa = dfa(
            listOf(
                """class""" to P1,                  // 0
                """[a-zA-Z_][a-zA-Z0-9_]*""" to P0, // 1
                """[0-9]+""" to P0,                 // 2
                """ +""" to P0,                     // 3
                """>>""" to P0,                     // 4
                """>""" to P0,                      // 5
            ),
        ).getOrThrow()

        assertEquals(NfaIndex(0) to "class", interpret(dfa, "class Foo"))
        assertEquals(NfaIndex(1) to "classz", interpret(dfa, "classz Foo"))
        assertEquals(NfaIndex(2) to "123", interpret(dfa, "123"))
        assertEquals(NfaIndex(3) to "  ", interpret(dfa, "  classz Foo"))
        assertEquals(NfaIndex(5) to ">", interpret(dfa, ">"))
        assertEquals(NfaIndex(4) to ">>", interpret(dfa, ">>"))
    }

    @Test
    fun ambiguousRegex() {
        // here the keyword and the regex have same precedence, so we have
        // an ambiguity
        assertTrue(
            dfa(listOf("""class""" to P0, """[a-zA-Z_][a-zA-Z0-9_]*""" to P0)).isFailure,
        )
    }

    @Test
    fun issue32() {
        assertTrue(dfa(listOf(""".""" to P0)).isSuccess)
    }

    @Test
    fun issue35() {
        assertTrue(
            dfa(listOf(""".*""" to P0, """[-+]?[0-9]*\.?[0-9]+""" to P0)).isFailure,
        )
    }

    @Test
    fun alternatives() {
        val dfa = dfa(listOf("""abc|abd""" to P0)).getOrThrow()
        assertEquals(NfaIndex(0) to "abc", interpret(dfa, "abc"))
        assertEquals(NfaIndex(0) to "abd", interpret(dfa, "abd"))
        assertNull(interpret(dfa, "123"))
    }

    @Test
    fun alternativesExtension() {
        val dfa = dfa(listOf("""abc|abcd""" to P0)).getOrThrow()
        assertEquals(NfaIndex(0) to "abc", interpret(dfa, "abc"))
        assertEquals(NfaIndex(0) to "abcd", interpret(dfa, "abcd"))
        assertNull(interpret(dfa, "123"))
    }

    @Test
    fun alternativesContraction() {
        val dfa = dfa(listOf("""abcd|abc""" to P0)).getOrThrow()
        assertEquals(NfaIndex(0) to "abc", interpret(dfa, "abc"))
        assertEquals(NfaIndex(0) to "abcd", interpret(dfa, "abcd"))
        assertNull(interpret(dfa, "123"))
    }
}
