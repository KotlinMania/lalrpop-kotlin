// port-lint: source message/text.rs
package io.github.kotlinmania.lalrpop.message.text

import io.github.kotlinmania.lalrpop.Style
import io.github.kotlinmania.lalrpop.message.AsciiView
import io.github.kotlinmania.lalrpop.message.writeChars
import io.github.kotlinmania.lalrpop.message.message.Content

/**
 * Text to be display. This will be flowed appropriately depending on
 * the container; e.g., in a Horiz, it will be one unit, but in a
 * Wrap, it will be broken up word by word.
 */
internal class Text private constructor(
    private val text: String,
) : Content {
    companion object {
        fun new(text: String): Text = Text(text = text)
    }

    override fun minWidth(): Int = this.text.count()

    override fun emit(view: AsciiView) {
        view.writeChars(0, 0, this.text.iterator(), Style.new())
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        for (word in splitWhitespace(this.text)) {
            wrapItems.add(Text.new(word.toString()))
        }
    }

    private fun splitWhitespace(input: String): List<CharSequence> {
        val words = mutableListOf<CharSequence>()
        var startIndex: Int? = null
        for (i in input.indices) {
            val ch = input[i]
            if (ch.isWhitespace()) {
                if (startIndex != null) {
                    words.add(input.subSequence(startIndex, i))
                    startIndex = null
                }
            } else if (startIndex == null) {
                startIndex = i
            }
        }
        if (startIndex != null) {
            words.add(input.subSequence(startIndex, input.length))
        }
        return words
    }
}
