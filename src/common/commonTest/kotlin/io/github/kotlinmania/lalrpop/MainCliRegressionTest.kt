package io.github.kotlinmania.lalrpop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainCliRegressionTest {
    @Test
    fun versionMatchesUpstreamCrateVersion() {
        assertEquals("0.23.1", VERSION)
    }

    @Test
    fun parseArgsSupportsEqualsSyntaxAttachedShortValuesCombinedFlagsAndTerminator() {
        val parsed =
            parseArgs(
                Arguments.fromVec(
                    listOf(
                        "--out-dir=gen",
                        "--level=debug",
                        "--features=featA,featB",
                        "-fc",
                        "--",
                        "-leading-dash.lalrpop",
                    ),
                ),
            ).getOrThrow()

        assertEquals("gen", parsed.flagOutDir)
        assertEquals(LevelFlag.Debug, parsed.flagLevel)
        assertEquals("featA,featB", parsed.flagFeatures)
        assertTrue(parsed.flagForce)
        assertTrue(parsed.flagColor)
        assertEquals(listOf("-leading-dash.lalrpop"), parsed.argInputs)

        val shortAttached =
            parseArgs(
                Arguments.fromVec(
                    listOf("-oout2", "-lquiet", "grammar.lalrpop"),
                ),
            ).getOrThrow()

        assertEquals("out2", shortAttached.flagOutDir)
        assertEquals(LevelFlag.Quiet, shortAttached.flagLevel)
        assertEquals(listOf("grammar.lalrpop"), shortAttached.argInputs)
    }

    @Test
    fun runMainRoutesHelpAndVersionToStdoutAndErrorsToStderr() {
        val versionOut = mutableListOf<String>()
        val versionErr = mutableListOf<String>()
        assertEquals(0, runMain(arrayOf("--version"), out = versionOut::add, err = versionErr::add))
        assertEquals(listOf("0.23.1"), versionOut)
        assertTrue(versionErr.isEmpty())

        val helpOut = mutableListOf<String>()
        val helpErr = mutableListOf<String>()
        assertEquals(0, runMain(arrayOf("--help"), out = helpOut::add, err = helpErr::add))
        assertEquals(listOf(USAGE), helpOut)
        assertTrue(helpErr.isEmpty())

        val badFlagOut = mutableListOf<String>()
        val badFlagErr = mutableListOf<String>()
        assertEquals(1, runMain(arrayOf("--level=invalid"), out = badFlagOut::add, err = badFlagErr::add))
        assertTrue(badFlagOut.isEmpty())
        assertEquals(1, badFlagErr.size)
        assertTrue(badFlagErr.single().contains("Unknown level: invalid"))

        val noInputsOut = mutableListOf<String>()
        val noInputsErr = mutableListOf<String>()
        assertEquals(1, runMain(emptyArray(), out = noInputsOut::add, err = noInputsErr::add))
        assertTrue(noInputsOut.isEmpty())
        assertEquals(listOf("Error: no input files specified! Try --help for help."), noInputsErr)

        val missingFile = "__nonexistent_lalrpop_input_t_f3ad34de__.lalrpop"
        val missingOut = mutableListOf<String>()
        val missingErr = mutableListOf<String>()
        assertEquals(1, runMain(arrayOf(missingFile), out = missingOut::add, err = missingErr::add))
        assertTrue(missingOut.isEmpty())
        assertEquals(1, missingErr.size)
        assertTrue(
            missingErr.single().startsWith("Error encountered processing `$missingFile`:"),
            "unexpected error output: ${missingErr.single()}",
        )
    }
}
