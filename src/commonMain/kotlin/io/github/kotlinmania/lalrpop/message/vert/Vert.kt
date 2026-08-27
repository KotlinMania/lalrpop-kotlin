// port-lint: source message/vert.rs
package io.github.kotlinmania.lalrpop.message.vert
import io.github.kotlinmania.lalrpop.message.AsciiView
import io.github.kotlinmania.lalrpop.message.message.Content

internal class Vert private constructor(
    private val items: List<Content>,
    private val separate: Int, // 0 => overlapping, 1 => each on its own line, 2 => paragraphs
) : Content {
    companion object {
        fun new(items: List<Content>, separate: Int): Vert =
            Vert(items = items, separate = separate)
    }

    override fun minWidth(): Int = this.items.map { it.minWidth() }.max()

    override fun emit(view: AsciiView) {
        emitVert(view, this.items, this.separate)
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        wrapItems.add(this)
    }
}

internal fun emitVert(view: AsciiView, items: List<Content>, separate: Int) {
    var row = 0
    for (item in items) {
        val (endRow, _) = item.emitAt(view, row, 0)
        row = endRow + separate
    }
}
