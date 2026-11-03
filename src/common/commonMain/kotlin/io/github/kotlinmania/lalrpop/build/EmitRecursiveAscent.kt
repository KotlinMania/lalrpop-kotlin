package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.grammar.parsetree.Visibility
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.LrCodeGeneration
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause
import io.github.kotlinmania.lalrpop.lexer.compileInternToken
import io.github.kotlinmania.lalrpop.lr1.BuildOutcome
import io.github.kotlinmania.lalrpop.lr1.buildStatesOrError
import io.github.kotlinmania.lalrpop.lr1.codegen.Ascent
import io.github.kotlinmania.lalrpop.lr1.codegen.ParseTable
import io.github.kotlinmania.lalrpop.lr1.codegen.TestAll
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.error.reportError as reportLr1Error
import io.github.kotlinmania.lalrpop.lr1.report.LrResult
import io.github.kotlinmania.lalrpop.lr1.report.generateReport
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust

internal fun emitRecursiveAscent(
    session: Session,
    grammar: Grammar,
    reportOut: StringBuilder? = null,
): String {
    val buffer = StringBuilder()
    val rust = RustWrite.new(buffer)

    // We generate a module structure like this:
    //
    // ```
    // mod <output-file> {
    //     // For each public symbol:
    //     fun parseXYZ();
    //     mod __XYZ { ... }
    //
    //     // For each bit of action code:
    //     <action-code>
    // }
    // ```
    //
    // Note that the action code goes in the outer module.  This is
    // intentional because it means that the foo.lalrpop file serves
    // as a module in the rust hierarchy, so if the action code
    // includes things like `super::` it will resolve in the natural
    // way.

    emitModuleAttributes(grammar, rust)
    emitUses(grammar, rust)

    if (grammar.startNonterminals.isEmpty()) {
        apiBuildPrintln("Error: no public symbols declared in grammar")
        error("Error: no public symbols declared in grammar")
    }

    // Find a better visibility for some generated items.
    // This will be the maximum of the visibility of all starting nonterminals.
    var maxStartNtVisibility: Visibility = Visibility.Priv
    for ((userNt, startNt) in grammar.startNonterminals) {
        val ntVis = grammar.nonterminals[startNt]?.visibility ?: Visibility.Priv
        maxStartNtVisibility = when {
            maxStartNtVisibility is Visibility.Pub && maxStartNtVisibility.path == null -> maxStartNtVisibility
            ntVis is Visibility.Priv -> maxStartNtVisibility
            maxStartNtVisibility == ntVis -> maxStartNtVisibility
            maxStartNtVisibility is Visibility.Priv -> ntVis
            else -> Visibility.Pub(path = null)
        }

        // We generate these, so there should always be exactly 1
        // production. Otherwise the LR(1) algorithm does not know
        // where to stop!
        check(grammar.productionsFor(startNt).size == 1) {
            "expected exactly 1 production for synthetic start `$startNt`"
        }

        session.log.log(Level.Verbose) { "Building states for public nonterminal `$userNt`" }

        val lr1Tls = Lr1Tls.install(grammar.terminals.copy())
        try {
            val lr1Result = buildStatesOrError(grammar, startNt)
            if (session.emitReport) {
                val report = reportOut ?: error("report output requested without a report sink")
                report.clear()
                when (lr1Result) {
                    is BuildOutcome.Ok -> generateReport(report, LrResult.Ok(lr1Result.states))
                    is BuildOutcome.Err -> generateReport(report, LrResult.Err(lr1Result.error))
                }
            }

            val states = when (lr1Result) {
                is BuildOutcome.Ok -> lr1Result.states
                is BuildOutcome.Err -> {
                    reportLr1Error(grammar, lr1Result.error, ::reportMessage)
                    error("Error: invalid LR(1) table construction")
                }
            }

            when (grammar.algorithm.codegen) {
                LrCodeGeneration.RecursiveAscent -> Ascent.compile(
                    grammar,
                    userNt,
                    startNt,
                    states,
                    "super",
                    rust,
                )
                LrCodeGeneration.TableDriven -> ParseTable.compile(
                    grammar,
                    userNt,
                    startNt,
                    states,
                    "super",
                    rust,
                )
                LrCodeGeneration.TestAll -> TestAll.compile(
                    grammar,
                    userNt,
                    startNt,
                    states,
                    rust,
                )
            }

            rust(rust, "#[allow(unused_imports)]")
            rust(
                rust,
                "{0}use self::{1}parse{2}::{3}Parser;",
                grammar.nonterminals[userNt]?.visibility ?: Visibility.Priv,
                grammar.prefix,
                startNt,
                userNt,
            )
        } finally {
            lr1Tls.drop()
        }
    }

    grammar.internToken?.let { internToken ->
        compileInternToken(grammar, internToken, rust)
        rust(
            rust,
            "pub(crate) use self::{0}lalrpop_util::lexer::Token;",
            grammar.prefix,
        )
    }

    emitActionCode(grammar, rust)

    rust(rust, "")
    rust(rust, "#[allow(clippy::type_complexity, dead_code)]")
    emitToTripleTrait(grammar, maxStartNtVisibility, rust)

    return buffer.toString()
}

