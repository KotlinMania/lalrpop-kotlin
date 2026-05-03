// port-lint: ignore
package io.github.kotlinmania.lalrpop.lr1.kotlintarget

/*
 * Data-only parity comparator between the lalrpop-kotlin Kotlin pipeline
 * and the upstream lalrpop Rust oracle on the starlark grammar.
 *
 * No source-syntax conversion is involved. Both sides produce the same
 * three pieces of integer data — terminal index list, action table,
 * EOF-action table — and the test asserts those data are identical
 * cell-for-cell.
 *
 * Driven by environment variables so the test runs only when the
 * operator has staged an oracle:
 *   LALRPOP_STARLARK_GRAMMAR_PATH — the .lalrpop source from which the
 *                                  oracle was generated. Default:
 *                                  /tmp/lalrpop_oracle_nocomments/grammar.lalrpop
 *   LALRPOP_STARLARK_ORACLE_RS    — absolute path to a no-comments
 *                                  grammar.rs produced by upstream
 *                                  lalrpop. Default:
 *                                  /tmp/lalrpop_oracle_nocomments/grammar.rs
 *
 * Important: the test reads the .lalrpop FROM DISK (not from the
 * embedded StarlarkGrammarFixture constant) and runs lalrpop-kotlin's
 * pipeline against that exact source. Both pipelines therefore consume
 * the same input bytes, so any state-count or table-cell divergence is
 * a real codegen bug, not a stale fixture.
 *
 * Generate the oracle once with:
 *   mkdir -p /tmp/lalrpop_oracle_nocomments
 *   cp .../tmp/starlark_syntax/src/syntax/grammar.lalrpop /tmp/lalrpop_oracle_nocomments/
 *   lalrpop -f -o /tmp/lalrpop_oracle_nocomments \
 *           /tmp/lalrpop_oracle_nocomments/grammar.lalrpop
 *
 * The test skips silently if either file is absent so the regular
 * hostNative run is unaffected.
 */

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.api.apiEnvVar
import io.github.kotlinmania.lalrpop.api.apiPathExists
import io.github.kotlinmania.lalrpop.build.apiBuildReadFileToString
import io.github.kotlinmania.lalrpop.build.parseAndNormalizeGrammar
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.lr1.Lr1Tls
import io.github.kotlinmania.lalrpop.lr1.buildStates
import io.github.kotlinmania.lalrpop.runtime.Production
import io.github.kotlinmania.lalrpop.runtime.ProductionAction
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class StarlarkGrammarTableParityTest {
    @Test
    fun kotlinTablesMatchUpstreamOracle() {
        val grammarPath = apiEnvVar("LALRPOP_STARLARK_GRAMMAR_PATH")
            ?: "/tmp/lalrpop_oracle_nocomments/grammar.lalrpop"
        val oraclePath = apiEnvVar("LALRPOP_STARLARK_ORACLE_RS")
            ?: "/tmp/lalrpop_oracle_nocomments/grammar.rs"

        if (!apiPathExists(grammarPath)) {
            println("skip: starlark grammar not at $grammarPath")
            return
        }
        if (!apiPathExists(oraclePath)) {
            println("skip: oracle grammar.rs not at $oraclePath")
            return
        }

        val grammarText = apiBuildReadFileToString(grammarPath)
        val oracleSrc = apiBuildReadFileToString(oraclePath)

        val oracleTerminals = extractTerminalArray(oracleSrc)
        val oracleAction = extractI16Array(oracleSrc, "const __ACTION: &[i16] = &[")
        val oracleEofAction = extractI16Array(oracleSrc, "const __EOF_ACTION: &[i16] = &[")

        // Match the production parse+normalize path used by the
        // Rust-emit pipeline exactly: Tls.install(session, fileText)
        // around parseAndNormalizeGrammar (the validating variant), not
        // the test-only normalizedGrammar (which uses
        // normalizeWithoutValidating + a fresh Session).
        val session = Session.new()
        val fileText = FileText.new(grammarPath, grammarText)
        val tlsOuter = Tls.install(session, fileText)
        try {
            val grammar = parseAndNormalizeGrammar(session, fileText)

            // Step 1: terminal-order parity. Both pipelines should index
            // terminals in identical order (the order they were declared
            // in the `extern { enum Tok }` block). If this disagrees the
            // cell-by-cell comparison below is meaningless.
            val kotlinTerminals = grammar.terminals.all.map { it.toString() }
            assertEquals(
                oracleTerminals.size,
                kotlinTerminals.size,
                "terminal count mismatch",
            )
            for (i in oracleTerminals.indices) {
                if (oracleTerminals[i] != kotlinTerminals[i]) {
                    fail(
                        "terminal index $i differs: oracle=${oracleTerminals[i]} kotlin=${kotlinTerminals[i]}",
                    )
                }
            }

            // Step 2: build the LR(1) state machine and pack tables.
            // Match the Rust-emit pipeline shape:
            //   - call buildStates (lane-table + rewriteStateIndices)
            //   - on the *synthetic* start nonterminal grammar synthesises
            //     for each public start (e.g. user `Starlark` →
            //     synthetic `__Starlark`); state count differs from
            //     building on the user nonterminal directly because the
            //     synthetic start adds the accept production wrapper.
            //   - keep Lr1Tls installed across buildStates *and*
            //     tablesFromLr1States: the latter iterates each state's
            //     TokenSet lookaheads, which delegate to the thread-local
            //     terminal-bit mapping installed here.
            val userNt = NonterminalString(Atom.from("Starlark"))
            val syntheticStart = grammar.startNonterminals[userNt]
                ?: error("grammar has no public `Starlark` nonterminal")
            val tls = Lr1Tls.install(grammar.terminals)
            val tables = try {
                val states = buildStates(grammar, syntheticStart)
                val productionCount = grammar.nonterminals.values.sumOf { it.productions.size }
                val productions = Array(productionCount) {
                    Production<Unit, Int>(
                        nonterminalId = 0,
                        rhsLength = 0,
                        action = ProductionAction { _, _ -> error("not invoked in parity test") },
                    )
                }
                tablesFromLr1States(
                    grammar = grammar,
                    states = states,
                    productions = productions,
                    acceptProductionId = 0,
                )
            } finally {
                tls.drop()
            }

            // Step 3: dimensions.
            assertEquals(
                oracleAction.size,
                tables.action.size,
                "action table size mismatch",
            )
            assertEquals(
                oracleEofAction.size,
                tables.eofAction.size,
                "eofAction table size mismatch",
            )
            assertEquals(
                tables.numStates,
                oracleEofAction.size,
                "state count mismatch (kotlin says ${tables.numStates}, oracle has ${oracleEofAction.size})",
            )

            // Step 4: cell-by-cell parity on action.
            val numTerminals = tables.numTerminals
            for (i in oracleAction.indices) {
                val o = oracleAction[i]
                val k = tables.action[i].toInt()
                if (o != k) {
                    val state = i / numTerminals
                    val term = i % numTerminals
                    fail(
                        "action[state=$state, term=$term=${oracleTerminals[term]}] " +
                            "oracle=$o kotlin=$k",
                    )
                }
            }

            // Step 5: cell-by-cell parity on eofAction.
            for (state in oracleEofAction.indices) {
                val o = oracleEofAction[state]
                val k = tables.eofAction[state].toInt()
                if (o != k) {
                    fail("eofAction[state=$state] oracle=$o kotlin=$k")
                }
            }

            println(
                "starlark grammar — kotlin tables match oracle: " +
                    "states=${tables.numStates}, terminals=$numTerminals, " +
                    "action=${oracleAction.size} cells, eofAction=${oracleEofAction.size} cells",
            )
        } finally {
            tlsOuter.drop()
        }
    }
}

