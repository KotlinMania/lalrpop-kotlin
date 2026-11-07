package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class FileTextTest {
    @Test
    fun lineColAndSpanStrHandleAsciiNewlinesTrailingNewlineAndEof() {
        val empty = FileText.test()
        assertEquals("", empty.text())
        assertEquals(0 to 0, empty.lineCol(0))
        assertEquals("", empty.lineText(0))
        assertEquals("test.lalrpop:1:1: 1:0", empty.spanStr(Span(0, 0)))
        assertEquals("", empty.spanText(Span(0, 0)))

        val ft = FileText.new("g.lalrpop", "abc\nde\n")
        assertEquals(listOf(0, 4, 7), ft.newlines)
        assertEquals(0 to 0, ft.lineCol(0))
        assertEquals(0 to 2, ft.lineCol(2))
        assertEquals(0 to 3, ft.lineCol(3))
        assertEquals(1 to 0, ft.lineCol(4))
        assertEquals(1 to 2, ft.lineCol(6))
        assertEquals(2 to 0, ft.lineCol(7))

        assertEquals("abc", ft.lineText(0))
        assertEquals("de", ft.lineText(1))
        assertEquals("", ft.lineText(2))

        assertEquals("abc", ft.spanText(Span(0, 3)))
        assertEquals("de", ft.spanText(Span(4, 6)))
        assertEquals("", ft.spanText(Span(7, 7)))
        assertEquals("g.lalrpop:1:1: 1:3", ft.spanStr(Span(0, 3)))
        assertEquals("g.lalrpop:2:1: 2:2", ft.spanStr(Span(4, 6)))
        assertEquals("g.lalrpop:3:1: 3:0", ft.spanStr(Span(7, 7)))

        assertEquals("", Repeat('~', 0).toString())
        assertEquals("~~~~", Repeat('~', 4).toString())
        val repeatBuf = StringBuilder().also { Repeat(' ', 3).fmt(it) }
        assertEquals("   ", repeatBuf.toString())

        assertTrue(FileText.fromPath("__nonexistent_file_text_slice__.lalrpop").isFailure)
    }

    @Test
    fun utf8ByteIndexingAcrossMultibyteBmpAndSupplementaryUnicode() {
        val ft = FileText.new("unicode.lalrpop", "aé語\n\uD83E\uDD80b")
        assertEquals(listOf(0, 7), ft.newlines)
        assertEquals(0 to 0, ft.lineCol(0))
        assertEquals(0 to 1, ft.lineCol(1))
        assertEquals(0 to 3, ft.lineCol(3))
        assertEquals(0 to 6, ft.lineCol(6))
        assertEquals(1 to 0, ft.lineCol(7))
        assertEquals(1 to 4, ft.lineCol(11))
        assertEquals(1 to 5, ft.lineCol(12))

        assertEquals("aé語", ft.lineText(0))
        assertEquals("\uD83E\uDD80b", ft.lineText(1))

        assertEquals("a", ft.spanText(Span(0, 1)))
        assertEquals("é", ft.spanText(Span(1, 3)))
        assertEquals("語", ft.spanText(Span(3, 6)))
        assertEquals("\uD83E\uDD80", ft.spanText(Span(7, 11)))
        assertEquals("b", ft.spanText(Span(11, 12)))
        assertEquals("é語\n\uD83E\uDD80", ft.spanText(Span(1, 11)))

        assertEquals("unicode.lalrpop:1:4: 1:6", ft.spanStr(Span(3, 6)))
        assertEquals("unicode.lalrpop:2:1: 2:4", ft.spanStr(Span(7, 11)))
        assertEquals("unicode.lalrpop:2:5: 2:5", ft.spanStr(Span(11, 12)))

        assertFails { ft.spanText(Span(1, 2)) }
        assertFails { ft.spanText(Span(2, 2)) }
        assertFails { ft.spanText(Span(8, 11)) }
        assertFails { ft.spanText(Span(0, 13)) }
    }

    @Test
    fun highlightSingleLineCoversZeroOneTwoAndWiderSpansIncludingMultibyte() {
        val ascii = FileText.new("single.lalrpop", "hello world")

        val zeroAtStart = StringBuilder().also { ascii.highlight(Span(0, 0), it) }.toString()
        assertEquals("  hello world\n  ^\n", zeroAtStart)

        val singleByte = StringBuilder().also { ascii.highlight(Span(6, 7), it) }.toString()
        assertEquals("  hello world\n        ^\n", singleByte)

        val twoBytes = StringBuilder().also { ascii.highlight(Span(6, 8), it) }.toString()
        assertEquals("  hello world\n        ~~\n", twoBytes)

        val fiveBytes = StringBuilder().also { ascii.highlight(Span(6, 11), it) }.toString()
        assertEquals("  hello world\n        ~~~~~\n", fiveBytes)

        val zeroAtEof = StringBuilder().also { ascii.highlight(Span(11, 11), it) }.toString()
        assertEquals("  hello world\n             ^\n", zeroAtEof)

        val unicode = FileText.new("utf8.lalrpop", "aé\uD83E\uDD80z\nnext")
        val twoByteChar = StringBuilder().also { unicode.highlight(Span(1, 3), it) }.toString()
        assertEquals("  aé\uD83E\uDD80z\n   ~~\n", twoByteChar)

        val fourByteScalar = StringBuilder().also { unicode.highlight(Span(3, 7), it) }.toString()
        assertEquals("  aé\uD83E\uDD80z\n     ~~~~\n", fourByteScalar)

        val trailingAscii = StringBuilder().also { unicode.highlight(Span(7, 8), it) }.toString()
        assertEquals("  aé\uD83E\uDD80z\n         ^\n", trailingAscii)

        val secondLine = StringBuilder().also { unicode.highlight(Span(9, 13), it) }.toString()
        assertEquals("  next\n  ~~~~\n", secondLine)
    }

    @Test
    fun highlightMultilineMatchesRustByteMaxLenAndScalarPadding() {
        val asciiMulti = FileText.new("multi.lalrpop", "alpha\nbeta gamma\ndelta")
        val asciiRendered = StringBuilder().also { asciiMulti.highlight(Span(2, 20), it) }.toString()
        assertEquals(
            "    ~~~~~~~~~+\n" +
                "| alpha      |\n" +
                "| beta gamma |\n" +
                "| delta\n" +
                "+~~~~\n",
            asciiRendered,
        )

        val supplementaryMulti = FileText.new("supp.lalrpop", "\uD83E\uDD80\nabcdef")
        val supplementaryRendered = StringBuilder().also { supplementaryMulti.highlight(Span(0, 7), it) }.toString()
        assertEquals(
            "  ~~~~~~~+\n" +
                "| \uD83E\uDD80      |\n" +
                "| abcdef\n" +
                "+~~~\n",
            supplementaryRendered,
        )

        val multibyteMaxFirst = FileText.new("mb.lalrpop", "ééé\na")
        val multibyteRendered = StringBuilder().also { multibyteMaxFirst.highlight(Span(4, 8), it) }.toString()
        assertEquals(
            "      ~~~+\n" +
                "| ééé    |\n" +
                "| a\n" +
                "+~~\n",
            multibyteRendered,
        )
    }
}
