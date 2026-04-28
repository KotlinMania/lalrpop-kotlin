// port-lint: source test_util.rs
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.normalize.NormError
import io.github.kotlinmania.lalrpop.normalize.normalizeWithoutValidating
import io.github.kotlinmania.lalrpop.parser.parseGrammar

private val SPAN: Regex = Regex("Span\\([0-9 ,\\n]*\\)")

private class ExpectedDebug(private val s: String) {
    override fun toString(): String {
        // Ignore trailing commas in multiline Debug representation.
        // Needed to work around rust-lang/rust#59076.
        return s.replace(",\n", "\n")
    }
}

fun <D : Any> expectDebug(actual: D, expected: String) {
    compare(
        ExpectedDebug(actual.toString()),
        ExpectedDebug(expected),
    )
}

fun <D : Any, E : Any> compare(actual: D, expected: E) {
    val actualS = actual.toString()
    val expectedS = expected.toString()

    if (normalize(actualS) != normalize(expectedS)) {
        val a = normalize(actualS)
        val e = normalize(expectedS)

        for (line in diffLines(a, e)) {
            when (line) {
                is DiffResult.Right -> println("- ${line.value}")
                is DiffResult.Left -> println("+ ${line.value}")
                is DiffResult.Both -> println("  ${line.left}")
            }
        }

        error("comparison failed")
    }
}

/**
 * Ignore differences in `Span` values, by replacing them all with fixed
 * dummy text.
 */
private fun normalize(withSpans: String): String =
    SPAN.replace(withSpans, "Span(..)")

fun normalizedGrammar(s: String): Grammar =
    normalizeWithoutValidating(parseGrammar(s).getOrThrow())

fun checkNormErr(expectedErr: String, span: String, err: NormError) {
    val expected = Regex(expectedErr)
    val startIndex = span.indexOf('~')
    val endIndex = span.lastIndexOf('~') + 1
    check(expected.containsMatchIn(err.message)) {
        "unexpected error text `${err.message}`, which did not match regular expression `$expected`"
    }
    check(startIndex <= endIndex)
    check(err.span == Span(startIndex, endIndex))
}

private sealed class DiffResult {
    data class Left(val value: String) : DiffResult()
    data class Right(val value: String) : DiffResult()
    data class Both(val left: String, val right: String) : DiffResult()
}

private fun diffLines(a: String, b: String): List<DiffResult> {
    // Minimal stand-in for `diff::lines`: line-by-line comparison.
    val al = a.split('\n')
    val bl = b.split('\n')
    val out = mutableListOf<DiffResult>()
    val n = minOf(al.size, bl.size)
    for (i in 0 until n) {
        if (al[i] == bl[i]) {
            out.add(DiffResult.Both(al[i], bl[i]))
        } else {
            out.add(DiffResult.Left(al[i]))
            out.add(DiffResult.Right(bl[i]))
        }
    }
    for (i in n until al.size) out.add(DiffResult.Left(al[i]))
    for (i in n until bl.size) out.add(DiffResult.Right(bl[i]))
    return out
}
