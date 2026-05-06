// port-lint: source build/fake_term.rs
package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.TermAttr
import io.github.kotlinmania.lalrpop.Terminal

/**
 * The associated output type from the upstream `term::Terminal`
 * implementation. In Kotlin the wrapped writer is an [Appendable],
 * exposed through [FakeTerminal.getRef], [FakeTerminal.getMut], and
 * [FakeTerminal.intoInner].
 */

/**
 * A `Terminal` that just ignores all attempts at formatting. Used
 * to report errors when no ANSI terminfo is available.
 *
 * The Rust version is generic over `W: io::Write` and implements both
 * writing (pass-through to the wrapped writer) and terminal control
 * (all formatting methods are no-ops). This Kotlin port mirrors that
 * shape: any [Appendable] can act as the write sink, and terminal
 * methods return success without changing formatting.
 */
internal typealias Output = Appendable

class FakeTerminal private constructor(private var write: Appendable) : Terminal, Appendable {

    override fun append(value: CharSequence?): Appendable {
        write.append(value)
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        write.append(value, startIndex, endIndex)
        return this
    }

    override fun append(value: Char): Appendable {
        write.append(value)
        return this
    }

    /** Appends [buf] to the wrapped sink and returns the number of characters written. */
    fun write(buf: String): Int {
        append(buf)
        return buf.length
    }

    /** No-op flush; [Appendable] has no portable flush operation. */
    fun flush() { /* no-op */ }

    /** Ignores foreground color changes. */
    override fun fg(color: Int): Result<Unit> = Result.success(Unit)

    /** Ignores background color changes. */
    override fun bg(color: Int): Result<Unit> = Result.success(Unit)

    /** Ignores terminal attribute changes. */
    override fun attr(attr: TermAttr): Result<Unit> = Result.success(Unit)

    /** No terminal attributes are reported as supported. */
    override fun supportsAttr(attr: TermAttr): Boolean = false

    /** Ignores reset requests. */
    override fun reset(): Result<Unit> = Result.success(Unit)

    /** No colors are reported as supported. */
    override fun supportsColor(): Boolean = false

    /** Reset is not reported as supported. */
    override fun supportsReset(): Boolean = false

    /** Ignores cursor movement requests. */
    override fun cursorUp(): Result<Unit> = Result.success(Unit)

    /** Ignores line deletion requests. */
    override fun deleteLine(): Result<Unit> = Result.success(Unit)

    /** Ignores carriage return requests. */
    override fun carriageReturn(): Result<Unit> = Result.success(Unit)

    /** Returns the wrapped sink. */
    fun getRef(): Appendable = this.write

    /** Returns the wrapped mutable sink. */
    fun getMut(): Appendable = this.write

    /** Consumes the terminal facade and returns the wrapped sink. */
    fun intoInner(): Appendable = this.write

    companion object {
        /** Creates a fake terminal over [write]. */
        fun new(write: Appendable): FakeTerminal = FakeTerminal(write)
    }
}
