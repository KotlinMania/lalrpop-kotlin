// port-lint: source normalize/norm_util.rs
package io.github.kotlinmania.lalrpop.normalize.normutil

import kotlin.test.Test
import kotlin.test.assertEquals

class NormUtilCheckBetweenBraces {
    @Test
    fun detectingNormalFunkyExpression() {
        assertEquals(Presence.Normal, checkBetweenBraces("<>"))
        assertEquals(Presence.Normal, checkBetweenBraces("ble <> blaa"))
        assertEquals(Presence.Normal, checkBetweenBraces("ble <> } b"))
        assertEquals(Presence.Normal, checkBetweenBraces("bl{ e <> } b"))
        assertEquals(Presence.Normal, checkBetweenBraces("bl{ e <>} b"))
        assertEquals(Presence.Normal, checkBetweenBraces("bl{ e <> e } b"))
        assertEquals(Presence.Normal, checkBetweenBraces("bl{ <> e } b"))
        assertEquals(Presence.Normal, checkBetweenBraces("bl{<> e } b"))
        assertEquals(Presence.Normal, checkBetweenBraces("bl{<>"))
        assertEquals(Presence.Normal, checkBetweenBraces("<>}"))
    }

    @Test
    fun detectingNopresenceOfFunkyExpression() {
        assertEquals(Presence.None, checkBetweenBraces("< >"))
        assertEquals(Presence.None, checkBetweenBraces("ble <b> blaa"))
    }

    @Test
    fun detectingIncurlybracketsFunkyExpression() {
        assertEquals(Presence.InCurlyBrackets, checkBetweenBraces("{<>}"))
        assertEquals(Presence.InCurlyBrackets, checkBetweenBraces("ble{<> }blaa"))
        assertEquals(Presence.InCurlyBrackets, checkBetweenBraces("ble{ <> } b"))
        assertEquals(Presence.InCurlyBrackets, checkBetweenBraces("bl{         <>} b"))
        assertEquals(Presence.InCurlyBrackets, checkBetweenBraces("bl{<>} b"))
        assertEquals(Presence.InCurlyBrackets, checkBetweenBraces("bl{<>         } b"))
    }

    @Test
    fun inCurlyBracesWithQuotes() {
        assertEquals(Presence.Normal, checkBetweenBraces("\"a{<>}\""))
        assertEquals(Presence.Normal, checkBetweenBraces("\"foo\",\"a{<>}\""))
        assertEquals(Presence.InCurlyBrackets, checkBetweenBraces("\"foo\",a{<>}"))
    }
}
