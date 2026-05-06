package io.github.kotlinmania.lalrpop.lr1.kotlintarget

import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.kotlintarget.IndentedWriter
import io.github.kotlinmania.lalrpop.lr1.Lr1State
import io.github.kotlinmania.lalrpop.runtime.Production
import io.github.kotlinmania.lalrpop.runtime.ProductionAction

data class KotlinParserEmitConfig(
    val packageName: String?,
    val parserClassName: String,
    val symbolClassName: String,
    val tablesObjectName: String,
    val locationType: String,
    val tokenType: String,
    val errorType: String,
    val initialLocationExpression: String,
    val tokenToTerminalIdExpression: String,
    val tokenToSymbolExpression: String,
    val mapActionFailureExpression: String,
    val errorRecoverySymbolExpression: String,
    val expectedTokensExpression: String,
    val supportsErrorRecovery: Boolean,
)

data class KotlinProductionBody(
    val lines: List<String>,
) {
    init {
        require(lines.isNotEmpty()) { "production body must contain at least one line" }
    }
}

fun emitTableDrivenKotlinParser(
    grammar: Grammar,
    states: List<Lr1State>,
    acceptProductionId: Int,
    actionBodies: List<KotlinProductionBody>,
    config: KotlinParserEmitConfig,
): String {
    val productionRows = productionRows(grammar, actionBodies)
    val placeholderProductions = Array(productionRows.size) {
        Production<Unit, Int>(
            nonterminalId = productionRows[it].nonterminalId.toShort(),
            rhsLength = productionRows[it].rhsLength,
            action = ProductionAction { _, _ -> Result.success(Unit) },
        )
    }
    val tables = tablesFromLr1States(
        grammar = grammar,
        states = states,
        productions = placeholderProductions,
        acceptProductionId = acceptProductionId,
    )

    val out = IndentedWriter()
    if (config.packageName != null) {
        out.line("package ${config.packageName}")
        out.line()
    }
    out.line("import io.github.kotlinmania.lalrpop.runtime.ParseResult")
    out.line("import io.github.kotlinmania.lalrpop.runtime.ParseTables")
    out.line("import io.github.kotlinmania.lalrpop.runtime.Parser")
    out.line("import io.github.kotlinmania.lalrpop.runtime.Production")
    out.line("import io.github.kotlinmania.lalrpop.runtime.ProductionAction")
    out.line("import io.github.kotlinmania.lalrpop.runtime.TableDrivenParserDefinition")
    out.line("import io.github.kotlinmania.lalrpop.runtime.TokResult")
    out.line()

    KotlinSymbolEmit(grammar, config.symbolClassName).emitInto(out)
    out.line()
    emitTablesObject(out, config, tables, productionRows)
    out.line()
    emitParserClass(out, config)
    return out.toString()
}

private data class ProductionRow(
    val nonterminalId: Int,
    val rhsLength: Int,
    val body: KotlinProductionBody,
)

private fun productionRows(
    grammar: Grammar,
    actionBodies: List<KotlinProductionBody>,
): List<ProductionRow> {
    val nonterminalIds: Map<NonterminalString, Int> =
        grammar.nonterminals.keys.withIndex().associate { (index, nt) -> nt to index }
    val rows = mutableListOf<ProductionRow>()
    for (nt in grammar.nonterminals.values) {
        for (production in nt.productions) {
            val body = actionBodies.getOrNull(rows.size)
                ?: error("missing Kotlin action body for production ${rows.size}")
            rows.add(
                ProductionRow(
                    nonterminalId = nonterminalIds.getValue(production.nonterminal),
                    rhsLength = production.symbols.size,
                    body = body,
                ),
            )
        }
    }
    require(actionBodies.size == rows.size) {
        "received ${actionBodies.size} action bodies for ${rows.size} productions"
    }
    return rows
}

private fun emitTablesObject(
    out: IndentedWriter,
    config: KotlinParserEmitConfig,
    tables: io.github.kotlinmania.lalrpop.runtime.ParseTables<Unit, Int>,
    rows: List<ProductionRow>,
) {
    out.block("object ${config.tablesObjectName} {") {
        line("private const val NUM_STATES = ${tables.numStates}")
        line("private const val NUM_TERMINALS = ${tables.numTerminals}")
        line("private const val NUM_NONTERMINALS = ${tables.numNonterminals}")
        line()
        emitShortArray(this, "ACTION", tables.action)
        line()
        emitShortArray(this, "EOF_ACTION", tables.eofAction)
        line()
        emitShortArray(this, "GOTO", tables.goto)
        line()
        emitProductions(this, config, rows)
        line()
        block("val TABLES: ParseTables<${config.symbolClassName}, ${config.locationType}> = ParseTables(", footer = ")") {
            line("numStates = NUM_STATES,")
            line("numTerminals = NUM_TERMINALS,")
            line("numNonterminals = NUM_NONTERMINALS,")
            line("action = ACTION,")
            line("eofAction = EOF_ACTION,")
            line("goto = GOTO,")
            line("productions = PRODUCTIONS,")
            line("acceptProductionId = ${tables.acceptProductionId},")
        }
    }
}

private fun emitShortArray(out: IndentedWriter, name: String, values: ShortArray) {
    out.line("private val $name: ShortArray = shortArrayOf(")
    out.indented {
        for (chunk in values.asIterable().chunked(16)) {
            line(chunk.joinToString(prefix = "", postfix = ",") { it.toString() })
        }
    }
    out.line(")")
}

private fun emitProductions(
    out: IndentedWriter,
    config: KotlinParserEmitConfig,
    rows: List<ProductionRow>,
) {
    val symbol = config.symbolClassName
    val location = config.locationType
    out.line("private val PRODUCTIONS: Array<Production<$symbol, $location>> = arrayOf(")
    out.indented {
        for (row in rows) {
            block("Production(", footer = "),") {
                line("nonterminalId = ${row.nonterminalId}.toShort(),")
                line("rhsLength = ${row.rhsLength},")
                line("action = ProductionAction { stack, span ->")
                indented {
                    for (sourceLine in row.body.lines) {
                        line(sourceLine)
                    }
                }
                line("},")
            }
        }
    }
    out.line(")")
}

private fun emitParserClass(out: IndentedWriter, config: KotlinParserEmitConfig) {
    val symbol = config.symbolClassName
    val location = config.locationType
    val token = config.tokenType
    val error = config.errorType
    out.block("class ${config.parserClassName} {") {
        block("private val definition = TableDrivenParserDefinition<$symbol, $location, $token, $error>(", footer = ")") {
            line("tables = ${config.tablesObjectName}.TABLES,")
            line("tokenToTerminalId = ${config.tokenToTerminalIdExpression},")
            line("tokenToSymbol = ${config.tokenToSymbolExpression},")
            line("mapActionFailure = ${config.mapActionFailureExpression},")
            line("initialLocation = ${config.initialLocationExpression},")
            line("supportsErrorRecovery = ${config.supportsErrorRecovery},")
            line("errorRecoverySymbolOf = ${config.errorRecoverySymbolExpression},")
            line("expectedTokensFor = ${config.expectedTokensExpression},")
        }
        line()
        block("fun parse(tokens: Iterator<TokResult<$location, $token, $error>>): ParseResult<$symbol, $location, $token, $error> {") {
            line("return Parser.drive(definition, tokens)")
        }
    }
}
