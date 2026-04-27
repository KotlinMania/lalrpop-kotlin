// port-lint: source external/ascii_canvas/lib
//! An "ASCII Canvas" allows us to draw lines and write text into a
//! fixed-sized canvas and then convert that canvas into ASCII
//! characters. ANSI styling is supported.
package io.github.kotlinmania.lalrpop.message

import io.github.kotlinmania.lalrpop.Style

/**
 * AsciiView is a view onto an `AsciiCanvas` which potentially
 * applies transformations along the way (e.g., shifting, adding
 * styling information). Most of the main drawing methods for
 * `AsciiCanvas` are defined as inherent methods on an `AsciiView`
 * trait object.
 */
interface AsciiView {
    fun columns(): Int
    fun readChar(row: Int, column: Int): Char
    fun writeChar(row: Int, column: Int, ch: Char, style: Style)
}

private fun AsciiView.addBoxDirs(row: Int, column: Int, dirs: Int) {
    val oldCh = this.readChar(row, column)
    val newCh = addDirs(oldCh, dirs)
    this.writeChar(row, column, newCh, Style.new())
}

/** Draws a line for the given range of rows at the given column. */
fun AsciiView.drawVerticalLine(rows: IntRange, column: Int) {
    val len = rows.count()
    for ((index, r) in rows.withIndex()) {
        val newDirs = when {
            index == 0 -> DOWN
            index == len - 1 -> UP
            else -> UP or DOWN
        }
        this.addBoxDirs(r, column, newDirs)
    }
}

/**
 * Draws a horizontal line along a given row for the given range
 * of columns.
 */
fun AsciiView.drawHorizontalLine(row: Int, columns: IntRange) {
    val len = columns.count()
    for ((index, c) in columns.withIndex()) {
        val newDirs = when {
            index == 0 -> RIGHT
            index == len - 1 -> LEFT
            else -> LEFT or RIGHT
        }
        this.addBoxDirs(row, c, newDirs)
    }
}

/** Writes characters in the given style at the given position. */
fun AsciiView.writeChars(row: Int, column: Int, chars: Iterator<Char>, style: Style) {
    var i = 0
    for (ch in chars) {
        this.writeChar(row, column + i, ch, style)
        i++
    }
}

/** Creates a new view onto the same canvas, but writing at an offset. */
fun AsciiView.shift(row: Int, column: Int): ShiftedView =
    ShiftedView.new(this, row, column)

/**
 * Creates a new view onto the same canvas, but applying a style
 * to all the characters written.
 */
fun AsciiView.styled(style: Style): StyleView =
    StyleView.new(this, style)

class AsciiCanvas(
    private var rowsCount: Int,
    private val columnsCount: Int,
    private val characters: MutableList<Char>,
    private val styles: MutableList<Style>,
) : AsciiView {
    companion object {
        /**
         * Create a canvas of the given size. We will automatically add
         * rows as needed, but the columns are fixed at creation.
         */
        fun new(rows: Int, columns: Int): AsciiCanvas = AsciiCanvas(
            rowsCount = rows,
            columnsCount = columns,
            characters = MutableList(columns * rows) { ' ' },
            styles = MutableList(columns * rows) { Style.new() },
        )
    }

    private fun growRowsIfNeeded(newRows: Int) {
        if (newRows >= this.rowsCount) {
            val newChars = (newRows - this.rowsCount) * this.columnsCount
            repeat(newChars) {
                this.characters.add(' ')
                this.styles.add(Style.new())
            }
            this.rowsCount = newRows
        }
    }

    private fun index(r: Int, c: Int): Int {
        growRowsIfNeeded(r + 1)
        return inRangeIndex(r, c)
    }

    private fun inRangeIndex(r: Int, c: Int): Int {
        check(r < this.rowsCount)
        check(c <= this.columnsCount)
        return r * this.columnsCount + c
    }

    private fun startIndex(r: Int): Int = inRangeIndex(r, 0)

    private fun endIndex(r: Int): Int = inRangeIndex(r, this.columnsCount)

    fun writeTo(out: Appendable) {
        for (row in toStrings()) {
            row.writeTo(out)
            out.append('\n')
        }
    }

    fun toStrings(): List<Row> =
        (0 until this.rowsCount).map { row ->
            val start = startIndex(row)
            val end = endIndex(row)
            val chars = this.characters.subList(start, end)
            val styles = this.styles.subList(start, end)
            Row.new(chars, styles)
        }

    override fun columns(): Int = this.columnsCount

    override fun readChar(row: Int, column: Int): Char {
        check(column < this.columnsCount)
        val index = this.index(row, column)
        return this.characters[index]
    }

    override fun writeChar(row: Int, column: Int, ch: Char, style: Style) {
        check(column < this.columnsCount)
        val index = this.index(row, column)
        this.characters[index] = ch
        this.styles[index] = style
    }
}

