// port-lint: source external/asciiCanvas/style.rs
// The `Style` type is a simplified view of the various
// attributes offered by the `term` library. These are
// enumerated as bits so they can be easily or'd together
// etc.
package io.github.kotlinmania.lalrpop

/**
 * `term::color::Color` — in the `term` crate this is `u16`. We reproduce
 * the named constants `Style.apply` references.
 */
object TermColor {
    const val BLACK: Int = 0
    const val RED: Int = 1
    const val GREEN: Int = 2
    const val YELLOW: Int = 3
    const val BLUE: Int = 4
    const val MAGENTA: Int = 5
    const val CYAN: Int = 6
    const val WHITE: Int = 7
    const val BRIGHT_BLACK: Int = 8
    const val BRIGHT_RED: Int = 9
    const val BRIGHT_GREEN: Int = 10
    const val BRIGHT_YELLOW: Int = 11
    const val BRIGHT_BLUE: Int = 12
    const val BRIGHT_MAGENTA: Int = 13
    const val BRIGHT_CYAN: Int = 14
    const val BRIGHT_WHITE: Int = 15
}

/** `term::Attr` — the subset `Style.apply` passes through. */
sealed class TermAttr {
    data object Bold : TermAttr()
    data object Dim : TermAttr()
    data class Italic(val on: Boolean) : TermAttr()
    data class Underline(val on: Boolean) : TermAttr()
    data object Blink : TermAttr()
    data class Standout(val on: Boolean) : TermAttr()
    data object Reverse : TermAttr()
    data object Secure : TermAttr()
}

/**
 * `term::Terminal` trait — the subset `Style.apply` calls. In the Rust
 * source this comes from the `term` crate. Consumers implement the
 * interface; the crate is the abstract boundary, the same position
 * `term::Terminal` occupies on the Rust side.
 */
interface Terminal {
    fun reset(): Result<Unit>
    fun supportsColor(): Boolean
    fun fg(color: Int): Result<Unit>
    fun bg(color: Int): Result<Unit>
    fun supportsAttr(attr: TermAttr): Boolean
    fun attr(attr: TermAttr): Result<Unit>
}

/** `class Style { bits: u64 }` */
class Style private constructor(private val bits: Long) {
    /** `fun with(self, otherStyle: Style) -> Style` */
    fun with(otherStyle: Style): Style = Style(bits or otherStyle.bits)

    /** `fun contains(self, otherStyle: Style) -> bool` */
    fun contains(otherStyle: Style): Boolean = this.with(otherStyle) == this

    override fun equals(other: Any?): Boolean = other is Style && other.bits == bits
    override fun hashCode(): Int = bits.hashCode()

