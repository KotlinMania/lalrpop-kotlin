// port-lint: source src/normalize/prevalidate/test.rs
package io.github.kotlinmania.lalrpop.normalize.prevalidate

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

import io.github.kotlinmania.lalrpop.checkNormErr
import io.github.kotlinmania.lalrpop.normalize.NormErrorException
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import kotlin.test.Test
import kotlin.test.fail

private fun checkErr(expectedErr: String, grammar: String, span: String) {
    val parsedGrammar = parseGrammar(grammar).getOrThrow()
    val err = try {
        validate(parsedGrammar)
        fail("expected error for grammar")
    } catch (e: NormErrorException) {
        e.err
    }
    checkNormErr(expectedErr, span, err)
}

class PrevalidateTest {
    @Test
    fun namedSymbols() {
        checkErr(
            """named symbols \(like `"Num"`\) require a custom action""",
            """grammar; Term = { <n:"Num"> };""",
            """                     ~~~~~    """,
        )
    }

    @Test
    fun badAssocType() {
        checkErr(
            """associated type `Foo` not recognized""",
            """grammar; extern { type Foo = i32; enum Tok { } }""",
            """                       ~~~                      """,
        )
    }

    @Test
    fun dupAssocType() {
        checkErr(
            """associated type `Location` already specified""",
            """grammar; extern { type Location = i32; type Location = u32; enum Tok { } }""",
            """                                            ~~~~~~~~                      """,
        )
    }

    @Test
    fun lookaheadWithoutLocType() {
        checkErr(
            """lookahead/lookbehind require you to declare the type of a location""",
            """grammar; extern { enum Tok { } } Foo = @L;""",
            """                                       ~~ """,
        )
    }

    @Test
    fun multipleExternToken() {
        checkErr(
            """multiple extern definitions are not permitted""",
            """grammar; extern { enum Tok { } } extern { enum Tok { } }""",
            """                                 ~~~~~~                 """,
        )
    }

    @Test
    fun unrecognizedAttribute() {
        checkErr(
            """unrecognized attribute `foo`""",
            """grammar; #[foo] Term = ();""",
            """           ~~~            """,
        )
    }

    @Test
    fun duplicateAttribute() {
        checkErr(
            """duplicate attribute `inline`""",
            """grammar; #[inline] #[inline] Term = ();""",
            """                     ~~~~~~            """,
        )
    }

    @Test
    fun pubInlineAttribute() {
        checkErr(
            """public items cannot be marked #\[inline\]""",
            """grammar; #[inline] pub Term = ();""",
            """           ~~~~~~            """,
        )
    }

    @Test
    fun missingCfgAttributeArg() {
        checkErr(
            """`cfg` attributes take one argument""",
            """grammar; #[cfg] pub Term = ();""",
            """           ~~~                """,
        )
    }

    @Test
    fun multipleMatchToken() {
        checkErr(
            """multiple match definitions are not permitted""",
            """grammar; match { _ } match { _ }""",
            """                     ~~~~~      """,
        )
    }

    @Test
    fun matchAfterExternToken() {
        checkErr(
            """match and extern \(with custom tokens\) definitions are mutually exclusive""",
            """grammar; extern { enum Tok { } } match { _ }""",
            """                                 ~~~~~      """,
        )
    }

    @Test
    fun externAfterMatchToken() {
        checkErr(
            """extern \(with custom tokens\) and match definitions are mutually exclusive""",
            """grammar; match { _ } extern { enum Tok { } }""",
            """                     ~~~~~~                 """,
        )
    }

    @Test
    fun expandableExpressionRequiresNamedVariables() {
        checkErr(
            """Using `<>` between curly braces \(e.g., `\{<>\}`\) only works when your parsed values have been given names \(e.g., `<x:Foo>`, not just `<Foo>`\)""",
            """grammar; Term = { <A> => Foo {<>} };""",
            """                  ~~~~~~~~~~~~~~~~  """,
        )
    }

    @Test
    fun mixingNamesAndAnonymousValues() {
        checkErr(
            """anonymous symbols like this one cannot be combined with named symbols like `b:B`""",
            """grammar; Term = { <A> <b:B> => Alien: Eighth passenger of Nostromo};""",
            """                  ~~~                                               """,
        )
    }

    @Test
    fun publicMacros() {
        checkErr(
            """macros cannot be marked public""",
            """grammar; pub Comma<T> = (T ",")* T?;""",
            """             ~~~~~~~~               """,
        )
    }

    @Test
    fun alternativeUnrecognizedAttribute() {
        checkErr(
            """unrecognized attribute `foo`""",
            """grammar; Term = { #[foo(bar="baz")] "a" => () };""",
            """                    ~~~~~~~~~~~~~~              """,
        )
    }

    @Test
    fun missingPrecedence() {
        checkErr(
            """missing precedence attribute on the first alternative""",
            """grammar; Term = { "a" => (), #[precedence(level="1")] "b" => () };""",
            """                  ~~~~~~~~~                                       """,
        )
    }

    @Test
    fun cannotParsePrecedence() {
        checkErr(
            """could not parse the precedence level `a`, expected integer""",
            """grammar; Term = { #[precedence(level="a")] "a" => ()};""",
            """                    ~~~~~~~~~~~~~~~~~~~~~             """,
        )
    }

    @Test
    fun invalidLvlPrecedence() {
        checkErr(
            """invalid argument `foo` for precedence attribute, expected `level`""",
            """grammar; Term = { #[precedence(foo="1")] "a" => ()};""",
            """                    ~~~~~~~~~~~~~~~~~~~             """,
        )
    }

    @Test
    fun missingArgPrecedence() {
        checkErr(
            """missing argument for precedence attribute, expected `level`""",
            """grammar; Term = { #[precedence] "a" => ()};""",
            """                    ~~~~~~~~~~             """,
        )
    }

    @Test
    fun cannotParseAssoc() {
        checkErr(
            """could not parse the associativity `foo`, expected `left`, `right`, `none` or `all`""",
            """grammar; Term = { #[precedence(level="1")] #[assoc(side="foo")] "a" => ()};""",
            """                                             ~~~~~~~~~~~~~~~~~             """,
        )
    }

    @Test
    fun invalidAssoc() {
        checkErr(
            """invalid argument `foo` for associativity attribute, expected `side`""",
            """grammar; Term = { #[precedence(level="1")] #[assoc(foo="left")] "a" => ()};""",
            """                                             ~~~~~~~~~~~~~~~~~             """,
        )
    }

    @Test
    fun missingArgAssoc() {
        checkErr(
            """missing argument for associativity attribute, expected `side`""",
            """grammar; Term = { #[precedence(level="1")] #[assoc] "a" => ()};""",
            """                                             ~~~~~             """,
        )
    }

    @Test
    fun firstLevelAssoc() {
        checkErr(
            """cannot set associativity on the first precedence level 3""",
            """grammar; Term = { #[precedence(level="3")] #[assoc(side="left")] "a" => ()};""",
            """                                             ~~~~~~~~~~~~~~~~~~             """,
        )
    }

    @Test
    fun missingMacroArg() {
        checkErr(
            """macros must have at least one argument""",
            """grammar; Macro<Smth>: String = { Smth => <>.to_string() } pub Root: String = { Macro<>}""",
            """                                                                               ~~~~~~~""",
        )
    }
}
