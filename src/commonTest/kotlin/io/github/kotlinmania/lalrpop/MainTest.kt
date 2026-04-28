// port-lint: source main.rs
package io.github.kotlinmania.lalrpop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun osVec(vals: Array<String>): List<String> = vals.map { it }

private fun parseArgsSlice(args: Array<String>): Args =
    parseArgs(Arguments.fromVec(osVec(args))).getOrThrow()

class MainTest {
    @Test
    fun testUsageHelp() {
        assertTrue(parseArgsSlice(arrayOf("--help")).flagHelp)
    }

    @Test
    fun testUsageVersion() {
        assertTrue(parseArgsSlice(arrayOf("--version")).flagVersion)
    }

    @Test
    fun testUsageSingleInput() {
        assertEquals(
            listOf("file.lalrpop"),
            parseArgsSlice(arrayOf("file.lalrpop")).argInputs,
        )
    }

    @Test
    fun testUsageMultipleInputs() {
        val files = listOf("file.lalrpop", "../file2.lalrpop")
        assertEquals(files, parseArgsSlice(files.toTypedArray()).argInputs)
    }

    @Test
    fun testUsageOutDir() {
        val args = parseArgsSlice(arrayOf("--out-dir", "abc", "file.lalrpop"))
        assertEquals("abc", args.flagOutDir)
        assertEquals(listOf("file.lalrpop"), args.argInputs)
    }

    @Test
    fun testUsageFeatures() {
        val args = parseArgsSlice(arrayOf("--features", "test,abc", "file.lalrpop"))
        assertEquals("test,abc", args.flagFeatures)
        assertEquals(listOf("file.lalrpop"), args.argInputs)
    }

    @Test
    fun testUsageEmitWhitespace() {
        val args = parseArgsSlice(arrayOf("--no-whitespace", "file.lalrpop"))
        assertTrue(args.flagNoWhitespace)
        assertEquals(listOf("file.lalrpop"), args.argInputs)
    }

    @Test
    fun testUsageLevel() {
        assertEquals(
            LevelFlag.Info,
            parseArgsSlice(arrayOf("-l", "info")).flagLevel,
        )
    }
}
