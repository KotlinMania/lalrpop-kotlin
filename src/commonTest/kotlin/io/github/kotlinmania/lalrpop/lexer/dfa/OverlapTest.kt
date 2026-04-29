// port-lint: source lexer/dfa/overlap.rs
package io.github.kotlinmania.lalrpop.lexer.dfa

import io.github.kotlinmania.lalrpop.collections.set.set
import io.github.kotlinmania.lalrpop.lexer.nfa.Test as NfaTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Helper that mirrors the upstream `macroRules! test!` in `overlap.rs`: collect a
 * set of inclusive `Char` ranges, call [removeOverlap], and map the resulting
 * [Test]s back into `CharRange`s for readable comparison.
 */
private fun runOverlap(ranges: List<CharRange>): List<CharRange> {
    val s = set<NfaTest>()
    for (r in ranges) {
        s.add(NfaTest.inclusiveRange(r.first, r.last))
    }
    return removeOverlap(s).map { r ->
        r.start().toInt().toChar()..r.end().toInt().toChar()
    }
}

class OverlapTest {
    @Test
    fun alphabet() {
        val result = runOverlap(listOf('a'..'z', 'c'..'l', '0'..'9'))
        assertEquals(listOf('0'..'9', 'a'..'b', 'c'..'l', 'm'..'z'), result)
    }

    @Test
    fun repeat() {
        val result = runOverlap(listOf('a'..'z', 'c'..'l', 'l'..'z', '0'..'9'))
        assertEquals(
            listOf('0'..'9', 'a'..'b', 'c'..'k', 'l'..'l', 'm'..'z'),
            result,
        )
    }

    @Test
    fun stagger() {
        val result = runOverlap(listOf('0'..'3', '2'..'4', '3'..'5'))
        assertEquals(
            listOf('0'..'1', '2'..'2', '3'..'3', '4'..'4', '5'..'5'),
            result,
        )
    }

    @Test
    fun emptyRange() {
        val result = runOverlap(listOf('b'..'b', 'a'..'z'))
        assertEquals(listOf('a'..'a', 'b'..'b', 'c'..'z'), result)
    }

    @Test
    fun `null`() {
        val result = runOverlap(listOf('\u0000'..'\u0000', '\u0000'..'a'))
        assertEquals(listOf('\u0000'..'\u0000', 1.toChar()..'a'), result)
    }
}
