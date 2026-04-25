// port-lint: source src/message/styled.rs
package io.github.kotlinmania.lalrpop_kotlin.message.styled

import io.github.kotlinmania.lalrpop_kotlin.Style
import io.github.kotlinmania.lalrpop_kotlin.message.AsciiView
import io.github.kotlinmania.lalrpop_kotlin.message.styled
import io.github.kotlinmania.lalrpop_kotlin.message.message.Content
import io.github.kotlinmania.lalrpop_kotlin.message.message.intoWrapItemsMap

class Styled private constructor(
    private val style: Style,
    private val content: Content,
) : Content {
    companion object {
        fun new(style: Style, content: Content): Styled =
            Styled(style = style, content = content)
    }

    override fun minWidth(): Int = this.content.minWidth()

    override fun emit(view: AsciiView) {
        this.content.emit(view.styled(this.style))
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        val style = this.style
        intoWrapItemsMap(this.content, wrapItems) { item -> Styled.new(style, item) }
    }

    override fun toString(): String = "Styled { content: $content }"
}
