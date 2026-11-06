package io.github.kotlinmania.lalrpop

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilTest {
    @Test
    fun sepFormatsEmptySingleAndMultipleDistinctElements() {
        assertEquals("", Sep(", ", emptyList<String>()).toString())
        assertEquals("alpha", Sep(", ", listOf("alpha")).toString())
        assertEquals("alpha, beta, gamma", Sep(", ", listOf("alpha", "beta", "gamma")).toString())
        assertEquals("1::2::3", Sep("::", listOf(1, 2, 3)).toString())

        val sep = Sep(" | ", listOf("left", "middle", "right"))
        val (delimiter, items) = sep
        assertEquals(" | ", delimiter)
        assertEquals(listOf("left", "middle", "right"), items)
        val out = StringBuilder()
        sep.fmt(out)
        assertEquals("left | middle | right", out.toString())
    }

    @Test
    fun prefixFormatsEmptySingleAndMultipleDistinctElements() {
        assertEquals("", Prefix(" ", emptyList<String>()).toString())
        assertEquals(" x", Prefix(" ", listOf("x")).toString())
        assertEquals(" x y z", Prefix(" ", listOf("x", "y", "z")).toString())
        assertEquals(" -> 10 -> 20", Prefix(" -> ", listOf(10, 20)).toString())

        val prefixed = Prefix(" ::", listOf("first", "second"))
        val (prefix, items) = prefixed
        assertEquals(" ::", prefix)
        assertEquals(listOf("first", "second"), items)
        val out = StringBuilder()
        prefixed.fmt(out)
        assertEquals(" ::first ::second", out.toString())
    }

    @Test
    fun escapePreservesAlphanumericsDoublesUnderscoreAndHexEscapesUnicodeScalars() {
        assertEquals("", Escape("").toString())
        assertEquals("abcXYZ019", Escape("abcXYZ019").toString())
        assertEquals("42", Escape(42).toString())
        assertEquals("_2d7", Escape(-7).toString())
        assertEquals("__", Escape("_").toString())
        assertEquals("a__b____c", Escape("a_b__c").toString())
        assertEquals("_2a", Escape("*").toString())
        assertEquals("_2b_2d_2f_3a", Escape("+-/:").toString())
        assertEquals("_28_29_5b_5d_7b_7d_20_3c_3e", Escape("()[]{} <>").toString())
        assertEquals("_0", Escape("\u0000").toString())
        assertEquals("_e9_3bb_8a9e", Escape("éλ語").toString())
        assertEquals("_10000", Escape("\uD800\uDC00").toString())
        assertEquals("_1f980", Escape("\uD83E\uDD80").toString())
        assertEquals("_10ffff", Escape("\uDBFF\uDFFF").toString())
        assertEquals("fn__1_1d54f___1f600_21", Escape("fn_1\uD835\uDD4F_\uD83D\uDE00!").toString())

        val escaped = Escape("Nt_Name")
        assertEquals("Nt_Name", escaped.field0)
        escaped.field0 = "Nt_\uD83E\uDD80"
        val out = StringBuilder()
        escaped.fmt(out)
        assertEquals("Nt___1f980", out.toString())
    }

    @Test
    fun stripRemovesLeadingAndTrailingUnicodeWhitespacePreservingInterior() {
        assertEquals("", strip(""))
        assertEquals("", strip("   \t\r\n  "))
        assertEquals("hello", strip("hello"))
        assertEquals("hello", strip("  hello  "))
        assertEquals(
            "use std::str::FromStr;",
            strip("\n    use std::str::FromStr;\r\n  "),
        )
        assertEquals(
            "fn action(x: i32) -> i32 {\n    x + 1\n}",
            strip("\t\n fn action(x: i32) -> i32 {\n    x + 1\n} \r\n"),
        )
        assertEquals("a \t\n b", strip("  a \t\n b  "))
        assertEquals("trimmed", strip("\u00A0\u3000trimmed\u3000\u00A0"))
    }
}
