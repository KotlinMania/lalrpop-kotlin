// port-lint: source message/mod.rs
package io.github.kotlinmania.lalrpop.message.message
import io.github.kotlinmania.lalrpop.message.AsciiCanvas
import io.github.kotlinmania.lalrpop.message.AsciiView
import io.github.kotlinmania.lalrpop.message.shift
import io.github.kotlinmania.lalrpop.message.styled.Styled

/** Content which can be rendered. */
interface Content {
    fun minWidth(): Int

    fun emit(view: AsciiView)

    /**
     * Creates a canvas at least `minWidth` in width (it may be
     * larger if the content requires that) and fills it with the
     * current content. Returns the canvas. Typically `minWidth`
     * would be 80 or the width of the current terminal.
     */
    fun emitToCanvas(minWidth: Int): AsciiCanvas {
        val computedMin = this.minWidth()
        val actualMin = maxOf(minWidth, computedMin)
        val canvas = AsciiCanvas.new(0, actualMin)
        this.emit(canvas)
        return canvas
    }

    /**
     * Emit at a particular upper-left corner, returning the
     * lower-right corner that was emitted.
     */
    fun emitAt(view: AsciiView, row: Int, column: Int): Pair<Int, Int> {
        val shiftedView = view.shift(row, column)
        this.emit(shiftedView)
        return shiftedView.close()
    }

    /**
     * When items are enclosed into a wrap, this method deconstructs
     * them into their indivisible components.
     */
    fun intoWrapItems(wrapItems: MutableList<Content>)
}

/**
 * Helper function: convert `content` into wrap items and then map
 * those with `op`, appending the final result into `wrapItems`.
 * Useful for "modifier" content items like `Styled` that do not
 * affect wrapping.
 */
internal fun intoWrapItemsMap(
    content: Content,
    wrapItems: MutableList<Content>,
    op: (Content) -> Content,
) {
    val subvector: MutableList<Content> = mutableListOf()
    content.intoWrapItems(subvector)
    for (item in subvector) {
        wrapItems.add(op(item))
    }
}
