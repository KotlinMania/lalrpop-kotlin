// port-lint: source message/indent.rs
package io.github.kotlinmania.lalrpop.message.indent
import io.github.kotlinmania.lalrpop.message.AsciiView
import io.github.kotlinmania.lalrpop.message.shift
import io.github.kotlinmania.lalrpop.message.message.Content

class Indent private constructor(
    private val amount: Int,
    private val content: Content,
) : Content {
    companion object {
        fun new(amount: Int, content: Content): Indent =
            Indent(amount = amount, content = content)
    }

    override fun minWidth(): Int = this.content.minWidth() + this.amount

    override fun emit(view: AsciiView) {
        val subview = view.shift(0, this.amount)
        this.content.emit(subview)
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        wrapItems.add(this)
    }
}
