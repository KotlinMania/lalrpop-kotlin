package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.grammar.parsetree.GrammarItem
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.parser.LrParseErrorException
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import io.github.kotlinmania.lalrpop.tok.Tok
import io.github.kotlinmania.lalrpoputil.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiagnosticByteSpanRegressionTest {
    @Test
    fun reportParseErrorExtractsSupplementaryScalarAndRejectsInvalidByteBoundaries() {
        val src = "grammar; // \u00E9\u8A9E\n\uD83E\uDD80"
        val fileText = FileText.new("unicode.lalrpop", src)
        val crabByteOffset = src.encodeToByteArray().size - 4

        val reported =
            reportParseError(
                fileText,
                ParseError.InvalidToken(location = crabByteOffset),
            ) { ft, sp, msg ->
                val out = StringBuilder()
                ft.highlight(sp, out)
                ReportedDiagnostic(sp, ft.spanStr(sp), msg, out.toString())
            }

        assertEquals(Span(18, 18), reported.span)
        assertEquals("unicode.lalrpop:2:1: 2:0", reported.spanStr)
        assertEquals("invalid character `\uD83E\uDD80`", reported.message)
        assertEquals("  \uD83E\uDD80\n  ^\n", reported.highlighted)

        // Mid-scalar continuation byte must fail strictly, never substitute or clamp.
        assertFailsWith<IllegalArgumentException> {
            reportParseError(
                fileText,
                ParseError.InvalidToken(location = crabByteOffset + 1),
            ) { _, _, msg -> msg }
        }

        // EOF byte offset has no character to read for InvalidToken (Rust `.next().unwrap()` panics).
        assertFailsWith<NoSuchElementException> {
            reportParseError(
                fileText,
                ParseError.InvalidToken(location = crabByteOffset + 4),
            ) { _, _, msg -> msg }
        }
    }

    @Test
    fun reportParseErrorSlicesUnrecognizedAndExtraTokensByUtf8Bytes() {
        val src = "/* \u00E9\u8A9E */ \"\uD83E\uDD80\""
        val fileText = FileText.new("tokens.lalrpop", src)
        val lo = "/* \u00E9\u8A9E */ ".encodeToByteArray().size
        val hi = src.encodeToByteArray().size

        val (unrecSpan, unrecMsg) =
            reportParseError(
                fileText,
                ParseError.UnrecognizedToken(
                    token = Triple(lo, Tok.StringLiteral("\uD83E\uDD80"), hi),
                    expected = emptyList(),
                ),
            ) { _, sp, msg -> sp to msg }
        assertEquals(Span(12, 18), unrecSpan)
        assertEquals("unexpected token: `\"\uD83E\uDD80\"`", unrecMsg)

        val (extraSpan, extraMsg) =
            reportParseError(
                fileText,
                ParseError.ExtraToken(
                    token = Triple(lo, Tok.StringLiteral("\uD83E\uDD80"), hi),
                ),
            ) { _, sp, msg -> sp to msg }
        assertEquals(Span(12, 18), extraSpan)
        assertEquals("extra token at end of input: `\"\uD83E\uDD80\"`", extraMsg)

        assertFailsWith<IllegalArgumentException> {
            reportParseError(
                fileText,
                ParseError.UnrecognizedToken(
                    token = Triple(lo + 2, Tok.StringLiteral("\uD83E\uDD80"), hi),
                    expected = emptyList(),
                ),
            ) { _, _, msg -> msg }
        }
    }

    @Test
    fun endToEndParseGrammarErrorsAndAstSpansUseUtf8BytesAcrossMultibyteAndShiftedInputs() {
        // 1. UnrecognizedEof after multibyte comment
        val eofSrc = "/* \uD83E\uDD80 */ grammar;\npub Foo: i32 ="
        val eofFile = FileText.new("eof.lalrpop", eofSrc)
        val eofParseError = (parseGrammar(eofSrc).exceptionOrNull() as LrParseErrorException).parseError
        val (eofSpan, eofLocStr, eofMsg) =
            reportParseError(eofFile, eofParseError) { ft, sp, msg ->
                Triple(sp, ft.spanStr(sp), msg)
            }
        val expectedEofByte = eofSrc.encodeToByteArray().size
        assertEquals(Span(expectedEofByte, expectedEofByte), eofSpan)
        assertEquals("eof.lalrpop:2:15: 2:14", eofLocStr)
        assertEquals("unexpected end of file", eofMsg)

        // 2. UnrecognizedToken after multibyte comment + supplementary literal
        val badTokSrc = "/* \u00E9\u8A9E */ grammar;\npub Foo: i32 = \"\uD83E\uDD80\" );"
        val badTokFile = FileText.new("badtok.lalrpop", badTokSrc)
        val badTokError = (parseGrammar(badTokSrc).exceptionOrNull() as LrParseErrorException).parseError
        val (badTokSpan, badTokMsg) =
            reportParseError(badTokFile, badTokError) { _, sp, msg -> sp to msg }
        val rparenByte = badTokSrc.substringBefore(')').encodeToByteArray().size
        assertEquals(Span(rparenByte, rparenByte + 1), badTokSpan)
        assertEquals("unexpected token: `)`", badTokMsg)

        // 3. String escape error inside grammar action127 after supplementary scalar
        val badEscapeSrc = "/* \u00E9 */ grammar;\npub Foo: String = \"\uD83E\uDD80\\q\" => <>;"
        val badEscapeFile = FileText.new("escape.lalrpop", badEscapeSrc)
        val badEscapeErr = (parseGrammar(badEscapeSrc).exceptionOrNull() as LrParseErrorException).parseError
        val (escSpan, escLocStr, escMsg) =
            reportParseError(badEscapeFile, badEscapeErr) { ft, sp, msg ->
                Triple(sp, ft.spanStr(sp), msg)
            }
        val qByte = badEscapeSrc.substringBefore('q').encodeToByteArray().size
        assertEquals(Span(qByte, qByte + 1), escSpan)
        assertEquals("escape.lalrpop:2:25: 2:25", escLocStr)
        assertEquals(
            "unrecognized escape; only \\n, \\r, \\t, \\0, \\\", \\\\, and \\x## are recognized",
            escMsg,
        )

        // 4. Shifted inner tokenizer error via match mapping (`parseMatchMapping(p.second, start.second + 2)`)
        val shiftedMatchSrc = "/* \uD83E\uDD80 */ grammar;\nmatch {\n    \"a\" => \u00A7\n}"
        val shiftedMatchFile = FileText.new("match.lalrpop", shiftedMatchSrc)
        val shiftedMatchErr = (parseGrammar(shiftedMatchSrc).exceptionOrNull() as LrParseErrorException).parseError
        val (matchErrSpan, matchErrHighlight, matchErrMsg) =
            reportParseError(shiftedMatchFile, shiftedMatchErr) { ft, sp, msg ->
                val out = StringBuilder()
                ft.highlight(sp, out)
                Triple(sp, out.toString(), msg)
            }
        val sectionByte = shiftedMatchSrc.substringBefore('\u00A7').encodeToByteArray().size
        assertEquals(Span(sectionByte, sectionByte + 1), matchErrSpan)
        assertEquals("unrecognized token", matchErrMsg)
        assertEquals("      \"a\" => \u00A7\n             ^\n", matchErrHighlight)

        // 5. Valid Unicode grammar AST spans align with FileText.spanText
        val validSrc = "/* \uD83E\uDD80 */ grammar;\npub \uD835\uDD4F: String = \"\uD83E\uDD80\\n\" => <>;"
        val validFile = FileText.new("valid.lalrpop", validSrc)
        val parsed = parseGrammar(validSrc).getOrThrow()
        val nt = parsed.items.filterIsInstance<GrammarItem.Nonterminal>().single().data
        assertEquals("\uD835\uDD4F", validFile.spanText(nt.span))
    }

    private data class ReportedDiagnostic(
        val span: Span,
        val spanStr: String,
        val message: String,
        val highlighted: String,
    )
}
