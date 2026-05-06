// port-lint: source tok/test.rs
package io.github.kotlinmania.lalrpop.tok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

private sealed class Expectation {
    data class ExpectTok(val tok: Tok) : Expectation()
    data class ExpectErr(val code: ErrorCode) : Expectation()
}

private fun genTest(rawInput: String, expected: List<Pair<String, Expectation>>) {
    val input = rawInput

    val tokenizer = Tokenizer(input, 0)
    val len = expected.size
    for ((expectedSpan, expectation) in expected) {
        val token = tokenizer.nextResult()
            ?: throw AssertionError("tokenizer ran out before expectations")
        val expectedStart = expectedSpan.indexOf('~')
        val expectedEnd = expectedSpan.lastIndexOf('~') + 1
        when (expectation) {
            is Expectation.ExpectTok -> {
                val actual = token.getOrElse { ex ->
                    val err = (ex as TokError).err
                    throw AssertionError("unexpected tokenizer error at ${err.location}: ${err.code}")
                }
                assertEquals(
                    Spanned(expectedStart, expectation.tok, expectedEnd),
                    actual,
                )
            }
            is Expectation.ExpectErr -> {
                val err = (token.exceptionOrNull() as TokError).err
                assertEquals(Error(location = expectedStart, code = expectation.code), err)
            }
        }
    }

    val tokenizer2 = Tokenizer(input, 0)
    repeat(len) { tokenizer2.nextResult() }
    assertNull(tokenizer2.nextResult())
}

private fun test(input: String, expected: List<Pair<String, Tok>>) {
    genTest(input, expected.map { (s, t) -> s to Expectation.ExpectTok(t) })
}

private fun testErr(input: String, expected: Pair<String, ErrorCode>) {
    val (span, ec) = expected
    genTest(input, listOf(span to Expectation.ExpectErr(ec)))
}

class TokTest {
    @Test
    fun basic() {
        test(
            "extern foo",
            listOf("~~~~~~    " to Tok.Extern, "       ~~~" to Tok.Id("foo")),
        )
    }

