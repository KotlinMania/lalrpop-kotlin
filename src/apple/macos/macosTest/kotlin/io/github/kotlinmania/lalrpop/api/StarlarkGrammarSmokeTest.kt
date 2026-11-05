package io.github.kotlinmania.lalrpop.api

/*
 * Optional smoke test: feeds an external starlark-syntax grammar.lalrpop
 * through lalrpop-kotlin's Configuration.processFile when configured via:
 *   LALRPOP_STARLARK_GRAMMAR_PATH — absolute path to grammar.lalrpop
 *   LALRPOP_STARLARK_OUT_PATH     — where to write the generated grammar.rs
 *
 * Skipped when either environment variable or source file is absent.
 */

import io.github.kotlinmania.lalrpop.build.apiBuildReadFileToString
import io.github.kotlinmania.lalrpop.build.apiBuildWriteFileBytes
import kotlin.test.Test

class StarlarkGrammarSmokeTest {
    @Test
    fun runsAgainstStarlarkGrammar() {
        val grammarPath = apiEnvVar("LALRPOP_STARLARK_GRAMMAR_PATH") ?: return
        val outPath = apiEnvVar("LALRPOP_STARLARK_OUT_PATH") ?: return

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
            Configuration
                .new()
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
