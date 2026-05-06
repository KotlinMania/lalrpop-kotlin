package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.buildLalrStates
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun parserEmitNt(text: String): NonterminalString =
    NonterminalString(Atom.from(text))

class KotlinParserEmitTest {
    @Test
    fun emitsTableDrivenKotlinParserSource() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
                grammar;
                extern { enum Tok { "a" => .., "b" => .. } }
                pub S: () = "a" "b" => ();
                """.trimIndent(),
            )

            val source = Lr1Tls.install(grammar.terminals).let { lr1Tls ->
                try {
                    val states = buildLalrStates(grammar, parserEmitNt("S"))
                    val productionCount = grammar.nonterminals.values.sumOf { it.productions.size }
                    emitTableDrivenKotlinParser(
                        grammar = grammar,
                        states = states,
                        acceptProductionId = 0,
                        actionBodies = List(productionCount) {
                            KotlinProductionBody(
                                lines = listOf(
                                    "Result.success(TinySymbol.Variant0(Unit))",
                                ),
                            ),
                        },
                        config = KotlinParserEmitConfig(
                            packageName = "generated.tiny",
                            parserClassName = "TinyParser",
                            symbolClassName = "TinySymbol",
                            tablesObjectName = "TinyTables",
                            locationType = "Int",
                            tokenType = "Tok",
                            errorType = "ParseUserError",
                            initialLocationExpression = "0",
                            tokenToTerminalIdExpression = "{ token -> token.terminalId }",
                            tokenToSymbolExpression = "{ terminalId, token -> token.toSymbol(terminalId) }",
                            mapActionFailureExpression = "{ cause -> throw cause }",
                            errorRecoverySymbolExpression = "{ recovery -> throw IllegalStateException(recovery.toString()) }",
                            expectedTokensExpression = "{ _ -> emptyList() }",
                            supportsErrorRecovery = false,
                        ),
                    )
                } finally {
                    lr1Tls.drop()
                }
            }

            assertTrue(source.contains("package generated.tiny"))
            assertTrue(source.contains("sealed class TinySymbol"))
            assertTrue(source.contains("object TinyTables"))
            assertTrue(source.contains("private val ACTION: ShortArray = shortArrayOf("))
            assertTrue(source.contains("private val EOF_ACTION: ShortArray = shortArrayOf("))
            assertTrue(source.contains("private val GOTO: ShortArray = shortArrayOf("))
            assertTrue(source.contains("private val PRODUCTIONS: Array<Production<TinySymbol, Int>>"))
            assertTrue(source.contains("TableDrivenParserDefinition<TinySymbol, Int, Tok, ParseUserError>"))
            assertTrue(source.contains("return Parser.drive(definition, tokens)"))
            assertTrue(source.contains("Result.success(TinySymbol.Variant0(Unit))"))
        }
    }

    @Test
    fun rejectsMissingProductionBodies() {
        Tls.test().use {
            val grammar = normalizedGrammar(
                """
                grammar;
                extern { enum Tok { "a" => .. } }
                pub S: () = "a" => ();
                """.trimIndent(),
            )

            Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                try {
                    val states = buildLalrStates(grammar, parserEmitNt("S"))
                    assertFailsWith<IllegalStateException> {
                        emitTableDrivenKotlinParser(
                            grammar = grammar,
                            states = states,
                            acceptProductionId = 0,
                            actionBodies = emptyList(),
                            config = KotlinParserEmitConfig(
                                packageName = null,
                                parserClassName = "TinyParser",
                                symbolClassName = "TinySymbol",
                                tablesObjectName = "TinyTables",
                                locationType = "Int",
                                tokenType = "Tok",
                                errorType = "ParseUserError",
                                initialLocationExpression = "0",
                                tokenToTerminalIdExpression = "{ _ -> null }",
                                tokenToSymbolExpression = "{ _, _ -> error(\"unreachable\") }",
                                mapActionFailureExpression = "{ cause -> throw cause }",
                                errorRecoverySymbolExpression = "{ _ -> error(\"unreachable\") }",
                                expectedTokensExpression = "{ _ -> emptyList() }",
                                supportsErrorRecovery = false,
                            ),
                        )
                    }
                } finally {
                    lr1Tls.drop()
                }
            }
        }
    }
}