    @Test
    fun eolComment() {
        test(
            "extern // This is a comment\n foo",
            listOf(
                "~~~~~~                          " to Tok.Extern,
                "                             ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun blockComment() {
        test(
            "extern /* This is a block comment */\n foo",
            listOf(
                "~~~~~~                                   " to Tok.Extern,
                "                                      ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun blockCommentInCode() {
        test(
            "=> ( test /* foo ) */ ),",
            listOf(
                "~~~~~~~~~~~~~~~~~~~~~~~ " to Tok.EqualsGreaterThanCode(" ( test /* foo ) */ )"),
                "                       ~" to Tok.Comma,
            ),
        )
    }

    @Test
    fun nestedBlockComment() {
        test(
            "extern /* This is a /* nested */ block comment */\n foo",
            listOf(
                "~~~~~~                                                " to Tok.Extern,
                "                                                   ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun blockComment3Star() {
        test(
            "extern /***/\n foo",
            listOf(
                "~~~~~~           " to Tok.Extern,
                "              ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun blockCommentNested3StarWithLinefeeds() {
        test(
            "extern /** /***/ \n*/\n foo",
            listOf(
                "~~~~~~                   " to Tok.Extern,
                "                      ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun blockComment5Star() {
        test(
            "extern /*****/\n foo",
            listOf(
                "~~~~~~             " to Tok.Extern,
                "                ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun blockComment12Star() {
        test(
            "extern /* **/\n foo",
            listOf(
                "~~~~~~            " to Tok.Extern,
                "               ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun blockCommentExtraSlashes() {
        test(
            "extern /*//**/*/\n foo",
            listOf(
                "~~~~~~               " to Tok.Extern,
                "                  ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun unterminatedBlockComment() {
        testErr(
            "/* This is unterminated",
            "~                      " to ErrorCode.UnterminatedBlockComment,
        )
    }

    @Test
    fun code1() {
        test(
            "=> a(b, c),",
            listOf(
                "~~~~~~~~~~ " to Tok.EqualsGreaterThanCode(" a(b, c)"),
                "          ~" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleIdThenEqualsgreaterthancodeFunctioncall() {
        test(
            "id => a(b, c),",
            listOf(
                "~~            " to Tok.Id("id"),
                "   ~~~~~~~~~~ " to Tok.EqualsGreaterThanCode(" a(b, c)"),
                "             ~" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleStringliteralSlashDotThenEqualsgreaterthancodeFunctioncall() {
        test(
            """ "\." => a(b, c),""",
            listOf(
                """ ~~~~            """ to Tok.StringLiteral("""\."""),
                """      ~~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(" a(b, c)"),
                """                ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleStringliteralSlashDotThenEqualsgreaterthancodeManyCharactersInStringliteral() {
        test(
            """ "\." => "Planet Earth" ,""",
            listOf(
                """ ~~~~                    """ to Tok.StringLiteral("""\."""),
                """      ~~~~~~~~~~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" "Planet Earth" """),
                """                        ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleStringliteralSlashDotThenEqualsgreaterthancodeOneCharacterDotInStringliteral() {
        test(
            """ "\." => "." ,""",
            listOf(
                """ ~~~~         """ to Tok.StringLiteral("""\."""),
                """      ~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" "." """),
                """             ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleStringliteralSlashOpenningbracketThenEqualsgreaterthancodeOneCharacterOpenningbracketInStringliteral() {
        test(
            """ "\(" => "(" ,""",
            listOf(
                """ ~~~~         """ to Tok.StringLiteral("""\("""),
                """      ~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" "(" """),
                """             ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleStringliteralSlashOpenningbracketThenEqualsgreaterthancodeEmptyStringliteral() {
        test(
            """ "\(" => "" ,""",
            listOf(
                """ ~~~~        """ to Tok.StringLiteral("""\("""),
                """      ~~~~~~ """ to Tok.EqualsGreaterThanCode(""" "" """),
                """            ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleStringliteralSlashDotThenEqualsgreaterthancodeOneCharacterDot() {
        test(
            """ "\." => '.' ,""",
            listOf(
                """ ~~~~         """ to Tok.StringLiteral("""\."""),
                """      ~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '.' """),
                """             ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun ruleStringliteralSlashOpenningbracketThenEqualsgreaterthancodeOneCharacterOpenningbracket() {
        test(
            """ "\(" => '(' ,""",
            listOf(
                """ ~~~~         """ to Tok.StringLiteral("""\("""),
                """      ~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '(' """),
                """             ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterOpenningbracket() {
        test(
            """=> '(' ,""",
            listOf(
                """~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '(' """),
                """       ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterEscapedN() {
        test(
            """=> '\n' ,""",
            listOf(
                """~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '\n' """),
                """        ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterEscapedW() {
        test(
            """=> '\w' ,""",
            listOf(
                """~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '\w' """),
                """        ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterEscapedPlanet123() {
        test(
            """=> '\planet123' ,""",
            listOf(
                """~~~~~~~~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '\planet123' """),
                """                ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterOpenningcurlybracket() {
        test(
            """=> '{' ,""",
            listOf(
                """~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '{' """),
                """       ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterOpenningsquarebracket() {
        test(
            """=> '[' ,""",
            listOf(
                """~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '[' """),
                """       ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterOpenningbracketWrappedByBrackets() {
        test(
            """=> ('(') ,""",
            listOf(
                """~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" ('(') """),
                """         ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeOneCharacterClosingbracketWrappedByBrackets() {
        test(
            """=> (')') ,""",
            listOf(
                """~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" (')') """),
                """         ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeTuple() {
        test(
            """=> (1,2,3) ,""",
            listOf(
                """~~~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" (1,2,3) """),
                """           ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeStatementWithLifetime() {
        test(
            """=> HuffmanTable::<Code<'a>>::new() ,""",
            listOf(
                """~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.EqualsGreaterThanCode(""" HuffmanTable::<Code<'a>>::new() """),
                """                                   ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeStatementWithManyLifetimes() {
        test(
            """=> (HuffmanTable::<Code<'a, 'b>>::new()),""",
            listOf(
                """~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.EqualsGreaterThanCode(""" (HuffmanTable::<Code<'a, 'b>>::new())"""),
                """                                        ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeNestedFunctionWithLifetimes() {
        test(
            """=> fn foo<'a>(x: &'a i32, y: &'a i32) -> &'a i32 {} ,""",
            listOf(
                """~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.EqualsGreaterThanCode(""" fn foo<'a>(x: &'a i32, y: &'a i32) -> &'a i32 {} """),
                """                                                    ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun whereWithLifetimes() {
        test(
            """where <'a,bar<'b,'c>>,baz;""",
            listOf(
                """~~~~~                     """ to Tok.Where,
                """      ~                   """ to Tok.LessThan,
                """       ~~                 """ to Tok.Lifetime("'a"),
                """         ~                """ to Tok.Comma,
                """          ~~~             """ to Tok.MacroId("bar"),
                """             ~            """ to Tok.LessThan,
                """              ~~          """ to Tok.Lifetime("'b"),
                """                ~         """ to Tok.Comma,
                """                 ~~       """ to Tok.Lifetime("'c"),
                """                   ~      """ to Tok.GreaterThan,
                """                    ~     """ to Tok.GreaterThan,
                """                     ~    """ to Tok.Comma,
                """                      ~~~ """ to Tok.Id("baz"),
                """                         ~""" to Tok.Semi,
            ),
        )
    }

    @Test
    fun forall() {
        test(
            """for<'a, 'b, 'c> FnMut""",
            listOf(
                """~~~                  """ to Tok.For,
                """   ~                 """ to Tok.LessThan,
                """    ~~               """ to Tok.Lifetime("'a"),
                """      ~              """ to Tok.Comma,
                """        ~~           """ to Tok.Lifetime("'b"),
                """          ~          """ to Tok.Comma,
                """            ~~       """ to Tok.Lifetime("'c"),
                """              ~      """ to Tok.GreaterThan,
                """                ~~~~~""" to Tok.Id("FnMut"),
            ),
        )
    }

    @Test
    fun whereForallFnmutWithReturnType() {
        test(
            """where F: for<'a> FnMut(&'a T) -> U;""",
            listOf(
                """~~~~~                              """ to Tok.Where,
                """      ~                            """ to Tok.Id("F"),
                """       ~                           """ to Tok.Colon,
                """         ~~~                       """ to Tok.For,
                """            ~                      """ to Tok.LessThan,
                """             ~~                    """ to Tok.Lifetime("'a"),
                """               ~                   """ to Tok.GreaterThan,
                """                 ~~~~~             """ to Tok.Id("FnMut"),
                """                      ~            """ to Tok.LeftParen,
                """                       ~           """ to Tok.Ampersand,
                """                        ~~         """ to Tok.Lifetime("'a"),
                """                           ~       """ to Tok.Id("T"),
                """                            ~      """ to Tok.RightParen,
                """                              ~~   """ to Tok.MinusGreaterThan,
                """                                 ~ """ to Tok.Id("U"),
                """                                  ~""" to Tok.Semi,
            ),
        )
    }

    @Test
    fun equalsgreaterthancodeErrorUnbalanced() {
        testErr("""=> (,""", """~    """ to ErrorCode.UnterminatedCode)
    }

    @Test
    fun equalsgreaterthancodeErrorUnbalancedClosingbracketCharacter() {
        testErr("""=> (,')',""", """~        """ to ErrorCode.UnterminatedCode)
    }

    @Test
    fun equalsgreaterthancodeErrorUnterminatedStringLiteral() {
        testErr(
            """=>  "Jan III Sobieski""",
            """    ~                """ to ErrorCode.UnterminatedStringLiteral,
        )
    }

    @Test
    fun equalsgreaterthancodeErrorUnterminatedCharacterLiteral() {
        testErr(
            """=>  '\x233  """,
            """    ~       """ to ErrorCode.UnterminatedCharacterLiteral,
        )
    }

    @Test
    fun equalsgreaterthancodeErrorEndOfInputInsteadOfClosingNormalCharacterLiteral() {
        testErr(
            """=>  'x""",
            """    ~ """ to ErrorCode.UnterminatedCharacterLiteral,
        )
    }

    @Test
    fun equalsgreaterthancodeSingleQuoteLiteral() {
        test(
            """=> { println!('\''); },""",
            listOf(
                """~~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.EqualsGreaterThanCode(""" { println!('\''); }"""),
                """                      ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun codeParen() {
        // Issue #25
        test(
            """=> a("(", c),""",
            listOf(
                """~~~~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" a("(", c)"""),
                """            ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun codeRegexParen() {
        // Issue #25
        test(
            """=> a(r##"("#""##, c),""",
            listOf(
                """~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.EqualsGreaterThanCode(""" a(r##"("#""##, c)"""),
                """                    ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun codeCommentEol() {
        test(
            "=> a(// (\n),",
            listOf(
                "~~~~~~~~~\n~," to Tok.EqualsGreaterThanCode(" a(// (\n)"),
                "=> a(// (\n)~" to Tok.Comma,
            ),
        )
    }

    @Test
    fun code2() {
        test(
            "=>? a(b, c),",
            listOf(
                "~~~~~~~~~~~ " to Tok.EqualsGreaterThanQuestionCode(" a(b, c)"),
                "           ~" to Tok.Comma,
            ),
        )
    }

    @Test
    fun codeForgotComma() {
        // intentionally forget the comma token; this is more of a test of `test`
        assertFails {
            test(
                "=> a(b, c),",
                listOf("~~~~~~~~~~ " to Tok.EqualsGreaterThanCode(" a(b, c)")),
            )
        }
    }

    @Test
    fun variousKindsOfIds() {
        test(
            "foo<T<'a,U,`Z*{}`,r#type,r#use>>",
            listOf(
                "~~~                             " to Tok.MacroId("foo"),
                "   ~                            " to Tok.LessThan,
                "    ~                           " to Tok.MacroId("T"),
                "     ~                          " to Tok.LessThan,
                "      ~~                        " to Tok.Lifetime("'a"),
                "        ~                       " to Tok.Comma,
                "         ~                      " to Tok.Id("U"),
                "          ~                     " to Tok.Comma,
                "           ~~~~~~               " to Tok.Escape("Z*{}"),
                "                 ~              " to Tok.Comma,
                "                  ~~~~~~        " to Tok.Id("r#type"),
                "                        ~       " to Tok.Comma,
                "                         ~~~~~  " to Tok.Id("r#use"),
                "                              ~ " to Tok.GreaterThan,
                "                               ~" to Tok.GreaterThan,
            ),
        )
    }

    @Test
    fun stringLiterals() {
        test(
            """foo "bar\"\n" baz""",
            listOf(
                """~~~              """ to Tok.Id("foo"),
                """    ~~~~~~~~~    """ to Tok.StringLiteral("""bar\"\n"""),
                """              ~~~""" to Tok.Id("baz"),
            ),
        )
    }

    @Test
    fun use1() {
        test(
            """use foo::bar; baz""",
            listOf(
                """~~~~~~~~~~~~     """ to Tok.Use(" foo::bar"),
                """            ~    """ to Tok.Semi,
                """              ~~~""" to Tok.Id("baz"),
            ),
        )
    }

    @Test
    fun use2() {
        test(
            """use {foo,bar}; baz""",
            listOf(
                """~~~~~~~~~~~~~     """ to Tok.Use(" {foo,bar}"),
                """             ~    """ to Tok.Semi,
                """               ~~~""" to Tok.Id("baz"),
            ),
        )
    }

    @Test
    fun where1() {
        test(
            """where <foo,bar>,baz;""",
            listOf(
                """~~~~~               """ to Tok.Where,
                """      ~             """ to Tok.LessThan,
                """       ~~~          """ to Tok.Id("foo"),
                """          ~         """ to Tok.Comma,
                """           ~~~      """ to Tok.Id("bar"),
                """              ~     """ to Tok.GreaterThan,
                """               ~    """ to Tok.Comma,
                """                ~~~ """ to Tok.Id("baz"),
                """                   ~""" to Tok.Semi,
            ),
        )
    }

    @Test
    fun regex1() {
        test(
            """raa r##" #"#"" "#"##rrr""",
            listOf(
                """~~~                    """ to Tok.Id("raa"),
                """    ~~~~~~~~~~~~~~~~   """ to Tok.RegexLiteral(""" #"#"" "#"""),
                """                    ~~~""" to Tok.Id("rrr"),
            ),
        )
    }

    @Test
    fun hashToken() {
        test(""" # """, listOf(""" ~ """ to Tok.Hash))
    }

    @Test
    fun shebangAttributeNormalText() {
        test(
            """ #![Attribute] """,
            listOf(""" ~~~~~~~~~~~~~ """ to Tok.ShebangAttribute("#![Attribute]")),
        )
    }

    @Test
    fun shebangAttributeSpecialCharactersWithoutQuotes() {
        test(
            """ #![set width = 80] """,
            listOf(""" ~~~~~~~~~~~~~~~~~~ """ to Tok.ShebangAttribute("#![set width = 80]")),
        )
    }

    @Test
    fun shebangAttributeSpecialCharactersWithQuotes() {
        test(
            """ #![set width = "80"] """,
            listOf(
                """ ~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.ShebangAttribute("""#![set width = "80"]"""),
            ),
        )
    }

    @Test
    fun shebangAttributeSpecialCharactersClosingSqbracketInStringLiteral() {
        test(
            """ #![set width = "80]"] """,
            listOf(
                """ ~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.ShebangAttribute("""#![set width = "80]"]"""),
            ),
        )
    }

    @Test
    fun shebangAttributeSpecialCharactersOpeningSqbracketInStringLiteral() {
        test(
            """ #![set width = "[80"] """,
            listOf(
                """ ~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.ShebangAttribute("""#![set width = "[80"]"""),
            ),
        )
    }

    @Test
    fun shebangAttributeSpecialCharactersNestedSqbrackets() {
        test(
            """ #![set width = [80]] """,
            listOf(
                """ ~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.ShebangAttribute("""#![set width = [80]]"""),
            ),
        )
    }

    @Test
    fun regex2() {
        test(
            """r"(123"""",
            listOf("""~~~~~~~""" to Tok.RegexLiteral("(123")),
        )
    }

    @Test
    fun charLiterals() {
        test(
            """'foo' 'a 'b '!' '!!' '\'' 'c""",
            listOf(
                """~~~~~                       """ to Tok.CharLiteral("foo"),
                """      ~~                    """ to Tok.Lifetime("'a"),
                """         ~~                 """ to Tok.Lifetime("'b"),
                """            ~~~             """ to Tok.CharLiteral("!"),
                """                ~~~~        """ to Tok.CharLiteral("!!"),
                """                     ~~~~   """ to Tok.CharLiteral("""\'"""),
                """                          ~~""" to Tok.Lifetime("'c"),
            ),
        )
    }

    @Test
    fun stringEscapes() {
        assertEquals("foo", applyStringEscapes("foo", 5).getOrThrow())
        assertEquals("""\""", applyStringEscapes("""\\""", 10).getOrThrow())
        assertEquals(""""""", applyStringEscapes("""\"""", 15).getOrThrow())
        assertEquals("up\ndown", applyStringEscapes("""up\ndown""", 25).getOrThrow())
        assertEquals("forth\rback", applyStringEscapes("""forth\rback""", 25).getOrThrow())
        assertEquals("left\tright", applyStringEscapes("""left\tright""", 40).getOrThrow())
        assertEquals("c-string\u0000", applyStringEscapes("""c-string\0""", 40).getOrThrow())
        assertEquals("back\u0008space", applyStringEscapes("""back\x08space""", 45).getOrThrow())
        assertEquals("xyz", applyStringEscapes("""xy\x7a""", 45).getOrThrow())

        // Errors.
        assertEquals(
            Error(location = 68, code = ErrorCode.UnrecognizedEscape),
            (applyStringEscapes("\u0192\\oo", 65).exceptionOrNull() as TokError).err,
        )
        // LALRPOP does not support the other Rust escape sequences.
        assertEquals(
            Error(location = 112, code = ErrorCode.UnrecognizedEscape),
            (applyStringEscapes("""star: \u{2a}""", 105).exceptionOrNull() as TokError).err,
        )
        // Raw ASCII escapes must be in 7-bit range
        assertEquals(
            Error(location = 17, code = ErrorCode.UnrecognizedEscape),
            (applyStringEscapes("""latin-\xb9""", 10).exceptionOrNull() as TokError).err,
        )
        // Raw ASCII escapes must contain two characters,
        // one octal and one hexadecimal.
        assertEquals(
            Error(location = 20, code = ErrorCode.UnrecognizedEscape),
            (applyStringEscapes("""back\x8space""", 15).exceptionOrNull() as TokError).err,
        )
        assertEquals(
            Error(location = 7, code = ErrorCode.UnterminatedAsciiEscape),
            (applyStringEscapes("""\x0""", 5).exceptionOrNull() as TokError).err,
        )
    }
}