    /**
     * `fun apply<T: Terminal + ?Sized>(self, term: &mut T) -> term::Result<()>`
     *
     * Attempts to apply the given style to the given terminal. If the style is
     * not supported, either there is no effect or else a similar, substitute
     * style may be applied.
     */
    fun apply(term: Terminal): Result<Unit> {
        term.reset().getOrElse { return Result.failure(it) }

        // `fgColor!` macro expansion, one per FG color.
        fun fgColor(color: Style, termColor: Int): Result<Unit> {
            if (this.contains(color) && term.supportsColor()) {
                return term.fg(termColor)
            }
            return Result.success(Unit)
        }

        fgColor(FG_BLACK, TermColor.BLACK).getOrElse { return Result.failure(it) }
        fgColor(FG_BLUE, TermColor.BLUE).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_BLACK, TermColor.BRIGHT_BLACK).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_BLUE, TermColor.BRIGHT_BLUE).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_CYAN, TermColor.BRIGHT_CYAN).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_GREEN, TermColor.BRIGHT_GREEN).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_MAGENTA, TermColor.BRIGHT_MAGENTA).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_RED, TermColor.BRIGHT_RED).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_WHITE, TermColor.BRIGHT_WHITE).getOrElse { return Result.failure(it) }
        fgColor(FG_BRIGHT_YELLOW, TermColor.BRIGHT_YELLOW).getOrElse { return Result.failure(it) }
        fgColor(FG_CYAN, TermColor.CYAN).getOrElse { return Result.failure(it) }
        fgColor(FG_GREEN, TermColor.GREEN).getOrElse { return Result.failure(it) }
        fgColor(FG_MAGENTA, TermColor.MAGENTA).getOrElse { return Result.failure(it) }
        fgColor(FG_RED, TermColor.RED).getOrElse { return Result.failure(it) }
        fgColor(FG_WHITE, TermColor.WHITE).getOrElse { return Result.failure(it) }
        fgColor(FG_YELLOW, TermColor.YELLOW).getOrElse { return Result.failure(it) }

        // `bgColor!` macro expansion, one per BG color.
        fun bgColor(color: Style, termColor: Int): Result<Unit> {
            if (this.contains(color) && term.supportsColor()) {
                return term.bg(termColor)
            }
            return Result.success(Unit)
        }

        bgColor(BG_BLACK, TermColor.BLACK).getOrElse { return Result.failure(it) }
        bgColor(BG_BLUE, TermColor.BLUE).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_BLACK, TermColor.BRIGHT_BLACK).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_BLUE, TermColor.BRIGHT_BLUE).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_CYAN, TermColor.BRIGHT_CYAN).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_GREEN, TermColor.BRIGHT_GREEN).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_MAGENTA, TermColor.BRIGHT_MAGENTA).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_RED, TermColor.BRIGHT_RED).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_WHITE, TermColor.BRIGHT_WHITE).getOrElse { return Result.failure(it) }
        bgColor(BG_BRIGHT_YELLOW, TermColor.BRIGHT_YELLOW).getOrElse { return Result.failure(it) }
        bgColor(BG_CYAN, TermColor.CYAN).getOrElse { return Result.failure(it) }
        bgColor(BG_GREEN, TermColor.GREEN).getOrElse { return Result.failure(it) }
        bgColor(BG_MAGENTA, TermColor.MAGENTA).getOrElse { return Result.failure(it) }
        bgColor(BG_RED, TermColor.RED).getOrElse { return Result.failure(it) }
        bgColor(BG_WHITE, TermColor.WHITE).getOrElse { return Result.failure(it) }
        bgColor(BG_YELLOW, TermColor.YELLOW).getOrElse { return Result.failure(it) }

        // `attr!` macro expansion, one per attribute.
        fun attrOn(style: Style, termAttr: TermAttr): Result<Unit> {
            if (this.contains(style) && term.supportsAttr(termAttr)) {
                return term.attr(termAttr)
            }
            return Result.success(Unit)
        }

        attrOn(BOLD, TermAttr.Bold).getOrElse { return Result.failure(it) }
        attrOn(DIM, TermAttr.Dim).getOrElse { return Result.failure(it) }
        attrOn(ITALIC, TermAttr.Italic(true)).getOrElse { return Result.failure(it) }
        attrOn(UNDERLINE, TermAttr.Underline(true)).getOrElse { return Result.failure(it) }
        attrOn(BLINK, TermAttr.Blink).getOrElse { return Result.failure(it) }
        attrOn(STANDOUT, TermAttr.Standout(true)).getOrElse { return Result.failure(it) }
        attrOn(REVERSE, TermAttr.Reverse).getOrElse { return Result.failure(it) }
        attrOn(SECURE, TermAttr.Secure).getOrElse { return Result.failure(it) }

        return Result.success(Unit)
    }

    override fun toString(): String = "Style($bits)"

    companion object {
        /** `fun new() -> Style` — `Style::default()`. */
        fun new(): Style = DEFAULT

        /** `public const DEFAULT: Style = Style { bits: 0 };` */
        val DEFAULT: Style = Style(0)

        // Expansion of `declareStyles!` — one `Style` constant per bit.
        private var nextBit: Long = 0
        private fun bit(): Style {
            val s = Style(1L shl nextBit.toInt())
            nextBit += 1
            return s
        }

        // Foreground colors:
        val FG_BLACK: Style = bit()
        val FG_BLUE: Style = bit()
        val FG_BRIGHT_BLACK: Style = bit()
        val FG_BRIGHT_BLUE: Style = bit()
        val FG_BRIGHT_CYAN: Style = bit()
        val FG_BRIGHT_GREEN: Style = bit()
        val FG_BRIGHT_MAGENTA: Style = bit()
        val FG_BRIGHT_RED: Style = bit()
        val FG_BRIGHT_WHITE: Style = bit()
        val FG_BRIGHT_YELLOW: Style = bit()
        val FG_CYAN: Style = bit()
        val FG_GREEN: Style = bit()
        val FG_MAGENTA: Style = bit()
        val FG_RED: Style = bit()
        val FG_WHITE: Style = bit()
        val FG_YELLOW: Style = bit()

        // Background colors:
        val BG_BLACK: Style = bit()
        val BG_BLUE: Style = bit()
        val BG_BRIGHT_BLACK: Style = bit()
        val BG_BRIGHT_BLUE: Style = bit()
        val BG_BRIGHT_CYAN: Style = bit()
        val BG_BRIGHT_GREEN: Style = bit()
        val BG_BRIGHT_MAGENTA: Style = bit()
        val BG_BRIGHT_RED: Style = bit()
        val BG_BRIGHT_WHITE: Style = bit()
        val BG_BRIGHT_YELLOW: Style = bit()
        val BG_CYAN: Style = bit()
        val BG_GREEN: Style = bit()
        val BG_MAGENTA: Style = bit()
        val BG_RED: Style = bit()
        val BG_WHITE: Style = bit()
        val BG_YELLOW: Style = bit()

        // Other:
        val BOLD: Style = bit()
        val DIM: Style = bit()
        val ITALIC: Style = bit()
        val UNDERLINE: Style = bit()
        val BLINK: Style = bit()
        val STANDOUT: Style = bit()
        val REVERSE: Style = bit()
        val SECURE: Style = bit()
    }
}

/**
 * Tracks the currently applied style so `setStyle` can skip redundant apply
 * calls.
 */
class StyleCursor private constructor(
    private var currentStyle: Style,
    private val terminal: Terminal,
) {
    fun term(): Terminal = terminal

    fun setStyle(style: Style): Result<Unit> {
        if (style != currentStyle) {
            style.apply(terminal).getOrElse { return Result.failure(it) }
            currentStyle = style
        }
        return Result.success(Unit)
    }

    companion object {
        fun new(term: Terminal): Result<StyleCursor> {
            val currentStyle = Style.DEFAULT
            currentStyle.apply(term).getOrElse { return Result.failure(it) }
            return Result.success(StyleCursor(currentStyle, term))
        }
    }
}
