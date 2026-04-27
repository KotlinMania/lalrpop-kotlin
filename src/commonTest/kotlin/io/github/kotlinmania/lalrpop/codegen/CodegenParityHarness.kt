// port-lint: source <none — Kotlin-side parity harness>
//! Codegen parity harness.
//!
//! For each entry in the corpus, this harness drives the lalrpop-kotlin
//! Rust-emission back-end on the input grammar and compares the output
//! against the upstream oracle byte-for-byte (modulo deterministic
//! whitespace normalisation).
//!
//! The corpus inputs and oracle outputs are staged under
//! `src/commonTest/resources/codegen-parity/` for human inspection.
//! Because Kotlin Multiplatform `commonTest` does not have a portable
//! filesystem reader, the harness consumes the inputs/oracles via the
//! [CodegenParityCorpus] object — entries are added by hand-embedding
//! the file contents into Kotlin string constants. Future work: replace
//! the embedded constants with a build-time generator that reads the
//! files under `resources/`.
//!
//! Adding a grammar to the corpus:
//!
//! 1. Drop the `.lalrpop` source under
//!    `src/commonTest/resources/codegen-parity/inputs/<name>.lalrpop`.
//! 2. Drop the upstream-emitted Rust under
//!    `src/commonTest/resources/codegen-parity/expected/<name>.expected.rs`.
//! 3. Update `MANIFEST.md` with the entry's status.
//! 4. Add the entry to [CodegenParityCorpus.entries].
//! 5. Run [CodegenParityHarness.runAll] from a `@Test` and triage diffs
//!    leaves-up: token enum → action functions → state table → reduce
//!    dispatch → error recovery → header.
package io.github.kotlinmania.lalrpop.codegen

import io.github.kotlinmania.lalrpop.build.compileGrammarToString
import kotlin.test.Test
import kotlin.test.fail

/**
 * One entry in the codegen-parity corpus. Each entry pairs a grammar
 * input with the upstream-emitted oracle Rust output for the same
 * grammar.
 *
 * @param name the grammar's name, also used to locate the staged
 *   resource files under `src/commonTest/resources/codegen-parity/`.
 * @param input the contents of `inputs/<name>.lalrpop`. Required.
 * @param expected the contents of `expected/<name>.expected.rs`.
 *   Required. The harness asserts byte-identity against this.
 * @param sha3 the upstream `// sha3: ...` line content (the hex digest
 *   only, without the `// sha3: ` prefix). Required for byte-identical
 *   header parity.
 * @param status the corpus status mirrored from `MANIFEST.md`.
 */
data class CodegenParityEntry(
    val name: String,
    val input: String,
    val expected: String,
    val sha3: String,
    val status: ParityStatus,
) {
    init {
        require(expected.isNotEmpty()) {
            "CodegenParityEntry '$name' has empty `expected` oracle. " +
                "Empty oracles are not allowed — embed the staged " +
                "expected/$name.expected.rs contents into the entry."
        }
        require(sha3.isNotEmpty()) {
            "CodegenParityEntry '$name' has empty `sha3` digest. " +
                "Take the value from the `// sha3: ...` line at the top " +
                "of expected/$name.expected.rs."
        }
    }
}

enum class ParityStatus {
    /** Harness runs but output diverges from oracle. */
    Divergent,

    /** Byte-identical to oracle (after whitespace normalisation). */
    Matching,
}

/**
 * Result of running the harness against a single entry. The harness
 * never throws on divergence — it returns a structured result so the
 * caller can print a useful diff.
 */
sealed class CodegenParityResult {
    /** The harness produced byte-identical output (after normalisation). */
    data object Matching : CodegenParityResult()

    /** The harness ran but the output diverged. */
    data class Divergent(
        val entry: CodegenParityEntry,
        val actual: String,
        val firstDiffLine: Int,
        val expectedLine: String?,
        val actualLine: String?,
    ) : CodegenParityResult()

