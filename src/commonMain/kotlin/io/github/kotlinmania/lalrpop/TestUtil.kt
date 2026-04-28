// port-lint: source test_util.rs
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.normalize.NormError
import io.github.kotlinmania.lalrpop.normalize.normalizeWithoutValidating
import io.github.kotlinmania.lalrpop.parser.parseGrammar

private val SPAN: Regex = Regex("Span\\([0-9 ,\\n]*\\)")

private class Error

private class Formatter(val sb: StringBuilder = StringBuilder())

private class ExpectedDebug(private val s: String) {
    fun fmt(fmt: Formatter): Result<Unit> {
        // Ignore trailing commas in multiline Debug representation.
        // Needed to work around rust-lang/rust#59076.
        val s = this.s.replace(",\n", "\n")
        fmt.sb.append(s)
        return Result.success(Unit)
    }

    fun fmt(fmt: Formatter, _error: Error): Result<Unit> = fmt(fmt)
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
        val prettyActualS = actual.toString()
        val prettyExpectedS = expected.toString()

        for (diff in diff.lines(normalize(prettyActualS), normalize(prettyExpectedS))) {
            when (diff) {
                is diff.Result.Right -> println("- ${diff.value}")
                is diff.Result.Left -> println("+ ${diff.value}")
                is diff.Result.Both -> println("  ${diff.left}")
            }
        }

        throw RuntimeException()
    }
}

private fun <T> Result<T>.unwrap(): T = getOrThrow()

/**
 * Ignore differences in `Span` values, by replacing them all with fixed
 * dummy text.
 */
private fun normalize(withSpans: String): String =
    SPAN.replace(withSpans, "Span(..)")

fun normalizedGrammar(s: String): Grammar {
    return Result.success(normalizeWithoutValidating(parseGrammar(s).unwrap())).unwrap()
}

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

private object diff {
    sealed class Result {
        data class Left(val value: String) : Result()
        data class Right(val value: String) : Result()
        data class Both(val left: String, val right: String) : Result()
    }

    fun lines(a: String, b: String): List<Result> {
        // Minimal stand-in for `diff::lines`: line-by-line comparison.
        val al = a.split('\n')
        val bl = b.split('\n')
        val out = mutableListOf<Result>()
        val n = minOf(al.size, bl.size)
        for (i in 0 until n) {
            if (al[i] == bl[i]) {
                out.add(Result.Both(al[i], bl[i]))
            } else {
                out.add(Result.Left(al[i]))
                out.add(Result.Right(bl[i]))
            }
        }
        for (i in n until al.size) out.add(Result.Left(al[i]))
        for (i in n until bl.size) out.add(Result.Right(bl[i]))
        return out
    }
}
