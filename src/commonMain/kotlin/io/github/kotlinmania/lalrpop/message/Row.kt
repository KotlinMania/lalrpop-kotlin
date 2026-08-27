// port-lint: source external/asciiCanvas/row
package io.github.kotlinmania.lalrpop.message

import io.github.kotlinmania.lalrpop.Style

internal class Row private constructor(
    private val text: String,
    private val styles: List<Style>,
) {
    companion object {
        fun new(chars: List<Char>, styles: List<Style>): Row {
            check(chars.size == styles.size)
            return Row(
                text = chars.joinToString(""),
                styles = styles.toList(),
            )
        }
    }

    fun writeTo(out: Appendable) {
        // commonMain has no ANSI terminal; styles are elided and only the text is emitted.
        out.append(this.text.trimEnd())
    }

    // Using display/debug just skips the styling.

    override fun toString(): String = this.text.trimEnd()
}
