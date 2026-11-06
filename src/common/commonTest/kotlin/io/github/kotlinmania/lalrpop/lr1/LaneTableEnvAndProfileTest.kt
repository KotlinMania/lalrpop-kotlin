package io.github.kotlinmania.lalrpop.lr1

import io.github.kotlinmania.lalrpop.Atom
import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Level
import io.github.kotlinmania.lalrpop.Log
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.api.apiEnvVar
import io.github.kotlinmania.lalrpop.api.apiSetEnvVar
import io.github.kotlinmania.lalrpop.grammar.parsetree.NonterminalString
import io.github.kotlinmania.lalrpop.normalize.normalize
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.parser.parseGrammar
import io.github.kotlinmania.lalrpop.profile
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaneTableEnvAndProfileTest {
    @Test
    fun laneTableEnvVarDisablesLaneTableAndSelectsLegacyLr1Construction() {
        val previous = apiEnvVar("LALRPOP_LANE_TABLE").orEmpty()
        try {
            apiSetEnvVar("LALRPOP_LANE_TABLE", "")
            assertTrue(useLaneTable())

            // In LR(1), `E = "x"` is reached with distinct lookaheads ["c"] vs ["d"],
            // so legacy canonical LR(1) keeps two `E = "x" (*)` states whereas lane-table
            // merges them into one.
            Tls.test().use {
                val grammar =
                    normalizedGrammar(
                        """
                        grammar;
                        pub S: () = {
                            "a" E "c" => (),
                            "b" E "d" => (),
                        };
                        E: () = "x" => ();
                        """.trimIndent(),
                    )
                val startSymbol = grammar.startNonterminals.getValue(NonterminalString(Atom.from("S")))
                Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                    try {
                        val laneStateCount = buildLr1States(grammar, startSymbol).size

                        apiSetEnvVar("LALRPOP_LANE_TABLE", "disabled")
                        assertFalse(useLaneTable())

                        val legacyStateCount = buildLr1States(grammar, startSymbol).size
                        assertTrue(
                            legacyStateCount > laneStateCount,
                            "expected legacy LR(1) ($legacyStateCount states) > lane-table ($laneStateCount states)",
                        )
                    } finally {
                        lr1Tls.drop()
                    }
                }
            }
        } finally {
            apiSetEnvVar("LALRPOP_LANE_TABLE", previous)
        }
    }

    @Test
    fun profileLogsElapsedSecondsAcrossNormalizeLr1AndLalrPhases() {
        val previous = apiEnvVar("LALRPOP_LANE_TABLE").orEmpty()
        try {
            val lines = mutableListOf<String>()
            val grammarText =
                """
                grammar;
                pub S: () = {
                    "a" E "c" => (),
                    "b" E "d" => (),
                };
                E: () = "x" => ();
                """.trimIndent()
            val session =
                Session.test().apply {
                    log = Log(Level.Verbose) { lines.add(it) }
                }

            Tls.install(session, FileText.new("profile-test.lalrpop", grammarText)).use {
                val parsed = parseGrammar(grammarText).getOrThrow()
                val grammar = normalize(session, parsed)
                val startSymbol = grammar.startNonterminals.getValue(NonterminalString(Atom.from("S")))

                Lr1Tls.install(grammar.terminals).also { lr1Tls ->
                    try {
                        apiSetEnvVar("LALRPOP_LANE_TABLE", "")
                        buildLr1States(grammar, startSymbol)

                        apiSetEnvVar("LALRPOP_LANE_TABLE", "disabled")
                        buildLalrStates(grammar, startSymbol)
                    } finally {
                        lr1Tls.drop()
                    }
                }
            }

            val expectedPhases =
                listOf(
                    "Grammar validation",
                    "Inlining",
                    "LR(1) state construction (lane)",
                    "LR(1) state construction (legacy)",
                    "LALR(1) state collapse",
                )
            for (phase in expectedPhases) {
                assertTrue(
                    "Phase `$phase` begun" in lines,
                    "missing begun log for `$phase` in $lines",
                )
                val completionPattern =
                    Regex("^Phase `${Regex.escape(phase)}` completed in [0-9.eE+-]+ seconds$")
                assertTrue(
                    lines.any { completionPattern.matches(it) },
                    "missing elapsed-seconds completion log for `$phase` in $lines",
                )
            }

            val countBeforeQuiet = lines.size
            session.log.setLevel(Level.Informative)
            val value = profile(session, "SuppressedAtInformative") { 42 }
            assertEquals(42, value)
            assertEquals(countBeforeQuiet, lines.size)
        } finally {
            apiSetEnvVar("LALRPOP_LANE_TABLE", previous)
        }
    }
}
