// port-lint: source lr1/codegen/test_all.rs
/**
 * Test module for comparing code generation strategies
 *
 * The TestAll code generation strategy uses both parse tables and recursive ascent, and then
 * compares the parsing return values to ensure they are both identical.  This is for use in the
 * `lalrpop-test` test suite and not intended for external consumption.
 */
package io.github.kotlinmania.lalrpop.lr1.codegen

import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.lr1.State
import io.github.kotlinmania.lalrpop.lr1.TokenSet
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust

object TestAll {
    /**
     * Direct port of upstream `lr1::codegen::testAll::compile`. Mirrors
     * the [Ascent.compile]/[ParseTable.compile] entry-point pattern so
     * callers in `build/EmitRecursiveAscent.kt` can dispatch through
     * the `TestAll.compile` name regardless of which back-end the
     * grammar selects.
     */
    fun compile(
        grammar: Grammar,
        userStartSymbol: NonterminalString,
        startSymbol: NonterminalString,
        states: List<State<TokenSet>>,
        out: RustWrite,
    ) {
        compileTestAll(grammar, userStartSymbol, startSymbol, states, out)
    }
}

internal fun compileTestAll(
    grammar: Grammar,
    userStartSymbol: NonterminalString,
    startSymbol: NonterminalString,
    states: List<State<TokenSet>>,
    out: RustWrite,
) {
    val ascent = newTestAll(grammar, userStartSymbol, startSymbol, states, out)
    ascent.write()
}

private fun newTestAll(
    grammar: Grammar,
    userStartSymbol: NonterminalString,
    startSymbol: NonterminalString,
    states: List<State<TokenSet>>,
    out: RustWrite,
): CodeGenerator<TestAll> = CodeGenerator.new(
    grammar,
    userStartSymbol,
    startSymbol,
    states,
    out,
    true,
    "super",
    TestAll,
)

private fun CodeGenerator<TestAll>.write() {
    this.writeParseMod { this1 ->
        this1.writeParserFn()

        rust(this1.out, "#[rustfmt::skip]")
        rust(this1.out, "mod ${this1.prefix}ascent {")
        Ascent.compile(
            this1.grammar,
            this1.userStartSymbol,
            this1.startSymbol,
            this1.states,
            "super::super::super",
            this1.out,
        )
        val pubUse = "${this1.grammar.nonterminals.getValue(this1.userStartSymbol).visibility}" +
            "use self::${this1.prefix}parse${this1.startSymbol}::${this1.userStartSymbol}Parser;"
        rust(this1.out, pubUse)
        rust(this1.out, "}")

        rust(this1.out, "#[rustfmt::skip]")
        rust(this1.out, "mod ${this1.prefix}parse_table {")
        ParseTable.compile(
            this1.grammar,
            this1.userStartSymbol,
            this1.startSymbol,
            this1.states,
            "super::super::super",
            this1.out,
        )
        rust(this1.out, pubUse)
        rust(this1.out, "}")
    }
}

private fun CodeGenerator<TestAll>.writeParserFn() {
    this.startParserFn()

    if (this.grammar.internToken != null) {
        rust(this.out, "let _ = self.builder;")
    }
    // parse input using both methods:
    this.callDelegate("ascent")
    this.callDelegate("parse_table")

    // check that result is the same either way:
    rust(
        this.out,
        "assert_eq!(${this.prefix}ascent, ${this.prefix}parse_table);",
    )

    rust(this.out, "return ${this.prefix}ascent;")

    this.endParserFn()
}

private fun CodeGenerator<TestAll>.callDelegate(delegate: String) {
    val nonLifetimes: List<TypeParameter> = this
        .grammar
        .typeParameters
        .filter { tp ->
            when (tp) {
                is TypeParameter.LifetimeTp -> false
                is TypeParameter.Id -> true
            }
        }
        .toList()
    val parameters = if (nonLifetimes.isEmpty()) {
        ""
    } else if (this.grammar.internToken != null) {
        "::<${Sep(", ", nonLifetimes)}>"
    } else {
        "::<${Sep(", ", nonLifetimes)}, _, _>"
    }
    rust(
        this.out,
        "let ${this.prefix}$delegate = ${this.prefix}$delegate::${this.userStartSymbol}Parser::new().parse$parameters(",
    )
    for (parameter in this.grammar.parameters) {
        rust(this.out, "${parameter.name},")
    }
    if (this.grammar.internToken == null) {
        rust(this.out, "${this.prefix}tokens0.clone(),")
    }
    rust(this.out, ");")
}
