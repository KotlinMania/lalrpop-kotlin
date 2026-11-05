// port-lint: source test_util.rs
package io.github.kotlinmania.lalrpop

import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.grammar.parsetree.TypeParameter
import io.github.kotlinmania.lalrpop.grammar.repr.Grammar
import io.github.kotlinmania.lalrpop.message.Row
import io.github.kotlinmania.lalrpop.normalize.NormError
import io.github.kotlinmania.lalrpop.normalize.normalizeWithoutValidating
import io.github.kotlinmania.lalrpop.parser.parseGrammar

private val SPAN: Regex = Regex("Span\\((?:start=[0-9]+, end=[0-9]+|[0-9 ,\\n]*)\\)")

private class ExpectedDebug(
    private val s: String,
) {
    // Ignore trailing commas in multiline Debug representation.
    // Needed to work around rust-lang/rust#59076.
    override fun toString(): String = s.replace(",\n", "\n")
}

/**
 * Pretty-printed debug formatter for the value shapes passed by the
 * lalrpop-kotlin test suite (`List<String>` and `List<Row>` from
 * `AsciiCanvas.toStrings()`, rendered as indented, quoted, comma-separated
 * multiline lists, with other types delegating to `toString()`).
 */
private fun rustDebugFormat(value: Any?): String =
    when (value) {
        null -> "null"
        is List<*> -> rustPrettyDebug(value)
        is TypeParameter.LifetimeTp -> "Lifetime(\n    ${value.lifetime}\n)"
        is TypeParameter.Id -> "Id(\n    Atom('${value.atom}' type=inline)\n)"
        else -> value.toString()
    }

private fun rustPrettyDebug(value: Any): String {
    if (value is List<*>) {
        if (value.isEmpty()) return "[]"
        return buildString {
            append("[\n")
            for (item in value) {
                if (item is String || item is Row) {
                    append("    \"")
                    append(item.toString())
                    append("\",\n")
                } else {
                    val rendered = rustDebugFormat(item)
                    for (line in rendered.lineSequence()) {
                        append("    ")
                        append(line)
                        append('\n')
                    }
                    setLength(length - 1)
                    append(",\n")
                }
            }
            append("]")
        }
    }
    return value.toString()
}

internal fun <D : Any> expectDebug(actual: D, expected: String) {
    compare(
        ExpectedDebug(rustPrettyDebug(actual)),
        ExpectedDebug(expected),
    )
}

internal fun <D : Any, E : Any> compare(actual: D, expected: E) {
    val actualS = actual.toString()
    val expectedS = expected.toString()

    if (normalize(actualS) != normalize(expectedS)) {
        val prettyActualS = actual.toString()
        val prettyExpectedS = expected.toString()

        for (d in Diff.lines(normalize(prettyActualS), normalize(prettyExpectedS))) {
            when (d) {
                is Diff.Result.Right -> println("- ${d.value}")
                is Diff.Result.Left -> println("+ ${d.value}")
                is Diff.Result.Both -> println("  ${d.left}")
            }
        }

        throw RuntimeException()
    }
}

/**
 * Ignore differences in `Span` values, by replacing them all with fixed
 * dummy text.
 */
private fun normalize(withSpans: String): String =
    SPAN.replace(withSpans, "Span(..)")

internal fun normalizedGrammar(s: String): Grammar =
    normalizeWithoutValidating(parseGrammar(s).getOrThrow())

internal fun checkNormErr(expectedErr: String, span: String, err: NormError) {
    val expected = Regex(expectedErr)
    val startIndex = span.indexOf('~')
    val endIndex = span.lastIndexOf('~') + 1
    check(expected.containsMatchIn(err.message)) {
        "unexpected error text `${err.message}`, which did not match regular expression `$expected`"
    }
    check(startIndex <= endIndex)
    check(err.span == Span(startIndex, endIndex))
}

private object Diff {
    sealed class Result {
        data class Left(
            val value: String,
        ) : Result()

        data class Right(
            val value: String,
        ) : Result()

        data class Both(
            val left: String,
            val right: String,
        ) : Result()
    }

    fun lines(a: String, b: String): List<Result> {
        // Line-by-line diff comparison.
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
