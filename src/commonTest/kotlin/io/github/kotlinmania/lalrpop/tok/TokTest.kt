// port-lint: source src/tok/test.rs
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
    // use $ to signal EOL because it can be replaced with a single space
    // for spans, and because it applies also to r#XXX# style strings:
    val input = rawInput.replace('$', '\n')

    val tokenizer = Tokenizer(input, 0)
    val len = expected.size
    for ((expectedSpan, expectation) in expected) {
        val token = tokenizer.nextResult()
            ?: throw AssertionError("tokenizer ran out before expectations")
        val expectedStart = expectedSpan.indexOf('~')
        val expectedEnd = expectedSpan.lastIndexOf('~') + 1
        when (expectation) {
            is Expectation.ExpectTok -> {
                assertEquals(
                    Spanned(expectedStart, expectation.tok, expectedEnd),
                    token.getOrThrow(),
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
    fun eol_comment() {
        test(
            "extern // This is a comment\$ foo",
            listOf(
                "~~~~~~                          " to Tok.Extern,
                "                             ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun block_comment() {
        test(
            "extern /* This is a block comment */\$ foo",
            listOf(
                "~~~~~~                                   " to Tok.Extern,
                "                                      ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun block_comment_in_code() {
        test(
            "=> ( test /* foo ) */ ),",
            listOf(
                "~~~~~~~~~~~~~~~~~~~~~~~ " to Tok.EqualsGreaterThanCode(" ( test /* foo ) */ )"),
                "                       ~" to Tok.Comma,
            ),
        )
    }

    @Test
    fun nested_block_comment() {
        test(
            "extern /* This is a /* nested */ block comment */\$ foo",
            listOf(
                "~~~~~~                                                " to Tok.Extern,
                "                                                   ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun block_comment_3_star() {
        test(
            "extern /***/\$ foo",
            listOf(
                "~~~~~~           " to Tok.Extern,
                "              ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun block_comment_nested_3_star_with_linefeeds() {
        test(
            "extern /** /***/ \$*/\$ foo",
            listOf(
                "~~~~~~                   " to Tok.Extern,
                "                      ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun block_comment_5_star() {
        test(
            "extern /*****/\$ foo",
            listOf(
                "~~~~~~             " to Tok.Extern,
                "                ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun block_comment_1_2_star() {
        test(
            "extern /* **/\$ foo",
            listOf(
                "~~~~~~            " to Tok.Extern,
                "               ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun block_comment_extra_slashes() {
        test(
            "extern /*//**/*/\$ foo",
            listOf(
                "~~~~~~               " to Tok.Extern,
                "                  ~~~" to Tok.Id("foo"),
            ),
        )
    }

    @Test
    fun unterminated_block_comment() {
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
    fun rule_id_then_equalsgreaterthancode_functioncall() {
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
    fun rule_stringliteral_slash_dot_then_equalsgreaterthancode_functioncall() {
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
    fun rule_stringliteral_slash_dot_then_equalsgreaterthancode_many_characters_in_stringliteral() {
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
    fun rule_stringliteral_slash_dot_then_equalsgreaterthancode_one_character_dot_in_stringliteral() {
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
    fun rule_stringliteral_slash_openningbracket_then_equalsgreaterthancode_one_character_openningbracket_in_stringliteral() {
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
    fun rule_stringliteral_slash_openningbracket_then_equalsgreaterthancode_empty_stringliteral() {
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
    fun rule_stringliteral_slash_dot_then_equalsgreaterthancode_one_character_dot() {
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
    fun rule_stringliteral_slash_openningbracket_then_equalsgreaterthancode_one_character_openningbracket() {
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
    fun equalsgreaterthancode_one_character_openningbracket() {
        test(
            """=> '(' ,""",
            listOf(
                """~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '(' """),
                """       ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_one_character_escaped_n() {
        test(
            """=> '\n' ,""",
            listOf(
                """~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '\n' """),
                """        ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_one_character_escaped_w() {
        test(
            """=> '\w' ,""",
            listOf(
                """~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '\w' """),
                """        ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_one_character_escaped_planet123() {
        test(
            """=> '\planet123' ,""",
            listOf(
                """~~~~~~~~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '\planet123' """),
                """                ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_one_character_openningcurlybracket() {
        test(
            """=> '{' ,""",
            listOf(
                """~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '{' """),
                """       ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_one_character_openningsquarebracket() {
        test(
            """=> '[' ,""",
            listOf(
                """~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" '[' """),
                """       ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_one_character_openningbracket_wrapped_by_brackets() {
        test(
            """=> ('(') ,""",
            listOf(
                """~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" ('(') """),
                """         ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_one_character_closingbracket_wrapped_by_brackets() {
        test(
            """=> (')') ,""",
            listOf(
                """~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" (')') """),
                """         ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_tuple() {
        test(
            """=> (1,2,3) ,""",
            listOf(
                """~~~~~~~~~~~ """ to Tok.EqualsGreaterThanCode(""" (1,2,3) """),
                """           ~""" to Tok.Comma,
            ),
        )
    }

    @Test
    fun equalsgreaterthancode_statement_with_lifetime() {
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
    fun equalsgreaterthancode_statement_with_many_lifetimes() {
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
    fun equalsgreaterthancode_nested_function_with_lifetimes() {
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
    fun where_with_lifetimes() {
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
    fun where_forall_fnmut_with_return_type() {
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
    fun equalsgreaterthancode_error_unbalanced() {
        testErr("""=> (,""", """~    """ to ErrorCode.UnterminatedCode)
    }

    @Test
    fun equalsgreaterthancode_error_unbalanced_closingbracket_character() {
        testErr("""=> (,')',""", """~        """ to ErrorCode.UnterminatedCode)
    }

    @Test
    fun equalsgreaterthancode_error_unterminated_string_literal() {
        testErr(
            """=>  "Jan III Sobieski""",
            """    ~                """ to ErrorCode.UnterminatedStringLiteral,
        )
    }

    @Test
    fun equalsgreaterthancode_error_unterminated_character_literal() {
        testErr(
            """=>  '\x233  """,
            """    ~       """ to ErrorCode.UnterminatedCharacterLiteral,
        )
    }

    @Test
    fun equalsgreaterthancode_error_end_of_input_instead_of_closing_normal_character_literal() {
        testErr(
            """=>  'x""",
            """    ~ """ to ErrorCode.UnterminatedCharacterLiteral,
        )
    }

    @Test
    fun equalsgreaterthancode_single_quote_literal() {
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
    fun code_paren() {
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
    fun code_regex_paren() {
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
    fun code_comment_eol() {
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
    fun code_forgot_comma() {
        // intentionally forget the comma token; this is more of a test of `test`
        assertFails {
            test(
                "=> a(b, c),",
                listOf("~~~~~~~~~~ " to Tok.EqualsGreaterThanCode(" a(b, c)")),
            )
        }
    }

    @Test
    fun various_kinds_of_ids() {
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
    fun string_literals() {
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
    fun hash_token() {
        test(""" # """, listOf(""" ~ """ to Tok.Hash))
    }

    @Test
    fun shebang_attribute_normal_text() {
        test(
            """ #![Attribute] """,
            listOf(""" ~~~~~~~~~~~~~ """ to Tok.ShebangAttribute("#![Attribute]")),
        )
    }

    @Test
    fun shebang_attribute_special_characters_without_quotes() {
        test(
            """ #![set width = 80] """,
            listOf(""" ~~~~~~~~~~~~~~~~~~ """ to Tok.ShebangAttribute("#![set width = 80]")),
        )
    }

    @Test
    fun shebang_attribute_special_characters_with_quotes() {
        test(
            """ #![set width = "80"] """,
            listOf(
                """ ~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.ShebangAttribute("""#![set width = "80"]"""),
            ),
        )
    }

    @Test
    fun shebang_attribute_special_characters_closing_sqbracket_in_string_literal() {
        test(
            """ #![set width = "80]"] """,
            listOf(
                """ ~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.ShebangAttribute("""#![set width = "80]"]"""),
            ),
        )
    }

    @Test
    fun shebang_attribute_special_characters_opening_sqbracket_in_string_literal() {
        test(
            """ #![set width = "[80"] """,
            listOf(
                """ ~~~~~~~~~~~~~~~~~~~~~ """ to
                    Tok.ShebangAttribute("""#![set width = "[80"]"""),
            ),
        )
    }

    @Test
    fun shebang_attribute_special_characters_nested_sqbrackets() {
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
    fun char_literals() {
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
    fun string_escapes() {
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