private data class Point(val row: Int, val column: Int)

/**
 * Gives a view onto an AsciiCanvas that has a fixed upper-left
 * point. You can get one of these by calling the `shift()` method on
 * any ASCII view.
 *
 * Shifted views also track the extent of the characters which are
 * written through them; the `close()` method can be used to read
 * that out when you are finished.
 */
class ShiftedView private constructor(
    // either the base canvas or another view
    private val base: AsciiView,
    // fixed at creation: the content is always allowed to grow down,
    // but cannot grow right more than `num_columns`
    private val upperLeft: Point,
    // this is updated to track content that is emitted
    private var lowerRight: Point,
) : AsciiView {
    companion object {
        internal fun new(base: AsciiView, row: Int, column: Int): ShiftedView {
            val upperLeft = Point(row = row, column = column)
            return ShiftedView(
                base = base,
                upperLeft = upperLeft,
                lowerRight = upperLeft,
            )
        }
    }

    /**
     * Finalize the view; returns the (maximal row, maximal column)
     * that was written (in the coordinates of the parent view, not
     * the shifted view). Note that these values are the actual last
     * places that were written, so if you wrote to that precise
     * location, you would overwrite some of the content that was
     * written.
     */
    fun close(): Pair<Int, Int> = Pair(this.lowerRight.row, this.lowerRight.column)

    private fun trackMax(row: Int, column: Int) {
        this.lowerRight = Point(
            row = maxOf(this.lowerRight.row, row),
            column = maxOf(this.lowerRight.column, column),
        )
    }

    override fun columns(): Int = this.base.columns() - this.upperLeft.column

    override fun readChar(row: Int, column: Int): Char {
        val r = this.upperLeft.row + row
        val c = this.upperLeft.column + column
        return this.base.readChar(r, c)
    }

    override fun writeChar(row: Int, column: Int, ch: Char, style: Style) {
        val r = this.upperLeft.row + row
        val c = this.upperLeft.column + column
        trackMax(r, c)
        this.base.writeChar(r, c, ch, style)
    }
}

/**
 * Gives a view onto an AsciiCanvas that applies an additional style
 * to things that are written. You can get one of these by calling
 * the `styled()` method on any ASCII view.
 */
class StyleView private constructor(
    private val base: AsciiView,
    private val style: Style,
) : AsciiView {
    companion object {
        internal fun new(base: AsciiView, style: Style): StyleView = StyleView(
            base = base,
            style = style,
        )
    }

    override fun columns(): Int = this.base.columns()

    override fun readChar(row: Int, column: Int): Char = this.base.readChar(row, column)

    override fun writeChar(row: Int, column: Int, ch: Char, style: Style) {
        this.base.writeChar(row, column, ch, style.with(this.style))
    }
}

// Unicode box-drawing characters

private const val UP: Int = 0b0001
private const val DOWN: Int = 0b0010
private const val LEFT: Int = 0b0100
private const val RIGHT: Int = 0b1000

private val BOX_CHARS: List<Pair<Char, Int>> = listOf(
    '\u2575' to UP,
    '\u2502' to (UP or DOWN),
    '\u2524' to (UP or DOWN or LEFT),
    '\u251C' to (UP or DOWN or RIGHT),
    '\u253C' to (UP or DOWN or LEFT or RIGHT),
    '\u2518' to (UP or LEFT),
    '\u2514' to (UP or RIGHT),
    '\u2534' to (UP or LEFT or RIGHT),
    // No UP:
    '\u2577' to DOWN,
    '\u2510' to (DOWN or LEFT),
    '\u250C' to (DOWN or RIGHT),
    '\u252C' to (DOWN or LEFT or RIGHT),
    // No UP|DOWN:
    '\u2576' to LEFT,
    '\u2500' to (LEFT or RIGHT),
    // No LEFT:
    '\u2574' to RIGHT,
    // No RIGHT:
    ' ' to 0,
)

private fun boxCharForDirs(dirs: Int): Char {
    for ((c, d) in BOX_CHARS) {
        if (dirs == d) {
            return c
        }
    }
    error("no box character for dirs: ${dirs.toString(2)}")
}

private fun dirsForBoxChar(ch: Char): Int? {
    for ((c, d) in BOX_CHARS) {
        if (c == ch) {
            return d
        }
    }
    return null
}

private fun addDirs(oldCh: Char, newDirs: Int): Char {
    val oldDirs = dirsForBoxChar(oldCh) ?: 0
    return boxCharForDirs(oldDirs or newDirs)
}