// --------------------------------------------------------------------
// Oracle extractors. Pure data scrapers — they read integers out of the
// upstream lalrpop output without translating any Rust-side syntax into
// Kotlin equivalents. The shape of the const-array literals lalrpop
// emits is stable across grammars; the parsers here exploit that.
// --------------------------------------------------------------------

/**
 * Extract a slice declared as `const NAME: &[i16] = &[ ... ];`. Returns
 * the integer cells in order. Tolerates whitespace, comments, and line
 * breaks between cells; expects the closing `];` on its own line.
 */
private fun extractI16Array(source: String, header: String): IntArray {
    val start = source.indexOf(header)
    require(start >= 0) { "could not find $header in oracle source" }
    val bodyStart = start + header.length
    val end = source.indexOf("];", bodyStart)
    require(end >= 0) { "could not find closing `];` for $header" }
    val body = source.substring(bodyStart, end)
    val out = mutableListOf<Int>()
    var i = 0
    while (i < body.length) {
        val c = body[i]
        if (c == '/' && i + 1 < body.length && body[i + 1] == '/') {
            // line comment
            while (i < body.length && body[i] != '\n') i++
        } else if (c == '-' || c.isDigit()) {
            var j = i + 1
            while (j < body.length && body[j].isDigit()) j++
            out.add(body.substring(i, j).toInt())
            i = j
        } else {
            i++
        }
    }
    return out.toIntArray()
}

/**
 * Extract `const __TERMINAL: &[&str] = &[ r###"<form>"###, ... ];`.
 * Returns the per-terminal raw form (with surrounding double-quotes) in
 * declaration order. The raw form matches what lalrpop-kotlin's
 * TerminalString.toString() produces — backslash-escaped control
 * characters, surrounding `"`s.
 */
private fun extractTerminalArray(source: String): List<String> {
    val header = "const __TERMINAL: &[&str] = &["
    val start = source.indexOf(header)
    require(start >= 0) { "could not find $header in oracle source" }
    val bodyStart = start + header.length
    val end = source.indexOf("];", bodyStart)
    require(end >= 0) { "could not find closing `];` for $header" }
    val body = source.substring(bodyStart, end)
    // Each cell is `r###"<form>"###` (lalrpop's standard hash count for
    // the terminal-name slice).
    val open = "r###\""
    val close = "\"###"
    val out = mutableListOf<String>()
    var i = 0
    while (true) {
        val o = body.indexOf(open, i)
        if (o < 0) break
        val c = body.indexOf(close, o + open.length)
        require(c > 0) { "unterminated raw string in __TERMINAL" }
        out.add(body.substring(o + open.length, c))
        i = c + close.length
    }
    return out
}
