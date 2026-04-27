// port-lint: source src/message/wrap.rs
package io.github.kotlinmania.lalrpop.message.wrap
import io.github.kotlinmania.lalrpop.message.AsciiView
import io.github.kotlinmania.lalrpop.message.message.Content

class Wrap private constructor(
    private val items: List<Content>,
) : Content {
    companion object {
        fun new(items: List<Content>): Wrap {
            val wrapItems: MutableList<Content> = mutableListOf()
            for (item in items) {
                item.intoWrapItems(wrapItems)
            }
            return Wrap(items = wrapItems)
        }
    }

    override fun minWidth(): Int = this.items.map { it.minWidth() }.max()

    override fun emit(view: AsciiView) {
        val columns = view.columns()
        var row = 0 // current row
        var height = 1 // max height of anything in this row
        var column = 0 // current column in this row

        for (item in this.items) {
            val len = item.minWidth()

            // If we don't have enough space for this content,
            // then move to the next line.
            if (column + len > columns) {
                column = 0
                row += height
                height = 1
            }

            check(column + len <= columns)

            val (cRow, cColumn) = item.emitAt(view, row, column)
            check(cColumn >= column)
            column = cColumn + 2
            height = maxOf(cRow - row + 1, height)
        }
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        wrapItems.addAll(this.items) // `items` are already subdivided
    }
}