internal fun emitModuleAttributes(grammar: Grammar, rust: RustWrite) {
    rust.writeModuleAttributes(grammar)
}

internal fun emitUses(grammar: Grammar, rust: RustWrite) {
    rust.writeUses("", grammar)
}

internal fun writeWhereClause(
    whereClauses: List<WhereClause>,
    toTripleWhereClauses: Sep<WhereClause>,
    rust: RustWrite,
) {
    if (whereClauses.isNotEmpty()) {
        rust(rust, "where {0}", toTripleWhereClauses)
    }
}

internal fun emitToTripleTrait(
    grammar: Grammar,
    maxStartNtVisibility: Visibility,
    rust: RustWrite,
) {
    val L = grammar.types.terminalLocType()
    val T = grammar.types.terminalTokenType()
    val E = grammar.types.errorType()

    val parseError = "${grammar.prefix}lalrpop_util::ParseError<$L, $T, $E>"

    val userTypeParameters = buildString {
        for (typeParameter in grammar.typeParameters) {
            append(typeParameter)
            append(", ")
        }
    }

    val whereClauses = grammar.whereClauses
    val toTripleWhereClauses = Sep(",", whereClauses)

    rust(
        rust,
        "{0}trait {1}ToTriple<{2}>",
        maxStartNtVisibility,
        grammar.prefix,
        userTypeParameters,
    )
    writeWhereClause(whereClauses, toTripleWhereClauses, rust)
    rust(rust, "{")
    rust(rust, "fn to_triple(self) -> Result<($L,$T,$L), $parseError>;")
    rust(rust, "}")

    rust(rust, "")
    if (grammar.types.optTerminalLocType() != null) {
        rust(
            rust,
            "impl<{0}> {1}ToTriple<{0}> for ($L, $T, $L)",
            userTypeParameters,
            grammar.prefix,
        )
        writeWhereClause(whereClauses, toTripleWhereClauses, rust)
        rust(rust, "{")
        rust(rust, "fn to_triple(self) -> Result<($L,$T,$L), $parseError> {")
        rust(rust, "Ok(self)")
        rust(rust, "}")
        rust(rust, "}")

        rust(
            rust,
            "impl<{0}> {1}ToTriple<{0}> for Result<($L, $T, $L), $E>",
            userTypeParameters,
            grammar.prefix,
        )
        writeWhereClause(whereClauses, toTripleWhereClauses, rust)
        rust(rust, "{")
        rust(rust, "fn to_triple(self) -> Result<($L,$T,$L), $parseError> {")
        rust(
            rust,
            "self.map_err(|error| {0}lalrpop_util::ParseError::User {{ error }})",
            grammar.prefix,
        )
        rust(rust, "}")
        rust(rust, "}")
    } else {
        rust(
            rust,
            "impl<{0}> {1}ToTriple<{0}> for $T",
            userTypeParameters,
            grammar.prefix,
        )
        writeWhereClause(whereClauses, toTripleWhereClauses, rust)
        rust(rust, "{")
        rust(rust, "fn to_triple(self) -> Result<((),$T,()), $parseError> {")
        rust(rust, "Ok(((), self, ()))")
        rust(rust, "}")
        rust(rust, "}")

        rust(
            rust,
            "impl<{0}> {1}ToTriple<{0}> for Result<$T,$E>",
            userTypeParameters,
            grammar.prefix,
        )
        writeWhereClause(whereClauses, toTripleWhereClauses, rust)
        rust(rust, "{")
        rust(rust, "fn to_triple(self) -> Result<((),$T,()), $parseError> {")
        rust(rust, "match self {")
        rust(rust, "Ok(v) => Ok(((), v, ())),")
        rust(
            rust,
            "Err(error) => Err({0}lalrpop_util::ParseError::User {{ error }}),",
            grammar.prefix,
        )
        rust(rust, "}") // match
        rust(rust, "}") // function
        rust(rust, "}") // implementation
    }
}
