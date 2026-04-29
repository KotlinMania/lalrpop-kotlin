// port-lint: source build/mod.rs
//! The orchestrator that walks the LR(1) construction pipeline and feeds
//! it into the codegen back-end. Translation of the `emitRecursiveAscent`
//! /`emitModuleAttributes` /`emitUses` /`emitToTripleTrait`
//! /`writeWhereClause` functions from upstream `src/build/mod.rs`.
//!
//! Per the project rule against translating `mod.rs` files as a single
//! `Mod.kt`, these functions live in build/EmitRecursiveAscent.kt next to
//! their action-emission siblings ([emitActionCode]).
package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Sep
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.grammar.parsetree.Visibility
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.grammar.repr.LrCodeGeneration
import io.github.kotlinmania.lalrpop.grammar.repr.WhereClause
import io.github.kotlinmania.lalrpop.lexer.compileInternToken
import io.github.kotlinmania.lalrpop.lr1.buildStates
import io.github.kotlinmania.lalrpop.lr1.codegen.Ascent
import io.github.kotlinmania.lalrpop.lr1.codegen.ParseTable
import io.github.kotlinmania.lalrpop.lr1.codegen.TestAll
import io.github.kotlinmania.lalrpop.lr1.tls.Lr1Tls
import io.github.kotlinmania.lalrpop.rust.RustWrite
import io.github.kotlinmania.lalrpop.rust.rust

/**
 * Runs the full Rust-emission back-end against an already-normalised
 * grammar and returns the emitted bytes (without the version/sha3 header,
 * which is added by [processFileInto]).
 *
 * Direct port of upstream `emitRecursiveAscent`.
 *
 * @throws IllegalStateException if the grammar declares no public start
 *   nonterminals (mirrors upstream `InvalidData` IO error).
 */
fun emitRecursiveAscent(
    session: Session,
    grammar: Grammar,
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
        error("Error: no public symbols declared in grammar")
    }

    // Find a better visibility for some generated items.
    // This will be the maximum of the visibility of all starting nonterminals.
    var maxStartNtVisibility: Visibility = Visibility.Priv
    for ((userNt, startNt) in grammar.startNonterminals) {
        val ntVis = grammar.nonterminals[startNt]?.visibility ?: Visibility.Priv
        maxStartNtVisibility = when {
            // (Pub(None), _) | (_, Priv) => keep current
            maxStartNtVisibility is Visibility.Pub && (maxStartNtVisibility as Visibility.Pub).path == null -> maxStartNtVisibility
            ntVis is Visibility.Priv -> maxStartNtVisibility
            // equal — keep
            maxStartNtVisibility == ntVis -> maxStartNtVisibility
            // (Priv, v) -> v
            maxStartNtVisibility is Visibility.Priv -> ntVis
            // anything else: collapse to most-public Pub
            else -> Visibility.Pub(path = null)
        }

        // We generate these, so there should always be exactly 1
        // production. Otherwise the LR(1) algorithm does not know
        // where to stop!
        check(grammar.productionsFor(startNt).size == 1) {
            "expected exactly 1 production for synthetic start `$startNt`"
        }

        // log(session, Verbose, "Building states for public nonterminal `{}`", userNt)
        session.log.log(Level.Verbose) { "Building states for public nonterminal `$userNt`" }

        val lr1Tls = Lr1Tls.install(grammar.terminals.copy())
        try {
            // Upstream wraps construction in a Result; the Kotlin port returns
            // the state list directly and reports errors via thrown exceptions
            // inside the LR(1) builder, so there is no Err arm to handle here.
            val states = buildStates(grammar, startNt)

            // Upstream optionally writes a report file; the Kotlin port has
            // no filesystem at this layer, so the `emitReport` branch is
            // left to callers that want the report.

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

/** Direct port of upstream `emitModuleAttributes`. */
fun emitModuleAttributes(grammar: Grammar, rust: RustWrite) {
    rust.writeModuleAttributes(grammar)
}

/** Direct port of upstream `emitUses`. */
fun emitUses(grammar: Grammar, rust: RustWrite) {
    rust.writeUses("", grammar)
}

/** Direct port of upstream `writeWhereClause`. */
fun writeWhereClause(
    whereClauses: List<WhereClause>,
    toTripleWhereClauses: Sep<WhereClause>,
    rust: RustWrite,
) {
    if (whereClauses.isNotEmpty()) {
        rust(rust, "where {0}", toTripleWhereClauses)
    }
}

/** Direct port of upstream `emitToTripleTrait`. */
fun emitToTripleTrait(
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
        rust(rust, "}") // function         rust(rust, "}") // implementation
    }
}