    /** The harness threw while compiling the grammar. */
    data class Threw(
        val entry: CodegenParityEntry,
        val cause: Throwable,
    ) : CodegenParityResult()
}

object CodegenParityHarness {
    /**
     * Runs the back-end against [entry.input], normalises whitespace,
     * and compares against [entry.expected] line-by-line.
     */
    fun run(entry: CodegenParityEntry): CodegenParityResult {
        val actual = try {
            compileGrammarToString(
                input = entry.input,
                sourceLabel = entry.name,
                hashHex = entry.sha3,
            )
        } catch (t: Throwable) {
            return CodegenParityResult.Threw(entry, t)
        }
        val normalisedExpected = normalise(entry.expected)
        val normalisedActual = normalise(actual)
        if (normalisedExpected == normalisedActual) {
            return CodegenParityResult.Matching
        }
        val expectedLines = normalisedExpected.split('\n')
        val actualLines = normalisedActual.split('\n')
        val limit = minOf(expectedLines.size, actualLines.size)
        var diffIndex = -1
        for (i in 0 until limit) {
            if (expectedLines[i] != actualLines[i]) {
                diffIndex = i
                break
            }
        }
        if (diffIndex == -1) {
            // Same prefix, one is longer.
            diffIndex = limit
        }
        return CodegenParityResult.Divergent(
            entry = entry,
            actual = actual,
            firstDiffLine = diffIndex + 1, // 1-based for humans
            expectedLine = expectedLines.getOrNull(diffIndex),
            actualLine = actualLines.getOrNull(diffIndex),
        )
    }

    /**
     * Runs every entry and returns the aggregate results. Caller
     * decides how to fail; a typical `@Test` body asserts that no
     * entry returned [CodegenParityResult.Divergent] or
     * [CodegenParityResult.Threw].
     */
    fun runAll(): List<CodegenParityResult> =
        CodegenParityCorpus.entries.map(::run)

    /**
     * Whitespace normalisation contract. Mirrors the rules in
     * `src/commonTest/resources/codegen-parity/MANIFEST.md`:
     *  * trailing whitespace stripped per line
     *  * line endings normalised to `\n`
     *  * trailing newlines collapsed to a single `\n`
     *
     * Anything else is a real divergence.
     */
    internal fun normalise(s: String): String {
        val unified = s.replace("\r\n", "\n").replace('\r', '\n')
        val trimmedLines = unified.split('\n').map { line -> line.trimEnd() }
        val joined = trimmedLines.joinToString("\n")
        // Collapse any number of trailing blank lines into a single \n.
        var end = joined.length
        while (end > 0 && joined[end - 1] == '\n') {
            end--
        }
        return joined.substring(0, end) + "\n"
    }
}

/**
 * Asserts that every corpus entry is in [ParityStatus.Matching]
 * status. Until that day, the test passes when the harness runs
 * cleanly and reports the current state. Use the `@Test` below as a
 * regression baseline once any single entry reaches `Matching`.
 */
class CodegenParityTest {
    @Test
    fun corpus_runs_to_completion() {
        val results = CodegenParityHarness.runAll()
        val report = StringBuilder()
        var anyFailure = false
        for ((entry, result) in CodegenParityCorpus.entries.zip(results)) {
            report.appendLine("[${entry.status}] ${entry.name}: ")
            when (result) {
                is CodegenParityResult.Matching ->
                    report.appendLine("  matching (byte-identical to oracle)")
                is CodegenParityResult.Divergent -> {
                    report.appendLine("  divergent at line ${result.firstDiffLine}")
                    report.appendLine("    expected: ${result.expectedLine}")
                    report.appendLine("    actual:   ${result.actualLine}")
                    if (entry.status == ParityStatus.Matching) anyFailure = true
                }
                is CodegenParityResult.Threw -> {
                    report.appendLine("  threw: ${result.cause::class.simpleName}: ${result.cause.message}")
                    anyFailure = true
                }
            }
        }
        println(report)
        if (anyFailure) {
            fail("codegen parity regressed; see report above")
        }
    }
}
