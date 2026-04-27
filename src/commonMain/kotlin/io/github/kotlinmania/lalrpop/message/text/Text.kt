// port-lint: source src/message/text.rs
package io.github.kotlinmania.lalrpop.message.text

import io.github.kotlinmania.lalrpop.Style
import io.github.kotlinmania.lalrpop.message.AsciiView
import io.github.kotlinmania.lalrpop.message.writeChars
import io.github.kotlinmania.lalrpop.message.horiz.Horiz
import io.github.kotlinmania.lalrpop.message.message.Content
import io.github.kotlinmania.lalrpop.message.wrap.Wrap

/**
 * Text to be display. This will be flowed appropriately depending on
 * the container; e.g., in a Horiz, it will be one unit, but in a
 * Wrap, it will be broken up word by word.
 */
class Text private constructor(
    private val text: String,
) : Content {
    companion object {
        fun new(text: String): Text = Text(text = text)
    }

    override fun minWidth(): Int = this.text.length

    override fun emit(view: AsciiView) {
        view.writeChars(0, 0, this.text.iterator(), Style.new())
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        for (word in this.text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            wrapItems.add(Text.new(word))
        }
    }
}
