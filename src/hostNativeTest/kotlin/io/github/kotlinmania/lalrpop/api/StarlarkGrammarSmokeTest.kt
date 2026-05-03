// port-lint: ignore
package io.github.kotlinmania.lalrpop.api

/*
 * One-off smoke test: feeds the upstream starlark-syntax grammar.lalrpop
 * through lalrpop-kotlin's Configuration.processFile and reports whether
 * the run completes and how big the output is.
 *
 * Driven by environment variables so the test does not hard-code a
 * cross-repo path:
 *   LALRPOP_STARLARK_GRAMMAR_PATH — absolute path to grammar.lalrpop
 *                                  (default: /tmp/lalrpop_oracle/grammar.lalrpop)
 *   LALRPOP_STARLARK_OUT_PATH    — where to drop the generated grammar.rs
 *                                  (default: /tmp/lalrpop_kotlin_out/grammar.rs)
 *
 * The test prints the head and tail of the generated file for visual
 * inspection. It is intended to be skipped when the source path is
 * absent so it does not break the regular hostNative test run.
 */

import io.github.kotlinmania.lalrpop.build.apiBuildReadFileToString
import io.github.kotlinmania.lalrpop.build.apiBuildWriteFileBytes
import kotlin.test.Test

class StarlarkGrammarSmokeTest {
    @Test
    fun runsAgainstStarlarkGrammar() {
        val grammarPath =
            apiEnvVar("LALRPOP_STARLARK_GRAMMAR_PATH")
                ?: "/tmp/lalrpop_oracle/grammar.lalrpop"
        val outPath =
            apiEnvVar("LALRPOP_STARLARK_OUT_PATH")
                ?: "/tmp/lalrpop_kotlin_out/grammar.rs"

        if (!apiPathExists(grammarPath)) {
            println("skip: starlark grammar not at $grammarPath")
            return
        }

        // Stage the grammar into a clean working directory next to the
        // output. processFile drops grammar.rs alongside the source when
        // session.outDir is null, so we work entirely under outParent.
        val outParent = pathParent(outPath)
        if (apiPathExists(outParent)) apiRemoveDirAll(outParent)
        apiCreateDir(outParent)

        val grammarText = apiBuildReadFileToString(grammarPath)
        val stagedGrammar = apiPathJoin(outParent, "grammar.lalrpop")
        apiBuildWriteFileBytes(stagedGrammar, grammarText)

        val origDir = apiCurrentDir()
        apiSetCurrentDir(outParent)
        try {
            Configuration.new()
                .forceBuild(true)
                .emitComments(true)
                .processFile("grammar.lalrpop")
        } finally {
            apiSetCurrentDir(origDir)
        }

        val generated = apiBuildReadFileToString(outPath)
        val lines = generated.lineSequence().toList()
        println("=== lalrpop-kotlin output for starlark grammar ===")
        println("byte length: ${generated.length}")
        println("line count:  ${lines.size}")
        println("--- first 20 lines ---")
        lines.take(20).forEach { println(it) }
        println("--- last 10 lines ---")
        lines.takeLast(10).forEach { println(it) }
        println("=== end smoke ===")
    }
}

private fun pathParent(path: String): String {
    val i = path.lastIndexOf('/')
    return if (i <= 0) "." else path.substring(0, i)
}
