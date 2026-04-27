// port-lint: source src/lr1/report/test.rs
package io.github.kotlinmania.lalrpop.lr1.report

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

import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session
import io.github.kotlinmania.lalrpop.lr1.buildStates
import io.github.kotlinmania.lalrpop.lr1.generateReport
import io.github.kotlinmania.lalrpop.lr1.tls.Lr1Tls
import io.github.kotlinmania.lalrpop.normalizedGrammar
import io.github.kotlinmania.lalrpop.tls.Tls
import kotlin.test.Test
import kotlin.test.assertTrue

private const val GRAMMAR_TEXT: String = """grammar;
pub A: () = {
    B,
    C
}

B: () = "b";
C: () = "c";
"""

class ReportTest {
    @Test
    fun testReportGeneration() {
        val output = StringBuilder()
        val grammar = normalizedGrammar(GRAMMAR_TEXT)
        Tls.install(Session.new(), FileText.new("", "")).use {
            val lr1Tls = Lr1Tls.install(grammar.terminals)
            try {
                val startNt = grammar.startNonterminals.keys.first()
                generateReport(output, buildStates(grammar, startNt))

                val report = output.toString()

                assertTrue(report.contains("Constructed 5 states"))
                for (i in 0 until 5) {
                    assertTrue(report.contains("State $i"))
                }
            } finally {
                lr1Tls.drop()
            }
        }
    }
}
