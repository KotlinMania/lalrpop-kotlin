// port-lint: source src/message/horiz.rs
package io.github.kotlinmania.lalrpop_kotlin.message.horiz
import io.github.kotlinmania.lalrpop_kotlin.message.AsciiView
import io.github.kotlinmania.lalrpop_kotlin.message.message.Content

class Horiz private constructor(
    private val items: List<Content>,
    private val separate: Int, // 0 => overlapping, 1 => each on its own line, 2 => paragraphs
) : Content {
    companion object {
        fun new(items: List<Content>, separate: Int): Horiz =
            Horiz(items = items, separate = separate)
    }

    override fun minWidth(): Int {
        val widths = this.items.map { it.minWidth() }
        if (widths.isEmpty()) return 0
        var total = widths[0]
        for (i in 1 until widths.size) {
            total += this.separate + widths[i]
        }
        return total
    }

    override fun emit(view: AsciiView) {
        emitHoriz(view, this.items, this.separate)
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        wrapItems.add(this)
    }
}

fun emitHoriz(view: AsciiView, items: List<Content>, separate: Int) {
    var column = 0
    for (item in items) {
        val (_, endColumn) = item.emitAt(view, 0, column)
        column = endColumn + separate
    }
}
