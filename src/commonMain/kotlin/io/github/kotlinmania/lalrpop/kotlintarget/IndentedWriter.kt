package io.github.kotlinmania.lalrpop.kotlintarget

/**
 * Buffer that accumulates emitted source text with explicit indentation control.
 *
 * Call sites read top-to-bottom as the structure they produce:
 *
 * ```
 * val out = IndentedWriter()
 * out.line("class FooParser {")
 * out.indented {
 *     line("fun parse(input: String): Result {")
 *     indented {
 *         line("return Result.success(parseInner(input))")
 *     }
 *     line("}")
 * }
 * out.line("}")
 * ```
 *
 * The `indented { … }` form is the only way to nest — the writer does not infer indent
 * changes from punctuation in the line itself, which keeps the structure of the emitted
 * code visible in the call site rather than hidden in the writer's heuristics.
 */
internal class IndentedWriter(private val indent: String = "    ") {
    private val buffer = StringBuilder()
    private var depth = 0

    /** Append a single line of source text at the current indent level, plus a trailing newline. */
    fun line(text: String = "") {
        if (text.isNotEmpty()) {
            repeat(depth) { buffer.append(indent) }
            buffer.append(text)
        }
        buffer.append('\n')
    }

    /** Run [body] with the indent depth incremented by one; restore on exit (including exceptions). */
    inline fun indented(body: IndentedWriter.() -> Unit) {
        increaseDepth()
        try {
            body()
        } finally {
            decreaseDepth()
        }
    }

    /**
     * Emit a header line, run [body] with one more indent level, then emit a footer line.
     * The default footer is `}` — the most common case for Kotlin blocks.
     */
    inline fun block(header: String, footer: String = "}", body: IndentedWriter.() -> Unit) {
        line(header)
        indented(body)
        line(footer)
    }

    @PublishedApi
    internal fun increaseDepth() {
        depth += 1
    }

    @PublishedApi
    internal fun decreaseDepth() {
        check(depth > 0) { "indent depth underflow" }
        depth -= 1
    }

    override fun toString(): String = buffer.toString()
}
